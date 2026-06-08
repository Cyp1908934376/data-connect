<#include "../layouts/main.ftl">
<@main title="任务配置" activeMenu="task">

<div class="container-fluid">
    <form method="post" action="/task/save">
        <input type="hidden" name="id" value="${(task.id)!''}">

        <div class="row mb-3">
            <div class="col-md-6">
                <label class="form-label">任务名称</label>
                <input type="text" name="name" class="form-control" value="${(task.name)!''}" required>
            </div>
            <div class="col-md-6">
                <label class="form-label">关联流程</label>
                <select name="flowConfigId" class="form-select" required>
                    <option value="">-- 选择对接流程 --</option>
                    <#if flows??>
                        <#list flows as f>
                            <option value="${f.id}" <#if task.flowConfigId?? && task.flowConfigId == f.id>selected</#if>>${f.name}</option>
                        </#list>
                    </#if>
                </select>
            </div>
        </div>

        <div class="row mb-3">
            <div class="col-md-4">
                <label class="form-label">Cron表达式</label>
                <input type="text" name="cronExpr" class="form-control" value="${(task.cronExpr)!''}" placeholder="如: 0 0 2 * * ? (每天凌晨2点)">
                <small class="text-muted">格式: 秒 分 时 日 月 周</small>
            </div>
            <div class="col-md-4">
                <label class="form-label">重试次数</label>
                <input type="number" name="retryTimes" class="form-control" value="${(task.retryTimes)!3}">
            </div>
            <div class="col-md-4">
                <label class="form-label">重试间隔(秒)</label>
                <input type="number" name="retryInterval" class="form-control" value="${(task.retryInterval)!60}">
            </div>
        </div>

        <div class="row mb-3">
            <div class="col-md-6">
                <label class="form-label">超时时间(秒)</label>
                <input type="number" name="timeout" class="form-control" value="${(task.timeout)!3600}">
            </div>
            <div class="col-md-6">
                <label class="form-label">通知Webhook URL</label>
                <input type="text" name="notifyUrl" class="form-control" value="${(task.notifyUrl)!''}" placeholder="选填，任务完成后回调">
            </div>
        </div>

        <div class="row mb-3">
            <div class="col-md-6">
                <label class="form-label">状态</label>
                <select name="status" class="form-select">
                    <option value="STOPPED" <#if (task.status!'') == 'STOPPED'>selected</#if>>已停止</option>
                    <option value="RUNNING" <#if (task.status!'') == 'RUNNING'>selected</#if>>运行中</option>
                    <option value="PAUSED" <#if (task.status!'') == 'PAUSED'>selected</#if>>已暂停</option>
                </select>
            </div>
        </div>

        <div class="mb-3">
            <label class="form-label">Cron快捷设置</label>
            <div class="d-flex gap-2 flex-wrap">
                <button type="button" class="btn btn-sm btn-outline-secondary" onclick="setCron('0 0 * * * ?')">每小时</button>
                <button type="button" class="btn btn-sm btn-outline-secondary" onclick="setCron('0 0 2 * * ?')">每天凌晨2点</button>
                <button type="button" class="btn btn-sm btn-outline-secondary" onclick="setCron('0 0 2 * * 1')">每周一凌晨2点</button>
                <button type="button" class="btn btn-sm btn-outline-secondary" onclick="setCron('0 0 2 1 * ?')">每月1号凌晨2点</button>
                <button type="button" class="btn btn-sm btn-outline-secondary" onclick="setCron('0 */5 * * * ?')">每5分钟</button>
                <button type="button" class="btn btn-sm btn-outline-secondary" onclick="setCron('0 */30 * * * ?')">每30分钟</button>
            </div>
        </div>

        <div class="d-flex gap-2">
            <button type="submit" class="btn btn-primary"><i class="bi bi-check-lg"></i> 保存</button>
            <a href="/task/list" class="btn btn-outline-secondary">取消</a>
        </div>
    </form>

    <#if execLogs?? && execLogs?size gt 0>
    <hr class="my-4">
    <h6>执行历史</h6>
    <div class="table-responsive">
        <table class="table table-sm">
            <thead>
                <tr><th>时间</th><th>状态</th><th>总数</th><th>成功</th><th>失败</th><th>耗时</th></tr>
            </thead>
            <tbody>
                <#list execLogs as log>
                <tr>
                    <td>${(log.startTime)!''}</td>
                    <td>
                        <#if log.status == 'SUCCESS'>
                            <span class="badge bg-success">成功</span>
                        <#elseif log.status == 'FAILED'>
                            <span class="badge bg-danger">失败</span>
                        <#else>
                            <span class="badge bg-secondary">${log.status}</span>
                        </#if>
                    </td>
                    <td>${log.totalCount!0}</td>
                    <td>${log.successCount!0}</td>
                    <td>${log.failCount!0}</td>
                    <td>
                        <#if log.startTime?? && log.endTime??>
                            ${((log.endTime?long - log.startTime?long) / 1000)?floor}s
                        </#if>
                    </td>
                </tr>
                </#list>
            </tbody>
        </table>
    </div>
    </#if>
</div>

<script>
function setCron(expr) {
    $('input[name=cronExpr]').val(expr);
}
</script>

</@main>
