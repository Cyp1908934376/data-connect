-- 启动必要数据：模板默认分类 + 可视化组件定义
-- 首次启动时自动执行（由 schema.sql 之后执行）

-- 模板分类
MERGE INTO template_category (id, name, parent_id, sort_order) KEY(id) VALUES
(1, '数据对接模板', 0, 1),
(2, '基础转换', 1, 1),
(3, '字段映射', 2, 1),
(4, '数据过滤', 2, 2),
(5, '格式转换', 2, 3),
(6, '高级处理', 1, 2),
(7, '数据聚合', 6, 1),
(8, '数据拆分', 6, 2),
(9, '数据校验', 6, 3),
(10, '业务场景', 1, 3),
(11, '订单同步', 10, 1),
(12, '用户数据', 10, 2),
(13, '库存管理', 10, 3),
(14, '通用代码模板', 0, 2),
(15, '数据处理', 14, 1),
(16, '工具函数', 14, 2),
(17, '格式化', 14, 3),
(18, '正则表达式', 14, 4),
(19, '开放API模板', 0, 3),
(20, 'Token认证', 19, 1),
(21, '分页获取', 19, 2),
(22, 'JSON解析', 19, 3),
(23, 'API综合场景', 19, 4),
(24, '列值处理', 1, 4);

-- 可视化组件定义
MERGE INTO component_definition (id, name, category, icon, description, execution_type, config_schema, sort_order, enabled) KEY(id) VALUES
(1, 'For循环', 'FLOW', 'bi bi-arrow-repeat', '遍历集合执行子步骤', 'FOR_LOOP', '{"loopVar":"item","indexVar":"loopIndex","totalVar":"loopTotal"}', 1, 1),
(2, '条件判断', 'FLOW', 'bi bi-question-diamond', '根据条件决定执行路径', 'CONDITION', '{"field":"","operator":"==","value":"","caseSensitive":true}', 2, 1),
(3, '数据库查询', 'DATA', 'bi bi-database', '执行SQL查询获取数据', 'DB_QUERY', '{"dsId":0,"sql":"","limit":1000}', 3, 1),
(4, 'API调用', 'DATA', 'bi bi-cloud', '发起HTTP接口获取数据', 'API_CALL', '{"dsId":0,"apiUrl":"","method":"GET","timeout":30}', 4, 1),
(5, '字段映射', 'TRANSFORM', 'bi bi-link-45deg', '将源字段映射到目标字段', 'FIELD_MAPPING', '{"mappings":[],"keepUnmapped":true,"defaultValue":""}', 5, 1),
(6, '数据过滤', 'TRANSFORM', 'bi bi-funnel', '按条件筛选数据行', 'DATA_FILTER', '{"conditions":[],"logic":"AND","caseSensitive":true}', 6, 1),
(7, '类型转换', 'TRANSFORM', 'bi bi-arrow-repeat', '转换字段的数据类型', 'TYPE_CONVERT', '{"conversions":[],"errorHandling":"SKIP","defaultValue":""}', 7, 1),
(8, '数据聚合', 'TRANSFORM', 'bi bi-bar-chart', '按字段分组统计', 'DATA_AGGREGATE', '{"groupByFields":[],"functions":[]}', 8, 1),
(9, '数据排序', 'TRANSFORM', 'bi bi-sort-down', '按字段排序数据', 'DATA_SORT', '{"sortFields":[],"caseSensitive":true}', 9, 1),
(10, '数据库写入', 'OUTPUT', 'bi bi-database-add', '将数据写入目标数据库', 'DB_WRITE', '{"dsId":0,"tableName":"","writeMode":"INSERT","autoCreateTable":true}', 10, 1),
(11, '日志输出', 'OUTPUT', 'bi bi-journal-text', '输出日志信息', 'LOG_OUTPUT', '{"level":"INFO","message":""}', 11, 1),
(12, '结果返回', 'OUTPUT', 'bi bi-check-circle', '作为流程的终点节点返回最终结果', 'RESULT_RETURN', '{"outputFields":""}', 12, 1);

-- 可视化模板分类
MERGE INTO visual_template_category (id, name, parent_id, sort_order) KEY(id) VALUES
(1, '数据同步', 0, 1),
(2, '数据转换', 0, 2),
(3, '数据校验', 0, 3),
(4, '业务场景', 0, 4),
(5, '系统内置', 0, 5);

-- 代码片段（编辑器右侧面板）
MERGE INTO template_snippet (id, name, group_name, description, code, sort_order) KEY(id) VALUES
(1, 'out[""] = input[""]', '字段处理', '字段取值赋值', 'out["​"] = input["​"]', 1),
(2, 'out[""] = input[""] ?: ""', '字段处理', '带默认值', 'out["​"] = input["​"] ?: ""​', 2),
(3, '字符串拼接', '字段处理', '多字段合并', 'out["full_name"] = input["first"] + " " + input["last"]', 3),
(4, '?.toUpperCase()', '字段处理', '安全转大写', 'out["name"] = input["name"]?.toUpperCase()', 4),
(5, 'as double', '类型转换 & 计算', '转为浮点数', 'out["​"] = input["​"] as double', 1),
(6, 'as int', '类型转换 & 计算', '转为整数', 'out["​"] = input["​"] as int', 2),
(7, '(x as double).round(2)', '类型转换 & 计算', '保留2位小数', 'out["​"] = (input["​"] as double).round(2)', 3),
(8, '数值计算', '类型转换 & 计算', '乘法示例', 'out["total"] = (input["price"] as double) * (input["qty"] as int)', 4),
(9, '当前时间格式化', '日期时间', '', 'import java.text.SimpleDateFormat
def sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
out["​"] = sdf.format(new Date())', 1),
(10, '时间戳 → 日期字符串', '日期时间', '', 'import java.text.SimpleDateFormat
def sdf = new SimpleDateFormat("yyyy-MM-dd")
out["​"] = sdf.format(new Date(Long.parseLong(input["​"] as String)))', 2),
(11, 'System.currentTimeMillis()', '日期时间', '当前时间戳', 'out["timestamp"] = System.currentTimeMillis()', 3),
(12, 'if 条件过滤', '控制流程', '不符合则跳过', 'if (input["​"] == ""​) {
    return input
}', 1),
(13, 'if-else 分支', '控制流程', '二元判断', 'if (input["​"]) {
    out["​"] = ""​
} else {
    out["​"] = ""​
}', 2),
(14, 'for-in 循环', '控制流程', '遍历集合', 'for (item in ​) {
    out[item.key] = item.value
}', 3),
(15, '列表转换', '控制流程', '遍历生成新列表', 'def result = []
for (row in input["​"]) {
    result.add([id: row.id, name: row.name])
}
out["​"] = result', 4),
(16, 'JsonSlurper 解析', 'JSON 处理', 'JSON字符串→Map', 'import groovy.json.JsonSlurper
def parsed = new JsonSlurper().parseText(input["​"] as String)
out.putAll(parsed as Map)', 1),
(17, 'JsonOutput.toJson', 'JSON 处理', 'Map→JSON字符串', 'import groovy.json.JsonOutput
def json = JsonOutput.toJson(out)
return json', 2),
(18, 'prettyPrint', 'JSON 处理', '格式化输出', 'import groovy.json.JsonOutput
out["json"] = JsonOutput.prettyPrint(JsonOutput.toJson(input))', 3),
(19, 'http.get()', 'API 调用', 'GET 请求模板', 'def resp = http.get("​", null)
if (resp.success) {
    out.data = resp.data
} else {
    out.success = false
    out.error = "HTTP " + resp.status
    return
}', 1),
(20, 'http.post()', 'API 调用', 'POST 请求模板', 'def resp = http.post("​", "​", ["Content-Type":"application/json"])
if (resp.success) {
    out.data = resp.data
} else {
    out.success = false
    out.error = "请求失败: HTTP " + resp.status
    return
}', 2),
(21, 'Token认证 + 查询', 'API 调用', '链式调用示例', '// 链式调用: 先获取 Token，再用 Token 调接口
def tokenResp = http.post("https://api.example.com/login",
    "{\\"username\\":\\"admin\\",\\"password\\":\\"123456\\"}",
    ["Content-Type":"application/json"])
if (!tokenResp.success) {
    out.success = false; out.error = "登录失败: " + tokenResp.status; return
}
def token = tokenResp.data.token
def dataResp = http.get("https://api.example.com/data", ["Authorization":"Bearer " + token])
out.data = dataResp.data
out.success = true', 3),
(22, '遍历请求', 'API 调用', '逐条查详情', 'def list = []
for (item in input["items"]) {
    def resp = http.get("https://api.example.com/detail/" + item.id, null)
    if (resp.success) { list.add(resp.data) }
}
out.data = list
out.success = true', 4),
(23, '必填校验', '数据校验', '抛出异常', 'if (!input["​"]) {
    throw new IllegalArgumentException("必填字段缺失: "​)
}', 1),
(24, '必填校验', '数据校验', '返回错误标记', 'if (!input["​"]) {
    out.success = false
    out.error = "缺少必填字段"
    return
}', 2),
(25, '类型校验', '数据校验', '检查数值类型', 'def val = input["​"]
if (val != null && !(val instanceof Number)) {
    out.success = false; out.error = "类型错误"; return
}', 3),
(26, 'Base64 编码', '附件/文件处理', '', '// Base64 编码
try {
    byte[] bytes = input["​"].toString().getBytes("UTF-8")
    out["​"] = bytes.encodeBase64().toString()
} catch (Exception e) {
    out.success = false; out.error = "Base64编码失败: " + e.message
}', 1),
(27, 'Base64 解码', '附件/文件处理', '', '// Base64 解码
try {
    byte[] bytes = input["​"].toString().decodeBase64()
    out["​"] = new String(bytes, "UTF-8")
    out.decodedSize = bytes.length
} catch (Exception e) {
    out.success = false; out.error = "Base64解码失败: " + e.message
}', 2);
