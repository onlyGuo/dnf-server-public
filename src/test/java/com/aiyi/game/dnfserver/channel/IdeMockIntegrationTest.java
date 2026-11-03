package com.aiyi.game.dnfserver.channel;

import com.aiyi.game.dnfserver.dao.User;
import com.aiyi.game.dnfserver.pvf.PvfManager;
import com.aiyi.game.dnfserver.task.MemberTask;
import com.aiyi.game.dnfserver.utils.cache.UserTokenCacheUtil;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Explicit local test with the same relative paths used by the IDE. */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "dnf.channels.sync-types=false")
public class IdeMockIntegrationTest {
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate database;
    @Autowired private TestRestTemplate http;
    @MockBean private MemberTask memberTask; // Never execute the real database cleanup in tests.
    @MockBean private PvfManager pvfManager;

    @BeforeClass public static void localOnly() {
        assumeTrue("Run only with the local IDE simulator", "true".equals(System.getenv("DNF_RUN_IDE_SIM_TEST")));
        assumeTrue(Files.isRegularFile(Paths.get("dnf_mock/data/.dnf-local-simulator")));
    }

    private static String supervisorPid(String group) throws Exception {
        Process process = new ProcessBuilder("dnf_mock/usr/local/bin/supervisorctl", "pid", group).redirectErrorStream(true).start();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (InputStream in = process.getInputStream()) {
            byte[] bytes = new byte[512]; int count;
            while ((count = in.read(bytes)) != -1) result.write(bytes, 0, count);
        }
        int exit = process.waitFor();
        String pid = new String(result.toByteArray(), StandardCharsets.UTF_8).trim();
        assertTrue("Unexpected Supervisor response", exit == 0 || (exit == 7 && "0".equals(pid)));
        return pid;
    }

    private ResponseEntity<Map> save(String url, HttpHeaders headers, int number, boolean enabled, String mode, String target) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("enabled", enabled);
        request.put("mode", mode);
        request.put("target", target);
        request.put("channelType", null); // No production game-channel write.
        return http.exchange(url + "/" + number, HttpMethod.PUT, new HttpEntity<>(request, headers), Map.class);
    }

    private ResponseEntity<Map> setEnabled(String url, HttpHeaders headers, int number, boolean enabled) {
        return http.exchange(url + "/" + number + "/enabled", HttpMethod.PUT,
                new HttpEntity<>(java.util.Collections.singletonMap("enabled", enabled), headers), Map.class);
    }

    @Test public void idePathsDiscoverAndManageOnlyTheSelectedLocalChannel() throws Exception {
        Properties old = new Properties();
        try (InputStream in = Files.newInputStream(Paths.get("dnf_mock/data/channel-manager/channels/53.properties"))) { old.load(in); }
        assumeTrue("Keep existing simulator channel type unchanged", old.getProperty("type", "").isEmpty());
        Long id = database.queryForObject("SELECT UID FROM d_taiwan.accounts WHERE admin = 2 ORDER BY UID LIMIT 1", Long.class);
        assertNotNull(id);
        User user = new User(); user.setId(id);
        String token = UUID.randomUUID().toString();
        UserTokenCacheUtil.putUserCache(token, user);
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token);
        String url = "http://127.0.0.1:" + port + "/api/v1/admin/channels";
        ResponseEntity<Map> list = http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertEquals(Boolean.TRUE, list.getBody().get("available"));
        List<?> channels = (List<?>) list.getBody().get("channels");
        Map<?, ?> channel53 = channels.stream().map(value -> (Map<?, ?>) value)
                .filter(value -> Integer.valueOf(53).equals(value.get("number"))).findFirst().orElse(null);
        assertNotNull(channel53);
        assertEquals(Boolean.TRUE, channel53.get("listening"));
        assertTrue(Long.parseLong(String.valueOf(channel53.get("memoryBytes"))) > 0);
        Map<?, ?> memory = (Map<?, ?>) list.getBody().get("memory");
        assertTrue(Long.parseLong(String.valueOf(memory.get("totalBytes"))) > 0);
        assertTrue(Long.parseLong(String.valueOf(memory.get("availableBytes"))) >= 0);
        ResponseEntity<Map> liveMemory = http.exchange(url + "/memory", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertEquals(HttpStatus.OK, liveMemory.getStatusCode());
        assertTrue(Long.parseLong(String.valueOf(liveMemory.getBody().get("totalBytes"))) > 0);
        String other = supervisorPid("channel_admin_54");
        String before = supervisorPid("channel_admin_53");
        try {
            ResponseEntity<Map> invalid = http.exchange(url + "/53/enabled", HttpMethod.PUT,
                    new HttpEntity<>(java.util.Collections.emptyMap(), headers), Map.class);
            assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());
            assertEquals(before, supervisorPid("channel_admin_53"));
            assertEquals(HttpStatus.OK, save(url, headers, 53, true, "STATIC", "198.51.100.77").getStatusCode());
            assertNotEquals(before, supervisorPid("channel_admin_53"));
            assertEquals(other, supervisorPid("channel_admin_54"));
            ResponseEntity<Map> disabled = setEnabled(url, headers, 53, false);
            assertEquals(HttpStatus.OK, disabled.getStatusCode());
            assertEquals(Boolean.FALSE, disabled.getBody().get("enabled"));
            assertEquals("STATIC", disabled.getBody().get("mode"));
            assertEquals("198.51.100.77", disabled.getBody().get("target"));
            assertEquals("0", supervisorPid("channel_admin_53"));
            assertEquals(other, supervisorPid("channel_admin_54"));
            ResponseEntity<Map> enabled = setEnabled(url, headers, 53, true);
            assertEquals(HttpStatus.OK, enabled.getStatusCode());
            assertEquals(Boolean.TRUE, enabled.getBody().get("enabled"));
            assertEquals("198.51.100.77", enabled.getBody().get("target"));
            assertEquals(other, supervisorPid("channel_admin_54"));
            assertEquals(HttpStatus.OK, save(url, headers, 53, true, "DDNS", "localhost").getStatusCode());
            assertNotEquals("0", supervisorPid("channel_admin_53"));
            assertEquals(other, supervisorPid("channel_admin_54"));
        } finally {
            save(url, headers, 53, Boolean.parseBoolean(old.getProperty("enabled", "false")),
                    old.getProperty("mode", "INHERIT"), old.getProperty("target", ""));
        }
    }

    @Test public void ideServesTheBuiltFrontendWithoutEmbeddingItInTheJar() {
        String base = "http://127.0.0.1:" + port;
        ResponseEntity<String> page = http.getForEntity(base + "/login", String.class);
        assertEquals(HttpStatus.OK, page.getStatusCode());
        assertTrue(page.getBody().contains("id=\"app\""));
        Matcher script = Pattern.compile("<script[^>]+src=\"([^\"]+\\.js)\"").matcher(page.getBody());
        assertTrue("Vite entry script is missing", script.find());
        ResponseEntity<String> asset = http.getForEntity(base + script.group(1), String.class);
        assertEquals(HttpStatus.OK, asset.getStatusCode());
        assertTrue(asset.getHeaders().getContentType().toString().contains("javascript"));
    }

    @Test public void ideCanCreateAnIndependentChannel() throws Exception {
        java.nio.file.Path settings = Paths.get("dnf_mock/data/channel-manager/channels/55.properties");
        java.nio.file.Path config = Paths.get("dnf_mock/data/conf.d/channel-admin-55.conf");
        assumeTrue("Leave existing simulator channels untouched", !Files.exists(settings) && !Files.exists(config));
        Long id = database.queryForObject("SELECT UID FROM d_taiwan.accounts WHERE admin = 2 ORDER BY UID LIMIT 1", Long.class);
        User user = new User(); user.setId(id);
        String token = UUID.randomUUID().toString(); UserTokenCacheUtil.putUserCache(token, user);
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token);
        String url = "http://127.0.0.1:" + port + "/api/v1/admin/channels";
        String other = supervisorPid("channel_admin_54");
        try {
            assertEquals(HttpStatus.OK, save(url, headers, 55, true, "INHERIT", "").getStatusCode());
            assertNotEquals("0", supervisorPid("channel_admin_55"));
            assertEquals(other, supervisorPid("channel_admin_54"));
            assertEquals(HttpStatus.OK, save(url, headers, 55, false, "INHERIT", "").getStatusCode());
        } finally {
            new ProcessBuilder("dnf_mock/usr/local/bin/supervisorctl", "stop", "channel_admin_55").start().waitFor();
            Files.deleteIfExists(config);
            new ProcessBuilder("dnf_mock/usr/local/bin/supervisorctl", "reread").start().waitFor();
            new ProcessBuilder("dnf_mock/usr/local/bin/supervisorctl", "remove", "channel_admin_55").start().waitFor();
            Files.deleteIfExists(settings);
            Files.deleteIfExists(Paths.get("dnf_mock/data/channel-manager/ips/55"));
        }
    }
}
