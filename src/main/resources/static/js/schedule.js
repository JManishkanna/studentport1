(() => {
    const { esc, get, post, put, del, formatTime, dayName, days, recordForm, confirmDialog, toast, queryParam } = Portal;
    const content = document.getElementById('content');

    const WEEK = [1, 2, 3, 4, 5, 6];
    const state = {
        rows: [],
        levels: [],
        teachers: [],
        subjects: [],
        classLevel: queryParam('classLevel', ''),
        section: queryParam('section', ''),
        teacherId: queryParam('teacherId', ''),
        subjectId: queryParam('subjectId', ''),
        readOnly: false,
    };

    async function load() {
        const me = await Portal.boot('schedule');

        if (me.role === 'STUDENT') {
            const profile = await get('api/students/me');
            state.classLevel = profile.classLevel;
            state.section = profile.section || '';
            state.rows = await get(`api/schedules/class?classLevel=${encodeURIComponent(profile.classLevel)}&section=${encodeURIComponent(profile.section || '')}`);
            state.readOnly = true;
            Portal.mountShell({
                active: 'schedule',
                title: 'My weekly timetable',
                subtitle: `${esc(profile.classLevel)}${profile.section ? ' · Section ' + esc(profile.section) : ''} · your class only`,
                actions: '<a class="btn btn-ghost" href="student-profile.html?me=1">My profile</a>',
            });
            renderGrid();
            return;
        }

        const [levels, teachers, subjects] = await Promise.all([
            get('api/students/class-levels'),
            get('api/teachers'),
            get('api/subjects'),
        ]);
        state.levels = levels;
        state.teachers = teachers;
        state.subjects = subjects;

        if (me.teacherId) state.teacherId = state.teacherId || String(me.teacherId);

        Portal.mountShell({
            active: 'schedule',
            title: me.role === 'ADMIN' ? 'Timetable builder' : 'Class timetable',
            subtitle: 'Weekly schedule across the academy',
            actions: me.role === 'ADMIN'
                ? '<button class="btn btn-primary" id="plan">Plan a week</button>'
                    + '<button class="btn btn-ghost" id="add">+ Single session</button>'
                : '',
        });

        renderFilters();
        await refresh();

        if (me.role === 'ADMIN') {
            document.getElementById('add').addEventListener('click', () => openForm(null));
            document.getElementById('plan').addEventListener('click', () => openPlanner());
        }
    }

    async function refresh() {
        const params = new URLSearchParams();
        if (state.classLevel) params.set('classLevel', state.classLevel);
        if (state.section) params.set('section', state.section);
        if (state.teacherId) params.set('teacherId', state.teacherId);
        if (state.subjectId) params.set('subjectId', state.subjectId);
        state.rows = await get(`api/schedules?${params.toString()}`);
        renderGrid();
    }

    function renderFilters() {
        const bar = document.createElement('section');
        bar.className = 'card';
        bar.innerHTML = `
            <div class="filters">
                <div class="field">
                    <label for="fl">Class level</label>
                    <select id="fl">
                        <option value="">All classes</option>
                        ${state.levels.map(l => `<option value="${esc(l)}"${l === state.classLevel ? ' selected' : ''}>${esc(l)}</option>`).join('')}
                    </select>
                </div>
                <div class="field">
                    <label for="fs">Section</label>
                    <input id="fs" type="text" placeholder="A" value="${esc(state.section)}" style="min-width:90px">
                </div>
                <div class="field">
                    <label for="ft">Teacher</label>
                    <select id="ft">
                        <option value="">Any teacher</option>
                        ${state.teachers.map(t => `<option value="${t.id}"${String(t.id) === state.teacherId ? ' selected' : ''}>${esc(t.firstName)} ${esc(t.lastName)}</option>`).join('')}
                    </select>
                </div>
                <div class="field">
                    <label for="fsub">Subject</label>
                    <select id="fsub">
                        <option value="">Any subject</option>
                        ${state.subjects.map(s => `<option value="${s.id}"${String(s.id) === state.subjectId ? ' selected' : ''}>${esc(s.code)} · ${esc(s.name)}</option>`).join('')}
                    </select>
                </div>
                <button class="btn btn-ghost" id="reset">Clear</button>
            </div>`;
        content.appendChild(bar);

        bar.querySelector('#fl').addEventListener('change', (e) => { state.classLevel = e.target.value; refresh(); });
        bar.querySelector('#ft').addEventListener('change', (e) => { state.teacherId = e.target.value; refresh(); });
        bar.querySelector('#fsub').addEventListener('change', (e) => { state.subjectId = e.target.value; refresh(); });

        let timer;
        bar.querySelector('#fs').addEventListener('input', (e) => {
            state.section = e.target.value;
            clearTimeout(timer);
            timer = setTimeout(refresh, 260);
        });
        bar.querySelector('#reset').addEventListener('click', () => {
            state.classLevel = ''; state.section = ''; state.teacherId = ''; state.subjectId = '';
            bar.querySelector('#fl').value = '';
            bar.querySelector('#fs').value = '';
            bar.querySelector('#ft').value = '';
            bar.querySelector('#fsub').value = '';
            refresh();
        });
    }

    function renderGrid() {
        const existing = content.querySelector('#grid');
        const card = existing || document.createElement('section');
        card.className = 'card';
        card.id = 'grid';

        const totalMinutes = state.rows.reduce((sum, r) => {
            const [sh, sm] = r.startTime.split(':').map(Number);
            const [eh, em] = r.endTime.split(':').map(Number);
            return sum + ((eh * 60 + em) - (sh * 60 + sm));
        }, 0);

        const admin = Portal.isAdmin();
        card.innerHTML = `
            <div class="card-head">
                <h2>Week at a glance</h2>
                <span class="hint">${state.rows.length} session(s) · ${(totalMinutes / 60).toFixed(1)} teaching hours${state.readOnly ? ' · read only' : ''}</span>
            </div>
            <div class="card-body">
                <div class="week">
                    ${WEEK.map(day => {
        const slots = state.rows.filter(row => row.dayOfWeek === day);
        return `<div class="day-col${slots.length ? '' : ' empty-day'}">
                                <h3>${esc(dayName(day))}
                                    ${admin ? `<button class="day-add" data-add-day="${day}" title="Add a session on ${esc(dayName(day))}" aria-label="Add a session on ${esc(dayName(day))}">+</button>` : ''}
                                </h3>
                                <div class="slots">
                                    ${slots.map(row => `
                                        <div class="slot" ${admin ? `data-edit="${row.id}" style="cursor:pointer"` : ''}>
                                            <div class="time">${formatTime(row.startTime)}-${formatTime(row.endTime)}</div>
                                            <div class="subj">${esc(row.subjectName)} <span class="badge">${esc(row.subjectCode)}</span></div>
                                            <div class="who">${esc(row.teacherName)}</div>
                                            <div class="room">${esc(row.classLevel)} · Sec ${esc(row.section)} · ${esc(row.room || 'room not set')}</div>
                                            ${admin ? `<button class="btn btn-danger btn-sm" data-remove="${row.id}" style="margin-top:8px">Remove</button>` : ''}
                                        </div>`).join('')
                        || (admin
                            ? `<button class="slot-add" data-add-day="${day}">+ Add a session</button>`
                            : '<div>Free</div>')}
                                </div>
                            </div>`;
    }).join('')}
                </div>
                ${state.rows.length ? '' : `<p class="cell-sub" style="margin-top:14px">No sessions match the current filters${state.readOnly ? ' for your class' : ''}. Use <strong>Plan a week</strong> to book the whole week in one go.</p>`}
            </div>`;

        if (!existing) content.appendChild(card);

        if (admin) {
            card.querySelectorAll('[data-edit]').forEach(slot => slot.addEventListener('click', (event) => {
                if (event.target.closest('[data-remove]')) return;
                openForm(state.rows.find(row => String(row.id) === slot.dataset.edit));
            }));
            card.querySelectorAll('[data-remove]').forEach(button => button.addEventListener('click', (event) => {
                event.stopPropagation();
                removeRow(state.rows.find(row => String(row.id) === button.dataset.remove));
            }));
            card.querySelectorAll('[data-add-day]').forEach(button => button.addEventListener('click', () => {
                openForm(null, quickAdd(Number(button.dataset.addDay)));
            }));
        }
    }

    /** What a one-click add on that day should already know: the day, the next free time, the usual length. */
    function quickAdd(day) {
        const slots = state.rows.filter(row => row.dayOfWeek === day);
        if (!slots.length) return { dayOfWeek: String(day), startTime: '08:00', endTime: '08:45' };

        const ends = slots.map(row => row.endTime).sort();
        const last = ends[ends.length - 1];
        const [hours, minutes] = last.split(':').map(Number);
        const start = Math.ceil((hours * 60 + minutes + 5) / 5) * 5;
        const usual = slots[slots.length - 1];
        const [sh, sm] = usual.startTime.split(':').map(Number);
        const [eh, em] = usual.endTime.split(':').map(Number);
        const length = Math.max(15, (eh * 60 + em) - (sh * 60 + sm));
        const end = start + length;
        const clock = (m) => `${String(Math.floor(m / 60) % 24).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;

        return {
            dayOfWeek: String(day),
            startTime: clock(start),
            endTime: clock(end),
            classLevel: usual.classLevel,
            section: usual.section,
            room: usual.room || '',
        };
    }

    function fields() {
        return [
            { name: 'classLevel', label: 'Class level', required: true, placeholder: 'Full Stack Java' },
            { name: 'section', label: 'Section', required: true, placeholder: 'A' },
            {
                name: 'subjectId',
                label: 'Subject',
                type: 'select',
                required: true,
                options: state.subjects.map(s => ({ value: String(s.id), label: `${s.code} · ${s.name}` })),
            },
            {
                name: 'teacherId',
                label: 'Teacher',
                type: 'select',
                required: true,
                options: state.teachers.map(t => ({ value: String(t.id), label: `${t.firstName} ${t.lastName} (${t.department})` })),
            },
            {
                name: 'dayOfWeek',
                label: 'Day',
                type: 'select',
                required: true,
                options: WEEK.map(d => ({ value: String(d), label: days[d] })),
            },
            { name: 'startTime', label: 'Start time', type: 'time', required: true },
            { name: 'endTime', label: 'End time', type: 'time', required: true },
            { name: 'room', label: 'Room', placeholder: 'Block A - 201' },
            { name: 'term', label: 'Term', placeholder: '2026-ODD' },
        ];
    }

    async function openForm(row, preset = {}) {
        await recordForm({
            title: row ? 'Edit class session' : 'Add a class session',
            subtitle: row
                ? `${row.classLevel} ${row.section} · ${row.subjectName}`
                : 'The portal rejects clashes for the same class or the same teacher.',
            fields: fields(),
            values: row || {
                dayOfWeek: '1', startTime: '08:00', endTime: '08:45', term: '2026-ODD',
                section: state.section || 'A', classLevel: state.classLevel || '', ...preset,
            },
            submitLabel: row ? 'Save session' : 'Add session',
            save: async (values) => {
                const payload = {
                    ...values,
                    subjectId: Number(values.subjectId),
                    teacherId: Number(values.teacherId),
                    dayOfWeek: Number(values.dayOfWeek),
                };
                if (row) {
                    await put(`api/schedules/${row.id}`, { id: row.id, ...payload });
                    toast('Session updated');
                } else {
                    await post('api/schedules', payload);
                    toast('Session added to the timetable');
                }
                await refresh();
            },
        });
    }

    /* ------------------------------------------------------------- week planner */

    const subjectName = (id) => {
        const subject = state.subjects.find(s => String(s.id) === String(id));
        return subject ? `${subject.code} ${subject.name}` : '';
    };
    const teacherName = (id) => {
        const teacher = state.teachers.find(t => String(t.id) === String(id));
        return teacher ? `${teacher.firstName} ${teacher.lastName}` : '';
    };
    const toMinutes = (clock) => {
        const [h, m] = (clock || '08:00').split(':').map(Number);
        return h * 60 + m;
    };
    const toClock = (minutes) => `${String(Math.floor(minutes / 60) % 24).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;
    const clamp = (value, low, high) => Math.min(high, Math.max(low, value));

    /** One entry per subject and faculty already on this class's timetable, so re-planning starts filled. */
    function existingPeriods(classLevel, section) {
        const seen = new Set();
        return state.rows
            .filter(row => row.classLevel === classLevel && (!section || row.section === section))
            .sort((a, b) => a.dayOfWeek - b.dayOfWeek || a.startTime.localeCompare(b.startTime))
            .filter(row => {
                const key = `${row.subjectId}|${row.teacherId}`;
                if (seen.has(key)) return false;
                seen.add(key);
                return true;
            })
            .slice(0, 16)
            .map(row => ({ subjectId: String(row.subjectId), teacherId: String(row.teacherId), chosen: true }));
    }

    /** Whoever already teaches this subject to this class, or to any class, is the obvious pick. */
    function suggestTeacher(subjectId, classLevel) {
        const matches = state.rows.filter(row => String(row.subjectId) === String(subjectId));
        const forClass = matches.find(row => row.classLevel === classLevel);
        const teacherId = (forClass || matches[0] || {}).teacherId;
        return teacherId ? String(teacherId) : '';
    }

    async function openPlanner() {
        const startLevel = state.classLevel || (state.levels[0] || '');
        const periods = existingPeriods(startLevel, state.section);
        if (!periods.length) periods.push({ subjectId: '', teacherId: '' });
        const known = state.rows.find(row => row.classLevel === startLevel
            && (!state.section || row.section === state.section));

        const backdrop = document.createElement('div');
        backdrop.className = 'modal-backdrop';
        backdrop.innerHTML = `
            <div class="modal wide" role="dialog" aria-modal="true" aria-label="Plan a week">
                <header>
                    <h3>Plan a week</h3>
                    <p>Set the clock once, list the periods in order and tick the days. The portal lays every session out and books the week in one go.</p>
                </header>
                <form novalidate>
                    <div class="modal-grid">
                        <div class="field">
                            <label for="p-level">Class level *</label>
                            ${state.levels.length
        ? `<select id="p-level"><option value="">Choose a class</option>${state.levels.map(l => `<option value="${esc(l)}"${l === startLevel ? ' selected' : ''}>${esc(l)}</option>`).join('')}</select>`
        : `<input id="p-level" type="text" value="${esc(startLevel)}" placeholder="Full Stack Java">`}
                        </div>
                        <div class="field"><label for="p-section">Section *</label><input id="p-section" type="text" value="${esc(state.section || 'A')}"></div>
                        <div class="field"><label for="p-room">Room</label><input id="p-room" type="text" placeholder="Block A - 201" value="${esc(known && known.room ? known.room : '')}"></div>
                        <div class="field"><label for="p-term">Term</label><input id="p-term" type="text" value="2026-ODD"></div>
                    </div>

                    <div class="plan-days">
                        <span class="plan-label">Meets on</span>
                        ${WEEK.map(d => `<label class="day-pick"><input type="checkbox" value="${d}"${d <= 5 ? ' checked' : ''}> ${esc(days[d])}</label>`).join('')}
                    </div>

                    <div class="modal-grid plan-clock">
                        <div class="field"><label for="p-first">First period starts</label><input id="p-first" type="time" value="08:00"></div>
                        <div class="field"><label for="p-length">Period length (min)</label><input id="p-length" type="number" min="10" max="240" step="5" value="45"></div>
                        <div class="field"><label for="p-break">Break between periods (min)</label><input id="p-break" type="number" min="0" max="180" step="5" value="10"></div>
                        <div class="field"><label for="p-perday">Periods a day</label><input id="p-perday" type="number" min="1" max="8" value="2"></div>
                    </div>

                    <div class="plan-head">
                        <span class="plan-label">Periods, in order</span>
                        <button type="button" class="btn btn-ghost btn-sm" id="reuse">Use this class's subjects</button>
                        <button type="button" class="btn btn-ghost btn-sm" id="add-period">+ Add period</button>
                    </div>
                    <div id="period-rows"></div>

                    <div class="plan-preview" id="preview"></div>
                    <p class="form-error"></p>
                    <div class="modal-actions">
                        <button type="button" class="btn-ghost" data-close>Cancel</button>
                        <button type="submit" class="btn-primary">Book the week</button>
                    </div>
                </form>
            </div>`;
        document.body.appendChild(backdrop);

        const form = backdrop.querySelector('form');
        const errorBox = backdrop.querySelector('.form-error');
        const rowsHost = backdrop.querySelector('#period-rows');
        const close = () => { backdrop.remove(); document.removeEventListener('keydown', onKey); };
        const onKey = (event) => { if (event.key === 'Escape') close(); };
        backdrop.addEventListener('click', (event) => { if (event.target === backdrop) close(); });
        backdrop.querySelector('[data-close]').addEventListener('click', close);
        document.addEventListener('keydown', onKey);

        function drawPeriods() {
            rowsHost.innerHTML = periods.map((period, index) => `
                <div class="period-row" data-index="${index}">
                    <span class="period-no">${index + 1}</span>
                    <select data-p="subject" aria-label="Subject for period ${index + 1}">
                        <option value="">Choose a subject</option>
                        ${state.subjects.map(s => `<option value="${s.id}"${String(s.id) === period.subjectId ? ' selected' : ''}>${esc(s.code)} · ${esc(s.name)}</option>`).join('')}
                    </select>
                    <select data-p="teacher" aria-label="Faculty for period ${index + 1}">
                        <option value="">Choose faculty</option>
                        ${state.teachers.map(t => `<option value="${t.id}"${String(t.id) === period.teacherId ? ' selected' : ''}>${esc(t.firstName)} ${esc(t.lastName)}</option>`).join('')}
                    </select>
                    <button type="button" class="btn btn-ghost btn-sm" data-drop>Remove</button>
                </div>`).join('');

            rowsHost.querySelectorAll('.period-row').forEach((row) => {
                const index = Number(row.dataset.index);
                row.querySelector('[data-p=subject]').addEventListener('change', (event) => {
                    periods[index].subjectId = event.target.value;
                    if (!periods[index].chosen) {
                        periods[index].teacherId = suggestTeacher(event.target.value, form.elements['p-level'].value);
                        periods[index].chosen = Boolean(periods[index].teacherId);
                        drawPeriods();
                    }
                    draw();
                });
                row.querySelector('[data-p=teacher]').addEventListener('change', (event) => {
                    periods[index].teacherId = event.target.value;
                    draw();
                });
                row.querySelector('[data-drop]').addEventListener('click', () => {
                    periods.splice(index, 1);
                    if (!periods.length) periods.push({ subjectId: '', teacherId: '' });
                    drawPeriods();
                    draw();
                });
            });
        }

        /** The week exactly as the portal will build it, so the plan is seen before it is saved. */
        function layout() {
            const chosen = WEEK.filter(d => backdrop.querySelector(`.plan-days input[value="${d}"]`).checked);
            const filled = periods.filter(p => p.subjectId && p.teacherId);
            const perDay = clamp(Number(form.elements['p-perday'].value) || 1, 1, 8);
            const length = clamp(Number(form.elements['p-length'].value) || 45, 10, 240);
            const gap = clamp(Number(form.elements['p-break'].value) || 0, 0, 180);
            const slots = [];
            let next = 0;
            for (const day of chosen) {
                for (let period = 0; period < perDay && next < filled.length; period++) {
                    const start = toMinutes(form.elements['p-first'].value) + period * (length + gap);
                    slots.push({ day, start, end: start + length, entry: filled[next++] });
                }
                if (next >= filled.length) break;
            }
            return { slots, leftOver: filled.length - next, chosen, filled };
        }

        function draw() {
            const plan = layout();
            const lines = plan.slots.map(slot => `
                <div class="preview-line">
                    <span class="pv-day">${esc(dayName(slot.day))}</span>
                    <span class="pv-time">${formatTime(toClock(slot.start))} - ${formatTime(toClock(slot.end))}</span>
                    <span class="pv-sub">${esc(subjectName(slot.entry.subjectId))}</span>
                    <span class="pv-who">${esc(teacherName(slot.entry.teacherId))}</span>
                </div>`).join('');
            const hidden = plan.slots.length > 12
                ? `<p class="cell-sub">+ ${plan.slots.length - 12} more sessions after these.</p>` : '';
            const empty = plan.filled.length === 0
                ? '<p class="cell-sub">Pick a subject and faculty for the first period to see the week.</p>' : '';
            const spills = plan.leftOver > 0
                ? `<p class="warn-line">${plan.leftOver} period(s) do not fit in ${plan.chosen.length} day(s) at ${Number(form.elements['p-perday'].value) || 1} a day - tick another day or allow more periods a day.</p>` : '';
            const missing = periods.filter(p => !p.subjectId || !p.teacherId).length;
            const short = missing > 0 && plan.filled.length > 0
                ? `<p class="cell-sub">${missing} period row(s) still need a subject and faculty, and will not be booked.</p>` : '';

            backdrop.querySelector('#preview').innerHTML = `${empty}${lines}${hidden}${spills}${short}`;
            backdrop.querySelector('[type=submit]').textContent =
                plan.slots.length ? `Book ${plan.slots.length} session(s)` : 'Book the week';
        }

        backdrop.querySelector('#add-period').addEventListener('click', () => {
            periods.push({ subjectId: '', teacherId: '' });
            drawPeriods();
            draw();
        });
        backdrop.querySelector('#reuse').addEventListener('click', () => {
            const reuse = existingPeriods(form.elements['p-level'].value, form.elements['p-section'].value.trim().toUpperCase());
            if (!reuse.length) {
                errorBox.textContent = 'This class has no sessions on the timetable yet, so there is nothing to reuse.';
                return;
            }
            periods.length = 0;
            periods.push(...reuse);
            errorBox.textContent = '';
            drawPeriods();
            draw();
        });
        ['p-first', 'p-length', 'p-break', 'p-perday'].forEach(id =>
            backdrop.querySelector('#' + id).addEventListener('input', draw));
        backdrop.querySelectorAll('.plan-days input').forEach(input => input.addEventListener('change', draw));

        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            const classLevel = form.elements['p-level'].value;
            const section = form.elements['p-section'].value.trim();
            const plan = layout();

            if (!classLevel) { errorBox.textContent = 'Choose the class this week belongs to.'; return; }
            if (!section) { errorBox.textContent = 'Give the section, for example A.'; return; }
            if (!plan.chosen.length) { errorBox.textContent = 'Tick at least one weekday.'; return; }
            if (!plan.filled.length) { errorBox.textContent = 'Add at least one period with a subject and faculty.'; return; }
            if (plan.leftOver) { errorBox.textContent = 'The week is full - tick another day or allow more periods a day.'; return; }

            const button = form.querySelector('[type=submit]');
            button.disabled = true;
            button.innerHTML = '<span class="spinner"></span> Booking';
            errorBox.textContent = '';
            try {
                const created = await post('api/schedules/week', {
                    classLevel,
                    section,
                    room: form.elements['p-room'].value.trim() || null,
                    term: form.elements['p-term'].value.trim() || null,
                    days: plan.chosen,
                    firstStart: form.elements['p-first'].value,
                    periodMinutes: Number(form.elements['p-length'].value),
                    breakMinutes: Number(form.elements['p-break'].value),
                    periodsPerDay: Number(form.elements['p-perday'].value),
                    periods: plan.filled.map(p => ({ subjectId: Number(p.subjectId), teacherId: Number(p.teacherId) })),
                });
                close();
                toast(`${created.length} session(s) booked for ${classLevel} ${section}`);
                await refresh();
            } catch (error) {
                errorBox.textContent = error.message || 'The week could not be booked.';
                button.disabled = false;
                draw();
            }
        });

        drawPeriods();
        draw();
    }

    async function removeRow(row) {
        const ok = await confirmDialog({
            title: 'Delete this session?',
            message: `${row.classLevel} ${row.section} · ${row.subjectName} on ${dayName(row.dayOfWeek)} at ${formatTime(row.startTime)}.`,
        });
        if (!ok) return;
        try {
            await del(`api/schedules/${row.id}`);
            toast('Session removed');
            await refresh();
        } catch (error) {
            toast(error.message, 'err');
        }
    }

    load().catch(error => { content.innerHTML = `<div class="banner err">${esc(error.message)}</div>`; });
})();
