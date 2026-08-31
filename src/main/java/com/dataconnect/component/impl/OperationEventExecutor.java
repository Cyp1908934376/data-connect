package com.dataconnect.component.impl;

import com.dataconnect.component.ComponentExecutor;
import com.dataconnect.component.ComponentExecutorFactory;
import com.dataconnect.component.DataPacket;
import com.dataconnect.component.ExecutionContext;
import com.dataconnect.entity.DsConfig;
import com.dataconnect.service.DataSourceService;
import com.dataconnect.service.DynamicDsManager;
import com.dataconnect.util.SqlDialect;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/**
 * 操作事件执行器
 * 根据数据源类型（DB/API）和操作类型执行对应的数据库操作或接口调用
 */
@Component
public class OperationEventExecutor implements ComponentExecutor {

    private static final Logger log = LoggerFactory.getLogger(OperationEventExecutor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private DynamicDsManager dynamicDsManager;

    @Autowired
    @Lazy
    private ComponentExecutorFactory executorFactory;

    @Override
    public String getType() {
        return "OPERATION";
    }

    @Override
    @SuppressWarnings("unchecked")
    public DataPacket execute(DataPacket input, Map<String, Object> config, ExecutionContext context) {
        String sourceType = getStringConfig(config, "sourceType", "DB");
        String operationType = getStringConfig(config, "operationType", "DB_QUERY");

        context.info("执行操作事件: sourceType=" + sourceType + ", operationType=" + operationType);

        try {
            if ("DB".equals(sourceType)) {
                return executeDbOperation(input, config, context);
            } else if ("API".equals(sourceType)) {
                return executeApiOperation(input, config, context);
            }
            return DataPacket.error("UNKNOWN_SOURCE", "未知数据源类型: " + sourceType);
        } catch (Exception e) {
            log.error("操作事件执行失败", e);
            context.error("操作事件执行失败: " + e.getMessage());
            return DataPacket.error("OPERATION_ERROR", "操作事件执行失败: " + e.getMessage());
        }
    }

    // ==================== 数据库操作 ====================

    @SuppressWarnings("unchecked")
    private DataPacket executeDbOperation(DataPacket input, Map<String, Object> config, ExecutionContext context) {
        String operationType = getStringConfig(config, "operationType", "DB_QUERY");
        Long dsId = getLongConfig(config, "dsId", 0L);

        if (dsId == 0) {
            return DataPacket.error("CONFIG_ERROR", "未配置数据源");
        }

        DsConfig dsConfig = dataSourceService.getById(dsId)
                .orElseThrow(() -> new RuntimeException("数据源不存在: " + dsId));

        switch (operationType) {
            case "DB_QUERY":
                return executeDbQuery(dsConfig, input, config, context);
            case "DB_INSERT":
                return executeDbInsert(dsConfig, input, config, context);
            case "DB_UPDATE":
                return executeDbUpdate(dsConfig, input, config, context);
            case "DB_DELETE":
                return executeDbDelete(dsConfig, input, config, context);
            default:
                return DataPacket.error("UNKNOWN_OP", "未知数据库操作: " + operationType);
        }
    }

    /**
     * 数据库查询 - 替换 ${param} 占位符后执行 SELECT
     */
    @SuppressWarnings("unchecked")
    private DataPacket executeDbQuery(DsConfig dsConfig, DataPacket input, Map<String, Object> config, ExecutionContext context) {
        String sql = getStringConfig(config, "sql", "");
        if (sql.isEmpty()) {
            return DataPacket.error("CONFIG_ERROR", "未配置SQL语句");
        }

        // 替换SQL中的 ${param} 变量
        String finalSql = SqlDialect.substitutePlaceholders(sql, mergeSqlParams(input));

        context.info("执行查询SQL: " + finalSql);

        try {
            DataSource ds = dynamicDsManager.getOrCreate(dsConfig);
            List<Map<String, Object>> rows = new ArrayList<>();
            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(finalSql)) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
            context.info("查询返回 " + rows.size() + " 条记录");
            return DataPacket.ofList(rows);
        } catch (Exception e) {
            log.error("数据库查询失败", e);
            return DataPacket.error("QUERY_ERROR", "数据库查询失败: " + e.getMessage());
        }
    }

    /**
     * 数据库写入：INSERT 仅新增；UPSERT 按唯一键存在则更新；OVERWRITE 先清空表再插入。
     */
    @SuppressWarnings("unchecked")
    private DataPacket executeDbInsert(DsConfig dsConfig, DataPacket input, Map<String, Object> config, ExecutionContext context) {
        String tableName = getStringConfig(config, "tableName", "");
        if (tableName.isEmpty()) {
            return DataPacket.error("CONFIG_ERROR", "未配置目标表名");
        }
        if (input == null || input.getRows() == null || input.getRows().isEmpty()) {
            context.info("写入输入为空，跳过");
            return DataPacket.empty();
        }

        String writeMode = getStringConfig(config, "writeMode", "INSERT").trim().toUpperCase();
        if (writeMode.isEmpty()) {
            writeMode = "INSERT";
        }
        List<String> uniqueKeys = parseUniqueKeys(getStringConfig(config, "uniqueKeys", ""));
        List<Map<String, String>> fieldMappings = (List<Map<String, String>>) config.get("fieldMappings");
        boolean autoCreateTable = getBooleanConfig(config, "autoCreateTable",
                fieldMappings == null || fieldMappings.isEmpty());
        List<Map<String, Object>> rows = input.getRows();
        if (fieldMappings == null || fieldMappings.isEmpty()) {
            fieldMappings = autoMappingsFromRow(rows.get(0));
            context.info("未配置字段映射，按接口字段自动写入: " + fieldMappings.size() + " 列");
        }
        if (fieldMappings.isEmpty()) {
            return DataPacket.error("CONFIG_ERROR", "没有可写入的字段");
        }
        if ("UPSERT".equals(writeMode) && uniqueKeys.isEmpty()) {
            uniqueKeys = defaultUniqueKeys(fieldMappings);
            if (uniqueKeys.isEmpty()) {
                return DataPacket.error("CONFIG_ERROR", "UPSERT 需要填写唯一键，例如 id");
            }
            context.info("UPSERT 未填唯一键，使用: " + uniqueKeys);
        }

        try {
            DataSource ds = dynamicDsManager.getOrCreate(dsConfig);
            try (Connection conn = ds.getConnection()) {
                if (autoCreateTable) {
                    ensureTable(conn, tableName, rows.get(0), fieldMappings, uniqueKeys, dsConfig.getDbType(), context);
                }
                if ("OVERWRITE".equals(writeMode)) {
                    String flag = "_truncated_" + tableName;
                    if (!Boolean.TRUE.equals(context.getGlobalVariable(flag))) {
                        truncateTable(conn, tableName, dsConfig.getDbType(), context);
                        context.setGlobalVariable(flag, true);
                    }
                }
                int count = 0;
                int inserted = 0;
                int updated = 0;
                int skipped = 0;
                String dbType = dsConfig.getDbType();
                boolean upsert = "UPSERT".equals(writeMode);
                Set<String> seenKeys = new HashSet<>();
                for (Map<String, Object> row : rows) {
                    InsertSpec spec = buildInsert(tableName, row, fieldMappings, dbType, context);
                    if (!uniqueKeys.isEmpty()) {
                        String fingerprint = uniqueFingerprint(spec.values, row, uniqueKeys);
                        boolean exists = fingerprint != null
                                && (seenKeys.contains(fingerprint)
                                || existsByUniqueKeys(conn, tableName, spec.values, row, uniqueKeys, dbType));
                        if (fingerprint != null) {
                            seenKeys.add(fingerprint);
                        } else {
                            context.warn("唯一键 " + uniqueKeys + " 在本行无值，无法去重，将直接插入");
                        }
                        if (exists) {
                            if (upsert) {
                                updateByUniqueKeys(conn, tableName, spec.values, uniqueKeys, dbType);
                                updated++;
                            } else {
                                skipped++;
                            }
                        } else {
                            executePrepared(conn, spec);
                            inserted++;
                        }
                    } else {
                        executePrepared(conn, spec);
                        inserted++;
                    }
                    count++;
                }
                context.info("写入完成: table=" + tableName + ", mode=" + writeMode
                        + ", 处理 " + count + " 条, 新增 " + inserted
                        + (upsert ? ", 更新 " + updated : "")
                        + (skipped > 0 ? ", 跳过重复 " + skipped : ""));
                Map<String, Object> resultRow = new LinkedHashMap<>();
                resultRow.put("writeCount", count);
                resultRow.put("insertCount", inserted);
                resultRow.put("updateCount", updated);
                resultRow.put("skipCount", skipped);
                resultRow.put("writeMode", writeMode);
                resultRow.put("tableName", tableName);
                resultRow.put("dsId", dsConfig.getId());
                return passThroughRows(rows, resultRow, getStringConfig(config, "returnType", "INSERTED_ROW"));
            }
        } catch (Exception e) {
            log.error("写入执行失败", e);
            return DataPacket.error("INSERT_ERROR", "写入执行失败: " + e.getMessage());
        }
    }

    private List<Map<String, String>> autoMappingsFromRow(Map<String, Object> row) {
        List<Map<String, String>> mappings = new ArrayList<>();
        if (row == null) {
            return mappings;
        }
        for (String key : row.keySet()) {
            if (key == null || key.isEmpty() || key.startsWith("_")) {
                continue;
            }
            Map<String, String> m = new LinkedHashMap<>();
            m.put("field", key);
            m.put("valueSource", "INPUT_PARAM");
            m.put("value", "${" + key + "}");
            mappings.add(m);
        }
        return mappings;
    }

    private DataPacket passThroughRows(List<Map<String, Object>> rows, Map<String, Object> stats, String returnType) {
        if ("AFFECTED_ROWS".equalsIgnoreCase(returnType)) {
            return DataPacket.of(stats);
        }
        DataPacket out = DataPacket.ofList(rows != null ? rows : new ArrayList<Map<String, Object>>());
        if (out.getVariables() == null) {
            out.setVariables(new LinkedHashMap<String, Object>());
        }
        if (stats != null) {
            out.getVariables().putAll(stats);
        }
        return out;
    }

    private InsertSpec buildInsert(String tableName, Map<String, Object> row,
            List<Map<String, String>> fieldMappings, String dbType, ExecutionContext context) {
        List<String> fields = new ArrayList<>();
        List<String> valuePlaceholders = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        Map<String, Object> collectedValues = new LinkedHashMap<>();
        Map<String, Object> inputRow = row != null ? row : new HashMap<String, Object>();

        for (Map<String, String> mapping : fieldMappings) {
            String field = mappingStr(mapping, "field", "");
            if (field == null || field.isEmpty()) {
                continue;
            }
            fields.add(SqlDialect.quoteIdent(field, dbType));
            Object bound = resolveMappingBound(mapping, inputRow, context);
            valuePlaceholders.add("?");
            params.add(bound);
            collectedValues.put(field, bound);
        }

        InsertSpec spec = new InsertSpec();
        spec.sql = "INSERT INTO " + SqlDialect.quoteIdent(tableName, dbType)
                + " (" + String.join(", ", fields) + ")"
                + " VALUES (" + String.join(", ", valuePlaceholders) + ")";
        spec.params = params;
        spec.values = collectedValues;
        return spec;
    }

    private void executePrepared(Connection conn, InsertSpec spec) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(spec.sql)) {
            for (int i = 0; i < spec.params.size(); i++) {
                pstmt.setObject(i + 1, spec.params.get(i));
            }
            pstmt.executeUpdate();
        }
    }

    private boolean existsByUniqueKeys(Connection conn, String tableName, Map<String, Object> values,
            Map<String, Object> inputRow, List<String> uniqueKeys, String dbType) throws SQLException {
        if (uniqueKeys == null || uniqueKeys.isEmpty()) {
            return false;
        }
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (String key : uniqueKeys) {
            Object val = lookupMappedValue(values, inputRow, key);
            String col = actualColumnName(values, key);
            if (val == null || isBlankValue(val)) {
                return false;
            }
            clauses.add(SqlDialect.quoteIdent(col, dbType) + " = ?");
            params.add(val);
        }
        String from = SqlDialect.quoteIdent(tableName, dbType);
        String where = String.join(" AND ", clauses);
        String sql = SqlDialect.existsOneSql(from, where, dbType);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void updateByUniqueKeys(Connection conn, String tableName, Map<String, Object> values,
            List<String> uniqueKeys, String dbType) throws SQLException {
        Set<String> keySet = new HashSet<String>();
        for (String key : uniqueKeys) {
            keySet.add(key.toLowerCase(Locale.ROOT));
        }
        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            if (e.getKey() != null && keySet.contains(e.getKey().toLowerCase(Locale.ROOT))) {
                continue;
            }
            sets.add(SqlDialect.quoteIdent(e.getKey(), dbType) + " = ?");
            params.add(e.getValue());
        }
        if (sets.isEmpty()) {
            return;
        }
        List<String> where = new ArrayList<>();
        for (String key : uniqueKeys) {
            String col = actualColumnName(values, key);
            where.add(SqlDialect.quoteIdent(col, dbType) + " = ?");
            params.add(lookupIgnoreCase(values, key));
        }
        String sql = "UPDATE " + SqlDialect.quoteIdent(tableName, dbType) + " SET " + String.join(", ", sets)
                + " WHERE " + String.join(" AND ", where);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ps.executeUpdate();
        }
    }

    private void truncateTable(Connection conn, String tableName, String dbType, ExecutionContext context) throws SQLException {
        String q = SqlDialect.quoteIdent(tableName, dbType);
        try (Statement stmt = conn.createStatement()) {
            try {
                stmt.execute("TRUNCATE TABLE " + q);
            } catch (SQLException e) {
                stmt.execute("DELETE FROM " + q);
            }
        }
        context.warn("已按覆盖模式清空表: " + tableName);
    }

    private static List<String> parseUniqueKeys(String raw) {
        List<String> keys = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return keys;
        }
        for (String part : raw.split("[,;\\s]+")) {
            String k = part.trim();
            if (!k.isEmpty()) {
                keys.add(k);
            }
        }
        return keys;
    }

    private static List<String> defaultUniqueKeys(List<Map<String, String>> fieldMappings) {
        List<String> keys = new ArrayList<>();
        for (Map<String, String> mapping : fieldMappings) {
            String field = mapping.get("field");
            if (field != null && "id".equalsIgnoreCase(field)) {
                keys.add(field);
                return keys;
            }
        }
        return keys;
    }

    private static boolean isIdent(String name) {
        return name != null && name.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    private static String uniqueFingerprint(Map<String, Object> values, Map<String, Object> row, List<String> uniqueKeys) {
        if (uniqueKeys == null || uniqueKeys.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String key : uniqueKeys) {
            Object val = lookupMappedValue(values, row, key);
            if (val == null || isBlankValue(val)) {
                return null;
            }
            sb.append('\u0001').append(String.valueOf(val).trim());
        }
        return sb.toString();
    }

    private static Object lookupMappedValue(Map<String, Object> values, Map<String, Object> row, String key) {
        Object v = lookupIgnoreCase(values, key);
        if (v != null && !isBlankValue(v)) {
            return v;
        }
        return lookupIgnoreCase(row, key);
    }

    private static String actualColumnName(Map<String, Object> values, String key) {
        if (values != null && key != null) {
            for (String k : values.keySet()) {
                if (k != null && k.equalsIgnoreCase(key)) {
                    return k;
                }
            }
        }
        return key;
    }

    private static Object lookupIgnoreCase(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        if (map.containsKey(key)) {
            return map.get(key);
        }
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static boolean isBlankValue(Object value) {
        return value == null || value.toString().trim().isEmpty();
    }

    private void ensureTable(Connection conn, String tableName, Map<String, Object> firstRow,
            List<Map<String, String>> fieldMappings, List<String> uniqueKeys, String dbType, ExecutionContext context) throws SQLException {
        if (tableExists(conn, tableName)) {
            context.info("目标表已存在: " + tableName);
            tryAddUniqueIndex(conn, tableName, uniqueKeys, dbType, context);
            return;
        }
        StringBuilder ddl = new StringBuilder("CREATE TABLE ");
        ddl.append(SqlDialect.quoteIdent(tableName, dbType)).append(" (");
        boolean first = true;
        List<String> createdFields = new ArrayList<>();
        Set<String> uniqueSet = uniqueKeys != null ? new LinkedHashSet<>(uniqueKeys) : Collections.emptySet();
        for (Map<String, String> mapping : fieldMappings) {
            String field = mappingStr(mapping, "field", "");
            if (field.isEmpty()) {
                continue;
            }
            if (!first) {
                ddl.append(", ");
            }
            first = false;
            createdFields.add(field);
            ddl.append(SqlDialect.quoteIdent(field, dbType)).append(" ");
            String paramName = mappingStr(mapping, "value", "").replace("${", "").replace("}", "");
            Object sample = firstRow != null
                    ? (firstRow.containsKey(field) ? firstRow.get(field) : firstRow.get(paramName))
                    : null;
            ddl.append(SqlDialect.columnType(sample, uniqueSet.contains(field), dbType));
        }
        if (createdFields.isEmpty()) {
            context.warn("没有可建表的字段，跳过自动建表");
            return;
        }
        ddl.append(")");
        ddl.append(SqlDialect.createTableSuffix(dbType));
        String sql = ddl.toString();
        context.info("自动建表 SQL: " + sql);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            context.info("已创建目标表: " + tableName);
        }
        List<String> uk = new ArrayList<>();
        if (uniqueKeys != null) {
            for (String k : uniqueKeys) {
                if (createdFields.contains(k)) {
                    uk.add(k);
                }
            }
        }
        tryAddUniqueIndex(conn, tableName, uk, dbType, context);
    }

    private void tryAddUniqueIndex(Connection conn, String tableName, List<String> uniqueKeys,
            String dbType, ExecutionContext context) {
        if (uniqueKeys == null || uniqueKeys.isEmpty()) {
            return;
        }
        List<String> cols = new ArrayList<>();
        for (String k : uniqueKeys) {
            if (isIdent(k)) {
                cols.add(SqlDialect.quoteIdent(k, dbType));
            }
        }
        if (cols.isEmpty()) {
            return;
        }
        String idx = SqlDialect.uniqueIndexName(tableName, dbType);
        String sql = "CREATE UNIQUE INDEX " + SqlDialect.quoteIdent(idx, dbType) + " ON "
                + SqlDialect.quoteIdent(tableName, dbType) + " (" + String.join(", ", cols) + ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            context.info("已创建唯一索引: " + idx);
        } catch (SQLException e) {
            context.warn("未创建唯一索引（可忽略，存在则更新仍按查询判断）: " + e.getMessage());
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        if (tableName == null || tableName.isEmpty()) {
            return false;
        }
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = null;
        try {
            catalog = conn.getCatalog();
        } catch (SQLException ignore) {
            catalog = null;
        }
        String[] names = new String[] { tableName, tableName.toLowerCase(Locale.ROOT), tableName.toUpperCase(Locale.ROOT) };
        for (String name : names) {
            if (lookupTable(meta, catalog, name) || lookupTable(meta, null, name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean lookupTable(DatabaseMetaData meta, String catalog, String name) throws SQLException {
        try (ResultSet rs = meta.getTables(catalog, null, name, new String[]{"TABLE", "BASE TABLE"})) {
            return rs.next();
        }
    }

    private Object jdbcValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map || value instanceof List) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                return String.valueOf(value);
            }
        }
        return value;
    }

    private static String mappingStr(Map<String, String> mapping, String key, String defaultValue) {
        if (mapping == null || key == null) {
            return defaultValue;
        }
        Object v = mapping.get(key);
        return v != null ? v.toString() : defaultValue;
    }

    private Object resolveInputValue(Map<String, Object> inputRow, String field, String value) {
        String paramName = value != null ? value.replace("${", "").replace("}", "").trim() : "";
        if (paramName.isEmpty()) {
            paramName = field;
        }
        if (inputRow == null) {
            return null;
        }
        if (inputRow.containsKey(paramName)) {
            return jdbcValue(inputRow.get(paramName));
        }
        if (inputRow.containsKey(field)) {
            return jdbcValue(inputRow.get(field));
        }
        for (Map.Entry<String, Object> e : inputRow.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(paramName)) {
                return jdbcValue(e.getValue());
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object applyValueTransform(Object value, String transform, String fieldName, ExecutionContext context) {
        if (transform == null || transform.trim().isEmpty()) {
            return value;
        }
        transform = transform.trim();
        if ("AUTO_INCREMENT".equalsIgnoreCase(transform) || "SEQ_PAD4".equalsIgnoreCase(transform)) {
            try {
                ComponentExecutor executor = executorFactory.getExecutor("EVENT");
                Map<String, Object> row = new LinkedHashMap<>();
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("targetField", fieldName);
                if ("SEQ_PAD4".equalsIgnoreCase(transform)) {
                    params.put("padLength", 4);
                }
                Map<String, Object> config = new LinkedHashMap<>();
                config.put("eventCode", "AUTO_INCREMENT");
                config.put("params", params);
                DataPacket out = executor.execute(DataPacket.of(row), config, context);
                if (out != null && out.isSuccess() && out.getRows() != null && !out.getRows().isEmpty()) {
                    Object seq = out.getRows().get(0).get(fieldName);
                    return seq != null ? seq : value;
                }
            } catch (Exception e) {
                context.warn("字段自增失败: " + e.getMessage());
            }
            return value;
        }
        if ("PAD_LEFT".equalsIgnoreCase(transform) || "PAD4".equalsIgnoreCase(transform)) {
            if (value == null) {
                return null;
            }
            return EventStepExecutor.padLeft(value.toString(), 4);
        }
        if (value == null) {
            return null;
        }
        if ("UPPER".equalsIgnoreCase(transform)) {
            return value.toString().toUpperCase();
        }
        if ("LOWER".equalsIgnoreCase(transform)) {
            return value.toString().toLowerCase();
        }
        if ("TRIM".equalsIgnoreCase(transform)) {
            return value.toString().trim();
        }
        if ("PINYIN_INITIAL".equalsIgnoreCase(transform)) {
            return com.dataconnect.util.PinyinInitials.from(value);
        }
        try {
            ComponentExecutor executor = executorFactory.getExecutor("EVENT");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(fieldName, value);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("sourceField", fieldName);
            params.put("targetField", fieldName);
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("eventCode", transform);
            config.put("params", params);
            DataPacket out = executor.execute(DataPacket.of(row), config, context);
            if (out != null && out.isSuccess() && out.getRows() != null && !out.getRows().isEmpty()) {
                Object transformed = out.getRows().get(0).get(fieldName);
                return transformed != null ? transformed : value;
            }
        } catch (Exception e) {
            context.warn("字段事件转换失败: " + transform + ", " + e.getMessage());
        }
        return value;
    }

    private static class InsertSpec {
        String sql;
        List<Object> params;
        Map<String, Object> values;
    }

    /**
     * 数据库修改 - 构建 UPDATE SET ... WHERE SQL
     */
    @SuppressWarnings("unchecked")
    private DataPacket executeDbUpdate(DsConfig dsConfig, DataPacket input, Map<String, Object> config, ExecutionContext context) {
        String tableName = getStringConfig(config, "tableName", "");
        if (tableName.isEmpty()) {
            // 兼容 tableName2
            tableName = getStringConfig(config, "tableName2", "");
        }
        if (tableName.isEmpty()) {
            return DataPacket.error("CONFIG_ERROR", "未配置目标表名");
        }

        List<Map<String, String>> fieldMappings = (List<Map<String, String>>) config.get("fieldMappings");
        if (fieldMappings == null || fieldMappings.isEmpty()) {
            return DataPacket.error("CONFIG_ERROR", "未配置SET字段");
        }

        List<Map<String, String>> whereConditions = (List<Map<String, String>>) config.get("whereConditions");
        if (whereConditions == null || whereConditions.isEmpty()) {
            return DataPacket.error("CONFIG_ERROR", "未配置WHERE条件（安全检查）");
        }

        List<Map<String, Object>> rows = input.getRows() != null ? input.getRows() : new ArrayList<Map<String, Object>>();
        if (rows.isEmpty()) {
            context.info("UPDATE 输入为空，跳过");
            return DataPacket.empty();
        }

        String dbType = dsConfig.getDbType();
        int affectedTotal = 0;
        try {
            DataSource ds = dynamicDsManager.getOrCreate(dsConfig);
            try (Connection conn = ds.getConnection()) {
                for (Map<String, Object> inputRow : rows) {
                    Map<String, Object> row = inputRow != null ? inputRow : new HashMap<String, Object>();
                    StringBuilder sql = new StringBuilder("UPDATE ").append(SqlDialect.quoteIdent(tableName, dbType)).append(" SET ");
                    List<Object> params = new ArrayList<>();
                    List<String> setClauses = new ArrayList<>();

                    for (Map<String, String> mapping : fieldMappings) {
                        String field = mappingStr(mapping, "field", "");
                        if (field.isEmpty()) {
                            continue;
                        }
                        Object bound = resolveMappingBound(mapping, row, context);
                        setClauses.add(SqlDialect.quoteIdent(field, dbType) + " = ?");
                        params.add(bound);
                    }
                    if (setClauses.isEmpty()) {
                        continue;
                    }
                    sql.append(String.join(", ", setClauses));
                    sql.append(" WHERE ");
                    List<String> whereClauses = new ArrayList<>();
                    for (Map<String, String> cond : whereConditions) {
                        String field = mappingStr(cond, "field", "");
                        String operator = mappingStr(cond, "operator", "=");
                        String value = mappingStr(cond, "value", "");
                        if (field.isEmpty()) {
                            continue;
                        }
                        if ("IN".equalsIgnoreCase(operator)) {
                            appendInClause(whereClauses, params, field, value, row, dbType);
                        } else if ("IS NULL".equalsIgnoreCase(operator) || "IS NOT NULL".equalsIgnoreCase(operator)) {
                            whereClauses.add(SqlDialect.quoteIdent(field, dbType) + " " + operator.toUpperCase(Locale.ROOT));
                        } else {
                            whereClauses.add(SqlDialect.quoteIdent(field, dbType) + " "
                                    + SqlDialect.compareOperator(operator) + " ?");
                            params.add(resolveInputValue(row, field, value));
                        }
                    }
                    if (whereClauses.isEmpty()) {
                        return DataPacket.error("CONFIG_ERROR", "未配置WHERE条件（安全检查）");
                    }
                    sql.append(String.join(" AND ", whereClauses));
                    context.info("执行UPDATE: " + sql);
                    try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
                        for (int i = 0; i < params.size(); i++) {
                            pstmt.setObject(i + 1, params.get(i));
                        }
                        affectedTotal += pstmt.executeUpdate();
                    }
                }
            }
            context.info("UPDATE影响行数: " + affectedTotal);
            Map<String, Object> resultRow = new LinkedHashMap<>();
            resultRow.put("affectedRows", affectedTotal);
            return passThroughRows(rows, resultRow, getStringConfig(config, "returnType", "INSERTED_ROW"));
        } catch (Exception e) {
            log.error("UPDATE执行失败", e);
            return DataPacket.error("UPDATE_ERROR", "UPDATE执行失败: " + e.getMessage());
        }
    }

    /**
     * 数据库删除 - 构建 DELETE WHERE SQL（安全检查：必须有WHERE）
     */
    @SuppressWarnings("unchecked")
    private DataPacket executeDbDelete(DsConfig dsConfig, DataPacket input, Map<String, Object> config, ExecutionContext context) {
        String tableName = getStringConfig(config, "tableName", "");
        if (tableName.isEmpty()) {
            tableName = getStringConfig(config, "tableName3", "");
        }
        if (tableName.isEmpty()) {
            return DataPacket.error("CONFIG_ERROR", "未配置目标表名");
        }

        List<Map<String, String>> whereConditions = (List<Map<String, String>>) config.get("whereConditions");
        if (whereConditions == null || whereConditions.isEmpty()) {
            return DataPacket.error("SAFETY_ERROR", "DELETE操作必须配置WHERE条件（安全检查）");
        }

        String dbType = dsConfig.getDbType();
        Map<String, Object> inputRow = input.getFirstRow() != null ? input.getFirstRow() : new HashMap<>();

        StringBuilder sql = new StringBuilder("DELETE FROM ").append(SqlDialect.quoteIdent(tableName, dbType)).append(" WHERE ");
        List<Object> params = new ArrayList<>();
        List<String> whereClauses = new ArrayList<>();

        for (Map<String, String> cond : whereConditions) {
            String field = mappingStr(cond, "field", "");
            String operator = mappingStr(cond, "operator", "=");
            String value = mappingStr(cond, "value", "");

            if (field.isEmpty()) continue;

            if ("IN".equalsIgnoreCase(operator)) {
                appendInClause(whereClauses, params, field, value, inputRow, dbType);
            } else if ("IS NULL".equalsIgnoreCase(operator) || "IS NOT NULL".equalsIgnoreCase(operator)) {
                whereClauses.add(SqlDialect.quoteIdent(field, dbType) + " " + operator.toUpperCase(Locale.ROOT));
            } else {
                whereClauses.add(SqlDialect.quoteIdent(field, dbType) + " "
                        + SqlDialect.compareOperator(operator) + " ?");
                params.add(resolveInputValue(inputRow, field, value));
            }
        }

        if (whereClauses.isEmpty()) {
            return DataPacket.error("SAFETY_ERROR", "DELETE操作必须包含有效的WHERE条件");
        }

        sql.append(String.join(" AND ", whereClauses));
        context.info("执行DELETE: " + sql.toString());

        try {
            DataSource ds = dynamicDsManager.getOrCreate(dsConfig);
            try (Connection conn = ds.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

                for (int i = 0; i < params.size(); i++) {
                    pstmt.setObject(i + 1, params.get(i));
                }

                int affected = pstmt.executeUpdate();
                context.info("DELETE影响行数: " + affected);

                Map<String, Object> resultRow = new LinkedHashMap<>();
                resultRow.put("affectedRows", affected);
                List<Map<String, Object>> rows = input.getRows() != null ? input.getRows() : new ArrayList<Map<String, Object>>();
                return passThroughRows(rows, resultRow, getStringConfig(config, "returnType", "INSERTED_ROW"));
            }
        } catch (Exception e) {
            log.error("DELETE执行失败", e);
            return DataPacket.error("DELETE_ERROR", "DELETE执行失败: " + e.getMessage());
        }
    }

    // ==================== API操作 ====================

    @SuppressWarnings("unchecked")
    private DataPacket executeApiOperation(DataPacket input, Map<String, Object> config, ExecutionContext context) {
        String operationType = getStringConfig(config, "operationType", "API_CALL");

        if ("API_CALL".equals(operationType)) {
            return executeApiCall(input, config, context);
        }
        return DataPacket.error("UNKNOWN_OP", "未知API操作: " + operationType);
    }

    /**
     * API调用 - 发送HTTP请求
     */
    @SuppressWarnings("unchecked")
    private DataPacket executeApiCall(DataPacket input, Map<String, Object> config, ExecutionContext context) {
        String apiUrl = getStringConfig(config, "apiUrl", "");
        String apiMethod = getStringConfig(config, "apiMethod", "GET");
        String apiHeaders = getStringConfig(config, "apiHeaders", "");
        String apiBody = getStringConfig(config, "apiBody", "");
        int timeout = getIntConfig(config, "timeout", 30);

        if (apiUrl.isEmpty()) {
            return DataPacket.error("CONFIG_ERROR", "未配置接口URL");
        }

        // 替换URL和Body中的 ${param} 变量
        Map<String, Object> inputRow = input.getFirstRow() != null ? input.getFirstRow() : new HashMap<>();
        String finalUrl = replaceParams(apiUrl, inputRow, input);
        String finalBody = replaceParams(apiBody, inputRow, input);

        context.info("API调用: " + apiMethod + " " + finalUrl
                + (finalBody != null && !finalBody.isEmpty() ? ", body=" + truncateLog(finalBody, 400) : ""));

        try {
            URL url = new URL(finalUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(apiMethod.toUpperCase());
            conn.setConnectTimeout(timeout * 1000);
            conn.setReadTimeout(timeout * 1000);
            conn.setRequestProperty("Accept", "application/json");

            // 设置请求头
            if (!apiHeaders.isEmpty()) {
                try {
                    Map<String, String> headersMap = objectMapper.readValue(apiHeaders,
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                    for (Map.Entry<String, String> entry : headersMap.entrySet()) {
                        String headerValue = replaceParams(entry.getValue(), inputRow, input);
                        conn.setRequestProperty(entry.getKey(), headerValue);
                    }
                } catch (Exception e) {
                    // 尝试作为简单JSON解析
                    conn.setRequestProperty("Content-Type", "application/json");
                }
            }

            // 设置请求体
            if (("POST".equalsIgnoreCase(apiMethod) || "PUT".equalsIgnoreCase(apiMethod)
                    || "PATCH".equalsIgnoreCase(apiMethod)) && !finalBody.isEmpty()) {
                conn.setDoOutput(true);
                byte[] bodyBytes = finalBody.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bodyBytes);
                }
            }

            // 读取响应
            int responseCode = conn.getResponseCode();
            StringBuilder responseBody = new StringBuilder();
            try (java.io.InputStream is = responseCode >= 200 && responseCode < 300
                    ? conn.getInputStream() : conn.getErrorStream()) {
                if (is != null) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(is, StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBody.append(line);
                    }
                }
            }

            context.info("API响应: code=" + responseCode + ", body长度=" + responseBody.length()
                    + (responseBody.length() > 0 ? ", body=" + truncateLog(responseBody.toString(), 400) : ""));
            if (responseCode < 200 || responseCode >= 300) {
                context.warn("API调用返回非成功状态: HTTP " + responseCode);
            }

            // 解析响应为DataPacket
            Map<String, Object> resultRow = new LinkedHashMap<>();
            resultRow.put("statusCode", responseCode);
            resultRow.put("responseBody", responseBody.toString());

            // 尝试解析JSON响应
            if (responseBody.length() > 0) {
                try {
                    Object parsed = objectMapper.readValue(responseBody.toString(), Object.class);
                    if (parsed instanceof List) {
                        List<Map<String, Object>> rows = (List<Map<String, Object>>) parsed;
                        for (Map<String, Object> row : rows) {
                            row.put("_statusCode", responseCode);
                        }
                        return DataPacket.ofList(rows);
                    } else if (parsed instanceof Map) {
                        Map<String, Object> parsedMap = (Map<String, Object>) parsed;
                        parsedMap.put("statusCode", responseCode);
                        return DataPacket.of(parsedMap);
                    }
                } catch (Exception e) {
                    // 非JSON响应，返回原始文本
                }
            }

            return DataPacket.of(resultRow);

        } catch (Exception e) {
            log.error("API调用失败", e);
            context.error("API调用失败: " + e.getMessage());
            return DataPacket.error("API_ERROR", "API调用失败: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> mergeSqlParams(DataPacket input) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (input != null && input.getVariables() != null) {
            merged.putAll(input.getVariables());
        }
        if (input != null && input.getFirstRow() != null) {
            merged.putAll(input.getFirstRow());
        }
        return merged;
    }

    /**
     * 替换 URL/Header/Body 中的 ${param}，不做 SQL 引号。
     */
    private String replaceParams(String template, DataPacket input) {
        return replaceParams(template, input.getFirstRow() != null ? input.getFirstRow() : new HashMap<>(), input);
    }

    private String replaceParams(String template, Map<String, Object> inputRow, DataPacket input) {
        if (template == null || template.isEmpty()) return template;
        String result = template;
        if (input != null && input.getVariables() != null) {
            for (Map.Entry<String, Object> entry : input.getVariables().entrySet()) {
                result = result.replace("${" + entry.getKey() + "}",
                        entry.getValue() != null ? String.valueOf(entry.getValue()) : "");
            }
        }
        if (inputRow != null) {
            for (Map.Entry<String, Object> entry : inputRow.entrySet()) {
                result = result.replace("${" + entry.getKey() + "}",
                        entry.getValue() != null ? String.valueOf(entry.getValue()) : "");
            }
        }
        return result;
    }

    private Object resolveMappingBound(Map<String, String> mapping, Map<String, Object> inputRow,
            ExecutionContext context) {
        String field = mappingStr(mapping, "field", "");
        String valueSource = mappingStr(mapping, "valueSource", "INPUT_PARAM");
        String value = mappingStr(mapping, "value", "");
        String transform = mappingStr(mapping, "transform", "");
        Object bound;
        if ("AUTO_INCREMENT".equalsIgnoreCase(transform) || isSeqAuto(valueSource, value)) {
            bound = applyValueTransform(null, "AUTO_INCREMENT", field, context);
            transform = "";
        } else if ("AUTO".equalsIgnoreCase(valueSource)) {
            bound = resolveAutoBound(value, field, context);
        } else if ("FIXED_VALUE".equalsIgnoreCase(valueSource)) {
            bound = value;
        } else {
            bound = resolveInputValue(inputRow, field, value);
        }
        return applyValueTransform(bound, transform, field, context);
    }

    private static boolean isSeqAuto(String valueSource, String value) {
        if (!"AUTO".equalsIgnoreCase(valueSource)) {
            return false;
        }
        String u = normalizeAutoKey(value);
        return "SEQ".equals(u) || "AUTO".equals(u) || "AUTO_INCREMENT".equals(u) || "DEFAULT".equals(u);
    }

    private Object resolveAutoBound(String value, String field, ExecutionContext context) {
        String u = normalizeAutoKey(value);
        if (u.isEmpty() || "NOW".equals(u) || "CURRENT_TIMESTAMP".equals(u)
                || "SYSDATE".equals(u) || "GETDATE".equals(u) || "LOCALTIMESTAMP".equals(u)) {
            return new Timestamp(System.currentTimeMillis());
        }
        if ("UUID".equals(u) || "GUID".equals(u) || "NEWID".equals(u)) {
            return java.util.UUID.randomUUID().toString();
        }
        if ("SEQ".equals(u) || "AUTO".equals(u) || "AUTO_INCREMENT".equals(u) || "DEFAULT".equals(u)) {
            return applyValueTransform(null, "AUTO_INCREMENT", field, context);
        }
        if (context != null) {
            context.warn("未知自动值 [" + value + "]，已按当前时间写入，未拼入 SQL");
        }
        return new Timestamp(System.currentTimeMillis());
    }

    private static String normalizeAutoKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT).replace("()", "").replace(" ", "_");
    }

    private void appendInClause(List<String> whereClauses, List<Object> params, String field,
            String rawValue, Map<String, Object> inputRow, String dbType) {
        Object resolved = resolveInputValue(inputRow, field, rawValue);
        String text = resolved != null ? String.valueOf(resolved) : (rawValue != null ? rawValue : "");
        List<String> placeholders = new ArrayList<>();
        for (String part : text.split("[,;，]")) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            placeholders.add("?");
            params.add(item);
        }
        if (placeholders.isEmpty()) {
            whereClauses.add("1=0");
            return;
        }
        whereClauses.add(SqlDialect.quoteIdent(field, dbType) + " IN (" + String.join(", ", placeholders) + ")");
    }

    // ==================== 配置读取辅助方法 ====================

    private String getStringConfig(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private Long getLongConfig(Map<String, Object> config, String key, Long defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try { return Long.parseLong((String) value); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    private int getIntConfig(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    private boolean getBooleanConfig(Map<String, Object> config, String key, boolean defaultValue) {
        Object value = config.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) {
            return "true".equalsIgnoreCase((String) value) || "1".equals(value);
        }
        return defaultValue;
    }

    private static String truncateLog(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "...(共" + s.length() + "字)" : s;
    }

    @Override
    public String validateConfig(Map<String, Object> config) {
        Long dsId = getLongConfig(config, "dsId", 0L);
        if (dsId == 0) {
            return "请选择数据源";
        }
        String operationType = getStringConfig(config, "operationType", "");
        if ("DB_QUERY".equals(operationType)) {
            if (getStringConfig(config, "sql", "").isEmpty()) {
                return "请配置SQL语句";
            }
        }
        if ("DB_INSERT".equals(operationType) || "DB_UPDATE".equals(operationType)) {
            if (getStringConfig(config, "tableName", "").isEmpty()) {
                return "请选择目标表";
            }
        }
        return null;
    }
}
