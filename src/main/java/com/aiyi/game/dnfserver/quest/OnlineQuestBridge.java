package com.aiyi.game.dnfserver.quest;

import com.aiyi.game.dnfserver.entity.CharacInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local, bounded requests to the channel holding the character's live quest state. */
@Service
public class OnlineQuestBridge {
    private final JdbcTemplate jdbc;
    private final Path directory;
    private final int group;

    public OnlineQuestBridge(DataSource source,
            @Value("${dnf.channels.data-dir:/data}") String dataDir,
            @Value("${dnf.channels.server-group:3}") int group) {
        this.jdbc = new JdbcTemplate(source);
        this.directory = Paths.get(dataDir).toAbsolutePath().normalize().resolve("channel-manager/mail-notify/quests");
        this.group = group;
    }

    /** Null means the requested character is offline and can use the existing database path. */
    public Map<String, Object> snapshot(CharacInfo character) {
        if (group < 1 || group > 6) throw new IllegalArgumentException("游戏大区配置无效");
        List<Integer> states = jdbc.queryForList("SELECT login_status FROM taiwan_login.login_account_" + group + " WHERE m_id=?",
                Integer.class, character.getMid());
        if (states.isEmpty() || states.get(0) == 0) return null;
        String response = request(character, "0 0");
        if ("other-character".equals(response)) return null;
        return parseSnapshot(response);
    }

    public void complete(CharacInfo character, Map<String, Object> snapshot, List<Integer> quests) {
        if (quests.size() > 20 || quests.stream().anyMatch(id -> id == null || id <= 0 || id > 65535))
            throw new IllegalArgumentException("任务编号超出了当前游戏版本支持的范围");
        StringBuilder payload = new StringBuilder("1 ").append(quests.size());
        for (int slot = 1; slot <= 20; slot++) {
            payload.append(' ').append(snapshot.get("play_" + slot));
            payload.append(' ').append(snapshot.get("play_" + slot + "_trigger"));
        }
        for (Integer quest : quests) payload.append(' ').append(quest);
        String result = request(character, payload.toString());
        if (!"completed".equals(result)) throw new IllegalArgumentException("任务状态已改变，请刷新任务后重试");
    }

    static Map<String, Object> parseSnapshot(String response) {
        String[] values = response.split(" ");
        if (values.length != 41 || !"snapshot".equals(values[0]))
            throw new IllegalArgumentException("游戏任务组件返回了无法识别的数据");
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            for (int slot = 1; slot <= 20; slot++) {
                int id = Integer.parseInt(values[slot * 2 - 1]);
                int trigger = Integer.parseInt(values[slot * 2]);
                if (id < 0 || trigger < 0) throw new NumberFormatException();
                result.put("play_" + slot, id);
                result.put("play_" + slot + "_trigger", trigger);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("游戏任务组件返回了无效的任务进度");
        }
        return result;
    }

    private String request(CharacInfo character, String operation) {
        if (character.getCharacNo() <= 0 || character.getMid() <= 0 || character.getMid() > Integer.MAX_VALUE)
            throw new IllegalArgumentException("角色或账号无效");
        String id = UUID.randomUUID().toString().replace("-", "");
        Path pending = directory.resolve(id + ".req");
        Path temporary = directory.resolve(id + ".tmp");
        Path done = directory.resolve(id + ".done");
        try {
            if (!ready()) throw new IllegalArgumentException("在线任务组件尚未就绪，请更新并启动角色所在的游戏服务");
            long expires = System.currentTimeMillis() / 1000 + 5;
            String data = group + " " + character.getMid() + " " + character.getCharacNo() + " " + expires + " " + operation + "\n";
            Files.write(temporary, data.getBytes(StandardCharsets.US_ASCII), StandardOpenOption.CREATE_NEW);
            Files.move(temporary, pending, StandardCopyOption.ATOMIC_MOVE);
            long deadline = System.nanoTime() + 6000000000L;
            while (System.nanoTime() < deadline) {
                if (Files.isRegularFile(done, LinkOption.NOFOLLOW_LINKS)) {
                    if (Files.size(done) > 1024) throw new IOException("任务回执过大");
                    String result = new String(Files.readAllBytes(done), StandardCharsets.US_ASCII).trim();
                    if ("expired".equals(result)) break;
                    if ("unsupported".equals(result)) throw new IllegalArgumentException("当前游戏版本不支持在线任务操作");
                    return result;
                }
                Thread.sleep(50);
            }
            throw new IllegalArgumentException("在线任务操作未得到确认，请先查看游戏内任务状态再决定是否重试");
        } catch (IOException e) {
            throw new IllegalArgumentException("无法连接本机游戏任务组件", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("在线任务操作已中断，请先查看游戏内任务状态");
        } finally {
            for (Path path : Arrays.asList(temporary, pending, done)) {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            }
        }
    }

    private boolean ready() throws IOException {
        if (!Files.isDirectory(directory)) return false;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "ready-*")) {
            long now = System.currentTimeMillis();
            for (Path file : files) {
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    long age = now - Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toMillis();
                    if (age >= -1000 && age < 4000) return true;
                }
            }
        }
        return false;
    }
}
