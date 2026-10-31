(() => {
    const { esc, get, post, avatar, formatDate, formatTime, toast, queryParam } = Portal;
    const content = document.getElementById('content');
    const STATUSES = ['PRESENT', 'LATE', 'ABSENT'];
    const DAYS_SHOWN = 10;
    const LOW_RATE = 75;

    const requested = queryParam('date');
    const isIsoDate = value => /^\d{4}-\d{2}-\d{2}$/.test(value || '');

    const state = {
        me: null,
        date: isIsoDate(requested) ? requested : isoToday(),
        from: isoDaysAgo(29),
        to: isoToday(),
        classLevel: '',
        section: '',
        classOptions: [],
        sectionOptions: [],
        sessions: [],
        summary: null,
        atRisk: null,
    };

    function isoToday() {
        return localIso(new Date());
    }

    function isoDaysAgo(days) {
        const date = new Date();
        date.setDate(date.getDate() - days);
        return localIso(date);
    }

    function localIso(date) {
        const adjusted = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
        return adjusted.toISOString().slice(0, 10);
    }

    function shortDate(value) {
        if (!value) return '';
        const date = new Date(`${value}T00:00:00`);
        return Number.isNaN(date.getTime())
            ? value
            : date.toLocaleDateString('en-IN', { day: '2-digit', month: 'short' });
    }

    function attendanceBadge(status) {
        const cls = (status || '').toLowerCase();
        const text = status ? status.charAt(0) + status.slice(1).toLowerCase() : 'Not marked';
        return `<span class="badge ${esc(cls)}">${esc(text)}</span>`;
    }

    function bounds() {
        return `from=${state.from}&to=${state.to}`;
    }

    function summaryQuery() {
        const params = new URLSearchParams({ from: state.from, to: state.to });
        if (state.classLevel) params.set('classLevel', state.classLevel);
        if (state.section) params.set('section', state.section);
        return params.toString();
    }

    function sessionQuery() {
        const params = new URLSearchParams({ date: state.date });
        if (state.classLevel) params.set('classLevel', state.classLevel);
        if (state.section) params.set('section', state.section);
        return params.toString();
    }

    async function load() {
        const me = await Portal.boot('attendance');
        state.me = me;
        if (me.role === 'STUDENT') {
            await loadStudent();
        } else {
            await loadStaff();
        }
    }

    /* ---------------------------------------------------------------- faculty and admin */

    async function loadStaff() {
        const [summary, sessions, atRisk] = await Promise.all([
            get(`api/attendance/summary?${summaryQuery()}`),
            get(`api/attendance/sessions?${sessionQuery()}`),
            get(`api/attendance/at-risk?${summaryQuery()}`),
        ]);
        state.summary = summary;
        state.sessions = sessions;
        state.atRisk = atRisk;

        state.classOptions = [...new Set(summary.classes.map(row => row.classLevel))].sort();
        state.sectionOptions = [...new Set(summary.classes.map(row => row.section).filter(Boolean))].sort();

        Portal.mountShell({
            active: 'attendance',
            title: 'Attendance',
            subtitle: 'Every class on the timetable, with the rate for the last 30 days',
            actions: '<a class="btn btn-ghost" href="schedule.html">Open timetable</a>',
        });
        renderStaff();
    }

    function renderStaff() {
        const summary = state.summary || { rate: 0, present: 0, late: 0, absent: 0, total: 0, classes: [], days: [] };
        content.innerHTML = `
            <section class="card">
                <div class="filters">
                    <div class="field">
                        <label for="fdate">Date</label>
                        <input id="fdate" type="date" value="${esc(state.date)}">
                    </div>
                    <div class="field">
                        <label for="fclass">Class</label>
                        <select id="fclass">
                            <option value="">All classes</option>
                            ${state.classOptions.map(level => `<option value="${esc(level)}"${level === state.classLevel ? ' selected' : ''}>${esc(level)}</option>`).join('')}
                        </select>
                    </div>
                    <div class="field">
                        <label for="fsection">Section</label>
                        <select id="fsection">
                            <option value="">All sections</option>
                            ${state.sectionOptions.map(section => `<option value="${esc(section)}"${section === state.section ? ' selected' : ''}>Section ${esc(section)}</option>`).join('')}
                        </select>
                    </div>
                    <button class="btn btn-ghost" id="fclear">Clear</button>
                </div>
            </section>

            ${statTiles(summary)}

            <section class="grid two">
                <div class="card">
                    <div class="card-head">
                        <h2>Attendance by class</h2>
                        <span class="hint">${esc(formatDate(summary.from))} to ${esc(formatDate(summary.to))}</span>
                    </div>
                    <div class="card-body">
                        ${classChart(summary.classes)}
                        <p class="chart-note-line">A late arrival counts as attended. Bars below 75% are red, below 90% amber.</p>
                    </div>
                </div>
                <div class="card">
                    <div class="card-head">
                        <h2>Daily attendance</h2>
                        <span class="hint">Last ${DAYS_SHOWN} days with marks</span>
                    </div>
                    <div class="card-body">
                        ${dayChart(summary.days)}
                    </div>
                </div>
            </section>

            <section class="card" id="sessions"></section>

            ${atRiskCard()}`;

        bindFilters();
        renderSessions();
    }

    function atRiskCard() {
        const atRisk = state.atRisk;
        if (!atRisk || !atRisk.students.length) return '';
        return `
            <section class="card">
                <div class="card-head">
                    <h2>Students below ${Number(atRisk.threshold).toFixed(0)}%</h2>
                    <span class="hint">${atRisk.students.length} student(s) · ${esc(formatDate(atRisk.from))} to ${esc(formatDate(atRisk.to))}</span>
                </div>
                <div class="card-body tight">
                    <table>
                        <thead><tr><th>Student</th><th>Class</th><th class="num">Rate</th><th class="num">Missed</th><th class="num">To recover</th><th>Weakest subject</th></tr></thead>
                        <tbody>
                            ${atRisk.students.map(row => `
                                <tr class="clickable" data-open="${row.studentId}">
                                    <td>
                                        <div class="person">
                                            ${avatar(row.photoUrl, row.fullName, 'avatar-xs')}
                                            <div>
                                                <div class="cell-strong">${esc(row.fullName)}</div>
                                                <div class="cell-sub">${esc(row.rollNumber)}</div>
                                            </div>
                                        </div>
                                    </td>
                                    <td><span class="badge">${esc(row.classLevel)}${row.section ? ' · ' + esc(row.section) : ''}</span></td>
                                    <td class="num"><span class="delta down">${Number(row.rate).toFixed(1)}%</span></td>
                                    <td class="num">${row.absent} of ${row.total}</td>
                                    <td class="num">${row.needed} class(es)</td>
                                    <td>
                                        <div class="cell-strong">${esc(row.weakestSubject || '-')}</div>
                                        <div class="cell-sub">${row.weakestRate === null || row.weakestRate === undefined ? '' : `${Number(row.weakestRate).toFixed(1)}%`}</div>
                                    </td>
                                </tr>`).join('')}
                        </tbody>
                    </table>
                    <p class="chart-note-line">"To recover" is how many classes in a row the student must attend to reach the threshold again. Select a row to open their profile.</p>
                </div>
            </section>`;
    }

    function statTiles(summary) {
        return `
            <section class="grid stats">
                <div class="card"><div class="stat accent"><span class="value">${Number(summary.rate || 0).toFixed(1)}%</span><span class="label">Attendance rate</span></div></div>
                <div class="card"><div class="stat teal"><span class="value">${summary.present}</span><span class="label">Present</span></div></div>
                <div class="card"><div class="stat amber"><span class="value">${summary.late}</span><span class="label">Late</span></div></div>
                <div class="card"><div class="stat"><span class="value">${summary.absent}</span><span class="label">Absent</span></div></div>
            </section>`;
    }

    function classChart(rows) {
        return Portal.rateBars(rows, {
            labelFor: row => row.section ? `${row.classLevel} · ${row.section}` : row.classLevel,
            noteFor: row => `${row.total} mark(s)`,
            aria: 'Attendance rate by class',
            emptyTitle: 'No attendance recorded in this range',
            emptyHint: 'Take attendance for any class and the chart fills in.',
        });
    }

    function dayChart(rows) {
        return Portal.rateColumns(rows, {
            labelFor: row => shortDate(row.date),
            aria: 'Daily attendance rate',
            limit: DAYS_SHOWN,
            emptyTitle: 'No daily marks yet',
            emptyHint: 'Each day with attendance appears as a bar.',
        });
    }

    function bindFilters() {
        const date = document.getElementById('fdate');
        const level = document.getElementById('fclass');
        const section = document.getElementById('fsection');

        date.addEventListener('change', async () => {
            state.date = date.value || isoToday();
            await refreshSessions();
        });
        level.addEventListener('change', async () => {
            state.classLevel = level.value;
            await refreshAll();
        });
        section.addEventListener('change', async () => {
            state.section = section.value;
            await refreshAll();
        });
        document.getElementById('fclear').addEventListener('click', async () => {
            state.classLevel = '';
            state.section = '';
            date.value = state.date;
            level.value = '';
            section.value = '';
            await refreshAll();
        });
    }

    async function refreshAll() {
        try {
            const [summary, sessions, atRisk] = await Promise.all([
                get(`api/attendance/summary?${summaryQuery()}`),
                get(`api/attendance/sessions?${sessionQuery()}`),
                get(`api/attendance/at-risk?${summaryQuery()}`),
            ]);
            state.summary = summary;
            state.sessions = sessions;
            state.atRisk = atRisk;
            renderStaff();
        } catch (error) {
            toast(error.message, 'err');
        }
    }

    async function refreshSessions() {
        try {
            state.sessions = await get(`api/attendance/sessions?${sessionQuery()}`);
            renderSessions();
        } catch (error) {
            toast(error.message, 'err');
        }
    }

    function renderSessions() {
        const card = document.getElementById('sessions');
        if (!card) return;
        const sessions = state.sessions;
        card.innerHTML = `
            <div class="card-head">
                <h2>Classes on ${esc(formatDate(state.date))}</h2>
                <span class="hint">${sessions.length} session(s) · ${state.classLevel ? esc(state.classLevel) : 'all classes'}</span>
            </div>
            <div class="card-body tight">
                <table>
                    <thead>
                        <tr><th>Time</th><th>Subject</th><th>Class</th><th>Faculty</th><th>Marked</th><th></th></tr>
                    </thead>
                    <tbody>
                        ${sessions.map(session => `
                            <tr>
                                <td>
                                    <div class="cell-strong">${formatTime(session.startTime)} - ${formatTime(session.endTime)}</div>
                                    <div class="cell-sub">${esc(session.room || 'room not set')}</div>
                                </td>
                                <td>
                                    <div class="cell-strong">${esc(session.subjectName)}</div>
                                    <div class="cell-sub">${esc(session.subjectCode)}</div>
                                </td>
                                <td><span class="badge">${esc(session.classLevel)}${session.section ? ' · ' + esc(session.section) : ''}</span></td>
                                <td>${esc(session.teacherName)}</td>
                                <td>
                                    <div class="cell-strong">${session.markedCount} / ${session.studentCount}</div>
                                    <div class="cell-sub">${session.complete ? 'complete' : session.markedCount ? 'partly marked' : 'not started'}</div>
                                </td>
                                <td class="num">
                                    <button class="btn ${session.complete ? 'btn-ghost' : 'btn-primary'} btn-sm"
                                            data-mark="${session.scheduleId}"${session.studentCount ? '' : ' disabled'}>
                                        ${session.complete ? 'Review' : 'Take attendance'}
                                    </button>
                                </td>
                            </tr>`).join('') || `<tr><td colspan="6">${Portal.emptyState('No classes meet on this date', 'Pick another date, or build the timetable first.')}</td></tr>`}
                    </tbody>
                </table>
            </div>`;

        card.querySelectorAll('[data-mark]').forEach(button => {
            button.addEventListener('click', () => openMarking(button.dataset.mark));
        });
    }

    async function openMarking(scheduleId) {
        let sheet;
        try {
            sheet = await get(`api/attendance/sheet?scheduleId=${scheduleId}&date=${state.date}`);
        } catch (error) {
            toast(error.message, 'err');
            return;
        }

        const backdrop = document.createElement('div');
        backdrop.className = 'modal-backdrop';
        backdrop.innerHTML = `
            <div class="modal" style="max-width:760px" role="dialog" aria-modal="true" aria-label="Attendance for ${esc(sheet.subjectName)}">
                <header>
                    <h3>${esc(sheet.subjectName)}</h3>
                    <p>${esc(sheet.classLevel)}${sheet.section ? ' · Section ' + esc(sheet.section) : ''} · ${formatTime(sheet.startTime)} - ${formatTime(sheet.endTime)} · ${esc(formatDate(sheet.date))} · ${esc(sheet.teacherName)}</p>
                </header>
                <div style="padding:16px 24px 24px">
                    <div class="sheet-tools">
                        <span class="hint">${sheet.students.length} active student(s)</span>
                        <button type="button" class="btn btn-ghost btn-sm" id="all-present">Mark everyone present</button>
                    </div>
                    <div class="sheet-list">
                        ${sheet.students.map(student => `
                            <div class="sheet-row">
                                <div class="person">
                                    ${avatar(student.photoUrl, student.studentName, 'avatar-xs')}
                                    <div>
                                        <div class="cell-strong">${esc(student.studentName)}</div>
                                        <div class="cell-sub">${esc(student.rollNumber)}</div>
                                    </div>
                                </div>
                                <select class="status-select" data-student="${student.studentId}" aria-label="Attendance for ${esc(student.studentName)}">
                                    ${STATUSES.map(status => `<option value="${status}"${(student.status || 'PRESENT') === status ? ' selected' : ''}>${status.charAt(0) + status.slice(1).toLowerCase()}</option>`).join('')}
                                </select>
                            </div>`).join('') || Portal.emptyState('No active students in this class', '')}
                    </div>
                    <p class="form-error" id="marks-error"></p>
                    <div class="modal-actions">
                        <button type="button" class="btn btn-ghost" data-close>Cancel</button>
                        <button type="button" class="btn btn-primary" id="save-marks"${sheet.students.length ? '' : ' disabled'}>Save attendance</button>
                    </div>
                </div>
            </div>`;
        document.body.appendChild(backdrop);

        const close = () => { backdrop.remove(); document.removeEventListener('keydown', onKey); };
        const onKey = (event) => { if (event.key === 'Escape') close(); };
        document.addEventListener('keydown', onKey);
        backdrop.addEventListener('click', (event) => { if (event.target === backdrop) close(); });
        backdrop.querySelector('[data-close]').addEventListener('click', close);

        backdrop.querySelector('#all-present').addEventListener('click', () => {
            backdrop.querySelectorAll('[data-student]').forEach(select => { select.value = 'PRESENT'; });
        });

        backdrop.querySelector('#save-marks').addEventListener('click', async (event) => {
            const button = event.currentTarget;
            const errorBox = backdrop.querySelector('#marks-error');
            const entries = [...backdrop.querySelectorAll('[data-student]')].map(select => ({
                studentId: Number(select.dataset.student),
                status: select.value,
            }));
            button.disabled = true;
            button.innerHTML = '<span class="spinner"></span> Saving';
            errorBox.textContent = '';
            try {
                await post('api/attendance', { scheduleId: sheet.scheduleId, date: sheet.date, entries });
                close();
                toast(`Attendance saved for ${sheet.subjectName}`);
                await refreshAll();
            } catch (error) {
                errorBox.textContent = error.message;
                button.disabled = false;
                button.textContent = 'Save attendance';
            }
        });

        const first = backdrop.querySelector('.status-select');
        if (first) first.focus();
    }

    /* ---------------------------------------------------------------- student */

    async function loadStudent() {
        const data = await get(`api/attendance/me?${bounds()}`);
        Portal.mountShell({
            active: 'attendance',
            title: 'My attendance',
            subtitle: `${formatDate(state.from)} to ${formatDate(state.to)} · late arrivals count as attended`,
        });

        content.innerHTML = `
            ${lowAttendanceBanner(data)}
            ${statTiles(data)}
            <section class="grid two">
                <div class="card">
                    <div class="card-head"><h2>Attendance by subject</h2><span class="hint">${data.total} session(s) marked</span></div>
                    <div class="card-body">${classChart(data.subjects.map(row => ({ ...row, classLevel: row.subjectName, section: null })))}</div>
                </div>
                <div class="card">
                    <div class="card-head"><h2>Daily attendance</h2><span class="hint">Last ${DAYS_SHOWN} days</span></div>
                    <div class="card-body">${dayChart(data.days)}</div>
                </div>
            </section>

            <section class="card">
                <div class="card-head"><h2>Session history</h2><span class="hint">${data.history.length} record(s)</span></div>
                <div class="card-body tight">
                    <table>
                        <thead><tr><th>Date</th><th>Subject</th><th>Time</th><th>Status</th></tr></thead>
                        <tbody>
                            ${data.history.map(row => `
                                <tr>
                                    <td>${esc(formatDate(row.date))}</td>
                                    <td>
                                        <div class="cell-strong">${esc(row.subjectName)}</div>
                                        <div class="cell-sub">${esc(row.subjectCode || '')}</div>
                                    </td>
                                    <td>${formatTime(row.startTime)}</td>
                                    <td>${attendanceBadge(row.status)}</td>
                                </tr>`).join('') || `<tr><td colspan="4">${Portal.emptyState('No attendance recorded yet', 'Your marks appear here after each class.')}</td></tr>`}
                        </tbody>
                    </table>
                </div>
            </section>`;
    }

    /** Only students with marks get a warning; a subject with no records is never flagged. */
    function lowAttendanceBanner(data) {
        if (!data.total || Number(data.rate) >= LOW_RATE) return '';
        const weak = data.subjects.filter(row => row.total && Number(row.rate) < LOW_RATE);
        const needed = Math.max(0, Math.ceil(3 * data.total - 4 * (data.present + data.late)));
        return `
            <div class="banner warn">
                Your attendance is ${Number(data.rate).toFixed(1)}%, under the ${LOW_RATE}% the academy watches.
                ${weak.length ? `Below the line in ${weak.map(row => `${esc(row.subjectName)} (${Number(row.rate).toFixed(1)}%)`).join(', ')}.` : ''}
                Attending the next ${needed} class${needed === 1 ? '' : 'es'} in a row brings you back to ${LOW_RATE}%.
            </div>`;
    }

    load().catch(error => { content.innerHTML = `<div class="banner err">${esc(error.message)}</div>`; });

    content.addEventListener('click', event => {
        const row = event.target.closest('tr[data-open]');
        if (row) {
            window.location.href = `student-profile.html?id=${row.dataset.open}`;
        }
    });
})();
