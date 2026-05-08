document.addEventListener('DOMContentLoaded', function () {
    const API_BASE_URL = window.BPS_API_BASE_URL || 'http://localhost:8080';
    const auth = window.BPSAuth;

    if (!auth || !auth.requireAuth('login.html')) return;

    if (auth.getRole && auth.getRole() === 'ROLE_VIEWER') {
        window.location.replace('dashboardViewer.html');
        return;
    }

    auth.startExpiryWatcher('login.html');

    const userRole = auth.getRole ? auth.getRole() : null;
    const params = new URLSearchParams(window.location.search);
    const employeeId = params.get('employeeId');
    const settingsBtn = document.getElementById('settingsBtn');

    if (userRole !== 'ROLE_ADMIN') {
        if (settingsBtn) {
            settingsBtn.style.display = 'none';
        }
    }

    const ui = {
        title: document.getElementById('employee-id-title'),
        riskPercent: document.getElementById('risk-percent'),
        riskLabel: document.getElementById('risk-label'),
        riskProgress: document.getElementById('risk-progress'),
        ee: document.getElementById('ee-index-value'),
        dp: document.getElementById('dp-index-value'),
        rpa: document.getElementById('rpa-index-value'),
        prLeadTime: document.getElementById('pr-lead-time-value'),
        weekend: document.getElementById('weekend-work-value'),
        night: document.getElementById('night-work-value'),
        reopen: document.getElementById('reopen-rate-value'),
        commits: document.getElementById('commits-per-day-value'),
        commitsTrend: document.getElementById('commits-trend-icon'),
        footer: document.getElementById('footer-model-info'),
        error: document.getElementById('details-error'),
        btnBack: document.getElementById('back-to-dashboard-btn'),
        btnRefresh: document.getElementById('refresh-score-btn')
    };

    let currentEmployeeData = null;
    const modal = document.getElementById('index-info-modal');
    const closeBtn = document.getElementById('close-index-modal');

    const indexMeta = {
        'EE': {
            title: 'Эмоциональное истощение',
            icon: 'psychology',
            color: 'secondary',
            desc: 'Индекс измеряет уровень усталости и переутомления. Он складывается из интенсивности работы (activity span), частоты работы по ночам и в выходные дни.'
        },
        'DP': {
            title: 'Деперсонализация',
            icon: 'distance',
            color: 'primary',
            desc: 'Индекс цинизма и отстраненности. Высокие значения указывают на формальный подход к коммуникации и снижение качества вовлеченности в командную работу.'
        },
        'RPA': {
            title: 'Редукция достижений',
            icon: 'trending_down',
            color: 'error',
            desc: 'Ощущение бесполезности усилий, чувство собственной некомпетентности. Высокий показатель говорит о том, что задачи "застревают", Pull Requests долго висят без merge / closed, а задачи часто возвращаются на доработку.'
        }
    };

    function openIndexModal(type) {
        const meta = indexMeta[type];
        const val = parseFloat(document.getElementById(type.toLowerCase() + '-index-value').textContent);

        document.getElementById('modal-title').textContent = meta.title;
        document.getElementById('modal-subtitle').textContent = type + ' INDEX';
        document.getElementById('modal-icon').textContent = meta.icon;
        document.getElementById('modal-description').textContent = meta.desc;
        document.getElementById('modal-current-value').textContent = val.toFixed(2);

        if (currentEmployeeData && currentEmployeeData.indexInterpretations) {
            document.getElementById('modal-analysis').innerHTML = currentEmployeeData.indexInterpretations[type];
        } else {
            document.getElementById('modal-analysis').innerHTML = "<p>Аналитика недоступна.</p>";
        }

        const container = document.getElementById('modal-icon-container');
        const badge = document.getElementById('modal-value-badge');

        if (val >= 2.0) {
            badge.textContent = 'Высокий риск';
            badge.className = 'px-3 py-1 rounded-full text-[12px] font-black bg-red-100 text-red-700 border border-red-200';
            container.className = 'w-12 h-12 rounded-2xl flex items-center justify-center text-white shadow-md bg-red-500';
        }
        else if (val >= 1.0) {
            badge.textContent = 'Повышенный риск';
            badge.className = 'px-3 py-1 rounded-full text-[12px] font-black bg-orange-100 text-orange-700 border border-orange-200';
            container.className = 'w-12 h-12 rounded-2xl flex items-center justify-center text-white shadow-md bg-orange-500';
        } else if (val <= -0.5) {
            badge.textContent = 'Низкий риск';
            badge.className = 'px-3 py-1 rounded-full text-[12px] font-black bg-emerald-100 text-emerald-700 border border-emerald-200';
            container.className = 'w-12 h-12 rounded-2xl flex items-center justify-center text-white shadow-md bg-emerald-500';
        } else {
            badge.textContent = 'В пределах нормы';
            badge.className = 'px-3 py-1 rounded-full text-[12px] font-black bg-blue-100 text-blue-700 border border-blue-200';
            container.className = 'w-12 h-12 rounded-2xl flex items-center justify-center text-white shadow-md bg-blue-500';
        }

        modal.classList.remove('hidden');
    }

    document.querySelectorAll('h3').forEach(h3 => {
        const type = h3.textContent.split(' ')[0];
        if (['EE', 'DP', 'RPA'].includes(type)) {
            const card = h3.closest('.bg-surface-container-lowest');
            if (card) {
                card.classList.add('cursor-pointer');
                card.onclick = () => openIndexModal(type);
            }
        }
    });

    closeBtn.onclick = () => modal.classList.add('hidden');
    window.onclick = (e) => { if (e.target == modal) modal.classList.add('hidden'); };

    function showError(msg) { if (ui.error) { ui.error.textContent = msg; ui.error.classList.remove('hidden'); } }
    function hideError() { if (ui.error) ui.error.classList.add('hidden'); }
    function toPercent(val) { return Math.round(Math.max(0, Math.min(1, Number(val || 0))) * 100); }
    function getRiskText(pct, statusColor) {
        const status = (statusColor || '').toUpperCase();
        if (status === 'RED' || pct >= 70) return 'Критический';
        if (status === 'YELLOW' || pct >= 40) return 'Повышенный';
        return 'Низкий';
    }

    function fillDetails(data) {
        currentEmployeeData = data;
        const pct = toPercent(data.riskProbability);

        if (ui.title) ui.title.textContent = '#' + String(data.employeeId || '').toUpperCase();
        if (ui.riskPercent) ui.riskPercent.textContent = pct + '%';

        if (ui.riskLabel) ui.riskLabel.textContent = getRiskText(pct, data.statusColor);

        if (ui.riskProgress) ui.riskProgress.style.width = pct + '%';

        const status = (data.statusColor || '').toUpperCase();
        const isRed = status === 'RED' || pct >= 70;
        const isYellow = status === 'YELLOW' || (pct >= 40 && pct < 70);

        const panel = ui.riskPercent ? ui.riskPercent.closest('.glass-panel') : null;

        if (isRed) {
            if (ui.riskPercent) ui.riskPercent.className = 'text-6xl font-black text-error';
            if (ui.riskLabel) ui.riskLabel.className = 'text-xl font-bold text-error';
            if (ui.riskProgress) ui.riskProgress.className = 'h-full rounded-full bg-error';
            if (panel) {
                panel.classList.remove('border-secondary', 'border-[#00A36C]');
                panel.classList.add('border-error');
            }
        } else if (isYellow) {
            if (ui.riskPercent) ui.riskPercent.className = 'text-6xl font-black text-secondary';
            if (ui.riskLabel) ui.riskLabel.className = 'text-xl font-bold text-secondary';
            if (ui.riskProgress) ui.riskProgress.className = 'h-full rounded-full bg-secondary';
            if (panel) {
                panel.classList.remove('border-error', 'border-[#00A36C]');
                panel.classList.add('border-secondary');
            }
        } else {
            if (ui.riskPercent) ui.riskPercent.className = 'text-6xl font-black text-[#00A36C]';
            if (ui.riskLabel) ui.riskLabel.className = 'text-xl font-bold text-[#00A36C]';
            if (ui.riskProgress) ui.riskProgress.className = 'h-full rounded-full bg-[#00A36C]';
            if (panel) {
                panel.classList.remove('border-error', 'border-secondary');
                panel.classList.add('border-[#00A36C]');
            }
        }

        if (ui.ee) ui.ee.textContent = Number(data.eeIndex || 0).toFixed(1);
        if (ui.dp) ui.dp.textContent = Number(data.dpIndex || 0).toFixed(1);
        if (ui.rpa) ui.rpa.textContent = Number(data.rpaIndex || 0).toFixed(1);

        if (ui.prLeadTime) ui.prLeadTime.textContent = Number(data.prLeadTimeHours || 0).toFixed(1);
        if (ui.reopen) ui.reopen.textContent = Number(data.reopenRatePercent || 0).toFixed(1);
        if (ui.commits) ui.commits.textContent = Number(data.commitsPerDay || 0).toFixed(1);

        if (ui.weekend) {
            ui.weekend.textContent = data.weekendWork ? 'Да' : 'Нет';
            ui.weekend.className = data.weekendWork ? 'text-2xl font-bold text-error' : 'text-2xl font-bold text-on-surface';
            const weekendIcon = ui.weekend.previousElementSibling;
            if (weekendIcon) {
                if (data.weekendWork) {
                    weekendIcon.className = 'material-symbols-outlined text-error';
                    weekendIcon.style.fontVariationSettings = "'FILL' 1";
                    weekendIcon.innerHTML = 'warning';
                } else {
                    weekendIcon.className = 'text-on-surface flex items-center justify-center';
                    weekendIcon.style.fontVariationSettings = '';
                    weekendIcon.innerHTML = `
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-6 h-6">
                            <path stroke-linecap="round" stroke-linejoin="round" d="M6.75 3v2.25M17.25 3v2.25M3 18.75V7.5a2.25 2.25 0 0 1 2.25-2.25h13.5A2.25 2.25 0 0 1 21 7.5v11.25m-18 0A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21 18.75m-18 0v-7.5A2.25 2.25 0 0 1 5.25 9h13.5A2.25 2.25 0 0 1 21 11.25v7.5" />
                        </svg>
                    `;
                }
            }
        }
        if (ui.night) {
            ui.night.textContent = data.nightWork ? 'Да' : 'Нет';
            ui.night.className = data.nightWork ? 'text-2xl font-bold text-error' : 'text-2xl font-bold text-on-surface';

            const nightIcon = ui.night.previousElementSibling;
            if (nightIcon) {
                if (data.nightWork) {
                    nightIcon.className = 'material-symbols-outlined text-error';
                    nightIcon.style.fontVariationSettings = "'FILL' 1";
                    nightIcon.innerHTML = 'warning';
                } else {
                    nightIcon.className = 'material-symbols-outlined text-outline flex items-center justify-center';
                    nightIcon.style.fontVariationSettings = '';
                    nightIcon.innerHTML = 'dark_mode';
                }
            }
        }
        if (ui.commitsTrend) {
            ui.commitsTrend.textContent = data.commitsTrendUp ? 'trending_up' : 'trending_down';
            ui.commitsTrend.className = data.commitsTrendUp ? 'material-symbols-outlined text-primary-container text-sm' : 'material-symbols-outlined text-error text-sm';
        }

        if (ui.footer) {
            const utcString = data.lastUpdatedAt.endsWith('Z') ? data.lastUpdatedAt : data.lastUpdatedAt + 'Z';
            const dateObj = new Date(utcString);

            const dateStr = dateObj.toLocaleString('ru-RU', { timeZone: 'UTC', hour: '2-digit', minute: '2-digit' });
            ui.footer.innerHTML = `<div class="flex items-center gap-4"><span>Model ${data.modelVersion}</span><span>•</span><span>Last updated: ${dateStr} UTC</span></div>`;
        }
    }

    async function loadEmployeeDetails() {
        if (!employeeId) return showError('Идентификатор не передан.');
        hideError();
        try {
            const response = await auth.fetchWithAuth(API_BASE_URL + '/api/v1/burnout/analytics/details/' + employeeId, {
                method: 'GET'
            });
            if (response.status === 401) { auth.handleUnauthorized('login.html'); return; }
            if (response.status === 403) { window.location.replace(auth.getDefaultDashboardPath()); return; }
            if (!response.ok) throw new Error('Не удалось загрузить данные.');
            fillDetails(await response.json());
        } catch (e) { showError(e.message); }
    }

    settingsBtn.addEventListener('click', () => {
        window.location.href = 'settings.html';
    });

    if (ui.btnBack) ui.btnBack.addEventListener('click', () => window.location.href = 'dashboard.html');

    if (ui.btnRefresh) ui.btnRefresh.addEventListener('click', async () => {
        const origText = ui.btnRefresh.textContent;
        ui.btnRefresh.textContent = 'Пересчет...';
        ui.btnRefresh.disabled = true;
        try {
            const res = await fetch(`${API_BASE_URL}/api/v1/burnout/ops/recalculate/${employeeId}`, {
                method: 'POST', headers: { Authorization: 'Bearer ' + auth.getToken() }
            });
            if (!res.ok) throw new Error('Ошибка пересчета.');
            await loadEmployeeDetails();
        } catch(e) { showError(e.message); }
        finally { ui.btnRefresh.textContent = origText; ui.btnRefresh.disabled = false; }
    });

    loadEmployeeDetails();
});