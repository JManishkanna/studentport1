(() => {
    const { esc, get, initials, statusBadge, formatDate, formatTime, dayName, queryParam, toast } = Portal;
    const content = document.getElementById('content');

    function groupBy(items, keyFn) {
        const map = new Map();
        for (const item of items) {
            const key = keyFn(item);
            if (!map.has(key)) map.set(key, []);
            map.get(key).push(item);
        }
        return map;
    }

    /** Flattens a grouped schedule into { key, sessions, related } table rows. */
    function summarise(map, relatedFn) {
        return [...map.entries()].map(([key, rows]) => ({
            key,
            sessions: rows.length,
            related: [...new Set(rows.map(relatedFn))].join(', '),
        }));
    }

    function summaryTable(rows, emptyLabel) {
        if (!rows.length) return `<tr><td colspan="3">${Portal.emptyState(emptyLabel, '')}</td></tr>`;
        return rows.map(row => `
            <tr>
                <td class="cell-strong">${esc(row.key)}</td>
                <td class="num">${row.sessions}</td>
                <td class="cell-sub">${esc(row.related)}</td>
            </tr>`).join('');
    }

    async function load() {
        const me = await Portal.boot('teachers');
        const asSelf = queryParam('me') === '1';

        if (!asSelf && !queryParam('id')) {
            throw new Error('Open a teacher from the directory, or use the "My profile" link.');
        }

        const teacher = await get(asSelf ? 'api/teachers/me' : `api/teachers/${queryParam('id', '')}`);
        const schedule = await get(asSelf ? 'api/teachers/me/schedule' : `api/teachers/${teacher.id}/schedule`);

        Portal.mountShell({
            active: asSelf ? 'me' : 'teachers',
            title: `${teacher.firstName} ${teacher.lastName}`,
            subtitle: `${esc(teacher.designation || 'Faculty')} · ${esc(teacher.department)} · joined ${formatDate(teacher.joiningDate)}`,
            actions: `<a class="btn btn-ghost" href="${asSelf ? 'index.html' : 'teachers.html'}">${asSelf ? 'Dashboard' : 'Back to directory'}</a>`,
        });

        const days = [1, 2, 3, 4, 5, 6];
        const bySubject = summarise(groupBy(schedule, row => row.subjectName), row => `${row.classLevel} ${row.section}`);
        const byClass = summarise(groupBy(schedule, row => `${row.classLevel} ${row.section}`), row => row.subjectName);

        content.innerHTML = `
            <section class="card">
                <div class="card-body">
                    <div class="person">
                        <div class="avatar-lg">${esc(initials(`${teacher.firstName} ${teacher.lastName}`))}</div>
                        <div>
                            <h2>${esc(teacher.firstName)} ${esc(teacher.lastName)}</h2>
                            <div class="meta">${esc(teacher.employeeCode)} · ${esc(teacher.designation || 'Faculty')} · ${esc(teacher.department)}</div>
                        </div>
                        <div style="margin-left:auto" class="chip-row">${statusBadge(teacher.status)}</div>
                    </div>
                </div>
            </section>

            <section class="grid profile">
                <div class="card">
                    <div class="card-head"><h2>Contact information</h2></div>
                    <div class="card-body">
                        <dl class="facts">
                            <dt>Email</dt><dd style="word-break:break-all">${esc(teacher.email)}</dd>
                            <dt>Phone</dt><dd>${esc(teacher.phone || '—')}</dd>
                            <dt>Office</dt><dd>${esc(teacher.officeRoom || '—')}</dd>
                            <dt>Department</dt><dd>${esc(teacher.department)}</dd>
                        </dl>
                    </div>
                </div>
                <div class="card">
                    <div class="card-head"><h2>Profile</h2></div>
                    <div class="card-body">
                        <dl class="facts">
                            <dt>Qualification</dt><dd>${esc(teacher.qualification || '—')}</dd>
                            <dt>Specialization</dt><dd>${esc(teacher.specialization || '—')}</dd>
                            <dt>Joining date</dt><dd>${formatDate(teacher.joiningDate)}</dd>
                            <dt>Sessions / week</dt><dd>${schedule.length}</dd>
                        </dl>
                    </div>
                </div>
            </section>

            <section class="card">
                <div class="card-head">
                    <h2>Weekly timetable</h2>
                    <span class="hint">${schedule.length} session(s)${schedule[0] ? ' · ' + esc(schedule[0].term) : ''}</span>
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
                                                <div class="time">${formatTime(row.startTime)}–${formatTime(row.endTime)}</div>
                                                <div class="subj">${esc(row.subjectName)}</div>
                                                <div class="who">${esc(row.classLevel)} · Sec ${esc(row.section)}</div>
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
                    <div class="card-head"><h2>Subjects taught</h2><span class="hint">Session count per subject</span></div>
                    <div class="card-body tight">
                        <table>
                            <thead><tr><th>Subject</th><th class="num">Sessions</th><th>Classes</th></tr></thead>
                            <tbody>${summaryTable(bySubject, 'No classes assigned yet')}</tbody>
                        </table>
                    </div>
                </div>
                <div class="card">
                    <div class="card-head"><h2>Classes assigned</h2><span class="hint">Session count per class</span></div>
                    <div class="card-body tight">
                        <table>
                            <thead><tr><th>Class</th><th class="num">Sessions</th><th>Subjects</th></tr></thead>
                            <tbody>${summaryTable(byClass, 'No classes assigned yet')}</tbody>
                        </table>
                    </div>
                </div>
            </section>`;
    }

    load().catch(error => {
        content.innerHTML = `<div class="banner err">${esc(error.message)}</div>`;
        toast(error.message, 'err');
    });
})();
