(() => {
    const { esc, get, post, put, del, recordForm, confirmDialog, toast, queryParam } = Portal;
    const content = document.getElementById('content');

    const state = {
        q: queryParam('q', ''),
        category: queryParam('category', ''),
        kind: queryParam('kind', ''),
        group: queryParam('group', ''),
        division: queryParam('division', ''),
        sort: queryParam('sort', 'group'),
        contacts: [],
        categories: [],
        people: [],
        peopleNote: '',
        role: '',
    };

    const HEADINGS = {
        FACULTY: ['Subject faculty', 'Who teaches the classes you are in'],
        STUDENT: ['Students', 'Class division, scholar number and guardian line'],
        ADMINISTRATION: ['Administration', 'Write to the desk that runs the academy'],
        DESK: ['Office desks', 'Helplines, rooms and opening hours'],
    };
    const KIND_ORDER = ['FACULTY', 'STUDENT', 'ADMINISTRATION', 'DESK'];

    const SORTS = [
        { value: 'group', label: 'Programme, then name', key: row => [row.group || '', row.division || '', row.name] },
        { value: 'name', label: 'Name (A–Z)', key: row => [row.name] },
        { value: 'name-desc', label: 'Name (Z–A)', key: row => [row.name], desc: true },
        { value: 'code', label: 'Roll / staff number', key: row => [row.code || '', row.name] },
        { value: 'phone', label: 'Phone number', key: row => [row.phone ? 0 : 1, digits(row.phone)] },
    ];

    const FIELDS = [
        { name: 'category', label: 'Category', required: true, placeholder: 'ADMISSIONS' },
        { name: 'name', label: 'Display name', required: true, placeholder: 'Admissions Desk' },
        { name: 'phone', label: 'Phone', required: true, placeholder: '+91 80 4000 1000' },
        { name: 'alternatePhone', label: 'Alternate phone' },
        { name: 'department', label: 'Department', placeholder: 'Front Office' },
        { name: 'email', label: 'Email', type: 'email', placeholder: 'desk@academy.edu' },
        { name: 'location', label: 'Location', placeholder: 'Block A - Ground' },
        { name: 'availableHours', label: 'Available hours', placeholder: 'Mon-Fri 09:00 - 17:00', span: 'full' },
    ];

    async function load() {
        const me = await Portal.boot('contacts');
        state.role = me.role;
        state.categories = await get('api/contacts/categories');
        state.contacts = await fetchRows();
        await loadPeople();

        Portal.mountShell({
            active: 'contacts',
            title: 'Contact information',
            subtitle: baseSubtitle(),
            actions: me.role === 'ADMIN' ? '<button class="btn btn-primary" id="add">+ Add contact</button>' : '',
        });

        renderFilters();
        renderDirectory();
        renderGroups();
        updateSummary();

        if (me.role === 'ADMIN') document.getElementById('add').addEventListener('click', () => openForm(null));
    }

    function baseSubtitle() {
        if (state.role === 'ADMIN') return 'Every faculty member and student, plus the academy desks';
        if (state.role === 'TEACHER') return 'The students you teach, with their class division';
        return 'Your subject faculty and the administration mailbox';
    }

    /** The people list is scoped by the session, so a sign-in with no classes simply gets a note. */
    async function loadPeople() {
        try {
            state.people = await get('api/contacts/people');
            state.peopleNote = '';
        } catch (error) {
            state.people = [];
            state.peopleNote = error.message;
        }
    }

    function fetchRows() {
        const params = new URLSearchParams();
        if (state.category) params.set('category', state.category);
        return get(`api/contacts?${params.toString()}`);
    }

    function deskRow(contact) {
        return { ...contact, kind: 'DESK', code: '', division: '', group: pretty(contact.category) };
    }

    function peopleRows() {
        return state.people;
    }

    function deskRows() {
        return state.contacts.map(deskRow);
    }

    function digits(phone) {
        return (phone || '').replace(/\D/g, '');
    }

    function distinct(values) {
        return [...new Set(values.filter(Boolean))].sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
    }

    function groups() {
        return distinct(peopleRows().map(row => row.group));
    }

    function divisions() {
        return distinct(peopleRows().map(row => row.division));
    }

    function matches(row) {
        if (!state.q) return true;
        const needle = state.q.toLowerCase();
        return [row.name, row.code, row.group, row.division, row.detail, row.phone, row.alternatePhone,
                row.email, row.department, row.location, row.availableHours]
            .filter(Boolean).some(value => String(value).toLowerCase().includes(needle));
    }

    /** Programme and division only exist for people, so desk rows always pass them. */
    function passes(row) {
        if (state.kind && row.kind !== state.kind) return false;
        if (!matches(row)) return false;
        if (row.kind === 'DESK') return true;
        return (!state.group || row.group === state.group)
            && (!state.division || row.division === state.division);
    }

    function sortOption() {
        return SORTS.find(s => s.value === state.sort) || SORTS[0];
    }

    function sorted(rows) {
        const option = sortOption();
        return rows.slice().sort((a, b) => {
            const left = option.key(a);
            const right = option.key(b);
            for (let i = 0; i < left.length; i += 1) {
                const x = left[i];
                const y = right[i];
                if (x === y) continue;
                const numeric = typeof x === 'number' && typeof y === 'number';
                const cmp = numeric
                    ? x - y
                    : String(x).localeCompare(String(y), undefined, { numeric: true, sensitivity: 'base' });
                return option.desc ? -cmp : cmp;
            }
            return 0;
        });
    }

    function isFiltered() {
        return Boolean(state.q || state.kind || state.group || state.division || state.category)
            || sortOption().value !== 'group';
    }

    function renderFilters() {
        const bar = content.querySelector('#filters') || document.createElement('section');
        bar.className = 'card';
        bar.id = 'filters';

        const options = groups();
        const sections = divisions();
        const kinds = KIND_ORDER.filter(kind => kind === 'DESK' ? deskRows().length : peopleRows().some(p => p.kind === kind));

        bar.innerHTML = `
            <div class="filters">
                <div class="field" style="flex:1 1 260px">
                    <label for="fq">Search</label>
                    <input id="fq" type="search" placeholder="Name, number, email, programme or desk" value="${esc(state.q)}">
                </div>
                ${options.length > 1 ? `
                <div class="field">
                    <label for="fg">Programme</label>
                    <select id="fg">
                        <option value="">All programmes</option>
                        ${options.map(g => `<option value="${esc(g)}"${g === state.group ? ' selected' : ''}>${esc(g)}</option>`).join('')}
                    </select>
                </div>` : ''}
                ${sections.length > 1 ? `
                <div class="field">
                    <label for="fd">Class division</label>
                    <select id="fd">
                        <option value="">Any division</option>
                        ${sections.map(d => `<option value="${esc(d)}"${d === state.division ? ' selected' : ''}>${esc(d)}</option>`).join('')}
                    </select>
                </div>` : ''}
                <div class="field">
                    <label for="fs">Sort by</label>
                    <select id="fs">
                        ${SORTS.map(s => `<option value="${s.value}"${s.value === state.sort ? ' selected' : ''}>${s.label}</option>`).join('')}
                    </select>
                </div>
                <button class="btn btn-ghost" id="reset">Clear</button>
                <div class="chip-row" id="kinds" style="flex-basis:100%;padding-bottom:6px">
                    <span class="hint">Show&nbsp;</span>
                    <button class="btn btn-ghost btn-sm ${state.kind ? '' : 'btn-primary'}" data-kind="">Everyone</button>
                    ${kinds.map(kind => `<button class="btn btn-ghost btn-sm ${kind === state.kind ? 'btn-primary' : ''}" data-kind="${kind}">${esc(HEADINGS[kind][0])}</button>`).join('')}
                </div>
                ${state.kind && state.kind !== 'DESK' ? '' : `
                <div class="chip-row" id="cats" style="flex-basis:100%;padding-bottom:6px">
                    <span class="hint">Desk&nbsp;</span>
                    <button class="btn btn-ghost btn-sm ${state.category ? '' : 'btn-primary'}" data-cat="">All desks</button>
                    ${state.categories.map(c => `<button class="btn btn-ghost btn-sm ${c === state.category ? 'btn-primary' : ''}" data-cat="${esc(c)}">${esc(pretty(c))}</button>`).join('')}
                </div>`}
            </div>`;

        if (bar.parentNode !== content) {
            content.appendChild(bar);
        }

        bar.querySelector('#fq').addEventListener('input', (event) => {
            state.q = event.target.value;
            renderDirectory();
            renderGroups();
            updateSummary();
        });
        const program = bar.querySelector('#fg');
        if (program) program.addEventListener('change', (event) => {
            state.group = event.target.value;
            renderDirectory();
            updateSummary();
        });
        const division = bar.querySelector('#fd');
        if (division) division.addEventListener('change', (event) => {
            state.division = event.target.value;
            renderDirectory();
            updateSummary();
        });
        bar.querySelector('#fs').addEventListener('change', (event) => {
            state.sort = event.target.value;
            renderDirectory();
            renderGroups();
            updateSummary();
        });
        bar.querySelectorAll('[data-kind]').forEach(button => button.addEventListener('click', () => {
            state.kind = button.dataset.kind;
            // Programme and division mean nothing for desk rows, so drop them when switching kind.
            state.group = '';
            state.division = '';
            renderFilters();
            renderDirectory();
            renderGroups();
            updateSummary();
        }));
        const cats = bar.querySelector('#cats');
        if (cats) cats.querySelectorAll('[data-cat]').forEach(button => button.addEventListener('click', async () => {
            state.category = button.dataset.cat;
            state.contacts = await fetchRows();
            renderFilters();
            renderGroups();
            updateSummary();
        }));
        bar.querySelector('#reset').addEventListener('click', async () => {
            state.q = ''; state.kind = ''; state.group = ''; state.division = ''; state.sort = 'group'; state.category = '';
            state.contacts = await fetchRows();
            renderFilters();
            renderDirectory();
            renderGroups();
            updateSummary();
        });
    }

    function pretty(category) {
        return (category || '').replace(/_/g, ' ').toLowerCase().replace(/(^|\s)\S/g, c => c.toUpperCase());
    }

    function updateSummary() {
        const subtitle = document.getElementById('topbar-subtitle');
        if (!subtitle) return;
        const shown = peopleRows().filter(passes).length + deskRows().filter(passes).length;
        const total = peopleRows().length + deskRows().length;
        subtitle.textContent = isFiltered() && shown !== total
            ? `${shown} of ${total} contact(s) match the current filters`
            : baseSubtitle();
    }

    function tableOf(rows, columns) {
        return `
            <table>
                <thead><tr>${columns.map(c => `<th>${c}</th>`).join('')}</tr></thead>
                <tbody>${rows.join('')}</tbody>
            </table>`;
    }

    function renderDirectory() {
        const rows = sorted(peopleRows().filter(passes));
        const wrap = content.querySelector('#directory') || document.createElement('div');
        wrap.className = 'grid two';
        wrap.id = 'directory';
        wrap.innerHTML = KIND_ORDER.filter(kind => kind !== 'DESK').map((kind) => {
            const members = rows.filter(row => row.kind === kind);
            if (!members.length) return '';
            const [heading, hint] = HEADINGS[kind];
            return `
                <section class="card">
                    <div class="card-head"><h2>${esc(heading)}</h2><span class="hint">${members.length} listed</span></div>
                    <div class="card-body tight">
                        ${tableOf(members.map(personRow => `<tr>
                            <td>
                                <div class="cell-strong">${esc(personRow.name)}</div>
                                <div class="cell-sub">${esc(personRow.code || '')}</div>
                                <div class="cell-sub">${esc(personRow.detail || hint)}</div>
                            </td>
                            <td>
                                ${personRow.phone
                                    ? `<div class="cell-strong"><a href="tel:${esc(personRow.phone.replace(/\s/g, ''))}">${esc(personRow.phone)}</a></div>`
                                    : '<div class="cell-sub">not listed</div>'}
                                ${personRow.alternatePhone ? `<div class="cell-sub">Guardian ${esc(personRow.alternatePhone)}</div>` : ''}
                            </td>
                            <td>
                                ${personRow.email
                                    ? `<a href="mailto:${esc(personRow.email)}">${esc(personRow.email)}</a>`
                                    : '<span class="cell-sub">not listed</span>'}
                            </td>
                        </tr>`), ['Person', 'Phone', 'Email'])}
                    </div>
                </section>`;
        }).join('');

        const showsPeople = state.kind !== 'DESK';
        if (!wrap.innerHTML.trim() && showsPeople) {
            wrap.innerHTML = `<section class="card"><div class="card-body">${esc(state.q
                ? `Nothing in your contacts matches "${state.q}".`
                : state.peopleNote || 'No people to list for this sign-in yet.')}</div></section>`;
        }
        wrap.style.display = showsPeople ? '' : 'none';

        if (wrap.parentNode !== content) {
            // Keep the people directory above the desk groups it shares the page with.
            const boards = content.querySelector('#boards');
            if (boards) content.insertBefore(wrap, boards);
            else content.appendChild(wrap);
        }
    }

    function renderGroups() {
        const rows = sorted(deskRows().filter(passes));
        const grouped = new Map();
        for (const row of rows) {
            if (!grouped.has(row.category)) grouped.set(row.category, []);
            grouped.get(row.category).push(row);
        }

        const existing = content.querySelector('#boards');
        const wrap = existing || document.createElement('div');
        wrap.className = 'grid two';
        wrap.id = 'boards';
        const showsDesks = !state.kind || state.kind === 'DESK';
        wrap.innerHTML = [...grouped.entries()].map(([category, items]) => `
            <section class="card">
                <div class="card-head"><h2>${esc(pretty(category))}</h2><span class="hint">${items.length} line(s)</span></div>
                <div class="card-body tight">
                    ${tableOf(items.map(c => `
                        <tr>
                            <td>
                                <div class="cell-strong">${esc(c.name)}</div>
                                <div class="cell-sub">${esc(c.department || '')}</div>
                                ${c.email ? `<div class="cell-sub"><a href="mailto:${esc(c.email)}">${esc(c.email)}</a></div>` : ''}
                            </td>
                            <td>
                                <div class="cell-strong"><a href="tel:${esc((c.phone || '').replace(/\s/g, ''))}">${esc(c.phone)}</a></div>
                                ${c.alternatePhone ? `<div class="cell-sub">${esc(c.alternatePhone)}</div>` : ''}
                            </td>
                            <td>
                                <div class="cell-sub">${esc(c.location || '—')}</div>
                                <div class="cell-sub">${esc(c.availableHours || '')}</div>
                            </td>
                            ${Portal.isAdmin() ? `<td class="num">
                                <button class="btn btn-ghost btn-sm" data-edit="${c.id}">Edit</button>
                                <button class="btn btn-danger btn-sm" data-remove="${c.id}">Delete</button>
                            </td>` : ''}
                        </tr>`), ['Desk', 'Phone', 'Where &amp; when', ...(Portal.isAdmin() ? [''] : [])])}
                </div>
            </section>`).join('')
            || (showsDesks
                ? `<section class="card"><div class="card-body">${Portal.emptyState('No desks match the current filters.', '')}</div></section>`
                : '');
        wrap.style.display = showsDesks ? '' : 'none';

        if (!existing) content.appendChild(wrap);

        wrap.querySelectorAll('[data-edit]').forEach(button => button.addEventListener('click', () => {
            openForm(state.contacts.find(c => String(c.id) === button.dataset.edit));
        }));
        wrap.querySelectorAll('[data-remove]').forEach(button => button.addEventListener('click', () => {
            remove(state.contacts.find(c => String(c.id) === button.dataset.remove));
        }));
    }

    async function reloadDesks() {
        state.categories = await get('api/contacts/categories');
        state.contacts = await fetchRows();
        renderFilters();
        renderGroups();
        updateSummary();
    }

    async function openForm(contact) {
        await recordForm({
            title: contact ? `Edit ${contact.name}` : 'Add a contact',
            subtitle: contact ? pretty(contact.category) : 'Categories are matched case-insensitively, e.g. TRANSPORT.',
            fields: FIELDS,
            values: contact || { category: state.category || '' },
            submitLabel: contact ? 'Save changes' : 'Add contact',
            save: async (values) => {
                if (contact) {
                    await put(`api/contacts/${contact.id}`, { id: contact.id, ...values });
                    toast(`${values.name} updated`);
                } else {
                    await post('api/contacts', values);
                    toast(`${values.name} added`);
                }
                await reloadDesks();
            },
        });
    }

    async function remove(contact) {
        const ok = await confirmDialog({ title: `Delete ${contact.name}?`, message: `${contact.phone} will be removed from the directory.` });
        if (!ok) return;
        try {
            await del(`api/contacts/${contact.id}`);
            toast(`${contact.name} deleted`);
            await reloadDesks();
        } catch (error) {
            toast(error.message, 'err');
        }
    }

    load().catch(error => { content.innerHTML = `<div class="banner err">${esc(error.message)}</div>`; });
})();
