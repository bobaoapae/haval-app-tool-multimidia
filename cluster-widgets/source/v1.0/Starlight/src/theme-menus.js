/* Legible menus within the existing strip below the central speedometer.
   Production menu descriptors use host-owned cards and confirmed telemetry. */
(function (root, factory) {
  const api = factory(typeof module === 'object' ? require('./menu-state') : root.StarlightMenuState);
  if (typeof module === 'object') module.exports = api;
  else root.ThemeMenus = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (S) {
  'use strict';

  const BOX = Object.freeze({x:520, y:524, w:880, h:180});
  const PAD = 18;
  const BODY = Object.freeze({x:BOX.x+PAD, y:BOX.y+44, w:BOX.w-PAD*2, h:78});
  const CLOSE = Object.freeze({x:BOX.x+BOX.w-41, y:BOX.y+3, w:36, h:35});
  const BACK = Object.freeze({x:CLOSE.x-42, y:CLOSE.y, w:36, h:35});
  const TAB_X = BOX.x+354;
  const TAB_W = 137;
  const AC_FIELDS = Object.freeze([
    {x:BODY.x, y:BODY.y, w:238, h:BODY.h},
    {x:BODY.x+250, y:BODY.y, w:238, h:BODY.h}
  ]);
  const AC_STATS = Object.freeze([0,1,2].map(i => ({x:BODY.x+502+i*114, y:BODY.y, w:110, h:BODY.h})));
  const AC_MINUS = Object.freeze({x:BOX.x+698, y:BOX.y+4, w:40, h:34});
  const AC_PLUS = Object.freeze({x:BOX.x+744, y:BOX.y+4, w:40, h:34});

  function text(c, value, x, y, size=22, color='#dce9f3', align='left', font='Scale Digits', maxWidth=Infinity) {
    const string = String(value).replace(/↩ voltar/g,'voltar').replace(/↩/g,'voltar');
    c.font = `${size}px "${font}"`;
    const measured = c.measureText(string).width;
    if (Number.isFinite(maxWidth) && measured > maxWidth && measured > 0) {
      size *= maxWidth/measured;
      c.font = `${size}px "${font}"`;
    }
    c.fillStyle = color; c.textAlign = align; c.textBaseline = 'middle';
    // A crisp outline keeps glyphs readable without a blur pass per label.
    c.shadowBlur = 0; c.shadowOffsetY = 0;
    c.lineWidth = 2; c.lineJoin = 'round'; c.strokeStyle = 'rgba(0,0,0,.78)';
    c.strokeText(string,x,y);
    c.fillText(string,x,y);
  }
  function wrapped(c,value,x,y,size,color,maxWidth,lineHeight=18) {
    const string=String(value).replace(/↩ voltar/g,'voltar').replace(/↩/g,'voltar');
    c.font=`${size}px "Label Sans"`;
    const lines=[]; let current='';
    string.split(/\s+/).forEach(word=>{
      const next=current?current+' '+word:word;
      if(current&&c.measureText(next).width>maxWidth){lines.push(current);current=word;}
      else current=next;
    });
    if(current)lines.push(current);
    lines.forEach((label,index)=>text(c,label,x,y+index*lineHeight,size,color,'left','Label Sans',maxWidth));
    return lines.length;
  }
  function line(c,x1,y1,x2,y2,color,width=1) {
    c.beginPath(); c.moveTo(x1,y1); c.lineTo(x2,y2); c.strokeStyle=color; c.lineWidth=width; c.stroke();
  }
  function rowGlow(c,box,active) {
    if (!active) return;
    line(c,box.x+2,box.y+7,box.x+2,box.y+box.h-7,'#c8e1f3',2);
    line(c,box.x+2,box.y+7,box.x+13,box.y+7,'#c8e1f3',1);
  }
  function rowBoxes(count) {
    const gap=10, width=(BODY.w-gap*(count-1))/count;
    return Array.from({length:count},(_,i)=>({x:BODY.x+i*(width+gap),y:BODY.y,w:width,h:BODY.h}));
  }
  function shownRows(state,content) {
    if (content.view==='menu') return content.rows;
    const start=Math.max(0,Math.min(content.rows.length-3,state.settingIndex-1));
    return content.rows.slice(start,start+3);
  }
  function tabs(content) {
    return content.tabs.map((tab,index)=>({x:TAB_X+index*TAB_W,y:BOX.y+4,w:TAB_W-8,h:34,tab}));
  }
  function layout(state) {
    const content=S.getContent(state), hits=[];
    if (!content.visible) return hits;
    const add=(box,action,id)=>{if(action)hits.push({...box,action,id});};
    add(CLOSE,content.closeAction,'close');
    if (content.view!=='menu' && content.view!=='ac') add(BACK,content.backAction,'back');
    if (content.view==='menu' || content.view==='settings') {
      const rows=shownRows(state,content), boxes=rowBoxes(rows.length);
      rows.forEach((row,index)=>add(boxes[index],row.action,row.id));
    }
    tabs(content).forEach(box=>add({x:box.x,y:box.y,w:box.w,h:box.h},box.tab.action,'tab-'+box.tab.index));
    if (content.view==='trips' && content.resetAction) add({x:BODY.x,y:BOX.y+133,w:282,h:34},content.resetAction,'trip-reset');
    if (content.view==='ac') {
      content.rows.forEach((row,index)=>add(AC_FIELDS[index],row.action,row.id));
      content.stats.forEach((stat,index)=>add(AC_STATS[index],stat.action,stat.id));
      add(AC_MINUS,content.adjustActions.decrease,'decrease');
      add(AC_PLUS,content.adjustActions.increase,'increase');
    }
    return hits;
  }
  function hitTest(state,x,y) {
    if (!Number.isFinite(x) || !Number.isFinite(y)) return null;
    const hit=layout(state).find(box=>x>=box.x&&x<box.x+box.w&&y>=box.y&&y<box.y+box.h);
    return hit ? hit.action : null;
  }
  function value(stat) {return stat.value+(stat.unit?' '+stat.unit:'');}
  function surface(c,state,content,projection) {
    if(projection){
      // The real projection keeps its native slot transparent. Only card1
      // menus may use local circular contrast; AC never gets a surface here.
      if(content.view==='ac')return;
      const count=content.view==='settings'?shownRows(state,content).length:4;
      c.fillStyle='rgba(5,14,23,.36)';
      rowBoxes(count).forEach(box=>{
        c.beginPath();c.arc(box.x+box.w/2,BOX.y+BOX.h/2,82,0,Math.PI*2);c.fill();
      });
      return;
    }
    const x=BOX.x+1,y=BOX.y+1,w=BOX.w-2,h=BOX.h-2,r=12;
    c.beginPath();c.moveTo(x+r,y);c.lineTo(x+w-r,y);c.quadraticCurveTo(x+w,y,x+w,y+r);
    c.lineTo(x+w,y+h-r);c.quadraticCurveTo(x+w,y+h,x+w-r,y+h);
    c.lineTo(x+r,y+h);c.quadraticCurveTo(x,y+h,x,y+h-r);
    c.lineTo(x,y+r);c.quadraticCurveTo(x,y,x+r,y);c.closePath();
    c.fillStyle='rgba(5,14,23,.72)';c.fill();
    c.strokeStyle='rgba(113,146,169,.22)';c.lineWidth=1;c.stroke();
  }
  function guides(c) {
    line(c,BOX.x+PAD,BOX.y+131.5,BOX.x+BOX.w-PAD,BOX.y+131.5,'rgba(159,166,173,.19)',.7);
  }
  function header(c,state,content) {
    text(c,content.title,BODY.x,BOX.y+21,28,'#e8f2fa','left','Scale Digits',280);

    tabs(content).forEach(box=>{
      const gray=content.view==='graphs';
      text(c,box.tab.title,box.x+box.w/2,BOX.y+21,18,box.tab.selected?(gray?'#e1e3e5':'#e0f0fb'):(gray?'#a3a9af':'#a6bdcf'),'center','Label Sans',box.w-12);
      if(box.tab.selected)line(c,box.x+10,BOX.y+37.5,box.x+box.w-10,BOX.y+37.5,gray?'#a7abb0':'#a5c9e2',1);
    });
    if(content.counter)text(c,content.counter,BACK.x-13,BOX.y+21,18,'#b0c4d4','right');

    if(content.view!=='menu'&&content.view!=='ac')text(c,'‹',BACK.x+BACK.w/2,BOX.y+19,31,'#9fbed4','center');
    if(content.view==='ac'){
      text(c,state.acFocus==='temp'?'AJUSTAR TEMP.':'AJUSTAR VENTILAÇÃO',AC_MINUS.x-10,BOX.y+21,16,'#b4cddd','right','Label Sans',225);
      [AC_MINUS,AC_PLUS].forEach((box,index)=>{
        line(c,box.x+4,box.y+box.h-3,box.x+box.w-4,box.y+box.h-3,'#38546b',.8);
        text(c,index?'+':'−',box.x+box.w/2,BOX.y+20,32,'#d1e7f5','center');
      });
    }
  }
  function mainRows(c,state,content) {
    const rows=shownRows(state,content), boxes=rowBoxes(rows.length);
    rows.forEach((row,index)=>{
      const box=boxes[index]; rowGlow(c,box,row.selected);
      if(index)line(c,box.x-5,box.y+10,box.x-5,box.y+box.h-10,'rgba(92,128,155,.16)',.7);
      const color=row.disabled?'#a4b9c9':'#e5f1f9';
      if(content.view==='menu'){
        text(c,String(row.index+1).padStart(2,'0'),box.x+13,box.y+9,14,'#a7c0d3');
        text(c,row.title,box.x+13,box.y+34,28,color,'left','Scale Digits',box.w-18);
        text(c,row.secondary||row.subtitle,box.x+13,box.y+63,16,'#b7ccdc','left','Label Sans',box.w-16);
      }else{
        text(c,row.title,box.x+13,box.y+13,18,'#b4ccde','left','Label Sans',box.w-32);
        text(c,row.value,box.x+13,box.y+43,34,color,'left','Scale Digits',box.w-44);
        if(row.disabled)text(c,'Exclusivo do Apex GT',box.x+13,box.y+68,16,'#a2bbcf','left','Label Sans',box.w-30);
        else text(c,'›',box.x+box.w-16,box.y+42,31,'#a7c4d9','center');
      }
    });
  }
  function statColumns(c,items) {
    const boxes=rowBoxes(items.length);
    items.forEach((stat,index)=>{
      const box=boxes[index];
      if(index)line(c,box.x-5,box.y+10,box.x-5,box.y+box.h-10,'rgba(92,128,155,.19)',.7);
      if(stat.id==='temps')wrapped(c,stat.label,box.x+12,box.y+6,18,'#b6cbda',box.w-22,18);
      else text(c,stat.label,box.x+12,box.y+13,18,'#b6cbda','left','Label Sans',box.w-22);
      text(c,value(stat),box.x+12,box.y+(stat.id==='temps'?54:44),stat.id==='tripDistance'?38:32,'#e5f1f9','left','Scale Digits',box.w-22);
      if(stat.secondary)text(c,stat.secondary,box.x+12,box.y+72,20,'#bfd4e3','left','Scale Digits',box.w-22);
    });
  }
  function graph(c,content) {
    const q=content.chart;
    text(c,q.formatted,BODY.x+11,BODY.y+33,41,'#d2d4d7','left','Scale Digits',194);
    text(c,q.unit+' · '+q.rangeLabel,BODY.x+12,BODY.y+67,16,'#bdc2c8','left','Label Sans',205);
    const x=BODY.x+226, width=BODY.w-238, top=BODY.y+9, bottom=BODY.y+70;
    const low=q.domain[0], high=q.domain[1], span=high-low;
    const yFor=sample=>bottom-(Math.max(low,Math.min(high,sample))-low)/span*(bottom-top);
    const zero=Math.round(yFor(0))+.5;
    // Signed samples cross an understated zero line; no area fills, color
    // coding, smoothing, or invented samples hide the underlying 18 readings.
    line(c,x,zero,x+width,zero,'rgba(165,170,177,.32)',.75);
    c.beginPath();
    let connected=false, points=[];
    for(let index=0;index<18;index++){
      const sampleIndex=index-(18-q.samples.length), sample=sampleIndex>=0?q.samples[sampleIndex]:null;
      if(!Number.isFinite(sample)){connected=false;continue;}
      const xx=x+index*width/17, yy=yFor(sample);
      if(connected)c.lineTo(xx,yy);else c.moveTo(xx,yy);
      connected=true;points.push({x:xx,y:yy});
    }
    c.strokeStyle='#b6b9bd';c.lineWidth=1.25;c.lineJoin='round';c.lineCap='butt';c.stroke();
    // Short neutral sample marks also make a single available point visible.
    points.forEach(point=>line(c,point.x,point.y-1.5,point.x,point.y+1.5,'#b6b9bd',1));
    text(c,'18 AMOSTRAS · HISTÓRICO DO VEÍCULO',x,BODY.y+83,14,'#a9b2bc','left','Label Sans',width);
  }
  function ac(c,content) {
    content.rows.forEach((row,index)=>{
      const box=AC_FIELDS[index];rowGlow(c,box,row.selected);
      text(c,row.label,box.x+13,box.y+11,18,'#bed5e5','left','Label Sans',box.w-26);
      text(c,row.value,box.x+13,box.y+43,44,'#eff8fe','left','Scale Digits',box.w-26);
      if(row.field==='temp'){
        const left=box.x+14,right=box.x+box.w-16,yy=box.y+73;
        line(c,left,yy,right,yy,'#294255',4);
        if(row.ratio>0){const fill=c.createLinearGradient(left,0,right,0);fill.addColorStop(0,'#89bce0');fill.addColorStop(1,'#d3e3ed');line(c,left,yy,left+(right-left)*row.ratio,yy,fill,4);}
      }else{
        const gap=7,w=(box.w-30-gap*6)/7;
        for(let n=0;n<7;n++){c.fillStyle=n<row.activeSegments?'#b0cfe5':'#283f52';c.fillRect(box.x+14+n*(w+gap),box.y+70,w,5);}
      }
    });
    content.stats.forEach((stat,index)=>{
      const box=AC_STATS[index];
      line(c,box.x-4,box.y+10,box.x-4,box.y+box.h-8,'rgba(99,135,163,.2)',.7);
      text(c,stat.label,box.x+9,box.y+10,18,'#b5ccdd','left','Label Sans',box.w-15);
      text(c,stat.value,box.x+9,box.y+36,28,stat.active?'#d8f0fc':'#c0d3e1','left','Scale Digits',box.w-16);
      if(stat.action)wrapped(c,index===1?'OK LONGO':'VOLTAR LONGO',box.x+9,box.y+60,14,'#abc4d7',box.w-16,14);
    });
  }
  function footer(c,content,panelState) {
    const total=panelState.readouts&&panelState.readouts.totalRange;
    const totalText=typeof total==='number'&&Number.isFinite(total)?total.toLocaleString('pt-BR',{maximumFractionDigits:0}):'--';
    const speed=Number.isFinite(panelState.speed)?String(Math.round(panelState.speed)):'--';
    const hint=content.feedback||(content.view==='trips'?content.footnote:content.hint);
    wrapped(c,hint,BODY.x+2,BOX.y+149,14,content.feedback?'#c9e6f7':'#b2cadd',278,18);
    text(c,'TOTAL '+totalText+' km EST.',BOX.x+BOX.w/2,BOX.y+157,28,'#e1f1fb','center','Scale Digits',270);
    text(c,'VELOCIDADE '+speed+' km/h',BOX.x+BOX.w-PAD-2,BOX.y+156,16,'#bed4e3','right','Scale Digits',190);
  }
  function render(c,state,panelState={}) {
    const content=S.getContent(state);
    if(!content.visible)return[];
    c.save();
    // Every surface, glyph and hitbox stays inside the existing central strip.
    c.beginPath();c.rect(BOX.x,BOX.y,BOX.w,BOX.h);c.clip();
    surface(c,state,content,panelState.projection===true);guides(c);header(c,state,content);
    if(content.view==='menu'||content.view==='settings')mainRows(c,state,content);
    if(content.view==='info')statColumns(c,content.stats);
    if(content.view==='trips')statColumns(c,[content.primary,...content.stats]);
    if(content.view==='graphs')graph(c,content);
    if(content.view==='ac')ac(c,content);
    footer(c,content,panelState);c.restore();return layout(state);
  }
  return {BOX,render,hitTest,layout};
});
