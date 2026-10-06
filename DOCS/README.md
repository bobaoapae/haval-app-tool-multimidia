# HavalShisuku - Documentação

## O que é

HavalShisuku é um aplicativo Android que roda na central multimídia de veículos Haval/GWM. Ele se conecta aos serviços internos do sistema automotivo via AIDL, permitindo:

- Ler e alterar propriedades do veículo (ar-condicionado, janelas, modos de condução, etc.)
- Projetar interfaces customizadas nos displays do cluster de instrumentos
- Automatizar comportamentos (fechar janelas ao atingir velocidade, desligar bluetooth ao desligar, etc.)
- Remapear botões do volante para ações personalizadas
- Injetar scripts Frida em processos do sistema

## Arquitetura Geral

```
┌────────────────────────────────────────────────────────────────────┐
│                         BOOT DO SISTEMA                           │
│  BootReceiver → ForegroundService → Telnet → Shizuku → Services  │
└──────────────────────────┬─────────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                     ServiceManager (Singleton)               │
│                                                              │
│  ┌──────────────────────┐  ┌─────────────────────────────┐  │
│  │ AIDL Services:       │  │ Sistema de dados:            │  │
│  │ • ControlService     │  │ • dataCache (Map)            │  │
│  │ • IVehicle           │  │ • IDataChanged listeners     │  │
│  │ • IDvr               │  │ • Broadcasts por propriedade │  │
│  │ • IVehicleModel      │  │ • SharedPreferences          │  │
│  │ • IClusterService    │  │                              │  │
│  │ • IInputService      │  │                              │  │
│  │ • IConnectivityMgr   │  │                              │  │
│  └──────────────────────┘  └─────────────────────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ Automações:                                          │    │
│  │ • Fechar janelas/teto por velocidade/power off       │    │
│  │ • Bluetooth/Hotspot auto-off                         │    │
│  │ • Max AC automático                                  │    │
│  │ • Ventilação do banco com A/C                        │    │
│  │ • AVAS/Monitoramento forçado                         │    │
│  └──────────────────────────────────────────────────────┘    │
└──────────────────────┬──────────────────┬────────────────────┘
                       │                  │
          ┌────────────▼──┐    ┌──────────▼─────────┐
          │ MainUiManager │    │  ProjectorManager   │
          │ (Menu Cluster)│    │                     │
          │               │    │ Display 1 → IP2     │
          │ Screen (FSM)  │    │   (WebView circular)│
          │ ├─ MainMenu   │    │                     │
          │ ├─ AcControl  │    │ Display 3 → IP      │
          │ ├─ Regen      │    │   (Native TextView) │
          │ └─ Graphics   │    │                     │
          └───────────────┘    └─────────────────────┘
                                        │
                                        ▼
                               ┌─────────────────┐
                               │  Frontend JS     │
                               │  (WebView)       │
                               │                  │
                               │  state.js        │
                               │  main.js         │
                               │  components/     │
                               │  ├─ mainMenu     │
                               │  ├─ aircon       │
                               │  ├─ regen        │
                               │  └─ graphs       │
                               └─────────────────┘
```

## Estrutura do Projeto

```
app/src/main/
├── java/br/com/redesurftank/havalshisuku/
│   ├── MainActivity.kt                    # Tela de configurações (Jetpack Compose)
│   ├── SplashActivity.kt                  # Tela inicial
│   ├── broadcastReceivers/
│   │   ├── BootReceiver.java              # Recebe boot e inicia ForegroundService
│   │   ├── RestartReceiver.java           # Reinicia o serviço via AlarmManager
│   │   └── DispatchAllDatasReceiver.java  # Re-sincroniza dados do veículo
│   ├── services/
│   │   ├── ForegroundService.java         # Pipeline de inicialização principal
│   │   └── AccessibilityService.java      # Serviço de acessibilidade
│   ├── managers/
│   │   ├── ServiceManager.java            # Core: gerencia todos os serviços AIDL e dados
│   │   ├── ProjectorManager.java          # Gerencia displays e projetores
│   │   └── AutoBrightnessManager.kt       # Brilho automático dia/noite
│   ├── projectors/
│   │   ├── BaseProjector.kt               # Classe base (extends Presentation)
│   │   ├── InstrumentProjector.kt         # Display 3: TextView nativo (manutenção)
│   │   └── InstrumentProjector2.kt        # Display 1: WebView circular (menu/AC/regen)
│   ├── models/
│   │   ├── CarConstants.java              # Enum com todas as chaves de propriedades do veículo
│   │   ├── SharedPreferencesKeys.kt       # Enum com chaves de configuração do app
│   │   ├── ServiceManagerEventType.java   # Enum de eventos internos
│   │   ├── SteeringWheelCustomActionType.kt # Ações dos botões do volante
│   │   ├── MainUiManager.java             # Máquina de estados para navegação do cluster
│   │   ├── CarInfo.kt                     # Dados do veículo (marca, modelo)
│   │   └── screens/
│   │       ├── Screen.java                # Interface base para telas do cluster
│   │       ├── MainMenu.java              # Menu principal do cluster
│   │       ├── AcControlScreen.java       # Tela de controle do A/C
│   │       ├── RegenScreen.java           # Tela de regeneração
│   │       └── GraphicsScreen.java        # Tela de gráficos
│   ├── listeners/
│   │   ├── IDataChanged.java              # Interface para mudanças de dados
│   │   └── IServiceManagerEvent.java      # Interface para eventos do ServiceManager
│   ├── utils/
│   │   ├── TelnetClientWrapper.java       # Cliente telnet para executar comandos root
│   │   ├── ShizukuUtils.java              # Wrapper para comandos via Shizuku
│   │   ├── FridaUtils.java                # Deploy e injeção de scripts Frida
│   │   ├── IPTablesUtils.java             # Desbloqueio de iptables
│   │   └── TermuxUtils.java               # Integração com Termux (SSHD)
│   └── ui/
│       ├── components/CommonComponents.kt # Componentes reutilizáveis (SettingCard, etc.)
│       └── theme/                         # Tema Material3
├── aidl/                                  # Interfaces AIDL dos serviços do sistema
├── res/raw/
│   ├── app.html                           # HTML carregado pelo WebView do cluster
│   ├── com_beantechs_accountservice.js    # Script Frida
│   ├── com_ts_car_power_controller_core.js
│   └── system_server.js
└── AndroidManifest.xml

cluster-widgets/air-control/               # Fonte do frontend JS (compilado → app.html)
├── src/
│   ├── main.js                            # Entry point, bridge window.* functions
│   ├── state.js                           # StateManager reativo
│   ├── utils/createElement.js             # Utilitário DOM tipo React
│   └── components/
│       ├── mainMenu.js                    # Menu principal
│       ├── aircon/                        # Componentes do A/C
│       ├── regen/                         # Componentes de regeneração
│       └── graphs/                        # Gráficos de consumo
└── index.html
```

## Como os Componentes se Conectam

1. **Boot** → `BootReceiver` inicia `ForegroundService`
2. **ForegroundService** conecta via Telnet (porta 23, localhost), executa `libshizuku.so` para iniciar Shizuku
3. **Shizuku** fornece acesso privilegiado → `ServiceManager.initializeServices()` conecta a todos os serviços AIDL
4. **ServiceManager** é o hub central: recebe dados do veículo via `IListener`, despacha para `IDataChanged` listeners e via broadcasts
5. **ProjectorManager** descobre displays físicos e cria projetores (WebView no Display 1, TextView no Display 3)
6. **MainUiManager** controla a navegação entre telas no cluster, recebendo key events dos botões do volante
7. **InstrumentProjector2** comunica com o JS via `evaluateJavascript()`, chamando `window.control()`, `window.focus()`, `window.showScreen()`
8. **MainActivity** (Jetpack Compose) é a tela de configurações, salva em SharedPreferences com DeviceProtectedStorage

## Documentação Detalhada

| Documento | Conteúdo |
|-----------|----------|
| [inicializacao.md](inicializacao.md) | Pipeline completo de boot: BootReceiver → Telnet → Shizuku → Serviços |
| [servicos-e-propriedades.md](servicos-e-propriedades.md) | ServiceManager, AIDL, CarConstants, cache, broadcasts |
| [projetores.md](projetores.md) | ProjectorManager, displays, WebView, JS bridge, como criar novas telas |
| [configuracoes.md](configuracoes.md) | SharedPreferences, PreferenceKeys, como adicionar novas configurações |
| [features.md](features.md) | Lista de todas as features/opções da tela de configuração |
