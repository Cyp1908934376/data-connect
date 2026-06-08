<#include "../layouts/main.ftl">
<@main title="数据源调试" activeMenu="datasource"
extraCss=["/static/codemirror/codemirror.min.css", "/static/codemirror/theme/monokai.css"]
extraJs=["/static/codemirror/codemirror.min.js", "/static/codemirror/mode/sql.min.js", "/static/codemirror/mode/javascript.min.js"]>

<style>
    .debug-header { background: #f8f9fa; border-radius: 0.5rem; padding: 1rem 1.25rem; margin-bottom: 1rem; }
    .debug-header .info-item { display: inline-flex; align-items: center; gap: 0.3rem; margin-right: 1.5rem; color: #495057; }
    .debug-header .info-label { font-size: 0.8rem; color: #6c757d; }
    .debug-result { min-height: 100px; }
    .debug-result.alert { font-family: monospace; font-size: 0.85rem; white-space: pre-wrap; }
    .table-list { max-height: 400px; overflow-y: auto; }
    .table-list .list-group-item { cursor: pointer; }
    .table-list .list-group-item:hover { background-color: #f0f7ff; }
    .table-list .list-group-item.active { background-color: #0d6efd; border-color: #0d6efd; color: #fff; }
    .preview-table-container { max-height: 400px; overflow: auto; }
    .preview-table-container table { font-size: 0.85rem; }
    .columns-table-container { max-height: 400px; overflow: auto; }
    .duration-badge { font-size: 0.75rem; }
    #resultArea1 .alert, #resultAreaApi .alert { white-space: pre-wrap; font-family: monospace; font-size: 0.85rem; }
    .api-param-row { display: flex; gap: 0.5rem; align-items: center; margin-bottom: 0.5rem; }
    .api-param-row input { flex: 1; }
    .api-param-row .btn { flex-shrink: 0; }
    .response-section { margin-top: 1rem; }
    .response-section h6 { border-bottom: 1px solid #dee2e6; padding-bottom: 0.3rem; }
    .response-headers { max-height: 200px; overflow: auto; }
    .response-body { max-height: 500px; overflow: auto; background: #1e1e1e; border-radius: 0.3rem; padding: 1rem; }
    .response-body pre { color: #d4d4d4; margin: 0; font-size: 0.8rem; white-space: pre-wrap; word-break: break-all; }
    .chain-step { border-left: 3px solid #0d6efd; padding-left: 1rem; margin-bottom: 0.75rem; }
    .chain-step.failed { border-left-color: #dc3545; }
    .chain-step .step-header { font-weight: 600; }
    .mode-badge { font-size: 0.7rem; vertical-align: middle; }
</style>

<#assign dsId = (config.id)!'' />
<#assign dsName = (config.name)!'未知' />
<#assign dsType = (config.sourceType)!'DB' />
<#assign dsHost = (config.host)!'' />
<#assign dsApiUrl = (config.apiUrl)!'' />
<#assign dsApiMode = (config.apiMode)!'SINGLE' />
<#assign dsApiMethod = (config.apiMethod)!'GET' />

<div class="datasource-debug-page">
    <!-- 数据源信息头部 -->
    <div class="debug-header">
        <div class="d-flex align-items-center flex-wrap gap-3">
            <h5 class="mb-0"><i class="bi bi-bug"></i> 调试 - ${dsName}</h5>
            <span class="info-item">
                <span class="info-label">类型:</span>
                <span class="badge <#if dsType == 'API'>bg-info<#else>bg-secondary</#if>">${dsType}</span>
            </span>
            <#if dsType == 'API'>
                <span class="info-item">
                    <span class="info-label">URL:</span>
                    <code>${dsApiUrl}</code>
                </span>
                <span class="info-item">
                    <span class="info-label">模式:</span>
                    <span class="badge bg-primary mode-badge">${dsApiMode}</span>
                </span>
                <span class="info-item">
                    <span class="info-label">方法:</span>
                    <span class="badge bg-light text-dark">${dsApiMethod}</span>
                </span>
            <#else>
                <span class="info-item">
                    <span class="info-label">主机:</span>
                    <code>${dsHost}</code>
                </span>
            </#if>
        </div>
    </div>

    <!-- 调试标签页 -->
    <ul class="nav nav-tabs" id="debugTabs" role="tablist">
        <li class="nav-item" role="presentation">
            <button class="nav-link active" id="tab1-tab" data-bs-toggle="tab" data-bs-target="#tab1" type="button" role="tab">
                <i class="bi bi-plug"></i> 连接测试
            </button>
        </li>
        <#if dsType == 'API'>
        <li class="nav-item" role="presentation">
            <button class="nav-link" id="tab2-tab" data-bs-toggle="tab" data-bs-target="#tab2" type="button" role="tab">
                <i class="bi bi-send"></i> API调用测试
            </button>
        </li>
        <#else>
        <li class="nav-item" role="presentation">
            <button class="nav-link" id="tab2-tab" data-bs-toggle="tab" data-bs-target="#tab2" type="button" role="tab">
                <i class="bi bi-terminal"></i> 查询测试
            </button>
        </li>
        <li class="nav-item" role="presentation">
            <button class="nav-link" id="tab3-tab" data-bs-toggle="tab" data-bs-target="#tab3" type="button" role="tab">
                <i class="bi bi-diagram-3"></i> 表结构预览
            </button>
        </li>
        <li class="nav-item" role="presentation">
            <button class="nav-link" id="tab4-tab" data-bs-toggle="tab" data-bs-target="#tab4" type="button" role="tab">
                <i class="bi bi-table"></i> 数据预览
            </button>
        </li>
        </#if>
        <li class="nav-item" role="presentation">
            <button class="nav-link" id="tab5-tab" data-bs-toggle="tab" data-bs-target="#tab5" type="button" role="tab">
                <i class="bi bi-clock-history"></i> 调试历史
            </button>
        </li>
    </ul>

    <div class="tab-content pt-3" id="debugTabContent">
        <!-- ===== Tab 1: 连接测试 ===== -->
        <div class="tab-pane fade show active" id="tab1" role="tabpanel">
            <div class="mb-3">
                <button type="button" id="btnTestConnection" class="btn btn-primary">
                    <i class="bi bi-lightning-charge"></i> 测试连接
                </button>
            </div>
            <div id="resultArea1" class="debug-result"></div>
        </div>

        <#if dsType == 'API'>
        <!-- ===== Tab 2: API调用测试 ===== -->
        <div class="tab-pane fade" id="tab2" role="tabpanel">
            <!-- 参数输入区 -->
            <div class="card mb-3">
                <div class="card-header bg-light d-flex justify-content-between align-items-center">
                    <span><i class="bi bi-braces"></i> 请求参数 <small class="text-muted">(URL 变量替换: ${'$'}{变量名})</small></span>
                    <button type="button" id="btnAddParam" class="btn btn-sm btn-outline-secondary">
                        <i class="bi bi-plus"></i> 添加参数
                    </button>
                </div>
                <div class="card-body" id="apiParamContainer">
                    <div class="api-param-row" id="paramRowTemplate" style="display:none;">
                        <input type="text" class="form-control form-control-sm param-key" placeholder="参数名">
                        <input type="text" class="form-control form-control-sm param-value" placeholder="参数值">
                        <button type="button" class="btn btn-sm btn-outline-danger btn-remove-param">
                            <i class="bi bi-trash"></i>
                        </button>
                    </div>
                    <div id="apiParamRows">
                        <div class="text-center text-muted py-2">
                            <small>URL 中无 ${'$'}{...} 占位符时无需添加参数</small>
                        </div>
                    </div>
                </div>
            </div>

            <div class="mb-3">
                <button type="button" id="btnExecuteApi" class="btn btn-primary">
                    <i class="bi bi-play-fill"></i> 执行调用
                </button>
                <button type="button" id="btnClearApiParams" class="btn btn-outline-secondary ms-2">
                    <i class="bi bi-eraser"></i> 清空参数
                </button>
            </div>

            <div id="resultAreaApi" class="debug-result"></div>

            <!-- 响应详情区 (动态填充) -->
            <div id="apiResponseDetail" style="display:none;">
                <!-- 状态码 & 耗时 -->
                <div class="mb-2" id="apiStatusBar"></div>
                <!-- 实际请求 URL -->
                <div class="response-section mb-2" id="apiActualUrlSection" style="display:none;">
                    <h6>实际请求</h6>
                    <code id="apiActualUrl" style="word-break:break-all;font-size:0.8rem;"></code>
                </div>
                <!-- 响应头 -->
                <div class="response-section mb-2">
                    <h6 class="d-flex align-items-center gap-2">
                        响应头
                        <button type="button" class="btn btn-sm btn-outline-secondary" data-bs-toggle="collapse" data-bs-target="#respHeadersCollapse">
                            <i class="bi bi-chevron-expand"></i>
                        </button>
                    </h6>
                    <div class="collapse show response-headers" id="respHeadersCollapse">
                        <table class="table table-sm table-bordered" id="respHeadersTable">
                        </table>
                    </div>
                </div>
                <!-- 响应体 -->
                <div class="response-section">
                    <h6 class="d-flex align-items-center gap-2">
                        响应体
                        <button type="button" class="btn btn-sm btn-outline-secondary" id="btnFormatJson">
                            <i class="bi bi-code-slash"></i> 格式化JSON
                        </button>
                    </h6>
                    <div class="response-body">
                        <pre id="respBodyPre"></pre>
                    </div>
                </div>
            </div>
        </div>
        <#else>
        <!-- ===== Tab 2: 查询测试 (DB) ===== -->
        <div class="tab-pane fade" id="tab2" role="tabpanel">
            <div class="mb-3">
                <label class="form-label fw-bold">SQL 查询语句</label>
                <textarea id="sqlInput" name="sql" data-cm-mode="sql">SELECT 1</textarea>
            </div>
            <div class="mb-3">
                <button type="button" id="btnExecuteQuery" class="btn btn-primary">
                    <i class="bi bi-play-fill"></i> 执行
                </button>
                <button type="button" id="btnClearSql" class="btn btn-outline-secondary ms-2">
                    <i class="bi bi-eraser"></i> 清空
                </button>
            </div>
            <div id="resultArea2" class="debug-result"></div>
        </div>

        <!-- ===== Tab 3: 表结构预览 (DB) ===== -->
        <div class="tab-pane fade" id="tab3" role="tabpanel">
            <div class="mb-3">
                <button type="button" id="btnLoadTables" class="btn btn-primary">
                    <i class="bi bi-arrow-repeat"></i> 加载表列表
                </button>
            </div>
            <div class="row g-3">
                <div class="col-md-4">
                    <h6 class="text-muted">表列表</h6>
                    <div class="table-list" id="tableList">
                        <div class="list-group" id="tableListGroup">
                            <div class="list-group-item text-center text-muted py-3">
                                请先点击"加载表列表"
                            </div>
                        </div>
                    </div>
                </div>
                <div class="col-md-8">
                    <h6 class="text-muted" id="columnsTitle">列信息 (选择左侧表查看)</h6>
                    <div class="columns-table-container" id="columnsContainer">
                        <div class="text-center text-muted py-4">
                            <i class="bi bi-arrow-left-circle" style="font-size:1.5rem;display:block;"></i>
                            请先选择一个表
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- ===== Tab 4: 数据预览 (DB) ===== -->
        <div class="tab-pane fade" id="tab4" role="tabpanel">
            <div class="row g-3 align-items-end mb-3">
                <div class="col-md-5">
                    <label class="form-label fw-bold">选择表</label>
                    <select id="previewTableSelect" class="form-select">
                        <option value="">请先选择表</option>
                    </select>
                </div>
                <div class="col-md-3">
                    <label class="form-label fw-bold">预览行数</label>
                    <select id="previewLimit" class="form-select">
                        <option value="10" selected>10 条</option>
                        <option value="20">20 条</option>
                        <option value="50">50 条</option>
                        <option value="100">100 条</option>
                    </select>
                </div>
                <div class="col-md-2">
                    <button type="button" id="btnPreviewData" class="btn btn-primary w-100">
                        <i class="bi bi-eye"></i> 预览
                    </button>
                </div>
                <div class="col-md-2">
                    <button type="button" id="btnLoadTablesForPreview" class="btn btn-outline-secondary w-100">
                        <i class="bi bi-arrow-repeat"></i> 加载表
                    </button>
                </div>
            </div>
            <div class="preview-table-container" id="previewDataContainer">
                <div class="text-center text-muted py-4">
                    请点击"加载表"然后选择一个表进行预览
                </div>
            </div>
        </div>
        </#if>

        <!-- ===== Tab 5: 调试历史 ===== -->
        <div class="tab-pane fade" id="tab5" role="tabpanel">
            <div class="table-responsive">
                <table class="table table-striped table-hover table-sm align-middle">
                    <thead class="table-light">
                        <tr>
                            <th>ID</th>
                            <th>操作类型</th>
                            <th>结果状态</th>
                            <th>耗时</th>
                            <th>操作时间</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        <#if debugLogs?? && debugLogs?size gt 0>
                            <#list debugLogs as log>
                            <tr>
                                <td>${log.id}</td>
                                <td>${(log.operationType)!'-'}</td>
                                <td>
                                    <#if log.resultStatus == 'SUCCESS'>
                                        <span class="badge bg-success">成功</span>
                                    <#elseif log.resultStatus == 'FAIL'>
                                        <span class="badge bg-danger">失败</span>
                                    <#else>
                                        <span class="badge bg-secondary">${(log.resultStatus)!'-'}</span>
                                    </#if>
                                </td>
                                <td>
                                    <#if log.duration??>
                                        <span class="duration-badge">${log.duration}ms</span>
                                    <#else>
                                        -
                                    </#if>
                                </td>
                                <td><small>${(log.createTime)!''}</small></td>
                                <td>
                                    <button type="button" class="btn btn-sm btn-outline-secondary view-log-detail"
                                            data-log-id="${log.id}">
                                        <i class="bi bi-eye"></i> 详情
                                    </button>
                                </td>
                            </tr>
                            </#list>
                        <#else>
                            <tr>
                                <td colspan="6" class="text-center text-muted py-4">
                                    暂无调试记录
                                </td>
                            </tr>
                        </#if>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
</div>

<!-- 调试详情 Modal -->
<div class="modal fade" id="debugDetailModal" tabindex="-1">
    <div class="modal-dialog modal-lg modal-dialog-scrollable">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title"><i class="bi bi-info-circle"></i> 调试详情</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
                <div class="mb-3">
                    <h6 class="d-flex justify-content-between align-items-center">
                        操作快照
                        <button type="button" class="btn btn-sm btn-outline-secondary copy-snapshot-btn" data-target="#detailConfig" title="复制操作快照">
                            <i class="bi bi-clipboard"></i> 复制
                        </button>
                    </h6>
                    <pre id="detailConfig" class="border rounded p-3 bg-light" style="max-height:300px;overflow:auto;font-size:0.8rem;"></pre>
                </div>
                <div>
                    <h6 class="d-flex justify-content-between align-items-center">
                        结果快照
                        <button type="button" class="btn btn-sm btn-outline-secondary copy-snapshot-btn" data-target="#detailResult" title="复制结果快照">
                            <i class="bi bi-clipboard"></i> 复制
                        </button>
                    </h6>
                    <pre id="detailResult" class="border rounded p-3 bg-light" style="max-height:300px;overflow:auto;font-size:0.8rem;"></pre>
                </div>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">关闭</button>
            </div>
        </div>
    </div>
</div>

<script>
$(function() {
    var datasourceId = '${dsId}';
    var dsType = '${dsType}';
    var dsApiMode = '${dsApiMode}';

    // ========== 初始化 CodeMirror 编辑器 ==========
    <#if dsType != 'API'>
    // SQL 编辑器 (DB only)
    var sqlEditor = CodeMirror.fromTextArea(document.getElementById('sqlInput'), {
        mode: 'sql', theme: 'monokai', lineNumbers: true,
        matchBrackets: true, autoCloseBrackets: true, lineWrapping: true, tabSize: 2
    });
    sqlEditor.setSize(null, 120);
    </#if>

    // JSON 响应体编辑器 (用于 Tab 5 详情)
    var jsonViewer = null;

    // ========== 通用辅助函数 ==========
    function ajaxGet(url, data, onSuccess, onError, onComplete) {
        $.ajax({
            url: url,
            type: 'GET',
            data: data,
            success: function(res) {
                onSuccess(res.data || res);
            },
            error: onError || function(xhr) {
                var msg = '请求失败 (' + xhr.status + ')';
                try {
                    var err = JSON.parse(xhr.responseText);
                    msg = err.message || msg;
                } catch(e) {}
                if (typeof showError === 'function') { showError(msg); } else { alert(msg); }
            },
            complete: onComplete
        });
    }

    function ajaxPost(url, data, onSuccess, onError, onComplete) {
        $.ajax({
            url: url,
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(data),
            success: function(res) {
                onSuccess(res.data || res);
            },
            error: onError || function(xhr) {
                var msg = '请求失败 (' + xhr.status + ')';
                try {
                    var err = JSON.parse(xhr.responseText);
                    msg = err.message || msg;
                } catch(e) {}
                if (typeof showError === 'function') { showError(msg); } else { alert(msg); }
            },
            complete: onComplete
        });
    }

    function showLoading(selector) {
        $(selector).html('<div class="text-center py-3"><span class="spinner-border spinner-border-sm me-2"></span>处理中...</div>');
    }

    function formatDuration(ms) {
        if (ms === undefined || ms === null) return '';
        return '<span class="badge bg-light text-dark duration-badge ms-2">耗时: ' + ms + 'ms</span>';
    }

    function escapeHtml(text) {
        if (!text) return '';
        return String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    // ========== Tab 1: 连接测试 (DB & API 共用) ==========
    $('#btnTestConnection').on('click', function() {
        var $btn = $(this);
        var originalHtml = $btn.html();
        $btn.prop('disabled', true).html('<span class="spinner-border spinner-border-sm"></span> 测试中...');
        showLoading('#resultArea1');

        ajaxGet('/datasource/api/testConnection', { id: datasourceId }, function(res) {
            var d = res.data || res;
            var html = '';
            if (d.success) {
                html += '<div class="alert alert-success"><i class="bi bi-check-circle-fill me-2"></i><strong>连接成功</strong>';
                html += formatDuration(d.duration);
                if (d.message) {
                    html += '<hr class="my-2"><small>' + escapeHtml(d.message) + '</small>';
                }
                if (d.statusCode) {
                    html += '<br><small>HTTP 状态码: <strong>' + d.statusCode + '</strong></small>';
                }
                if (d.body) {
                    html += '<hr class="my-2"><small style="white-space:pre-wrap;">' + escapeHtml(d.body) + '</small>';
                }
                html += '</div>';
            } else {
                html += '<div class="alert alert-danger"><i class="bi bi-x-circle-fill me-2"></i><strong>连接失败</strong>';
                html += formatDuration(d.duration);
                var errMsg = d.message || d.error || '';
                if (errMsg) {
                    html += '<hr class="my-2"><small>' + escapeHtml(errMsg) + '</small>';
                }
                html += '</div>';
            }
            $('#resultArea1').html(html);
        }, function(xhr) {
            var msg = '';
            try { var err = JSON.parse(xhr.responseText); msg = err.message || ''; } catch(e) {}
            $('#resultArea1').html('<div class="alert alert-danger"><i class="bi bi-exclamation-triangle-fill me-2"></i><strong>请求异常</strong>' +
                (msg ? '<hr class="my-2"><small>' + escapeHtml(msg) + '</small>' : '') + '</div>');
        }, function() {
            $btn.prop('disabled', false).html(originalHtml);
        });
    });

    // ========== API: Tab 2 - API调用测试 ==========
    if (dsType === 'API') {
        var paramCounter = 0;

        // 添加参数行
        function addParamRow(key, value) {
            paramCounter++;
            var $row = $('<div class="api-param-row" data-param-id="' + paramCounter + '">'
                + '<input type="text" class="form-control form-control-sm param-key" placeholder="参数名" value="' + escapeHtml(key || '') + '">'
                + '<input type="text" class="form-control form-control-sm param-value" placeholder="参数值" value="' + escapeHtml(value || '') + '">'
                + '<button type="button" class="btn btn-sm btn-outline-danger btn-remove-param"><i class="bi bi-trash"></i></button>'
                + '</div>');
            $('#apiParamRows').append($row);
        }

        $('#btnAddParam').on('click', function() {
            addParamRow('', '');
        });

        $('#apiParamRows').on('click', '.btn-remove-param', function() {
            $(this).closest('.api-param-row').remove();
        });

        $('#btnClearApiParams').on('click', function() {
            $('#apiParamRows').empty();
            $('#apiParamRows').html('<div class="text-center text-muted py-2"><small>URL 中无 ${'$'}{...} 占位符时无需添加参数</small></div>');
            $('#resultAreaApi').html('');
            $('#apiResponseDetail').hide();
        });

        // 收集参数
        function collectParams() {
            var params = {};
            $('#apiParamRows .api-param-row').each(function() {
                var key = $.trim($(this).find('.param-key').val());
                var value = $.trim($(this).find('.param-value').val());
                if (key) {
                    params[key] = value;
                }
            });
            return params;
        }

        // 格式化 JSON 字符串（尝试解析后美化）
        function tryPrettyJson(str) {
            try {
                var obj = JSON.parse(str);
                return JSON.stringify(obj, null, 2);
            } catch(e) {
                return str;
            }
        }

        // 渲染 API 响应
        function renderApiResponse(res) {
            $('#apiResponseDetail').show();
            var d = res;

            // 状态栏
            var statusHtml = '';
            if (d.success) {
                statusHtml += '<span class="badge bg-success fs-6"><i class="bi bi-check-circle me-1"></i>成功</span> ';
            } else {
                statusHtml += '<span class="badge bg-danger fs-6"><i class="bi bi-x-circle me-1"></i>失败</span> ';
            }
            if (d.statusCode) {
                var cls = d.statusCode >= 200 && d.statusCode < 300 ? 'bg-success' : (d.statusCode >= 400 ? 'bg-danger' : 'bg-warning');
                statusHtml += '<span class="badge ' + cls + '">HTTP ' + d.statusCode + '</span> ';
            }
            statusHtml += formatDuration(d.duration);
            if (d.error) {
                statusHtml += '<div class="alert alert-danger mt-2 mb-0"><small>' + escapeHtml(d.error) + '</small></div>';
            }
            $('#apiStatusBar').html(statusHtml);

            // 实际请求 URL
            if (d.url) {
                $('#apiActualUrlSection').show();
                var methodBadge = '<span class="badge bg-secondary">' + (d.method || 'GET') + '</span> ';
                $('#apiActualUrl').html(methodBadge + escapeHtml(d.url));
            } else {
                $('#apiActualUrlSection').hide();
            }

            // 响应头
            var headersHtml = '';
            if (d.responseHeaders && typeof d.responseHeaders === 'object') {
                var keys = Object.keys(d.responseHeaders);
                for (var i = 0; i < keys.length; i++) {
                    headersHtml += '<tr><td class="text-muted" style="width:30%;"><small>' + escapeHtml(keys[i]) + '</small></td>'
                        + '<td><small>' + escapeHtml(String(d.responseHeaders[keys[i]])) + '</small></td></tr>';
                }
            }
            if (!headersHtml) {
                headersHtml = '<tr><td class="text-muted">无响应头</td></tr>';
            }
            $('#respHeadersTable').html(headersHtml);

            // 响应体 - 优先显示 JSON，否则原始文本
            var bodyHtml;
            if (d.bodyJson !== undefined && d.bodyJson !== null) {
                bodyHtml = JSON.stringify(d.bodyJson, null, 2);
            } else if (d.body) {
                bodyHtml = tryPrettyJson(d.body);
            } else if (d.lastResponse) {
                bodyHtml = tryPrettyJson(String(d.lastResponse));
            } else {
                bodyHtml = '(空响应)';
            }
            $('#respBodyPre').text(bodyHtml);

            // CHAIN 模式：显示各步骤结果
            var chainStepsHtml = '';
            if (d.steps && Array.isArray(d.steps) && d.steps.length > 0) {
                chainStepsHtml += '<div class="response-section mt-3"><h6><i class="bi bi-link-45deg"></i> 链式调用步骤</h6>';
                for (var i = 0; i < d.steps.length; i++) {
                    var step = d.steps[i];
                    var stepClass = step.success ? 'chain-step' : 'chain-step failed';
                    chainStepsHtml += '<div class="' + stepClass + '">';
                    chainStepsHtml += '<div class="step-header">' + escapeHtml(step.step || ('Step ' + (i+1))) + ' ';
                    chainStepsHtml += step.success
                        ? '<span class="badge bg-success">成功</span>'
                        : '<span class="badge bg-danger">失败</span>';
                    if (step.statusCode) {
                        chainStepsHtml += ' <span class="badge bg-secondary">HTTP ' + step.statusCode + '</span>';
                    }
                    chainStepsHtml += '</div>';
                    // 提取的变量
                    var extracted = [];
                    for (var k in step) {
                        if (k.indexOf('extracted_') === 0) {
                            extracted.push('<small class="text-success"><strong>' + escapeHtml(k.substring(10)) + '</strong> = ' + escapeHtml(String(step[k])) + '</small>');
                        }
                    }
                    if (extracted.length > 0) {
                        chainStepsHtml += '<div class="mt-1">' + extracted.join(' &nbsp;|&nbsp; ') + '</div>';
                    }
                    if (!step.success && step.error) {
                        chainStepsHtml += '<div class="text-danger"><small>' + escapeHtml(step.error) + '</small></div>';
                    }
                    if (step.body) {
                        chainStepsHtml += '<div class="mt-1"><small class="text-muted">响应: </small><code style="font-size:0.7rem;">' + escapeHtml(tryPrettyJson(String(step.body)).substring(0, 500)) + '</code></div>';
                    }
                    chainStepsHtml += '</div>';
                }
                chainStepsHtml += '</div>';
            }
            // SCRIPT 模式：显示模板执行结果
            if (dsApiMode === 'SCRIPT' && d.data !== undefined) {
                chainStepsHtml += '<div class="response-section mt-3"><h6><i class="bi bi-file-code"></i> 脚本输出</h6>';
                chainStepsHtml += '<pre class="bg-light p-2" style="font-size:0.8rem;max-height:200px;overflow:auto;">' + escapeHtml(JSON.stringify(d.data, null, 2)) + '</pre>';
                chainStepsHtml += '</div>';
            }

            // 结果区顶部插入
            var resultHtml = '';
            if (chainStepsHtml) {
                resultHtml += chainStepsHtml;
            }
            $('#resultAreaApi').html(resultHtml);
        }

        // 执行 API 调用
        $('#btnExecuteApi').on('click', function() {
            var params = collectParams();
            var $btn = $(this);
            var originalHtml = $btn.html();
            $btn.prop('disabled', true).html('<span class="spinner-border spinner-border-sm"></span> 执行中...');
            showLoading('#resultAreaApi');
            $('#apiResponseDetail').hide();

            ajaxPost('/datasource/api/executeApi', { id: datasourceId, params: params }, function(res) {
                renderApiResponse(res);
            }, function(xhr) {
                var msg = '';
                try { var err = JSON.parse(xhr.responseText); msg = err.message || ''; } catch(e) {}
                $('#apiResponseDetail').show();
                $('#apiStatusBar').html('<span class="badge bg-danger">请求异常</span>');
                if (msg) {
                    $('#apiStatusBar').append('<div class="alert alert-danger mt-2 mb-0"><small>' + escapeHtml(msg) + '</small></div>');
                }
                $('#apiActualUrlSection').hide();
                $('#respHeadersTable').html('<tr><td class="text-muted">无</td></tr>');
                $('#respBodyPre').text(msg || '请求失败');
            }, function() {
                $btn.prop('disabled', false).html(originalHtml);
            });
        });

        // JSON 格式化按钮
        $('#btnFormatJson').on('click', function() {
            var currentText = $('#respBodyPre').text();
            if (currentText) {
                $('#respBodyPre').text(tryPrettyJson(currentText));
            }
        });
    }

    // ========== DB: Tab 2 - 查询测试 ==========
    if (dsType !== 'API') {
        $('#btnExecuteQuery').on('click', function() {
            var sql = $.trim(sqlEditor.getValue());
            if (!sql) {
                if (typeof showWarning === 'function') { showWarning('请输入SQL查询语句'); } else { alert('请输入SQL查询语句'); }
                return;
            }
            var $btn = $(this);
            var originalHtml = $btn.html();
            $btn.prop('disabled', true).html('<span class="spinner-border spinner-border-sm"></span> 执行中...');
            showLoading('#resultArea2');

            ajaxPost('/datasource/api/executeQuery', { id: datasourceId, sql: sql }, function(res) {
                var html = '';
                if (res.success) {
                    if (res.columns && res.rows) {
                        html += '<div class="mb-2"><strong>查询结果</strong>: ' + res.rows.length + ' 行';
                        html += formatDuration(res.duration) + '</div>';
                        html += '<div class="table-responsive"><table class="table table-sm table-bordered table-hover"><thead><tr>';
                        for (var i = 0; i < res.columns.length; i++) {
                            html += '<th>' + escapeHtml(res.columns[i]) + '</th>';
                        }
                        html += '</tr></thead><tbody>';
                        for (var r = 0; r < res.rows.length; r++) {
                            html += '<tr>';
                            for (var c = 0; c < res.columns.length; c++) {
                                var val = res.rows[r][c];
                                html += '<td>' + (val !== null && val !== undefined ? escapeHtml(String(val)) : '<span class="text-muted fst-italic">NULL</span>') + '</td>';
                            }
                            html += '</tr>';
                        }
                        html += '</tbody></table></div>';
                    } else if (res.affectedRows !== undefined) {
                        html += '<div class="alert alert-info"><i class="bi bi-info-circle me-2"></i>影响行数: <strong>' + res.affectedRows + '</strong>';
                        html += formatDuration(res.duration) + '</div>';
                    } else {
                        html += '<div class="alert alert-success"><i class="bi bi-check-circle me-2"></i>执行成功';
                        html += formatDuration(res.duration) + '</div>';
                    }
                } else {
                    html += '<div class="alert alert-danger"><i class="bi bi-x-circle-fill me-2"></i><strong>执行失败</strong>';
                    html += formatDuration(res.duration);
                    if (res.message || res.error) {
                        html += '<hr class="my-2"><small>' + escapeHtml(res.message || res.error) + '</small>';
                    }
                    html += '</div>';
                }
                $('#resultArea2').html(html);
            }, function(xhr) {
                var msg = '';
                try { var err = JSON.parse(xhr.responseText); msg = err.message || ''; } catch(e) {}
                $('#resultArea2').html('<div class="alert alert-danger"><i class="bi bi-exclamation-triangle-fill me-2"></i><strong>请求异常</strong>' +
                    (msg ? '<hr class="my-2"><small>' + escapeHtml(msg) + '</small>' : '') + '</div>');
            }, function() {
                $btn.prop('disabled', false).html(originalHtml);
            });
        });

        $('#btnClearSql').on('click', function() {
            sqlEditor.setValue('');
            sqlEditor.focus();
        });

        // ========== DB: Tab 3 - 表结构预览 ==========
        $('#btnLoadTables').on('click', function() {
            var $btn = $(this);
            var originalHtml = $btn.html();
            $btn.prop('disabled', true).html('<span class="spinner-border spinner-border-sm"></span> 加载中...');

            ajaxGet('/datasource/api/getTables', { id: datasourceId }, function(res) {
                if (res.success && res.tables && res.tables.length > 0) {
                    var html = '';
                    for (var i = 0; i < res.tables.length; i++) {
                        var t = res.tables[i];
                        var tableName = typeof t === 'string' ? t : (t.tableName || t.name || t.TABLE_NAME || '');
                        html += '<button type="button" class="list-group-item list-group-item-action table-select-item" data-table="' + escapeHtml(tableName) + '">';
                        html += '<i class="bi bi-table me-2"></i>' + escapeHtml(tableName);
                        html += '</button>';
                    }
                    $('#tableListGroup').html(html);
                } else {
                    $('#tableListGroup').html('<div class="list-group-item text-center text-muted py-3">没有找到表</div>');
                }
            }, function(xhr) {
                var msg = '';
                try { var err = JSON.parse(xhr.responseText); msg = err.message || ''; } catch(e) {}
                $('#tableListGroup').html('<div class="list-group-item text-center text-danger py-3">加载失败: ' + escapeHtml(msg) + '</div>');
            }, function() {
                $btn.prop('disabled', false).html(originalHtml);
            });
        });

        $('#tableListGroup').on('click', '.table-select-item', function() {
            var $item = $(this);
            var tableName = $item.data('table');
            $('#tableListGroup .table-select-item').removeClass('active');
            $item.addClass('active');

            $('#columnsTitle').text('列信息 - ' + tableName);
            showLoading('#columnsContainer');

            ajaxGet('/datasource/api/getColumns', { id: datasourceId, tableName: tableName }, function(res) {
                if (res.success && res.columns && res.columns.length > 0) {
                    var html = '<table class="table table-sm table-bordered table-hover">';
                    html += '<thead class="table-light"><tr><th>列名</th><th>数据类型</th><th>允许为空</th><th>默认值</th><th>备注</th></tr></thead><tbody>';
                    for (var i = 0; i < res.columns.length; i++) {
                        var col = res.columns[i];
                        html += '<tr>';
                        html += '<td><strong>' + escapeHtml(col.columnName || col.name || col.COLUMN_NAME || '') + '</strong></td>';
                        html += '<td><code>' + escapeHtml(col.dataType || col.type || col.TYPE_NAME || '') + (col.columnSize ? '(' + col.columnSize + ')' : '') + '</code></td>';
                        html += '<td>' + (col.nullable == 1 || col.nullable === true || col.IS_NULLABLE === 'YES' ? '<span class="text-muted">是</span>' : '否') + '</td>';
                        html += '<td>' + (col.defaultValue || col.COLUMN_DEF || '') + '</td>';
                        html += '<td><small>' + (col.remarks || col.comment || col.REMARKS || '') + '</small></td>';
                        html += '</tr>';
                    }
                    html += '</tbody></table>';
                    $('#columnsContainer').html(html);
                } else {
                    $('#columnsContainer').html('<div class="text-center text-muted py-4">未找到列信息</div>');
                }
            }, function(xhr) {
                var msg = '';
                try { var err = JSON.parse(xhr.responseText); msg = err.message || ''; } catch(e) {}
                $('#columnsContainer').html('<div class="alert alert-danger">加载失败: ' + escapeHtml(msg) + '</div>');
            });
        });

        // ========== DB: Tab 4 - 数据预览 ==========
        function loadTableOptionsForPreview() {
            var $btn = $('#btnLoadTablesForPreview');
            var originalHtml = $btn.html();
            $btn.prop('disabled', true).html('<span class="spinner-border spinner-border-sm"></span>');

            ajaxGet('/datasource/api/getTables', { id: datasourceId }, function(res) {
                if (res.success && res.tables && res.tables.length > 0) {
                    var options = '<option value="">请选择表</option>';
                    for (var i = 0; i < res.tables.length; i++) {
                        var t = res.tables[i];
                        var tableName = typeof t === 'string' ? t : (t.tableName || t.name || t.TABLE_NAME || '');
                        options += '<option value="' + escapeHtml(tableName) + '">' + escapeHtml(tableName) + '</option>';
                    }
                    $('#previewTableSelect').html(options);
                } else {
                    if (typeof showWarning === 'function') { showWarning('未获取到表列表'); } else { alert('未获取到表列表'); }
                }
            }, null, function() {
                $btn.prop('disabled', false).html(originalHtml);
            });
        }

        $('#btnLoadTablesForPreview').on('click', loadTableOptionsForPreview);

        $('#btnPreviewData').on('click', function() {
            var tableName = $('#previewTableSelect').val();
            if (!tableName) {
                if (typeof showWarning === 'function') { showWarning('请先选择要预览的表'); } else { alert('请先选择要预览的表'); }
                return;
            }
            var limit = $('#previewLimit').val();
            var $btn = $(this);
            var originalHtml = $btn.html();
            $btn.prop('disabled', true).html('<span class="spinner-border spinner-border-sm"></span>');

            ajaxGet('/datasource/api/previewData', { id: datasourceId, tableName: tableName, limit: limit }, function(res) {
                if (res.success && res.columns && res.rows) {
                    var html = '<p class="text-muted mb-2">表 <strong>' + escapeHtml(tableName) + '</strong>，共 ' + res.rows.length + ' 行</p>';
                    html += '<table class="table table-sm table-bordered table-hover"><thead class="table-light"><tr>';
                    for (var i = 0; i < res.columns.length; i++) {
                        html += '<th>' + escapeHtml(res.columns[i]) + '</th>';
                    }
                    html += '</tr></thead><tbody>';
                    for (var r = 0; r < res.rows.length; r++) {
                        html += '<tr>';
                        for (var c = 0; c < res.columns.length; c++) {
                            var val = res.rows[r][c];
                            html += '<td>' + (val !== null && val !== undefined ? escapeHtml(String(val)) : '<span class="text-muted fst-italic">NULL</span>') + '</td>';
                        }
                        html += '</tr>';
                    }
                    html += '</tbody></table>';
                    $('#previewDataContainer').html(html);
                } else {
                    $('#previewDataContainer').html('<div class="alert alert-warning">未返回数据或表可能为空</div>');
                }
            }, function(xhr) {
                var msg = '';
                try { var err = JSON.parse(xhr.responseText); msg = err.message || ''; } catch(e) {}
                $('#previewDataContainer').html('<div class="alert alert-danger">预览失败: ' + escapeHtml(msg) + '</div>');
            }, function() {
                $btn.prop('disabled', false).html(originalHtml);
            });
        });

        // Auto-load tables when entering Tab 4
        $('#tab4-tab').on('shown.bs.tab', function() {
            if ($('#previewTableSelect option').length <= 1) {
                loadTableOptionsForPreview();
            }
        });

        // 切换到 Tab 2 时刷新 SQL 编辑器
        $('#tab2-tab').on('shown.bs.tab', function() {
            sqlEditor.refresh();
        });
    }

    // ========== Tab 5: 查看调试详情 (共用) ==========
    var debugLogData = {
        <#if debugLogs??>
            <#list debugLogs as log>
                ${log.id}: {
                    config: ${log.configSnapshot!'{}'},
                    result: ${log.resultSnapshot!'{}'}
                }<#if log_has_next>,</#if>
            </#list>
        </#if>
    };

    // 复制快照按钮
    $(document).on('click', '.copy-snapshot-btn', function() {
        var $btn = $(this);
        var target = $btn.data('target');
        var text = $(target).text();
        if (!text) return;

        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(function() {
                $btn.html('<i class="bi bi-check-lg"></i> 已复制');
                setTimeout(function() { $btn.html('<i class="bi bi-clipboard"></i> 复制'); }, 1500);
            }).catch(function() {
                fallbackCopy(text, $btn);
            });
        } else {
            fallbackCopy(text, $btn);
        }
    });

    function fallbackCopy(text, $btn) {
        var $ta = $('<textarea>').val(text).css({position:'fixed',left:'-9999px'}).appendTo('body');
        $ta[0].select();
        try { document.execCommand('copy'); $btn.html('<i class="bi bi-check-lg"></i> 已复制'); }
        catch(e) { $btn.html('<i class="bi bi-x-lg"></i> 失败'); }
        setTimeout(function() { $btn.html('<i class="bi bi-clipboard"></i> 复制'); }, 1500);
        $ta.remove();
    }

    $('.view-log-detail').on('click', function() {
        var logId = $(this).data('log-id');
        var data = debugLogData[logId];
        if (!data) return;
        try {
            $('#detailConfig').text(typeof data.config === 'string' ? JSON.stringify(JSON.parse(data.config), null, 2) : JSON.stringify(data.config, null, 2));
        } catch(e) {
            $('#detailConfig').text(JSON.stringify(data.config, null, 2) || '');
        }
        try {
            $('#detailResult').text(typeof data.result === 'string' ? JSON.stringify(JSON.parse(data.result), null, 2) : JSON.stringify(data.result, null, 2));
        } catch(e) {
            $('#detailResult').text(JSON.stringify(data.result, null, 2) || '');
        }
        var modal = new bootstrap.Modal(document.getElementById('debugDetailModal'));
        modal.show();
    });
});
</script>

</@main>