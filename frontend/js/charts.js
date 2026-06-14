class ErrorChart {
    constructor(canvasId) {
        this.canvas = document.getElementById(canvasId);
        this.ctx = this.canvas.getContext('2d');
        this.data = [];
        this.maxPoints = 100;
        this.animFrame = null;
    }

    setData(measurements) {
        this.data = measurements.slice(-this.maxPoints).map(m => ({
            time: new Date(m.measurementTime),
            error: m.weighingError,
            relativeError: m.relativeError,
            isAlert: m.isAlert
        }));
        this.draw();
    }

    addDataPoint(measurement) {
        this.data.push({
            time: new Date(measurement.measurementTime),
            error: measurement.weighingError,
            relativeError: measurement.relativeError,
            isAlert: measurement.isAlert
        });

        if (this.data.length > this.maxPoints) {
            this.data.shift();
        }

        this.draw();
    }

    draw() {
        const ctx = this.ctx;
        const width = this.canvas.width = this.canvas.offsetWidth * window.devicePixelRatio;
        const height = this.canvas.height = this.canvas.offsetHeight * window.devicePixelRatio;
        ctx.scale(window.devicePixelRatio, window.devicePixelRatio);

        const w = this.canvas.offsetWidth;
        const h = this.canvas.offsetHeight;

        ctx.clearRect(0, 0, w, h);

        const padding = { top: 20, right: 20, bottom: 30, left: 60 };
        const chartWidth = w - padding.left - padding.right;
        const chartHeight = h - padding.top - padding.bottom;

        ctx.strokeStyle = '#e0e0e0';
        ctx.lineWidth = 1;
        for (let i = 0; i <= 5; i++) {
            const y = padding.top + (chartHeight / 5) * i;
            ctx.beginPath();
            ctx.moveTo(padding.left, y);
            ctx.lineTo(w - padding.right, y);
            ctx.stroke();
        }

        ctx.strokeStyle = '#999';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(padding.left, padding.top);
        ctx.lineTo(padding.left, h - padding.bottom);
        ctx.stroke();

        ctx.beginPath();
        ctx.moveTo(padding.left, h - padding.bottom);
        ctx.lineTo(w - padding.right, h - padding.bottom);
        ctx.stroke();

        if (this.data.length < 2) {
            ctx.fillStyle = '#999';
            ctx.font = '12px sans-serif';
            ctx.textAlign = 'center';
            ctx.fillText('数据不足', w / 2, h / 2);
            return;
        }

        let minError = Infinity;
        let maxError = -Infinity;
        this.data.forEach(d => {
            if (d.error < minError) minError = d.error;
            if (d.error > maxError) maxError = d.error;
        });

        const margin = Math.max(Math.abs(minError), Math.abs(maxError)) * 0.2 || 0.01;
        minError -= margin;
        maxError += margin;

        ctx.fillStyle = '#666';
        ctx.font = '10px sans-serif';
        ctx.textAlign = 'right';
        ctx.textBaseline = 'middle';
        for (let i = 0; i <= 5; i++) {
            const y = padding.top + (chartHeight / 5) * i;
            const value = maxError - ((maxError - minError) / 5) * i;
            ctx.fillText(value.toFixed(4) + 'g', padding.left - 5, y);
        }

        const zeroY = padding.top + chartHeight * ((maxError - 0) / (maxError - minError));
        ctx.strokeStyle = '#1976d2';
        ctx.lineWidth = 1;
        ctx.setLineDash([5, 3]);
        ctx.beginPath();
        ctx.moveTo(padding.left, zeroY);
        ctx.lineTo(w - padding.right, zeroY);
        ctx.stroke();
        ctx.setLineDash([]);

        ctx.beginPath();
        ctx.strokeStyle = '#1976d2';
        ctx.lineWidth = 2;

        this.data.forEach((d, i) => {
            const x = padding.left + (chartWidth / (this.data.length - 1)) * i;
            const y = padding.top + chartHeight * ((maxError - d.error) / (maxError - minError));

            if (i === 0) {
                ctx.moveTo(x, y);
            } else {
                ctx.lineTo(x, y);
            }
        });
        ctx.stroke();

        const gradient = ctx.createLinearGradient(0, padding.top, 0, h - padding.bottom);
        gradient.addColorStop(0, 'rgba(25, 118, 210, 0.3)');
        gradient.addColorStop(1, 'rgba(25, 118, 210, 0.02)');

        ctx.beginPath();
        ctx.moveTo(padding.left, h - padding.bottom);
        this.data.forEach((d, i) => {
            const x = padding.left + (chartWidth / (this.data.length - 1)) * i;
            const y = padding.top + chartHeight * ((maxError - d.error) / (maxError - minError));
            ctx.lineTo(x, y);
        });
        ctx.lineTo(w - padding.right, h - padding.bottom);
        ctx.closePath();
        ctx.fillStyle = gradient;
        ctx.fill();

        this.data.forEach((d, i) => {
            const x = padding.left + (chartWidth / (this.data.length - 1)) * i;
            const y = padding.top + chartHeight * ((maxError - d.error) / (maxError - minError));

            if (d.isAlert) {
                ctx.beginPath();
                ctx.arc(x, y, 4, 0, Math.PI * 2);
                ctx.fillStyle = '#ff5252';
                ctx.fill();
                ctx.strokeStyle = '#d32f2f';
                ctx.lineWidth = 1;
                ctx.stroke();
            }
        });

        ctx.fillStyle = '#888';
        ctx.font = '10px sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'top';

        if (this.data.length > 0) {
            const firstTime = this.data[0].time;
            const lastTime = this.data[this.data.length - 1].time;

            ctx.fillText(this.formatTime(firstTime), padding.left, h - padding.bottom + 5);
            ctx.fillText(this.formatTime(lastTime), w - padding.right, h - padding.bottom + 5);
        }

        ctx.fillStyle = '#666';
        ctx.font = '11px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText('称量误差 (g)', w / 2, 10);
    }

    formatTime(date) {
        return date.getHours().toString().padStart(2, '0') + ':' +
               date.getMinutes().toString().padStart(2, '0');
    }

    getStats() {
        if (this.data.length === 0) {
            return { avg: 0, std: 0, max: 0, count: 0 };
        }

        const errors = this.data.map(d => d.error);
        const avg = errors.reduce((a, b) => a + b, 0) / errors.length;
        const variance = errors.reduce((sum, e) => sum + (e - avg) ** 2, 0) / errors.length;
        const std = Math.sqrt(variance);
        const max = Math.max(...errors.map(e => Math.abs(e)));

        return {
            avg: avg,
            std: std,
            max: max,
            count: this.data.length
        };
    }
}

class HistogramChart {
    constructor(containerId) {
        this.container = document.getElementById(containerId);
    }

    render(bins, counts) {
        if (!bins || !counts || bins.length === 0) return '';

        const maxCount = Math.max(...counts);

        let html = '<div class="histogram-bars" style="display:flex;align-items:flex-end;height:100%;gap:1px;">';

        counts.forEach((count, i) => {
            const height = maxCount > 0 ? (count / maxCount) * 100 : 0;
            const binValue = bins[i];

            html += `
                <div class="histogram-bar" 
                     style="flex:1;background:linear-gradient(180deg,#4fc3f7,#1976d2);
                            height:${height}%;min-height:2px;border-radius:2px 2px 0 0;
                            position:relative;" 
                     title="${binValue?.toFixed?.(4) || binValue}: ${count}">
                </div>
            `;
        });

        html += '</div>';
        return html;
    }
}
