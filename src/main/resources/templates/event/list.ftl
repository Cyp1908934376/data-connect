<#include "../layouts/main.ftl">
<@main title="事件管理" activeMenu="event">

<style>
    .event-card {
        cursor: pointer;
        transition: all 0.2s;
    }
    .event-card:hover {
        transform: translateY(-2px);
        box-shadow: 0 4px 8px rgba(0,0,0,0.1);
    }
    .event-card.builtin {
        border-left: 3px solid #0d6efd;
    }
    .category-badge {
        font-size: 0.75rem;
    }
</style>

<div class="container-fluid">
    <#if error??>
    <div class="alert alert-danger alert-dismissible fade show">
        <i class="bi bi-exclamation-triangle"></i> ${error}
        <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
    </div>
    </#if>
    <#if success??>
    <div class="alert alert-success alert-dismissible fade show">
        <i class="bi bi-check-circle"></i> ${success}
        <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
    </div>
    </#if>

    <div class="d-flex justify-content-between align-items-center mb-3">
        <div>
            <h5 class="mb-0"><i class="bi bi-puzzle"></i> 事件管理</h5>
            <small class="text-muted">可注册、可配置、可复用的数据处理单元</small>
        </div>
        <div class="d-flex gap-2">
            <a href="/event/form" class="btn btn-sm btn-primary">
                <i class="bi bi-plus-lg"></i> 新建事件
            </a>
        </div>
    </div>

    <!-- 分类过滤 -->
    <div class="mb-3">
        <div class="d-flex gap-2 flex-wrap">
            <a href="/event/list" class="badge bg-secondary text-decoration-none<#if !selectedCategory??> bg-primary</#if>">全部</a>
            <#if categories??>
                <#list categories as cat>
                    <a href="/event/list?category=${cat}" class="badge text-decoration-none<#if selectedCategory?? && selectedCategory == cat> bg-primary<#else> bg-secondary</#if>">${cat}</a>
                </#list>
            </#if>
        </div>
    </div>

    <!-- 搜索 -->
    <div class="mb-3">
        <form method="get" action="/event/list" class="row g-2">
            <div class="col-md-4">
                <div class="input-group input-group-sm">
                    <input type="text" name="keyword" class="form-control" placeholder="搜索事件名称/编码..." value="${(keyword)!''}">
                    <button type="submit" class="btn btn-outline-secondary"><i class="bi bi-search"></i></button>
                </div>
            </div>
        </form>
    </div>

    <!-- 事件列表 -->
    <div class="row g-3">
        <#if list?? && list?size gt 0>
            <#list list as evt>
            <div class="col-md-4">
                <div class="card event-card <#if evt.isBuiltin == 1>builtin</#if>">
                    <div class="card-body">
                        <div class="d-flex justify-content-between align-items-start">
                            <div class="d-flex align-items-center gap-2">
                                <i class="bi ${(evt.icon)!'bi-lightning'} fs-5 text-primary"></i>
                                <div>
                                    <h6 class="card-title mb-0">${evt.name}
                                        <#if evt.isBuiltin == 1><span class="badge bg-info category-badge">内置</span></#if>
                                    </h6>
                                    <code class="small text-muted">${evt.code}</code>
                                </div>
                            </div>
                            <span class="badge bg-secondary category-badge">${evt.category}</span>
                        </div>
                        <p class="card-text text-muted small mt-2">${(evt.description)!'暂无描述'}</p>
                        <div class="d-flex justify-content-between align-items-center mt-2">
                            <span class="badge bg-light text-dark">${evt.handlerType}</span>
                            <div>
                                <#if evt.isBuiltin == 0>
                                    <a href="/event/form?id=${evt.id}" class="btn btn-sm btn-outline-primary">
                                        <i class="bi bi-pencil"></i> 编辑
                                    </a>
                                    <button type="button" class="btn btn-sm btn-outline-danger" onclick="deleteEvent(${evt.id})">
                                        <i class="bi bi-trash"></i>
                                    </button>
                                <#else>
                                    <a href="/event/form?id=${evt.id}" class="btn btn-sm btn-outline-secondary">
                                        <i class="bi bi-eye"></i> 查看
                                    </a>
                                </#if>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            </#list>
        <#else>
            <div class="col-12 text-center text-muted py-5">
                <i class="bi bi-puzzle" style="font-size:3rem;display:block;"></i>
                <p class="mt-2">暂无事件定义</p>
                <a href="/event/form" class="btn btn-primary">创建第一个事件</a>
            </div>
        </#if>
    </div>
</div>

<script>
function deleteEvent(id) {
    if (confirm('确定要删除此事件吗？')) {
        var form = document.createElement('form');
        form.method = 'POST';
        form.action = '/event/delete/' + id;
        document.body.appendChild(form);
        form.submit();
    }
}
</script>

</@main>
