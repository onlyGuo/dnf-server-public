package com.aiyi.game.dnfserver.mail;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class OnlineMailNotificationServiceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private JdbcDataSource source;
    private JdbcTemplate jdbc;
    private Path data, directory;
    private OnlineMailNotificationService notifications;

    @Before public void setUp() throws Exception {
        source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        data = temp.newFolder("data").toPath();
        directory = Files.createDirectories(data.resolve("channel-manager/mail-notify"));
        notifications = new OnlineMailNotificationService(source, data.toString(), 3);
        assertTrue(notifications.ensureQueue());
    }

    @Test public void savedRequestIsAcknowledgedByTheGameOnce() throws Exception {
        Files.write(directory.resolve("ready-42"), new byte[]{1});
        String id = notifications.enqueue(1);
        notifications.dispatchPending();
        Path request = directory.resolve(id + ".req");
        assertTrue(Files.isRegularFile(request));
        String content = new String(Files.readAllBytes(request), StandardCharsets.US_ASCII);
        assertTrue(content.matches("3 1 [0-9]+\\n"));
        Files.delete(request); // The game atomically claims the request before responding.
        Files.write(directory.resolve(id + ".done"), "sent\n".getBytes(StandardCharsets.US_ASCII));
        notifications.dispatchPending();
        assertEquals("sent", notifications.await(id, 0));
        assertFalse(Files.exists(request));
        assertFalse(Files.exists(directory.resolve(id + ".done")));
        notifications.dispatchPending();
        assertFalse(Files.exists(request));
    }

    @Test public void concurrentWorkersCannotClaimTheSameMailTwice() throws Exception {
        Files.write(directory.resolve("ready-42"), new byte[]{1});
        String id = notifications.enqueue(1);
        OnlineMailNotificationService other = new OnlineMailNotificationService(source, data.toString(), 3);
        CountDownLatch start = new CountDownLatch(1);
        Thread a = new Thread(() -> { await(start); notifications.dispatchPending(); });
        Thread b = new Thread(() -> { await(start); other.dispatchPending(); });
        a.start(); b.start(); start.countDown(); a.join(); b.join();
        assertTrue(Files.isRegularFile(directory.resolve(id + ".req")));
        assertEquals(Integer.valueOf(1), jdbc.queryForObject("SELECT state FROM dnf_mail_notification WHERE id=?", Integer.class, id));
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            assertEquals(1L, files.filter(p -> p.getFileName().toString().endsWith(".req")).count());
        }
    }

    @Test public void unavailableOrExpiredBridgeDoesNotPretendToNotify() {
        String id = notifications.enqueue(1);
        notifications.dispatchPending();
        assertEquals("pending", notifications.await(id, 0));
        jdbc.update("UPDATE dnf_mail_notification SET created_at=? WHERE id=?",
                new Timestamp(System.currentTimeMillis() - 30000), id);
        notifications.dispatchPending();
        assertEquals("unavailable", notifications.await(id, 0));
        assertFalse(Files.exists(directory.resolve(id + ".req")));
    }

    @Test public void javaQueueAndNativeBridgeUseTheSameProtocol() throws Exception {
        String driver = System.getenv("DNF_MAIL_TEST_DRIVER");
        assumeTrue("Native test driver not supplied", driver != null && !driver.isEmpty());
        Files.write(directory.resolve("ready-42"), new byte[]{1});
        String id = notifications.enqueue(1);
        notifications.dispatchPending();
        Process process = new ProcessBuilder(driver, directory.toString()).redirectErrorStream(true).start();
        assertEquals(0, process.waitFor());
        notifications.dispatchPending();
        assertEquals("sent", notifications.await(id, 0));
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
}
