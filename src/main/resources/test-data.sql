-- ============================================================
-- Test Data for Data-Connect Platform
-- Encoding: UTF-8 (must be executed with charset=UTF-8)
-- Purpose: Cover all tables, all enum values, all relationships
-- Console Warning: Windows console defaults to GBK(936).
--   If executing via H2 console web UI, encoding is safe.
--   If executing via command line, run: chcp 65001 first!
--   Or use H2 web console: http://localhost:8080/h2-console
-- ============================================================

-- ============================================================
-- 1. template_category — 4 new categories with Chinese names
-- ============================================================
MERGE INTO template_category (id, name, parent_id, sort_order) KEY(id) VALUES
(30, '中文字符测试分类', 1, 1),
(31, '特殊符号分类 ♠♣♥♦', 1, 2),
(32, '日本語テストカテゴリ', 1, 3),
(33, '한국어테스트카테고리', 1, 4);

-- ============================================================
-- 2. template — Templates covering ALL types, with Chinese+special chars
-- ============================================================
MERGE INTO template (id, name, category_id, content, variables, type, tags, is_deleted, version) KEY(id) VALUES
(301, '中文名称-自定义模板', 30,
'// 中文注释：数据转换模板\n// 处理特殊字符：①②③④⑤\n// Emoji测试：🎉🚀💾\ndef result = [:]\nresult.output = input.name?.trim()\nresult.timestamp = System.currentTimeMillis()\nreturn result',
'[{"name":"input","type":"map","description":"输入数据映射","required":true}]',
'CUSTOM', '中文,自定义,测试', 0, 1),

(302, '数据过滤-中文条件', 30,
'// 过滤年龄大于指定值的记录\ndef minAge = binding.variables?.getOrDefault("minAge", 18) as int\nif (input.age == null || (input.age as int) < minAge) {\n    return null  // 丢弃不符合条件的数据\n}\nreturn input',
'[{"name":"minAge","type":"number","description":"最小年龄阈值","required":false,"defaultValue":"18"}]',
'DATA_FILTER', '过滤,条件筛选', 0, 1),

(303, '字段映射-中英混合 Field Mapping', 30,
'def result = [:]\nresult["用户姓名"] = input.user_name\nresult["电子邮箱"] = input.email\nresult["联系电话"] = input.phone\nreturn result',
'[]',
'FIELD_MAPPING', '字段映射,中英混合', 0, 1),

(304, '格式转换-日期格式化 Format Convert', 30,
'import java.text.SimpleDateFormat\ndef sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")\nif (input.birthday) {\n    input.birthday = sdf.format(new Date(input.birthday))\n}\nreturn input',
'[]',
'FORMAT_CONVERT', '格式转换,日期', 0, 1),

(305, '数据聚合-统计汇总 Data Aggregation', 30,
'// 累加求和示例\ndef state = context.computeIfAbsent("total") { 0 }\nstate = state + (input.amount as double)\ncontext["total"] = state\ncontext["count"] = (context["count"] ?: 0) + 1\n// 最后一条数据时返回汇总\nif (context["__is_last__"]) {\n    return [总计金额: state, 记录总数: context["count"]]\n}\nreturn null',
'[]',
'DATA_AGGREGATION', '聚合,统计', 0, 1),

(306, '数据校验-特殊字符验证', 30,
'// 校验数据完整性和特殊字符\nif (!input.name || input.name.trim().isEmpty()) {\n    return [错误: "姓名为空", 数据: input]\n}\nif (input.name.contains("<") || input.name.contains(">")) {\n    return [错误: "姓名含非法字符<>", 数据: input]\n}\nreturn input',
'[]',
'DATA_VALIDATION', '校验,特殊字符', 0, 1),

(307, 'SQL注入防护测试模板', 30,
'// 过滤SQL关键字\ndef dangerous = ["DROP", "DELETE", "INSERT", "UPDATE", "--", "/*", "*/", "xp_"]\ndef value = input.value?.toString()?.toUpperCase()\nfor (kw in dangerous) {\n    if (value?.contains(kw)) {\n        return [警告: "检测到潜在SQL注入: ${kw}", 原始值: input.value]\n    }\n}\nreturn input',
'[]',
'DATA_VALIDATION', '安全,SQL注入防护', 0, 1),

(308, 'JsonPath提取-嵌套JSON解析', 31,
'import groovy.json.JsonSlurper\ndef json = new JsonSlurper().parseText(input.rawJson)\ndef result = [:]\nresult.id = json.response?.data?.id\nresult.姓名 = json.response?.data?.name\nresult.邮箱 = json.response?.data?.email\nreturn result',
'[{"name":"rawJson","type":"string","description":"原始JSON字符串","required":true}]',
'FIELD_MAPPING', 'JSON解析,JsonPath', 0, 1),

(309, 'API链式调用模板-混合模式 ♠♥♣♦', 31,
'// 步骤1: 获取Token\ndef tokenResp = http.get("https://api.example.com/token", [:])\ndef token = tokenResp.access_token\n// 步骤2: 使用Token获取数据\ndef dataResp = http.get("https://api.example.com/data", ["Authorization": "Bearer ${token}"])\nreturn dataResp',
'[]',
'CUSTOM', 'API,链式调用,认证', 0, 1);

-- ============================================================
-- 3. template_version — version history for templates
-- ============================================================
MERGE INTO template_version (id, template_id, version, content, change_log) KEY(id) VALUES
(1001, 301, 1, '// v1: 初始版本\n// 中文注释：数据转换模板\ndef result = [:]\nresult.output = input.name?.trim()\nreturn result', '初始创建-中文版本'),
(1002, 301, 2, '// v2: 增加时间戳字段\n// 中文注释：数据转换模板\ndef result = [:]\nresult.output = input.name?.trim()\nresult.timestamp = System.currentTimeMillis()\nreturn result', '增加时间戳输出'),
(1003, 302, 1, '// v1: 基础过滤\ndef minAge = 18\nif ((input.age as int) < minAge) return null\nreturn input', '初始版本'),
(1004, 303, 1, '// v1: 基础字段映射\ndef result = [:]\nresult["用户姓名"] = input.user_name\nresult["电子邮箱"] = input.email\nreturn result', '初始版本-中文字段映射');

-- ============================================================
-- 4. template_snippet — code snippets with Chinese descriptions
-- ============================================================
MERGE INTO template_snippet (id, name, group_name, description, code, sort_order) KEY(id) VALUES
(50, '中文日志输出', '字段处理', '输出带中文标签的日志信息',
'// 中文日志输出\nprintln "处理记录: ${input.id}, 名称: ${input.name}"\nlog.info "数据校验通过 - ${input.name}"', 1),
(51, '空值安全处理', '字段处理', '安全处理null值和空字符串，避免NullPointerException',
'// 空值安全处理（支持中文变量）\ndef safeGet = { obj, field -> obj?."${field}" ?: "" }\ndef 姓名 = safeGet(input, "name")\ndef 地址 = safeGet(input, "address") ?: "未知地址"', 2),
(52, '字符集检测转换', '类型转换与计算', '检测并转换字符串的字符编码（UTF-8/GBK）',
'// 字符集检测转换\n// Windows控制台默认GBK(936)，服务端UTF-8\ndef bytes = input.value?.toString()?.getBytes("ISO-8859-1")\ndef utf8Str = bytes ? new String(bytes, "UTF-8") : ""\ndef gbkStr = bytes ? new String(bytes, "GBK") : ""\nreturn [utf8: utf8Str, gbk: gbkStr, 原始值: input.value]', 1);

-- ============================================================
-- 5. ds_config — Data sources: DB + API, covering ALL modes/auth types
-- ============================================================
MERGE INTO ds_config (id, name, description, source_type, db_type, host, port, db_name, table_name, username, password, charset, jdbc_params, max_pool_size, min_idle, conn_timeout, init_sql, test_query, ssl_enabled, ssl_cert_path, api_type, api_method, api_url, api_timeout, api_retry_times, api_retry_interval, api_headers, api_body, api_auth_type, api_auth_config, api_mode, template_id, api_chain_config, enabled) KEY(id) VALUES

-- DB Type 1: H2本地测试数据库
(201, '本地H2测试库-中文名称', '用于测试的中文H2数据库连接',
'DB', 'H2', 'localhost', 9092, 'test_db', 'user_info', 'sa', '', 'UTF-8', '', 5, 1, 30, '', 'SELECT 1', 0, '',
NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1),

-- DB Type 2: MySQL
(202, 'MySQL测试数据库_生产环境', 'MySQL数据库连接-包含中文字段',
'DB', 'MySQL', '192.168.1.100', 3306, '生产数据', '用户表', 'root', 'enc:cm9vdDEyMzQ1Ng==', 'UTF-8', 'useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai', 20, 5, 60, 'SET NAMES utf8mb4', 'SELECT 1', 0, '',
NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1),

-- DB Type 3: PostgreSQL with special chars
(203, 'PostgreSQL-特殊符号测试★', 'PostgreSQL数据库连接测试-特殊字符名称★☆',
'DB', 'PostgreSQL', '10.0.0.50', 5432, 'test_db_特殊', 'public.测试表', 'pguser', 'enc:cGdwYXNzMTIz', 'UTF-8', 'currentSchema=public', 10, 2, 30, '', 'SELECT 1', 0, '',
NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1),

-- DB Type 4: Oracle
(204, 'Oracle数据库_财务系统', 'Oracle 19c 财务系统数据源',
'DB', 'Oracle', 'oracle-host', 1521, 'FINANCE', 'FIN.凭证表', 'finance_user', 'enc:ZmluYW5jZTEyMw==', 'UTF-8', '', 10, 2, 60, 'ALTER SESSION SET NLS_DATE_FORMAT=''YYYY-MM-DD HH24:MI:SS''', 'SELECT 1 FROM DUAL', 0, '',
NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1),

-- DB Type 5: SQL Server
(205, 'SQLServer_2025_测试', 'SQL Server 2025 测试数据源-中文备注',
'DB', 'SQL Server', 'sqlserver-host', 1433, 'TestDB_中文', 'dbo.测试数据表', 'sa', 'enc:U2FAIzEyMzQ1Ng==', 'UTF-8', 'encrypt=false', 15, 3, 30, '', 'SELECT 1', 0, '',
NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1),

-- API Type 1: SINGLE mode with NONE auth
(206, '公共API-中文天气查询', '使用公共天气API进行数据查询（无认证）',
'API', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'UTF-8', '', NULL, NULL, NULL, NULL, NULL, NULL, NULL,
'HTTPS', 'GET', 'https://api.weather.example.com/v1/current?city=北京', 15, 3, 2000,
'[{"key":"Accept","value":"application/json"}]', '',
'NONE', '{}',
'SINGLE', NULL, '', 1),

-- API Type 2: SINGLE with API_KEY auth
(207, '开放平台API-密钥认证', '第三方开放平台API接口-使用API Key认证',
'API', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'UTF-8', '', NULL, NULL, NULL, NULL, NULL, NULL, NULL,
'HTTPS', 'POST', 'https://api.openplatform.example.com/v2/data/query', 30, 3, 1000,
'[{"key":"Content-Type","value":"application/json"},{"key":"Accept","value":"application/json"}]',
'{"query":"SELECT * FROM 用户数据","page":1,"pageSize":100}',
'API_KEY', '{"key":"X-API-Key","value":"test-api-key-12345","location":"HEADER"}',
'SINGLE', NULL, '', 1),

-- API Type 3: SINGLE with BEARER auth
(208, '企业API-Bearer令牌认证', '企业内部API-Bearer Token认证访问',
'API', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'UTF-8', '', NULL, NULL, NULL, NULL, NULL, NULL, NULL,
'HTTPS', 'GET', 'https://api.enterprise.example.com/api/v1/员工信息', 20, 5, 3000,
'[{"key":"Accept","value":"application/json;charset=UTF-8"}]', '',
'BEARER', '{"token":"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiLmtYvor5XnlKjmiLciLCJleHAiOjk5OTk5OTk5OTl9.test"}',
'SINGLE', NULL, '', 1),

-- API Type 4: SINGLE with BASIC auth
(209, '内部服务-Basic认证', '内部微服务接口-Basic认证方式',
'API', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'UTF-8', '', NULL, NULL, NULL, NULL, NULL, NULL, NULL,
'HTTP', 'POST', 'http://internal-api.example.com/service/数据同步', 10, 2, 500,
'[{"key":"Content-Type","value":"application/json;charset=UTF-8"}]',
'{"action":"sync","table":"用户信息表","timestamp":"${currentTime}"}',
'BASIC', '{"username":"admin","password":"admin123"}',
'SINGLE', NULL, '', 1),

-- API Type 5: CHAIN mode
(210, '链式API-登录后查询数据链', '先登录获取Token，再使用Token查询数据的链式调用',
'API', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'UTF-8', '', NULL, NULL, NULL, NULL, NULL, NULL, NULL,
'HTTPS', 'POST', 'https://api.example.com/auth/login', 30, 3, 1000,
'[{"key":"Content-Type","value":"application/json"}]',
'{"username":"测试用户","password":"test123"}',
'BEARER', '{"tokenJsonPath":"$.data.token","headerPrefix":"Bearer "}',
'CHAIN', NULL,
'[{"name":"登录获取Token","url":"https://api.example.com/auth/login","method":"POST","headers":{"Content-Type":"application/json"},"body":"{\"username\":\"测试用户\",\"password\":\"test123\"}","extractPath":"$.data.token","extractKey":"token"},{"name":"查询用户数据","url":"https://api.example.com/api/user/list","method":"GET","headers":{"Authorization":"Bearer ${token}"},"extractPath":"$.data.list","extractKey":"userList"}]',
1),

-- API Type 6: SCRIPT mode
(211, '脚本模式-自定义Groovy编排', '使用Groovy脚本完全自定义API调用编排逻辑',
'API', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'UTF-8', '', NULL, NULL, NULL, NULL, NULL, NULL, NULL,
'HTTPS', 'POST', '', 60, 3, 1000,
'[]', '',
'NONE', '{}',
'SCRIPT', 301, '', 1),

-- DB disabled
(212, '已禁用的数据源-测试', '该数据源已被禁用，用于测试禁用状态',
'DB', 'MySQL', 'localhost', 3306, 'disabled_db', 'disabled_table', 'root', '', 'UTF-8', '', 10, 2, 30, '', 'SELECT 1', 0, '',
NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0);

-- ============================================================
-- 6. column_config — column configurations, both RECEIVE and PUSH
-- ============================================================
MERGE INTO column_config (id, name, description, column_type, columns_json) KEY(id) VALUES
(201, '用户接收字段配置-中文', '从API接收的用户数据字段定义（中文命名）',
'RECEIVE',
'[{"key":"用户ID","value":"user_id","type":"string"},{"key":"姓名","value":"user_name","type":"string"},{"key":"电子邮箱","value":"email","type":"string"},{"key":"手机号码","value":"phone","type":"string"},{"key":"出生日期","value":"birthday","type":"date"},{"key":"年龄","value":"age","type":"number"},{"key":"是否会员","value":"is_vip","type":"boolean"},{"key":"备注信息","value":"remark","type":"text"}]'),

(202, '用户推送字段配置-中文', '推送到目标系统的用户字段映射（中文命名）',
'PUSH',
'[{"key":"姓名","value":"full_name","type":"string"},{"key":"邮箱","value":"email_addr","type":"string"},{"key":"电话","value":"phone_num","type":"string"},{"key":"地址","value":"address_detail","type":"string"},{"key":"入职日期","value":"hire_date","type":"date"},{"key":"薪资","value":"salary","type":"number"},{"key":"是否在职","value":"is_active","type":"boolean"},{"key":"个人简介","value":"bio","type":"text"}]'),

(203, '订单字段接收配置-中英混合 Order Fields', '订单数据接收字段定义',
'RECEIVE',
'[{"key":"订单号","value":"order_id","type":"string"},{"key":"用户ID","value":"user_id","type":"string"},{"key":"订单金额","value":"amount","type":"number"},{"key":"创建时间","value":"create_time","type":"datetime"},{"key":"订单状态","value":"status","type":"string"},{"key":"商品列表","value":"items","type":"json"},{"key":"附件","value":"attachment","type":"file"}]'),

(204, 'JSON特殊字段测试 ♠♣♥♦', '包含特殊字符的字段配置',
'RECEIVE',
'[{"key":"字段①","value":"field_one","type":"string"},{"key":"字段②","value":"field_two","type":"number"},{"key":"字段③","value":"field_three","type":"boolean"},{"key":"中文键名","value":"chinese_key","type":"text"},{"key":"日本語フィールド","value":"japanese_field","type":"string"}]');

-- ============================================================
-- 7. mapping_template — field mapping templates
-- ============================================================
MERGE INTO mapping_template (id, name, description, ds_config_id, column_config_id, mappings, postman_json) KEY(id) VALUES
(201, '用户数据映射-中英双向映射', '将API接收的中文字段映射到数据库英文字段',
201, 201,
'[{"receiveKey":"用户ID","pushKey":"user_id"},{"receiveKey":"姓名","pushKey":"full_name"},{"receiveKey":"电子邮箱","pushKey":"email_addr"},{"receiveKey":"手机号码","pushKey":"phone_num"},{"receiveKey":"出生日期","pushKey":"birth_date"},{"receiveKey":"是否会员","pushKey":"vip_flag"}]',
'{"info":{"name":"User Mapping Import","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"}}'),

(202, '订单数据映射-特殊字符测试', '订单系统字段映射-包含中英文混合和特殊符号',
203, 203,
'[{"receiveKey":"订单号","pushKey":"order_id"},{"receiveKey":"用户ID","pushKey":"user_id"},{"receiveKey":"订单金额","pushKey":"total_amount"},{"receiveKey":"创建时间","pushKey":"created_at"},{"receiveKey":"订单状态","pushKey":"order_status"},{"receiveKey":"商品列表","pushKey":"item_list"},{"receiveKey":"附件","pushKey":"attach_file"}]',
NULL),

(203, 'nullable映射模板', '测试空ds_config_id和空column_config_id的情况',
NULL, NULL,
'[{"receiveKey":"输入字段A","pushKey":"输出字段A"},{"receiveKey":"输入字段B","pushKey":"输出字段B"}]',
NULL);

-- ============================================================
-- 8. flow_config — integration flows covering ALL sync strategies
-- ============================================================
MERGE INTO flow_config (id, name, description, input_ds_id, output_ds_id, pre_template_id, mapping_template_id, post_template_id, template_params, pipeline_config, sync_strategy, incremental_column, incremental_column_type) KEY(id) VALUES
(201, '【全量同步】API到数据库-用户数据', '将API用户数据全量同步到数据库，包含中文字段映射',
206, 201, 301, 201, 304,
'{"batchSize":100,"timeout":3600}',
'[{"position":"AFTER_READ","name":"数据预处理阶段","steps":[{"type":"TEMPLATE","templateId":301,"mappingTemplateId":null,"params":{"清洗空值":true}}]},{"position":"BEFORE_WRITE","name":"数据映射阶段","steps":[{"type":"MAPPING","templateId":null,"mappingTemplateId":201,"params":{}}]},{"position":"AFTER_WRITE","name":"后处理阶段","steps":[{"type":"TEMPLATE","templateId":304,"mappingTemplateId":null,"params":{"格式化日期":true}}]}]',
'FULL', NULL, NULL),

(202, '【增量同步-时间】订单数据_INCR_TIME', '基于时间戳增量同步订单数据',
203, 201, 302, 202, NULL,
'{"batchSize":200,"timeout":7200}',
'[{"position":"AFTER_READ","name":"过滤已同步数据","steps":[{"type":"TEMPLATE","templateId":302,"mappingTemplateId":null,"params":{"minAge":0}}]},{"position":"BEFORE_WRITE","name":"字段映射","steps":[{"type":"MAPPING","templateId":null,"mappingTemplateId":202,"params":{}}]}]',
'INCREMENTAL_TIME', 'update_time', 'DATETIME'),

(203, '【增量同步-ID】审计日志_INCR_ID', '基于自增ID增量同步审计日志',
202, 201, NULL, 203, NULL,
NULL,
'[{"position":"BEFORE_WRITE","name":"直接映射","steps":[{"type":"MAPPING","templateId":null,"mappingTemplateId":203,"params":{}}]}]',
'INCREMENTAL_ID', 'audit_id', 'NUMERIC'),

(204, '【多阶段管道】复杂ETL流程 ⚙️', '包含预处理器→映射→校验→聚合→后处理的完整ETL管道',
206, 201, 301, 201, 305,
'{"batchSize":50,"timeout":14400}',
'[{"position":"AFTER_READ","name":"①数据清洗","steps":[{"type":"TEMPLATE","templateId":301,"mappingTemplateId":null,"params":{}},{"type":"TEMPLATE","templateId":302,"mappingTemplateId":null,"params":{"minAge":18}}]},{"position":"BEFORE_WRITE","name":"②数据校验与映射","steps":[{"type":"TEMPLATE","templateId":306,"mappingTemplateId":null,"params":{}},{"type":"MAPPING","templateId":null,"mappingTemplateId":201,"params":{}},{"type":"TEMPLATE","templateId":304,"mappingTemplateId":null,"params":{}}]},{"position":"AFTER_WRITE","name":"③聚合汇总","steps":[{"type":"TEMPLATE","templateId":305,"mappingTemplateId":null,"params":{}}]}]',
'FULL', NULL, NULL),

(205, '【简化流程】无管道仅映射', '最简单的同步流程-无管道配置，仅字段映射',
206, 201, NULL, 201, NULL,
NULL, NULL,
'FULL', NULL, NULL);

-- ============================================================
-- 9. task_config — tasks covering ALL statuses
-- ============================================================
MERGE INTO task_config (id, name, flow_config_id, cron_expr, status, retry_times, retry_interval, timeout, notify_url) KEY(id) VALUES
(201, '定时任务-用户数据每小时全量同步 ⏰', 201, '0 0 * * * ?', 'RUNNING', 3, 60, 3600, 'https://webhook.example.com/task/notify?任务=用户同步'),
(202, '定时任务-订单增量同步(每30分钟)', 202, '0 */30 * * * ?', 'RUNNING', 5, 30, 1800, ''),
(203, '已暂停-审计日志同步任务 ⏸️', 203, '0 */10 * * * ?', 'PAUSED', 3, 60, 900, ''),
(204, '已停止-废弃的旧同步任务', 201, '0 0 2 * * ?', 'STOPPED', 1, 0, 600, ''),
(205, '单次手动-复杂ETL任务(不自动调度)', 204, '', 'STOPPED', 1, 60, 14400, 'https://webhook.example.com/etl/notify?任务=复杂ETL');

-- ============================================================
-- 10. task_execution_log — execution history for tasks
-- ============================================================
MERGE INTO task_execution_log (id, task_id, status, start_time, end_time, total_count, success_count, fail_count, log_detail) KEY(id) VALUES
(201, 201, 'SUCCESS', '2026-06-08 10:00:00', '2026-06-08 10:05:30', 1500, 1500, 0, '全量同步完成-用户数据：共处理1500条记录，全部成功。源：API(中文天气查询)，目标：本地H2测试库。编码：UTF-8。'),
(202, 201, 'SUCCESS', '2026-06-08 11:00:00', '2026-06-08 11:04:15', 1498, 1497, 1, '全量同步完成-用户数据：1497成功，1条失败（字段"备注信息"包含非法字符<>），已跳过。'),
(203, 202, 'SUCCESS', '2026-06-08 10:00:00', '2026-06-08 10:02:10', 350, 350, 0, '增量同步(时间)完成：30分钟内新增订单350条，全部同步成功。'),
(204, 202, 'FAILED', '2026-06-08 10:30:00', '2026-06-08 10:35:00', 100, 50, 50, '增量同步失败：连接超时，50条数据写入失败。错误信息：Connection timeout to MySQL数据库_生产环境。建议检查网络连接和数据库状态。'),
(205, 203, 'RUNNING', '2026-06-08 16:00:00', NULL, NULL, NULL, NULL, '正在执行审计日志增量同步...'),
(206, 201, 'SUCCESS', '2026-06-08 12:00:00', '2026-06-08 12:03:45', 1492, 1488, 4, '全量同步完成：1488成功，4条失败-特殊字符异常（包含emoji🎉，数据库字符集不兼容）。建议检查目标数据库的utf8mb4配置。');

-- ============================================================
-- 11. debug_log — debug/test logs for data sources
-- ============================================================
MERGE INTO debug_log (id, ds_config_id, operation_type, config_snapshot, result_status, result_snapshot, duration) KEY(id) VALUES
(201, 206, 'CONNECT_TEST', '{"url":"https://api.weather.example.com/v1/current?city=北京","method":"GET"}', 'SUCCESS', '{"statusCode":200,"message":"连接成功-API响应正常","responseTime":"156ms"}', 156),
(202, 206, 'API_TEST', '{"url":"https://api.weather.example.com/v1/current?city=北京","method":"GET","mode":"SINGLE"}', 'SUCCESS', '{"statusCode":200,"body":{"城市":"北京","温度":"26°C","天气":"晴","湿度":"45%","更新时间":"2026-06-08 16:00:00"}}', 234),
(203, 201, 'CONNECT_TEST', '{"dbType":"H2","url":"jdbc:h2:file:./data/dataconnect","username":"sa"}', 'SUCCESS', '{"message":"数据库连接成功-H2","version":"H2 2.2.224"}', 45),
(204, 201, 'QUERY_TEST', '{"sql":"SELECT COUNT(*) AS 记录总数 FROM ds_config WHERE source_type=''API''"}', 'SUCCESS', '{"columns":["记录总数"],"rows":[[6]],"duration":"12ms"}', 12),
(205, 201, 'SCHEMA_PREVIEW', '{"tableName":"ds_config"}', 'SUCCESS', '{"columns":[{"name":"ID","type":"BIGINT"},{"name":"NAME","type":"VARCHAR(100)"},{"name":"SOURCE_TYPE","type":"VARCHAR(20)"},{"name":"CHARSET","type":"VARCHAR(50)"}]}', 28),
(206, 202, 'CONNECT_TEST', '{"dbType":"MySQL","host":"192.168.1.100","port":3306,"dbName":"生产数据","username":"root"}', 'FAILED', '{"error":"Connection refused: 无法连接到MySQL数据库_生产环境 (192.168.1.100:3306)。请检查数据库是否启动，防火墙是否放行。","errorCode":"CONNECTION_REFUSED"}', 30005),
(207, 210, 'API_TEST', '{"url":"https://api.example.com/auth/login","method":"POST","mode":"CHAIN"}', 'SUCCESS', '{"chainSteps":[{"step":1,"name":"登录获取Token","status":"SUCCESS","extractedToken":"eyJhbG...","duration":180},{"step":2,"name":"查询用户数据","status":"SUCCESS","recordCount":42,"duration":320}],"totalDuration":500}', 500),
(208, 211, 'API_TEST', '{"url":"","method":"POST","mode":"SCRIPT","templateId":301}', 'SUCCESS', '{"scriptOutput":{"processedRecords":100,"status":"脚本执行成功"},"templateVersion":2,"duration":"1.2s"}', 1200);

-- ============================================================
-- VERIFICATION QUERIES (run after inserting test data)
-- ============================================================
-- SELECT 'template_category' AS 表名, COUNT(*) AS 记录数 FROM template_category
-- UNION ALL SELECT 'template', COUNT(*) FROM template
-- UNION ALL SELECT 'template_version', COUNT(*) FROM template_version
-- UNION ALL SELECT 'template_snippet', COUNT(*) FROM template_snippet
-- UNION ALL SELECT 'ds_config', COUNT(*) FROM ds_config
-- UNION ALL SELECT 'column_config', COUNT(*) FROM column_config
-- UNION ALL SELECT 'mapping_template', COUNT(*) FROM mapping_template
-- UNION ALL SELECT 'flow_config', COUNT(*) FROM flow_config
-- UNION ALL SELECT 'task_config', COUNT(*) FROM task_config
-- UNION ALL SELECT 'task_execution_log', COUNT(*) FROM task_execution_log
-- UNION ALL SELECT 'debug_log', COUNT(*) FROM debug_log
-- ORDER BY 表名;

-- ============================================================
-- ENCODING VERIFICATION
-- After insertion, run these to verify no garbled characters:
-- SELECT * FROM ds_config WHERE name LIKE '%中文%' OR name LIKE '%测试%';
-- SELECT * FROM template WHERE tags LIKE '%中文%';
-- SELECT * FROM task_config WHERE name LIKE '%⏰%' OR name LIKE '%⏸️%';
-- ============================================================
