<#include "../layouts/main.ftl">
<@main title="模板管理" activeMenu="template"
extraCss=["/static/codemirror/codemirror.min.css", "/static/codemirror/theme/monokai.css"]
extraJs=["/static/codemirror/codemirror.min.js", "/static/codemirror/mode/javascript.min.js"]>

<div class="container-fluid">
    <div class="action-bar">
        <div class="search-group">
            <form class="d-flex" method="get" action="/template/list">
                <input type="text" name="keyword" class="form-control form-control-sm" placeholder="搜索模板..." value="${(RequestParameters.keyword)!''}">
                <button type="submit" class="btn btn-sm btn-outline-secondary ms-2"><i class="bi bi-search"></i></button>
            </form>
        </div>
        <a href="/template/editor" class="btn btn-sm btn-primary"><i class="bi bi-plus-circle"></i> 新增模板</a>
        <button type="button" class="btn btn-sm btn-outline-secondary" onclick="showSnippetModal()"><i class="bi bi-code-slash"></i> 管理片段</button>
    </div>

    <div class="row">
        <div class="col-md-3">
            <div class="card">
                <div class="card-header">模板分类</div>
                <div class="card-body p-0">
                    <div class="p-2 border-bottom">
                        <a href="/template/list" class="category-all-link <#if !currentCategoryId??>active</#if>">全部模板</a>
                    </div>
                    <#if categoryTree??>
                        <ul class="category-tree" id="categoryTree">
                            <#list categoryTree as root>
                                <#assign hasChildren = root.children?? && root.children?size gt 0>
                                <#assign isActive = currentCategoryId?? && currentCategoryId == root.id>
                                <li class="tree-node <#if isActive>active</#if>" data-id="${root.id}">
                                    <span class="tree-toggle <#if !hasChildren>tree-toggle-empty</#if>"><#if hasChildren><i class="bi bi-chevron-down"></i></#if></span>
                                    <a href="/template/list?categoryId=${root.id}" onclick="event.stopPropagation()">${root.name}</a>
                                    <#if hasChildren>
                                        <ul class="tree-children">
                                            <#list root.children as child>
                                                <li class="tree-node <#if currentCategoryId?? && currentCategoryId == child.id>active</#if>" data-id="${child.id}">
                                                    <span class="tree-toggle tree-toggle-empty"></span>
                                                    <a href="/template/list?categoryId=${child.id}" onclick="event.stopPropagation()">${child.name}</a>
                                                </li>
                                            </#list>
                                        </ul>
                                    </#if>
                                </li>
                            </#list>
                        </ul>
                    </#if>
                </div>
            </div>
        </div>

        <div class="col-md-9">
            <#if templates?? && templates?size gt 0>
                <div class="row">
                    <#list templates as t>
                        <div class="col-md-6 mb-3">
                            <div class="card h-100">
                                <div class="card-body">
                                    <h6 class="card-title">
                                        <a href="/template/editor?id=${t.id}">${t.name}</a>
                                        <span class="badge bg-secondary ms-1">v${t.version!'1'}</span>
                                    </h6>
                                    <p class="text-muted small mb-1">类型: ${t.type!''} | 标签: ${t.tags!''}</p>
                                    <p class="text-muted small">更新: ${(t.updateTime)!''}</p>
                                </div>
                                <div class="card-footer">
                                    <div class="btn-group-ops">
                                        <a href="/template/editor?id=${t.id}" class="btn-edit"><i class="bi bi-pencil-square"></i> 编辑</a>
                                        <form method="post" action="/template/delete/${t.id}" style="display:inline" onsubmit="return confirm('确定删除此模板？')">
                                            <button type="submit" class="btn-delete"><i class="bi bi-trash"></i> 删除</button>
                                        </form>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </#list>
                </div>
            <#else>
                <div class="text-center py-5 text-muted">
                    <i class="bi bi-inbox" style="font-size:3rem;"></i>
                    <p class="mt-2">暂无模板，点击上方按钮创建</p>
                </div>
            </#if>
        </div>
    </div>
</div>

<!-- 代码片段管理弹窗 -->
<div class="modal fade" id="snippetModal" tabindex="-1">
    <div class="modal-dialog modal-lg">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title">模板代码片段管理</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
            </div>
            <div class="modal-body">
                <div class="row mb-3">
                    <div class="col-md-5">
                        <input type="text" class="form-control form-control-sm" id="snippetName" placeholder="片段名称 (显示文本)">
                    </div>
                    <div class="col-md-3">
                        <input type="text" class="form-control form-control-sm" id="snippetGroup" placeholder="分组名">
                    </div>
                    <div class="col-md-2">
                        <input type="text" class="form-control form-control-sm" id="snippetDesc" placeholder="描述(选填)">
                    </div>
                    <div class="col-md-2">
                        <button type="button" class="btn btn-sm btn-primary w-100" onclick="saveSnippet()"><i class="bi bi-plus"></i> 添加</button>
                    </div>
                </div>
                <div class="mb-3">
                    <label class="form-label small">代码内容 <span class="text-danger">*</span></label>
                    <div id="snippetEditorWrapper" style="height:160px;border:1px solid #ddd;"></div>
                </div>
                <input type="hidden" id="editSnippetId">
                <table class="table table-sm table-hover" id="snippetTable">
                    <thead>
                        <tr><th>分组</th><th>名称</th><th>描述</th><th style="width:80px">操作</th></tr>
                    </thead>
                    <tbody id="snippetTableBody"></tbody>
                </table>
            </div>
        </div>
    </div>
</div>

<script>
// Category tree: click row to toggle, click link to navigate
$(function() {
    $('#categoryTree').on('click', '.tree-node', function(e) {
        if ($(e.target).closest('a').length) return;
        var children = this.querySelector(':scope > .tree-children');
        if (!children) return;
        var icon = $(this).children('.tree-toggle').find('i')[0];
        if (children.classList.contains('collapsed')) {
            children.classList.remove('collapsed');
            if (icon) { icon.className = 'bi bi-chevron-down'; }
        } else {
            children.classList.add('collapsed');
            if (icon) { icon.className = 'bi bi-chevron-right'; }
        }
    });
});

// ---- Snippet Management ----
var snippetEditor;

function initSnippetEditor() {
    if (!snippetEditor) {
        snippetEditor = CodeMirror($('#snippetEditorWrapper')[0], {
            mode: 'javascript',
            theme: 'monokai',
            lineNumbers: true,
            matchBrackets: true,
            autoCloseBrackets: true,
            lineWrapping: true,
            tabSize: 2
        });
        snippetEditor.setSize(null, 160);
    }
}

function showSnippetModal() {
    $('#editSnippetId').val('');
    $('#snippetName').val('');
    $('#snippetGroup').val('');
    $('#snippetDesc').val('');
    loadSnippetTable();

    // Init CodeMirror after modal is fully visible to avoid sizing issues
    $('#snippetModal').one('shown.bs.modal', function() {
        initSnippetEditor();
        snippetEditor.setValue('');
        snippetEditor.refresh();
    });

    new bootstrap.Modal('#snippetModal').show();
}

function loadSnippetTable() {
    $.get('/template/api/snippets', function(res) {
        var rows = '';
        if (res.code === 0 && res.data) {
            res.data.forEach(function(group) {
                group.items.forEach(function(item) {
                    rows += '<tr>';
                    rows += '<td><span class="badge bg-secondary">' + $('<span>').text(group.title).html() + '</span></td>';
                    rows += '<td><code>' + $('<span>').text(item.label).html() + '</code></td>';
                    rows += '<td class="text-muted small">' + $('<span>').text(item.desc || '').html() + '</td>';
                    rows += '<td>';
                    rows += '<button class="btn btn-sm btn-outline-secondary me-1 snippet-edit-btn" data-id="' + item.id + '" data-name="' + $('<span>').text(item.label).html() + '" data-group="' + $('<span>').text(group.title).html() + '" data-desc="' + $('<span>').text(item.desc || '').html() + '" data-code="' + $('<span>').text(item.code).html() + '"><i class="bi bi-pencil"></i></button>';
                    rows += '<button class="btn btn-sm btn-outline-danger" onclick="deleteSnippet(' + item.id + ')"><i class="bi bi-trash"></i></button>';
                    rows += '</td></tr>';
                });
            });
        }
        $('#snippetTableBody').html(rows || '<tr><td colspan="4" class="text-center text-muted">暂无片段</td></tr>');
    });
}

// Use delegated handler for edit buttons to avoid inline escaping issues
$('#snippetTableBody').on('click', '.snippet-edit-btn', function() {
    var $btn = $(this);
    if (!snippetEditor) initSnippetEditor();
    $('#editSnippetId').val($btn.data('id'));
    $('#snippetName').val($btn.data('name'));
    $('#snippetGroup').val($btn.data('group'));
    $('#snippetDesc').val($btn.data('desc'));
    snippetEditor.setValue($btn.data('code'));
    snippetEditor.refresh();
});

function saveSnippet() {
    if (!snippetEditor) return;
    var name = $('#snippetName').val().trim();
    var code = snippetEditor.getValue().trim();
    if (!name || !code) { alert('名称和代码不能为空'); return; }
    $.post('/template/api/saveSnippet', {
        id: $('#editSnippetId').val() || undefined,
        name: name,
        groupName: $('#snippetGroup').val().trim(),
        description: $('#snippetDesc').val().trim(),
        code: code
    }, function(res) {
        if (res.code === 0) {
            $('#editSnippetId').val('');
            $('#snippetName').val('');
            $('#snippetGroup').val('');
            $('#snippetDesc').val('');
            snippetEditor.setValue('');
            loadSnippetTable();
        } else {
            alert('保存失败: ' + (res.message || ''));
        }
    }).fail(function() { alert('请求失败'); });
}

function deleteSnippet(id) {
    if (!confirm('确定删除此代码片段？')) return;
    $.post('/template/api/deleteSnippet/' + id, function(res) {
        if (res.code === 0) loadSnippetTable();
        else alert('删除失败');
    }).fail(function() { alert('请求失败'); });
}
</script>
</@main>
