/* ============================================================
   Academy Portal :: colour themes
   Loaded in the page head so the saved theme is on <html> before
   the stylesheet paints. Colours themselves live in main.css.
   ============================================================ */
(() => {
    const STORAGE_KEY = 'academy.theme';
    const THEMES = [
        { id: 'slate', label: 'Slate' },
        { id: 'evergreen', label: 'Evergreen' },
        { id: 'marine', label: 'Marine' },
        { id: 'terracotta', label: 'Terracotta' },
        { id: 'sandstone', label: 'Sandstone' },
        { id: 'graphite', label: 'Graphite' },
    ];
    const ids = new Set(THEMES.map(theme => theme.id));

    function stored() {
        try {
            const saved = localStorage.getItem(STORAGE_KEY);
            return ids.has(saved) ? saved : 'slate';
        } catch (error) {
            return 'slate';
        }
    }

    let current = stored();
    document.documentElement.setAttribute('data-theme', current);

    function apply(id) {
        current = ids.has(id) ? id : 'slate';
        document.documentElement.setAttribute('data-theme', current);
        try { localStorage.setItem(STORAGE_KEY, current); } catch (error) { /* private mode */ }
        window.dispatchEvent(new CustomEvent('academy:theme', { detail: current }));
        return current;
    }

    /** Fills every <select data-theme-picker> inside the given root and keeps them in sync. */
    function mount(root = document) {
        root.querySelectorAll('select[data-theme-picker]').forEach(select => {
            if (select.dataset.pickerReady) return;
            select.dataset.pickerReady = '1';
            THEMES.forEach(theme => {
                const option = document.createElement('option');
                option.value = theme.id;
                option.textContent = theme.label;
                select.appendChild(option);
            });
            select.value = current;
            select.addEventListener('change', () => apply(select.value));
        });
    }

    function sync() {
        document.querySelectorAll('select[data-theme-picker]').forEach(select => {
            select.value = current;
        });
    }

    window.AcademyTheme = {
        options: () => THEMES.slice(),
        current: () => current,
        apply,
        mount,
        sync,
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => mount(document));
    } else {
        mount(document);
    }
})();
