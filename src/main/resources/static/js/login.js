(() => {
    const form = document.getElementById('login-form');
    const notice = document.getElementById('notice');
    const button = document.getElementById('signin');
    const params = new URLSearchParams(window.location.search);

    function show(kind, message) {
        notice.className = `banner ${kind}`;
        notice.textContent = message;
        notice.hidden = false;
    }

    if (params.has('error')) show('err', 'That username and password combination was not recognised.');
    if (params.has('logout')) show('info', 'You have been signed out.');
    if (params.has('expired')) show('warn', 'Your session ended. Please sign in again.');

    // Already signed in? Skip the form entirely.
    fetch('api/auth/me', { credentials: 'same-origin' })
        .then((response) => { if (response.ok) window.location.href = 'index.html'; });

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        button.disabled = true;
        button.innerHTML = '<span class="spinner"></span> Signing in';

        try {
            // Read the CSRF token first: Spring Security rejects the POST without it.
            const csrfResponse = await fetch('api/auth/csrf', { credentials: 'same-origin' });
            const csrf = await csrfResponse.json();

            const body = new URLSearchParams({
                username: document.getElementById('username').value,
                password: document.getElementById('password').value,
            });

            const response = await fetch('login', {
                method: 'POST',
                credentials: 'same-origin',
                redirect: 'follow',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                    [csrf.headerName || 'X-XSRF-TOKEN']: csrf.token || '',
                },
                body,
            });

            if (response.redirected && !response.url.includes('login.html')) {
                window.location.href = response.url;
                return;
            }
            show('err', 'Sign in failed. Check the username and password and try again.');
            button.disabled = false;
            button.textContent = 'Sign in';
        } catch (error) {
            show('err', 'Could not reach the portal: ' + error.message);
            button.disabled = false;
            button.textContent = 'Sign in';
        }
    });
})();
