document.addEventListener('DOMContentLoaded', function () {
    const API_BASE_URL = window.BPS_API_BASE_URL || 'http://localhost:8080';
    const auth = window.BPSAuth;

    if (!auth || !auth.requireAuth('login.html')) return;
    auth.startExpiryWatcher('login.html');

    const avgRiskValue = document.getElementById('avg-risk-value');
    const avgRiskProgress = document.getElementById('avg-risk-progress');
    const highRiskCount = document.getElementById('high-risk-count');
    const highRiskTotal = document.getElementById('high-risk-total');
    const errorBlock = document.getElementById('dashboard-error');
    const settingsBtn = document.getElementById('settingsBtn');
    const userRole = auth.getRole ? auth.getRole() : null;

    if (userRole !== 'ROLE_ADMIN') {
        if (settingsBtn) {
            settingsBtn.style.display = 'none';
        }
    }

    function showError(message) {
        if (!errorBlock) return;
        errorBlock.textContent = message;
        errorBlock.classList.remove('hidden');
    }

    function hideError() {
        if (!errorBlock) return;
        errorBlock.textContent = '';
        errorBlock.classList.add('hidden');
    }

    function toPercent(value) {
        const n = Number(value);
        if (Number.isNaN(n)) return 0;
        const normalized = n > 1 ? n / 100 : n;
        const clamped = Math.max(0, Math.min(1, normalized));
        return Math.round(clamped * 100);
    }

    function normalizeStats(raw) {
        const s = raw || {};
        const averageRiskPercent = Number.isFinite(Number(s.averageRiskPercent))
            ? Math.max(0, Math.min(100, Math.round(Number(s.averageRiskPercent))))
            : toPercent(s.averageRisk);

        return {
            averageRiskPercent,
            averageRiskTrendUp: Boolean(s.averageRiskTrendUp),
            highRiskCount: Number(s.highRiskCount ?? 0),
            totalEmployees: Number(s.totalEmployees ?? 0),
            lastEtlRun: s.lastEtlRun || 'Н/Д',
            nextEtlRun: s.nextEtlRun || 'Н/Д',
            activeModelVersion: s.activeModelVersion || 'Н/Д'
        };
    }

    function updateCards(rawStats) {
        const stats = normalizeStats(rawStats);
        if (avgRiskValue) avgRiskValue.textContent = stats.averageRiskPercent + '%';
        if (avgRiskProgress) avgRiskProgress.style.width = stats.averageRiskPercent + '%';
        if (highRiskCount) highRiskCount.textContent = String(stats.highRiskCount);
        if (highRiskTotal) highRiskTotal.textContent = '/ ' + stats.totalEmployees + ' Всего';
        const trendIcon = document.getElementById('avg-risk-trend');

        const avgRiskYesterdayNode = document.getElementById('avg-risk-yesterday');
        if (avgRiskYesterdayNode && rawStats.averageRiskPercentYesterday !== undefined) {
            avgRiskYesterdayNode.textContent = 'Вчера было: ' + rawStats.averageRiskPercentYesterday + '%';
        }

        if (trendIcon) {
            trendIcon.textContent = stats.averageRiskTrendUp ? 'trending_up' : 'trending_down';
            trendIcon.className = stats.averageRiskTrendUp
                ? 'material-symbols-outlined text-primary-container text-lg'
                : 'material-symbols-outlined text-primary-container text-lg';
        }
        const lastEtlRunNode = document.getElementById('last-etl-run');
        const nextEtlRunNode = document.getElementById('next-etl-run');
        const modelVersionNode = document.getElementById('active-model-version');

        if (lastEtlRunNode) lastEtlRunNode.textContent = stats.lastEtlRun;
        if (nextEtlRunNode) nextEtlRunNode.textContent = stats.nextEtlRun;
        if (modelVersionNode) modelVersionNode.textContent = stats.activeModelVersion;
    }

    async function loadSummary() {
        hideError();
        try {
            const response = await auth.fetchWithAuth(API_BASE_URL + '/api/v1/burnout/analytics/summary', {
                method: 'GET'
            });

            if (response.status === 401) { auth.handleUnauthorized('login.html'); return; }
            if (response.status === 403) { window.location.replace(auth.getDefaultDashboardPath()); return; }

            if (!response.ok) throw new Error('Не удалось загрузить сводку по сотрудникам.');

            const data = await response.json();
            updateCards(data.stats);
        } catch (error) {
            showError(error.message || 'Ошибка загрузки данных dashboard.');
        }
    }
    loadSummary();
});
