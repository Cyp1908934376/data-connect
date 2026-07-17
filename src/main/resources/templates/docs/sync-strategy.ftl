<#include "../layouts/main.ftl">
<@main title="同步策略说明" activeMenu="">
<div class="container-fluid">
    <div class="mb-3">
        <a href="javascript:history.back()" class="btn btn-sm btn-outline-secondary"><i class="bi bi-arrow-left"></i> 返回</a>
    </div>
    <div class="card">
        <div class="card-body markdown-body">
            ${content}
        </div>
    </div>
</div>
<style>
.markdown-body { line-height: 1.8; }
.markdown-body h2 { margin-top: 1.5em; border-bottom: 2px solid #eee; padding-bottom: 0.3em; }
.markdown-body h3 { margin-top: 1.2em; }
.markdown-body table { margin: 1em 0; }
.markdown-body th { background: #f5f5f5; white-space: nowrap; }
.markdown-body code { background: #f0f0f0; padding: 2px 6px; border-radius: 3px; font-size: 90%; }
.markdown-body pre { background: #f5f5f5; padding: 1em; border-radius: 5px; overflow-x: auto; }
.markdown-body hr { margin: 2em 0; }
.markdown-body li { margin-left: 1.5em; }
</style>
</@main>
