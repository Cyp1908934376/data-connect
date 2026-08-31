package com.dataconnect.component.impl;

import com.dataconnect.component.ComponentExecutor;
import com.dataconnect.component.DataPacket;
import com.dataconnect.component.ExecutionContext;
import com.dataconnect.entity.DsConfig;
import com.dataconnect.service.DataSourceService;
import com.dataconnect.service.DynamicDsManager;
import com.dataconnect.util.SqlDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * 数据库写入组件执行器
 * 将数据写入数据库
 */
@Component
public class DbWriteExecutor implements ComponentExecutor {

    private static final Logger log = LoggerFactory.getLogger(DbWriteExecutor.class);

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private DynamicDsManager dynamicDsManager;

    @Override
    public String getType() {
        return "DB_WRITE";
    }

    @Override
    public DataPacket execute(DataPacket input, Map<String, Object> config, ExecutionContext context) {
        Long dsId = getLongConfig(config, "dsId", 0L);
        String tableName = getStringConfig(config, "tableName", "");
        String writeMode = getStringConfig(config, "writeMode", "INSERT");  // INSERT/UPSERT/UPDATE
        boolean autoCreateTable = getBooleanConfig(config, "autoCreateTable", true);

        if (dsId == 0) {
            context.error("未配置数据源ID");
            return DataPacket.error("CONFIG_ERROR", "未配置数据源ID");
        }

        if (input == null || input.isEmpty()) {
            context.debug("数据库写入: 输入为空");
            return DataPacket.empty();
        }

        context.info("执行数据库写入, dsId=" + dsId + ", table=" + tableName + ", mode=" + writeMode);

        try {
            // 获取数据源配置
            DsConfig dsConfig = dataSourceService.getById(dsId)
                    .orElseThrow(() -> new RuntimeException("数据源不存在: " + dsId));

            // 如果没有指定表名，使用默认表
            if (tableName.isEmpty()) {
                tableName = "data_sync_result";
            }

            // 执行写入
            int writeCount = writeToDatabase(dsConfig, tableName, input.getRows(), writeMode, autoCreateTable, context);

            context.info("数据库写入完成, 写入 " + writeCount + " 条记录");

            // 返回写入结果
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("writeCount", writeCount);
            result.put("tableName", tableName);
            return DataPacket.of(result);

        } catch (Exception e) {
            log.error("数据库写入失败", e);
            context.error("数据库写入失败: " + e.getMessage());
            return DataPacket.error("DB_WRITE_ERROR", "数据库写入失败: " + e.getMessage());
        }
    }

    @Override
    public String validateConfig(Map<String, Object> config) {
        Long dsId = getLongConfig(config, "dsId", 0L);
        if (dsId == 0) {
            return "请配置数据源ID";
        }
        return null;
    }

    @Override
    public String[] getInputPorts() {
        return new String[]{"data"};
    }

    @Override
    public String[] getOutputPorts() {
        return new String[]{"result"};
    }

    /**
     * 写入数据库
     */
    private int writeToDatabase(DsConfig dsConfig, String tableName, List<Map<String, Object>> rows,
                                 String writeMode, boolean autoCreateTable, ExecutionContext context) {
        DataSource ds = dynamicDsManager.getOrCreate(dsConfig);
        if (ds == null) {
            throw new RuntimeException("无法创建数据源连接");
        }

        int count = 0;
        try (Connection conn = ds.getConnection()) {
            // 自动建表
            if (autoCreateTable && !rows.isEmpty()) {
                if (!tableExists(conn, tableName)) {
                    String ddl = buildCreateTableDDL(tableName, rows.get(0), dsConfig.getDbType());
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(ddl);
                        context.info("自动建表: " + tableName);
                    }
                }
            }

            // 写入数据
            for (Map<String, Object> row : rows) {
                try {
                    switch (writeMode) {
                        case "UPSERT":
                            executeUpsert(conn, tableName, row, dsConfig.getDbType());
                            break;
                        case "UPDATE":
                            executeUpdate(conn, tableName, row, dsConfig.getDbType());
                            break;
                        default:  // INSERT
                            executeInsert(conn, tableName, row, dsConfig.getDbType());
                            break;
                    }
                    count++;
                } catch (SQLException e) {
                    context.warn("写入行失败: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("数据库操作失败: " + e.getMessage(), e);
        }

        return count;
    }

    /**
     * 执行INSERT
     */
    private void executeInsert(Connection conn, String tableName, Map<String, Object> row, String dbType) throws SQLException {
        StringBuilder sql = new StringBuilder("INSERT INTO " + SqlDialect.quoteIdent(tableName, dbType) + " (");
        StringBuilder values = new StringBuilder(" VALUES (");
        List<Object> params = new ArrayList<>();

        for (Map.Entry<String, Object> entry : row.entrySet()) {
            sql.append(SqlDialect.quoteIdent(entry.getKey(), dbType)).append(",");
            values.append("?,");
            params.add(entry.getValue());
        }

        sql.setLength(sql.length() - 1);
        values.setLength(values.length() - 1);
        sql.append(")").append(values).append(")");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ps.executeUpdate();
        }
    }

    /**
     * 执行UPSERT
     */
    private void executeUpsert(Connection conn, String tableName, Map<String, Object> row, String dbType) throws SQLException {
        try {
            executeInsert(conn, tableName, row, dbType);
        } catch (SQLException e) {
            if (e.getErrorCode() == 23505 || (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate"))) {
                executeUpdate(conn, tableName, row, dbType);
            } else {
                throw e;
            }
        }
    }

    /**
     * 执行UPDATE
     */
    private void executeUpdate(Connection conn, String tableName, Map<String, Object> row, String dbType) throws SQLException {
        StringBuilder sql = new StringBuilder("UPDATE " + SqlDialect.quoteIdent(tableName, dbType) + " SET ");
        List<Object> params = new ArrayList<>();
        Object idValue = null;

        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if ("id".equalsIgnoreCase(entry.getKey())) {
                idValue = entry.getValue();
            } else {
                sql.append(SqlDialect.quoteIdent(entry.getKey(), dbType)).append("=?,");
                params.add(entry.getValue());
            }
        }

        if (idValue == null) {
            throw new SQLException("UPDATE需要id字段");
        }

        sql.setLength(sql.length() - 1);
        sql.append(" WHERE ").append(SqlDialect.quoteIdent("id", dbType)).append("=?");
        params.add(idValue);

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ps.executeUpdate();
        }
    }

    /**
     * 检查表是否存在
     */
    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    /**
     * 构建建表DDL
     */
    private String buildCreateTableDDL(String tableName, Map<String, Object> firstRow, String dbType) {
        StringBuilder ddl = new StringBuilder("CREATE TABLE " + SqlDialect.quoteIdent(tableName, dbType) + " (");
        ddl.append(SqlDialect.idColumnDdl(dbType));

        for (Map.Entry<String, Object> entry : firstRow.entrySet()) {
            if ("id".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            ddl.append(", ").append(SqlDialect.quoteIdent(entry.getKey(), dbType)).append(" ");
            ddl.append(SqlDialect.columnType(entry.getValue(), false, dbType));
        }

        ddl.append(")");
        ddl.append(SqlDialect.createTableSuffix(dbType));
        return ddl.toString();
    }

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

    private boolean getBooleanConfig(Map<String, Object> config, String key, boolean defaultValue) {
        Object value = config.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return defaultValue;
    }
}
