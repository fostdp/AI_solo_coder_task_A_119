@echo off
chcp 65001 >nul
echo ============================================
echo   古代天平衡器精度检定与误差分析系统
echo   前端启动脚本 (Windows)
echo ============================================
echo.

cd /d "%~dp0frontend"

echo 正在启动本地HTTP服务器...
echo.
echo 访问地址: http://localhost:3000
echo.
echo 按 Ctrl+C 停止服务
echo ============================================
echo.

where python >nul 2>nul
if %errorlevel% equ 0 (
    python -m http.server 3000
) else (
    where npx >nul 2>nul
    if %errorlevel% equ 0 (
        npx http-server -p 3000 -c-1
    ) else (
        echo [错误] 未检测到Python或Node.js环境
        echo 请安装Python 3+ 或 Node.js
        echo.
        echo 或者直接用浏览器打开 index.html 文件
        pause
    )
)
