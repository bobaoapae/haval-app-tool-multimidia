(function () {
    "use strict";

    var preferenceKey = "apexDisplayMode";
    var canonicalKey = "app.preferences." + preferenceKey;

    function validMode(value) {
        return value === "Contour" || value === "Vector" ? value : null;
    }

    function create(options) {
        var root = options.root;
        var mode = "Contour";
        var disposed = false;
        var canonicalSeen = false;

        function apply(next) {
            if (disposed) return;
            var changed = mode !== next;
            mode = next;
            root.classList.toggle("display-contour", mode === "Contour");
            root.classList.toggle("display-vector", mode === "Vector");
            root.setAttribute("data-display", mode);
            if (changed) window.dispatchEvent(new CustomEvent("apex-display-change", { detail: { mode: mode } }));
        }

        function update(key, value, allowAliases) {
            if (disposed) return;
            if (key === canonicalKey) canonicalSeen = true;
            else {
                var preferenceAlias = key === "app.preferences.apex_display_mode";
                var controlAlias = allowAliases && (key === "apexDisplayMode" || key === "apex_display_mode");
                if (canonicalSeen || (!preferenceAlias && !controlAlias)) return;
            }
            var next = validMode(value);
            if (next !== null && next !== mode) apply(next);
        }

        function setMode(value) {
            if (disposed) return false;
            var next = validMode(value);
            if (next === null) return false;
            if (next === mode) return true;
            // This is only the theme's visual preference, never the host's
            // global display setting or projection bounds. Offline changes are
            // session-local; persisted changes use the theme-scoped API only.
            apply(next);
            try {
                if (window.Android && typeof window.Android.savePreference === "function") {
                    window.Android.savePreference(preferenceKey, next);
                }
            } catch (error) {}
            return true;
        }

        var initial = null;
        try {
            if (window.Android && typeof window.Android.getPreference === "function") {
                initial = validMode(window.Android.getPreference(preferenceKey, "Contour"));
            }
        } catch (error) {}
        apply(initial || "Contour");

        return {
            keys: [canonicalKey],
            update: update,
            setMode: setMode,
            getMode: function () { return mode; },
            cleanup: function () { disposed = true; }
        };
    }

    window.ApexDisplay = { create: create };
})();
