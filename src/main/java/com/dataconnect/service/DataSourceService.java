package com.dataconnect.service;

import com.dataconnect.entity.DsConfig;
import com.dataconnect.repository.DsConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class DataSourceService {

    private static final Logger log = LoggerFactory.getLogger(DataSourceService.class);

    @Autowired
    private DsConfigRepository dsConfigRepository;

    @Autowired
    private DynamicDsManager dynamicDsManager;

    public List<DsConfig> listAll() {
        return dsConfigRepository.findAll();
    }

    public List<DsConfig> listByType(String sourceType) {
        return dsConfigRepository.findBySourceType(sourceType);
    }

    public Map<Long, String> getIdNameMap() {
        Map<Long, String> map = new LinkedHashMap<>();
        for (DsConfig config : dsConfigRepository.findAll()) {
            map.put(config.getId(), config.getName());
        }
        return map;
    }

    public Optional<DsConfig> getById(Long id) {
        return dsConfigRepository.findById(id);
    }

    public DsConfig save(DsConfig config) {
        log.info("保存数据源配置, name={}, type={}", config.getName(), config.getSourceType());
        DsConfig saved = dsConfigRepository.save(config);
        log.info("数据源配置已保存, id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    public DsConfig update(Long id, DsConfig updated) {
        DsConfig existing = dsConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("数据源不存在: " + id));
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        if ("DB".equals(existing.getSourceType())) {
            existing.setDbType(updated.getDbType());
            existing.setHost(updated.getHost());
            existing.setPort(updated.getPort());
            existing.setDbName(updated.getDbName());
            existing.setTableName(updated.getTableName());
            existing.setUsername(updated.getUsername());
            if (updated.getPassword() != null && !updated.getPassword().isEmpty()) {
                existing.setPassword(updated.getPassword());
            }
            existing.setCharset(updated.getCharset());
            existing.setJdbcParams(updated.getJdbcParams());
            existing.setMaxPoolSize(updated.getMaxPoolSize());
            existing.setMinIdle(updated.getMinIdle());
            existing.setConnTimeout(updated.getConnTimeout());
            existing.setInitSql(updated.getInitSql());
            existing.setTestQuery(updated.getTestQuery());
            existing.setSslEnabled(updated.getSslEnabled());
            existing.setSslCertPath(updated.getSslCertPath());
        } else {
            existing.setApiType(updated.getApiType());
            existing.setApiMethod(updated.getApiMethod());
            existing.setApiUrl(updated.getApiUrl());
            existing.setApiTimeout(updated.getApiTimeout());
            existing.setApiRetryTimes(updated.getApiRetryTimes());
            existing.setApiRetryInterval(updated.getApiRetryInterval());
            existing.setApiHeaders(updated.getApiHeaders());
            existing.setApiBody(updated.getApiBody());
            existing.setApiAuthType(updated.getApiAuthType());
            existing.setApiAuthConfig(updated.getApiAuthConfig());
            existing.setApiMode(updated.getApiMode());
            existing.setTemplateId(updated.getTemplateId());
            existing.setApiChainConfig(updated.getApiChainConfig());
        }
        DsConfig saved = dsConfigRepository.save(existing);
        log.info("数据源配置已更新, id={}, name={}", saved.getId(), saved.getName());
        if ("DB".equals(existing.getSourceType())) {
            dynamicDsManager.refresh(saved);
            log.info("已刷新数据源连接池, id={}", saved.getId());
        }
        return saved;
    }

    public void delete(Long id) {
        log.info("删除数据源配置, id={}", id);
        dynamicDsManager.close(id);
        dsConfigRepository.deleteById(id);
        log.info("数据源配置已删除, id={}", id);
    }

    public boolean testConnection(Long dsId) {
        DsConfig config = dsConfigRepository.findById(dsId).orElse(null);
        if (config == null || !"DB".equals(config.getSourceType())) return false;
        return dynamicDsManager.testConnection(config);
    }

    public boolean testConnection(DsConfig config) {
        return dynamicDsManager.testConnection(config);
    }

    public Map<String, Object> executeQuery(Long dsId, String sql) {
        Map<String, Object> result = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        DsConfig config = dsConfigRepository.findById(dsId).orElse(null);
        if (config == null) {
            log.warn("执行SQL失败: 数据源不存在, dsId={}", dsId);
            result.put("success", false);
            result.put("error", "数据源不存在");
            return result;
        }
        DataSource ds = dynamicDsManager.getOrCreate(config);
        if (ds == null) {
            log.warn("执行SQL失败: 无法创建连接, dsId={}", dsId);
            result.put("success", false);
            result.put("error", "无法创建数据源连接");
            return result;
        }
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            String upperSql = sql.trim().toUpperCase();
            if (upperSql.startsWith("SELECT") || upperSql.startsWith("SHOW")
                    || upperSql.startsWith("DESCRIBE") || upperSql.startsWith("DESC")
                    || upperSql.startsWith("EXPLAIN")) {
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    List<Map<String, Object>> rows = new ArrayList<>();
                    ResultSetMetaData meta = rs.getMetaData();
                    List<String> columns = new ArrayList<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        columns.add(meta.getColumnName(i));
                    }
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (String col : columns) {
                            row.put(col, rs.getObject(col));
                        }
                        rows.add(row);
                    }
                    result.put("columns", columns);
                    result.put("rows", rows);
                    result.put("rowCount", rows.size());
                }
            } else {
                int affected = stmt.executeUpdate(sql);
                result.put("affectedRows", affected);
            }
            result.put("success", true);
        } catch (Exception e) {
            log.error("执行SQL失败, dsId={}", dsId, e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        result.put("duration", System.currentTimeMillis() - start);
        return result;
    }

    public Map<String, Object> getTables(Long dsId) {
        Map<String, Object> result = new LinkedHashMap<>();
        DsConfig config = dsConfigRepository.findById(dsId).orElse(null);
        if (config == null) {
            log.warn("获取表列表失败: 数据源不存在, dsId={}", dsId);
            result.put("success", false);
            result.put("error", "数据源不存在");
            return result;
        }
        DataSource ds = dynamicDsManager.getOrCreate(config);
        if (ds == null) {
            log.warn("获取表列表失败: 无法创建连接, dsId={}", dsId);
            result.put("success", false);
            result.put("error", "无法创建数据源连接");
            return result;
        }
        try (Connection conn = ds.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            List<Map<String, Object>> tables = new ArrayList<>();
            try (ResultSet rs = meta.getTables(config.getDbName(), null, "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    Map<String, Object> table = new LinkedHashMap<>();
                    table.put("name", rs.getString("TABLE_NAME"));
                    table.put("type", rs.getString("TABLE_TYPE"));
                    table.put("remarks", rs.getString("REMARKS"));
                    tables.add(table);
                }
            }
            result.put("tables", tables);
            result.put("success", true);
        } catch (Exception e) {
            log.error("获取表列表失败, dsId={}", dsId, e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    public Map<String, Object> getColumns(Long dsId, String tableName) {
        Map<String, Object> result = new LinkedHashMap<>();
        DsConfig config = dsConfigRepository.findById(dsId).orElse(null);
        if (config == null) {
            log.warn("获取字段列表失败: 数据源不存在, dsId={}", dsId);
            result.put("success", false);
            result.put("error", "数据源不存在");
            return result;
        }
        DataSource ds = dynamicDsManager.getOrCreate(config);
        if (ds == null) {
            log.warn("获取字段列表失败: 无法创建连接, dsId={}", dsId);
            result.put("success", false);
            result.put("error", "无法创建数据源连接");
            return result;
        }
        try (Connection conn = ds.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            List<Map<String, Object>> columns = new ArrayList<>();
            try (ResultSet rs = meta.getColumns(config.getDbName(), null, tableName, "%")) {
                while (rs.next()) {
                    Map<String, Object> col = new LinkedHashMap<>();
                    col.put("name", rs.getString("COLUMN_NAME"));
                    col.put("type", rs.getString("TYPE_NAME"));
                    col.put("size", rs.getInt("COLUMN_SIZE"));
                    col.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    col.put("remarks", rs.getString("REMARKS"));
                    columns.add(col);
                }
            }
            result.put("columns", columns);
            result.put("success", true);
        } catch (Exception e) {
            log.error("获取字段列表失败, dsId={}, table={}", dsId, tableName, e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    public Map<String, Object> previewData(Long dsId, String tableName, int limit) {
        return executeQuery(dsId, "SELECT * FROM " + tableName + " LIMIT " + limit);
    }
}
