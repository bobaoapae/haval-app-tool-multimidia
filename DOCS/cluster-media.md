# Cluster Media Widget

Widget de mídia exibido no cluster do veículo, mostrando em tempo real a faixa tocando via **Android Auto**, **Bluetooth (AVRCP)** ou **Rádio FM/AM**.

---

## Visão geral

O widget é renderizado no `InstrumentProjector2` via WebView e controlado por sinais ITS (Intelligent Transportation System) do veículo, NLS (Notification Listener Service) e polling de logcat para Android Auto.

Existe em dois formatos:

- **Mini bar** — barra fixa no rodapé do cluster (ativada/desativada em Configurações → Mini Bar de Mídia)
- **Tela de mídia** — tela dedicada acessada pelo menu do cluster (option 8)

---

## Fontes de mídia

### Rádio FM/AM

Leitura via ITS (beantechs pub/sub):

| Chave ITS | Descrição |
|---|---|
| `sys.radio.cur_channel_info` | `{freqKhz, ?, rdsStatus, playing}` — frequência atual |
| `sys.radio.play_state` | `"1"` = tocando, `"0"` = pausado |
| `sys.radio.play_control_action` | `"6"` = seek next, `"7"` = seek prev, `"0"` = pause, `"1"` = play |
| `sys.radio.fm_favorites_station_list` | `{freqKhz1, freqKhz2, ...}` — lista de favoritos |
| `sys.radio.search_state` | `"3"` = seek em andamento, `"0"` = idle |
| `sys.basic.audio_source_app` | Fonte ativa: `"1"`/`"10"` = FM, `"2"`/`"11"` = AM, `"3"` = BT, etc. |

> **Atenção:** `sys.radio.cur_channel_info` retém `playing=1` em cache mesmo após a rádio parar (valor *stale*). Sempre confirmar com `sys.radio.play_state` antes de confiar no campo `playing`.

**Favoritos** são lidos de dois lugares:
1. ITS listener em tempo real (`sys.radio.fm_favorites_station_list`)
2. `local_radio.xml` via Shizuku no startup: `cat /data/user_de/0/com.beantechs.mediacenter/shared_prefs/local_radio.xml`

**Navegação por favoritos** via setas do cluster (keycodes 1024/1025): o `InstrumentProjector2` escreve `sys.radio.cur_channel_info = {freqKhz,0,0,0}` para sintonizar diretamente.

### Bluetooth (AVRCP)

- NLS (`MediaNotificationListenerService`) + `MediaSessionManager.getActiveSessions()`
- Polling de 5s via `pollRunnable`
- ITS: `sys.bluetooth.avrcp_music_info` — título e artista
- ITS: `sys.bluetooth.avrcp_play_state` — `"1"` = tocando, `"2"` = pausado

### Android Auto

O ITS `sys.bluetooth.avrcp_music_info` fica **vazio** quando AA está ativo. A informação da faixa vem do logcat:

```
MC-driver-LastMediaInfoSpManager: savePlayMediaInfo json = {"title":"...","author":"...","mediaId":"ANDROID_AUTO/..."}
```

**Implementação:** polling a cada 2s via Shizuku:
```kotlin
ShizukuUtils.runCommandAndGetOutput(
    arrayOf("sh", "-c", "logcat -t 2000 | grep 'savePlayMediaInfo json' | tail -1")
)
```

`lastAaMediaId` evita re-despachar a mesma faixa. É resetado a cada transição de fonte para garantir que a mesma música seja exibida ao voltar para o AA.

`cachedAaTitle` / `cachedAaArtist` guardam a última faixa em memória para restaurar o display imediatamente ao voltar para o AA sem depender do buffer do logcat.

---

## Arquitetura de estado — `isRadioSource`

Flag booleana central que controla qual fonte está ativa:

**Seta para `true`:**
- `sys.basic.audio_source_app` com FM ou AM
- `sys.radio.play_state = "1"`
- `sys.radio.cur_channel_info playing=1` **E** `sys.radio.play_state = "1"` (dual confirmation)

**Seta para `false`:**
- `sys.basic.audio_source_app` com qualquer fonte não FM/AM
- `sys.radio.play_state = "0"`

> A dupla confirmação (`cur_channel_info` + `play_state`) é necessária porque `cur_channel_info` pode estar *stale*. `play_state` está em `DEFAULT_KEYS` do `ServiceManager` e sempre reflete o estado real.

---

## Lógica de transição entre fontes

| Transição | Comportamento |
|---|---|
| Rádio → AA/BT | `play_state=0` → `isRadioSource=false`, `lastAaMediaId=""`, poll imediato do AA |
| AA/BT → Rádio | `source_app` FM/AM → `isRadioSource=true`, lê frequência e reenvia favoritos |
| Startup com AA | Dual confirmation impede que `cur_channel_info` stale mostre rádio erroneamente |
| Voltar para AA com mesma música | `lastAaMediaId=""` garante re-despacho; `cachedAaTitle` restaura display imediato |

---

## Controles de mídia (teclas do cluster)

| Keycode | Evento | Ação |
|---|---|---|
| 1024 | `RADIO_NAVIGATE "next"` | Próxima rádio favorita (ou seek fallback) |
| 1025 | `RADIO_NAVIGATE "prev"` | Rádio favorita anterior |
| 1028 | `RADIO_PLAY_PAUSE` | Desabilitado — botão "O" do hardware faz pause/play nativo |

Guards ativos em `RADIO_NAVIGATE`: ignora se `!isRadioSource` ou se `isRadioSeeking`.

---

## Mini bar de mídia

Posição: `bottom: 65px; right: 460px` (à esquerda do círculo do cluster).

Ativado/desativado em tempo real via SharedPreferences (`mediaBarEnabled`) com `OnSharedPreferenceChangeListener`.

**Marquee:** usa `translateX(min(0px, calc(-100% + 224px)))` — o `min()` impede que textos curtos se movam para a direita.

---

## Arquivos principais

| Arquivo | Responsabilidade |
|---|---|
| `projectors/InstrumentProjector2.kt` | Estado (`isRadioSource`, `cachedAaTitle`), listeners ITS, polling AA, navegação favoritos |
| `services/MediaNotificationListenerService.kt` | NLS, MediaSession, polling BT 5s, `onPlaybackStateChanged` |
| `cluster-widgets/air-control/src/core/components/dashboardInfo.js` | `createMediaBar()`, marquee, counter X/Y de favoritos |
| `cluster-widgets/air-control/src/styles/night.style.css` | `.dashboard-media-bar`, `.media-title`, animações marquee |
| `install-car.mjs` | FASE 4: ativa NLS via `settings put` + `cmd notification allow_listener` |
