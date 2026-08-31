package com.dataconnect.component.impl;

import com.dataconnect.component.ComponentExecutor;
import com.dataconnect.component.DataPacket;
import com.dataconnect.component.ExecutionContext;
import com.dataconnect.entity.DsConfig;
import com.dataconnect.service.DataSourceService;
import com.dataconnect.service.DynamicDsManager;
import com.dataconnect.util.SqlDialect;
import com.dataconnect.util.SqlPageWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * 数据库查询组件执行器
 * 执行SQL查询并返回结果
 */
@Component
public class DbQueryExecutor implements ComponentExecutor {

    private static final Logger log = LoggerFactory.getLogger(DbQueryExecutor.class);

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private DynamicDsManager dynamicDsManager;

    @Override
    public String getType() {
        return "DB_QUERY";
    }

    @Override
    public DataPacket execute(DataPacket input, Map<String, Object> config, ExecutionContext context) {
        Long dsId = getLongConfig(config, "dsId", 0L);
        String sql = getStringConfig(config, "sql", "");
        int limit = getIntConfig(config, "limit", 0);

        if (dsId == 0) {
            context.error("未配置数据源ID");
            return DataPacket.error("CONFIG_ERROR", "未配置数据源ID");
        }

        if (sql.isEmpty()) {
            context.error("未配置SQL语句");
            return DataPacket.error("CONFIG_ERROR", "未配置SQL语句");
        }

        context.info("执行数据库查询, dsId=" + dsId + ", sql=" + sql);

        try {
            // 获取数据源配置
            DsConfig dsConfig = dataSourceService.getById(dsId)
                    .orElseThrow(() -> new RuntimeException("数据源不存在: " + dsId));

            // 替换SQL中的变量
            String finalSql = SqlDialect.substitutePlaceholders(sql, mergeSqlParams(input));

            if (limit > 0) {
                finalSql = SqlPageWrapper.wrapFirstPage(finalSql, dsConfig.getDbType(), limit);
            }

            context.debug("执行SQL: " + finalSql);

            // 执行查询
            List<Map<String, Object>> rows = executeQuery(dsConfig, finalSql);
            context.info("查询返回 " + rows.size() + " 条记录");

            return DataPacket.ofList(rows);

        } catch (Exception e) {
            log.error("数据库查询失败", e);
            context.error("数据库查询失败: " + e.getMessage());
            return DataPacket.error("QUERY_ERROR", "数据库查询失败: " + e.getMessage());
        }
    }

    @Override
    public String validateConfig(Map<String, Object> config) {
        Long dsId = getLongConfig(config, "dsId", 0L);
        String sql = getStringConfig(config, "sql", "");

        if (dsId == 0) {
            return "请配置数据源ID";
        }
        if (sql.isEmpty()) {
            return "请配置SQL语句";
        }
        return null;
    }

    /**
     * 执行查询
     */
    private List<Map<String, Object>> executeQuery(DsConfig dsConfig, String sql) {
        List<Map<String, Object>> rows = new ArrayList<>();
        DataSource ds = dynamicDsManager.getOrCreate(dsConfig);

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                rows.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQL执行失败: " + e.getMessage(), e);
        }

        return rows;
    }

    @Override
    public String[] getInputPorts() {
        return new String[]{"params"};
    }

    @Override
    public String[] getOutputPorts() {
        return new String[]{"result"};
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

    private int getIntConfig(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    private Map<String, Object> mergeSqlParams(DataPacket input) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (input == null) {
            return merged;
        }
        if (input.getVariables() != null) {
            merged.putAll(input.getVariables());
        }
        if (input.getFirstRow() != null) {
            merged.putAll(input.getFirstRow());
        }
        return merged;
    }
}
