(() => {
    const { esc, get, post, download, toast, statusBadge, emptyState, queryParam } = Portal;
    const content = document.getElementById('content');

    const state = {
        q: queryParam('q', ''),
        classLevel: queryParam('classLevel', ''),
        students: [],
        picked: new Set(),
        conferredOn: new Date().toISOString().slice(0, 10),
        term: '2026-ODD',
        busy: false,
    };

    const nameOf = (student) => `${student.firstName} ${student.lastName}`.trim();

    async function load() {
        await Portal.boot('certificates', { roles: ['ADMIN'] });
        state.students = await get('api/students');

        Portal.mountShell({
            active: 'certificates',
            title: 'Graduation certificates',
            subtitle: `${state.students.length} record(s) in the register - pick one or many and download the PDF`,
        });

        renderFilters();
        renderRunCard();
        renderRoster();
    }

    function programmes() {
        return [...new Set(state.students.map(s => s.classLevel).filter(Boolean))].sort();
    }

    function visible() {
        const needle = state.q.trim().toLowerCase();
        return state.students
            .filter(s => !state.classLevel || s.classLevel === state.classLevel)
            .filter(s => !needle || [s.rollNumber, nameOf(s), s.email, s.city, s.guardianName]
                .filter(Boolean).some(value => String(value).toLowerCase().includes(needle)))
            .sort((a, b) => `${a.classLevel}${a.section}${a.rollNumber}`
                .localeCompare(`${b.classLevel}${b.section}${b.rollNumber}`, undefined, { numeric: true }));
    }

    function renderFilters() {
        const bar = document.createElement('section');
        bar.className = 'card';
        bar.innerHTML = `
            <div class="filters">
                <div class="field" style="flex:1 1 260px">
                    <label for="fq">Search</label>
                    <input id="fq" type="search" placeholder="Name, roll number, email or city" value="${esc(state.q)}">
                </div>
                <div class="field">
                    <label for="fp">Programme</label>
                    <select id="fp">
                        <option value="">All programmes</option>
                        ${programmes().map(p => `<option value="${esc(p)}" ${p === state.classLevel ? 'selected' : ''}>${esc(p)}</option>`).join('')}
                    </select>
                </div>
                <div class="field">
                    <label>Selection</label>
                    <div class="chip-row">
                        <button class="btn btn-ghost btn-sm" id="all-visible">All listed</button>
                        <button class="btn btn-ghost btn-sm" id="none">Clear</button>
                    </div>
                </div>
            </div>`;
        content.appendChild(bar);

        bar.querySelector('#fq').addEventListener('input', (event) => {
            state.q = event.target.value;
            renderRoster();
        });
        bar.querySelector('#fp').addEventListener('change', (event) => {
            state.classLevel = event.target.value;
            renderRoster();
        });
        bar.querySelector('#all-visible').addEventListener('click', () => {
            visible().forEach(s => state.picked.add(s.id));
            renderRoster();
            renderRunCard();
        });
        bar.querySelector('#none').addEventListener('click', () => {
            state.picked.clear();
            renderRoster();
            renderRunCard();
        });
    }

    function renderRunCard() {
        const wrap = document.createElement('section');
        wrap.className = 'card';
        wrap.id = 'run';
        const chosen = state.students.filter(s => state.picked.has(s.id));
        const previewNames = chosen.slice(0, 3).map(nameOf).join(', ');
        const label = chosen.length === 1 ? 'certificate' : 'certificates';
        wrap.innerHTML = `
            <div class="card-head">
                <h2>Certificate run</h2>
                <span class="hint">${chosen.length} selected</span>
            </div>
            <div class="card-body">
                <div class="filters" style="padding:0;align-items:flex-end">
                    <div class="field">
                        <label for="frun-date">Conferred on</label>
                        <input id="frun-date" type="date" value="${esc(state.conferredOn)}">
                    </div>
                    <div class="field">
                        <label for="frun-term">Term printed on the page</label>
                        <input id="frun-term" value="${esc(state.term)}" placeholder="2026-ODD">
                    </div>
                    <div class="field">
                        <label>&nbsp;</label>
                        <div class="chip-row">
                            <button class="btn btn-ghost" id="preview" ${chosen.length ? '' : 'disabled'}>Preview first</button>
                            <button class="btn btn-primary" id="issue" ${chosen.length ? '' : 'disabled'}>Generate ${esc(label)} (PDF)</button>
                        </div>
                    </div>
                </div>
                <p class="cell-sub" style="margin:12px 0 0">
                    ${chosen.length
    ? `${esc(previewNames)}${chosen.length > 3 ? ` and ${chosen.length - 3} more` : ''} - one landscape A4 page per graduate.`
    : 'Tick students in the register below to add them to this run.'}
                </p>
            </div>`;

        const roster = content.querySelector('#roster');
        const previous = content.querySelector('#run');
        if (previous) previous.remove();
        if (roster) content.insertBefore(wrap, roster); else content.appendChild(wrap);

        wrap.querySelector('#frun-date').addEventListener('change', (event) => { state.conferredOn = event.target.value; });
        wrap.querySelector('#frun-term').addEventListener('input', (event) => { state.term = event.target.value; });
        wrap.querySelector('#preview').addEventListener('click', () => preview([...state.picked]));
        wrap.querySelector('#issue').addEventListener('click', () => issue());
    }

    function renderRoster() {
        const rows = visible();
        const allChecked = rows.length > 0 && rows.every(s => state.picked.has(s.id));

        const wrap = document.createElement('section');
        wrap.className = 'card';
        wrap.id = 'roster';
        wrap.innerHTML = `
            <div class="card-head">
                <h2>Student register</h2>
                <span class="hint">${rows.length} listed · ${state.picked.size} selected</span>
            </div>
            <div class="card-body tight">
                <table>
                    <thead>
                        <tr>
                            <th style="width:46px"><input type="checkbox" id="hdr-all" ${allChecked ? 'checked' : ''} aria-label="Select every listed student"></th>
                            <th>Student</th>
                            <th>Programme</th>
                            <th>Section</th>
                            <th>Status</th>
                            <th></th>
                        </tr>
                    </thead>
                    <tbody>
                        ${rows.map(s => `
                            <tr>
                                <td><input type="checkbox" data-pick="${s.id}" ${state.picked.has(s.id) ? 'checked' : ''} aria-label="Select ${esc(nameOf(s))}"></td>
                                <td>
                                    <div class="cell-strong">${esc(nameOf(s))}</div>
                                    <div class="cell-sub">${esc(s.rollNumber)}</div>
                                    <div class="cell-sub">${esc(s.email || '')}</div>
                                </td>
                                <td>${esc(s.classLevel || '—')}</td>
                                <td>${esc(s.section || '—')}</td>
                                <td>${statusBadge(s.status)}</td>
                                <td class="num">
                                    <button class="btn btn-ghost btn-sm" data-preview="${s.id}">Preview</button>
                                    <button class="btn btn-ghost btn-sm" data-solo="${s.id}">Certify only</button>
                                </td>
                            </tr>`).join('')}
                    </tbody>
                </table>
                ${rows.length ? '' : emptyState('No student matches that search.', ' Clear it to see the whole register.')}
            </div>`;

        const previous = content.querySelector('#roster');
        if (previous) previous.remove();
        content.appendChild(wrap);

        wrap.querySelector('#hdr-all').addEventListener('change', (event) => {
            rows.forEach(s => (event.target.checked ? state.picked.add(s.id) : state.picked.delete(s.id)));
            renderRoster();
            renderRunCard();
        });
        wrap.querySelectorAll('[data-pick]').forEach(box => box.addEventListener('change', () => {
            const id = Number(box.dataset.pick);
            if (box.checked) state.picked.add(id); else state.picked.delete(id);
            renderRunCard();
            wrap.querySelector('#hdr-all').checked = visible().every(s => state.picked.has(s.id));
        }));
        wrap.querySelectorAll('[data-preview]').forEach(button => button.addEventListener('click', () => {
            preview([Number(button.dataset.preview)]);
        }));
        wrap.querySelectorAll('[data-solo]').forEach(button => button.addEventListener('click', () => {
            state.picked = new Set([Number(button.dataset.solo)]);
            renderRoster();
            renderRunCard();
            issue();
        }));
    }

    const ready = (ids) => {
        if (!ids.length) {
            toast('Pick at least one student first.', 'err');
            return false;
        }
        if (!state.conferredOn) {
            toast('The conferral date is required.', 'err');
            return false;
        }
        return true;
    };

    const payload = (ids) => ({
        studentIds: ids,
        conferredOn: state.conferredOn,
        term: state.term.trim() || null,
    });

    /** Shows the exact wording the PDF will carry, without saving anything. */
    async function preview(ids) {
        if (!ready(ids)) return;
        let view;
        try {
            view = await post('api/certificates/preview', payload(ids));
        } catch (error) {
            toast(error.message, 'err');
            return;
        }
        showPreview(view);
    }

    function showPreview(view) {
        const backdrop = document.createElement('div');
        backdrop.className = 'modal-backdrop';
        backdrop.innerHTML = `
            <div class="modal wide" role="dialog" aria-modal="true" aria-label="Certificate preview">
                <header>
                    <h3>Certificate preview</h3>
                    <p>${view.pages.length} page(s), worded exactly as the download - nothing is saved yet.</p>
                    <div class="modal-actions" style="margin-top:14px">
                        <button type="button" class="btn btn-ghost" data-close>Close</button>
                        <button type="button" class="btn btn-primary" data-issue>Generate ${view.pages.length === 1 ? 'certificate' : 'certificates'} (PDF)</button>
                    </div>
                </header>
                <div class="cert-scroll">${view.pages.map(page => sheet(view, page)).join('')}</div>
            </div>`;
        document.body.appendChild(backdrop);

        const onKey = (event) => { if (event.key === 'Escape') { backdrop.remove(); document.removeEventListener('keydown', onKey); } };
        const close = () => { backdrop.remove(); document.removeEventListener('keydown', onKey); };
        document.addEventListener('keydown', onKey);
        backdrop.addEventListener('click', (event) => {
            if (event.target === backdrop || event.target.hasAttribute('data-close')) close();
        });
        backdrop.querySelector('[data-issue]').addEventListener('click', () => {
            close();
            issue();
        });
    }

    function sheet(view, page) {
        return `
            <article class="cert-sheet">
                <div class="academy">${esc(page.academyName)}</div>
                <div class="heading">${esc(view.heading)}</div>
                <div class="certify">${esc(view.certifyLine)}</div>
                <div class="graduate">${esc(page.studentName)}</div>
                <div class="roll">${esc(page.rollLine)}</div>
                <p class="statement">${esc(page.statement)}</p>
                <div class="subjects">${esc(page.subjects)}</div>
                <div class="when">${esc(page.conferredLine)}</div>
                <div class="sign">
                    ${page.signatures.map(s => `<div><strong>${esc(s.name)}</strong><span>${esc(s.role)}</span></div>`).join('')}
                </div>
                <div class="serial">${esc(page.serialLine)}</div>
            </article>`;
    }

    async function issue() {
        if (state.busy) return;
        const ids = [...state.picked];
        if (!ready(ids)) return;

        state.busy = true;
        const button = content.querySelector('#issue');
        if (button) { button.disabled = true; button.innerHTML = '<span class="spinner"></span> Drawing'; }
        try {
            const file = await download('api/certificates', payload(ids));
            toast(`${file.filename} saved · ${ids.length} page(s)`);
        } catch (error) {
            toast(error.message, 'err');
        } finally {
            state.busy = false;
            const again = content.querySelector('#issue');
            if (again) {
                again.disabled = false;
                again.textContent = `Generate ${ids.length === 1 ? 'certificate' : 'certificates'} (PDF)`;
            }
        }
    }

    load().catch(error => { content.innerHTML = `<div class="banner err">${esc(error.message)}</div>`; });
})();
