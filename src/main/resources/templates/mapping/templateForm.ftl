<#include "../layouts/main.ftl">
<@main title="对接模板配置" activeMenu="mapping">

<div class="container-fluid">
    <div class="mb-3">
        <a href="/mapping/templateList" class="btn btn-sm btn-outline-secondary"><i class="bi bi-arrow-left"></i> 返回列表</a>
    </div>

    <form id="mainForm">
        <input type="hidden" id="templateId" name="id" value="${(template.id)!''}">
        <input type="hidden" id="mappingsInput" name="mappings" value="${(template.mappings)!''}">
        <input type="hidden" id="postmanJsonInput" name="postmanJson">

        <!-- 基本信息 -->
        <div class="card mb-3">
            <div class="card-header">基本信息</div>
            <div class="card-body">
                <div class="row">
                    <div class="col-md-6 mb-3">
                        <label class="form-label">模板名称 <span class="text-danger">*</span></label>
                        <input type="text" class="form-control" id="templateName" name="name" value="${(template.name)!''}" required>
                    </div>
                    <div class="col-md-6 mb-3">
                        <label class="form-label">描述</label>
                        <input type="text" class="form-control" id="templateDesc" name="description" value="${(template.description)!''}">
                    </div>
                </div>
            </div>
        </div>

        <!-- 数据源和列配置选择 -->
        <div class="card mb-3">
            <div class="card-header">数据源 & 列配置</div>
            <div class="card-body">
                <div class="row">
                    <div class="col-md-6 mb-3">
                        <label class="form-label">关联数据源</label>
                        <select class="form-select" id="dsConfigId" name="dsConfigId">
                            <option value="">-- 选择数据源 --</option>
                            <#if dataSources??>
                                <#list dataSources as ds>
                                    <option value="${ds.id}" <#if template.dsConfigId?? && template.dsConfigId == ds.id>selected</#if>>${ds.name} (${(ds.sourceType)!''})</option>
                                </#list>
                            </#if>
                        </select>
                        <small class="text-muted">选择对接的输入/输出数据源</small>
                    </div>
                    <div class="col-md-6 mb-3">
                        <label class="form-label">接收列配置 <span class="text-danger">*</span></label>
                        <select class="form-select" id="columnConfigId" name="columnConfigId" onchange="onColumnConfigChange()">
                            <option value="">-- 选择列配置 --</option>
                            <#if columnConfigs??>
                                <#list columnConfigs as cc>
                                    <#if ((cc.columnType)!'RECEIVE') == 'RECEIVE'>
                                    <option value="${cc.id}" <#if template.columnConfigId?? && template.columnConfigId == cc.id>selected</#if>>${cc.name}</option>
                                    </#if>
                                </#list>
                            </#if>
                        </select>
                        <small class="text-muted">选择列配置来定义接收列（<a href="/mapping/columnConfig" target="_blank">管理列配置</a>）</small>
                    </div>
                    <div class="col-md-6 mb-3">
                        <label class="form-label">推送列配置</label>
                        <select class="form-select" id="pushColumnConfigId" name="pushColumnConfigId" onchange="onPushColumnConfigChange()">
                            <option value="">-- 选择推送列配置 --</option>
                            <#if columnConfigs??>
                                <#list columnConfigs as cc>
                                    <#if ((cc.columnType)!'RECEIVE') == 'PUSH'>
                                    <option value="${cc.id}" <#if (template.pushColumnConfigId!0) == cc.id>selected</#if>>${cc.name}</option>
                                    </#if>
                                </#list>
                            </#if>
                        </select>
                        <small class="text-muted">选择列配置来定义推送列（可选，仅显示推送列类型）</small>
                    </div>
                </div>
            </div>
        </div>

        <!-- Postman JSON 导入 -->
        <div class="card mb-3">
            <div class="card-header">Postman JSON 导入 <small class="text-muted">(可选)</small></div>
            <div class="card-body">
                <div class="mb-2">
                    <label class="form-label">上传 Postman 导出文件</label>
                    <input type="file" class="form-control" id="postmanFile" accept=".json">
                </div>
                <button type="button" class="btn btn-outline-secondary btn-sm" onclick="parsePostman()">
                    <i class="bi bi-upload"></i> 解析并填充
                </button>
                <small class="text-muted ms-2">支持 Postman v2.1 导出格式，将从中提取 query 参数和 body 字段作为键值对</small>
                <div id="postmanPreview" class="mt-2" style="display:none;">
                    <small class="text-muted">已解析 <span id="postmanCount">0</span> 个字段</small>
                </div>
            </div>
        </div>

        <!-- 映射配置表 -->
        <div class="card mb-3">
            <div class="card-header d-flex justify-content-between align-items-center">
                <span>字段映射配置</span>
                <button type="button" class="btn btn-sm btn-outline-secondary" onclick="autoFillMappings()"><i class="bi bi-magic"></i> 自动填充</button>
            </div>
            <div class="card-body">
                <table class="table table-bordered table-sm" id="mappingTable">
                    <thead>
                        <tr>
                            <th style="width:5%">#</th>
                            <th style="width:30%">接收列 (接收键)</th>
                            <th style="width:30%">推送列 (推送键)</th>
                            <th style="width:20%">推送列名 (推送值)</th>
                            <th style="width:10%">操作</th>
                        </tr>
                    </thead>
                    <tbody id="mappingTableBody">
                        <tr id="emptyMappingRow">
                            <td colspan="5" class="text-center text-muted py-3">请先选择"接收列配置"来生成映射行</td>
                        </tr>
                    </tbody>
                </table>
                <button type="button" class="btn btn-sm btn-outline-secondary mt-1" onclick="addMappingRow()"><i class="bi bi-plus"></i> 添加自定义行</button>
            </div>
        </div>

        <!-- 操作按钮 -->
        <div class="d-flex gap-2">
            <button type="button" class="btn btn-primary" onclick="saveTemplate()"><i class="bi bi-save"></i> 保存模板</button>
            <a href="/mapping/templateList" class="btn btn-outline-secondary">取消</a>
        </div>
    </form>
</div>

<script>
// All column configs data for dropdown options
var allColumnConfigData = {};
<#if columnConfigs??>
    <#list columnConfigs as cc>
        allColumnConfigData[${cc.id}] = ${cc.columnsJson!'[]'};
    </#list>
</#if>

// Template data for reference (templateId -> {name, type})
var _templateData = {};
<#if templates??>
    <#list templates as t>
        _templateData[${t.id}] = { name: "${t.name?js_string}", type: "${(t.type)!'CUSTOM'?js_string}" };
    </#list>
</#if>

// Existing mappings
var existingMappings = [];
<#if template.mappings?? && template.mappings != ''>
try {
    existingMappings = JSON.parse('${template.mappings?js_string}');
} catch(e) { existingMappings = []; }
</#if>

function onColumnConfigChange() {
    var opt = $('#columnConfigId option:selected');
    var configId = opt.val();
    if (!configId || !allColumnConfigData[configId]) {
        $('#emptyMappingRow').show();
        $('#mappingTableBody tr:not(#emptyMappingRow)').remove();
        return;
    }
    var cols = allColumnConfigData[configId];
    renderMappingRows(cols);
}

function renderMappingRows(receiveColumns) {
    $('#emptyMappingRow').hide();
    $('#mappingTableBody tr.mapping-row').remove();

    receiveColumns.forEach(function(col, idx) {
        // Find existing mapping for this receiveKey
        var existing = existingMappings.find(function(m) { return m.receiveKey === col.key; });
        var pushKey = existing ? existing.pushKey : col.key;
        var pushValue = existing ? existing.pushValue : col.value;
        addMappingRowInternal(idx + 1, col.key, col.value, col.type || 'string', pushKey, pushValue, col.templateId, col.templateName);
    });

    // Apply existing mappings that don't match any receive column
    existingMappings.forEach(function(m) {
        var found = receiveColumns.find(function(c) { return c.key === m.receiveKey; });
        if (!found) {
            var idx = $('#mappingTableBody tr.mapping-row').length + 1;
            addMappingRowInternal(idx, m.receiveKey, m.receiveValue || '', 'string', m.pushKey, m.pushValue, m.templateId, m.templateName);
        }
    });

    if ($('#mappingTableBody tr.mapping-row').length === 0) {
        $('#emptyMappingRow').show();
    }
    initSearchableSelects();
}

function addMappingRow() {
    var idx = $('#mappingTableBody tr.mapping-row').length + 1;
    addMappingRowInternal(idx, '', '', 'string', '', '', null, null);
    initSearchableSelects();
}

function addMappingRowInternal(idx, receiveKey, receiveValue, type, pushKey, pushValue, templateId, templateName) {
    // Build push column dropdown from push column config first, fall back to receive config
    var pushConfigId = $('#pushColumnConfigId').val();
    var receiveConfigId = $('#columnConfigId').val();
    var pushCols = (pushConfigId && allColumnConfigData[pushConfigId]) ? allColumnConfigData[pushConfigId] : [];
    var receiveCols = allColumnConfigData[receiveConfigId] || [];
    var pushOptions = '<option value="">-- 选择推送列 --</option>';
    if (pushCols.length > 0) {
        pushOptions += '<optgroup label="推送列配置">';
        pushCols.forEach(function(c) {
            var sel = (pushKey === c.key) ? ' selected' : '';
            pushOptions += '<option value="' + (c.key || '') + '" data-value="' + (c.value || '') + '"' + sel + '>' + (c.key || '') + ' (' + (c.value || '') + ')</option>';
        });
        pushOptions += '</optgroup>';
    }
    if (receiveCols.length > 0) {
        pushOptions += '<optgroup label="接收列配置">';
        receiveCols.forEach(function(c) {
            var sel = (pushKey === c.key && pushCols.length === 0) ? ' selected' : '';
            pushOptions += '<option value="' + (c.key || '') + '" data-value="' + (c.value || '') + '"' + sel + '>' + (c.key || '') + ' (' + (c.value || '') + ')</option>';
        });
        pushOptions += '</optgroup>';
    }

    // Build template badge if column has a linked template
    var templateBadge = '';
    if (templateId) {
        var tname = templateName || (_templateData[templateId] ? _templateData[templateId].name : '模板#' + templateId);
        templateBadge = ' <span class="badge bg-info" title="将通过模板处理: ' + tname + '"><i class="bi bi-box-arrow-in-right"></i> ' + tname + '</span>';
    }

    var row = '<tr class="mapping-row" data-template-id="' + (templateId || '') + '" data-template-name="' + (templateName || '') + '">'
        + '<td class="row-num">' + idx + '</td>'
        + '<td><input type="text" class="form-control form-control-sm receive-key" value="' + (receiveKey || '') + '" placeholder="接收键">' + templateBadge + '</td>'
        + '<td><select class="form-select form-select-sm push-key" onchange="onPushKeyChange(this)">' + pushOptions + '<option value="__custom__">-- 自定义 --</option></select></td>'
        + '<td><input type="text" class="form-control form-control-sm push-value" value="' + (pushValue || '') + '" placeholder="推送列名"></td>'
        + '<td><button type="button" class="btn btn-sm btn-outline-danger" onclick="removeMappingRow(this)"><i class="bi bi-trash"></i></button></td>'
        + '</tr>';
    $('#mappingTableBody').append(row);
    $('#emptyMappingRow').hide();
    renumberRows();
}

// Refresh push column dropdowns when push column config changes
function onPushColumnConfigChange() {
    var rows = $('#mappingTableBody tr.mapping-row');
    if (rows.length === 0) return;
    // Rebuild push options
    var pushConfigId = $('#pushColumnConfigId').val();
    var receiveConfigId = $('#columnConfigId').val();
    var pushCols = (pushConfigId && allColumnConfigData[pushConfigId]) ? allColumnConfigData[pushConfigId] : [];
    var receiveCols = allColumnConfigData[receiveConfigId] || [];
    var pushOptions = '<option value="">-- 选择推送列 --</option>';
    if (pushCols.length > 0) {
        pushOptions += '<optgroup label="推送列配置">';
        pushCols.forEach(function(c) {
            pushOptions += '<option value="' + (c.key || '') + '" data-value="' + (c.value || '') + '">' + (c.key || '') + ' (' + (c.value || '') + ')</option>';
        });
        pushOptions += '</optgroup>';
    }
    if (receiveCols.length > 0) {
        pushOptions += '<optgroup label="接收列配置">';
        receiveCols.forEach(function(c) {
            pushOptions += '<option value="' + (c.key || '') + '" data-value="' + (c.value || '') + '">' + (c.key || '') + ' (' + (c.value || '') + ')</option>';
        });
        pushOptions += '</optgroup>';
    }
    pushOptions += '<option value="__custom__">-- 自定义 --</option>';
    rows.each(function() {
        var $row = $(this);
        var currentPushKey = $row.find('.push-key').val();
        // Only update if the push key is a select, not custom input
        if ($row.find('.push-key').is('select')) {
            $row.find('.push-key').html(pushOptions);
            if (currentPushKey) $row.find('.push-key').val(currentPushKey);
        }
    });
}

// Track used push keys globally for searchable dropdown
window._usedPushKeys = [];

function collectUsedPushKeys() {
    var selected = [];
    $('#mappingTableBody tr.mapping-row').each(function() {
        var $pk = $(this).find('.push-key');
        var val = $pk.is('select') ? $pk.val() : $pk.val();
        if (val && val !== '__custom__' && val !== '') selected.push(val);
    });
    window._usedPushKeys = selected;
}

function onPushKeyChange(sel) {
    var val = $(sel).val();
    var $row = $(sel).closest('tr');
    if (val === '__custom__') {
        var wrap = $(sel).closest('.searchable-select-wrap');
        if (wrap.length) {
            wrap.replaceWith('<input type="text" class="form-control form-control-sm push-key" value="" placeholder="自定义推送键">');
        } else {
            $row.find('.push-key').replaceWith('<input type="text" class="form-control form-control-sm push-key" value="" placeholder="自定义推送键">');
        }
        $row.find('.push-value').val('');
        collectUsedPushKeys();
        return;
    }
    // Check for duplicate
    var otherKeys = [];
    $('#mappingTableBody tr.mapping-row').each(function() {
        if (this === $row[0]) return;
        var $pk = $(this).find('.push-key');
        var v = $pk.is('select') ? $pk.val() : $pk.val();
        if (v && v !== '__custom__') otherKeys.push(v);
    });
    if (val && otherKeys.indexOf(val) >= 0) {
        showWarning('推送键 "' + val + '" 已在其他行使用');
        $(sel).val('');
        $row.find('.push-value').val('');
        return;
    }
    var selectedOption = $(sel).find('option:selected');
    $row.find('.push-value').val(selectedOption.data('value') || '');
    collectUsedPushKeys();
}

function removeMappingRow(btn) {
    $(btn).closest('tr').remove();
    renumberRows();
    collectUsedPushKeys();
    if ($('#mappingTableBody tr.mapping-row').length === 0) {
        $('#emptyMappingRow').show();
    }
}

function renumberRows() {
    $('#mappingTableBody tr.mapping-row').each(function(i) {
        $(this).find('.row-num').text(i + 1);
    });
}

function autoFillMappings() {
    $('#mappingTableBody tr.mapping-row').each(function() {
        var receiveKey = $(this).find('.receive-key').val();
        if (receiveKey && !$(this).find('.push-key').val()) {
            $(this).find('.push-key').val(receiveKey);
        }
        if (receiveKey && !$(this).find('.push-value').val()) {
            $(this).find('.push-value').val(receiveKey);
        }
    });
}

function parsePostman() {
    var fileInput = $('#postmanFile')[0];
    if (!fileInput.files || fileInput.files.length === 0) {
        showWarning('请选择 Postman 导出 JSON 文件');
        return;
    }
    var file = fileInput.files[0];
    var reader = new FileReader();
    reader.onload = function(e) {
        var json = e.target.result;
        $('#postmanJsonInput').val(json);
        $.ajax({
            url: '/mapping/api/parsePostman',
            type: 'POST',
            data: { postmanJson: json },
            success: function(res) {
                if (res.code === 0 && res.data && res.data.length > 0) {
                    $('#postmanPreview').show();
                    $('#postmanCount').text(res.data.length);
                    // Auto-fill: add parsed key-values as receive columns
                    // First, clear existing rows (keep if user already has mappings?)
                    $('#mappingTableBody tr.mapping-row').remove();
                    res.data.forEach(function(kv, idx) {
                        addMappingRowInternal(idx + 1, kv.key, kv.value, 'string', kv.key, kv.value, null, null);
                    });
                    initSearchableSelects();
                    showSuccess('成功解析 ' + res.data.length + ' 个字段');
                } else {
                    showWarning('未从文件中解析到字段，请确认文件为 Postman v2.1 导出格式');
                }
            },
            error: function() { showError('解析请求失败'); }
        });
    };
    reader.readAsText(file);
}

function collectMappings() {
    var mappings = [];
    $('#mappingTableBody tr.mapping-row').each(function() {
        var receiveKey = $(this).find('.receive-key').val().trim();
        var pushKeyEl = $(this).find('.push-key');
        var pushKey = pushKeyEl.is('select') ? pushKeyEl.val() : pushKeyEl.val();
        var pushValue = $(this).find('.push-value').val().trim();
        var templateId = $(this).data('template-id');
        var templateName = $(this).data('template-name');
        if (receiveKey || pushKey) {
            var m = {
                receiveKey: receiveKey,
                pushKey: pushKey || '',
                pushValue: pushValue || ''
            };
            if (templateId) {
                m.templateId = templateId;
                m.templateName = templateName || '';
            }
            mappings.push(m);
        }
    });
    return mappings;
}

function saveTemplate() {
    var name = $('#templateName').val().trim();
    if (!name) { showWarning('请输入模板名称'); return; }

    var mappings = collectMappings();
    $('#mappingsInput').val(JSON.stringify(mappings));

    var formData = $('#mainForm').serialize();
    $.post('/mapping/api/saveTemplate', formData, function(res) {
        if (res.code === 0) {
            showSuccess('保存成功');
            // Update template id if new (so subsequent saves update instead of create)
            if (res.data && res.data.id && !$('#templateId').val()) {
                $('#templateId').val(res.data.id);
                // Update URL without reload
                var newUrl = '/mapping/templateForm?id=' + res.data.id;
                history.replaceState(null, '', newUrl);
            }
        } else {
            showError(res.message || '', { title: '保存失败' });
        }
    }).fail(function() { showError('请求失败'); });
}

// Init: load existing mappings on page load
$(function() {
    // Try to populate mapping rows from existing data
    var selectedOpt = $('#columnConfigId option:selected');
    var columnsJson = selectedOpt.data('columns');
    if (columnsJson) {
        try {
            var cols = typeof columnsJson === 'string' ? JSON.parse(columnsJson) : columnsJson;
            renderMappingRows(cols);
        } catch(e) {}
    }
    // If there are existing mappings but no column config selected, render them directly
    if (existingMappings.length > 0 && $('#mappingTableBody tr.mapping-row').length === 0) {
        existingMappings.forEach(function(m, idx) {
            addMappingRowInternal(idx + 1, m.receiveKey, m.receiveValue || '', 'string', m.pushKey, m.pushValue, m.templateId, m.templateName);
        });
        initSearchableSelects();
    }
});
</script>

</@main>
