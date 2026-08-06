//log always on in dev mode local). Set the log for the other scenarios
var DEBUG = false;
if (process.env.NODE_ENV === 'development') {
    //DEBUG = true;
}


export const logger = {
    log: (...args) => DEBUG && console.log('[LOG]', ...args),
    error: (...args) => DEBUG && console.error('[ERROR]', ...args),
    warn: (...args) => DEBUG && console.warn('[WARN]', ...args),
    enter: (name, ...args) => DEBUG && console.log(`>>> ENTER ${name}`, ...args),
    leave: (name) => DEBUG && console.log(`<<< LEAVE ${name}`)
};
