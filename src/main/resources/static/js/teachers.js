(() => {
    const { esc, get, post, put, del, initials, statusBadge, recordForm, confirmDialog, toast, queryParam } = Portal;
    const content = document.getElementById('content');

    const state = { q: queryParam('q', ''), department: queryParam('department', ''), teachers: [], departments: [] };

    const STATUSES = [
        { value: 'ACTIVE', label: 'Active' },
        { value: 'ON_LEAVE', label: 'On leave' },
        { value: 'INACTIVE', label: 'Inactive' },
    ];

    const FIELDS = [
        { name: 'employeeCode', label: 'Employee code', required: true, placeholder: 'ACAD-T-008' },
        { name: 'department', label: 'Department', required: true, placeholder: 'Mathematics' },
        { name: 'firstName', label: 'First name', required: true },
        { name: 'lastName', label: 'Last name', required: true },
        { name: 'designation', label: 'Designation', placeholder: 'Senior Faculty' },
        { name: 'status', label: 'Status', type: 'select', options: STATUSES },
        { name: 'email', label: 'Email', type: 'email', required: true },
        { name: 'phone', label: 'Phone' },
        { name: 'officeRoom', label: 'Office / room' },
        { name: 'joiningDate', label: 'Joining date', type: 'date' },
        { name: 'specialization', label: 'Specialization', span: 'full' },
        { name: 'qualification', label: 'Qualification', span: 'full' },
    ];

    async function load() {
        const me = await Portal.boot('teachers');
        state.departments = await get('api/teachers/departments');
        state.teachers = await fetchRows();

        Portal.mountShell({
            active: 'teachers',
            title: 'Faculty directory',
            subtitle: `${state.teachers.length} teacher(s) · contact details and timetables`,
            actions: me.role === 'ADMIN' ? '<button class="btn btn-primary" id="add">+ Add teacher</button>' : '',
        });

        renderFilters();
        renderGrid();

        if (me.role === 'ADMIN') document.getElementById('add').addEventListener('click', () => openForm(null));
    }

    function fetchRows() {
        const params = new URLSearchParams();
        if (state.q) params.set('q', state.q);
        if (state.department) params.set('department', state.department);
        return get(`api/teachers?${params.toString()}`);
    }

    function renderFilters() {
        const bar = document.createElement('section');
        bar.className = 'card';
        bar.innerHTML = `
            <div class="filters">
                <div class="field" style="flex:1 1 260px">
                    <label for="fq">Search</label>
                    <input id="fq" type="search" placeholder="Name, email or employee code" value="${esc(state.q)}">
                </div>
                <div class="field">
                    <label for="fd">Department</label>
                    <select id="fd">
                        <option value="">All departments</option>
                        ${state.departments.map(d => `<option value="${esc(d)}"${d === state.department ? ' selected' : ''}>${esc(d)}</option>`).join('')}
                    </select>
                </div>
                <button class="btn btn-ghost" id="reset">Clear</button>
            </div>`;
        content.appendChild(bar);

        let timer;
        bar.querySelector('#fq').addEventListener('input', (event) => {
            state.q = event.target.value;
            clearTimeout(timer);
            timer = setTimeout(refresh, 260);
        });
        bar.querySelector('#fd').addEventListener('change', (event) => { state.department = event.target.value; refresh(); });
        bar.querySelector('#reset').addEventListener('click', () => {
            state.q = ''; state.department = '';
            bar.querySelector('#fq').value = '';
            bar.querySelector('#fd').value = '';
            refresh();
        });
    }

    async function refresh() {
        try {
            state.teachers = await fetchRows();
            const subtitle = document.getElementById('topbar-subtitle');
            if (subtitle) subtitle.textContent = `${state.teachers.length} teacher(s) · contact details and timetables`;
            renderGrid();
        } catch (error) {
            toast(error.message, 'err');
        }
    }

    function renderGrid() {
        const existing = content.querySelector('#directory');
        const card = existing || document.createElement('section');
        card.className = 'card';
        card.id = 'directory';
        card.innerHTML = `
            <div class="card-head"><h2>Teachers</h2><span class="hint">Select a card for the full record and timetable</span></div>
            <div class="card-body">
                <div class="grid cards">
                    ${state.teachers.map(t => `
                        <article class="card" data-id="${t.id}" style="cursor:pointer">
                            <div class="card-body">
                                <div class="person">
                                    <div class="avatar-lg">${esc(initials(`${t.firstName} ${t.lastName}`))}</div>
                                    <div style="min-width:0">
                                        <div class="cell-strong">${esc(t.firstName)} ${esc(t.lastName)}</div>
                                        <div class="cell-sub">${esc(t.designation || 'Faculty')} · ${esc(t.department)}</div>
                                    </div>
                                </div>
                                <dl class="facts" style="margin-top:12px">
                                    <dt>Email</dt><dd style="word-break:break-all">${esc(t.email)}</dd>
                                    <dt>Phone</dt><dd>${esc(t.phone || '—')}</dd>
                                    <dt>Office</dt><dd>${esc(t.officeRoom || '—')}</dd>
                                    <dt>Teaches</dt><dd>${esc(t.specialization || '—')}</dd>
                                </dl>
                                <div class="chip-row" style="margin-top:14px">
                                    ${statusBadge(t.status)}
                                    <span class="badge">${esc(t.employeeCode)}</span>
                                    ${Portal.isAdmin() ? `<button class="btn btn-ghost btn-sm" data-edit="${t.id}">Edit</button>
                                       <button class="btn btn-ghost btn-sm" data-login="${t.id}">Login</button>
                                       <button class="btn btn-danger btn-sm" data-remove="${t.id}">Delete</button>` : ''}
                                </div>
                            </div>
                        </article>`).join('') || `<div style="grid-column:1/-1">${Portal.emptyState('No teachers match those filters.', '')}</div>`}
                </div>
            </div>`;
        if (!existing) content.appendChild(card);

        card.querySelectorAll('article[data-id]').forEach(article => article.addEventListener('click', (event) => {
            if (event.target.closest('button')) return;
            window.location.href = `teacher-profile.html?id=${article.dataset.id}`;
        }));
        card.querySelectorAll('[data-edit]').forEach(button => button.addEventListener('click', (event) => {
            event.stopPropagation();
            openForm(state.teachers.find(t => String(t.id) === button.dataset.edit));
        }));
        card.querySelectorAll('[data-login]').forEach(button => button.addEventListener('click', (event) => {
            event.stopPropagation();
            const teacher = state.teachers.find(t => String(t.id) === button.dataset.login);
            openLoginDialog(teacher);
        }));
        card.querySelectorAll('[data-remove]').forEach(button => button.addEventListener('click', async (event) => {
            event.stopPropagation();
            const teacher = state.teachers.find(t => String(t.id) === button.dataset.remove);
            await remove(teacher);
        }));
    }

    async function openForm(teacher) {
        await recordForm({
            title: teacher ? `Edit ${teacher.firstName} ${teacher.lastName}` : 'Add a teacher',
            subtitle: teacher ? teacher.employeeCode : 'Employee codes and emails must be unique.',
            fields: FIELDS,
            values: teacher || { status: 'ACTIVE', joiningDate: new Date().toISOString().slice(0, 10) },
            submitLabel: teacher ? 'Save changes' : 'Add teacher',
            save: async (values) => {
                const saved = teacher
                    ? await put(`api/teachers/${teacher.id}`, { id: teacher.id, ...values })
                    : await post('api/teachers', values);
                toast(teacher ? `${saved.firstName} ${saved.lastName} updated` : `${saved.firstName} ${saved.lastName} added`);
                state.departments = await get('api/teachers/departments');
                await refresh();
            },
        });
    }

    /** The sign-in is the academy email; only the default password can be shown, a chosen one cannot. */
    function openLoginDialog(teacher) {
        const full = `${teacher.firstName} ${teacher.lastName}`;
        return Portal.loginDialog({
            title: full,
            subtitle: `${teacher.employeeCode} · ${teacher.department}`,
            basePath: `api/teachers/${teacher.id}`,
            who: 'teacher',
            name: teacher.firstName,
        });
    }

    async function remove(teacher) {
        const ok = await confirmDialog({
            title: `Remove ${teacher.firstName} ${teacher.lastName}?`,
            message: 'Any class sessions assigned to this teacher are deleted with the record.',
        });
        if (!ok) return;
        try {
            await del(`api/teachers/${teacher.id}`);
            toast(`${teacher.firstName} ${teacher.lastName} removed`);
            await refresh();
        } catch (error) {
            toast(error.message, 'err');
        }
    }

    load().catch(error => { content.innerHTML = `<div class="banner err">${esc(error.message)}</div>`; });
})();
