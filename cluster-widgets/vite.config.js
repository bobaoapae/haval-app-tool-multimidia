import { defineConfig } from 'vite';
import { fileURLToPath } from 'node:url';
import fs from 'node:fs';
import path from 'node:path';
import { createThemeCatalog } from './dev/theme-catalog.mjs';
import {
  isSportGaugeMotionPath,
  pauseHiddenSportCanvas,
  tuneSportGaugeMotion,
} from './dev/sport-gauge-motion.mjs';

const clusterWidgetsRoot = path.dirname(fileURLToPath(import.meta.url));

function themeCatalogPlugin() {
  return {
    name: 'haval-theme-catalog',
    configureServer(server) {
      server.middlewares.use((request, response, next) => {
        const pathname = new URL(request.url || '/', 'http://localhost').pathname;
        if (pathname !== '/__theme_catalog') {
          next();
          return;
        }

        try {
          const themes = createThemeCatalog(clusterWidgetsRoot);
          response.statusCode = 200;
          response.setHeader('Content-Type', 'application/json; charset=utf-8');
          response.setHeader('Cache-Control', 'no-store');
          response.end(JSON.stringify({ themes }));
        } catch (error) {
          response.statusCode = 500;
          response.setHeader('Content-Type', 'application/json; charset=utf-8');
          response.end(JSON.stringify({ error: error.message }));
        }
      });
    },
  };
}

function sportGaugeMotionPreviewPlugin() {
  return {
    name: 'haval-sport-gauge-motion-preview',
    enforce: 'pre',
    configureServer(server) {
      server.middlewares.use((request, response, next) => {
        const pathname = new URL(request.url || '/', 'http://localhost').pathname;
        if (!isSportGaugeMotionPath(pathname)) {
          next();
          return;
        }

        const sourcePath = path.join(clusterWidgetsRoot, pathname);
        const motionResult = tuneSportGaugeMotion(fs.readFileSync(sourcePath, 'utf8'));
        const canvasResult = pauseHiddenSportCanvas(motionResult.source);
        if (!motionResult.tuned || !canvasResult.paused) {
          response.statusCode = 409;
          response.setHeader('Content-Type', 'text/plain; charset=utf-8');
          response.end('Bundle Sport incompatível com o ajuste de movimento esperado.');
          return;
        }

        response.statusCode = 200;
        response.setHeader('Content-Type', 'text/html; charset=utf-8');
        response.setHeader('Cache-Control', 'no-store');
        response.end(canvasResult.source);
      });
    },
  };
}

function themeAbsoluteImportCompatibilityPlugin() {
  const minimalistStyle = path.join(
    clusterWidgetsRoot,
    'source',
    'v1.0',
    'minimalist',
    'src',
    'styles',
    'night.style.css',
  );
  return {
    name: 'haval-theme-absolute-import-compatibility',
    enforce: 'pre',
    transform(source, id) {
      if (id.split('?')[0] === minimalistStyle) {
        return {
          code: source.replace('@import "/src/fonts/fonts.css";', '@import "../fonts/fonts.css";'),
          map: null,
        };
      }
      return null;
    },
  };
}

export default defineConfig({
  root: clusterWidgetsRoot,
  appType: 'mpa',
  plugins: [
    themeAbsoluteImportCompatibilityPlugin(),
    sportGaugeMotionPreviewPlugin(),
    themeCatalogPlugin(),
  ],
  server: {
    strictPort: true,
    fs: {
      allow: [clusterWidgetsRoot],
    },
  },
  // The repository intentionally keeps several independent theme packages below this
  // root. Let each requested theme resolve dependencies from its own node_modules instead
  // of asking Vite to crawl every historical/noncontract HTML entry at startup.
  optimizeDeps: {
    noDiscovery: true,
  },
  build: {
    target: 'chrome80',
  },
});
