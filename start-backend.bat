@echo off
chcp 65001 >nul
echo ============================================
echo   古代天平衡器精度检定与误差分析系统
echo   后端启动脚本 (Windows)
echo ============================================
echo.

cd /d "%~dp0backend"

echo [1/3] 检查Java环境...
java -version
if %errorlevel% neq 0 (
    echo [错误] 未检测到Java环境，请安装JDK 11+
    pause
    exit /b 1
)

echo.
echo [2/3] 检查Maven环境...
mvn -version
if %errorlevel% neq 0 (
    echo [错误] 未检测到Maven环境，请安装Maven
    pause
    exit /b 1
)

echo.
echo [3/3] 启动Spring Boot应用...
echo.
echo 服务地址: http://localhost:8080/api
echo API文档: http://localhost:8080/api
echo.
echo 按 Ctrl+C 停止服务
echo ============================================
echo.

mvn spring-boot:run -Dspring-boot.run.profiles=local

pause
