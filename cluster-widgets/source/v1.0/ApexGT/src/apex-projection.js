(function () {
    "use strict";

    // Como o Apex GT se comporta enquanto o CarPlay ou o Android Auto ocupam o painel.
    //
    // "Ampla" (padrão) integra o tema ao mapa: as superfícies escuras somem, a moldura se dissolve em
    // rampa até a borda do viewport e uma vinheta escurece as pontas — tudo em apex-projection.css, com
    // gradiente. Nada de `mask-mode: luminance`: ele NÃO existe no WebView desta central, e foi o que
    // deixou a moldura inteira e a borda dura em volta do mapa na 1.0.21.
    //
    // "Janela" mantém o recorte próprio do tema (520..1400 × 120..650).
    //
    // É preferência do tema (`apexProjectionMode` no theme.xml), trocada em Telas. Quem liga e desliga
    // o estado de projeção é o apex-gt.js, pela classe `projection-active` no root.
    var preferenceKey = "apexProjectionMode";
    var canonicalKey = "app.preferences." + preferenceKey;

    function validMode(value) {
        if (typeof value !== "string") return null;
        var token = value.trim().toLowerCase();
        if (token === "ampla" || token === "wide") return "Ampla";
        if (token === "janela" || token === "window" || token === "viewport") return "Janela";
        return null;
    }

    function create(options) {
        var root = options.root;
        var mode = "Ampla";
        var disposed = false;
        var canonicalSeen = false;

        function apply(next) {
            if (disposed) return;
            mode = next;
            root.classList.toggle("projection-wide", mode === "Ampla");
            root.setAttribute("data-projection-mode", mode);
        }

        function update(key, value, allowAliases) {
            if (disposed) return;
            if (key === canonicalKey) canonicalSeen = true;
            else {
                var preferenceAlias = key === "app.preferences.apex_projection_mode";
                var controlAlias = allowAliases && (key === preferenceKey || key === "apex_projection_mode");
                if (canonicalSeen || (!preferenceAlias && !controlAlias)) return;
            }
            var next = validMode(value);
            if (next !== null && next !== mode) apply(next);
        }

        var initial = null;
        try {
            if (window.Android && typeof window.Android.getPreference === "function") {
                initial = validMode(window.Android.getPreference(preferenceKey, "Ampla"));
            }
        } catch (error) {}
        apply(initial || "Ampla");

        return {
            keys: [canonicalKey],
            preferenceKeys: [canonicalKey],
            update: update,
            getMode: function () { return mode; },
            cleanup: function () { disposed = true; }
        };
    }

    window.ApexProjection = { create: create };
})();
