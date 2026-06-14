class MetrologyApi {
    constructor(baseUrl) {
        this.baseUrl = baseUrl || AppConfig.API_BASE;
        this.timeout = AppConfig.api.timeoutMs;
    }

    async _fetch(url, options = {}) {
        const controller = new AbortController();
        const id = setTimeout(() => controller.abort(), this.timeout);
        try {
            const response = await fetch(this.baseUrl + url, {
                ...options,
                signal: controller.signal
            });
            clearTimeout(id);
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }
            return await response.json();
        } catch (e) {
            clearTimeout(id);
            throw e;
        }
    }

    getDynasties() { return this._fetch('/dynasties'); }
    getAllBalances() { return this._fetch('/balances/all'); }
    getBalanceStatistics() { return this._fetch('/balances/statistics'); }
    getBalanceMeasurements(balanceId, start, end) {
        let url = `/balances/${balanceId}/measurements`;
        if (start && end) {
            url += `?startTime=${encodeURIComponent(start)}&endTime=${encodeURIComponent(end)}`;
        }
        return this._fetch(url);
    }
    getAlertsByBalance(balanceId) { return this._fetch(`/alerts/balance/${balanceId}`); }
    runErrorAnalysis(balanceId, count) {
        const c = count || AppConfig.api.defaultSimulationCount;
        return this._fetch(`/error-analysis/${balanceId}?simulationCount=${c}`, { method: 'POST' });
    }
    runWeightSystemAnalysis(dynastyId, clusterCount) {
        let url = '/weight-system/analyze?clusterCount=' + (clusterCount || 0);
        if (dynastyId) url += `&dynastyId=${dynastyId}`;
        return this._fetch(url, { method: 'POST' });
    }
}

class MetrologyPanelController {
    constructor(options) {
        this.api = new MetrologyApi(AppConfig.API_BASE);
        this.balance3d = options.balance3d;
        this.errorChart = options.errorChart;

        this.balances = [];
        this.dynasties = [];
        this.currentBalance = null;
        this.stompClient = null;

        this._bindDOMElements();
    }

    _bindDOMElements() {
        this.el = {
            dynastyFilter: document.getElementById('dynastyFilter'),
            typeFilter: document.getElementById('typeFilter'),
            balanceList: document.getElementById('balanceList'),
            currentBalanceName: document.getElementById('currentBalanceName'),

            infoCode: document.getElementById('infoCode'),
            infoType: document.getElementById('infoType'),
            infoDynasty: document.getElementById('infoDynasty'),
            infoCapacity: document.getElementById('infoCapacity'),
            infoLeftArm: document.getElementById('infoLeftArm'),
            infoRightArm: document.getElementById('infoRightArm'),
            infoKnife: document.getElementById('infoKnife'),
            infoGrade: document.getElementById('infoGrade'),
            infoLocation: document.getElementById('infoLocation'),
            infoDesc: document.getElementById('infoDesc'),

            avgError: document.getElementById('avgError'),
            stdDev: document.getElementById('stdDev'),
            maxError: document.getElementById('maxError'),
            measureCount: document.getElementById('measureCount'),

            alertList: document.getElementById('alertList'),
            totalBalances: document.getElementById('totalBalances'),
            activeAlerts: document.getElementById('activeAlerts'),
            recentMeasurements: document.getElementById('recentMeasurements'),

            btnErrorAnalysis: document.getElementById('btnErrorAnalysis'),
            btnWeightSystem: document.getElementById('btnWeightSystem'),
            btnRotate: document.getElementById('btnRotate'),
            btnReset: document.getElementById('btnReset'),
            btnRunWS: document.getElementById('btnRunWS'),
            wsDynasty: document.getElementById('wsDynasty'),
            analysisResult: document.getElementById('analysisResult'),
            wsResult: document.getElementById('wsResult'),
            alertBanner: document.getElementById('alertBanner'),
            alertMessage: document.getElementById('alertMessage')
        };
    }

    async init() {
        this._setupEventListeners();
        this._setupTabs();
        await this._loadDynasties();
        await this._loadBalances();
        await this._loadStatistics();
        this._connectWebSocket();
    }

    _setupEventListeners() {
        this.el.dynastyFilter.addEventListener('change', () => this._filterBalances());
        this.el.typeFilter.addEventListener('change', () => this._filterBalances());

        this.el.btnErrorAnalysis.addEventListener('click', () => this._runErrorAnalysis());
        this.el.btnWeightSystem.addEventListener('click', () => this._showWeightSystemModal());
        this.el.btnRunWS.addEventListener('click', () => this._runWeightSystemAnalysis());

        this.el.btnRotate.addEventListener('click', (e) => {
            const active = this.balance3d.toggleAutoRotate();
            e.target.classList.toggle('active', active);
        });
        this.el.btnReset.addEventListener('click', () => this.balance3d.resetView());
    }

    _setupTabs() {
        const tabBtns = document.querySelectorAll('.tab-btn');
        tabBtns.forEach(btn => {
            btn.addEventListener('click', () => {
                const tabId = btn.dataset.tab;
                tabBtns.forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
                document.getElementById('tab-' + tabId).classList.add('active');
            });
        });
    }

    async _loadDynasties() {
        try {
            this.dynasties = await this.api.getDynasties();
            const fill = (select) => {
                this.dynasties.forEach(d => {
                    const opt = document.createElement('option');
                    opt.value = d.id;
                    opt.textContent = d.name;
                    select.appendChild(opt);
                });
            };
            fill(this.el.dynastyFilter);
            fill(this.el.wsDynasty);
        } catch (e) {
            console.error('加载朝代失败:', e);
        }
    }

    async _loadBalances() {
        try {
            this.balances = await this.api.getAllBalances();
            this._renderBalanceList(this.balances);
        } catch (e) {
            console.error('加载天平列表失败:', e);
            this._loadMockBalances();
        }
    }

    _loadMockBalances() {
        const mock = [];
        for (let i = 1; i <= 20; i++) {
            mock.push({
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
        this.balances = mock;
        this._renderBalanceList(mock);
    }

    _renderBalanceList(balances) {
        const list = this.el.balanceList;
        list.innerHTML = '';
        balances.forEach(b => {
            const item = document.createElement('div');
            item.className = 'balance-item';
            item.dataset.id = b.id;
            const isEqual = b.balanceType === 'EQUAL_ARM';
            item.innerHTML = `
                <div class="name">${b.name}</div>
                <div class="code">${b.balanceCode}</div>
                <span class="type-badge ${isEqual ? 'equal' : 'unequal'}">${isEqual ? '等臂' : '不等臂'}</span>
                <span class="alert-indicator ${b.isAlert ? 'active' : ''}"></span>`;
            item.addEventListener('click', () => this._selectBalance(b));
            list.appendChild(item);
        });
    }

    _filterBalances() {
        const did = this.el.dynastyFilter.value;
        const type = this.el.typeFilter.value;
        let filtered = this.balances;
        if (did) filtered = filtered.filter(b => b.dynastyId == did);
        if (type) filtered = filtered.filter(b => b.balanceType === type);
        this._renderBalanceList(filtered);
    }

    _selectBalance(balance) {
        this.currentBalance = balance;
        document.querySelectorAll('.balance-item').forEach(item => {
            item.classList.toggle('active', item.dataset.id == balance.id);
        });
        this.el.currentBalanceName.textContent = balance.name;
        this.balance3d.updateBalanceData(balance);
        this._updateInfoPanel(balance);
        this._loadMeasurements(balance.id);
        this._loadAlerts(balance.id);
    }

    _updateInfoPanel(balance) {
        const d = this.dynasties.find(x => x.id === balance.dynastyId);
        this.el.infoCode.textContent = balance.balanceCode || '-';
        this.el.infoType.textContent = balance.balanceType === 'EQUAL_ARM' ? '等臂天平' : '不等臂天平';
        this.el.infoDynasty.textContent = d?.name || '-';
        this.el.infoCapacity.textContent = balance.maxCapacity ? balance.maxCapacity + ' g' : '-';
        this.el.infoLeftArm.textContent = balance.leftArmLength ? balance.leftArmLength + ' mm' : '-';
        this.el.infoRightArm.textContent = balance.rightArmLength ? balance.rightArmLength + ' mm' : '-';
        this.el.infoKnife.textContent = balance.knifeEdgeRadius ? balance.knifeEdgeRadius + ' mm' : '-';
        this.el.infoGrade.textContent = balance.accuracyGrade || '-';
        this.el.infoLocation.textContent = balance.discoveryLocation || '-';
        this.el.infoDesc.textContent = balance.description || '-';
    }

    async _loadMeasurements(balanceId) {
        try {
            const m = await this.api.getBalanceMeasurements(balanceId);
            if (m && m.length > 0) {
                this.errorChart.setData(m);
                this._updateErrorStats();
            } else {
                this._generateMockMeasurements(balanceId);
            }
        } catch (e) {
            console.error('加载测量数据失败:', e);
            this._generateMockMeasurements(balanceId);
        }
    }

    _generateMockMeasurements(balanceId) {
        const data = [];
        const baseError = (Math.random() - 0.5) * 0.01;
        for (let i = 0; i < 50; i++) {
            data.push({
                measurementTime: new Date(Date.now() - (50 - i) * 3600000).toISOString(),
                weighingError: baseError + (Math.random() - 0.5) * 0.005,
                relativeError: (baseError + (Math.random() - 0.5) * 0.005) / 10,
                isAlert: Math.random() > 0.9
            });
        }
        this.errorChart.setData(data);
        this._updateErrorStats();
    }

    _updateErrorStats() {
        const s = this.errorChart.getStats();
        this.el.avgError.textContent = s.avg.toFixed(4) + ' g';
        this.el.stdDev.textContent = s.std.toFixed(4) + ' g';
        this.el.maxError.textContent = s.max.toFixed(4) + ' g';
        this.el.measureCount.textContent = s.count;
    }

    async _loadAlerts(balanceId) {
        try {
            const a = await this.api.getAlertsByBalance(balanceId);
            this._renderAlerts(a);
        } catch (e) {
            console.error('加载告警失败:', e);
            this._renderMockAlerts();
        }
    }

    _renderMockAlerts() {
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
        this._renderAlerts(alerts);
    }

    _renderAlerts(alerts) {
        if (!alerts || alerts.length === 0) {
            this.el.alertList.innerHTML = '<p class="placeholder-text">暂无告警记录</p>';
            return;
        }
        this.el.alertList.innerHTML = alerts.map(a => `
            <div class="alert-item ${a.alertLevel}">
                <div class="alert-header">
                    <span class="alert-level">${a.alertLevel}</span>
                    <span class="alert-time">${this._formatDate(a.createdAt)}</span>
                </div>
                <div class="alert-msg">${a.message}</div>
            </div>`).join('');
    }

    async _loadStatistics() {
        try {
            const s = await this.api.getBalanceStatistics();
            this.el.totalBalances.textContent = s.totalCount || 100;
            this.el.activeAlerts.textContent = s.activeAlerts || 0;
            this.el.recentMeasurements.textContent = s.recentMeasurements || 0;
        } catch (e) {
            console.error('加载统计数据失败:', e);
        }
    }

    async _runErrorAnalysis() {
        if (!this.currentBalance) { alert('请先选择一个天平'); return; }
        const btn = this.el.btnErrorAnalysis;
        btn.textContent = '分析中...';
        btn.disabled = true;
        try {
            const result = await this.api.runErrorAnalysis(this.currentBalance.id, 10000);
            this._renderAnalysisResult(result);
        } catch (e) {
            console.error('误差分析失败:', e);
            this._renderMockAnalysis();
        }
        btn.textContent = '误差分析(蒙特卡洛)';
        btn.disabled = false;
    }

    _renderMockAnalysis() {
        this._renderAnalysisResult({
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
        });
    }

    _renderAnalysisResult(result) {
        const hasHist = result.histogramBins && result.histogramCounts;
        const fmt = (v, d) => typeof v === 'number' ? v.toFixed(d) : v;
        const html = `
            <div class="analysis-grid">
                <div class="analysis-card">
                    <div class="value">${fmt(result.combinedUncertainty, 6)}</div>
                    <div class="label">合成不确定度 (g)</div>
                </div>
                <div class="analysis-card">
                    <div class="value">${fmt(result.expandedUncertainty, 6)}</div>
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
                        <span>刀口摩擦</span><span>${fmt(result.frictionContribution, 1)}%</span>
                    </div>
                    <div class="bar-track">
                        <div class="bar-fill friction" style="width:${result.frictionContribution}%"></div>
                    </div>
                </div>
                <div class="contribution-bar">
                    <div class="bar-label">
                        <span>臂长不等</span><span>${fmt(result.armLengthContribution, 1)}%</span>
                    </div>
                    <div class="bar-track">
                        <div class="bar-fill arm-length" style="width:${result.armLengthContribution}%"></div>
                    </div>
                </div>
                <div class="contribution-bar">
                    <div class="bar-label">
                        <span>砝码误差</span><span>${fmt(result.weightContribution, 1)}%</span>
                    </div>
                    <div class="bar-track">
                        <div class="bar-fill weight" style="width:${result.weightContribution}%"></div>
                    </div>
                </div>
            </div>
            ${hasHist ? '<div class="histogram-container" id="histogramContainer"></div>' : ''}`;
        this.el.analysisResult.innerHTML = html;
        if (hasHist) {
            const hc = new HistogramChart('histogramContainer');
            document.getElementById('histogramContainer').innerHTML =
                hc.render(result.histogramBins, result.histogramCounts);
        }
        document.querySelector('.tab-btn[data-tab="analysis"]').click();
    }

    _showWeightSystemModal() {
        document.getElementById('weightSystemModal').style.display = 'flex';
    }

    async _runWeightSystemAnalysis() {
        const dynastyId = this.el.wsDynasty.value;
        const btn = this.el.btnRunWS;
        btn.textContent = '分析中...';
        btn.disabled = true;
        try {
            const result = await this.api.runWeightSystemAnalysis(dynastyId, 0);
            this._renderWeightSystemResult(result);
        } catch (e) {
            console.error('权衡制度分析失败:', e);
            this._renderMockWeightSystemResult();
        }
        btn.textContent = '运行分析';
        btn.disabled = false;
    }

    _renderMockWeightSystemResult() {
        this._renderWeightSystemResult({
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
        });
    }

    _renderWeightSystemResult(result) {
        const fmt = (v, d) => typeof v === 'number' ? v.toFixed(d) : v;
        const clusters = (result.clusters || []).map((c, i) => `
            <div class="ws-cluster">
                <div class="cluster-info">
                    <div class="cluster-id">${i + 1}</div>
                    <div class="cluster-stats">
                        中心值: <strong>${fmt(c.center, 3)} g</strong>
                        &nbsp;&nbsp;范围: ${fmt(c.minValue, 3)} ~ ${fmt(c.maxValue, 3)} g
                        &nbsp;&nbsp;标准差: ${fmt(c.stdDev, 4)}
                    </div>
                </div>
                <div class="sample-count">${c.sampleCount} 样本</div>
            </div>`).join('');

        this.el.wsResult.innerHTML = `
            <div class="ws-summary">
                <div class="ws-summary-item">
                    <div class="value">${fmt(result.jinStandard, 2)} g</div>
                    <div class="label">推断斤标准 (16两)</div>
                </div>
                <div class="ws-summary-item">
                    <div class="value">${fmt(result.liangStandard, 4)} g</div>
                    <div class="label">推断两标准</div>
                </div>
                <div class="ws-summary-item">
                    <div class="value">${result.clusterCount}</div>
                    <div class="label">聚类数量</div>
                </div>
                <div class="ws-summary-item">
                    <div class="value">${fmt(result.silhouetteScore, 3)}</div>
                    <div class="label">轮廓系数</div>
                </div>
            </div>
            <h4 style="margin-bottom:12px;font-size:14px;color:#333;">聚类详情</h4>
            <div class="ws-clusters">${clusters}</div>
            <div style="margin-top:16px;padding:12px;background:#f5f7fa;border-radius:6px;font-size:12px;color:#666;">
                <strong>分析方法:</strong> ${result.method || 'K-Means'} 聚类分析
                <br><strong>说明:</strong> 基于出土砝码实际质量的聚类分析，推断该朝代的权衡制度标准。轮廓系数越接近1表示聚类效果越好。
            </div>`;
    }

    _connectWebSocket() {
        try {
            const socket = new SockJS(AppConfig.WS_URL);
            this.stompClient = Stomp.over(socket);
            this.stompClient.connect({}, () => {
                console.log('WebSocket连接成功');
                this.stompClient.subscribe(AppConfig.WS_ALERT_TOPIC, (msg) => {
                    this._handleNewAlert(JSON.parse(msg.body));
                });
            }, (err) => console.log('WebSocket连接失败，使用轮询模式:', err));
        } catch (e) {
            console.log('WebSocket初始化失败:', e);
        }
    }

    _handleNewAlert(alert) {
        this._showAlertBanner(alert);
        const cur = parseInt(this.el.activeAlerts.textContent) || 0;
        this.el.activeAlerts.textContent = cur + 1;
        if (this.currentBalance && alert.balanceId === this.currentBalance.id) {
            this._loadAlerts(this.currentBalance.id);
        }
        const item = document.querySelector(`.balance-item[data-id="${alert.balanceId}"]`);
        const ind = item?.querySelector('.alert-indicator');
        if (ind) ind.classList.add('active');
    }

    _showAlertBanner(alert) {
        this.el.alertMessage.textContent = alert.message || '新的告警';
        this.el.alertBanner.style.display = 'flex';
        setTimeout(() => {
            this.el.alertBanner.style.display = 'none';
        }, AppConfig.api.alertBannerDurationMs);
    }

    _formatDate(dateStr) {
        if (!dateStr) return '-';
        return new Date(dateStr).toLocaleString('zh-CN', {
            month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
        });
    }
}

function closeModal(modalId) {
    document.getElementById(modalId).style.display = 'none';
}
