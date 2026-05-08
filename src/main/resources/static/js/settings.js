document.addEventListener('DOMContentLoaded', async function () {
    const API_BASE_URL = window.BPS_API_BASE_URL || 'http://localhost:8080';
    const auth = window.BPSAuth;

    if (!auth || !auth.requireAuth('login.html')) {
        return;
    }

    if (auth.getRole && auth.getRole() !== 'ROLE_ADMIN') {
        window.location.replace(auth.getDefaultDashboardPath());
        return;
    }

    auth.startExpiryWatcher('login.html');

    const API_URL = API_BASE_URL + '/api/v1/settings';
    const saveBtn = document.getElementById('saveBtn');
    const resetBtn = document.getElementById('resetBtn');

    const fieldIds = [
        'greenThreshold', 'yellowThreshold', 'scoringAlpha',
        'jiraUrl', 'jiraUsername', 'jiraToken',
        'githubOwner', 'githubToken',
        'timezone', 'isolatedEventDurationSeconds',
        'minCommitLength', 'doneStatuses', 'boilerplateWords'
    ];

    const populateFields = (data) => {
        fieldIds.forEach(id => {
            const element = document.getElementById(id);
            if (element && data[id] !== undefined) {
                element.value = data[id];
                element.classList.remove('ring-2', 'ring-red-500', 'border-red-500', 'bg-red-50');
            }
        });
    };

    try {
        const response = await auth.fetchWithAuth(API_URL, { method: 'GET' });

        if (response.status === 401) { auth.handleUnauthorized('login.html'); return; }
        if (response.status === 403) { window.location.replace(auth.getDefaultDashboardPath()); return; }
        if (!response.ok) throw new Error('Ошибка HTTP: ' + response.status);

        const data = await response.json();
        populateFields(data);
    } catch (err) {
        console.error('Ошибка загрузки настроек:', err);
        alert('Не удалось загрузить настройки. Сервер недоступен.');
    }

    saveBtn.addEventListener('click', async () => {
        let isValid = true;
        const payload = {};

        fieldIds.forEach(id => {
            const element = document.getElementById(id);
            if (element) {
                if (!element.value.trim()) {
                    element.classList.add('ring-2', 'ring-red-500', 'border-red-500', 'bg-red-50');
                    isValid = false;
                } else {
                    element.classList.remove('ring-2', 'ring-red-500', 'border-red-500', 'bg-red-50');
                    if (element.type === 'number') {
                        payload[id] = parseFloat(element.value);
                    } else {
                        payload[id] = element.value;
                    }
                }
            }
        });

        if (!isValid) {
            alert('Пожалуйста, заполните все обязательные поля!');
            return;
        }

        saveBtn.innerText = 'Сохранение...';
        saveBtn.disabled = true;

        try {
            const response = await auth.fetchWithAuth(API_URL, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (response.status === 401) { auth.handleUnauthorized('login.html'); return; }
            if (response.status === 403) { window.location.replace(auth.getDefaultDashboardPath()); return; }
            if (!response.ok) throw new Error('Ошибка сервера: ' + response.status);

            alert('Настройки успешно сохранены и применены!');
        } catch (err) {
            console.error(err);
            alert('Произошла ошибка при сохранении настроек.');
        } finally {
            saveBtn.innerText = 'Сохранить изменения';
            saveBtn.disabled = false;
        }
    });

    resetBtn.addEventListener('click', async () => {
        if (!confirm('Вы уверены, что хотите сбросить все настройки до заводских значений? Токены придется вводить заново!')) {
            return;
        }

        resetBtn.innerText = 'Сброс...';
        resetBtn.disabled = true;

        try {
            const response = await auth.fetchWithAuth(API_URL + '/reset', {
                method: 'POST'
            });

            if (response.status === 401) { auth.handleUnauthorized('login.html'); return; }
            if (response.status === 403) { window.location.replace(auth.getDefaultDashboardPath()); return; }
            if (!response.ok) throw new Error('Ошибка сервера: ' + response.status);

            const data = await response.json();
            populateFields(data);
            alert('Настройки сброшены до заводских!');
        } catch (err) {
            console.error(err);
            alert('Ошибка при сбросе настроек.');
        } finally {
            resetBtn.innerText = 'Сбросить до заводских';
            resetBtn.disabled = false;
        }
    });

    document.querySelectorAll('.toggle-password').forEach(btn => {
        btn.addEventListener('click', function() {
            const input = this.previousElementSibling;
            const icon = this.querySelector('span');
            if (input.type === 'password') {
                input.type = 'text';
                icon.textContent = 'visibility_off';
            } else {
                input.type = 'password';
                icon.textContent = 'visibility';
            }
        });
    });

    document.getElementById('backBtn').addEventListener('click', () => {
        window.location.href = 'dashboard.html';
    });
});