package com.aiyi.game.dnfserver.channel;

import com.aiyi.game.dnfserver.dao.User;
import com.aiyi.game.dnfserver.dao.PostalDao;
import com.aiyi.game.dnfserver.entity.Postal;
import com.aiyi.game.dnfserver.mail.OnlineMailNotificationService;
import com.aiyi.game.dnfserver.pvf.PvfManager;
import com.aiyi.game.dnfserver.task.MemberTask;
import com.aiyi.game.dnfserver.utils.cache.UserTokenCacheUtil;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.TestPropertySource;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.verify;

/** Explicit local integration test; all game files are temporary and DB queries are read-only. */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "dnf.channels.sync-types=false")
public class LocalDatabaseIntegrationTest {
    private static Path game;
    private static Path log;
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate database;
    @Autowired private TestRestTemplate http;
    @Autowired private ApplicationContext application;
    @MockBean private PostalDao postalDao;
    @MockBean private OnlineMailNotificationService mailNotifications;
    @MockBean private PvfManager pvfManager;

    @BeforeClass public static void setUpLocalGame() throws Exception {
        assumeTrue("Run only when explicitly enabled", "true".equals(System.getenv("DNF_RUN_LOCAL_INTEGRATION")));
        Path root = Files.createTempDirectory("dnf-local-integration-");
        Path data = Files.createDirectories(root.resolve("data"));
        game = Files.createDirectories(root.resolve("game"));
        Path proc = Files.createDirectories(root.resolve("proc"));
        Files.createDirectories(data.resolve("run"));
        Files.createDirectories(data.resolve("monitor_ip"));
        Files.write(game.resolve("df_game_r"), new byte[0]);
        Files.write(data.resolve("monitor_ip/MONITOR_PUBLIC_IP"), "192.0.2.1\n".getBytes(StandardCharsets.UTF_8));
        Files.write(data.resolve("run/start_game.sh"), ("#!/bin/bash\ncp /tmp/$channel_name.cfg " + game + "/cfg/$channel_name.cfg\n").getBytes(StandardCharsets.UTF_8));
        log = root.resolve("supervisor.log");
        Path supervisor = root.resolve("supervisorctl");
        String script = "#!/bin/sh\ncmd=$1; name=${2:-}\n" +
                "if [ \"$cmd\" = status ]; then if [ -z \"$name\" ]; then echo 'dnf_channel:channel RUNNING'; echo 'dnf_channel:game_siroco53 RUNNING'; elif [ \"$name\" = dnf:monitor_ip ] || [ \"$name\" = dnf:bridge ]; then echo \"$name: ERROR (no such process)\"; exit 3; else echo \"$name RUNNING\"; fi; exit 0; fi\n" +
                "echo \"$cmd $name\" >> '" + log + "'\n";
        Files.write(supervisor, script.getBytes(StandardCharsets.UTF_8));
        assertTrue(supervisor.toFile().setExecutable(true));
        System.setProperty("dnf.channels.enabled", "auto");
        System.setProperty("dnf.member.reset-enabled", "false");
        System.setProperty("dnf.channels.data-dir", data.toString());
        System.setProperty("dnf.channels.game-dir", game.toString());
        System.setProperty("dnf.channels.supervisorctl", supervisor.toString());
        System.setProperty("dnf.channels.process-dir", proc.toString());
    }

    @Test public void existingDatabaseAndAutomaticGameDiscoveryWorkTogether() throws Exception {
        assertTrue("The local test must not run the production cleanup task", application.getBeansOfType(MemberTask.class).isEmpty());
        Long adminId = database.queryForObject("SELECT UID FROM d_taiwan.accounts WHERE admin = 2 ORDER BY UID LIMIT 1", Long.class);
        assertNotNull(adminId);
        User user = new User();
        user.setId(adminId);
        String token = UUID.randomUUID().toString();
        UserTokenCacheUtil.putUserCache(token, user);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        String url = "http://127.0.0.1:" + port + "/api/v1/admin/channels";
        ResponseEntity<Map> discovered = http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertEquals(HttpStatus.OK, discovered.getStatusCode());
        assertEquals(Boolean.TRUE, discovered.getBody().get("available"));
        assertEquals(1, ((java.util.List<?>) discovered.getBody().get("channels")).size());
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("enabled", true);
        settings.put("mode", "STATIC");
        settings.put("target", "198.51.100.4");
        settings.put("channelType", null); // Never update the real game database in this test.
        ResponseEntity<Map> saved = http.exchange(url + "/53", HttpMethod.PUT, new HttpEntity<>(settings, headers), Map.class);
        assertEquals(HttpStatus.OK, saved.getStatusCode());
        assertEquals(Collections.singletonList("restart dnf_channel:game_siroco53"), Files.readAllLines(log, StandardCharsets.UTF_8));
        Files.write(log, new byte[0]);
        Map<String, Object> defaultAddress = new LinkedHashMap<>();
        defaultAddress.put("mode", "STATIC");
        defaultAddress.put("target", "203.0.113.8");
        ResponseEntity<Map> defaultSaved = http.exchange(url + "/default-address", HttpMethod.PUT, new HttpEntity<>(defaultAddress, headers), Map.class);
        assertEquals(HttpStatus.OK, defaultSaved.getStatusCode());
        assertEquals("203.0.113.8", defaultSaved.getBody().get("resolvedIp"));
        assertEquals(java.util.Arrays.asList("restart dnf_channel:channel", "restart dnf_channel:game_siroco53"), Files.readAllLines(log, StandardCharsets.UTF_8));
        Files.delete(game.resolve("df_game_r"));
        ResponseEntity<Map> absent = http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertEquals(Boolean.FALSE, absent.getBody().get("available"));
        assertEquals(Integer.valueOf(1), database.queryForObject("SELECT 1", Integer.class));
        if (!Files.isRegularFile(java.nio.file.Paths.get("data/Script.pvf"))) {
            ResponseEntity<String> missingPvf = http.exchange("http://127.0.0.1:" + port + "/api/v1/pvf/search",
                    HttpMethod.GET, new HttpEntity<>(headers), String.class);
            assertEquals(HttpStatus.BAD_REQUEST, missingPvf.getStatusCode());
            assertTrue(missingPvf.getBody().contains("可直接输入物品 ID"));
        }

        Map<String, Object> mail = new LinkedHashMap<>();
        mail.put("receiveCharacNo", 1);
        mail.put("itemId", 12345);
        mail.put("addInfo", 1);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Void> sent = http.exchange("http://127.0.0.1:" + port + "/api/v1/postal",
                HttpMethod.POST, new HttpEntity<>(mail, headers), Void.class);
        assertTrue(sent.getStatusCode().is2xxSuccessful());
        ArgumentCaptor<Postal> captured = ArgumentCaptor.forClass(Postal.class);
        verify(postalDao).add(captured.capture()); // A mock: no real game mail is sent.
        assertEquals(12345, captured.getValue().getItemId());
    }
}
