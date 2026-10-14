/* Production menu reducer: canonical telemetry owns data and card state.
 * Commands are descriptors only; host-runtime executes the existing allowlist. */
(function (root, factory) {
  'use strict';
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.StarlightMenuState = factory();
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  var keyFields = {
    'car.basic.vehicle_speed': 'speed',
    'car.basic.total_odometer': 'odometer',
    'car.basic.inside_temp': 'insideTemp',
    'car.basic.outside_temp': 'outsideTemp',
    'car.configure.default_temp_unit': 'tempUnit',
    'car.basic.remain_fuel_percentage': 'fuel',
    'car.ev_info.cur_battery_power_percentage': 'battery',
    'car.ev_info.fuel_mode_remain_odometer': 'fuelRange',
    'car.ev_info.electric_mode_remain_odometer': 'batteryRange',
    'car.ev_info.power_battery_voltage': 'voltage',
    'car.ev_info.cur_charge_current': 'current',
    'car.basic.cur_journey_odometer': 'tripADistance',
    'car.basic.cur_journey_drivetime': 'tripATime',
    'car.basic.cur_journey_avg_fuel_consume': 'tripAConsumption',
    'car.basic.avg_vehicle_speed_since_startup': 'tripASpeed',
    'car.basic.accumulated_odometer': 'tripBDistance',
    'car.basic.accumulated_drivetime': 'tripBTime',
    'car.basic.avg_fuel_consumption': 'tripBConsumption',
    'car.basic.vehicle_speed_since_reset': 'tripBSpeed',
    'car.basic.tire_pressure_front_left': 'tireFL',
    'car.basic.tire_pressure_front_right': 'tireFR',
    'car.basic.tire_pressure_rear_left': 'tireRL',
    'car.basic.tire_pressure_rear_right': 'tireRR',
    'car.drive_setting.drive_mode': 'driveMode',
    'car.ev_setting.power_model_config': 'propulsionMode',
    'car.drive_setting.steering_wheel_assist_mode': 'steerMode',
    'car.ev_setting.energy_recovery_level': 'regenMode',
    'car.drive_setting.esp_enable': 'esp',
    'car.hvac.power_mode': 'acPower',
    'car.hvac.fan_speed': 'acFan',
    'car.hvac.driver_temperature': 'acTemp',
    'car.hvac.cycle_mode': 'acRecycle',
    'car.hvac.auto_enable': 'acAuto'
  };
  var fields = Object.keys(keyFields).map(function (key) { return keyFields[key]; });
  var labels = {
    driveMode: {0:'NORMAL',1:'SPORT',2:'ECO',3:'NEVE',4:'AREIA',5:'LAMA',11:'AWD'},
    propulsionMode: {0:'HEV',1:'EVP',3:'EV'},
    steerMode: {0:'NORMAL',1:'ESPORTIVA',2:'CONFORTO'},
    regenMode: {0:'NORMAL',1:'ALTO',2:'BAIXO'},
    esp: {0:'OFF',1:'ON'}
  };
  var mainItems = [
    {title:'INFORMAÇÕES',subtitle:'Veículo e pneus',view:'info'},
    {title:'GRÁFICOS',subtitle:'Potência e velocidade',view:'graphs'},
    {title:'AJUSTES',subtitle:'Preferências do veículo',view:'settings'},
    {title:'TRIP A / TRIP B',subtitle:'Dados de viagem',view:'trips'}
  ];
  var settingItems = [
    {title:'CONDUÇÃO',field:'driveMode',key:'car.drive_setting.drive_mode',values:[0,2,1]},
    {title:'PROPULSÃO',field:'propulsionMode',key:'car.ev_setting.power_model_config',values:[0,1,3]},
    {title:'DIREÇÃO',field:'steerMode',key:'car.drive_setting.steering_wheel_assist_mode',values:[2,0,1]},
    {title:'REGENERAÇÃO',field:'regenMode',key:'car.ev_setting.energy_recovery_level',values:[2,0,1]},
    {title:'ESTABILIDADE',field:'esp',key:'car.drive_setting.esp_enable',values:[1,0]},
    {title:'DISPLAY',field:'displayMode',values:['Contour','Vector'],disabled:true,
      reason:'Contour / Vector são exclusivos do Apex GT; modelo 3 preservado'}
  ];
  var tabs = {info:['GERAL','PNEUS'],graph:['POTÊNCIA','VELOCIDADE'],trip:['TRIP A','TRIP B']};
  var keys = ['UP','DOWN','ENTER','BACK','ENTER_LONG','BACK_LONG'];

  function deepFreeze(value) {
    if (value && typeof value === 'object' && !Object.isFrozen(value)) {
      Object.keys(value).forEach(function (key) { deepFreeze(value[key]); });
      Object.freeze(value);
    }
    return value;
  }
  var metadata = deepFreeze({production:true,mainItems:mainItems,settingItems:settingItems,
    tabs:tabs,keyFields:keyFields,labels:labels,keys:keys,cards:{panel:0,main:1,ac:3},
    ac:{temperature:{min:16,max:32,step:0.5},fan:{min:0,max:7,step:1}},
    graphs:{speedMax:200,sourceSpeedMax:180,powerMin:-100,powerMax:100,maxSamples:18},
    source:'ApexGT/src/apex-menus.js',sourceMenuBlob:'4e17e03831c6f99d652b70000df953f6d4ba787f'});

  function numeric(value) {
    if (typeof value !== 'number' && typeof value !== 'string') return null;
    if (typeof value === 'string' && !/^[+-]?(?:\d+\.?\d*|\.\d+)(?:e[+-]?\d+)?$/i.test(value.trim())) return null;
    var n = Number(value);
    return Number.isFinite(n) ? n : null;
  }
  function normalize(field, value) {
    if (labels[field]) {
      var n = numeric(value);
      if (n !== null && Object.prototype.hasOwnProperty.call(labels[field],n)) return n;
      var label = typeof value === 'string' ? value.trim().toUpperCase() : '';
      if (field === 'propulsionMode' && /^HEV (?:INTELIGENTE|PRIORIDADE \d+%)$/.test(label)) return 0;
      if (field === 'esp' && (label === 'ATIVO' || label === 'INATIVO')) return label === 'ATIVO' ? 1 : 0;
      var found = Object.keys(labels[field]).filter(function (key) { return labels[field][key] === label; });
      return found.length ? Number(found[0]) : null;
    }
    if (field === 'tempUnit' && (value === '°C' || value === '°F')) return value === '°F' ? 1 : 0;
    var number = numeric(value);
    if (number === null) return null;
    if (field === 'insideTemp' || field === 'outsideTemp') return number === -1 || number === 255 ? null : number;
    if (field === 'acTemp') return number >= 16 && number <= 32 ? number : null;
    if (field === 'acFan') return Number.isInteger(number) && number >= 0 && number <= 7 ? number : null;
    if (field === 'fuel' || field === 'battery') return number >= 0 && number <= 100 ? number : null;
    if (['acPower','acAuto','acRecycle','tempUnit'].indexOf(field) >= 0) return number === 0 || number === 1 ? number : null;
    if (field === 'current') return number;
    return number >= 0 ? number : null;
  }
  function applyValues(data, values) {
    if (!values || typeof values !== 'object') return;
    Object.keys(values).forEach(function (key) {
      var field = keyFields[key] || (fields.indexOf(key) >= 0 ? key : null);
      if (field) data[field] = normalize(field,values[key]);
    });
  }
  function createState(seed) {
    var data = {};
    fields.forEach(function (field) { data[field] = null; });
    applyValues(data,seed);
    return {production:true,card:0,menuIndex:0,detail:false,infoIndex:0,graphIndex:0,
      settingIndex:0,tripIndex:0,acFocus:'fan',data:data,history:{speed:[],power:[]},
      sampledAt:{speed:null,power:null},time:0,telemetryTime:0,lastKeyAt:null,feedback:'',lastCommand:null};
  }
  function copy(state) {
    return Object.assign({},state,{data:Object.assign({},state.data),
      history:{speed:state.history.speed.slice(),power:state.history.power.slice()},
      sampledAt:Object.assign({},state.sampledAt)});
  }
  function getView(state) {
    return state.card === 3 ? 'ac' : state.card === 1 ? (state.detail ? mainItems[state.menuIndex].view : 'menu') : 'panel';
  }
  function power(data) {
    if (data.voltage === null || data.current === null) return null;
    var p = data.voltage * data.current / 1000;
    return Number.isFinite(p) ? p : null;
  }
  function recordSample(state, metric, value, now) {
    if (value === null) return;
    var series = state.history[metric];
    if (state.sampledAt[metric] !== null && now - state.sampledAt[metric] < 250 && series.length) series[series.length-1] = value;
    else {
      state.sampledAt[metric] = now;
      series.push(value);
      if (series.length > 18) series.shift();
    }
  }
  function cardChanged(state, card) {
    if ([0,1,3].indexOf(card) < 0) return;
    if (card === 1 && state.card !== 1) state.detail = false;
    state.card = card;
    state.feedback = '';
    state.lastCommand = null;
  }
  function known(state, names) {
    if (names.every(function (field) { return state.data[field] !== null; })) return true;
    state.feedback = 'AGUARDANDO DADOS DO VEÍCULO';
    return false;
  }
  function requestWrite(state, writes, systemAction) {
    // No optimistic data, reset values or acknowledgements. Telemetry is authoritative.
    state.lastCommand = {systemAction:systemAction || null,
      writes:writes.map(function (entry) { return {key:entry[0],value:String(entry[1])}; }),
      order:systemAction ? ['systemAction','writes'] : ['writes']};
    state.feedback = 'AGUARDANDO RETORNO DO VEÍCULO';
  }
  function changeSetting(state) {
    var item = settingItems[state.settingIndex];
    if (item.disabled) {
      state.feedback = 'DISPLAY EXCLUSIVO DO APEX GT';
      state.lastCommand = null;
      return;
    }
    if (!known(state,[item.field])) return;
    var next = item.values[(item.values.indexOf(state.data[item.field])+1)%item.values.length];
    requestWrite(state,[[item.key,next]],null,item.title+': '+labels[item.field][next]);
  }
  function acKey(state,key) {
    var data = state.data;
    if (key === 'ENTER') { state.acFocus = state.acFocus === 'fan' ? 'temp' : 'fan'; return; }
    if (key === 'BACK_LONG') {
      if (known(state,['acRecycle'])) requestWrite(state,[['car.hvac.cycle_mode',data.acRecycle === 1 ? 0 : 1]],null,'RECIRCULAÇÃO');
      return;
    }
    if (key === 'ENTER_LONG') {
      if (known(state,['acAuto'])) requestWrite(state,[['car.hvac.auto_enable',data.acAuto === 1 ? 0 : 1]],'CANCEL_MAX_AC','MODO AUTO');
      return;
    }
    if (key !== 'UP' && key !== 'DOWN') return;
    if (state.acFocus === 'temp') {
      if (!known(state,['acTemp'])) return;
      var temp = Math.max(16,Math.min(32,Math.round((data.acTemp+(key === 'UP' ? 0.5 : -0.5))*2)/2));
      if (temp !== data.acTemp) requestWrite(state,[['car.hvac.driver_temperature',temp.toFixed(1)]],null,'TEMPERATURA');
      return;
    }
    if (!known(state,['acFan','acPower'])) return;
    var fan = data.acPower === 0 ? 0 : data.acFan;
    var nextFan = Math.max(0,Math.min(7,fan+(key === 'UP' ? 1 : -1)));
    if (fan === nextFan) return;
    var writes = [];
    if (nextFan > 0 && data.acPower === 0) writes.push(['car.hvac.power_mode',1]);
    if (nextFan === 0) writes.push(['car.hvac.power_mode',0]);
    writes.push(['car.hvac.fan_speed',nextFan]);
    requestWrite(state,writes,null,'VENTILAÇÃO');
  }
  function mainKey(state,key) {
    var step = key === 'UP' ? -1 : key === 'DOWN' ? 1 : 0;
    if (!state.detail) {
      if (key === 'ENTER') state.detail = true;
      if (step) state.menuIndex = (state.menuIndex+step+mainItems.length)%mainItems.length;
      return;
    }
    if (key === 'BACK') { state.detail = false; state.feedback = ''; return; }
    var view = getView(state);
    if (view === 'info' && step) state.infoIndex = 1-state.infoIndex;
    if (view === 'graphs' && step) state.graphIndex = 1-state.graphIndex;
    if (view === 'settings') {
      if (step) state.settingIndex = (state.settingIndex+step+settingItems.length)%settingItems.length;
      if (key === 'ENTER') changeSetting(state);
    }
    if (view === 'trips') {
      if (step) state.tripIndex = 1-state.tripIndex;
      // Only an explicit long press on Trip B can request reset; data remain unchanged.
      if (key === 'ENTER_LONG' && state.tripIndex === 1 && known(state,['tripBDistance','tripBTime','tripBConsumption','tripBSpeed'])) requestWrite(state,[],'RESET_DRIVE_INFO','AGUARDANDO RETORNO DO VEÍCULO');
    }
  }
  function dispatch(state,action) {
    if (!state || !action || typeof action !== 'object') return state;
    var next = copy(state);
    var type = action.type;
    if (type !== 'TELEMETRY') next.lastCommand = null;
    if (type === 'HOST_CARD') cardChanged(next,numeric(action.card));
    else if (type === 'CARD' || type === 'CLOSE') return state;
    else if (type === 'CLEAR_FEEDBACK') { next.feedback = ''; next.lastCommand = null; }
    else if (type === 'RESET_HISTORY') {
      next.history = {speed:[],power:[]};
      next.sampledAt = {speed:null,power:null};
      next.telemetryTime = 0;
    } else if (type === 'RESET_DATA') {
      var reset = createState(action.values);
      next.data = reset.data;
      next.history = reset.history;
      next.sampledAt = reset.sampledAt;
      next.telemetryTime = 0;
      next.feedback = '';
      next.lastCommand = null;
    }
    else if (type === 'OPEN') {
      var index = mainItems.findIndex(function (item) { return item.view === action.section; });
      if (index >= 0 && next.card === 1) { next.menuIndex = index; next.detail = true; }
    } else if (type === 'FOCUS' && Number.isInteger(action.index)) {
      var currentView = getView(next);
      if (action.group === 'menu' && currentView === 'menu' && action.index >= 0 && action.index < mainItems.length) next.menuIndex = action.index;
      if (action.group === 'setting' && currentView === 'settings' && action.index >= 0 && action.index < settingItems.length) next.settingIndex = action.index;
    } else if (type === 'TAB' && next.card === 1 && next.detail && (action.index === 0 || action.index === 1)) {
      var expected = {info:'info',graphs:'graph',trips:'trip'}[getView(next)];
      if (expected === action.group) next[action.group+'Index'] = action.index;
    } else if (type === 'SETTING' && getView(next) === 'settings' && Number.isInteger(action.index) && action.index >= 0 && action.index < settingItems.length) {
      next.settingIndex = action.index;
      changeSetting(next);
    } else if (type === 'AC_FOCUS' && next.card === 3 && (action.field === 'temp' || action.field === 'fan')) next.acFocus = action.field;
    else if (type === 'KEY' && (next.card === 1 || next.card === 3)) {
      var key = typeof action.key === 'string' ? action.key.toUpperCase() : '';
      if (keys.indexOf(key) < 0) return state;
      var now = Number.isFinite(action.now) ? action.now : state.time+100;
      if (next.lastKeyAt !== null && now-next.lastKeyAt < 50) return state;
      next.time = Math.max(next.time,now);
      next.lastKeyAt = now;
      if (next.card === 3) acKey(next,key); else mainKey(next,key);
    } else if (type === 'TELEMETRY') {
      // Telemetry timeline and keyboard debounce clocks are independent.
      var at = Number.isFinite(action.now) ? Math.max(state.telemetryTime,action.now) : state.telemetryTime+250;
      next.telemetryTime = at;
      applyValues(next.data,action.values);
      if (next.data.speed !== state.data.speed) recordSample(next,'speed',next.data.speed,at);
      if (next.data.voltage !== state.data.voltage || next.data.current !== state.data.current) recordSample(next,'power',power(next.data),at);
    }
    return next;
  }

  function number(value,decimals) {
    return value === null || !Number.isFinite(value) ? '--' : value.toLocaleString('pt-BR',{minimumFractionDigits:decimals||0,maximumFractionDigits:decimals||0});
  }
  function temperature(data,value) {
    return value === null ? '--' : number(value,value%1 ? 1 : 0)+(data.tempUnit === 1 ? '°F' : '°C');
  }
  function consumption(value) {
    return value === null || (value > 0 && !Number.isFinite(100/value)) ? '--' : value === 0 ? '0,0 L/100 km' : number(100/value,1)+' km/L';
  }
  function makeTabs(group,index) {
    return tabs[group].map(function (label,i) { return {title:label,label:label,index:i,group:group,selected:i===index,action:{type:'TAB',group:group,index:i}}; });
  }
  function stat(id,label,value,unit,extra) {
    var result = Object.assign({id:id,title:label,label:label,value:value,unit:unit||'',secondary:'',selected:false,disabled:false},extra||{});
    result.subtitle = result.secondary;
    return result;
  }
  function getContent(state) {
    var data = state.data;
    var view = getView(state);
    var content = {production:true,view:view,visible:view!=='panel',card:state.card,title:'',subtitle:'',
      tabs:[],rows:[],stats:[],chart:null,primary:null,counter:'',hint:'',feedback:state.feedback,footnote:'',
      closeAction:null,backAction:{type:'KEY',key:'BACK'}};
    if (view === 'panel') return content;
    content.title = view === 'menu' ? 'MENU PRINCIPAL' : view === 'ac' ? 'CLIMATIZAÇÃO' : mainItems[state.menuIndex].title;
    if (view === 'menu') {
      content.counter = (state.menuIndex+1)+'/'+mainItems.length;
      content.hint = '↑ ↓ navegar · OK abrir';
      content.rows = mainItems.map(function (item,i) { return stat(item.view,item.title,'','',{
        secondary:item.subtitle,index:i,selected:state.menuIndex===i,action:{type:'OPEN',section:item.view}}); });
    } else if (view === 'settings') {
      content.counter = (state.settingIndex+1)+'/'+settingItems.length;
      content.hint = '↑ ↓ escolher · OK alterar · ↩ voltar';
      content.rows = settingItems.map(function (item,i) {
        var value = item.disabled ? 'Contour / Vector' : data[item.field] === null ? '--' : labels[item.field][data[item.field]];
        return stat(item.field,item.title,value,'',{index:i,selected:state.settingIndex===i,disabled:!!item.disabled,
          secondary:item.disabled ? 'Exclusivo do Apex GT' : 'Ajuste do veículo',reason:item.reason||'',action:{type:'SETTING',index:i}});
      });
      content.footnote = 'Leituras confirmadas pelo veículo';
    } else if (view === 'info') {
      content.tabs = makeTabs('info',state.infoIndex);
      content.hint = '↑ ↓ alternar · ↩ voltar';
      if (state.infoIndex === 0) content.stats = [
        stat('odometer','ODÔMETRO',number(data.odometer),'km'),
        stat('temps','TEMP. INTERNA · EXTERNA',temperature(data,data.insideTemp)+' · '+temperature(data,data.outsideTemp)),
        stat('fuel','COMBUSTÍVEL',number(data.fuel),data.fuel===null?'':'%',{secondary:number(data.fuelRange)+' km'}),
        stat('battery','BATERIA',number(data.battery),data.battery===null?'':'%',{secondary:number(data.batteryRange)+' km'})
      ];
      else content.stats = ['FL','FR','RL','RR'].map(function (position,i) {
        var value = data['tire'+position];
        return stat('tire'+position,['DIANT. ESQ.','DIANT. DIR.','TRAS. ESQ.','TRAS. DIR.'][i],value===null||value<=0?'--':number(value/6.89476,1),'PSI');
      });
    } else if (view === 'graphs') {
      content.tabs = makeTabs('graph',state.graphIndex);
      content.hint = '↑ ↓ alternar · ↩ voltar';
      var metric = state.graphIndex === 0 ? 'power' : 'speed';
      var value = metric === 'power' ? power(data) : data.speed;
      content.chart = {metric:metric,value:value,formatted:number(value,metric==='power'?1:0),unit:metric==='power'?'kW':'km/h',
        // Owner explicitly selected a 200 km/h panel; retain values above scale too.
        domain:metric==='power'?[-100,100]:[0,200],samples:state.history[metric].slice(),maxSamples:18,
        rangeLabel:metric==='power'?'−100 / 100 kW':'0 / 200 km/h'};
      content.primary = stat(metric,metric==='power'?'POTÊNCIA':'VELOCIDADE',content.chart.formatted,content.chart.unit);
      content.footnote = 'HISTÓRICO DO VEÍCULO';
    } else if (view === 'trips') {
      content.tabs = makeTabs('trip',state.tripIndex);
      content.hint = '↑ ↓ alternar · ↩ voltar';
      var prefix = state.tripIndex===0?'tripA':'tripB';
      var duration = data[prefix+'Time'] === null ? null : Math.round(data[prefix+'Time']);
      content.primary = stat('tripDistance','DISTÂNCIA',number(data[prefix+'Distance'],1),'km');
      content.stats = [stat('tripTime','TEMPO',duration===null?'--':Math.floor(duration/60)+'h '+String(duration%60).padStart(2,'0')+'min'),
        stat('tripSpeed','VELOC. MÉDIA',number(data[prefix+'Speed'],1),'km/h'),
        stat('tripConsumption','CONSUMO',consumption(data[prefix+'Consumption']))];
      content.footnote = state.tripIndex===0?'REINÍCIO AUTOMÁTICO':'SEGURE OK PARA ZERAR';
      content.resetAction = state.tripIndex===1 ? {type:'KEY',key:'ENTER_LONG'} : null;
    } else if (view === 'ac') {
      content.hint = '↑ ↓ ajustar · OK alternar';
      content.rows = [
        stat('acTemp','TEMPERATURA',data.acTemp===null?'--':data.acTemp<=16?'LO':data.acTemp>=32?'HI':temperature(data,data.acTemp),'',{
          field:'temp',selected:state.acFocus==='temp',min:16,max:32,step:0.5,raw:data.acTemp,ratio:data.acTemp===null?0:(data.acTemp-16)/16,action:{type:'AC_FOCUS',field:'temp'}}),
        stat('acFan','VENTILAÇÃO',data.acPower===null?'--':data.acPower===0?'OFF':number(data.acFan),'',{
          field:'fan',selected:state.acFocus==='fan',min:0,max:7,step:1,raw:data.acFan,segments:7,
          activeSegments:data.acPower===1&&data.acFan!==null?data.acFan:0,action:{type:'AC_FOCUS',field:'fan'}})
      ];
      content.stats = [stat('acPower','SISTEMA',data.acPower===null?'--':data.acPower===1?'LIGADO':'DESL.','',{active:data.acPower===1}),
        stat('acAuto','MODO',data.acAuto===null?'--':data.acAuto===1?'AUTO':'MANUAL','',{active:data.acAuto===1,action:{type:'KEY',key:'ENTER_LONG'}}),
        stat('acRecycle','ENTRADA',data.acRecycle===null?'--':data.acRecycle===1?'RECIRC.':'EXTERNO','',{active:data.acRecycle===1,action:{type:'KEY',key:'BACK_LONG'}})];
      content.footnote = 'Segure OK · Auto     Segure ↩ · Recircular';
      content.adjustActions = {increase:{type:'KEY',key:'UP'},decrease:{type:'KEY',key:'DOWN'}};
    }
    return content;
  }

  return Object.freeze({createState:createState,dispatch:dispatch,getView:getView,
    getContent:getContent,metadata:metadata,normalize:normalize,power:power});
}));
