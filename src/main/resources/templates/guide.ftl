<#include "layouts/main.ftl">
<@main title="数据对接服务 - 新手引导" activeMenu="guide">

<style>
    .guide-step-num { width: 28px; height: 28px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; font-weight: 600; font-size: 14px; }
    .guide-ol { margin-bottom: 0; padding-left: 1.2rem; }
    .guide-ol li { margin-bottom: .35rem; }
    .guide-flow .badge { font-size: 13px; font-weight: 500; }
</style>

<div class="container-fluid" style="max-width: 960px;">

    <div class="text-center mb-4">
        <h3 class="fw-bold">欢迎使用数据对接服务</h3>
        <p class="text-muted mb-0">按下面顺序做一遍，就能完成「接口取数 → 写入数据库」，并可选定时或对外发布。</p>
    </div>

    <div class="card mb-4 border-0 shadow-sm">
        <div class="card-body text-center py-4">
            <div class="d-flex justify-content-center align-items-center flex-wrap gap-2 guide-flow">
                <span class="badge bg-secondary px-3 py-2">① 驱动（按需）</span>
                <span class="text-muted">→</span>
                <span class="badge bg-primary px-3 py-2">② 数据源</span>
                <span class="text-muted">→</span>
                <span class="badge bg-success px-3 py-2">③ 可视化模板</span>
                <span class="text-muted">→</span>
                <span class="badge bg-info px-3 py-2">④ 执行 / 定时</span>
                <span class="text-muted">→</span>
                <span class="badge bg-warning text-dark px-3 py-2">⑤ 发布（可选）</span>
            </div>
            <div class="small text-muted mt-3">日常对接走「可视化模板」。定时保存后会出现在「任务管理」。</div>
        </div>
    </div>

    <div class="card mb-4 border-start border-secondary border-4 shadow-sm">
        <div class="card-body">
            <h5 class="card-title mb-3">
                <span class="guide-step-num bg-secondary text-white me-2">1</span>驱动管理 <span class="small text-muted fw-normal">（连非内置库时才需要）</span>
            </h5>
            <p class="text-muted">平台已带 MySQL / MariaDB 等常用驱动。连 <strong>Oracle、SQL Server、PostgreSQL</strong> 等时，先装对应 JDBC 包。</p>
            <ol class="guide-ol small">
                <li>打开 <strong>驱动管理</strong>。</li>
                <li>点「下载驱动」从目录安装，或「上传驱动」选择本地 <code>.jar</code>。</li>
                <li>在「已安装驱动」里能看到即可，再到数据源里选对应库类型。</li>
            </ol>
            <div class="mt-3">
                <a href="/driver/list" class="btn btn-outline-secondary btn-sm"><i class="bi bi-box-seam"></i> 去驱动管理</a>
            </div>
        </div>
    </div>

    <div class="card mb-4 border-start border-primary border-4 shadow-sm">
        <div class="card-body">
            <h5 class="card-title text-primary mb-3">
                <span class="guide-step-num bg-primary text-white me-2">2</span>添加数据源
            </h5>
            <p class="text-muted">数据源是拉数和写数的连接。接口、库各配一个，后面模板里直接选。</p>
            <div class="row g-3">
                <div class="col-md-6">
                    <div class="border rounded p-3 h-100 bg-light">
                        <h6 class="mb-2"><i class="bi bi-cloud-arrow-down"></i> 接口数据源</h6>
                        <ol class="guide-ol small text-muted">
                            <li>数据源管理 → 新增，类型选接口。</li>
                            <li>填 URL、方法（GET/POST）。</li>
                            <li>需要登录态时配置 Token 地址、取 Token 路径、查询参数名。</li>
                            <li>保存后可用「测试」看返回结构。</li>
                        </ol>
                    </div>
                </div>
                <div class="col-md-6">
                    <div class="border rounded p-3 h-100 bg-light">
                        <h6 class="mb-2"><i class="bi bi-database"></i> 数据库数据源</h6>
                        <ol class="guide-ol small text-muted">
                            <li>类型选数据库，选库类型（与已装驱动一致）。</li>
                            <li>填主机、端口、库名、账号密码。</li>
                            <li>点测试连接，成功后再保存。</li>
                            <li>写库、读 SQL 都用这个数据源。</li>
                        </ol>
                    </div>
                </div>
            </div>
            <div class="mt-3">
                <a href="/datasource/list" class="btn btn-outline-primary btn-sm"><i class="bi bi-database"></i> 去数据源管理</a>
                <a href="/datasource/form" class="btn btn-primary btn-sm ms-1"><i class="bi bi-plus"></i> 新增数据源</a>
            </div>
        </div>
    </div>

    <div class="card mb-4 border-start border-success border-4 shadow-sm">
        <div class="card-body">
            <h5 class="card-title text-success mb-3">
                <span class="guide-step-num bg-success text-white me-2">3</span>创建可视化模板
            </h5>
            <p class="text-muted mb-2">这是主操作页：从上到下加事件，把「取数 → 过滤/转换 → 写表」串起来。典型：接口进主表，再写部门表。</p>
            <ol class="guide-ol small">
                <li>打开 <strong>可视化模板</strong> → 新增。</li>
                <li><strong>输入事件</strong>：先选「手动触发」调试；要定时再改为「定时触发」并填 Cron（如每小时 <code>0 0 * * * ?</code>）。</li>
                <li>点「添加事件步骤」→ <strong>数据源事件</strong>：选接口数据源，或直接填 URL；可设每批条数、增量字段。</li>
                <li>再添加 <strong>操作事件</strong>：选库数据源，操作选新增，填目标表。
                    <ul class="mt-1 mb-1">
                        <li>字段映射：取值选「输入字段」，源字段填接口字段名。</li>
                        <li>要去重：填<strong>唯一键</strong>（目标列名，如 <code>id</code>）。仅新增会跳过已存在；「存在则更新」会覆盖。</li>
                        <li>返回方式保持「把接口原数据传给下一步」，才能再写第二张表。</li>
                    </ul>
                </li>
                <li>需要部门等从表：可再加过滤 + 第二个操作事件，映射部门字段，同样填唯一键。</li>
                <li>字段要中文转拼音首字母：映射里转换选「中文首字母」。</li>
                <li>保存后，在列表点<strong>执行</strong>，看日志是否写入成功。</li>
            </ol>
            <div class="alert alert-light border small mb-0 mt-3">
                增量同步的进度记在水位线里。要重新从头拉数：编辑模板，输入类型选定时，点「重置水位线」。这不会清空已写入的表数据。
            </div>
            <div class="mt-3">
                <a href="/visual/list" class="btn btn-success btn-sm"><i class="bi bi-bezier2"></i> 去可视化模板</a>
            </div>
        </div>
    </div>

    <div class="card mb-4 border-start border-info border-4 shadow-sm">
        <div class="card-body">
            <h5 class="card-title text-info mb-3">
                <span class="guide-step-num bg-info text-white me-2">4</span>执行与定时
            </h5>
            <p class="text-muted">可视化模板<strong>不必</strong>再到「任务管理」里手工建任务：输入事件选定时并保存后，会自动出现在任务列表。</p>
            <div class="table-responsive">
                <table class="table table-sm table-bordered mb-0 small">
                    <thead class="table-light">
                        <tr><th style="width:28%;">你想做的</th><th>怎么做</th></tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td>马上跑一次</td>
                            <td>可视化模板列表 → 执行。定时模板也可用「立即执行」。</td>
                        </tr>
                        <tr>
                            <td>按点自动跑</td>
                            <td>输入事件选「定时触发」，填 Cron，<strong>保存模板</strong>后生效，并出现在「任务管理」。写库请用唯一键 + 存在则更新，避免重复。</td>
                        </tr>
                        <tr>
                            <td>看结果 / 排查</td>
                            <td>执行日志里看每步 SQL 和行数；写入完成会提示新增、跳过重复。</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </div>
    </div>

    <div class="card mb-4 border-start border-warning border-4 shadow-sm">
        <div class="card-body">
            <h5 class="card-title mb-3">
                <span class="guide-step-num bg-warning text-dark me-2">5</span>发布管理 <span class="small text-muted fw-normal">（把模板变成 HTTP 接口）</span>
            </h5>
            <p class="text-muted">给外部系统调用：保存模板后，到发布管理挂上可视化模板。</p>
            <ol class="guide-ol small">
                <li>发布管理 → 新增发布。</li>
                <li>「关联可视化模板」选刚保存的模板。</li>
                <li>端口可用「自动分配」，路径如 <code>/api/data</code>。</li>
                <li>保存后点<strong>启动</strong>。按页面给出的内部/外部地址 POST 调用。</li>
                <li>不需要对外时，停用即可，不影响模板自己的定时执行。</li>
            </ol>
            <div class="mt-3">
                <a href="/publish/list" class="btn btn-outline-warning btn-sm"><i class="bi bi-cloud-upload"></i> 去发布管理</a>
            </div>
        </div>
    </div>

    <div class="card mb-4 border-0 shadow-sm">
        <div class="card-header bg-white">
            <h5 class="mb-0"><i class="bi bi-clock-history"></i> 任务管理</h5>
        </div>
        <div class="card-body small text-muted">
            <p>可视化模板选「定时触发」并保存后，会自动出现在任务列表，可在这里暂停、改 Cron。</p>
            <a href="/task/list" class="btn btn-outline-secondary btn-sm"><i class="bi bi-clock-history"></i> 去任务管理</a>
        </div>
    </div>

    <div class="card mb-4 border-0 shadow-sm">
        <div class="card-header bg-white">
            <h5 class="mb-0"><i class="bi bi-lightbulb text-warning"></i> 名词对照</h5>
        </div>
        <div class="card-body p-0">
            <table class="table table-borderless mb-0">
                <tr>
                    <td class="fw-bold ps-3" style="width: 130px;">驱动</td>
                    <td class="text-muted">连接某种数据库所需的 JDBC 包，装一次即可</td>
                </tr>
                <tr>
                    <td class="fw-bold ps-3">数据源</td>
                    <td class="text-muted">一组连接信息：库或 HTTP 接口</td>
                </tr>
                <tr>
                    <td class="fw-bold ps-3">可视化模板</td>
                    <td class="text-muted">用事件步骤编排的取数/写数流程，推荐日常使用</td>
                </tr>
                <tr>
                    <td class="fw-bold ps-3">唯一键</td>
                    <td class="text-muted">写库时用来判断「这行已经有了」的目标列，如 id、部门编码</td>
                </tr>
                <tr>
                    <td class="fw-bold ps-3">水位线</td>
                    <td class="text-muted">增量/分批已经拉到哪；重置后下次从头拉，不清空目标表</td>
                </tr>
                <tr>
                    <td class="fw-bold ps-3">发布</td>
                    <td class="text-muted">把可视化模板暴露成 HTTP 接口</td>
                </tr>
                <tr>
                    <td class="fw-bold ps-3">任务</td>
                    <td class="text-muted">定时调度：可视化模板的 Cron 会自动出现在任务列表</td>
                </tr>
            </table>
        </div>
    </div>

    <div class="alert alert-info border-0 shadow-sm">
        <i class="bi bi-info-circle"></i>
        建议第一次：<strong>接口数据源 + 库数据源 → 可视化模板写一张表 → 列表执行一次</strong>。跑通后再加定时、第二张表或发布。
    </div>

</div>

</@main>
