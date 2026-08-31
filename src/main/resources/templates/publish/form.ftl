<#include "../layouts/main.ftl">
<@main title="发布配置" activeMenu="publish">

<style>
    .port-status { font-size: 0.85rem; }
    .port-status.available { color: #198754; }
    .port-status.occupied { color: #dc3545; }
</style>

<div class="publish-form-page">
    <#if error??>
    <div class="alert alert-danger alert-dismissible fade show" role="alert">
        <i class="bi bi-exclamation-triangle"></i> ${error}
        <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
    </div>
    </#if>
    <#if success??>
    <div class="alert alert-success alert-dismissible fade show" role="alert">
        <i class="bi bi-check-circle"></i> ${success}
        <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
    </div>
    </#if>

    <#assign isEdit = config?? && config.id?? />

    <form id="publishForm" method="post" action="/publish/save" novalidate>
        <input type="hidden" name="id" value="${(config.id)!''}">

        <div class="row g-3">
            <!-- 基本信息 -->
            <div class="col-md-6">
                <label class="form-label">发布名称 <span class="text-danger">*</span></label>
                <input type="text" class="form-control" name="name" value="${(config.name)!''}" required placeholder="请输入发布名称" maxlength="200">
            </div>
            <div class="col-md-6">
                <label class="form-label">关联可视化模板 <span class="text-danger">*</span></label>
                <select class="form-select" name="visualTemplateId" id="templateSelect">
                    <option value="">请选择模板</option>
                    <#list templateList as tpl>
                        <option value="${tpl.id}" <#if ((config.visualTemplateId)!0) == tpl.id>selected</#if>>
                            ${tpl.name} (ID: ${tpl.id})
                        </option>
                    </#list>
                </select>
            </div>
            <#if ((config.flowConfigId)!0) gt 0>
            <div class="col-md-6">
                <label class="form-label">关联对接流程 <span class="badge bg-secondary">旧版</span></label>
                <select class="form-select" name="flowConfigId" id="flowSelect">
                    <option value="">不关联</option>
                    <#list flowList as flow>
                        <option value="${flow.id}" <#if ((config.flowConfigId)!0) == flow.id>selected</#if>>
                            ${flow.name} (ID: ${flow.id})
                        </option>
                    </#list>
                </select>
            </div>
            <#else>
            <input type="hidden" name="flowConfigId" value="0">
            </#if>
            <div class="col-12">
                <label class="form-label">描述</label>
                <input type="text" class="form-control" name="description" value="${(config.description)!''}" placeholder="请输入发布描述" maxlength="500">
            </div>

            <!-- 端口配置 -->
            <div class="col-md-6">
                <label class="form-label">
                    端口号 <span class="text-danger">*</span>
                    <button type="button" class="btn btn-sm btn-outline-info ms-2" id="btnAllocatePort">
                        <i class="bi bi-magic"></i> 自动分配
                    </button>
                </label>
                <div class="input-group">
                    <input type="number" class="form-control" name="port" id="portInput" 
                           value="${(config.port)!''}" min="10000" max="65535" 
                           placeholder="10000-65535" required>
                    <button type="button" class="btn btn-outline-secondary" id="btnCheckPort">
                        <i class="bi bi-check-circle"></i> 检测
                    </button>
                </div>
                <div id="portStatus" class="port-status mt-1"></div>
                <small class="form-text text-muted">
                    <i class="bi bi-info-circle"></i> 端口范围 10000-65535，确保端口未被占用
                </small>
            </div>
            <div class="col-md-6">
                <label class="form-label">API路径</label>
                <input type="text" class="form-control" name="apiPath" value="${(config.apiPath)!'/api/data'}" placeholder="/api/data">
            </div>

            <!-- 认证配置 -->
            <div class="col-md-6">
                <label class="form-label">认证方式</label>
                <select class="form-select" name="authType" id="authTypeSelect">
                    <option value="NONE" <#if ((config.authType)!'NONE') == 'NONE'>selected</#if>>无认证</option>
                    <option value="TOKEN" <#if ((config.authType)!'') == 'TOKEN'>selected</#if>>Token认证</option>
                    <option value="BASIC" <#if ((config.authType)!'') == 'BASIC'>selected</#if>>Basic认证</option>
                </select>
            </div>
            <div class="col-md-6" id="authConfigSection" style="display:none;">
                <label class="form-label">认证配置 <small class="text-muted">(JSON)</small></label>
                <textarea class="form-control" name="authConfig" rows="2" placeholder='{"token": "your-token"}'>${(config.authConfig)!''}</textarea>
            </div>

            <!-- 限流配置 -->
            <div class="col-md-6">
                <label class="form-label">限流 <small class="text-muted">(每秒请求数，0不限)</small></label>
                <input type="number" class="form-control" name="rateLimit" value="${(config.rateLimit)!0}" min="0">
            </div>
            <div class="col-md-6">
                <label class="form-label">缓存时间 <small class="text-muted">(秒，0不缓存)</small></label>
                <input type="number" class="form-control" name="cacheTtl" value="${(config.cacheTtl)!0}" min="0">
            </div>
        </div>

        <#if isEdit>
        <!-- 调用地址 -->
        <div class="mt-3 p-3 bg-light rounded border">
            <label class="form-label fw-bold"><i class="bi bi-link-45deg"></i> API 调用地址</label>

            <div class="mb-2">
                <span class="badge bg-primary me-1">内部</span>
                <small class="text-muted">通过主应用端口调用</small>
                <div class="input-group input-group-sm mt-1">
                    <span class="input-group-text">POST</span>
                    <input type="text" class="form-control font-monospace" readonly
                           value="http://localhost:8010/publish/execute/${(config.id)!''}">
                    <button type="button" class="btn btn-outline-secondary" onclick="copyText(this.previousElementSibling.value)">
                        <i class="bi bi-clipboard"></i>
                    </button>
                </div>
            </div>

            <div>
                <span class="badge bg-success me-1">自定义</span>
                <small class="text-muted">独立端口 + 自定义路径（需启动服务）</small>
                <div class="input-group input-group-sm mt-1">
                    <span class="input-group-text">POST</span>
                    <input type="text" class="form-control font-monospace" readonly
                           value="http://localhost:${(config.port)!8010}${(config.apiPath)!'/api/data'}">
                    <button type="button" class="btn btn-outline-secondary" onclick="copyText(this.previousElementSibling.value)">
                        <i class="bi bi-clipboard"></i>
                    </button>
                </div>
            </div>
        </div>
        </#if>

        <!-- 底部操作按钮 -->
        <div class="d-flex justify-content-between align-items-center mt-4 pt-3 border-top">
            <#if isEdit>
                <div>
                    <#if (config.status!'STOPPED') == 'RUNNING'>
                        <button type="button" class="btn btn-warning" id="btnStopService">
                            <i class="bi bi-stop-circle"></i> 停止服务
                        </button>
                        <button type="button" class="btn btn-info" id="btnRestartService">
                            <i class="bi bi-arrow-clockwise"></i> 重启服务
                        </button>
                    <#else>
                        <button type="button" class="btn btn-success" id="btnStartService">
                            <i class="bi bi-play-circle"></i> 启动服务
                        </button>
                    </#if>
                </div>
            <#else>
                <div></div>
            </#if>
            <div class="d-flex gap-2">
                <a href="/publish/list" class="btn btn-outline-secondary">取消</a>
                <button type="submit" class="btn btn-primary">
                    <i class="bi bi-check-lg"></i> 保存
                </button>
            </div>
        </div>
    </form>
</div>

<script>
$(function() {
    <#if error??>
    showError('${error?js_string}');
    </#if>
    <#if success??>
    showSuccess('${success?js_string}');
    </#if>

    // 认证方式切换
    $('#authTypeSelect').on('change', function() {
        var type = $(this).val();
        $('#authConfigSection').toggle(type !== 'NONE');
    });
    // 初始化
    if ($('#authTypeSelect').val() !== 'NONE') {
        $('#authConfigSection').show();
    }

    // 自动分配端口
    $('#btnAllocatePort').on('click', function() {
        $.get('/publish/api/allocatePort', function(res) {
            if (res.code === 0 && res.data) {
                $('#portInput').val(res.data.port);
                checkPort(res.data.port);
            }
        });
    });

    // 检测端口
    $('#btnCheckPort').on('click', function() {
        var port = parseInt($('#portInput').val());
        if (!port || port < 10000 || port > 65535) {
            showWarning('请输入有效的端口号 (10000-65535)');
            return;
        }
        checkPort(port);
    });

    // 端口输入框失焦时检测
    $('#portInput').on('blur', function() {
        var port = parseInt($(this).val());
        if (port && port >= 10000 && port <= 65535) {
            checkPort(port);
        }
    });

    function checkPort(port) {
        var excludeId = $('input[name="id"]').val() || '';
        $.get('/publish/api/checkPort', { port: port, excludeId: excludeId }, function(res) {
            if (res.code === 0 && res.data) {
                var $status = $('#portStatus');
                if (res.data.available) {
                    $status.html('<i class="bi bi-check-circle"></i> 端口可用').removeClass('occupied').addClass('available');
                } else {
                    $status.html('<i class="bi bi-x-circle"></i> ' + (res.data.message || '端口已被占用')).removeClass('available').addClass('occupied');
                }
            }
        });
    }

    // 表单提交验证
    $('#publishForm').on('submit', function(e) {
        var name = $('input[name="name"]').val().trim();
        var flowId = $('select[name="flowConfigId"]').val() || $('input[name="flowConfigId"]').val();
        var templateId = $('select[name="visualTemplateId"]').val();
        var port = parseInt($('#portInput').val());

        if (!name) {
            showWarning('请输入发布名称');
            e.preventDefault();
            return false;
        }
        if (!templateId && (!flowId || flowId === '0')) {
            showWarning('请关联可视化模板');
            e.preventDefault();
            return false;
        }
        if (!port || port < 10000 || port > 65535) {
            showWarning('请输入有效的端口号 (10000-65535)');
            e.preventDefault();
            return false;
        }
    });

    // 启动服务
    $('#btnStartService').on('click', function() {
        var id = $('input[name="id"]').val();
        var $btn = $(this).prop('disabled', true);
        $.post('/publish/api/start/' + id, function(res) {
            if (res.code === 0) {
                showSuccess('服务已启动');
                setTimeout(function() { location.reload(); }, 1000);
            } else {
                showError(res.message || '启动失败');
                $btn.prop('disabled', false);
            }
        }).fail(function() {
            showError('请求失败');
            $btn.prop('disabled', false);
        });
    });

    // 停止服务
    $('#btnStopService').on('click', function() {
        var id = $('input[name="id"]').val();
        var $btn = $(this).prop('disabled', true);
        $.post('/publish/api/stop/' + id, function(res) {
            if (res.code === 0) {
                showSuccess('服务已停止');
                setTimeout(function() { location.reload(); }, 1000);
            } else {
                showError(res.message || '停止失败');
                $btn.prop('disabled', false);
            }
        }).fail(function() {
            showError('请求失败');
            $btn.prop('disabled', false);
        });
    });

    // 重启服务
    $('#btnRestartService').on('click', function() {
        var id = $('input[name="id"]').val();
        var $btn = $(this).prop('disabled', true);
        $.post('/publish/api/restart/' + id, function(res) {
            if (res.code === 0) {
                showSuccess('服务已重启');
                setTimeout(function() { location.reload(); }, 1000);
            } else {
                showError(res.message || '重启失败');
                $btn.prop('disabled', false);
            }
        }).fail(function() {
            showError('请求失败');
            $btn.prop('disabled', false);
        });
    });

    // 复制文本
    function copyText(text) {
        navigator.clipboard.writeText(text).then(function() {
            showSuccess('已复制');
        }).catch(function() {
            var $temp = $('<input>');
            $('body').append($temp);
            $temp.val(text).select();
            document.execCommand('copy');
            $temp.remove();
            showSuccess('已复制');
        });
    }
});
</script>

</@main>
