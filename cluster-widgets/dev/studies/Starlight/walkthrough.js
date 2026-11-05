(function(root,factory){const api=factory(typeof module==='object'?require('./menu-state'):root.StarlightMenuState,typeof module==='object'?require('./renderer'):root.MotionStudy,typeof module==='object'?require('./apex-data'):root.ApexData);if(typeof module==='object')module.exports=api;else root.ThemeWalkthrough=api;})(typeof globalThis!=='undefined'?globalThis:this,function(M,R,A){'use strict';
const DURATION=40;
const events=[
 [18,{type:'CARD',card:1},'Menu principal'],
 [19,{type:'KEY',key:'DOWN'},'Navegação do menu'],
 [20,{type:'KEY',key:'ENTER'},'Gráfico da potência da bateria'],
 [21,{type:'KEY',key:'DOWN'},'Gráfico de velocidade'],
 [22,{type:'KEY',key:'BACK'},'Voltar ao menu principal'],
 [22.5,{type:'KEY',key:'DOWN'},'Ajustes do veículo · simulação'],
 [23,{type:'KEY',key:'ENTER'},'Preferências do Apex GT'],
 [24,{type:'SETTING',index:2},'Direção alterada somente na simulação'],
 [25,{type:'KEY',key:'BACK'},'Voltar ao menu principal'],
 [25.5,{type:'KEY',key:'DOWN'},'Dados de viagem'],
 [26,{type:'KEY',key:'ENTER'},'Trip A'],
 [27,{type:'KEY',key:'DOWN'},'Trip B'],
 [28,{type:'CLOSE'},'Menu principal fechado'],
 [29,{type:'CARD',card:3},'Menu AC'],
 [30,{type:'AC_FOCUS',field:'temp'},'Selecionar temperatura'],
 [30.7,{type:'KEY',key:'UP'},'Temperatura 22,5°C · simulada'],
 [31.4,{type:'KEY',key:'UP'},'Temperatura 23°C · simulada'],
 [32,{type:'KEY',key:'ENTER'},'Selecionar ventilação'],
 [33,{type:'KEY',key:'UP'},'Ventilação nível 4 · simulada'],
 [34,{type:'KEY',key:'ENTER_LONG'},'Alternar Auto · simulação'],
 [35,{type:'KEY',key:'BACK_LONG'},'Alternar recirculação · simulação'],
 [36,{type:'KEY',key:'DOWN'},'Ventilação nível 3 · simulada'],
 [37,{type:'CLOSE'},'Menus fechados · animação retomada']
];
function create(){let state=M.createState(A.demo(0,R.panelState(0,'drive'))),cursor=0,last=-1,label='';
 return function sample(time){const t=Math.max(0,Math.min(DURATION,time));if(t<last){state=M.createState(A.demo(0,R.panelState(0,'drive')));cursor=0;label='';}last=t;
 const mode=t<16?'drive':'motion',panelTime=t<16?t:t>=37?8.3+(t-37):8.3,p=R.panelState(panelTime,mode);
 state=M.dispatch(state,{type:'TELEMETRY',values:{speed:p.speed,voltage:400,current:p.batteryPowerKw/400*1000},now:t*1000});
 while(cursor<events.length&&events[cursor][0]<=t){const e=events[cursor++];state=M.dispatch(state,{...e[1],now:e[0]*1000});label=e[2];}
 return{time:t,panelTime,mode,menuState:state,label:t<16?p.phase+' · BATERIA '+R.batteryLabel(p.batteryPowerKw):label||'Modelo 3 · detalhes do vidro',paused:t>=16&&t<37};
 };
}
return{DURATION,events,create};
});
