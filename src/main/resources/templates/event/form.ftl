<#include "../layouts/main.ftl">
<@main title="${pageTitle!'事件编辑'}" activeMenu="event">

<style>
    .param-row {
        background: #f8f9fa;
        border: 1px solid #dee2e6;
        border-radius: 4px;
        padding: 8px;
        margin-bottom: 6px;
    }
</style>

<#assign isEdit = event?? && event.id?? />

<div class="container-fluid">
    <div class="d-flex justify-content-between align-items-center mb-3">
        <h5><i class="bi bi-puzzle"></i> ${pageTitle!'事件编辑'}</h5>
        <a href="/event/list" class="btn btn-sm btn-outline-secondary">
            <i class="bi bi-arrow-left"></i> 返回列表
        </a>
    </div>

    <#if error??>
    <div class="alert alert-danger"><i class="bi bi-exclamation-triangle"></i> ${error}</div>
    </#if>

    <form method="post" action="/event/save">
        <input type="hidden" name="id" value="${(event.id)!''}">

        <div class="card mb-3">
            <div class="card-header"><strong>基本信息</strong></div>
            <div class="card-body">
                <div class="row g-3">
                    <div class="col-md-4">
                        <label class="form-label">事件名称 <span class="text-danger">*</span></label>
                        <input type="text" class="form-control" name="name" value="${(event.name)!''}" required placeholder="如：Base64编码">
                    </div>
                    <div class="col-md-3">
                        <label class="form-label">事件编码 <span class="text-danger">*</span></label>
                        <input type="text" class="form-control" name="code" value="${(event.code)!''}" required placeholder="如：BASE64_ENCODE" <#if isEdit && event.isBuiltin == 1>readonly</#if>>
                        <small class="text-muted">唯一标识，建议大写+下划线</small>
                    </div>
                    <div class="col-md-2">
                        <label class="form-label">分类</label>
                        <input type="text" class="form-control" name="category" value="${(event.category)!''}" placeholder="如：加密安全" list="categoryList">
                        <datalist id="categoryList">
                            <option value="编码转换">
                            <option value="加密安全">
                            <option value="数据脱敏">
                            <option value="格式转换">
                            <option value="数据处理">
                            <option value="图片处理">
                            <option value="自定义">
                        </datalist>
                    </div>
                    <div class="col-md-3">
                        <label class="form-label">图标 <small class="text-muted">(Bootstrap Icons)</small></label>
                        <input type="text" class="form-control" name="icon" value="${(event.icon)!'bi-lightning'}" placeholder="bi-lightning">
                    </div>
                    <div class="col-md-12">
                        <label class="form-label">描述</label>
                        <textarea class="form-control" name="description" rows="2" placeholder="事件功能描述">${(event.description)!''}</textarea>
                    </div>
                </div>
            </div>
        </div>

        <div class="card mb-3">
            <div class="card-header"><strong>输入参数定义</strong> <small class="text-muted">(JSON格式)</small></div>
            <div class="card-body">
                <div class="mb-2">
                    <small class="text-muted">格式: [{"name": "参数名", "label": "标签", "type": "string", "required": true, "description": "说明"}]</small>
                </div>
                <textarea class="form-control font-monospace" name="inputSchema" rows="6" placeholder='[{"name":"sourceField","label":"源字段","type":"string","required":true,"description":"需要处理的字段名"}]'>${(event.inputSchema)!''}</textarea>
            </div>
        </div>

        <div class="card mb-3">
            <div class="card-header"><strong>输出说明</strong> <small class="text-muted">(JSON格式)</small></div>
            <div class="card-body">
                <textarea class="form-control font-monospace" name="outputSchema" rows="3" placeholder='{"type":"FIELD_MODIFY","description":"对指定字段进行处理"}'>${(event.outputSchema)!''}</textarea>
            </div>
        </div>

        <div class="card mb-3">
            <div class="card-header"><strong>执行器配置</strong></div>
            <div class="card-body">
                <div class="row g-3">
                    <div class="col-md-3">
                        <label class="form-label">执行器类型</label>
                        <select class="form-select" name="handlerType" id="handlerTypeSelect" onchange="toggleHandlerConfig()" <#if isEdit && event.isBuiltin == 1>disabled</#if>>
                            <option value="BUILTIN" <#if ((event.handlerType)!'GROOVY') == 'BUILTIN'>selected</#if>>内置Java类</option>
                            <option value="GROOVY" <#if ((event.handlerType)!'GROOVY') == 'GROOVY'>selected</#if>>Groovy脚本</option>
                            <option value="SHELL" <#if ((event.handlerType)!'') == 'SHELL'>selected</#if>>Shell命令</option>
                            <option value="TEMPLATE" <#if ((event.handlerType)!'') == 'TEMPLATE'>selected</#if>>调用模板</option>
                        </select>
                        <#if isEdit && event.isBuiltin == 1><input type="hidden" name="handlerType" value="${event.handlerType}"></#if>
                    </div>
                    <div class="col-md-9">
                        <label class="form-label">
                            <span id="handlerConfigLabel">处理类名/脚本/命令</span>
                        </label>
                        <textarea class="form-control font-monospace" name="handlerConfig" id="handlerConfigInput" rows="6" placeholder="Groovy脚本代码...">${(event.handlerConfig)!''}</textarea>
                        <small id="handlerConfigHint" class="text-muted">输入Groovy脚本代码</small>
                    </div>
                </div>
            </div>
        </div>

        <div class="d-flex justify-content-between mb-4">
            <div>
                <a href="/event/list" class="btn btn-outline-secondary">取消</a>
            </div>
            <button type="submit" class="btn btn-primary">
                <i class="bi bi-check-lg"></i> 保存事件
            </button>
        </div>
    </form>
</div>

<script>
function toggleHandlerConfig() {
    var type = $('#handlerTypeSelect').val();
    var label = '', hint = '';
    switch (type) {
        case 'BUILTIN':
            label = '处理类标识';
            hint = '内置事件的处理标识（如 base64Encode, md5Hash 等）';
            break;
        case 'GROOVY':
            label = 'Groovy脚本';
            hint = '输入Groovy脚本代码，可用变量: input(DataPacket), params(Map)';
            break;
        case 'SHELL':
            label = 'Shell命令';
            hint = '输入Shell命令或脚本路径';
            break;
        case 'TEMPLATE':
            label = '模板ID';
            hint = '输入要调用的可视化模板ID';
            break;
    }
    $('#handlerConfigLabel').text(label);
    $('#handlerConfigHint').text(hint);
}

$(function() {
    toggleHandlerConfig();
    <#if error??>
    // 显示错误
    </#if>
});
</script>

</@main>
