-- Data Connect 元数据表 DDL (H2)
-- 首次启动时自动执行

-- 数据源配置表
CREATE TABLE IF NOT EXISTS ds_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) DEFAULT '',
    source_type VARCHAR(20) NOT NULL,          -- DB / API
    db_type VARCHAR(50) DEFAULT '',             -- MySQL, PostgreSQL, Oracle, SQL Server, SQLite, H2, MariaDB, ClickHouse, DB2, Trino, Derby, HSQLDB, TDengine, DuckDB, Firebird, Drill, Presto, Neo4j, SAP_HANA, Snowflake, InfluxDB
    host VARCHAR(200) DEFAULT '',
    port INT DEFAULT 0,
    db_name VARCHAR(200) DEFAULT '',
    table_name VARCHAR(200) DEFAULT '',              -- 指定读取的表名，留空则自动取第一个表
    username VARCHAR(200) DEFAULT '',
    password VARCHAR(500) DEFAULT '',           -- 简单加密存储
    charset VARCHAR(50) DEFAULT 'UTF-8',
    jdbc_params VARCHAR(1000) DEFAULT '',       -- 额外JDBC参数(key=value&key2=value2)
    max_pool_size INT DEFAULT 10,
    min_idle INT DEFAULT 2,
    conn_timeout INT DEFAULT 30,                -- 连接超时(秒)
    init_sql VARCHAR(2000) DEFAULT '',
    test_query VARCHAR(200) DEFAULT '',
    ssl_enabled TINYINT DEFAULT 0,
    ssl_cert_path VARCHAR(500) DEFAULT '',
    -- API类型专用字段
    api_type VARCHAR(20) DEFAULT '',            -- HTTP/HTTPS
    api_method VARCHAR(10) DEFAULT 'GET',       -- GET/POST/PUT/DELETE
    api_url VARCHAR(1000) DEFAULT '',
    api_timeout INT DEFAULT 30,
    api_retry_times INT DEFAULT 3,
    api_retry_interval INT DEFAULT 1000,
    api_headers TEXT DEFAULT '',                -- JSON格式
    api_body TEXT DEFAULT '',                   -- 请求体模板
    api_auth_type VARCHAR(50) DEFAULT 'NONE',   -- NONE/BASIC/BEARER/API_KEY/OAUTH2
    api_auth_config TEXT DEFAULT '',            -- JSON格式
    api_mode VARCHAR(20) DEFAULT 'SINGLE',     -- SINGLE=单接口, CHAIN=多接口链, SCRIPT=复杂脚本
    template_id BIGINT DEFAULT 0,               -- 关联模板(SCRIPT模式或响应转换)
    api_chain_config TEXT DEFAULT '',           -- CHAIN模式的多接口链配置JSON
    enabled TINYINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 模板分类表
CREATE TABLE IF NOT EXISTS template_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    parent_id BIGINT DEFAULT 0,
    sort_order INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 模板表
CREATE TABLE IF NOT EXISTS template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    category_id BIGINT DEFAULT 0,
    content TEXT DEFAULT '',
    variables TEXT DEFAULT '',                  -- JSON格式：[{name, type, description, defaultValue, required, validation}]
    type VARCHAR(50) DEFAULT 'CUSTOM',          -- CUSTOM/FIELD_MAPPING/DATA_FILTER/FORMAT_CONVERT/DATA_AGGREGATION/DATA_VALIDATION
    tags VARCHAR(500) DEFAULT '',
    is_deleted TINYINT DEFAULT 0,
    version INT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 模板版本表
CREATE TABLE IF NOT EXISTS template_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    version INT NOT NULL,
    content TEXT DEFAULT '',
    change_log VARCHAR(500) DEFAULT '',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 对接流程配置表
CREATE TABLE IF NOT EXISTS flow_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500) DEFAULT '',
    input_ds_id BIGINT DEFAULT 0,
    output_ds_id BIGINT DEFAULT 0,
    pre_template_id BIGINT DEFAULT 0,
    mapping_template_id BIGINT DEFAULT 0,
    post_template_id BIGINT DEFAULT 0,
    template_params TEXT DEFAULT '',            -- JSON格式：模板参数值
    pipeline_config TEXT DEFAULT '',            -- JSON格式：管道阶段配置 [{"position":"AFTER_READ","name":"阶段","steps":[{"type":"TEMPLATE","templateId":1}]}]
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 任务配置表
CREATE TABLE IF NOT EXISTS task_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    flow_config_id BIGINT NOT NULL,
    cron_expr VARCHAR(100) DEFAULT '',          -- Cron表达式
    status VARCHAR(20) DEFAULT 'STOPPED',       -- RUNNING/PAUSED/STOPPED
    retry_times INT DEFAULT 3,
    retry_interval INT DEFAULT 60,              -- 重试间隔(秒)
    timeout INT DEFAULT 3600,                   -- 超时时间(秒)
    notify_url VARCHAR(500) DEFAULT '',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 任务执行记录表
CREATE TABLE IF NOT EXISTS task_execution_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'RUNNING',       -- RUNNING/SUCCESS/FAILED/STOPPED
    start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP NULL,
    total_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,
    log_detail TEXT DEFAULT '',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 调试记录表
CREATE TABLE IF NOT EXISTS debug_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ds_config_id BIGINT DEFAULT 0,
    operation_type VARCHAR(50) DEFAULT '',      -- CONNECT_TEST/QUERY_TEST/SCHEMA_PREVIEW/DATA_PREVIEW/API_TEST
    config_snapshot TEXT DEFAULT '',            -- JSON：当时的数据源配置快照
    result_status VARCHAR(20) DEFAULT '',       -- SUCCESS/FAILED
    result_snapshot TEXT DEFAULT '',            -- JSON：结果快照
    duration BIGINT DEFAULT 0,                  -- 耗时(ms)
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 列配置表
CREATE TABLE IF NOT EXISTS column_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500) DEFAULT '',
    column_type VARCHAR(20) DEFAULT 'RECEIVE',     -- RECEIVE=接收列, PUSH=推送列
    columns_json TEXT DEFAULT '',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 数据对接模板表
CREATE TABLE IF NOT EXISTS mapping_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500) DEFAULT '',
    ds_config_id BIGINT DEFAULT 0,
    column_config_id BIGINT DEFAULT 0,
    mappings TEXT DEFAULT '',
    postman_json TEXT DEFAULT '',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 模板代码片段表
CREATE TABLE IF NOT EXISTS template_snippet (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    group_name VARCHAR(100) DEFAULT '',
    description VARCHAR(500) DEFAULT '',
    code TEXT NOT NULL,
    sort_order INT DEFAULT 0
);

-- 索引
ALTER TABLE ds_config ADD COLUMN IF NOT EXISTS table_name VARCHAR(200) DEFAULT '';
ALTER TABLE ds_config ADD COLUMN IF NOT EXISTS api_mode VARCHAR(20) DEFAULT 'SINGLE';
ALTER TABLE ds_config ADD COLUMN IF NOT EXISTS template_id BIGINT DEFAULT 0;
ALTER TABLE ds_config ADD COLUMN IF NOT EXISTS api_chain_config TEXT DEFAULT '';

ALTER TABLE flow_config ADD COLUMN IF NOT EXISTS pre_template_id BIGINT DEFAULT 0;
ALTER TABLE flow_config ADD COLUMN IF NOT EXISTS mapping_template_id BIGINT DEFAULT 0;
ALTER TABLE flow_config ADD COLUMN IF NOT EXISTS post_template_id BIGINT DEFAULT 0;
ALTER TABLE flow_config ADD COLUMN IF NOT EXISTS pipeline_config TEXT DEFAULT '';

ALTER TABLE flow_config ADD COLUMN IF NOT EXISTS sync_strategy VARCHAR(20) DEFAULT 'FULL';
ALTER TABLE flow_config ADD COLUMN IF NOT EXISTS incremental_column VARCHAR(100) DEFAULT '';
ALTER TABLE flow_config ADD COLUMN IF NOT EXISTS incremental_column_type VARCHAR(20) DEFAULT 'DATETIME';

ALTER TABLE column_config ADD COLUMN IF NOT EXISTS column_type VARCHAR(20) DEFAULT 'RECEIVE';

CREATE INDEX IF NOT EXISTS idx_ds_config_source_type ON ds_config(source_type);
CREATE INDEX IF NOT EXISTS idx_ds_config_enabled ON ds_config(enabled);
CREATE INDEX IF NOT EXISTS idx_template_category_id ON template(category_id);
CREATE INDEX IF NOT EXISTS idx_template_is_deleted ON template(is_deleted);
CREATE INDEX IF NOT EXISTS idx_template_version_tid ON template_version(template_id);
CREATE INDEX IF NOT EXISTS idx_task_config_status ON task_config(status);
CREATE INDEX IF NOT EXISTS idx_task_execution_log_tid ON task_execution_log(task_id);
CREATE INDEX IF NOT EXISTS idx_debug_log_ds_id ON debug_log(ds_config_id);
