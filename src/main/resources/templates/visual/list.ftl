<#include "../layouts/main.ftl">
<@main title="可视化模板" activeMenu="visual">

<style>
    .template-card {
        cursor: pointer;
        transition: all 0.2s;
    }
    .template-card:hover {
        transform: translateY(-2px);
        box-shadow: 0 4px 8px rgba(0,0,0,0.1);
    }
    .category-sidebar {
        border-right: 1px solid #dee2e6;
        min-height: 60vh;
    }
    .event-type-badge {
        font-size: 0.7rem;
    }
    .template-card.builtin {
        border-left: 3px solid #6c757d;
    }
    .event-step {
        background: #f8f9fa;
        border: 1px solid #dee2e6;
        border-radius: 6px;
        padding: 12px;
        margin-bottom: 10px;
        position: relative;
    }
    .event-step.fixed {
        background: #e8f4f8;
        border-color: #b8daff;
    }
    .event-step .step-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 10px;
    }
    .event-step .step-number {
        width: 28px;
        height: 28px;
        border-radius: 50%;
        background: #6c757d;
        color: #fff;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 0.85rem;
        font-weight: bold;
    }
    .event-step.fixed .step-number {
        background: #0d6efd;
    }
    .event-step .btn-remove {
        cursor: pointer;
        color: #dc3545;
    }
    .step-connector {
        text-align: center;
        color: #6c757d;
        margin: 5px 0;
    }
    .editor-modal .modal-body {
        max-height: 75vh;
        overflow-y: auto;
    }
    /* 数据源步骤样式 */
    .ds-branch-tabs { display: flex; gap: 0; margin-bottom: 8px; }
    .ds-branch-tab { padding: 4px 14px; cursor: pointer; border: 1px solid #dee2e6; font-size: 0.82rem; transition: all 0.15s; }
    .ds-branch-tab:first-child { border-radius: 4px 0 0 4px; }
    .ds-branch-tab:last-child { border-radius: 0 4px 4px 0; }
    .ds-branch-tab.active { background: #0d6efd; color: #fff; border-color: #0d6efd; }
    .ds-branch-panel { display: none; }
    .ds-branch-panel.active { display: block; }
    .condition-row { display: flex; gap: 4px; align-items: center; margin-bottom: 4px; }
    .condition-row select, .condition-row input { height: 28px; font-size: 0.82rem; padding: 2px 6px; }
    .condition-row .btn-remove-cond { cursor: pointer; color: #dc3545; padding: 2px 4px; }
    .advanced-toggle { cursor: pointer; padding: 6px 10px; background: #f8f9fa; border: 1px solid #e9ecef; border-radius: 4px; font-size: 0.82rem; display: flex; justify-content: space-between; align-items: center; margin-top: 8px; }
    .advanced-toggle:hover { background: #e9ecef; }
    .advanced-body { border: 1px solid #e9ecef; border-top: none; border-radius: 0 0 4px 4px; padding: 8px; display: none; }
    .advanced-body.open { display: block; }
    .result-preview { max-height: 180px; overflow-y: auto; font-size: 0.78rem; }
    .result-preview table { width: 100%; border-collapse: collapse; }
    .result-preview th, .result-preview td { padding: 3px 6px; border: 1px solid #dee2e6; white-space: nowrap; max-width: 150px; overflow: hidden; text-overflow: ellipsis; }
    .result-preview th { background: #f8f9fa; position: sticky; top: 0; font-weight: 600; }
    .sql-preview { font-family: monospace; font-size: 0.82rem; background: #1e1e1e; color: #d4d4d4; padding: 8px; border-radius: 4px; white-space: pre-wrap; word-break: break-all; max-height: 80px; overflow-y: auto; }
    .mapping-help { font-size: 0.78rem; line-height: 1.55; background: #f8fafc; }
    .mapping-help code { font-size: 0.75rem; }
    .exec-log-list { max-height: 180px; overflow-y: auto; font-size: 0.82rem; }
    .exec-log-list .list-group-item { cursor: pointer; padding: 6px 10px; }
    .exec-log-console { background: #1e1e1e; color: #d4d4d4; font-family: Consolas, monospace; font-size: 0.78rem; padding: 10px; border-radius: 4px; max-height: 420px; overflow-y: auto; white-space: pre-wrap; word-break: break-all; min-height: 120px; }
    .exec-log-console .ERROR { color: #ff6b6b; }
    .exec-log-console .WARN { color: #ffc107; }
    .exec-log-console .INFO { color: #8be9a8; }
    .exec-log-console .DEBUG { color: #9cdcfe; }
    .run-status-widget {
        position: fixed; right: 20px; bottom: 20px; z-index: 1080;
        background: #1e1e1e; color: #eee; border-radius: 10px;
        box-shadow: 0 8px 24px rgba(0,0,0,.28); min-width: 280px; max-width: 380px;
        display: none; padding: 12px 14px;
    }
    .run-status-widget.show { display: block; }
    .run-status-widget .run-widget-title { font-size: 0.88rem; font-weight: 600; }
    .run-status-widget .run-widget-meta { font-size: 0.75rem; color: #adb5bd; margin-top: 4px; }
    .run-status-widget .run-widget-actions { margin-top: 8px; display: flex; gap: 6px; }
    #templateRunModal .modal-dialog { max-width: 820px; }
</style>

<div class="visual-template-page">
    <#if error??>
    <div class="alert alert-danger alert-dismissible fade show" role="alert">
        <i class="bi bi-exclamation-triangle"></i> ${error}
        <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
    </div>
    </#if>
    <#if success??>
    <div class="alert alert-success alert-dismissible fade show" role="alert">
        <i class="bi bi-check-circle"></i> ${success}
        <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
    </div>
    </#if>

    <div class="d-flex justify-content-between align-items-center mb-3">
        <div>
            <h5 class="mb-0"><i class="bi bi-bezier2"></i> 可视化模板</h5>
            <small class="text-muted">表单配置式模板管理</small>
        </div>
        <div class="d-flex gap-2">
            <button type="button" class="btn btn-sm btn-primary" onclick="openEditor()">
                <i class="bi bi-plus-lg"></i> 新建模板
            </button>
            <button type="button" class="btn btn-sm btn-outline-secondary" data-bs-toggle="modal" data-bs-target="#categoryModal">
                <i class="bi bi-folder"></i> 分类管理
            </button>
        </div>
    </div>

    <div class="row">
        <!-- 左侧分类树 -->
        <div class="col-md-3 category-sidebar">
            <h6 class="text-muted mb-3">模板分类</h6>
            <div id="categoryTree">
                <div class="list-group list-group-flush">
                    <a href="/visual/list" class="list-group-item list-group-item-action active">
                        <i class="bi bi-collection"></i> 全部模板
                    </a>
                    <#if categories??>
                        <#list categories as cat>
                        <a href="/visual/list?categoryId=${cat.id}" class="list-group-item list-group-item-action">
                            <i class="bi bi-folder"></i> ${cat.name}
                        </a>
                        </#list>
                    </#if>
                </div>
            </div>
        </div>

        <!-- 右侧模板列表 -->
        <div class="col-md-9">
            <div class="mb-3">
                <div class="input-group input-group-sm">
                    <input type="text" id="searchInput" class="form-control" placeholder="搜索模板名称..." value="${(keyword)!''}">
                    <button type="button" id="searchBtn" class="btn btn-outline-secondary">
                        <i class="bi bi-search"></i> 搜索
                    </button>
                </div>
            </div>

            <div class="row g-3">
                <#if list?? && list?size gt 0>
                    <#list list as tpl>
                    <#assign isBuiltinTpl = (tpl.builtinCode)?? && tpl.builtinCode != '' />
                    <div class="col-md-6">
                        <div class="card template-card<#if isBuiltinTpl> builtin</#if>" data-id="${tpl.id}">
                            <div class="card-body">
                                <div class="d-flex justify-content-between align-items-start">
                                    <h6 class="card-title mb-1">${tpl.name}
                                        <#if isBuiltinTpl><span class="badge bg-secondary ms-1" style="font-size:0.65rem;">系统</span></#if>
                                    </h6>
                                    <span class="badge bg-secondary">v${tpl.version}</span>
                                </div>
                                <p class="card-text text-muted small">${(tpl.description)!'暂无描述'}</p>
                                <div class="d-flex justify-content-between align-items-center mt-2">
                                    <small class="text-muted">${(tpl.updateTime)!''}</small>
                                    <div>
                                        <#assign inputType = templateInputTypes[tpl.id?c]!''/>
                                        <#if inputType == 'CRON'>
                                        <button type="button" class="btn btn-sm btn-outline-success" onclick="executeTemplate(${tpl.id}, '${tpl.name?js_string}')" title="立即执行一次（不打断定时）">
                                            <i class="bi bi-play"></i> 立即执行
                                        </button>
                                        <span class="badge bg-info text-dark" title="已按 Cron 定时执行"><i class="bi bi-clock"></i> 定时</span>
                                        <#elseif inputType == '' || inputType == 'MANUAL'>
                                        <button type="button" class="btn btn-sm btn-outline-success" onclick="executeTemplate(${tpl.id}, '${tpl.name?js_string}')">
                                            <i class="bi bi-play"></i> 执行
                                        </button>
                                        <#else>
                                        <button type="button" class="btn btn-sm btn-outline-success" onclick="executeTemplate(${tpl.id}, '${tpl.name?js_string}')" title="立即执行一次">
                                            <i class="bi bi-play"></i> 执行
                                        </button>
                                        </#if>
                                        <button type="button" class="btn btn-sm btn-outline-secondary" onclick="openTemplateLogs(${tpl.id}, '${tpl.name?js_string}')">
                                            <i class="bi bi-journal-text"></i> 日志
                                        </button>
                                        <button type="button" class="btn btn-sm btn-outline-primary" onclick="openEditor(${tpl.id})">
                                            <i class="bi bi-pencil"></i> 编辑
                                        </button>
                                        <#if !isBuiltinTpl>
                                        <button type="button" class="btn btn-sm btn-outline-danger" onclick="deleteTemplate(${tpl.id})">
                                            <i class="bi bi-trash"></i>
                                        </button>
                                        </#if>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    </#list>
                <#else>
                    <div class="col-12 text-center text-muted py-5">
                        <i class="bi bi-bezier2" style="font-size:3rem;display:block;"></i>
                        <p class="mt-2">暂无可视化模板</p>
                        <button type="button" class="btn btn-primary" onclick="openEditor()">创建第一个模板</button>
                    </div>
                </#if>
            </div>
        </div>
    </div>
</div>

<!-- 模板运行日志（可关闭，关闭后任务继续在后台执行） -->
<div class="modal fade" id="templateRunModal" tabindex="-1">
    <div class="modal-dialog modal-lg modal-dialog-scrollable">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title"><i class="bi bi-journal-text"></i> 运行日志 <span class="text-muted" id="templateRunTitle"></span></h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="关闭"></button>
            </div>
            <div class="modal-body">
                <div class="d-flex justify-content-between align-items-center mb-2">
                    <div class="small text-muted" id="templateRunSummary">等待开始...</div>
                    <div class="d-flex gap-2">
                        <button type="button" class="btn btn-sm btn-outline-secondary" onclick="copyTemplateRunLogs()"><i class="bi bi-clipboard"></i> 复制</button>
                    </div>
                </div>
                <div class="exec-log-console" id="templateRunConsole">等待开始...</div>
            </div>
            <div class="modal-footer justify-content-between">
                <span class="small text-muted" id="templateRunHint">关闭后任务继续在后台执行，可从右下角重新打开</span>
                <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">关闭日志</button>
            </div>
        </div>
    </div>
</div>

<div id="runStatusWidget" class="run-status-widget" role="status">
    <div class="d-flex justify-content-between align-items-start">
        <div>
            <div class="run-widget-title" id="runWidgetTitle">后台执行中</div>
            <div class="run-widget-meta" id="runWidgetMeta"></div>
        </div>
        <button type="button" class="btn-close btn-close-white" id="runWidgetDismiss" style="font-size:.65rem;" onclick="dismissRunWidget()" title="关闭提示"></button>
    </div>
    <div class="run-widget-actions">
        <button type="button" class="btn btn-sm btn-primary" onclick="openLiveRunLogs()"><i class="bi bi-journal-text"></i> 查看日志</button>
    </div>
</div>

<!-- 模板运行日志 -->
<div class="modal fade" id="templateLogModal" tabindex="-1">
    <div class="modal-dialog modal-lg">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title">运行日志 <span class="text-muted" id="templateLogTitle"></span></h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
            </div>
            <div class="modal-body">
                <div class="small text-muted mb-2" id="templateLogSummary">选择一条记录查看步骤开始/结束时间</div>
                <div class="list-group exec-log-list mb-2" id="templateLogList"></div>
                <div class="exec-log-console" id="templateLogConsole">暂无日志</div>
            </div>
        </div>
    </div>
</div>

<!-- 分类管理弹窗 -->
<div class="modal fade" id="categoryModal" tabindex="-1">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title">分类管理</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
            </div>
            <div class="modal-body">
                <div id="categoryList">
                    <#if categories??>
                        <#list categories as cat>
                        <div class="d-flex justify-content-between align-items-center mb-2 p-2 border rounded">
                            <span>${cat.name}</span>
                            <button type="button" class="btn btn-sm btn-outline-danger btn-delete-category" data-id="${cat.id}">
                                <i class="bi bi-trash"></i>
                            </button>
                        </div>
                        </#list>
                    <#else>
                        <p class="text-muted">暂无分类</p>
                    </#if>
                </div>
                <hr>
                <div class="input-group">
                    <input type="text" id="newCategoryName" class="form-control" placeholder="新分类名称">
                    <button type="button" id="btnAddCategory" class="btn btn-primary">添加</button>
                </div>
            </div>
        </div>
    </div>
</div>

<!-- 模板编辑器弹窗 -->
<div class="modal fade editor-modal" id="editorModal" tabindex="-1">
    <div class="modal-dialog modal-xl">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="editorModalTitle">新建可视化模板</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
            </div>
            <div class="modal-body">
                <form id="templateForm">
                    <input type="hidden" id="editId" value="">

                    <!-- 模板基本信息 -->
                    <div class="row g-3 mb-3">
                        <div class="col-md-5">
                            <label class="form-label">模板名称 <span class="text-danger">*</span></label>
                            <input type="text" class="form-control" id="editName" required placeholder="请输入模板名称">
                        </div>
                        <div class="col-md-5">
                            <label class="form-label">描述</label>
                            <input type="text" class="form-control" id="editDescription" placeholder="模板描述">
                        </div>
                        <div class="col-md-2">
                            <label class="form-label">&nbsp;</label>
                            <button type="button" class="btn btn-outline-primary w-100" id="btnAddEventStep" onclick="addEventStep()">
                                <i class="bi bi-plus"></i> 添加步骤
                            </button>
                        </div>
                    </div>

                    <!-- 事件步骤列表 -->
                    <div id="eventStepsList">
                        <!-- 步骤1: 输入事件（固定） -->
                        <div class="event-step fixed" data-step-type="INPUT">
                            <div class="step-header">
                                <div class="d-flex align-items-center gap-2">
                                    <span class="step-number">1</span>
                                    <span class="badge bg-primary">输入事件</span>
                                    <span class="text-muted small">- 流程开始</span>
                                </div>
                            </div>
                            <div class="row g-2">
                                <div class="col-md-6">
                                    <label class="form-label small">输入类型</label>
                                    <select class="form-select form-select-sm step-config" data-field="inputType" onchange="onInputTypeChange(this)">
                                        <option value="MANUAL">手动触发</option>
                                        <option value="CRON">定时触发</option>
                                        <option value="DATA_CHANGE">数据变更触发</option>
                                        <option value="API_CALL">API调用触发</option>
                                    </select>
                                </div>
                                <div class="col-md-6 cron-config" style="display:none;">
                                    <label class="form-label small">Cron表达式 <span class="text-muted">(秒 分 时 日 月 周)</span></label>
                                    <input type="text" class="form-control form-control-sm step-config" data-field="cronExpr" placeholder="0 0 * * * ?">
                                    <div class="form-text small">例：每小时 <code>0 0 * * * ?</code>，每5分钟 <code>0 */5 * * * ?</code>，每天0点 <code>0 0 0 * * ?</code></div>
                                </div>
                                <div class="col-md-12 cron-config mt-2" style="display:none;">
                                    <div class="alert alert-light border py-2 px-3 mb-0 small">
                                        保存后会按 Cron 执行，并出现在「任务管理」里（可暂停、立即执行、看执行历史）。定时不会自动清空目标表。要避免重复：数据源选<strong>增量</strong>，写库选<strong>存在则更新</strong>。只有选了「覆盖整表」才会先清空再写。
                                        <div id="watermarkInfo" class="mt-1 text-muted"></div>
                                        <button type="button" class="btn btn-sm btn-outline-secondary mt-1" onclick="resetWatermark()">重置水位线</button>
                                    </div>
                                </div>
                            </div>
                            <!-- 输入参数配置 -->
                            <div class="mt-2" id="inputParamsSection">
                                <div class="d-flex align-items-center justify-content-between mb-1">
                                    <label class="form-label small mb-0">输入参数 <span class="text-muted">(外部调用时传入)</span></label>
                                    <button type="button" class="btn btn-sm btn-outline-primary" onclick="addInputParam()">
                                        <i class="bi bi-plus"></i> 添加参数
                                    </button>
                                </div>
                                <div id="inputParamsList">
                                    <!-- 动态添加的输入参数行 -->
                                </div>
                            </div>
                        </div>

                        <div class="step-connector"><i class="bi bi-arrow-down"></i></div>

                        <!-- 动态步骤区域 -->
                        <div id="dynamicSteps">
                            <!-- 动态添加的步骤会在这里 -->
                        </div>

                        <div class="step-connector"><i class="bi bi-arrow-down"></i></div>

                        <!-- 数据映射事件（固定，可删除） -->
                        <div class="event-step fixed" data-step-type="MAPPING" id="fixedMappingStep">
                            <div class="step-header">
                                <div class="d-flex align-items-center gap-2">
                                    <span class="step-number" id="mappingStepNumber">-</span>
                                    <span class="badge bg-warning">数据映射</span>
                                    <span class="text-muted small">- 字段映射转换</span>
                                </div>
                                <span class="btn-remove" onclick="removeFixedMapping()" title="删除映射步骤"><i class="bi bi-x-circle"></i></span>
                            </div>
                            <div class="row g-2">
                                <div class="col-md-12">
                                    <label class="form-label small">选择映射模板 <span class="text-muted">(从数据对接模块导入)</span></label>
                                    <select class="form-select form-select-sm step-config" data-field="mappingTemplateId" onchange="onMappingTemplateChange(this)">
                                        <option value="">不使用映射模板</option>
                                        <#if mappingTemplates??>
                                            <#list mappingTemplates as mt>
                                                <option value="${mt.id}">${mt.name}</option>
                                            </#list>
                                        </#if>
                                    </select>
                                </div>
                                <div class="col-md-12 mt-2">
                                    <div class="d-flex align-items-center justify-content-between mb-1">
                                        <label class="form-label small mb-0">映射规则 <small class="text-muted">(源可选手动输入；嵌套目标如 档号/全宗号)</small></label>
                                        <button type="button" class="btn btn-sm btn-outline-primary" onclick="addMappingRuleRow($('#fixedMappingStep'), '', '', '')">
                                            <i class="bi bi-plus"></i> 添加映射规则
                                        </button>
                                    </div>
                                    <div class="row g-1 mb-1 small text-muted">
                                        <div class="col-md-4">源字段</div>
                                        <div class="col-md-4">目标字段（嵌套用 /）</div>
                                        <div class="col-md-3">转换（可选事件）</div>
                                    </div>
                                    <div class="alert alert-light border mapping-help py-2 px-3 mb-2">
                                        <div class="fw-semibold mb-1">填写说明</div>
                                        <ul class="mb-0 ps-3">
                                            <li>左边选库字段；选「手动输入」则填固定值，例如 <code>2026</code>。</li>
                                            <li>文件号要 <code>0001</code>：教务已有编号时转换选「四位补零」；没有编号则选「四位序号」，源可留空。</li>
                                            <li>右边是 XML 标签名。扁平标签直接写 <code>正题名</code>、<code>一级目录</code>。</li>
                                            <li>嵌套标签用 <code>/</code> 分隔。例如两行目标分别填 <code>档号/全宗号</code>、<code>档号/案卷号</code>，会生成：<br>
                                                <code>&lt;档号&gt;&lt;全宗号&gt;...&lt;/全宗号&gt;&lt;案卷号&gt;...&lt;/案卷号&gt;&lt;/档号&gt;</code></li>
                                        </ul>
                                    </div>
                                    <div class="mapping-rules-list">
                                        <!-- 动态添加的映射规则行 -->
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div class="step-connector"><i class="bi bi-arrow-down"></i></div>

                        <!-- 最后: 输出事件（固定） -->
                        <div class="event-step fixed" data-step-type="OUTPUT">
                            <div class="step-header">
                                <div class="d-flex align-items-center gap-2">
                                    <span class="step-number" id="outputStepNumber">2</span>
                                    <span class="badge bg-success">输出事件</span>
                                    <span class="text-muted small">- 流程结束</span>
                                </div>
                            </div>
                            <!-- 输出方式选择 -->
                            <div class="col-md-12 mb-2">
                                <label class="form-label small">输出方式</label>
                                <div class="d-flex gap-3">
                                    <label class="form-check form-check-inline">
                                        <input type="radio" name="outputMode" value="RETURN" class="form-check-input" checked> 默认返回
                                    </label>
                                    <label class="form-check form-check-inline">
                                        <input type="radio" name="outputMode" value="CALL_TEMPLATE" class="form-check-input"> 调用模板处理
                                    </label>
                                    <label class="form-check form-check-inline">
                                        <input type="radio" name="outputMode" value="FILE" class="form-check-input"> 写出文件
                                    </label>
                                </div>
                            </div>

                            <!-- RETURN 模式面板 -->
                            <div class="col-md-12 output-panel" data-mode="RETURN">
                                <div class="d-flex align-items-center justify-content-between mb-1">
                                    <label class="form-label small mb-0">输出参数 <span class="text-muted">(可选，用于提取/重命名字段，不配则返回全部)</span></label>
                                    <button type="button" class="btn btn-sm btn-outline-primary" onclick="addOutputParam('','')">
                                        <i class="bi bi-plus"></i> 添加输出参数
                                    </button>
                                </div>
                                <div id="outputParamsList">
                                    <!-- 动态添加的输出参数行 -->
                                </div>
                            </div>

                            <!-- CALL_TEMPLATE 模式面板 -->
                            <div class="col-md-12 output-panel" data-mode="CALL_TEMPLATE" style="display:none;">
                                <div class="row g-2">
                                    <div class="col-md-6">
                                        <label class="form-label small">选择模板</label>
                                        <input type="hidden" data-field="outputCallTemplateId" value="">
                                        <div class="dropdown">
                                            <button class="btn btn-outline-secondary btn-sm dropdown-toggle w-100 text-start" type="button" data-bs-toggle="dropdown" id="outputCallTemplateBtn">
                                                请选择模板
                                            </button>
                                            <ul class="dropdown-menu w-100" style="max-height:300px;overflow-y:auto;" id="outputCallTemplateMenu">
                                                <#if allTemplates??>
                                                    <#list allTemplates as t>
                                                        <li><a class="dropdown-item" href="#" data-id="${t.id}">${t.name}<#if (t.builtinCode)?? && t.builtinCode != ''> · 系统</#if></a></li>
                                                    </#list>
                                                </#if>
                                            </ul>
                                        </div>
                                        <div class="form-text small text-muted">论文归档请选择系统模板「论文归档推送」（会自动先下载附件再推档案）。</div>
                                    </div>
                                    <div class="col-md-3">
                                        <label class="form-label small">传递方式</label>
                                        <select class="form-select form-select-sm" data-field="outputPassMode">
                                            <option value="PACKET">整包传递</option>
                                            <option value="ROW">逐行传递</option>
                                            <option value="BATCH">分批传递</option>
                                        </select>
                                    </div>
                                    <div class="col-md-3 batch-size-config" style="display:none;">
                                        <label class="form-label small">每批行数</label>
                                        <input type="number" class="form-control form-control-sm" data-field="outputBatchSize" value="100">
                                    </div>
                                </div>
                                <div class="row g-2 mt-1">
                                    <div class="col-md-3">
                                        <label class="form-label small">超时(秒)</label>
                                        <input type="number" class="form-control form-control-sm" data-field="outputTimeout" value="60">
                                    </div>
                                    <div class="col-md-4">
                                        <label class="form-label small">失败处理</label>
                                        <select class="form-select form-select-sm" data-field="outputOnError">
                                            <option value="STOP">停止并报错</option>
                                            <option value="IGNORE">忽略继续</option>
                                            <option value="RETRY">重试</option>
                                        </select>
                                    </div>
                                    <div class="col-md-2 retry-config" style="display:none;">
                                        <label class="form-label small">重试次数</label>
                                        <input type="number" class="form-control form-control-sm" data-field="outputRetryCount" value="3">
                                    </div>
                                </div>
                                <input type="hidden" data-field="outputTarget" value="RETURN">
                                <input type="hidden" data-field="outputTable" value="">
                                <input type="hidden" data-field="outputDsId" value="">
                            </div>

                            <!-- FILE 模式面板 -->
                            <div class="col-md-12 output-panel" data-mode="FILE" style="display:none;">
                                <p class="text-muted small mb-2"><i class="bi bi-info-circle"></i> 数据将直接以文件形式下载到浏览器，不写入服务器。</p>
                                <div class="row g-2">
                                    <div class="col-md-4">
                                        <label class="form-label small">文件格式</label>
                                        <select class="form-select form-select-sm" data-field="outputFileFormat">
                                            <option value="JSON">JSON</option>
                                            <option value="CSV">CSV</option>
                                            <option value="XML">XML</option>
                                            <option value="TXT">TXT</option>
                                        </select>
                                    </div>
                                </div>
                                <div class="row g-2 mt-1">
                                    <div class="col-md-4">
                                        <label class="form-label small">写入模式</label>
                                        <select class="form-select form-select-sm" data-field="outputWriteMode">
                                            <option value="OVERWRITE">覆盖</option>
                                            <option value="APPEND">追加</option>
                                            <option value="DAILY">按日期分文件</option>
                                        </select>
                                    </div>
                                    <div class="col-md-4">
                                        <label class="form-label small">编码</label>
                                        <select class="form-select form-select-sm" data-field="fileOptionEncoding">
                                            <option value="UTF-8">UTF-8</option>
                                            <option value="GBK">GBK</option>
                                            <option value="ISO-8859-1">ISO-8859-1</option>
                                        </select>
                                    </div>
                                    <div class="col-md-4 json-options">
                                        <label class="form-label small">JSON选项</label>
                                        <div class="form-check form-check-inline">
                                            <input type="checkbox" class="form-check-input" data-field="fileOptionPretty" checked> 格式化
                                        </div>
                                        <div class="form-check form-check-inline">
                                            <input type="checkbox" class="form-check-input" data-field="fileOptionHeader"> JSON Lines
                                        </div>
                                    </div>
                                    <div class="col-md-4 csv-options" style="display:none;">
                                        <label class="form-label small">CSV选项</label>
                                        <div class="form-check form-check-inline">
                                            <input type="checkbox" class="form-check-input" data-field="fileOptionHeader" checked> 包含表头
                                        </div>
                                        <input type="text" class="form-control form-control-sm mt-1" data-field="fileOptionDelimiter" value="," placeholder="分隔符">
                                    </div>
                                </div>
                                <input type="hidden" data-field="outputTarget" value="RETURN">
                                <input type="hidden" data-field="outputTable" value="">
                                <input type="hidden" data-field="outputDsId" value="">
                            </div>
                        </div>
                    </div>
                </form>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">取消</button>
                <button type="button" class="btn btn-primary" onclick="saveTemplate()">
                    <i class="bi bi-check-lg"></i> 保存
                </button>
            </div>
        </div>
    </div>
</div>

<!-- 添加步骤弹窗 -->
<div class="modal fade" id="addStepModal" tabindex="-1">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title">添加步骤</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
            </div>
            <div class="modal-body">
                <div class="list-group">
                    <button type="button" class="list-group-item list-group-item-action" onclick="confirmAddStep('DATA_SOURCE')">
                        <i class="bi bi-database text-primary"></i> <strong>数据源事件</strong>
                        <br><small class="text-muted">查数据库，或先取 Token 再调接口拉数</small>
                    </button>
                    <button type="button" class="list-group-item list-group-item-action" onclick="confirmAddStep('MAPPING')">
                        <i class="bi bi-link-45deg text-warning"></i> <strong>数据映射</strong>
                        <br><small class="text-muted">字段映射和转换</small>
                    </button>
                    <button type="button" class="list-group-item list-group-item-action" onclick="confirmAddStep('FILTER')">
                        <i class="bi bi-funnel text-info"></i> <strong>数据过滤</strong>
                        <br><small class="text-muted">按条件过滤数据</small>
                    </button>
                    <button type="button" class="list-group-item list-group-item-action" onclick="confirmAddStep('OPERATION')">
                        <i class="bi bi-lightning text-success"></i> <strong>操作事件</strong>
                        <br><small class="text-muted">数据库操作 / 接口调用</small>
                    </button>
                    <button type="button" class="list-group-item list-group-item-action" onclick="confirmAddStep('CALL_TEMPLATE')">
                        <i class="bi bi-box-arrow-right text-danger"></i> <strong>调用模板</strong>
                        <br><small class="text-muted">调用其他可视化模板</small>
                    </button>
                    <button type="button" class="list-group-item list-group-item-action" onclick="confirmAddStep('EVENT')">
                        <i class="bi bi-lightning text-purple"></i> <strong>事件处理</strong>
                        <br><small class="text-muted">编码/加密/脱敏/格式转换等</small>
                    </button>
                </div>
            </div>
        </div>
    </div>
</div>

<script>
var dynamicStepCounter = 0;
var editStepCounter = 0;

// 数据源和模板列表（从后端传递）
var dataSourceList = [<#if dataSources??><#list dataSources as ds>{id: ${ds.id}, name: "${ds.name?js_string}", sourceType: "${ds.sourceType!''}", dbType: "${ds.dbType!''}", tableNames: "${(ds.tableNames!'')?js_string}", tableName: "${(ds.tableName!'')?js_string}", apiUrl: "${(ds.apiUrl!'')?js_string}", apiMethod: "${(ds.apiMethod!'GET')?js_string}", apiHeaders: "${(ds.apiHeaders!'')?js_string}", apiBody: "${(ds.apiBody!'')?js_string}", apiTimeout: "${(ds.apiTimeout!'180')?js_string}", apiRetryTimes: "${(ds.apiRetryTimes!'3')?js_string}", apiRetryInterval: "${(ds.apiRetryInterval!'1000')?js_string}"}<#if ds_has_next>,</#if></#list></#if>];

var templateList = [<#if allTemplates??><#list allTemplates as t>{id: ${t.id}, name: "${t.name?js_string}", builtinCode: "${(t.builtinCode!'')?js_string}"}<#if t_has_next>,</#if></#list></#if>];
var editingBuiltin = false;

var mappingTemplateList = [<#if mappingTemplates??><#list mappingTemplates as mt>{id: ${mt.id}, name: "${mt.name?js_string}", mappings: '${(mt.mappings!"[]")?js_string}'}<#if mt_has_next>,</#if></#list></#if>];
var eventListForMapping = [];

$(function() {
    $.get('/event/api/list', function(res) {
        if (res.code === 0 && res.data) {
            eventListForMapping = res.data;
            $('.mapping-transform, .fv-transform').each(function() {
                var cur = $(this).val();
                $(this).html(buildMappingTransformOptions(cur));
            });
        }
    });
    <#if error??>
    showError('${error!?js_string}');
    </#if>
    <#if success??>
    showSuccess('${success!?js_string}');
    </#if>

    // 搜索
    $('#searchBtn').on('click', function() {
        var keyword = $('#searchInput').val();
        window.location.href = '/visual/list?keyword=' + encodeURIComponent(keyword);
    });

    $('#searchInput').on('keypress', function(e) {
        if (e.which === 13) $('#searchBtn').click();
    });

    // 添加分类
    $('#btnAddCategory').on('click', function() {
        var name = $('#newCategoryName').val().trim();
        if (!name) { showWarning('请输入分类名称'); return; }
        $.post('/visual/api/saveCategory', { name: name }, function(res) {
            if (res.success) { showSuccess('添加成功'); setTimeout(function() { location.reload(); }, 500); }
            else { showError(res.message || '添加失败'); }
        });
    });

    // 删除分类
    $('.btn-delete-category').on('click', function() {
        var id = $(this).data('id');
        if (confirm('确定要删除此分类吗？')) {
            $.post('/visual/api/deleteCategory/' + id, function(res) {
                if (res.success) { showSuccess('删除成功'); setTimeout(function() { location.reload(); }, 500); }
                else { showError(res.message || '删除失败'); }
            });
        }
    });

    // 输出模式切换
    // Bootstrap dropdown 模板选择
    $(document).on('click', '#outputCallTemplateMenu .dropdown-item', function(e) {
        e.preventDefault();
        var id = $(this).data('id');
        var name = $(this).text();
        $('[data-field="outputCallTemplateId"]').val(id);
        $('#outputCallTemplateBtn').html(name + ' <span class="caret"></span>').removeClass('text-muted');
    });

    $('input[name="outputMode"]').on('change', function() {
        var mode = $(this).val();
        $('.output-panel').hide();
        $('.output-panel[data-mode="' + mode + '"]').show();
    });

    // 传递方式切换
    $('[data-field="outputPassMode"]').on('change', function() {
        $('.batch-size-config').toggle($(this).val() === 'BATCH');
    });

    // 失败处理切换
    $('[data-field="outputOnError"]').on('change', function() {
        $('.retry-config').toggle($(this).val() === 'RETRY');
    });

    $(document).on('change', '[data-field="inputType"]', function() {
        onInputTypeChange(this);
    });

    // 文件格式切换
    $('[data-field="outputFileFormat"]').on('change', function() {
        var fmt = $(this).val();
        $('.json-options').toggle(fmt === 'JSON');
        $('.csv-options').toggle(fmt === 'CSV');
    });

    // 初始化输出模式
    $('input[name="outputMode"]:checked').trigger('change');
    $('[data-field="outputFileFormat"]').trigger('change');
});

var inputParamCounter = 0;
var outputParamCounter = 0;

// 添加输入参数
function addInputParam(name, type, required, defaultValue) {
    inputParamCounter++;
    var html = '<div class="row g-1 mb-1 input-param-row" data-idx="' + inputParamCounter + '">';
    html += '<div class="col-md-3"><input type="text" class="form-control form-control-sm" placeholder="参数名" value="' + (name || '') + '"></div>';
    html += '<div class="col-md-2"><select class="form-select form-select-sm">';
    html += '<option value="string"' + (type === 'string' ? ' selected' : '') + '>string</option>';
    html += '<option value="number"' + (type === 'number' ? ' selected' : '') + '>number</option>';
    html += '<option value="boolean"' + (type === 'boolean' ? ' selected' : '') + '>boolean</option>';
    html += '<option value="json"' + (type === 'json' ? ' selected' : '') + '>json</option>';
    html += '</select></div>';
    html += '<div class="col-md-2"><select class="form-select form-select-sm">';
    html += '<option value="true"' + (required !== false ? ' selected' : '') + '>必填</option>';
    html += '<option value="false"' + (required === false ? ' selected' : '') + '>可选</option>';
    html += '</select></div>';
    html += '<div class="col-md-4"><input type="text" class="form-control form-control-sm" placeholder="默认值(可选)" value="' + (defaultValue || '') + '"></div>';
    html += '<div class="col-md-1"><button type="button" class="btn btn-sm btn-outline-danger" onclick="$(this).closest(\'.input-param-row\').remove()"><i class="bi bi-x"></i></button></div>';
    html += '</div>';
    $('#inputParamsList').append(html);
}

// 添加输出参数
function addOutputParam(name, sourceField) {
    outputParamCounter++;
    var html = '<div class="row g-1 mb-1 output-param-row" data-idx="' + outputParamCounter + '">';
    html += '<div class="col-md-4"><input type="text" class="form-control form-control-sm" placeholder="返回字段名" value="' + (name || '') + '"></div>';
    // sourceField：如果有缓存的字段列表，用下拉选择；否则用输入框
    var fields = window._outputSourceFields || [];
    if (fields.length > 0) {
        html += '<div class="col-md-7"><select class="form-select form-select-sm" data-field="sourceField">';
        html += '<option value="">选择数据源字段</option>';
        fields.forEach(function(f) {
            var selected = sourceField === f ? ' selected' : '';
            html += '<option value="' + f + '"' + selected + '>' + f + '</option>';
        });
        html += '</select></div>';
    } else {
        html += '<div class="col-md-7"><input type="text" class="form-control form-control-sm" data-field="sourceField" placeholder="数据源字段名" value="' + (sourceField || '') + '"></div>';
    }
    html += '<div class="col-md-1"><button type="button" class="btn btn-sm btn-outline-danger" onclick="$(this).closest(\'.output-param-row\').remove()"><i class="bi bi-x"></i></button></div>';
    html += '</div>';
    $('#outputParamsList').append(html);
}

// 获取输入参数配置
function getInputParams() {
    var params = [];
    $('#inputParamsList .input-param-row').each(function() {
        var $cols = $(this).find('.col-md-3, .col-md-2, .col-md-4');
        var name = $cols.eq(0).find('input').val();
        var type = $cols.eq(1).find('select').val();
        var required = $cols.eq(2).find('select').val() === 'true';
        var defaultValue = $cols.eq(3).find('input').val();
        if (name) {
            params.push({ name: name, type: type, required: required, defaultValue: defaultValue });
        }
    });
    return params;
}

function ensureArchiveInputParams() {
    var existing = {};
    getInputParams().forEach(function(p) { existing[p.name] = true; });
    var added = 0;
    [
        {name: 'apiUrl', required: true, def: ''},
        {name: 'appkey', required: true, def: ''},
        {name: 'password', required: true, def: ''},
        {name: 'ccode', required: false, def: 'lwdj'}
    ].forEach(function(p) {
        if (!existing[p.name]) {
            addInputParam(p.name, 'string', p.required, p.def);
            added++;
        }
    });
    if (added > 0) {
        showSuccess('已添加 ' + added + ' 个归档入参（apiUrl / appkey / password / ccode）');
    } else {
        showSuccess('归档入参已存在');
    }
}

// 从数据源 columns 自动填充映射规则行（只填充空的映射步骤）
function fillMappingsFromDsColumns() {
    var columns = window._cachedDsColumns || [];
    if (columns.length === 0) return;
    // 对所有映射步骤，如果还没有映射规则行则自动填充
    $('.event-step[data-step-type="MAPPING"]').each(function() {
        var $step = $(this);
        if ($step.find('.mapping-rule-row').length === 0) {
            columns.forEach(function(col) {
                addMappingRuleRow($step, col, col, '');
            });
        }
    });
}

// 添加一条映射规则行
var mappingRuleCounter = 0;
function refreshMappingSrcDatalist() {
    var columns = window._cachedDsColumns || [];
    $('.mapping-rule-row').each(function() {
        var $row = $(this);
        var current = getMappingSrcValue($row);
        var literal = isMappingSrcLiteral($row);
        if (current && columns.indexOf(current) >= 0) {
            literal = false;
        }
        $row.children('.col-md-4').first().html(buildMappingSrcSelectHtml(current, literal));
    });
}

function isMappingSrcLiteral($row) {
    return $row.find('.mapping-src-select').css('display') === 'none';
}

function isSavedMappingLiteral(m) {
    if (!m) return false;
    return m.literal === true || m.literal === 'true' || m.srcType === 'literal';
}

function getMappingSrcValue($row) {
    if ($row.find('.mapping-src-select').css('display') === 'none') {
        return ($row.find('.mapping-src-custom').val() || '').trim();
    }
    var mode = $row.find('.mapping-src-select').val();
    if (mode === '__CUSTOM__') return '';
    return mode || '';
}

function buildMappingSrcSelectHtml(selectedSrc, literal) {
    var columns = window._cachedDsColumns || [];
    var inColumns = !!(selectedSrc && columns.indexOf(selectedSrc) >= 0);
    var isCustom = !!literal && !inColumns;
    if (!isCustom && selectedSrc && columns.length > 0 && !inColumns) {
        isCustom = true;
    }
    var html = '<div class="mapping-src-wrap">';
    html += '<select class="form-select form-select-sm mapping-src-select"' + (isCustom ? ' style="display:none;"' : '') + ' onchange="onMappingSrcModeChange(this)">';
    html += '<option value="">选择字段（自增可留空）</option>';
    columns.forEach(function(col) {
        html += '<option value="' + col.replace(/"/g, '&quot;') + '"' + (!isCustom && selectedSrc === col ? ' selected' : '') + '>' + col + '</option>';
    });
    if (!isCustom && selectedSrc && !inColumns) {
        html += '<option value="' + String(selectedSrc).replace(/"/g, '&quot;') + '" selected>' + selectedSrc + '</option>';
    }
    html += '<option value="__CUSTOM__">手动输入</option>';
    html += '</select>';
    html += '<div class="input-group input-group-sm mapping-src-custom-group"' + (isCustom ? '' : ' style="display:none;"') + '>';
    html += '<input type="text" class="form-control mapping-src-custom" placeholder="输入固定值，如 2026" value="' + (isCustom ? String(selectedSrc || '').replace(/"/g, '&quot;') : '') + '">';
    html += '<button type="button" class="btn btn-outline-secondary" title="返回下拉选择" onclick="backToMappingSrcSelect(this)"><i class="bi bi-chevron-down"></i></button>';
    html += '</div></div>';
    return html;
}

function onMappingSrcModeChange(el) {
    if ($(el).val() !== '__CUSTOM__') return;
    var $wrap = $(el).closest('.mapping-src-wrap');
    $(el).hide().val('');
    $wrap.find('.mapping-src-custom-group').show();
    $wrap.find('.mapping-src-custom').val('').focus();
}

function backToMappingSrcSelect(btn) {
    var $wrap = $(btn).closest('.mapping-src-wrap');
    $wrap.find('.mapping-src-custom-group').hide();
    $wrap.find('.mapping-src-custom').val('');
    $wrap.find('.mapping-src-select').show().val('');
}

function mappingRuleHelpHtml() {
    return '<div class="alert alert-light border mapping-help py-2 px-3 mb-2">'
        + '<div class="fw-semibold mb-1">填写说明</div>'
        + '<ul class="mb-0 ps-3">'
        + '<li>左边选库字段；选「手动输入」则填固定值，例如 <code>2026</code>。</li>'
        + '<li>文件号要 <code>0001</code>：教务已有编号时转换选「四位补零」；没有编号则选「四位序号」，源可留空。</li>'
        + '<li>右边是 XML 标签名。扁平标签直接写 <code>正题名</code>、<code>一级目录</code>。</li>'
        + '<li>嵌套标签用 <code>/</code> 分隔。例如两行目标分别填 <code>档号/全宗号</code>、<code>档号/案卷号</code>，会生成：<br>'
        + '<code>&lt;档号&gt;&lt;全宗号&gt;...&lt;/全宗号&gt;&lt;案卷号&gt;...&lt;/案卷号&gt;&lt;/档号&gt;</code></li>'
        + '</ul></div>';
}

function addMappingRuleRow($step, src, dst, transform, literal) {
    mappingRuleCounter++;
    var html = '<div class="row g-1 mb-1 mapping-rule-row" data-idx="' + mappingRuleCounter + '">';
    html += '<div class="col-md-4">' + buildMappingSrcSelectHtml(src || '', literal) + '</div>';
    html += '<div class="col-md-4"><input type="text" class="form-control form-control-sm mapping-dst" placeholder="如 档号/全宗号" value="' + (dst || '') + '"></div>';
    html += '<div class="col-md-3"><select class="form-select form-select-sm mapping-transform">' + buildMappingTransformOptions(transform || '') + '</select></div>';
    html += '<div class="col-md-1"><button type="button" class="btn btn-sm btn-outline-danger" onclick="$(this).closest(\'.mapping-rule-row\').remove()"><i class="bi bi-x"></i></button></div>';
    html += '</div>';
    $step.find('.mapping-rules-list').append(html);
}

function buildMappingTransformOptions(selected) {
    var html = '<option value="">无转换</option>';
    html += '<optgroup label="常用">';
    [['UPPER', 'UPPER-转大写'], ['LOWER', 'LOWER-转小写'], ['TRIM', 'TRIM-去空格'],
     ['PINYIN_INITIAL', '中文首字母'], ['PAD_LEFT', '四位补零(0001)'], ['SEQ_PAD4', '四位序号(0001)']].forEach(function(item) {
        html += '<option value="' + item[0] + '"' + (selected === item[0] ? ' selected' : '') + '>' + item[1] + '</option>';
    });
    html += '</optgroup>';
    if (eventListForMapping && eventListForMapping.length > 0) {
        html += '<optgroup label="事件处理">';
        var skip = { UPPER:1, LOWER:1, TRIM:1, PINYIN_INITIAL:1, AUTO_INCREMENT:1, SEQ_PAD4:1, PAD_LEFT:1, PAD4:1 };
        eventListForMapping.forEach(function(evt) {
            var code = evt.code || '';
            if (skip[code]) return;
            var name = evt.name || code;
            html += '<option value="' + code + '"' + (selected === code ? ' selected' : '') + '>' + name + '</option>';
        });
        html += '</optgroup>';
    }
    return html;
}

function isSeqPadTransform(t) {
    if (!t) return false;
    var u = String(t).toUpperCase();
    return u === 'AUTO_INCREMENT' || u === 'SEQ_PAD4' || u === 'PAD4_SEQ' || /^AUTO_INCREMENT:\d+$/.test(u) || /^SEQ_PAD\d+$/.test(u);
}

// 获取某个映射步骤的所有规则
function getMappingRules($step) {
    var rules = [];
    $step.find('.mapping-rule-row').each(function() {
        var src = getMappingSrcValue($(this));
        var dst = $(this).find('.mapping-dst').val();
        var transform = $(this).find('.mapping-transform').val();
        if (dst && (src || isSeqPadTransform(transform))) {
            var rule = { src: src || '_seq', dst: dst };
            if (transform) rule.transform = transform;
            var columns = window._cachedDsColumns || [];
            if ((!src || src === '_seq') && isSeqPadTransform(transform)) {
                rule.literal = true;
            } else if (isMappingSrcLiteral($(this)) && columns.indexOf(src) < 0) {
                rule.literal = true;
            }
            rules.push(rule);
        }
    });
    return rules;
}

// 获取输出参数配置
function getOutputParams() {
    var params = [];
    $('#outputParamsList .output-param-row').each(function() {
        var name = $(this).find('.col-md-4 input').val();
        var $sourceEl = $(this).find('[data-field="sourceField"]');
        var sourceField = '';
        if ($sourceEl.is('select')) {
            sourceField = $sourceEl.val();
        } else {
            sourceField = $sourceEl.val();
        }
        if (name) {
            params.push({ name: name, sourceField: sourceField });
        }
    });
    return params;
}

// 打开编辑器弹窗
function applyBuiltinEditorMode(on) {
    editingBuiltin = !!on;
    $('#btnAddEventStep').toggle(!editingBuiltin);
    $('input[name="outputMode"]').prop('disabled', editingBuiltin);
    if (editingBuiltin) {
        $('#fixedMappingStep').hide();
        $('#fixedMappingStep').prev('.step-connector').hide();
        $('input[name="outputMode"][value="RETURN"]').prop('checked', true).trigger('change');
    } else {
        $('#fixedMappingStep').show();
        $('#fixedMappingStep').prev('.step-connector').show();
    }
}

function openEditor(id) {
    dynamicStepCounter = 0;
    editStepCounter = 0;
    applyBuiltinEditorMode(false);
    $('#dynamicSteps').html('');
    // 同步清除映射规则和输出字段（避免AJAX延迟显示旧数据）
    $('#fixedMappingStep').find('.mapping-rules-list').html('');
    $('#fixedMappingStep').find('[data-field="mappingTemplateId"]').val('');
    $('[data-field="outputCallTemplateId"]').val('');
    $('#outputCallTemplateBtn').html('请选择模板').addClass('text-muted');
    $('[data-field="outputFilePath"]').val('');

    if (id) {
        // 编辑模式
        $.get('/visual/api/detail/' + id, function(res) {
            if (res.code === 0 && res.data) {
                var tpl = res.data;
                var builtin = !!(tpl.builtinCode && String(tpl.builtinCode).trim());
                applyBuiltinEditorMode(builtin);
                $('#editorModalTitle').text(builtin ? '编辑系统模板' : '编辑可视化模板');
                $('#editId').val(tpl.id);
                $('#editName').val(tpl.name);
                $('#editDescription').val(tpl.description);

                // 加载事件配置
                if (tpl.eventConfig) {
                    try {
                        var config = JSON.parse(tpl.eventConfig);
                        loadStepsConfig(config);
                    } catch (e) {
                        console.error('Failed to parse event config:', e);
                    }
                }
                if (builtin) {
                    $('input[name="outputMode"][value="RETURN"]').prop('checked', true).trigger('change');
                }

                // 加载输入参数
                $('#inputParamsList').html('');
                if (tpl.inputParams) {
                    try {
                        var inputParams = JSON.parse(tpl.inputParams);
                        if (Array.isArray(inputParams)) {
                            inputParams.forEach(function(p) {
                                addInputParam(p.name, p.type, p.required, p.defaultValue);
                            });
                        }
                    } catch (e) { }
                }

                // 加载输出参数
                $('#outputParamsList').html('');
                if (tpl.outputParams) {
                    try {
                        var outputParams = JSON.parse(tpl.outputParams);
                        if (Array.isArray(outputParams)) {
                            outputParams.forEach(function(p) {
                                addOutputParam(p.name, p.sourceField);
                            });
                        }
                    } catch (e) { }
                }

                $('#editorModal').modal('show');
            } else {
                showError('加载模板失败');
            }
        });
    } else {
        // 新建模式
        applyBuiltinEditorMode(false);
        $('#editorModalTitle').text('新建可视化模板');
        $('#editId').val('');
        $('#editName').val('');
        $('#editDescription').val('');
        $('#inputParamsList').html('');
        $('#outputParamsList').html('');
        $('input[name="outputMode"][value="RETURN"]').prop('checked', true).trigger('change');
        $('[data-field="outputFilePath"]').val('');
        $('[data-field="outputCallTemplateId"]').val('');
        $('#outputCallTemplateBtn').html('请选择模板').addClass('text-muted');
        $('[data-field="outputPassMode"]').val('PACKET');
        $('[data-field="outputTimeout"]').val('60');
        $('[data-field="outputOnError"]').val('STOP');
        $('[data-field="outputFileFormat"]').val('JSON');
        $('[data-field="outputWriteMode"]').val('OVERWRITE');
        // 清除数据映射步骤的映射规则
        $('#fixedMappingStep').find('.mapping-rules-list').html('');
        $('#fixedMappingStep').find('[data-field="mappingTemplateId"]').val('');
        updateStepNumbers();
        $('#editorModal').modal('show');
    }
}

// 加载步骤配置
function loadStepsConfig(config) {
    // 加载输入配置
    if (config.input) {
        var $inputStep = $('[data-step-type="INPUT"]');
        $inputStep.find('[data-field="inputType"]').val(config.input.inputType || 'MANUAL').trigger('change');
        if (config.input.cronExpr) $inputStep.find('[data-field="cronExpr"]').val(config.input.cronExpr);
        loadWatermarkInfo();
    }

    // 加载动态步骤
    if (config.steps && config.steps.length > 0) {
        config.steps.forEach(function(step) {
            addDynamicStep(step.type, step);
        });
    }

    // 加载固定映射步骤配置
    if (config.mapping) {
        var $fixedMapping = $('#fixedMappingStep');
        if ($fixedMapping.length > 0) {
            if (config.mapping.mappingTemplateId) {
                $fixedMapping.find('[data-field="mappingTemplateId"]').val(config.mapping.mappingTemplateId);
            }
            // 清空并重新填充映射规则行
            $fixedMapping.find('.mapping-rules-list').html('');
            if (config.mapping.mappings && Array.isArray(config.mapping.mappings)) {
                config.mapping.mappings.forEach(function(m) {
                    addMappingRuleRow($fixedMapping, m.src || m.source || '', m.dst || m.target || '', m.transform || '', isSavedMappingLiteral(m));
                });
            }
        }
    }

    // 加载输出配置
    var mode = 'RETURN';
    if (config.output) {
        mode = config.output.outputMode || config.output.outputTarget || 'RETURN';
        if (mode === 'DATABASE' || mode === 'API') mode = 'CALL_TEMPLATE';
    }
    // 先全部重置为默认值（彻底清残留）
    $('[data-field="outputCallTemplateId"]').val('');
    $('#outputCallTemplateBtn').html('请选择模板').addClass('text-muted');
    $('[data-field="outputPassMode"]').val('PACKET');
    $('[data-field="outputTimeout"]').val('60');
    $('[data-field="outputOnError"]').val('STOP');
    $('[data-field="outputFilePath"]').val('');
    $('[data-field="outputFileFormat"]').val('JSON');
    $('[data-field="outputWriteMode"]').val('OVERWRITE');
    if (config.output) {
        var $outputStep = $('[data-step-type="OUTPUT"]');
        // 先恢复所有字段值（在面板显示前设值）
        $outputStep.find('[data-field="outputTarget"]').val(config.output.outputTarget || 'RETURN');
        if (config.output.outputDsId) $outputStep.find('[data-field="outputDsId"]').val(config.output.outputDsId);
        if (config.output.outputTable) $outputStep.find('[data-field="outputTable"]').val(config.output.outputTable);

        if (config.output.outputDsId) $('[data-field="outputDsId"]').val(config.output.outputDsId);
        if (config.output.outputTable) $('[data-field="outputTable"]').val(config.output.outputTable);

        if (mode === 'CALL_TEMPLATE') {
            if (config.output.callTemplateId) {
                var tid = config.output.callTemplateId;
                var $sel = $('[data-field="outputCallTemplateId"]');
                // 确保选项存在
                if ($sel.find('option[value="' + tid + '"]').length === 0) {
                    $sel.append('<option value="' + tid + '">模板#' + tid + '</option>');
                    $.get('/visual/api/detail/' + tid, function(res) {
                        if (res.code === 0) $sel.find('option[value="' + tid + '"]').text(res.data.name);
                    });
                }
                var $sel = $('[data-field="outputCallTemplateId"]');
                $sel.val(tid);
                // 更新Bootstrap dropdown按钮显示
                var tname = $('#outputCallTemplateMenu').find('[data-id="' + tid + '"]').text();
                if (!tname) {
                    tname = '模板#' + tid;
                    $('#outputCallTemplateMenu').append('<li><a class="dropdown-item" href="#" data-id="' + tid + '">' + tname + '</a></li>');
                }
                $('#outputCallTemplateBtn').html(tname).removeClass('text-muted');
            }
            if (config.output.passMode) $('[data-field="outputPassMode"]').val(config.output.passMode);
            if (config.output.batchSize) $('[data-field="outputBatchSize"]').val(config.output.batchSize);
            if (config.output.timeout) $('[data-field="outputTimeout"]').val(config.output.timeout);
            if (config.output.onError) $('[data-field="outputOnError"]').val(config.output.onError);
        } else if (mode === 'FILE') {
            if (config.output.filePath) $('[data-field="outputFilePath"]').val(config.output.filePath);
            if (config.output.fileFormat) $('[data-field="outputFileFormat"]').val(config.output.fileFormat);
            if (config.output.writeMode) $('[data-field="outputWriteMode"]').val(config.output.writeMode);
            if (config.output.fileOptions) {
                if (config.output.fileOptions.pretty !== undefined) $('[data-field="fileOptionPretty"]').prop('checked', config.output.fileOptions.pretty);
                if (config.output.fileOptions.includeHeader !== undefined) $('[data-field="fileOptionHeader"]').prop('checked', config.output.fileOptions.includeHeader);
                if (config.output.fileOptions.delimiter) $('[data-field="fileOptionDelimiter"]').val(config.output.fileOptions.delimiter);
                if (config.output.fileOptions.encoding) $('[data-field="fileOptionEncoding"]').val(config.output.fileOptions.encoding);
            }
        }
    }

    // 最后切换输出模式
    $('input[name="outputMode"][value="' + mode + '"]').prop('checked', true).trigger('change');

    updateStepNumbers();
}

// 添加步骤按钮
function addEventStep() {
    $('#addStepModal').modal('show');
}

// 确认添加步骤
function confirmAddStep(type) {
    $('#addStepModal').modal('hide');
    addDynamicStep(type, null);
}

// ============ 数据源步骤辅助函数 ============

// 切换数据库/接口分支
function switchDsBranch(el, branch) {
    var $step = $(el).closest('.event-step');
    $step.find('.ds-source-tabs .ds-branch-tab').removeClass('active');
    $step.find('.ds-source-tabs .ds-branch-tab[data-branch="' + branch + '"]').addClass('active');
    if ($(el).hasClass('ds-branch-tab') && !$(el).closest('.ds-source-tabs').length) {
        $step.find('.ds-branch-tab').removeClass('active');
        $(el).addClass('active');
    }
    $step.find('.ds-branch-panel').removeClass('active');
    $step.find('.ds-branch-panel[data-branch="' + branch + '"]').addClass('active');
    $step.find('.ds-type-label').text(branch === 'api' ? '接口' : '数据库');
}

function fillMockOpenApi(el) {
    var $step = $(el).closest('.event-step');
    var origin = window.location.origin || ('http://127.0.0.1:' + (window.location.port || '8010'));
    $step.find('[data-field="tokenUrl"]').val(origin + '/mock/openapi/token');
    $step.find('[data-field="tokenMethod"]').val('POST');
    $step.find('[data-field="tokenExtractPath"]').val('result.access_token');
    $step.find('[data-field="tokenQueryParam"]').val('access_token');
    $step.find('[data-field="apiUrl"]').val(origin + '/mock/openapi/data');
    $step.find('[data-field="apiMethod"]').val('GET');
    $step.find('[data-field="apiListPath"]').val('result.data');
    $step.find('[data-field="batchSize"]').val('0');
    $step.find('[data-field="apiPageField"]').val('');
    $step.find('[data-field="apiSizeField"]').val('');
    $step.find('[data-field="incrementalField"]').val('id');
    $step.find('[data-field="incrementalParam"]').val('since');
    showSuccess('已填入本机模拟接口：先取 token，再带 access_token 拉 result.data；增量字段 id / since');
}

function onInputTypeChange(el) {
    var val = $(el).val();
    var $root = $(el).closest('.event-step');
    $root.find('.cron-config').toggle(val === 'CRON');
    if (val === 'CRON') {
        loadWatermarkInfo();
    }
}

function loadWatermarkInfo() {
    var id = $('#editId').val();
    var $box = $('#watermarkInfo');
    if (!$box.length) return;
    if (!id) {
        $box.text('保存模板后才会启动定时，并记录水位线。');
        return;
    }
    $.get('/visual/api/watermark/' + id, function(res) {
        if (res.code !== 0 || !res.data || !res.data.exists) {
            $box.text('尚无水位线。第一次定时会按当前策略拉取，成功后再记住进度。');
            return;
        }
        var d = res.data;
        $box.text('当前水位: lastValue=' + (d.lastValue || '-') + ', lastOffset=' + (d.lastOffset || 0)
            + (d.lastExecTime ? ', 上次=' + d.lastExecTime : ''));
    });
}

function resetWatermark() {
    var id = $('#editId').val();
    if (!id) {
        showWarning('请先保存模板');
        return;
    }
    if (!confirm('重置后下次将从头/按全量拉数，确定？')) return;
    $.post('/visual/api/watermark/' + id + '/reset', function(res) {
        if (res.code === 0) {
            showSuccess(res.data || '水位线已重置');
            loadWatermarkInfo();
        } else {
            showError(res.message || '重置失败');
        }
    });
}

// 数据源选择变更 → 根据类型自动切换分支
function onDsChange(el) {
    var dsId = $(el).val();
    var $step = $(el).closest('.event-step');
    if (!dsId) {
        $step.find('.ds-type-label').text('可在下方切换 数据库 / 接口');
        return;
    }

    // 查找数据源信息
    var dsInfo = {};
    dataSourceList.forEach(function(ds) {
        if (ds.id == dsId) { dsInfo = ds; }
    });
    var dsType = dsInfo.sourceType || '';

    // 更新类型标签
    var typeLabel = dsType === 'API' ? '接口' : '数据库';
    $step.find('.ds-type-label').text(typeLabel);
    $step.find('.ds-source-tabs .ds-branch-tab').removeClass('active');
    $step.find('.ds-source-tabs .ds-branch-tab[data-branch="' + (dsType === 'API' ? 'api' : 'db') + '"]').addClass('active');

    // 自动切换分支
    $step.find('.ds-branch-panel').removeClass('active');
    if (dsType === 'API') {
        $step.find('.ds-branch-panel[data-branch="api"]').addClass('active');
        // 自动填充API字段
        var $apiPanel = $step.find('.ds-branch-panel[data-branch="api"]');
        if (dsInfo.apiUrl && !$apiPanel.find('[data-field="apiUrl"]').val()) {
            $apiPanel.find('[data-field="apiUrl"]').val(dsInfo.apiUrl || '');
            $apiPanel.find('[data-field="apiMethod"]').val(dsInfo.apiMethod || 'GET');
            $apiPanel.find('[data-field="apiHeaders"]').val(dsInfo.apiHeaders || '');
            $apiPanel.find('[data-field="apiBody"]').val(dsInfo.apiBody || '');
            $apiPanel.find('[data-field="apiTimeout"]').val(dsInfo.apiTimeout || '180');
            $apiPanel.find('[data-field="apiRetryTimes"]').val(dsInfo.apiRetryTimes != null && dsInfo.apiRetryTimes !== '' ? dsInfo.apiRetryTimes : '3');
            $apiPanel.find('[data-field="apiRetryInterval"]').val(dsInfo.apiRetryInterval != null && dsInfo.apiRetryInterval !== '' ? dsInfo.apiRetryInterval : '1000');
        }
    } else {
        $step.find('.ds-branch-panel[data-branch="db"]').addClass('active');

        // 优先用数据源已配置的表名自动生成基础SQL
        var configuredTables = resolveTableList(dsInfo.tableNames || '', dsInfo.tableName || '');
        var $sql = $step.find('[data-field="sql"]');
        if (configuredTables.length > 0 && !$sql.val().trim()) {
            var firstTable = configuredTables[0];
            $sql.val('SELECT * FROM ' + firstTable);
        }

        // 加载字段（优先用配置的表名，否则实时查库）
        if (configuredTables.length > 0) {
            loadColumnsForDsDirect(dsId, configuredTables[0], $step);
        } else {
            loadColumnsForDs(dsId, $step);
        }
    }
}

// 直接从配置的表名加载字段（无需先查表列表）
function loadColumnsForDsDirect(dsId, tableName, $step) {
    $.get('/datasource/api/getColumns', {id: dsId, tableName: tableName}, function(res) {
        if (res.code === 0 && res.data && res.data.success) {
            cacheDsColumns(res.data.columns, $step);
        }
    });
}

// 加载数据源字段并缓存（兜底：AJAX查库获取表列表→取第一个表→查字段）
function loadColumnsForDs(dsId, $step) {
    $.get('/datasource/api/getTables', {id: dsId}, function(res) {
        if (res.code === 0 && res.data && res.data.success) {
            var tables = res.data.tables || [];
            if (tables.length > 0) {
                var firstTable = tables[0].name;
                // 自动生成SQL（如果为空）
                var $sql = $step.find('[data-field="sql"]');
                if (!$sql.val().trim()) {
                    $sql.val('SELECT * FROM ' + firstTable);
                }
                // 加载字段
                $.get('/datasource/api/getColumns', {id: dsId, tableName: firstTable}, function(res2) {
                    if (res2.code === 0 && res2.data && res2.data.success) {
                        cacheDsColumns(res2.data.columns, $step);
                    }
                });
            }
        }
    });
}

// 将嵌套对象展平为 dot.path 格式
function flattenRowColumns(row, prefix) {
    var paths = [];
    if (!row || typeof row !== 'object') return paths;
    Object.keys(row).forEach(function(key) {
        var val = row[key];
        var fullPath = prefix ? prefix + '.' + key : key;
        paths.push(fullPath);
        if (val && typeof val === 'object' && !Array.isArray(val)) {
            paths = paths.concat(flattenRowColumns(val, fullPath));
        }
    });
    return paths;
}

// 缓存数据源字段（供映射和输出步骤使用）
function cacheDsColumns(columns, $step) {
    var colNames = [];
    if (columns && columns.length > 0) {
        colNames = columns.map(function(c) { return c.name; });
    }
    window._cachedDsColumns = colNames;
    refreshMappingSrcDatalist();
    fillOutputSourceFields(colNames);
    fillMappingsFromDsColumns();
}

// 自动填充输出步骤的sourceField下拉
function fillOutputSourceFields(columns) {
    if (!columns || columns.length === 0) return;
    // 更新所有输出参数行的sourceField下拉
    $('#outputParamsList .output-param-row').each(function() {
        var $sel = $(this).find('select[data-field="sourceField"]');
        if ($sel.length === 0) {
            // 如果是input类型的，尝试找第二个input
            var $inputs = $(this).find('input');
            // 跳过
        }
    });
    // 存储供后续添加输出参数时使用
    window._outputSourceFields = columns;
}

// 根据输入参数自动生成SQL条件
function autoGenerateSql(el) {
    var $step = $(el).closest('.event-step');
    var $sql = $step.find('[data-field="sql"]');
    var currentSql = $sql.val().trim();

    // 获取输入参数
    var inputParams = getInputParams();
    if (inputParams.length === 0) {
        showWarning('请先在输入事件中添加输入参数');
        return;
    }

    // 如果SQL为空，提示用户先输入基础SQL
    if (!currentSql || currentSql.toUpperCase().indexOf('SELECT') === -1) {
        showWarning('请先在SQL语句中输入基础查询，如 SELECT * FROM table_name');
        return;
    }

    // 获取当前数据源的数据库类型
    var dsId = $step.find('[data-field="dsId"]').val();
    var dbType = '';
    dataSourceList.forEach(function(ds) {
        if (ds.id == dsId) dbType = (ds.dbType || '').toUpperCase();
    });

    // 去掉已有的WHERE子句
    var whereIdx = currentSql.toUpperCase().indexOf(' WHERE ');
    var baseSql = whereIdx > 0 ? currentSql.substring(0, whereIdx) : currentSql;

    // 根据数据库类型生成模糊匹配语法
    var likeExpr;
    function quoteIdent(name) {
        var n = String(name || '').replace(/`/g, '').replace(/"/g, '').replace(/]/g, '');
        if (dbType.indexOf('SQLSERVER') >= 0 || dbType.indexOf('MSSQL') >= 0) {
            return '[' + n + ']';
        }
        if (dbType.indexOf('MYSQL') >= 0 || dbType.indexOf('MARIA') >= 0 ||
            dbType.indexOf('TIDB') >= 0 || dbType.indexOf('CLICKHOUSE') >= 0 ||
            dbType.indexOf('TDENGINE') >= 0 || dbType.indexOf('TAOS') >= 0) {
            return '`' + n + '`';
        }
        if (dbType.indexOf('POSTGRES') >= 0 || dbType.indexOf('ORACLE') >= 0 ||
            dbType.indexOf('H2') >= 0 || dbType.indexOf('DB2') >= 0 ||
            dbType.indexOf('SQLITE') >= 0 || dbType.indexOf('HANA') >= 0 ||
            dbType.indexOf('FIREBIRD') >= 0 || dbType.indexOf('KINGBASE') >= 0 ||
            dbType.indexOf('GAUSS') >= 0) {
            return '"' + n + '"';
        }
        return '`' + n + '`';
    }
    if (dbType.indexOf('POSTGRESQL') >= 0 || dbType.indexOf('PG') >= 0 ||
        dbType.indexOf('ORACLE') >= 0 || dbType.indexOf('SQLITE') >= 0 ||
        dbType.indexOf('DB2') >= 0 || dbType.indexOf('DUCKDB') >= 0 ||
        dbType.indexOf('FIREBIRD') >= 0 || dbType.indexOf('HANA') >= 0 ||
        dbType.indexOf('KINGBASE') >= 0 || dbType.indexOf('GAUSS') >= 0) {
        likeExpr = function(param) { return "'%' || $" + '{' + param + "} || '%'"; };
    } else if (dbType.indexOf('SQLSERVER') >= 0 || dbType.indexOf('MSSQL') >= 0) {
        likeExpr = function(param) { return "'%' + $" + '{' + param + "} + '%'"; };
    } else {
        likeExpr = function(param) { return "CONCAT('%', $" + '{' + param + "}, '%')"; };
    }

    var conditions = [];
    inputParams.forEach(function(p) {
        var name = p.name;
        var type = (p.type || 'string').toLowerCase();
        var col = quoteIdent(name);
        var cond = '';
        if (type === 'number' || type === 'boolean') {
            cond = col + ' = $' + '{' + name + '}';
        } else if (type === 'date' || type === 'datetime' || type === 'time' || type === 'timestamp') {
            cond = col + ' >= $' + '{' + name + '}';
        } else {
            cond = col + ' LIKE ' + likeExpr(name);
        }
        conditions.push(cond);
    });

    if (conditions.length > 0) {
        var newSql = baseSql + ' WHERE ' + conditions.join(' AND ');
        $sql.val(newSql);
        showSuccess('已根据 ' + conditions.length + ' 个输入参数生成查询条件（' + (dbType || '默认') + ' 语法）');
    }

    // 自动填充输出步骤的sourceField下拉
    var cachedCols = window._cachedDsColumns || [];
    if (cachedCols.length > 0) {
        fillOutputSourceFields(cachedCols);
    }
}

// 切换高级配置展开/折叠
function toggleAdvanced(el) {
    var $body = $(el).next('.advanced-body');
    $body.toggleClass('open');
    var $icon = $(el).find('i');
    $icon.toggleClass('bi-chevron-down bi-chevron-up');
}

// 测试查询（数据库）
function testQuery(el) {
    var $step = $(el).closest('.event-step');
    var dsId = $step.find('[data-field="dsId"]').val();
    var sql = $step.find('[data-field="sql"]').val();
    if (!dsId || !sql) {
        showWarning('请先选择数据源和编写SQL');
        return;
    }
    var $preview = $step.find('.result-preview');
    $preview.html('<div class="text-center text-muted py-2"><i class="bi bi-hourglass-split"></i> 查询中...</div>');
    $.ajax({
        url: '/datasource/api/testQuery',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({dsId: parseInt(dsId), sql: sql, limit: 5}),
        success: function(res) {
            if (res.code === 0 && res.data) {
                renderPreviewTable($preview, res.data);
                // 缓存查询结果的实际列名并自动填充映射规则
                if (res.data.columns && res.data.columns.length > 0) {
                    // 展平嵌套字段
                    var flatCols = res.data.columns.slice();
                    if (res.data.rows && res.data.rows.length > 0) {
                        flatCols = flattenRowColumns(res.data.rows[0], '');
                    }
                    window._cachedDsColumns = flatCols;
                    refreshMappingSrcDatalist();
                    fillOutputSourceFields(flatCols);
                    fillMappingsFromDsColumns();
                }
            } else {
                $preview.html('<div class="text-danger small">' + (res.message || '查询失败') + '</div>');
            }
        },
        error: function() {
            $preview.html('<div class="text-danger small">请求失败</div>');
        }
    });
}

// 测试请求（接口）
function testApiQuery(el) {
    var $step = $(el).closest('.event-step');
    var dsId = parseInt($step.find('[data-field="dsId"]').val()) || 0;
    var url = $step.find('[data-field="apiUrl"]').val();
    var method = $step.find('[data-field="apiMethod"]').val() || 'GET';
    var headers = $step.find('[data-field="apiHeaders"]').val();
    var body = $step.find('[data-field="apiBody"]').val();
    if (!url) {
        showWarning('请填写接口URL');
        return;
    }
    var $preview = $step.find('.result-preview');
    $preview.html('<div class="text-center text-muted py-2"><i class="bi bi-hourglass-split"></i> 请求中...</div>');
    $.ajax({
        url: '/datasource/api/testQuery',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({
            dsId: dsId, sql: '', limit: 5,
            apiUrl: url, apiMethod: method, apiHeaders: headers, apiBody: body,
            tokenUrl: $step.find('[data-field="tokenUrl"]').val() || '',
            tokenMethod: $step.find('[data-field="tokenMethod"]').val() || 'POST',
            tokenHeaders: $step.find('[data-field="tokenHeaders"]').val() || '',
            tokenBody: $step.find('[data-field="tokenBody"]').val() || '',
            tokenExtractPath: $step.find('[data-field="tokenExtractPath"]').val() || 'result.access_token',
            tokenQueryParam: $step.find('[data-field="tokenQueryParam"]').val() || 'access_token',
            apiListPath: $step.find('[data-field="apiListPath"]').val() || '',
            timeout: parseInt($step.find('[data-field="apiTimeout"]').val(), 10) || 180,
            apiRetryTimes: parseInt($step.find('[data-field="apiRetryTimes"]').val(), 10),
            apiRetryInterval: parseInt($step.find('[data-field="apiRetryInterval"]').val(), 10)
        }),
        success: function(res) {
            if (res.code === 0 && res.data) {
                renderPreviewTable($preview, res.data);
                // 展平嵌套字段并缓存
                if (res.data.columns && res.data.columns.length > 0) {
                    var flatCols2 = res.data.columns.slice();
                    if (res.data.rows && res.data.rows.length > 0) {
                        flatCols2 = flattenRowColumns(res.data.rows[0], '');
                    }
                    window._cachedDsColumns = flatCols2;
                    refreshMappingSrcDatalist();
                    fillOutputSourceFields(flatCols2);
                    fillMappingsFromDsColumns();
                }
            } else {
                $preview.html('<div class="text-danger small">' + (res.message || '请求失败') + '</div>');
            }
        },
        error: function() {
            $preview.html('<div class="text-danger small">请求失败</div>');
        }
    });
}

// 渲染预览表格（最多5行，超出滚动）
function renderPreviewTable($container, data) {
    var columns = data.columns || [];
    var rows = data.rows || [];
    if (columns.length === 0 && rows.length === 0) {
        $container.html('<div class="text-muted small">无数据</div>');
        return;
    }
    var html = '<table><thead><tr>';
    columns.forEach(function(col) { html += '<th>' + col + '</th>'; });
    html += '</tr></thead><tbody>';
    var displayRows = rows.slice(0, 5);
    displayRows.forEach(function(row) {
        html += '<tr>';
        if (Array.isArray(row)) {
            // 数组行：按索引遍历
            row.forEach(function(cell) {
                var v = cell, title = '';
                if (typeof v === 'object') { title = JSON.stringify(v); v = title.substring(0, 40) + (title.length > 40 ? '...' : ''); }
                var tv = String(title || (v != null ? v : ''));
                html += '<td title="' + tv.replace(/"/g, '&quot;') + '">' + (v != null ? v : '') + '</td>';
            });
        } else if (typeof row === 'object') {
            columns.forEach(function(col) {
                var v = row[col], title = '';
                if (typeof v === 'object') { title = JSON.stringify(v); v = title.substring(0, 40) + (title.length > 40 ? '...' : ''); }
                var tv = String(title || (v != null ? v : ''));
                html += '<td title="' + tv.replace(/"/g, '&quot;') + '">' + (v != null ? v : '') + '</td>';
            });
        }
        html += '</tr>';
    });
    html += '</tbody></table>';
        html += '<div class="text-muted small mt-1">测试样例 ' + displayRows.length + ' 行（只用于查看结构和映射字段）</div>';
    $container.html(html);
}

// ============ 数据映射步骤辅助函数 ============

// 删除固定映射步骤
function removeFixedMapping() {
    if (confirm('确定要删除数据映射步骤吗？')) {
        $('#fixedMappingStep').prev('.step-connector').remove();
        $('#fixedMappingStep').next('.step-connector').remove();
        $('#fixedMappingStep').remove();
        updateStepNumbers();
    }
}

// 恢复固定映射步骤（如果被删除了）
function restoreFixedMapping() {
    if ($('#fixedMappingStep').length > 0) return; // 已存在
    var html = '<div class="step-connector"><i class="bi bi-arrow-down"></i></div>';
    html += '<div class="event-step fixed" data-step-type="MAPPING" id="fixedMappingStep">';
    // ... 重新生成映射步骤HTML
    // 这个函数暂时不需要，用户删除后可以通过重新创建模板恢复
}

// 映射模板选择变更 → 自动填充映射规则
// 映射模板选择变更 → 清空并重新填充映射规则行
function onMappingTemplateChange(el) {
    var templateId = $(el).val();
    var $step = $(el).closest('.event-step');
    // 清空现有映射规则行
    $step.find('.mapping-rules-list').html('');
    if (!templateId) {
        return;
    }
    // 查找映射模板
    var found = null;
    mappingTemplateList.forEach(function(mt) {
        if (mt.id == templateId) found = mt;
    });
    if (found && found.mappings) {
        try {
            var mappings = typeof found.mappings === 'string' ? JSON.parse(found.mappings) : found.mappings;
            if (Array.isArray(mappings)) {
                mappings.forEach(function(m) {
                    addMappingRuleRow($step, m.src || m.source || '', m.dst || m.target || '', m.transform || '', isSavedMappingLiteral(m));
                });
            }
        } catch(e) {
            showWarning('映射模板数据解析失败');
            return;
        }
        showSuccess('已加载映射模板: ' + found.name);
    }
}

// 添加动态步骤
function addDynamicStep(type, data) {
    dynamicStepCounter++;
    editStepCounter++;
    var stepId = 'step_' + editStepCounter;

    var html = '<div class="event-step" id="' + stepId + '" data-step-type="' + type + '" data-step-index="' + editStepCounter + '">';
    html += '<div class="step-header">';
    html += '<div class="d-flex align-items-center gap-2">';
    html += '<span class="step-number"></span>';

    // 类型标签
    switch (type) {
        case 'DATA_SOURCE':
            html += '<span class="badge bg-primary">数据源事件</span>';
            break;
        case 'MAPPING':
            html += '<span class="badge bg-warning">数据映射</span>';
            break;
        case 'FILTER':
            html += '<span class="badge bg-info">数据过滤</span>';
            break;
        case 'CALL_TEMPLATE':
            html += '<span class="badge bg-danger">调用模板</span>';
            break;
        case 'OPERATION':
            html += '<span class="badge bg-success">操作事件</span>';
            break;
        case 'EVENT':
            html += '<span class="badge bg-purple" style="background:#6f42c1;">事件处理</span>';
            break;
        case 'THESIS_ARCHIVE':
            html += '<span class="badge bg-dark">论文归档推送</span>';
            break;
        case 'FILE_DOWNLOAD':
            html += '<span class="badge bg-secondary">附件下载</span>';
            break;
    }

    html += '</div>';
    var lockedStep = editingBuiltin && (type === 'FILE_DOWNLOAD' || type === 'THESIS_ARCHIVE' || type === 'CALL_TEMPLATE');
    if (!lockedStep) {
        html += '<span class="btn-remove" onclick="removeStep(\'' + stepId + '\')"><i class="bi bi-x-circle"></i></span>';
    } else {
        html += '<span class="badge bg-light text-muted">不可删除</span>';
    }
    html += '</div>';

    // 根据类型显示不同的配置
    html += '<div class="row g-2">';

    switch (type) {
        case 'DATA_SOURCE':
            html += '<div class="col-md-5">';
            html += '<label class="form-label small">数据源 <span class="text-muted">(可选)</span></label>';
            html += '<select class="form-select form-select-sm step-config" data-field="dsId" onchange="onDsChange(this)">';
            html += '<option value="">不选，直接填接口或 SQL</option>';
            html += '</select>';
            html += '</div>';
            html += '<div class="col-md-7">';
            html += '<label class="form-label small">类型 <span class="text-muted ds-type-label">可在下方切换 数据库 / 接口</span></label>';
            html += '</div>';
            html += '<div class="col-md-12">';
            html += '<div class="ds-branch-tabs ds-source-tabs mb-2">';
            html += '<span class="ds-branch-tab active" data-branch="db" onclick="switchDsBranch(this,\'db\')">数据库</span>';
            html += '<span class="ds-branch-tab" data-branch="api" onclick="switchDsBranch(this,\'api\')">接口</span>';
            html += '</div>';
            html += '</div>';

            // 数据库分支
            html += '<div class="col-md-12 ds-branch-panel active" data-branch="db">';
            html += '<div class="row g-2">';
            html += '<div class="col-md-8">';
            html += '<label class="form-label small">SQL语句 <span class="text-muted">(自动生成，可手动编辑)</span></label>';
            html += '<textarea class="form-control form-form-control-sm step-config font-monospace" data-field="sql" rows="2" placeholder="SELECT * FROM table WHERE id = $' + '{id}" style="font-size:0.82rem;">' + (data && data.sql ? data.sql : '') + '</textarea>';
            html += '</div>';
            html += '<div class="col-md-4">';
            html += '<label class="form-label small">&nbsp;</label><br>';
            html += '<button type="button" class="btn btn-sm btn-outline-primary" onclick="autoGenerateSql(this)"><i class="bi bi-magic"></i> 根据输入参数生成条件</button>';
            html += '</div>';
            html += '</div>';
            html += '<div class="row g-2 mt-1">';
            html += '<div class="col-md-4">';
            html += '<label class="form-label small">每批条数 <span class="text-muted">(0=一次取完)</span></label>';
            html += '<input type="number" min="0" class="form-control form-control-sm step-config" data-field="batchSize" value="' + (data && data.batchSize != null && data.batchSize !== '' ? data.batchSize : '100') + '">';
            html += '</div>';
            html += '<div class="col-md-4">';
            html += '<label class="form-label small">最多批次 <span class="text-muted">(0=直到没数据)</span></label>';
            html += '<input type="number" min="0" class="form-control form-control-sm step-config" data-field="maxBatches" value="' + (data && data.maxBatches != null && data.maxBatches !== '' ? data.maxBatches : '0') + '">';
            html += '</div>';
            html += '<div class="col-md-12"><div class="small text-muted">正式执行按批循环（查一批处理一批）。可在 SQL 中写 $' + '{offset}、$' + '{pageSize}、$' + '{page} 自行分页；不写则系统自动包装分页。定时请用下方「每次只跑一批」或增量，避免一次 Cron 拉完全部。</div></div>';
            html += '</div>';
            // 高级配置
            html += '<div class="advanced-toggle" onclick="toggleAdvanced(this)"><span>高级配置</span><i class="bi bi-chevron-down"></i></div>';
            html += '<div class="advanced-body">';
            html += '<div class="d-flex justify-content-between align-items-center mb-1">';
            html += '<span class="small text-muted">测试只取 5 条，用于查看字段和映射</span>';
            html += '<button type="button" class="btn btn-sm btn-outline-info" onclick="testQuery(this)"><i class="bi bi-play"></i> 测试查询</button>';
            html += '</div>';
            html += '<div class="result-preview"><table><tr><td class="text-muted">点击"测试查询"查看结构和字段</td></tr></table></div>';
            html += '</div>';
            html += '</div>';

            // 接口分支
            html += '<div class="col-md-12 ds-branch-panel" data-branch="api">';
            html += '<div class="row g-2">';
            html += '<div class="col-md-6">';
            html += '<label class="form-label small">接口URL <span class="text-muted">(数据接口)</span></label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="apiUrl" placeholder="https://api.example.com/data" value="' + (data && data.apiUrl ? data.apiUrl : '') + '">';
            html += '</div>';
            html += '<div class="col-md-3">';
            html += '<label class="form-label small">请求方式</label>';
            html += '<select class="form-select form-select-sm step-config" data-field="apiMethod">';
            html += '<option value="GET"' + (data && data.apiMethod === 'GET' ? ' selected' : '') + '>GET</option>';
            html += '<option value="POST"' + (data && data.apiMethod === 'POST' ? ' selected' : '') + '>POST</option>';
            html += '</select>';
            html += '</div>';
            html += '<div class="col-md-3">';
            html += '<label class="form-label small">超时(秒)</label>';
            html += '<input type="number" min="1" max="600" class="form-control form-control-sm step-config" data-field="apiTimeout" value="' + (data && data.apiTimeout ? data.apiTimeout : '180') + '">';
            html += '</div>';
            html += '</div>';
            html += '<div class="row g-2 mt-1">';
            html += '<div class="col-md-3">';
            html += '<label class="form-label small">失败重试次数</label>';
            html += '<input type="number" min="0" max="10" class="form-control form-control-sm step-config" data-field="apiRetryTimes" value="' + (data && data.apiRetryTimes != null && data.apiRetryTimes !== '' ? data.apiRetryTimes : '3') + '">';
            html += '</div>';
            html += '<div class="col-md-3">';
            html += '<label class="form-label small">重试间隔(毫秒)</label>';
            html += '<input type="number" min="0" max="60000" class="form-control form-control-sm step-config" data-field="apiRetryInterval" value="' + (data && data.apiRetryInterval != null && data.apiRetryInterval !== '' ? data.apiRetryInterval : '1000') + '">';
            html += '</div>';
            html += '<div class="col-md-6"><div class="small text-muted mt-4">超时或 5xx 时再试；默认 3 分钟超时、失败再试 3 次。设 0 则不重试。</div></div>';
            html += '</div>';
            html += '<div class="row g-2 mt-1">';
            html += '<div class="col-md-6">';
            html += '<label class="form-label small">请求头 <small class="text-muted">(JSON)</small></label>';
            html += '<textarea class="form-control form-control-sm step-config" data-field="apiHeaders" rows="2" placeholder=\'{"Content-Type":"application/json"}\'>' + (data && data.apiHeaders ? data.apiHeaders : '') + '</textarea>';
            html += '</div>';
            html += '<div class="col-md-6">';
            html += '<label class="form-label small">请求体 <small class="text-muted">(POST时)</small></label>';
            html += '<textarea class="form-control form-control-sm step-config" data-field="apiBody" rows="2" placeholder=\'{"key":"value"}\'>' + (data && data.apiBody ? data.apiBody : '') + '</textarea>';
            html += '</div>';
            html += '</div>';
            html += '<div class="row g-2 mt-1">';
            html += '<div class="col-md-6">';
            html += '<label class="form-label small">Token 接口URL <span class="text-muted">(先取 token，再请求数据接口)</span></label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="tokenUrl" placeholder="http://host/openapi/token，可留空" value="' + (data && data.tokenUrl ? data.tokenUrl : '') + '">';
            html += '</div>';
            html += '<div class="col-md-3">';
            html += '<label class="form-label small">Token 请求方式</label>';
            html += '<select class="form-select form-select-sm step-config" data-field="tokenMethod">';
            html += '<option value="POST"' + (!data || !data.tokenMethod || data.tokenMethod === 'POST' ? ' selected' : '') + '>POST</option>';
            html += '<option value="GET"' + (data && data.tokenMethod === 'GET' ? ' selected' : '') + '>GET</option>';
            html += '</select>';
            html += '</div>';
            html += '<div class="col-md-3">';
            html += '<label class="form-label small">Token 提取路径</label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="tokenExtractPath" placeholder="result.access_token" value="' + (data && data.tokenExtractPath ? data.tokenExtractPath : 'result.access_token') + '">';
            html += '</div>';
            html += '<div class="col-md-6">';
            html += '<label class="form-label small">Token 请求头 <small class="text-muted">(JSON)</small></label>';
            html += '<textarea class="form-control form-control-sm step-config" data-field="tokenHeaders" rows="2" placeholder=\'{"Content-Type":"application/json"}\'>' + (data && data.tokenHeaders ? data.tokenHeaders : '') + '</textarea>';
            html += '</div>';
            html += '<div class="col-md-6">';
            html += '<label class="form-label small">Token 请求体</label>';
            html += '<textarea class="form-control form-control-sm step-config" data-field="tokenBody" rows="2" placeholder=\'{"appid":"xxx"}\'>' + (data && data.tokenBody ? data.tokenBody : '') + '</textarea>';
            html += '</div>';
            html += '<div class="col-md-4">';
            html += '<label class="form-label small">数据接口 Token 参数名</label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="tokenQueryParam" placeholder="access_token" value="' + (data && data.tokenQueryParam != null ? data.tokenQueryParam : 'access_token') + '">';
            html += '</div>';
            html += '<div class="col-md-8"><div class="small text-muted mt-4">会自动带上 ?access_token=...；也可在 URL 写 $' + '{access_token}。列表路径填 result.data。<a href="javascript:void(0)" onclick="fillMockOpenApi(this)">填入本机模拟接口</a></div></div>';
            html += '</div>';
            html += '<div class="row g-2 mt-1">';
            html += '<div class="col-md-4">';
            html += '<label class="form-label small">每批条数 <span class="text-muted">(0=一次取完)</span></label>';
            html += '<input type="number" min="0" class="form-control form-control-sm step-config" data-field="batchSize" value="' + (data && data.batchSize != null && data.batchSize !== '' ? data.batchSize : '100') + '">';
            html += '</div>';
            html += '<div class="col-md-4">';
            html += '<label class="form-label small">最多批次 <span class="text-muted">(0=直到没数据)</span></label>';
            html += '<input type="number" min="0" class="form-control form-control-sm step-config" data-field="maxBatches" value="' + (data && data.maxBatches != null && data.maxBatches !== '' ? data.maxBatches : '0') + '">';
            html += '</div>';
            html += '<div class="col-md-4">';
            html += '<label class="form-label small">起始页</label>';
            html += '<input type="number" class="form-control form-control-sm step-config" data-field="apiPageStart" value="' + (data && data.apiPageStart != null && data.apiPageStart !== '' ? data.apiPageStart : '1') + '">';
            html += '</div>';
            html += '<div class="col-md-4">';
            html += '<label class="form-label small">页码参数名</label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="apiPageField" placeholder="page / pageNo，空则不传" value="' + (data && data.apiPageField != null ? data.apiPageField : 'page') + '">';
            html += '</div>';
            html += '<div class="col-md-4">';
            html += '<label class="form-label small">条数参数名</label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="apiSizeField" placeholder="pageSize / limit，空则不传" value="' + (data && data.apiSizeField != null ? data.apiSizeField : 'pageSize') + '">';
            html += '</div>';
            html += '<div class="col-md-4">';
            html += '<label class="form-label small">偏移参数名</label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="apiOffsetField" placeholder="offset / start，可选" value="' + (data && data.apiOffsetField ? data.apiOffsetField : '') + '">';
            html += '</div>';
            html += '<div class="col-md-6">';
            html += '<label class="form-label small">列表字段路径</label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="apiListPath" placeholder="result.data" value="' + (data && data.apiListPath ? data.apiListPath : '') + '">';
            html += '</div>';
            html += '<div class="col-md-12"><div class="small text-muted">接口按批循环。参数名按对方接口填写；也可在 URL/请求体写 $' + '{page}、$' + '{pageSize}、$' + '{offset}、$' + '{limit}。定时建议配合下方增量/每次一批，URL 可写 $' + '{lastValue}。</div></div>';
            html += '</div>';
            // 接口高级配置
            html += '<div class="advanced-toggle" onclick="toggleAdvanced(this)"><span>高级配置</span><i class="bi bi-chevron-down"></i></div>';
            html += '<div class="advanced-body">';
            html += '<div class="row g-2 mb-2">';
            html += '<div class="col-md-6">';
            html += '<label class="form-label small">时间字段</label>';
            html += '<select class="form-select form-select-sm step-config" data-field="timeField"><option value="">无</option></select>';
            html += '</div>';
            html += '<div class="col-md-6">';
            html += '<label class="form-label small">时间格式</label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="timeFormat" value="yyyy-MM-dd" placeholder="yyyy-MM-dd">';
            html += '</div>';
            html += '</div>';
            html += '<div class="d-flex justify-content-between align-items-center mb-1">';
            html += '<span class="small text-muted">测试只取 5 条，用于查看字段和映射</span>';
            html += '<button type="button" class="btn btn-sm btn-outline-info" onclick="testApiQuery(this)"><i class="bi bi-play"></i> 测试请求</button>';
            html += '</div>';
            html += '<div class="result-preview"><table><tr><td class="text-muted">点击"测试请求"查看结构和字段</td></tr></table></div>';
            html += '</div>';
            html += '</div>';

            html += '<div class="col-md-12 mt-2 p-2 border rounded bg-light">';
            html += '<div class="fw-semibold small mb-1">同步方式（定时用）</div>';
            html += '<div class="row g-2">';
            html += '<div class="col-md-3">';
            html += '<label class="form-label small">读取策略</label>';
            html += '<select class="form-select form-select-sm step-config" data-field="syncMode">';
            html += '<option value="FULL"' + (!data || !data.syncMode || data.syncMode === 'FULL' ? ' selected' : '') + '>全量</option>';
            html += '<option value="INCREMENTAL"' + (data && data.syncMode === 'INCREMENTAL' ? ' selected' : '') + '>增量</option>';
            html += '</select>';
            html += '</div>';
            html += '<div class="col-md-3">';
            html += '<label class="form-label small">增量字段</label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="incrementalField" placeholder="id / update_time" value="' + (data && data.incrementalField ? data.incrementalField : '') + '">';
            html += '</div>';
            html += '<div class="col-md-3">';
            html += '<label class="form-label small">接口增量参数名</label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="incrementalParam" placeholder="since，可空则用字段名" value="' + (data && data.incrementalParam ? data.incrementalParam : '') + '">';
            html += '</div>';
            html += '<div class="col-md-3">';
            html += '<label class="form-label small">分批与定时</label>';
            html += '<select class="form-select form-select-sm step-config" data-field="batchPerRun">';
            html += '<option value="ALL"' + (!data || !data.batchPerRun || data.batchPerRun === 'ALL' ? ' selected' : '') + '>本轮拉完所有批次</option>';
            html += '<option value="ONE"' + (data && data.batchPerRun === 'ONE' ? ' selected' : '') + '>每次只跑一批（定时续跑）</option>';
            html += '</select>';
            html += '</div>';
            html += '<div class="col-md-12"><div class="small text-muted">增量会记住上次最大字段值，下次只取更大的数据；也可在 URL 写 $' + '{lastValue}。全量 + 每次一批会记住页码，下次 Cron 从下一页继续。</div></div>';
            html += '</div></div>';

            // 加载数据源选项
            setTimeout(function() {
                var $step = $('#' + stepId);
                var $select = $step.find('[data-field="dsId"]');
                dataSourceList.forEach(function(ds) {
                    var selected = data && data.dsId == ds.id ? ' selected' : '';
                    $select.append('<option value="' + ds.id + '"' + selected + '>' + ds.name + '</option>');
                });
                if (data && data.dsId) {
                    $select.trigger('change');
                } else if (data && (data.apiUrl || data.tokenUrl)) {
                    var $apiTab = $step.find('.ds-source-tabs .ds-branch-tab[data-branch="api"]');
                    if ($apiTab.length) {
                        switchDsBranch($apiTab[0], 'api');
                    }
                }
            }, 0);
            break;

        case 'MAPPING':
            html += '<div class="col-md-12">';
            html += '<label class="form-label small">选择映射模板 <span class="text-muted">(从数据对接模块导入)</span></label>';
            html += '<select class="form-select form-select-sm" data-field="mappingTemplateId" onchange="onMappingTemplateChange(this)">';
            html += '<option value="">不使用映射模板</option>';
            mappingTemplateList.forEach(function(mt) {
                var selected = data && data.mappingTemplateId == mt.id ? ' selected' : '';
                html += '<option value="' + mt.id + '"' + selected + '>' + mt.name + '</option>';
            });
            html += '</select>';
            html += '</div>';
            html += '<div class="col-md-12 mt-2">';
            html += '<div class="d-flex align-items-center justify-content-between mb-1">';
            html += '<label class="form-label small mb-0">映射规则 <small class="text-muted">(源可选手动输入；嵌套目标如 档号/全宗号)</small></label>';
            html += '<button type="button" class="btn btn-sm btn-outline-primary" onclick="addMappingRuleRow($(this).closest(\'.event-step\'), \'\', \'\', \'\')">';
            html += '<i class="bi bi-plus"></i> 添加映射规则</button>';
            html += '</div>';
            html += '<div class="row g-1 mb-1 small text-muted"><div class="col-md-4">源字段</div><div class="col-md-4">目标字段（嵌套用 /）</div><div class="col-md-3">转换（可选事件）</div></div>';
            html += mappingRuleHelpHtml();
            html += '<div class="mapping-rules-list">';
            html += '<!-- 动态添加的映射规则行 -->';
            html += '</div>';
            html += '</div>';
            // 编辑模式下加载已保存的映射规则行
            setTimeout(function() {
                var $step = $('#' + stepId);
                if (data && data.mappings && Array.isArray(data.mappings)) {
                    data.mappings.forEach(function(m) {
                        addMappingRuleRow($step, m.src || m.source || '', m.dst || m.target || '', m.transform || '', isSavedMappingLiteral(m));
                    });
                }
            }, 0);
            break;

        case 'FILTER':
            html += '<div class="col-md-4">';
            html += '<label class="form-label small">字段名</label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="filterField" placeholder="field_name" value="' + (data && data.filterField ? data.filterField : '') + '">';
            html += '</div>';
            html += '<div class="col-md-4">';
            html += '<label class="form-label small">运算符</label>';
            html += '<select class="form-select form-select-sm step-config" data-field="filterOperator">';
            ['==', '!=', '>', '<', '>=', '<=', 'LIKE', 'IN', 'IS NULL'].forEach(function(op) {
                html += '<option value="' + op + '" ' + (data && data.filterOperator === op ? 'selected' : '') + '>' + op + '</option>';
            });
            html += '</select>';
            html += '</div>';
            html += '<div class="col-md-4">';
            html += '<label class="form-label small">比较值</label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="filterValue" placeholder="value" value="' + (data && data.filterValue ? data.filterValue : '') + '">';
            html += '</div>';
            break;

        case 'CALL_TEMPLATE':
            html += '<div class="col-md-6">';
            html += '<label class="form-label small">选择模板</label>';
            html += '<select class="form-select form-select-sm step-config" data-field="callTemplateId">';
            html += '<option value="">请选择模板</option>';
            html += '</select>';
            html += '</div>';
            html += '<div class="col-md-12 mt-2">';
            html += '<label class="form-label small">传递参数 <small class="text-muted">(键值对，键和值之间用英文冒号)</small></label>';
            html += '<textarea class="form-control form-control-sm step-config font-monospace" data-field="callParams" rows="3" placeholder=\'{"xh":"2024030304"}\'></textarea>';
            html += '<div class="form-text small text-muted">正确示例：<code>{"xh":"2024030304"}</code> 或 <code>{"学号":"$' + '{xh}"}</code>。不要写成 <code>{"xh","2024030304"}</code>。</div>';
            html += '</div>';
            // 加载模板选项，并用 jQuery 回填 JSON，避免 value="{" 截断导致看起来没保存
            setTimeout(function() {
                var $step = $('#' + stepId);
                var $select = $step.find('[data-field="callTemplateId"]');
                templateList.forEach(function(t) {
                    var selected = data && data.callTemplateId == t.id ? ' selected' : '';
                    var label = t.name + (t.builtinCode ? ' · 系统' : '');
                    $select.append('<option value="' + t.id + '"' + selected + '>' + label + '</option>');
                });
                if (editingBuiltin) {
                    $select.prop('disabled', true);
                    var lockedId = $select.val() || (data && data.callTemplateId ? data.callTemplateId : '');
                    $select.after('<input type="hidden" class="step-config" data-field="callTemplateId" value="' + lockedId + '">');
                }
                $step.find('[data-field="callParams"]').val(formatCallParamsForInput(data && data.callParams));
            }, 0);
            break;

        case 'OPERATION':
            // 数据源选择
            html += '<div class="col-md-5">';
            html += '<label class="form-label small">数据源</label>';
            html += '<select class="form-select form-select-sm step-config" data-field="dsId" onchange="onOperationDsChange(this)">';
            html += '<option value="">请选择数据源</option>';
            html += '</select>';
            html += '</div>';
            html += '<div class="col-md-7">';
            html += '<label class="form-label small">类型 <span class="text-muted op-ds-type-label">选择数据源后自动识别</span></label>';
            html += '<input type="hidden" class="step-config" data-field="sourceType" value="' + (data && data.sourceType ? data.sourceType : '') + '">';
            html += '<input type="hidden" class="step-config" data-field="operationType" value="' + (data && data.operationType ? data.operationType : 'DB_QUERY') + '">';
            html += '</div>';

            // 操作类型tabs容器
            html += '<div class="col-md-12 mt-2 op-tabs-container" style="display:none;">';
            // DB tabs
            html += '<div class="op-db-tabs">';
            html += '<div class="ds-branch-tabs mb-2">';
            html += '<span class="ds-branch-tab active" onclick="switchOperationType(this,\'DB_QUERY\')">查询</span>';
            html += '<span class="ds-branch-tab" onclick="switchOperationType(this,\'DB_INSERT\')">新增</span>';
            html += '<span class="ds-branch-tab" onclick="switchOperationType(this,\'DB_UPDATE\')">修改</span>';
            html += '<span class="ds-branch-tab" onclick="switchOperationType(this,\'DB_DELETE\')">删除</span>';
            html += '</div>';

            // DB_QUERY面板
            html += '<div class="op-panel op-panel-DB_QUERY active">';
            html += '<div class="row g-2">';
            html += '<div class="col-md-8">';
            html += '<label class="form-label small">SQL语句 <span class="text-muted">(支持$' + '{param}占位符)</span></label>';
            html += '<textarea class="form-control form-control-sm step-config font-monospace" data-field="sql" rows="3" placeholder="SELECT * FROM table WHERE id = $' + '{id}" style="font-size:0.82rem;">' + (data && data.sql ? data.sql : '') + '</textarea>';
            html += '</div>';
            html += '<div class="col-md-4">';
            html += '<label class="form-label small">&nbsp;</label><br>';
            html += '<button type="button" class="btn btn-sm btn-outline-primary" onclick="autoGenerateSql(this)"><i class="bi bi-magic"></i> 根据输入参数生成条件</button>';
            html += '</div>';
            html += '</div>';
            html += '<div class="advanced-toggle mt-2" onclick="toggleAdvanced(this)"><span>高级配置</span><i class="bi bi-chevron-down"></i></div>';
            html += '<div class="advanced-body">';
            html += '<div class="d-flex justify-content-between align-items-center mb-1">';
            html += '<span class="small text-muted">查询结果预览</span>';
            html += '<button type="button" class="btn btn-sm btn-outline-info" onclick="testQuery(this)"><i class="bi bi-play"></i> 测试查询</button>';
            html += '</div>';
            html += '<div class="result-preview"><table><tr><td class="text-muted">点击"测试查询"查看结果</td></tr></table></div>';
            html += '</div>';
            html += '</div>';

            // DB_INSERT面板
            html += '<div class="op-panel op-panel-DB_INSERT" style="display:none;">';
            html += '<div class="row g-2">';
            html += '<div class="col-md-6">';
            html += '<label class="form-label small">目标表 <span class="text-muted">(可手填新表名)</span></label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="tableName" list="op-table-list-' + stepId + '" placeholder="选择或输入表名，如 mock_openapi_sync" value="' + (data && data.tableName ? data.tableName : '') + '" onchange="loadTableFieldsForOperation(this)">';
            html += '<datalist id="op-table-list-' + stepId + '"></datalist>';
            html += '</div>';
            html += '<div class="col-md-6">';
            html += '<label class="form-label small">返回方式 <span class="text-muted">(下一步还要写另一张表时选「原数据」)</span></label>';
            html += '<select class="form-select form-select-sm step-config" data-field="returnType">';
            html += '<option value="INSERTED_ROW"' + (!data || !data.returnType || data.returnType === 'INSERTED_ROW' ? ' selected' : '') + '>把接口原数据传给下一步</option>';
            html += '<option value="AFFECTED_ROWS"' + (data && data.returnType === 'AFFECTED_ROWS' ? ' selected' : '') + '>只返回写入统计</option>';
            html += '</select>';
            html += '</div>';
            html += '<div class="col-md-12">';
            html += '<label class="small mb-0"><input type="checkbox" data-field="autoCreateTable"' + (data && (data.autoCreateTable === false || data.autoCreateTable === 'false') ? '' : ' checked') + '> 表不存在时自动创建（无字段映射则按接口字段建表并逐行写入）</label>';
            html += '</div>';
            html += '<div class="col-md-4">';
            html += '<label class="form-label small">写入方式</label>';
            html += '<select class="form-select form-select-sm step-config" data-field="writeMode">';
            html += '<option value="INSERT"' + (!data || !data.writeMode || data.writeMode === 'INSERT' ? ' selected' : '') + '>仅新增（填了唯一键则跳过重复）</option>';
            html += '<option value="UPSERT"' + (data && data.writeMode === 'UPSERT' ? ' selected' : '') + '>存在则更新（定时推荐）</option>';
            html += '<option value="OVERWRITE"' + (data && data.writeMode === 'OVERWRITE' ? ' selected' : '') + '>覆盖整表（先清空再写入）</option>';
            html += '</select>';
            html += '</div>';
            html += '<div class="col-md-8">';
            html += '<label class="form-label small">唯一键 <span class="text-muted">(填了以后新增也会按此查重，多个用逗号，须与目标字段名一致)</span></label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="uniqueKeys" placeholder="id" value="' + (data && data.uniqueKeys ? data.uniqueKeys : '') + '">';
            html += '</div>';
            html += '</div>';
            html += '<div class="mt-2">';
            html += '<div class="d-flex align-items-center justify-content-between mb-1">';
            html += '<label class="form-label small mb-0">字段映射 <span class="text-muted">(可留空自动按接口字段写入；目标列可手填，转换可选事件)</span></label>';
            html += '<button type="button" class="btn btn-sm btn-outline-primary" onclick="addFieldValueRow($(this).closest(\'.event-step\').find(\'.field-value-list\'), \'\', \'INPUT_PARAM\', \'\', \'\')">';
            html += '<i class="bi bi-plus"></i> 添加字段</button>';
            html += '</div>';
            html += '<div class="row g-1 mb-1 small text-muted px-1"><div style="width:22%;">目标字段</div><div style="width:16%;">取值</div><div style="width:22%;">源字段/值</div><div style="flex:1;">转换（事件）</div><div style="width:28px;"></div></div>';
            html += '<div class="field-value-list">';
            html += '<!-- 动态添加的字段-值映射行 -->';
            html += '</div>';
            html += '</div>';
            html += '</div>';

            // DB_UPDATE面板
            html += '<div class="op-panel op-panel-DB_UPDATE" style="display:none;">';
            html += '<div class="row g-2">';
            html += '<div class="col-md-6">';
            html += '<label class="form-label small">目标表</label>';
            html += '<select class="form-select form-select-sm step-config" data-field="tableName2" onchange="loadTableFieldsForOperation(this)">';
            html += '<option value="">请先选择数据源</option>';
            if (data && data.tableName) {
                html += '<option value="' + data.tableName + '" selected>' + data.tableName + '</option>';
            }
            html += '</select>';
            html += '</div>';
            html += '</div>';
            html += '<div class="mt-2">';
            html += '<div class="d-flex align-items-center justify-content-between mb-1">';
            html += '<label class="form-label small mb-0">SET 字段 <span class="text-muted">(目标列可手填，转换可选事件)</span></label>';
            html += '<button type="button" class="btn btn-sm btn-outline-primary" onclick="addFieldValueRow($(this).closest(\'.event-step\').find(\'.field-set-list\'), \'\', \'INPUT_PARAM\', \'\', \'\')">';
            html += '<i class="bi bi-plus"></i> 添加SET字段</button>';
            html += '</div>';
            html += '<div class="row g-1 mb-1 small text-muted px-1"><div style="width:22%;">目标字段</div><div style="width:16%;">取值</div><div style="width:22%;">源字段/值</div><div style="flex:1;">转换（事件）</div><div style="width:28px;"></div></div>';
            html += '<div class="field-set-list">';
            html += '<!-- 动态添加的SET字段行 -->';
            html += '</div>';
            html += '</div>';
            html += '<div class="mt-2">';
            html += '<div class="d-flex align-items-center justify-content-between mb-1">';
            html += '<label class="form-label small mb-0">WHERE 条件</label>';
            html += '<button type="button" class="btn btn-sm btn-outline-primary" onclick="addConditionRow($(this).closest(\'.event-step\').find(\'.where-cond-list\'), \'\', \'=\', \'\')">';
            html += '<i class="bi bi-plus"></i> 添加条件</button>';
            html += '</div>';
            html += '<div class="where-cond-list">';
            html += '<!-- 动态添加的WHERE条件行 -->';
            html += '</div>';
            html += '</div>';
            html += '</div>';

            // DB_DELETE面板
            html += '<div class="op-panel op-panel-DB_DELETE" style="display:none;">';
            html += '<div class="row g-2">';
            html += '<div class="col-md-6">';
            html += '<label class="form-label small">目标表</label>';
            html += '<select class="form-select form-select-sm step-config" data-field="tableName3" onchange="loadTableFieldsForOperation(this)">';
            html += '<option value="">请先选择数据源</option>';
            if (data && data.tableName) {
                html += '<option value="' + data.tableName + '" selected>' + data.tableName + '</option>';
            }
            html += '</select>';
            html += '</div>';
            html += '</div>';
            html += '<div class="mt-2">';
            html += '<div class="d-flex align-items-center justify-content-between mb-1">';
            html += '<label class="form-label small mb-0">WHERE 条件 <span class="text-danger">(必须，安全检查)</span></label>';
            html += '<button type="button" class="btn btn-sm btn-outline-primary" onclick="addConditionRow($(this).closest(\'.event-step\').find(\'.where-del-list\'), \'\', \'=\', \'\')">';
            html += '<i class="bi bi-plus"></i> 添加条件</button>';
            html += '</div>';
            html += '<div class="where-del-list">';
            html += '<!-- 动态添加的WHERE条件行 -->';
            html += '</div>';
            html += '</div>';
            html += '<div class="mt-2"><label class="small"><input type="checkbox" checked disabled> 必须包含WHERE条件（安全检查）</label></div>';
            html += '</div>';
            html += '</div>'; // end op-db-tabs

            // API tabs
            html += '<div class="op-api-tabs" style="display:none;">';
            html += '<div class="ds-branch-tabs mb-2">';
            html += '<span class="ds-branch-tab active" onclick="switchOperationType(this,\'API_CALL\')">调用API</span>';
            html += '</div>';

            // API_CALL面板
            html += '<div class="op-panel op-panel-API_CALL active">';
            html += '<div class="row g-2">';
            html += '<div class="col-md-6">';
            html += '<label class="form-label small">接口URL</label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="apiUrl" placeholder="https://api.example.com/data" value="' + (data && data.apiUrl ? data.apiUrl : '') + '">';
            html += '</div>';
            html += '<div class="col-md-3">';
            html += '<label class="form-label small">请求方式</label>';
            html += '<select class="form-select form-select-sm step-config" data-field="apiMethod">';
            html += '<option value="GET"' + (data && data.apiMethod === 'GET' ? ' selected' : '') + '>GET</option>';
            html += '<option value="POST"' + (!data || data.apiMethod === 'POST' ? ' selected' : '') + '>POST</option>';
            html += '<option value="PUT"' + (data && data.apiMethod === 'PUT' ? ' selected' : '') + '>PUT</option>';
            html += '<option value="DELETE"' + (data && data.apiMethod === 'DELETE' ? ' selected' : '') + '>DELETE</option>';
            html += '</select>';
            html += '</div>';
            html += '<div class="col-md-3">';
            html += '<label class="form-label small">超时(秒)</label>';
            html += '<input type="number" class="form-control form-control-sm step-config" data-field="timeout" value="' + (data && data.timeout ? data.timeout : '30') + '">';
            html += '</div>';
            html += '</div>';
            html += '<div class="row g-2 mt-1">';
            html += '<div class="col-md-6">';
            html += '<label class="form-label small">请求头 <small class="text-muted">(JSON)</small></label>';
            html += '<textarea class="form-control form-control-sm step-config" data-field="apiHeaders" rows="2" placeholder=\'{"Content-Type":"application/json"}\'>' + (data && data.apiHeaders ? data.apiHeaders : '') + '</textarea>';
            html += '</div>';
            html += '<div class="col-md-6">';
            html += '<label class="form-label small">请求体模板 <small class="text-muted">(支持$' + '{param})</small></label>';
            html += '<textarea class="form-control form-control-sm step-config" data-field="apiBody" rows="3" placeholder=\'{"name":"$' + '{userName}","age":$' + '{age}}\'>' + (data && data.apiBody ? data.apiBody : '') + '</textarea>';
            html += '</div>';
            html += '</div>';
            html += '<div class="advanced-toggle mt-2" onclick="toggleAdvanced(this)"><span>高级配置</span><i class="bi bi-chevron-down"></i></div>';
            html += '<div class="advanced-body">';
            html += '<div class="d-flex justify-content-between align-items-center mb-1">';
            html += '<span class="small text-muted">响应预览</span>';
            html += '<button type="button" class="btn btn-sm btn-outline-info" onclick="testApiQuery(this)"><i class="bi bi-play"></i> 测试请求</button>';
            html += '</div>';
            html += '<div class="result-preview"><table><tr><td class="text-muted">点击"测试请求"查看结果</td></tr></table></div>';
            html += '</div>';
            html += '</div>';
            html += '</div>'; // end op-api-tabs
            html += '</div>'; // end op-tabs-container

            // 加载数据源选项并恢复配置
            setTimeout(function() {
                var $step = $('#' + stepId);
                var $select = $step.find('[data-field="dsId"]');
                dataSourceList.forEach(function(ds) {
                    var selected = data && data.dsId == ds.id ? ' selected' : '';
                    $select.append('<option value="' + ds.id + '"' + selected + '>' + ds.name + '</option>');
                });
                if (data && data.dsId) {
                    $select.trigger('change');
                }
                // 恢复已保存的字段映射和条件
                if (data && data.fieldMappings) {
                    var $list = $step.find('.field-value-list');
                    data.fieldMappings.forEach(function(fm) {
                        addFieldValueRow($list, fm.field, fm.valueSource, fm.value, fm.transform);
                    });
                }
                if (data && data.whereConditions) {
                    var $wlist = $step.find('.where-cond-list');
                    data.whereConditions.forEach(function(wc) {
                        addConditionRow($wlist, wc.field, wc.operator, wc.value);
                    });
                }
                // 恢复正确的操作类型tab
                if (data && data.sourceType === 'API') {
                    // 显示API tabs
                    $step.find('.op-db-tabs').hide();
                    $step.find('.op-api-tabs').show();
                    var apiOpType = (data && data.operationType) || 'API_CALL';
                    var $apiTab = $step.find('.op-api-tabs .ds-branch-tab').first();
                    if ($apiTab.length > 0) {
                        switchOperationType($apiTab[0], apiOpType);
                    }
                } else if (data && data.operationType && data.operationType !== 'DB_QUERY') {
                    // 切换到保存的操作类型tab
                    var $targetTab = $step.find('.op-db-tabs .ds-branch-tab').filter(function() {
                        return $(this).text().trim().indexOf(
                            data.operationType === 'DB_QUERY' ? '查询' :
                            data.operationType === 'DB_INSERT' ? '新增' :
                            data.operationType === 'DB_UPDATE' ? '修改' :
                            data.operationType === 'DB_DELETE' ? '删除' : ''
                        ) >= 0;
                    });
                    if ($targetTab.length > 0) {
                        switchOperationType($targetTab[0], data.operationType);
                    }
                }
                // 恢复表名
                if (data && data.tableName) {
                    $step.find('[data-field="tableName"]').val(data.tableName);
                    $step.find('[data-field="tableName2"]').val(data.tableName);
                    $step.find('[data-field="tableName3"]').val(data.tableName);
                }
                if (data && data.writeMode) {
                    $step.find('[data-field="writeMode"]').val(data.writeMode);
                }
                if (data && data.uniqueKeys) {
                    $step.find('[data-field="uniqueKeys"]').val(data.uniqueKeys);
                }
                if (data && data.returnType) {
                    $step.find('[data-field="returnType"]').val(data.returnType);
                }
                // 恢复UPDATE的SET字段（fieldMappings 用于UPDATE时也放到 field-set-list）
                if (data && data.operationType === 'DB_UPDATE' && data.fieldMappings) {
                    var $setList = $step.find('.field-set-list');
                    if ($setList.find('.condition-row').length === 0) {
                        data.fieldMappings.forEach(function(fm) {
                            addFieldValueRow($setList, fm.field, fm.valueSource, fm.value, fm.transform);
                        });
                    }
                }
                // 恢复DELETE的WHERE条件
                if (data && data.operationType === 'DB_DELETE' && data.whereConditions) {
                    var $delList = $step.find('.where-del-list');
                    if ($delList.find('.condition-row').length === 0) {
                        data.whereConditions.forEach(function(wc) {
                            addConditionRow($delList, wc.field, wc.operator, wc.value);
                        });
                    }
                }
                // 恢复API字段
                if (data && data.apiUrl) {
                    $step.find('[data-field="apiUrl"]').val(data.apiUrl);
                }
                if (data && data.apiMethod) {
                    $step.find('[data-field="apiMethod"]').val(data.apiMethod);
                }
                if (data && data.apiHeaders) {
                    $step.find('[data-field="apiHeaders"]').val(data.apiHeaders);
                }
                if (data && data.apiBody) {
                    $step.find('[data-field="apiBody"]').val(data.apiBody);
                }
            }, 0);
            break;

        case 'EVENT':
            html += '<div class="col-md-5">';
            html += '<label class="form-label small">选择事件</label>';
            html += '<select class="form-select form-select-sm step-config" data-field="eventCode" onchange="onEventSelect(this)">';
            html += '<option value="">请选择事件</option>';
            html += '</select>';
            html += '</div>';
            html += '<div class="col-md-7">';
            html += '<label class="form-label small">事件描述 <span class="text-muted event-desc-label"></span></label>';
            html += '</div>';
            html += '<div class="col-md-12 mt-2 event-params-container" style="display:none;">';
            html += '<label class="form-label small">事件参数</label>';
            html += '<div class="event-params-list">';
            html += '<!-- 动态渲染的参数输入 -->';
            html += '</div>';
            html += '</div>';
            // 加载事件列表
            setTimeout(function() {
                var $step = $('#' + stepId);
                $.get('/event/api/list', function(res) {
                    if (res.code === 0 && res.data) {
                        var $select = $step.find('[data-field="eventCode"]');
                        res.data.forEach(function(evt) {
                            var selected = data && data.eventCode === evt.code ? ' selected' : '';
                            var schemaJson = JSON.stringify(evt.inputSchema || {});
                            $select.append('<option value="' + evt.code + '"' + selected + ' data-schema=\'' + schemaJson.replace(/'/g, '&#39;') + '\'>' + evt.name + '</option>');
                        });
                        if (data && data.eventCode) {
                            $select.trigger('change');
                            if (data.params) {
                                Object.keys(data.params).forEach(function(k) {
                                    $step.find('.event-param[data-param-name="' + k + '"]').val(data.params[k]);
                                });
                            }
                        }
                    }
                });
            }, 0);
            break;

        case 'THESIS_ARCHIVE':
            html += '<div class="col-md-12">';
            html += '<div class="alert alert-secondary py-2 mb-2 small mb-0">';
            html += '后台写死：下载PDF → MD5 → 元数据.xml → ZIP → 取Token → 上传 file2Archives。<br>';
            html += 'Token 参数从<strong>模板入参</strong>读取（apiUrl / appkey / password / ccode），步骤里可写字面量或 $' + '{参数名}。';
            html += '</div>';
            html += '</div>';
            html += '<div class="col-md-12 mb-1">';
            html += '<button type="button" class="btn btn-sm btn-outline-primary" onclick="ensureArchiveInputParams()"><i class="bi bi-plus-circle"></i> 一键添加归档入参</button>';
            html += '</div>';
            html += '<div class="col-md-12">';
            html += '<label class="form-label small">档案接口 URL</label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="apiUrl" placeholder="$' + '{apiUrl}" value="' + (data && data.apiUrl ? data.apiUrl : ('$' + '{apiUrl}')) + '">';
            html += '</div>';
            html += '<div class="col-md-4">';
            html += '<label class="form-label small">appkey</label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="appkey" placeholder="$' + '{appkey}" value="' + (data && data.appkey ? data.appkey : ('$' + '{appkey}')) + '">';
            html += '</div>';
            html += '<div class="col-md-4">';
            html += '<label class="form-label small">password</label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="password" placeholder="$' + '{password}" value="' + (data && data.password ? data.password : ('$' + '{password}')) + '">';
            html += '</div>';
            html += '<div class="col-md-4">';
            html += '<label class="form-label small">ccode</label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="ccode" placeholder="$' + '{ccode} 或 lwdj" value="' + (data && data.ccode ? data.ccode : ('$' + '{ccode}')) + '">';
            html += '</div>';
            break;

        case 'FILE_DOWNLOAD':
            html += '<div class="col-md-12">';
            html += '<div class="alert alert-secondary py-2 mb-2 small">';
            html += '按行用 URL 模板下载附件。返回 HTML 时自动转 PDF，结果写入 <code>pdfFiles</code>，后面接「论文归档推送」即可。<br>';
            html += '占位符 $' + '{字段名} 优先取当前行（映射后），再取模板入参。例：http://host/Interface/Dag_Sr.aspx?xh=$' + '{学号}';
            html += '</div>';
            html += '</div>';
            html += '<div class="col-md-12">';
            html += '<label class="form-label small">下载 URL 模板</label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="urlTemplate" placeholder="http://host/Interface/Dag_Sr.aspx?xh=$' + '{学号}" value="' + (data && data.urlTemplate ? data.urlTemplate : '') + '">';
            html += '</div>';
            html += '<div class="col-md-6">';
            html += '<label class="form-label small">文件名模板</label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="fileNameTemplate" placeholder="$' + '{学号}_成绩表.pdf" value="' + (data && data.fileNameTemplate ? data.fileNameTemplate : ('$' + '{学号}_成绩表.pdf')) + '">';
            html += '</div>';
            html += '<div class="col-md-6">';
            html += '<label class="form-label small">转换方式</label>';
            html += '<select class="form-select form-select-sm step-config" data-field="convertMode">';
            html += '<option value="AUTO"' + (!data || !data.convertMode || data.convertMode === 'AUTO' ? ' selected' : '') + '>自动（HTML转PDF，已是PDF则原样）</option>';
            html += '<option value="HTML_TO_PDF"' + (data && data.convertMode === 'HTML_TO_PDF' ? ' selected' : '') + '>强制 HTML→PDF</option>';
            html += '<option value="NONE"' + (data && data.convertMode === 'NONE' ? ' selected' : '') + '>不转换</option>';
            html += '</select>';
            html += '</div>';
            html += '<div class="col-md-6">';
            html += '<label class="form-label small">请求头 JSON（可选）</label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="headers" placeholder=\'{"Cookie":"..."}\' value="' + (data && data.headers ? String(data.headers).replace(/"/g, '&quot;') : '') + '">';
            html += '</div>';
            html += '<div class="col-md-6">';
            html += '<label class="form-label small">中文字体路径（可选）</label>';
            html += '<input type="text" class="form-control form-control-sm step-config" data-field="fontPath" placeholder="Linux 可填 wqy / Noto 字体路径" value="' + (data && data.fontPath ? data.fontPath : '') + '">';
            html += '</div>';
            break;
    }

    html += '</div>';
    html += '</div>';
    html += '<div class="step-connector"><i class="bi bi-arrow-down"></i></div>';

    $('#dynamicSteps').append(html);
    updateStepNumbers();
}

// 移除步骤
function removeStep(stepId) {
    $('#' + stepId).next('.step-connector').remove();
    $('#' + stepId).remove();
    updateStepNumbers();
}

// 更新步骤编号
function updateStepNumbers() {
    var $allSteps = $('.event-step');
    $allSteps.each(function(index) {
        $(this).find('.step-number').text(index + 1);
    });
    $('#outputStepNumber').text($allSteps.length);
}

// ============ OPERATION 步骤辅助函数 ============

// 操作事件-数据源选择变更
function onOperationDsChange(el) {
    var $step = $(el).closest('.event-step');
    var dsId = $(el).val();
    if (!dsId) {
        $step.find('.op-tabs-container').hide();
        $step.find('.op-ds-type-label').text('选择数据源后自动识别');
        return;
    }
    // 查找数据源信息
    var dsInfo = {};
    dataSourceList.forEach(function(ds) {
        if (ds.id == dsId) { dsInfo = ds; }
    });
    var dsType = dsInfo.sourceType || '';
    // 更新类型标签和隐藏字段
    var typeLabel = dsType === 'API' ? '接口' : '数据库';
    $step.find('.op-ds-type-label').text(typeLabel);
    $step.find('[data-field="sourceType"]').val(dsType || 'DB');
    // 显示对应的tabs
    $step.find('.op-tabs-container').show();
    if (dsType === 'API') {
        $step.find('.op-db-tabs').hide();
        $step.find('.op-api-tabs').show();
        $step.find('[data-field="operationType"]').val('API_CALL');
        // 自动填充API字段
        if (dsInfo.apiUrl && !$step.find('[data-field="apiUrl"]').val()) {
            $step.find('[data-field="apiUrl"]').val(dsInfo.apiUrl || '');
            $step.find('[data-field="apiMethod"]').val(dsInfo.apiMethod || 'GET');
            $step.find('[data-field="apiHeaders"]').val(dsInfo.apiHeaders || '');
            $step.find('[data-field="apiBody"]').val(dsInfo.apiBody || '');
            $step.find('[data-field="timeout"]').val(dsInfo.apiTimeout || '30');
        }
    } else {
        $step.find('.op-db-tabs').show();
        $step.find('.op-api-tabs').hide();
        $step.find('[data-field="operationType"]').val('DB_QUERY');
        // 优先用数据源已配置的表名，否则从数据库加载
        var configuredTables = resolveTableList(dsInfo.tableNames || '', dsInfo.tableName || '');
        if (configuredTables.length > 0) {
            populateOpTableSelects($step, configuredTables);
        } else {
            loadOpTables(dsId, $step);
        }
    }
}

// 解析数据源配置的表名列表（支持中英文逗号、空格、换行分隔 + tableName 单表）
function resolveTableList(tableNames, tableName) {
    var tables = [];
    if (tableNames) {
        // 统一替换中文逗号为英文逗号后分割
        tableNames.replace(/，/g, ',').replace(/\n/g, ',').split(',').forEach(function(t) {
            var trimmed = t.trim();
            if (trimmed && tables.indexOf(trimmed) < 0) tables.push(trimmed);
        });
    }
    if (tableName && tableName.trim() && tables.indexOf(tableName.trim()) < 0) {
        tables.push(tableName.trim());
    }
    return tables;
}

// 填充操作事件的表选择下拉框
function populateOpTableSelects($step, tables) {
    $step.find('[data-field="tableName"], [data-field="tableName2"], [data-field="tableName3"]').each(function() {
        var $el = $(this);
        var curVal = $el.val();
        if ($el.is('select')) {
            var opts = '<option value="">请选择表</option>';
            tables.forEach(function(t) {
                opts += '<option value="' + t + '">' + t + '</option>';
            });
            if (curVal && tables.indexOf(curVal) < 0) {
                opts += '<option value="' + curVal + '" selected>' + curVal + '</option>';
            }
            $el.html(opts);
            if (curVal) $el.val(curVal);
        } else {
            var listId = $el.attr('list');
            if (listId) {
                var $dl = $step.find('#' + listId);
                if ($dl.length) {
                    var opts = '';
                    tables.forEach(function(t) {
                        opts += '<option value="' + t + '">';
                    });
                    $dl.html(opts);
                }
            }
            if (!curVal && tables.length === 1) {
                $el.val(tables[0]);
            }
        }
    });
}

// 切换操作类型tab
function switchOperationType(el, opType) {
    var $step = $(el).closest('.event-step');
    // 更新tab active状态
    $(el).closest('.ds-branch-tabs').find('.ds-branch-tab').removeClass('active');
    $(el).addClass('active');
    // 切换面板
    $step.find('.op-panel').hide().removeClass('active');
    $step.find('.op-panel-' + opType).show().addClass('active');
    // 更新隐藏字段
    $step.find('[data-field="operationType"]').val(opType);
}

// 加载操作事件的表列表（从数据库实时查询，作为没有配置表名时的兜底）
function loadOpTables(dsId, $step) {
    $.get('/datasource/api/getTables', {id: dsId}, function(res) {
        if (res.code === 0 && res.data && res.data.success) {
            var tables = res.data.tables || [];
            var tableNames = tables.map(function(t) { return t.name; });
            populateOpTableSelects($step, tableNames);
        }
    });
}

// 选择表后加载字段
function loadTableFieldsForOperation(el) {
    var $step = $(el).closest('.event-step');
    var dsId = $step.find('[data-field="dsId"]').val();
    var tableName = $(el).val();
    if (!dsId || !tableName) return;
    $.get('/datasource/api/getColumns', {id: dsId, tableName: tableName}, function(res) {
        if (res.code === 0 && res.data && res.data.success) {
            var cols = res.data.columns || [];
            // 缓存字段供后续使用
            $step.data('tableColumns', cols.map(function(c) { return c.name; }));
            $step.find('.fv-field').each(function() {
                var listId = $(this).attr('list');
                if (listId) {
                    $step.find('#' + listId).html(buildDatalistOptions($step.data('tableColumns'), $(this).val()));
                }
            });
            $step.find('.cond-field').each(function() {
                var listId = $(this).attr('list');
                if (listId) {
                    $step.find('#' + listId).html(buildDatalistOptions($step.data('tableColumns'), $(this).val()));
                }
            });
        }
    });
}

// 添加字段-值映射行 (INSERT/UPDATE用)：目标字段可手填，转换可选事件
var fieldValueRowCounter = 0;
function addFieldValueRow($container, field, valueSource, value, transform) {
    fieldValueRowCounter++;
    var $step = $container.closest('.event-step');
    var tableCols = ($step.length > 0 && $step.data('tableColumns')) ? $step.data('tableColumns') : [];
    var srcCols = getSourceFieldSuggestions();
    var fieldListId = 'fv-field-list-' + fieldValueRowCounter;
    var srcListId = 'fv-src-list-' + fieldValueRowCounter;
    var html = '<div class="condition-row fv-map-row" data-idx="' + fieldValueRowCounter + '">';
    html += '<div style="width:22%;">';
    html += '<input type="text" class="form-control form-control-sm fv-field" list="' + fieldListId + '" placeholder="目标字段，可手填" value="' + (field || '') + '">';
    html += '<datalist id="' + fieldListId + '">' + buildDatalistOptions(tableCols, field) + '</datalist>';
    html += '</div>';
    html += '<div style="width:16%;"><select class="form-select form-select-sm fv-source" onchange="onFvSourceChange(this)">';
    html += '<option value="INPUT_PARAM"' + (!valueSource || valueSource === 'INPUT_PARAM' ? ' selected' : '') + '>输入字段</option>';
    html += '<option value="FIXED_VALUE"' + (valueSource === 'FIXED_VALUE' ? ' selected' : '') + '>固定值</option>';
    html += '<option value="AUTO"' + (valueSource === 'AUTO' ? ' selected' : '') + '>自动</option>';
    html += '</select></div>';
    html += '<div class="fv-value-wrap" style="width:22%;">' + buildFvValueHtml(valueSource, value, srcListId, srcCols) + '</div>';
    html += '<div style="flex:1;"><select class="form-select form-select-sm fv-transform">' + buildMappingTransformOptions(transform || '') + '</select></div>';
    html += '<div><button type="button" class="btn btn-sm btn-outline-danger btn-remove-cond" onclick="$(this).closest(\'.condition-row\').remove()"><i class="bi bi-x"></i></button></div>';
    html += '</div>';
    $container.append(html);
}

function getSourceFieldSuggestions() {
    var cols = window._cachedDsColumns ? window._cachedDsColumns.slice() : [];
    try {
        getInputParams().forEach(function(p) {
            if (p && p.name && cols.indexOf(p.name) < 0) cols.push(p.name);
        });
    } catch (e) {}
    return cols;
}

function normalizeAutoValue(value) {
    var v = String(value || 'NOW').trim().toUpperCase().replace('()', '');
    if (v === 'CURRENT_TIMESTAMP' || v === 'SYSDATE' || v === 'GETDATE' || v === '') return 'NOW';
    if (v === 'GUID' || v === 'NEWID') return 'UUID';
    if (v === 'AUTO' || v === 'AUTO_INCREMENT' || v === 'DEFAULT') return 'SEQ';
    if (v === 'NOW' || v === 'UUID' || v === 'SEQ') return v;
    return 'NOW';
}

function buildFvValueHtml(valueSource, value, srcListId, srcCols) {
    if (valueSource === 'AUTO') {
        var cur = normalizeAutoValue(value);
        var html = '<select class="form-select form-select-sm fv-value">';
        html += '<option value="NOW"' + (cur === 'NOW' ? ' selected' : '') + '>当前时间</option>';
        html += '<option value="UUID"' + (cur === 'UUID' ? ' selected' : '') + '>UUID</option>';
        html += '<option value="SEQ"' + (cur === 'SEQ' ? ' selected' : '') + '>自增序号</option>';
        html += '</select>';
        return html;
    }
    var cols = srcCols && srcCols.length ? srcCols : getSourceFieldSuggestions();
    var ph = valueSource === 'FIXED_VALUE' ? '固定值' : '源字段名，可手填';
    var html = '<input type="text" class="form-control form-control-sm fv-value" list="' + srcListId + '" placeholder="' + ph + '" value="' + (value || '') + '">';
    html += '<datalist id="' + srcListId + '">' + buildDatalistOptions(cols, value) + '</datalist>';
    return html;
}

function onFvSourceChange(el) {
    var $row = $(el).closest('.fv-map-row');
    var src = $(el).val();
    var $wrap = $row.find('.fv-value-wrap');
    var oldVal = $wrap.find('.fv-value').val();
    var srcListId = 'fv-src-list-' + $row.data('idx');
    var nextVal = src === 'AUTO' ? 'NOW' : (oldVal === 'NOW' || oldVal === 'UUID' || oldVal === 'SEQ' ? '' : oldVal);
    $wrap.html(buildFvValueHtml(src, nextVal, srcListId, getSourceFieldSuggestions()));
}

function buildDatalistOptions(cols, extra) {
    var html = '';
    var seen = {};
    (cols || []).forEach(function(col) {
        if (!col || seen[col]) return;
        seen[col] = true;
        html += '<option value="' + String(col).replace(/"/g, '&quot;') + '">';
    });
    if (extra && !seen[extra]) {
        html += '<option value="' + String(extra).replace(/"/g, '&quot;') + '">';
    }
    return html;
}

// 添加WHERE条件行 (UPDATE/DELETE用)
var conditionRowCounter = 0;
function addConditionRow($container, field, operator, value) {
    conditionRowCounter++;
    var columns = [];
    var $step = $container.closest('.event-step');
    if ($step.length > 0) {
        columns = $step.data('tableColumns') || [];
    }
    var fieldHtml = '<input type="text" class="form-control form-control-sm cond-field" list="cond-field-list-' + conditionRowCounter + '" placeholder="字段名，可手填" value="' + (field || '') + '">';
    fieldHtml += '<datalist id="cond-field-list-' + conditionRowCounter + '">' + buildDatalistOptions(columns, field) + '</datalist>';
    var html = '<div class="condition-row" data-idx="' + conditionRowCounter + '">';
    html += '<div style="width:35%;">' + fieldHtml + '</div>';
    html += '<div style="width:20%;"><select class="form-select form-select-sm cond-op">';
    ['=', '!=', '>', '<', '>=', '<=', 'LIKE', 'IN', 'IS NULL', 'IS NOT NULL'].forEach(function(op) {
        html += '<option value="' + op + '"' + (operator === op ? ' selected' : '') + '>' + op + '</option>';
    });
    html += '</select></div>';
    html += '<div style="flex:1;"><input type="text" class="form-control form-control-sm cond-value" placeholder="值/参数" value="' + (value || '') + '"></div>';
    html += '<div><button type="button" class="btn btn-sm btn-outline-danger btn-remove-cond" onclick="$(this).closest(\'.condition-row\').remove()"><i class="bi bi-x"></i></button></div>';
    html += '</div>';
    $container.append(html);
}

// 收集操作事件配置
function getOperationConfig($step) {
    var config = {};
    config.dsId = parseInt($step.find('[data-field="dsId"]').val()) || 0;
    config.sourceType = $step.find('[data-field="sourceType"]').val() || 'DB';
    config.operationType = $step.find('[data-field="operationType"]').val() || 'DB_QUERY';
    config.sql = $step.find('[data-field="sql"]').val() || '';

    var tableName = $step.find('[data-field="tableName"]').val() || $step.find('[data-field="tableName2"]').val() || $step.find('[data-field="tableName3"]').val() || '';
    config.tableName = tableName;

    config.returnType = $step.find('[data-field="returnType"]').val() || 'INSERTED_ROW';
    config.autoCreateTable = $step.find('[data-field="autoCreateTable"]').is(':checked');
    config.writeMode = $step.find('[data-field="writeMode"]').val() || 'INSERT';
    config.uniqueKeys = $step.find('[data-field="uniqueKeys"]').val() || '';

    // 收集字段映射
    config.fieldMappings = [];
    $step.find('.field-value-list .condition-row, .field-set-list .condition-row').each(function() {
        var field = $(this).find('.fv-field').val();
        var vs = $(this).find('.fv-source').val();
        var val = $(this).find('.fv-value').val();
        if (field) {
            var item = { field: field, valueSource: vs, value: val };
            var transform = $(this).find('.fv-transform').val();
            if (transform) item.transform = transform;
            config.fieldMappings.push(item);
        }
    });

    // 收集WHERE条件
    config.whereConditions = [];
    $step.find('.where-cond-list .condition-row, .where-del-list .condition-row').each(function() {
        var field = $(this).find('.cond-field').val();
        var op = $(this).find('.cond-op').val();
        var val = $(this).find('.cond-value').val();
        if (field) {
            config.whereConditions.push({ field: field, operator: op, value: val });
        }
    });

    // API字段
    config.apiUrl = $step.find('[data-field="apiUrl"]').val() || '';
    config.apiMethod = $step.find('[data-field="apiMethod"]').val() || 'GET';
    config.apiHeaders = $step.find('[data-field="apiHeaders"]').val() || '';
    config.apiBody = $step.find('[data-field="apiBody"]').val() || '';
    config.timeout = parseInt($step.find('[data-field="timeout"]').val()) || 30;

    return config;
}

// 事件选择变更
function onEventSelect(el) {
    var $step = $(el).closest('.event-step');
    var $opt = $(el).find('option:selected');
    var schemaStr = $opt.data('schema');
    if (!schemaStr) {
        $step.find('.event-params-container').hide();
        return;
    }
    var schema;
    try {
        schema = typeof schemaStr === 'string' ? JSON.parse(schemaStr) : schemaStr;
    } catch (e) {
        schema = {};
    }
    $step.find('.event-desc-label').text(schema.description || '');
    $step.find('.event-params-container').show();
    var html = '';
    if (schema.fields && schema.fields.length > 0) {
        schema.fields.forEach(function(f) {
            var defVal = f.defaultValue != null ? f.defaultValue : (f['default'] != null ? f['default'] : '');
            html += '<div class="row g-1 mb-1">';
            html += '<div class="col-md-4"><label class="form-label small">' + (f.label || f.name) + (f.required ? ' <span class="text-danger">*</span>' : '') + '</label></div>';
            html += '<div class="col-md-8"><input type="text" class="form-control form-control-sm event-param" data-param-name="' + f.name + '" placeholder="' + (f.description || f.label || '') + '" value="' + String(defVal).replace(/"/g, '&quot;') + '"></div>';
            html += '</div>';
        });
    }
    $step.find('.event-params-list').html(html);
}

function formatCallParamsForInput(raw) {
    if (raw == null || raw === '') return '';
    if (typeof raw === 'string') {
        var s = raw.trim();
        if (!s || s === '{}') return s === '{}' ? '{}' : s;
        try {
            return JSON.stringify(JSON.parse(s), null, 2);
        } catch (e) {
            return raw;
        }
    }
    try {
        return JSON.stringify(raw, null, 2);
    } catch (e) {
        return '';
    }
}

function parseCallParamsForSave(raw) {
    if (raw == null) return {};
    if (typeof raw === 'object' && !Array.isArray(raw)) return raw;
    var s = String(raw).trim();
    if (!s) return {};
    var parsed = tryParseCallParamsObject(s);
    if (parsed) return parsed;
    throw new Error('传递参数格式不对。键和值之间要用英文冒号，例如 {"xh":"2024030304"}');
}

function tryParseCallParamsObject(s) {
    var parsed = parseJsonObject(s);
    if (parsed) return parsed;
    var normalized = s
        .replace(/[“”]/g, '"')
        .replace(/[‘’]/g, "'")
        .replace(/：/g, ':')
        .replace(/，/g, ',')
        .trim();
    parsed = parseJsonObject(normalized);
    if (parsed) return parsed;
    parsed = parseJsonObject(normalized.replace(/'/g, '"'));
    if (parsed) return parsed;
    return parseCommaSeparatedPairs(normalized);
}

function parseJsonObject(s) {
    try {
        var parsed = JSON.parse(s);
        if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
            return parsed;
        }
    } catch (e) {}
    return null;
}

function parseCommaSeparatedPairs(s) {
    if (s.charAt(0) !== '{' || s.charAt(s.length - 1) !== '}') return null;
    var inner = s.slice(1, -1).trim();
    if (!inner || inner.indexOf(':') >= 0) return null;
    var parts = [];
    var re = /"([^"]*)"|'([^']*)'|([^,\s]+)/g;
    var m;
    while ((m = re.exec(inner)) !== null) {
        parts.push(m[1] != null ? m[1] : (m[2] != null ? m[2] : m[3]));
    }
    if (parts.length < 2 || parts.length % 2 !== 0) return null;
    var obj = {};
    for (var i = 0; i < parts.length; i += 2) {
        obj[parts[i]] = parts[i + 1];
    }
    return obj;
}

// 获取所有步骤配置
function getStepsConfig() {
    var config = {
        input: {},
        steps: [],
        output: {}
    };

    // 获取输入配置
    var $inputStep = $('[data-step-type="INPUT"]');
    config.input.inputType = $inputStep.find('[data-field="inputType"]').val();
    config.input.cronExpr = $inputStep.find('[data-field="cronExpr"]').val();

    // 获取动态步骤
    $('#dynamicSteps .event-step').each(function() {
        var $step = $(this);
        var type = $step.data('step-type');
        var stepConfig = { type: type };

        $step.find('.step-config').each(function() {
            var field = $(this).data('field');
            var value = $(this).val();
            stepConfig[field] = value;
        });

        // MAPPING 类型：从行UI收集映射规则
        if (type === 'MAPPING') {
            stepConfig.mappings = getMappingRules($step);
        }
        // OPERATION 类型：使用专门的配置收集函数
        if (type === 'OPERATION') {
            stepConfig = getOperationConfig($step);
            stepConfig.type = 'OPERATION';
        }
        // EVENT 类型：收集事件参数
        if (type === 'EVENT') {
            stepConfig.eventCode = $step.find('[data-field="eventCode"]').val() || '';
            stepConfig.params = {};
            $step.find('.event-param').each(function() {
                var paramName = $(this).data('param-name');
                stepConfig.params[paramName] = $(this).val();
            });
        }
        if (type === 'CALL_TEMPLATE') {
            if (stepConfig.callTemplateId) {
                stepConfig.callTemplateId = parseInt(stepConfig.callTemplateId, 10) || 0;
            }
            stepConfig.callParams = parseCallParamsForSave(stepConfig.callParams);
        }
        if (type === 'DATA_SOURCE') {
            var $active = $step.find('.ds-branch-panel.active');
            if ($active.length) {
                $active.find('.step-config').each(function() {
                    var field = $(this).data('field');
                    if (field) {
                        stepConfig[field] = $(this).val();
                    }
                });
            }
            var bs = parseInt(stepConfig.batchSize, 10);
            stepConfig.batchSize = isNaN(bs) ? 100 : bs;
            var mb = parseInt(stepConfig.maxBatches, 10);
            stepConfig.maxBatches = isNaN(mb) ? 0 : mb;
            if (stepConfig.apiPageStart != null && stepConfig.apiPageStart !== '') {
                var ps = parseInt(stepConfig.apiPageStart, 10);
                stepConfig.apiPageStart = isNaN(ps) ? 1 : ps;
            }
        }

        config.steps.push(stepConfig);
    });

    // 获取固定映射步骤配置（如果存在）
    var $fixedMapping = $('#fixedMappingStep');
    if (!editingBuiltin && $fixedMapping.length > 0 && $fixedMapping.is(':visible')) {
        var mappingConfig = { type: 'MAPPING', isFixed: true };
        $fixedMapping.find('.step-config').each(function() {
            var field = $(this).data('field');
            var value = $(this).val();
            mappingConfig[field] = value;
        });
        // 从行UI收集映射规则
        mappingConfig.mappings = getMappingRules($fixedMapping);
        config.mapping = mappingConfig;
    }

    // 获取输出配置
    var $outputStep = $('[data-step-type="OUTPUT"]');
    var outputMode = $('input[name="outputMode"]:checked').val() || 'RETURN';
    config.output.outputMode = outputMode;
    config.output.outputTarget = outputMode; // 向后兼容
    config.output.outputDsId = parseInt($outputStep.find('[data-field="outputDsId"]').val()) || 0;
    config.output.outputTable = $outputStep.find('[data-field="outputTable"]').val();

    // CALL_TEMPLATE 模式
    if (outputMode === 'CALL_TEMPLATE') {
        config.output.callTemplateId = parseInt($('[data-field="outputCallTemplateId"]').val()) || 0;
        config.output.passMode = $('[data-field="outputPassMode"]').val() || 'PACKET';
        config.output.batchSize = parseInt($('[data-field="outputBatchSize"]').val()) || 100;
        config.output.timeout = parseInt($('[data-field="outputTimeout"]').val()) || 60;
        config.output.onError = $('[data-field="outputOnError"]').val() || 'STOP';
        config.output.retryCount = parseInt($('[data-field="outputRetryCount"]').val()) || 3;
    }

    // FILE 模式
    if (outputMode === 'FILE') {
        config.output.filePath = $('[data-field="outputFilePath"]').val() || '';
        config.output.fileFormat = $('[data-field="outputFileFormat"]').val() || 'JSON';
        config.output.fileOptions = {
            pretty: $('[data-field="fileOptionPretty"]').is(':checked'),
            includeHeader: $('[data-field="fileOptionHeader"]').is(':checked'),
            delimiter: $('[data-field="fileOptionDelimiter"]').val() || ',',
            encoding: $('[data-field="fileOptionEncoding"]').val() || 'UTF-8'
        };
        config.output.writeMode = $('[data-field="outputWriteMode"]').val() || 'OVERWRITE';
    }

    return config;
}

// 保存模板
function saveTemplate() {
    var name = $('#editName').val().trim();
    var description = $('#editDescription').val();

    if (!name) {
        showWarning('请输入模板名称');
        return;
    }

    var stepsConfig;
    try {
        stepsConfig = getStepsConfig();
    } catch (e) {
        showWarning(e.message || '步骤配置有误，请检查传递参数 JSON');
        return;
    }

    // 收集输入参数和输出参数
    var inputParams = getInputParams();
    var outputParams = getOutputParams();

    var data = {
        id: $('#editId').val() || null,
        name: name,
        description: description,
        eventType: 'CUSTOM',
        eventConfig: JSON.stringify(stepsConfig),
        inputParams: JSON.stringify(inputParams),
        outputParams: JSON.stringify(outputParams)
    };

    $.ajax({
        url: '/visual/api/save',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(data),
        success: function(res) {
            if (res.code === 0) {
                showSuccess('保存成功');
                $('#editorModal').modal('hide');
                setTimeout(function() { location.reload(); }, 500);
            } else {
                showError(res.message || '保存失败');
            }
        },
        error: function() {
            showError('请求失败');
        }
    });
}

// 执行模板：日志弹窗可随时关闭，任务继续在后台跑
var _runPollTimer = null;
var _liveRun = {
    runId: null,
    templateId: null,
    name: '',
    status: 'IDLE',
    lastCount: 0,
    logs: [],
    rowCount: 0,
    errorMessage: '',
    autoScroll: true,
    startedAt: 0
};

function executeTemplate(id, name) {
    if (_runPollTimer) {
        clearTimeout(_runPollTimer);
        _runPollTimer = null;
    }
    _liveRun.runId = null;
    _liveRun.templateId = id;
    _liveRun.name = name || '';
    _liveRun.status = 'RUNNING';
    _liveRun.lastCount = 0;
    _liveRun.logs = [];
    _liveRun.rowCount = 0;
    _liveRun.errorMessage = '';
    _liveRun.startedAt = Date.now();
    $('#templateRunTitle').text(name ? ('- ' + name) : '');
    $('#templateRunSummary').text('正在启动...');
    $('#templateRunHint').text('关闭后任务继续在后台执行，可从右下角重新打开');
    $('#templateRunConsole').html('<div class="INFO">正在启动...</div>');
    showTemplateRunModal();
    updateRunWidget();
    $.ajax({
        url: '/visual/api/execute-async/' + id,
        type: 'POST',
        contentType: 'application/json',
        data: '{}',
        success: function(res) {
            if (res.code !== 0 || !res.data || !res.data.runId) {
                _liveRun.status = 'FAILED';
                _liveRun.errorMessage = res.message || '启动失败';
                $('#templateRunConsole').append('<div class="ERROR">启动失败: ' + escapeRunLog(res.message || '') + '</div>');
                $('#templateRunSummary').text('启动失败');
                showError(res.message || '启动失败');
                updateRunWidget();
                return;
            }
            _liveRun.runId = res.data.runId;
            pollTemplateRun(res.data.runId, name);
        },
        error: function() {
            _liveRun.status = 'FAILED';
            _liveRun.errorMessage = '请求失败';
            showError('请求失败');
            $('#templateRunSummary').text('请求失败');
            updateRunWidget();
        }
    });
}

function escapeRunLog(text) {
    return String(text == null ? '' : text)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function renderTemplateRunLogs(logs) {
    var html = '';
    (logs || []).forEach(function(line) {
        var level = line.level || 'INFO';
        html += '<div class="' + level + '">[' + escapeRunLog(line.time || '') + '] [' + escapeRunLog(level) + '] '
            + escapeRunLog(line.message || '') + '</div>';
    });
    var $c = $('#templateRunConsole');
    var el = $c[0];
    var stickBottom = !el || (el.scrollHeight - el.scrollTop - el.clientHeight < 40);
    $c.html(html || '<div class="INFO">等待日志...</div>');
    if (el && stickBottom) el.scrollTop = el.scrollHeight;
}

function showTemplateRunModal() {
    var modalEl = document.getElementById('templateRunModal');
    if (modalEl && window.bootstrap && bootstrap.Modal) {
        bootstrap.Modal.getOrCreateInstance(modalEl).show();
    } else {
        $('#templateRunModal').modal('show');
    }
}

function openLiveRunLogs() {
    if (!_liveRun.runId && _liveRun.status === 'IDLE') {
        showInfo('当前没有运行中的任务');
        return;
    }
    renderTemplateRunLogs(_liveRun.logs);
    updateRunSummary();
    showTemplateRunModal();
}

function dismissRunWidget() {
    $('#runStatusWidget').removeClass('show');
}

function updateRunSummary() {
    var status = _liveRun.status;
    var count = (_liveRun.logs || []).length;
    if (status === 'RUNNING') {
        $('#templateRunSummary').text('运行中 · 已输出 ' + count + ' 条日志（关闭弹窗不影响执行）');
        $('#templateRunHint').text('关闭后任务继续在后台执行，可从右下角重新打开');
    } else if (status === 'SUCCESS') {
        $('#templateRunSummary').text('运行结束 · 成功 · ' + count + ' 条日志 · 返回 ' + (_liveRun.rowCount || 0) + ' 行');
        $('#templateRunHint').text('任务已结束，可关闭此窗口');
    } else if (status === 'FAILED') {
        $('#templateRunSummary').text('运行失败 · ' + count + ' 条日志' + (_liveRun.errorMessage ? ' · ' + _liveRun.errorMessage : ''));
        $('#templateRunHint').text('任务已结束，可关闭此窗口');
    }
}

function updateRunWidget() {
    var $w = $('#runStatusWidget');
    var modalOpen = $('#templateRunModal').hasClass('show');
    if (_liveRun.status === 'IDLE' || modalOpen) {
        $w.removeClass('show');
        return;
    }
    var name = _liveRun.name || '模板';
    var count = (_liveRun.logs || []).length;
    if (_liveRun.status === 'RUNNING') {
        $('#runWidgetTitle').html('<span class="spinner-border spinner-border-sm me-1"></span> 正在执行 ' + escapeRunLog(name));
        $('#runWidgetMeta').text(count + ' 条日志 · 点击可重新打开');
        $('#runWidgetDismiss').hide();
    } else if (_liveRun.status === 'SUCCESS') {
        $('#runWidgetTitle').text('执行成功 · ' + name);
        $('#runWidgetMeta').text('返回 ' + (_liveRun.rowCount || 0) + ' 行 · ' + count + ' 条日志');
        $('#runWidgetDismiss').show();
    } else {
        $('#runWidgetTitle').text('执行失败 · ' + name);
        $('#runWidgetMeta').text(_liveRun.errorMessage || (count + ' 条日志'));
        $('#runWidgetDismiss').show();
    }
    $w.addClass('show');
}

function copyTemplateRunLogs() {
    var text = $('#templateRunConsole').text() || '';
    if (!text || text === '等待开始...' || text === '等待日志...') {
        showInfo('暂无日志可复制');
        return;
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(function() {
            showSuccess('日志已复制');
        }).catch(function() {
            showError('复制失败');
        });
    } else {
        showError('当前浏览器不支持复制');
    }
}

function pollTemplateRun(runId, name) {
    var pollOnce = function() {
        if (_liveRun.runId !== runId) {
            return;
        }
        $.ajax({
            url: '/visual/api/execute-status/' + runId,
            type: 'GET',
            success: function(res) {
                if (_liveRun.runId !== runId) {
                    return;
                }
                if (res.code !== 0 || !res.data) {
                    _runPollTimer = setTimeout(pollOnce, 400);
                    return;
                }
                var data = res.data;
                var logs = data.logs || [];
                _liveRun.logs = logs;
                if (logs.length !== _liveRun.lastCount || data.status === 'SUCCESS' || data.status === 'FAILED') {
                    renderTemplateRunLogs(logs);
                    _liveRun.lastCount = logs.length;
                }
                if (data.status === 'SUCCESS' || data.status === 'FAILED') {
                    _runPollTimer = null;
                    var ok = data.status === 'SUCCESS';
                    _liveRun.status = ok ? 'SUCCESS' : 'FAILED';
                    _liveRun.rowCount = data.rowCount || 0;
                    _liveRun.errorMessage = data.errorMessage || '';
                    updateRunSummary();
                    updateRunWidget();
                    if (ok) {
                        if (data.download && data.download.content) {
                            var blob = new Blob([data.download.content], {type: (data.download.contentType || 'application/octet-stream') + ';charset=utf-8'});
                            var a = document.createElement('a');
                            a.href = URL.createObjectURL(blob);
                            a.download = data.download.fileName || 'export.json';
                            document.body.appendChild(a); a.click(); document.body.removeChild(a);
                            showSuccess('下载完成: ' + a.download);
                        } else {
                            showSuccess('执行成功，返回 ' + (data.rowCount || 0) + ' 行');
                        }
                    } else {
                        showError(data.errorMessage || '执行失败');
                    }
                    return;
                }
                updateRunSummary();
                updateRunWidget();
                _runPollTimer = setTimeout(pollOnce, 400);
            },
            error: function() {
                if (_liveRun.runId !== runId) {
                    return;
                }
                _liveRun.status = 'FAILED';
                _liveRun.errorMessage = '获取运行状态失败';
                $('#templateRunConsole').append('<div class="ERROR">轮询运行状态失败，请稍后重试</div>');
                showError('获取运行状态失败');
                updateRunSummary();
                updateRunWidget();
            }
        });
    };
    pollOnce();
}

$('#templateRunModal').on('hidden.bs.modal', function() {
    updateRunWidget();
});
$('#templateRunModal').on('shown.bs.modal', function() {
    updateRunWidget();
    var el = document.getElementById('templateRunConsole');
    if (el) el.scrollTop = el.scrollHeight;
});

var _currentLogTemplateId = 0;
function openTemplateLogs(id, name, currentLog) {
    _currentLogTemplateId = id;
    $('#templateLogTitle').text(name ? ('- ' + name) : '');
    $('#templateLogConsole').text('加载中...');
    $('#templateLogList').empty();
    $('#templateLogModal').modal('show');
    if (currentLog) {
        renderTemplateLogDetail(currentLog);
    }
    $.get('/visual/api/exec-logs/' + id, function(res) {
        if (res.code !== 0 || !res.data || !res.data.length) {
            if (!currentLog) {
                $('#templateLogConsole').text('暂无运行记录');
                $('#templateLogSummary').text('执行一次模板后会在这里显示开始/结束时间和每步耗时');
            }
            return;
        }
        var html = '';
        res.data.forEach(function(item, idx) {
            var ok = item.success === true || item.success === 'true';
            html += '<a class="list-group-item list-group-item-action' + (idx === 0 && !currentLog ? ' active' : '') + '" data-file="' + item.file + '">';
            html += '<span class="badge ' + (ok ? 'bg-success' : 'bg-danger') + ' me-1">' + (ok ? '成功' : '失败') + '</span>';
            html += (item.startTime || item.file) + ' → ' + (item.endTime || '');
            html += ' <span class="text-muted">(' + (item.durationMs || 0) + 'ms, ' + (item.rowCount || 0) + '行)</span>';
            html += '</a>';
        });
        $('#templateLogList').html(html);
        $('#templateLogList .list-group-item').on('click', function() {
            $('#templateLogList .list-group-item').removeClass('active');
            $(this).addClass('active');
            loadTemplateLogFile($(this).data('file'));
        });
        if (!currentLog) {
            loadTemplateLogFile(res.data[0].file);
        }
    });
}

function loadTemplateLogFile(filename) {
    if (!_currentLogTemplateId || !filename) return;
    $('#templateLogConsole').text('加载中...');
    $.get('/visual/api/exec-logs/' + _currentLogTemplateId + '/' + encodeURIComponent(filename), function(res) {
        if (res.code === 0 && res.data) {
            renderTemplateLogDetail(res.data);
        } else {
            $('#templateLogConsole').text(res.message || '读取失败');
        }
    });
}

function renderTemplateLogDetail(rec) {
    var summary = '开始 ' + (rec.startTime || '-') + '，结束 ' + (rec.endTime || '-') + '，耗时 ' + (rec.durationMs || 0) + 'ms，返回 ' + (rec.rowCount || 0) + ' 行';
    if (rec.errorMessage) summary += '；错误: ' + rec.errorMessage;
    $('#templateLogSummary').text(summary);
    var logs = rec.logs || [];
    if (!logs.length) {
        $('#templateLogConsole').text('无步骤日志');
        return;
    }
    var html = '';
    logs.forEach(function(line) {
        var level = line.level || 'INFO';
        html += '<div class="' + level + '">[' + escapeRunLog(line.time || '') + '] [' + escapeRunLog(level) + '] ' + escapeRunLog(line.message || '') + '</div>';
    });
    $('#templateLogConsole').html(html);
}

// 删除模板
function deleteTemplate(id) {
    if (confirm('确定要删除此模板吗？')) {
        $.post('/visual/api/delete/' + id, function(res) {
            if (res.code === 0) {
                showSuccess('删除成功');
                setTimeout(function() { location.reload(); }, 500);
            } else {
                showError(res.message || '删除失败');
            }
        });
    }
}
</script>

</@main>
