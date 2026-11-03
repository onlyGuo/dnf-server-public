package com.aiyi.game.dnfserver.database;

import org.springframework.stereotype.Service;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Uses only the configured datasource; identifier quoting and JDBC metadata protect CRUD operations. */
@Service
public class DatabaseManager {
    private static final int MAX_ROWS = 1000;
    private final DataSource source;
    public DatabaseManager(DataSource source) { this.source = source; }

    public List<String> schemas() throws SQLException {
        try (Connection c = source.getConnection(); ResultSet rs = c.getMetaData().getCatalogs()) {
            List<String> names = new ArrayList<>();
            while (rs.next()) names.add(rs.getString(1));
            Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
            return names;
        }
    }

    public List<Map<String, Object>> tables(String schema) throws SQLException {
        try (Connection c = source.getConnection()) {
            requireSchema(c, schema);
            List<Map<String, Object>> tables = new ArrayList<>();
            try (ResultSet rs = c.getMetaData().getTables(schema, null, "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) tables.add(map("name", rs.getString("TABLE_NAME"), "type", rs.getString("TABLE_TYPE"), "comment", rs.getString("REMARKS")));
            }
            return tables;
        }
    }

    public Map<String, Object> completion(String schema) throws SQLException {
        try (Connection c = source.getConnection()) {
            requireSchema(c, schema);
            List<Map<String, String>> columns = new ArrayList<>();
            try (ResultSet rs = c.getMetaData().getColumns(schema, null, "%", "%")) {
                while (rs.next()) {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("table", rs.getString("TABLE_NAME"));
                    item.put("name", rs.getString("COLUMN_NAME"));
                    columns.add(item);
                }
            }
            return map("columns", columns);
        }
    }

    public Map<String, Object> describe(String schema, String table) throws SQLException {
        try (Connection c = source.getConnection()) { return describe(c, schema, table); }
    }

    private Map<String, Object> describe(Connection c, String schema, String table) throws SQLException {
        requireSchema(c, schema);
        boolean found = false, view = false;
        try (ResultSet rs = c.getMetaData().getTables(schema, null, table, new String[]{"TABLE", "VIEW"})) {
            while (rs.next()) if (table.equals(rs.getString("TABLE_NAME")) && schema.equals(rs.getString("TABLE_CAT"))) {
                found = true; view = "VIEW".equalsIgnoreCase(rs.getString("TABLE_TYPE")); break;
            }
        }
        if (!found) throw new IllegalArgumentException("表不存在或无权访问");
        List<String> primary = new ArrayList<>();
        Map<Integer, String> keys = new TreeMap<>();
        try (ResultSet rs = c.getMetaData().getPrimaryKeys(schema, null, table)) {
            while (rs.next()) keys.put(rs.getInt("KEY_SEQ"), rs.getString("COLUMN_NAME"));
        }
        primary.addAll(keys.values());
        List<Map<String, Object>> columns = new ArrayList<>();
        try (ResultSet rs = c.getMetaData().getColumns(schema, null, table, "%")) {
            while (rs.next()) {
                if (!table.equals(rs.getString("TABLE_NAME"))) continue;
                String name = rs.getString("COLUMN_NAME");
                String auto;
                try { auto = rs.getString("IS_AUTOINCREMENT"); } catch (SQLException ignored) { auto = "NO"; }
                columns.add(map("name", name, "type", rs.getString("TYPE_NAME"), "jdbcType", rs.getInt("DATA_TYPE"),
                        "size", rs.getInt("COLUMN_SIZE"), "nullable", rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                        "primary", primary.contains(name), "auto", "YES".equalsIgnoreCase(auto),
                        "defaultValue", rs.getString("COLUMN_DEF"), "comment", rs.getString("REMARKS")));
            }
        }
        return map("schema", schema, "table", table, "view", view, "primaryKeys", primary, "columns", columns);
    }

    public Map<String, Object> rows(String schema, String table, int page, int pageSize) throws SQLException {
        if (page < 1 || pageSize < 1 || pageSize > 200) throw new IllegalArgumentException("分页参数无效");
        try (Connection c = source.getConnection()) {
            Map<String, Object> metadata = describe(c, schema, table);
            String from = quote(schema) + "." + quote(table);
            long total;
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + from)) { rs.next(); total = rs.getLong(1); }
            @SuppressWarnings("unchecked") List<String> keys = (List<String>) metadata.get("primaryKeys");
            String order = keys.isEmpty() ? "" : " ORDER BY " + joinIdentifiers(keys);
            try (PreparedStatement st = c.prepareStatement("SELECT * FROM " + from + order + " LIMIT ? OFFSET ?")) {
                st.setInt(1, pageSize); st.setLong(2, (long) (page - 1) * pageSize);
                try (ResultSet rs = st.executeQuery()) {
                    Map<String, Object> result = readResult(rs, pageSize);
                    result.put("total", total); result.put("page", page); result.put("pageSize", pageSize);
                    result.put("metadata", metadata); return result;
                }
            }
        }
    }

    public int modify(String schema, String table, String operation, Map<String, Object> keys, Map<String, Object> values) throws SQLException {
        try (Connection c = source.getConnection()) {
            Map<String, Object> info = describe(c, schema, table);
            if (Boolean.TRUE.equals(info.get("view"))) throw new IllegalArgumentException("视图请通过 SQL 编辑");
            @SuppressWarnings("unchecked") List<Map<String, Object>> columns = (List<Map<String, Object>>) info.get("columns");
            @SuppressWarnings("unchecked") List<String> primary = (List<String>) info.get("primaryKeys");
            Map<String, Integer> types = new LinkedHashMap<>();
            for (Map<String, Object> col : columns) types.put((String) col.get("name"), (Integer) col.get("jdbcType"));
            if (values == null) values = Collections.emptyMap();
            if (keys == null) keys = Collections.emptyMap();
            if (!types.keySet().containsAll(values.keySet())) throw new IllegalArgumentException("字段不存在");
            if (!"insert".equals(operation) && (primary.isEmpty() || !keys.keySet().equals(new HashSet<>(primary))))
                throw new IllegalArgumentException("需要完整主键才能修改或删除行");
            String target = quote(schema) + "." + quote(table);
            String sql;
            if ("insert".equals(operation)) {
                if (values.isEmpty()) sql = "INSERT INTO " + target + " () VALUES ()";
                else sql = "INSERT INTO " + target + " (" + joinIdentifiers(values.keySet()) + ") VALUES (" + placeholders(values.size()) + ")";
            } else if ("update".equals(operation)) {
                if (values.isEmpty()) throw new IllegalArgumentException("没有需要修改的字段");
                List<String> assignments = new ArrayList<>(); for (String name : values.keySet()) assignments.add(quote(name) + "=?");
                sql = "UPDATE " + target + " SET " + String.join(", ", assignments) + where(primary);
            } else if ("delete".equals(operation)) sql = "DELETE FROM " + target + where(primary);
            else throw new IllegalArgumentException("不支持的操作");
            try (PreparedStatement st = c.prepareStatement(sql)) {
                int i = 1;
                for (Map.Entry<String, Object> value : values.entrySet()) i = bind(st, i, types.get(value.getKey()), value.getValue());
                if (!"insert".equals(operation)) for (String key : primary) i = bind(st, i, types.get(key), keys.get(key));
                int count = st.executeUpdate();
                if (!"insert".equals(operation) && count != 1) throw new IllegalArgumentException("目标行不存在或未发生变化，请刷新");
                return count;
            }
        }
    }

    public Map<String, Object> execute(String schema, String sql) throws SQLException {
        if (sql == null || sql.trim().isEmpty() || sql.length() > 1000000) throw new IllegalArgumentException("SQL 不能为空或超过 1 MB");
        List<String> statements = SqlSplitter.split(sql);
        if (statements.size() > 100) throw new IllegalArgumentException("单次最多执行 100 条语句");
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection c = source.getConnection()) {
            if (schema != null && !schema.isEmpty()) { requireSchema(c, schema); c.setCatalog(schema); }
            for (String statement : statements) {
                try (Statement st = c.createStatement()) {
                    st.setMaxRows(MAX_ROWS + 1); st.setQueryTimeout(60);
                    boolean hasResult = st.execute(statement);
                    do {
                        if (hasResult) try (ResultSet rs = st.getResultSet()) {
                            Map<String, Object> result = readResult(rs, MAX_ROWS); result.put("statement", statement); results.add(result);
                        } else {
                            int updated = st.getUpdateCount();
                            if (updated == -1) break;
                            results.add(map("statement", statement, "affectedRows", updated));
                        }
                        hasResult = st.getMoreResults();
                    } while (true);
                } catch (SQLException e) {
                    results.add(map("statement", statement, "error", e.getMessage()));
                    break;
                }
            }
        }
        return map("results", results, "maxRows", MAX_ROWS);
    }

    private static Map<String, Object> readResult(ResultSet rs, int limit) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        List<Map<String, Object>> columns = new ArrayList<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) columns.add(map("name", meta.getColumnLabel(i), "type", meta.getColumnTypeName(i)));
        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;
        while (rs.next()) {
            if (rows.size() == limit) { truncated = true; break; }
            List<Object> row = new ArrayList<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) row.add(jsonValue(rs.getObject(i)));
            rows.add(row);
        }
        return map("columns", columns, "rows", rows, "truncated", truncated);
    }

    private static Object jsonValue(Object value) throws SQLException {
        if (value instanceof Blob) value = ((Blob) value).getBytes(1, (int) ((Blob) value).length());
        if (value instanceof byte[]) return map("encoding", "base64", "value", Base64.getEncoder().encodeToString((byte[]) value));
        if (value instanceof java.util.Date) return value.toString();
        if (value instanceof BigDecimal || value instanceof java.math.BigInteger) return value.toString();
        if (value instanceof Long && (((Long) value) > 9007199254740991L || ((Long) value) < -9007199254740991L)) return value.toString();
        if (value instanceof Number || value instanceof Boolean || value == null) return value;
        return value.toString();
    }

    private static int bind(PreparedStatement st, int index, int type, Object value) throws SQLException {
        if (value == null) st.setNull(index, type);
        else if (value instanceof Map && "base64".equals(((Map<?, ?>) value).get("encoding"))) {
            try { st.setBytes(index, Base64.getDecoder().decode(String.valueOf(((Map<?, ?>) value).get("value")))); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("二进制字段必须是有效的 Base64"); }
        } else st.setObject(index, value);
        return index + 1;
    }

    private static void requireSchema(Connection c, String schema) throws SQLException {
        if (schema == null || schema.isEmpty()) throw new IllegalArgumentException("请选择数据库");
        try (ResultSet rs = c.getMetaData().getCatalogs()) { while (rs.next()) if (schema.equals(rs.getString(1))) return; }
        throw new IllegalArgumentException("数据库不存在或无权访问");
    }
    private static String quote(String identifier) { return "`" + identifier.replace("`", "``") + "`"; }
    private static String joinIdentifiers(Collection<String> names) { List<String> quoted = new ArrayList<>(); for (String n : names) quoted.add(quote(n)); return String.join(", ", quoted); }
    private static String placeholders(int n) { return String.join(", ", Collections.nCopies(n, "?")); }
    private static String where(List<String> keys) { List<String> terms = new ArrayList<>(); for (String key : keys) terms.add(quote(key) + "=?"); return " WHERE " + String.join(" AND ", terms); }
    private static Map<String, Object> map(Object... values) { Map<String, Object> result = new LinkedHashMap<>(); for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]); return result; }
}
