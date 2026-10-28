(() => {
    const { esc, get, post, avatar, formatDate, formatTime, toast } = Portal;
    const content = document.getElementById('content');
    const STATUSES = ['PRESENT', 'LATE', 'ABSENT'];
    const DAYS_SHOWN = 10;

    const state = {
        me: null,
        date: isoToday(),
        from: isoDaysAgo(29),
        to: isoToday(),
        classLevel: '',
        section: '',
        classOptions: [],
        sectionOptions: [],
        sessions: [],
        summary: null,
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
        const [summary, sessions] = await Promise.all([
            get(`api/attendance/summary?${summaryQuery()}`),
            get(`api/attendance/sessions?${sessionQuery()}`),
        ]);
        state.summary = summary;
        state.sessions = sessions;

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

            <section class="card" id="sessions"></section>`;

        bindFilters();
        renderSessions();
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
        if (!rows || !rows.length) {
            return Portal.emptyState('No attendance recorded in this range', 'Take attendance for any class and the chart fills in.');
        }
        const W = 660;
        const rowH = 46;
        const top = 8;
        const left = 170;
        const right = 74;
        const barH = 16;
        const track = W - left - right;
        const H = top + rows.length * rowH;
        const body = rows.map((row, index) => {
            const y = top + index * rowH;
            const pct = Math.max(0, Math.min(100, Number(row.rate) || 0));
            const width = pct === 0 ? 0 : Math.max(3, track * pct / 100);
            const tone = pct >= 90 ? '' : pct >= 75 ? ' mid' : ' low';
            const label = row.section ? `${row.classLevel} · ${row.section}` : row.classLevel;
            return `<g>
                <text class="chart-label" x="0" y="${y + 18}">${esc(label)}</text>
                <text class="chart-note" x="0" y="${y + 32}">${row.total} mark(s)</text>
                <rect class="chart-track" x="${left}" y="${y + 8}" width="${track}" height="${barH}" rx="4"></rect>
                <rect class="chart-bar${tone}" x="${left}" y="${y + 8}" width="${width}" height="${barH}" rx="4">
                    <title>${esc(label)}: ${pct.toFixed(1)}%</title>
                </rect>
                <text class="chart-value" x="${W}" y="${y + 21}" text-anchor="end">${pct.toFixed(1)}%</text>
            </g>`;
        }).join('');
        return `<svg class="chart" viewBox="0 0 ${W} ${H}" role="img" aria-label="Attendance rate by class">${body}</svg>`;
    }

    function dayChart(rows, labelFor = row => Number(row.rate || 0).toFixed(1)) {
        const shown = (rows || []).slice(-DAYS_SHOWN);
        if (!shown.length) {
            return Portal.emptyState('No daily marks yet', 'Each day with attendance appears as a bar.');
        }
        const W = 660;
        const H = 250;
        const left = 38;
        const right = 12;
        const top = 16;
        const bottom = 42;
        const plotW = W - left - right;
        const plotH = H - top - bottom;
        const step = plotW / shown.length;
        const barW = Math.min(46, step * 0.56);
        const grid = [0, 25, 50, 75, 100].map(pct => {
            const y = top + plotH - plotH * pct / 100;
            return `<line class="chart-grid" x1="${left}" x2="${W - right}" y1="${y}" y2="${y}"></line>
                <text class="chart-value" x="${left - 6}" y="${y + 3}" text-anchor="end">${pct}%</text>`;
        }).join('');
        const bars = shown.map((row, index) => {
            const pct = Math.max(0, Math.min(100, Number(row.rate) || 0));
            const height = Math.max(2, plotH * pct / 100);
            const x = left + step * index + (step - barW) / 2;
            const y = top + plotH - height;
            const tone = pct >= 90 ? '' : pct >= 75 ? ' mid' : ' low';
            return `<rect class="chart-bar${tone}" x="${x}" y="${y}" width="${barW}" height="${height}" rx="4">
                    <title>${esc(shortDate(row.date))}: ${pct.toFixed(1)}%</title>
                </rect>
                <text class="chart-value" x="${x + barW / 2}" y="${H - bottom + 16}" text-anchor="middle">${esc(shortDate(row.date))}</text>
                <text class="chart-note" x="${x + barW / 2}" y="${H - bottom + 30}" text-anchor="middle">${esc(labelFor(row))}</text>`;
        }).join('');
        return `<svg class="chart" viewBox="0 0 ${W} ${H}" role="img" aria-label="Daily attendance rate">
            ${grid}
            <line class="chart-axis" x1="${left}" x2="${W - right}" y1="${top + plotH}" y2="${top + plotH}"></line>
            ${bars}
        </svg>`;
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
            const [summary, sessions] = await Promise.all([
                get(`api/attendance/summary?${summaryQuery()}`),
                get(`api/attendance/sessions?${sessionQuery()}`),
            ]);
            state.summary = summary;
            state.sessions = sessions;
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

    load().catch(error => { content.innerHTML = `<div class="banner err">${esc(error.message)}</div>`; });
})();
