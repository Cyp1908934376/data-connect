/**
 * 数据对接服务 - 通用JS
 */

$(function() {
    // 侧边栏折叠
    $('#sidebarToggle').on('click', function() {
        $('#sidebar').toggleClass('collapsed');
        if ($(window).width() <= 768) {
            $('#sidebar').toggleClass('show');
        }
    });

    // 确认删除
    $('.btn-delete-confirm').on('click', function() {
        var that = this;
        var msg = $(this).data('confirm') || '确定要删除吗？';
        if (confirm(msg)) {
            var form = $(this).closest('form');
            if (form.length) {
                form.submit();
            } else {
                var href = $(this).data('href');
                if (href) {
                    window.location.href = href;
                }
            }
        }
    });

    // AJAX 通用操作
    $('.btn-ajax-action').on('click', function() {
        var url = $(this).data('url');
        var method = $(this).data('method') || 'POST';
        var confirmMsg = $(this).data('confirm');
        if (confirmMsg && !confirm(confirmMsg)) return;

        var $btn = $(this).prop('disabled', true);
        $.ajax({
            url: url,
            type: method,
            success: function(res) {
                if (res.code === 0) {
                    showMessage('操作成功', 'success');
                    setTimeout(function() { location.reload(); }, 800);
                } else {
                    showMessage('操作失败: ' + (res.message || '未知错误'), 'error');
                }
            },
            error: function(xhr) {
                var msg = '请求失败';
                try { var err = JSON.parse(xhr.responseText); msg = err.message || msg; } catch(e) {}
                showMessage(msg, 'error');
            },
            complete: function() {
                $btn.prop('disabled', false);
            }
        });
    });

    // 密码显示/隐藏切换
    $('.password-toggle').on('click', function() {
        var $input = $($(this).data('target'));
        var type = $input.attr('type');
        $input.attr('type', type === 'password' ? 'text' : 'password');
        $(this).find('i').toggleClass('bi-eye bi-eye-slash');
    });

    // 自动关闭alert
    setTimeout(function() {
        $('.alert-auto-close').fadeOut();
    }, 3000);

    // 搜索型下拉框：超过10个选项的 select 自动添加搜索功能
    initSearchableSelects();
});

/**
 * 为选项超过阈值的 select 添加搜索过滤功能
 */
function initSearchableSelects(threshold) {
    threshold = threshold || 10;
    $('select').each(function() {
        var $select = $(this);
        // 跳过已处理的、选项不足的、以及有特殊 class 标记跳过的
        if ($select.closest('.searchable-select-wrap').length) return;
        if ($select.hasClass('no-search')) return;
        if ($select.find('option').length <= threshold) return;

        makeSearchable($select);
    });
}

/**
 * 将 select 转换为可搜索下拉
 */
function makeSearchable($select) {
    var $wrap = $('<div class="searchable-select-wrap"></div>');
    var $input = $('<input type="text" class="form-select searchable-select-input" autocomplete="off" placeholder="输入搜索...">');
    var $dropdown = $('<div class="searchable-select-dropdown"></div>');
    var currentVal = $select.val();

    $select.hide().after($wrap);
    $wrap.append($select).append($input).append($dropdown);

    // 设置初始显示文本
    updateSearchInput($select, $input);

    // 构建下拉选项列表
    function buildOptions(filter) {
        filter = (filter || '').toLowerCase();
        var html = '';
        var hasMatch = false;
        $select.find('option').each(function() {
            var opt = $(this);
            if (opt.val() === '' && opt.text() === '') return; // skip empty
            var text = opt.text().toLowerCase();
            var val = opt.val() ? opt.val().toLowerCase() : '';
            if (filter === '' || text.indexOf(filter) >= 0 || val.indexOf(filter) >= 0) {
                var selected = opt.val() === $select.val() ? ' selected' : '';
                html += '<div class="searchable-select-option' + selected + '" data-value="' + (opt.val() || '') + '">' + opt.text() + '</div>';
                hasMatch = true;
            }
        });
        if (!hasMatch) {
            html = '<div class="searchable-select-no-result">无匹配结果</div>';
        }
        return html;
    }

    $dropdown.html(buildOptions(''));

    // 点击输入框显示下拉
    $input.on('focus', function() {
        $dropdown.html(buildOptions('')).show();
        $input.select();
    });

    // 输入时过滤
    $input.on('input', function() {
        $dropdown.html(buildOptions($input.val())).show();
    });

    // 选择选项
    $dropdown.on('mousedown', '.searchable-select-option', function(e) {
        e.preventDefault();
        var val = $(this).data('value');
        $select.val(val).trigger('change');
        updateSearchInput($select, $input);
        $dropdown.hide();
    });

    // 点击外部关闭
    $(document).on('click', function(e) {
        if (!$(e.target).closest('.searchable-select-wrap').length) {
            $dropdown.hide();
        }
    });

    // 监听 select 的外部变更（如程序设置值）
    $select.on('change', function() {
        updateSearchInput($select, $input);
    });
}

function updateSearchInput($select, $input) {
    var selected = $select.find('option:selected');
    if (selected.length && selected.val() !== '') {
        $input.val(selected.text());
    } else {
        $input.val('');
    }
}

/**
 * 显示消息通知 (ElementUI 风格)
 * @param {string} msg    - 消息内容
 * @param {string} type   - 类型: success | error | warning | info
 * @param {object} options - 可选配置 { duration: 毫秒, title: 标题 }
 */
function showMessage(msg, type, options) {
    type = type || 'info';
    options = options || {};
    var duration = options.duration;
    // 默认自动关闭时间：error 5s，其他 3s；传 0 则不自动关闭
    if (duration === undefined) {
        duration = type === 'error' ? 5000 : 3000;
    }

    var icons = {
        success: '\u2713',  // ✓
        error: '\u2717',    // ✗
        warning: '\u26a0',  // ⚠
        info: '\u2139'      // ℹ
    };

    var $container = $('#messageArea');
    if (!$container.length) {
        $container = $('<div id="messageArea" class="notification-container"></div>').appendTo('body');
    }

    var $item = $(
        '<div class="notification-item notification-' + type + '">' +
            '<span class="notification-icon">' + (icons[type] || icons.info) + '</span>' +
            '<div class="notification-content">' +
                (options.title ? '<div class="notification-title">' + options.title + '</div>' : '') +
                '<div class="notification-message">' + msg + '</div>' +
            '</div>' +
            '<button type="button" class="notification-close">&times;</button>' +
        '</div>'
    );

    $container.append($item);

    // 关闭按钮
    $item.find('.notification-close').on('click', function() {
        removeItem($item);
    });

    // 自动关闭
    if (duration > 0) {
        $item.data('timeout', setTimeout(function() {
            removeItem($item);
        }, duration));
    }

    // 鼠标悬停时暂停自动关闭
    $item.on('mouseenter', function() {
        var tid = $item.data('timeout');
        if (tid) { clearTimeout(tid); $item.data('timeout', null); }
    }).on('mouseleave', function() {
        if (duration > 0 && !$item.data('timeout')) {
            $item.data('timeout', setTimeout(function() {
                removeItem($item);
            }, duration));
        }
    });

    function removeItem($el) {
        var tid = $el.data('timeout');
        if (tid) { clearTimeout(tid); }
        $el.addClass('notification-removing');
        setTimeout(function() { $el.remove(); }, 250);
    }
}

/**
 * 快捷方法
 */
function showSuccess(msg, options) { showMessage(msg, 'success', options); }
function showError(msg, options) { showMessage(msg, 'error', options); }
function showWarning(msg, options) { showMessage(msg, 'warning', options); }
function showInfo(msg, options) { showMessage(msg, 'info', options); }

/**
 * 全屏加载遮罩
 * @param {string} title    - 主标题
 * @param {string} subtitle - 副标题
 * @returns {object} - { update, close }
 */
function showLoading(title, subtitle) {
    title = title || '处理中...';
    subtitle = subtitle || '请稍候，正在执行数据对接';

    var $overlay = $('#globalLoadingOverlay');
    if (!$overlay.length) {
        $overlay = $(
            '<div id="globalLoadingOverlay" class="loading-overlay">' +
                '<div class="loading-card">' +
                    '<div class="loading-spinner"></div>' +
                    '<div class="loading-title"></div>' +
                    '<div class="loading-subtitle"></div>' +
                    '<div class="loading-progress-bar"></div>' +
                    '<div class="loading-elapsed"></div>' +
                '</div>' +
            '</div>'
        ).appendTo('body');
    }

    $overlay.find('.loading-title').text(title);
    $overlay.find('.loading-subtitle').text(subtitle);
    $overlay.find('.loading-elapsed').text('');
    $overlay.addClass('show');

    // 计时器
    var startTime = Date.now();
    var timerId = setInterval(function() {
        var elapsed = Math.floor((Date.now() - startTime) / 1000);
        var timeStr = elapsed < 60
            ? '已等待 ' + elapsed + ' 秒'
            : '已等待 ' + Math.floor(elapsed / 60) + ' 分 ' + (elapsed % 60) + ' 秒';
        $overlay.find('.loading-elapsed').text(timeStr);
    }, 1000);

    return {
        update: function(newTitle, newSubtitle) {
            if (newTitle !== undefined) $overlay.find('.loading-title').text(newTitle);
            if (newSubtitle !== undefined) $overlay.find('.loading-subtitle').text(newSubtitle);
        },
        close: function() {
            clearInterval(timerId);
            $overlay.removeClass('show');
        }
    };
}

function hideLoading() {
    var $overlay = $('#globalLoadingOverlay');
    if ($overlay.length) {
        $overlay.removeClass('show');
    }
}

/**
 * 格式化日期
 */
function formatDate(dateStr) {
    if (!dateStr) return '';
    var d = new Date(dateStr);
    var pad = function(n) { return n < 10 ? '0' + n : n; };
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) +
        ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
}

/**
 * 格式化耗时
 */
function formatDuration(ms) {
    if (ms < 1000) return ms + 'ms';
    if (ms < 60000) return (ms / 1000).toFixed(1) + 's';
    return Math.floor(ms / 60000) + 'm ' + Math.floor((ms % 60000) / 1000) + 's';
}
