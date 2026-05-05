# Ambiente de desenvolvimento no emulador AAOS

Este documento descreve como rodar o app **Haval Shisuku** em um emulador
AAOS (Automotive OS) sem precisar de uma multimídia Haval real, quais são os
pontos fortes desse fluxo, suas limitações, e o impacto que ele tem no dia
a dia de desenvolvimento.

> Toda a infraestrutura descrita aqui só é ativada quando o app detecta que
> está rodando em um emulador (`Build.HARDWARE in {"goldfish", "ranchu"}`,
> ou fingerprint começando em `generic`/`unknown`/`sdk`). Em hardware real
> nada do que está aqui executa — os caminhos do `IIntelligentVehicleControlService`,
> Shizuku, Telnet e afins seguem inalterados.

---

## 1. Para quem é

- **Desenvolvedores que não têm a multimídia Haval em mãos** e precisam
  iterar em mudanças de UI do cluster, lógica de menu, integração com VHAL,
  comportamento dos botões de volante etc.
- **Quem quer reproduzir bugs** que dependem de combinações específicas de
  velocidade, marcha, ignição, EPB, temperatura externa, nível de bateria/
  combustível — sem precisar dirigir o carro.
- **Pull requests e revisão de código**: validação visual rápida de
  alterações no projetor do cluster sem precisar instalar em hardware.

---

## 2. Início rápido

### Pré-requisitos

- macOS ou Linux com [Android Studio](https://developer.android.com/studio)
  e o **AAOS system image** instalado.
- Uma AVD criada a partir de um system image automotivo. Convencionamos o
  nome `haval_h6_infotainment` (qualquer AVD AAOS serve, mas o auto-detect
  do `launch.sh` prefere AVDs cujo nome contenha `haval` ou `gcar`).
- O SDK Android em `~/Library/Android/sdk` (caminho padrão do Android
  Studio no macOS). Caso esteja em outro lugar, exporte
  `ANDROID_SDK_ROOT=/caminho/para/sdk` antes de rodar os scripts.

### Comando único — do zero ao cluster funcionando

```bash
./launch.sh
```

Esse script:

1. Detecta a AVD instalada (pega automaticamente uma com `haval`/`gcar` no
   nome, ou a primeira da lista).
2. Verifica se um emulador já está rodando — se sim, pula o boot. Se houver
   apenas um *fantasma* `emulator-XXXX offline` no `adb devices` (sem
   processo `qemu-system` real), limpa o estado do `adb` e segue.
3. Sobe o emulador em background (logs em `./emulator.log`), espera o
   `sys.boot_completed` ficar `1`, captura o serial real do dispositivo
   (ignorando fantasmas) e encadeia em `./emulator-setup.sh`.

Variáveis úteis:

| Variável | Efeito |
|---|---|
| `FRESH=1 ./launch.sh` | Boot limpo (`-no-snapshot-load`) |
| `WIPE=1 ./launch.sh` | Apaga `userdata` antes do boot (`-wipe-data`) |
| `AVD=outra_avd ./launch.sh` | Usa AVD específica |
| `./launch.sh emulator-5556` | Encadeia setup em um emulador já existente |

### Controle do cluster pelo teclado

Em um segundo terminal, com foco neste terminal (não no emulador):

```bash
./cluster-keys.sh
```

Mapa de teclas (impresso no início e disponível por `?`):

| Tecla | Ação | Código vendor |
|---|---|---|
| `w` / `k` / `↑` | UP | 1024 |
| `s` / `j` / `↓` | DOWN | 1025 |
| Espaço / Enter | ENTER | 1028 |
| `h` | HOME | 1029 |
| `b` / Backspace / Esc | BACK | 1030 |
| `W` (maiúsculo) | UP_LONG | 1033 |
| `S` | DOWN_LONG | 1034 |
| `E` | ENTER_LONG | 1037 |
| `B` | BACK_LONG | 1039 |
| `1` | Botão custom #1 | 517 |
| `2` | Botão custom #2 | 1031 |
| `q` | Sair | — |

### Dados do veículo (velocidade, marcha, etc.)

Pelo painel **Extended Controls → Car sensor data** do emulador:

- **Vehicle speed** → cluster atualiza km/h em tempo real
- **Gear** → muda marcha (P/R/N/D)
- **Ignition state** → liga/desliga
- **Parking brake** → EPB on/off
- **Outside temperature** → temperatura externa
- **Fuel level** / **Battery level** → percentuais

Para acompanhar pelo terminal:

```bash
adb logcat -s VehicleStatus:I
```

---

## 3. O que está envolvido

### Componentes do app que entram em ação no emulador

| Classe | Papel |
|---|---|
| `EmulatorVehicleBridge` | Substitui o `IIntelligentVehicleControlService`. Usa `CarPropertyManager` para assinar propriedades VHAL e injeta os valores no `dataCache` do `ServiceManager`. |
| `EmulatorInputBridge` | Recebe broadcasts `br.com.redesurftank.havalshisuku.SIM_STEERING_KEY` (com extra `keycode`) e dispara `ServiceManager.dispatchSimulatedInputKey(int)`. Substitui o `com.beantechs.inputservice` do hardware. |
| `ServiceManager.ensureSharedPreferencesForEmulator()` | Anexa `SharedPreferences`, popula o `dataCache` com defaults parseáveis (ESP, modo EV, marcha, regen, HVAC), aquece o `MainUiManager`, e força `currentCard = 1`. Sem isso, várias telas dão NPE em `Integer.parseInt(null)` na primeira tecla pressionada. |
| `ServiceManager.dispatchSimulatedInputKey(int)` | Espelho do `IInputListener.dispatchKeyEvent` proprietário: roteia o keycode para `MainUiManager.handleGeneralKeyEvents` ou `handleSteeringWheelCustomButton`. |
| `ServiceManager.updateData(...)` (fallback) | Quando `controlService` é null (emulador), grava direto no `dataCache` e aciona `OnDataChanged` — o mesmo caminho que o callback do serviço real dispararia. Isso é o que faz, por exemplo, `RegenScreen` UP/DOWN refletir visualmente no cluster. |
| `ProjectorManager` | Detecta o display `Overlay #1` automaticamente pelo nome e renderiza o `InstrumentProjector2` lá. |

### Scripts do repositório

| Script | Função |
|---|---|
| `launch.sh` | Boot da AVD + espera por `sys.boot_completed` + chamada do `emulator-setup.sh`. |
| `emulator-setup.sh` | Cria o display de overlay 1920×720, garante os pacotes do cluster AAOS habilitados para o usuário 10, contorna dois bugs do `systemui` da imagem, dá grant de permissões `CAR_SPEED`/`CAR_ENERGY` e abre o app. |
| `cluster-keys.sh` | Loop interativo de leitura de teclas que envia broadcasts do *steering-wheel*. |

### Display de overlay

O cluster real do carro tem ~1920×720. No emulador criamos um *overlay
display* virtual com essas dimensões via:

```bash
adb shell settings put global overlay_display_devices '1920x720/160'
```

O `ProjectorManager` reconhece o display pelo nome (`Overlay #1`) e
renderiza o `InstrumentProjector2` (com `WebView` transparente) nele.
Aparece como uma janela flutuante separada na UI do emulador.

---

## 4. Pontos fortes

- **Iteração rápida**: ciclos de `gradle assembleDebug` + `adb install` +
  reset do app levam segundos em vez de minutos. Não precisa abrir o carro,
  conectar OBD, fazer pareamento Shizuku, etc.
- **Reprodutibilidade**: condições de carro (velocidade exata, gear, EPB)
  são determinísticas via Extended Controls. Bugs ligados a thresholds
  (ex.: ações dependentes de velocidade) ficam triviais de reproduzir.
- **Cobertura visual do cluster**: o `WebView` de instrumentos roda igual
  ao do hardware. Mudanças de tema HTML/CSS são visíveis instantaneamente.
- **CI-amigável**: como tudo é script, dá para rodar em runners (GitHub
  Actions com `reactivecircus/android-emulator-runner`, por exemplo) para
  smoke tests do projetor.
- **Sem risco de brick**: nenhum write em `/system`, nenhum exploit
  Shizuku/Telnet — o caminho do emulador é um *bypass* limpo.
- **Multi-user AAOS preservado**: o app continua rodando como usuário 10
  (perfil de motorista), igual ao hardware, então diferenças de stack de
  janelas, ocupant zones, etc. ficam visíveis.

---

## 5. Implicações no fluxo de desenvolvimento

### O que muda em relação ao hardware

1. **Nem todos os branches de código são exercitados.** O `if (isEmulator)`
   no `ForegroundService.onStartCommand` pula Shizuku, Telnet, Frida,
   verificações de UID, integridade de instalação, init de `iptables`,
   bind ao `IIntelligentVehicleControlService` etc. Tudo o que depende
   *diretamente* desses serviços precisa de hardware para validação final.

2. **Permissões precisam ser concedidas para o usuário 10.** Em AAOS o
   perfil do motorista é `--user 10`, e `pm grant <permissao>` sem a flag
   `--user 10` só afeta o usuário 0, que **não** é o que renderiza no
   display principal. O `emulator-setup.sh` já cuida disso para
   `CAR_SPEED` e `CAR_ENERGY`. Se você adicionar dependência em outra
   permissão `dangerous`, lembre-se de incluí-la no script.

3. **Pacotes do cluster AAOS devem ficar habilitados.** Se você desabilitar
   `com.android.car.cluster.osdouble` ou `com.android.car.cluster.home`,
   o `car_service` fica em loop tentando bindá-los para o usuário 10 e
   derruba o `systemui` junto, causando flicker e perda de foco do app.
   Mantenha-os habilitados — eles renderizam em displays virtuais
   internos (4 e 6) que são invisíveis para nós.

4. **Workarounds aplicados ao `systemui`**: a imagem AAOS do emulador tem
   dois bugs:

   - `KeyguardSliceProvider.onCreateSliceProvider` faz NPE em
     `NotificationMediaManager.addCallback`. Desabilitamos somente esse
     provider via `pm disable`.
   - `SystemUIService.create()` faz `SecurityException` por falta de
     `BLUETOOTH_CONNECT`. Concedemos o runtime permission via `pm grant`.

   Sem esses dois fixes, `systemui` cai em crash-loop a ~4 Hz e o sistema
   fica instável. **Não revertam essas linhas no `emulator-setup.sh`** sem
   testar em uma imagem AAOS atualizada que tenha resolvido ambos.

5. **Persistência entre execuções.** O `LAST_CLUSTER_SCREEN` em
   `SharedPreferences` antes era restaurado a cada start, então reabrir o
   app caía no submenu anterior. O `ensureSharedPreferencesForEmulator()`
   força esse pref para `main_menu` em todo bring-up — mantém o teste
   reprodutível.

### Onde NÃO confiar somente no emulador

- **Comportamento do `IIntelligentVehicleControlService`**: o emulador
  tem só um *stub* (fallback no `dataCache`). Lógicas que dependem de
  callbacks do serviço real, `setBoxConfig`, `request("cmd.common.request.set", ...)`,
  HVAC suspension, etc. precisam de hardware.
- **Integração Shizuku/Telnet/Frida**: completamente desabilitada no
  emulador. Bugs dessas integrações **só** se reproduzem em hardware.
- **Botões custom do volante (517, 1031)** que disparam ações como
  *change power mode* ou *change regen level*: o keypress é roteado, mas
  a ação propriamente dita acaba caindo no fallback de `updateData` —
  o estado é gravado no cache local, mas o carro real não recebe.
  Em hardware, vai pelo `controlService.request(...)`.
- **Áudio do carro, telefonia (TBox), DVR**: não simulados.
- **Problemas de timing/race conditions específicos do hardware**: o
  emulator timing é diferente. Crashes que aparecem só em hardware (ex.:
  ordem de inicialização de serviços Beantechs) vão precisar de sessão
  com a multimídia.

### Boas práticas

- Quando alterar código que pode rodar em ambos os caminhos, teste no
  emulador primeiro (rápido), depois em hardware antes de mergear.
- Quando alterar lógica que depende de serviço Beantechs/Autolink, marque
  no PR explicitamente que **não dá** para validar no emulador.
- Ao adicionar uma nova `SharedPreferencesKeys` lida em `Screen.initialize`
  ou em `processKey`, considere se um valor default precisa ser semeado em
  `ensureSharedPreferencesForEmulator()` — se o caminho passar por
  `Integer.parseInt`/`Float.parseFloat`, precisa.

---

## 6. Limitações conhecidas

| Limitação | Impacto | Workaround |
|---|---|---|
| Cluster AAOS *stock* não renderiza por baixo do nosso projetor | Não dá para "compor" o velocímetro AAOS com nossa UI; o `ClusterOsDoubleActivity` é só um espelho de display interno e fica preto se acionado isoladamente | Aceito por design — nosso projetor é o único renderer no overlay |
| `inject-vhal-event` é one-shot para sensores contínuos (speed, fuel, battery) | O VHAL fake do emulador sobrescreve imediatamente | Usar `inject-continuous-events` com sample-rate alto, ou os sliders persistentes do Extended Controls |
| `ENGINE_RPM` (0x11600201) requer `CAR_ENGINE_DETAILED`, que é `signature\|privileged` | RPM fica zero no cluster | Sem solução para apps não-system; aceitar |
| Display de overlay aparece como janela flutuante separada na UI do emulador | Não é óbvio para quem está abrindo a primeira vez — pode parecer que o cluster sumiu | Procurar a janela "Overlay #1" do emulador |
| `systemui` precisa de dois workarounds em runtime para não cair em crash-loop | Sem o `emulator-setup.sh`, expectativa de instabilidade visual | Sempre rodar o `emulator-setup.sh` |
| Estado do `adb` às vezes mantém um *fantasma* `emulator-5554 offline` mesmo após o `qemu` morrer | `adb wait-for-device` falha com "more than one device" | `launch.sh` detecta isso e faz `adb kill-server`/`start-server` automaticamente |
| AAOS executa o app para usuário 0 *e* usuário 10 simultaneamente | Dois processos Haval rodando, podem confundir logs | Filtrar logs por PID ou pelo prefixo `u10` em `dumpsys` |
| Botões custom (517/1031) só "atualizam" estado local | Ação real (mudar power mode, etc.) não acontece sem `controlService` | Aceito; basta saber ao testar |
| `INFO_FUEL_CAPACITY` e `INFO_EV_BATTERY_CAPACITY` da imagem são fixos (15 L / 150 kWh) | Cálculo de % parte desses valores | Mudar a AVD ou aceitar os números do emulador |

---

## 7. Solução de problemas

### "App é preto" / não vejo o app no display principal

Causa mais comum: `am start` foi disparado para o usuário 0. Em AAOS o
foreground é o usuário 10. O `emulator-setup.sh` já lança como
`--user 10 -n .../.SplashActivity`. Confira:

```bash
adb -e shell dumpsys activity activities | grep -A2 "Display #0"
```

Esperado: `topResumedActivity = u10 br.com.redesurftank.havalshisuku/.MainActivity`.

### "Não vejo o cluster"

A janela de overlay é separada da janela principal do emulador na UI do
Android Studio / `emulator -avd`. Procure por uma janela secundária
"Overlay #1". Confirme que existe:

```bash
adb -e shell dumpsys window displays | grep -A2 "Overlay #1"
```

Se ela existe mas não está visível, é porque o emulador está oculto em
algum espaço de janela do macOS — Mission Control costuma resolver.

### "UP/DOWN não muda nada no submenu"

Se você está num submenu como `RegenScreen` ou `AcControlScreen`, a tecla
chama `serviceManager.updateData(...)`. Em hardware isso vai pro
`controlService.request(...)`. No emulador (essa branch) o fallback grava
no `dataCache` e dispara `OnDataChanged`, que aciona o listener do
projetor. Se mesmo assim não vê mudança visual:

1. Cheque o evento que está sendo disparado:

   ```bash
   adb -e logcat -s ServiceManager:W | grep "Dispatching service manager event"
   ```

2. Se o evento sai mas o WebView não muda, é problema de tema HTML — não
   do bridge. Inspecione com `chrome://inspect`.

### "Tudo virou crash storm depois que mexi no `pm disable`"

`pm disable com.android.car.cluster.osdouble` provoca crash-loop. Reverta:

```bash
adb -e shell pm enable --user 10 com.android.car.cluster.osdouble
adb -e shell pm enable --user 10 com.android.car.cluster.home
```

### "`launch.sh` reclama de `unbound variable`"

Bug antigo. Se aparecer, faça `git pull` — a expansão segura de array
sob `set -u` (`${EMU_FLAGS[@]+"${EMU_FLAGS[@]}"}`) já está aplicada.

### "`adb` insiste que tem um emulador rodando mas não tem"

Fantasma persistente do `adb` em `127.0.0.1:5555`. O `launch.sh` detecta
e limpa automaticamente; manualmente:

```bash
adb disconnect emulator-5554
adb kill-server && adb start-server
```

---

## 8. Estendendo o suporte

Se você precisar simular mais sinais VHAL além dos 9 atuais:

1. Descubra o ID hexa da propriedade no AAOS:
   ```bash
   adb -e shell cmd car_service get-carpropertyconfig 0xXXXX
   ```
2. Adicione a constante em `EmulatorVehicleBridge.java` (siga o padrão
   `PROP_*`).
3. Adicione um caso em `handlePropertyChange()` mapeando para o
   `CarConstants.*` correspondente do app.
4. Se for um número que precisa de capacidade de referência (como fuel
   level vs `INFO_FUEL_CAPACITY`), leia a capacidade em `injectCurrentValues()`
   e normalize.
5. Se for uma propriedade com permissão diferente, adicione no
   `AndroidManifest.xml` *e* no `emulator-setup.sh` (caso seja `dangerous`).

Para adicionar um novo botão simulado de volante, basta enviar um keycode
inédito via broadcast e adicionar o caso correspondente em
`ServiceManager.dispatchSimulatedInputKey(int)`.

---

## 9. Referências cruzadas

- Origem do problema do `pm disable` derrubando `systemui`: ver comentário
  longo em `emulator-setup.sh` antes do `pm enable` dos pacotes de cluster.
- Origem do detect de fantasmas no `adb`: ver bloco *Step 1* em
  `launch.sh`.
- Onde o `ForegroundService` decide pegar o atalho do emulador:
  `ForegroundService.onStartCommand`, bloco `if (isEmulator)`.
- Caminho do dispatch de tecla custom de volante:
  `EmulatorInputBridge` → `ServiceManager.dispatchSimulatedInputKey` →
  `MainUiManager.handleGeneralKeyEvents` → `Screen.processKey` →
  (se aplicável) `ServiceManager.updateData` → fallback `OnDataChanged`.
