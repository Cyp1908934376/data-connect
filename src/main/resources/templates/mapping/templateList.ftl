<#include "../layouts/main.ftl">
<@main title="对接模板" activeMenu="mapping">

<div class="container-fluid">
    <div class="action-bar">
        <div class="search-group">
            <form class="d-flex" method="get" action="/mapping/templateList">
                <input type="text" name="keyword" class="form-control form-control-sm" placeholder="搜索对接模板..." value="${(RequestParameters.keyword)!''}">
                <button type="submit" class="btn btn-sm btn-outline-secondary ms-2"><i class="bi bi-search"></i></button>
            </form>
        </div>
        <div>
            <a href="/mapping/columnConfig" class="btn btn-sm btn-outline-secondary me-2"><i class="bi bi-gear"></i> 列配置管理</a>
            <a href="/mapping/templateForm" class="btn btn-sm btn-primary"><i class="bi bi-plus-circle"></i> 新增对接模板</a>
        </div>
    </div>

    <#if templates?? && templates?size gt 0>
        <div class="row">
            <#list templates as t>
                <div class="col-md-6 mb-3">
                    <div class="card h-100">
                        <div class="card-body">
                            <h6 class="card-title">
                                <a href="/mapping/templateForm?id=${t.id}">${t.name}</a>
                            </h6>
                            <p class="text-muted small mb-1">${t.description!''}</p>
                            <p class="text-muted small mb-1">
                                关联数据源:
                                <#if t.dsConfigId?? && dsConfigMap??>
                                    <span class="badge bg-info">${dsConfigMap[t.dsConfigId?string]!"未知"}</span>
                                <#else>
                                    <span class="text-muted">未设置</span>
                                </#if>
                            </p>
                            <p class="text-muted small mb-1">
                                列配置:
                                <#if t.columnConfigId??>
                                    <#assign found=false>
                                    <#if columnConfigMap??>
                                        <#list columnConfigMap as cc>
                                            <#if cc.id == t.columnConfigId>
                                                <span class="badge bg-secondary">${cc.name}</span>
                                                <#assign found=true>
                                            </#if>
                                        </#list>
                                    </#if>
                                    <#if !found><span class="text-muted">未知</span></#if>
                                <#else>
                                    <span class="text-muted">未设置</span>
                                </#if>
                            </p>
                            <p class="text-muted small">更新: ${(t.updateTime)!''}</p>
                        </div>
                        <div class="card-footer">
                            <div class="btn-group-ops">
                                <a href="/mapping/templateForm?id=${t.id}" class="btn-edit"><i class="bi bi-pencil-square"></i> 编辑</a>
                                <button type="button" class="btn-delete" onclick="deleteTemplate(${t.id})"><i class="bi bi-trash"></i> 删除</button>
                            </div>
                        </div>
                    </div>
                </div>
            </#list>
        </div>
    <#else>
        <div class="text-center py-5 text-muted">
            <i class="bi bi-inbox" style="font-size:3rem;"></i>
            <p class="mt-2">暂无对接模板</p>
            <p>
                <a href="/mapping/templateForm" class="btn btn-primary"><i class="bi bi-plus-circle"></i> 创建第一个对接模板</a>
                <a href="/mapping/columnConfig" class="btn btn-outline-secondary ms-2"><i class="bi bi-gear"></i> 管理列配置</a>
            </p>
        </div>
    </#if>
</div>

<script>
// 确保通知函数可用（兼容 app.js 缓存未更新的情况）
if (typeof showMessage === 'undefined') {
    function showMessage(msg, type, options) {
        type = type || 'info'; options = options || {};
        var icons = {success:'\u2713',error:'\u2717',warning:'\u26a0',info:'\u2139'};
        var $c = $('#messageArea');
        if (!$c.length) { $c = $('<div id="messageArea" class="notification-container"></div>').appendTo('body'); }
        var $item = $('<div class="notification-item notification-'+type+'"><span class="notification-icon">'+(icons[type]||icons.info)+'</span><div class="notification-content">'+(options.title?'<div class="notification-title">'+options.title+'</div>':'')+'<div class="notification-message">'+msg+'</div></div><button class="notification-close">&times;</button></div>');
        $c.append($item);
        $item.find('.notification-close').on('click',function(){ $item.remove(); });
        var dur = options.duration; if (dur===undefined) dur = type==='error'?5000:3000;
        if (dur>0) { setTimeout(function(){ $item.fadeOut(function(){ $(this).remove(); }); }, dur); }
    }
    function showSuccess(msg, opt) { showMessage(msg, 'success', opt); }
    function showError(msg, opt) { showMessage(msg, 'error', opt); }
    function showWarning(msg, opt) { showMessage(msg, 'warning', opt); }
    function showInfo(msg, opt) { showMessage(msg, 'info', opt); }
}
function deleteTemplate(id) {
    if (!confirm('确定删除此对接模板？')) return;
    $.post('/mapping/api/deleteTemplate/' + id, function(res) {
        if (res.code === 0) {
            showSuccess('删除成功');
            setTimeout(function() { location.reload(); }, 500);
        } else {
            showError(res.message || '', { title: '删除失败' });
        }
    }).fail(function() { showError('请求失败'); });
}
</script>

</@main>
