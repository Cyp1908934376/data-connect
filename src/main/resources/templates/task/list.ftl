<#include "../layouts/main.ftl">
<@main title="任务管理" activeMenu="task">

<div class="container-fluid">
    <div class="action-bar">
        <div></div>
        <a href="/task/form" class="btn btn-sm btn-primary"><i class="bi bi-plus-circle"></i> 新增任务</a>
    </div>

    <#if tasks?? && tasks?size gt 0>
        <div class="table-responsive">
            <table class="table table-hover">
                <thead>
                    <tr>
                        <th>ID</th>
                        <th>任务名称</th>
                        <th>Cron表达式</th>
                        <th>状态</th>
                        <th>创建时间</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody>
                    <#list tasks as t>
                    <tr>
                        <td>${t.id}</td>
                        <td><a href="/task/form?id=${t.id}">${t.name}</a></td>
                        <td><code>${(t.cronExpr)!'-'}</code></td>
                        <td>
                            <#if t.status == 'RUNNING'>
                                <span class="badge bg-success">运行中</span>
                            <#elseif t.status == 'PAUSED'>
                                <span class="badge bg-warning">已暂停</span>
                            <#else>
                                <span class="badge bg-secondary">已停止</span>
                            </#if>
                        </td>
                        <td>${(t.createTime)!''}</td>
                        <td>
                            <div class="btn-group-ops">
                                <a href="/task/form?id=${t.id}" class="btn-edit"><i class="bi bi-pencil-square"></i> 编辑</a>
                                <#if t.status != 'RUNNING'>
                                    <button class="btn-start btn-ajax-action" data-url="/task/api/start/${t.id}"><i class="bi bi-play-fill"></i> 启动</button>
                                <#else>
                                    <button class="btn-pause btn-ajax-action" data-url="/task/api/pause/${t.id}"><i class="bi bi-pause-fill"></i> 暂停</button>
                                    <button class="btn-stop btn-ajax-action" data-url="/task/api/stop/${t.id}" data-confirm="确定停止任务？"><i class="bi bi-stop-fill"></i> 停止</button>
                                </#if>
                                <button class="btn-execute-now btn-ajax-action" data-url="/task/api/executeOnce/${t.id}"><i class="bi bi-lightning-fill"></i> 立即执行</button>
                                <form method="post" action="/task/delete/${t.id}" style="display:inline" onsubmit="return confirm('确定删除？')">
                                    <button type="submit" class="btn-delete"><i class="bi bi-trash"></i> 删除</button>
                                </form>
                            </div>
                        </td>
                    </tr>
                    </#list>
                </tbody>
            </table>
        </div>
    <#else>
        <div class="text-center py-5 text-muted">
            <i class="bi bi-inbox" style="font-size:3rem;"></i>
            <p class="mt-2">暂无任务，请先创建对接流程后新增任务</p>
        </div>
    </#if>
</div>

</@main>
