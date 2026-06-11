<#include "../layouts/main.ftl">
<@main title="数据源配置" activeMenu="datasource"
extraCss=["/static/codemirror/codemirror.min.css", "/static/codemirror/theme/monokai.css"]
extraJs=["/static/codemirror/codemirror.min.js", "/static/codemirror/mode/sql.min.js", "/static/codemirror/mode/javascript.min.js"]>

<style>
    .tab-content { padding-top: 1.5rem; }
    .CodeMirror { border: 1px solid #ced4da; border-radius: 6px; height: auto; min-height: 80px; }
    .cm-editor-label { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.25rem; }
    .cm-editor-label .cm-hint { font-size: 0.75rem; color: #6c757d; }
</style>

<#assign isEdit = config?? && config.id?? />
<#assign sourceType = (config.sourceType)!'DB' />

<div class="datasource-form-page">
    <ul class="nav nav-tabs" id="sourceTypeTabs" role="tablist">
        <li class="nav-item" role="presentation">
            <button class="nav-link <#if sourceType != 'API'>active</#if>" id="db-tab" data-bs-toggle="tab"
                    data-bs-target="#dbPanel" type="button" role="tab">
                <i class="bi bi-database"></i> 数据库数据源
            </button>
        </li>
        <li class="nav-item" role="presentation">
            <button class="nav-link <#if sourceType == 'API'>active</#if>" id="api-tab" data-bs-toggle="tab"
                    data-bs-target="#apiPanel" type="button" role="tab">
                <i class="bi bi-cloud"></i> 接口数据源
            </button>
        </li>
    </ul>

    <#if error??>
    <div class="alert alert-danger alert-dismissible fade show" role="alert">
        <i class="bi bi-exclamation-triangle"></i> ${error}
        <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
    </div>
    </#if>

    <form id="datasourceForm" method="post" action="/datasource/save" novalidate>
        <input type="hidden" id="sourceType" name="sourceType" value="${sourceType}">
        <input type="hidden" name="id" value="${(config.id)!''}">

        <!-- 名称和描述放在 tab 外部，两个 tab 共用，避免 JS 同步问题 -->
        <div class="row g-3 mb-3">
            <div class="col-md-6">
                <label class="form-label">数据源名称 <span class="text-danger">*</span></label>
                <input type="text" class="form-control" name="name" value="${(config.name)!''}" required placeholder="请输入数据源名称" maxlength="100">
            </div>
            <div class="col-md-6">
                <label class="form-label">描述</label>
                <input type="text" class="form-control" name="description" value="${(config.description)!''}" placeholder="请输入数据源描述" maxlength="500">
            </div>
        </div>

        <div class="tab-content" id="sourceTypeTabContent">
            <!-- ========== 数据库数据源 Tab ========== -->
            <div class="tab-pane fade <#if sourceType != 'API'>show active</#if>" id="dbPanel" role="tabpanel">
                <div class="row g-3">
                    <div class="col-md-6">
                        <label class="form-label">数据库类型 <span class="text-danger">*</span></label>
                        <select class="form-select" name="dbType">
                            <option value="">请选择数据库类型</option>
                            <#list dbTypes as dt>
                                <option value="${dt}" <#if ((config.dbType)!'') == dt>selected</#if>>${dt}</option>
                            </#list>
                        </select>
                    </div>
                    <div class="col-md-6">
                        <label class="form-label">主机地址 <span class="text-danger">*</span></label>
                        <input type="text" class="form-control" name="host" value="${(config.host)!''}" placeholder="例如: 192.168.1.100 或 db.example.com">
                    </div>
                    <div class="col-md-6">
                        <label class="form-label">端口 <span class="text-danger">*</span></label>
                        <input type="number" class="form-control" name="port" value="${(config.port)!''}" placeholder="例如: 3306" min="1" max="65535">
                    </div>
                    <div class="col-md-6">
                        <label class="form-label">数据库名称</label>
                        <input type="text" class="form-control" name="dbName" value="${(config.dbName)!''}" placeholder="请输入数据库名称">
                    </div>
                    <div class="col-md-6">
                        <label class="form-label">
                            数据表名称
                            <small class="text-muted">(选填)</small>
                        </label>
                        <input type="text" class="form-control" name="tableName" value="${(config.tableName)!''}" placeholder="例如: user_info">
                        <small class="form-text text-muted">
                            <i class="bi bi-info-circle"></i> 作为<strong>输入数据源</strong>时：指定读取哪张表，留空则自动取第一张表<br>
                            <i class="bi bi-info-circle"></i> 作为<strong>输出数据源</strong>时：指定写入哪张表，留空则写入默认表 data_sync_result
                        </small>
                    </div>
                    <div class="col-md-6">
                        <label class="form-label">字符集</label>
                        <select class="form-select" name="charset">
                            <option value="UTF-8" <#if ((config.charset)!'UTF-8') == 'UTF-8'>selected</#if>>UTF-8</option>
                            <option value="GBK" <#if ((config.charset)!'') == 'GBK'>selected</#if>>GBK</option>
                            <option value="GB2312" <#if ((config.charset)!'') == 'GB2312'>selected</#if>>GB2312</option>
                            <option value="ISO-8859-1" <#if ((config.charset)!'') == 'ISO-8859-1'>selected</#if>>ISO-8859-1</option>
                            <option value="latin1" <#if ((config.charset)!'') == 'latin1'>selected</#if>>latin1</option>
                        </select>
                    </div>
                    <div class="col-md-6">
                        <label class="form-label">用户名</label>
                        <input type="text" class="form-control" name="username" value="${(config.username)!''}" placeholder="数据库用户名" autocomplete="off">
                    </div>
                    <div class="col-md-6">
                        <label class="form-label">密码</label>
                        <div class="input-group">
                            <input type="password" class="form-control" id="dbPassword" name="password" value="${(config.password)!''}" placeholder="数据库密码" autocomplete="off">
                            <button type="button" class="btn btn-outline-secondary toggle-password-btn" data-target="dbPassword">
                                <i class="bi bi-eye"></i>
                            </button>
                        </div>
                    </div>
                    <div class="col-12">
                        <label class="form-label">JDBC 额外参数</label>
                        <input type="text" class="form-control" name="jdbcParams" value="${(config.jdbcParams)!''}" placeholder="例如: useSSL=false&serverTimezone=Asia/Shanghai">
                    </div>
                </div>

                <hr class="my-3">
                <h6 class="mb-3">连接池配置</h6>
                <div class="row g-3">
                    <div class="col-md-4">
                        <label class="form-label">最大连接数</label>
                        <input type="number" class="form-control" name="maxPoolSize" value="${(config.maxPoolSize)!10}" min="1" max="100">
                    </div>
                    <div class="col-md-4">
                        <label class="form-label">最小空闲连接</label>
                        <input type="number" class="form-control" name="minIdle" value="${(config.minIdle)!2}" min="0" max="50">
                    </div>
                    <div class="col-md-4">
                        <label class="form-label">连接超时(秒)</label>
                        <input type="number" class="form-control" name="connTimeout" value="${(config.connTimeout)!30}" min="1" max="300">
                    </div>
                </div>

                <hr class="my-3">
                <h6 class="mb-3">高级设置</h6>
                <div class="row g-3">
                    <div class="col-12">
                        <label class="form-label">初始化SQL <small class="text-muted">(连接池初始化时执行的SQL)</small></label>
                        <textarea id="dbInitSql" name="initSql" data-cm-mode="sql">${(config.initSql)!''}</textarea>
                    </div>
                </div>
            </div>

            <!-- ========== 接口数据源 Tab ========== -->
            <div class="tab-pane fade <#if sourceType == 'API'>show active</#if>" id="apiPanel" role="tabpanel">
                <div class="row g-3">
                    <div class="col-md-6">
                        <label class="form-label">接口协议 <span class="text-danger">*</span></label>
                        <select class="form-select" id="apiType" name="apiType">
                            <option value="HTTP" <#if ((config.apiType)!'HTTP') == 'HTTP'>selected</#if>>HTTP</option>
                            <option value="HTTPS" <#if ((config.apiType)!'') == 'HTTPS'>selected</#if>>HTTPS</option>
                        </select>
                    </div>
                    <div class="col-md-6">
                        <label class="form-label">接口URL <span class="text-danger">*</span></label>
                        <input type="text" class="form-control" id="apiUrl" name="apiUrl" value="${(config.apiUrl)!''}" placeholder="例如: https://api.example.com/data">
                    </div>
                    <div class="col-md-6">
                        <label class="form-label">请求方式 <span class="text-danger">*</span></label>
                        <select class="form-select" id="apiMethod" name="apiMethod">
                            <option value="GET" <#if ((config.apiMethod)!'GET') == 'GET'>selected</#if>>GET</option>
                            <option value="POST" <#if ((config.apiMethod)!'') == 'POST'>selected</#if>>POST</option>
                            <option value="PUT" <#if ((config.apiMethod)!'') == 'PUT'>selected</#if>>PUT</option>
                            <option value="DELETE" <#if ((config.apiMethod)!'') == 'DELETE'>selected</#if>>DELETE</option>
                        </select>
                    </div>
                    <div class="col-12">
                        <label class="form-label">接口模式 <span class="text-danger">*</span></label>
                        <select class="form-select" id="apiMode" name="apiMode" style="max-width:400px;">
                            <option value="SINGLE" <#if ((config.apiMode)!'SINGLE') == 'SINGLE'>selected</#if>>单接口 (SINGLE) - 直接调用单个API</option>
                            <option value="CHAIN" <#if ((config.apiMode)!'') == 'CHAIN'>selected</#if>>多接口链 (CHAIN) - 多步API流水线调用</option>
                            <option value="SCRIPT" <#if ((config.apiMode)!'') == 'SCRIPT'>selected</#if>>复杂脚本 (SCRIPT) - Groovy模板编排</option>
                        </select>
                    </div>
                </div>

                <!-- SINGLE 模式配置 -->
                <div class="api-mode-section" id="singleSection">
                    <h6 class="mb-3 mt-2">
                        <i class="bi bi-arrow-left-right"></i> 单接口配置
                        <button type="button" class="btn btn-sm btn-outline-warning ms-2" onclick="loadSingleExample()">
                            <i class="bi bi-lightbulb"></i> 加载示例
                        </button>
                    </h6>
                    <div class="row g-3">
                        <div class="col-md-4">
                            <label class="form-label">超时时间(秒)</label>
                            <input type="number" class="form-control" id="apiTimeout" name="apiTimeout" value="${(config.apiTimeout)!30}" min="1" max="300">
                        </div>
                        <div class="col-md-4">
                            <label class="form-label">重试次数</label>
                            <input type="number" class="form-control" id="apiRetryTimes" name="apiRetryTimes" value="${(config.apiRetryTimes)!0}" min="0" max="10">
                        </div>
                        <div class="col-md-4">
                            <label class="form-label">重试间隔(毫秒)</label>
                            <input type="number" class="form-control" id="apiRetryInterval" name="apiRetryInterval" value="${(config.apiRetryInterval)!1000}" min="100" max="60000">
                        </div>
                        <div class="col-md-6">
                            <label class="form-label">认证方式</label>
                            <select class="form-select" id="apiAuthType" name="apiAuthType">
                                <option value="NONE" <#if ((config.apiAuthType)!'NONE') == 'NONE'>selected</#if>>无认证</option>
                                <option value="BASIC" <#if ((config.apiAuthType)!'') == 'BASIC'>selected</#if>>Basic Auth</option>
                                <option value="BEARER" <#if ((config.apiAuthType)!'') == 'BEARER'>selected</#if>>Bearer Token</option>
                                <option value="API_KEY" <#if ((config.apiAuthType)!'') == 'API_KEY'>selected</#if>>API Key</option>
                                <option value="OAUTH2" <#if ((config.apiAuthType)!'') == 'OAUTH2'>selected</#if>>OAuth 2.0</option>
                            </select>
                        </div>
                        <div class="col-md-6">
                            <label class="form-label">认证配置 <small class="text-muted">(JSON)</small></label>
                            <textarea id="apiAuthConfig" name="apiAuthConfig" data-cm-mode="javascript">${(config.apiAuthConfig)!''}</textarea>
                        </div>
                        <div class="col-12">
                            <label class="form-label">自定义请求头 <small class="text-muted">(JSON)</small></label>
                            <textarea id="apiHeaders" name="apiHeaders" data-cm-mode="javascript">${(config.apiHeaders)!''}</textarea>
                        </div>
                        <div class="col-12">
                            <label class="form-label">请求体</label>
                            <textarea id="apiBody" name="apiBody" data-cm-mode="javascript">${(config.apiBody)!''}</textarea>
                        </div>
                    </div>
                </div>

                <!-- CHAIN 模式配置 -->
                <div class="api-mode-section" id="chainSection" style="display:none;">
                    <h6 class="mb-3 mt-2">
                        <i class="bi bi-link-45deg"></i> 多接口链配置
                        <button type="button" class="btn btn-sm btn-outline-warning ms-2" onclick="loadChainExample()">
                            <i class="bi bi-lightbulb"></i> 加载示例
                        </button>
                    </h6>
                    <div class="row g-3">
                        <div class="col-md-6">
                            <label class="form-label">认证方式 <small class="text-muted">(所有步骤共用)</small></label>
                            <select class="form-select" id="chainAuthType">
                                <option value="NONE" <#if ((config.apiAuthType)!'NONE') == 'NONE'>selected</#if>>无认证</option>
                                <option value="BASIC" <#if ((config.apiAuthType)!'') == 'BASIC'>selected</#if>>Basic Auth</option>
                                <option value="BEARER" <#if ((config.apiAuthType)!'') == 'BEARER'>selected</#if>>Bearer Token</option>
                                <option value="API_KEY" <#if ((config.apiAuthType)!'') == 'API_KEY'>selected</#if>>API Key</option>
                            </select>
                        </div>
                        <div class="col-md-6">
                            <label class="form-label">认证配置 <small class="text-muted">(JSON)</small></label>
                            <textarea id="chainAuthConfig" data-cm-mode="javascript">${(config.apiAuthConfig)!''}</textarea>
                        </div>
                        <div class="col-12">
                            <label class="form-label">
                                接口链配置 <small class="text-muted">(JSON数组，每步一个对象)</small>
                                <button type="button" class="btn btn-sm btn-outline-secondary ms-2" id="chainHelpBtn" title="查看格式说明">
                                    <i class="bi bi-question-circle"></i> 格式说明
                                </button>
                            </label>
                            <textarea id="apiChainConfig" name="apiChainConfig" data-cm-mode="javascript" style="min-height:200px;">${(config.apiChainConfig)!''}</textarea>
                        </div>
                    </div>
                </div>

                <!-- SCRIPT 模式配置 -->
                <div class="api-mode-section" id="scriptSection" style="display:none;">
                    <h6 class="mb-3 mt-2">
                        <i class="bi bi-code-slash"></i> 复杂脚本配置
                        <button type="button" class="btn btn-sm btn-outline-warning ms-2" onclick="loadScriptExample()">
                            <i class="bi bi-lightbulb"></i> 加载示例
                        </button>
                    </h6>
                    <div class="row g-3">
                        <div class="col-md-6">
                            <label class="form-label">选择模板 <span class="text-danger">*</span></label>
                            <select class="form-select" id="templateId" name="templateId">
                                <option value="0">请选择模板...</option>
                                <#list templates as t>
                                    <option value="${t.id}" <#if ((config.templateId)!0) == t.id>selected</#if>>${t.name} <#if t.type??>(<small>${t.type}</small>)</#if></option>
                                </#list>
                            </select>
                            <small class="text-muted">使用模板管理中的Groovy脚本进行复杂编排，脚本中可用 http、config、params 变量</small>
                        </div>
                        <div class="col-md-6">
                            <label class="form-label">认证方式 <small class="text-muted">(脚本中通过config使用)</small></label>
                            <select class="form-select" id="scriptAuthType">
                                <option value="NONE" <#if ((config.apiAuthType)!'NONE') == 'NONE'>selected</#if>>无认证</option>
                                <option value="BASIC" <#if ((config.apiAuthType)!'') == 'BASIC'>selected</#if>>Basic Auth</option>
                                <option value="BEARER" <#if ((config.apiAuthType)!'') == 'BEARER'>selected</#if>>Bearer Token</option>
                                <option value="API_KEY" <#if ((config.apiAuthType)!'') == 'API_KEY'>selected</#if>>API Key</option>
                            </select>
                        </div>
                        <div class="col-md-12">
                            <label class="form-label">认证配置 <small class="text-muted">(JSON)</small></label>
                            <textarea id="scriptAuthConfig" data-cm-mode="javascript">${(config.apiAuthConfig)!''}</textarea>
                        </div>
                    </div>
                </div>

            </div>
        </div>

        <!-- 底部操作按钮 -->
        <div class="d-flex justify-content-between align-items-center mt-4 pt-3 border-top">
            <button type="button" id="testConnectionBtn" class="btn btn-outline-info">
                <i class="bi bi-lightning-charge"></i> 测试连接
            </button>
            <div class="d-flex gap-2">
                <a href="/datasource/list" class="btn btn-outline-secondary">取消</a>
                <button type="submit" class="btn btn-primary">
                    <i class="bi bi-check-lg"></i> 保存
                </button>
            </div>
        </div>
    </form>
</div>

<script>
// CodeMirror 实例管理
var cmInstances = {};

function initCodeMirror(id, mode) {
    var textarea = document.getElementById(id);
    if (!textarea) return;
    var cm = CodeMirror.fromTextArea(textarea, {
        mode: mode || 'javascript',
        theme: 'monokai',
        lineNumbers: true,
        matchBrackets: true,
        autoCloseBrackets: true,
        lineWrapping: true,
        tabSize: 2,
        viewportMargin: 10
    });
    cm.setSize(null, mode === 'sql' ? 100 : (id === 'apiChainConfig' ? 200 : 120));
    cmInstances[id] = cm;
}

function refreshAllCM() {
    for (var key in cmInstances) {
        cmInstances[key].refresh();
    }
}

function syncAllCM() {
    for (var key in cmInstances) {
        cmInstances[key].save();
    }
}

function getApiMode() {
    return $('#apiMode').val() || 'SINGLE';
}

function ensureCodeMirror(id, mode) {
    if (!cmInstances[id]) {
        var textarea = document.getElementById(id);
        if (textarea && textarea.offsetParent !== null) {
            initCodeMirror(id, mode);
        }
    }
    if (cmInstances[id]) {
        cmInstances[id].refresh();
    }
}

function switchApiMode() {
    var mode = getApiMode();
    $('#singleSection').toggle(mode === 'SINGLE');
    $('#chainSection').toggle(mode === 'CHAIN');
    $('#scriptSection').toggle(mode === 'SCRIPT');

    // Lazy-init CodeMirror for newly visible editors
    if (mode === 'SINGLE') {
        ensureCodeMirror('apiAuthConfig', 'javascript');
        ensureCodeMirror('apiHeaders', 'javascript');
        ensureCodeMirror('apiBody', 'javascript');
    } else if (mode === 'CHAIN') {
        ensureCodeMirror('apiChainConfig', 'javascript');
        ensureCodeMirror('chainAuthConfig', 'javascript');
    } else if (mode === 'SCRIPT') {
        ensureCodeMirror('scriptAuthConfig', 'javascript');
    }

    syncAuthFields();
    setTimeout(refreshAllCM, 100);
}

function syncAuthFields() {
    var mode = getApiMode();
    if (mode === 'CHAIN') {
        $('#apiAuthType').val($('#chainAuthType').val());
        if (cmInstances['chainAuthConfig']) cmInstances['chainAuthConfig'].save();
        var val = $('#chainAuthConfig').val();
        if (cmInstances['apiAuthConfig']) {
            cmInstances['apiAuthConfig'].setValue(val);
        } else {
            $('#apiAuthConfig').val(val);
        }
    } else if (mode === 'SCRIPT') {
        $('#apiAuthType').val($('#scriptAuthType').val());
        if (cmInstances['scriptAuthConfig']) cmInstances['scriptAuthConfig'].save();
        var val = $('#scriptAuthConfig').val();
        if (cmInstances['apiAuthConfig']) {
            cmInstances['apiAuthConfig'].setValue(val);
        } else {
            $('#apiAuthConfig').val(val);
        }
    }
}

// ===== 示例加载函数 =====

function loadSingleExample() {
    $('input[name="name"]').val('JSONPlaceholder示例');
    $('input[name="description"]').val('获取单条文章数据');
    $('#apiUrl').val('https://jsonplaceholder.typicode.com/posts/1');
    $('#apiMethod').val('GET');
    $('#apiTimeout').val('30');
    $('#apiAuthType').val('NONE');
    if (cmInstances['apiAuthConfig']) cmInstances['apiAuthConfig'].setValue('');
    if (cmInstances['apiHeaders']) cmInstances['apiHeaders'].setValue('{\n  "Accept": "application/json"\n}');
    if (cmInstances['apiBody']) cmInstances['apiBody'].setValue('');
    showInfo('已加载单接口示例：GET 请求 JSONPlaceholder 获取文章数据');
}

function loadChainExample() {
    $('input[name="name"]').val('Token认证链示例');
    $('input[name="description"]').val('先登录获取Token，再调用数据接口');
    $('#apiUrl').val('https://api.example.com');
    $('#apiMethod').val('GET');
    $('#apiTimeout').val('30');
    $('#chainAuthType').val('NONE');

    var chainJson = [
        {
            "name": "获取Token",
            "url": "https://api.example.com/auth/login",
            "method": "POST",
            "headers": {"Content-Type": "application/json"},
            "body": "{\"username\":\"admin\",\"password\":\"123456\"}",
            "extract": {"token": "data.accessToken", "userId": "data.userId"}
        },
        {
            "name": "查询列表",
            "url": "https://api.example.com/api/v1/orders?userId=${r"${"}userId}&page=1",
            "method": "GET",
            "headers": {"Authorization": "Bearer ${r"${"}token}"},
            "extract": {"orderList": "data.records"}
        },
        {
            "name": "获取每个订单详情",
            "url": "https://api.example.com/api/v1/orders/${r"${"}orderId}",
            "method": "GET",
            "headers": {"Authorization": "Bearer ${r"${"}token}"}
        }
    ];

    if (cmInstances['apiChainConfig']) {
        cmInstances['apiChainConfig'].setValue(JSON.stringify(chainJson, null, 2));
    } else {
        $('#apiChainConfig').val(JSON.stringify(chainJson, null, 2));
    }
    if (cmInstances['chainAuthConfig']) cmInstances['chainAuthConfig'].setValue('');
    showInfo('已加载多接口链示例：<br>1. POST 登录获取 token<br>2. GET 查询订单列表<br>3. GET 获取每个订单详情<br><br>变量 ${"$"}{token}、${"$"}{userId} 自动从响应中提取并在后续步骤使用', { duration: 6000, title: '加载示例' });
}

function loadScriptExample() {
    $('input[name="name"]').val('Groovy脚本编排示例');
    $('input[name="description"]').val('查询清单→逐条查详情→条件过滤→结果聚合');
    $('#apiUrl').val('https://api.example.com');
    $('#apiMethod').val('GET');
    $('#apiTimeout').val('30');
    $('#scriptAuthType').val('NONE');
    if (cmInstances['scriptAuthConfig']) cmInstances['scriptAuthConfig'].setValue('');
    showInfo('此模式需要在"模板管理"中创建Groovy模板，模板中可使用：<br>- http.get(url, headers) / http.post(url, body, headers)<br>- config: 当前数据源配置<br>- params: 传入的额外参数<br>- out: 输出Map<br><br>示例模板脚本见模板管理的编辑器。', { duration: 6000, title: '已加载脚本模式示例配置' });
}

// 页面初始化
$(function() {
    // 显示服务端返回的 flash 消息为通知
    <#if error??>
    showError('${error?js_string}');
    </#if>
    <#if success??>
    showSuccess('${success?js_string}');
    </#if>

    // 初始化可见的 CodeMirror 编辑器（隐藏区域的不初始化，等显示时再初始化）
    initCodeMirror('dbInitSql', 'sql');
    // SINGLE 区域默认可见时初始化
    if (getApiMode() === 'SINGLE') {
        initCodeMirror('apiAuthConfig', 'javascript');
        initCodeMirror('apiHeaders', 'javascript');
        initCodeMirror('apiBody', 'javascript');
    }

    // 编辑模式：同步 chain/script auth 选择器的初始值
    var savedAuthType = '${(config.apiAuthType)!"NONE"}';
    if (savedAuthType && savedAuthType !== 'NONE') {
        $('#chainAuthType').val(savedAuthType);
        $('#scriptAuthType').val(savedAuthType);
    }

    // 初始显示对应模式
    switchApiMode();

    // API模式切换
    $('#apiMode').on('change', switchApiMode);
    $('#chainAuthType').on('change', syncAuthFields);
    $('#scriptAuthType').on('change', syncAuthFields);

    // Tab切换时初始化/刷新CodeMirror
    $('#sourceTypeTabs button').on('shown.bs.tab', function(e) {
        var panelId = $(e.target).data('bs-target');
        var type = (panelId === '#apiPanel') ? 'API' : 'DB';
        $('#sourceType').val(type);
        if (type === 'API') {
            // Lazy-init editors now that API tab is visible
            switchApiMode();
        }
        setTimeout(refreshAllCM, 100);
    });

    // 密码切换
    $('.toggle-password-btn').on('click', function() {
        var $input = $('#' + $(this).data('target'));
        var $icon = $(this).find('i');
        var isPass = $input.attr('type') === 'password';
        $input.attr('type', isPass ? 'text' : 'password');
        $icon.toggleClass('bi-eye bi-eye-slash');
    });

    // Chain格式帮助
    $('#chainHelpBtn').on('click', function() {
        var helpText = '接口链配置为JSON数组，每步一个对象：\n\n' +
            '[\n' +
            '  {\n' +
            '    "name": "获取Token",\n' +
            '    "url": "https://api.example.com/login",\n' +
            '    "method": "POST",\n' +
            '    "headers": {"Content-Type": "application/json"},\n' +
            '    "body": "{\\"username\\":\\"admin\\",\\"password\\":\\"123456\\"}",\n' +
            '    "extract": {"token": "data.token"}\n' +
            '  },\n' +
            '  {\n' +
            '    "name": "获取数据",\n' +
            '    "url": "https://api.example.com/data?page=1",\n' +
            '    "method": "GET",\n' +
            '    "headers": {"Authorization": "Bearer ${r"${"}token}"}\n' +
            '  }\n' +
            ']\n\n' +
            '说明：\n' +
            '- extract: 从上一步响应JSON中提取变量（支持点号路径如data.token）\n' +
            '- ${r"${"}变量名}: 在后续步骤中引用已提取的变量\n' +
            '- 变量可在url、headers、body中使用';
        showInfo(helpText.replace(/\n/g, '<br>'), { title: '接口链格式说明', duration: 8000 });
    });

    // 测试连接
    $('#testConnectionBtn').on('click', function() {
        syncAllCM();
        syncAuthFields();
        var $btn = $(this).prop('disabled', true).html('<span class="spinner-border spinner-border-sm"></span> 测试中...');
        var type = $('#sourceType').val();
        var formData = {
            sourceType: type,
            id: $('input[name="id"]').val(),
            name: $('input[name="name"]').val(),
            description: $('input[name="description"]').val()
        };

        if (type === 'API') {
            $.extend(formData, {
                apiType: $('#apiType').val(),
                apiMethod: $('#apiMethod').val(), apiUrl: $('#apiUrl').val(),
                apiTimeout: $('#apiTimeout').val(), apiRetryTimes: $('#apiRetryTimes').val(),
                apiRetryInterval: $('#apiRetryInterval').val(),
                apiHeaders: $('#apiHeaders').val(), apiBody: $('#apiBody').val(),
                apiAuthType: $('#apiAuthType').val(), apiAuthConfig: $('#apiAuthConfig').val(),
                apiMode: getApiMode(), templateId: $('#templateId').val(),
                apiChainConfig: $('#apiChainConfig').val()
            });
        } else {
            $.extend(formData, {
                dbType: $('select[name="dbType"]').val(),
                host: $('input[name="host"]').val(), port: $('input[name="port"]').val(),
                dbName: $('input[name="dbName"]').val(),
                username: $('input[name="username"]').val(), password: $('#dbPassword').val(),
                charset: $('select[name="charset"]').val(), jdbcParams: $('input[name="jdbcParams"]').val(),
                maxPoolSize: $('input[name="maxPoolSize"]').val(), minIdle: $('input[name="minIdle"]').val(),
                connTimeout: $('input[name="connTimeout"]').val(),
                initSql: $('textarea[name="initSql"]').val(),
                testQuery: $('input[name="testQuery"]').val()
            });
        }

        $.ajax({
            url: '/datasource/api/testConnectionWithConfig',
            type: 'POST', contentType: 'application/json',
            data: JSON.stringify(formData),
            success: function(res) {
                if (res.code === 0 && res.data && res.data.success) {
                    showSuccess('耗时: ' + (res.data.duration || 'N/A') + 'ms', { title: '连接测试成功' });
                } else {
                    var msg = (res.data && res.data.error) || (res.data && res.data.message) || res.message || '未知错误';
                    showError(msg, { title: '连接测试失败' });
                }
            },
            error: function(xhr) {
                var msg = '请求失败';
                try { var err = JSON.parse(xhr.responseText); msg = err.message || msg; } catch(e) {}
                showError(msg, { title: '连接测试异常' });
            },
            complete: function() { $btn.prop('disabled', false).html('<i class="bi bi-lightning-charge"></i> 测试连接'); }
        });
    });

    // 表单提交前同步 + 按当前 tab 校验
    $('#datasourceForm').on('submit', function(e) {
        syncAllCM();
        syncAuthFields();
        // 确保 sourceType 正确
        if ($('#apiPanel').hasClass('active')) {
            $('#sourceType').val('API');
            if (!$('#apiUrl').val().trim()) {
                showWarning('请输入接口URL');
                e.preventDefault();
                return false;
            }
        } else {
            $('#sourceType').val('DB');
            if (!$('input[name="host"]').val().trim()) {
                showWarning('请输入主机地址');
                e.preventDefault();
                return false;
            }
            if (!$('input[name="port"]').val()) {
                showWarning('请输入端口');
                e.preventDefault();
                return false;
            }
        }
    });

});
</script>

</@main>
