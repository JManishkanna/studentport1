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

        const [schedule, subjects, classmates, progress, comments] = await Promise.all([
            get(scoped.schedule), get(scoped.subjects), get(scoped.classmates),
            quietProgress(asSelf, student.id), quietComments(asSelf, student.id),
        ]);

        const byId = !asSelf;
        Portal.mountShell({
            active: asSelf ? 'me' : 'students',
            title: `${student.firstName} ${student.lastName}`,
            subtitle: `${esc(student.rollNumber)} · ${esc(student.classLevel)}${student.section ? ' · Section ' + esc(student.section) : ''} · admitted ${formatDate(student.admissionDate)}`,
            actions: `<a class="btn btn-ghost" href="${byId ? 'students.html' : 'index.html'}">${byId ? 'Back to roster' : 'Dashboard'}</a>`,
        });

        render(student, schedule, subjects, classmates, me, asSelf, progress, comments);
    }

    /** Markers and notes belong to the class teachers, so a faculty member outside this division sees nothing rather than an error. */
    async function quietProgress(asSelf, studentId) {
        try {
            return await get(asSelf ? 'api/progress/me' : `api/students/${studentId}/progress`);
        } catch (error) {
            if (error.status === 403) return null;
            throw error;
        }
    }

    async function quietComments(asSelf, studentId) {
        try {
            return await get(asSelf ? 'api/students/me/comments' : `api/students/${studentId}/comments`);
        } catch (error) {
            if (error.status === 403) return null;
            throw error;
        }
    }

    function render(student, schedule, subjects, classmates, me, asSelf, progress, comments) {
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
            </section>

            ${gradesCard(progress, asSelf, student)}
            ${notesCard(comments, student, me, asSelf, subjects)}`;

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

        const addNote = document.getElementById('add-note');
        if (addNote) addNote.addEventListener('click', () => openNote(student, subjects));

        content.querySelectorAll('[data-del-note]').forEach(button => button.addEventListener('click', async () => {
            const ok = await confirmDialog({
                title: 'Delete this note?',
                message: 'It disappears from the student record and from the student’s own view.',
                confirmLabel: 'Delete',
            });
            if (!ok) return;
            try {
                await del(`api/comments/${button.dataset.delNote}`);
                toast('Note removed');
                await reload();
            } catch (error) {
                toast(error.message, 'err');
            }
        }));
    }

    /** Marks per subject, weighted by the marks each assessment is out of. */
    function gradesCard(progress, asSelf, student) {
        const link = asSelf ? '<a class="btn btn-ghost btn-sm" href="progress.html">Full progress</a>' : '';
        if (!progress) {
            return `
                <section class="card">
                    <div class="card-head"><h2>Grades by subject</h2>${link}</div>
                    <div class="card-body">
                        ${Portal.emptyState('Marks are not visible to you', `Only ${esc(student.firstName)} and the faculty who teach this class can see the marks.`)}
                    </div>
                </section>`;
        }
        const rows = progress.subjects.map(subject => `
            <tr>
                <td>
                    <div class="cell-strong">${esc(subject.subjectName)}</div>
                    <div class="cell-sub">${esc(subject.subjectCode)} · ${esc(subject.teacherName || 'unassigned')}</div>
                </td>
                <td class="num">
                    <div class="cell-strong">${score(subject.percent)}</div>
                    <div class="cell-sub">${subject.graded} marked${subject.pending ? `, ${subject.pending} due` : ''}</div>
                </td>
                <td class="num">
                    ${subject.change === null || subject.change === undefined
                ? '<span class="cell-sub">first score</span>'
                : `<span class="delta ${subject.change > 0 ? 'up' : subject.change < 0 ? 'down' : 'flat'}">${subject.change > 0 ? '+' : ''}${Number(subject.change).toFixed(1)}</span>`}
                </td>
                <td>${Portal.sparkline(subject.points.filter(point => point.percent !== null).map(point => point.percent), { aria: `Score trend for ${subject.subjectName}` })}</td>
                <td class="num">
                    ${subject.attendanceTotal
                ? `<span class="delta ${subject.lowAttendance ? 'down' : 'flat'}">${Number(subject.attendanceRate).toFixed(1)}%</span>`
                : '<span class="cell-sub">no marks</span>'}
                </td>
            </tr>`).join('');
        return `
            <section class="card">
                <div class="card-head">
                    <h2>Grades by subject</h2>
                    <span class="hint">${progress.overallPercent === null
                ? 'nothing graded yet'
                : `weighted ${Number(progress.overallPercent).toFixed(1)}% overall`}</span>
                    ${link}
                </div>
                <div class="card-body tight">
                    <table>
                        <thead><tr><th>Subject</th><th class="num">Weighted</th><th class="num">Last change</th><th>Trend</th><th class="num">Attendance</th></tr></thead>
                        <tbody>${rows || `<tr><td colspan="5">${Portal.emptyState('No subjects on this record yet', '')}</td></tr>`}</tbody>
                    </table>
                    <p class="chart-note-line">Weighted means each assessment counts for the share of marks it is out of. Attendance covers the last 30 days; under ${Number(progress.threshold).toFixed(0)}% is flagged.</p>
                </div>
            </section>`;
    }

    function score(value) {
        return value === null || value === undefined ? '—' : `${Number(value).toFixed(1)}%`;
    }

    /** Notes left by faculty. The student reads their own, admins and the class teachers read and write them. */
    function notesCard(comments, student, me, asSelf, subjects) {
        const canWrite = !asSelf && (me.role === 'ADMIN' || me.role === 'TEACHER');
        const button = canWrite ? '<button class="btn btn-ghost btn-sm" id="add-note">+ Add note</button>' : '';
        if (!comments) {
            return `
                <section class="card">
                    <div class="card-head"><h2>Teacher notes</h2>${button}</div>
                    <div class="card-body">
                        ${Portal.emptyState('Notes are not visible to you', `Only ${esc(student.firstName)} and the faculty who teach this class can read them.`)}
                    </div>
                </section>`;
        }
        return `
            <section class="card">
                <div class="card-head">
                    <h2>Teacher notes</h2>
                    <span class="hint">${comments.length} note(s)</span>
                    ${button}
                </div>
                <div class="card-body">
                    <div class="notes">
                        ${comments.map(note => `
                            <article class="note">
                                <div class="note-head">
                                    <span class="note-who">${esc(note.authorName)}</span>
                                    <span class="badge role-${esc(note.authorRole)}">${esc(Portal.label(note.authorRole))}</span>
                                    ${note.subjectName ? `<span class="badge">${esc(note.subjectCode || '')} ${esc(note.subjectName)}</span>` : '<span class="badge">General</span>'}
                                    <span class="note-meta">${formatDate(note.createdAt)}</span>
                                    ${note.canDelete ? `<button class="btn btn-danger btn-sm" data-del-note="${note.id}">Delete</button>` : ''}
                                </div>
                                <p class="note-body">${esc(note.body)}</p>
                            </article>`).join('') || Portal.emptyState('No notes on this record', canWrite ? 'Use Add note to leave feedback the student and the office can read.' : '')}
                    </div>
                </div>
            </section>`;
    }

    async function openNote(student, subjects) {
        await recordForm({
            title: `Note for ${student.firstName} ${student.lastName}`,
            subtitle: 'The student and the office see this on the profile.',
            fields: [
                {
                    name: 'subjectId',
                    label: 'Subject',
                    type: 'select',
                    emptyLabel: 'General (not about one subject)',
                    options: subjects.map(s => ({ value: String(s.subjectId), label: `${s.code} · ${s.name}` })),
                },
                { name: 'body', label: 'Note', type: 'textarea', required: true, rows: 4, span: 'full' },
            ],
            submitLabel: 'Save note',
            save: async (values) => {
                await post(`api/students/${student.id}/comments`, {
                    subjectId: values.subjectId ? Number(values.subjectId) : null,
                    body: values.body,
                });
                toast('Note added');
                await reload();
            },
        });
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
