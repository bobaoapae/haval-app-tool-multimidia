# Pipeline de Inicialização

## Visão Geral

```
Boot Android
    │
    ▼
BootReceiver.onReceive()
    │  Registra timestamp do boot
    │  Inicia ForegroundService
    ▼
ForegroundService.onStartCommand()
    │
    ├─ 1. Verifica se já está rodando (guard)
    ├─ 2. Cria notificação foreground
    ├─ 3. Verifica integridade da instalação (UID ≤ 10999)
    │
    ▼ (background thread)
    ├─ 4. Conecta via Telnet (localhost:23)
    ├─ 5. Encontra/executa libshizuku.so
    ├─ 6. Aguarda Shizuku binder (timeout 5s)
    │
    ▼ shizukuBinderReceived()
    ├─ 7. Verifica permissão Shizuku
    │
    ▼ checkService()
    ├─ 8. Inicia SSHD (Termux) se instalado
    ├─ 9. Inicia ADB se não estiver rodando
    ├─ 10. Define swappiness = 60
    ├─ 11. Desbloqueia IPTables (loop a cada 15s)
    ├─ 12. ServiceManager.initializeServices()
    ├─ 13. Registra listener para restart do IVC
    └─ 14. Registra DispatchAllDatasReceiver
```

## Detalhamento

### 1. BootReceiver

**Arquivo:** `broadcastReceivers/BootReceiver.java`

Recebe o broadcast `BOOT_COMPLETED` do Android. Registra o timestamp do boot no `ServiceManager` e inicia o `ForegroundService`.

```java
// Registra quando o boot foi recebido (usado para telemetria)
ServiceManager.getInstance().setTimeBootReceived(SystemClock.uptimeMillis());

// Inicia como foreground service (obrigatório no Android moderno)
Intent serviceIntent = new Intent(context, ForegroundService.class);
context.startForegroundService(serviceIntent);
```

### 2. ForegroundService

**Arquivo:** `services/ForegroundService.java`

Serviço principal que orquestra toda a inicialização. Implementa `Shizuku.OnBinderDeadListener` para detectar quando o Shizuku morre.

#### Verificação de UID

O app precisa ser instalado com um exploit que garante UID ≤ 10999. UIDs maiores que esse valor não conseguem conectar via Telnet na porta 23 (bloqueio do sistema). Se o UID for inválido, o serviço mostra um toast e para.

```java
if (selfPackageInfo.uid > 10999) {
    // Não pode iniciar automaticamente
    return START_NOT_STICKY;
}
```

#### Conexão Telnet e Shizuku

A central multimídia tem um servidor Telnet rodando em `127.0.0.1:23` com acesso root. O app se conecta e:

1. Busca o caminho do `libshizuku.so` dentro de `/data/app` (cacheia em SharedPreferences)
2. Executa o binário para iniciar o servidor Shizuku
3. Se detecta que matou um processo antigo do Shizuku, espera 5 segundos antes de prosseguir

```java
var telnetClient = new TelnetClientWrapper();
telnetClient.connect("127.0.0.1", 23);

// Encontra libshizuku.so
String filePath = telnetClient.executeCommand("find /data/app -name libshizuku.so");

// Executa para iniciar o servidor Shizuku
telnetClient.executeCommand(filePath);
```

Após iniciar o Shizuku, registra um listener para receber o binder. Se o binder não chegar em 5 segundos, reinicia o serviço.

### 3. TelnetClientWrapper

**Arquivo:** `utils/TelnetClientWrapper.java`

Wrapper sobre `org.apache.commons.net.telnet.TelnetClient`. Funcionalidades:

- `connect(host, port)` - conecta com timeout de 1 segundo
- `executeCommand(command)` - envia comando e espera resposta (prompt `:/ #`)
  - Timeout de 5 segundos
  - Remove echo do comando e ANSI escapes da resposta
  - Retorna output limpo
- `disconnect()` - fecha a conexão

### 4. Shizuku

Shizuku permite executar comandos e acessar serviços do sistema com permissões elevadas sem root direto. O app usa:

- **ShizukuUtils** - Executa comandos shell via `Shizuku.newProcess()` (ex: `pm grant`, `settings put`, `pkill`)
- **ShizukuBinderWrapper** - Wrapper de `IBinder` que roteia chamadas pelo Shizuku, permitindo acessar serviços do sistema como se fosse um app do sistema

### 5. checkService() - Inicialização dos Serviços

Após o Shizuku estar pronto, `checkService()` é chamado:

1. **SSHD**: Verifica se Termux está instalado. Se sim, verifica se `sshd` está rodando e inicia se necessário
2. **ADB**: Verifica se `adbd` está rodando e inicia com `start adbd` se necessário
3. **Swappiness**: Define `vm.swappiness = 60`
4. **IPTables**: Desbloqueia INPUT/OUTPUT com `IPTablesUtils.unlockInputOutputAll()`. Executa em loop (a cada 15s se sucesso, 5s se falha)
5. **ServiceManager**: Chama `ServiceManager.getInstance().initializeServices(context)`. Se falhar, reinicia o serviço

### 6. Mecanismo de Restart

O `ForegroundService` implementa restart automático em várias situações:

- Timeout do Shizuku binder (5s sem resposta)
- Shizuku binder morre (`onBinderDead()`)
- `ServiceManager.initializeServices()` falha
- Broadcast `com.beantechs.intelligentvehiclecontrol.INIT_COMPLETED` recebido após já estar rodando

O restart funciona via `AlarmManager`:

```java
private synchronized void restart() {
    // Limpa estado
    isShizukuInitialized = false;
    isServiceRunning = false;

    // Agenda restart em 1 segundo via AlarmManager
    Intent broadcastIntent = new Intent(this, RestartReceiver.class);
    PendingIntent pendingIntent = PendingIntent.getBroadcast(...);
    alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent);

    stopSelf();
}
```

O `RestartReceiver` recebe o alarm e chama `startForegroundService()` novamente.

### 7. Frida

Se a configuração `ENABLE_FRIDA_HOOKS` estiver ativa, o deploy do Frida é enfileirado como tarefa pendente e executado após a inicialização dos serviços:

1. `FridaUtils.ensureFridaServerRunning()` - Verifica/inicia o servidor Frida
2. `FridaUtils.injectAllScripts()` - Injeta scripts em processos-alvo
3. Se `ENABLE_FRIDA_HOOK_SYSTEM_SERVER` ativo, injeta no `system_server` após 10s de delay

Os scripts Frida ficam em `res/raw/` (ex: `com_beantechs_accountservice.js`, `system_server.js`).
