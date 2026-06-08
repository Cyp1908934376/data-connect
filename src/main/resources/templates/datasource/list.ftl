<#include "../layouts/main.ftl">
<@main title="数据源列表" activeMenu="datasource">

<style>
    .action-bar { margin-bottom: 1rem; }
    .action-bar .form-select, .action-bar .form-control { width: auto; display: inline-block; }
    .status-badge { font-size: 0.75rem; }
</style>

<div class="datasource-list-page">
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
    <div class="d-flex justify-content-between align-items-center action-bar flex-wrap gap-2">
        <div class="d-flex gap-2 align-items-center flex-wrap">
            <input type="text" id="searchInput" class="form-control form-control-sm" placeholder="搜索名称或主机地址..." style="width:240px;">
            <select id="typeFilter" class="form-select form-select-sm" style="width:140px;">
                <option value="">全部</option>
                <option value="DB">数据库</option>
                <option value="API">接口</option>
            </select>
            <button type="button" id="searchBtn" class="btn btn-sm btn-outline-secondary">
                <i class="bi bi-search"></i> 搜索
            </button>
            <button type="button" id="resetBtn" class="btn btn-sm btn-outline-secondary">
                <i class="bi bi-arrow-repeat"></i> 重置
            </button>
        </div>
        <a href="/datasource/form" class="btn btn-sm btn-primary">
            <i class="bi bi-plus-lg"></i> 新增数据源
        </a>
    </div>

    <div class="table-responsive">
        <table class="table table-striped table-hover table-sm align-middle">
            <thead class="table-light">
                <tr>
                    <th style="width:60px;">ID</th>
                    <th>名称</th>
                    <th style="width:70px;">类型</th>
                    <th>数据库类型/接口类型</th>
                    <th>主机地址/接口URL</th>
                    <th style="width:80px;">状态</th>
                    <th style="width:160px;">创建时间</th>
                    <th style="width:150px;">操作</th>
                </tr>
            </thead>
            <tbody>
                <#if list?? && list?size gt 0>
                    <#list list as ds>
                    <tr data-type="${(ds.sourceType)!''}">
                        <td>${ds.id}</td>
                        <td>${(ds.name)!''}</td>
                        <td>
                            <#if ds.sourceType == 'API'>
                                <span class="badge bg-info">API</span>
                            <#else>
                                <span class="badge bg-secondary">DB</span>
                            </#if>
                        </td>
                        <td>
                            <#if ds.sourceType == 'API'>
                                ${(ds.apiType)!'-'}
                            <#else>
                                ${(ds.dbType)!'-'}
                            </#if>
                        </td>
                        <td>
                            <#if ds.sourceType == 'API'>
                                ${(ds.apiUrl)!'-'}
                            <#else>
                                ${(ds.host)!'-'}<#if ds.port?? && ds.port != 0>:${(ds.port)!''}</#if>
                            </#if>
                        </td>
                        <td>
                            <#if (ds.enabled!0) == 1>
                                <span class="badge bg-success status-badge">启用</span>
                            <#else>
                                <span class="badge bg-secondary status-badge">禁用</span>
                            </#if>
                        </td>
                        <td><small>${(ds.createTime)!''}</small></td>
                        <td>
                            <div class="btn-group-ops">
                                <a href="/datasource/form?id=${ds.id}" class="btn-edit" title="编辑">
                                    <i class="bi bi-pencil-square"></i> 编辑
                                </a>
                                <form method="post" action="/datasource/delete/${ds.id}" style="display:inline;"
                                      onsubmit="return confirm('确定要删除数据源「${ds.name}」吗？此操作不可恢复。');">
                                    <button type="submit" class="btn-delete" title="删除">
                                        <i class="bi bi-trash"></i> 删除
                                    </button>
                                </form>
                                <a href="/datasource/debug?id=${ds.id}" class="btn-debug" title="调试">
                                    <i class="bi bi-bug"></i> 调试
                                </a>
                            </div>
                        </td>
                    </tr>
                    </#list>
                <#else>
                    <tr>
                        <td colspan="8" class="text-center text-muted py-4">
                            <i class="bi bi-inbox" style="font-size:2rem;display:block;"></i>
                            暂无数据源，点击右上角"新增数据源"按钮添加
                        </td>
                    </tr>
                </#if>
            </tbody>
        </table>
    </div>
</div>

<script>
$(function() {
    // 显示服务端返回的 flash 消息为通知
    <#if error??>
    showError('${error?js_string}');
    </#if>
    <#if success??>
    showSuccess('${success?js_string}');
    </#if>

    // Filter on search button click
    $('#searchBtn').on('click', function() {
        var keyword = $('#searchInput').val().toLowerCase();
        var type = $('#typeFilter').val();
        $('#searchInput').val(keyword);
        filterTable(keyword, type);
    });

    // Filter on Enter key in search input
    $('#searchInput').on('keypress', function(e) {
        if (e.which === 13) {
            $('#searchBtn').trigger('click');
        }
    });

    // Filter on type dropdown change
    $('#typeFilter').on('change', function() {
        var keyword = $('#searchInput').val().toLowerCase();
        var type = $(this).val();
        filterTable(keyword, type);
    });

    // Reset button
    $('#resetBtn').on('click', function() {
        $('#searchInput').val('');
        $('#typeFilter').val('');
        $('tbody tr').show();
    });

    function filterTable(keyword, type) {
        $('tbody tr').each(function() {
            var $row = $(this);
            var text = $row.text().toLowerCase();
            var rowType = $row.data('type') || '';
            var matchKeyword = !keyword || text.indexOf(keyword) >= 0;
            var matchType = !type || rowType === type;
            if (matchKeyword && matchType) {
                $row.show();
            } else {
                $row.hide();
            }
        });
    }
});
</script>

</@main>