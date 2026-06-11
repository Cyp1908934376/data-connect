<#include "../layouts/main.ftl">
<@main title="驱动管理 - DataConnect" activeMenu="driver">

<div class="d-flex justify-content-between align-items-center mb-3">
    <h5 class="mb-0"><i class="bi bi-box-seam"></i> JDBC 驱动管理</h5>
    <div>
        <button class="btn btn-sm btn-upload me-1" onclick="showUploadTab()">
            <i class="bi bi-upload"></i> 上传驱动
        </button>
        <button class="btn btn-sm btn-download" onclick="showCatalogTab()">
            <i class="bi bi-cloud-download"></i> 下载驱动
        </button>
    </div>
</div>

<ul class="nav nav-tabs mb-3" id="driverTabs">
    <li class="nav-item">
        <a class="nav-link active" data-bs-toggle="tab" href="#tab-installed">
            <i class="bi bi-check-circle"></i> 已安装驱动
        </a>
    </li>
    <li class="nav-item">
        <a class="nav-link" data-bs-toggle="tab" href="#tab-catalog">
            <i class="bi bi-download"></i> 可下载驱动
        </a>
    </li>
</ul>

<div class="tab-content">
    <!-- Tab 1: Installed Drivers -->
    <div class="tab-pane fade show active" id="tab-installed">
        <div id="installedList"></div>
    </div>

    <!-- Tab 2: Downloadable Catalog -->
    <div class="tab-pane fade" id="tab-catalog">
        <!-- Upload area -->
        <div class="upload-zone" id="uploadZone" onclick="$('#fileInput').click()">
            <i class="bi bi-cloud-arrow-up fs-3 text-muted"></i>
            <p class="text-muted mb-0 mt-1">点击上传 .jar 驱动文件，或拖拽到此处</p>
            <input type="file" id="fileInput" accept=".jar" style="display:none" onchange="handleUpload(this)">
        </div>
        <div id="catalogList"></div>
    </div>
</div>

<script>
// Drag & drop
var uploadZone = document.getElementById('uploadZone');
if (uploadZone) {
    uploadZone.addEventListener('dragover', function(e) { e.preventDefault(); this.classList.add('drag-over'); });
    uploadZone.addEventListener('dragleave', function(e) { this.classList.remove('drag-over'); });
    uploadZone.addEventListener('drop', function(e) {
        e.preventDefault();
        this.classList.remove('drag-over');
        var file = e.dataTransfer.files[0];
        if (file && file.name.toLowerCase().endsWith('.jar')) {
            doUpload(file);
        } else {
            showError('只支持 .jar 文件');
        }
    });
}

function loadInstalled() {
    $.get('/driver/api/list', function(res) {
        if (res.code !== 0) { showError(res.message); return; }
        var drivers = res.data;
        if (!drivers || drivers.length === 0) {
            $('#installedList').html('<div class="text-center text-muted py-5"><i class="bi bi-inbox fs-1"></i><p class="mt-2">暂无驱动</p></div>');
            return;
        }
        var html = '<table class="table table-striped table-hover table-sm align-middle"><thead><tr>' +
            '<th>驱动名称</th><th>数据库类型</th><th>驱动类</th><th>版本</th><th>大小</th><th>来源</th><th>操作</th></tr></thead><tbody>';
        for (var i = 0; i < drivers.length; i++) {
            var d = drivers[i];
            var badge = d.source === 'BUILT_IN'
                ? '<span class="badge bg-primary"><i class="bi bi-lock-fill"></i> 内置</span>'
                : '<span class="badge bg-success"><i class="bi bi-file-earmark"></i> 外部</span>';
            var actions = '';
            if (d.locked) {
                actions = '<span class="text-muted small"><i class="bi bi-lock"></i> 系统内置</span>';
            } else {
                actions = '<button class="btn btn-delete btn-sm" onclick="deleteDriver(\'' + d.key + '\')" title="删除">' +
                    '<i class="bi bi-trash"></i> 删除</button>';
            }
            html += '<tr><td><strong>' + d.name + '</strong></td>' +
                '<td>' + (d.dbType || '-') + '</td>' +
                '<td><code style="font-size:0.75rem">' + (d.driverClass || '-') + '</code></td>' +
                '<td>' + (d.version || '-') + '</td>' +
                '<td>' + (d.size || '-') + '</td>' +
                '<td>' + badge + '</td>' +
                '<td>' + actions + '</td></tr>';
        }
        html += '</tbody></table>';
        $('#installedList').html(html);
    }).fail(function() { showError('加载已安装驱动失败'); });
}

function loadCatalog() {
    $.get('/driver/api/catalog', function(res) {
        if (res.code !== 0) { showError(res.message); return; }
        var drivers = res.data;
        if (!drivers || drivers.length === 0) {
            $('#catalogList').html('<div class="text-center text-muted py-4">所有驱动均已安装</div>');
            return;
        }
        var html = '<div class="driver-grid mt-3">';
        for (var i = 0; i < drivers.length; i++) {
            var d = drivers[i];
            var mirrorLabels = ['国际镜像', '阿里云镜像', '清华镜像'];
            var urls = d.mirrorUrls && d.mirrorUrls.length > 0 ? d.mirrorUrls : (d.mavenCentralUrl ? [d.mavenCentralUrl] : []);
            var mirrorBtns = urls.map(function(url, i) {
                var label = mirrorLabels[i] || ('镜像' + (i + 1));
                return '<button class="btn btn-download btn-sm mirror-btn" onclick="downloadDriver(\'' + d.key + '\', ' + i + ', this)" title="从 ' + label + ' 下载">' +
                    '<i class="bi bi-cloud-download"></i> ' + label + '</button>';
            }).join('');

            html += '<div class="driver-card">' +
                '<div class="driver-name">' + d.name + ' <small class="text-muted">' + (d.version || '') + '</small></div>' +
                '<div class="driver-meta"><i class="bi bi-cpu"></i> ' + (d.driverClass || '-') + '</div>' +
                '<div class="driver-meta">' + (d.dbType || '') + '</div>' +
                '<div class="driver-actions">' + mirrorBtns + '</div>' +
                '</div>';
        }
        html += '</div>';
        $('#catalogList').html(html);
    }).fail(function() { showError('加载驱动目录失败'); });
}

function handleUpload(input) {
    var file = input.files[0];
    if (file) doUpload(file);
}

function doUpload(file) {
    var formData = new FormData();
    formData.append('file', file);
    var loading = showLoading('上传中', '正在上传 ' + file.name);
    $.ajax({
        url: '/driver/api/upload',
        type: 'POST',
        data: formData,
        processData: false,
        contentType: false
    }).done(function(res) {
        if (res.code === 0) {
            showSuccess(file.name + ' 上传成功');
            loadInstalled();
        } else {
            showError(res.message);
        }
    }).fail(function() {
        showError('上传失败，请检查网络');
    }).always(function() {
        loading.close();
        $('#fileInput').val('');
    });
}

function downloadDriver(key, mirrorIndex, btn) {
    if (!confirm('确认从该镜像下载驱动？下载后自动安装并加载。')) return;
    var $btn = $(btn);
    var originalHtml = $btn.html();
    $btn.prop('disabled', true).html('<span class="spinner-border spinner-border-sm"></span> 下载中...');
    $.ajax({
        url: '/driver/api/download',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({key: key, mirrorIndex: mirrorIndex})
    }).done(function(res) {
        if (res.code === 0) {
            showSuccess(res.data.name + ' 下载安装成功！');
            loadInstalled();
            loadCatalog();
        } else {
            showError(res.message);
        }
    }).fail(function() {
        showError('下载失败，请尝试其他镜像');
    }).always(function() {
        $btn.prop('disabled', false).html(originalHtml);
    });
}

function deleteDriver(key) {
    if (!confirm('确认删除该驱动？\n\n注意：驱动类无法从内存中卸载，建议删除后重启应用以完全移除。')) return;
    $.ajax({
        url: '/driver/api/' + key,
        type: 'DELETE'
    }).done(function(res) {
        if (res.code === 0) {
            showWarning('已删除，建议重启应用以完全移除');
            loadInstalled();
            loadCatalog();
        } else {
            showError(res.message);
        }
    }).fail(function() {
        showError('删除失败');
    });
}

function showUploadTab() {
    new bootstrap.Tab(document.getElementById('driverTabs').querySelector('[href="#tab-catalog"]')).show();
}

function showCatalogTab() {
    new bootstrap.Tab(document.getElementById('driverTabs').querySelector('[href="#tab-catalog"]')).show();
}

// Initial load
$(document).ready(function() {
    loadInstalled();
    loadCatalog();
});
</script>

</@main>
