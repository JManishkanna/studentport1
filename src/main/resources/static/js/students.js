(() => {
    const { esc, get, post, put, del, statusBadge, avatar, recordForm, confirmDialog, toast, queryParam } = Portal;
    const content = document.getElementById('content');

    const state = {
        q: queryParam('q', ''),
        classLevel: queryParam('classLevel', ''),
        status: queryParam('status', ''),
        students: [],
        levels: [],
    };

    const GENDERS = [
        { value: 'MALE', label: 'Male' },
        { value: 'FEMALE', label: 'Female' },
        { value: 'OTHER', label: 'Other' },
    ];
    const STATUSES = [
        { value: 'ACTIVE', label: 'Active' },
        { value: 'SUSPENDED', label: 'Suspended' },
        { value: 'ALUMNI', label: 'Alumni' },
    ];

    function studentFields() {
        return [
            { name: 'rollNumber', label: 'Roll number', required: true, placeholder: 'ACAD-1105' },
            { name: 'classLevel', label: 'Class level', required: true, placeholder: 'Full Stack Java' },
            { name: 'firstName', label: 'First name', required: true },
            { name: 'lastName', label: 'Last name', required: true },
            { name: 'section', label: 'Section', placeholder: 'A' },
            { name: 'gender', label: 'Gender', type: 'select', options: GENDERS, emptyLabel: 'Select' },
            { name: 'email', label: 'Email', type: 'email', required: true, placeholder: 'name@student.academy.edu' },
            { name: 'phone', label: 'Phone', placeholder: '+91 90000 00000' },
            { name: 'dateOfBirth', label: 'Date of birth', type: 'date' },
            { name: 'admissionDate', label: 'Admission date', type: 'date' },
            { name: 'status', label: 'Status', type: 'select', options: STATUSES },
            { name: 'city', label: 'City' },
            { name: 'guardianName', label: 'Guardian name' },
            { name: 'guardianPhone', label: 'Guardian phone' },
            { name: 'guardianEmail', label: 'Guardian email', type: 'email' },
            { name: 'addressLine', label: 'Address', type: 'textarea', span: 'full', rows: 2 },
        ];
    }

    async function load() {
        const me = await Portal.boot('students', { roles: ['ADMIN', 'TEACHER'] });
        state.levels = await get('api/students/class-levels');
        state.students = await fetchRows();

        Portal.mountShell({
            active: 'students',
            title: 'Student records',
            subtitle: `${state.students.length} student(s) match the current filters`,
            actions: me.role === 'ADMIN' ? '<button class="btn btn-primary" id="add">+ Admit student</button>' : '',
        });

        renderFilters();
        renderTable();

        if (me.role === 'ADMIN') {
            document.getElementById('add').addEventListener('click', () => openForm(null));
        }
    }

    function fetchRows() {
        const params = new URLSearchParams();
        if (state.q) params.set('q', state.q);
        if (state.classLevel) params.set('classLevel', state.classLevel);
        if (state.status) params.set('status', state.status);
        return get(`api/students?${params.toString()}`);
    }

    function renderFilters() {
        const bar = document.createElement('section');
        bar.className = 'card';
        bar.innerHTML = `
            <div class="filters">
                <div class="field" style="flex:1 1 260px">
                    <label for="fq">Search</label>
                    <input id="fq" type="search" placeholder="Name, roll number or email" value="${esc(state.q)}">
                </div>
                <div class="field">
                    <label for="flevel">Class level</label>
                    <select id="flevel">
                        <option value="">All classes</option>
                        ${state.levels.map(l => `<option value="${esc(l)}"${l === state.classLevel ? ' selected' : ''}>${esc(l)}</option>`).join('')}
                    </select>
                </div>
                <div class="field">
                    <label for="fstatus">Status</label>
                    <select id="fstatus">
                        <option value="">Any status</option>
                        ${STATUSES.map(s => `<option value="${s.value}"${s.value === state.status ? ' selected' : ''}>${s.label}</option>`).join('')}
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
        bar.querySelector('#flevel').addEventListener('change', (event) => { state.classLevel = event.target.value; refresh(); });
        bar.querySelector('#fstatus').addEventListener('change', (event) => { state.status = event.target.value; refresh(); });
        bar.querySelector('#reset').addEventListener('click', () => {
            state.q = ''; state.classLevel = ''; state.status = '';
            bar.querySelector('#fq').value = '';
            bar.querySelector('#flevel').value = '';
            bar.querySelector('#fstatus').value = '';
            refresh();
        });
    }

    async function refresh() {
        try {
            state.students = await fetchRows();
            const subtitle = document.getElementById('topbar-subtitle');
            if (subtitle) subtitle.textContent = `${state.students.length} student(s) match the current filters`;
            renderTable();
        } catch (error) {
            toast(error.message, 'err');
        }
    }

    function renderTable() {
        const existing = content.querySelector('#roster');
        const card = existing || document.createElement('section');
        card.className = 'card';
        card.id = 'roster';
        card.innerHTML = `
            <div class="card-head">
                <h2>Roster</h2>
                <span class="hint">Select a row to open the full profile</span>
            </div>
            <div class="card-body tight">
                <table>
                    <thead>
                        <tr><th>Student</th><th>Roll</th><th>Class</th><th>Guardian</th><th>Contact</th><th>Status</th><th></th></tr>
                    </thead>
                    <tbody>
                        ${state.students.map(s => `
                            <tr class="clickable" data-id="${s.id}" data-roll="${esc(s.rollNumber)}">
                                <td>
                                    <div class="person">
                                        ${avatar(s.photoUrl, `${s.firstName} ${s.lastName}`, 'avatar-xs')}
                                        <div>
                                            <div class="cell-strong">${esc(s.firstName)} ${esc(s.lastName)}</div>
                                            <div class="cell-sub">${esc(s.email)}</div>
                                        </div>
                                    </div>
                                </td>
                                <td>${esc(s.rollNumber)}</td>
                                <td><span class="badge">${esc(s.classLevel)}${s.section ? ' · ' + esc(s.section) : ''}</span></td>
                                <td>
                                    <div class="cell-strong">${esc(s.guardianName || '-')}</div>
                                    <div class="cell-sub">${esc(s.guardianPhone || '')}</div>
                                </td>
                                <td>${esc(s.phone || '-')}<div class="cell-sub">${esc(s.city || '')}</div></td>
                                <td>${statusBadge(s.status)}</td>
                                <td class="num">
                                    ${Portal.isAdmin() ? `
                                        <button class="btn btn-ghost btn-sm" data-edit="${s.id}">Edit</button>
                                        <button class="btn btn-ghost btn-sm" data-login="${s.id}">Login</button>
                                        <button class="btn btn-danger btn-sm" data-remove="${s.id}">Delete</button>` : ''}
                                </td>
                            </tr>`).join('') || `<tr><td colspan="7">${Portal.emptyState('No students match those filters.', 'Try clearing the search or pick another class.')}</td></tr>`}
                    </tbody>
                </table>
            </div>`;

        if (!existing) content.appendChild(card);

        card.querySelectorAll('tr[data-id]').forEach(row => {
            row.addEventListener('click', (event) => {
                if (event.target.closest('button')) return;
                window.location.href = `student-profile.html?id=${row.dataset.id}`;
            });
        });
        card.querySelectorAll('[data-edit]').forEach(button => button.addEventListener('click', async (event) => {
            event.stopPropagation();
            const student = state.students.find(s => String(s.id) === button.dataset.edit);
            openForm(student);
        }));
        card.querySelectorAll('[data-login]').forEach(button => button.addEventListener('click', async (event) => {
            event.stopPropagation();
            const student = state.students.find(s => String(s.id) === button.dataset.login);
            await openLoginDialog(student);
        }));
        card.querySelectorAll('[data-remove]').forEach(button => button.addEventListener('click', async (event) => {
            event.stopPropagation();
            const student = state.students.find(s => String(s.id) === button.dataset.remove);
            await removeStudent(student);
        }));
    }

    async function openForm(student) {
        const fields = studentFields();
        await recordForm({
            title: student ? `Edit ${student.firstName} ${student.lastName}` : 'Admit a new student',
            subtitle: student ? `Roll ${student.rollNumber}` : 'Roll numbers and emails must be unique across the academy.',
            fields,
            submitLabel: student ? 'Save changes' : 'Admit student',
            values: student || { status: 'ACTIVE', admissionDate: new Date().toISOString().slice(0, 10) },
            save: async (values) => {
                const savedRecord = student
                    ? await put(`api/students/${student.id}`, { id: student.id, ...values })
                    : await post('api/students', values);
                toast(student ? `${savedRecord.firstName} ${savedRecord.lastName} updated` : `${savedRecord.firstName} admitted as ${savedRecord.rollNumber}`);
                await refresh();
            },
        });
    }

    /** The sign-in is the academy email; only the default password can be shown, a chosen one cannot. */
    function openLoginDialog(student) {
        const full = `${student.firstName} ${student.lastName}`;
        return Portal.loginDialog({
            title: full,
            subtitle: `Roll ${student.rollNumber} · ${student.classLevel}`,
            basePath: `api/students/${student.id}`,
            who: 'student',
            name: student.firstName,
        });
    }

    async function removeStudent(student) {
        const ok = await confirmDialog({
            title: `Remove ${student.firstName} ${student.lastName}?`,
            message: `Roll ${student.rollNumber}. Their subject enrollments are removed too. This cannot be undone.`,
        });
        if (!ok) return;
        try {
            await del(`api/students/${student.id}`);
            toast(`${student.firstName} ${student.lastName} removed`);
            await refresh();
        } catch (error) {
            toast(error.message, 'err');
        }
    }

    load().catch(error => { content.innerHTML = `<div class="banner err">${esc(error.message)}</div>`; });
})();
