(() => {
    const { esc, del, post, put, upload, initials, label, toast, refreshMe, confirmDialog, recordForm } = Portal;
    const content = document.getElementById('content');

    async function load() {
        const me = await Portal.boot('account');
        mount(me);
        render(me);
    }

    function mount(me) {
        Portal.mountShell({
            active: 'account',
            title: 'Account settings',
            subtitle: `${label(me.role)} · signed in as ${esc(me.username)}`,
        });
    }

    function render(me) {
        content.innerHTML = `
            ${me.passwordIsDefault
                ? '<div class="banner warn">This account still uses the password the academy handed out. Change it below so only you know it.</div>'
                : ''}
            <section class="grid two">
                <div class="card">
                    <div class="card-head"><h2>Profile picture</h2><span class="hint">PNG, JPEG or WebP · up to 2 MB</span></div>
                    <div class="card-body">
                        <div class="photo-row">
                            <div id="photo-slot"></div>
                            <div>
                                <input id="photo-file" type="file" accept="image/png,image/jpeg,image/webp" hidden>
                                <button type="button" class="btn btn-primary" id="photo-pick">Choose image</button>
                                <button type="button" class="btn btn-ghost" id="photo-remove"${me.photoUrl ? '' : ' disabled'}>Remove picture</button>
                                <p class="cell-sub" id="photo-note" style="margin:8px 0 0">The picture appears beside your name in the sidebar.</p>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="card">
                    <div class="card-head"><h2>Sign-in</h2></div>
                    <div class="card-body">
                        <dl class="facts">
                            <dt>Username</dt><dd><code>${esc(me.username)}</code></dd>
                            <dt>Role</dt><dd>${esc(label(me.role))}</dd>
                            <dt>Password</dt><dd>${me.passwordIsDefault ? 'Academy default. Change it below.' : 'Chosen by you'}</dd>
                        </dl>
                    </div>
                </div>
            </section>

            ${me.role === 'ADMIN' ? `
            <section class="card">
                <div class="card-head"><h2>Administration details</h2><span class="hint">Shown to every signed-in user on Contacts</span></div>
                <div class="card-body">
                    <dl class="facts">
                        <dt>Display name</dt><dd>${esc(me.fullName)}</dd>
                        <dt>Email</dt><dd>${esc(me.email || '-')}</dd>
                        <dt>Phone</dt><dd>${esc(me.phone || '-')}</dd>
                    </dl>
                    <div class="modal-actions" style="justify-content:flex-start;margin-top:16px">
                        <button type="button" class="btn btn-ghost" id="admin-details">Edit details</button>
                    </div>
                </div>
            </section>` : ''}

            <section class="card">
                <div class="card-head"><h2>Change password</h2><span class="hint">At least 8 characters</span></div>
                <div class="card-body">
                    <form id="password-form" class="filters" autocomplete="off" style="padding:0">
                        <div class="field" style="flex:1 1 200px">
                            <label for="pw-current">Current password</label>
                            <input id="pw-current" name="currentPassword" type="password" autocomplete="current-password" required>
                        </div>
                        <div class="field" style="flex:1 1 200px">
                            <label for="pw-new">New password</label>
                            <input id="pw-new" name="newPassword" type="password" autocomplete="new-password" minlength="8" maxlength="72" required>
                        </div>
                        <div class="field" style="flex:1 1 200px">
                            <label for="pw-again">Repeat new password</label>
                            <input id="pw-again" name="confirmPassword" type="password" autocomplete="new-password" minlength="8" maxlength="72" required>
                        </div>
                        <button type="submit" class="btn btn-primary">Update password</button>
                    </form>
                    <p class="form-error" id="password-error"></p>
                </div>
            </section>`;

        renderPhoto(me);
        bindPhoto();
        bindPassword();
        if (me.role === 'ADMIN') bindAdministration(me);
    }

    function renderPhoto(me) {
        const slot = document.getElementById('photo-slot');
        slot.innerHTML = me.photoUrl
            ? `<img class="photo-frame" src="${esc(me.photoUrl)}" alt="Picture of ${esc(me.fullName)}">`
            : `<div class="photo-frame">${esc(initials(me.fullName))}</div>`;
    }

    function bindPhoto() {
        const file = document.getElementById('photo-file');
        document.getElementById('photo-pick').addEventListener('click', () => file.click());
        file.addEventListener('change', () => {
            if (file.files[0]) uploadPhoto(file.files[0]);
        });
        document.getElementById('photo-remove').addEventListener('click', removePhoto);
    }

    async function uploadPhoto(file) {
        const form = new FormData();
        form.append('file', file);
        try {
            await upload('api/profile/photo', form);
            await afterChange('Profile picture updated');
        } catch (error) {
            toast(error.message, 'err');
        }
    }

    async function removePhoto() {
        const ok = await confirmDialog({
            title: 'Remove your profile picture?',
            message: 'Your initials will show in its place until you upload another one.',
            confirmLabel: 'Remove',
        });
        if (!ok) return;
        try {
            await del('api/profile/photo');
            await afterChange('Profile picture removed');
        } catch (error) {
            toast(error.message, 'err');
        }
    }

    function bindPassword() {
        const form = document.getElementById('password-form');
        const errorBox = document.getElementById('password-error');
        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            errorBox.textContent = '';
            const current = form.elements.currentPassword.value;
            const next = form.elements.newPassword.value;
            const again = form.elements.confirmPassword.value;

            if (next.length < 8) { errorBox.textContent = 'Use at least 8 characters for the new password.'; return; }
            if (next !== again) { errorBox.textContent = 'The two new passwords do not match.'; return; }

            const button = form.querySelector('[type=submit]');
            button.disabled = true;
            button.innerHTML = '<span class="spinner"></span> Updating';
            try {
                await post('api/auth/password', { currentPassword: current, newPassword: next });
                await afterChange('Password updated. Use it the next time you sign in.');
            } catch (error) {
                errorBox.textContent = error.message;
                button.disabled = false;
                button.textContent = 'Update password';
            }
        });
    }

    /** The administration line on Contacts is the administrator's own profile; only they can change it. */
    function bindAdministration(me) {
        const button = document.getElementById('admin-details');
        if (!button) return;
        button.addEventListener('click', async () => {
            await recordForm({
                title: 'Administration details',
                subtitle: 'These details appear on the Contacts page for students, teachers and administrators.',
                fields: [
                    { name: 'fullName', label: 'Display name', required: true, span: 'full' },
                    { name: 'email', label: 'Email', type: 'email' },
                    { name: 'phone', label: 'Phone', placeholder: '+91 80 4000 1010' },
                ],
                values: me,
                submitLabel: 'Save details',
                save: async (values) => {
                    await put('api/contacts/administration', values);
                    await afterChange('Administration details updated');
                },
            });
        });
    }

    /** Redraws the signed-in identity so a new picture or password state is visible at once. */
    async function afterChange(message) {
        const fresh = await refreshMe();
        mount(fresh);
        render(fresh);
        toast(message);
    }

    load().catch(error => { content.innerHTML = `<div class="banner err">${esc(error.message)}</div>`; });
})();
