<#macro main title="数据对接服务" activeMenu="" extraCss=[] extraJs=[]>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${title}</title>
    <link rel="stylesheet" href="/static/css/bootstrap.min.css">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.10.5/font/bootstrap-icons.css">
    <link rel="stylesheet" href="/static/css/app.css">
    <#list extraCss as css>
        <link rel="stylesheet" href="${css}">
    </#list>
    <script src="/static/js/jquery.min.js"></script>
    <script src="/static/js/bootstrap.bundle.min.js"></script>
    <script src="/static/js/app.js"></script>
    <#list extraJs as js>
        <script src="${js}"></script>
    </#list>
</head>
<body>
<div class="app-container">
    <!-- 左侧菜单 -->
    <nav class="sidebar" id="sidebar">
        <div class="sidebar-header">
            <h5 class="mb-0 text-white">数据对接服务</h5>
        </div>
        <ul class="nav flex-column">
            <li class="nav-item">
                <a class="nav-link ${(activeMenu=='index')?string('active','')}" href="/">
                    <i class="bi bi-house-door"></i> 首页
                </a>
            </li>
            <li class="nav-item">
                <a class="nav-link ${(activeMenu=='datasource')?string('active','')}" href="/datasource/list">
                    <i class="bi bi-database"></i> 数据源管理
                </a>
            </li>
            <li class="nav-item">
                <a class="nav-link ${(activeMenu=='template')?string('active','')}" href="/template/list">
                    <i class="bi bi-file-earmark-code"></i> 模板管理
                </a>
            </li>
            <li class="nav-item">
                <a class="nav-link ${(activeMenu=='flow')?string('active','')}" href="/flow/list">
                    <i class="bi bi-diagram-3"></i> 对接流程
                </a>
            </li>
            <li class="nav-item">
                <a class="nav-link ${(activeMenu=='mapping')?string('active','')}" href="/mapping/templateList">
                    <i class="bi bi-link-45deg"></i> 数据对接
                </a>
            </li>
            <li class="nav-item">
                <a class="nav-link ${(activeMenu=='task')?string('active','')}" href="/task/list">
                    <i class="bi bi-clock-history"></i> 任务管理
                </a>
            </li>
        </ul>
    </nav>

    <!-- 右侧内容区 -->
    <div class="main-content">
        <!-- 顶部导航 -->
        <header class="topbar">
            <button class="btn btn-sm btn-outline-secondary" id="sidebarToggle">
                <i class="bi bi-list"></i>
            </button>
            <nav aria-label="breadcrumb" class="ms-3">
                <ol class="breadcrumb mb-0">
                    <li class="breadcrumb-item"><a href="/">首页</a></li>
                    <li class="breadcrumb-item active">${pageTitle!title}</li>
                </ol>
            </nav>
        </header>

        <!-- 内容区 -->
        <main class="content">
            <#nested>
        </main>
    </div>
</div>

<!-- 消息通知容器 -->
<div id="messageArea" class="notification-container"></div>

</body>
</html>
</#macro>
