package com.dataconnect.util;

import java.util.Locale;

/**
 * 按数据库方言包装分页 SQL。优先用该库较老版本就支持的写法，
 * 避免 SQL Server 2008 不认 OFFSET、Oracle 11g 不认 FETCH FIRST 这类问题。
 */
public final class SqlPageWrapper {

    private SqlPageWrapper() {}

    public static String wrap(String sql, String dbType, int offset, int pageSize) {
        if (sql == null || pageSize <= 0) {
            return sql;
        }
        String trimmed = stripTrailingSemicolon(sql);
        if (SqlDialect.isSqlServer(dbType)) {
            return wrapSqlServerRowNumber(trimmed, offset, pageSize);
        }
        if (SqlDialect.isOracle(dbType)) {
            return wrapOracleRownum(trimmed, offset, pageSize);
        }
        if (SqlDialect.isDb2(dbType) || SqlDialect.isDerby(dbType)) {
            return wrapRowNumber(trimmed, offset, pageSize, "ORDER BY 1");
        }
        if (SqlDialect.isFirebird(dbType)) {
            return wrapFirebirdFirstSkip(trimmed, offset, pageSize);
        }
        if (SqlDialect.isClickHouse(dbType)) {
            return appendOrWrap(trimmed, " LIMIT " + offset + ", " + pageSize, true);
        }
        return wrapLimitOffset(trimmed, offset, pageSize);
    }

    /** 测试/预览只要第一页时用更简单的语法。 */
    public static String wrapFirstPage(String sql, String dbType, int limit) {
        if (sql == null || limit <= 0) {
            return sql;
        }
        String trimmed = stripTrailingSemicolon(sql);
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.contains(" TOP ") || upper.contains("TOP(") || upper.contains(" LIMIT ")
                || upper.contains("FETCH FIRST") || upper.contains(" OFFSET ")
                || upper.contains("ROWNUM") || upper.contains(" FIRST ")) {
            return trimmed;
        }
        if (SqlDialect.isSqlServer(dbType)) {
            return insertAfterSelect(trimmed, "TOP " + limit + " ");
        }
        if (SqlDialect.isOracle(dbType)) {
            return wrapOracleRownum(trimmed, 0, limit);
        }
        if (SqlDialect.isFirebird(dbType)) {
            return insertAfterSelect(trimmed, "FIRST " + limit + " ");
        }
        if (SqlDialect.isDb2(dbType)) {
            return trimmed + " FETCH FIRST " + limit + " ROWS ONLY";
        }
        return trimmed + " LIMIT " + limit;
    }

    private static String wrapLimitOffset(String sql, int offset, int pageSize) {
        String clause = " LIMIT " + pageSize + " OFFSET " + offset;
        return appendOrWrap(sql, clause, sqlAlreadyHasLimit(sql));
    }

    private static boolean sqlAlreadyHasLimit(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        return upper.contains(" LIMIT ") || upper.contains("\nLIMIT ");
    }

    private static String appendOrWrap(String sql, String limitClause, boolean wrapSubquery) {
        int mainSelect = indexOfMainSelect(sql);
        String prefix = mainSelect > 0 ? sql.substring(0, mainSelect).trim() + " " : "";
        String body = mainSelect >= 0 ? sql.substring(mainSelect).trim() : sql;
        if (wrapSubquery) {
            return prefix + "SELECT * FROM (" + body + ") _ds_page" + limitClause;
        }
        return prefix + body + limitClause;
    }

    private static String wrapSqlServerRowNumber(String sql, int offset, int pageSize) {
        return wrapRowNumber(sql, offset, pageSize, "ORDER BY (SELECT NULL)");
    }

    private static String wrapRowNumber(String sql, int offset, int pageSize, String defaultOver) {
        int mainSelect = indexOfMainSelect(sql);
        String prefix = mainSelect > 0 ? sql.substring(0, mainSelect).trim() + " " : "";
        String body = mainSelect >= 0 ? sql.substring(mainSelect).trim() : sql;
        String[] parts = splitTrailingOrderBy(body);
        String inner;
        String over;
        if (parts[1] != null && orderByHasQualifier(parts[1])) {
            // TOP 必须和 ORDER BY 在同一层 SELECT，不能再包一层无 TOP 的派生表
            String src = stripTrailingSemicolon(body);
            inner = mainSelectHasTop(src) ? src : insertAfterSelect(src, "TOP 9223372036854775807 ");
            over = defaultOver;
        } else if (parts[1] != null) {
            inner = parts[0];
            over = parts[1];
        } else {
            inner = body;
            over = defaultOver;
        }
        int from = offset;
        int to = offset + pageSize;
        return prefix
                + "SELECT * FROM (SELECT ROW_NUMBER() OVER (" + over + ") AS _ds_rn, _inner.* FROM ("
                + inner + ") _inner) _ds_page WHERE _ds_rn > " + from + " AND _ds_rn <= " + to;
    }

    private static String wrapOracleRownum(String sql, int offset, int pageSize) {
        int mainSelect = indexOfMainSelect(sql);
        String prefix = mainSelect > 0 ? sql.substring(0, mainSelect).trim() + " " : "";
        String body = mainSelect >= 0 ? sql.substring(mainSelect).trim() : sql;
        int to = offset + pageSize;
        return prefix
                + "SELECT * FROM (SELECT _inner.*, ROWNUM AS _ds_rn FROM ("
                + body + ") _inner WHERE ROWNUM <= " + to
                + ") WHERE _ds_rn > " + offset;
    }

    private static String wrapFirebirdFirstSkip(String sql, int offset, int pageSize) {
        return insertAfterSelect(sql, "FIRST " + pageSize + " SKIP " + offset + " ");
    }

    private static String insertAfterSelect(String sql, String keyword) {
        int mainSelect = indexOfMainSelect(sql);
        if (mainSelect < 0) {
            return sql;
        }
        int insertAt = mainSelect + 6;
        String rest = sql.substring(insertAt);
        String head = rest.trim().toUpperCase(Locale.ROOT);
        if (head.startsWith("DISTINCT") || head.startsWith("ALL")) {
            int skip = head.startsWith("DISTINCT") ? 8 : 3;
            int pos = insertAt;
            while (pos < sql.length() && Character.isWhitespace(sql.charAt(pos))) {
                pos++;
            }
            pos += skip;
            return sql.substring(0, pos) + " " + keyword + sql.substring(pos).replaceFirst("^\\s*", " ");
        }
        return sql.substring(0, insertAt) + " " + keyword + rest.replaceFirst("^\\s*", "");
    }

    private static String stripTrailingSemicolon(String sql) {
        return sql.replaceAll(";+\\s*$", "").trim();
    }

    static int indexOfMainSelect(String sql) {
        if (sql == null || sql.isEmpty()) {
            return -1;
        }
        String upper = sql.toUpperCase(Locale.ROOT);
        int depth = 0;
        int i = 0;
        while (i < sql.length()) {
            int skip = skipLiteral(sql, i);
            if (skip > i) {
                i = skip;
                continue;
            }
            char c = sql.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0 && isKeywordAt(upper, i, "SELECT")) {
                return i;
            }
            i++;
        }
        return -1;
    }

    /**
     * 只拆主查询末尾的 ORDER BY，不拆 SELECT 列表里
     * ROW_NUMBER() OVER (... ORDER BY xs.XsXh) 这种窗口排序。
     */
    static String[] splitTrailingOrderBy(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        int depth = 0;
        int mainFrom = -1;
        int lastOrder = -1;
        int i = 0;
        while (i < sql.length()) {
            int skip = skipLiteral(sql, i);
            if (skip > i) {
                i = skip;
                continue;
            }
            char c = sql.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0 && isKeywordAt(upper, i, "FROM")) {
                if (mainFrom < 0) {
                    mainFrom = i;
                }
            } else if (depth == 0 && mainFrom >= 0 && isOrderByAt(upper, sql, i)) {
                lastOrder = i;
            }
            i++;
        }
        if (lastOrder < 0) {
            return new String[]{sql, null};
        }
        return new String[]{sql.substring(0, lastOrder).trim(), sql.substring(lastOrder).trim()};
    }

    static boolean orderByHasQualifier(String orderBy) {
        if (orderBy == null || orderBy.isEmpty()) {
            return false;
        }
        int i = 0;
        while (i < orderBy.length()) {
            int skip = skipLiteral(orderBy, i);
            if (skip > i) {
                i = skip;
                continue;
            }
            if (orderBy.charAt(i) == '.' && i > 0 && i + 1 < orderBy.length()) {
                char prev = orderBy.charAt(i - 1);
                char next = orderBy.charAt(i + 1);
                if ((Character.isLetterOrDigit(prev) || prev == '_' || prev == ']' || prev == '"')
                        && (Character.isLetterOrDigit(next) || next == '_' || next == '[' || next == '"')) {
                    return true;
                }
            }
            i++;
        }
        return false;
    }

    private static boolean mainSelectHasTop(String sql) {
        int sel = indexOfMainSelect(sql);
        if (sel < 0) {
            return false;
        }
        String rest = sql.substring(sel + 6).replaceFirst("^\\s*", "").toUpperCase(Locale.ROOT);
        if (rest.startsWith("DISTINCT")) {
            rest = rest.substring(8).replaceFirst("^\\s*", "");
        } else if (rest.startsWith("ALL")) {
            rest = rest.substring(3).replaceFirst("^\\s*", "");
        }
        return rest.startsWith("TOP ") || rest.startsWith("TOP(");
    }

    private static boolean isOrderByAt(String upper, String sql, int i) {
        if (!isKeywordAt(upper, i, "ORDER")) {
            return false;
        }
        int j = i + 5;
        while (j < sql.length() && Character.isWhitespace(sql.charAt(j))) {
            j++;
        }
        return isKeywordAt(upper, j, "BY");
    }

    private static boolean isKeywordAt(String upper, int i, String keyword) {
        int n = keyword.length();
        if (i + n > upper.length() || !upper.startsWith(keyword, i)) {
            return false;
        }
        if (i > 0 && isIdentChar(upper.charAt(i - 1))) {
            return false;
        }
        if (i + n < upper.length() && isIdentChar(upper.charAt(i + n))) {
            return false;
        }
        return true;
    }

    private static boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' ;
    }

    /** 跳过字符串、-- 行注释、/* 块注释，返回下一个应扫描的下标。 */
    static int skipLiteral(String sql, int i) {
        if (i >= sql.length()) {
            return i;
        }
        char c = sql.charAt(i);
        if (c == '\'') {
            int j = i + 1;
            while (j < sql.length()) {
                if (sql.charAt(j) == '\'') {
                    if (j + 1 < sql.length() && sql.charAt(j + 1) == '\'') {
                        j += 2;
                        continue;
                    }
                    return j + 1;
                }
                j++;
            }
            return sql.length();
        }
        if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
            int j = i + 2;
            while (j < sql.length() && sql.charAt(j) != '\n') {
                j++;
            }
            return j;
        }
        if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
            int j = sql.indexOf("*/", i + 2);
            return j < 0 ? sql.length() : j + 2;
        }
        return i;
    }
}
