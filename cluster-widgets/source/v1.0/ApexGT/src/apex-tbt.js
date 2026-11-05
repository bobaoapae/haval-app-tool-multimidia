(function () {
    "use strict";

    // Cartão de turn-by-turn do Android Auto: o mesmo de Default e Minimalist (shared/tbt, via
    // window.ApexShared), só posicionado pelo apex-tbt.css. Sem rota o cartão some sozinho; um
    // alerta de radar aparece mesmo sem rota, como nos outros temas.
    var DIRECTIONS_KEY = "app.navigation.directions";
    var RADAR_KEY = "app.navigation.radar";

    function create(options) {
        var shared = window.ApexShared;
        var disposed = false;
        if (!shared || typeof shared.createTbtCard !== "function") {
            return { keys: [], update: function () {}, cleanup: function () {} };
        }
        var card = shared.createTbtCard();
        card.element.classList.add("apex-tbt");
        options.root.appendChild(card.element);

        function update(key, value) {
            if (disposed) return;
            if (key === DIRECTIONS_KEY) card.update(shared.parseNavigationDirections(value));
            else if (key === RADAR_KEY) card.setRadar(shared.parseRadarAlert(value));
        }

        return {
            keys: [DIRECTIONS_KEY, RADAR_KEY],
            update: update,
            element: card.element,
            cleanup: function () {
                if (disposed) return;
                disposed = true;
                if (card.element.parentNode) card.element.parentNode.removeChild(card.element);
            }
        };
    }

    window.ApexTbt = { create: create };
})();
