package com.aiyi.game.dnfserver.database;

import org.springframework.web.bind.annotation.*;
import java.sql.SQLException;
import java.util.*;

@RestController
@RequestMapping("api/v1/admin/database")
public class DatabaseController {
    private final DatabaseManager database;
    public DatabaseController(DatabaseManager database) { this.database = database; }

    @GetMapping("schemas") public List<String> schemas() throws SQLException { return database.schemas(); }
    @GetMapping("tables") public List<Map<String, Object>> tables(@RequestParam String schema) throws SQLException { return database.tables(schema); }
    @GetMapping("columns") public Map<String, Object> columns(@RequestParam String schema, @RequestParam String table) throws SQLException { return database.describe(schema, table); }
    @GetMapping("completion") public Map<String, Object> completion(@RequestParam String schema) throws SQLException { return database.completion(schema); }
    @GetMapping("rows") public Map<String, Object> rows(@RequestParam String schema, @RequestParam String table, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="50") int pageSize) throws SQLException { return database.rows(schema, table, page, pageSize); }
    @PostMapping("execute") public Map<String, Object> execute(@RequestBody Query query) throws SQLException { return database.execute(query.schema, query.sql); }
    @PostMapping("rows") public Map<String, Object> modify(@RequestBody Change change) throws SQLException { return Collections.singletonMap("affectedRows", database.modify(change.schema, change.table, change.operation, change.keys, change.values)); }

    public static class Query { public String schema; public String sql; }
    public static class Change { public String schema; public String table; public String operation; public Map<String,Object> keys; public Map<String,Object> values; }
}
