(function () {
    var TOKEN_KEY = 'bps.auth.token';
    var REFRESH_TOKEN_KEY = 'bps.auth.refresh_token';
    var TYPE_KEY = 'bps.auth.type';
    var USERNAME_KEY = 'bps.auth.username';
    var ROLE_KEY = 'bps.auth.role';

    function removeAuthPendingClass() {
        document.documentElement.classList.remove('auth-pending');
    }

    function safeGetItem(key) {
        try { return localStorage.getItem(key); } catch (error) { return null; }
    }

    function safeSetItem(key, value) {
        try { localStorage.setItem(key, value); } catch (error) { return; }
    }

    function safeRemoveItem(key) {
        try { localStorage.removeItem(key); } catch (error) { return; }
    }

    function decodeBase64Url(value) {
        var normalized = value.replace(/-/g, '+').replace(/_/g, '/');
        var padding = normalized.length % 4;
        if (padding) normalized += '='.repeat(4 - padding);
        return atob(normalized);
    }

    function parseJwtPayload(token) {
        if (!token || token.split('.').length < 2) return null;
        try {
            var encodedPayload = token.split('.')[1];
            var decodedPayload = decodeBase64Url(encodedPayload);
            return JSON.parse(decodedPayload);
        } catch (error) { return null; }
    }

    function getToken() {return safeGetItem(TOKEN_KEY);}
    function getRefreshToken() {return safeGetItem(REFRESH_TOKEN_KEY);}
    function getRole() {return safeGetItem(ROLE_KEY);}

    function clearAuth() {
        safeRemoveItem(TOKEN_KEY);
        safeRemoveItem(REFRESH_TOKEN_KEY);
        safeRemoveItem(TYPE_KEY);
        safeRemoveItem(USERNAME_KEY);
        safeRemoveItem(ROLE_KEY);
    }

    function saveAuth(authData, fallbackUsername) {
        if (!authData || !authData.token) return;

        safeSetItem(TOKEN_KEY, authData.token);
        if (authData.refreshToken) {
            safeSetItem(REFRESH_TOKEN_KEY, authData.refreshToken);
        }
        safeSetItem(TYPE_KEY, authData.type || 'Bearer');
        safeSetItem(USERNAME_KEY, authData.username || fallbackUsername || '');
        if (authData.role) safeSetItem(ROLE_KEY, authData.role);
    }

    function isTokenExpired(token, skewSeconds) {
        var payload = parseJwtPayload(token);
        var skew = typeof skewSeconds === 'number' ? skewSeconds : 10;
        if (!payload || !payload.exp) return true;
        var nowInSeconds = Math.floor(Date.now() / 1000);
        return payload.exp <= nowInSeconds + skew;
    }

    function hasValidToken() {
        var token = getToken();
        var refreshToken = getRefreshToken();
        if (!token && !refreshToken) return false;
        if (isTokenExpired(token, 0) && !refreshToken) {
            clearAuth();
            return false;
        }
        return true;
    }

    let refreshPromise = null;

    async function refreshAccessToken() {
        if (refreshPromise) return refreshPromise;

        refreshPromise = (async () => {
            try {
                const baseUrl = window.BPS_API_BASE_URL || 'http://localhost:8080';
                const res = await fetch(baseUrl + '/api/v1/auth/refresh-token', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ refreshToken: getRefreshToken() })
                });
                if (res.ok) {
                    const data = await res.json();
                    safeSetItem(TOKEN_KEY, data.accessToken);
                    safeSetItem(REFRESH_TOKEN_KEY, data.refreshToken);
                    return data.accessToken;
                } else {
                    clearAuth();
                    return null;
                }
            } catch (e) {
                console.error("Ошибка при обновлении токена", e);
                return null;
            } finally {
                refreshPromise = null;
            }
        })();

        return refreshPromise;
    }

    async function fetchWithAuth(url, options = {}) {
        let token = getToken();
        let refreshToken = getRefreshToken();
        if (token && refreshToken && isTokenExpired(token, 5)) {
            token = await refreshAccessToken();
        }
        if (!options.headers) options.headers = {};
        if (token) options.headers['Authorization'] = 'Bearer ' + token;
        let response = await fetch(url, options);
        if (response.status === 401 && refreshToken) {
            token = await refreshAccessToken();
            if (token) {
                options.headers['Authorization'] = 'Bearer ' + token;
                response = await fetch(url, options);
            } else {
                handleUnauthorized('login.html');
            }
        }
        return response;
    }

    function handleUnauthorized(loginPath) {
        clearAuth();
        removeAuthPendingClass();
        window.location.replace(loginPath || 'login.html');
        return false;
    }

    function requireAuth(loginPath) {
        if (!hasValidToken()) return handleUnauthorized(loginPath);
        removeAuthPendingClass();
        return true;
    }

    function guardProtectedPage(loginPath) {
        return requireAuth(loginPath || 'login.html');
    }

    function getDefaultDashboardPath() {
        var role = getRole();
        if (role === 'ROLE_VIEWER') return 'dashboardViewer.html';
        return 'dashboard.html';
    }

    function guardLoginPage(dashboardPath) {
        if (hasValidToken()) {
            window.location.replace(dashboardPath || getDefaultDashboardPath());
            return false;
        }
        removeAuthPendingClass();
        return true;
    }

    function startExpiryWatcher(loginPath, intervalMs) {
        var interval = typeof intervalMs === 'number' ? intervalMs : 30000;
        window.setInterval(async function () {
            var token = getToken();
            var refreshToken = getRefreshToken();
            if (token && refreshToken && isTokenExpired(token, 60)) {
                const newToken = await refreshAccessToken();
                if (!newToken) handleUnauthorized(loginPath || 'login.html');
            }
        }, interval);
    }

    window.BPSAuth = {
        TOKEN_KEY: TOKEN_KEY,
        getToken: getToken,
        getRefreshToken: getRefreshToken,
        getRole: getRole,
        saveAuth: saveAuth,
        clearAuth: clearAuth,
        hasValidToken: hasValidToken,
        isTokenExpired: isTokenExpired,
        handleUnauthorized: handleUnauthorized,
        requireAuth: requireAuth,
        guardProtectedPage: guardProtectedPage,
        guardLoginPage: guardLoginPage,
        startExpiryWatcher: startExpiryWatcher,
        parseJwtPayload: parseJwtPayload,
        getDefaultDashboardPath: getDefaultDashboardPath,
        fetchWithAuth: fetchWithAuth
    };
})();

document.addEventListener('DOMContentLoaded', function () {
    const avatarBtn = document.getElementById('user-avatar-btn');
    const dropdownMenu = document.getElementById('user-dropdown-menu');
    const logoutBtn = document.getElementById('logout-btn');

    if (avatarBtn && dropdownMenu) {
        avatarBtn.addEventListener('click', function (e) {
            e.stopPropagation();
            dropdownMenu.classList.toggle('hidden');
        });

        document.addEventListener('click', function (e) {
            if (!dropdownMenu.contains(e.target) && e.target !== avatarBtn) {
                dropdownMenu.classList.add('hidden');
            }
        });
    }

    if (logoutBtn) {
        logoutBtn.addEventListener('click', async function () {
            const token = window.BPSAuth.getToken();
            if (token) {
                try {
                    const baseUrl = window.BPS_API_BASE_URL || 'http://localhost:8080';
                    await fetch(baseUrl + '/api/v1/auth/logout', {
                        method: 'POST',
                        headers: { 'Authorization': 'Bearer ' + token }
                    });
                } catch (e) { console.error('Ошибка выхода:', e); }
            }
            window.BPSAuth.clearAuth();
            window.location.replace('login.html');
        });
    }
});