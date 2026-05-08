document.addEventListener('DOMContentLoaded', function () {
    const API_BASE_URL = window.BPS_API_BASE_URL || 'http://localhost:8080';
    const auth = window.BPSAuth;

    if (!auth || !auth.requireAuth('login.html')) return;
    if (auth.getRole && auth.getRole() === 'ROLE_VIEWER') {
        window.location.replace('dashboardViewer.html');
        return;
    }
    auth.startExpiryWatcher('login.html');

    const mappingList = document.getElementById('mapping-list');
    const mappingCount = document.getElementById('mapping-count');
    const saveButton = document.getElementById('save-mappings-btn');
    const resetButton = document.getElementById('reset-mappings-btn');
    const errorBlock = document.getElementById('employees-error');
    const unsavedCount = document.getElementById('unsaved-count');
    const avatarsContainer = document.getElementById('unsaved-avatars-container');
    const prevBtn = document.getElementById('prev-page-btn');
    const nextBtn = document.getElementById('next-page-btn');
    const pageText = document.getElementById('current-page-text');
    const settingsBtn = document.getElementById('settingsBtn');
    const userRole = auth.getRole ? auth.getRole() : null;

    if (userRole !== 'ROLE_ADMIN') {
        if (settingsBtn) {
            settingsBtn.style.display = 'none';
        }
    }

    let originalMappings = [];
    const pendingChanges = new Map();

    let currentPage = 1;
    const ITEMS_PER_PAGE = 5;

    const githubRegex = /^([a-zA-Z0-9-]{1,39})?$/;

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

    function getInitials(name, fallback) {
        const text = String(name || fallback || '').trim();
        if (!text) return '??';
        const parts = text.split(/\s+/).filter(Boolean);
        if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
        return (parts[0][0] + parts[1][0]).toUpperCase();
    }

    function pluralize(count, one, few, many) {
        const mod10 = count % 10;
        const mod100 = count % 100;
        if (mod10 === 1 && mod100 !== 11) return one;
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return few;
        return many;
    }

    function updateUnsavedCount() {
        const count = pendingChanges.size;
        if (unsavedCount) {
            unsavedCount.textContent = count ? `${count} ${pluralize(count, 'изменение', 'изменения', 'изменений')} не сохранено` : 'Изменений нет';
        }

        if (!avatarsContainer) return;
        if (count === 0) {
            avatarsContainer.innerHTML = '';
            return;
        }

        const changesArr = Array.from(pendingChanges.values());
        let avatarsHtml = '';
        const colors = ['bg-primary-fixed', 'bg-secondary-fixed'];

        for (let i = 0; i < Math.min(count, 2); i++) {
            const initials = escapeHtml(getInitials(changesArr[i].displayName, changesArr[i].email));
            avatarsHtml += `<div class="w-8 h-8 rounded-full border-2 border-white ${colors[i % 2]} flex items-center justify-center text-[10px] font-bold">${initials}</div>`;
        }

        if (count > 2) {
            avatarsHtml += `<div class="w-8 h-8 rounded-full border-2 border-white bg-surface-container-highest flex items-center justify-center text-[10px] text-on-surface-variant">+${count - 2}</div>`;
        }

        avatarsContainer.innerHTML = avatarsHtml;
    }

    function updateMappingCount() {
        if (!mappingCount) return;
        const total = originalMappings.length;
        if (total === 0) {
            mappingCount.textContent = `Показаны 0 из 0 сотрудников`;
            return;
        }
        const start = (currentPage - 1) * ITEMS_PER_PAGE + 1;
        const end = Math.min(currentPage * ITEMS_PER_PAGE, total);
        mappingCount.textContent = `Показаны ${start}–${end} из ${total} сотрудников`;
    }

    function getMappingByEmail(email) {
        return originalMappings.find(item => item.email === email);
    }

    function setPendingChange(email, payload) {
        if (!payload) {
            pendingChanges.delete(email);
        } else {
            const original = getMappingByEmail(email);
            payload.displayName = original ? original.displayName : email;
            pendingChanges.set(email, payload);
        }
        updateUnsavedCount();
    }

    function readRowValues(row) {
        const email = row.getAttribute('data-email');
        const input = row.querySelector('input[data-field="github"]');
        const activeButton = row.querySelector('[data-action="toggle-active"]');

        if (!email || !input || !activeButton) return null;

        return {
            email: email,
            githubUsername: input.value.trim() || null,
            isActive: activeButton.getAttribute('data-active') === 'true'
        };
    }

    function updateRowPendingState(row) {
        const current = readRowValues(row);
        if (!current) return;

        const input = row.querySelector('input[data-field="github"]');

        if (!githubRegex.test(current.githubUsername || '')) {
            input.classList.add('!ring-error', '!text-error', '!bg-error-container/20');
            setPendingChange(current.email, null);
            showError(`Логин ${current.githubUsername} содержит недопустимые символы.`);
            return;
        } else {
            input.classList.remove('!ring-error', '!text-error', '!bg-error-container/20');
            hideError();
        }

        const original = getMappingByEmail(current.email);
        if (!original) {
            setPendingChange(current.email, current);
            return;
        }

        if (current.githubUsername !== (original.githubUsername || null) || current.isActive !== original.isActive) {
            setPendingChange(current.email, current);
        } else {
            setPendingChange(current.email, null);
        }
    }

    function renderPage() {
        if (!mappingList) return;

        if (!originalMappings.length) {
            mappingList.innerHTML = '<div class="px-8 py-6 text-sm text-on-surface-variant font-medium text-center">Отлично! Все сотрудники распределены.</div>';
            updateMappingCount();
            return;
        }

        const totalPages = Math.ceil(originalMappings.length / ITEMS_PER_PAGE);
        if (currentPage > totalPages) currentPage = totalPages;
        if (currentPage < 1) currentPage = 1;

        const start = (currentPage - 1) * ITEMS_PER_PAGE;
        const end = start + ITEMS_PER_PAGE;
        const pageData = originalMappings.slice(start, end);

        const rowsHtml = pageData.map((item, index) => {
            const pending = pendingChanges.get(item.email);
            const displayGithub = pending && pending.githubUsername !== null ? pending.githubUsername : (item.githubUsername || '');
            const isActive = pending ? pending.isActive : item.isActive;

            const initials = escapeHtml(getInitials(item.displayName, item.email));
            const email = escapeHtml(item.email);
            const github = escapeHtml(displayGithub);

            const colorClass = index % 3 === 1 ? 'bg-secondary/10 text-secondary' : (index % 3 === 2 ? 'bg-tertiary/10 text-tertiary' : 'bg-primary/10 text-primary');

            const btnClass = isActive
                ? 'text-error/60 hover:text-error hover:bg-error-container/20'
                : 'bg-error text-white hover:bg-error/90';

            return `
                <div class="flex flex-col md:grid md:grid-cols-12 gap-3 md:gap-4 px-4 md:px-8 py-4 md:py-3 items-start md:items-center hover:bg-surface-container-high transition-colors group" data-email="${email}">
                    
                    <div class="w-full md:col-span-5">
                        <div class="flex items-center gap-3">
                            <div class="w-8 h-8 rounded-full shrink-0 ${colorClass} flex items-center justify-center font-bold text-xs">${initials}</div>
                            <div class="flex flex-col min-w-0">
                                <span class="text-on-surface font-medium text-sm truncate">${email}</span>
                                <span class="text-[10px] text-on-surface-variant truncate">${escapeHtml(item.displayName || '')}</span>
                            </div>
                        </div>
                    </div>

                    <div class="w-full md:col-span-5 mt-1 md:mt-0">
                        <div class="relative w-full md:max-w-xs">
                            <span class="absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-xs font-mono">@</span>
                            <input class="w-full pl-7 pr-3 py-1.5 bg-surface-container-low border-none rounded-full text-sm focus:ring-2 focus:ring-primary/20 focus:bg-surface-container-lowest transition-all"
                                data-field="github" placeholder="Github username" type="text" value="${github}" />
                        </div>
                    </div>

                    <div class="w-full md:col-span-2 flex justify-end md:block md:text-right mt-2 md:mt-0">
                        <button class="text-xs font-bold uppercase tracking-tighter transition-colors px-3 py-1 rounded-full ${btnClass}" 
                            data-action="toggle-active" data-active="${isActive}">
                            ${isActive ? 'Деактивировать' : 'Отменить'}
                        </button>
                    </div>
                </div>`;
        }).join('');

        mappingList.innerHTML = rowsHtml;

        if (pageText) pageText.textContent = currentPage;
        if (prevBtn) prevBtn.disabled = currentPage === 1;
        if (nextBtn) nextBtn.disabled = currentPage === totalPages || totalPages === 0;

        updateMappingCount();
    }

    async function loadMappings() {
        hideError();
        if (mappingList) mappingList.innerHTML = '<div class="px-8 py-6 text-sm text-on-surface-variant">Загрузка...</div>';

        try {
            const response = await auth.fetchWithAuth(API_BASE_URL + '/api/v1/burnout/ops/jira-users', {
                method: 'GET'
            });

            if (response.status === 401) { auth.handleUnauthorized('login.html'); return; }
            if (response.status === 403) { window.location.replace(auth.getDefaultDashboardPath()); return; }
            if (!response.ok) throw new Error('Не удалось загрузить список сотрудников.');

            let data = await response.json();

            data.sort((a, b) => {
                if (a.isMapped !== b.isMapped) return a.isMapped ? 1 : -1;
                return a.email.localeCompare(b.email);
            });

            originalMappings = data;
            pendingChanges.clear();
            currentPage = 1;
            renderPage();
            updateUnsavedCount();
        } catch (error) {
            showError(error.message || 'Ошибка загрузки данных сотрудников.');
        }
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

    async function saveChanges() {
        hideError();
        if (!pendingChanges.size) return;

        const payload = Array.from(pendingChanges.values()).map(item => ({
            email: item.email,
            githubUsername: item.githubUsername,
            isActive: item.isActive
        }));

        const originalBtnText = saveButton.innerHTML;
        saveButton.innerHTML = 'Сохраняем...';
        saveButton.disabled = true;

        try {
            const response = await fetch(API_BASE_URL + '/api/v1/burnout/ops/map-github', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    Authorization: 'Bearer ' + auth.getToken()
                },
                body: JSON.stringify(payload)
            });

            if (response.status === 401) { auth.handleUnauthorized('login.html'); return; }
            if (response.status === 403) { window.location.replace(auth.getDefaultDashboardPath()); return; }
            if (!response.ok) {
                const message = await response.text();
                throw new Error(message || 'Не удалось сохранить изменения.');
            }

            originalMappings = originalMappings.map(item => {
                const change = pendingChanges.get(item.email);
                if (!change) return item;
                return { ...item, githubUsername: change.githubUsername, isActive: change.isActive };
            });

            originalMappings = originalMappings.filter(item => item.isActive !== false);
            originalMappings.forEach(item => { item.isMapped = !!item.githubUsername; });

            originalMappings.sort((a, b) => {
                if (a.isMapped !== b.isMapped) return a.isMapped ? 1 : -1;
                return a.email.localeCompare(b.email);
            });

            pendingChanges.clear();
            const totalPages = Math.ceil(originalMappings.length / ITEMS_PER_PAGE);
            if (currentPage > totalPages && totalPages > 0) currentPage = totalPages;

            renderPage();
            updateUnsavedCount();
        } catch (error) {
            showError(error.message || 'Ошибка сохранения изменений.');
        } finally {
            saveButton.innerHTML = originalBtnText;
            saveButton.disabled = false;
        }
    }

    if (prevBtn) {
        prevBtn.addEventListener('click', () => {
            if (currentPage > 1) { currentPage--; renderPage(); }
        });
    }

    if (nextBtn) {
        nextBtn.addEventListener('click', () => {
            const totalPages = Math.ceil(originalMappings.length / ITEMS_PER_PAGE);
            if (currentPage < totalPages) { currentPage++; renderPage(); }
        });
    }

    if (mappingList) {
        mappingList.addEventListener('input', e => {
            if (e.target.tagName !== 'INPUT') return;
            const row = e.target.closest('[data-email]');
            if (row) updateRowPendingState(row);
        });

        mappingList.addEventListener('click', e => {
            const button = e.target.closest('[data-action="toggle-active"]');
            if (!button) return;

            const isActive = button.getAttribute('data-active') === 'true';
            button.setAttribute('data-active', !isActive);

            if (isActive) {
                button.textContent = 'Отменить';
                button.className = 'text-xs font-bold uppercase tracking-tighter transition-colors px-3 py-1 rounded-full bg-error text-white hover:bg-error/90';
            } else {
                button.textContent = 'Деактивировать';
                button.className = 'text-xs font-bold uppercase tracking-tighter transition-colors px-3 py-1 rounded-full text-error/60 hover:text-error hover:bg-error-container/20';
            }

            const row = button.closest('[data-email]');
            if (row) updateRowPendingState(row);
        });
    }

    if (saveButton) saveButton.addEventListener('click', saveChanges);
    if (resetButton) resetButton.addEventListener('click', () => {
        pendingChanges.clear();
        hideError();
        renderPage();
        updateUnsavedCount();
    });

    const uploadBtn = document.getElementById('upload-csv-btn');
    const fileInput = document.getElementById('csv-upload-input');

    document.getElementById('settingsBtn').addEventListener('click', () => {
        window.location.href = 'settings.html';
    });

    if (uploadBtn && fileInput) {
        uploadBtn.addEventListener('click', () => {
            fileInput.click();
        });

        fileInput.addEventListener('change', async (event) => {
            const file = event.target.files[0];
            if (!file) return;

            hideError();
            const originalText = uploadBtn.innerHTML;
            uploadBtn.innerHTML = 'Загрузка...';
            uploadBtn.disabled = true;

            const formData = new FormData();
            formData.append('file', file);

            try {
                const response = await fetch(API_BASE_URL + '/api/v1/burnout/ops/map-github/csv', {
                    method: 'POST',
                    headers: {
                        Authorization: 'Bearer ' + auth.getToken()
                    },
                    body: formData
                });

                if (response.status === 401) { auth.handleUnauthorized('login.html'); return; }
                if (response.status === 403) { window.location.replace(auth.getDefaultDashboardPath()); return; }

                if (!response.ok) {
                    const message = await response.text();
                    throw new Error(message || 'Не удалось загрузить CSV.');
                }

                const resultMessage = await response.text();
                if (errorBlock) {
                    errorBlock.textContent = resultMessage;
                    errorBlock.classList.remove('text-error', 'hidden');
                    errorBlock.classList.add('text-primary');

                    setTimeout(() => {
                        errorBlock.classList.remove('text-primary');
                        errorBlock.classList.add('text-error');
                        hideError();
                    }, 5000);
                }
                await loadMappings();

            } catch (error) {
                showError(error.message || 'Ошибка загрузки файла.');
            } finally {
                uploadBtn.innerHTML = originalText;
                uploadBtn.disabled = false;
                fileInput.value = '';
            }
        });
    }
    loadMappings();
});