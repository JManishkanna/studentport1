(() => {
    const {
        esc, get, post, put, del, boot, formatDate, formatTime, dayName, shortDate,
        kindBadge, trendLine, recordForm, confirmDialog, toast, emptyState, avatar,
    } = Portal;
    const content = document.getElementById('content');

    const KINDS = [
        { value: 'ASSIGNMENT', label: 'Assignment' },
        { value: 'QUIZ', label: 'Quiz' },
        { value: 'EXAM', label: 'Exam' },
    ];
    const DEADLINE_DAYS = 14;

    const state = {
        me: null,
        assessments: [],
        classes: [],
        filters: { classLevel: '', section: '', subjectId: '' },
        upcoming: null,
    };

    function isoToday() {
        const date = new Date();
        return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
    }

    function isoIn(days) {
        const date = new Date();
        date.setDate(date.getDate() + days);
        return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
    }

    function percent(value) {
        return value === null || value === undefined ? '—' : `${Number(value).toFixed(1)}%`;
    }

    function classLabel(row) {
        return `${row.classLevel}${row.section ? ' · ' + row.section : ''}`;
    }

    async function load() {
        const me = await boot('grades');
        state.me = me;
        if (me.role === 'STUDENT') {
            await loadStudent();
        } else {
            await loadStaff();
        }
    }

    /* ---------------------------------------------------------------- student */

    async function loadStudent() {
        const [progress, upcoming] = await Promise.all([
            get('api/progress/me'),
            get(`api/upcoming?days=${DEADLINE_DAYS}`),
        ]);
        state.upcoming = upcoming;

        Portal.mountShell({
            active: 'grades',
            title: 'My grades',
            subtitle: `${esc(progress.classLevel)}${progress.section ? ' · Section ' + esc(progress.section) : ''} · ${progress.subjects.length} subject(s)`,
            actions: '<a class="btn btn-ghost" href="attendance.html">Attendance</a>',
        });

        const open = progress.subjects.reduce((total, row) => total + row.pending, 0);
        const marked = progress.subjects.reduce((total, row) => total + row.graded, 0);

        content.innerHTML = `
            <section class="grid stats">
                <div class="card stat accent"><span class="value">${percent(progress.overallPercent)}</span><span class="label">Weighted score</span></div>
                <div class="card stat"><span class="value">${marked}</span><span class="label">Marks recorded</span></div>
                <div class="card stat amber"><span class="value">${open}</span><span class="label">Still to come</span></div>
                <div class="card stat teal"><span class="value">${percent(progress.attendanceRate)}</span><span class="label">Attendance, 30 days</span></div>
            </section>

            ${progress.lowAttendance
            ? `<div class="banner warn">Attendance is ${percent(progress.attendanceRate)} over the last 30 days, under the ${Number(progress.threshold).toFixed(0)}% the academy watches. Missed classes are listed on the <a href="attendance.html">attendance page</a>.</div>`
            : ''}

            ${progress.subjects.length === 0
            ? `<section class="card"><div class="card-body">${emptyState('No subjects on your record yet', 'Once the office enrols you, marks appear here.')}</div></section>`
            : progress.subjects.map(subjectCard).join('')}

            <section class="grid two">${upcomingCards(upcoming)}</section>`;
    }

    function subjectCard(subject) {
        const points = [...subject.points].reverse();
        const scored = subject.points.filter(point => point.percent !== null && point.percent !== undefined);
        const latest = scored.length ? scored[scored.length - 1] : null;
        const change = subject.change === null || subject.change === undefined
            ? '<span class="delta flat">needs two marks</span>'
            : `<span class="delta ${subject.change > 0 ? 'up' : subject.change < 0 ? 'down' : 'flat'}">${subject.change > 0 ? '+' : ''}${subject.change.toFixed(1)}</span>`;

        return `
            <section class="card" id="subject-${subject.subjectId}">
                <div class="card-head">
                    <h2>${esc(subject.subjectName)}</h2>
                    <span class="hint">${esc(subject.subjectCode)} · ${esc(subject.teacherName || 'faculty not assigned')}</span>
                </div>
                <div class="card-body">
                    <dl class="facts">
                        <dt>Weighted</dt><dd>${percent(subject.percent)} over ${subject.graded} mark(s)</dd>
                        <dt>Latest</dt><dd>${latest ? `${percent(latest.percent)} · ${esc(latest.title)}` : 'not marked yet'}</dd>
                        <dt>Trend</dt><dd>${change} since the previous mark</dd>
                        <dt>Attendance</dt><dd>${percent(subject.attendanceRate)} over ${subject.attendanceTotal} session(s)</dd>
                        <dt>Due next</dt><dd>${subject.pending}</dd>
                    </dl>
                    ${subject.lowAttendance
                ? `<p class="warn-line">Attendance in this subject is ${percent(subject.attendanceRate)}, under the 75% the academy watches.</p>`
                : ''}
                    ${trendLine(subject.points, {
                aria: `Score trend for ${subject.subjectName}`,
                emptyTitle: 'No marks recorded for this subject yet',
                emptyHint: 'The line fills in once your faculty enters marks.',
            })}
                    <p class="chart-note-line">The dashed line is 75%. Points join assessments in the order they fell due.</p>
                </div>
                <div class="card-body tight">
                    <table>
                        <thead><tr><th>Assessment</th><th>Type</th><th>Due</th><th class="num">Marks</th><th class="num">%</th><th>Remark</th></tr></thead>
                        <tbody>
                            ${points.map(point => `
                                <tr>
                                    <td><div class="cell-strong">${esc(point.title)}</div><div class="cell-sub">out of ${point.maxMarks}</div></td>
                                    <td>${kindBadge(point.kind)}</td>
                                    <td>${esc(formatDate(point.dueDate))}</td>
                                    <td class="num">${point.marks === null || point.marks === undefined ? '<span class="cell-sub">not marked</span>' : esc(point.marks)}</td>
                                    <td class="num">${point.percent === null || point.percent === undefined ? '—' : `${Number(point.percent).toFixed(1)}%`}</td>
                                    <td><span class="cell-sub">${esc(point.remark || '')}</span></td>
                                </tr>`).join('') || `<tr><td colspan="6">${emptyState('Nothing set for this subject yet', '')}</td></tr>`}
                        </tbody>
                    </table>
                </div>
            </section>`;
    }

    /* ---------------------------------------------------------------- staff */

    async function loadStaff() {
        const assessments = await get('api/assessments');
        state.assessments = assessments;
        state.classes = await classRows();
        state.upcoming = await get(`api/upcoming?days=${DEADLINE_DAYS}`);

        const isAdmin = state.me.role === 'ADMIN';
        Portal.mountShell({
            active: 'grades',
            title: isAdmin ? 'Coursework and marks' : 'My subjects',
            subtitle: isAdmin
                ? 'Set assignments, quizzes and exams for any class, then enter the marks'
                : 'Set work for the classes on your timetable and enter the marks',
            actions: `<button class="btn btn-primary" id="new-assessment">New assessment</button>`,
        });

        renderStaff();
        document.getElementById('new-assessment').addEventListener('click', () => openForm(null));
    }

    /** The timetable rows the caller may set work for, with subject names on each row. */
    async function classRows() {
        if (state.me.role === 'TEACHER') {
            return get('api/teachers/me/schedule');
        }
        const [sessions, subjects] = await Promise.all([get('api/schedules'), get('api/subjects')]);
        const names = new Map(subjects.map(subject => [subject.id, subject]));
        return sessions.map(session => {
            const subject = names.get(session.subjectId) || {};
            return {
                subjectId: session.subjectId,
                subjectCode: subject.code,
                subjectName: subject.name,
                classLevel: session.classLevel,
                section: session.section,
            };
        });
    }

    function scopeOptions() {
        const seen = new Map();
        state.classes.forEach(row => {
            const key = `${row.subjectId}|${row.classLevel}|${row.section || ''}`;
            if (!seen.has(key)) {
                seen.set(key, `${classLabel(row)} · ${row.subjectCode || ''} ${row.subjectName || ''}`.trim());
            }
        });
        return [...seen.entries()].map(([value, label]) => ({ value, label }))
            .sort((a, b) => a.label.localeCompare(b.label));
    }

    function filtered() {
        const { classLevel, section, subjectId } = state.filters;
        return state.assessments
            .filter(row => !classLevel || row.classLevel === classLevel)
            .filter(row => !section || row.section === section)
            .filter(row => !subjectId || String(row.subjectId) === subjectId)
            .slice()
            .sort((a, b) => b.dueDate.localeCompare(a.dueDate) || b.id - a.id);
    }

    function renderStaff() {
        const rows = filtered();
        const awaiting = state.assessments.filter(row => row.rosterCount > 0 && row.gradedCount < row.rosterCount);
        const complete = state.assessments.filter(row => row.rosterCount > 0 && row.gradedCount >= row.rosterCount);
        const today = isoToday();
        const soon = state.assessments.filter(row => row.dueDate >= today && row.dueDate <= isoIn(DEADLINE_DAYS));
        const levels = [...new Set(state.assessments.map(row => row.classLevel))].sort();
        const sections = [...new Set(state.assessments.map(row => row.section).filter(Boolean))].sort();

        content.innerHTML = `
            <section class="grid stats">
                <div class="card stat accent"><span class="value">${state.assessments.length}</span><span class="label">Assessments set</span></div>
                <div class="card stat amber"><span class="value">${awaiting.length}</span><span class="label">Awaiting marks</span></div>
                <div class="card stat teal"><span class="value">${complete.length}</span><span class="label">Fully marked</span></div>
                <div class="card stat"><span class="value">${soon.length}</span><span class="label">Due in ${DEADLINE_DAYS} days</span></div>
            </section>

            ${state.me.role === 'TEACHER' && !state.classes.length
            ? `<section class="card"><div class="card-body">${emptyState('Nothing is on your timetable yet', 'Add sessions on the timetable page, then work can be set for them.')}</div></section>`
            : ''}

            <section class="card">
                <div class="filters">
                    <div class="field">
                        <label for="fclass">Class</label>
                        <select id="fclass">
                            <option value="">All classes</option>
                            ${levels.map(level => `<option value="${esc(level)}"${level === state.filters.classLevel ? ' selected' : ''}>${esc(level)}</option>`).join('')}
                        </select>
                    </div>
                    <div class="field">
                        <label for="fsection">Section</label>
                        <select id="fsection">
                            <option value="">All sections</option>
                            ${sections.map(section => `<option value="${esc(section)}"${section === state.filters.section ? ' selected' : ''}>Section ${esc(section)}</option>`).join('')}
                        </select>
                    </div>
                    <div class="field">
                        <label for="fsubject">Subject</label>
                        <select id="fsubject">
                            <option value="">All subjects</option>
                            ${subjectFilterOptions().map(option => `<option value="${esc(option.value)}"${option.value === state.filters.subjectId ? ' selected' : ''}>${esc(option.label)}</option>`).join('')}
                        </select>
                    </div>
                    <button class="btn btn-ghost" id="fclear">Clear</button>
                </div>
            </section>

            <section class="card">
                <div class="card-head">
                    <h2>Assessments</h2>
                    <span class="hint">${rows.length} shown${awaiting.length ? ` · ${awaiting.length} awaiting marks` : ''}</span>
                </div>
                <div class="card-body tight">
                    <table>
                        <thead>
                            <tr><th>Title</th><th>Subject</th><th>Class</th><th>Type</th><th>Due</th><th class="num">Marked</th><th class="num">Average</th><th></th></tr>
                        </thead>
                        <tbody>
                            ${rows.map(row => `
                                <tr>
                                    <td>
                                        <div class="cell-strong">${esc(row.title)}</div>
                                        ${row.description ? `<div class="cell-sub">${esc(row.description)}</div>` : ''}
                                    </td>
                                    <td><div class="cell-strong">${esc(row.subjectName)}</div><div class="cell-sub">${esc(row.subjectCode)}</div></td>
                                    <td><span class="badge">${esc(classLabel(row))}</span><div class="cell-sub">${esc(row.teacherName || '')}</div></td>
                                    <td>${kindBadge(row.kind)}</td>
                                    <td>${esc(formatDate(row.dueDate))}</td>
                                    <td class="num">
                                        <div class="cell-strong">${row.gradedCount} / ${row.rosterCount}</div>
                                        ${row.rosterCount && row.gradedCount < row.rosterCount ? '<div class="cell-sub">marks pending</div>' : ''}
                                    </td>
                                    <td class="num">${percent(row.averagePercent)}</td>
                                    <td class="num">
                                        <div class="chip-row" style="justify-content:flex-end">
                                            <button class="btn ${row.rosterCount && row.gradedCount < row.rosterCount ? 'btn-primary' : 'btn-ghost'} btn-sm" data-marks="${row.id}">
                                                ${row.gradedCount ? 'Review marks' : 'Enter marks'}
                                            </button>
                                            <button class="btn btn-ghost btn-sm" data-edit="${row.id}">Edit</button>
                                            <button class="btn btn-danger btn-sm" data-delete="${row.id}">Delete</button>
                                        </div>
                                    </td>
                                </tr>`).join('')
                            || `<tr><td colspan="8">${emptyState('No assessments yet', 'Set an assignment, quiz or exam for one of your classes.')}</td></tr>`}
                        </tbody>
                    </table>
                </div>
            </section>

            <section class="grid two">${upcomingCards(state.upcoming)}</section>`;

        document.getElementById('new-assessment').addEventListener('click', () => openForm(null));
        document.getElementById('fclear').addEventListener('click', () => {
            state.filters = { classLevel: '', section: '', subjectId: '' };
            renderStaff();
        });
        document.getElementById('fclass').addEventListener('change', event => {
            state.filters.classLevel = event.target.value;
            renderStaff();
        });
        document.getElementById('fsection').addEventListener('change', event => {
            state.filters.section = event.target.value;
            renderStaff();
        });
        document.getElementById('fsubject').addEventListener('change', event => {
            state.filters.subjectId = event.target.value;
            renderStaff();
        });
        content.querySelectorAll('[data-marks]').forEach(button =>
            button.addEventListener('click', () => openMarks(button.dataset.marks)));
        content.querySelectorAll('[data-edit]').forEach(button =>
            button.addEventListener('click', () => openForm(state.assessments.find(row => String(row.id) === button.dataset.edit))));
        content.querySelectorAll('[data-delete]').forEach(button =>
            button.addEventListener('click', () => removeAssessment(state.assessments.find(row => String(row.id) === button.dataset.delete))));
    }

    function subjectFilterOptions() {
        const seen = new Map();
        state.assessments.forEach(row => seen.set(String(row.subjectId), `${row.subjectCode} · ${row.subjectName}`));
        return [...seen.entries()].map(([value, label]) => ({ value, label })).sort((a, b) => a.label.localeCompare(b.label));
    }

    /* ---------------------------------------------------------------- forms */

    async function openForm(assessment) {
        const options = scopeOptions();
        if (!options.length) {
            toast('No class on the timetable matches your subjects yet.', 'err');
            return;
        }
        const values = assessment
            ? {
                scope: `${assessment.subjectId}|${assessment.classLevel}|${assessment.section || ''}`,
                title: assessment.title,
                kind: assessment.kind,
                dueDate: assessment.dueDate,
                maxMarks: assessment.maxMarks,
                description: assessment.description,
            }
            : { kind: 'ASSIGNMENT', maxMarks: 20 };

        const saved = await recordForm({
            title: assessment ? `Edit ${assessment.title}` : 'New assessment',
            subtitle: assessment
                ? 'Changing the due date or maximum marks does not move marks already entered.'
                : 'Work is set for one subject in one class division.',
            fields: [
                { name: 'scope', label: 'Class and subject', type: 'select', required: true, options, span: 'full' },
                { name: 'title', label: 'Title', required: true, placeholder: 'Assignment 3' },
                { name: 'kind', label: 'Type', type: 'select', required: true, options: KINDS },
                { name: 'dueDate', label: 'Due date', type: 'date', required: true },
                { name: 'maxMarks', label: 'Maximum marks', type: 'number', required: true, min: 1, max: 1000 },
                { name: 'description', label: 'What the student has to do', type: 'textarea', span: 'full', rows: 3 },
            ],
            values,
            submitLabel: assessment ? 'Save changes' : 'Create assessment',
            save: async (form) => {
                const [subjectId, classLevel, section] = form.scope.split('|');
                const body = {
                    subjectId: Number(subjectId),
                    classLevel,
                    section: section || null,
                    title: form.title,
                    kind: form.kind,
                    dueDate: form.dueDate,
                    maxMarks: Number(form.maxMarks),
                    description: form.description || null,
                };
                if (assessment) {
                    await put(`api/assessments/${assessment.id}`, body);
                    toast(`${body.title} updated`);
                } else {
                    await post('api/assessments', body);
                    toast(`${body.title} set for ${classLevel}${section ? ' ' + section : ''}`);
                }
                await reloadStaff();
            },
        });
        return saved;
    }

    async function removeAssessment(assessment) {
        const ok = await confirmDialog({
            title: `Delete ${assessment.title}?`,
            message: 'Every mark recorded against it goes too. This cannot be undone.',
            confirmLabel: 'Delete',
        });
        if (!ok) return;
        try {
            await del(`api/assessments/${assessment.id}`);
            toast(`${assessment.title} deleted`);
            await reloadStaff();
        } catch (error) {
            toast(error.message, 'err');
        }
    }

    /** The marking sheet: one row per enrolled student, blank means the work is not marked. */
    async function openMarks(id) {
        let sheet;
        try {
            sheet = await get(`api/assessments/${id}/grades`);
        } catch (error) {
            toast(error.message, 'err');
            return;
        }
        const assessment = sheet.assessment;

        const backdrop = document.createElement('div');
        backdrop.className = 'modal-backdrop';
        backdrop.innerHTML = `
            <div class="modal wide" role="dialog" aria-modal="true" aria-label="Marks for ${esc(assessment.title)}">
                <header>
                    <h3>${esc(assessment.title)}</h3>
                    <p>${kindBadge(assessment.kind)} · ${esc(assessment.subjectName)} · ${esc(classLabel(assessment))} · due ${esc(formatDate(assessment.dueDate))} · out of ${assessment.maxMarks}</p>
                </header>
                <div style="padding:16px 24px 24px">
                    <div class="sheet-tools">
                        <span class="hint">${assessment.gradedCount} of ${sheet.students.length} marked</span>
                        <span class="hint">Leave a box empty where the work was not submitted.</span>
                    </div>
                    <div class="sheet-list">
                        <div class="grade-row head"><span>Student</span><span>Marks</span><span>Remark</span></div>
                        ${sheet.students.map(student => `
                            <div class="grade-row" data-student="${student.studentId}">
                                <div class="person">
                                    ${avatar(student.photoUrl, student.fullName, 'avatar-xs')}
                                    <div>
                                        <div class="cell-strong">${esc(student.fullName)}</div>
                                        <div class="cell-sub">${esc(student.rollNumber)}</div>
                                    </div>
                                </div>
                                <input class="grade-input marks" type="number" min="0" max="${assessment.maxMarks}" step="0.5"
                                       value="${student.marks === null || student.marks === undefined ? '' : esc(student.marks)}"
                                       aria-label="Marks for ${esc(student.fullName)}">
                                <input class="grade-input grade-remark" type="text" maxlength="200"
                                       value="${esc(student.remark || '')}" aria-label="Remark for ${esc(student.fullName)}"
                                       placeholder="Optional remark">
                            </div>`).join('')
                        || `<div style="padding:16px">${emptyState('No active student is enrolled in this class', '')}</div>`}
                    </div>
                    <p class="form-error" id="grades-error"></p>
                    <div class="modal-actions">
                        <button type="button" class="btn btn-ghost" data-close>Cancel</button>
                        <button type="button" class="btn btn-primary" id="save-grades"${sheet.students.length ? '' : ' disabled'}>Save marks</button>
                    </div>
                </div>
            </div>`;
        document.body.appendChild(backdrop);

        const close = () => { backdrop.remove(); document.removeEventListener('keydown', onKey); };
        const onKey = (event) => { if (event.key === 'Escape') close(); };
        document.addEventListener('keydown', onKey);
        backdrop.addEventListener('click', (event) => { if (event.target === backdrop) close(); });
        backdrop.querySelector('[data-close]').addEventListener('click', close);

        backdrop.querySelector('#save-grades').addEventListener('click', async (event) => {
            const button = event.currentTarget;
            const errorBox = backdrop.querySelector('#grades-error');
            const entries = [...backdrop.querySelectorAll('.grade-row[data-student]')].map(row => {
                const raw = row.querySelector('.marks').value.trim();
                const remark = row.querySelector('.grade-remark').value.trim();
                return {
                    studentId: Number(row.dataset.student),
                    marks: raw === '' ? null : Number(raw),
                    remark: remark === '' ? null : remark,
                };
            });
            button.disabled = true;
            button.innerHTML = '<span class="spinner"></span> Saving';
            errorBox.textContent = '';
            try {
                await put(`api/assessments/${assessment.id}/grades`, { entries });
                close();
                toast(`Marks saved for ${assessment.title}`);
                await reloadStaff();
            } catch (error) {
                errorBox.textContent = error.message;
                button.disabled = false;
                button.textContent = 'Save marks';
            }
        });

        const first = backdrop.querySelector('.marks');
        if (first) first.focus();
    }

    async function reloadStaff() {
        state.assessments = await get('api/assessments');
        state.upcoming = await get(`api/upcoming?days=${DEADLINE_DAYS}`);
        renderStaff();
    }

    /* ---------------------------------------------------------------- shared */

    function upcomingCards(upcoming) {
        const classes = upcoming.classes.length
            ? `<ul class="up-list">${upcoming.classes.map(row => `
                <li class="up-item">
                    <span class="up-when">${esc(dayName(row.dayOfWeek))} ${esc(shortDate(row.date))}</span>
                    <div class="up-main">
                        <div class="cell-strong">${esc(row.subjectName)} <span class="badge">${esc(row.subjectCode)}</span></div>
                        <div class="cell-sub">${esc(row.classLevel)}${row.section ? ' · ' + esc(row.section) : ''} · ${esc(row.teacherName)}</div>
                    </div>
                    <span class="up-side">${formatTime(row.startTime)} - ${formatTime(row.endTime)}${row.room ? ' · ' + esc(row.room) : ''}</span>
                </li>`).join('')}</ul>`
            : emptyState('Nothing is scheduled in the next 7 days', '');

        const work = upcoming.assessments.length
            ? `<ul class="up-list">${upcoming.assessments.map(row => `
                <li class="up-item">
                    <span class="up-when">${esc(shortDate(row.dueDate))}</span>
                    <div class="up-main">
                        <div class="cell-strong">${esc(row.title)} ${kindBadge(row.kind)}</div>
                        <div class="cell-sub">${esc(row.subjectName)} · ${esc(row.classLevel)}${row.section ? ' ' + esc(row.section) : ''} · ${row.maxMarks} marks</div>
                    </div>
                    <span class="up-side">${row.daysLeft === 0 ? '<span class="up-due">today</span>'
                : row.daysLeft === 1 ? '<span class="up-due">tomorrow</span>'
                    : `in ${row.daysLeft} days`}</span>
                </li>`).join('')}</ul>`
            : emptyState(`No deadlines in the next ${DEADLINE_DAYS} days`, '');

        return `
            <div class="card">
                <div class="card-head">
                    <h2>Next classes</h2>
                    <span class="hint">${esc(formatDate(upcoming.from))} to ${esc(formatDate(upcoming.to))}</span>
                </div>
                <div class="card-body tight">${classes}</div>
            </div>
            <div class="card">
                <div class="card-head"><h2>Deadlines ahead</h2><span class="hint">${upcoming.assessments.length} in ${DEADLINE_DAYS} days</span></div>
                <div class="card-body tight">${work}</div>
            </div>`;
    }

    load().catch(error => {
        content.innerHTML = `<div class="banner err">${esc(error.message)}</div>`;
    });
})();
