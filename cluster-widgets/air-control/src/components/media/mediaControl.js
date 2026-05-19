import {getState, subscribe} from '../../state.js';
import {div, span} from '../../utils/createElement.js';

export function createMediaScreen() {
    var main = div({className: 'main-container'});
    const container = div({className: 'media-screen'});

    const outerRing = div({className: 'media-outer-ring'});
    const innerRingShadow = div({className: 'media-inner-ring-shadow'});
    const innerRing = div({className: 'media-inner-ring'});

    const sourceBadge = div({
        className: 'media-source-badge',
        children: [getState('mediaSource') || '—']
    });

    const titleWrapper = div({className: 'media-title-wrapper'});
    const titleEl = span({
        className: 'media-title',
        children: [getState('mediaTitle') || 'Sem mídia']
    });
    titleWrapper.appendChild(titleEl);

    const artistEl = div({
        className: 'media-artist',
        children: [getState('mediaArtist') || '']
    });

    const playIndicator = div({className: 'media-play-indicator'});
    for (var i = 0; i < 4; i++) {
        var dot = div({className: 'media-play-dot'});
        playIndicator.appendChild(dot);
    }

    innerRing.appendChild(sourceBadge);
    innerRing.appendChild(titleWrapper);
    innerRing.appendChild(artistEl);
    innerRing.appendChild(playIndicator);

    container.appendChild(outerRing);
    container.appendChild(innerRingShadow);
    container.appendChild(innerRing);
    main.appendChild(container);

    var titleScrollTimer = null;

    function applyMarquee(title) {
        if (titleScrollTimer) clearTimeout(titleScrollTimer);
        titleEl.classList.remove('marquee');
        titleEl.style.transform = '';
        if (title && title.length > 16) {
            titleScrollTimer = setTimeout(function() {
                titleEl.classList.add('marquee');
            }, 800);
        }
    }

    function updateSource(source) {
        sourceBadge.textContent = source || '—';
    }

    function updateTitle(title) {
        titleEl.textContent = title || 'Sem mídia';
        applyMarquee(title);
    }

    function updateArtist(artist) {
        artistEl.textContent = artist || '';
    }

    function updatePlaying(isPlaying) {
        if (isPlaying) {
            playIndicator.classList.add('playing');
        } else {
            playIndicator.classList.remove('playing');
        }
    }

    updateSource(getState('mediaSource'));
    updateTitle(getState('mediaTitle'));
    updateArtist(getState('mediaArtist'));
    updatePlaying(getState('mediaIsPlaying'));

    var subscriptions = [
        subscribe('mediaSource', updateSource),
        subscribe('mediaTitle', updateTitle),
        subscribe('mediaArtist', updateArtist),
        subscribe('mediaIsPlaying', updatePlaying),
    ];

    main.cleanup = function() {
        if (titleScrollTimer) clearTimeout(titleScrollTimer);
        subscriptions.forEach(function(unsub) { unsub(); });
    };

    return main;
}
