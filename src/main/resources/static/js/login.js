document.addEventListener('DOMContentLoaded', function () {
    const API_BASE_URL = window.BPS_API_BASE_URL || 'http://localhost:8080';
    const auth = window.BPSAuth;

    if (!auth) {
        return;
    }

    const passwordInput = document.getElementById('password');
    const toggleButton = document.getElementById('toggle-password');
    const loginForm = document.getElementById('login-form');
    const emailInput = document.getElementById('email');
    const loginError = document.getElementById('login-error');
    const loginSubmit = document.getElementById('login-submit');

    if (!passwordInput || !toggleButton || !loginForm || !emailInput || !loginSubmit) {
        return;
    }

    if (auth.hasValidToken()) {
        window.location.replace(auth.getDefaultDashboardPath());
        return;
    }

    const icon = toggleButton.querySelector('span');

    function showError(message) {
        if (!loginError) {
            return;
        }
        loginError.textContent = message;
        loginError.classList.remove('hidden');
    }

    function clearError() {
        if (!loginError) {
            return;
        }
        loginError.textContent = '';
        loginError.classList.add('hidden');
    }

    toggleButton.addEventListener('click', function () {
        const isHidden = passwordInput.type === 'password';
        passwordInput.type = isHidden ? 'text' : 'password';

        if (icon) {
            icon.textContent = isHidden ? 'visibility_off' : 'visibility';
        }
    });

    loginForm.addEventListener('submit', async function (event) {
        event.preventDefault();
        clearError();

        const username = emailInput.value.trim();
        const password = passwordInput.value;

        if (!username || !password) {
            showError('Введите корпоративную почту и пароль.');
            return;
        }

        const originalLabel = loginSubmit.innerHTML;
        loginSubmit.disabled = true;
        loginSubmit.classList.add('opacity-80');
        loginSubmit.innerHTML = '<span>Выполняется вход...</span>';

        try {
            const response = await fetch(API_BASE_URL + '/api/v1/auth/login', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    username: username,
                    password: password
                })
            });

            if (!response.ok) {
                throw new Error('Неверные учетные данные или сервер недоступен.');
            }

            const authData = await response.json();

            auth.saveAuth(authData, username);

            window.location.replace(auth.getDefaultDashboardPath());
        } catch (error) {
            showError(error.message || 'Ошибка авторизации. Попробуйте еще раз.');
            loginSubmit.disabled = false;
            loginSubmit.classList.remove('opacity-80');
            loginSubmit.innerHTML = originalLabel;
        }
    });
});