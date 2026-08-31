<#include "layouts/main.ftl">
<@main title="数据对接服务 - 首页" activeMenu="index">

<div class="container-fluid">
    <div class="row mb-4">
        <div class="col">
            <h3>数据对接服务</h3>
            <p class="text-muted">零环境依赖，开箱即用的数据对接平台。支持多种数据库和接口的数据转换与同步。</p>
        </div>
    </div>

    <!-- 统计卡片 -->
    <div class="row mb-4">
        <div class="col-md-3 mb-3">
            <div class="card border-primary">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-center">
                        <div>
                            <h6 class="text-muted">数据源</h6>
                            <h3 class="mb-0">${dsCount!0}</h3>
                        </div>
                        <i class="bi bi-database text-primary" style="font-size:2rem;"></i>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-md-3 mb-3">
            <div class="card border-success">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-center">
                        <div>
                            <h6 class="text-muted">可视化模板</h6>
                            <h3 class="mb-0">${visualCount!0}</h3>
                        </div>
                        <i class="bi bi-bezier2 text-success" style="font-size:2rem;"></i>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-md-3 mb-3">
            <div class="card border-info">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-center">
                        <div>
                            <h6 class="text-muted">定时任务</h6>
                            <h3 class="mb-0">${taskCount!0}</h3>
                        </div>
                        <i class="bi bi-clock-history text-info" style="font-size:2rem;"></i>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-md-3 mb-3">
            <div class="card border-warning">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-center">
                        <div>
                            <h6 class="text-muted">运行中任务</h6>
                            <h3 class="mb-0">${runningTaskCount!0}</h3>
                        </div>
                        <i class="bi bi-play-circle text-warning" style="font-size:2rem;"></i>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- 快捷入口 -->
    <div class="row">
        <div class="col-md-6 mb-3">
            <div class="card h-100">
                <div class="card-header">快捷操作</div>
                <div class="card-body">
                    <div class="d-grid gap-2">
                        <a href="/guide" class="btn btn-outline-secondary"><i class="bi bi-compass"></i> 新手引导（推荐先看）</a>
                        <a href="/datasource/form" class="btn btn-outline-primary"><i class="bi bi-plus-circle"></i> 新增数据源</a>
                        <a href="/visual/list" class="btn btn-outline-success"><i class="bi bi-bezier2"></i> 可视化模板</a>
                        <a href="/publish/list" class="btn btn-outline-warning"><i class="bi bi-cloud-upload"></i> 发布管理</a>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-md-6 mb-3">
            <div class="card h-100">
                <div class="card-header">系统信息</div>
                <div class="card-body">
                    <table class="table table-sm">
                        <tr><td class="text-muted">内嵌数据库</td><td>H2 (文件模式)</td></tr>
                        <tr><td class="text-muted">数据目录</td><td>./data/</td></tr>
                        <tr><td class="text-muted">日志目录</td><td>./logs/</td></tr>
                        <tr><td class="text-muted">Web控制台</td><td><a href="/h2-console" target="_blank">H2 Console</a></td></tr>
                    </table>
                </div>
            </div>
        </div>
        <div class="col-md-6 mb-3">
            <div class="card h-100">
                <div class="card-header">数据同步</div>
                <div class="card-body">
                    <div class="d-grid gap-2">
                        <button class="btn btn-outline-info btn-sync-reload">
                            <i class="bi bi-arrow-down-circle"></i> 从 data.sql 重新加载
                        </button>
                        <button class="btn btn-outline-success btn-sync-export">
                            <i class="bi bi-arrow-up-circle"></i> 导出配置到 data.sql
                        </button>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

</@main>
