# Serviços e Propriedades do Veículo

## ServiceManager

**Arquivo:** `managers/ServiceManager.java`

Singleton central que gerencia toda a comunicação com os serviços do veículo. Acessa os serviços via Shizuku (que fornece permissões de sistema).

### initializeServices()

Sequência de inicialização:

```
1. Limpa serviços anteriores (cleanup de binders e connections)
2. Verifica se Shizuku está vivo (pingBinder)
3. Conecta ao IntelligentVehicleControlService (via ShizukuBinderWrapper)
4. Conecta ao VoiceAdapterService (IBinderPool)
   ├── queryBinder(6) → IVehicle
   ├── queryBinder(8) → IDvr
   └── queryBinder(13) → IVehicleModel
5. Bind ao ClusterService (ServiceConnection)
6. Bind ao InputService (ServiceConnection)
7. Registra AccessibilityService via settings
8. Concede WRITE_SECURE_SETTINGS via Shizuku
9. Registra listener de dados (IListener)
10. Adiciona keys para monitoramento
11. Conecta ao ConnectivityManager (via ShizukuBinderWrapper)
12. Registra receivers de Bluetooth e Wi-Fi
13. Dispara dispatchAllData() - busca valores atuais de todas as keys
14. Aplica configurações iniciais (volume, monitoramento, AVAS, brilho, Frida, etc.)
15. Inicializa MainUiManager
16. Inicializa ProjectorManager
```

### Tabela de Serviços AIDL

| Serviço | Nome do Sistema | Interface AIDL | Para que serve |
|---------|----------------|----------------|----------------|
| **ControlService** | `com.beantechs.intelligentvehiclecontrol` | `IIntelligentVehicleControlService` | Ler/escrever propriedades do veículo. Serviço principal. |
| **VoiceAdapter (Pool)** | `com.beantechs.voice.adapter.VoiceAdapterService` | `IBinderPool` | Pool de binders para IVehicle, IDvr, IVehicleModel |
| **IVehicle** | via pool (id=6) | `IVehicle` | Controle de janelas, teto solar, cortinas |
| **IDvr** | via pool (id=8) | `IDvr` | Controle da câmera AVM (visão 360°) |
| **IVehicleModel** | via pool (id=13) | `IVehicleModel` | Informações do veículo (marca, modelo, tipo) |
| **ClusterService** | `com.autolink.clusterservice.ClusterService` | `IClusterService` / `IClusterCallback` | Comunicação com o cluster de instrumentos |
| **InputService** | `com.beantechs.inputservice` | `IInputService` / `IInputListener` | Interceptação de teclas do volante |
| **ConnectivityManager** | `connectivity` | `IConnectivityManager` | Controle de Wi-Fi tethering |

### Como os serviços são obtidos

Os serviços do sistema são acessados via reflection no `android.os.ServiceManager`:

```java
// Obtém IBinder do serviço do sistema
private static IBinder getSystemService(String serviceName) {
    return (IBinder) getService.invoke(null, serviceName);
}

// Wrapa com ShizukuBinderWrapper para ter permissões elevadas
IBinder controlBinder = new ShizukuBinderWrapper(
    getSystemService("com.beantechs.intelligentvehiclecontrol")
);
controlService = IIntelligentVehicleControlService.Stub.asInterface(controlBinder);
```

Para serviços que usam `bindService()` (ClusterService, InputService), é criada uma `ServiceConnection` normal.

## CarConstants

**Arquivo:** `models/CarConstants.java`

Enum Java com todas as chaves de propriedades do veículo. Cada constante mapeia para uma string que é a chave usada no `ControlService`.

### Organização por Domínio

| Prefixo | Domínio | Exemplos |
|---------|---------|----------|
| `car.basic.*` | Dados básicos do veículo | velocidade, odômetro, temperatura, portas, janelas, luzes, marcha |
| `car.hvac.*` | Ar-condicionado | temperatura, velocidade do ventilador, modo, recirculação |
| `car.ev.*` | Veículo elétrico | bateria, modo EV/HEV, recuperação de energia, AVAS |
| `car.drive_setting.*` | Configurações de condução | modo de condução, ESP, assistência da direção, retrovisores |
| `car.comfort_setting.*` | Conforto | aquecimento/ventilação dos bancos, massagem, memória |
| `car.intelligent_driving_setting.*` | Condução inteligente (ADAS) | assistência de faixa, frenagem automática, cruise |
| `car.hud_setting.*` | Head-Up Display | ativação, ângulo, altura, brilho |
| `car.ipk_setting.*` | Painel de instrumentos (nativo) | brilho |
| `car.frs_setting.*` | Monitoramento do motorista | detecção de distração |
| `car.configure.*` | Configurações do veículo | pedal único, grille ativa |
| `sys.*` | Sistema | volume, brilho da tela, AVM |

### Exemplo de uso

```java
// Ler velocidade atual
String speed = serviceManager.getData(CarConstants.CAR_BASIC_VEHICLE_SPEED.getValue());

// Alterar modo EV
serviceManager.updateData(
    CarConstants.CAR_EV_SETTING_POWER_MODEL_CONFIG.getValue(),
    "3"  // 0=HEV, 1=Prior.EV, 3=EV
);
```

### DEFAULT_KEYS vs KEYS_TO_SAVE

**DEFAULT_KEYS** - propriedades monitoradas em tempo real. O `ServiceManager` registra um listener para receber notificações quando qualquer uma dessas chaves muda. Inclui propriedades como velocidade, marcha, temperatura, status do A/C, bateria EV, brilho, etc.

**KEYS_TO_SAVE** - propriedades que são salvas/restauradas por perfil de usuário. Quando o usuário troca de perfil (`switchUser()`), os valores atuais dessas chaves são salvos em JSON e os do novo perfil são restaurados. Inclui principalmente configurações de condução (modo, ESP, direção) e ADAS.

O array completo de keys monitoradas é gerado por `getCombinedKeys()`:

```java
public String[] getCombinedKeys() {
    List<String> keys = new ArrayList<>();
    keys.addAll(List.of(CarConstants.FromArray(DEFAULT_KEYS)));    // Keys padrão
    keys.addAll(sharedPreferences.getStringSet("carMonitorProperties", new HashSet<>())); // Keys extras do usuário
    return keys.toArray(new String[0]);
}
```

## Sistema de Cache e Broadcasts

### Cache

O `ServiceManager` mantém um `Map<String, String> dataCache` com os últimos valores recebidos.

- **`getData(key)`** - Retorna do cache se disponível, senão busca do `controlService.fetchData()` e cacheia
- **`getUpdatedData(key)`** - Sempre busca do `controlService.fetchData()` e atualiza o cache (bypass do cache)
- **`updateData(key, value)`** - Envia para o `controlService.request()` para alterar o valor no veículo

### Broadcasts

Quando um dado muda, `OnDataChanged()` dispara:

1. **Broadcast genérico**: `android.intent.haval.{key}` com extra `value`
2. **Broadcast específico**: `android.intent.haval.{key}_{value}` (sem extras)
3. **Listeners internos**: Notifica todos os `IDataChanged` registrados
4. **Atualiza cache**: `dataCache.put(key, value)`
5. **Lógica de automação**: Executa automações configuradas (fechar janelas, desligar bluetooth, etc.)

### IDataChanged

Interface para receber mudanças de dados:

```java
public interface IDataChanged {
    void onDataChanged(String key, String value);
}

// Registrar listener
ServiceManager.getInstance().addDataChangedListener((key, value) -> {
    if (key.equals(CarConstants.CAR_BASIC_VEHICLE_SPEED.getValue())) {
        // fazer algo com a velocidade
    }
});
```

### ServiceManagerEventType

Enum de eventos internos (não relacionados a propriedades do veículo):

| Evento | Quando é disparado | Args |
|--------|--------------------|------|
| `CLUSTER_CARD_CHANGED` | Cluster muda de card/view | `int cardId` |
| `STEERING_WHEEL_AC_CONTROL` | Controle do A/C pelo volante | `SteeringWheelAcControlType` |
| `UPDATE_SCREEN` | Tela do cluster atualizada | `Screen screen` |
| `MENU_ITEM_NAVIGATION` | Navegação no menu do cluster | `String menuItemId` |
| `GRAPH_SCREEN_NAVIGATION` | Navegação nos gráficos | `String graphName` |
| `MAX_AUTO_AC_STATUS_CHANGED` | Estado do Max AC mudou | `int status` (0=off) |

## Como Ler/Escrever Propriedades

### Leitura

```java
// Do cache (rápido, pode estar desatualizado)
String value = ServiceManager.getInstance().getData(CarConstants.CAR_BASIC_VEHICLE_SPEED.getValue());

// Forçando busca do veículo (sempre atualizado)
String value = ServiceManager.getInstance().getUpdatedData(CarConstants.CAR_BASIC_VEHICLE_SPEED.getValue());

// Todos os dados cacheados
Map<String, String> all = ServiceManager.getInstance().getAllCurrentCachedData();
```

### Escrita

```java
// Altera propriedade no veículo (internamente usa controlService.request)
ServiceManager.getInstance().updateData(
    CarConstants.CAR_HVAC_FAN_SPEED.getValue(),
    "5"
);
```

### Monitoramento reativo

```java
// Registra listener para ser notificado de mudanças
ServiceManager.getInstance().addDataChangedListener(new IDataChanged() {
    @Override
    public void onDataChanged(String key, String value) {
        // Chamado sempre que qualquer propriedade monitorada muda
    }
});

// Ou para eventos do ServiceManager
ServiceManager.getInstance().addServiceManagerEventListener((event, args) -> {
    if (event == ServiceManagerEventType.CLUSTER_CARD_CHANGED) {
        int card = (int) args[0];
    }
});
```

### Adicionando novas propriedades ao monitoramento

Para monitorar uma propriedade que não está em `DEFAULT_KEYS`:

1. Adicione a constante ao array `DEFAULT_KEYS` no `ServiceManager`
2. Ou adicione dinamicamente ao SharedPreferences set `CAR_MONITOR_PROPERTIES`
3. Chame `ServiceManager.getInstance().updateMonitoringProperties()` para re-registrar
