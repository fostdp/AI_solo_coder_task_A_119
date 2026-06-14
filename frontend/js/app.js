const API_BASE = 'http://localhost:8080/api';
const WS_URL = 'http://localhost:8080/api/ws';

class BalanceApp {
    constructor() {
        this.balance3d = null;
        this.errorChart = null;
        this.currentBalance = null;
        this.balances = [];
        this.dynasties = [];
        this.stompClient = null;

        this.init();
    }

    init() {
        this.balance3d = new Balance3D('threeCanvas');
        this.errorChart = new ErrorChart('errorChart');

        this.balance3d.onBalanceClick = (data) => {
            this.showComponentInfo(data);
        };

        this.loadDynasties();
        this.loadBalances();
        this.loadStatistics();
        this.setupEventListeners();
        this.connectWebSocket();
        this.setupTabs();
    }

    setupEventListeners() {
        document.getElementById('dynastyFilter').addEventListener('change', () => this.filterBalances());
        document.getElementById('typeFilter').addEventListener('change', () => this.filterBalances());

        document.getElementById('btnErrorAnalysis').addEventListener('click', () => this.runErrorAnalysis());
        document.getElementById('btnWeightSystem').addEventListener('click', () => this.showWeightSystemModal());

        document.getElementById('btnRotate').addEventListener('click', (e) => {
            const isActive = this.balance3d.toggleAutoRotate();
            e.target.classList.toggle('active', isActive);
        });

        document.getElementById('btnReset').addEventListener('click', () => {
            this.balance3d.resetView();
        });

        document.getElementById('btnRunWS').addEventListener('click', () => this.runWeightSystemAnalysis());
    }

    setupTabs() {
        const tabBtns = document.querySelectorAll('.tab-btn');
        tabBtns.forEach(btn => {
            btn.addEventListener('click', () => {
                const tabId = btn.dataset.tab;
                tabBtns.forEach(b => b.classList.remove('active'));
                btn.classList.add('active');

                document.querySelectorAll('.tab-content').forEach(content => {
                    content.classList.remove('active');
                });
                document.getElementById('tab-' + tabId).classList.add('active');
            });
        });
    }

    async loadDynasties() {
        try {
            const response = await fetch(`${API_BASE}/dynasties`);
            this.dynasties = await response.json();

            const select = document.getElementById('dynastyFilter');
            const wsSelect = document.getElementById('wsDynasty');

            this.dynasties.forEach(d => {
                const option = document.createElement('option');
                option.value = d.id;
                option.textContent = d.name;
                select.appendChild(option);

                const wsOption = option.cloneNode(true);
                wsSelect.appendChild(wsOption);
            });
        } catch (e) {
            console.error('加载朝代失败:', e);
        }
    }

    async loadBalances() {
        try {
            const response = await fetch(`${API_BASE}/balances/all`);
            this.balances = await response.json();
            this.renderBalanceList(this.balances);
        } catch (e) {
            console.error('加载天平列表失败:', e);
            this.loadMockBalances();
        }
    }

    loadMockBalances() {
        const mockBalances = [];
        for (let i = 1; i <= 20; i++) {
            mockBalances.push({
                id: i,
                balanceCode: 'BAL-' + String(i).padStart(4, '0'),
                name: '青铜等臂天平 第' + i + '号',
                balanceType: i % 3 === 0 ? 'UNEQUAL_ARM' : 'EQUAL_ARM',
                dynastyId: (i % 16) + 1,
                maxCapacity: 50 + (i % 20) * 25,
                leftArmLength: 150 + (i % 10) * 5,
                rightArmLength: 150 + (i % 10) * 5 + (i % 3 === 0 ? 10 : 0),
                knifeEdgeRadius: 1.5 + (i % 10) * 0.1,
                discoveryLocation: '河南安阳',
                material: '青铜',
                description: '出土于河南安阳的古代天平',
                accuracyGrade: i % 5 === 0 ? '一级' : '二级',
                allowableError: 0.005
            });
        }
        this.balances = mockBalances;
        this.renderBalanceList(mockBalances);
    }

    renderBalanceList(balances) {
        const list = document.getElementById('balanceList');
        list.innerHTML = '';

        balances.forEach(balance => {
            const item = document.createElement('div');
            item.className = 'balance-item';
            item.dataset.id = balance.id;

            const isEqual = balance.balanceType === 'EQUAL_ARM';
            const typeText = isEqual ? '等臂' : '不等臂';
            const typeClass = isEqual ? 'equal' : 'unequal';

            item.innerHTML = `
                <div class="name">${balance.name}</div>
                <div class="code">${balance.balanceCode}</div>
                <span class="type-badge ${typeClass}">${typeText}</span>
                <span class="alert-indicator ${balance.isAlert ? 'active' : ''}"></span>
            `;

            item.addEventListener('click', () => this.selectBalance(balance));
            list.appendChild(item);
        });
    }

    filterBalances() {
        const dynastyId = document.getElementById('dynastyFilter').value;
        const type = document.getElementById('typeFilter').value;

        let filtered = this.balances;

        if (dynastyId) {
            filtered = filtered.filter(b => b.dynastyId == dynastyId);
        }
        if (type) {
            filtered = filtered.filter(b => b.balanceType === type);
        }

        this.renderBalanceList(filtered);
    }

    selectBalance(balance) {
        this.currentBalance = balance;

        document.querySelectorAll('.balance-item').forEach(item => {
            item.classList.remove('active');
            if (item.dataset.id == balance.id) {
                item.classList.add('active');
            }
        });

        document.getElementById('currentBalanceName').textContent = balance.name;
        this.balance3d.updateBalanceData(balance);

        this.updateInfoPanel(balance);
        this.loadMeasurements(balance.id);
        this.loadAlerts(balance.id);
    }

    updateInfoPanel(balance) {
        const dynasty = this.dynasties.find(d => d.id === balance.dynastyId);

        document.getElementById('infoCode').textContent = balance.balanceCode || '-';
        document.getElementById('infoType').textContent = 
            balance.balanceType === 'EQUAL_ARM' ? '等臂天平' : '不等臂天平';
        document.getElementById('infoDynasty').textContent = dynasty?.name || '-';
        document.getElementById('infoCapacity').textContent = 
            balance.maxCapacity ? balance.maxCapacity + ' g' : '-';
        document.getElementById('infoLeftArm').textContent = 
            balance.leftArmLength ? balance.leftArmLength + ' mm' : '-';
        document.getElementById('infoRightArm').textContent = 
            balance.rightArmLength ? balance.rightArmLength + ' mm' : '-';
        document.getElementById('infoKnife').textContent = 
            balance.knifeEdgeRadius ? balance.knifeEdgeRadius + ' mm' : '-';
        document.getElementById('infoGrade').textContent = balance.accuracyGrade || '-';
        document.getElementById('infoLocation').textContent = balance.discoveryLocation || '-';
        document.getElementById('infoDesc').textContent = balance.description || '-';
    }

    async loadMeasurements(balanceId) {
        try {
            const response = await fetch(
                `${API_BASE}/balances/${balanceId}/measurements`
            );
            const measurements = await response.json();

            if (measurements && measurements.length > 0) {
                this.errorChart.setData(measurements);
                this.updateErrorStats();
            } else {
                this.generateMockMeasurements(balanceId);
            }
        } catch (e) {
            console.error('加载测量数据失败:', e);
            this.generateMockMeasurements(balanceId);
        }
    }

    generateMockMeasurements(balanceId) {
        const mockData = [];
        const baseError = (Math.random() - 0.5) * 0.01;

        for (let i = 0; i < 50; i++) {
            mockData.push({
                measurementTime: new Date(Date.now() - (50 - i) * 3600000).toISOString(),
                weighingError: baseError + (Math.random() - 0.5) * 0.005,
                relativeError: (baseError + (Math.random() - 0.5) * 0.005) / 10,
                isAlert: Math.random() > 0.9
            });
        }

        this.errorChart.setData(mockData);
        this.updateErrorStats();
    }

    updateErrorStats() {
        const stats = this.errorChart.getStats();

        document.getElementById('avgError').textContent = stats.avg.toFixed(4) + ' g';
        document.getElementById('stdDev').textContent = stats.std.toFixed(4) + ' g';
        document.getElementById('maxError').textContent = stats.max.toFixed(4) + ' g';
        document.getElementById('measureCount').textContent = stats.count;
    }

    async loadAlerts(balanceId) {
        try {
            const response = await fetch(`${API_BASE}/alerts/balance/${balanceId}`);
            const alerts = await response.json();
            this.renderAlerts(alerts);
        } catch (e) {
            console.error('加载告警失败:', e);
            this.renderMockAlerts();
        }
    }

    renderMockAlerts() {
        const alerts = [];
        for (let i = 0; i < 3; i++) {
            alerts.push({
                id: i + 1,
                alertLevel: i === 0 ? 'CRITICAL' : 'WARNING',
                message: '称量误差超过允许范围，当前误差 0.012g',
                createdAt: new Date(Date.now() - i * 7200000).toISOString(),
                isResolved: false
            });
        }
        this.renderAlerts(alerts);
    }

    renderAlerts(alerts) {
        const container = document.getElementById('alertList');

        if (!alerts || alerts.length === 0) {
            container.innerHTML = '<p class="placeholder-text">暂无告警记录</p>';
            return;
        }

        container.innerHTML = alerts.map(alert => `
            <div class="alert-item ${alert.alertLevel}">
                <div class="alert-header">
                    <span class="alert-level">${alert.alertLevel}</span>
                    <span class="alert-time">${this.formatDate(alert.createdAt)}</span>
                </div>
                <div class="alert-msg">${alert.message}</div>
            </div>
        `).join('');
    }

    async loadStatistics() {
        try {
            const response = await fetch(`${API_BASE}/balances/statistics`);
            const stats = await response.json();

            document.getElementById('totalBalances').textContent = stats.totalCount || 100;
            document.getElementById('activeAlerts').textContent = stats.activeAlerts || 0;
            document.getElementById('recentMeasurements').textContent = stats.recentMeasurements || 0;
        } catch (e) {
            console.error('加载统计数据失败:', e);
        }
    }

    async runErrorAnalysis() {
        if (!this.currentBalance) {
            alert('请先选择一个天平');
            return;
        }

        const btn = document.getElementById('btnErrorAnalysis');
        btn.textContent = '分析中...';
        btn.disabled = true;

        try {
            const response = await fetch(
                `${API_BASE}/error-analysis/${this.currentBalance.id}?simulationCount=10000`,
                { method: 'POST' }
            );

            if (!response.ok) {
                throw new Error('分析失败');
            }

            const result = await response.json();
            this.renderAnalysisResult(result);
        } catch (e) {
            console.error('误差分析失败:', e);
            this.renderMockAnalysis();
        }

        btn.textContent = '误差分析(蒙特卡洛)';
        btn.disabled = false;
    }

    renderMockAnalysis() {
        const mockResult = {
            simulationCount: 10000,
            meanError: 0.0023,
            stdDeviation: 0.0045,
            combinedUncertainty: 0.0045,
            expandedUncertainty: 0.0090,
            coverageFactor: 2.0,
            frictionContribution: 35.2,
            armLengthContribution: 45.8,
            weightContribution: 19.0,
            accuracyGrade: '二级'
        };
        this.renderAnalysisResult(mockResult);
    }

    renderAnalysisResult(result) {
        const container = document.getElementById('analysisResult');

        const histogramHtml = result.histogramBins && result.histogramCounts
            ? `<div class="histogram-container" id="histogramContainer"></div>`
            : '';

        container.innerHTML = `
            <div class="analysis-grid">
                <div class="analysis-card">
                    <div class="value">${result.combinedUncertainty?.toFixed?.(6) || result.combinedUncertainty}</div>
                    <div class="label">合成不确定度 (g)</div>
                </div>
                <div class="analysis-card">
                    <div class="value">${result.expandedUncertainty?.toFixed?.(6) || result.expandedUncertainty}</div>
                    <div class="label">扩展不确定度 (g, k=${result.coverageFactor})</div>
                </div>
                <div class="analysis-card">
                    <div class="value">${result.simulationCount?.toLocaleString?.() || result.simulationCount}</div>
                    <div class="label">蒙特卡洛模拟次数</div>
                </div>
            </div>

            <div style="text-align:center;">
                <span class="accuracy-badge ${result.accuracyGrade}">精度等级: ${result.accuracyGrade}</span>
            </div>

            <div class="contribution-bars">
                <h4 style="margin-bottom:12px;font-size:13px;color:#555;">误差来源贡献占比</h4>
                <div class="contribution-bar">
                    <div class="bar-label">
                        <span>刀口摩擦</span>
                        <span>${result.frictionContribution?.toFixed?.(1) || result.frictionContribution}%</span>
                    </div>
                    <div class="bar-track">
                        <div class="bar-fill friction" style="width:${result.frictionContribution}%"></div>
                    </div>
                </div>
                <div class="contribution-bar">
                    <div class="bar-label">
                        <span>臂长不等</span>
                        <span>${result.armLengthContribution?.toFixed?.(1) || result.armLengthContribution}%</span>
                    </div>
                    <div class="bar-track">
                        <div class="bar-fill arm-length" style="width:${result.armLengthContribution}%"></div>
                    </div>
                </div>
                <div class="contribution-bar">
                    <div class="bar-label">
                        <span>砝码误差</span>
                        <span>${result.weightContribution?.toFixed?.(1) || result.weightContribution}%</span>
                    </div>
                    <div class="bar-track">
                        <div class="bar-fill weight" style="width:${result.weightContribution}%"></div>
                    </div>
                </div>
            </div>

            ${histogramHtml}
        `;

        if (result.histogramBins && result.histogramCounts) {
            const histogram = new HistogramChart('histogramContainer');
            document.getElementById('histogramContainer').innerHTML = 
                histogram.render(result.histogramBins, result.histogramCounts);
        }

        document.querySelector('.tab-btn[data-tab="analysis"]').click();
    }

    showWeightSystemModal() {
        document.getElementById('weightSystemModal').style.display = 'flex';
    }

    async runWeightSystemAnalysis() {
        const dynastyId = document.getElementById('wsDynasty').value;
        const btn = document.getElementById('btnRunWS');
        btn.textContent = '分析中...';
        btn.disabled = true;

        try {
            const url = dynastyId 
                ? `${API_BASE}/weight-system/analyze?dynastyId=${dynastyId}&clusterCount=0`
                : `${API_BASE}/weight-system/analyze?clusterCount=0`;

            const response = await fetch(url, { method: 'POST' });
            const result = await response.json();
            this.renderWeightSystemResult(result);
        } catch (e) {
            console.error('权衡制度分析失败:', e);
            this.renderMockWeightSystemResult();
        }

        btn.textContent = '运行分析';
        btn.disabled = false;
    }

    renderMockWeightSystemResult() {
        const mockResult = {
            clusterCount: 4,
            silhouetteScore: 0.723,
            jinStandard: 250.5,
            liangStandard: 15.65625,
            method: 'K_MEANS',
            clusters: [
                { clusterId: 0, center: 15.2, sampleCount: 12, minValue: 14.8, maxValue: 15.8, stdDev: 0.25 },
                { clusterId: 1, center: 31.5, sampleCount: 8, minValue: 30.8, maxValue: 32.2, stdDev: 0.42 },
                { clusterId: 2, center: 62.8, sampleCount: 5, minValue: 61.5, maxValue: 64.0, stdDev: 0.85 },
                { clusterId: 3, center: 125.5, sampleCount: 3, minValue: 124.0, maxValue: 127.0, stdDev: 1.2 }
            ]
        };
        this.renderWeightSystemResult(mockResult);
    }

    renderWeightSystemResult(result) {
        const container = document.getElementById('wsResult');

        const clustersHtml = result.clusters?.map((c, i) => `
            <div class="ws-cluster">
                <div class="cluster-info">
                    <div class="cluster-id">${i + 1}</div>
                    <div class="cluster-stats">
                        中心值: <strong>${c.center?.toFixed?.(3) || c.center} g</strong>
                        &nbsp;&nbsp;范围: ${c.minValue?.toFixed?.(3) || c.minValue} ~ ${c.maxValue?.toFixed?.(3) || c.maxValue} g
                        &nbsp;&nbsp;标准差: ${c.stdDev?.toFixed?.(4) || c.stdDev}
                    </div>
                </div>
                <div class="sample-count">${c.sampleCount} 样本</div>
            </div>
        `).join('') || '';

        container.innerHTML = `
            <div class="ws-summary">
                <div class="ws-summary-item">
                    <div class="value">${result.jinStandard?.toFixed?.(2) || result.jinStandard} g</div>
                    <div class="label">推断斤标准 (16两)</div>
                </div>
                <div class="ws-summary-item">
                    <div class="value">${result.liangStandard?.toFixed?.(4) || result.liangStandard} g</div>
                    <div class="label">推断两标准</div>
                </div>
                <div class="ws-summary-item">
                    <div class="value">${result.clusterCount}</div>
                    <div class="label">聚类数量</div>
                </div>
                <div class="ws-summary-item">
                    <div class="value">${result.silhouetteScore?.toFixed?.(3) || result.silhouetteScore}</div>
                    <div class="label">轮廓系数</div>
                </div>
            </div>

            <h4 style="margin-bottom:12px;font-size:14px;color:#333;">聚类详情</h4>
            <div class="ws-clusters">
                ${clustersHtml}
            </div>

            <div style="margin-top:16px;padding:12px;background:#f5f7fa;border-radius:6px;font-size:12px;color:#666;">
                <strong>分析方法:</strong> ${result.method || 'K-Means'} 聚类分析
                <br>
                <strong>说明:</strong> 基于出土砝码实际质量的聚类分析，推断该朝代的权衡制度标准。
                轮廓系数越接近1表示聚类效果越好。
            </div>
        `;
    }

    showComponentInfo(data) {
        console.log('点击组件:', data);
    }

    connectWebSocket() {
        try {
            const socket = new SockJS(WS_URL);
            this.stompClient = Stomp.over(socket);

            this.stompClient.connect({}, () => {
                console.log('WebSocket连接成功');
                this.stompClient.subscribe('/topic/alerts', (message) => {
                    const alert = JSON.parse(message.body);
                    this.handleNewAlert(alert);
                });
            }, (error) => {
                console.log('WebSocket连接失败，使用轮询模式:', error);
            });
        } catch (e) {
            console.log('WebSocket初始化失败:', e);
        }
    }

    handleNewAlert(alert) {
        this.showAlertBanner(alert);

        const alertCount = parseInt(document.getElementById('activeAlerts').textContent) || 0;
        document.getElementById('activeAlerts').textContent = alertCount + 1;

        if (this.currentBalance && alert.balanceId === this.currentBalance.id) {
            this.loadAlerts(this.currentBalance.id);
        }

        const balanceItem = document.querySelector(`.balance-item[data-id="${alert.balanceId}"]`);
        if (balanceItem) {
            const indicator = balanceItem.querySelector('.alert-indicator');
            if (indicator) {
                indicator.classList.add('active');
            }
        }
    }

    showAlertBanner(alert) {
        const banner = document.getElementById('alertBanner');
        document.getElementById('alertMessage').textContent = alert.message || '新的告警';
        banner.style.display = 'flex';

        setTimeout(() => {
            banner.style.display = 'none';
        }, 5000);
    }

    formatDate(dateStr) {
        if (!dateStr) return '-';
        const date = new Date(dateStr);
        return date.toLocaleString('zh-CN', {
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    }
}

function closeModal(modalId) {
    document.getElementById(modalId).style.display = 'none';
}

document.addEventListener('DOMContentLoaded', () => {
    window.app = new BalanceApp();
});

document.getElementById('weightSystemModal').addEventListener('click', (e) => {
    if (e.target.id === 'weightSystemModal') {
        closeModal('weightSystemModal');
    }
});
