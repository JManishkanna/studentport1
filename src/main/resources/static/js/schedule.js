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
                subtitle: `${esc(profile.classLevel)}${profile.section ? ' · Section ' + esc(profile.section) : ''} — your class only`,
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
            actions: me.role === 'ADMIN' ? '<button class="btn btn-primary" id="add">+ Add class session</button>' : '',
        });

        renderFilters();
        await refresh();

        if (me.role === 'ADMIN') document.getElementById('add').addEventListener('click', () => openForm(null));
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
                        ${state.subjects.map(s => `<option value="${s.id}"${String(s.id) === state.subjectId ? ' selected' : ''}>${esc(s.code)} — ${esc(s.name)}</option>`).join('')}
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
                                <h3>${esc(dayName(day))}</h3>
                                <div class="slots">
                                    ${slots.map(row => `
                                        <div class="slot" ${admin ? `data-edit="${row.id}" style="cursor:pointer"` : ''}>
                                            <div class="time">${formatTime(row.startTime)}–${formatTime(row.endTime)}</div>
                                            <div class="subj">${esc(row.subjectName)} <span class="badge">${esc(row.subjectCode)}</span></div>
                                            <div class="who">${esc(row.teacherName)}</div>
                                            <div class="room">${esc(row.classLevel)} · Sec ${esc(row.section)} · ${esc(row.room || 'room not set')}</div>
                                            ${admin ? `<button class="btn btn-danger btn-sm" data-remove="${row.id}" style="margin-top:8px">Remove</button>` : ''}
                                        </div>`).join('') || '<div>Free</div>'}
                                </div>
                            </div>`;
    }).join('')}
                </div>
                ${state.rows.length ? '' : `<p class="cell-sub" style="margin-top:14px">No sessions match the current filters${state.readOnly ? ' for your class' : ''}.</p>`}
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
        }
    }

    function fields() {
        return [
            { name: 'classLevel', label: 'Class level', required: true, placeholder: 'Grade 11' },
            { name: 'section', label: 'Section', required: true, placeholder: 'A' },
            {
                name: 'subjectId',
                label: 'Subject',
                type: 'select',
                required: true,
                options: state.subjects.map(s => ({ value: String(s.id), label: `${s.code} — ${s.name}` })),
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

    async function openForm(row) {
        await recordForm({
            title: row ? 'Edit class session' : 'Add a class session',
            subtitle: row
                ? `${row.classLevel} ${row.section} · ${row.subjectName}`
                : 'The portal rejects clashes for the same class or the same teacher.',
            fields: fields(),
            values: row || { dayOfWeek: '1', startTime: '08:00', endTime: '08:45', term: '2026-ODD', section: state.section || 'A', classLevel: state.classLevel || '' },
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
