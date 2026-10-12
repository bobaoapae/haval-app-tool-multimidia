const assert=require('assert/strict'),fs=require('fs'),{createCanvas,GlobalFonts}=require('@napi-rs/canvas'),M=require('./menu-state'),V=require('./theme-menus'),R=require('./renderer'),A=require('./apex-data');
for(const[f,n]of[['ScaleDigits.ttf','Scale Digits'],['LabelSans.ttf','Label Sans']])GlobalFonts.registerFromPath('assets/'+f,n);
const results=[];
for(const page of ['menu','info','graphs','settings','trips','ac']){
 const c=createCanvas(1920,720),ctx=c.getContext('2d'),p=R.panelState(12,'city'),s0=M.createState(A.demo(12,p)),s=M.dispatch(s0,page==='ac'?{type:'CARD',card:3}:page==='menu'?{type:'CARD',card:1}:{type:'OPEN',section:page});
 V.render(ctx,s,p);const b=V.BOX,d=ctx.getImageData(b.x,b.y,b.w,b.h).data;let visible=0,opaque=0;for(let i=3;i<d.length;i+=4){visible+=d[i]>0;opaque+=d[i]>220;}
 const occupied=visible/(b.w*b.h),solid=opaque/(b.w*b.h);assert(occupied<.40,'no full-menu background '+page);assert(solid<.20,'opaque area limited to glyphs/marks '+page);assert.equal(ctx.getImageData(0,0,1,1).data[3],0);results.push({page,nontransparentPixelFraction:occupied,opaquePixelFraction:solid});
}
const source=fs.readFileSync('theme-menus.js','utf8'),graph=source.slice(source.indexOf('function graph'),source.indexOf('function ac'));
assert(!graph.includes('fillRect'));assert(!graph.includes('createLinearGradient'));assert(graph.includes("c.lineWidth=1.25"));assert(graph.includes("c.strokeStyle='#b6b9bd'"));
fs.writeFileSync('qa/menu-transparency-validation.json',JSON.stringify({result:'PASS',results,graph:'neutral-gray thin polyline, no filled area',browserVerified:false},null,2));console.log('Transparent menu and sober-gray graph checks PASS');
