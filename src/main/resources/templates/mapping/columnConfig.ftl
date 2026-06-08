<#include "../layouts/main.ftl">
<@main title="列配置管理" activeMenu="mapping">

<div class="container-fluid">
    <div class="action-bar">
        <div class="search-group">
            <form class="d-flex" method="get" action="/mapping/columnConfig">
                <input type="text" name="keyword" class="form-control form-control-sm" placeholder="搜索列配置..." value="${(RequestParameters.keyword)!''}">
                <button type="submit" class="btn btn-sm btn-outline-secondary ms-2"><i class="bi bi-search"></i></button>
            </form>
        </div>
        <a href="/mapping/templateList" class="btn btn-sm btn-outline-secondary me-2"><i class="bi bi-arrow-left"></i> 返回对接模板</a>
        <button type="button" class="btn btn-sm btn-primary" onclick="showAddModal()"><i class="bi bi-plus-circle"></i> 新增列配置</button>
    </div>

    <#if configs?? && configs?size gt 0>
        <div class="table-responsive">
            <table class="table table-hover">
                <thead>
                    <tr>
                        <th>名称</th>
                        <th>描述</th>
                        <th>类型</th>
                        <th>列数量</th>
                        <th>更新时间</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody>
                    <#list configs as c>
                        <tr>
                            <td><strong>${c.name}</strong></td>
                            <td>${c.description!''}</td>
                            <td>
                                <#if ((c.columnType)!'RECEIVE') == 'RECEIVE'>
                                    <span class="badge bg-info">接收列</span>
                                <#else>
                                    <span class="badge bg-warning">推送列</span>
                                </#if>
                            </td>
                            <td><span class="badge bg-secondary column-count" data-id="${c.id}">0</span></td>
                            <td class="text-muted small">${(c.updateTime)!''}</td>
                            <td>
                                <button class="btn-edit" onclick="editConfig(${c.id})"><i class="bi bi-pencil-square"></i> 编辑</button>
                                <button class="btn-delete" onclick="deleteConfig(${c.id})"><i class="bi bi-trash"></i> 删除</button>
                            </td>
                        </tr>
                    </#list>
                </tbody>
            </table>
        </div>
    <#else>
        <div class="text-center py-5 text-muted">
            <i class="bi bi-inbox" style="font-size:3rem;"></i>
            <p class="mt-2">暂无列配置，点击上方按钮创建</p>
        </div>
    </#if>
</div>

<!-- 新增/编辑弹窗 -->
<div class="modal fade" id="columnConfigModal" tabindex="-1">
    <div class="modal-dialog modal-lg">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="modalTitle">新增列配置</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
            </div>
            <div class="modal-body">
                <form id="configForm">
                    <input type="hidden" id="configId" name="id">
                    <div class="mb-3">
                        <label class="form-label">名称 <span class="text-danger">*</span></label>
                        <input type="text" class="form-control" id="configName" name="name" required placeholder="如：用户数据映射、订单字段映射">
                    </div>
                    <div class="mb-3">
                        <label class="form-label">描述</label>
                        <input type="text" class="form-control" id="configDesc" name="description" placeholder="选填">
                    </div>
                    <div class="mb-3">
                        <label class="form-label">列类型 <span class="text-danger">*</span></label>
                        <select class="form-select" id="configColumnType" name="columnType" style="max-width:240px;">
                            <option value="RECEIVE">接收列</option>
                            <option value="PUSH">推送列</option>
                        </select>
                        <small class="text-muted">接收列用于对接模板中选择输入字段，推送列用于选择输出字段</small>
                    </div>
                    <div class="mb-3">
                        <label class="form-label">列定义 <span class="text-danger">*</span></label>
                        <small class="text-muted d-block mb-2">定义列的键值对</small>
                        <table class="table table-bordered table-sm" id="columnTable">
                            <thead>
                                <tr>
                                    <th style="width:3%"></th>
                                    <th style="width:22%">键 (key)</th>
                                    <th style="width:22%">值 (value)</th>
                                    <th style="width:12%">类型</th>
                                    <th style="width:20%">关联模板</th>
                                    <th style="width:21%">操作</th>
                                </tr>
                            </thead>
                            <tbody id="columnTableBody">
                                <tr class="column-row">
                                    <td class="drag-handle" draggable="true"><i class="bi bi-grip-vertical"></i></td>
                                    <td><input type="text" class="form-control form-control-sm col-key" placeholder="如: user_name"></td>
                                    <td><input type="text" class="form-control form-control-sm col-value" placeholder="如: 用户名"></td>
                                    <td>
                                        <select class="form-select form-select-sm col-type" onchange="onColumnTypeChange(this)">
                                            <option value="string">字符串</option>
                                            <option value="number">数字</option>
                                            <option value="boolean">布尔</option>
                                            <option value="date">日期</option>
                                            <option value="datetime">日期时间</option>
                                            <option value="text">文本</option>
                                            <option value="json">JSON</option>
                                            <option value="blob">BLOB</option>
                                            <option value="file">文件</option>
                                        </select>
                                    </td>
                                    <td>
                                        <select class="form-select form-select-sm col-template" style="display:none;">
                                            <option value="">-- 选择模板 --</option>
                                        </select>
                                    </td>
                                    <td><button type="button" class="btn btn-sm btn-outline-danger" onclick="removeColumnRow(this)"><i class="bi bi-trash"></i></button></td>
                                </tr>
                            </tbody>
                        </table>
                        <button type="button" class="btn btn-sm btn-outline-secondary mt-1" onclick="addColumnRow()"><i class="bi bi-plus"></i> 添加列</button>
                    </div>
                </form>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">取消</button>
                <button type="button" class="btn btn-primary" onclick="saveConfig()">保存</button>
            </div>
        </div>
    </div>
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
}
// Store config data server-side to avoid HTML attribute injection issues
var _configData = {
<#if configs??>
    <#list configs as c>
    ${c.id}: {
        name: "${c.name?js_string}",
        description: "${c.description?js_string!''}",
        columnType: "${(c.columnType)!'RECEIVE'?js_string}",
        columnsJson: "${c.columnsJson?js_string!''}"
    }<#if c_has_next>,</#if>
    </#list>
</#if>
};

// Store template data for template selector
var _templateData = {
<#if templates??>
    <#list templates as t>
    ${t.id}: { name: "${t.name?js_string}", type: "${(t.type)!'CUSTOM'?js_string}" }<#if t_has_next>,</#if>
    </#list>
</#if>
};

// Special types that require a template
var SPECIAL_TYPES = ['json', 'blob', 'file', 'text'];

function initTemplateSelect(selectEl) {
    // Populate template dropdown options (keep existing options if any)
    var currentVal = $(selectEl).val();
    $(selectEl).empty();
    $(selectEl).append('<option value="">-- 选择模板 --</option>');
    Object.keys(_templateData).forEach(function(id) {
        var t = _templateData[id];
        var sel = (currentVal && currentVal == id) ? ' selected' : '';
        $(selectEl).append('<option value="' + id + '"' + sel + '>' + t.name + ' (' + t.type + ')</option>');
    });
}

function onColumnTypeChange(sel) {
    var row = $(sel).closest('tr');
    var type = $(sel).val();
    var templateSelect = row.find('.col-template');
    if (SPECIAL_TYPES.indexOf(type) >= 0) {
        templateSelect.show();
        initTemplateSelect(templateSelect[0]);
    } else {
        templateSelect.hide();
        templateSelect.val('');
    }
}

// Initialize column counts from stored data
$(function() {
    $('.column-count').each(function() {
        var id = $(this).data('id');
        var data = _configData[id];
        if (data && data.columnsJson) {
            try {
                var arr = JSON.parse(data.columnsJson);
                $(this).text(arr.length);
            } catch(e) {}
        }
    });
});

function showAddModal() {
    $('#modalTitle').text('新增列配置');
    $('#configId').val('');
    $('#configName').val('');
    $('#configDesc').val('');
    $('#configColumnType').val('RECEIVE');
    $('#columnTableBody').empty();
    addColumnRow();
    new bootstrap.Modal('#columnConfigModal').show();
}

function addColumnRow() {
    var row = '<tr class="column-row">'
        + '<td class="drag-handle" draggable="true"><i class="bi bi-grip-vertical"></i></td>'
        + '<td><input type="text" class="form-control form-control-sm col-key" placeholder="字段名"></td>'
        + '<td><input type="text" class="form-control form-control-sm col-value" placeholder="显示名"></td>'
        + '<td><select class="form-select form-select-sm col-type" onchange="onColumnTypeChange(this)">'
        + '<option value="string">字符串</option>'
        + '<option value="number">数字</option>'
        + '<option value="boolean">布尔</option>'
        + '<option value="date">日期</option>'
        + '<option value="datetime">日期时间</option>'
        + '<option value="text">文本</option>'
        + '<option value="json">JSON</option>'
        + '<option value="blob">BLOB</option>'
        + '<option value="file">文件</option>'
        + '</select></td>'
        + '<td><select class="form-select form-select-sm col-template" style="display:none;"><option value="">-- 选择模板 --</option></select></td>'
        + '<td><button type="button" class="btn btn-sm btn-outline-danger" onclick="removeColumnRow(this)"><i class="bi bi-trash"></i></button></td>'
        + '</tr>';
    $(row).appendTo('#columnTableBody');
}

function removeColumnRow(btn) {
    $(btn).closest('tr').remove();
}

// ===== 拖拽排序 =====
var dragSrcRow = null;

// dragstart/dragend 在拖拽手柄上触发，需要找到父 tr
$('#columnTableBody').on('dragstart', '.drag-handle', function(e) {
    dragSrcRow = this.closest('tr.column-row');
    if (dragSrcRow) {
        dragSrcRow.classList.add('dragging');
        e.originalEvent.dataTransfer.effectAllowed = 'move';
        e.originalEvent.dataTransfer.setData('text/plain', '');
    }
});

$('#columnTableBody').on('dragend', '.drag-handle', function() {
    if (dragSrcRow) dragSrcRow.classList.remove('dragging');
    $('#columnTableBody tr').removeClass('drag-over drag-over-top drag-over-bottom');
    dragSrcRow = null;
});

// dragover/drop 在 tr 上触发
$('#columnTableBody').on('dragover', 'tr.column-row', function(e) {
    e.preventDefault();
    e.originalEvent.dataTransfer.dropEffect = 'move';
});

$('#columnTableBody').on('dragenter', 'tr.column-row', function(e) {
    e.preventDefault();
    if (this === dragSrcRow) return;
    this.classList.add('drag-over');
    var rect = this.getBoundingClientRect();
    var mid = rect.top + rect.height / 2;
    this.classList.remove('drag-over-top', 'drag-over-bottom');
    this.classList.add(e.clientY < mid ? 'drag-over-top' : 'drag-over-bottom');
});

$('#columnTableBody').on('dragleave', 'tr.column-row', function() {
    this.classList.remove('drag-over', 'drag-over-top', 'drag-over-bottom');
});

$('#columnTableBody').on('drop', 'tr.column-row', function(e) {
    e.stopPropagation();
    this.classList.remove('drag-over', 'drag-over-top', 'drag-over-bottom');
    if (dragSrcRow === this || !dragSrcRow) return;
    var rect = this.getBoundingClientRect();
    var mid = rect.top + rect.height / 2;
    if (e.clientY < mid) {
        $(dragSrcRow).insertBefore($(this));
    } else {
        $(dragSrcRow).insertAfter($(this));
    }
    dragSrcRow = null;
});

function editConfig(id) {
    var data = _configData[id];
    if (!data) return;
    $('#modalTitle').text('编辑列配置');
    $('#configId').val(id);
    $('#configName').val(data.name);
    $('#configDesc').val(data.description || '');
    $('#configColumnType').val(data.columnType || 'RECEIVE');
    $('#columnTableBody').empty();
    if (data.columnsJson) {
        try {
            var cols = JSON.parse(data.columnsJson);
            cols.forEach(function(c) {
                addColumnRow();
                var lastRow = $('#columnTableBody tr:last');
                lastRow.find('.col-key').val(c.key || '');
                lastRow.find('.col-value').val(c.value || '');
                if (c.type) {
                    lastRow.find('.col-type').val(c.type);
                    // Trigger type change to show template selector if needed
                    onColumnTypeChange(lastRow.find('.col-type')[0]);
                }
                if (c.templateId) {
                    lastRow.find('.col-template').val(c.templateId);
                }
            });
        } catch(e) { console.error('Parse columnsJson error:', e); }
    }
    if ($('#columnTableBody tr').length === 0) addColumnRow();
    new bootstrap.Modal('#columnConfigModal').show();
}

function saveConfig() {
    var name = $('#configName').val().trim();
    if (!name) { showWarning('请输入名称'); return; }
    var columns = [];
    $('#columnTableBody .column-row').each(function() {
        var key = $(this).find('.col-key').val().trim();
        var value = $(this).find('.col-value').val().trim();
        var type = $(this).find('.col-type').val();
        var templateId = $(this).find('.col-template').val();
        var colObj = {key: key, value: value, type: type};
        if (templateId) {
            colObj.templateId = parseInt(templateId);
            var tdata = _templateData[templateId];
            if (tdata) colObj.templateName = tdata.name;
        }
        if (key) {
            columns.push(colObj);
        }
    });
    if (columns.length === 0) { showWarning('请至少添加一个列定义'); return; }
    $.ajax({
        url: '/mapping/api/saveColumnConfig',
        type: 'POST',
        data: $.param({
            id: $('#configId').val() || undefined,
            name: name,
            description: $('#configDesc').val().trim(),
            columnType: $('#configColumnType').val(),
            columnsJson: JSON.stringify(columns)
        }, true),
        success: function(res) {
            if (res.code === 0) {
                showSuccess('保存成功');
                setTimeout(function() { location.reload(); }, 500);
            } else {
                showError(res.message || '', { title: '保存失败' });
            }
        },
        error: function() { showError('请求失败', { title: '错误' }); }
    });
}

function deleteConfig(id) {
    if (!confirm('确定删除此列配置？')) return;
    $.post('/mapping/api/deleteColumnConfig/' + id, function(res) {
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
