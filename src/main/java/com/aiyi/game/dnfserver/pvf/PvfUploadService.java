package com.aiyi.game.dnfserver.pvf;

import com.aiyi.game.dnfserver.channel.ChannelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PvfUploadService {
    private static final Logger LOG = LoggerFactory.getLogger(PvfUploadService.class);
    private final PvfManager pvfManager;
    private final ChannelService channels;
    private final Path persistentPvf;

    public PvfUploadService(PvfManager pvfManager, ChannelService channels,
                            @Value("${dnf.channels.data-dir:/data}") String dataDir) {
        this.pvfManager = pvfManager;
        this.channels = channels;
        this.persistentPvf = Paths.get(dataDir).toAbsolutePath().normalize().resolve("Script.pvf");
    }

    public synchronized Map<String, Object> upload(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择非空的 PVF 文件");
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".pvf")) {
            throw new IllegalArgumentException("请选择 .pvf 文件");
        }
        // The existing PVF parser uses an int-sized in-memory buffer.
        if (file.getSize() >= Integer.MAX_VALUE) throw new IllegalArgumentException("PVF 文件过大，当前解析器无法读取");
        if (!channels.canReloadPvf()) {
            throw new IllegalStateException("未发现可管理的游戏服务或 Supervisor，无法上传并重启游戏频道");
        }
        Path gamePvf = pvfManager.getPvfPath();
        Path stage = Files.createTempFile(gamePvf.getParent(), ".pvf-upload-", ".tmp");
        try {
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, stage, StandardCopyOption.REPLACE_EXISTING);
            }
            PvfManager.Catalog candidate;
            try {
                candidate = pvfManager.parseCandidate(stage);
            } catch (Exception e) {
                LOG.warn("上传的 PVF 解析失败", e);
                throw new IllegalArgumentException("PVF 解析失败，原文件未改变：" + message(e), e);
            }
            return channels.installPvfAndRestart(() -> {
                install(stage, gamePvf);
                pvfManager.activate(candidate);
            });
        } finally {
            deleteQuietly(stage);
        }
    }

    private void install(Path staged, Path gamePvf) throws IOException {
        Path gameTarget = actualTarget(gamePvf);
        List<Path> paths = new ArrayList<>();
        // The upstream DNF container copies /data/Script.pvf into the game directory
        // at startup. Update that game-owned persistent source when it exists.
        if (Files.exists(persistentPvf, LinkOption.NOFOLLOW_LINKS)) {
            Path persistentTarget = actualTarget(persistentPvf);
            if (!sameFile(gameTarget, persistentTarget)) paths.add(persistentTarget);
        }
        paths.add(gameTarget);

        List<Target> targets = new ArrayList<>();
        List<Target> replaced = new ArrayList<>();
        boolean committed = false;
        boolean rollbackFailed = false;
        try {
            for (Path path : paths) {
                Target target = new Target(path);
                targets.add(target);
                target.prepare(staged);
            }
            for (Target target : targets) {
                move(target.newFile, target.path);
                target.newFile = null;
                replaced.add(target);
            }
            committed = true;
        } catch (IOException | RuntimeException e) {
            Collections.reverse(replaced);
            List<String> rollbackErrors = new ArrayList<>();
            for (Target target : replaced) {
                try {
                    target.restore();
                } catch (IOException restoreError) {
                    rollbackFailed = true;
                    target.keepBackup = true;
                    rollbackErrors.add(target.path + "（备份：" + target.backup + "）");
                    LOG.error("PVF 恢复失败，保留备份 {}", target.backup, restoreError);
                }
            }
            String detail = rollbackFailed ? "；以下文件恢复失败，请使用保留的备份检查：" + String.join("、", rollbackErrors)
                    : "，原 PVF 已保留";
            throw new IOException("无法更新游戏服务的 PVF：" + message(e) + detail, e);
        } finally {
            for (Target target : targets) {
                deleteQuietly(target.newFile);
                if (!target.keepBackup) deleteQuietly(target.backup);
            }
        }
        if (committed) LOG.info("已更新游戏服务 PVF：{}", gamePvf);
    }

    private static Path actualTarget(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            if (!Files.exists(path)) throw new IOException("PVF 文件软链接无效：" + path);
            return path.toRealPath();
        }
        if (Files.exists(path) && !Files.isRegularFile(path)) throw new IOException("PVF 路径不是普通文件：" + path);
        return Files.exists(path) ? path.toRealPath() : path.getParent().toRealPath().resolve(path.getFileName());
    }

    private static boolean sameFile(Path first, Path second) {
        // Distinct hard links need both directory entries replaced; only skip the
        // second write when they resolve to exactly the same path.
        return first.equals(second);
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); }
        catch (IOException e) { LOG.warn("清理 PVF 临时文件失败：{}", path, e); }
    }

    private static String message(Exception e) {
        return e.getMessage() == null || e.getMessage().trim().isEmpty() ? "文件操作失败" : e.getMessage();
    }

    private static final class Target {
        final Path path;
        final boolean existed;
        Path newFile;
        Path backup;
        boolean keepBackup;

        Target(Path path) { this.path = path; this.existed = Files.isRegularFile(path); }

        void prepare(Path staged) throws IOException {
            Path parent = path.getParent();
            if (parent == null || !Files.isDirectory(parent)) throw new IOException("PVF 目录不存在：" + parent);
            newFile = Files.createTempFile(parent, ".pvf-new-", ".tmp");
            Files.copy(staged, newFile, StandardCopyOption.REPLACE_EXISTING);
            if (existed) {
                PosixFileAttributeView oldView = Files.getFileAttributeView(path, PosixFileAttributeView.class);
                PosixFileAttributeView newView = Files.getFileAttributeView(newFile, PosixFileAttributeView.class);
                if (oldView != null && newView != null) {
                    PosixFileAttributes attrs = oldView.readAttributes();
                    newView.setPermissions(attrs.permissions());
                    if (!newView.readAttributes().group().equals(attrs.group())) newView.setGroup(attrs.group());
                    if (!Files.getOwner(newFile).equals(attrs.owner())) Files.setOwner(newFile, attrs.owner());
                }
                backup = Files.createTempFile(parent, ".pvf-backup-", ".tmp");
                Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            } else {
                PosixFileAttributeView view = Files.getFileAttributeView(newFile, PosixFileAttributeView.class);
                if (view != null) {
                    Set<PosixFilePermission> mode = java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--");
                    view.setPermissions(mode);
                }
            }
        }

        void restore() throws IOException {
            if (existed) {
                move(backup, path);
                backup = null;
            } else {
                Files.deleteIfExists(path);
            }
        }
    }
}
