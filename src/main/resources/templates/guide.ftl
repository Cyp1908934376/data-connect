<#include "layouts/main.ftl">
<@main title="数据对接服务 - 新手引导" activeMenu="guide">

<div class="container-fluid" style="max-width: 900px;">

    <!-- 标题区 -->
    <div class="text-center mb-5">
        <h3 class="fw-bold">欢迎使用数据对接服务</h3>
        <p class="text-muted">零环境依赖，开箱即用的数据集成平台。跟着下面的步骤，快速完成你的第一个数据对接任务。</p>
    </div>

    <!-- 总体流程图 -->
    <div class="card mb-5 border-0 shadow-sm">
        <div class="card-body text-center py-4">
            <div class="d-flex justify-content-center align-items-center flex-wrap gap-2" style="font-size: 14px;">
                <span class="badge bg-primary px-3 py-2">① 添加数据源</span>
                <span class="text-muted">→</span>
                <span class="badge bg-success px-3 py-2">② 创建模板</span>
                <span class="text-muted">→</span>
                <span class="badge bg-info px-3 py-2">③ 配置映射</span>
                <span class="text-muted">→</span>
                <span class="badge bg-warning px-3 py-2">④ 建立流程</span>
                <span class="text-muted">→</span>
                <span class="badge bg-secondary px-3 py-2">⑤ 定时调度</span>
            </div>
        </div>
    </div>

    <!-- 步骤 1：添加数据源 -->
    <div class="card mb-4 border-start border-primary border-4 shadow-sm">
        <div class="card-body">
            <h5 class="card-title text-primary mb-3">
                <span class="badge bg-primary me-2">1</span>添加数据源
            </h5>
            <p class="text-muted">数据源是数据对接的起点和终点。支持两种类型：</p>
            <div class="row g-3 mt-2">
                <div class="col-md-6">
                    <div class="border rounded p-3 bg-light">
                        <h6><i class="bi bi-database"></i> 数据库数据源</h6>
                        <ul class="mb-0 small text-muted">
                            <li>支持 MySQL、PostgreSQL、Oracle、SQL Server 等 20+ 数据库</li>
                            <li>配置连接信息后即可浏览表结构、执行 SQL 调试</li>
                            <li>连接池自动管理，即配即用</li>
                        </ul>
                    </div>
                </div>
                <div class="col-md-6">
                    <div class="border rounded p-3 bg-light">
                        <h6><i class="bi bi-cloud-arrow-down"></i> 接口数据源</h6>
                        <ul class="mb-0 small text-muted">
                            <li>支持 HTTP/HTTPS API 调用</li>
                            <li>三种模式：单次调用、链式调用、Groovy 脚本编排</li>
                            <li>支持 Header/Body 动态配置和变量替换</li>
                        </ul>
                    </div>
                </div>
            </div>
            <div class="mt-3">
                <a href="/datasource/list" class="btn btn-outline-primary btn-sm"><i class="bi bi-arrow-right"></i> 去添加数据源</a>
            </div>
        </div>
    </div>

    <!-- 步骤 2：创建模板 -->
    <div class="card mb-4 border-start border-success border-4 shadow-sm">
        <div class="card-body">
            <h5 class="card-title text-success mb-3">
                <span class="badge bg-success me-2">2</span>创建转换模板
            </h5>
            <p class="text-muted">模板是数据转换的核心，使用 Groovy 脚本定义数据处理逻辑。</p>
            <div class="border rounded p-3 bg-light">
                <pre class="mb-0" style="font-size: 13px; background: #f8f9fa;"><code><span style="color: #6c757d;">// 示例：字段转换模板</span>
<span style="color: #0d6efd;">def</span> input = binding.input     <span style="color: #6c757d;">// 输入数据（自动注入）</span>
<span style="color: #0d6efd;">def</span> params = binding.params   <span style="color: #6c757d;">// 模板参数（界面配置）</span>
<span style="color: #0d6efd;">def</span> out = [:]                <span style="color: #6c757d;">// 输出数据</span>

out.fullName = input.firstName + <span style="color: #198754;">' '</span> + input.lastName
out.age = input.age + params.offset
out.processedAt = <span style="color: #0d6efd;">new</span> Date().format(<span style="color: #198754;">'yyyy-MM-dd HH:mm:ss'</span>)</code></pre>
            </div>
            <ul class="mt-2 small text-muted">
                <li>树形分类管理，支持版本历史与回滚</li>
                <li>内置 CodeMirror 编辑器，支持 Groovy 语法高亮</li>
                <li>可复用代码片段库，拖拽即用</li>
            </ul>
            <div class="mt-3">
                <a href="/template/list" class="btn btn-outline-success btn-sm"><i class="bi bi-arrow-right"></i> 去管理模板</a>
            </div>
        </div>
    </div>

    <!-- 步骤 3：配置映射 -->
    <div class="card mb-4 border-start border-info border-4 shadow-sm">
        <div class="card-body">
            <h5 class="card-title text-info mb-3">
                <span class="badge bg-info me-2">3</span>配置数据映射
            </h5>
            <p class="text-muted">定义源字段与目标字段之间的对应关系，支持字段级别的转换处理。</p>
            <div class="row g-3 mt-2">
                <div class="col-md-6">
                    <div class="border rounded p-3 bg-light">
                        <h6><i class="bi bi-table"></i> 列配置</h6>
                        <p class="small text-muted mb-0">定义接收列（源）和推送列（目标）的结构，支持类型映射和默认值设置。</p>
                    </div>
                </div>
                <div class="col-md-6">
                    <div class="border rounded p-3 bg-light">
                        <h6><i class="bi bi-arrow-left-right"></i> 映射模板</h6>
                        <p class="small text-muted mb-0">建立 receiveKey → pushKey 的对应关系，支持 Postman JSON 导入。</p>
                    </div>
                </div>
            </div>
            <div class="mt-3">
                <a href="/mapping/templateList" class="btn btn-outline-info btn-sm"><i class="bi bi-arrow-right"></i> 去配置映射</a>
            </div>
        </div>
    </div>

    <!-- 步骤 4：建立流程 -->
    <div class="card mb-4 border-start border-warning border-4 shadow-sm">
        <div class="card-body">
            <h5 class="card-title text-warning mb-3">
                <span class="badge bg-warning text-dark me-2">4</span>建立对接流程
            </h5>
            <p class="text-muted">流程是数据对接的执行单元，将数据源、模板、映射串联成完整的 Pipeline。</p>
            <div class="border rounded p-3 bg-light">
                <div class="d-flex align-items-center gap-3 flex-wrap">
                    <div class="text-center">
                        <div class="border rounded p-2 bg-white">
                            <i class="bi bi-box-arrow-in-right text-primary"></i>
                            <div class="small mt-1">输入数据源</div>
                        </div>
                    </div>
                    <span class="text-muted">→</span>
                    <div class="text-center">
                        <div class="border rounded p-2 bg-white">
                            <i class="bi bi-gear text-success"></i>
                            <div class="small mt-1">管道处理</div>
                            <div class="xsmall text-muted">(模板转换/字段映射)</div>
                        </div>
                    </div>
                    <span class="text-muted">→</span>
                    <div class="text-center">
                        <div class="border rounded p-2 bg-white">
                            <i class="bi bi-box-arrow-right text-danger"></i>
                            <div class="small mt-1">输出数据源</div>
                        </div>
                    </div>
                </div>
            </div>
            <ul class="mt-2 small text-muted">
                <li>四步向导式配置，清晰直观</li>
                <li>管道阶段：读取后(AFTER_READ)、写入前(BEFORE_WRITE)、写入后(AFTER_WRITE)</li>
                <li>支持全量同步和增量同步（按 ID 或时间字段）</li>
                <li>增量同步自动保存水位点，断点续传</li>
            </ul>
            <div class="mt-3">
                <a href="/flow/list" class="btn btn-outline-warning btn-sm"><i class="bi bi-arrow-right"></i> 去创建流程</a>
            </div>
        </div>
    </div>

    <!-- 步骤 5：任务调度 -->
    <div class="card mb-4 border-start border-secondary border-4 shadow-sm">
        <div class="card-body">
            <h5 class="card-title text-secondary mb-3">
                <span class="badge bg-secondary me-2">5</span>创建调度任务
            </h5>
            <p class="text-muted">将流程配置为定时任务，按 Cron 表达式自动执行，无需人工干预。</p>
            <div class="border rounded p-3 bg-light">
                <table class="table table-sm mb-0">
                    <tr><td class="text-muted">Cron 示例</td><td><code>0 0 2 * * ?</code> — 每天凌晨 2 点执行</td></tr>
                    <tr><td class="text-muted">状态管理</td><td>RUNNING（运行中）/ PAUSED（暂停）/ STOPPED（停止）</td></tr>
                    <tr><td class="text-muted">执行日志</td><td>每次执行记录 SQL 日志和控制台输出，方便排查</td></tr>
                </table>
            </div>
            <div class="mt-3">
                <a href="/task/list" class="btn btn-outline-secondary btn-sm"><i class="bi bi-arrow-right"></i> 去管理任务</a>
            </div>
        </div>
    </div>

    <!-- 核心概念 -->
    <div class="card mb-4 border-0 shadow-sm">
        <div class="card-header bg-white">
            <h5 class="mb-0"><i class="bi bi-lightbulb text-warning"></i> 核心概念速览</h5>
        </div>
        <div class="card-body">
            <div class="table-responsive">
                <table class="table table-borderless mb-0">
                    <tr>
                        <td class="fw-bold" style="width: 120px;">数据源</td>
                        <td class="text-muted">数据的来源或去向，可以是数据库（JDBC 连接）或 HTTP 接口</td>
                    </tr>
                    <tr>
                        <td class="fw-bold">模板</td>
                        <td class="text-muted">Groovy 脚本，定义如何将输入数据转换为输出数据</td>
                    </tr>
                    <tr>
                        <td class="fw-bold">映射</td>
                        <td class="text-muted">源字段名与目标字段名的对应关系（receiveKey → pushKey）</td>
                    </tr>
                    <tr>
                        <td class="fw-bold">流程</td>
                        <td class="text-muted">完整的 ETL Pipeline：输入 → 管道处理 → 输出</td>
                    </tr>
                    <tr>
                        <td class="fw-bold">任务</td>
                        <td class="text-muted">流程的定时调度配置，支持 Cron 表达式</td>
                    </tr>
                </table>
            </div>
        </div>
    </div>

    <!-- 提示 -->
    <div class="alert alert-info border-0 shadow-sm">
        <i class="bi bi-info-circle"></i> <strong>提示：</strong>
        建议按顺序完成以上步骤。首次使用可先在"数据源管理"中添加一个数据源并测试连接，熟悉基本操作后再逐步深入。
    </div>

</div>

</@main>
