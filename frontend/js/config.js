const AppConfig = {
    API_BASE: 'http://localhost:8080/api',
    WS_URL: 'http://localhost:8080/api/ws',
    WS_ALERT_TOPIC: '/topic/alerts',

    balance3d: {
        canvasId: 'threeCanvas',
        minCameraDistance: 80,
        maxCameraDistance: 400,
        lodSwitchDistance: 350,
        autoRotateSpeed: 0.005,
        physics: {
            stiffness: 25.0,
            damping: 1.5,
            beamArmLength: 85,
            lowDetailSineFreq: 1.5,
            lowDetailSineAmp: 0.02,
            fixedStepHigh: 0.016,
            fixedStepLow: 0.05
        },
        performance: {
            fpsDropThrottle2: 30,
            fpsDropThrottle3: 20,
            fpsRecoveryContinuousSec: 3
        },
        mobile: {
            rotateSensitivity: 0.008,
            desktopRotateSensitivity: 0.01,
            zoomSensitivity: 0.5,
            desktopZoomSensitivity: 0.3
        }
    },

    chart: {
        errorCanvasId: 'errorChart',
        maxDataPoints: 100,
        histogramBins: 50
    },

    api: {
        timeoutMs: 15000,
        defaultSimulationCount: 10000,
        alertBannerDurationMs: 5000
    },

    dynastyList: [
        "战国", "秦", "西汉", "东汉", "三国", "西晋", "东晋", "南北朝",
        "隋", "唐", "五代十国", "北宋", "南宋", "元", "明", "清"
    ],

    accuracyGrades: ['特级', '一级', '二级', '三级', '等外']
};

if (typeof window !== 'undefined') {
    window.AppConfig = AppConfig;
}
