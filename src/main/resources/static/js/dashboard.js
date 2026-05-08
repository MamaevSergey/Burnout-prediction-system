document.addEventListener('DOMContentLoaded', function () {
    const API_BASE_URL = window.BPS_API_BASE_URL || 'http://localhost:8080';
    const auth = window.BPSAuth;

    if (!auth || !auth.requireAuth('login.html')) {
        return;
    }

    if (auth.getRole && auth.getRole() === 'ROLE_VIEWER') {
        window.location.replace('dashboardViewer.html');
        return;
    }

    auth.startExpiryWatcher('login.html');

    const userRole = auth.getRole ? auth.getRole() : null;
    const kpiGrid = document.getElementById('kpi-grid');
    const cardEtl = document.getElementById('card-etl');
    const cardModel = document.getElementById('card-model');
    const settingsBtn = document.getElementById('settingsBtn');

    if (userRole !== 'ROLE_ADMIN') {
        if (settingsBtn) {
            settingsBtn.style.display = 'none';
        }
    }

    if (userRole === 'ROLE_HR') {
        if (cardEtl) cardEtl.classList.add('hidden');
        if (cardModel) cardModel.classList.add('hidden');
        if (kpiGrid) kpiGrid.classList.remove('lg:grid-cols-4');
    }

    const summaryTableBody = document.getElementById('summary-table-body');
    const searchInput = document.getElementById('summary-search');
    const avgRiskValue = document.getElementById('avg-risk-value');
    const avgRiskProgress = document.getElementById('avg-risk-progress');
    const highRiskCount = document.getElementById('high-risk-count');
    const highRiskTotal = document.getElementById('high-risk-total');
    const errorBlock = document.getElementById('dashboard-error');

    if (!summaryTableBody || !searchInput) {
        return;
    }

    let allEmployees = [];
    let filteredEmployees = [];
    let visibleCount = 8;
    const loadMoreButton = document.querySelector('.p-6.bg-surface-container-low\\/30.text-center button');

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

    function escapeHtml(value) {
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/\"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function getRiskMeta(employee) {
        const probability = Number(employee.riskProbability || 0);
        const status = (employee.statusColor || '').toLowerCase();

        if (status.includes('error') || status.includes('red') || status.includes('high') || probability >= 0.7) {
            return {
                label: 'Критический риск',
                badgeClass: 'bg-error-container text-on-error-container',
                dotClass: 'bg-error'
            };
        }

        if (status.includes('secondary') || status.includes('yellow') || status.includes('medium') || probability >= 0.4) {
            return {
                label: 'Повышенный риск',
                badgeClass: 'bg-secondary-container text-on-secondary-container',
                dotClass: 'bg-secondary'
            };
        }

        return {
            label: 'Низкий риск',
            badgeClass: 'bg-surface-container-highest text-on-surface',
            dotClass: 'bg-[#00A36C]'
        };
    }

    function toPercent(value) {
        const clamped = Math.max(0, Math.min(1, Number(value || 0)));
        return Math.round(clamped * 100);
    }

    function updateCards(stats) {
        if (!stats) return;

        if (avgRiskValue) avgRiskValue.textContent = stats.averageRiskPercent + '%';
        if (avgRiskProgress) avgRiskProgress.style.width = stats.averageRiskPercent + '%';
        if (highRiskCount) highRiskCount.textContent = stats.highRiskCount;
        if (highRiskTotal) highRiskTotal.textContent = '/ ' + stats.totalEmployees + ' Всего';

        const avgRiskYesterdayNode = document.getElementById('avg-risk-yesterday');
        if (avgRiskYesterdayNode && stats.averageRiskPercentYesterday !== undefined) {
            avgRiskYesterdayNode.textContent = 'Вчера было: ' + stats.averageRiskPercentYesterday + '%';
        }

        const trendIcon = document.getElementById('avg-risk-trend');
        if (trendIcon) {
            trendIcon.textContent = stats.averageRiskTrendUp ? 'trending_up' : 'trending_down';
            trendIcon.className = stats.averageRiskTrendUp
                ? 'material-symbols-outlined text-primary-container text-lg'
                : 'material-symbols-outlined text-primary-container text-lg';
        }

        const lastEtlRunNode = document.getElementById('last-etl-run');
        const nextEtlRunNode = document.getElementById('next-etl-run');
        const modelVersionNode = document.getElementById('active-model-version');

        if (lastEtlRunNode) lastEtlRunNode.textContent = escapeHtml(stats.lastEtlRun || 'Никогда');
        if (nextEtlRunNode) nextEtlRunNode.textContent = escapeHtml(stats.nextEtlRun || 'Ожидается расписание');
        if (modelVersionNode) modelVersionNode.textContent = escapeHtml(stats.activeModelVersion || 'Нет данных');
    }

    function renderRows(data) {
        if (!data.length) {
            summaryTableBody.innerHTML =
                '<tr><td colspan="5" class="px-8 py-8 text-center text-on-surface-variant">Нет данных для отображения</td></tr>';
            if (loadMoreButton) loadMoreButton.parentElement.classList.add('hidden');
            return;
        }

        const itemsToShow = data.slice(0, visibleCount);

        const rowsHtml = itemsToShow.map(function (employee) {
            const riskMeta = getRiskMeta(employee);
            const riskPercent = toPercent(employee.riskProbability);

            const fullId = escapeHtml(employee.employeeId);
            const displayId = 'EMP-' + fullId.substring(0, 4).toUpperCase();

            return '' +
                '<tr class="hover:bg-surface-container-high transition-colors group">' +
                '<td class="px-8 py-6 font-mono font-medium text-primary">' + displayId + '</td>' +
                '<td class="px-8 py-6">' +
                '<span class="inline-flex items-center px-3 py-1 rounded-full text-xs font-bold ' + riskMeta.badgeClass + '">' +
                '<span class="w-2 h-2 rounded-full ' + riskMeta.dotClass + ' mr-2"></span>' + riskMeta.label +
                '</span>' +
                '</td>' +
                '<td class="px-8 py-6 font-bold text-on-surface">' + riskPercent + '%</td>' +
                '<td class="px-8 py-6 text-on-surface-variant text-sm">' + escapeHtml(employee.lastSync || 'Н/Д') + '</td>' +
                '<td class="px-8 py-6 text-right">' +
                '<button class="text-primary font-bold text-sm hover:underline active:scale-95 transition-all" data-employee-id="' + fullId + '">Подробнее</button>' +
                '</td>' +
                '</tr>';
        }).join('');

        summaryTableBody.innerHTML = rowsHtml;

        if (loadMoreButton) {
            const total = data.length;
            if (total > 8) {
                loadMoreButton.parentElement.classList.remove('hidden');

                if (visibleCount < total) {
                    const remaining = total - 8;
                    loadMoreButton.textContent = `Загрузить больше (${remaining} Оставшиеся)`;
                } else {
                    loadMoreButton.textContent = 'Свернуть';
                }
            } else {
                loadMoreButton.parentElement.classList.add('hidden');
            }
        }
    }

    document.getElementById('settingsBtn').addEventListener('click', () => {
        window.location.href = 'settings.html';
    });

    function applySearch() {
        const query = searchInput.value.trim().toLowerCase();
        visibleCount = 8;

        if (!query) {
            filteredEmployees = allEmployees;
            renderRows(filteredEmployees);
            return;
        }

        filteredEmployees = allEmployees.filter(function (employee) {
            const employeeId = String(employee.employeeId || '').toLowerCase();
            const role = String(employee.role || '').toLowerCase();
            return employeeId.includes(query) || role.includes(query);
        });

        renderRows(filteredEmployees);
    }

    const mobileMenuBtn = document.getElementById('mobile-menu-btn');
    const mobileMenu = document.getElementById('mobile-menu');

    if (mobileMenuBtn && mobileMenu) {
        mobileMenuBtn.addEventListener('click', () => {
            mobileMenu.classList.toggle('hidden');
            const icon = mobileMenuBtn.querySelector('.material-symbols-outlined');
            if (icon) {
                icon.textContent = mobileMenu.classList.contains('hidden') ? 'menu' : 'close';
            }
        });
    }

    async function loadSummary() {
        hideError();

        try {
            const response = await auth.fetchWithAuth(API_BASE_URL + '/api/v1/burnout/analytics/summary', {
                method: 'GET'
            });

            if (response.status === 401) { auth.handleUnauthorized('login.html'); return; }
            if (response.status === 403) { window.location.replace(auth.getDefaultDashboardPath()); return; }

            if (!response.ok) {
                throw new Error('Не удалось загрузить сводку по сотрудникам.');
            }

            const data = await response.json();
            if (!data) return;
            allEmployees = data.employees || [];
            allEmployees.sort(function (a, b) {
                return Number(b.riskProbability || 0) - Number(a.riskProbability || 0);
            });

            updateCards(data.stats);
            filteredEmployees = allEmployees;
            renderRows(filteredEmployees);
        } catch (error) {
            showError(error.message || 'Ошибка загрузки данных dashboard.');
            renderRows([]);
        }
    }

    summaryTableBody.addEventListener('click', function (event) {
        const target = event.target;
        if (!(target instanceof HTMLElement)) return;

        const actionButton = target.closest('[data-employee-id]');
        if (!actionButton) return;

        const employeeId = actionButton.getAttribute('data-employee-id');
        if (!employeeId) return;

        window.location.href = 'employeeDetails.html?employeeId=' + encodeURIComponent(employeeId);
    });

    searchInput.addEventListener('input', applySearch);

    loadSummary();

    if (loadMoreButton) {
        loadMoreButton.addEventListener('click', function () {
            if (visibleCount < filteredEmployees.length) {
                visibleCount = filteredEmployees.length;
            } else {
                visibleCount = 8;
            }
            renderRows(filteredEmployees);
        });
    }
});