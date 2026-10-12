/* Compact, transparent menus below the central speedometer.
   Apex-derived menu actions remain local simulation only; no vehicle bridge. */
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
    c.shadowColor = 'rgba(0,0,0,.72)'; c.shadowBlur = 3; c.shadowOffsetY = 1;
    c.fillText(string,x,y);
    c.shadowBlur = 0; c.shadowOffsetY = 0;
  }
  function line(c,x1,y1,x2,y2,color,width=1) {
    c.beginPath(); c.moveTo(x1,y1); c.lineTo(x2,y2); c.strokeStyle=color; c.lineWidth=width; c.stroke();
  }
  function rowGlow(c,box,active) {
    if (!active) return;
    // The selected item has a fine marker, never a filled card or tint.
    line(c,box.x+1.5,box.y+11,box.x+1.5,box.y+box.h-11,'#b9c7d2',1);
    line(c,box.x+1.5,box.y+11,box.x+13,box.y+11,'#b9c7d2',1);
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
  function guides(c) {
    // Background pixels stay completely untouched. Only this subtle divider
    // separates the controls from their shared total-range/speed footer.
    line(c,BOX.x+PAD,BOX.y+131.5,BOX.x+BOX.w-PAD,BOX.y+131.5,'rgba(159,166,173,.19)',.7);
  }
  function header(c,state,content) {
    text(c,content.title,BODY.x,BOX.y+21,21,'#d8e7f2','left','Scale Digits',222);
    text(c,'SIMULADO',BOX.x+310,BOX.y+21,9,'#668197','right','Label Sans');
    tabs(content).forEach(box=>{
      const gray=content.view==='graphs';
      text(c,box.tab.title,box.x+box.w/2,BOX.y+21,14,box.tab.selected?(gray?'#d4d6d9':'#d9eaf6'):(gray?'#858b92':'#758da1'),'center','Label Sans',box.w-12);
      if(box.tab.selected)line(c,box.x+10,BOX.y+37.5,box.x+box.w-10,BOX.y+37.5,gray?'#a7abb0':'#a5c9e2',1);
    });
    if(content.counter)text(c,content.counter,BACK.x-13,BOX.y+21,14,'#829eb4','right');
    text(c,'×',CLOSE.x+CLOSE.w/2,BOX.y+20,28,'#9fbed4','center');
    if(content.view!=='menu'&&content.view!=='ac')text(c,'‹',BACK.x+BACK.w/2,BOX.y+19,31,'#9fbed4','center');
    if(content.view==='ac'){
      text(c,state.acFocus==='temp'?'AJUSTAR TEMP.':'AJUSTAR VENTILAÇÃO',AC_MINUS.x-10,BOX.y+21,11,'#88a4bb','right','Label Sans',225);
      [AC_MINUS,AC_PLUS].forEach((box,index)=>{
        line(c,box.x+4,box.y+box.h-3,box.x+box.w-4,box.y+box.h-3,'#38546b',.8);
        text(c,index?'+':'−',box.x+box.w/2,BOX.y+20,25,'#b8d3e6','center');
      });
    }
  }
  function mainRows(c,state,content) {
    const rows=shownRows(state,content), boxes=rowBoxes(rows.length);
    rows.forEach((row,index)=>{
      const box=boxes[index]; rowGlow(c,box,row.selected);
      if(index)line(c,box.x-5,box.y+10,box.x-5,box.y+box.h-10,'rgba(92,128,155,.16)',.7);
      const color=row.disabled?'#7890a3':'#dce9f3';
      if(content.view==='menu'){
        text(c,String(row.index+1).padStart(2,'0'),box.x+13,box.y+11,11,'#7796af');
        text(c,row.title,box.x+13,box.y+34,20,color,'left','Scale Digits',box.w-24);
        text(c,row.secondary||row.subtitle,box.x+13,box.y+60,11,'#8ca6bb','left','Label Sans',box.w-24);
      }else{
        text(c,row.title,box.x+13,box.y+14,12,'#88a5bc','left','Label Sans',box.w-32);
        text(c,row.value,box.x+13,box.y+43,28,color,'left','Scale Digits',box.w-44);
        if(row.disabled)text(c,'Exclusivo do Apex GT',box.x+13,box.y+65,10,'#617d92','left','Label Sans',box.w-30);
        else text(c,'›',box.x+box.w-16,box.y+42,25,'#6f96b5','center');
      }
    });
  }
  function statColumns(c,items) {
    const boxes=rowBoxes(items.length);
    items.forEach((stat,index)=>{
      const box=boxes[index];
      if(index)line(c,box.x-5,box.y+10,box.x-5,box.y+box.h-10,'rgba(92,128,155,.19)',.7);
      text(c,stat.label,box.x+12,box.y+14,11,'#8da6bb','left','Label Sans',box.w-22);
      text(c,value(stat),box.x+12,box.y+43,stat.id==='tripDistance'?34:29,'#dce9f3','left','Scale Digits',box.w-22);
      if(stat.secondary)text(c,stat.secondary,box.x+12,box.y+68,16,'#9fb9cd','left','Scale Digits',box.w-22);
    });
  }
  function graph(c,content) {
    const q=content.chart;
    text(c,q.formatted,BODY.x+11,BODY.y+33,41,'#d2d4d7','left','Scale Digits',194);
    text(c,q.unit+' · '+q.rangeLabel,BODY.x+12,BODY.y+66,11,'#90969d','left','Label Sans',195);
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
    text(c,'18 AMOSTRAS · HISTÓRICO SIMULADO',x,BODY.y+82,9,'#737b84','left','Label Sans',width);
  }
  function ac(c,content) {
    content.rows.forEach((row,index)=>{
      const box=AC_FIELDS[index];rowGlow(c,box,row.selected);
      text(c,row.label,box.x+13,box.y+13,12,'#9eb5c8','left','Label Sans',box.w-26);
      text(c,row.value,box.x+13,box.y+41,33,'#deedf7','left','Scale Digits',box.w-26);
      if(row.field==='temp'){
        const left=box.x+14,right=box.x+box.w-16,yy=box.y+68;
        line(c,left,yy,right,yy,'#294255',4);
        if(row.ratio>0){const fill=c.createLinearGradient(left,0,right,0);fill.addColorStop(0,'#89bce0');fill.addColorStop(1,'#d3e3ed');line(c,left,yy,left+(right-left)*row.ratio,yy,fill,4);}
      }else{
        const gap=7,w=(box.w-30-gap*6)/7;
        for(let n=0;n<7;n++){c.fillStyle=n<row.activeSegments?'#b0cfe5':'#283f52';c.fillRect(box.x+14+n*(w+gap),box.y+65,w,5);}
      }
    });
    content.stats.forEach((stat,index)=>{
      const box=AC_STATS[index];
      line(c,box.x-4,box.y+10,box.x-4,box.y+box.h-8,'rgba(99,135,163,.2)',.7);
      text(c,stat.label,box.x+9,box.y+14,10,'#7f9db5','left','Label Sans',box.w-15);
      text(c,stat.value,box.x+9,box.y+42,23,stat.active?'#c6e4f4':'#a4bacc','left','Scale Digits',box.w-16);
      if(stat.action)text(c,index===1?'OK LONGO':'VOLTAR LONGO',box.x+9,box.y+67,8,'#5f8099','left','Label Sans',box.w-16);
    });
  }
  function footer(c,content,panelState) {
    const total=panelState.readouts&&panelState.readouts.totalRange;
    const totalText=typeof total==='number'&&Number.isFinite(total)?total.toLocaleString('pt-BR',{maximumFractionDigits:0}):'--';
    const speed=Number.isFinite(panelState.speed)?String(Math.round(panelState.speed)):'--';
    const hint=content.feedback||(content.view==='trips'?content.footnote:content.hint);
    text(c,hint,BODY.x+2,BOX.y+156,11,content.feedback?'#abcfe5':'#829fb6','left','Label Sans',278);
    text(c,'TOTAL '+totalText+' km EST.',BOX.x+BOX.w/2,BOX.y+157,24,'#cee2f0','center','Scale Digits',270);
    text(c,'VELOCIDADE '+speed+' km/h',BOX.x+BOX.w-PAD-2,BOX.y+156,13,'#8fabc1','right','Scale Digits',190);
  }
  function render(c,state,panelState={}) {
    const content=S.getContent(state);
    if(!content.visible)return[];
    c.save();
    // Constrain every label, highlight and shadow to this temporary central strip.
    c.beginPath();c.rect(BOX.x,BOX.y,BOX.w,BOX.h);c.clip();
    guides(c);header(c,state,content);
    if(content.view==='menu'||content.view==='settings')mainRows(c,state,content);
    if(content.view==='info')statColumns(c,content.stats);
    if(content.view==='trips')statColumns(c,[content.primary,...content.stats]);
    if(content.view==='graphs')graph(c,content);
    if(content.view==='ac')ac(c,content);
    footer(c,content,panelState);c.restore();return layout(state);
  }
  return {BOX,render,hitTest,layout};
});
