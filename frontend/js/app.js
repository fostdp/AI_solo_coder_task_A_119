document.addEventListener('DOMContentLoaded', () => {
    const balance3d = new Balance3D(AppConfig.balance3d.canvasId);
    const errorChart = new ErrorChart(AppConfig.chart.errorCanvasId);
    errorChart.maxPoints = AppConfig.chart.maxDataPoints;

    const panel = new MetrologyPanelController({
        balance3d: balance3d,
        errorChart: errorChart
    });

    panel.init().then(() => {
        console.log('[App] 古代天平衡器系统初始化完成');
        console.log('[App] 设备模式:', balance3d.isMobile ? '移动端' : '桌面端',
                    '| 性能等级:', balance3d.performanceLevel,
                    '| DPR:', balance3d.targetPixelRatio.toFixed(2));
    }).catch(err => console.error('[App] 初始化失败:', err));

    window.__metrology = { balance3d, errorChart, panel };
});
