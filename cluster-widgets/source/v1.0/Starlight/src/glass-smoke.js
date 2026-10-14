/* Independently coded Canvas2D interior material. No source-video pixels are used.
 * The fixed 0..768 domain is revealed by the glass frontier; changing length never
 * stretches or accelerates the density field. Cached periodic textures are only
 * advected/composited at frame time, so seeks do not depend on playback history.
 */
(function(root,factory){
  const api=factory();
  if(typeof module==='object' && module.exports) module.exports=api;
  else root.GlassSmoke=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(){
  'use strict';
  const WIDTH=768, TOP=132, BOTTOM=60, HEIGHT=TOP+BOTTOM;
  const TEX_W=512,TEX_H=160,SCALE=2,TAU=Math.PI*2;
  const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
  const smooth=(a,b,v)=>{const q=clamp((v-a)/(b-a),0,1);return q*q*(3-2*q);};
  const mod=(v,n)=>((v%n)+n)%n;
  const finite=(v,fallback)=>Number.isFinite(v)?v:fallback;
  function hash(x,y,seed){let n=Math.imul(x+seed,374761393)^Math.imul(y+11,668265263);n=Math.imul(n^(n>>>13),1274126177);return ((n^(n>>>16))>>>0)/4294967295;}
  // Quintic periodic value noise has no visible seams in either tiled direction.
  function noise(x,y,nx,ny,seed){
    x=mod(x,nx);y=mod(y,ny);
    const ix=Math.floor(x),iy=Math.floor(y),a=x-ix,b=y-iy;
    const sx=a*a*a*(a*(a*6-15)+10),sy=b*b*b*(b*(b*6-15)+10);
    const aa=hash(ix,iy,seed),ba=hash((ix+1)%nx,iy,seed);
    const ab=hash(ix,(iy+1)%ny,seed),bb=hash((ix+1)%nx,(iy+1)%ny,seed);
    return (aa+(ba-aa)*sx)*(1-sy)+(ab+(bb-ab)*sx)*sy;
  }
  const WISP_GUARD=2;
  const wispSteps=Array.from({length:22},(_,j)=>{
    const s=j/21;
    return {s,head:s*s*s,sway:3+9*s,angle:s*5.5,arc:s*TAU*1.25,
      widthFactor:1-.55*s,heightBrush:7.5-3*s,
      fade:Math.pow(Math.sin(Math.PI*(s*.91+.035)),.8),slope:1-s*.65,
      shoulder:1.8+.65*smooth(.15,.8,s)};
  });
  const wispSeeds=Array.from({length:19},(_,k)=>({
    phase:hash(k,5,813)*TAU,u:18+k*40+hash(k,9,379)*25,
    height:20+hash(k,12,719)*68,width:12+hash(k,17,333)*10,
    opacity:.65+.35*hash(k,26,923)
  }));
  function createRenderer(canvasFactory){
    let factory=canvasFactory||null,cache=null;
    const counts={'-1':{nominal:0,drawn:0,culled:0,computed:0},'1':{nominal:0,drawn:0,culled:0,computed:0}};
    function stats(){return {left:Object.assign({},counts['-1']),right:Object.assign({},counts['1'])};}
    function make(w,h){
      if(factory)return factory(w,h);
      if(typeof OffscreenCanvas!=='undefined')return new OffscreenCanvas(w,h);
      if(typeof document!=='undefined'){const c=document.createElement('canvas');c.width=w;c.height=h;return c;}
      throw Error('GlassSmoke needs setCanvasFactory(createCanvas) outside a browser');
    }
    function texture(seed){
      const c=make(TEX_W,TEX_H),ctx=c.getContext('2d'),im=ctx.createImageData(TEX_W,TEX_H);
      for(let y=0;y<TEX_H;y++)for(let x=0;x<TEX_W;x++){
        const u=x/TEX_W,v=y/TEX_H;
        // Narrow anisotropic cells bend into rising strands instead of 100px blobs.
        const warpX=(noise(u*7,v*7,7,7,seed+13)-.5)*.047
          +Math.sin(v*TAU*3+noise(u*8,v*4,8,4,seed+31)*4)*.008;
        const warpY=(noise(u*8,v*5,8,5,seed+29)-.5)*.052;
        const a=noise((u+warpX)*25,(v+warpY)*7,25,7,seed);
        const b=noise((u+warpX*.65)*53,(v+warpY)*17,53,17,seed+3);
        const d=noise((u+warpX*.40)*97,(v+warpY*.6)*31,97,31,seed+7);
        const body=Math.pow(smooth(.35,.94,a*.72+b*.22+d*.06),1.7);
        const filament=Math.exp(-Math.pow((a-.55)/.085,2))*smooth(.36,.74,b);
        const density=clamp(body*.68+filament*.18,0,1);
        const i=(y*TEX_W+x)*4;im.data[i]=im.data[i+1]=im.data[i+2]=255;im.data[i+3]=Math.round(density*255);
      }
      ctx.putImageData(im,0,0);return c;
    }
    function wispBrush(){
      const c=make(48,48),ctx=c.getContext('2d');
      const g=ctx.createRadialGradient(24,24,0,24,24,24);
      g.addColorStop(0,'rgba(255,255,255,.9)');g.addColorStop(.24,'rgba(255,255,255,.65)');
      g.addColorStop(.6,'rgba(255,255,255,.17)');g.addColorStop(1,'rgba(255,255,255,0)');
      ctx.fillStyle=g;ctx.fillRect(0,0,48,48);return c;
    }
    function drawWisps(ctx,brush,time,side,length){
      // Fixed-domain, continuously deforming translucent ribbons. Their curved
      // heads lift from the floor and dissolve; they never become exterior smoke.
      const phase=side===-1?0:2.37;
      const record=counts[side];record.nominal=19*22;record.drawn=record.culled=record.computed=0;
      for(const seed of wispSeeds){
        if(seed.u-22-seed.width>length+WISP_GUARD||seed.u+22+seed.width<-WISP_GUARD){record.culled+=22;continue;}
        const p=seed.phase+phase,u=seed.u;
        const height=seed.height*(.87+.13*Math.sin(time*.37+p));
        const breath=.50+.50*Math.sin(time*.29+p*1.73);
        for(const step of wispSteps){
          const s=step.s,head=step.head,width=seed.width*step.widthFactor,heightBrush=step.heightBrush;
          const bound=step.sway+head*10+width+1e-9;
          if(u-bound>length+WISP_GUARD||u+bound<-WISP_GUARD){record.culled++;continue;}
          record.computed++;
          const x=u+Math.sin(step.angle+p+time*.27)*step.sway
            +head*Math.sin(step.arc+p*.6+time*.21)*10;
          const y=TOP-s*height+head*Math.cos(step.arc+p*.6+time*.21)*9;
          if(x-width>length+WISP_GUARD||x+width<-WISP_GUARD||y-heightBrush>HEIGHT+WISP_GUARD||y+heightBrush<-WISP_GUARD){record.culled++;continue;}
          const opacity=(.038+.077*breath)*seed.opacity*step.fade*step.slope*step.shoulder;
          ctx.globalAlpha=opacity;
          ctx.drawImage(brush,x-width,y-heightBrush,width*2,heightBrush*2);
          record.drawn++;
        }
      }
      ctx.globalAlpha=1;
    }
    function init(){
      if(cache)return cache;
      const mask=make(WIDTH,HEIGHT),mc=mask.getContext('2d'),mi=mc.createImageData(WIDTH,HEIGHT);
      for(let y=0;y<HEIGHT;y++)for(let x=0;x<WIDTH;x++){
        const v=y-TOP,upper=80+x*.055;
        const inner=smooth(0,46,x),top=smooth(-upper,-upper*.57,v);
        const density=v<=0
          ? (.81*Math.exp(-Math.pow((v+13)/38,2))+.21*Math.exp(-Math.pow((v+38)/62,2)))*top
          : .28*Math.exp(-v/15.5)*(1-smooth(24,58,v));
        const i=(y*WIDTH+x)*4;mi.data[i]=mi.data[i+1]=mi.data[i+2]=255;mi.data[i+3]=Math.round(clamp(density*inner*.77,0,1)*255);
      }
      mc.putImageData(mi,0,0);
      // Separate mutable staging surfaces prevent deferred native drawImage calls
      // for the left wing from sampling the subsequently recolored right wing.
      cache={mask,brush:wispBrush(),textures:[texture(117),texture(541),texture(997)],buffers:{'-1':make(WIDTH,HEIGHT),'1':make(WIDTH,HEIGHT)}};
      return cache;
    }
    function tiles(ctx,texture,dx,dy,opacity){
      const w=TEX_W*SCALE,h=TEX_H*SCALE;
      const startX=-mod(dx,w),startY=-mod(dy,h);
      ctx.globalAlpha=opacity;
      for(let x=startX;x<WIDTH;x+=w)for(let y=startY;y<HEIGHT;y+=h)ctx.drawImage(texture,x,y,w,h);
    }
    function render(ctx,options){
      options=options||{};
      const side=options.side===-1?-1:1;
      const length=clamp(finite(options.length,0),0,WIDTH);
      const opacity=clamp(finite(options.alpha,1),0,1);
      if(!ctx||length<=0||opacity<=0)return;
      const time=finite(options.time,0),green=!!options.green,heat=green?0:clamp(finite(options.heat,0),0,1);
      const c=init(),buffer=c.buffers[side],b=buffer.getContext('2d');
      b.save();b.setTransform(1,0,0,1,0,0);b.globalCompositeOperation='source-over';b.globalAlpha=1;
      b.clearRect(0,0,WIDTH,HEIGHT);
      b.beginPath();b.rect(0,0,Math.min(WIDTH,length+WISP_GUARD),HEIGHT);b.clip();
      // One dominant field preserves clear gaps; two faint layers gently evolve lobes.
      // Equal-weight layers would fill those gaps and flatten the material into haze.
      // Position comes from absolute time, never accumulated delta-time.
      const phase=side===-1?0:173;
      const tt=mod(time,1048576);
      tiles(b,c.textures[0],tt*7.1+phase,tt*2.5+30,.41);
      tiles(b,c.textures[1],-tt*4.7+phase*.67+215,-tt*1.7+81,.12);
      tiles(b,c.textures[2],tt*2.4-phase*.31+49,tt*1.15+160,.065);
      drawWisps(b,c.brush,tt,side,length);
      b.globalAlpha=1;b.globalCompositeOperation='destination-in';b.drawImage(c.mask,0,0);
      b.globalCompositeOperation='source-in';
      b.fillStyle=green?'rgb(133,187,158)':'rgb(180,200,227)';b.fillRect(0,0,WIDTH,HEIGHT);
      // A restrained warm tint is restricted to the existing outer frontier region.
      // heat is supplied by the caller; this module invents no speed/telemetry trigger.
      if(heat>0){
        b.globalCompositeOperation='source-atop';b.globalAlpha=heat*.72;
        const start=Math.max(0,length-148),g=b.createLinearGradient(start,0,length,0);
        g.addColorStop(0,'rgba(205,111,161,0)');g.addColorStop(.55,'rgba(223,118,160,.28)');g.addColorStop(1,'rgba(235,118,148,.72)');
        b.fillStyle=g;b.fillRect(start,0,length-start,HEIGHT);
      }
      b.restore();
      ctx.save();ctx.globalAlpha*=opacity;ctx.translate(960+side*180,380);ctx.transform(side,.35,0,1,0,0);
      // End face matches the glass slab. The high interior stays almost transparent.
      ctx.beginPath();ctx.moveTo(0,-TOP);ctx.lineTo(length,-TOP);ctx.lineTo(length,0);
      ctx.lineTo(Math.max(0,length-10),58);ctx.lineTo(0,31);ctx.closePath();ctx.clip();
      ctx.imageSmoothingEnabled=true;ctx.drawImage(buffer,0,-TOP);ctx.restore();
    }
    function cleanup(){
      for(const side of [-1,1]){const c=counts[side];c.nominal=c.drawn=c.culled=c.computed=0;}
      if(!cache)return;
      const surfaces=[cache.mask,cache.brush].concat(cache.textures,[cache.buffers['-1'],cache.buffers['1']]);
      for(const surface of surfaces){surface.width=1;surface.height=1;}
      cache=null;
    }
    function setCanvasFactory(f){if(f!=null&&typeof f!=='function')throw TypeError('Canvas factory must be a function');cleanup();factory=f||null;}
    return {render,setCanvasFactory,cleanup,stats};
  }
  const shared=createRenderer();
  return {render:shared.render,setCanvasFactory:shared.setCanvasFactory,cleanup:shared.cleanup,stats:shared.stats,createRenderer,
    constants:Object.freeze({width:WIDTH,top:TOP,bottom:BOTTOM,originX:960,innerOffset:180,originY:380,slope:.35})};
});
