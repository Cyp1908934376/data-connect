@echo off
REM 启动宁波诺丁汉 Mock API Server
REM 用于 data-connect 项目的本地模拟测试

cd /d "%~dp0"

echo ========================================
echo   宁波诺丁汉 Mock API Server
echo ========================================
echo.
echo 启动后可在 data-connect 模板400中使用:
echo   params.mockMode = false
echo   params.apiBaseUrl = http://localhost:8081
echo.

python nottingham-mock-server.py --port 8081 --count 1042

pause
