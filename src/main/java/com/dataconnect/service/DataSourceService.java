package com.dataconnect.service;

import com.dataconnect.entity.DsConfig;
import com.dataconnect.repository.DsConfigRepository;
import com.dataconnect.util.SqlDialect;
import com.dataconnect.util.SqlPageWrapper;
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
            existing.setTableNames(updated.getTableNames());
            existing.setCustomQuerySql(updated.getCustomQuerySql());
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

    public Map<String, Object> testConnectionWithMessage(DsConfig config) {
        return dynamicDsManager.testConnectionWithMessage(config);
    }

    public Map<String, Object> executeQuery(Long dsId, String sql) {
        return executeQuery(dsId, sql, 0);
    }

    public Map<String, Object> executeQuery(Long dsId, String sql, int maxRows) {
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
            if (maxRows > 0) {
                stmt.setMaxRows(maxRows);
            }
            String upperSql = sql.trim().toUpperCase();
            if (upperSql.startsWith("SELECT") || upperSql.startsWith("SHOW")
                    || upperSql.startsWith("DESCRIBE") || upperSql.startsWith("DESC")
                    || upperSql.startsWith("EXPLAIN")) {
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    List<Map<String, Object>> rows = new ArrayList<>();
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();
                    List<String> columns = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String label = meta.getColumnLabel(i);
                        if (label == null || label.isEmpty()) {
                            label = meta.getColumnName(i);
                        }
                        columns.add(label);
                    }
                    while (rs.next()) {
                        if (maxRows > 0 && rows.size() >= maxRows) {
                            break;
                        }
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            row.put(columns.get(i - 1), rs.getObject(i));
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

            // 解析配置的多表名
            Set<String> configuredTables = parseTableNames(config);
            boolean hasFilter = !configuredTables.isEmpty();

            String dbType = config.getDbType() != null ? config.getDbType().toUpperCase() : "";
            String catalog = null;
            String schemaPattern = null;

            if (dbType.contains("ORACLE")) {
                // Oracle: catalog=null, schema=用户名大写
                catalog = null;
                schemaPattern = config.getUsername() != null ? config.getUsername().toUpperCase() : null;
            } else if (dbType.contains("POSTGRESQL") || dbType.contains("PG")) {
                catalog = null;
                schemaPattern = "public";
            } else if (dbType.contains("SQLSERVER") || dbType.contains("MSSQL")) {
                catalog = config.getDbName();
                schemaPattern = "dbo";
            } else {
                catalog = config.getDbName();
            }

            try (ResultSet rs = meta.getTables(catalog, schemaPattern, "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    // 如果配置了多表名，则只返回配置中的表
                    if (hasFilter && !configuredTables.contains(tableName.toUpperCase())) {
                        continue;
                    }
                    Map<String, Object> table = new LinkedHashMap<>();
                    table.put("name", tableName);
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

    /**
     * 解析多表名配置（逗号分隔）
     * 同时支持 tableName 和 tableNames 字段，合并去重
     */
    private Set<String> parseTableNames(DsConfig config) {
        Set<String> result = new HashSet<>();
        // 兼容旧的 tableName 字段
        String tableName = config.getTableName();
        if (tableName != null && !tableName.trim().isEmpty()) {
            result.add(tableName.trim().toUpperCase());
        }
        // 新的 tableNames 字段（支持中英文逗号、换行分隔）
        String tableNames = config.getTableNames();
        if (tableNames != null && !tableNames.trim().isEmpty()) {
            for (String name : tableNames.replace("，", ",").replace("\n", ",").split(",")) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed.toUpperCase());
                }
            }
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

            String dbType = config.getDbType() != null ? config.getDbType().toUpperCase() : "";
            String catalog = null;
            String schemaPattern = null;
            if (dbType.contains("ORACLE")) {
                schemaPattern = config.getUsername() != null ? config.getUsername().toUpperCase() : null;
            } else if (dbType.contains("POSTGRESQL") || dbType.contains("PG")) {
                schemaPattern = "public";
            } else if (dbType.contains("SQLSERVER") || dbType.contains("MSSQL")) {
                catalog = config.getDbName();
                schemaPattern = "dbo";
            } else {
                catalog = config.getDbName();
            }

            try (ResultSet rs = meta.getColumns(catalog, schemaPattern, tableName, "%")) {
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
        DsConfig config = dsConfigRepository.findById(dsId).orElse(null);
        if (config == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("error", "数据源不存在");
            return result;
        }
        String sql = SqlPageWrapper.wrapFirstPage(
                "SELECT * FROM " + SqlDialect.quoteIdent(tableName, config.getDbType()),
                config.getDbType(), limit);
        return executeQuery(dsId, sql, limit);
    }

    /**
     * 测试/预览：给 SQL 加上首屏条数限制（SQL 已有分页则不改）。
     */
    public String applyFirstPageLimit(String sql, String dbType, int limit) {
        return SqlPageWrapper.wrapFirstPage(sql, dbType, limit);
    }

    /**
     * 取第一个启用的 MySQL 兼容数据源（MySQL / MariaDB / TiDB / OceanBase）。
     */
    public Optional<DsConfig> findFirstMysql() {
        for (DsConfig ds : dsConfigRepository.findAll()) {
            if (!"DB".equalsIgnoreCase(ds.getSourceType())) {
                continue;
            }
            if (ds.getEnabled() != null && ds.getEnabled() == 0) {
                continue;
            }
            String t = ds.getDbType() != null ? ds.getDbType().toLowerCase() : "";
            if (t.contains("mysql") || t.contains("mariadb") || t.contains("tidb") || t.contains("oceanbase")) {
                return Optional.of(ds);
            }
        }
        return Optional.empty();
    }
}
