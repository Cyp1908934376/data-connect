<#include "../layouts/main.ftl">
<@main title="对接流程向导" activeMenu="flow">

<div class="container-fluid">
    <!-- 步骤进度条 -->
    <div class="step-progress mb-4">
        <div class="step-item active" id="stepItem1">
            <div class="step-circle">1</div>
            <span class="step-label">选择数据源</span>
        </div>
        <div class="step-item" id="stepItem2">
            <div class="step-circle">2</div>
            <span class="step-label">配置管道</span>
        </div>
        <div class="step-item" id="stepItem3">
            <div class="step-circle">3</div>
            <span class="step-label">同步策略</span>
        </div>
        <div class="step-item" id="stepItem4">
            <div class="step-circle">4</div>
            <span class="step-label">执行对接</span>
        </div>
        <div class="step-item" id="stepItem5">
            <div class="step-circle">5</div>
            <span class="step-label">查看结果</span>
        </div>
    </div>

    <form method="post" action="/flow/save" id="flowForm">
        <input type="hidden" name="id" value="${(flowConfig.id)!''}">
        <input type="hidden" name="inputDsId" id="inputDsId" value="${(flowConfig.inputDsId)!''}">
        <input type="hidden" name="outputDsId" id="outputDsId" value="${(flowConfig.outputDsId)!''}">
        <input type="hidden" name="preTemplateId" id="preTemplateId" value="${(flowConfig.preTemplateId)!''}">
        <input type="hidden" name="mappingTemplateId" id="mappingTemplateId" value="${(flowConfig.mappingTemplateId)!''}">
        <input type="hidden" name="postTemplateId" id="postTemplateId" value="${(flowConfig.postTemplateId)!''}">
        <input type="hidden" name="templateParams" id="templateParams" value="${(flowConfig.templateParams)!''}">
        <input type="hidden" name="syncStrategy" id="syncStrategy" value="${(flowConfig.syncStrategy)!'FULL'}">
        <input type="hidden" name="incrementalColumn" id="incrementalColumnVal" value="${(flowConfig.incrementalColumn)!''}">
        <input type="hidden" name="incrementalColumnType" id="incrementalColumnTypeVal" value="${(flowConfig.incrementalColumnType)!'DATETIME'}">
        <input type="hidden" name="name" value="${(flowConfig.name)!''}">
        <input type="hidden" name="description" value="${(flowConfig.description)!''}">

        <!-- 步骤1: 选择数据源 -->
        <div class="step-panel" id="step1">
            <h5 class="mb-3">第一步: 选择输入和输出数据源</h5>
            <div class="row">
                <div class="col-md-5">
                    <div class="card">
                        <div class="card-header bg-primary text-white">输入数据源</div>
                        <div class="card-body">
                            <input type="text" class="form-control form-control-sm mb-2" placeholder="搜索数据源..." id="dsSearchInput">
                            <div style="max-height:400px;overflow-y:auto;">
                                <#if allSources??>
                                    <#list allSources as ds>
                                        <div class="card ds-card mb-2" data-ds-id="${ds.id}" data-ds-name="${ds.name}" data-role="input"
                                             onclick="selectDs(this, 'input')">
                                            <div class="card-body py-2 px-3">
                                                <small><strong>${ds.name}</strong></small>
                                                <span class="badge bg-<#if ds.sourceType == 'DB'>info<#else>warning</#if> float-end">
                                                    <#if ds.sourceType == 'DB'>${(ds.dbType)!'DB'}<#else>${(ds.apiType)!'API'}</#if>
                                                </span>
                                                <br><small class="text-muted">
                                                    <#if ds.sourceType == 'DB'>${(ds.host)!''}:${(ds.port)!''}<#else>${(ds.apiUrl)!''}</#if>
                                                </small>
                                            </div>
                                        </div>
                                    </#list>
                                </#if>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="col-md-2 text-center d-flex align-items-center justify-content-center">
                    <button type="button" class="btn btn-outline-secondary btn-lg" onclick="swapDs()" title="交换输入输出">
                        <i class="bi bi-arrow-left-right"></i>
                    </button>
                </div>
                <div class="col-md-5">
                    <div class="card">
                        <div class="card-header bg-success text-white">输出数据源</div>
                        <div class="card-body" id="outputDsContainer">
                            <#if allSources??>
                                <#list allSources as ds>
                                    <div class="card ds-card mb-2" data-ds-id="${ds.id}" data-ds-name="${ds.name}" data-role="output"
                                         onclick="selectDs(this, 'output')">
                                        <div class="card-body py-2 px-3">
                                            <small><strong>${ds.name}</strong></small>
                                            <span class="badge bg-<#if ds.sourceType == 'DB'>info<#else>warning</#if> float-end">
                                                <#if ds.sourceType == 'DB'>${(ds.dbType)!'DB'}<#else>${(ds.apiType)!'API'}</#if>
                                            </span>
                                        </div>
                                    </div>
                                </#list>
                            </#if>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- 步骤3: 配置同步策略 -->
        <div class="step-panel" id="step3" style="display:none;">
            <h5 class="mb-3">第三步: 配置同步策略</h5>
            <p class="text-muted small mb-3">选择数据同步方式，全量或增量更新。增量更新将基于上次执行的水位线仅同步新增/变更数据。</p>

            <div class="card mb-3">
                <div class="card-body">
                    <div class="mb-3">
                        <label class="form-label fw-bold">同步方式</label>
                        <div class="btn-group w-100" role="group" id="syncStrategyGroup">
                            <input type="radio" class="btn-check" name="syncStrategyRadio" id="strategyFull"
                                   value="FULL" autocomplete="off" checked>
                            <label class="btn btn-outline-primary" for="strategyFull">
                                <strong><i class="bi bi-arrow-repeat"></i> 全量同步</strong><br>
                                <small class="text-muted">每次同步所有数据，覆盖已有记录</small>
                            </label>
                            <input type="radio" class="btn-check" name="syncStrategyRadio" id="strategyTime"
                                   value="INCREMENTAL_TIME" autocomplete="off">
                            <label class="btn btn-outline-primary" for="strategyTime">
                                <strong><i class="bi bi-clock"></i> 按时间增量</strong><br>
                                <small class="text-muted">基于时间字段，仅同步上次执行后变化的数据</small>
                            </label>
                            <input type="radio" class="btn-check" name="syncStrategyRadio" id="strategyId"
                                   value="INCREMENTAL_ID" autocomplete="off">
                            <label class="btn btn-outline-primary" for="strategyId">
                                <strong><i class="bi bi-hash"></i> 按ID增量</strong><br>
                                <small class="text-muted">基于自增ID字段，仅同步大于上次最大ID的数据</small>
                            </label>
                        </div>
                    </div>

                    <div id="incrementalConfig" style="display:none;">
                        <hr>
                        <p class="text-muted small mb-2">增量字段配置：指定输入数据源中用于判断增量的字段。</p>
                        <div class="row">
                            <div class="col-md-8 mb-3">
                                <label class="form-label">字段名 <span class="text-danger">*</span></label>
                                <input type="text" class="form-control" id="incrementalColumn"
                                       placeholder="例如: update_time, id" value="${(flowConfig.incrementalColumn)!''}">
                                <small class="text-muted">输入数据源表中用于判断新/旧数据的字段</small>
                            </div>
                            <div class="col-md-4 mb-3">
                                <label class="form-label">字段类型</label>
                                <select class="form-select" id="incrementalColumnType">
                                    <option value="DATETIME" <#if ((flowConfig.incrementalColumnType)!'DATETIME') == 'DATETIME'>selected</#if>>日期时间</option>
                                    <option value="NUMERIC" <#if ((flowConfig.incrementalColumnType)!'') == 'NUMERIC'>selected</#if>>数值</option>
                                </select>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- 步骤2: 配置处理管道 -->
        <div class="step-panel" id="step2" style="display:none;">
            <h5 class="mb-2">第二步: 配置处理管道</h5>
            <p class="text-muted small mb-3">定义数据处理阶段，每个阶段包含多个模板步骤。步骤按顺序执行，上一步的输出是下一步的输入。阶段按 <b>读取后 → 写入前 → 写入后</b> 的顺序执行。</p>

            <div id="pipelineStages"></div>

            <div class="text-center mt-3">
                <button type="button" class="btn btn-outline-primary btn-sm" onclick="addStage()">
                    + 添加处理阶段
                </button>
            </div>

            <input type="hidden" name="pipelineConfig" id="pipelineConfig" value="">
        </div>

        <!-- 步骤4: 执行对接 -->
        <div class="step-panel" id="step4" style="display:none;">
            <h5 class="mb-3">第四步: 执行数据对接</h5>
            <div class="mb-3">
                <label class="form-label">流程名称</label>
                <input type="text" class="form-control" id="flowName" value="${(flowConfig.name)!''}" placeholder="为本次流程命名(保存时需要)">
            </div>
            <div class="mb-3">
                <label class="form-label">流程描述</label>
                <textarea class="form-control" id="flowDesc" rows="2" placeholder="选填">${(flowConfig.description)!''}</textarea>
            </div>
            <div class="d-grid gap-2 d-md-flex">
                <button type="button" class="btn btn-primary btn-lg" id="btnExecute" onclick="executeFlow()">
                    <i class="bi bi-play-fill"></i> 开始执行
                </button>
                <button type="button" class="btn btn-outline-secondary" id="btnSave" onclick="saveFlow()">
                    <i class="bi bi-save"></i> 保存流程
                </button>
            </div>
            <div class="console-log mt-3" id="consoleLog" style="display:none;max-height:300px;"></div>
        </div>

        <!-- 步骤5: 查看结果 -->
        <div class="step-panel" id="step5" style="display:none;">
            <h5 class="mb-3">第五步: 执行结果</h5>
            <div class="row mb-4" id="resultCards"></div>
            <div id="resultDetail"></div>
            <div class="mt-3">
                <a href="/flow/list" class="btn btn-outline-primary">返回流程列表</a>
                <button type="button" class="btn btn-primary" onclick="goToStep(1)"><i class="bi bi-arrow-repeat"></i> 重新执行</button>
            </div>
        </div>
    </form>

    <!-- 底部导航按钮 -->
    <div class="d-flex justify-content-between mt-4" id="stepButtons">
        <button type="button" class="btn btn-outline-secondary" id="btnPrev" onclick="prevStep()" style="display:none;">
            <i class="bi bi-chevron-left"></i> 上一步
        </button>
        <button type="button" class="btn btn-primary ms-auto" id="btnNext" onclick="nextStep()">
            下一步 <i class="bi bi-chevron-right"></i>
        </button>
    </div>
</div>

<!-- 模板/映射选择器弹窗 -->
<div class="modal fade" id="selectorModal" tabindex="-1">
    <div class="modal-dialog modal-lg modal-dialog-scrollable">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="selectorModalTitle">选择模板</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
            </div>
            <div class="modal-body">
                <div class="input-group input-group-sm mb-3">
                    <span class="input-group-text"><i class="bi bi-search"></i></span>
                    <input type="text" class="form-control" placeholder="输入关键字搜索..." id="selectorSearch" autocomplete="off">
                </div>
                <div class="row" id="selectorCardList" style="max-height:380px;overflow-y:auto;"></div>
            </div>
            <div class="modal-footer">
                <small class="text-muted me-auto" id="selectorHint">请点击卡片选择一项</small>
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">取消</button>
                <button type="button" class="btn btn-primary" id="btnConfirmSelect" disabled onclick="confirmSelect()">确定添加</button>
            </div>
        </div>
    </div>
</div>

<script>
var currentStep = 1;
var selectedInputDsId = ${(flowConfig.inputDsId)!'null'};
var selectedOutputDsId = ${(flowConfig.outputDsId)!'null'};
// 旧字段保留向后兼容
var selectedPreTemplateId = ${(flowConfig.preTemplateId)!'null'};
var selectedMappingTemplateId = ${(flowConfig.mappingTemplateId)!'null'};
var selectedPostTemplateId = ${(flowConfig.postTemplateId)!'null'};

// 模板数据（供管道构建器使用）
var templateList = [
    <#if templates??>
        <#list templates as t>
            {id: ${t.id}, name: "${t.name?js_string}", type: "${(t.type)!'CUSTOM'}", version: ${(t.version)!'1'}, tags: "${(t.tags)!''?js_string}"}<#sep>,</#sep>
        </#list>
    </#if>
];
var mappingTemplateList = [
    <#if mappingTemplates??>
        <#list mappingTemplates as mt>
            {id: ${mt.id}, name: "${mt.name?js_string}", description: "${(mt.description)!''?js_string}"}<#sep>,</#sep>
        </#list>
    </#if>
];

// 管道配置（数组）
var pipelineStages = [];

// 初始化管道配置
var pipelineConfigJson = '${(pipelineConfigJson)!'[]'}';
if (pipelineConfigJson && pipelineConfigJson !== '' && pipelineConfigJson !== '[]') {
    try { pipelineStages = JSON.parse(pipelineConfigJson); } catch(e) {}
} else {
    // 从旧字段合成（向后兼容）
    var afterReadStage = null;
    if (selectedPreTemplateId) {
        afterReadStage = {position: 'AFTER_READ', name: '前置处理', steps: [{type: 'TEMPLATE', templateId: selectedPreTemplateId, params: {}}]};
        pipelineStages.push(afterReadStage);
    }
    if (selectedMappingTemplateId) {
        if (!afterReadStage) {
            afterReadStage = {position: 'AFTER_READ', name: '数据对接', steps: []};
            pipelineStages.push(afterReadStage);
        }
        afterReadStage.steps.push({type: 'MAPPING', mappingTemplateId: selectedMappingTemplateId});
    }
    if (selectedPostTemplateId) {
        pipelineStages.push({position: 'BEFORE_WRITE', name: '后置处理', steps: [{type: 'TEMPLATE', templateId: selectedPostTemplateId, params: {}}]});
    }
}

<#if flowConfig.inputDsId??>selectedInputDsId = ${flowConfig.inputDsId};</#if>
<#if flowConfig.outputDsId??>selectedOutputDsId = ${flowConfig.outputDsId};</#if>

// ---- 管道管理函数 ----

function renderPipeline() {
    var $container = $('#pipelineStages');
    if (!pipelineStages.length) {
        $container.html('<div class="text-center text-muted py-4 border rounded">暂无处理阶段，点击下方按钮添加</div>');
        $('#pipelineConfig').val('[]');
        return;
    }
    var html = '';
    for (var si = 0; si < pipelineStages.length; si++) {
        var stage = pipelineStages[si];
        var posLabel = stage.position === 'AFTER_READ' ? '读取后' : stage.position === 'BEFORE_WRITE' ? '写入前' : '写入后';
        var posColor = stage.position === 'AFTER_READ' ? 'primary' : stage.position === 'BEFORE_WRITE' ? 'warning' : 'success';
        html += '<div class="card mb-3 border-' + posColor + '">';
        html += '<div class="card-header py-2 d-flex align-items-center justify-content-between">';
        html += '<div class="d-flex align-items-center">';
        html += '<span class="badge bg-' + posColor + ' me-2">' + posLabel + '</span>';
        html += '<input type="text" class="form-control form-control-sm me-2" style="width:160px;" value="' + escHtml(stage.name||'') + '" onchange="pipelineStages[' + si + '].name=this.value;syncPipeline()" placeholder="阶段名称">';
        html += '<select class="form-select form-select-sm" style="width:120px;" onchange="changePosition(' + si + ', this.value)">';
        html += '<option value="AFTER_READ"' + (stage.position==='AFTER_READ'?' selected':'') + '>读取后</option>';
        html += '<option value="BEFORE_WRITE"' + (stage.position==='BEFORE_WRITE'?' selected':'') + '>写入前</option>';
        html += '<option value="AFTER_WRITE"' + (stage.position==='AFTER_WRITE'?' selected':'') + '>写入后</option>';
        html += '</select></div>';
        html += '<div><button type="button" class="btn btn-sm btn-outline-secondary me-1" onclick="moveStage(' + si + ', -1)" ' + (si===0?'disabled':'') + '>↑</button>';
        html += '<button type="button" class="btn btn-sm btn-outline-secondary me-1" onclick="moveStage(' + si + ', 1)" ' + (si===pipelineStages.length-1?'disabled':'') + '>↓</button>';
        html += '<button type="button" class="btn btn-sm btn-outline-danger" onclick="removeStage(' + si + ')">×</button></div>';
        html += '</div>';
        html += '<div class="card-body py-2">';
        if (stage.steps && stage.steps.length > 0) {
            html += '<table class="table table-sm table-borderless mb-1"><tbody>';
            for (var sti = 0; sti < stage.steps.length; sti++) {
                var step = stage.steps[sti];
                var stepTypeLabel = step.type === 'TEMPLATE' ? '模板' : '映射';
                var stepTypeColor = step.type === 'TEMPLATE' ? 'info' : 'warning';
                var stepName = '';
                if (step.type === 'TEMPLATE') {
                    var t = findTemplate(step.templateId);
                    stepName = t ? t.name : ('模板#' + (step.templateId||'?'));
                } else {
                    var m = findMapping(step.mappingTemplateId);
                    stepName = m ? m.name : ('映射#' + (step.mappingTemplateId||'?'));
                }
                html += '<tr><td style="width:30px;"><span class="badge bg-' + stepTypeColor + '">' + (sti+1) + '</span></td>';
                html += '<td><span class="badge bg-light text-dark me-1">' + stepTypeLabel + '</span>' + escHtml(stepName) + '</td>';
                html += '<td class="text-end"><button type="button" class="btn btn-sm btn-outline-secondary me-1" onclick="moveStep(' + si + ',' + sti + ',-1)" ' + (sti===0?'disabled':'') + '>↑</button>';
                html += '<button type="button" class="btn btn-sm btn-outline-secondary me-1" onclick="moveStep(' + si + ',' + sti + ',1)" ' + (sti===stage.steps.length-1?'disabled':'') + '>↓</button>';
                html += '<button type="button" class="btn btn-sm btn-outline-danger" onclick="removeStep(' + si + ',' + sti + ')">×</button></td></tr>';
            }
            html += '</tbody></table>';
        } else {
            html += '<div class="text-muted small py-1">暂无步骤</div>';
        }
        html += '<div><button type="button" class="btn btn-sm btn-outline-info me-1" onclick="addStep(' + si + ',\'TEMPLATE\')">+ 添加模板</button>';
        html += '<button type="button" class="btn btn-sm btn-outline-warning" onclick="addStep(' + si + ',\'MAPPING\')">+ 添加映射</button></div>';
        html += '</div></div>';
    }
    $container.html(html);
    syncPipeline();
}

function findTemplate(id) { for (var i=0;i<templateList.length;i++){if(templateList[i].id===id)return templateList[i];} return null; }
function findMapping(id) { for (var i=0;i<mappingTemplateList.length;i++){if(mappingTemplateList[i].id===id)return mappingTemplateList[i];} return null; }
function escHtml(s) { return (s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }

function addStage() {
    pipelineStages.push({position: 'AFTER_READ', name: '新阶段', steps: []});
    renderPipeline();
}

function removeStage(si) {
    if (confirm('确定删除该阶段及其所有步骤？')) {
        pipelineStages.splice(si, 1);
        renderPipeline();
    }
}

function moveStage(si, dir) {
    var newIdx = si + dir;
    if (newIdx < 0 || newIdx >= pipelineStages.length) return;
    var tmp = pipelineStages[si];
    pipelineStages[si] = pipelineStages[newIdx];
    pipelineStages[newIdx] = tmp;
    renderPipeline();
}

function changePosition(si, val) {
    pipelineStages[si].position = val;
    renderPipeline();
}

function addStep(si, type) {
    showSelector(si, type);
}

// ---- 模板/映射选择器弹窗 ----
var pendingStageIndex = -1;
var pendingStepType = '';
var selectedItemId = null;

function showSelector(si, type) {
    var list = type === 'TEMPLATE' ? templateList : mappingTemplateList;
    if (list.length === 0) {
        if (type === 'TEMPLATE') showWarning('暂无可用模板，请先在模板管理中创建');
        else showWarning('暂无可用映射模板，请先在数据对接模板管理中创建');
        return;
    }
    pendingStageIndex = si;
    pendingStepType = type;
    selectedItemId = null;
    $('#btnConfirmSelect').prop('disabled', true);
    $('#selectorModalTitle').text(type === 'TEMPLATE' ? '选择模板' : '选择映射模板');
    renderSelectorCards(list, type);
    $('#selectorSearch').val('').trigger('input');
    $('#selectorModal').modal('show');
}

function renderSelectorCards(list, type) {
    var html = '';
    for (var i = 0; i < list.length; i++) {
        var item = list[i];
        html += '<div class="col-md-6 mb-2 selector-item" data-filter="' + escHtml(item.name).toLowerCase() + '">';
        html += '<div class="card selector-card h-100" data-item-id="' + item.id + '" onclick="selectItem(this, ' + item.id + ')" style="user-select:none;">';
        html += '<div class="card-body py-2 px-3">';
        html += '<div class="d-flex justify-content-between align-items-start">';
        html += '<strong class="small">' + escHtml(item.name) + '</strong>';
        if (type === 'TEMPLATE') {
            html += '<span class="badge bg-info ms-1">v' + (item.version||1) + '</span>';
        }
        html += '</div>';
        if (type === 'TEMPLATE' && item.tags) {
            html += '<small class="text-muted">' + escHtml(item.tags) + '</small>';
        }
        if (type === 'MAPPING' && item.description) {
            html += '<br><small class="text-muted">' + escHtml(item.description) + '</small>';
        }
        html += '</div></div></div>';
    }
    $('#selectorCardList').html(html);
    $('#selectorHint').text('请点击卡片选择一项');
}

function selectItem(el, id) {
    $('.selector-card').removeClass('selected');
    $(el).addClass('selected');
    selectedItemId = id;
    $('#btnConfirmSelect').prop('disabled', false);
    $('#selectorHint').text('已选择: ' + $(el).find('strong').text());
}

function confirmSelect() {
    if (!selectedItemId) return;
    if (pendingStepType === 'TEMPLATE') {
        pipelineStages[pendingStageIndex].steps.push({type: 'TEMPLATE', templateId: selectedItemId, params: {}});
    } else {
        pipelineStages[pendingStageIndex].steps.push({type: 'MAPPING', mappingTemplateId: selectedItemId});
    }
    $('#selectorModal').modal('hide');
    renderPipeline();
}

// 搜索过滤
$(document).on('input', '#selectorSearch', function() {
    var filter = $(this).val().toLowerCase();
    $('#selectorCardList .selector-item').each(function() {
        $(this).toggle($(this).data('filter').indexOf(filter) >= 0);
    });
});

function removeStep(si, sti) {
    pipelineStages[si].steps.splice(sti, 1);
    renderPipeline();
}

function moveStep(si, sti, dir) {
    var steps = pipelineStages[si].steps;
    var newIdx = sti + dir;
    if (newIdx < 0 || newIdx >= steps.length) return;
    var tmp = steps[sti];
    steps[sti] = steps[newIdx];
    steps[newIdx] = tmp;
    renderPipeline();
}

function syncPipeline() {
    $('#pipelineConfig').val(JSON.stringify(pipelineStages));
}

// ---- 保留原有功能 ----

function showStep(n) {
    $('.step-panel').hide();
    $('#step' + n).show();
    $('.step-item').removeClass('active done');
    for (var i = 1; i <= 5; i++) {
        if (i < n) $('#stepItem' + i).addClass('done');
        if (i == n) $('#stepItem' + i).addClass('active');
    }
    $('#btnPrev').toggle(n > 1);
    $('#btnNext').toggle(n < 5);
    currentStep = n;
    if (n === 2) renderPipeline();
}

// ---- Sync strategy management ----
function syncIncrementalConfig() {
    var strategy = $('input[name=syncStrategyRadio]:checked').val();
    $('#syncStrategy').val(strategy);
    $('#incrementalColumnVal').val($('#incrementalColumn').val());
    $('#incrementalColumnTypeVal').val($('#incrementalColumnType').val());
}

$('input[name=syncStrategyRadio]').on('change', function() {
    var strategy = $(this).val();
    $('#incrementalConfig').toggle(strategy !== 'FULL');
    $('#syncStrategy').val(strategy);
});

function selectDs(el, role) {
    $('[data-role='+role+']').removeClass('selected');
    $(el).addClass('selected');
    var id = $(el).data('ds-id');
    if (role === 'input') {
        selectedInputDsId = id;
        $('#inputDsId').val(id);
    } else {
        selectedOutputDsId = id;
        $('#outputDsId').val(id);
    }
}

function swapDs() {
    var tmp = selectedInputDsId;
    selectedInputDsId = selectedOutputDsId;
    selectedOutputDsId = tmp;
    $('#inputDsId').val(selectedInputDsId||'');
    $('#outputDsId').val(selectedOutputDsId||'');
    var inp = $('[data-role=input].selected');
    var out = $('[data-role=output].selected');
    $('[data-role=input]').removeClass('selected');
    $('[data-role=output]').removeClass('selected');
    if (inp.length) $('[data-role=output][data-ds-id='+inp.data('ds-id')+']').addClass('selected');
    if (out.length) $('[data-role=input][data-ds-id='+out.data('ds-id')+']').addClass('selected');
}

function nextStep() {
    if (currentStep === 1 && (!selectedInputDsId || !selectedOutputDsId)) {
        showWarning('请选择输入和输出数据源');
        return;
    }
    showStep(currentStep + 1);
}

function prevStep() { showStep(currentStep - 1); }

function goToStep(n) { showStep(n); }

function saveFlow() {
    var name = $('#flowName').val();
    if (!name) { showWarning('请输入流程名称'); return; }
    $('input[name=name]').val(name);
    $('input[name=description]').val($('#flowDesc').val());
    syncPipeline();
    syncIncrementalConfig();
    $('#flowForm').submit();
}

function executeFlow() {
    if (!selectedInputDsId || !selectedOutputDsId) {
        showWarning('请先选择输入和输出数据源');
        return;
    }
    var name = $('#flowName').val();
    if (!name) { showWarning('请输入流程名称'); return; }
    $('input[name=name]').val(name);
    $('input[name=description]').val($('#flowDesc').val());
    syncPipeline();
    syncIncrementalConfig();

    var formData = $('#flowForm').serialize();
    var loading = showLoading('正在保存流程...', '请稍候');

    $.post('/flow/api/save', formData, function(saveRes) {
        if (saveRes.code !== 0 || !saveRes.data || !saveRes.data.id) {
            loading.close();
            showError(saveRes.message || '未知错误', { title: '保存流程失败' });
            return;
        }
        var flowConfigId = saveRes.data.id;
        loading.update('正在执行数据对接...', '数据量较大时可能需要较长时间，请耐心等待');

        $('#consoleLog').show().html('<span class="log-info">正在执行...</span>');
        $.post('/flow/api/execute', { flowConfigId: flowConfigId }, function(res) {
            loading.close();
            if (res.code === 0) {
                showResults(res.data);
                showStep(5);
            } else {
                showError(res.message || '未知错误', { title: '执行失败' });
            }
        }).fail(function(xhr) {
            loading.close();
            var msg = '请求失败';
            try { var err = JSON.parse(xhr.responseText); msg = err.message || msg; } catch(e) {}
            showError(msg, { title: '执行异常' });
        });
    }).fail(function(xhr) {
        loading.close();
        var msg = '请求失败';
        try { var err = JSON.parse(xhr.responseText); msg = err.message || msg; } catch(e) {}
        showError(msg, { title: '保存流程失败' });
    });
}

function showResults(data) {
    var html = '<div class="col-md-3"><div class="card text-center"><div class="card-body"><h3>'+(data.totalCount||0)+'</h3><p>总数</p></div></div></div>';
    html += '<div class="col-md-3"><div class="card text-center"><div class="card-body"><h3 class="text-success">'+(data.successCount||0)+'</h3><p>成功</p></div></div></div>';
    html += '<div class="col-md-3"><div class="card text-center"><div class="card-body"><h3 class="text-danger">'+(data.failCount||0)+'</h3><p>失败</p></div></div></div>';
    html += '<div class="col-md-3"><div class="card text-center"><div class="card-body"><h3>'+(data.duration||0)+'ms</h3><p>耗时</p></div></div></div>';
    $('#resultCards').html(html);

    if (data.logs && data.logs.length > 0) {
        var logHtml = '<div class="console-log">' + data.logs.map(function(l) {
            var cls = l.indexOf('[ERROR]') >= 0 ? 'log-error' : l.indexOf('[WARN]') >= 0 ? 'log-warn' : 'log-info';
            return '<span class="'+cls+'">'+l+'</span>';
        }).join('<br>') + '</div>';
        $('#resultDetail').html('<h6>执行日志</h6>' + logHtml);
    }
}

// Init
showStep(1);
<#if flowConfig.id??>
// Edit mode: pre-select data sources
<#if flowConfig.inputDsId??>
selectedInputDsId = ${flowConfig.inputDsId}; $('#inputDsId').val(${flowConfig.inputDsId});
$(function() {
    $('[data-role=input][data-ds-id=${flowConfig.inputDsId}]').addClass('selected');
});
</#if>
<#if flowConfig.outputDsId??>
selectedOutputDsId = ${flowConfig.outputDsId}; $('#outputDsId').val(${flowConfig.outputDsId});
$(function() {
    $('[data-role=output][data-ds-id=${flowConfig.outputDsId}]').addClass('selected');
});
</#if>
// Edit mode: pre-select sync strategy
$(function() {
    var savedStrategy = '${(flowConfig.syncStrategy)!'FULL'}';
    if (savedStrategy && savedStrategy !== 'FULL') {
        var radio = $('input[name=syncStrategyRadio][value=' + savedStrategy + ']');
        if (radio.length) {
            radio.prop('checked', true);
            $('#incrementalConfig').show();
        }
    }
    $('#syncStrategy').val(savedStrategy);
    $('#incrementalColumn').val('${(flowConfig.incrementalColumn)!''?js_string}');
    $('#incrementalColumnType').val('${(flowConfig.incrementalColumnType)!'DATETIME'}');
});
</#if>
</script>

</@main>
