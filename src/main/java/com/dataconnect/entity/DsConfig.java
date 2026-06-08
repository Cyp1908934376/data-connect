package com.dataconnect.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ds_config")
public class DsConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;  // DB / API

    // === 数据库数据源字段 ===
    @Column(name = "db_type", length = 50)
    private String dbType;

    @Column(name = "host", length = 200)
    private String host;

    @Column(name = "port")
    private Integer port;

    @Column(name = "db_name", length = 200)
    private String dbName;

    @Column(name = "table_name", length = 200)
    private String tableName;

    @Column(name = "username", length = 200)
    private String username;

    @Column(name = "password", length = 500)
    private String password;

    @Column(name = "charset", length = 50)
    private String charset;

    @Column(name = "jdbc_params", length = 1000)
    private String jdbcParams;

    @Column(name = "max_pool_size")
    private Integer maxPoolSize;

    @Column(name = "min_idle")
    private Integer minIdle;

    @Column(name = "conn_timeout")
    private Integer connTimeout;

    @Column(name = "init_sql", length = 2000)
    private String initSql;

    @Column(name = "test_query", length = 200)
    private String testQuery;

    @Column(name = "ssl_enabled")
    private Integer sslEnabled;

    @Column(name = "ssl_cert_path", length = 500)
    private String sslCertPath;

    // === 接口数据源字段 ===
    @Column(name = "api_type", length = 20)
    private String apiType;

    @Column(name = "api_method", length = 10)
    private String apiMethod;

    @Column(name = "api_url", length = 1000)
    private String apiUrl;

    @Column(name = "api_timeout")
    private Integer apiTimeout;

    @Column(name = "api_retry_times")
    private Integer apiRetryTimes;

    @Column(name = "api_retry_interval")
    private Integer apiRetryInterval;

    @Column(name = "api_headers", columnDefinition = "TEXT")
    private String apiHeaders;

    @Column(name = "api_body", columnDefinition = "TEXT")
    private String apiBody;

    @Column(name = "api_auth_type", length = 50)
    private String apiAuthType;

    @Column(name = "api_auth_config", columnDefinition = "TEXT")
    private String apiAuthConfig;

    // API 模式: SINGLE=单接口, CHAIN=多接口链式调用, SCRIPT=复杂脚本编排
    @Column(name = "api_mode", length = 20)
    private String apiMode;

    // 关联模板: SCRIPT模式使用模板中的Groovy脚本，或用于响应转换
    @Column(name = "template_id")
    private Long templateId;

    // 多接口链配置 JSON，仅 CHAIN 模式使用
    @Column(name = "api_chain_config", columnDefinition = "TEXT")
    private String apiChainConfig;

    @Column(name = "enabled")
    private Integer enabled;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (enabled == null) enabled = 1;
        if (sslEnabled == null) sslEnabled = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getDbType() { return dbType; }
    public void setDbType(String dbType) { this.dbType = dbType; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public String getDbName() { return dbName; }
    public void setDbName(String dbName) { this.dbName = dbName; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getCharset() { return charset; }
    public void setCharset(String charset) { this.charset = charset; }
    public String getJdbcParams() { return jdbcParams; }
    public void setJdbcParams(String jdbcParams) { this.jdbcParams = jdbcParams; }
    public Integer getMaxPoolSize() { return maxPoolSize; }
    public void setMaxPoolSize(Integer maxPoolSize) { this.maxPoolSize = maxPoolSize; }
    public Integer getMinIdle() { return minIdle; }
    public void setMinIdle(Integer minIdle) { this.minIdle = minIdle; }
    public Integer getConnTimeout() { return connTimeout; }
    public void setConnTimeout(Integer connTimeout) { this.connTimeout = connTimeout; }
    public String getInitSql() { return initSql; }
    public void setInitSql(String initSql) { this.initSql = initSql; }
    public String getTestQuery() { return testQuery; }
    public void setTestQuery(String testQuery) { this.testQuery = testQuery; }
    public Integer getSslEnabled() { return sslEnabled; }
    public void setSslEnabled(Integer sslEnabled) { this.sslEnabled = sslEnabled; }
    public String getSslCertPath() { return sslCertPath; }
    public void setSslCertPath(String sslCertPath) { this.sslCertPath = sslCertPath; }
    public String getApiType() { return apiType; }
    public void setApiType(String apiType) { this.apiType = apiType; }
    public String getApiMethod() { return apiMethod; }
    public void setApiMethod(String apiMethod) { this.apiMethod = apiMethod; }
    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public Integer getApiTimeout() { return apiTimeout; }
    public void setApiTimeout(Integer apiTimeout) { this.apiTimeout = apiTimeout; }
    public Integer getApiRetryTimes() { return apiRetryTimes; }
    public void setApiRetryTimes(Integer apiRetryTimes) { this.apiRetryTimes = apiRetryTimes; }
    public Integer getApiRetryInterval() { return apiRetryInterval; }
    public void setApiRetryInterval(Integer apiRetryInterval) { this.apiRetryInterval = apiRetryInterval; }
    public String getApiHeaders() { return apiHeaders; }
    public void setApiHeaders(String apiHeaders) { this.apiHeaders = apiHeaders; }
    public String getApiBody() { return apiBody; }
    public void setApiBody(String apiBody) { this.apiBody = apiBody; }
    public String getApiAuthType() { return apiAuthType; }
    public void setApiAuthType(String apiAuthType) { this.apiAuthType = apiAuthType; }
    public String getApiAuthConfig() { return apiAuthConfig; }
    public void setApiAuthConfig(String apiAuthConfig) { this.apiAuthConfig = apiAuthConfig; }
    public String getApiMode() { return apiMode; }
    public void setApiMode(String apiMode) { this.apiMode = apiMode; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getApiChainConfig() { return apiChainConfig; }
    public void setApiChainConfig(String apiChainConfig) { this.apiChainConfig = apiChainConfig; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
