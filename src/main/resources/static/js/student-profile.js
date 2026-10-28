(() => {
    const { esc, get, post, put, del, upload, statusBadge, avatar, formatDate, formatTime, dayName, age, recordForm, confirmDialog, toast, queryParam } = Portal;
    const content = document.getElementById('content');

    const STATUSES = [
        { value: 'ACTIVE', label: 'Active' },
        { value: 'SUSPENDED', label: 'Suspended' },
        { value: 'ALUMNI', label: 'Alumni' },
    ];

    async function load() {
        const me = await Portal.boot('me');
        const asSelf = queryParam('me') === '1' || (!queryParam('id') && me.role === 'STUDENT');
        const source = asSelf ? 'api/students/me' : `api/students/${queryParam('id', '')}`;

        if (!asSelf && !queryParam('id')) {
            throw new Error('Open a student from the roster, or use the "My profile" link.');
        }

        const student = await get(source);
        const scoped = asSelf
            ? { schedule: 'api/students/me/schedule', subjects: 'api/students/me/subjects', classmates: 'api/students/me/classmates' }
            : { schedule: `api/students/${student.id}/schedule`, subjects: `api/students/${student.id}/subjects`, classmates: `api/students/${student.id}/classmates` };

        const [schedule, subjects, classmates] = await Promise.all([
            get(scoped.schedule), get(scoped.subjects), get(scoped.classmates),
        ]);

        const byId = !asSelf;
        Portal.mountShell({
            active: asSelf ? 'me' : 'students',
            title: `${student.firstName} ${student.lastName}`,
            subtitle: `${esc(student.rollNumber)} · ${esc(student.classLevel)}${student.section ? ' · Section ' + esc(student.section) : ''} · admitted ${formatDate(student.admissionDate)}`,
            actions: `<a class="btn btn-ghost" href="${byId ? 'students.html' : 'index.html'}">${byId ? 'Back to roster' : 'Dashboard'}</a>`,
        });

        render(student, schedule, subjects, classmates, me, asSelf);
    }

    function render(student, schedule, subjects, classmates, me, asSelf) {
        const days = [1, 2, 3, 4, 5, 6];
        const yearsOld = age(student.dateOfBirth);
        const canAdmin = me.role === 'ADMIN';

        content.innerHTML = `
            <section class="card">
                <div class="card-body">
                    <div class="person">
                        ${avatar(student.photoUrl, `${student.firstName} ${student.lastName}`)}
                        <div>
                            <h2>${esc(student.firstName)} ${esc(student.lastName)}</h2>
                            <div class="meta">
                                ${esc(student.rollNumber)} · ${esc(student.classLevel)}${student.section ? ' · Sec ' + esc(student.section) : ''}
                                · ${esc(student.gender || 'gender not set')}${yearsOld ? ` · ${yearsOld} years` : ''}
                            </div>
                        </div>
                        <div style="margin-left:auto" class="chip-row">
                            ${statusBadge(student.status)}
                            ${canAdmin ? `<input id="photo-file" type="file" accept="image/png,image/jpeg,image/webp" hidden>
                                <button class="btn btn-ghost btn-sm" id="photo-pick">Change photo</button>
                                <button class="btn btn-ghost btn-sm" id="photo-remove"${student.photoUrl ? '' : ' disabled'}>Remove photo</button>
                                <button class="btn btn-ghost btn-sm" id="edit">Edit profile</button>` : ''}
                        </div>
                    </div>
                </div>
            </section>

            <section class="grid profile">
                <div class="card">
                    <div class="card-head"><h2>Contact &amp; guardian</h2>${asSelf ? '<span class="hint">visible to you only</span>' : ''}</div>
                    <div class="card-body">
                        <dl class="facts">
                            <dt>Email</dt><dd>${esc(student.email)}</dd>
                            <dt>Phone</dt><dd>${esc(student.phone || '-')}</dd>
                            <dt>Address</dt><dd>${esc(student.addressLine || '-')}${student.city ? ', ' + esc(student.city) : ''}</dd>
                            <dt>Guardian</dt><dd>${esc(student.guardianName || '-')}</dd>
                            <dt>Guardian phone</dt><dd>${esc(student.guardianPhone || '-')}</dd>
                            <dt>Guardian email</dt><dd>${esc(student.guardianEmail || '-')}</dd>
                        </dl>
                    </div>
                </div>

                <div class="card">
                    <div class="card-head"><h2>Academic record</h2></div>
                    <div class="card-body">
                        <dl class="facts">
                            <dt>Roll number</dt><dd>${esc(student.rollNumber)}</dd>
                            <dt>Class</dt><dd>${esc(student.classLevel)}${student.section ? ' · ' + esc(student.section) : ''}</dd>
                            <dt>Date of birth</dt><dd>${formatDate(student.dateOfBirth)}</dd>
                            <dt>Admitted</dt><dd>${formatDate(student.admissionDate)}</dd>
                            <dt>Subjects</dt><dd>${subjects.length}</dd>
                            <dt>Sessions / week</dt><dd>${schedule.length}</dd>
                        </dl>
                    </div>
                </div>
            </section>

            <section class="card">
                <div class="card-head">
                    <h2>Weekly class schedule</h2>
                    <span class="hint">${schedule.length} session(s) · ${esc(schedule[0] ? schedule[0].term : 'no term set')}</span>
                </div>
                <div class="card-body">
                    <div class="week">
                        ${days.map(day => {
        const slots = schedule.filter(row => row.dayOfWeek === day);
        return `<div class="day-col${slots.length ? '' : ' empty-day'}">
                                    <h3>${esc(dayName(day))}</h3>
                                    <div class="slots">
                                        ${slots.map(row => `
                                            <div class="slot">
                                                <div class="time">${formatTime(row.startTime)}-${formatTime(row.endTime)}</div>
                                                <div class="subj">${esc(row.subjectName)}</div>
                                                <div class="who">${esc(row.teacherName)}</div>
                                                <div class="room">${esc(row.room || 'room not set')}</div>
                                            </div>`).join('') || '<div>Free</div>'}
                                    </div>
                                </div>`;
    }).join('')}
                    </div>
                </div>
            </section>

            <section class="grid two">
                <div class="card">
                    <div class="card-head">
                        <h2>Enrolled subjects</h2>
                        ${canAdmin ? '<button class="btn btn-ghost btn-sm" id="enroll">+ Add subject</button>' : ''}
                    </div>
                    <div class="card-body tight">
                        <table>
                            <thead><tr><th>Subject</th><th>Faculty</th><th class="num">Credits</th>${canAdmin ? '<th></th>' : ''}</tr></thead>
                            <tbody>
                                ${subjects.map(s => `<tr data-subject="${s.subjectId}">
                                    <td><div class="cell-strong">${esc(s.name)}</div><div class="cell-sub">${esc(s.code)} · ${esc(s.department)}</div></td>
                                    <td>${esc(s.teacherNames || 'Not assigned')}</td>
                                    <td class="num">${esc(s.credits)}</td>
                                    ${canAdmin ? `<td class="num"><button class="btn btn-danger btn-sm" data-drop="${s.subjectId}" data-name="${esc(s.name)}">Drop</button></td>` : ''}
                                </tr>`).join('') || `<tr><td colspan="4">${Portal.emptyState('No subjects enrolled yet', '')}</td></tr>`}
                            </tbody>
                        </table>
                    </div>
                </div>

                <div class="card">
                    <div class="card-head"><h2>Classmates</h2><span class="hint">${classmates.length} in ${esc(student.classLevel)}${student.section ? ' ' + esc(student.section) : ''}</span></div>
                    <div class="card-body tight">
                        <table>
                            <thead><tr><th>Student</th><th>Roll</th><th>Guardian</th></tr></thead>
                            <tbody>
                                ${classmates.map(c => `<tr${canAdmin ? ` class="clickable" data-open="${c.id}"` : ''}>
                                    <td>
                                        <div class="person">
                                            ${avatar(c.photoUrl, `${c.firstName} ${c.lastName}`, 'avatar-xs')}
                                            <div>
                                                <div class="cell-strong">${esc(c.firstName)} ${esc(c.lastName)}</div>
                                                <div class="cell-sub">${esc(c.email)}</div>
                                            </div>
                                        </div>
                                    </td>
                                    <td>${esc(c.rollNumber)}</td>
                                    <td><div class="cell-strong">${esc(c.guardianName || '-')}</div><div class="cell-sub">${esc(c.guardianPhone || '')}</div></td>
                                </tr>`).join('') || `<tr><td colspan="3">${Portal.emptyState('Nobody else in this class', '')}</td></tr>`}
                            </tbody>
                        </table>
                    </div>
                </div>
            </section>`;

        const edit = document.getElementById('edit');
        if (edit) edit.addEventListener('click', () => openEdit(student));

        if (canAdmin) bindPhoto(student);

        const enroll = document.getElementById('enroll');
        if (enroll) enroll.addEventListener('click', () => openEnroll(student));

        content.querySelectorAll('[data-drop]').forEach(button => button.addEventListener('click', async () => {
            try {
                await del(`api/students/${student.id}/subjects/${button.dataset.drop}`);
                toast(`Dropped ${button.dataset.name}`);
                await reload();
            } catch (error) {
                toast(error.message, 'err');
            }
        }));

        content.querySelectorAll('[data-open]').forEach(row => row.addEventListener('click', async () => {
            window.location.href = `student-profile.html?id=${row.dataset.open}`;
        }));
    }

    async function reload() {
        content.innerHTML = '<div class="skeleton"><span class="spinner"></span> Reloading…</div>';
        await load();
    }

    /** Admins manage the picture on the student's sign-in; the student can also change it themselves. */
    function bindPhoto(student) {
        const file = document.getElementById('photo-file');
        document.getElementById('photo-pick').addEventListener('click', () => file.click());
        file.addEventListener('change', async () => {
            if (!file.files[0]) return;
            const form = new FormData();
            form.append('file', file.files[0]);
            try {
                await upload(`api/students/${student.id}/photo`, form);
                toast('Profile picture updated');
                await reload();
            } catch (error) {
                toast(error.message, 'err');
            }
        });
        document.getElementById('photo-remove').addEventListener('click', async () => {
            const ok = await confirmDialog({
                title: 'Remove this picture?',
                message: 'Their initials will show in its place until another picture is uploaded.',
                confirmLabel: 'Remove',
            });
            if (!ok) return;
            try {
                await del(`api/students/${student.id}/photo`);
                toast('Profile picture removed');
                await reload();
            } catch (error) {
                toast(error.message, 'err');
            }
        });
    }

    async function openEdit(student) {
        const fields = [
            { name: 'rollNumber', label: 'Roll number', required: true },
            { name: 'classLevel', label: 'Class level', required: true },
            { name: 'firstName', label: 'First name', required: true },
            { name: 'lastName', label: 'Last name', required: true },
            { name: 'section', label: 'Section' },
            { name: 'gender', label: 'Gender', type: 'select', options: [{ value: 'MALE', label: 'Male' }, { value: 'FEMALE', label: 'Female' }, { value: 'OTHER', label: 'Other' }] },
            { name: 'email', label: 'Email', type: 'email', required: true },
            { name: 'phone', label: 'Phone' },
            { name: 'dateOfBirth', label: 'Date of birth', type: 'date' },
            { name: 'admissionDate', label: 'Admission date', type: 'date' },
            { name: 'status', label: 'Status', type: 'select', options: STATUSES },
            { name: 'city', label: 'City' },
            { name: 'guardianName', label: 'Guardian name' },
            { name: 'guardianPhone', label: 'Guardian phone' },
            { name: 'guardianEmail', label: 'Guardian email', type: 'email' },
            { name: 'addressLine', label: 'Address', type: 'textarea', span: 'full', rows: 2 },
        ];
        await recordForm({
            title: `Edit ${student.firstName} ${student.lastName}`,
            subtitle: `Roll ${student.rollNumber}`,
            fields,
            values: student,
            submitLabel: 'Save changes',
            save: async (values) => {
                await put(`api/students/${student.id}`, { id: student.id, ...values });
                toast('Profile updated');
                await reload();
            },
        });
    }

    async function openEnroll(student) {
        const subjects = await get('api/subjects');
        const enrolled = new Set(await get(`api/students/${student.id}/subjects`).map(s => s.subjectId));
        const options = subjects
            .filter(s => !enrolled.has(s.id))
            .map(s => ({ value: String(s.id), label: `${s.code} · ${s.name}` }));

        if (!options.length) {
            toast('Every subject in the catalog is already enrolled.');
            return;
        }

        await recordForm({
            title: 'Enroll in a subject',
            subtitle: `Adding a subject for ${student.firstName} ${student.lastName}.`,
            fields: [{ name: 'subjectId', label: 'Subject', type: 'select', required: true, options, span: 'full' }],
            submitLabel: 'Enroll',
            save: async (values) => {
                await post(`api/students/${student.id}/subjects/${values.subjectId}`, {});
                toast('Subject added');
                await reload();
            },
        });
    }

    load().catch(error => {
        content.innerHTML = `<div class="banner err">${esc(error.message)}</div>`;
    });
})();
