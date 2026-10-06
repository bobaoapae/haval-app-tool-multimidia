# Projetores - Displays do Cluster

## Visão Geral

O sistema de projetores permite renderizar interfaces customizadas nos displays físicos do cluster de instrumentos do veículo. O veículo possui múltiplos displays Android, e o app pode criar `Presentation` (janelas) em displays secundários.

```
┌──────────────────────────────────────────────────┐
│                  ProjectorManager                 │
│                                                   │
│  Descobre displays → Cria projetores por ID       │
│                                                   │
│  Display 1 ──→ InstrumentProjector2 (WebView)     │
│  Display 3 ──→ InstrumentProjector  (Native)      │
└──────────────────────────────────────────────────┘
```

## ProjectorManager

**Arquivo:** `managers/ProjectorManager.java`

Singleton que gerencia a descoberta de displays e criação de projetores.

### Como funciona

1. Na inicialização, obtém `DisplayManager` do sistema
2. Itera todos os displays disponíveis
3. Para cada display ID registrado em `projectorCreators`, cria o projetor correspondente
4. Se algum display ainda não existe (pode ser adicionado depois), registra um `DisplayListener` para criá-lo quando aparecer
5. Monitora `CAR_BASIC_ENGINE_STATE` para esconder/mostrar projetores quando a tela principal liga/desliga

```java
// Mapa de Display ID → Criador do projetor
projectorCreators.put(1, (ctx, disp) -> {
    instrumentProjector2 = new InstrumentProjector2(ctx, disp);
    instrumentProjector2.show();
});

projectorCreators.put(3, (ctx, disp) -> {
    instrumentProjector = new InstrumentProjector(ctx, disp);
    instrumentProjector.show();
});
```

### Ciclo de vida

- `initialize()` é chamado no final de `ServiceManager.initializeServices()`
- Escuta mudanças de `ENGINE_STATE`: valores `-1` ou `15` = tela desligada → `carMainScreenOff()`; outros valores → `carMainScreenOn()`
- Displays podem ser adicionados/removidos dinamicamente (o listener cuida disso)

## BaseProjector

**Arquivo:** `projectors/BaseProjector.kt`

Classe abstrata base que estende `android.app.Presentation` (janela em display secundário).

```kotlin
abstract class BaseProjector(outerContext: Context, display: Display) : Presentation(outerContext, display) {
    protected val handler = Handler(Looper.getMainLooper())

    // Garante que o bloco rode na UI thread
    fun ensureUi(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }

    abstract fun carMainScreenOff()
    abstract fun carMainScreenOn()
}
```

Métodos que toda implementação deve ter:
- `carMainScreenOff()` - chamado quando o motor/tela principal desliga
- `carMainScreenOn()` - chamado quando liga

## InstrumentProjector (Display 3)

**Arquivo:** `projectors/InstrumentProjector.kt`

Projetor nativo simples que mostra aviso de manutenção no Display 3. Usa views Android nativas (sem WebView).

### O que faz

- Mostra um `TextView` na parte inferior com informações de manutenção
- Exibe "Próxima Manutenção em: X Km ou Y dias"
- Pisca em vermelho quando a manutenção está próxima (< 1000 km ou < 30 dias)
- Atualiza a cada 60 segundos e quando o odômetro muda

### Como funciona

1. `onCreate()` cria um `RelativeLayout` transparente com um `TextView`
2. Registra listener de `IDataChanged` para monitorar `CAR_BASIC_TOTAL_ODOMETER`
3. Registra listener de `SharedPreferences` para reagir a mudanças de configuração
4. `updateView()` calcula km restantes e dias, formata texto, aplica animação de blink se necessário

### Configurações envolvidas

- `ENABLE_INSTRUMENT_REVISION_WARNING` - habilita/desabilita o aviso
- `INSTRUMENT_REVISION_KM` - quilometragem da próxima revisão
- `INSTRUMENT_REVISION_NEXT_DATE` - data da próxima revisão (millis)

## InstrumentProjector2 (Display 1) - WebView

**Arquivo:** `projectors/InstrumentProjector2.kt`

Projetor principal que renderiza uma interface customizada no Display 1 usando WebView. Mostra controle do A/C, menu de configurações do veículo, gráficos de consumo e regeneração.

### Arquitetura

```
Display 1 (1920x860 aprox.)
┌─────────────────────────────────────────────────┐
│                                                  │
│              ┌─────────┐                         │
│              │ WebView │ ← Área circular         │
│              │ (452px) │   centerX=1630           │
│              │         │   centerY=430            │
│              └─────────┘   radius=226             │
│                                                  │
└─────────────────────────────────────────────────┘
```

### Posicionamento

O WebView é colocado dentro de um `FrameLayout` circular clippado:

```kotlin
val radius = 226
val centerX = 1630
val centerY = 430

val circularView = FrameLayout(context)
val params = FrameLayout.LayoutParams(radius * 2, radius * 2)
params.leftMargin = centerX - radius
params.topMargin = centerY - radius
circularView.clipToOutline = true
circularView.outlineProvider = /* oval outline */
```

### Visibilidade

O projetor só é visível quando:
1. `ENABLE_INSTRUMENT_PROJECTOR` está ativo
2. `ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION` está ativo
3. O motor/tela principal está ligado
4. O cluster card view não é 0

### Bridge Kotlin ↔ JS

A comunicação entre Kotlin e JavaScript é unidirecional (Kotlin → JS) via `evaluateJavascript()`:

```kotlin
// Kotlin envia dados para o JS
evaluateJsIfReady(webView, "control('fan', $value)")
evaluateJsIfReady(webView, "focus('temp')")
evaluateJsIfReady(webView, "showScreen('aircon')")
```

O JS expõe 4 funções globais em `window`:

| Função JS | Chamada por | Propósito |
|-----------|-------------|-----------|
| `window.control(key, value)` | Kotlin: mudanças de dados | Atualiza estado reativo (fan, temp, power, etc.) |
| `window.focus(item)` | Kotlin: navegação | Muda foco no menu ou na tela do A/C |
| `window.showScreen(name)` | Kotlin: mudança de tela | Troca entre telas (main_menu, aircon, regen, graph) |
| `window.cleanup()` | Kotlin: limpeza | Limpa componente atual |

### Dados enviados ao JS

O `InstrumentProjector2` escuta `IDataChanged` e `IServiceManagerEvent` e despacha para o JS:

| Propriedade do veículo | Chamada JS |
|----------------------|------------|
| `CAR_HVAC_FAN_SPEED` | `control('fan', value)` |
| `CAR_HVAC_DRIVER_TEMPERATURE` | `control('temp', value)` |
| `CAR_HVAC_POWER_MODE` | `control('power', value)` |
| `CAR_HVAC_CYCLE_MODE` | `control('recycle', value)` |
| `CAR_HVAC_AUTO_ENABLE` | `control('auto', value)` |
| `CAR_HVAC_ANION_ENABLE` | `control('aion', value)` |
| `CAR_BASIC_OUTSIDE_TEMP` | `control('outside_temp', value)` |
| `CAR_BASIC_INSIDE_TEMP` | `control('inside_temp', value)` |
| `CAR_EV_SETTING_POWER_MODEL_CONFIG` | `control('evMode', label)` |
| `CAR_DRIVE_SETTING_DRIVE_MODE` | `control('drivingMode', label)` |
| `CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE` | `control('steerMode', label)` |
| `CAR_DRIVE_SETTING_ESP_ENABLE` | `control('espStatus', label)` |
| `CAR_CONFIGURE_PEDAL_CONTROL_ENABLE` | `control('onepedal', bool)` |
| `CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL` | `control('regenMode', label)` |
| `CAR_EV_INFO_ENERGY_OUTPUT_PERCENTAGE` | `control('evConsumption', value)` |
| `CAR_BASIC_INSTANT_FUEL_CONSUMPTION` | `control('gasConsumption', value)` |
| `CAR_BASIC_VEHICLE_SPEED` | `control('carSpeed', value)` |

### Fila de JS pendente

Se o WebView ainda não terminou de carregar, as chamadas JS são enfileiradas:

```kotlin
private fun evaluateJsIfReady(webView: WebView?, js: String) {
    if (webViewsLoaded.getOrDefault(webView, false)) {
        webView.evaluateJavascript(js, null)
    } else {
        pendingJsQueues.getOrPut(webView) { mutableListOf() }.add(js)
    }
}
```

Quando `onPageFinished()` dispara, a fila é executada.

## Frontend JavaScript

### Estrutura

O frontend fica em `cluster-widgets/air-control/src/` e é compilado para `app/src/main/res/raw/app.html` (inline).

```
src/
├── main.js          # Entry point, define window.* bridge functions
├── state.js         # StateManager reativo
├── utils/
│   └── createElement.js  # Utilitário tipo React para DOM
└── components/
    ├── mainMenu.js       # Menu principal com opções do veículo
    ├── aircon/
    │   ├── mainAcControl.js  # Tela do A/C
    │   ├── acTemperature.js  # Display de temperatura
    │   ├── fan.js            # Indicador de ventilador
    │   ├── focusElement.js   # Elemento com foco visual
    │   ├── infoTemperature.js # Info de temperatura
    │   └── status.js         # Status do A/C
    ├── regen/
    │   └── regenControl.js   # Tela de regeneração
    └── graphs/
        ├── graphs.js         # Tela de gráficos
        └── warpTunnel.js     # Efeito visual de túnel
```

### StateManager (state.js)

Sistema reativo simples que gerencia estado global. Similar a um store:

```javascript
var stateManager = new StateManager({
    screen: 'main_menu',          // Tela atual
    focusedMenuItem: 'option_4',  // Item focado no menu
    espStatus: 'ON',
    drivingMode: 'Normal',
    temp: 25, fan: 1, power: 1,  // Estado do A/C
    // ... etc
});

// API
stateManager.get(key)              // Lê valor
stateManager.set(key, value)       // Define valor (notifica listeners)
stateManager.subscribe(key, fn)    // Escuta mudanças de uma chave
```

Também expõe um `Proxy` chamado `state` para acesso direto: `state.fan = 5`.

### createElement (createElement.js)

Utilitário para criar elementos DOM de forma declarativa (similar a React.createElement):

```javascript
createElement('div', {
    className: 'container',
    style: { color: 'white', fontSize: '14px' },
    onClick: () => console.log('clicked'),
    children: [
        createElement('span', { children: ['Hello'] })
    ]
});

// Shorthands disponíveis
div({ className: 'x', children: [...] })
span({ style: { color: 'red' }, children: ['text'] })
```

### main.js - Entry Point e Bridge

O `main.js` é o ponto de entrada. Ele:

1. Renderiza a tela inicial (`main_menu`)
2. Escuta mudanças no `screen` do state para re-renderizar
3. Define as funções `window.*` que o Kotlin chama

```javascript
// Renderização
function render() {
    const screen = get('screen');
    // cleanup anterior
    if (screen === 'main_menu') currentComponent = createMainMenu();
    else if (screen === 'aircon') currentComponent = createAcControlScreen();
    else if (screen === 'regen') currentComponent = createRegenScreen();
    else if (screen === 'graph') currentComponent = createGraphScreen();
    appContainer.appendChild(currentComponent);
}

subscribe('screen', render);

// Bridge: funções chamadas pelo Kotlin via evaluateJavascript
window.showScreen = function(screenName) { setState('screen', screenName); };
window.focus = function(item) { setState('focusArea', item); };
window.control = function(key, value) { setState(key, value); };
```

## Botões do Volante

### Keycodes de navegação (menu do cluster)

Interceptados pelo `IInputListener` no `ServiceManager`:

| KeyCode | Ação | Screen.Key |
|---------|------|------------|
| 1024 | Cima | `UP` |
| 1025 | Baixo | `DOWN` |
| 1028 | Confirmar | `ENTER` |
| 1029 | Home | `HOME` |
| 1030 | Voltar | `BACK` |
| 1033 | Cima (longo) | `UP_LONG` |
| 1034 | Baixo (longo) | `DOWN_LONG` |
| 1037 | Confirmar (longo) | `ENTER_LONG` |
| 1039 | Voltar (longo) | `BACK_LONG` |

Estes só funcionam quando `ENABLE_CUSTOM_MENU` está ativo e `clusterCardView == 1`.

### Keycodes dos botões customizáveis

| KeyCode | Botão |
|---------|-------|
| 517 | Botão personalizado 1 |
| 1031 | Botão personalizado 2 |

Funcionam quando `ENABLE_STEERING_WHEEL_CUSTOM_BUTTONS` está ativo. A ação de cada botão é configurável (ver `SteeringWheelCustomActionType`).

## MainUiManager - Máquina de Estados

**Arquivo:** `models/MainUiManager.java`

Gerencia a navegação entre telas no display do cluster.

### Telas disponíveis (Screen)

| Tela | jsName | Descrição |
|------|--------|-----------|
| `MainMenu` | `main_menu` | Menu principal com opções de ESP, modo EV, modo de condução, A/C, direção, regeneração, gráficos |
| `AcControlScreen` | `aircon` | Controle do ar-condicionado |
| `RegenScreen` | `regen` | Nível de regeneração e controle de pedal único |
| `GraphicsScreen` | `graph` | Gráficos de consumo de energia/combustível |

### Navegação

O `MainMenu` define 7 itens com ações:

| Item | ID | Ação |
|------|----|------|
| ESP | `option_1` | Cicla ON/OFF |
| Modo EV | `option_2` | Cicla EV/HEV/PHEV |
| Modo Condução | `option_3` | Cicla Normal/Eco/Sport |
| A/C | `option_4` | Navega para AcControlScreen |
| Direção | `option_5` | Cicla Conforto/Normal/Esportiva |
| Regeneração | `option_6` | Navega para RegenScreen |
| Gráficos | `option_7` | Navega para GraphicsScreen |

A posição atual do menu é persistida em `SharedPreferences` (`LAST_CLUSTER_MENU_ITEM`, `LAST_CLUSTER_SCREEN`).

### Processamento de teclas

```
MainMenu.processKey():
  UP    → Move para item anterior (circular)
  DOWN  → Move para próximo item (circular)
  ENTER → Executa ação do item:
           - CycleValues: cicla o valor e envia para o carro
           - NavigateTo: navega para sub-tela
```

#### AcControlScreen (`jsName = "aircon"`)

Alterna entre controlar velocidade do ventilador e temperatura via ENTER. UP/DOWN ajustam ±0.5°C ou ±1 nível de fan. Long-press UP/DOWN vai para máximo/mínimo. BACK volta ao MainMenu. BACK_LONG alterna recirculação. ENTER_LONG alterna modo auto.

#### RegenScreen (`jsName = "regen"`)

UP/DOWN ciclam entre níveis de regeneração `[Normal(2), Médio(0), Alto(1)]`. ENTER força Médio. BACK/BACK_LONG voltam. ENTER_LONG alterna condução com pedal único.

#### GraphicsScreen (`jsName = "graph"`)

UP/DOWN/ENTER navegam entre 3 gráficos: `evConsumption`, `gasConsumption`, `carSpeed`. BACK/BACK_LONG voltam ao MainMenu. A navegação dispara `GRAPH_SCREEN_NAVIGATION` que chama `control('currentGraph', ...)` no WebView.

O gráfico de velocidade (`carSpeed`) tem comportamento especial: habilita um **cronômetro 0-100 km/h** e uma **animação de túnel** quando o modo Sport está ativo e aceleração rápida de 0 é detectada. O timer aborta após 15s se 100 km/h não for atingido.

## Guia: Como Adicionar uma Nova Tela ao Cluster

### Passo 1: Criar a Screen (Kotlin/Java)

Crie uma nova classe em `models/screens/` implementando `Screen`:

```java
package br.com.redesurftank.havalshisuku.models.screens;

public class MinhaNovaScreen implements Screen {
    private Screen returnScreen;

    @Override
    public String getJsName() {
        return "minha_nova_tela";  // Nome usado no JS
    }

    @Override
    public void initialize() {
        // Dispara evento para atualizar o projetor
        ServiceManager.getInstance().dispatchServiceManagerEvent(
            ServiceManagerEventType.UPDATE_SCREEN, this
        );
    }

    @Override
    public void processKey(Key key) {
        switch (key) {
            case BACK:
                // Volta ao menu anterior
                MainUiManager.getInstance().updateScreen(returnScreen);
                break;
            case UP:
                // Lógica de navegação para cima
                break;
            case DOWN:
                // Lógica de navegação para baixo
                break;
            case ENTER:
                // Lógica de confirmação
                break;
        }
    }

    @Override
    public void setReturnScreen(Screen previousScreen) {
        this.returnScreen = previousScreen;
    }
}
```

### Passo 2: Registrar no MainMenu

Em `MainMenu.java`, adicione ao `menuItems`:

```java
Screen minhaNovaScreen = new MinhaNovaScreen();
minhaNovaScreen.setReturnScreen(this);
minhaNovaScreen.initialize();

menuItems = Arrays.asList(
    // ... itens existentes ...
    new MenuItem("option_8", new MenuAction.NavigateTo(minhaNovaScreen))
);
```

### Passo 3: Criar o componente JS

Crie um novo arquivo em `cluster-widgets/air-control/src/components/`:

```javascript
// minhaTela.js
import { setState, subscribe } from '../state.js';
import { div, span } from '../utils/createElement.js';

export function createMinhaNovaScreen() {
    const container = div({
        style: {
            width: '100%', height: '100%',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: 'white', fontSize: '20px'
        },
        children: [
            span({ children: ['Minha Nova Tela'] })
        ]
    });

    // Subscrever a mudanças de estado se necessário
    const unsub = subscribe('minhaChave', (value) => {
        // Atualizar DOM
    });

    return {
        element: container,
        cleanup: () => { unsub(); }
    };
}
```

### Passo 4: Registrar no main.js

```javascript
import { createMinhaNovaScreen } from './components/minhaTela.js';

function render() {
    // ... existente ...
    else if (screen === 'minha_nova_tela') {
        currentComponent = createMinhaNovaScreen();
    }
    // ...
}
```

### Passo 5: Enviar dados do Kotlin (se necessário)

Em `InstrumentProjector2.kt`, adicione no listener de dados:

```kotlin
ServiceManager.getInstance().addDataChangedListener { key, value ->
    when (key) {
        CarConstants.MINHA_PROPRIEDADE.value -> {
            evaluateJsIfReady(webView, "control('minhaChave', $value)")
        }
    }
}
```

### Passo 6: Adicionar ao menu JS

Em `mainMenu.js`, adicione um novo item de menu visual correspondente ao `option_8`.

### Passo 7: Compilar o frontend

O frontend em `cluster-widgets/air-control/` precisa ser compilado e o resultado colocado em `app/src/main/res/raw/app.html`. O HTML gerado inclui todo o JS inline.
