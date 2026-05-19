import { getState, subscribe } from '../../state.js';
import { div, span } from '../../../utils/createElement.js';

export function createMediaScreen() {
    var main = div({ className: 'main-container' });
    const container = div({ className: 'media-screen' });
    const outerRing = div({ className: 'media-outer-ring' });
    const innerRingShadow = div({ className: 'media-inner-ring-shadow' });
    const innerRing = div({ className: 'media-inner-ring' });

    const sourceBadge = div({ className: 'media-source-badge', children: [getState('mediaSource') || '—'] });

    const titleWrapper = div({ className: 'media-title-wrapper' });
    const titleEl = span({ className: 'media-title', children: [getState('mediaTitle') || 'Sem mídia'] });
    titleWrapper.appendChild(titleEl);

    const artistEl = div({ className: 'media-artist', children: [getState('mediaArtist') || ''] });

    const playIndicator = div({ className: 'media-play-indicator' });
    for (var i = 0; i < 4; i++) playIndicator.appendChild(div({ className: 'media-play-dot' }));

    // Favorites navigation arrows (radio only)
    const favNav = div({ className: 'media-fav-nav' });
    const btnUp = div({ className: 'media-fav-btn media-fav-up', children: ['▲'] });
    const favPos = span({ className: 'media-fav-pos', children: [''] });
    const btnDown = div({ className: 'media-fav-btn media-fav-down', children: ['▼'] });
    favNav.appendChild(btnUp);
    favNav.appendChild(favPos);
    favNav.appendChild(btnDown);

    innerRing.appendChild(sourceBadge);
    innerRing.appendChild(titleWrapper);
    innerRing.appendChild(artistEl);
    innerRing.appendChild(playIndicator);
    innerRing.appendChild(favNav);
    container.appendChild(outerRing);
    container.appendChild(innerRingShadow);
    container.appendChild(innerRing);
    main.appendChild(container);

    var marqueeTimer = null;

    function applyMarquee(title) {
        if (marqueeTimer) clearTimeout(marqueeTimer);
        titleEl.classList.remove('marquee');
        if (title && title.length > 16) {
            marqueeTimer = setTimeout(function () { titleEl.classList.add('marquee'); }, 800);
        }
    }

    function isRadio(source) {
        if (source === 'FM' || source === 'AM') return true;
        var title = getState('mediaTitle') || '';
        return title.endsWith(' FM') || title.endsWith(' AM');
    }

    function refreshFavNav() {
        var show = isRadio(getState('mediaSource'));
        favNav.style.display = show ? 'flex' : 'none';
        if (!show) return;
        var favs = getState('mediaFavorites');
        var title = getState('mediaTitle');
        if (Array.isArray(favs) && favs.length > 0) {
            var idx = favs.indexOf(title);
            favPos.textContent = idx >= 0 ? (idx + 1) + '/' + favs.length : '';
        } else {
            favPos.textContent = '';
        }
    }

    function updateSource(v) { sourceBadge.textContent = v || '—'; refreshFavNav(); }
    function updateTitle(v) { titleEl.textContent = v || 'Sem mídia'; applyMarquee(v); refreshFavNav(); }
    function updateArtist(v) { artistEl.textContent = v || ''; }
    function updatePlaying(v) { v ? playIndicator.classList.add('playing') : playIndicator.classList.remove('playing'); }
    function updateFavorites() { refreshFavNav(); }

    updateSource(getState('mediaSource'));
    updateTitle(getState('mediaTitle'));
    updateArtist(getState('mediaArtist'));
    updatePlaying(getState('mediaIsPlaying'));
    refreshFavNav();

    var subs = [
        subscribe('mediaSource', updateSource),
        subscribe('mediaTitle', updateTitle),
        subscribe('mediaArtist', updateArtist),
        subscribe('mediaIsPlaying', updatePlaying),
        subscribe('mediaFavorites', updateFavorites),
    ];

    main.cleanup = function () {
        if (marqueeTimer) clearTimeout(marqueeTimer);
        subs.forEach(function (u) { u(); });
    };

    return main;
}
