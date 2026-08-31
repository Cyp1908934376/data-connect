<#include "../layouts/main.ftl">
<@main title="发布管理" activeMenu="publish">

<style>
    .status-badge { font-size: 0.75rem; }
    .port-badge { font-family: monospace; }
    .ops-btn { display: inline-flex; align-items: center; justify-content: center; gap: 4px; padding: 4px 16px; border: none; border-radius: 4px; font-size: 0.82rem; cursor: pointer; transition: all 0.15s; text-decoration: none; line-height: 1.6; height: 30px; min-width: 70px; box-sizing: border-box; vertical-align: middle; white-space: nowrap; }
    .ops-btn:hover { filter: brightness(0.9); }
    .ops-btn-start { background: #198754; color: #fff; }
    .ops-btn-stop { background: #dc3545; color: #fff; }
    .ops-btn-restart { background: #0dcaf0; color: #000; }
    .ops-btn-edit { background: #6c757d; color: #fff; }
    .ops-btn-delete { background: #fff; color: #dc3545; border: 1px solid #dc3545; }
    .ops-btn-delete:hover { background: #dc3545; color: #fff; }
    .ops-btn:disabled { opacity: 0.5; cursor: not-allowed; }
    .btn-ops { display: inline-flex; gap: 5px; align-items: center; }
    .btn-ops form { display: inline; margin: 0; padding: 0; line-height: 1; }
</style>

<div class="publish-list-page">
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

    <div class="d-flex justify-content-between align-items-center mb-3">
        <div>
            <h5 class="mb-0"><i class="bi bi-cloud-upload"></i> 发布管理</h5>
            <small class="text-muted">将可视化模板部署为独立 API 服务</small>
        </div>
        <a href="/publish/form" class="btn btn-sm btn-primary">
            <i class="bi bi-plus-lg"></i> 新增发布
        </a>
    </div>

    <div class="table-responsive">
        <table class="table table-striped table-hover table-sm align-middle">
            <thead class="table-light">
                <tr>
                    <th style="width:60px;">ID</th>
                    <th>名称</th>
                    <th>关联目标</th>
                    <th>调用地址</th>
                    <th style="width:100px;">端口</th>
                    <th style="width:80px;">状态</th>
                    <th>API路径</th>
                    <th style="width:100px;">认证</th>
                    <th style="width:160px;">创建时间</th>
                    <th style="width:180px;">操作</th>
                </tr>
            </thead>
            <tbody>
                <#if list?? && list?size gt 0>
                    <#list list as pub>
                    <tr data-id="${pub.id}">
                        <td>${pub.id}</td>
                        <td>${(pub.name)!''}</td>
                        <td>
                            <#assign flowId = (pub.flowConfigId)!0 />
                            <#assign tplId = (pub.visualTemplateId)!0 />
                            <#assign flowName = flowMap[flowId?string]!'' />
                            <#assign tplName = templateMap[tplId?string]!'' />
                            <#if flowName?has_content>
                                <span class="badge bg-primary">流程</span> ${flowName}
                            <#elseif tplName?has_content>
                                <span class="badge bg-info">模板</span> ${tplName}
                            <#else>
                                <span class="text-muted">-</span>
                            </#if>
                        </td>
                        <td>
                            <code class="small d-block">/publish/execute/${pub.id}</code>
                            <code class="small text-muted d-block">${pub.apiPath!'/api/data'}</code>
                        </td>
                        <td>
                            <span class="badge bg-dark port-badge">${pub.port}</span>
                        </td>
                        <td>
                            <#if pub.status == 'RUNNING'>
                                <span class="badge bg-success status-badge">运行中</span>
                            <#elseif pub.status == 'ERROR'>
                                <span class="badge bg-danger status-badge">错误</span>
                            <#else>
                                <span class="badge bg-secondary status-badge">已停止</span>
                            </#if>
                        </td>
                        <td><code>${(pub.apiPath)!'/api/data'}</code></td>
                        <td>
                            <#if pub.authType == 'TOKEN'>
                                <span class="badge bg-info">Token</span>
                            <#elseif pub.authType == 'BASIC'>
                                <span class="badge bg-info">Basic</span>
                            <#else>
                                <span class="text-muted">无</span>
                            </#if>
                        </td>
                        <td><small>${(pub.createTime)!''}</small></td>
                        <td>
                            <div class="btn-ops">
                                <#if pub.status == 'RUNNING'>
                                    <button type="button" class="ops-btn ops-btn-restart" data-id="${pub.id}" title="重启">
                                        <i class="bi bi-arrow-clockwise"></i> 重启
                                    </button>
                                    <button type="button" class="ops-btn ops-btn-stop" data-id="${pub.id}">
                                        <i class="bi bi-stop-circle"></i> 停止
                                    </button>
                                <#else>
                                    <button type="button" class="ops-btn ops-btn-start" data-id="${pub.id}">
                                        <i class="bi bi-play-circle"></i> 启动
                                    </button>
                                </#if>
                                <a href="/publish/form?id=${pub.id}" class="ops-btn ops-btn-edit" title="编辑">
                                    <i class="bi bi-pencil-square"></i> 编辑
                                </a>
                                <form method="post" action="/publish/delete/${pub.id}" style="display:inline;margin:0;padding:0;line-height:1;"
                                      onsubmit="return confirm('确定要删除此发布配置吗？');">
                                    <button type="submit" class="ops-btn ops-btn-delete" title="删除">
                                        <i class="bi bi-trash"></i>
                                    </button>
                                </form>
                            </div>
                        </td>
                    </tr>
                    </#list>
                <#else>
                    <tr>
                        <td colspan="9" class="text-center text-muted py-4">
                            <i class="bi bi-cloud" style="font-size:2rem;display:block;"></i>
                            暂无发布配置，点击右上角"新增发布"按钮添加
                        </td>
                    </tr>
                </#if>
            </tbody>
        </table>
    </div>
</div>

<script>
$(function() {
    <#if error??>
    showError('${error?js_string}');
    </#if>
    <#if success??>
    showSuccess('${success?js_string}');
    </#if>

    // 启动服务
    $('.ops-btn-start').on('click', function() {
        var id = $(this).data('id');
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
    $('.ops-btn-stop').on('click', function() {
        var id = $(this).data('id');
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
    $('.ops-btn-restart').on('click', function() {
        var id = $(this).data('id');
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
});
</script>

</@main>
