-- 初始化数据：模板默认分类 (首次启动时自动执行)
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

-- ============================================
-- 示例数据：三种API模式 (SINGLE / CHAIN / SCRIPT)
-- ============================================

-- 示例模板：SCRIPT模式使用的Groovy脚本（先查帖子→提取userId→查用户详情）
MERGE INTO template (id, name, category_id, content, type, tags, is_deleted, version) KEY(id) VALUES
(101, '示例-API链式编排脚本', 1,
'// API链式编排示例：先查帖子，根据userId查用户详情
def postResp = http.get("https://jsonplaceholder.typicode.com/posts/1", null)
if (!postResp.success) {
    out.success = false
    out.error = "获取帖子失败: HTTP " + postResp.status
    return
}
def post = postResp.data
def userId = post.userId

// 第二步：根据userId查询用户信息
def userResp = http.get("https://jsonplaceholder.typicode.com/users/" + userId, null)
if (!userResp.success) {
    out.success = false
    out.error = "获取用户失败: HTTP " + userResp.status
    return
}

out.post = post
out.user = userResp.data
out.success = true',
'CUSTOM', '示例,API,链式编排', 0, 1);

-- SINGLE模式：直接GET请求获取帖子详情
MERGE INTO ds_config (id, name, description, source_type, api_method, api_url, api_timeout, api_mode, api_auth_type, enabled) KEY(id) VALUES
(101, '示例-单接口查询(JSONPlaceholder)', 'SINGLE模式：直接GET请求获取帖子详情，适合简单接口查询场景', 'API', 'GET', 'https://jsonplaceholder.typicode.com/posts/1', 30, 'SINGLE', 'NONE', 1);

-- CHAIN模式：先查帖子提取userId，再查用户详情（两步链式调用）
MERGE INTO ds_config (id, name, description, source_type, api_method, api_url, api_timeout, api_mode, api_auth_type, api_chain_config, enabled) KEY(id) VALUES
(102, '示例-链式调用(帖子→用户)', 'CHAIN模式：第一步查帖子提取userId，第二步用userId查用户详情，演示变量提取与传递', 'API', 'GET', '', 30, 'CHAIN', 'NONE', '[{"name":"查帖子","url":"https://jsonplaceholder.typicode.com/posts/1","method":"GET","extract":{"userId":"userId"}},{"name":"查用户","url":"https://jsonplaceholder.typicode.com/users/${userId}","method":"GET"}]', 1);

-- SCRIPT模式：使用Groovy模板脚本进行复杂API编排
MERGE INTO ds_config (id, name, description, source_type, api_method, api_url, api_timeout, api_mode, api_auth_type, template_id, enabled) KEY(id) VALUES
(103, '示例-脚本编排(Groovy)', 'SCRIPT模式：使用Groovy脚本实现链式API调用，支持条件判断和循环', 'API', 'GET', '', 30, 'SCRIPT', 'NONE', 101, 1);

-- ============================================
-- 示例数据：列配置 & 数据对接模板
-- ============================================

-- 列配置：定义可用的输入/输出列
MERGE INTO column_config (id, name, description, columns_json) KEY(id) VALUES
(101, '示例-用户字段配置', '用户相关字段的列定义，用于数据对接时的字段映射',
 '[{"key":"user_name","value":"用户名"},{"key":"email","value":"邮箱"},{"key":"phone","value":"电话"},{"key":"address","value":"地址"}]');

-- 数据对接模板：将JSONPlaceholder返回的用户字段映射到中文列
MERGE INTO mapping_template (id, name, description, ds_config_id, column_config_id, mappings) KEY(id) VALUES
(101, '示例-用户数据对接', '将API返回的英文用户字段映射到中文列配置', 101, 101,
 '[{"receiveKey":"username","pushKey":"user_name"},{"receiveKey":"email","pushKey":"email"},{"receiveKey":"phone","pushKey":"phone"}]');

-- ============================================
-- OpenAPI 系列模板：基于旧版DataCenter的open_api逻辑提炼为Groovy脚本模板
-- 这些模板可在"模板管理"中使用，作为SCRIPT模式数据源的执行脚本
-- 使用时根据实际接口修改配置区的URL、字段名等参数
-- ============================================

-- 模板1: Token认证 + 分页获取全量数据（综合场景）
MERGE INTO template (id, name, category_id, content, type, tags, is_deleted, version) KEY(id) VALUES
(200, 'OpenAPI-Token认证+分页获取全量数据', 23,
'// ============================================
// OpenAPI模板：Token认证 + 分页获取全量数据
// 来源：旧版DataCenter open_api核心逻辑
//
// 使用方式：在数据源配置中选择SCRIPT模式，关联此模板
// 模板参数(params)可配置：
//   - username: 登录用户名
//   - password: 登录密码
// 输出(out)：
//   - success: 是否成功
//   - data: 全量数据列表
//   - totalPages: 总页数
//   - totalRecords: 总记录数
// ============================================

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

// ==================== 配置区（根据实际接口修改） ====================
def API_URL = "https://api.example.com/data/list"
def TOKEN_URL = "https://api.example.com/auth/token"
def USERNAME_FIELD = "username"       // token接口的用户名字段名
def PASSWORD_FIELD = "password"       // token接口的密码字段名
def TOKEN_FIELD = "access_token"      // token响应中的token字段名
def TOKEN_IS_POST = true              // token接口是否POST
def API_IS_POST = false               // 业务接口是否POST
def PAGE_FIELD = "pageNo"             // 页码字段名
def LIMIT_FIELD = "pageSize"          // 每页条数字段名
def PAGE_SIZE = 100                   // 每页条数
def START_PAGE = 1                    // 起始页码
def DATA_PATH = "data.records"        // 响应JSON中数据列表的路径（点号分隔）
// ==================================================================

// 读取params中的用户名密码（优先使用params，其次使用硬编码默认值）
def username = params?.username ?: "admin"
def password = params?.password ?: "123456"

def allData = []
def currentPage = START_PAGE
def totalPages = 0

out.success = false

// ==================== Step 1: 获取Token ====================
try {
    log?.info("Step 1: 获取Token - ${TOKEN_URL}")

    def tokenBody = JsonOutput.toJson([
        (USERNAME_FIELD): username,
        (PASSWORD_FIELD): password
    ])
    def tokenHeaders = ["Content-Type": "application/json"]

    def tokenResp
    if (TOKEN_IS_POST) {
        tokenResp = http.post(TOKEN_URL, tokenBody, tokenHeaders)
    } else {
        tokenResp = http.get(TOKEN_URL, tokenHeaders)
    }

    if (!tokenResp.success || tokenResp.status != 200) {
        out.error = "Token获取失败: HTTP ${tokenResp.status}"
        return
    }

    // 从响应中提取Token
    def tokenData = tokenResp.data
    def token = null
    if (tokenData instanceof Map) {
        token = tokenData[TOKEN_FIELD]
    }
    if (!token) {
        out.error = "Token响应中未找到字段: ${TOKEN_FIELD}"
        return
    }
    log?.info("Token获取成功")

    // ==================== Step 2: 分页循环获取数据 ====================
    def apiHeaders = [
        "Authorization": "Bearer ${token}",
        "Content-Type": "application/json"
    ]

    while (true) {
        log?.info("Step 2: 获取第 ${currentPage} 页数据")

        // 构建带分页参数的URL
        def urlWithParams = API_URL
        def separator = API_URL.contains("?") ? "&" : "?"
        urlWithParams += "${separator}${PAGE_FIELD}=${currentPage}&${LIMIT_FIELD}=${PAGE_SIZE}"

        def apiResp
        if (API_IS_POST) {
            // POST方式：分页参数放在body中
            def bodyMap = [
                (PAGE_FIELD): currentPage,
                (LIMIT_FIELD): PAGE_SIZE
            ]
            apiResp = http.post(API_URL, JsonOutput.toJson(bodyMap), apiHeaders)
        } else {
            apiResp = http.get(urlWithParams, apiHeaders)
        }

        if (!apiResp.success || apiResp.status != 200) {
            out.error = "API调用失败(第${currentPage}页): HTTP ${apiResp.status}"
            return
        }

        // 解析响应，按路径提取数据列表
        def respData = apiResp.data
        if (!(respData instanceof Map)) {
            out.error = "响应格式错误(第${currentPage}页): 期望JSON对象"
            return
        }

        // 按路径导航到数据列表
        def pageData = respData
        if (DATA_PATH) {
            def paths = DATA_PATH.split("\\.")
            for (def path : paths) {
                if (pageData instanceof Map) {
                    pageData = pageData[path]
                } else {
                    break
                }
            }
        }

        if (!(pageData instanceof List)) {
            out.error = "数据路径[${DATA_PATH}]不是数组(第${currentPage}页)"
            return
        }

        def pageSize = pageData.size()
        allData.addAll(pageData)
        log?.info("第 ${currentPage} 页获取 ${pageSize} 条，累计 ${allData.size()} 条")

        // 判断是否还有下一页（核心逻辑：返回量 < pageSize 则到最后一页）
        if (pageSize < PAGE_SIZE) {
            totalPages = currentPage
            break
        }

        currentPage++

        // 安全上限：最多获取100页
        if (currentPage - START_PAGE > 100) {
            log?.warn("已达最大页数上限100页，停止循环")
            totalPages = currentPage
            break
        }
    }

    out.success = true
    out.data = allData
    out.totalPages = totalPages
    out.totalRecords = allData.size()
    log?.info("完成：共获取 ${allData.size()} 条数据，${totalPages} 页")

} catch (Exception e) {
    out.success = false
    out.error = "执行异常: ${e.message}"
    log?.error("OpenAPI模板执行异常", e)
}',
'CUSTOM', 'OpenAPI,Token认证,分页,综合', 0, 1);

-- 模板2: 分页循环获取全量数据（无需认证）
MERGE INTO template (id, name, category_id, content, type, tags, is_deleted, version) KEY(id) VALUES
(201, 'OpenAPI-分页循环获取全量数据', 21,
'// ============================================
// OpenAPI模板：分页循环获取全量数据（无需认证）
// 来源：旧版DataCenter open_api 分页循环逻辑
//
// 核心逻辑：while(dataSize == pageSize) 持续翻页
// 退出条件：返回数据量 < pageSize 或达到最大页数上限
// ============================================

// ==================== 配置区 ====================
def API_URL = "https://api.example.com/data/list"
def IS_POST = false
def PAGE_FIELD = "page"
def LIMIT_FIELD = "size"
def PAGE_SIZE = 100
def START_PAGE = 1
def DATA_PATH = "data.list"           // JSON中数据数组的路径
def MAX_PAGES = 50                    // 最大页数安全上限
// ==============================================

def allData = []
def currentPage = START_PAGE

out.success = false

try {
    while (true) {
        // 构建URL（GET方式直接在URL带分页参数）
        def url = API_URL
        def sep = API_URL.contains("?") ? "&" : "?"
        url += "${sep}${PAGE_FIELD}=${currentPage}&${LIMIT_FIELD}=${PAGE_SIZE}"

        def resp = http.get(url, null)

        if (!resp.success || resp.status != 200) {
            out.error = "第${currentPage}页请求失败: HTTP ${resp.status}"
            return
        }

        // 按路径提取数据
        def pageData = resp.data
        if (DATA_PATH && pageData instanceof Map) {
            DATA_PATH.split("\\.").each { path ->
                if (pageData instanceof Map) pageData = pageData[path]
            }
        }

        if (!(pageData instanceof List)) {
            out.error = "数据路径[${DATA_PATH}]解析失败，类型: ${pageData?.class?.simpleName}"
            return
        }

        def count = pageData.size()
        allData.addAll(pageData)

        // 退出条件：返回量 < 页大小
        if (count < PAGE_SIZE) break

        currentPage++
        if (currentPage - START_PAGE >= MAX_PAGES) {
            log?.warn("达到最大页数上限${MAX_PAGES}，停止翻页")
            break
        }
    }

    out.success = true
    out.data = allData
    out.totalPages = currentPage - START_PAGE + 1
    out.totalRecords = allData.size()

} catch (Exception e) {
    out.success = false
    out.error = "分页获取异常: ${e.message}"
}',
'CUSTOM', 'OpenAPI,分页,循环', 0, 1);

-- 模板3: JSON嵌套响应解析与字段映射
MERGE INTO template (id, name, category_id, content, type, tags, is_deleted, version) KEY(id) VALUES
(202, 'OpenAPI-JSON嵌套响应解析与字段映射', 22,
'// ============================================
// OpenAPI模板：JSON嵌套响应解析与字段映射
// 来源：旧版DataCenter analyzeJsonMap逻辑
//
// 功能：将嵌套JSON响应中的数组数据解析为扁平记录列表
// 支持多层嵌套、字段重命名、默认值、过滤
// ============================================

// ==================== 字段映射配置 ====================
// 格式：[from: "源字段", to: "目标字段", defaultValue: "默认值"]
def FIELD_MAPPING = [
    [from: "id",           to: "record_id",   defaultValue: ""],
    [from: "name",         to: "user_name",   defaultValue: "未知"],
    [from: "email",        to: "email_addr",  defaultValue: ""],
    [from: "status",       to: "status_code", defaultValue: "0"],
    [from: "createTime",   to: "create_time", defaultValue: ""]
]

// 响应中数据数组的路径（点号分隔），如 "data.records"
def DATA_PATH = "data.records"

// 过滤条件（可选）：只保留 filterField 值在 filterValues 中的记录
def FILTER_FIELD = ""          // 如 "status"
def FILTER_VALUES = []         // 如 ["active", "pending"]
// ==================================================================

out.success = false

try {
    // 从 rawResponse 或 input 获取API响应数据
    def respData = out.rawResponse ?: input

    if (!respData) {
        out.error = "无输入数据"
        return
    }

    // === Step 1: 按路径导航到数据数组 ===
    def dataList = respData
    if (DATA_PATH) {
        DATA_PATH.split("\\.").each { path ->
            if (dataList instanceof Map) {
                dataList = dataList[path]
            }
        }
    }

    if (!(dataList instanceof List)) {
        out.error = "路径[${DATA_PATH}]未找到数组数据，实际类型: ${dataList?.class?.simpleName}"
        return
    }

    // === Step 2: 逐条映射字段 ===
    def result = []
    for (def item : dataList) {
        if (!(item instanceof Map)) continue

        def record = [:]
        for (def mapping : FIELD_MAPPING) {
            def value = item[mapping.from]
            if (value == null) {
                value = mapping.defaultValue ?: ""
            }
            record[mapping.to] = value
        }

        // 过滤检查
        if (FILTER_FIELD && FILTER_VALUES) {
            def filterVal = record[FILTER_FIELD]?.toString()
            if (!FILTER_VALUES.contains(filterVal)) continue
        }

        result.add(record)
    }

    out.success = true
    out.data = result
    out.totalRecords = result.size()
    out.message = "解析完成：${dataList.size()}条原始数据 -> ${result.size()}条映射结果"

} catch (Exception e) {
    out.success = false
    out.error = "JSON解析异常: ${e.message}"
}',
'CUSTOM', 'OpenAPI,JSON解析,字段映射', 0, 1);

-- 模板4: Basic认证API调用
MERGE INTO template (id, name, category_id, content, type, tags, is_deleted, version) KEY(id) VALUES
(203, 'OpenAPI-Basic认证API调用', 20,
'// ============================================
// OpenAPI模板：Basic认证API调用
// 来源：旧版DataCenter nameField/nameValue/passwordField/passwordValue 认证逻辑
//
// 使用方式：在数据源配置中选择SCRIPT模式，关联此模板
// params: username, password
// ============================================

// ==================== 配置区 ====================
def API_URL = "https://api.example.com/data"
def IS_POST = false
def REQUEST_BODY = null  // POST时的请求体JSON字符串
// ==============================================

def username = params?.username ?: "admin"
def password = params?.password ?: "123456"

out.success = false

try {
    // Basic认证：Base64编码 username:password
    def credential = "${username}:${password}".getBytes("UTF-8").encodeBase64().toString()
    def headers = [
        "Authorization": "Basic ${credential}",
        "Content-Type": "application/json"
    ]

    def resp
    if (IS_POST) {
        resp = http.post(API_URL, REQUEST_BODY ?: "", headers)
    } else {
        resp = http.get(API_URL, headers)
    }

    if (!resp.success) {
        out.error = "API调用失败: HTTP ${resp.status}"
        return
    }

    out.success = true
    out.data = resp.data
    out.status = resp.status

} catch (Exception e) {
    out.success = false
    out.error = "Basic认证调用异常: ${e.message}"
}',
'CUSTOM', 'OpenAPI,Basic认证', 0, 1);

-- 模板5: API链式调用（Token→业务接口→分页全量获取）
MERGE INTO template (id, name, category_id, content, type, tags, is_deleted, version) KEY(id) VALUES
(204, 'OpenAPI-完整链式调用（登录→查数据→分页全量）', 23,
'// ============================================
// OpenAPI模板：完整API链式调用
// 来源：旧版DataCenter handle() 完整执行流程
//
// 流程：登录获取Token -> 调业务接口 -> 分页循环获取全量数据 -> 返回
// 这是一个可直接用于生产环境的完整模板
// ============================================

import groovy.json.JsonOutput

// ==================== 配置区 ====================
// -- Token接口 --
def TOKEN_URL = "https://api.example.com/auth/login"
def TOKEN_IS_POST = true
def USERNAME_FIELD = "username"
def PASSWORD_FIELD = "password"
def TOKEN_PATH = "data.token"          // 响应JSON中token的路径

// -- 业务接口 --
def API_URL = "https://api.example.com/data/list"
def API_IS_POST = false

// -- 分页配置 --
def PAGE_FIELD = "pageNo"
def LIMIT_FIELD = "pageSize"
def PAGE_SIZE = 100
def START_PAGE = 1
def DATA_PATH = "data.records"
def MAX_PAGES = 50

// -- 认证凭据 --
def username = params?.username ?: "admin"
def password = params?.password ?: "123456"
// ==================================================================

def allData = []
def currentPage = START_PAGE

out.success = false

try {
    // ========== Phase 1: 获取Token ==========
    def loginBody = JsonOutput.toJson([
        (USERNAME_FIELD): username,
        (PASSWORD_FIELD): password
    ])
    def loginResp = TOKEN_IS_POST ?
        http.post(TOKEN_URL, loginBody, ["Content-Type": "application/json"]) :
        http.get(TOKEN_URL, null)

    if (!loginResp.success || loginResp.status != 200) {
        out.error = "Phase 1 登录失败: HTTP ${loginResp.status}"
        out.phase = "login"
        return
    }

    // 按路径提取token
    def token = loginResp.data
    if (TOKEN_PATH) {
        TOKEN_PATH.split("\\.").each { path ->
            if (token instanceof Map) token = token[path]
        }
    }
    if (!token) {
        out.error = "Phase 1 未提取到Token，路径: ${TOKEN_PATH}"
        out.phase = "login"
        return
    }

    def apiHeaders = [
        "Authorization": "Bearer ${token}",
        "Content-Type": "application/json"
    ]

    // ========== Phase 2: 分页获取全量数据 ==========
    while (true) {
        def url = API_URL
        def sep = API_URL.contains("?") ? "&" : "?"
        url += "${sep}${PAGE_FIELD}=${currentPage}&${LIMIT_FIELD}=${PAGE_SIZE}"

        def resp = API_IS_POST ?
            http.post(API_URL, JsonOutput.toJson([(PAGE_FIELD): currentPage, (LIMIT_FIELD): PAGE_SIZE]), apiHeaders) :
            http.get(url, apiHeaders)

        if (!resp.success || resp.status != 200) {
            out.error = "Phase 2 第${currentPage}页失败: HTTP ${resp.status}"
            out.phase = "fetch"
            out.partialData = allData
            return
        }

        // 按路径提取数据数组
        def pageData = resp.data
        if (DATA_PATH && pageData instanceof Map) {
            DATA_PATH.split("\\.").each { path ->
                if (pageData instanceof Map) pageData = pageData[path]
            }
        }

        if (!(pageData instanceof List)) {
            out.error = "Phase 2 数据路径[${DATA_PATH}]不是数组"
            out.phase = "fetch"
            return
        }

        def count = pageData.size()
        allData.addAll(pageData)

        if (count < PAGE_SIZE) break

        currentPage++
        if (currentPage - START_PAGE >= MAX_PAGES) break
    }

    // ========== Phase 3: 返回结果 ==========
    out.success = true
    out.data = allData
    out.totalRecords = allData.size()
    out.totalPages = currentPage - START_PAGE + 1

} catch (Exception e) {
    out.success = false
    out.error = "链式调用异常: ${e.message}"
}',
'CUSTOM', 'OpenAPI,链式调用,Token,分页,完整流程', 0, 1);

-- 模板6: JSON多级嵌套数组提取
MERGE INTO template (id, name, category_id, content, type, tags, is_deleted, version) KEY(id) VALUES
(205, 'OpenAPI-JSON多级嵌套数组提取', 22,
'// ============================================
// OpenAPI模板：多级嵌套JSON数组提取
// 来源：旧版DataCenter analyzeJsonMap 多级嵌套处理逻辑
//
// 场景：API返回的JSON中有多层嵌套数组，如:
// { "data": { "categories": [ { "name":"A", "items": [...] } ] } }
// 需要提取 items 中的所有记录并扁平化
// ============================================

// ==================== 配置区 ====================
// 数据路径配置（从外到内逐级导航）
// 格式：[path: "字段名", isArray: 是否数组]
def PATH_CONFIG = [
    [path: "data",       isArray: false],
    [path: "records",    isArray: true]
]

// 最终需要提取的字段映射
def FIELD_MAPPING = [
    [from: "id",   to: "item_id"],
    [from: "name", to: "item_name"],
    [from: "type", to: "item_type"]
]
// ==============================================

out.success = false

try {
    // 获取原始响应
    def respData = out.rawResponse ?: input
    if (!respData) {
        out.error = "无输入数据"
        return
    }

    // === 按路径配置逐级导航 ===
    def current = respData
    for (int i = 0; i < PATH_CONFIG.size(); i++) {
        def cfg = PATH_CONFIG[i]
        if (!(current instanceof Map)) {
            out.error = "路径[${cfg.path}]导航失败: 当前节点不是对象"
            return
        }
        current = current[cfg.path]
        if (current == null) {
            out.error = "路径[${cfg.path}]不存在"
            return
        }
    }

    // 最终节点应该是数组，如果不是则包装
    if (!(current instanceof List)) {
        current = [current]
    }

    // === 扁平化提取字段 ===
    def result = []
    for (def item : current) {
        if (!(item instanceof Map)) continue
        def record = [:]
        for (def mapping : FIELD_MAPPING) {
            def value = item[mapping.from]
            record[mapping.to] = (value != null) ? value : ""
        }
        result.add(record)
    }

    out.success = true
    out.data = result
    out.totalRecords = result.size()

} catch (Exception e) {
    out.success = false
    out.error = "嵌套提取异常: ${e.message}"
}',
'CUSTOM', 'OpenAPI,JSON解析,嵌套数组', 0, 1);

-- ============================================
-- 示例模板：附件/文件处理（用于列配置特殊类型 file/blob/json 等）
-- ============================================
MERGE INTO template (id, name, category_id, content, type, tags, is_deleted, version) KEY(id) VALUES
(206, '示例-附件文件处理', 24,
'// ============================================
// 附件/文件处理模板
// 用途：对列配置中 file、blob、json 等特殊类型字段进行预处理
// 适用场景：
//   1. 文件路径 → Base64编码
//   2. Base64数据 → 提取文件类型/大小等元信息
//   3. JSON字符串 → 反序列化为对象
//   4. BLOB二进制 → 转Base64字符串或提取元数据
//
// 输入：input 中包含了当前列的值（key=列配置中的 key）
// 输出：out 中处理后的值（通常保持相同的 key）
// ============================================

// ==================== 配置区 ====================
// 处理模式：BASE64_ENCODE / BASE64_DECODE / JSON_PARSE / EXTRACT_META / PASS_THROUGH
def MODE = params.mode ?: "PASS_THROUGH"

// 数据键名（对应列配置中的 key）
def DATA_KEY = params.dataKey ?: input.keySet().first()

// 最大输出长度限制（避免超大内容撑爆内存）
def MAX_OUTPUT_LENGTH = (params.maxLength ?: 50000) as int
// ==============================================

try {
    def rawValue = input[DATA_KEY]
    if (rawValue == null) {
        out.success = false
        out.error = "输入中未找到键: ${DATA_KEY}"
        return
    }

    def strValue = rawValue instanceof String ? rawValue : String.valueOf(rawValue)

    switch (MODE) {
        case "BASE64_ENCODE":
            // 将文本/文件内容进行Base64编码
            byte[] bytes
            try {
                // 尝试作为文件路径读取
                def file = new File(strValue)
                if (file.exists() && file.isFile()) {
                    bytes = file.bytes
                    out.originalPath = strValue
                    out.fileName = file.name
                    out.fileSize = bytes.length
                } else {
                    bytes = strValue.getBytes("UTF-8")
                }
            } catch (Exception e) {
                bytes = strValue.getBytes("UTF-8")
            }
            out[DATA_KEY] = bytes.encodeBase64().toString()
            out.encoding = "base64"
            out.originalLength = strValue.length()
            out.encodedLength = out[DATA_KEY].length()
            break

        case "BASE64_DECODE":
            // Base64解码
            try {
                byte[] decoded = strValue.decodeBase64()
                // 尝试识别内容类型
                if (decoded.length > 4) {
                    // 检查是否为常见文件头
                    def header = decoded[0..3]
                    if (header[0] == (byte)0xFF && header[1] == (byte)0xD8) {
                        out.contentType = "image/jpeg"
                    } else if (header[0] == (byte)0x89 && header[1] == (byte)0x50) {
                        out.contentType = "image/png"
                    } else if (header[0] == (byte)0x25 && header[1] == (byte)0x50) {
                        out.contentType = "application/pdf"
                    } else {
                        out.contentType = "application/octet-stream"
                    }
                }
                out[DATA_KEY] = new String(decoded, "UTF-8")
                out.decodedLength = decoded.length
            } catch (Exception e) {
                out.success = false
                out.error = "Base64解码失败: ${e.message}"
                return
            }
            break

        case "JSON_PARSE":
            // 解析JSON字符串为对象
            try {
                def parsed = new groovy.json.JsonSlurper().parseText(strValue)
                if (parsed instanceof Map) {
                    out.putAll(parsed as Map)
                } else if (parsed instanceof List) {
                    out[DATA_KEY] = parsed
                    out.isArray = true
                    out.arrayLength = parsed.size()
                } else {
                    out[DATA_KEY] = parsed
                }
                out.parseSuccess = true
            } catch (Exception e) {
                out.success = false
                out.error = "JSON解析失败: ${e.message}"
                return
            }
            break

        case "EXTRACT_META":
            // 提取数据元信息
            out[DATA_KEY] = strValue
            out.dataType = rawValue.getClass().simpleName
            out.dataLength = strValue.length()
            out.isBase64 = (strValue ==~ /^[A-Za-z0-9+/=]+$/ && strValue.length() % 4 == 0)
            if (strValue.length() > MAX_OUTPUT_LENGTH) {
                out.truncated = true
                out[DATA_KEY] = strValue.substring(0, MAX_OUTPUT_LENGTH) + "...(truncated)"
            }
            break

        case "PASS_THROUGH":
        default:
            // 直接透传
            out[DATA_KEY] = strValue
            if (strValue.length() > MAX_OUTPUT_LENGTH) {
                out.truncated = true
                out[DATA_KEY] = strValue.substring(0, MAX_OUTPUT_LENGTH) + "...(truncated)"
            }
            break
    }

    out.success = true
    out.mode = MODE

} catch (Exception e) {
    out.success = false
    out.error = "附件处理异常: ${e.message}"
}',
'CUSTOM', '附件,文件,Base64,JSON,列配置特殊类型', 0, 1);

-- ============================================
-- 初始数据：模板代码片段 (编辑器右侧面板)
-- ============================================
MERGE INTO template_snippet (id, name, group_name, description, code, sort_order) KEY(id) VALUES
-- 字段处理
(1, 'out[""] = input[""]', '字段处理', '字段取值赋值', 'out["​"] = input["​"]', 1),
(2, 'out[""] = input[""] ?: ""', '字段处理', '带默认值', 'out["​"] = input["​"] ?: ""​', 2),
(3, '字符串拼接', '字段处理', '多字段合并', 'out["full_name"] = input["first"] + " " + input["last"]', 3),
(4, '?.toUpperCase()', '字段处理', '安全转大写', 'out["name"] = input["name"]?.toUpperCase()', 4),
-- 类型转换 & 计算
(5, 'as double', '类型转换 & 计算', '转为浮点数', 'out["​"] = input["​"] as double', 1),
(6, 'as int', '类型转换 & 计算', '转为整数', 'out["​"] = input["​"] as int', 2),
(7, '(x as double).round(2)', '类型转换 & 计算', '保留2位小数', 'out["​"] = (input["​"] as double).round(2)', 3),
(8, '数值计算', '类型转换 & 计算', '乘法示例', 'out["total"] = (input["price"] as double) * (input["qty"] as int)', 4),
-- 日期时间
(9, '当前时间格式化', '日期时间', '', 'import java.text.SimpleDateFormat
def sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
out["​"] = sdf.format(new Date())', 1),
(10, '时间戳 → 日期字符串', '日期时间', '', 'import java.text.SimpleDateFormat
def sdf = new SimpleDateFormat("yyyy-MM-dd")
out["​"] = sdf.format(new Date(Long.parseLong(input["​"] as String)))', 2),
(11, 'System.currentTimeMillis()', '日期时间', '当前时间戳', 'out["timestamp"] = System.currentTimeMillis()', 3),
-- 控制流程
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
-- JSON 处理
(16, 'JsonSlurper 解析', 'JSON 处理', 'JSON字符串→Map', 'import groovy.json.JsonSlurper
def parsed = new JsonSlurper().parseText(input["​"] as String)
out.putAll(parsed as Map)', 1),
(17, 'JsonOutput.toJson', 'JSON 处理', 'Map→JSON字符串', 'import groovy.json.JsonOutput
def json = JsonOutput.toJson(out)
return json', 2),
(18, 'prettyPrint', 'JSON 处理', '格式化输出', 'import groovy.json.JsonOutput
out["json"] = JsonOutput.prettyPrint(JsonOutput.toJson(input))', 3),
-- API 调用
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
-- 数据校验
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
-- 附件/文件处理
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

-- ============================================
-- 示例：API 输出数据源 & 对接流程
-- 演示如何通过接口将数据推送到其他系统
-- ============================================

-- API输出模板：逐条POST推送数据到目标系统
MERGE INTO template (id, name, category_id, content, type, tags, is_deleted, version) KEY(id) VALUES
(300, '示例-API逐条推送(数据输出)', 19,
'// ============================================
// API 输出模板：逐条推送数据到目标系统
// 使用场景：作为对接流程的输出端，将数据逐条 POST 到下游系统
//
// 输入(input)：流水线处理后的单行数据 Map
// 输出(out)：success / status / response
// ============================================

import groovy.json.JsonOutput

// ==================== 配置区 ====================
def API_URL = "https://httpbin.org/post"
def METHOD = "POST"
def HEADERS = [
    "Content-Type": "application/json",
    "Accept": "application/json"
]
// ==============================================

out.success = false

try {
    def body = JsonOutput.toJson(input)

    def resp
    if (METHOD == "POST") {
        resp = http.post(API_URL, body, HEADERS)
    } else if (METHOD == "PUT") {
        resp = http.put(API_URL, body, HEADERS)
    } else {
        out.error = "Unsupported method: ${METHOD}"
        return
    }

    if (!resp.success || (resp.status != 200 && resp.status != 201)) {
        out.error = "Push failed: HTTP ${resp.status}"
        out.response = resp.data
        return
    }

    out.success = true
    out.status = resp.status
    out.response = resp.data

} catch (Exception e) {
    out.success = false
    out.error = "Push error: ${e.message}"
}',
'CUSTOM', 'API输出,数据推送,POST', 0, 1);

-- API输出数据源：POST到 httpbin（用于测试API输出功能）
MERGE INTO ds_config (id, name, description, source_type, api_method, api_url, api_timeout, api_mode, api_auth_type, enabled) KEY(id) VALUES
(201, '示例-API输出(httpbin-post)', '作为对接流程的输出端，将数据逐条POST到 httpbin.org/post（httpbin会回显你发送的数据，方便验证推送是否成功）', 'API', 'POST', 'https://httpbin.org/post', 30, 'SINGLE', 'NONE', 1);

-- API输出数据源：SCRIPT模式，使用Groovy模板自定义推送逻辑
MERGE INTO ds_config (id, name, description, source_type, api_method, api_url, api_timeout, api_mode, api_auth_type, template_id, enabled) KEY(id) VALUES
(202, '示例-API输出(脚本推送)', 'SCRIPT模式API输出：使用Groovy模板自定义推送逻辑，适合需要签名、特殊编码、复杂请求头等场景', 'API', 'POST', '', 30, 'SCRIPT', 'NONE', 300, 1);

-- ============================================
-- 使用说明：如何测试 API 输出功能
-- ============================================
-- 1. 先创建一个 DB 输入数据源（如连接自带的 H2 数据库）
-- 2. 打开"对接流程" -> 新建流程
-- 3. 步骤1"选择数据源"：输入选你创建的 DB 数据源，输出选 "示例-API输出(httpbin-post)" (id=201)
-- 4. 步骤2"配置管线"：可选加字段映射或模板转换
-- 5. 步骤3"同步策略"：选全量同步
-- 6. 步骤4"执行"：点击执行，数据会逐条 POST 到 httpbin.org/post
--    httpbin 会回显你发送的 JSON 数据，可在调试日志中查看推送结果

-- ============================================
-- 示例：三种数据流向
-- ① DB -> API   ② API -> DB   ③ API -> API
-- ============================================

-- DB输入数据源：读取H2元数据库的ds_config表
MERGE INTO ds_config (id, name, description, source_type, db_type, host, port, db_name, table_name, username, password, charset, enabled) KEY(id) VALUES
(301, '示例-数据库输入(H2元数据)', '读取H2自带的ds_config表，包含所有数据源配置记录，用于演示DB->API流程', 'DB', 'H2', '', 0, './data/dataconnect;IFEXISTS=TRUE;AUTO_SERVER=TRUE', 'ds_config', 'sa', '', 'UTF-8', 1);

-- DB输出数据源：写入H2数据库的test_output表（表不存在时自动建表）
MERGE INTO ds_config (id, name, description, source_type, db_type, host, port, db_name, table_name, username, password, charset, enabled) KEY(id) VALUES
(302, '示例-数据库输出(H2-test_output)', '写入H2数据库的test_output表（自动建表），用于演示API->DB流程', 'DB', 'H2', '', 0, './data/dataconnect;IFEXISTS=TRUE;AUTO_SERVER=TRUE', 'test_output', 'sa', '', 'UTF-8', 1);

-- ============================================
-- 示例流程 ①：DB -> API（从数据库读取，推送到接口）
-- 输入: ds_config=301 (H2:ds_config表)
-- 输出: ds_config=201 (httpbin POST)
-- ============================================
MERGE INTO flow_config (id, name, description, input_ds_id, output_ds_id, sync_strategy, pipeline_config) KEY(id) VALUES
(401, '示例-DB到API推送', '从H2的ds_config表读取数据，逐条POST到httpbin.org/post', 301, 201, 'FULL', '[]');

-- ============================================
-- 示例流程 ②：API -> DB（从接口拉数据，写入数据库）
-- 输入: ds_config=101 (JSONPlaceholder GET)
-- 输出: ds_config=302 (H2:test_output表)
-- ============================================
MERGE INTO flow_config (id, name, description, input_ds_id, output_ds_id, sync_strategy, pipeline_config) KEY(id) VALUES
(402, '示例-API到数据库写入', '从JSONPlaceholder获取帖子数据，写入H2的test_output表', 101, 302, 'FULL', '[]');

-- ============================================
-- 示例流程 ③：API -> API（从接口拉数据，处理后推送到另一个接口）
-- 输入: ds_config=101 (JSONPlaceholder GET)
-- 输出: ds_config=201 (httpbin POST)
-- ============================================
MERGE INTO flow_config (id, name, description, input_ds_id, output_ds_id, sync_strategy, pipeline_config) KEY(id) VALUES
(403, '示例-API到API转发', '从JSONPlaceholder获取数据，逐条POST转发到httpbin.org/post', 101, 201, 'FULL', '[]');

-- ============================================
-- 模拟接口：宁波诺丁汉大学 - 学生论文数据
-- 流程：Token认证 → 获取论文列表 → 推送到目标接口
-- ============================================
-- 模板：宁波诺丁汉大学 - 学生论文数据（真实API）
MERGE INTO template (id, name, category_id, content, type, tags, is_deleted, version) KEY(id) VALUES
(400, '诺丁汉学生论文数据源', 23,
'// ============================================
// 宁波诺丁汉大学 学生论文接口 (真实API)
//
// 接口说明：
//   1. GET /ws/api/student-theses?size=10&offset=10 → 论文列表（Api-Key认证）
//   2. 根据返回的 count 和 offset/size 循环分页拉取全量数据
//   3. 论文下载: GET /student-theses/{uuid}/files/{fileId} (downloadBaseUrl, 需带Api-Key)
//
// params 可配置:
//   - apiBaseUrl: 元数据API基础地址 (默认 https://research.nottingham.edu.cn/ws/api)
//   - downloadBaseUrl: 下载API基础地址 (默认 https://research.nottingham.edu.cn/ws/api)
//   - apiKey: Api-Key (默认已配置)
//   - pageSize: 每页条数 (默认10，最大1000)
//   - downloadFiles: 是否下载PDF到本地 (默认false, 设为true则下载)
//   - downloadDir: PDF保存目录 (默认 ./downloads/theses)
// ============================================

import groovy.json.JsonSlurper

def API_BASE = params?.apiBaseUrl ?: "https://research.nottingham.edu.cn/ws/api"
def DOWNLOAD_BASE = params?.downloadBaseUrl ?: "https://research.nottingham.edu.cn/ws/api"
def API_KEY = params?.apiKey ?: "e3c5f52d-a905-43ac-a10c-4ea5255e368d"
def PAGE_SIZE = Math.min((params?.pageSize ?: 10) as int, 1000)
def MAX_RECORDS = (params?.maxRecords ?: 0) as int  // 0=不限制, >0=最多拉取N条

def headers = [
    "Api-Key": API_KEY,
    "Accept": "application/json"
]

out.success = false

// 分页循环拉取全部论文数据
def allItems = []
def offset = 0
def totalCount = 0

while (true) {
    def pageUrl = "${API_BASE}/student-theses?size=${PAGE_SIZE}&offset=${offset}".toString()
    def pageResp = http.get(pageUrl, headers)

    if (!pageResp.success || pageResp.status != 200) {
        out.error = "论文数据获取失败(offset=${offset}): HTTP ${pageResp.status}".toString()
        return
    }

    def pageData = pageResp.data
    def pageItems = pageData?.items ?: []
    totalCount = pageData?.count ?: 0
    allItems.addAll(pageItems)

    // 达到最大条数限制则退出
    if (MAX_RECORDS > 0 && allItems.size() >= MAX_RECORDS) break
    // 当前页不足一页 或 已拉取全部数据，退出循环
    if (pageItems.size() < PAGE_SIZE || allItems.size() >= totalCount) break
    offset += PAGE_SIZE
}

// 截断超出部分
if (MAX_RECORDS > 0 && allItems.size() > MAX_RECORDS) {
    allItems = allItems.take(MAX_RECORDS)
}

// 展平嵌套字段为扁平 key，兼容映射模板
def items = allItems.collect { item ->
    // ---- 基本标识 ----
    def primaryId = item.identifiers?.find { it.typeDiscriminator == "PrimaryId" }
    def firstContributor = item.contributors?.getAt(0)
    def firstSupervisor = item.supervisors?.getAt(0)
    def firstOrg = item.organizations?.getAt(0)
    def date = item.awardDate

    // ---- 作者信息 (contributors 替代旧 personAssociations) ----
    def authorFirst = firstContributor?.name?.firstName ?: ""
    def authorLast = firstContributor?.name?.lastName ?: ""
    def authorPersonId = firstContributor?.person?.uuid ?: firstContributor?.externalPerson?.uuid ?: ""

    // ---- 导师信息 ----
    def supervisorFirst = firstSupervisor?.name?.firstName ?: ""
    def supervisorLast = firstSupervisor?.name?.lastName ?: ""
    def supervisorPersonId = firstSupervisor?.person?.uuid ?: firstSupervisor?.externalPerson?.uuid ?: ""
    def allSupervisors = item.supervisors?.collect { s ->
        "${s?.name?.firstName ?: ""} ${s?.name?.lastName ?: ""}".trim()
    }?.findAll { it }?.join("; ") ?: ""

    // ---- 关键词 (FreeKeywordsKeywordGroup) ----
    def freeKwGroup = item.keywordGroups?.find { it.typeDiscriminator == "FreeKeywordsKeywordGroup" }
    def kwList = freeKwGroup?.keywords?.find { it.locale == "en_GB" }?.freeKeywords
    def keywords = kwList ? kwList.join(", ") : ""

    // ---- 学科分类 (ClassificationsKeywordGroup) ----
    def classGroup = item.keywordGroups?.find { it.typeDiscriminator == "ClassificationsKeywordGroup" }
    def subjectTerms = classGroup?.classifications?.collect { it.term }
    def subjects = subjectTerms?.collect { it."en_GB" }?.findAll { it }?.join("; ") ?: ""
    def subjectsZh = subjectTerms?.collect { it."zh_CN" }?.findAll { it }?.join("; ") ?: ""

    // ---- 摘要 (扁平对象 en_GB/zh_CN，非数组) ----
    def abstractEn = item.abstract?.get("en_GB") ?: ""
    def abstractCn = item.abstract?.get("zh_CN") ?: ""

    // ---- 论文类型 ----
    def degreeEn = item.type?.term?.get("en_GB") ?: ""
    def degreeZh = item.type?.term?.get("zh_CN") ?: ""

    // ---- 语言 ----
    def language = item.language?.term?.get("en_GB") ?: ""
    def languageZh = item.language?.term?.get("zh_CN") ?: ""

    // ---- 可见性 ----
    def visibility = item.visibility?.key ?: ""
    def visibilityDesc = item.visibility?.description?.get("en_GB") ?: ""
    def visibilityDescZh = item.visibility?.description?.get("zh_CN") ?: ""

    // ---- 工作流状态 ----
    def status = item.workflow?.step ?: ""
    def statusDesc = item.workflow?.description?.get("en_GB") ?: ""
    def statusDescZh = item.workflow?.description?.get("zh_CN") ?: ""

    // ====== 构建文档下载地址: {downloadBase}/student-theses/{uuid}/files/{fileId} ======
    // 为所有文档构建 downloadUrl（下载API需带 Api-Key header）
    def documents = item.documents?.collect { doc ->
        if (doc.fileId && item.uuid) {
            doc.downloadUrl = "${DOWNLOAD_BASE}/student-theses/${item.uuid}/files/${doc.fileId}".toString()
        }
        return doc
    }
    // 仅 PDF 类型的文档
    def pdfDocs = documents?.findAll { it.mimeType == "application/pdf" } ?: []

    // ====== 扁平字段 ======
    item.id = primaryId?.value ?: item.uuid ?: ""               // eprint ID 或 UUID
    item.pureId = item.pureId ?: ""                              // Pure 系统 ID
    item.uuid = item.uuid ?: ""                                  // UUID (下载必需)
    item.title = item.title?.value ?: ""                         // 论文标题
    item.portalUrl = item.portalUrl ?: ""                        // 门户页面 URL
    item.version = item.version ?: ""                            // 版本号
    item.createdBy = item.createdBy ?: ""                        // 创建者
    item.createdDate = item.createdDate ?: ""                   // 创建日期
    item.modifiedBy = item.modifiedBy ?: ""                     // 修改者
    item.modifiedDate = item.modifiedDate ?: ""                 // 修改日期

    // 作者
    item.author = "${authorFirst} ${authorLast}".trim()
    item.authorFirst = authorFirst
    item.authorLast = authorLast
    item.authorPersonId = authorPersonId

    // 导师
    item.supervisor = "${supervisorFirst} ${supervisorLast}".trim()
    item.supervisors = allSupervisors
    item.supervisorPersonId = supervisorPersonId

    // 院系 (organizations 只有 uuid，此处保留 UUID 引用)
    item.orgUuid = firstOrg?.uuid ?: ""
    item.managingOrgUuid = item.managingOrganization?.uuid ?: ""

    // 学位 & 语言
    item.degree = degreeEn
    item.degreeZh = degreeZh
    item.language = language
    item.languageZh = languageZh

    // 日期
    item.publishYear = date?.year
    item.submissionDate = date ? "${date.year}-${String.format("%02d", date.month ?: 1)}-${String.format("%02d", date.day ?: 1)}".toString() : ""

    // 摘要
    item.abstract_en = abstractEn
    item.abstract_cn = abstractCn

    // 关键词 & 学科
    item.keywords = keywords
    item.subjects = subjects
    item.subjectsZh = subjectsZh

    // 可见性 & 状态
    item.visibility = visibility
    item.visibilityDesc = visibilityDesc
    item.visibilityDescZh = visibilityDescZh
    item.status = status                    // approved / draft / ...
    item.statusDesc = statusDesc
    item.statusDescZh = statusDescZh

    // 附件
    item.documents = documents              // 全部文档（含 downloadUrl）
    item.pdf_count = pdfDocs.size()
    item.pdf_url = pdfDocs.getAt(0)?.downloadUrl ?: ""
    item.pdf_fileName = pdfDocs.getAt(0)?.fileName ?: ""
    item.pdf_size = pdfDocs.getAt(0)?.size ?: 0

    // 下载后的本地路径（由下方下载阶段填充）
    item.pdf_localPath = ""
    item.pdf_downloaded = false
    item.pdf_downloadError = ""

    return item
}

// ====== 文件下载阶段 ======
// 通过 params.downloadFiles=true 启用，将PDF下载到本地磁盘
def shouldDownload = params?.downloadFiles?.toString() == "true"
if (shouldDownload) {
    def downloadDir = params?.downloadDir ?: "./downloads/theses"
    def dlDir = new File(downloadDir)
    if (!dlDir.exists()) dlDir.mkdirs()

    items.eachWithIndex { item, idx ->
        def pdfDoc = item.documents?.find { it.mimeType == "application/pdf" && it.downloadUrl }
        if (!pdfDoc) {
            item.pdf_downloadError = "无可下载的PDF"
            return
        }

        def fileName = pdfDoc.fileName ?: "${item.uuid}.pdf"
        def localFile = new File(dlDir, fileName)

        // 已存在则跳过
        if (localFile.exists() && localFile.length() > 0) {
            item.pdf_localPath = localFile.absolutePath
            item.pdf_downloaded = true
            return
        }

        try {
            def dlConn = new URL(pdfDoc.downloadUrl).openConnection()
            dlConn.setConnectTimeout(10000)
            dlConn.setReadTimeout(120000)
            dlConn.setRequestProperty("Api-Key", API_KEY)

            if (dlConn.getResponseCode() != 200) {
                item.pdf_downloadError = "HTTP ${dlConn.getResponseCode()}"
                return
            }

            def fileBytes = dlConn.getInputStream().bytes
            def fos = new FileOutputStream(localFile)
            fos.write(fileBytes)
            fos.close()
            dlConn.getInputStream().close()

            item.pdf_localPath = localFile.absolutePath
            item.pdf_downloaded = true
        } catch (Exception e) {
            item.pdf_downloadError = e.message
            if (localFile.exists()) localFile.delete()
        }
    }
}

out.success = true
out.count = totalCount
out.pageInformation = [offset: 0, size: items.size()]
out.items = items
out.totalRecords = items.size()
out.message = "API获取成功: ${items.size()} 条论文记录".toString()
if (shouldDownload) {
    def downloaded = items.count { it.pdf_downloaded }
    out.message += ", PDF下载: ${downloaded}/${items.size()}"
}
out.downloadBaseUrl = DOWNLOAD_BASE
out.downloadApiKey = API_KEY
',
'CUSTOM', '宁波诺丁汉,论文数据,API输出,真实接口', 0, 1);

-- API输入数据源：诺丁汉学生论文接口（SCRIPT模式）

MERGE INTO ds_config (id, name, description, source_type, api_method, api_url, api_timeout, api_mode, api_auth_type, template_id, enabled) KEY(id) VALUES
(401, '学生论文数据源(诺丁汉)', '宁波诺丁汉大学学生论文真实API。Api-Key认证，分页循环拉取全量数据(size默认10最大1000)。配置downloadFiles=true可将PDF下载到本地(downloadDir指定目录)', 'API', 'GET', '', 30, 'SCRIPT', 'NONE', 400, 1);

-- ============================================
-- 示例流程 ④：API → API
-- 诺丁汉论文数据源 → httpbin POST
-- ============================================
MERGE INTO flow_config (id, name, description, input_ds_id, output_ds_id, sync_strategy, pipeline_config) KEY(id) VALUES
(404, '示例-论文数据API转发', '从诺丁汉学生论文API读取数据，逐条POST推送到httpbin.org/post，演示API→API数据转发', 401, 201, 'FULL', '[]');


-- ============================================
-- 档案管理系统 Open API 接口模板
-- 目标: http://localhost:8080/open_api/
-- 认证: POST /open_api/gettoken {appkey, appsecret} -> JWT token
-- ============================================

-- 模板：档案系统 - add_file 数据推送

-- API输出数据源：档案系统 add_file
MERGE INTO ds_config (id, name, description, source_type, api_method, api_url, api_timeout, api_mode, api_auth_type, template_id, enabled) KEY(id) VALUES
(501, '档案系统-写元数据(add_file)', 'SCRIPT模式：先获取JWT Token，再将数据POST到档案管理系统 /open_api/add_file。需在params中配置appkey/appsecret/ccode', 'API', 'POST', '', 60, 'SCRIPT', 'NONE', 500, 1);

-- ============================================
-- 示例流程 5：模拟论文 -> 档案系统 add_file
-- API->API: 从模拟论文接口读取 -> 推送到档案管理系统
-- ============================================
MERGE INTO flow_config (id, name, description, input_ds_id, output_ds_id, sync_strategy, pipeline_config) KEY(id) VALUES
(405, '示例-论文数据到档案系统', '从诺丁汉论文数据源读取，逐条推送到档案管理系统的 /open_api/add_file。使用前需在输出数据源(501)的params中配置正确的appkey/appsecret/ccode', 401, 501, 'FULL', '[]');

-- Update template 500 with RSA encryption + correct form format


-- Template 502: archive system add_file with RSA encryption and form format
-- NOTE: all strings in Groovy code use double quotes to avoid SQL single-quote conflict
MERGE INTO template (id, name, category_id, content, type, tags, is_deleted, version) KEY(id) VALUES
(502, '档案系统-写档案元数据(add_file)', 19,
'// ============================================
// 档案管理系统 Open API - 添加档案元数据
// 目标接口: POST /open_api/add_file (已调通)
//
// 认证流程: RSA公钥加密密码 -> gettoken -> JWT
// 参数格式: application/x-www-form-urlencoded (NOT JSON)
//
// params可配置:
//   - apiUrl: 档案系统地址(默认http://localhost:8080)
//   - appkey: 系统标识(默认dba)
//   - password: 明文密码(默认Aa@12345)
//   - ccode: 表名代码(默认lwdj，论文登记表)
// ============================================

import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import groovy.json.JsonSlurper
import java.net.URLEncoder

def API_BASE = params?.apiUrl ?: "http://localhost:8080"
def APPKEY = params?.appkey ?: "dba"
def PASSWORD = params?.password ?: "Aa@12345"
def CCODE = params?.ccode ?: "lwdj"

// ---- Step 1: get RSA public key ----
def pubkeyResp = http.post(API_BASE + "/open_api/getPublicKey", "", ["Content-Type": "application/json"])
if (!pubkeyResp.success) {
    out.success = false; out.error = "get public key failed"; return
}
def pubkeyData = pubkeyResp.data
def publicKeyStr = pubkeyData?.data?.PublicKey ?: pubkeyData?.PublicKey
if (!publicKeyStr) {
    out.success = false; out.error = "public key format error"; return
}

// ---- Step 2: RSA encrypt password ----
def keyBytes = Base64.getDecoder().decode(publicKeyStr)
def keySpec = new X509EncodedKeySpec(keyBytes)
def keyFactory = KeyFactory.getInstance("RSA")
def publicKey = keyFactory.generatePublic(keySpec)
def cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
cipher.init(Cipher.ENCRYPT_MODE, publicKey)
def encryptedBytes = cipher.doFinal(PASSWORD.getBytes("UTF-8"))
def appsecret = Base64.getEncoder().encodeToString(encryptedBytes)

// ---- Step 3: get JWT token (form format) ----
def tokenParams = "appkey=" + URLEncoder.encode(APPKEY, "UTF-8") + "&appsecret=" + URLEncoder.encode(appsecret, "UTF-8")
def tokenResp = http.post(API_BASE + "/open_api/gettoken", tokenParams, [
    "Content-Type": "application/x-www-form-urlencoded"
])
if (!tokenResp.success || tokenResp.status != 200) {
    out.success = false; out.error = "get token failed: HTTP " + tokenResp.status; return
}
def tokenJson = tokenResp.data
if (tokenJson.code != 0) {
    out.success = false; out.error = "get token failed: " + tokenJson.msg; return
}
def token = tokenJson.data?.token
if (!token) {
    out.success = false; out.error = "token is empty"; return
}

// ---- Step 4: build form fields and POST ----
// 只发送简单值字段，过滤掉嵌套 Map/List 对象
def formFields = [:]
formFields.ccode = CCODE

input.each { key, value ->
    if (value != null && !(value instanceof Map) && !(value instanceof List)) {
        formFields[key.toString()] = value.toString()
    }
}

def formBody = formFields.collect { k, v ->
    URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v ?: "", "UTF-8")
}.join("&")

def addUrl = API_BASE + "/open_api/add_file?flag=" + URLEncoder.encode(CCODE, "UTF-8")
def addResp = http.post(addUrl, formBody, [
    "Content-Type": "application/x-www-form-urlencoded",
    "token": token
])

if (!addResp.success) {
    out.success = false; out.error = "add_file request failed"; return
}

def result = addResp.data
if (result.code == 0 || result.code == 200) {
    out.success = true
    out.maskey = result.data?.maskey
    out.message = result.msg ?: "ok"
} else {
    out.success = false
    out.error = result.msg ?: "unknown error"
    out.response = result
}',
'CUSTOM', '档案系统,open_api,add_file,RSA加密,form格式', 0, 1);

-- Update data source 501 to use new template 502
MERGE INTO ds_config (id, name, description, source_type, api_method, api_url, api_timeout, api_mode, api_auth_type, template_id, enabled) KEY(id) VALUES
(501, '档案系统-写元数据(add_file)', 'SCRIPT模式：RSA加密获取JWT，form格式POST到档案管理系统 /open_api/add_file。默认ccode=lwdj', 'API', 'POST', '', 60, 'SCRIPT', 'NONE', 502, 1);

-- Flow: mock thesis -> add_file (updated to use template 502 via ds_config 501)
MERGE INTO flow_config (id, name, description, input_ds_id, output_ds_id, sync_strategy, pipeline_config) KEY(id) VALUES
(405, '示例-论文数据到档案系统', '从诺丁汉论文数据源读取，逐条推送到档案管理系统的 /open_api/add_file(lwdj表)', 401, 501, 'FULL', '[]');


-- ============================================
-- 档案系统 add_file_tx 附件上传模板
-- 伪造PDF用于测试
-- ============================================

-- 模板 503：add_file_tx 附件上传（含伪造PDF）
MERGE INTO template (id, name, category_id, content, type, tags, is_deleted, version) KEY(id) VALUES
(503, '档案系统-上传附件(add_file_tx)', 19,
'// ============================================
// 档案管理系统 - 上传文件附件
// 目标接口: POST /open_api/add_file_tx (multipart/form-data)
// 包含一个伪造的PDF用于测试
//
// params可配置:
//   - apiUrl: 档案系统地址(默认http://localhost:8080)
//   - appkey: 系统标识(默认dba)
//   - password: 明文密码(默认Aa@12345)
//   - tbname: 表名(默认lwdj)
//   - aid: 档案ID(从input中获取或手动指定)
// ============================================

import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import groovy.json.JsonSlurper
import java.net.URLEncoder
import java.net.HttpURLConnection
import java.net.URL

def API_BASE = params?.apiUrl ?: "http://localhost:8080"
def APPKEY = params?.appkey ?: "dba"
def PASSWORD = params?.password ?: "Aa@12345"
def TBNAME = params?.tbname ?: "lwdj"
// aid from input (set by previous add_file step) or params
def AID = params?.aid ?: input?.aid?.toString() ?: input?.id?.toString() ?: "0"

// ---- Step 1: get RSA public key ----
def pubkeyResp = http.post(API_BASE + "/open_api/getPublicKey", "", ["Content-Type": "application/json"])
if (!pubkeyResp.success) {
    out.success = false; out.error = "get public key failed"; return
}
def publicKeyStr = pubkeyResp.data?.data?.PublicKey ?: pubkeyResp.data?.PublicKey
if (!publicKeyStr) {
    out.success = false; out.error = "public key not found"; return
}

// ---- Step 2: RSA encrypt password ----
def keyBytes = Base64.getDecoder().decode(publicKeyStr)
def keySpec = new X509EncodedKeySpec(keyBytes)
def keyFactory = KeyFactory.getInstance("RSA")
def publicKey = keyFactory.generatePublic(keySpec)
def cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
cipher.init(Cipher.ENCRYPT_MODE, publicKey)
def encryptedBytes = cipher.doFinal(PASSWORD.getBytes("UTF-8"))
def appsecret = Base64.getEncoder().encodeToString(encryptedBytes)

// ---- Step 3: get JWT token ----
def tokenParams = "appkey=" + URLEncoder.encode(APPKEY, "UTF-8") + "&appsecret=" + URLEncoder.encode(appsecret, "UTF-8")
def tokenResp = http.post(API_BASE + "/open_api/gettoken", tokenParams, [
    "Content-Type": "application/x-www-form-urlencoded"
])
if (!tokenResp.success || tokenResp.status != 200) {
    out.success = false; out.error = "get token failed"; return
}
def token = tokenResp.data?.data?.token
if (!token) {
    out.success = false; out.error = "token is empty"; return
}

// ---- Step 4: generate fake PDF ----
def pdfTitle = input?.title?.toString() ?: "test"
def pdfAuthor = input?.author?.toString() ?: "unknown"
def pdfContent = "%PDF-1.4\n" +
    "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
    "2 0 obj<</Type/Pages/Count 1/Kids[3 0 R]>>endobj\n" +
    "3 0 obj<</Type/Page/MediaBox[0 0 612 792]/Parent 2 0 R/Resources<</Font<</F1 4 0 R>>>>>/Contents 5 0 R>>endobj\n" +
    "4 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\n" +
    "5 0 obj<</Length 44>>stream\n" +
    "BT /F1 24 Tf 100 700 Td (" + pdfTitle + ") Tj Tf ET\n" +
    "endstream\n" +
    "endobj\n" +
    "xref\n0 6\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n" +
    "0000000115 00000 n \n0000000210 00000 n \n0000000269 00000 n \n" +
    "trailer<</Size 6/Root 1 0 R>>\nstartxref\n361\n%%EOF"

def pdfBytes = pdfContent.getBytes("UTF-8")

// ---- Step 5: upload via add_file_tx (multipart/form-data) ----
def boundary = "----DataConnect" + System.currentTimeMillis()
def url = new URL(API_BASE + "/open_api/add_file_tx")
def conn = (HttpURLConnection) url.openConnection()
conn.setDoOutput(true)
conn.setRequestMethod("POST")
conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
conn.setRequestProperty("token", token)
conn.setConnectTimeout(30000)
conn.setReadTimeout(120000)

def fileName = (input?.title?.toString() ?: "document") + ".pdf"
fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_")

// Build multipart body
def CRLF = "\r\n"
def outStream = conn.getOutputStream()
def writer = new OutputStreamWriter(outStream, "UTF-8")

// maskey field (aid_tbname format, plain text OK per API)
def maskey = AID + "_" + TBNAME
writer.write("--" + boundary + CRLF)
writer.write("Content-Disposition: form-data; name=\"maskey\"" + CRLF + CRLF)
writer.write(maskey + CRLF)

// filename field
writer.write("--" + boundary + CRLF)
writer.write("Content-Disposition: form-data; name=\"filename\"" + CRLF + CRLF)
writer.write(fileName + CRLF)

// file field
writer.write("--" + boundary + CRLF)
writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + CRLF)
writer.write("Content-Type: application/pdf" + CRLF + CRLF)
writer.flush()
outStream.write(pdfBytes)
outStream.flush()
writer.write(CRLF)

// End boundary
writer.write("--" + boundary + "--" + CRLF)
writer.flush()
writer.close()

def respCode = conn.getResponseCode()
def respStream = (respCode == 200) ? conn.getInputStream() : conn.getErrorStream()
def respText = respStream.getText("UTF-8")
def result = new JsonSlurper().parseText(respText)
conn.disconnect()

if (result.code == 0 || result.code == 200) {
    out.success = true
    out.fileName = fileName
    out.fileSize = pdfBytes.length
    out.maskey = result.data?.maskey ?: maskey
    out.message = "file uploaded: " + fileName + " (" + pdfBytes.length + " bytes)"
} else {
    out.success = false
    out.error = result.msg ?: "upload failed"
    out.response = result
}',
'CUSTOM', '档案系统,open_api,add_file_tx,附件上传,PDF', 0, 1);

-- 数据源：add_file_tx 附件上传
MERGE INTO ds_config (id, name, description, source_type, api_method, api_url, api_timeout, api_mode, api_auth_type, template_id, enabled) KEY(id) VALUES
(503, '档案系统-上传附件(add_file_tx)', 'SCRIPT模式：RSA加密获取JWT -> 生成伪造PDF -> multipart上传到档案管理系统 /open_api/add_file_tx。默认tbname=lwdj', 'API', 'POST', '', 120, 'SCRIPT', 'NONE', 503, 1);

-- 示例流程：论文数据 -> 上传附件到档案系统
MERGE INTO flow_config (id, name, description, input_ds_id, output_ds_id, sync_strategy, pipeline_config) KEY(id) VALUES
(406, '示例-论文PDF到档案附件', '从诺丁汉论文数据源读取，为每条记录生成一个伪造PDF并通过 /open_api/add_file_tx 上传到档案系统的 u_lwdj_tx 附件表', 401, 503, 'FULL', '[]');


-- ============================================
-- 论文字段 → 档案表字段 映射配置
-- ============================================

-- 列配置：论文数据源输出字段（匹配真实API结构 + 扁平兼容字段）
MERGE INTO column_config (id, name, description, column_type, columns_json) KEY(id) VALUES
(201, '论文数据字段(输入)', '诺丁汉论文API输出的完整字段定义（含uuid、documents附件数组及自动构建的downloadUrl）', 'RECEIVE',
 '[{"key":"id","value":"论文编号(eprint ID或UUID)"},{"key":"pureId","value":"Pure系统ID"},{"key":"uuid","value":"UUID(下载必需)"},{"key":"title","value":"论文标题"},{"key":"portalUrl","value":"门户页面URL"},{"key":"version","value":"版本号"},{"key":"createdBy","value":"创建者"},{"key":"createdDate","value":"创建日期"},{"key":"modifiedBy","value":"修改者"},{"key":"modifiedDate","value":"修改日期"},{"key":"author","value":"作者全名"},{"key":"authorFirst","value":"作者名"},{"key":"authorLast","value":"作者姓"},{"key":"authorPersonId","value":"作者Person UUID"},{"key":"supervisor","value":"第一导师全名"},{"key":"supervisors","value":"全部导师(分号分隔)"},{"key":"supervisorPersonId","value":"导师Person UUID"},{"key":"orgUuid","value":"所属机构UUID"},{"key":"managingOrgUuid","value":"管理组织UUID"},{"key":"degree","value":"学位类型(英文)"},{"key":"degreeZh","value":"学位类型(中文)"},{"key":"language","value":"语言(英文)"},{"key":"languageZh","value":"语言(中文)"},{"key":"publishYear","value":"授予年份"},{"key":"submissionDate","value":"授予日期(yyyy-MM-dd)"},{"key":"abstract_en","value":"英文摘要"},{"key":"abstract_cn","value":"中文摘要"},{"key":"keywords","value":"自由关键词(逗号分隔)"},{"key":"subjects","value":"学科分类(英文)"},{"key":"subjectsZh","value":"学科分类(中文)"},{"key":"visibility","value":"可见性KEY"},{"key":"visibilityDesc","value":"可见性描述(英文)"},{"key":"visibilityDescZh","value":"可见性描述(中文)"},{"key":"status","value":"工作流状态KEY"},{"key":"statusDesc","value":"工作流状态描述(英文)"},{"key":"statusDescZh","value":"工作流状态描述(中文)"},{"key":"documents","value":"全部附件(含downloadUrl)"},{"key":"pdf_count","value":"PDF附件数量"},{"key":"pdf_url","value":"第一PDF下载地址"},{"key":"pdf_fileName","value":"第一PDF文件名"},{"key":"pdf_size","value":"第一PDF文件大小(字节)"}]');

-- 列配置：档案表目标字段
MERGE INTO column_config (id, name, description, column_type, columns_json) KEY(id) VALUES
(202, '档案表字段(输出-lwdj)', '档案管理系统u_lwdj表常用字段', 'PUSH',
 '[{"key":"title","value":"题名(wh)"},{"key":"author","value":"责任者(zrz)"},{"key":"c1","value":"一级目录"},{"key":"c2","value":"二级目录"},{"key":"c3","value":"三级目录"},{"key":"nd","value":"年度"},{"key":"bgq","value":"保管期限"},{"key":"ztc","value":"主题词"},{"key":"tx","value":"提要"},{"key":"dw","value":"单位"},{"key":"djsj","value":"登记时间"},{"key":"ccode","value":"表名代码"}]');

-- 字段映射模板：论文字段 → 档案表字段
MERGE INTO mapping_template (id, name, description, ds_config_id, column_config_id, mappings) KEY(id) VALUES
(201, '论文→档案字段映射(lwdj)', '将诺丁汉论文数据的英文字段映射到档案系统lwdj表的中文字段', 401, 202,
 '[{"receiveKey":"title","pushKey":"title"},{"receiveKey":"author","pushKey":"author"},{"receiveKey":"orgUuid","pushKey":"c1"},{"receiveKey":"degree","pushKey":"c2"},{"receiveKey":"publishYear","pushKey":"c3"},{"receiveKey":"submissionDate","pushKey":"nd"},{"receiveKey":"pdf_count","pushKey":"bgq"},{"receiveKey":"supervisor","pushKey":"dw"},{"receiveKey":"id","pushKey":"djsj"},{"receiveKey":"keywords","pushKey":"ztc"},{"receiveKey":"abstract_cn","pushKey":"tx"}]');

-- ============================================
-- 更新流程：加上字段映射管线步骤
-- pipeline_config: AFTER_READ阶段，类型MAPPING，引用mappingTemplateId=201
-- ============================================
MERGE INTO flow_config (id, name, description, input_ds_id, output_ds_id, sync_strategy, pipeline_config) KEY(id) VALUES
(405, '示例-论文数据到档案系统', '从诺丁汉论文数据源读取 → 字段映射 → 推送到档案管理系统 add_file(lwdj表)', 401, 501, 'FULL', '[{"position":"AFTER_READ","name":"论文字段映射","steps":[{"type":"MAPPING","mappingTemplateId":201}]}]');

MERGE INTO flow_config (id, name, description, input_ds_id, output_ds_id, sync_strategy, pipeline_config) KEY(id) VALUES
(406, '示例-论文PDF到档案附件', '从诺丁汉论文数据源读取 → 字段映射 → 生成PDF上传到档案系统 add_file_tx(u_lwdj_tx表)', 401, 503, 'FULL', '[{"position":"AFTER_READ","name":"论文字段映射","steps":[{"type":"MAPPING","mappingTemplateId":201}]}]');

-- ============================================
-- 列配置：u_lwdj 表完整字段（基于SQL Server实际表结构）
-- ============================================
MERGE INTO column_config (id, name, description, column_type, columns_json) KEY(id) VALUES
(202, '档案表字段(输出-lwdj)', '档案管理系统u_lwdj表常用字段（基于实际表结构）', 'PUSH',
 '[{"key":"ccode","value":"表名代码(ccode)"},{"key":"c1","value":"一级目录"},{"key":"c2","value":"二级目录"},{"key":"c3","value":"三级目录"},{"key":"c4","value":"文件目录"},{"key":"title","value":"题名"},{"key":"author","value":"责任者(zrz)"},{"key":"nd","value":"年度"},{"key":"qzh","value":"全宗号"},{"key":"mlh","value":"目录号"},{"key":"ajh","value":"案卷号"},{"key":"wjh","value":"文件号"},{"key":"jnh","value":"卷内号"},{"key":"flh","value":"分类号"},{"key":"gdh","value":"归档号"},{"key":"wh","value":"文号"},{"key":"ztm","value":"正题名"},{"key":"ftm","value":"副题名"},{"key":"hzz","value":"合著者"},{"key":"sj","value":"时间"},{"key":"dw","value":"单位"},{"key":"wb","value":"文别"},{"key":"mj","value":"密级"},{"key":"bgq","value":"保管期限"},{"key":"sl","value":"数量"},{"key":"gg","value":"规格"},{"key":"gdfs","value":"归档方式"},{"key":"zt","value":"载体"},{"key":"fj","value":"附件"},{"key":"cb","value":"成文"},{"key":"fz","value":"附注"},{"key":"ztc","value":"主题词"},{"key":"tx","value":"提要"},{"key":"dh","value":"档号"},{"key":"djsj","value":"登记时间"},{"key":"lrsj","value":"录入时间"},{"key":"swrq","value":"收文日期"},{"key":"gdr","value":"归档人"},{"key":"sry","value":"输入人"},{"key":"jsr","value":"接收人"},{"key":"yjr","value":"移交人"},{"key":"eyjr","value":"二移交人"},{"key":"ejsr","value":"二接收人"},{"key":"ywjm","value":"原文件名"},{"key":"cph","value":"存盘号"},{"key":"wz","value":"位置"},{"key":"pub","value":"是否公开"},{"key":"atype","value":"档案类型"},{"key":"isborrow","value":"是否借出"},{"key":"sensitive","value":"敏感标记"},{"key":"rfid","value":"RFID标签"},{"key":"search","value":"搜索字段"},{"key":"storehouse","value":"库房"},{"key":"storearea","value":"库区"},{"key":"storerow","value":"排"},{"key":"storecolumn","value":"列"},{"key":"nodeKey","value":"节点Key"},{"key":"nodeName","value":"节点名"},{"key":"tbtype","value":"表类型"},{"key":"rolekey","value":"角色Key"},{"key":"unitsub","value":"子单位"},{"key":"pdfcount","value":"PDF页数"},{"key":"is4Chk","value":"四性检测标记"},{"key":"archived","value":"归档状态"},{"key":"islisting","value":"上架状态"},{"key":"is_onshelf","value":"在架状态"},{"key":"userid","value":"用户ID"},{"key":"unitid","value":"单位ID"},{"key":"dwdm","value":"单位代码"},{"key":"oaflag","value":"OA标识"},{"key":"otbname","value":"原始表名"},{"key":"origin","value":"数据来源"}]');

-- Update column_config 202 to include fdext1-20 extension fields
MERGE INTO column_config (id, name, description, column_type, columns_json) KEY(id) VALUES
(202, '档案表字段(输出-lwdj)', '档案管理系统u_lwdj表完整字段（含扩展字段）', 'PUSH',
 '[{"key":"ccode","value":"表名代码(ccode)"},{"key":"c1","value":"一级目录"},{"key":"c2","value":"二级目录"},{"key":"c3","value":"三级目录"},{"key":"c4","value":"文件目录"},{"key":"title","value":"题名"},{"key":"author","value":"责任者(zrz)"},{"key":"nd","value":"年度"},{"key":"qzh","value":"全宗号"},{"key":"mlh","value":"目录号"},{"key":"ajh","value":"案卷号"},{"key":"wjh","value":"文件号"},{"key":"jnh","value":"卷内号"},{"key":"flh","value":"分类号"},{"key":"gdh","value":"归档号"},{"key":"wh","value":"文号"},{"key":"ztm","value":"正题名"},{"key":"ftm","value":"副题名"},{"key":"hzz","value":"合著者"},{"key":"sj","value":"时间"},{"key":"dw","value":"单位"},{"key":"wb","value":"文别"},{"key":"mj","value":"密级"},{"key":"bgq","value":"保管期限"},{"key":"sl","value":"数量"},{"key":"gg","value":"规格"},{"key":"gdfs","value":"归档方式"},{"key":"zt","value":"载体"},{"key":"fj","value":"附件"},{"key":"cb","value":"成文"},{"key":"fz","value":"附注"},{"key":"ztc","value":"主题词"},{"key":"tx","value":"提要"},{"key":"dh","value":"档号"},{"key":"djsj","value":"登记时间"},{"key":"lrsj","value":"录入时间"},{"key":"swrq","value":"收文日期"},{"key":"gdr","value":"归档人"},{"key":"sry","value":"输入人"},{"key":"jsr","value":"接收人"},{"key":"yjr","value":"移交人"},{"key":"eyjr","value":"二移交人"},{"key":"ejsr","value":"二接收人"},{"key":"ywjm","value":"原文件名"},{"key":"cph","value":"存盘号"},{"key":"wz","value":"位置"},{"key":"pub","value":"是否公开"},{"key":"atype","value":"档案类型"},{"key":"isborrow","value":"是否借出"},{"key":"sensitive","value":"敏感标记"},{"key":"rfid","value":"RFID标签"},{"key":"search","value":"搜索字段"},{"key":"storehouse","value":"库房"},{"key":"storearea","value":"库区"},{"key":"storerow","value":"排"},{"key":"storecolumn","value":"列"},{"key":"nodeKey","value":"节点Key"},{"key":"nodeName","value":"节点名"},{"key":"tbtype","value":"表类型"},{"key":"rolekey","value":"角色Key"},{"key":"unitsub","value":"子单位"},{"key":"pdfcount","value":"PDF页数"},{"key":"is4Chk","value":"四性检测标记"},{"key":"archived","value":"归档状态"},{"key":"islisting","value":"上架状态"},{"key":"is_onshelf","value":"在架状态"},{"key":"userid","value":"用户ID"},{"key":"unitid","value":"单位ID"},{"key":"dwdm","value":"单位代码"},{"key":"oaflag","value":"OA标识"},{"key":"otbname","value":"原始表名"},{"key":"origin","value":"数据来源"},{"key":"fdext1","value":"扩展字段1"},{"key":"fdext2","value":"扩展字段2"},{"key":"fdext3","value":"扩展字段3"},{"key":"fdext4","value":"扩展字段4"},{"key":"fdext5","value":"扩展字段5"},{"key":"fdext6","value":"扩展字段6"},{"key":"fdext7","value":"扩展字段7"},{"key":"fdext8","value":"扩展字段8"},{"key":"fdext9","value":"扩展字段9"},{"key":"fdext10","value":"扩展字段10"},{"key":"fdext11","value":"扩展字段11"},{"key":"fdext12","value":"扩展字段12"},{"key":"fdext13","value":"扩展字段13"},{"key":"fdext14","value":"扩展字段14"},{"key":"fdext15","value":"扩展字段15"},{"key":"fdext16","value":"扩展字段16"},{"key":"fdext17","value":"扩展字段17"},{"key":"fdext18","value":"扩展字段18"},{"key":"fdext19","value":"扩展字段19"},{"key":"fdext20","value":"扩展字段20"}]');


-- ============================================
-- 合并模板：add_file + add_file_tx 一步完成
-- 流程：建档案元数据 -> 获取maskey -> 下载/伪造PDF -> 上传附件
-- ============================================
MERGE INTO template (id, name, category_id, content, type, tags, is_deleted, version) KEY(id) VALUES
(504, '档案系统-写档案+上传附件(合并)', 19,
'// ============================================
// 档案管理系统 - 一步完成：建档案元数据 + 上传PDF附件
//
// 流程:
//   1. RSA加密获取JWT
//   2. POST /open_api/add_file 建元数据 -> 获取 maskey
//   3. 尝试从 pdf_url 下载PDF (带 Api-Key 认证)
//   4. POST /open_api/add_file_tx 上传附件
//
// params 可配置:
//   - nottApiKey: 诺丁汉API的Api-Key (用于下载论文PDF)
// ============================================

import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.net.URLEncoder
import java.net.HttpURLConnection
import java.net.URL

def API_BASE = params?.apiUrl ?: "http://localhost:8080"
def APPKEY = params?.appkey ?: "dba"
def PASSWORD = params?.password ?: "Aa@12345"
def CCODE = params?.ccode ?: "lwdj"
def NOTT_API_KEY = params?.nottApiKey ?: "e3c5f52d-a905-43ac-a10c-4ea5255e368d"

// ======================
// Phase 1: 获取JWT Token
// ======================
def pubkeyResp = http.post(API_BASE + "/open_api/getPublicKey", "", ["Content-Type": "application/json"])
if (!pubkeyResp.success) {
    out.success = false; out.error = "获取公钥失败"; return
}
def publicKeyStr = pubkeyResp.data?.data?.PublicKey ?: pubkeyResp.data?.PublicKey
if (!publicKeyStr) {
    out.success = false; out.error = "公钥为空"; return
}

def keyBytes = Base64.getDecoder().decode(publicKeyStr)
def keySpec = new X509EncodedKeySpec(keyBytes)
def keyFactory = KeyFactory.getInstance("RSA")
def publicKey = keyFactory.generatePublic(keySpec)
def cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
cipher.init(Cipher.ENCRYPT_MODE, publicKey)
def encryptedBytes = cipher.doFinal(PASSWORD.getBytes("UTF-8"))
def appsecret = Base64.getEncoder().encodeToString(encryptedBytes)

def tokenParams = "appkey=" + URLEncoder.encode(APPKEY, "UTF-8") + "&appsecret=" + URLEncoder.encode(appsecret, "UTF-8")
def tokenResp = http.post(API_BASE + "/open_api/gettoken", tokenParams, [
    "Content-Type": "application/x-www-form-urlencoded"
])
if (!tokenResp.success || tokenResp.status != 200) {
    out.success = false; out.error = "获取token失败"; return
}
def token = tokenResp.data?.data?.token
if (!token) {
    out.success = false; out.error = "token为空"; return
}

// ======================
// Phase 2: 建档案元数据 (add_file)
// ======================
def formFields = [:]
formFields.ccode = CCODE
// 传递记录唯一标识给档案系统，确保每条记录独立
// 优先取管线映射后的字段名（c1/c2/nd/ztc/tx/dw），兜底取原始字段名
formFields.externalId = input?.id?.toString() ?: ""
formFields.c1 = input?.c1?.toString() ?: input?.orgUuid?.toString() ?: ""
formFields.c2 = input?.c2?.toString() ?: input?.degree?.toString() ?: ""
formFields.title = input?.title?.toString() ?: ""
formFields.author = input?.author?.toString() ?: ""
formFields.nd = input?.nd?.toString() ?: input?.submissionDate?.toString() ?: ""
formFields.ztc = input?.ztc?.toString() ?: input?.keywords?.toString() ?: ""
formFields.tx = input?.tx?.toString() ?: input?.abstract_cn?.toString() ?: ""
formFields.dw = input?.dw?.toString() ?: input?.supervisor?.toString() ?: ""

input.each { k, v ->
    if (v != null && !(v instanceof Map) && !(v instanceof List) && !formFields.containsKey(k.toString())) {
        formFields[k.toString()] = v.toString()
    }
}

def formBody = formFields.collect { k, v ->
    URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v ?: "", "UTF-8")
}.join("&")

def addUrl = API_BASE + "/open_api/add_file?flag=" + URLEncoder.encode(CCODE, "UTF-8")
def addResp = http.post(addUrl, formBody, [
    "Content-Type": "application/x-www-form-urlencoded",
    "token": token
])

if (!addResp.success) {
    out.success = false; out.error = "add_file网络请求失败"; return
}
def addResult = addResp.data
if (addResult.code != 0 && addResult.code != 200) {
    out.success = false; out.error = "add_file失败: " + (addResult.msg ?: ""); return
}

def maskey = addResult.data?.maskey
if (!maskey) {
    out.success = false; out.error = "add_file未返回maskey"; return
}
out.maskey = maskey

// ======================
// Phase 3: 获取PDF文件内容
// ======================
def pdfBytes = null
def pdfUrl = input?.pdf_url?.toString()
def fileName = (input?.title?.toString() ?: "document") + ".pdf"
fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_")

if (pdfUrl) {
    try {
        def pdfConn = new URL(pdfUrl).openConnection()
        pdfConn.setConnectTimeout(10000)
        pdfConn.setReadTimeout(30000)
        // 诺丁汉文件下载API需要 Api-Key 认证
        if (pdfUrl.contains("nottingham")) {
            pdfConn.setRequestProperty("Api-Key", NOTT_API_KEY)
        }
        def pdfStream = pdfConn.getInputStream()
        pdfBytes = pdfStream.bytes
        pdfStream.close()
        out.pdfSource = "downloaded from " + pdfUrl
    } catch (Exception e) {
        out.pdfDownloadError = e.message
        pdfBytes = null
    }
}

if (!pdfBytes) {
    out.pdfSource = "download failed"
    out.pdfDownloadError = pdfUrl ? "Cannot download: " + pdfUrl : "No pdf_url available"
    out.success = false
    out.error = "PDF download failed: " + (out.pdfDownloadError ?: "unknown error")
    return
}


// ======================
// Phase 4: 上传附件 (add_file_tx)
// ======================
def boundary = "----DataConnect" + System.currentTimeMillis()
def txUrl = new URL(API_BASE + "/open_api/add_file_tx")
def conn = (HttpURLConnection) txUrl.openConnection()
conn.setDoOutput(true)
conn.setRequestMethod("POST")
conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
conn.setRequestProperty("token", token)
conn.setConnectTimeout(30000)
conn.setReadTimeout(120000)

def CRLF = "\r\n"
def outStream = conn.getOutputStream()
def writer = new OutputStreamWriter(outStream, "UTF-8")

writer.write("--" + boundary + CRLF)
writer.write("Content-Disposition: form-data; name=\"maskey\"" + CRLF + CRLF)
writer.write(maskey + CRLF)

writer.write("--" + boundary + CRLF)
writer.write("Content-Disposition: form-data; name=\"filename\"" + CRLF + CRLF)
writer.write(fileName + CRLF)

writer.write("--" + boundary + CRLF)
writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + CRLF)
writer.write("Content-Type: application/pdf" + CRLF + CRLF)
writer.flush()
outStream.write(pdfBytes)
outStream.flush()
writer.write(CRLF)

writer.write("--" + boundary + "--" + CRLF)
writer.flush()
writer.close()

def txCode = conn.getResponseCode()
def txStream = (txCode == 200) ? conn.getInputStream() : conn.getErrorStream()
def txText = txStream.getText("UTF-8")
def txResult = new JsonSlurper().parseText(txText)
conn.disconnect()

if (txResult.code == 0 || txResult.code == 200) {
    out.success = true
    out.fileName = fileName
    out.fileSize = pdfBytes.length
    out.message = "档案+附件上传成功: " + fileName + " (" + pdfBytes.length + " bytes)"
} else {
    out.success = true
    out.fileUploadError = txResult.msg
    out.message = "档案元数据已创建, 但附件上传失败: " + (txResult.msg ?: "")
}',
'CUSTOM', '档案系统,add_file,add_file_tx,合并,PDF上传', 0, 1);

-- 数据源：合并版 (504)
MERGE INTO ds_config (id, name, description, source_type, api_method, api_url, api_timeout, api_mode, api_auth_type, template_id, enabled) KEY(id) VALUES
(504, '档案系统-写档案+附件(合并)', 'SCRIPT模式：一步完成 add_file(建元数据) + add_file_tx(上传PDF)。从诺丁汉API下载真实PDF(需配置nottApiKey)，下载失败则报错', 'API', 'POST', '', 120, 'SCRIPT', 'NONE', 504, 1);

-- 更新流程405，使用合并数据源504
MERGE INTO flow_config (id, name, description, input_ds_id, output_ds_id, sync_strategy, pipeline_config) KEY(id) VALUES
(405, '示例-论文数据到档案系统', '从诺丁汉论文数据源读取 -> 字段映射 -> add_file建元数据+add_file_tx上传PDF附件(一步完成)', 401, 504, 'FULL', '[{"position":"AFTER_READ","name":"论文字段映射","steps":[{"type":"MAPPING","mappingTemplateId":201}]}]');

-- Fix template 504: copy all input fields after pipeline mapping instead of hardcoding field names
DELETE FROM template WHERE id=504;
MERGE INTO template (id, name, category_id, content, type, tags, is_deleted, version) KEY(id) VALUES
(504, '档案系统-写档案+上传附件(合并)', 19,
'// ============================================
// 档案管理系统 - 一步完成：建档案元数据 + 上传PDF附件
// 字段由管线映射处理，模板直接透传input全部字段
//
// params 可配置:
//   - apiUrl: 档案系统地址 (默认 http://localhost:8080)
//   - appkey: 档案系统appkey
//   - password: 档案系统密码
//   - ccode: 表名代码 (默认 lwdj)
//   - nottApiKey: 诺丁汉API的Api-Key (用于下载论文PDF)
// ============================================

import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.net.URLEncoder
import java.net.HttpURLConnection
import java.net.URL

def API_BASE = params?.apiUrl ?: "http://localhost:8080"
def APPKEY = params?.appkey ?: "dba"
def PASSWORD = params?.password ?: "Aa@12345"
def CCODE = params?.ccode ?: "lwdj"
def NOTT_API_KEY = params?.nottApiKey ?: "e3c5f52d-a905-43ac-a10c-4ea5255e368d"

// ======================
// Phase 1: 获取JWT Token
// ======================
def pubkeyResp = http.post(API_BASE + "/open_api/getPublicKey", "", ["Content-Type": "application/json"])
if (!pubkeyResp.success) {
    out.success = false; out.error = "获取公钥失败"; return
}
def publicKeyStr = pubkeyResp.data?.data?.PublicKey ?: pubkeyResp.data?.PublicKey
if (!publicKeyStr) {
    out.success = false; out.error = "公钥为空"; return
}

def keyBytes = Base64.getDecoder().decode(publicKeyStr)
def keySpec = new X509EncodedKeySpec(keyBytes)
def keyFactory = KeyFactory.getInstance("RSA")
def publicKey = keyFactory.generatePublic(keySpec)
def cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
cipher.init(Cipher.ENCRYPT_MODE, publicKey)
def encryptedBytes = cipher.doFinal(PASSWORD.getBytes("UTF-8"))
def appsecret = Base64.getEncoder().encodeToString(encryptedBytes)

def tokenParams = "appkey=" + URLEncoder.encode(APPKEY, "UTF-8") + "&appsecret=" + URLEncoder.encode(appsecret, "UTF-8")
def tokenResp = http.post(API_BASE + "/open_api/gettoken", tokenParams, [
    "Content-Type": "application/x-www-form-urlencoded"
])
if (!tokenResp.success || tokenResp.status != 200) {
    out.success = false; out.error = "获取token失败"; return
}
def token = tokenResp.data?.data?.token
if (!token) {
    out.success = false; out.error = "token为空"; return
}

// ======================
// Phase 2: 建档案元数据 (add_file)
// 透传管线映射后的字段，排除内部标识字段让档案系统自建
// ======================
def formFields = [:]
formFields.ccode = CCODE
formFields.isOverride = "false"

// 排除的字段：内部标识(id/uuid等)、复杂对象字段、非档案字段
def skipFields = ["id","externalId","externalIdSource","pureId","uuid",
    "info","workflow","visibility","confidential","type","language",
    "personAssociations","supervisors","organisationalUnits",
    "keywordGroups","awardingInstitutions","documents",
    "managingOrganisationalUnit","supervisorOrganisationalUnits",
    "pdf_url","pdf_count","citation_count","status",
    "departmentEn","authorFirst","authorLast","authorEmail",
    "abstract","abstract_en","awardDate","doi","degreeZh"]

// 从input复制字段，跳过排除的
input.each { k, v ->
    if (v != null && !skipFields.contains(k.toString())) {
        formFields[k.toString()] = v.toString()
    }
}
// 也合并params中的字段
params.each { k, v ->
    if (v != null && !formFields.containsKey(k) && !skipFields.contains(k.toString())) {
        formFields[k] = v
    }
}

def formBody = formFields.collect { k, v ->
    URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v ?: "", "UTF-8")
}.join("&")

def addUrl = API_BASE + "/open_api/add_file"
def addResp = http.post(addUrl, formBody, [
    "Content-Type": "application/x-www-form-urlencoded",
    "token": token
])

if (!addResp.success) {
    out.success = false; out.error = "add_file网络请求失败"; return
}
def addResult = addResp.data
if (addResult.code != 0 && addResult.code != 200) {
    out.success = false; out.error = "add_file失败: " + (addResult.msg ?: ""); return
}

def maskey = addResult.data?.maskey
if (!maskey) {
    out.success = false; out.error = "add_file未返回maskey"; return
}
out.maskey = maskey

// ======================
// Phase 3: 获取PDF
// ======================
def pdfBytes = null
def pdfUrl = input?.pdf_url?.toString()
def fileName = (input?.title?.toString() ?: "document") + ".pdf"
fileName = fileName.replaceAll("[\\/:*?\"<>|]", "_")

if (pdfUrl) {
    try {
        def pdfConn = new URL(pdfUrl).openConnection()
        pdfConn.setConnectTimeout(10000)
        pdfConn.setReadTimeout(30000)
        // 诺丁汉文件下载API需要 Api-Key 认证
        if (pdfUrl.contains("nottingham")) {
            pdfConn.setRequestProperty("Api-Key", NOTT_API_KEY)
        }
        pdfBytes = pdfConn.getInputStream().bytes
        out.pdfSource = "downloaded"
    } catch (Exception e) {
        out.pdfDownloadError = e.message
        pdfBytes = null
    }
}
if (!pdfBytes) {
    out.pdfSource = "download failed"
    out.pdfDownloadError = pdfUrl ? "Cannot download: " + pdfUrl : "No pdf_url available"
    out.success = false
    out.error = "PDF download failed: " + (out.pdfDownloadError ?: "unknown error")
    return
}

// ======================
// Phase 4: 上传附件 (add_file_tx)
// ======================
def boundary = "----DataConnect" + System.currentTimeMillis()
def txUrl = new URL(API_BASE + "/open_api/add_file_tx")
def conn = (HttpURLConnection) txUrl.openConnection()
conn.setDoOutput(true)
conn.setRequestMethod("POST")
conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
conn.setRequestProperty("token", token)
conn.setConnectTimeout(30000)
conn.setReadTimeout(120000)

def CRLF = "\r\n"
def outStream = conn.getOutputStream()
def writer = new OutputStreamWriter(outStream, "UTF-8")

writer.write("--" + boundary + CRLF)
writer.write("Content-Disposition: form-data; name=\"maskey\"" + CRLF + CRLF)
writer.write(maskey + CRLF)

writer.write("--" + boundary + CRLF)
writer.write("Content-Disposition: form-data; name=\"filename\"" + CRLF + CRLF)
writer.write(fileName + CRLF)

writer.write("--" + boundary + CRLF)
writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + CRLF)
writer.write("Content-Type: application/pdf" + CRLF + CRLF)
writer.flush()
outStream.write(pdfBytes)
outStream.flush()
writer.write(CRLF)

writer.write("--" + boundary + "--" + CRLF)
writer.flush()
writer.close()

def txCode = conn.getResponseCode()
def txStream = (txCode == 200) ? conn.getInputStream() : conn.getErrorStream()
def txText = txStream.getText("UTF-8")
def txResult = new JsonSlurper().parseText(txText)
conn.disconnect()

if (txResult.code == 0 || txResult.code == 200) {
    out.success = true
    out.fileName = fileName
    out.fileSize = pdfBytes.length
    out.message = "档案+附件上传成功: " + fileName + " (" + pdfBytes.length + " bytes)"
} else {
    out.success = true
    out.fileUploadError = txResult.msg
    out.message = "档案元数据已创建, 但附件上传失败: " + (txResult.msg ?: "")
}',
'CUSTOM', '档案系统,add_file,add_file_tx,合并,PDF上传', 0, 1);
