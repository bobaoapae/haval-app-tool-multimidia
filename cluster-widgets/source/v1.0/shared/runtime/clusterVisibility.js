/**
 * Existing host control('clusterEnabled', boolean): suppress the whole theme page.
 * Keep its DOM/state and viewport geometry intact so re-enabling restores the
 * current screen. Only explicit false disables; older hosts remain enabled.
 */
export function applyClusterVisibility(enabled) {
    document.documentElement.classList.toggle('cluster-disabled', enabled === false);
}
