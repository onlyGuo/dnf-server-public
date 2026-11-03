package com.aiyi.game.dnfserver.database;

import java.util.ArrayList;
import java.util.List;

/** Split MySQL scripts without splitting quoted strings, comments or stored routine bodies. */
public final class SqlSplitter {
    private SqlSplitter() { }

    public static List<String> split(String sql) {
        List<String> result = new ArrayList<>();
        StringBuilder statement = new StringBuilder();
        String delimiter = ";";
        char quote = 0;
        boolean lineComment = false, blockComment = false, lineStart = true;
        for (int i = 0; i < sql.length();) {
            char ch = sql.charAt(i), next = i + 1 < sql.length() ? sql.charAt(i + 1) : 0;
            if (lineStart && quote == 0 && !lineComment && !blockComment && statement.toString().trim().isEmpty()) {
                int end = sql.indexOf('\n', i);
                if (end < 0) end = sql.length();
                String line = sql.substring(i, end).trim();
                if (line.regionMatches(true, 0, "DELIMITER ", 0, 10)) {
                    delimiter = line.substring(10).trim();
                    if (delimiter.isEmpty() || delimiter.length() > 16) throw new IllegalArgumentException("SQL 分隔符无效");
                    i = end < sql.length() ? end + 1 : end;
                    lineStart = true;
                    continue;
                }
            }
            if (lineComment) {
                statement.append(ch); i++;
                if (ch == '\n') { lineComment = false; lineStart = true; }
                continue;
            }
            if (blockComment) {
                statement.append(ch); i++;
                if (ch == '*' && next == '/') { statement.append(next); i++; blockComment = false; }
                lineStart = ch == '\n';
                continue;
            }
            if (quote != 0) {
                statement.append(ch); i++;
                if (ch == '\\' && quote != '`' && i < sql.length()) { statement.append(sql.charAt(i++)); }
                else if (ch == quote && i < sql.length() && sql.charAt(i) == quote) { statement.append(quote); i++; }
                else if (ch == quote) quote = 0;
                lineStart = ch == '\n';
                continue;
            }
            if (sql.startsWith(delimiter, i)) {
                add(result, statement); i += delimiter.length(); lineStart = false; continue;
            }
            if (ch == '\'' || ch == '"' || ch == '`') quote = ch;
            else if (ch == '#') lineComment = true;
            else if (ch == '-' && next == '-' && (i + 2 == sql.length() || Character.isWhitespace(sql.charAt(i + 2)))) lineComment = true;
            else if (ch == '/' && next == '*') blockComment = true;
            statement.append(ch); i++;
            lineStart = ch == '\n';
        }
        if (quote != 0 || blockComment) throw new IllegalArgumentException("SQL 包含未结束的引号或注释");
        add(result, statement);
        return result;
    }

    private static void add(List<String> result, StringBuilder statement) {
        String value = statement.toString().trim();
        if (!value.isEmpty()) result.add(value);
        statement.setLength(0);
    }
}
