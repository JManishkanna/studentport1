(async () => {
    const { esc, get, formatDate, formatTime, dayName, statusBadge, slotCard, initials } = Portal;
    const content = document.getElementById('content');

    const isoDay = new Date().getDay();
    const today = isoDay === 0 ? 7 : isoDay;

    function fail(error) {
        content.innerHTML = `<div class="banner err">${esc(error.message)}</div>`;
    }

    try {
        const me = await Portal.boot('dashboard');
        const stats = await get('api/dashboard');

        if (me.role === 'STUDENT') {
            await renderStudentView(me, today);
        } else {
            await renderStaffView(me, stats, today);
        }
    } catch (error) {
        fail(error);
    }

    /* ------------------------------------------------ student home */
    async function renderStudentView(me, today) {
        const [profile, schedule, subjects] = await Promise.all([
            get('api/students/me'),
            get('api/students/me/schedule'),
            get('api/students/me/subjects'),
        ]);

        Portal.mountShell({
            active: 'dashboard',
            title: `Hello, ${esc(profile.firstName)}`,
            subtitle: `${new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}`,
            actions: `<a class="btn btn-primary" href="student-profile.html?me=1">Open my profile</a>`,
        });

        const mine = schedule.filter(row => row.dayOfWeek === today);
        const next = mine[0];

        content.innerHTML = `
            <section class="grid stats">
                <div class="card stat accent"><span class="value">${esc(profile.classLevel)} ${esc(profile.section || '-')}</span><span class="label">My class</span></div>
                <div class="card stat teal"><span class="value">${subjects.length}</span><span class="label">Subjects enrolled</span></div>
                <div class="card stat"><span class="value">${mine.length}</span><span class="label">Classes today (${esc(dayName(today))})</span></div>
                <div class="card stat amber"><span class="value">${next ? `${formatTime(next.startTime)}` : '-'}</span><span class="label">First period</span></div>
            </section>

            <section class="grid profile">
                <div class="card">
                    <div class="card-head"><h2>Today's classes</h2><span class="hint">${esc(dayName(today))}</span></div>
                    <div class="card-body">
                        ${mine.length
                            ? `<div class="week" style="grid-template-columns:1fr"><div class="day-col"><div class="slots">${mine.map(row => slotCard(row, { showClass: false })).join('')}</div></div></div>`
                            : Portal.emptyState('Nothing scheduled for your class today.', 'Check the timetable for the rest of the week.')}
                    </div>
                </div>

                <div class="card">
                    <div class="card-head"><h2>My subjects</h2></div>
                    <div class="card-body tight">
                        <table>
                            <thead><tr><th>Subject</th><th>Faculty</th><th class="num">Credits</th></tr></thead>
                            <tbody>
                                ${subjects.map(s => `<tr>
                                    <td><div class="cell-strong">${esc(s.name)}</div><div class="cell-sub">${esc(s.code)} · ${esc(s.department)}</div></td>
                                    <td>${esc(s.teacherNames || 'Not assigned yet')}</td>
                                    <td class="num">${esc(s.credits)}</td>
                                </tr>`).join('') || `<tr><td colspan="3">${Portal.emptyState('No subjects enrolled', '')}</td></tr>`}
                            </tbody>
                        </table>
                    </div>
                </div>
            </section>

            <section class="grid two">
                <div class="card">
                    <div class="card-head"><h2>Guardian contact</h2></div>
                    <div class="card-body">
                        <dl class="facts">
                            <dt>Guardian</dt><dd>${esc(profile.guardianName || '-')}</dd>
                            <dt>Phone</dt><dd>${esc(profile.guardianPhone || '-')}</dd>
                            <dt>Email</dt><dd>${esc(profile.guardianEmail || '-')}</dd>
                            <dt>Status</dt><dd>${statusBadge(profile.status)}</dd>
                        </dl>
                    </div>
                </div>
                <div class="card">
                    <div class="card-head"><h2>Shortcuts</h2></div>
                    <div class="card-body">
                        <div class="chip-row">
                            <a class="btn btn-ghost" href="schedule.html">Weekly timetable</a>
                            <a class="btn btn-ghost" href="student-profile.html?me=1">Full profile</a>
                            <a class="btn btn-ghost" href="teachers.html">Faculty directory</a>
                            <a class="btn btn-ghost" href="contacts.html">Office contacts</a>
                        </div>
                        <p class="cell-sub" style="margin-top:16px">
                            Signed in as ${esc(me.fullName)} · <span class="badge role-STUDENT">Student</span> · roll ${esc(profile.rollNumber)}.
                            Admitted on ${formatDate(profile.admissionDate)}.
                        </p>
                    </div>
                </div>
            </section>`;
    }

    /* ------------------------------------------------ admin / teacher home */
    async function renderStaffView(me, stats, today) {
        let mine = [];
        if (me.role === 'TEACHER' && me.teacherId) {
            mine = (await get(`api/teachers/${me.teacherId}/schedule`)).filter(row => row.dayOfWeek === today);
        }

        const actions = me.role === 'ADMIN'
            ? `<a class="btn btn-ghost" href="contacts.html">Contacts</a>
               <a class="btn btn-ghost" href="schedule.html">Timetable</a>
               <a class="btn btn-primary" href="students.html">Manage students</a>`
            : `<a class="btn btn-primary" href="students.html">Student directory</a>`;

        Portal.mountShell({
            active: 'dashboard',
            title: me.role === 'ADMIN' ? 'Academy at a glance' : 'Faculty overview',
            subtitle: new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }),
            actions,
        });

        content.innerHTML = `
            <section class="grid stats">
                <div class="card stat accent"><span class="value">${stats.totalStudents}</span><span class="label">Students on roll</span></div>
                <div class="card stat teal"><span class="value">${stats.activeStudents}</span><span class="label">Active students</span></div>
                <div class="card stat"><span class="value">${stats.totalTeachers}</span><span class="label">Faculty members</span></div>
                <div class="card stat"><span class="value">${stats.totalSubjects}</span><span class="label">Subjects offered</span></div>
                <div class="card stat amber"><span class="value">${stats.sessionsToday}</span><span class="label">Classes today</span></div>
            </section>

            ${me.role === 'TEACHER' ? `
            <section class="card">
                <div class="card-head"><h2>My classes today</h2><span class="hint">${mine.length} session(s)</span></div>
                <div class="card-body">
                    ${mine.length
                        ? `<div class="week" style="grid-template-columns:1fr"><div class="day-col"><div class="slots">${mine.map(row => slotCard(row)).join('')}</div></div></div>`
                        : Portal.emptyState('No sessions on your timetable today.', '')}
                </div>
            </section>` : ''}

            <section class="grid two">
                <div class="card">
                    <div class="card-head"><h2>Sessions scheduled today</h2><span class="hint">${esc(dayName(today))}</span></div>
                    <div class="card-body tight">
                        <table>
                            <thead><tr><th>Time</th><th>Class</th><th>Subject</th><th>Faculty</th><th>Room</th></tr></thead>
                            <tbody>
                                ${stats.todaySessions.length ? stats.todaySessions.map(row => `<tr>
                                    <td class="num" style="text-align:left;font-variant-numeric:tabular-nums">${formatTime(row.startTime)}-${formatTime(row.endTime)}</td>
                                    <td><span class="badge">${esc(row.classLevel)} · ${esc(row.section)}</span></td>
                                    <td><div class="cell-strong">${esc(row.subjectName)}</div><div class="cell-sub">${esc(row.subjectCode)}</div></td>
                                    <td>${esc(row.teacherName)}</td>
                                    <td>${esc(row.room || '-')}</td>
                                </tr>`).join('') : `<tr><td colspan="5">${Portal.emptyState('A quiet day. No classes are scheduled.', 'Sunday or an unconfigured day.')}</td></tr>`}
                            </tbody>
                        </table>
                    </div>
                </div>

                <div class="card">
                    <div class="card-head"><h2>Class levels on roll</h2></div>
                    <div class="card-body">
                        <div class="chip-row">
                            ${stats.classLevels.map(level => `<a class="btn btn-ghost btn-sm" href="students.html?classLevel=${encodeURIComponent(level)}">${esc(level)}</a>`).join('') || '<p class="cell-sub">No students admitted yet.</p>'}
                        </div>
                        <p class="cell-sub" style="margin-top:18px">
                            Signed in as ${esc(me.fullName)} · <span class="badge role-${esc(me.role)}">${esc(Portal.label(me.role))}</span>
                        </p>
                    </div>
                </div>
            </section>`;
    }
})();
