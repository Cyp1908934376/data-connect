package com.dataconnect.service;

import com.dataconnect.entity.DsConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DynamicDsManager {

    private static final Logger log = LoggerFactory.getLogger(DynamicDsManager.class);
    private final Map<Long, HikariDataSource> dataSourceMap = new ConcurrentHashMap<>();

    public DataSource getOrCreate(DsConfig config) {
        HikariDataSource existing = dataSourceMap.get(config.getId());
        if (existing != null && !existing.isClosed()) {
            log.debug("复用已存在的连接池, dsId={}, name={}", config.getId(), config.getName());
            return existing;
        }
        log.info("创建新的连接池, dsId={}, name={}, dbType={}", config.getId(), config.getName(), config.getDbType());
        return dataSourceMap.computeIfAbsent(config.getId(), id -> createHikariDataSource(config));
    }

    public void refresh(DsConfig config) {
        log.info("刷新连接池, dsId={}, name={}", config.getId(), config.getName());
        close(config.getId());
        HikariDataSource ds = createHikariDataSource(config);
        if (ds != null) {
            dataSourceMap.put(config.getId(), ds);
            log.info("连接池已刷新, dsId={}", config.getId());
        }
    }

    public void close(Long dsId) {
        log.info("关闭连接池, dsId={}", dsId);
        HikariDataSource ds = dataSourceMap.remove(dsId);
        if (ds != null && !ds.isClosed()) {
            ds.close();
            log.info("连接池已关闭, dsId={}", dsId);
        }
    }

    public DataSource get(Long dsId) {
        return dataSourceMap.get(dsId);
    }

    @PreDestroy
    public void closeAll() {
        log.info("应用关闭, 正在释放所有连接池, count={}", dataSourceMap.size());
        dataSourceMap.forEach((id, ds) -> {
            if (!ds.isClosed()) {
                ds.close();
                log.info("连接池已关闭, dsId={}", id);
            }
        });
        dataSourceMap.clear();
        log.info("所有连接池已释放");
    }

    private HikariDataSource createHikariDataSource(DsConfig config) {
        try {
            HikariConfig hikariConfig = new HikariConfig();
            String jdbcUrl = buildJdbcUrl(config);
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(config.getUsername());
            hikariConfig.setPassword(config.getPassword());
            hikariConfig.setDriverClassName(getDriverClass(config.getDbType()));
            hikariConfig.setMaximumPoolSize(config.getMaxPoolSize() != null ? config.getMaxPoolSize() : 10);
            hikariConfig.setMinimumIdle(config.getMinIdle() != null ? config.getMinIdle() : 2);
            hikariConfig.setConnectionTimeout((config.getConnTimeout() != null ? config.getConnTimeout() : 30) * 1000L);

            Properties props = new Properties();
            if (config.getCharset() != null && !config.getCharset().isEmpty()) {
                props.setProperty("characterEncoding", config.getCharset());
            }
            if (config.getJdbcParams() != null && !config.getJdbcParams().isEmpty()) {
                String[] pairs = config.getJdbcParams().split("&");
                for (String pair : pairs) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) props.setProperty(kv[0], kv[1]);
                }
            }
            hikariConfig.setDataSourceProperties(props);

            HikariDataSource ds = new HikariDataSource(hikariConfig);
            log.info("连接池创建成功, dsId={}, name={}, url={}", config.getId(), config.getName(), jdbcUrl);
            return ds;
        } catch (Exception e) {
            log.error("Failed to create datasource for: {}", config.getName(), e);
            return null;
        }
    }

    public boolean testConnection(DsConfig config) {
        String jdbcUrl = buildJdbcUrl(config);
        String driverClass = getDriverClass(config.getDbType());
        log.info("测试数据库连接, name={}, dbType={}, host={}:{}", config.getName(), config.getDbType(), config.getHost(), config.getPort());
        try {
            Class.forName(driverClass);
            try (Connection conn = DriverManager.getConnection(jdbcUrl, config.getUsername(), config.getPassword())) {
                boolean valid = conn.isValid(10);
                log.info("连接测试{}, name={}", valid ? "成功" : "失败", config.getName());
                return valid;
            }
        } catch (Exception e) {
            log.warn("Connection test failed for {}: {}", config.getName(), e.getMessage());
            return false;
        }
    }

    public String buildJdbcUrl(DsConfig config) {
        String dbType = config.getDbType();
        String host = config.getHost();
        Integer port = config.getPort();
        String dbName = config.getDbName();
        String charset = config.getCharset() != null ? config.getCharset() : "UTF-8";

        if ("MySQL".equalsIgnoreCase(dbType) || "MariaDB".equalsIgnoreCase(dbType)
                || "TiDB".equalsIgnoreCase(dbType) || "OceanBase".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=%s&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                    host, port != null ? port : 3306, dbName, charset);
        } else if ("PostgreSQL".equalsIgnoreCase(dbType) || "Greenplum".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:postgresql://%s:%d/%s",
                    host, port != null ? port : 5432, dbName);
        } else if ("Oracle".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:oracle:thin:@%s:%d:%s",
                    host, port != null ? port : 1521, dbName);
        } else if ("SQL Server".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:sqlserver://%s:%d;databaseName=%s",
                    host, port != null ? port : 1433, dbName);
        } else if ("SQLite".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:sqlite:%s", dbName);
        } else if ("H2".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:h2:%s;MODE=MySQL", dbName);
        } else if ("ClickHouse".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:clickhouse://%s:%d/%s",
                    host, port != null ? port : 8123, dbName);
        } else if ("DB2".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:db2://%s:%d/%s",
                    host, port != null ? port : 50000, dbName);
        } else if ("Trino".equalsIgnoreCase(dbType) || "Presto".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:trino://%s:%d/%s/%s",
                    host, port != null ? port : 8080, "catalog", dbName);
        } else if ("Derby".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:derby:%s;create=true", dbName);
        } else if ("HSQLDB".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:hsqldb:hsql://%s:%d/%s",
                    host, port != null ? port : 9001, dbName);
        } else if ("TDengine".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:TAOS://%s:%d/%s",
                    host, port != null ? port : 6030, dbName);
        } else if ("DuckDB".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:duckdb:%s", dbName);
        } else if ("Firebird".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:firebirdsql://%s:%d/%s",
                    host, port != null ? port : 3050, dbName);
        } else if ("Drill".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:drill:drillbit=%s:%d",
                    host, port != null ? port : 31010);
        } else if ("Neo4j".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:neo4j:bolt://%s:%d",
                    host, port != null ? port : 7687);
        } else if ("SAP_HANA".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:sap://%s:%d/?databaseName=%s",
                    host, port != null ? port : 39015, dbName);
        } else if ("Snowflake".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:snowflake://%s.snowflakecomputing.com/?db=%s&schema=PUBLIC",
                    host, dbName);
        }
        // 兜底：直接用 jdbcParams 或拼接
        return String.format("jdbc:%s://%s:%d/%s", dbType.toLowerCase(), host, port, dbName);
    }

    public String getDriverClass(String dbType) {
        if ("MySQL".equalsIgnoreCase(dbType) || "TiDB".equalsIgnoreCase(dbType) || "OceanBase".equalsIgnoreCase(dbType)) {
            return "com.mysql.cj.jdbc.Driver";
        } else if ("MariaDB".equalsIgnoreCase(dbType)) {
            return "org.mariadb.jdbc.Driver";
        } else if ("PostgreSQL".equalsIgnoreCase(dbType) || "Greenplum".equalsIgnoreCase(dbType)) {
            return "org.postgresql.Driver";
        } else if ("Oracle".equalsIgnoreCase(dbType)) {
            return "oracle.jdbc.OracleDriver";
        } else if ("SQL Server".equalsIgnoreCase(dbType)) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        } else if ("SQLite".equalsIgnoreCase(dbType)) {
            return "org.sqlite.JDBC";
        } else if ("H2".equalsIgnoreCase(dbType)) {
            return "org.h2.Driver";
        } else if ("ClickHouse".equalsIgnoreCase(dbType)) {
            return "com.clickhouse.jdbc.ClickHouseDriver";
        } else if ("DB2".equalsIgnoreCase(dbType)) {
            return "com.ibm.db2.jcc.DB2Driver";
        } else if ("Trino".equalsIgnoreCase(dbType)) {
            return "io.trino.jdbc.TrinoDriver";
        } else if ("Presto".equalsIgnoreCase(dbType)) {
            return "com.facebook.presto.jdbc.PrestoDriver";
        } else if ("Derby".equalsIgnoreCase(dbType)) {
            return "org.apache.derby.jdbc.EmbeddedDriver";
        } else if ("HSQLDB".equalsIgnoreCase(dbType)) {
            return "org.hsqldb.jdbc.JDBCDriver";
        } else if ("TDengine".equalsIgnoreCase(dbType)) {
            return "com.taosdata.jdbc.TSDBDriver";
        } else if ("DuckDB".equalsIgnoreCase(dbType)) {
            return "org.duckdb.DuckDBDriver";
        } else if ("Firebird".equalsIgnoreCase(dbType)) {
            return "org.firebirdsql.jdbc.FBDriver";
        } else if ("Drill".equalsIgnoreCase(dbType)) {
            return "org.apache.drill.jdbc.Driver";
        } else if ("Neo4j".equalsIgnoreCase(dbType)) {
            return "org.neo4j.jdbc.Driver";
        } else if ("SAP_HANA".equalsIgnoreCase(dbType)) {
            return "com.sap.db.jdbc.Driver";
        } else if ("Snowflake".equalsIgnoreCase(dbType)) {
            return "net.snowflake.client.jdbc.SnowflakeDriver";
        }
        return "";
    }
}
