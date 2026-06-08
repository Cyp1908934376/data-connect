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
