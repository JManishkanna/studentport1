/* ============================================================
   Academy Portal :: shared client (auth, fetch, shell, modals)
   ============================================================ */
const Portal = (() => {
    let csrf = { headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: null };
    let me = null;

    const NAV = [
        { section: 'Overview' },
        { key: 'dashboard', label: 'Dashboard', href: 'index.html', icon: '▦', roles: ['ADMIN', 'TEACHER', 'STUDENT'] },
        { section: 'Directory' },
        { key: 'students', label: 'Students', href: 'students.html', icon: '☺', roles: ['ADMIN', 'TEACHER'] },
        { key: 'teachers', label: 'Teachers', href: 'teachers.html', icon: '⚭', roles: ['ADMIN', 'TEACHER', 'STUDENT'] },
        { key: 'schedule', label: 'Timetable', href: 'schedule.html', icon: '▤', roles: ['ADMIN', 'TEACHER', 'STUDENT'] },
        { key: 'contacts', label: 'Contacts', href: 'contacts.html', icon: '☏', roles: ['ADMIN', 'TEACHER', 'STUDENT'] },
        { key: 'certificates', label: 'Certificates', href: 'certificates.html', icon: '✦', roles: ['ADMIN'] },
        { section: 'Account' },
        { key: 'me', label: 'My profile', href: 'student-profile.html?me=1', icon: '◐', roles: ['STUDENT'] },
        { key: 'me', label: 'My profile', href: 'teacher-profile.html?me=1', icon: '◐', roles: ['TEACHER'] },
        { key: 'me', label: 'My timetable', href: 'teacher-profile.html?me=1&tab=schedule', icon: '▤', roles: ['TEACHER'] },
        { key: 'account', label: 'Account settings', href: 'account.html', icon: '⚙', roles: ['ADMIN', 'TEACHER', 'STUDENT'] },
    ];

    const DAYS = ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

    function esc(value) {
        if (value === null || value === undefined) return '';
        return String(value)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function parse(text) {
        try { return JSON.parse(text); } catch { return null; }
    }

    async function json(path, { method = 'GET', body } = {}) {
        const headers = { Accept: 'application/json' };
        if (body !== undefined) headers['Content-Type'] = 'application/json';
        if (method !== 'GET' && method !== 'HEAD') headers[csrf.headerName] = csrf.token;

        const response = await fetch(path, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body),
            credentials: 'same-origin',
        });

        if (response.status === 401) {
            window.location.href = 'login.html?expired=1';
            throw new Error('Your session has ended. Please sign in again.');
        }

        const text = await response.text();
        const payload = text ? parse(text) : null;

        if (!response.ok) {
            const fallback = response.status === 403
                ? 'Your account is not allowed to perform that action.'
                : `Request failed with status ${response.status}`;
            const detail = payload && (payload.detail || payload.message) ? (payload.detail || payload.message) : fallback;
            const error = new Error(detail);
            error.status = response.status;
            error.payload = payload;
            throw error;
        }
        return payload;
    }

    async function refreshCsrf() {
        const data = await json('api/auth/csrf');
        if (data && data.token) {
            csrf = { headerName: data.headerName, parameterName: data.parameterName, token: data.token };
        }
    }

    const get = (path) => json(path);
    const post = (path, body) => json(path, { method: 'POST', body });
    const put = (path, body) => json(path, { method: 'PUT', body });
    const del = (path) => json(path, { method: 'DELETE' });

    /** Loads the CSRF token and the signed-in account before any page renders. */
    async function boot(activeKey, { roles } = {}) {
        await refreshCsrf();
        me = await json('api/auth/me');
        if (roles && !roles.includes(me.role)) {
            throw new Error(`The ${label(me.role)} role cannot open this page.`);
        }
        return me;
    }

    function label(role) {
        return role ? role.charAt(0) + role.slice(1).toLowerCase() : role;
    }

    function current() { return me; }
    function isAdmin() { return me && me.role === 'ADMIN'; }
    function isTeacher() { return me && me.role === 'TEACHER'; }
    function isStudent() { return me && me.role === 'STUDENT'; }

    function initials(name) {
        if (!name) return '?';
        return name.split(/\s+/).filter(Boolean).slice(0, 2).map(p => p[0].toUpperCase()).join('');
    }

    function formatDate(value) {
        if (!value) return '—';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return value;
        return date.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
    }

    function formatTime(value) {
        if (!value) return '';
        const [h, m] = String(value).split(':');
        return `${String(h).padStart(2, '0')}:${String(m || '00').padStart(2, '0')}`;
    }

    function dayName(day) { return DAYS[day] || `Day ${day}`; }

    function age(dob) {
        if (!dob) return null;
        const birth = new Date(dob);
        const now = new Date();
        let years = now.getFullYear() - birth.getFullYear();
        const m = now.getMonth() - birth.getMonth();
        if (m < 0 || (m === 0 && now.getDate() < birth.getDate())) years -= 1;
        return years;
    }

    /* ---------------------------------------------------------------- shell */

    function mountShell({ title, subtitle, actions = '', active }) {
        const sidebar = document.getElementById('sidebar');
        const topbar = document.getElementById('topbar');
        const content = document.getElementById('content');
        if (!sidebar || !topbar || !content) return content;

        const links = NAV
            .filter(item => item.roles ? item.roles.includes(me.role) : true)
            .map(item => item.section
                ? `<div class="nav-label">${esc(item.section)}</div>`
                : `<a href="${item.href}" class="${item.key === active ? 'active' : ''}">
                       <span class="ico">${item.icon}</span><span>${esc(item.label)}</span>
                   </a>`)
            .join('');

        const avatar = me.photoUrl
            ? `<img class="avatar" src="${esc(me.photoUrl)}" alt="">`
            : `<div class="avatar">${esc(initials(me.fullName))}</div>`;

        sidebar.innerHTML = `
            <div class="brand">
                <div class="brand-mark">AC</div>
                <div>
                    <div class="brand-name">Academy Portal</div>
                    <div class="brand-sub">Student management</div>
                </div>
            </div>
            <nav class="nav">${links}</nav>
            <div class="sidebar-foot">
                <div class="userchip">
                    ${avatar}
                    <div class="who">
                        <strong>${esc(me.fullName)}</strong>
                        <span>${esc(label(me.role))}${me.username ? ' · ' + esc(me.username) : ''}</span>
                    </div>
                </div>
                <button class="signout" id="signout">Sign out</button>
            </div>`;

        topbar.innerHTML = `
            <div>
                <h1>${esc(title)}</h1>
                ${subtitle ? `<p id="topbar-subtitle">${subtitle}</p>` : ''}
            </div>
            <div class="topbar-actions">${actions}</div>`;

        const signout = document.getElementById('signout');
        if (signout) {
            signout.addEventListener('click', () => submitSignout());
        }
        return content;
    }

    async function submitSignout() {
        await refreshCsrf();
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = 'logout';
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = csrf.parameterName;
        input.value = csrf.token;
        form.appendChild(input);
        document.body.appendChild(form);
        form.submit();
    }

    /* ---------------------------------------------------------------- toasts */

    function toast(message, kind = 'ok') {
        let stack = document.querySelector('.toast-stack');
        if (!stack) {
            stack = document.createElement('div');
            stack.className = 'toast-stack';
            document.body.appendChild(stack);
        }
        const item = document.createElement('div');
        item.className = `toast${kind === 'err' ? ' err' : ''}`;
        item.textContent = message;
        stack.appendChild(item);
        setTimeout(() => item.remove(), kind === 'err' ? 6500 : 4000);
    }

    /* ---------------------------------------------------------------- modal */

    function fieldHtml(field, value) {
        const id = `f-${esc(field.name)}`;
        const attrs = `id="${id}" name="${esc(field.name)}"${field.required ? ' required' : ''}${field.placeholder ? ` placeholder="${esc(field.placeholder)}"` : ''}`;
        let control;
        if (field.type === 'select') {
            const options = (field.options || [])
                .map(o => `<option value="${esc(o.value)}"${String(o.value) === String(value ?? '') ? ' selected' : ''}>${esc(o.label)}</option>`)
                .join('');
            control = `<select ${attrs}>${field.emptyLabel ? `<option value="">${esc(field.emptyLabel)}</option>` : ''}${options}</select>`;
        } else if (field.type === 'textarea') {
            control = `<textarea ${attrs} rows="${field.rows || 3}">${esc(value ?? '')}</textarea>`;
        } else {
            const type = field.type || 'text';
            control = `<input type="${type}" ${attrs} value="${esc(value ?? '')}"${field.step ? ` step="${field.step}"` : ''}${field.min !== undefined ? ` min="${field.min}"` : ''}${field.max !== undefined ? ` max="${field.max}"` : ''}>`;
        }
        const span = field.span === 2 ? ' span2' : field.span === 'full' ? ' spanfull' : '';
        return `<div class="field${span}"><label for="${id}">${esc(field.label)}${field.required ? ' *' : ''}</label>${control}</div>`;
    }

    /** Record form: the caller supplies the save function so API errors surface inside the dialog. */
    function recordForm({ title, subtitle, fields, values = {}, submitLabel = 'Save', save }) {
        return new Promise((resolve) => {
            const backdrop = document.createElement('div');
            backdrop.className = 'modal-backdrop';
            backdrop.innerHTML = `
                <div class="modal" role="dialog" aria-modal="true" aria-label="${esc(title)}">
                    <header>
                        <h3>${esc(title)}</h3>
                        ${subtitle ? `<p>${esc(subtitle)}</p>` : ''}
                    </header>
                    <form novalidate>
                        <div class="modal-grid">
                            ${fields.map(f => fieldHtml(f, values[f.name])).join('')}
                        </div>
                        <p class="form-error"></p>
                        <div class="modal-actions">
                            <button type="button" class="btn-ghost" data-close>Cancel</button>
                            <button type="submit" class="btn-primary">${esc(submitLabel)}</button>
                        </div>
                    </form>
                </div>`;
            document.body.appendChild(backdrop);

            const form = backdrop.querySelector('form');
            const errorBox = backdrop.querySelector('.form-error');
            const onKey = (event) => { if (event.key === 'Escape') { backdrop.remove(); document.removeEventListener('keydown', onKey); resolve(null); } };
            const close = () => { backdrop.remove(); document.removeEventListener('keydown', onKey); };

            backdrop.addEventListener('click', (event) => { if (event.target === backdrop) { close(); resolve(null); } });
            backdrop.querySelector('[data-close]').addEventListener('click', () => { close(); resolve(null); });
            document.addEventListener('keydown', onKey);

            form.addEventListener('submit', async (event) => {
                event.preventDefault();
                const collected = {};
                for (const field of fields) {
                    const raw = form.elements[field.name].value;
                    collected[field.name] = raw === '' ? null : raw;
                }
                const missing = fields.filter(f => f.required && (collected[f.name] === null || collected[f.name] === ''));
                if (missing.length) {
                    errorBox.textContent = `${missing.map(f => f.label).join(', ')} required before saving.`;
                    return;
                }
                const submit = form.querySelector('[type=submit]');
                submit.disabled = true;
                submit.innerHTML = '<span class="spinner"></span> Saving';
                errorBox.textContent = '';
                try {
                    await save(collected);
                    close();
                    resolve(collected);
                } catch (err) {
                    errorBox.textContent = err.message || 'Could not save this record.';
                    submit.disabled = false;
                    submit.textContent = submitLabel;
                }
            });

            form.querySelector('input, select, textarea').focus();
        });
    }

    /**
     * The administrator's view of one sign-in. The login id is always known; a password the person
     * chose cannot be read back (only its hash is stored), so the dialog offers a reset instead.
     */
    async function loginDialog({ title, subtitle, basePath, who, name }) {
        let info;
        try {
            info = await get(`${basePath}/login`);
        } catch (error) {
            toast(error.message, 'err');
            return;
        }

        const backdrop = document.createElement('div');
        backdrop.className = 'modal-backdrop';
        backdrop.innerHTML = `
            <div class="modal" style="max-width:560px" role="dialog" aria-modal="true" aria-label="Sign-in for ${esc(title)}">
                <header>
                    <h3>Sign-in for ${esc(title)}</h3>
                    <p>${esc(subtitle)}</p>
                </header>
                <div style="padding:18px 24px 24px">
                    <dl class="facts">
                        <dt>Username</dt><dd><code>${esc(info.username)}</code></dd>
                        <dt>Password</dt><dd id="login-password">${info.passwordIsDefault
                            ? `Academy default: <code>${esc(info.defaultPassword)}</code>`
                            : `Chosen by the ${esc(who)}`}</dd>
                    </dl>
                    <p class="cell-sub" id="login-note" style="margin:12px 0 0">${info.passwordIsDefault
                        ? `The ${esc(who)} has not chosen a password yet.`
                        : `The ${esc(who)} chose this password; it cannot be read back. Reset it to hand out a new one.`}</p>
                    <div class="modal-actions">
                        <button type="button" class="btn-ghost" data-close>Close</button>
                        <button type="button" class="btn-ghost" id="reset-login">Reset to default</button>
                        <button type="button" class="btn-primary" id="set-login">Set a new password</button>
                    </div>
                </div>
            </div>`;
        document.body.appendChild(backdrop);

        const close = () => { backdrop.remove(); document.removeEventListener('keydown', onKey); };
        const onKey = (event) => { if (event.key === 'Escape') close(); };
        document.addEventListener('keydown', onKey);
        backdrop.addEventListener('click', (event) => { if (event.target === backdrop) close(); });
        backdrop.querySelector('[data-close]').addEventListener('click', close);

        backdrop.querySelector('#reset-login').addEventListener('click', async (event) => {
            const button = event.currentTarget;
            button.disabled = true;
            try {
                const reset = await post(`${basePath}/login/reset`, {});
                backdrop.querySelector('#login-password').innerHTML = `New password: <code>${esc(reset.password)}</code>`;
                backdrop.querySelector('#login-note').textContent =
                    `Share this with the ${who} once. Only its hash is stored, so it cannot be shown again.`;
                toast(`Password for ${name} reset`);
            } catch (error) {
                toast(error.message, 'err');
            } finally {
                button.disabled = false;
            }
        });

        backdrop.querySelector('#set-login').addEventListener('click', async () => {
            close();
            await recordForm({
                title: `Set a password for ${title}`,
                subtitle: `The ${who} signs in with this once, then can change it from the portal.`,
                fields: [{ name: 'password', label: 'New password', type: 'password', required: true, span: 'full' }],
                submitLabel: 'Set password',
                save: async (values) => {
                    await post(`${basePath}/login/reset`, { password: values.password });
                    toast(`New password set for ${name}`);
                },
            });
        });
    }

    function confirmDialog({ title, message, confirmLabel = 'Delete', danger = true }) {
        return new Promise((resolve) => {
            const backdrop = document.createElement('div');
            backdrop.className = 'modal-backdrop';
            backdrop.innerHTML = `
                <div class="modal" style="max-width:440px" role="alertdialog" aria-modal="true">
                    <header><h3>${esc(title)}</h3></header>
                    <div style="padding:16px 24px 24px">
                        <p style="margin:0 0 18px;color:var(--ink-soft)">${esc(message)}</p>
                        <div class="modal-actions">
                            <button type="button" class="btn-ghost" data-no>Cancel</button>
                            <button type="button" class="${danger ? 'btn-danger' : 'btn-primary'}" data-yes>${esc(confirmLabel)}</button>
                        </div>
                    </div>
                </div>`;
            document.body.appendChild(backdrop);
            const close = (value) => { backdrop.remove(); resolve(value); };
            backdrop.querySelector('[data-no]').addEventListener('click', () => close(false));
            backdrop.querySelector('[data-yes]').addEventListener('click', () => close(true));
            backdrop.addEventListener('click', (event) => { if (event.target === backdrop) close(false); });
        });
    }

    /* ---------------------------------------------------------------- snippets */

    function statusBadge(status) {
        const cls = (status || '').toLowerCase().replace(' ', '_');
        const text = status ? status.replace(/_/g, ' ') : 'Unknown';
        return `<span class="badge ${esc(cls)}">${esc(text)}</span>`;
    }

    /** A profile picture when the person has one, otherwise their initials in the same frame. */
    function avatar(photoUrl, name, variant = 'avatar-lg') {
        return photoUrl
            ? `<img class="${variant}" src="${esc(photoUrl)}" alt="Picture of ${esc(name)}">`
            : `<div class="${variant}">${esc(initials(name))}</div>`;
    }

    function slotCard(row, { showClass = true } = {}) {
        return `
            <div class="slot" data-id="${row.id}">
                <div class="time">${formatTime(row.startTime)} – ${formatTime(row.endTime)}</div>
                <div class="subj">${esc(row.subjectName)} <span class="badge">${esc(row.subjectCode)}</span></div>
                <div class="who">${esc(row.teacherName)}</div>
                ${showClass ? `<div class="room">${esc(row.classLevel)} · Sec ${esc(row.section)} · ${esc(row.room || 'room not set')}</div>`
                : `<div class="room">${esc(row.room || 'room not set')}</div>`}
            </div>`;
    }

    function emptyState(title, hint) {
        return `<div class="empty"><strong>${esc(title)}</strong>${esc(hint || '')}</div>`;
    }

    function queryParam(name, fallback = null) {
        const value = new URLSearchParams(window.location.search).get(name);
        return value === null || value === '' ? fallback : value;
    }

    /** Posts for a file answer and saves it; a failed run still arrives as JSON. */
    async function download(path, body, fallbackName = 'download.pdf') {
        const response = await fetch(path, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
            body: JSON.stringify(body),
            credentials: 'same-origin',
        });

        if (response.status === 401) {
            window.location.href = 'login.html?expired=1';
            throw new Error('Your session has ended. Please sign in again.');
        }

        const type = response.headers.get('Content-Type') || '';
        if (!response.ok || !type.includes('application/pdf')) {
            const payload = parse(await response.text());
            const detail = payload && (payload.detail || payload.message);
            const error = new Error(detail || `Request failed with status ${response.status}`);
            error.status = response.status;
            throw error;
        }

        const quoted = /filename="?([^";]+)"?/.exec(response.headers.get('Content-Disposition') || '');
        const filename = quoted ? quoted[1] : fallbackName;
        const url = URL.createObjectURL(await response.blob());
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = filename;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(url);

        return { filename, count: Number(response.headers.get('X-Certificate-Count')) || null };
    }

    /** Sends a multipart form (profile pictures) and returns the JSON answer. */
    async function upload(path, formData) {
        const response = await fetch(path, {
            method: 'POST',
            headers: { [csrf.headerName]: csrf.token },
            body: formData,
            credentials: 'same-origin',
        });

        if (response.status === 401) {
            window.location.href = 'login.html?expired=1';
            throw new Error('Your session has ended. Please sign in again.');
        }

        const text = await response.text();
        const payload = text ? parse(text) : null;
        if (!response.ok) {
            const fallback = response.status === 403
                ? 'Your account is not allowed to perform that action.'
                : `Request failed with status ${response.status}`;
            const detail = payload && (payload.detail || payload.message) ? (payload.detail || payload.message) : fallback;
            const error = new Error(detail);
            error.status = response.status;
            throw error;
        }
        return payload;
    }

    /** Re-reads /api/auth/me so a new picture or password state shows up without a reload. */
    async function refreshMe() {
        me = await json('api/auth/me');
        return me;
    }

    return {
        esc, get, post, put, del, boot, refreshCsrf, current, isAdmin, isTeacher, isStudent, label,
        initials, formatDate, formatTime, dayName, days: DAYS, age,
        mountShell, toast, recordForm, confirmDialog, loginDialog, statusBadge, avatar, slotCard, emptyState, queryParam,
        download, upload, refreshMe,
    };
})();
