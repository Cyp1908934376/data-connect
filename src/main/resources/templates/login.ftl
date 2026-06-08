<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>登录 - 数据对接服务</title>
    <link rel="stylesheet" href="/static/css/bootstrap.min.css">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.10.5/font/bootstrap-icons.css">
    <link rel="stylesheet" href="/static/css/app.css">
    <script src="/static/js/jquery.min.js"></script>
    <style>
        .login-container {
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        }
        .login-card {
            width: 400px;
            padding: 40px;
            background: #fff;
            border-radius: 8px;
            box-shadow: 0 10px 40px rgba(0,0,0,0.15);
        }
        .login-card h4 {
            text-align: center;
            margin-bottom: 30px;
            color: #333;
        }
    </style>
</head>
<body>
<div class="login-container">
    <div class="login-card">
        <h4>数据对接服务</h4>
        <div id="loginError" class="alert alert-danger d-none"></div>
        <form id="loginForm">
            <div class="mb-3">
                <label for="username" class="form-label">用户名</label>
                <input type="text" class="form-control" id="username" name="username" placeholder="请输入用户名" required autofocus>
            </div>
            <div class="mb-3">
                <label for="password" class="form-label">密码</label>
                <input type="password" class="form-control" id="password" name="password" placeholder="请输入密码" required>
            </div>
            <button type="submit" class="btn btn-primary w-100">登 录</button>
        </form>
    </div>
</div>
<script>
$(function() {
    $('#loginForm').on('submit', function(e) {
        e.preventDefault();
        var username = $('#username').val().trim();
        var password = $('#password').val();
        if (!username || !password) {
            $('#loginError').removeClass('d-none').text('请输入用户名和密码');
            return;
        }
        $.post('/login', { username: username, password: password }, function(res) {
            if (res.code === 0) {
                window.location.href = '/';
            } else {
                $('#loginError').removeClass('d-none').text(res.message);
            }
        }).fail(function() {
            $('#loginError').removeClass('d-none').text('登录失败，请重试');
        });
    });
});
</script>
</body>
</html>
