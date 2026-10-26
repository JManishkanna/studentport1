(() => {
    const { esc, get, post, put, del, recordForm, confirmDialog, toast, queryParam } = Portal;
    const content = document.getElementById('content');

    const state = { q: queryParam('q', ''), category: queryParam('category', ''), contacts: [], categories: [] };

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
        state.categories = await get('api/contacts/categories');
        state.contacts = await fetchRows();

        Portal.mountShell({
            active: 'contacts',
            title: 'Contact information',
            subtitle: 'Department desks, helplines and office hours',
            actions: me.role === 'ADMIN' ? '<button class="btn btn-primary" id="add">+ Add contact</button>' : '',
        });

        renderFilters();
        renderGroups();

        if (me.role === 'ADMIN') document.getElementById('add').addEventListener('click', () => openForm(null));
    }

    function fetchRows() {
        const params = new URLSearchParams();
        if (state.category) params.set('category', state.category);
        return get(`api/contacts?${params.toString()}`);
    }

    function renderFilters() {
        const bar = document.createElement('section');
        bar.className = 'card';
        bar.innerHTML = `
            <div class="filters">
                <div class="field" style="flex:1 1 260px">
                    <label for="fq">Search</label>
                    <input id="fq" type="search" placeholder="Name, phone, email or department" value="${esc(state.q)}">
                </div>
                <div class="chip-row" id="cats" style="padding-bottom:6px">
                    <button class="btn btn-ghost btn-sm ${state.category ? '' : 'btn-primary'}" data-cat="">All</button>
                    ${state.categories.map(c => `<button class="btn btn-ghost btn-sm ${c === state.category ? 'btn-primary' : ''}" data-cat="${esc(c)}">${esc(pretty(c))}</button>`).join('')}
                </div>
            </div>`;
        content.appendChild(bar);

        bar.querySelector('#fq').addEventListener('input', (event) => {
            state.q = event.target.value;
            renderGroups();
        });
        bar.querySelectorAll('[data-cat]').forEach(button => button.addEventListener('click', async () => {
            state.category = button.dataset.cat;
            bar.querySelectorAll('[data-cat]').forEach(b => b.classList.toggle('btn-primary', b === button));
            state.contacts = await fetchRows();
            renderGroups();
        }));
    }

    function pretty(category) {
        return (category || '').replace(/_/g, ' ').toLowerCase().replace(/(^|\s)\S/g, c => c.toUpperCase());
    }

    function matches(contact) {
        if (!state.q) return true;
        const needle = state.q.toLowerCase();
        return [contact.name, contact.phone, contact.alternatePhone, contact.email, contact.department, contact.location]
            .filter(Boolean).some(value => value.toLowerCase().includes(needle));
    }

    function renderGroups() {
        const visible = state.contacts.filter(matches);
        const grouped = new Map();
        for (const contact of visible) {
            if (!grouped.has(contact.category)) grouped.set(contact.category, []);
            grouped.get(contact.category).push(contact);
        }

        const existing = content.querySelector('#boards');
        const wrap = existing || document.createElement('div');
        wrap.className = 'grid two';
        wrap.id = 'boards';
        wrap.innerHTML = [...grouped.entries()].map(([category, items]) => `
            <section class="card">
                <div class="card-head"><h2>${esc(pretty(category))}</h2><span class="hint">${items.length} line(s)</span></div>
                <div class="card-body tight">
                    <table>
                        <thead><tr><th>Desk</th><th>Phone</th><th>Where &amp; when</th>${Portal.isAdmin() ? '<th></th>' : ''}</tr></thead>
                        <tbody>
                            ${items.map(c => `
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
                                </tr>`).join('')}
                        </tbody>
                    </table>
                </div>
            </section>`).join('')
            || `<section class="card"><div class="card-body">${Portal.emptyState('No contacts match that search.', '')}</div></section>`;

        if (!existing) content.appendChild(wrap);

        wrap.querySelectorAll('[data-edit]').forEach(button => button.addEventListener('click', () => {
            openForm(state.contacts.find(c => String(c.id) === button.dataset.edit));
        }));
        wrap.querySelectorAll('[data-remove]').forEach(button => button.addEventListener('click', () => {
            remove(state.contacts.find(c => String(c.id) === button.dataset.remove));
        }));
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
                state.categories = await get('api/contacts/categories');
                state.contacts = await fetchRows();
                renderGroups();
            },
        });
    }

    async function remove(contact) {
        const ok = await confirmDialog({ title: `Delete ${contact.name}?`, message: `${contact.phone} will be removed from the directory.` });
        if (!ok) return;
        try {
            await del(`api/contacts/${contact.id}`);
            toast(`${contact.name} deleted`);
            state.categories = await get('api/contacts/categories');
            state.contacts = await fetchRows();
            renderGroups();
        } catch (error) {
            toast(error.message, 'err');
        }
    }

    load().catch(error => { content.innerHTML = `<div class="banner err">${esc(error.message)}</div>`; });
})();
