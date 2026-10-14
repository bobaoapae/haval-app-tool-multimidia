/* Classical worker entry. Packager embeds the same offline renderer dependencies. */
// STARLIGHT_DEV_IMPORTS_START
if (typeof globalThis === 'undefined') self.globalThis = self;
importScripts('apex-data.js','scales.js','galaxy-stars.js','glass-smoke.js','renderer.js');
// STARLIGHT_DEV_IMPORTS_END
(function () {
  'use strict';
  var canvas = null, context = null, layer = null, generation = 0;
  var painter = self.MotionStudy;
  function error(reason) { self.postMessage({type:'error',reason:reason,generation:generation}); }
  self.onmessage = function (event) {
    var data = event.data || {};
    if (data.type === 'probe') {
      generation = data.generation; layer = data.layer;
      try {
        if (typeof OffscreenCanvas !== 'function' || !painter || typeof painter.renderBackground !== 'function' || typeof painter.renderBeams !== 'function') throw new Error('Missing worker Canvas2D');
        var probe = new OffscreenCanvas(1,1), probeContext = probe.getContext('2d');
        if (!probeContext || typeof probe.transferToImageBitmap !== 'function') throw new Error('Missing transferable Canvas2D');
        probeContext.fillRect(0,0,1,1);
        var bitmap = probe.transferToImageBitmap();
        painter.setCanvasFactory(function (width,height) { return new OffscreenCanvas(width,height); });
        self.postMessage({type:'ready',generation:generation,layer:layer,probeBitmap:bitmap},[bitmap]);
      } catch (failure) { self.postMessage({type:'unsupported',reason:'canvas2d-probe-failed',generation:generation}); }
    } else if (data.type === 'bind') {
      try {
        generation = data.generation; layer = data.layer; canvas = data.canvas; context = canvas.getContext('2d');
        if (!context) throw new Error('Missing layer Canvas2D');
        self.postMessage({type:'bound',generation:generation,layer:layer});
      } catch (failure) { error('canvas2d-bind-failed'); }
    } else if (data.type === 'clear') {
      generation = Math.max(generation,data.generation);
      if (context) context.clearRect(0,0,1920,720);
    } else if (data.type === 'frame') {
      if (!context || data.generation < generation) {
        self.postMessage({type:'frame',generation:data.generation,sequence:data.sequence,painted:false,costMs:0}); return;
      }
      generation = data.generation;
      var start = performance.now();
      try {
        var draw = layer === 'background' ? painter.renderBackground : painter.renderBeams;
        draw(context,data.time,'live',{state:data.state,starMotion:data.starMotion,menuState:data.menuState});
        self.postMessage({type:'frame',generation:generation,sequence:data.sequence,painted:true,costMs:performance.now()-start});
      } catch (failure) { error('render-failed'); }
    }
  };
}());
