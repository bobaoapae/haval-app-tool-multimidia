const SPORT_THEME_PATHS = new Set([
  '/Themes/SportRed/index.html',
  '/Themes/SportRedLite/index.html',
]);

const OLD_EASING = 'Math.exp(-i/180)';
const NEW_EASING = 'Math.exp(-i/100)';
const SPEED_SNAP = 'Math.abs(eU.speed-eK)>=30&&(eK=eU.speed),';
const POWER_SNAP = 'Math.abs(eU.powerKw-ew)>=80&&(ew=eU.powerKw),';
const OLD_SPEED_TRANSITION =
  'transition:x1 80ms linear,y1 80ms linear,x2 80ms linear,y2 80ms linear';
const NEW_SPEED_TRANSITION =
  'transition:x1 34ms linear,y1 34ms linear,x2 34ms linear,y2 34ms linear';
const HIDDEN_CANVAS_LOOP_START = '!function A(){let r,n;s.clearRect(0,0,500,500),';
const VISIBLE_CANVAS_LOOP_START =
  '!function A(){let r,n;if(!P.closest(".display-normal")){f=requestAnimationFrame(A);return}s.clearRect(0,0,500,500),';

function countOccurrences(source, fragment) {
  return source.split(fragment).length - 1;
}

export function isSportGaugeMotionPath(pathname) {
  return SPORT_THEME_PATHS.has(pathname);
}

// Preview counterpart of ProjectionDisplayHtmlPolicy. Keep this exact and fail-closed so the
// immutable Sport packages remain untouched while Theme Lab reflects the production behavior.
export function tuneSportGaugeMotion(source) {
  const expectedFragments = [OLD_EASING, SPEED_SNAP, POWER_SNAP, OLD_SPEED_TRANSITION];
  if (expectedFragments.some((fragment) => countOccurrences(source, fragment) !== 1)) {
    return { source, tuned: false };
  }

  return {
    source: source
      .replace(OLD_EASING, NEW_EASING)
      .replace(SPEED_SNAP, '')
      .replace(POWER_SNAP, '')
      .replace(OLD_SPEED_TRANSITION, NEW_SPEED_TRANSITION),
    tuned: true,
  };
}

export function pauseHiddenSportCanvas(source) {
  if (
    countOccurrences(source, HIDDEN_CANVAS_LOOP_START) !== 1 ||
    source.includes(VISIBLE_CANVAS_LOOP_START)
  ) {
    return { source, paused: false };
  }
  return {
    source: source.replace(HIDDEN_CANVAS_LOOP_START, VISIBLE_CANVAS_LOOP_START),
    paused: true,
  };
}
