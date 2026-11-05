package com.aiyi.game.dnfserver.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** A bounded, local bridge from saved game mail to a running game channel. */
@Service
public class OnlineMailNotificationService {
    private static final Logger LOG = LoggerFactory.getLogger(OnlineMailNotificationService.class);
    private static final String TABLE = "dnf_mail_notification";
    private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
            + "id VARCHAR(32) NOT NULL PRIMARY KEY, character_id INT NOT NULL, server_group SMALLINT NOT NULL, "
            + "state SMALLINT NOT NULL DEFAULT 0, owner_id VARCHAR(32), "
            + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, claimed_at TIMESTAMP NULL, "
            + "KEY idx_mail_notification_pending (server_group, state, created_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8";
    private static final long PENDING_TTL_MS = 15000L;
    private static final long CLAIM_TTL_MS = 5000L;
    private static final long READY_TTL_MS = 4000L;
    private final DataSource source;
    private final JdbcTemplate jdbc;
    private final Path directory;
    private final int serverGroup;
    private final String owner = UUID.randomUUID().toString().replace("-", "");
    private volatile boolean schemaReady;
    private ScheduledExecutorService executor;

    public OnlineMailNotificationService(DataSource source,
            @Value("${dnf.channels.data-dir:/data}") String dataDir,
            @Value("${dnf.channels.server-group:3}") int serverGroup) {
        this.source = source;
        this.jdbc = new JdbcTemplate(source);
        this.directory = Paths.get(dataDir).toAbsolutePath().normalize().resolve("channel-manager/mail-notify");
        this.serverGroup = serverGroup;
    }

    @PostConstruct public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "dnf-online-mail-notify");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(() -> {
            try { dispatchPending(); }
            catch (RuntimeException e) { LOG.warn("在线邮件通知检查失败：{}", e.getMessage()); }
        }, 3, 500, TimeUnit.MILLISECONDS);
    }

    @PreDestroy public void stop() {
        if (executor != null) executor.shutdownNow();
    }

    /** Called when a mail is sent, not at startup; existing databases are otherwise untouched. */
    public synchronized boolean ensureQueue() {
        if (schemaReady) return true;
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
            schemaReady = true;
            return true;
        } catch (Exception e) {
            LOG.warn("无法准备在线邮件通知队列，邮件仍可保存：{}", e.getMessage());
            return false;
        }
    }

    /** Must be called in the same transaction as the postal insert. */
    public String enqueue(long characterId) {
        if (characterId < 1 || characterId > Integer.MAX_VALUE || serverGroup < 1 || serverGroup > 6)
            throw new IllegalArgumentException("收件角色或大区无效");
        String id = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO " + TABLE + " (id, character_id, server_group, state) VALUES (?, ?, ?, 0)",
                id, characterId, serverGroup);
        return id;
    }

    public String await(String id, long timeoutMs) {
        if (id == null) return "unavailable";
        long deadline = System.nanoTime() + Math.max(0L, timeoutMs) * 1000000L;
        do {
            try {
                Integer state = jdbc.queryForObject("SELECT state FROM " + TABLE + " WHERE id = ?", Integer.class, id);
                if (state != null && state == 2) return "sent";
                if (state != null && (state == 3 || state == 4)) return "unavailable";
            } catch (Exception e) {
                LOG.debug("无法读取邮件通知状态：{}", e.getMessage());
                return "unavailable";
            }
            if (System.nanoTime() >= deadline) break;
            try { Thread.sleep(100); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        } while (true);
        return "pending";
    }

    public synchronized void dispatchPending() {
        if (!Files.isDirectory(directory)) return;
        try {
            Timestamp databaseNow = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class);
            if (databaseNow == null) return;
            long now = databaseNow.getTime();
            List<Notification> rows = jdbc.query("SELECT id, character_id, state, owner_id, created_at, claimed_at FROM "
                            + TABLE + " WHERE server_group = ? AND state IN (0, 1) ORDER BY created_at LIMIT 64",
                    (rs, row) -> new Notification(rs.getString(1), rs.getLong(2), rs.getInt(3), rs.getString(4),
                            rs.getTimestamp(5), rs.getTimestamp(6)), serverGroup);
            boolean ready = hasReadyChannel();
            for (Notification row : rows) {
                if (!validId(row.id)) continue;
                if (row.state == 0) {
                    if (row.created == null || now - row.created.getTime() > PENDING_TTL_MS) {
                        updateState(row.id, 0, 4);
                    } else if (ready && jdbc.update("UPDATE " + TABLE + " SET state=1, owner_id=?, claimed_at=CURRENT_TIMESTAMP WHERE id=? AND state=0",
                            owner, row.id) == 1) {
                        try { writeRequest(row); }
                        catch (IOException e) { updateOwnedState(row.id, 3); LOG.warn("无法提交在线邮件通知：{}", e.getMessage()); }
                    }
                } else if (row.state == 1) {
                    finishClaim(row, now);
                }
            }
        } catch (Exception e) {
            // The table may not exist until the first mail is sent.
            LOG.debug("在线邮件通知暂不可用：{}", e.getMessage());
        }
    }

    private boolean hasReadyChannel() throws IOException {
        long now = System.currentTimeMillis();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, "ready-*")) {
            for (Path entry : entries) {
                if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) continue;
                long age = now - Files.getLastModifiedTime(entry, LinkOption.NOFOLLOW_LINKS).toMillis();
                if (age >= -1000 && age <= READY_TTL_MS) return true;
            }
        }
        return false;
    }

    private void writeRequest(Notification row) throws IOException {
        Path temp = directory.resolve(row.id + "." + owner + ".tmp");
        Path request = directory.resolve(row.id + ".req");
        long expiry = System.currentTimeMillis() / 1000L + CLAIM_TTL_MS / 1000L;
        byte[] payload = (serverGroup + " " + row.characterId + " " + expiry + "\n").getBytes(StandardCharsets.US_ASCII);
        try {
            Files.write(temp, payload, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try { Files.move(temp, request, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, request); }
        } finally { Files.deleteIfExists(temp); }
    }

    private void finishClaim(Notification row, long now) throws IOException {
        Path done = directory.resolve(row.id + ".done");
        if (Files.isRegularFile(done, LinkOption.NOFOLLOW_LINKS)) {
            String result = new String(Files.readAllBytes(done), StandardCharsets.US_ASCII).trim();
            if (jdbc.update("UPDATE " + TABLE + " SET state=? WHERE id=? AND state=1 AND owner_id=?",
                    "sent".equals(result) ? 2 : 3, row.id, row.owner) == 1) Files.deleteIfExists(done);
        } else if (row.claimed == null || now - row.claimed.getTime() > CLAIM_TTL_MS + 2000L) {
            // Never resend an ambiguous claim: the game may already have processed it.
            if (jdbc.update("UPDATE " + TABLE + " SET state=3 WHERE id=? AND state=1 AND owner_id=?", row.id, row.owner) == 1)
                Files.deleteIfExists(directory.resolve(row.id + ".req"));
        }
    }

    private void updateState(String id, int from, int to) {
        jdbc.update("UPDATE " + TABLE + " SET state=? WHERE id=? AND state=?", to, id, from);
    }

    private void updateOwnedState(String id, int to) {
        jdbc.update("UPDATE " + TABLE + " SET state=? WHERE id=? AND state=1 AND owner_id=?", to, id, owner);
    }

    private static boolean validId(String id) { return id != null && id.matches("[0-9a-f]{32}"); }

    private static final class Notification {
        final String id, owner;
        final long characterId;
        final int state;
        final Timestamp created, claimed;
        Notification(String id, long characterId, int state, String owner, Timestamp created, Timestamp claimed) {
            this.id = id; this.characterId = characterId; this.state = state; this.owner = owner;
            this.created = created; this.claimed = claimed;
        }
    }
}
