<#include "../layouts/main.ftl">
<@main title="模板编辑器" activeMenu="template"
extraCss=["/static/codemirror/codemirror.min.css", "/static/codemirror/theme/monokai.css"]
extraJs=["/static/codemirror/codemirror.min.js", "/static/codemirror/mode/javascript.min.js", "/static/codemirror/mode/sql.min.js"]>

<div class="container-fluid">
    <form method="post" action="/template/save">
        <input type="hidden" name="id" value="${(template.id)!''}">

        <div class="row mb-3">
            <div class="col-md-6">
                <label class="form-label">模板名称</label>
                <input type="text" name="name" class="form-control" value="${(template.name)!''}" required>
            </div>
            <div class="col-md-3">
                <label class="form-label">所属分类</label>
                <select name="categoryId" class="form-select">
                    <option value="0">-- 请选择 --</option>
                    <#if categories??>
                        <#list categories as cat>
                            <option value="${cat.id}" <#if template.categoryId?? && template.categoryId == cat.id>selected</#if>>
                                <#if cat.parentId != 0>&nbsp;&nbsp;--&nbsp;</#if>${cat.name}
                            </option>
                        </#list>
                    </#if>
                </select>
            </div>
            <div class="col-md-3">
                <label class="form-label">模板类型</label>
                <select name="type" class="form-select">
                    <option value="CUSTOM" <#if (template.type!'') == 'CUSTOM'>selected</#if>>自定义</option>
                    <option value="FIELD_MAPPING" <#if (template.type!'') == 'FIELD_MAPPING'>selected</#if>>字段映射</option>
                    <option value="DATA_FILTER" <#if (template.type!'') == 'DATA_FILTER'>selected</#if>>数据过滤</option>
                    <option value="FORMAT_CONVERT" <#if (template.type!'') == 'FORMAT_CONVERT'>selected</#if>>格式转换</option>
                    <option value="DATA_AGGREGATION" <#if (template.type!'') == 'DATA_AGGREGATION'>selected</#if>>数据聚合</option>
                    <option value="DATA_VALIDATION" <#if (template.type!'') == 'DATA_VALIDATION'>selected</#if>>数据校验</option>
                </select>
            </div>
        </div>

        <div class="row mb-3">
            <div class="col-md-12">
                <label class="form-label">标签</label>
                <input type="text" name="tags" class="form-control" value="${(template.tags)!''}" placeholder="多个标签用逗号分隔">
            </div>
        </div>

        <div class="editor-container mb-3">
            <div class="editor-main">
                <label class="form-label">模板代码 <small class="text-muted">(支持 Groovy/Java 语法，可访问 input(输入行)、params(模板参数)、out(输出Map))</small></label>
                <textarea id="codeEditor" name="content" style="display:none;">${(template.content)!''}</textarea>
                <div id="editorWrapper" class="editor-wrapper"></div>
                <div class="editor-resize-handle" id="editorResizeHandle">
                    <div class="resize-bar"></div>
                </div>
            </div>
            <div class="editor-panel">
                <ul class="nav nav-tabs" role="tablist">
                    <li class="nav-item"><button class="nav-link active" data-bs-toggle="tab" data-bs-target="#tabVars" type="button">变量</button></li>
                    <li class="nav-item"><button class="nav-link" data-bs-toggle="tab" data-bs-target="#tabSnippets" type="button">模板片段</button></li>
                    <#if versions?? && versions?size gt 0>
                    <li class="nav-item"><button class="nav-link" data-bs-toggle="tab" data-bs-target="#tabHistory" type="button">版本历史</button></li>
                    </#if>
                </ul>
                <div class="tab-content">
                    <div class="tab-pane fade show active" id="tabVars">
                        <div class="p-2">
                            <p class="text-muted small">变量替换: 脚本中的 `${r"${变量名}"}` 会被替换为数据行中对应字段的实际值。脚本通过 <code>input</code> 访问当前行数据，通过 <code>out</code> 设置输出。</p>
                            <textarea id="variablesEditor" name="variables" data-cm-mode="javascript">${(template.variables)!''}</textarea>
                        </div>
                    </div>
                    <div class="tab-pane fade" id="tabSnippets">
                        <div class="p-2">
                            <p class="text-muted small mb-2">点击或拖拽到编辑器插入代码片段 (Groovy):</p>
                            <div id="snippetList"></div>
                        </div>
                    </div>
                    <#if versions?? && versions?size gt 0>
                    <div class="tab-pane fade" id="tabHistory">
                        <div class="p-2">
                            <#list versions as v>
                                <div class="mb-2 p-2 border-bottom">
                                    <small><strong>v${v.version}</strong> - ${(v.createTime)!''}</small>
                                    <button type="button" class="btn btn-xs btn-outline-warning float-end"
                                            onclick="if(confirm('回滚到v${v.version}?')) location.href='/template/rollback/${template.id}/${v.id}'">回滚</button>
                                </div>
                            </#list>
                        </div>
                    </div>
                    </#if>
                </div>
            </div>
        </div>

        <div class="d-flex gap-2">
            <button type="submit" class="btn btn-primary"><i class="bi bi-check-lg"></i> 保存模板</button>
            <a href="/template/list" class="btn btn-outline-secondary">取消</a>
            <#if template.id?? && template.id gt 0>
                <button type="button" class="btn-delete-hard ms-auto"
                        onclick="if(confirm('确定彻底删除此模板及所有历史版本？')) location.href='/template/hardDelete/${template.id}'">
                    <i class="bi bi-trash-fill"></i> 彻底删除
                </button>
            </#if>
        </div>
    </form>
</div>

<script>
// Load snippets from server
function loadSnippets() {
    $.get('/template/api/snippets', function(res) {
        if (res.code === 0 && res.data) {
            var html = '';
            res.data.forEach(function(group) {
                html += '<div class="snippet-group">';
                html += '<div class="snippet-group-title">' + group.title + '</div>';
                group.items.forEach(function(item) {
                    var label = item.label;
                    if (item.desc) label += ' <small class="text-muted">— ' + item.desc + '</small>';
                    var code = (item.code || '').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
                    html += '<span class="snippet-item" draggable="true" data-snippet="' + code + '" title="点击插入 / 拖拽到编辑器">' + label + '</span>';
                });
                html += '</div>';
            });
            $('#snippetList').html(html);
        }
    });
}

$(function() {
    loadSnippets();

    // 主代码编辑器
    var $wrapper = $('#editorWrapper');
    var editor = CodeMirror($wrapper[0], {
        value: $('#codeEditor').val(),
        mode: 'javascript',
        theme: 'monokai',
        lineNumbers: true,
        matchBrackets: true,
        autoCloseBrackets: true,
        lineWrapping: true,
        tabSize: 2
    });
    editor.setSize(null, $wrapper.height());
    editor.on('change', function() {
        $('#codeEditor').val(editor.getValue());
    });

    // Insert snippet at cursor
    window.insertSnippet = function(code) {
        editor.replaceSelection(code);
        editor.focus();
    };

    // ---- Click to insert ----
    $('#snippetList').on('click', '.snippet-item', function() {
        var code = $(this).attr('data-snippet');
        if (code) {
            code = code.replace(/&quot;/g, '"').replace(/&#39;/g, "'");
            insertSnippet(code);
        }
        return false;
    });

    // ---- Drag to insert ----
    $('#snippetList').on('dragstart', '.snippet-item', function(e) {
        var code = $(this).attr('data-snippet');
        if (code) {
            code = code.replace(/&quot;/g, '"').replace(/&#39;/g, "'");
            e.originalEvent.dataTransfer.setData('text/plain', code);
            e.originalEvent.dataTransfer.effectAllowed = 'copy';
        }
    });

    // Accept drops on the editor (CodeMirror handles text/plain natively)
    $wrapper.on('dragover', function(e) {
        e.preventDefault();
        e.originalEvent.dataTransfer.dropEffect = 'copy';
    });

    // 拖拽调节编辑器高度
    var $handle = $('#editorResizeHandle');
    var startY, startHeight;
    $handle.on('mousedown', function(e) {
        e.preventDefault();
        startY = e.clientY;
        startHeight = $wrapper.height();
        $handle.addClass('active');
        $(document).on('mousemove.editorResize', function(e) {
            var newHeight = startHeight + (e.clientY - startY);
            var minH = parseInt($wrapper.css('min-height'));
            var maxH = parseInt($wrapper.css('max-height'));
            newHeight = Math.max(minH, Math.min(maxH, newHeight));
            $wrapper.height(newHeight);
            editor.setSize(null, newHeight);
        }).on('mouseup.editorResize', function() {
            $(document).off('.editorResize');
            $handle.removeClass('active');
        });
    });

    // 变量定义编辑器
    var varTextarea = document.getElementById('variablesEditor');
    if (varTextarea) {
        var variablesEditor = CodeMirror.fromTextArea(varTextarea, {
            mode: 'javascript',
            theme: 'monokai',
            lineNumbers: true,
            matchBrackets: true,
            autoCloseBrackets: true,
            lineWrapping: true,
            tabSize: 2
        });
        variablesEditor.setSize(null, 120);
    }
});
</script>

</@main>
