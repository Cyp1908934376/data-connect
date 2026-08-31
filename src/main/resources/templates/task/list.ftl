<#include "../layouts/main.ftl">
<@main title="任务管理" activeMenu="task">

<div class="container-fluid">
    <div class="action-bar">
        <div class="text-muted small">可视化模板在输入事件里选「定时触发」并保存后，会自动出现在这里。</div>
    </div>

    <#if tasks?? && tasks?size gt 0>
        <div class="table-responsive">
            <table class="table table-hover">
                <thead>
                    <tr>
                        <th>ID</th>
                        <th>任务名称</th>
                        <th>来源</th>
                        <th>Cron表达式</th>
                        <th>状态</th>
                        <th>创建时间</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody>
                    <#list tasks as t>
                    <#assign visual = ((t.taskType)!'FLOW') == 'VISUAL'/>
                    <tr>
                        <td>${t.id}</td>
                        <td><a href="/task/form?id=${t.id}">${t.name}</a></td>
                        <td>
                            <#if visual>
                                <span class="badge bg-info text-dark">可视化模板</span>
                            <#else>
                                <span class="badge bg-secondary">对接流程</span>
                            </#if>
                        </td>
                        <td><code>${(t.cronExpr)!'-'}</code></td>
                        <td>
                            <#if (t.status!'') == 'RUNNING'>
                                <span class="badge bg-success">运行中</span>
                            <#elseif (t.status!'') == 'PAUSED'>
                                <span class="badge bg-warning">已暂停</span>
                            <#else>
                                <span class="badge bg-secondary">已停止</span>
                            </#if>
                        </td>
                        <td>${(t.createTime)!''}</td>
                        <td>
                            <div class="btn-group-ops">
                                <a href="/task/form?id=${t.id}" class="btn-edit"><i class="bi bi-pencil-square"></i> 编辑</a>
                                <#if visual && t.visualTemplateId?? && t.visualTemplateId gt 0>
                                    <a href="/visual/list" class="btn-edit"><i class="bi bi-bezier2"></i> 模板</a>
                                </#if>
                                <#if (t.status!'') != 'RUNNING'>
                                    <button class="btn-start btn-ajax-action" data-url="/task/api/start/${t.id}"><i class="bi bi-play-fill"></i> 启动</button>
                                <#else>
                                    <button class="btn-pause btn-ajax-action" data-url="/task/api/pause/${t.id}"><i class="bi bi-pause-fill"></i> 暂停</button>
                                    <button class="btn-stop btn-ajax-action" data-url="/task/api/stop/${t.id}" data-confirm="确定停止任务？"><i class="bi bi-stop-fill"></i> 停止</button>
                                </#if>
                                <button class="btn-execute-now btn-ajax-action" data-url="/task/api/executeOnce/${t.id}"><i class="bi bi-lightning-fill"></i> 立即执行</button>
                                <form method="post" action="/task/delete/${t.id}" style="display:inline" onsubmit="return confirm('<#if visual>删除后该模板输入将改回手动触发，确定？<#else>确定删除？</#if>')">
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
            <p class="mt-2">暂无任务。可视化模板选「定时触发」并保存后会自动出现。</p>
        </div>
    </#if>
</div>

</@main>
