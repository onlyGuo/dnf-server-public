package com.aiyi.game.dnfserver.quest;

import com.aiyi.game.dnfserver.entity.CharacInfo;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;

public class OnlineQuestBridgeTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private OnlineQuestBridge bridge;
    private JdbcTemplate jdbc;
    private Path directory;
    private CharacInfo character;

    @Before public void setUp() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA taiwan_login");
        jdbc.execute("CREATE TABLE taiwan_login.login_account_3(m_id INT, login_status INT)");
        jdbc.update("INSERT INTO taiwan_login.login_account_3 VALUES (2, 1)");
        Path root = temporary.newFolder().toPath();
        directory = Files.createDirectories(root.resolve("channel-manager/mail-notify/quests"));
        bridge = new OnlineQuestBridge(source, root.toString(), 3);
        character = new CharacInfo();
        character.setCharacNo(1);
        character.setMid(2);
    }

    @Test public void offlineCharacterKeepsDatabasePath() {
        jdbc.update("UPDATE taiwan_login.login_account_3 SET login_status=0");
        assertNull(bridge.snapshot(character));
    }

    @Test public void onlineCharacterRequiresLiveComponent() {
        try {
            bridge.snapshot(character);
            fail("missing component must not silently use offline data");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("尚未就绪"));
        }
    }

    @Test public void gameSnapshotAndCompletionAreAcknowledgedAndCleanedUp() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Files.write(directory.resolve("ready-42"), new byte[0]);
        Future<?> game = executor.submit(() -> {
            try {
                for (int stage = 0; stage < 2; stage++) {
                    Path request = waitForRequest();
                    String payload = new String(Files.readAllBytes(request), StandardCharsets.US_ASCII);
                    String[] values = payload.trim().split(" ");
                    assertEquals("3", values[0]); assertEquals("2", values[1]); assertEquals("1", values[2]);
                    assertEquals(Integer.toString(stage), values[4]);
                    if (stage == 1) { assertEquals("1", values[5]); assertEquals("101", values[46]); }
                    String response = stage == 0 ? snapshotResponse() : "completed\n";
                    Path done = directory.resolve(request.getFileName().toString().replace(".req", ".done"));
                    Files.delete(request);
                    Files.write(done, response.getBytes(StandardCharsets.US_ASCII));
                }
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        try {
            Map<String, Object> snapshot = bridge.snapshot(character);
            assertEquals(101, snapshot.get("play_1"));
            assertEquals(5, snapshot.get("play_1_trigger"));
            bridge.complete(character, snapshot, Collections.singletonList(101));
            game.get(5, TimeUnit.SECONDS);
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.done")) {
                assertFalse(files.iterator().hasNext());
            }
        } finally { executor.shutdownNow(); }
    }

    @Test public void malformedSnapshotIsRejected() {
        try { OnlineQuestBridge.parseSnapshot("snapshot 1 2"); fail(); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("无法识别")); }
    }

    private Path waitForRequest() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.req")) {
                for (Path file : files) return file;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("request not received");
    }

    private String snapshotResponse() {
        StringBuilder result = new StringBuilder("snapshot");
        for (int i = 0; i < 20; i++) result.append(i == 0 ? " 101 5" : " 0 0");
        return result.append('\n').toString();
    }
}
