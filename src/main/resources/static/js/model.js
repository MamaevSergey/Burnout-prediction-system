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

    const dropzone = document.getElementById('csv-dropzone');
    const fileInput = document.getElementById('csv-file-input');
    const previewBody = document.getElementById('preview-body');
    const previewFileName = document.getElementById('preview-file-name');
    const previewCount = document.getElementById('preview-count');
    const trainButton = document.getElementById('train-model-btn');
    const modelStatus = document.getElementById('model-status');
    const settingsBtn = document.getElementById('settingsBtn');
    const userRole = auth.getRole ? auth.getRole() : null;

    if (userRole !== 'ROLE_ADMIN') {
        if (settingsBtn) {
            settingsBtn.style.display = 'none';
        }
    }

    if (!dropzone || !fileInput || !previewBody || !trainButton || !modelStatus) return;

    let selectedFile = null;
    let allCsvRows = [];
    let isPreviewExpanded = false;
    const DEFAULT_ROWS = 8;

    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

    function setStatus(message, isError) {
        modelStatus.textContent = message;
        modelStatus.className = 'text-xs mt-2 font-medium';

        if (isError) {
            modelStatus.classList.add('text-error');
        } else if (message.toLowerCase().includes('успешно')) {
            modelStatus.classList.add('text-[#00A36C]');
        } else {
            modelStatus.classList.add('text-on-surface-variant', 'opacity-60');
        }
    }

    function updateTimeEstimate(rowCount) {
        const estimatedMinutes = Math.max(1, Math.ceil(rowCount / 500));
        let minuteStr = 'минут';
        const mod10 = estimatedMinutes % 10;
        const mod100 = estimatedMinutes % 100;

        if (mod10 === 1 && mod100 !== 11) minuteStr = 'минуту';
        else if ([2, 3, 4].includes(mod10) && ![12, 13, 14].includes(mod100)) minuteStr = 'минуты';

        setStatus(`Примерное время обучения: ~${estimatedMinutes} ${minuteStr}`, false);
    }

    function escapeHtml(value) {
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function parseCsvLine(line) {
        const parts = line.split(',');
        if (parts.length < 2) return [line.trim(), ''];
        return [parts[0].trim(), parts[1].trim()];
    }

    window.togglePreview = function() {
        isPreviewExpanded = !isPreviewExpanded;
        renderPreview();
    };

    function renderEmptyState() {
        previewBody.innerHTML = `
            <tr>
                <td colspan="2" class="px-6 py-12 text-sm text-on-surface-variant text-center border-dashed border border-outline-variant/10">
                    Ожидание загрузки датасета...
                </td>
            </tr>`;
        previewCount.textContent = '0 строк';
        trainButton.disabled = true;
        trainButton.classList.add('opacity-50', 'cursor-not-allowed');
        trainButton.classList.remove('hover:scale-105');
    }

    function renderPreview() {
        if (!allCsvRows.length) {
            renderEmptyState();
            return;
        }

        const visibleCount = isPreviewExpanded ? allCsvRows.length : Math.min(DEFAULT_ROWS, allCsvRows.length);
        const rowsToShow = allCsvRows.slice(0, visibleCount);

        const html = rowsToShow.map(function (row) {
            const parsed = parseCsvLine(row);
            const hash = escapeHtml(parsed[0]);
            const label = escapeHtml(parsed[1]);
            const isBurnout = label === '1';
            const badgeClass = isBurnout
                ? 'bg-error-container/30 text-error'
                : 'bg-secondary-container/30 text-secondary';

            return `
                <tr class="hover:bg-surface-bright transition-colors">
                    <td class="px-6 py-4 text-sm font-mono text-on-surface">${hash}</td>
                    <td class="px-6 py-4"><span class="px-3 py-1 rounded-full ${badgeClass} text-[10px] font-bold">${label}</span></td>
                </tr>`;
        }).join('');

        let expandRow = '';
        if (allCsvRows.length > DEFAULT_ROWS) {
            const actionText = isPreviewExpanded ? 'Свернуть таблицу' : `Показать все (${allCsvRows.length.toLocaleString('ru-RU')} строк)`;
            expandRow = `
                <tr class="hover:bg-surface-container transition-colors cursor-pointer" onclick="window.togglePreview()">
                    <td colspan="2" class="px-6 py-3 text-center text-sm font-bold text-primary">${actionText}</td>
                </tr>`;
        }

        previewBody.innerHTML = html + expandRow;
        previewCount.textContent = `${visibleCount.toLocaleString('ru-RU')} из ${allCsvRows.length.toLocaleString('ru-RU')} строк показано`;
    }

    function validateDataset(lines, hasHeader) {
        for (let i = 0; i < lines.length; i++) {
            const parsed = parseCsvLine(lines[i]);
            const hash = parsed[0];
            const label = parsed[1];
            const lineNumber = i + (hasHeader ? 2 : 1);

            if (!uuidRegex.test(hash)) {
                setStatus(`Ошибка в строке ${lineNumber}: неверный формат employee_hash. Ожидается UUID.`, true);
                return false;
            }
            if (label !== '0' && label !== '1') {
                setStatus(`Ошибка в строке ${lineNumber}: burnout_label должен быть строго 0 или 1.`, true);
                return false;
            }
        }
        return true;
    }

    function onFileSelected(file) {
        if (!file) return;

        if (!file.name.toLowerCase().endsWith('.csv')) {
            setStatus('Поддерживаются только CSV файлы.', true);
            renderEmptyState();
            return;
        }

        selectedFile = file;
        previewFileName.textContent = file.name;

        const reader = new FileReader();
        reader.onload = function () {
            const text = String(reader.result || '');
            const lines = text.split(/\r?\n/).map(l => l.trim()).filter(l => l.length > 0);

            if (lines.length > 0) {
                const hasHeader = lines[0].toLowerCase().includes('hash') || lines[0].toLowerCase().includes('burnout');
                const dataRows = hasHeader ? lines.slice(1) : lines;

                if (!validateDataset(dataRows, hasHeader)) {
                    allCsvRows = [];
                    renderEmptyState();
                    return;
                }

                allCsvRows = dataRows;
                trainButton.disabled = false;
                trainButton.classList.remove('opacity-50', 'cursor-not-allowed');
                trainButton.classList.add('hover:scale-105');
                updateTimeEstimate(allCsvRows.length);
            } else {
                allCsvRows = [];
                setStatus('Файл пустой.', true);
                renderEmptyState();
            }

            isPreviewExpanded = false;
            if (allCsvRows.length > 0) renderPreview();
        };
        reader.onerror = function () {
            setStatus('Не удалось прочитать файл.', true);
            renderEmptyState();
        };
        reader.readAsText(file, 'utf-8');
    }

    dropzone.addEventListener('dragover', (e) => {
        e.preventDefault();
        dropzone.classList.add('border-primary', 'bg-surface-container-highest');
    });

    dropzone.addEventListener('dragleave', (e) => {
        e.preventDefault();
        dropzone.classList.remove('border-primary', 'bg-surface-container-highest');
    });

    dropzone.addEventListener('drop', (e) => {
        e.preventDefault();
        dropzone.classList.remove('border-primary', 'bg-surface-container-highest');
        if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
            onFileSelected(e.dataTransfer.files[0]);
        }
    });

    dropzone.addEventListener('click', function () {
        fileInput.click();
    });

    fileInput.addEventListener('change', function (event) {
        const target = event.target;
        if (!(target instanceof HTMLInputElement) || !target.files || !target.files.length) return;

        onFileSelected(target.files[0]);
        fileInput.value = '';
    });

    const mobileMenuBtn = document.getElementById('mobile-menu-btn');
    const mobileMenu = document.getElementById('mobile-menu');

    if (mobileMenuBtn && mobileMenu) {
        mobileMenuBtn.addEventListener('click', () => {
            mobileMenu.classList.toggle('hidden');
            const icon = mobileMenuBtn.querySelector('.material-symbols-outlined');
            if (icon) icon.textContent = mobileMenu.classList.contains('hidden') ? 'menu' : 'close';
        });
    }

    document.getElementById('settingsBtn').addEventListener('click', () => {
        window.location.href = 'settings.html';
    });

    async function uploadAndTrain() {
        if (!selectedFile || allCsvRows.length === 0) {
            setStatus('Сначала загрузите валидный CSV файл.', true);
            return;
        }

        const formData = new FormData();
        formData.append('file', selectedFile);

        const originalButtonContent = trainButton.innerHTML;
        trainButton.disabled = true;
        trainButton.classList.add('opacity-80', 'cursor-not-allowed');
        trainButton.classList.remove('hover:scale-105');
        trainButton.innerHTML = '<span>Обучение...</span>';
        setStatus('Запущена загрузка датасета и обучение модели...', false);

        try {
            const response = await auth.fetchWithAuth(API_BASE_URL + '/api/v1/burnout/ops/train/csv', {
                method: 'POST',
                body: formData
            });

            if (response.status === 401) { auth.handleUnauthorized('login.html'); return; }
            if (response.status === 403) { window.location.replace(auth.getDefaultDashboardPath()); return; }

            const message = await response.text();

            if (!response.ok) throw new Error(message || 'Не удалось выполнить обучение модели.');

            setStatus(message || 'Модель успешно обучена и активирована.', false);
        } catch (error) {
            setStatus(error.message || 'Ошибка обучения модели.', true);
        } finally {
            trainButton.disabled = false;
            trainButton.classList.remove('opacity-80', 'cursor-not-allowed');
            trainButton.classList.add('hover:scale-105');
            trainButton.innerHTML = originalButtonContent;
        }
    }

    trainButton.addEventListener('click', uploadAndTrain);
});

