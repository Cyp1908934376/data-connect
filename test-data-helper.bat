@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

echo ============================================
echo  Data-Connect 测试数据管理工具
echo ============================================
echo.
echo  注意：控制台已切换到 UTF-8 (65001)
echo  如果中文显示乱码，请手动执行: chcp 65001
echo.

set DATA_DIR=data
set TIMESTAMP=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%

:menu
echo ┌──────────────────────────────────────┐
echo │  1. 备份当前数据库                      │
echo │  2. 恢复最近的备份                      │
echo │  3. 查看所有备份                        │
echo │  4. 启动应用并打开H2控制台               │
echo │  5. 查看数据库表结构                     │
echo │  0. 退出                               │
echo └──────────────────────────────────────┘
echo.
set /p choice="请选择操作 (0-5): "

if "%choice%"=="1" goto backup
if "%choice%"=="2" goto restore
if "%choice%"=="3" goto list
if "%choice%"=="4" goto start_app
if "%choice%"=="5" goto schema
if "%choice%"=="0" goto end
echo 无效选择，请重新输入
goto menu

:backup
echo.
echo [备份] 正在备份数据库...
if not exist "%DATA_DIR%" mkdir "%DATA_DIR%"
copy /Y "%DATA_DIR%\dataconnect.mv.db" "%DATA_DIR%\dataconnect_backup_%TIMESTAMP%.mv.db" >nul
if exist "%DATA_DIR%\dataconnect.trace.db" (
    copy /Y "%DATA_DIR%\dataconnect.trace.db" "%DATA_DIR%\dataconnect.trace_backup_%TIMESTAMP%.db" >nul
)
echo [完成] 备份已保存: dataconnect_backup_%TIMESTAMP%.mv.db
echo.
goto menu

:restore
echo.
echo [恢复] 可用的备份文件:
dir /B /O-D "%DATA_DIR%\dataconnect_backup_*.mv.db" 2>nul
if errorlevel 1 (
    echo 没有找到备份文件！
    goto menu
)
echo.
set /p backup_file="请输入要恢复的备份文件名: "
if not exist "%DATA_DIR%\%backup_file%" (
    echo 文件不存在: %backup_file%
    goto menu
)
echo [恢复] 正在恢复 %backup_file% ...
copy /Y "%DATA_DIR%\%backup_file%" "%DATA_DIR%\dataconnect.mv.db" >nul
echo [完成] 数据库已恢复到 %backup_file%
echo 重启应用后生效。
echo.
goto menu

:list
echo.
echo [备份列表]
dir /B /O-D "%DATA_DIR%\dataconnect_backup_*.mv.db" 2>nul
if errorlevel 1 echo 没有找到备份文件
echo.
goto menu

:start_app
echo.
echo [启动] 正在启动应用... 请等待启动完成后访问 http://localhost:8080/h2-console
echo JDBC URL: jdbc:h2:file:./data/dataconnect
echo 用户名: sa  密码: (空)
echo 在H2控制台中执行: RUNSCRIPT FROM 'src/main/resources/test-data.sql'
echo.
mvnw.cmd spring-boot:run
goto menu

:schema
echo.
echo [表结构]
echo ============================================
echo  主应用表 (schema.sql):
echo    ds_config          - 数据源配置
echo    template_category  - 模板分类
echo    template           - 模板
echo    template_version   - 模板版本历史
echo    template_snippet   - 代码片段
echo    flow_config        - 集成流配置
echo    task_config        - 任务调度配置
echo    task_execution_log - 任务执行日志
echo    debug_log          - 调试日志
echo    column_config      - 字段配置
echo    mapping_template   - 字段映射模板
echo ============================================
echo.
goto menu

:end
echo 再见！
endlocal
