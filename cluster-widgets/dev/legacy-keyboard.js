export const SPORT_MENU_ITEMS = [
  'option_1',
  'option_2',
  'option_3',
  'option_7',
  'option_5',
  'option_6',
  'option_4',
  'option_8',
  'option_9',
];

export const BASIC_MENU_ITEMS = [
  'option_1',
  'option_2',
  'option_3',
  'option_7',
  'option_5',
  'option_6',
  'option_4',
];

const SPORT_DISPLAY_MODES = [
  { id: 'mode_analogico', value: 'Normal' },
  { id: 'mode_analogico_v2', value: 'Analógico V2' },
  { id: 'mode_digital', value: 'Digital' },
  { id: 'mode_mapa', value: 'Mapa' },
  { id: 'mode_mapa_graduado', value: 'Mapa Graduado' },
  { id: 'mode_mapa_limpo', value: 'Mapa Limpo' },
];

const BASIC_DISPLAY_MODES = [
  { id: 'mode_normal', value: 'Normal' },
  { id: 'mode_reduzido', value: 'Reduzido' },
  { id: 'mode_clean', value: 'Clean' },
  { id: 'mode_mapa', value: 'Mapa' },
];

const SPORT_COLORS = [
  { id: 'color_red_sport', value: 'Red Sport' },
  { id: 'color_red_gt', value: 'Red GT' },
  { id: 'color_ocean_blue', value: 'Ocean Blue' },
  { id: 'color_green', value: 'Green' },
  { id: 'color_dark_blue', value: 'Dark Blue' },
  { id: 'color_amber_gold', value: 'Amber Gold' },
  { id: 'color_purple_gt', value: 'Purple GT' },
];

function cycle(items, currentValue, direction) {
  const currentIndex = Math.max(0, items.indexOf(currentValue));
  return items[(currentIndex + direction + items.length) % items.length];
}

function cycleObject(items, currentId, direction) {
  const currentIndex = Math.max(0, items.findIndex((item) => item.id === currentId));
  return items[(currentIndex + direction + items.length) % items.length];
}

export function installLegacyKeyboardAdapter(frameWindow, theme, onStatusChange) {
  if (!frameWindow || !theme.keyboard.startsWith('legacy-')) {
    return { cleanup() {} };
  }

  const isSport = theme.keyboard === 'legacy-sport';
  const menuItems = isSport ? SPORT_MENU_ITEMS : BASIC_MENU_ITEMS;
  const displayModes = isSport ? SPORT_DISPLAY_MODES : BASIC_DISPLAY_MODES;

  const state = {
    screen: 'main_menu',
    cardId: 1,
    focusedMenuItem: 'option_4',
    display: isSport ? 'Analógico V2' : 'Normal',
    displayFocus: isSport ? 'mode_analogico_v2' : 'mode_normal',
    colorTheme: isSport ? 'Red Sport' : null,
    colorFocus: isSport ? 'color_red_sport' : null,
    focusArea: 'fan',
    fan: 1,
    temp: 22,
    auto: 0,
    maxauto: 0,
    recycle: 0,
    power: 1,
    espStatus: 'ON',
    evMode: 'HEV',
    drivingMode: 'Normal',
    steerMode: 'Conforto',
    regenMode: 'Normal',
    onepedal: false,
    currentGraph: 'evConsumption',
  };

  const report = () => {
    onStatusChange?.({ ...state });
  };

  const control = (key, value) => {
    state[key] = value;
    if (typeof frameWindow.control === 'function') {
      frameWindow.control(key, value);
    }
    report();
  };

  const focus = (target) => {
    if (state.screen === 'main_menu') state.focusedMenuItem = target;
    if (state.screen === 'aircon') state.focusArea = target;
    if (state.screen === 'display_selection') state.displayFocus = target;
    if (state.screen === 'color_selection') state.colorFocus = target;
    if (typeof frameWindow.focus === 'function') frameWindow.focus(target);
    report();
  };

  const showScreen = (screen) => {
    state.screen = screen;
    if (typeof frameWindow.showScreen === 'function') frameWindow.showScreen(screen);
    report();
  };

  const setCard = (cardId) => {
    control('cardId', cardId);
    if (cardId === 1) {
      control('mainMenuSessionActive', true);
      showScreen('main_menu');
    } else if (cardId === 3) {
      control('mainMenuSessionActive', false);
      showScreen('aircon');
    } else {
      control('mainMenuSessionActive', false);
    }
  };

  const cycleCard = (direction) => {
    const cards = [0, 1, 3];
    setCard(cycle(cards, state.cardId, direction));
  };

  const cycleSimpleState = (key, values) => {
    control(key, cycle(values, state[key], 1));
  };

  const handleMainMenu = (key) => {
    if (key === 'ArrowUp' || key === 'ArrowDown') {
      focus(cycle(menuItems, state.focusedMenuItem, key === 'ArrowUp' ? -1 : 1));
      return true;
    }
    if (key !== 'Enter') return false;

    switch (state.focusedMenuItem) {
      case 'option_1':
        cycleSimpleState('espStatus', ['ON', 'OFF']);
        break;
      case 'option_2':
        cycleSimpleState('evMode', ['EV', 'EVP', 'HEV']);
        break;
      case 'option_3':
        cycleSimpleState('drivingMode', ['Normal', 'Eco', 'Sport']);
        break;
      case 'option_5':
        cycleSimpleState('steerMode', ['Normal', 'Conforto', 'Esportiva']);
        break;
      case 'option_6':
        showScreen('regen');
        break;
      case 'option_7':
        showScreen('graph');
        break;
      case 'option_4':
        showScreen('display_selection');
        focus(state.displayFocus);
        break;
      case 'option_8':
        if (isSport) {
          showScreen('color_selection');
          focus(state.colorFocus);
        }
        break;
      case 'option_9':
        if (isSport) showScreen('now_playing');
        break;
      default:
        break;
    }
    return true;
  };

  const handleDisplaySelection = (key) => {
    if (key === 'ArrowUp' || key === 'ArrowDown') {
      const next = cycleObject(displayModes, state.displayFocus, key === 'ArrowUp' ? -1 : 1);
      focus(next.id);
      return true;
    }
    if (key === 'Enter') {
      const selected = displayModes.find((item) => item.id === state.displayFocus);
      if (selected) control('display', selected.value);
      return true;
    }
    return false;
  };

  const handleColorSelection = (key) => {
    if (key === 'ArrowUp' || key === 'ArrowDown') {
      const next = cycleObject(SPORT_COLORS, state.colorFocus, key === 'ArrowUp' ? -1 : 1);
      focus(next.id);
      return true;
    }
    if (key === 'Enter') {
      const selected = SPORT_COLORS.find((item) => item.id === state.colorFocus);
      if (selected) control('colorTheme', selected.value);
      return true;
    }
    return false;
  };

  const handleAircon = (key) => {
    if (key === 'Enter') {
      focus(state.focusArea === 'fan' ? 'temp' : 'fan');
      return true;
    }
    if (key === 'ArrowUp' || key === 'ArrowDown') {
      const direction = key === 'ArrowUp' ? 1 : -1;
      if (state.focusArea === 'fan') {
        control('fan', Math.min(7, Math.max(0, Number(state.fan) + direction)));
      } else {
        const nextTemp = Math.min(32, Math.max(16, Number(state.temp) + direction * 0.5));
        control('temp', nextTemp.toFixed(1));
      }
      return true;
    }
    if (key === ' ') {
      control('auto', state.auto === 1 ? 0 : 1);
      return true;
    }
    if (key.toLowerCase() === 'a') {
      control('maxauto', state.maxauto === 1 ? 0 : 1);
      return true;
    }
    if (key.toLowerCase() === 'p') {
      control('power', state.power === 1 ? 0 : 1);
      return true;
    }
    if (key.toLowerCase() === 'r') {
      control('recycle', state.recycle === 1 ? 0 : 1);
      return true;
    }
    return false;
  };

  const handleRegen = (key) => {
    if (key === 'ArrowUp' || key === 'ArrowDown') {
      const modes = ['Baixo', 'Normal', 'Alto'];
      control('regenMode', cycle(modes, state.regenMode, key === 'ArrowUp' ? 1 : -1));
      return true;
    }
    if (key === 'Enter') {
      control('onepedal', !state.onepedal);
      return true;
    }
    return false;
  };

  const handleGraph = (key) => {
    if (key === 'ArrowUp' || key === 'ArrowDown' || key === 'Enter') {
      const graphs = ['evConsumption', 'gasConsumption', 'carSpeed'];
      control('currentGraph', cycle(graphs, state.currentGraph, key === 'ArrowUp' ? -1 : 1));
      return true;
    }
    return false;
  };

  const onKeyDown = (event) => {
    if (event.ctrlKey || event.altKey || event.metaKey) return;

    if (event.key === '[' || event.key === 'PageUp') {
      event.preventDefault();
      cycleCard(-1);
      return;
    }
    if (event.key === ']' || event.key === 'PageDown') {
      event.preventDefault();
      cycleCard(1);
      return;
    }
    if ((event.key === 'ArrowLeft' || event.key === 'ArrowRight') &&
        (state.screen === 'main_menu' || state.screen === 'aircon')) {
      event.preventDefault();
      cycleCard(event.key === 'ArrowLeft' ? -1 : 1);
      return;
    }
    if (event.key === 'Backspace' || event.key === 'Escape') {
      event.preventDefault();
      if (state.cardId === 3) setCard(1);
      else showScreen('main_menu');
      return;
    }

    let handled = false;
    if (state.screen === 'main_menu') handled = handleMainMenu(event.key);
    else if (state.screen === 'display_selection') handled = handleDisplaySelection(event.key);
    else if (state.screen === 'color_selection') handled = handleColorSelection(event.key);
    else if (state.screen === 'aircon') handled = handleAircon(event.key);
    else if (state.screen === 'regen') handled = handleRegen(event.key);
    else if (state.screen === 'graph' || state.screen === 'graphs') handled = handleGraph(event.key);

    if (handled) event.preventDefault();
  };

  frameWindow.document.addEventListener('keydown', onKeyDown);
  control('fan', state.fan);
  control('temp', state.temp.toFixed(1));
  control('power', state.power);
  report();

  return {
    cleanup() {
      frameWindow.document.removeEventListener('keydown', onKeyDown);
    },
  };
}
