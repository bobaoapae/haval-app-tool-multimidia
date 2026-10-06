// modules are defined as an array
// [ module function, map of requires ]
//
// map of requires is short require name -> numeric require
//
// anything defined in a previous bundle is accessed via the
// orig method which is the require for previous bundles

(function (
  modules,
  entry,
  mainEntry,
  parcelRequireName,
  externals,
  distDir,
  publicUrl,
  devServer
) {
  /* eslint-disable no-undef */
  var globalObject =
    typeof globalThis !== 'undefined'
      ? globalThis
      : typeof self !== 'undefined'
      ? self
      : typeof window !== 'undefined'
      ? window
      : typeof global !== 'undefined'
      ? global
      : {};
  /* eslint-enable no-undef */

  // Save the require from previous bundle to this closure if any
  var previousRequire =
    typeof globalObject[parcelRequireName] === 'function' &&
    globalObject[parcelRequireName];

  var importMap = previousRequire.i || {};
  var cache = previousRequire.cache || {};
  // Do not use `require` to prevent Webpack from trying to bundle this call
  var nodeRequire =
    typeof module !== 'undefined' &&
    typeof module.require === 'function' &&
    module.require.bind(module);

  function newRequire(name, jumped) {
    if (!cache[name]) {
      if (!modules[name]) {
        if (externals[name]) {
          return externals[name];
        }
        // if we cannot find the module within our internal map or
        // cache jump to the current global require ie. the last bundle
        // that was added to the page.
        var currentRequire =
          typeof globalObject[parcelRequireName] === 'function' &&
          globalObject[parcelRequireName];
        if (!jumped && currentRequire) {
          return currentRequire(name, true);
        }

        // If there are other bundles on this page the require from the
        // previous one is saved to 'previousRequire'. Repeat this as
        // many times as there are bundles until the module is found or
        // we exhaust the require chain.
        if (previousRequire) {
          return previousRequire(name, true);
        }

        // Try the node require function if it exists.
        if (nodeRequire && typeof name === 'string') {
          return nodeRequire(name);
        }

        var err = new Error("Cannot find module '" + name + "'");
        err.code = 'MODULE_NOT_FOUND';
        throw err;
      }

      localRequire.resolve = resolve;
      localRequire.cache = {};

      var module = (cache[name] = new newRequire.Module(name));

      modules[name][0].call(
        module.exports,
        localRequire,
        module,
        module.exports,
        globalObject
      );
    }

    return cache[name].exports;

    function localRequire(x) {
      var res = localRequire.resolve(x);
      if (res === false) {
        return {};
      }
      // Synthesize a module to follow re-exports.
      if (Array.isArray(res)) {
        var m = {__esModule: true};
        res.forEach(function (v) {
          var key = v[0];
          var id = v[1];
          var exp = v[2] || v[0];
          var x = newRequire(id);
          if (key === '*') {
            Object.keys(x).forEach(function (key) {
              if (
                key === 'default' ||
                key === '__esModule' ||
                Object.prototype.hasOwnProperty.call(m, key)
              ) {
                return;
              }

              Object.defineProperty(m, key, {
                enumerable: true,
                get: function () {
                  return x[key];
                },
              });
            });
          } else if (exp === '*') {
            Object.defineProperty(m, key, {
              enumerable: true,
              value: x,
            });
          } else {
            Object.defineProperty(m, key, {
              enumerable: true,
              get: function () {
                if (exp === 'default') {
                  return x.__esModule ? x.default : x;
                }
                return x[exp];
              },
            });
          }
        });
        return m;
      }
      return newRequire(res);
    }

    function resolve(x) {
      var id = modules[name][1][x];
      return id != null ? id : x;
    }
  }

  function Module(moduleName) {
    this.id = moduleName;
    this.bundle = newRequire;
    this.require = nodeRequire;
    this.exports = {};
  }

  newRequire.isParcelRequire = true;
  newRequire.Module = Module;
  newRequire.modules = modules;
  newRequire.cache = cache;
  newRequire.parent = previousRequire;
  newRequire.distDir = distDir;
  newRequire.publicUrl = publicUrl;
  newRequire.devServer = devServer;
  newRequire.i = importMap;
  newRequire.register = function (id, exports) {
    modules[id] = [
      function (require, module) {
        module.exports = exports;
      },
      {},
    ];
  };

  // Only insert newRequire.load when it is actually used.
  // The code in this file is linted against ES5, so dynamic import is not allowed.
  // INSERT_LOAD_HERE

  Object.defineProperty(newRequire, 'root', {
    get: function () {
      return globalObject[parcelRequireName];
    },
  });

  globalObject[parcelRequireName] = newRequire;

  for (var i = 0; i < entry.length; i++) {
    newRequire(entry[i]);
  }

  if (mainEntry) {
    // Expose entry point to Node, AMD or browser globals
    // Based on https://github.com/ForbesLindesay/umd/blob/master/template.js
    var mainExports = newRequire(mainEntry);

    // CommonJS
    if (typeof exports === 'object' && typeof module !== 'undefined') {
      module.exports = mainExports;

      // RequireJS
    } else if (typeof define === 'function' && define.amd) {
      define(function () {
        return mainExports;
      });
    }
  }
})({"75BAb":[function(require,module,exports,__globalThis) {
var global = arguments[3];
var HMR_HOST = null;
var HMR_PORT = null;
var HMR_SERVER_PORT = 1234;
var HMR_SECURE = false;
var HMR_ENV_HASH = "439701173a9199ea";
var HMR_USE_SSE = false;
module.bundle.HMR_BUNDLE_ID = "c28f771bfd4bfff4";
"use strict";
/* global HMR_HOST, HMR_PORT, HMR_SERVER_PORT, HMR_ENV_HASH, HMR_SECURE, HMR_USE_SSE, chrome, browser, __parcel__import__, __parcel__importScripts__, ServiceWorkerGlobalScope */ /*::
import type {
  HMRAsset,
  HMRMessage,
} from '@parcel/reporter-dev-server/src/HMRServer.js';
interface ParcelRequire {
  (string): mixed;
  cache: {|[string]: ParcelModule|};
  hotData: {|[string]: mixed|};
  Module: any;
  parent: ?ParcelRequire;
  isParcelRequire: true;
  modules: {|[string]: [Function, {|[string]: string|}]|};
  HMR_BUNDLE_ID: string;
  root: ParcelRequire;
}
interface ParcelModule {
  hot: {|
    data: mixed,
    accept(cb: (Function) => void): void,
    dispose(cb: (mixed) => void): void,
    // accept(deps: Array<string> | string, cb: (Function) => void): void,
    // decline(): void,
    _acceptCallbacks: Array<(Function) => void>,
    _disposeCallbacks: Array<(mixed) => void>,
  |};
}
interface ExtensionContext {
  runtime: {|
    reload(): void,
    getURL(url: string): string;
    getManifest(): {manifest_version: number, ...};
  |};
}
declare var module: {bundle: ParcelRequire, ...};
declare var HMR_HOST: string;
declare var HMR_PORT: string;
declare var HMR_SERVER_PORT: string;
declare var HMR_ENV_HASH: string;
declare var HMR_SECURE: boolean;
declare var HMR_USE_SSE: boolean;
declare var chrome: ExtensionContext;
declare var browser: ExtensionContext;
declare var __parcel__import__: (string) => Promise<void>;
declare var __parcel__importScripts__: (string) => Promise<void>;
declare var globalThis: typeof self;
declare var ServiceWorkerGlobalScope: Object;
*/ var OVERLAY_ID = '__parcel__error__overlay__';
var OldModule = module.bundle.Module;
function Module(moduleName) {
    OldModule.call(this, moduleName);
    this.hot = {
        data: module.bundle.hotData[moduleName],
        _acceptCallbacks: [],
        _disposeCallbacks: [],
        accept: function(fn) {
            this._acceptCallbacks.push(fn || function() {});
        },
        dispose: function(fn) {
            this._disposeCallbacks.push(fn);
        }
    };
    module.bundle.hotData[moduleName] = undefined;
}
module.bundle.Module = Module;
module.bundle.hotData = {};
var checkedAssets /*: {|[string]: boolean|} */ , disposedAssets /*: {|[string]: boolean|} */ , assetsToDispose /*: Array<[ParcelRequire, string]> */ , assetsToAccept /*: Array<[ParcelRequire, string]> */ , bundleNotFound = false;
function getHostname() {
    return HMR_HOST || (typeof location !== 'undefined' && location.protocol.indexOf('http') === 0 ? location.hostname : 'localhost');
}
function getPort() {
    return HMR_PORT || (typeof location !== 'undefined' ? location.port : HMR_SERVER_PORT);
}
// eslint-disable-next-line no-redeclare
let WebSocket = globalThis.WebSocket;
if (!WebSocket && typeof module.bundle.root === 'function') try {
    // eslint-disable-next-line no-global-assign
    WebSocket = module.bundle.root('ws');
} catch  {
// ignore.
}
var hostname = getHostname();
var port = getPort();
var protocol = HMR_SECURE || typeof location !== 'undefined' && location.protocol === 'https:' && ![
    'localhost',
    '127.0.0.1',
    '0.0.0.0'
].includes(hostname) ? 'wss' : 'ws';
// eslint-disable-next-line no-redeclare
var parent = module.bundle.parent;
if (!parent || !parent.isParcelRequire) {
    // Web extension context
    var extCtx = typeof browser === 'undefined' ? typeof chrome === 'undefined' ? null : chrome : browser;
    // Safari doesn't support sourceURL in error stacks.
    // eval may also be disabled via CSP, so do a quick check.
    var supportsSourceURL = false;
    try {
        (0, eval)('throw new Error("test"); //# sourceURL=test.js');
    } catch (err) {
        supportsSourceURL = err.stack.includes('test.js');
    }
    var ws;
    if (HMR_USE_SSE) ws = new EventSource('/__parcel_hmr');
    else try {
        // If we're running in the dev server's node runner, listen for messages on the parent port.
        let { workerData, parentPort } = module.bundle.root('node:worker_threads') /*: any*/ ;
        if (workerData !== null && workerData !== void 0 && workerData.__parcel) {
            parentPort.on('message', async (message)=>{
                try {
                    await handleMessage(message);
                    parentPort.postMessage('updated');
                } catch  {
                    parentPort.postMessage('restart');
                }
            });
            // After the bundle has finished running, notify the dev server that the HMR update is complete.
            queueMicrotask(()=>parentPort.postMessage('ready'));
        }
    } catch  {
        if (typeof WebSocket !== 'undefined') try {
            ws = new WebSocket(protocol + '://' + hostname + (port ? ':' + port : '') + '/');
        } catch (err) {
            // Ignore cloudflare workers error.
            if (err.message && !err.message.includes('Disallowed operation called within global scope')) console.error(err.message);
        }
    }
    if (ws) {
        // $FlowFixMe
        ws.onmessage = async function(event /*: {data: string, ...} */ ) {
            var data /*: HMRMessage */  = JSON.parse(event.data);
            await handleMessage(data);
        };
        if (ws instanceof WebSocket) {
            ws.onerror = function(e) {
                if (e.message) console.error(e.message);
            };
            ws.onclose = function() {
                console.warn("[parcel] \uD83D\uDEA8 Connection to the HMR server was lost");
            };
        }
    }
}
async function handleMessage(data /*: HMRMessage */ ) {
    checkedAssets = {} /*: {|[string]: boolean|} */ ;
    disposedAssets = {} /*: {|[string]: boolean|} */ ;
    assetsToAccept = [];
    assetsToDispose = [];
    bundleNotFound = false;
    if (data.type === 'reload') fullReload();
    else if (data.type === 'update') {
        // Remove error overlay if there is one
        if (typeof document !== 'undefined') removeErrorOverlay();
        let assets = data.assets;
        // Handle HMR Update
        let handled = assets.every((asset)=>{
            return asset.type === 'css' || asset.type === 'js' && hmrAcceptCheck(module.bundle.root, asset.id, asset.depsByBundle);
        });
        // Dispatch a custom event in case a bundle was not found. This might mean
        // an asset on the server changed and we should reload the page. This event
        // gives the client an opportunity to refresh without losing state
        // (e.g. via React Server Components). If e.preventDefault() is not called,
        // we will trigger a full page reload.
        if (handled && bundleNotFound && assets.some((a)=>a.envHash !== HMR_ENV_HASH) && typeof window !== 'undefined' && typeof CustomEvent !== 'undefined') handled = !window.dispatchEvent(new CustomEvent('parcelhmrreload', {
            cancelable: true
        }));
        if (handled) {
            console.clear();
            // Dispatch custom event so other runtimes (e.g React Refresh) are aware.
            if (typeof window !== 'undefined' && typeof CustomEvent !== 'undefined') window.dispatchEvent(new CustomEvent('parcelhmraccept'));
            await hmrApplyUpdates(assets);
            hmrDisposeQueue();
            // Run accept callbacks. This will also re-execute other disposed assets in topological order.
            let processedAssets = {};
            for(let i = 0; i < assetsToAccept.length; i++){
                let id = assetsToAccept[i][1];
                if (!processedAssets[id]) {
                    hmrAccept(assetsToAccept[i][0], id);
                    processedAssets[id] = true;
                }
            }
        } else fullReload();
    }
    if (data.type === 'error') {
        // Log parcel errors to console
        for (let ansiDiagnostic of data.diagnostics.ansi){
            let stack = ansiDiagnostic.codeframe ? ansiDiagnostic.codeframe : ansiDiagnostic.stack;
            console.error("\uD83D\uDEA8 [parcel]: " + ansiDiagnostic.message + '\n' + stack + '\n\n' + ansiDiagnostic.hints.join('\n'));
        }
        if (typeof document !== 'undefined') {
            // Render the fancy html overlay
            removeErrorOverlay();
            var overlay = createErrorOverlay(data.diagnostics.html);
            // $FlowFixMe
            document.body.appendChild(overlay);
        }
    }
}
function removeErrorOverlay() {
    var overlay = document.getElementById(OVERLAY_ID);
    if (overlay) {
        overlay.remove();
        console.log("[parcel] \u2728 Error resolved");
    }
}
function createErrorOverlay(diagnostics) {
    var overlay = document.createElement('div');
    overlay.id = OVERLAY_ID;
    let errorHTML = '<div style="background: black; opacity: 0.85; font-size: 16px; color: white; position: fixed; height: 100%; width: 100%; top: 0px; left: 0px; padding: 30px; font-family: Menlo, Consolas, monospace; z-index: 9999;">';
    for (let diagnostic of diagnostics){
        let stack = diagnostic.frames.length ? diagnostic.frames.reduce((p, frame)=>{
            return `${p}
<a href="${protocol === 'wss' ? 'https' : 'http'}://${hostname}:${port}/__parcel_launch_editor?file=${encodeURIComponent(frame.location)}" style="text-decoration: underline; color: #888" onclick="fetch(this.href); return false">${frame.location}</a>
${frame.code}`;
        }, '') : diagnostic.stack;
        errorHTML += `
      <div>
        <div style="font-size: 18px; font-weight: bold; margin-top: 20px;">
          \u{1F6A8} ${diagnostic.message}
        </div>
        <pre>${stack}</pre>
        <div>
          ${diagnostic.hints.map((hint)=>"<div>\uD83D\uDCA1 " + hint + '</div>').join('')}
        </div>
        ${diagnostic.documentation ? `<div>\u{1F4DD} <a style="color: violet" href="${diagnostic.documentation}" target="_blank">Learn more</a></div>` : ''}
      </div>
    `;
    }
    errorHTML += '</div>';
    overlay.innerHTML = errorHTML;
    return overlay;
}
function fullReload() {
    if (typeof location !== 'undefined' && 'reload' in location) location.reload();
    else if (typeof extCtx !== 'undefined' && extCtx && extCtx.runtime && extCtx.runtime.reload) extCtx.runtime.reload();
    else try {
        let { workerData, parentPort } = module.bundle.root('node:worker_threads') /*: any*/ ;
        if (workerData !== null && workerData !== void 0 && workerData.__parcel) parentPort.postMessage('restart');
    } catch (err) {
        console.error("[parcel] \u26A0\uFE0F An HMR update was not accepted. Please restart the process.");
    }
}
function getParents(bundle, id) /*: Array<[ParcelRequire, string]> */ {
    var modules = bundle.modules;
    if (!modules) return [];
    var parents = [];
    var k, d, dep;
    for(k in modules)for(d in modules[k][1]){
        dep = modules[k][1][d];
        if (dep === id || Array.isArray(dep) && dep[dep.length - 1] === id) parents.push([
            bundle,
            k
        ]);
    }
    if (bundle.parent) parents = parents.concat(getParents(bundle.parent, id));
    return parents;
}
function updateLink(link) {
    var href = link.getAttribute('href');
    if (!href) return;
    var newLink = link.cloneNode();
    newLink.onload = function() {
        if (link.parentNode !== null) // $FlowFixMe
        link.parentNode.removeChild(link);
    };
    newLink.setAttribute('href', // $FlowFixMe
    href.split('?')[0] + '?' + Date.now());
    // $FlowFixMe
    link.parentNode.insertBefore(newLink, link.nextSibling);
}
var cssTimeout = null;
function reloadCSS() {
    if (cssTimeout || typeof document === 'undefined') return;
    cssTimeout = setTimeout(function() {
        var links = document.querySelectorAll('link[rel="stylesheet"]');
        for(var i = 0; i < links.length; i++){
            // $FlowFixMe[incompatible-type]
            var href /*: string */  = links[i].getAttribute('href');
            var hostname = getHostname();
            var servedFromHMRServer = hostname === 'localhost' ? new RegExp('^(https?:\\/\\/(0.0.0.0|127.0.0.1)|localhost):' + getPort()).test(href) : href.indexOf(hostname + ':' + getPort());
            var absolute = /^https?:\/\//i.test(href) && href.indexOf(location.origin) !== 0 && !servedFromHMRServer;
            if (!absolute) updateLink(links[i]);
        }
        cssTimeout = null;
    }, 50);
}
function hmrDownload(asset) {
    if (asset.type === 'js') {
        if (typeof document !== 'undefined') {
            let script = document.createElement('script');
            script.src = asset.url + '?t=' + Date.now();
            if (asset.outputFormat === 'esmodule') script.type = 'module';
            return new Promise((resolve, reject)=>{
                var _document$head;
                script.onload = ()=>resolve(script);
                script.onerror = reject;
                (_document$head = document.head) === null || _document$head === void 0 || _document$head.appendChild(script);
            });
        } else if (typeof importScripts === 'function') {
            // Worker scripts
            if (asset.outputFormat === 'esmodule') return import(asset.url + '?t=' + Date.now());
            else return new Promise((resolve, reject)=>{
                try {
                    importScripts(asset.url + '?t=' + Date.now());
                    resolve();
                } catch (err) {
                    reject(err);
                }
            });
        }
    }
}
async function hmrApplyUpdates(assets) {
    global.parcelHotUpdate = Object.create(null);
    let scriptsToRemove;
    try {
        // If sourceURL comments aren't supported in eval, we need to load
        // the update from the dev server over HTTP so that stack traces
        // are correct in errors/logs. This is much slower than eval, so
        // we only do it if needed (currently just Safari).
        // https://bugs.webkit.org/show_bug.cgi?id=137297
        // This path is also taken if a CSP disallows eval.
        if (!supportsSourceURL) {
            let promises = assets.map((asset)=>{
                var _hmrDownload;
                return (_hmrDownload = hmrDownload(asset)) === null || _hmrDownload === void 0 ? void 0 : _hmrDownload.catch((err)=>{
                    // Web extension fix
                    if (extCtx && extCtx.runtime && extCtx.runtime.getManifest().manifest_version == 3 && typeof ServiceWorkerGlobalScope != 'undefined' && global instanceof ServiceWorkerGlobalScope) {
                        extCtx.runtime.reload();
                        return;
                    }
                    throw err;
                });
            });
            scriptsToRemove = await Promise.all(promises);
        }
        assets.forEach(function(asset) {
            hmrApply(module.bundle.root, asset);
        });
    } finally{
        delete global.parcelHotUpdate;
        if (scriptsToRemove) scriptsToRemove.forEach((script)=>{
            if (script) {
                var _document$head2;
                (_document$head2 = document.head) === null || _document$head2 === void 0 || _document$head2.removeChild(script);
            }
        });
    }
}
function hmrApply(bundle /*: ParcelRequire */ , asset /*:  HMRAsset */ ) {
    var modules = bundle.modules;
    if (!modules) return;
    if (asset.type === 'css') reloadCSS();
    else if (asset.type === 'js') {
        let deps = asset.depsByBundle[bundle.HMR_BUNDLE_ID];
        if (deps) {
            if (modules[asset.id]) {
                // Remove dependencies that are removed and will become orphaned.
                // This is necessary so that if the asset is added back again, the cache is gone, and we prevent a full page reload.
                let oldDeps = modules[asset.id][1];
                for(let dep in oldDeps)if (!deps[dep] || deps[dep] !== oldDeps[dep]) {
                    let id = oldDeps[dep];
                    let parents = getParents(module.bundle.root, id);
                    if (parents.length === 1) hmrDelete(module.bundle.root, id);
                }
            }
            if (supportsSourceURL) // Global eval. We would use `new Function` here but browser
            // support for source maps is better with eval.
            (0, eval)(asset.output);
            // $FlowFixMe
            let fn = global.parcelHotUpdate[asset.id];
            modules[asset.id] = [
                fn,
                deps
            ];
        }
        // Always traverse to the parent bundle, even if we already replaced the asset in this bundle.
        // This is required in case modules are duplicated. We need to ensure all instances have the updated code.
        if (bundle.parent) hmrApply(bundle.parent, asset);
    }
}
function hmrDelete(bundle, id) {
    let modules = bundle.modules;
    if (!modules) return;
    if (modules[id]) {
        // Collect dependencies that will become orphaned when this module is deleted.
        let deps = modules[id][1];
        let orphans = [];
        for(let dep in deps){
            let parents = getParents(module.bundle.root, deps[dep]);
            if (parents.length === 1) orphans.push(deps[dep]);
        }
        // Delete the module. This must be done before deleting dependencies in case of circular dependencies.
        delete modules[id];
        delete bundle.cache[id];
        // Now delete the orphans.
        orphans.forEach((id)=>{
            hmrDelete(module.bundle.root, id);
        });
    } else if (bundle.parent) hmrDelete(bundle.parent, id);
}
function hmrAcceptCheck(bundle /*: ParcelRequire */ , id /*: string */ , depsByBundle /*: ?{ [string]: { [string]: string } }*/ ) {
    checkedAssets = {};
    if (hmrAcceptCheckOne(bundle, id, depsByBundle)) return true;
    // Traverse parents breadth first. All possible ancestries must accept the HMR update, or we'll reload.
    let parents = getParents(module.bundle.root, id);
    let accepted = false;
    while(parents.length > 0){
        let v = parents.shift();
        let a = hmrAcceptCheckOne(v[0], v[1], null);
        if (a) // If this parent accepts, stop traversing upward, but still consider siblings.
        accepted = true;
        else if (a !== null) {
            // Otherwise, queue the parents in the next level upward.
            let p = getParents(module.bundle.root, v[1]);
            if (p.length === 0) {
                // If there are no parents, then we've reached an entry without accepting. Reload.
                accepted = false;
                break;
            }
            parents.push(...p);
        }
    }
    return accepted;
}
function hmrAcceptCheckOne(bundle /*: ParcelRequire */ , id /*: string */ , depsByBundle /*: ?{ [string]: { [string]: string } }*/ ) {
    var modules = bundle.modules;
    if (!modules) return;
    if (depsByBundle && !depsByBundle[bundle.HMR_BUNDLE_ID]) {
        // If we reached the root bundle without finding where the asset should go,
        // there's nothing to do. Mark as "accepted" so we don't reload the page.
        if (!bundle.parent) {
            bundleNotFound = true;
            return true;
        }
        return hmrAcceptCheckOne(bundle.parent, id, depsByBundle);
    }
    if (checkedAssets[id]) return null;
    checkedAssets[id] = true;
    var cached = bundle.cache[id];
    if (!cached) return true;
    assetsToDispose.push([
        bundle,
        id
    ]);
    if (cached && cached.hot && cached.hot._acceptCallbacks.length) {
        assetsToAccept.push([
            bundle,
            id
        ]);
        return true;
    }
    return false;
}
function hmrDisposeQueue() {
    // Dispose all old assets.
    for(let i = 0; i < assetsToDispose.length; i++){
        let id = assetsToDispose[i][1];
        if (!disposedAssets[id]) {
            hmrDispose(assetsToDispose[i][0], id);
            disposedAssets[id] = true;
        }
    }
    assetsToDispose = [];
}
function hmrDispose(bundle /*: ParcelRequire */ , id /*: string */ ) {
    var cached = bundle.cache[id];
    bundle.hotData[id] = {};
    if (cached && cached.hot) cached.hot.data = bundle.hotData[id];
    if (cached && cached.hot && cached.hot._disposeCallbacks.length) cached.hot._disposeCallbacks.forEach(function(cb) {
        cb(bundle.hotData[id]);
    });
    delete bundle.cache[id];
}
function hmrAccept(bundle /*: ParcelRequire */ , id /*: string */ ) {
    // Execute the module.
    bundle(id);
    // Run the accept callbacks in the new version of the module.
    var cached = bundle.cache[id];
    if (cached && cached.hot && cached.hot._acceptCallbacks.length) {
        let assetsToAlsoAccept = [];
        cached.hot._acceptCallbacks.forEach(function(cb) {
            let additionalAssets = cb(function() {
                return getParents(module.bundle.root, id);
            });
            if (Array.isArray(additionalAssets) && additionalAssets.length) assetsToAlsoAccept.push(...additionalAssets);
        });
        if (assetsToAlsoAccept.length) {
            let handled = assetsToAlsoAccept.every(function(a) {
                return hmrAcceptCheck(a[0], a[1]);
            });
            if (!handled) return fullReload();
            hmrDisposeQueue();
        }
    }
}

},{}],"8eP6S":[function(require,module,exports,__globalThis) {
var _stateJs = require("../core/state.js");
var _mainMenuJs = require("../core/components/mainMenu.js");
window.__AIR_CONTROL_TEST_MODE = true;
(0, _stateJs.setState)('enableOdometer', true);
(0, _stateJs.setState)('enableRevisionWarning', true);
(0, _stateJs.setState)('odometer', 11450);
(0, _stateJs.setState)('nextRevisionKm', 12000);
(0, _stateJs.setState)('nextRevisionDate', Date.now() + 1296000000);
(0, _stateJs.setState)('tripAnalysisActive', true);
(0, _stateJs.setState)('tripAnalysisScore', 82);
const focusableAreas = {
    main_menu: (0, _mainMenuJs.menuItems).map((item)=>item.id),
    ac_control: [
        'fan',
        'temp'
    ],
    regen: [
        'Baixo',
        'Normal',
        'Alto'
    ],
    graph: [
        'evConsumption',
        'gasConsumption',
        'carSpeed'
    ],
    display_selection: [
        'title_mask',
        'mode_normal',
        'mode_reduzido',
        'mode_clean'
    ]
};
// If running under dev-controls (index.html), add a red background to help identify the environment
if (window.location.pathname.endsWith('index.html') || window.location.pathname === '/' || window.location.pathname.endsWith('/')) console.log('[Dev-Controls] Environment detected');
document.addEventListener('keydown', (e)=>{
    if (e.ctrlKey || e.altKey || e.metaKey) return;
    const currentState = (0, _stateJs.stateManager).getState();
    const currentScreen = currentState.screen;
    const currentCardId = currentState.cardId !== undefined ? currentState.cardId : 1;
    const cards = [
        0,
        1,
        3
    ];
    // If in Clean mode, any key (except modifiers already handled) restores Normal mode
    if (currentState.display === 'Clean') {
        console.log('[Clean Mode] Exit via key press:', e.key);
        window.control('display', 'Normal');
        return;
    }
    if (e.key.toLowerCase() === 'w') {
        const currentWarn = (0, _stateJs.stateManager).get('warningActive');
        console.log('[Warning Debug] Toggling warningActive to:', !currentWarn);
        if (window.updateWarning) window.updateWarning('fake.warning', !currentWarn ? '1' : '0');
        else (0, _stateJs.setState)('warningActive', !currentWarn);
        // If we are activating warning, hide cards
        if (!currentWarn) (0, _stateJs.setState)('cardId', 0);
        else (0, _stateJs.setState)('cardId', 1);
        return;
    }
    if (e.key.toLowerCase() === 'l') {
        const current = (0, _stateJs.stateManager).get('bsdLeft');
        console.log('[BSD Debug] Toggling Left BSD to:', !current);
        if (window.updateWarning) window.updateWarning('car.ipk_info.bsd_lca_warning_reqleft', !current ? '1' : '0');
        else (0, _stateJs.setState)('bsdLeft', !current);
        return;
    }
    if (e.key.toLowerCase() === 'r') {
        const current = (0, _stateJs.stateManager).get('bsdRight');
        console.log('[BSD Debug] Toggling Right BSD to:', !current);
        if (window.updateWarning) window.updateWarning('car.ipk_info.bsd_lca_warning_reqright', !current ? '1' : '0');
        else (0, _stateJs.setState)('bsdRight', !current);
        return;
    }
    if (e.key === 'Escape') {
        console.log('[Warning Debug] Force clear warningActive');
        (0, _stateJs.setState)('warningActive', false);
    }
    if (e.key === 'ArrowRight') {
        const currentIndex = cards.indexOf(currentCardId);
        const nextIndex = (currentIndex + 1) % cards.length;
        const targetCard = cards[nextIndex];
        const cardMeaning = {
            0: 'Hide Menu',
            1: 'Main Menu',
            3: 'AC Menu'
        };
        console.log(`[Card Simulation] Cycle Up -> Card ${targetCard} (${cardMeaning[targetCard]})`);
        (0, _stateJs.setState)('cardId', targetCard);
        return;
    }
    if (e.key === 'ArrowLeft') {
        const currentIndex = cards.indexOf(currentCardId);
        const prevIndex = (currentIndex - 1 + cards.length) % cards.length;
        const targetCard = cards[prevIndex];
        const cardMeaning = {
            0: 'Hide Menu',
            1: 'Main Menu',
            3: 'AC Menu'
        };
        console.log(`[Card Simulation] Cycle Down -> Card ${targetCard} (${cardMeaning[targetCard]})`);
        (0, _stateJs.setState)('cardId', targetCard);
        return;
    }
    if (e.key === 'Backspace') {
        if (currentScreen !== 'main_menu') window.showScreen('main_menu');
        return;
    }
    if (currentScreen === 'main_menu') {
        const menuItems = focusableAreas.main_menu;
        const currentIndex = menuItems.indexOf(currentState.focusedMenuItem);
        if (e.key === 'ArrowUp') {
            const prevIndex = (currentIndex - 1 + menuItems.length) % menuItems.length;
            window.focus(menuItems[prevIndex]);
        } else if (e.key === 'ArrowDown') {
            const nextIndex = (currentIndex + 1) % menuItems.length;
            window.focus(menuItems[nextIndex]);
        } else if (e.key === 'Enter') {
            if (currentState.focusedMenuItem === 'option_1') {
                const currentStatus = (0, _stateJs.stateManager).getState().espStatus;
                const newStatus = currentStatus === 'ON' ? 'OFF' : 'ON';
                (0, _stateJs.setState)('espStatus', newStatus);
            } else if (currentState.focusedMenuItem === 'option_2') {
                const modes = [
                    'EV',
                    'EVP',
                    'HEV'
                ];
                const currentMode = (0, _stateJs.stateManager).getState().evMode;
                const currentIndex = modes.indexOf(currentMode);
                const nextIndex = (currentIndex + 1) % modes.length;
                const newMode = modes[nextIndex];
                (0, _stateJs.setState)('evMode', newMode);
            } else if (currentState.focusedMenuItem === 'option_3') {
                const modes = [
                    'Normal',
                    'Eco',
                    'Sport'
                ];
                const currentMode = (0, _stateJs.stateManager).getState().drivingMode;
                const currentIndex = modes.indexOf(currentMode);
                const nextIndex = (currentIndex + 1) % modes.length;
                const newMode = modes[nextIndex];
                (0, _stateJs.setState)('drivingMode', newMode);
            } else if (currentState.focusedMenuItem === 'option_4') window.showScreen('display_selection');
            else if (currentState.focusedMenuItem === 'option_5') {
                const modes = [
                    'Normal',
                    'Conforto',
                    'Esportiva'
                ];
                const currentMode = (0, _stateJs.stateManager).getState().steerMode;
                const currentIndex = modes.indexOf(currentMode);
                const nextIndex = (currentIndex + 1) % modes.length;
                const newMode = modes[nextIndex];
                (0, _stateJs.setState)('steerMode', newMode);
            } else if (currentState.focusedMenuItem === 'option_6') window.showScreen('regen');
            else if (currentState.focusedMenuItem === 'option_7') window.showScreen('graph');
        }
    } else if (currentScreen === 'aircon') {
        const focusedArea = currentState.focusArea;
        if (e.key === 'Enter') {
            if (currentState.impulseauto == 1) window.focus('temp');
            else {
                const controls = focusableAreas.ac_control;
                const currentIndex = controls.indexOf(focusedArea);
                const nextIndex = (currentIndex + 1) % controls.length;
                window.focus(controls[nextIndex]);
            }
        } else if (e.key === ' ') {
            e.preventDefault();
            const newAutoModeState = currentState.auto == 0 ? 1 : 0;
            (0, _stateJs.setState)('auto', newAutoModeState);
        } else if (e.key === 'a') {
            e.preventDefault();
            const newModeState = currentState.maxauto == 0 ? 1 : 0;
            (0, _stateJs.setState)('maxauto', newModeState);
        }
        switch(focusedArea){
            case 'fan':
                const currentFan = parseInt(currentState.fan, 10) || 0;
                if (e.key === 'ArrowUp' && currentFan < 7) window.control('fan', String(currentFan + 1));
                else if (e.key === 'ArrowDown' && currentFan > 0) window.control('fan', String(currentFan - 1));
                break;
            case 'temp':
                if (currentState.impulseauto == 1) {
                    const currentTargetTemp = parseFloat(currentState.targetTemp) || 21.0;
                    if (e.key === 'ArrowUp' && currentTargetTemp < 32.0) window.control('targetTemp', (currentTargetTemp + 0.5).toFixed(1));
                    else if (e.key === 'ArrowDown' && currentTargetTemp > 16.0) window.control('targetTemp', (currentTargetTemp - 0.5).toFixed(1));
                } else {
                    const currentTemp = parseFloat(currentState.temp) || 21.0;
                    if (e.key === 'ArrowUp' && currentTemp < 32.0) window.control('temp', (currentTemp + 0.5).toFixed(1));
                    else if (e.key === 'ArrowDown' && currentTemp > 16.0) window.control('temp', (currentTemp - 0.5).toFixed(1));
                }
                break;
            default:
                break;
        }
    } else if (currentScreen === 'regen') {
        const regenMode = currentState.regenMode;
        if (e.key === 'Enter') {
            const nextValue = !currentState.onepedal;
            console.log(`[Regen Simulation] Toggle onepedal via Enter -> ${nextValue}`);
            window.control('onepedal', nextValue);
        } else if (e.key === 'ArrowUp') {
            const controls = focusableAreas.regen;
            const currentIndex = controls.indexOf(regenMode);
            const nextIndex = (currentIndex + 1) % controls.length;
            window.control('regenMode', controls[nextIndex]);
        } else if (e.key === 'ArrowDown') {
            const controls = focusableAreas.regen;
            const currentIndex = controls.indexOf(regenMode);
            const prevIndex = (currentIndex - 1 + controls.length) % controls.length;
            window.control('regenMode', controls[prevIndex]);
        }
    } else if (currentScreen === 'graph') {
        const currentGraph = currentState.currentGraph;
        if (e.key === 'Enter' || e.key === 'ArrowDown') {
            const controls = focusableAreas.graph;
            const currentIndex = controls.indexOf(currentGraph);
            const nextIndex = (currentIndex + 1) % controls.length;
            window.control('currentGraph', controls[nextIndex]);
        } else if (e.key === 'ArrowUp') {
            const controls = focusableAreas.graph;
            const currentIndex = controls.indexOf(currentGraph);
            const prevIndex = (currentIndex - 1 + controls.length) % controls.length;
            window.control('currentGraph', controls[prevIndex]);
        }
    } else if (currentScreen === 'display_selection') {
        const controls = focusableAreas.display_selection;
        const currentFocus = currentState.displayFocus || 'mode_normal';
        const currentIndex = Math.max(0, controls.indexOf(currentFocus));
        if (e.key === 'ArrowUp') {
            let prevIndex = (currentIndex - 1 + controls.length) % controls.length;
            // Skip title
            if (controls[prevIndex] === 'title_mask') prevIndex = (prevIndex - 1 + controls.length) % controls.length;
            window.focus(controls[prevIndex]);
        } else if (e.key === 'ArrowDown') {
            let nextIndex = (currentIndex + 1) % controls.length;
            // Skip title
            if (controls[nextIndex] === 'title_mask') nextIndex = (nextIndex + 1) % controls.length;
            window.focus(controls[nextIndex]);
        } else if (e.key === 'Enter') {
            if (currentFocus.startsWith('mode_')) {
                const newDisplay = currentFocus.replace('mode_', '');
                const formattedDisplay = newDisplay.charAt(0).toUpperCase() + newDisplay.slice(1);
                window.control('display', formattedDisplay);
                if (window.Android && window.Android.saveSetting) window.Android.saveSetting('currentClusterDisplay', formattedDisplay);
            }
        }
    }
    if (e.key === 'g' || e.key === 'G') {
        const gears = [
            'P',
            'R',
            'N',
            'D'
        ];
        const currentGear = (0, _stateJs.stateManager).getState().gearState;
        const currentIndex = gears.indexOf(currentGear);
        const nextIndex = (currentIndex + 1) % gears.length;
        (0, _stateJs.setState)('gearState', gears[nextIndex]);
    }
    if (e.key.toLowerCase() === 'k') {
        const options = [
            false,
            true,
            'left',
            'right'
        ];
        const currentAppInDash = (0, _stateJs.stateManager).getState().appInDash;
        let currentIndex = options.indexOf(currentAppInDash);
        if (currentIndex === -1) currentIndex = 0;
        const nextIndex = (currentIndex + 1) % options.length;
        const nextValue = options[nextIndex];
        console.log(`[Mask Simulation] Cycle appInDash -> ${nextValue}`);
        (0, _stateJs.setState)('appInDash', nextValue);
    }
    if (e.key.toLowerCase() === 'c') {
        const current = (0, _stateJs.stateManager).getState().clusterEnabled;
        console.log(`[Cluster Simulation] Toggling clusterEnabled to: ${!current}`);
        (0, _stateJs.setState)('clusterEnabled', !current);
    }
    if (e.key.toLowerCase() === 'o') {
        const currentOnePedal = (0, _stateJs.stateManager).getState().onepedal;
        console.log(`[Mode Simulation] Toggle onepedal -> ${!currentOnePedal}`);
        (0, _stateJs.setState)('onepedal', !currentOnePedal);
    }
    if (e.key === '6') {
        const current = (0, _stateJs.stateManager).get('enableOdometer');
        console.log(`[Testing] Toggling enableOdometer to: ${!current}`);
        (0, _stateJs.setState)('enableOdometer', !current);
    }
    if (e.key === '7') {
        const current = (0, _stateJs.stateManager).get('enableRevisionWarning');
        console.log(`[Testing] Toggling enableRevisionWarning to: ${!current}`);
        (0, _stateJs.setState)('enableRevisionWarning', !current);
    }
    if (e.key.toLowerCase() === 'm') {
        const modes = [
            'km',
            'date',
            'none'
        ];
        if (!window.maintenanceMode) window.maintenanceMode = 'km';
        const currentIndex = modes.indexOf(window.maintenanceMode);
        const nextIndex = (currentIndex + 1) % modes.length;
        window.maintenanceMode = modes[nextIndex];
        console.log(`[Maintenance Simulation] Toggle Mode -> ${window.maintenanceMode}`);
        if (window.maintenanceMode === 'none') {
            (0, _stateJs.setState)('enableRevisionWarning', false);
            (0, _stateJs.setState)('nextRevisionKm', 999999);
            (0, _stateJs.setState)('nextRevisionDate', 0);
        } else if (window.maintenanceMode === 'km') {
            (0, _stateJs.setState)('enableRevisionWarning', true);
            (0, _stateJs.setState)('nextRevisionKm', 12000);
            (0, _stateJs.setState)('nextRevisionDate', Date.now() + 5184000000);
        } else if (window.maintenanceMode === 'date') {
            (0, _stateJs.setState)('enableRevisionWarning', true);
            (0, _stateJs.setState)('nextRevisionKm', 20000);
            (0, _stateJs.setState)('nextRevisionDate', Date.now() + 1296000000);
        }
    }
});
let lastValue = 0;
const smoothingFactor = 0.05; // Less dramatic changes
let timeToModeChange = 10;
let simulationPhase = 'idle';
let currentSpeed = 0;
let steadyTimeCounter = 0;
const SIMULATION_INTERVAL = 100;
// Fuel and Battery Animation Constants
const MAX_FUEL_RANGE = 700;
const MAX_BATTERY_RANGE = 170;
const DECREASE_TIME_MS = 30000;
const INCREASE_TIME_MS = 5000;
let fuelBatteryPhase = 'decreasing';
let animationTimeCounter = 0;
if (window.simulationInterval) clearInterval(window.simulationInterval);
window.simulationInterval = setInterval(()=>{
    switch(simulationPhase){
        case 'accelerating':
            if (currentSpeed < 150) currentSpeed += 2.0;
            else {
                currentSpeed = 150;
                simulationPhase = 'decelerating';
            }
            break;
        case 'decelerating':
            if (currentSpeed > 20) currentSpeed -= 5;
            else {
                currentSpeed = 20;
                simulationPhase = 'steady';
                steadyTimeCounter = 0;
            }
            break;
        case 'steady':
            const STEADY_DURATION_MS = 1000;
            if (steadyTimeCounter * SIMULATION_INTERVAL < STEADY_DURATION_MS) steadyTimeCounter++;
            else simulationPhase = 'stopping';
            break;
        case 'stopping':
            if (currentSpeed > 0) currentSpeed -= 1;
            else {
                currentSpeed = 0;
                simulationPhase = 'idle';
                setTimeout(()=>{
                    simulationPhase = 'accelerating';
                }, 5000);
            }
            break;
        case 'idle':
        default:
            break;
    }
    (0, _stateJs.setState)('carSpeed', Math.max(0, currentSpeed.toFixed(1)));
    (0, _stateJs.setState)('tripAnalysisScore', Math.max(74, Math.min(99, Math.round(88 - lastValue / 12 + currentSpeed / 30))));
    const randomTarget = Math.floor(Math.random() * 101);
    lastValue = lastValue * (1 - smoothingFactor) + randomTarget * smoothingFactor;
    timeToModeChange--;
    if (timeToModeChange <= 0) {
        const currentMode = (0, _stateJs.stateManager).getState().gasConsumptionMode;
        const newMode = currentMode === 'Running' ? 'Idle' : 'Running';
        (0, _stateJs.setState)('gasConsumptionMode', newMode);
        timeToModeChange = Math.floor(Math.random() * 100) + 50;
    }
    const currentMode = (0, _stateJs.stateManager).getState().gasConsumptionMode;
    if (currentMode === 'Running') {
        const gasV = Math.round(lastValue) / 3;
        (0, _stateJs.setState)('gasConsumption', gasV);
        (0, _stateJs.setState)('gasConsumptionIdle', 0);
        // Simulate RPM: if running AND speed > 0, it should be between 800 and 7000
        // Force 0 if speed is 0
        const playsRPM = currentSpeed > 0;
        const simulatedRPM = playsRPM ? 1000 + currentSpeed * 40 + Math.random() * 500 : 0;
        (0, _stateJs.setState)('engineRPM', Math.min(Math.max(simulatedRPM, 0), 7000));
    } else {
        (0, _stateJs.setState)('gasConsumption', 0);
        (0, _stateJs.setState)('gasConsumptionIdle', Math.round(lastValue) / 20);
        // If idle, RPM is 800 but ONLY if speed > 0
        const idleRPM = currentSpeed > 0 ? 800 : 0;
        (0, _stateJs.setState)('engineRPM', idleRPM);
    }
    // Simulate EV power factor: -100 to +100 % (for power ring)
    const powerFactor = Math.round(lastValue * 2) - 100;
    (0, _stateJs.setState)('evPowerFactor', powerFactor * 2);
    // Simulate EV power in kW: ±120 kW range (for graph)
    if (powerFactor > 0) (0, _stateJs.setState)('evPowerKw', Math.round(powerFactor * 4 * Math.abs(currentSpeed) / 100));
    else (0, _stateJs.setState)('evPowerKw', Math.round(powerFactor));
    (0, _stateJs.setState)('lastRegenValue', Math.round(lastValue));
    // Fuel and Battery Animation Logic
    animationTimeCounter += SIMULATION_INTERVAL;
    let percent = 100;
    if (fuelBatteryPhase === 'decreasing') {
        percent = 100 - animationTimeCounter / DECREASE_TIME_MS * 100;
        if (animationTimeCounter >= DECREASE_TIME_MS) {
            percent = 0;
            fuelBatteryPhase = 'increasing';
            animationTimeCounter = 0;
        }
    } else {
        percent = animationTimeCounter / INCREASE_TIME_MS * 100;
        if (animationTimeCounter >= INCREASE_TIME_MS) {
            percent = 100;
            fuelBatteryPhase = 'decreasing';
            animationTimeCounter = 0;
        }
    }
    const currentFuelPercent = Math.max(0, Math.min(100, Math.round(percent)));
    const currentBatteryPercent = Math.max(0, Math.min(100, Math.round(percent)));
    (0, _stateJs.setState)('fuelPercent', currentFuelPercent);
    (0, _stateJs.setState)('batteryPercent', currentBatteryPercent);
    (0, _stateJs.setState)('fuelRange', Math.round(currentFuelPercent / 100 * MAX_FUEL_RANGE));
    (0, _stateJs.setState)('batteryRange', Math.round(currentBatteryPercent / 100 * MAX_BATTERY_RANGE));
    // Odometer Simulation
    if (!window.simulatedOdo) window.simulatedOdo = 11450.5; // Start near revision
    if (currentSpeed > 0) {
        // km/h to km/step: (speed * interval_ms) / (1000 * 3600)
        const delta = currentSpeed * SIMULATION_INTERVAL / 3600000;
        window.simulatedOdo += delta;
    }
    (0, _stateJs.setState)('odometer', Math.floor(window.simulatedOdo));
}, SIMULATION_INTERVAL);
setTimeout(()=>{
    simulationPhase = 'accelerating';
}, 5000);

},{"../core/state.js":"kQl0o","../core/components/mainMenu.js":"91EIF"}]},["75BAb"], null, "parcelRequirede8c", {})

//# sourceMappingURL=testing-utils.fd4bfff4.js.map
