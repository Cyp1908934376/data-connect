<#include "../layouts/main.ftl">
<@main title="对接流程" activeMenu="flow">

<div class="container-fluid">
    <div class="action-bar">
        <span></span>
        <a href="/flow/wizard" class="btn btn-primary"><i class="bi bi-lightning-charge"></i> 创建对接流程</a>
    </div>

    <#if flows?? && flows?size gt 0>
        <div class="table-responsive">
            <table class="table table-hover">
                <thead>
                    <tr>
                        <th>ID</th>
                        <th>流程名称</th>
                        <th>同步策略</th>
                        <th>描述</th>
                        <th>创建时间</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody>
                    <#list flows as f>
                    <tr>
                        <td>${f.id}</td>
                        <td><a href="/flow/wizard?id=${f.id}">${f.name}</a></td>
                        <td>
                            <#assign strat = (f.syncStrategy)!'FULL'>
                            <#if strat == 'INCREMENTAL_TIME'>
                                <span class="badge bg-info">按时间增量</span>
                            <#elseif strat == 'INCREMENTAL_ID'>
                                <span class="badge bg-info">按ID增量</span>
                            <#else>
                                <span class="badge bg-secondary">全量</span>
                            </#if>
                            <#if (f.incrementalColumn)?? && f.incrementalColumn != ''>
                                <small class="text-muted d-block">${f.incrementalColumn}</small>
                            </#if>
                        </td>
                        <td>${(f.description)!''}</td>
                        <td>${(f.createTime)!''}</td>
                        <td>
                            <div class="btn-group-ops">
                                <a href="/flow/wizard?id=${f.id}" class="btn-edit"><i class="bi bi-pencil-square"></i> 编辑</a>
                                <button class="btn btn-outline-success btn-execute-flow" data-flow-id="${f.id}">执行</button>
                                <button class="btn btn-outline-info btn-log-flow" data-flow-id="${f.id}" data-flow-name="${f.name}"><i class="bi bi-journal-text"></i> 日志</button>
                                <form method="post" action="/flow/delete/${f.id}" style="display:inline" onsubmit="return confirm('确定删除？')">
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
            <p class="mt-2">暂无流程配置</p>
        </div>
    </#if>
</div>

<!-- 同步日志查看弹窗 -->
<div class="modal fade" id="logModal" tabindex="-1">
    <div class="modal-dialog modal-xl modal-dialog-scrollable">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="logModalTitle">同步日志</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
            </div>
            <div class="modal-body">
                <!-- 水位线 -->
                <div class="card mb-3" id="watermarkCard">
                    <div class="card-header d-flex justify-content-between align-items-center">
                        <strong><i class="bi bi-droplet"></i> 当前水位线</strong>
                        <span class="badge bg-secondary" id="watermarkStatus">加载中...</span>
                    </div>
                    <div class="card-body py-2" id="watermarkContent">
                        <span class="text-muted small">加载中...</span>
                    </div>
                </div>
                <!-- 执行记录列表 -->
                <h6><i class="bi bi-list-ul"></i> 执行记录</h6>
                <div class="table-responsive" style="max-height:300px;overflow-y:auto;">
                    <table class="table table-sm table-hover">
                        <thead><tr><th>执行时间</th><th>策略</th><th>状态</th><th>读取</th><th>写入</th><th>耗时</th><th>操作</th></tr></thead>
                        <tbody id="execLogTableBody">
                            <tr><td colspan="7" class="text-center text-muted">加载中...</td></tr>
                        </tbody>
                    </table>
                </div>
                <!-- 日志详情 -->
                <div class="card mt-3" id="logDetailCard" style="display:none;">
                    <div class="card-header d-flex justify-content-between align-items-center">
                        <strong><i class="bi bi-file-text"></i> 日志详情 — <span id="logDetailName"></span></strong>
                        <button type="button" class="btn btn-sm btn-outline-secondary" onclick="$('#logDetailCard').hide()">关闭</button>
                    </div>
                    <div class="card-body p-0">
                        <div class="console-log" id="logDetailContent" style="max-height:300px;overflow-y:auto;margin:0;border:none;"></div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

<script>
$('.btn-execute-flow').on('click', function() {
    var $btn = $(this);
    var flowId = $btn.data('flow-id');
    if (!confirm('确定执行此流程？')) return;

    var loading = showLoading('正在执行数据对接...', '数据量较大时可能需要较长时间，请耐心等待');
    $btn.prop('disabled', true).text('执行中...');

    $.post('/flow/api/execute', { flowConfigId: flowId }, function(res) {
        loading.close();
        if (res.code === 0) {
            var d = res.data;
            showSuccess('总数: ' + (d.totalCount || 0) + ', 成功: ' + (d.successCount || 0) + ', 失败: ' + (d.failCount || 0) + ', 耗时: ' + (d.duration || 0) + 'ms', { title: '执行完成', duration: 6000 });
        } else {
            showError(res.message || '未知错误', { title: '执行失败' });
        }
    }).fail(function(xhr) {
        loading.close();
        var msg = '请求失败';
        try { var err = JSON.parse(xhr.responseText); msg = err.message || msg; } catch(e) {}
        showError(msg, { title: '执行异常' });
    }).always(function() {
        $btn.prop('disabled', false).text('执行');
    });
});

// ---- 同步日志查看 ----
var currentLogFlowId = null;

$('.btn-log-flow').on('click', function() {
    currentLogFlowId = $(this).data('flow-id');
    var flowName = $(this).data('flow-name');
    $('#logModalTitle').text('同步日志 — ' + flowName);
    $('#logDetailCard').hide();
    loadWatermark();
    loadExecLogs();
    new bootstrap.Modal('#logModal').show();
});

function loadWatermark() {
    $.get('/flow/api/watermark/' + currentLogFlowId, function(res) {
        if (res.code === 0 && res.data) {
            var wm = res.data;
            $('#watermarkStatus').removeClass('bg-secondary').addClass('bg-success').text(wm.strategy || '');
            var html = '<div class="row small">';
            html += '<div class="col-md-2"><span class="text-muted">策略:</span> ' + escHtmlS(wm.strategy) + '</div>';
            html += '<div class="col-md-2"><span class="text-muted">增量列:</span> ' + escHtmlS(wm.incrementalColumn || '-') + '</div>';
            html += '<div class="col-md-3"><span class="text-muted">上次水位值:</span> <code>' + escHtmlS(wm.lastValue || '-') + '</code></div>';
            html += '<div class="col-md-3"><span class="text-muted">上次执行:</span> ' + escHtmlS(wm.lastExecTime || '-') + '</div>';
            html += '<div class="col-md-2"><span class="text-muted">状态:</span> ' + escHtmlS(wm.lastExecStatus || '-') + '</div>';
            html += '</div>';
            $('#watermarkContent').html(html);
        } else {
            $('#watermarkStatus').removeClass('bg-secondary').addClass('bg-warning text-dark').text('无水位线');
            $('#watermarkContent').html('<span class="text-muted small">尚未执行过，或为全量同步模式</span>');
        }
    }).fail(function() {
        $('#watermarkStatus').removeClass('bg-secondary').addClass('bg-danger').text('加载失败');
    });
}

function loadExecLogs() {
    $.get('/flow/api/execution-logs/' + currentLogFlowId, function(res) {
        if (res.code === 0 && res.data && res.data.length > 0) {
            var rows = '';
            res.data.forEach(function(fname) {
                // Parse timestamp from filename: execution-yyyyMMdd-HHmmss-SSS.json
                var tsMatch = fname.match(/execution-(.+)\.json$/);
                var ts = tsMatch ? tsMatch[1] : fname;
                var displayTs = ts.length >= 15 ? ts.substring(0,4)+'-'+ts.substring(4,6)+'-'+ts.substring(6,8)+' '+ts.substring(9,11)+':'+ts.substring(11,13)+':'+ts.substring(13,15) : ts;
                rows += '<tr>';
                rows += '<td class="small">' + displayTs + '</td>';
                rows += '<td><span class="badge bg-light text-dark exec-strat">-</span></td>';
                rows += '<td><span class="badge bg-secondary exec-status">-</span></td>';
                rows += '<td class="small exec-read">-</td>';
                rows += '<td class="small exec-write">-</td>';
                rows += '<td class="small exec-dur">-</td>';
                rows += '<td><button class="btn btn-sm btn-outline-info" onclick="viewLogDetail(\'' + fname + '\')"><i class="bi bi-eye"></i></button></td>';
                rows += '</tr>';
            });
            $('#execLogTableBody').html(rows);

            // Load preview summary for each log (lazy)
            res.data.forEach(function(fname, idx) {
                $.get('/flow/api/execution-log/' + currentLogFlowId + '/' + fname, function(logRes) {
                    if (logRes.code === 0 && logRes.data) {
                        var d = logRes.data;
                        var $row = $('#execLogTableBody tr').eq(idx);
                        $row.find('.exec-strat').text(d.strategy || '-');
                        $row.find('.exec-status').removeClass('bg-secondary')
                            .addClass(d.status === 'SUCCESS' ? 'bg-success' : 'bg-danger').text(d.status || '-');
                        $row.find('.exec-read').text(d.readCount || 0);
                        $row.find('.exec-write').text(d.writeCount || 0);
                        $row.find('.exec-dur').text((d.durationMs || 0) + 'ms');
                    }
                });
            });
        } else {
            $('#execLogTableBody').html('<tr><td colspan="7" class="text-center text-muted">暂无执行记录</td></tr>');
        }
    }).fail(function() {
        $('#execLogTableBody').html('<tr><td colspan="7" class="text-center text-danger">加载失败</td></tr>');
    });
}

function viewLogDetail(fname) {
    $.get('/flow/api/execution-log/' + currentLogFlowId + '/' + fname, function(res) {
        if (res.code === 0 && res.data) {
            var d = res.data;
            $('#logDetailName').text(fname);
            var html = '<div class="p-2 small">';
            html += '<strong>状态:</strong> <span class="badge ' + (d.status === 'SUCCESS' ? 'bg-success' : 'bg-danger') + '">' + d.status + '</span> ';
            html += '<strong>策略:</strong> ' + (d.strategy || '-') + ' ';
            html += '<strong>读取:</strong> ' + (d.readCount || 0) + ' ';
            html += '<strong>写入:</strong> ' + (d.writeCount || 0) + ' ';
            html += '<strong>耗时:</strong> ' + (d.durationMs || 0) + 'ms';
            if (d.errorMessage) html += '<br><span class="text-danger"><strong>错误:</strong> ' + escHtmlS(d.errorMessage) + '</span>';
            html += '</div><hr class="my-1">';

            if (d.stepLogs && d.stepLogs.length > 0) {
                d.stepLogs.forEach(function(l) {
                    var cls = l.level === 'ERROR' ? 'log-error' : l.level === 'WARN' ? 'log-warn' : 'log-info';
                    html += '<span class="' + cls + '">[' + l.timestamp + '] [' + l.level + '] ' + escHtmlS(l.message) + '</span><br>';
                });
            }
            $('#logDetailContent').html(html);
            $('#logDetailCard').show();
        }
    }).fail(function() {
        showError('加载日志详情失败');
    });
}

function escHtmlS(s) {
    if (!s) return '';
    return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
</script>

</@main>
