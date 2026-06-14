@echo off
chcp 65001 >nul
echo ============================================
echo   古代天平模拟器启动脚本 (Windows)
echo ============================================
echo.

cd /d "%~dp0simulator"

echo [1/2] 检查Python环境...
python --version
if %errorlevel% neq 0 (
    echo [错误] 未检测到Python环境
    pause
    exit /b 1
)

echo.
echo [2/2] 检查paho-mqtt依赖...
python -c "import paho.mqtt.client" 2>nul
if %errorlevel% neq 0 (
    echo 正在安装paho-mqtt...
    pip install paho-mqtt
)

echo.
echo ============================================
echo  可选模式:
echo    1. 正常模式 (每小时发布一轮100件天平数据)
echo    2. 快速模式 (每5秒发布一轮，用于测试)
echo    3. 单轮模式 (仅发布一轮)
echo    4. 特定天平测试模式
echo ============================================
echo.
set /p mode="请选择模式 (1-4，默认2): "

if "%mode%"=="" set mode=2

if "%mode%"=="1" (
    echo 启动正常模式...
    python balance_simulator.py
) else if "%mode%"=="2" (
    echo 启动快速模式...
    python balance_simulator.py fast
) else if "%mode%"=="3" (
    echo 启动单轮模式...
    python balance_simulator.py single
) else if "%mode%"=="4" (
    set /p code="请输入天平编号 (如 BAL-0001): "
    set /p count="请输入发布次数 (默认10): "
    set /p interval="请输入间隔秒数 (默认1): "
    if "%count%"=="" set count=10
    if "%interval%"=="" set interval=1
    python balance_simulator.py balance %code% %count% %interval%
) else (
    echo 无效选项
)

pause
