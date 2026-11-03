package com.aiyi.game.dnfserver.pvf;

import com.xiaoyouma.dnf.parser.npk.model.NpkImg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
public class ImagePackStorage {
    private static final Logger LOG = LoggerFactory.getLogger(ImagePackStorage.class);
    private static final long MIN_FREE_BYTES = 128L * 1024 * 1024;
    private static final int BUFFER_SIZE = 128 * 1024;
    private static final String STAGE_PREFIX = ".dnf-imagepacks-stage-";
    private static final String BACKUP_PREFIX = ".dnf-imagepacks-backup-";
    private static final String TRANSACTION = ".dnf-imagepacks-transaction";
    private final Path imagePacks = Paths.get("data", "ImagePacks2").toAbsolutePath().normalize();
    private final Path legacyUploads = Paths.get("data", "imagepacks-uploads").toAbsolutePath().normalize();
    private final AtomicReference<NpkAssetIndex> active = new AtomicReference<>(NpkAssetIndex.empty());
    private final ReentrantReadWriteLock switchLock = new ReentrantReadWriteLock(true);
    private volatile String warning;

    @Value("${spring.servlet.multipart.max-file-size:32GB}")
    private String maxUploadSize;
    private long maxUploadBytes;
    @Value("${dnf.imagepacks.max-unpacked-bytes:137438953472}")
    private long maxUnpackedBytes;
    @Value("${dnf.imagepacks.max-file-bytes:17179869184}")
    private long maxFileBytes;
    @Value("${dnf.imagepacks.max-entries:20000}")
    private int maxEntries;

    @PostConstruct
    public void initialize() {
        maxUploadBytes = DataSize.parse(maxUploadSize).toBytes();
        try {
            if (Files.isDirectory(imagePacks)) recoverPending(imagePacks.toRealPath());
            Path legacySource = null;
            try { legacySource = legacySource(); }
            catch (IOException e) { warning = "旧上传资源不可用：" + safeMessage(e); LOG.warn(warning); }
            if (legacySource != null) {
                try {
                    migrateLegacy(legacySource);
                    return;
                } catch (IOException | RuntimeException e) {
                    warning = "旧上传资源尚未迁移到 ImagePacks2：" + safeMessage(e);
                    LOG.warn(warning, e);
                    try {
                        NpkAssetIndex old = NpkAssetIndex.build(legacySource, "previous-upload", true, true);
                        if (old.getPackCount() > 0 && old.getImageCount() > 0) {
                            active.set(old);
                            return;
                        }
                    } catch (IOException | RuntimeException oldError) { LOG.warn("旧上传资源也无法读取", oldError); }
                }
            }
            NpkAssetIndex index = NpkAssetIndex.build(imagePacks, "imagepacks", false, false);
            active.set(index);
            LOG.info("已加载 ImagePacks2：{} 个 NPK，{} 个图片索引", index.getPackCount(), index.getImageCount());
        } catch (IOException | RuntimeException e) {
            warning = "加载 ImagePacks2 失败：" + safeMessage(e);
            LOG.warn(warning, e);
        }
    }

    NpkImg loadImg(String path) throws IOException {
        switchLock.readLock().lock();
        try { return active.get().loadImg(path); }
        finally { switchLock.readLock().unlock(); }
    }

    public Map<String, Object> status() {
        NpkAssetIndex index = active.get();
        Map<String, Object> result = new HashMap<>();
        result.put("available", index.getPackCount() > 0 && index.getImageCount() > 0);
        result.put("packCount", index.getPackCount());
        result.put("imageCount", index.getImageCount());
        result.put("revision", index.getRevision());
        result.put("uploaded", index.isUploaded());
        result.put("maxUploadBytes", maxUploadBytes);
        result.put("warning", warning);
        return result;
    }

    public synchronized Map<String, Object> upload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择非空的 ImagePacks2 ZIP 文件");
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IllegalArgumentException("请选择 ZIP 格式的 ImagePacks2 文件");
        }
        if (file.getSize() > maxUploadBytes) throw new IllegalArgumentException("ZIP 文件超过允许的上传大小");

        Path stage = null;
        try {
            Path root = imagePackRoot();
            switchLock.writeLock().lock();
            try { recoverPending(root); }
            finally { switchLock.writeLock().unlock(); }
            stage = createWorkDir(root, STAGE_PREFIX);
            Path archive = stage.resolve("upload.zip");
            copyArchive(file, archive);
            Path files = stage.resolve("files");
            Files.createDirectory(files);
            extract(archive, files);

            NpkAssetIndex candidate = NpkAssetIndex.build(files, UUID.randomUUID().toString(), true, true);
            if (candidate.getPackCount() == 0 || candidate.getImageCount() == 0) {
                throw new IOException("ZIP 中没有可用的 NPK 图片资源");
            }
            Files.delete(archive);
            String previousWarning = warning;
            warning = null;
            try { install(root, stage, candidate); }
            catch (IOException | RuntimeException e) { warning = previousWarning; throw e; }
            LOG.info("已启用上传的 ImagePacks2：{} 个 NPK，{} 个图片索引", candidate.getPackCount(), candidate.getImageCount());
            return status();
        } catch (IOException | RuntimeException e) {
            LOG.warn("ImagePacks2 上传失败", e);
            throw new IllegalArgumentException("ImagePacks2 上传失败：" + safeMessage(e), e);
        } finally {
            if (stage != null && !Files.exists(stage.getParent().resolve(TRANSACTION))) deleteTree(stage);
        }
    }

    /** Only our own transient directories are excluded from the ImagePacks2 index. */
    static boolean isInternalDirectory(Path relative) {
        if (relative.getNameCount() == 0) return false;
        String first = relative.getName(0).toString();
        return first.startsWith(STAGE_PREFIX) || first.startsWith(BACKUP_PREFIX);
    }

    private Path imagePackRoot() throws IOException {
        if (!Files.exists(imagePacks, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(imagePacks);
        if (!Files.isDirectory(imagePacks)) throw new IOException("ImagePacks2 不是可用的目录，请检查路径和权限");
        return imagePacks.toRealPath();
    }

    private Path legacySource() throws IOException {
        Path marker = legacyUploads.resolve("current");
        if (!Files.isRegularFile(marker)) return null;
        String name = new String(Files.readAllBytes(marker), StandardCharsets.UTF_8).trim();
        if (!name.matches("package-[a-f0-9-]{36}")) throw new IOException("旧上传资源标记无效");
        Path source = legacyUploads.resolve(name).resolve("files");
        if (!Files.isDirectory(source)) throw new IOException("旧上传资源目录不存在");
        return source;
    }

    private void migrateLegacy(Path source) throws IOException {
        Path root = imagePackRoot();
        if (Files.isSameFile(source, root)) {
            NpkAssetIndex index = NpkAssetIndex.build(root, UUID.randomUUID().toString(), true, true);
            if (index.getPackCount() == 0 || index.getImageCount() == 0) throw new IOException("旧上传资源为空");
            Files.deleteIfExists(legacyUploads.resolve("current"));
            active.set(index);
            return;
        }
        Path stage = createWorkDir(root, STAGE_PREFIX);
        try {
            Path files = stage.resolve("files");
            Files.createDirectory(files);
            List<Path> paths = npkFiles(source, false);
            long total = 0;
            for (Path relative : paths) {
                Path input = source.resolve(relative);
                if (Files.isSymbolicLink(input)) throw new IOException("旧上传资源中包含无法迁移的软链接");
                long size = Files.size(input);
                if (size > maxFileBytes || total > maxUnpackedBytes - size) throw new IOException("旧上传资源超过允许的大小");
                total += size;
            }
            requireSpace(root, total);
            for (Path relative : paths) {
                prepareParent(files, relative);
                Files.copy(source.resolve(relative), files.resolve(relative));
            }
            NpkAssetIndex candidate = NpkAssetIndex.build(files, UUID.randomUUID().toString(), true, true);
            if (candidate.getPackCount() == 0 || candidate.getImageCount() == 0) throw new IOException("旧上传资源为空");
            warning = null;
            install(root, stage, candidate);
            LOG.info("已将以前上传的图标资源迁移到 ImagePacks2");
        } finally {
            if (!Files.exists(root.resolve(TRANSACTION))) deleteTree(stage);
        }
    }

    /** Replace NPK files in place so ImagePacks2 may also be a mounted directory. */
    private void install(Path root, Path stage, NpkAssetIndex candidate) throws IOException {
        switchLock.writeLock().lock();
        Path backup = null;
        try {
            recoverPending(root);
            backup = createWorkDir(root, BACKUP_PREFIX);
            String retired = ".current-retired-" + UUID.randomUUID();
            Properties transaction = new Properties();
            transaction.setProperty("stage", stage.getFileName().toString());
            transaction.setProperty("backup", backup.getFileName().toString());
            transaction.setProperty("retired", retired);
            transaction.setProperty("phase", "BACKUP");
            writeTransaction(root, transaction);
            for (Path relative : npkFiles(root, true)) moveRelative(root, backup, relative);

            transaction.setProperty("phase", "INSTALL");
            writeTransaction(root, transaction);
            Path files = stage.resolve("files");
            for (Path relative : npkFiles(files, false)) moveRelative(files, root, relative);
            Path oldMarker = legacyUploads.resolve("current");
            if (Files.exists(oldMarker, LinkOption.NOFOLLOW_LINKS)) move(oldMarker, legacyUploads.resolve(retired));

            transaction.setProperty("phase", "COMMITTED");
            writeTransaction(root, transaction);
            active.set(candidate.at(root));
            try { recoverPending(root); }
            catch (IOException cleanupError) {
                warning = "新图标已启用，但旧资源的临时备份尚未清理：" + safeMessage(cleanupError);
                LOG.warn(warning, cleanupError);
            }
        } catch (IOException | RuntimeException failure) {
            try {
                if (Files.exists(root.resolve(TRANSACTION))) recoverPending(root);
                else if (backup != null) removeTree(backup);
            } catch (IOException rollbackError) {
                failure.addSuppressed(rollbackError);
                throw new IOException("更新失败且自动恢复未完成，已保留临时备份，请检查 ImagePacks2：" + safeMessage(rollbackError), failure);
            }
            throw new IOException("更新失败，原图标资源已恢复：" + safeMessage(failure), failure);
        } finally {
            switchLock.writeLock().unlock();
        }
    }

    private void recoverPending(Path root) throws IOException {
        Path marker = root.resolve(TRANSACTION);
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) throw new IOException("ImagePacks2 更新记录无效");
        Properties transaction = new Properties();
        try (InputStream input = Files.newInputStream(marker)) { transaction.load(input); }
        String stageName = transaction.getProperty("stage", "");
        String backupName = transaction.getProperty("backup", "");
        String retiredName = transaction.getProperty("retired", "");
        String phase = transaction.getProperty("phase", "");
        if (!stageName.matches("\\.dnf-imagepacks-stage-[a-f0-9-]{36}") ||
                !backupName.matches("\\.dnf-imagepacks-backup-[a-f0-9-]{36}") ||
                !retiredName.matches("\\.current-retired-[a-f0-9-]{36}") ||
                !(phase.equals("BACKUP") || phase.equals("INSTALL") || phase.equals("COMMITTED"))) {
            throw new IOException("ImagePacks2 更新记录损坏，已保留原文件供检查");
        }
        Path stage = root.resolve(stageName);
        Path backup = root.resolve(backupName);
        if (Files.isSymbolicLink(stage) || Files.isSymbolicLink(backup)) throw new IOException("ImagePacks2 临时目录无效");
        if (!phase.equals("COMMITTED")) {
            if (phase.equals("INSTALL")) {
                for (Path relative : npkFiles(root, true)) Files.deleteIfExists(root.resolve(relative));
            }
            if (!Files.isDirectory(backup)) throw new IOException("ImagePacks2 备份目录不存在，无法自动恢复");
            for (Path relative : npkFiles(backup, false)) moveRelative(backup, root, relative);
            Path retired = legacyUploads.resolve(retiredName);
            if (Files.exists(retired, LinkOption.NOFOLLOW_LINKS)) {
                Path oldMarker = legacyUploads.resolve("current");
                if (Files.exists(oldMarker, LinkOption.NOFOLLOW_LINKS)) throw new IOException("旧上传资源标记冲突，无法自动恢复");
                move(retired, oldMarker);
            }
        }
        removeTree(backup);
        removeTree(stage);
        Files.deleteIfExists(legacyUploads.resolve(retiredName));
        Files.deleteIfExists(marker);
    }

    private static void writeTransaction(Path root, Properties transaction) throws IOException {
        Path temp = Files.createTempFile(root, ".dnf-imagepacks-state-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temp)) { transaction.store(output, null); }
            move(temp, root.resolve(TRANSACTION));
        } finally { Files.deleteIfExists(temp); }
    }

    private static Path createWorkDir(Path root, String prefix) throws IOException {
        return Files.createDirectory(root.resolve(prefix + UUID.randomUUID()));
    }

    private static List<Path> npkFiles(Path root, boolean skipInternal) throws IOException {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(root)) return files;
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (skipInternal && !dir.equals(root) && isInternalDirectory(root.relativize(dir))) return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if ((attrs.isRegularFile() || attrs.isSymbolicLink()) && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".npk")) {
                    files.add(root.relativize(file));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private static void moveRelative(Path source, Path destination, Path relative) throws IOException {
        prepareParent(destination, relative);
        move(source.resolve(relative), destination.resolve(relative));
    }

    private static void prepareParent(Path root, Path relative) throws IOException {
        Path parent = relative.getParent();
        Path current = root;
        if (relative.isAbsolute()) throw new IOException("ImagePacks2 路径无效");
        if (parent == null) return;
        for (Path part : parent) {
            if (part.toString().equals("..")) throw new IOException("ImagePacks2 路径无效");
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("ImagePacks2 路径冲突：" + relative);
                }
            } else Files.createDirectory(current);
        }
    }

    private void copyArchive(MultipartFile file, Path archive) throws IOException {
        requireSpace(archive.getParent(), file.getSize());
        long copied = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = file.getInputStream(); OutputStream output = Files.newOutputStream(archive, StandardOpenOption.CREATE_NEW)) {
            int n;
            while ((n = input.read(buffer)) != -1) {
                copied += n;
                if (copied > maxUploadBytes) throw new IOException("ZIP 文件超过允许的上传大小");
                output.write(buffer, 0, n);
            }
        }
        if (copied == 0) throw new IOException("ZIP 文件为空");
    }

    private void extract(Path archive, Path destination) throws IOException {
        try (ZipFile zip = openZip(archive)) {
            if (zip.size() > maxEntries) throw new IOException("ZIP 中的文件数量超过限制");
            List<PackEntry> packs = new ArrayList<>();
            boolean withDirectory = false, withoutDirectory = false;
            long total = 0;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                List<String> parts = safeParts(entry.getName());
                if (parts.isEmpty() || entry.isDirectory() || isMetadata(parts)) continue;
                String filename = parts.get(parts.size() - 1);
                if (!filename.toLowerCase(Locale.ROOT).endsWith(".npk")) continue;
                boolean underFolder = parts.get(0).equalsIgnoreCase("ImagePacks2");
                if (underFolder && parts.size() == 1) continue;
                withDirectory |= underFolder;
                withoutDirectory |= !underFolder;
                if (withDirectory && withoutDirectory) throw new IOException("ZIP 同时包含 ImagePacks2 目录和目录外的 NPK，请只使用一种结构");
                long size = entry.getSize();
                if (size < 0 || size > maxFileBytes || total > maxUnpackedBytes - size) {
                    throw new IOException("NPK 文件或解压后的资源总大小超过限制");
                }
                total += size;
                List<String> relative = underFolder ? parts.subList(1, parts.size()) : parts;
                if (isInternalDirectory(Paths.get(relative.get(0)))) throw new IOException("ZIP 包含系统保留的目录名称");
                Path path = destination;
                for (String part : relative) path = path.resolve(part);
                packs.add(new PackEntry(entry, path));
            }
            if (packs.isEmpty()) throw new IOException("ZIP 中未找到 NPK 文件，请检查 ImagePacks2 目录结构");
            requireSpace(destination, total);
            Set<String> names = new HashSet<>();
            for (PackEntry pack : packs) {
                String key = destination.relativize(pack.path).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (!names.add(key)) throw new IOException("ZIP 包含重复的 NPK 路径：" + key);
                Files.createDirectories(pack.path.getParent());
                CRC32 crc = new CRC32();
                long extracted = 0;
                byte[] buffer = new byte[BUFFER_SIZE];
                try (InputStream input = zip.getInputStream(pack.entry);
                     OutputStream output = Files.newOutputStream(pack.path, StandardOpenOption.CREATE_NEW)) {
                    int n;
                    while ((n = input.read(buffer)) != -1) {
                        extracted += n;
                        if (extracted > pack.entry.getSize() || extracted > maxFileBytes) throw new IOException("NPK 文件大小异常");
                        crc.update(buffer, 0, n);
                        output.write(buffer, 0, n);
                    }
                }
                if (extracted != pack.entry.getSize() || crc.getValue() != pack.entry.getCrc()) {
                    throw new IOException("ZIP 文件损坏：" + pack.entry.getName());
                }
            }
        }
    }

    private ZipFile openZip(Path archive) throws IOException {
        try {
            return new ZipFile(archive.toFile(), StandardCharsets.UTF_8);
        } catch (IOException | IllegalArgumentException utf8Error) {
            try {
                return new ZipFile(archive.toFile(), Charset.forName("GBK"));
            } catch (IOException | IllegalArgumentException gbkError) {
                throw new IOException("无法读取 ZIP 文件，请检查文件是否完整且未加密", gbkError);
            }
        }
    }

    private List<String> safeParts(String name) throws IOException {
        if (name == null || name.isEmpty() || name.length() > 4096) throw new IOException("ZIP 中存在无效的文件名");
        String path = name.replace('\\', '/');
        if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) throw new IOException("ZIP 中存在不安全的文件路径");
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..") || part.indexOf('\0') >= 0 || part.indexOf(':') >= 0) {
                throw new IOException("ZIP 中存在不安全的文件路径");
            }
            parts.add(part);
        }
        if (parts.size() > 64) throw new IOException("ZIP 中的目录层级过深");
        return parts;
    }

    private boolean isMetadata(List<String> parts) {
        String name = parts.get(parts.size() - 1);
        return parts.get(0).equalsIgnoreCase("__MACOSX") || name.startsWith("._") || name.equalsIgnoreCase(".DS_Store");
    }

    private void requireSpace(Path path, long needed) throws IOException {
        long available = Files.getFileStore(path).getUsableSpace();
        if (needed < 0 || available < MIN_FREE_BYTES || needed > available - MIN_FREE_BYTES) {
            throw new IOException("磁盘剩余空间不足，上传需要同时容纳 ZIP 和解压后的资源");
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null || e.getMessage().isEmpty() ? "文件读取或保存失败" : e.getMessage();
    }

    private static void deleteTree(Path path) {
        try {
            removeTree(path);
        } catch (IOException e) {
            LOG.warn("清理 ImagePacks2 临时文件失败：{}", path, e);
        }
    }

    private static void removeTree(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { Files.deleteIfExists(file); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static final class PackEntry {
        final ZipEntry entry;
        final Path path;
        PackEntry(ZipEntry entry, Path path) { this.entry = entry; this.path = path; }
    }
}
