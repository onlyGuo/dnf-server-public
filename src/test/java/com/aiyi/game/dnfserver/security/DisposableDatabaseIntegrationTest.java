package com.aiyi.game.dnfserver.security;

import com.aiyi.game.dnfserver.conf.CommonAttr;
import com.aiyi.game.dnfserver.dao.User;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.inventory.InventoryCodec;
import com.aiyi.game.dnfserver.pvf.NpkManager;
import com.aiyi.game.dnfserver.pvf.PvfManager;
import com.aiyi.game.dnfserver.service.AccountService;
import com.aiyi.game.dnfserver.utils.cache.CacheUtil;
import com.aiyi.game.dnfserver.utils.cache.Key;
import com.aiyi.game.dnfserver.utils.cache.UserTokenCacheUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.DeflaterOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import static org.junit.Assert.*;

/** Only runs against the disposable local MariaDB fixture, never the configured game DB. */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"dnf.member.reset-enabled=false", "dnf.channels.enabled=false", "dnf.channels.sync-types=false"})
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DisposableDatabaseIntegrationTest {
    @Autowired private JdbcTemplate db;
    @Autowired private TestRestTemplate http;
    @Autowired private AccountService accounts;
    @MockBean private PvfManager pvf;
    @MockBean private NpkManager npk;

    @BeforeClass public static void requireDisposableDb() throws Exception {
        String marker = System.getenv("DNF_DISPOSABLE_DB_MARKER");
        Assume.assumeTrue("Disposable fixture required", "true".equals(System.getenv("DNF_RUN_DISPOSABLE_DB_TEST")) &&
                "jdbc:mysql://127.0.0.1:19308/dnf_service?useSSL=false".equals(System.getenv("SPRING_DATASOURCE_URL")) &&
                marker != null && marker.matches("[0-9a-f]{32}"));
        Class.forName("com.mysql.jdbc.Driver");
        try (Connection c = DriverManager.getConnection(System.getenv("SPRING_DATASOURCE_URL"),
                System.getenv("SPRING_DATASOURCE_USERNAME"), System.getenv("SPRING_DATASOURCE_PASSWORD"));
             Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT marker FROM dnf_service.codex_disposable_marker")) {
            Assume.assumeTrue("Database fixture marker did not match", rs.next() && marker.equals(rs.getString(1)));
        }
    }
    private String token(long id) {
        String token = "disposable-test-" + id;
        User user = new User(); user.setId(id); UserTokenCacheUtil.putUserCache(token, user); return token;
    }
    private ResponseEntity<String> request(HttpMethod method, String path, Object body, long userId) {
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token(userId)); headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }
    private Map body(ResponseEntity<String> response) throws IOException { return new ObjectMapper().readValue(response.getBody(), Map.class); }
    private static Map<String, Object> map(Object... args) {
        Map<String, Object> m = new LinkedHashMap<>(); for (int i = 0; i < args.length; i += 2) m.put((String) args[i], args[i + 1]); return m;
    }

    @Test public void a_startupPromotesFirstAndEnforcesRoles() {
        assertEquals(Integer.valueOf(2), db.queryForObject("SELECT admin FROM d_taiwan.accounts WHERE UID=1", Integer.class));
        String allAccounts = request(HttpMethod.GET, "/api/v1/account", null, 1).getBody();
        assertTrue(allAccounts.contains("\"admin\":2")); assertTrue(allAccounts.contains("\"admin\":1"));
        assertEquals(HttpStatus.OK, request(HttpMethod.GET, "/api/v1/session", null, 2).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, request(HttpMethod.GET, "/api/v1/admin/database/schemas", null, 2).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, request(HttpMethod.GET, "/api/v1/admin/channels", null, 2).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, request(HttpMethod.GET, "/api/v1/session", null, 3).getStatusCode());
        assertEquals(HttpStatus.OK, request(HttpMethod.GET, "/api/v1/charac/player", null, 2).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, request(HttpMethod.GET, "/api/v1/charac/other", null, 2).getStatusCode());
        String visibleAccounts = request(HttpMethod.GET, "/api/v1/account", null, 2).getBody();
        assertTrue(visibleAccounts.contains("player")); assertFalse(visibleAccounts.contains("other"));
        String visibleCharacters = request(HttpMethod.GET, "/api/v1/charac", null, 2).getBody();
        assertTrue(visibleCharacters.contains("own")); assertFalse(visibleCharacters.contains("other"));
        assertEquals(HttpStatus.FORBIDDEN, request(HttpMethod.PUT, "/api/v1/charac", map("characNo", 12), 2).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, request(HttpMethod.POST, "/api/v1/charac/12/overTasks", null, 2).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, request(HttpMethod.POST, "/api/v1/postal", map("receiveCharacNo", "12", "itemId", 1), 2).getStatusCode());
        assertEquals(HttpStatus.OK, request(HttpMethod.POST, "/api/v1/postal", map("receiveCharacNo", "11", "itemId", 1), 2).getStatusCode());
        db.update("INSERT INTO taiwan_cain_2nd.postal(receive_charac_no,item_id) VALUES (12,999)");
        String visibleMail = request(HttpMethod.GET, "/api/v1/postal", null, 2).getBody();
        assertTrue(visibleMail.contains("\"receiveCharacNo\":\"11\"")); assertFalse(visibleMail.contains("\"receiveCharacNo\":\"12\""));
        ResponseEntity<String> login = http.postForEntity("/api/v1/login", map("account", "player", "password", "test-pass"), String.class);
        assertNotEquals(HttpStatus.OK, login.getStatusCode());
        assertEquals(HttpStatus.OK, http.postForEntity("/api/v1/client/login", map("accountname", "gm", "password", "test-pass"), String.class).getStatusCode());
        assertEquals(HttpStatus.OK, http.postForEntity("/api/v1/client/login", map("accountname", "player", "password", "test-pass"), String.class).getStatusCode());
    }

    @Test public void b_schemaSqlAndPrimaryKeyOperations() throws Exception {
        db.execute("CREATE TABLE dnf_service.qa_rows (a INT NOT NULL,b INT NOT NULL,label VARCHAR(40),payload BLOB,PRIMARY KEY(a,b))");
        assertEquals(HttpStatus.OK, request(HttpMethod.GET, "/api/v1/admin/database/tables?schema=dnf_service", null, 1).getStatusCode());
        ResponseEntity<String> completion = request(HttpMethod.GET, "/api/v1/admin/database/completion?schema=dnf_service", null, 1);
        assertEquals(HttpStatus.OK, completion.getStatusCode()); assertTrue(completion.getBody().contains("label"));
        Map<String, Object> base = map("schema", "dnf_service", "table", "qa_rows", "operation", "insert", "values", map("a", 1, "b", 2, "label", "old", "payload", map("encoding", "base64", "value", "AAH/")));
        assertEquals(HttpStatus.OK, request(HttpMethod.POST, "/api/v1/admin/database/rows", base, 1).getStatusCode());
        base = map("schema", "dnf_service", "table", "qa_rows", "operation", "update", "keys", map("a", 1, "b", 2), "values", map("label", "new"));
        assertEquals(HttpStatus.OK, request(HttpMethod.POST, "/api/v1/admin/database/rows", base, 1).getStatusCode());
        assertEquals("new", db.queryForObject("SELECT label FROM dnf_service.qa_rows WHERE a=1 AND b=2", String.class));
        assertEquals(HttpStatus.OK, request(HttpMethod.GET, "/api/v1/admin/database/rows?schema=dnf_service&table=qa_rows", null, 1).getStatusCode());
        ResponseEntity<String> sql = request(HttpMethod.POST, "/api/v1/admin/database/execute", map("schema", "dnf_service", "sql", "SELECT 'a;b'; SELECT label FROM qa_rows;"), 1);
        assertEquals(HttpStatus.OK, sql.getStatusCode());
        assertEquals(2, ((List<?>) body(sql).get("results")).size());
        base = map("schema", "dnf_service", "table", "qa_rows", "operation", "delete", "keys", map("a", 1, "b", 2), "values", Collections.emptyMap());
        assertEquals(HttpStatus.OK, request(HttpMethod.POST, "/api/v1/admin/database/rows", base, 1).getStatusCode());
    }

    @Test public void c_inventoryPreservesDataAndRefusesOnlineOrStaleWrites() throws Exception {
        byte[] raw = new byte[249 * 61]; int p = 9 * 61; raw[p + 1] = 1; raw[p + 2] = 42; raw[p + 33] = 77;
        ByteArrayOutputStream out = new ByteArrayOutputStream(); out.write(new byte[]{7, 6, 5, 4});
        try (DeflaterOutputStream z = new DeflaterOutputStream(out)) { z.write(raw); }
        db.update("INSERT INTO taiwan_cain_2nd.inventory(charac_no,inventory,equipslot,creature,katagaki) VALUES (11,?,X'',X'',X'')", out.toByteArray());
        assertEquals(HttpStatus.FORBIDDEN, request(HttpMethod.GET, "/api/v1/charac/12/inventory", null, 2).getStatusCode());
        ResponseEntity<String> initial = request(HttpMethod.GET, "/api/v1/charac/11/inventory", null, 2);
        assertEquals(HttpStatus.OK, initial.getStatusCode()); String revision = (String) body(initial).get("revision");
        Map<String, Object> move = map("revision", revision, "action", "move", "index", 9, "to", 10);
        assertEquals(HttpStatus.OK, request(HttpMethod.POST, "/api/v1/charac/11/inventory", move, 2).getStatusCode());
        byte[] result = db.queryForObject("SELECT inventory FROM taiwan_cain_2nd.inventory WHERE charac_no=11", byte[].class);
        InventoryCodec decoded = InventoryCodec.read(result);
        assertEquals(42L, decoded.slot(10).get("id"));
        assertEquals(HttpStatus.BAD_REQUEST, request(HttpMethod.POST, "/api/v1/charac/11/inventory", move, 2).getStatusCode());
        String current = (String) body(request(HttpMethod.GET, "/api/v1/charac/11/inventory", null, 2)).get("revision");
        assertEquals(HttpStatus.BAD_REQUEST, request(HttpMethod.POST, "/api/v1/charac/11/inventory", map("revision", current, "action", "move", "index", 10, "to", 57), 2).getStatusCode());
        assertEquals(HttpStatus.OK, request(HttpMethod.POST, "/api/v1/charac/11/inventory", map("revision", current, "action", "edit", "index", 10, "changes", map("enhancement", 5, "amountOrGrade", 100)), 2).getStatusCode());
        decoded = InventoryCodec.read(db.queryForObject("SELECT inventory FROM taiwan_cain_2nd.inventory WHERE charac_no=11", byte[].class));
        assertEquals(5, decoded.slot(10).get("enhancement")); assertEquals(100L, decoded.slot(10).get("amountOrGrade"));
        current = (String) body(request(HttpMethod.GET, "/api/v1/charac/11/inventory", null, 2)).get("revision");
        db.update("UPDATE taiwan_login.login_account_3 SET login_status=1 WHERE m_id=3");
        assertEquals(HttpStatus.BAD_REQUEST, request(HttpMethod.POST, "/api/v1/charac/11/inventory", map("revision", current, "action", "move", "index", 10, "to", 9), 2).getStatusCode());
        db.update("UPDATE taiwan_login.login_account_3 SET login_status=0 WHERE m_id=3");
        db.execute("CREATE TABLE taiwan_login.login_account_1 (m_id INT PRIMARY KEY, login_status TINYINT NOT NULL)");
        db.update("INSERT INTO taiwan_login.login_account_1 VALUES (3,1)");
        assertEquals(HttpStatus.BAD_REQUEST, request(HttpMethod.POST, "/api/v1/charac/11/inventory", map("revision", current, "action", "move", "index", 10, "to", 9), 2).getStatusCode());
    }

    @Test public void z_firstAndSubsequentRegistrationsReceiveServerAssignedRoles() {
        db.execute("DELETE FROM d_taiwan.accounts");
        AccountVO first = registration("first", "first-index"); first.setAdmin(0); first.setParentUid(99);
        accounts.register(first, false);
        assertEquals(Integer.valueOf(2), db.queryForObject("SELECT admin FROM d_taiwan.accounts WHERE accountname='first'", Integer.class));
        AccountVO player = registration("new-player", "player-index"); player.setAdmin(2); player.setParentUid(99); player.setRecommender("first");
        accounts.register(player, false);
        Map<String, Object> row = db.queryForMap("SELECT admin,parent_uid FROM d_taiwan.accounts WHERE accountname='new-player'");
        assertEquals(0, ((Number) row.get("admin")).intValue()); assertEquals(first.getUid(), ((Number) row.get("parent_uid")).longValue());
        db.update("INSERT INTO dnf_service.GM_REGIST_AUTH_CODE(AUTH_CODE,USE_COUNT,MAX_COUNT) VALUES ('qa-code',0,1)");
        AccountVO gm = registration("new-gm", "gm-index"); gm.setAuthCode("qa-code"); gm.setAdmin(2); gm.setRecommender("first");
        accounts.register(gm, true);
        assertEquals(Integer.valueOf(1), db.queryForObject("SELECT admin FROM d_taiwan.accounts WHERE accountname='new-gm'", Integer.class));
    }
    private AccountVO registration(String name, String index) {
        CacheUtil.put(Key.as(CommonAttr.CACHE.VALIDATION_CODE, index), "answer", TimeUnit.MINUTES, 1);
        AccountVO user = new AccountVO(); user.setAccountname(name); user.setPassword("test-pass"); user.setValidationIndex(index); user.setValicode("answer"); return user;
    }
}
