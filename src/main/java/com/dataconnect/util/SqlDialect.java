package com.dataconnect.util;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 各库 SQL 拼接的公共方言：标识符引号、字面量、存在性查询、列类型。
 * 能绑定参数的地方仍应使用 PreparedStatement，这里只处理不得不拼进 SQL 的片段。
 */
public final class SqlDialect {

    private static final Set<String> COMPARE_OPS = new HashSet<String>(Arrays.asList(
            "=", "!=", "<>", ">", "<", ">=", "<=", "LIKE", "NOT LIKE"));
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[^}]+\\}");
    private static final Set<String> SKIP_KEYS = new HashSet<String>(Arrays.asList(
            "offset", "page", "pageSize"));

    private SqlDialect() {}

    public static String norm(String dbType) {
        if (dbType == null) {
            return "";
        }
        return dbType.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
    }

    public static boolean isMysqlFamily(String dbType) {
        String t = norm(dbType);
        return t.contains("mysql") || t.contains("mariadb") || t.contains("tidb")
                || t.contains("oceanbase");
    }

    public static boolean isSqlServer(String dbType) {
        String t = norm(dbType);
        return t.contains("sqlserver") || t.contains("mssql");
    }

    public static boolean isOracle(String dbType) {
        return norm(dbType).contains("oracle");
    }

    public static boolean isPostgresFamily(String dbType) {
        String t = norm(dbType);
        return t.contains("postgre") || t.contains("greenplum") || t.contains("kingbase")
                || t.contains("opengauss") || t.contains("gauss");
    }

    public static boolean isClickHouse(String dbType) {
        return norm(dbType).contains("clickhouse");
    }

    public static boolean isTdengine(String dbType) {
        String t = norm(dbType);
        return t.contains("tdengine") || t.contains("taos");
    }

    public static boolean isDb2(String dbType) {
        return norm(dbType).contains("db2");
    }

    public static boolean isDerby(String dbType) {
        return norm(dbType).contains("derby");
    }

    public static boolean isFirebird(String dbType) {
        String t = norm(dbType);
        return t.contains("firebird") || t.contains("jaybird");
    }

    public static boolean isSqlite(String dbType) {
        return norm(dbType).contains("sqlite");
    }

    public static boolean isH2(String dbType) {
        String t = norm(dbType);
        return t.equals("h2") || t.startsWith("h2") || t.contains("h2database");
    }

    public static boolean isHana(String dbType) {
        String t = norm(dbType);
        return t.contains("hana") || t.contains("sap");
    }

    /** 反引号标识符：MySQL / MariaDB / ClickHouse / TDengine */
    public static boolean useBacktick(String dbType) {
        return isMysqlFamily(dbType) || isClickHouse(dbType) || isTdengine(dbType);
    }

    /**
     * 按方言给标识符加引号。支持 schema.table 或 catalog.schema.table。
     */
    public static String quoteIdent(String name, String dbType) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String[] parts = splitQualified(trimmed);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(quoteSimple(parts[i], dbType));
        }
        return sb.toString();
    }

    public static String quoteLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Boolean) {
            return Boolean.TRUE.equals(value) ? "1" : "0";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        return "'" + String.valueOf(value).replace("'", "''") + "'";
    }

    /**
     * 把 SQL 里的 ${param} 换成字面量。数字/布尔不加引号，其余加单引号并转义。
     * 未匹配到的占位符替换为 NULL，避免半截 ${} 进库。
     */
    public static String substitutePlaceholders(String sql, Map<String, ?> values) {
        return substitutePlaceholders(sql, values, true);
    }

    public static String substitutePlaceholders(String sql, Map<String, ?> values, boolean nullLeftover) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }
        String result = sql;
        if (values != null) {
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                String key = entry.getKey();
                if (key == null || SKIP_KEYS.contains(key)) {
                    continue;
                }
                String placeholder = "${" + key + "}";
                if (result.contains(placeholder)) {
                    result = result.replace(placeholder, quoteLiteral(entry.getValue()));
                }
            }
        }
        if (nullLeftover && result.contains("${")) {
            result = PLACEHOLDER.matcher(result).replaceAll("NULL");
        }
        return result;
    }

    public static String existsOneSql(String quotedFrom, String where, String dbType) {
        if (isSqlServer(dbType)) {
            return "SELECT TOP 1 1 FROM " + quotedFrom + " WHERE " + where;
        }
        if (isOracle(dbType)) {
            return "SELECT 1 FROM " + quotedFrom + " WHERE " + where + " AND ROWNUM <= 1";
        }
        if (isFirebird(dbType)) {
            return "SELECT FIRST 1 1 FROM " + quotedFrom + " WHERE " + where;
        }
        if (isDb2(dbType) || isDerby(dbType)) {
            return "SELECT 1 FROM " + quotedFrom + " WHERE " + where + " FETCH FIRST 1 ROW ONLY";
        }
        return "SELECT 1 FROM " + quotedFrom + " WHERE " + where + " LIMIT 1";
    }

    public static String compareOperator(String operator) {
        if (operator == null) {
            return "=";
        }
        String op = operator.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        if ("==".equals(op)) {
            return "=";
        }
        if (COMPARE_OPS.contains(op)) {
            return op;
        }
        return "=";
    }

    public static String columnType(Object sample, boolean uniqueCol, String dbType) {
        if (isTemporal(sample)) {
            if (isSqlServer(dbType)) {
                return "DATETIME2";
            }
            if (isOracle(dbType)) {
                return "TIMESTAMP";
            }
            if (isMysqlFamily(dbType)) {
                return "DATETIME";
            }
            return "TIMESTAMP";
        }
        if (sample instanceof Boolean) {
            if (isSqlServer(dbType)) {
                return "BIT";
            }
            if (isOracle(dbType)) {
                return "NUMBER(1)";
            }
            if (isMysqlFamily(dbType)) {
                return "TINYINT";
            }
            return "BOOLEAN";
        }
        if (sample instanceof Number) {
            if (isIntegerNumber(sample)) {
                if (isOracle(dbType)) {
                    return "NUMBER(19)";
                }
                return "BIGINT";
            }
            if (isSqlServer(dbType)) {
                return "FLOAT";
            }
            if (isOracle(dbType)) {
                return "NUMBER";
            }
            if (isPostgresFamily(dbType) || isHana(dbType) || isDb2(dbType)) {
                return "DOUBLE PRECISION";
            }
            return "DOUBLE";
        }
        if (isSqlServer(dbType)) {
            return uniqueCol ? "NVARCHAR(255)" : "NVARCHAR(MAX)";
        }
        if (isOracle(dbType)) {
            return uniqueCol ? "VARCHAR2(255)" : "VARCHAR2(4000)";
        }
        if (isPostgresFamily(dbType)) {
            return uniqueCol ? "VARCHAR(255)" : "TEXT";
        }
        if (isHana(dbType)) {
            return uniqueCol ? "NVARCHAR(255)" : "NVARCHAR(2000)";
        }
        return uniqueCol ? "VARCHAR(255)" : "VARCHAR(2000)";
    }

    public static String createTableSuffix(String dbType) {
        if (isClickHouse(dbType)) {
            return " ENGINE = MergeTree() ORDER BY tuple()";
        }
        return "";
    }

    public static String uniqueIndexName(String tableName, String dbType) {
        String base = tableName != null ? tableName.replaceAll("[^A-Za-z0-9_]", "_") : "t";
        String idx = "uk_dc_" + base;
        int max = isOracle(dbType) ? 30 : 60;
        if (idx.length() > max) {
            idx = idx.substring(0, max);
        }
        return idx;
    }

    public static String idColumnDdl(String dbType) {
        if (isMysqlFamily(dbType) || isH2(dbType)) {
            return "id BIGINT AUTO_INCREMENT PRIMARY KEY";
        }
        if (isPostgresFamily(dbType)) {
            return "id BIGSERIAL PRIMARY KEY";
        }
        if (isSqlServer(dbType)) {
            return "id BIGINT IDENTITY(1,1) PRIMARY KEY";
        }
        if (isOracle(dbType)) {
            return "id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY";
        }
        if (isDb2(dbType) || isDerby(dbType)) {
            return "id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY";
        }
        if (isSqlite(dbType)) {
            return "id INTEGER PRIMARY KEY AUTOINCREMENT";
        }
        if (norm(dbType).contains("hsqldb")) {
            return "id BIGINT IDENTITY PRIMARY KEY";
        }
        if (isFirebird(dbType)) {
            return "id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY";
        }
        if (isClickHouse(dbType)) {
            return "id UUID DEFAULT generateUUIDv4()";
        }
        return "id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY";
    }

    public static boolean isSafeIdent(String name) {
        return name != null && name.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");
    }

    private static String[] splitQualified(String name) {
        if (name.indexOf('.') < 0) {
            return new String[]{name};
        }
        return name.split("\\.");
    }

    private static String quoteSimple(String name, String dbType) {
        String ident = unwrapIdent(name);
        if (ident.isEmpty()) {
            return ident;
        }
        if (isSqlServer(dbType)) {
            return "[" + ident.replace("]", "]]") + "]";
        }
        if (useBacktick(dbType)) {
            return "`" + ident.replace("`", "``") + "`";
        }
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }

    private static String unwrapIdent(String name) {
        if (name == null) {
            return "";
        }
        String s = name.trim();
        if (s.length() >= 2) {
            char a = s.charAt(0);
            char b = s.charAt(s.length() - 1);
            if ((a == '`' && b == '`') || (a == '"' && b == '"') || (a == '[' && b == ']')) {
                s = s.substring(1, s.length() - 1);
            }
        }
        return s;
    }

    private static boolean isTemporal(Object sample) {
        return sample instanceof Date || sample instanceof Time || sample instanceof Timestamp
                || sample instanceof java.util.Date || sample instanceof Temporal;
    }

    private static boolean isIntegerNumber(Object sample) {
        return sample instanceof Byte || sample instanceof Short || sample instanceof Integer
                || sample instanceof Long;
    }
}
