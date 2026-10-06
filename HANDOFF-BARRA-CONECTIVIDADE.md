# Handoff — card de conectividade na barra estendida (companheiro deste PR)

Um chip único na barra estendida que mostra, num relance, a situação de conectividade do carro:
o **roteamento do hotspot** (do HotRouter) ou, quando o roteamento está off, **por onde a própria
multimídia (tela) está navegando** + o estado do corte de 4G.

Este PR **não inclui o card** porque a barra estendida é bem diferente entre as bases — este doc é o
guia pra plugar na sua barra. **Nenhum dado novo é capturado:** o card só combina o que já está neste
PR (`HotRouterManager` + `MobileDataManager` + `ServiceManager.isHotspotOnAir`).

## De onde vêm os dados (tudo já neste PR)

| fonte | o que dá |
|---|---|
| `HotRouterManager.getInstance().readStatusBlocking()` | `.mode` = OFF/STARTING/WLAN/4G/ERROR (já com o freshness: travado -> ERROR) |
| `ServiceManager.getInstance().isHotspotOnAir()` | hotspot do carro REALMENTE no ar (carrier do sysfs; getWifiApState é -1 neste OEM) |
| `MobileDataManager.isControlEnabled()` | controle de dados móveis ligado |
| `MobileDataManager.blockReason(ctx)` | motivo do corte do 4G ("manual"/"consumo"/"WiFi"/"AA"/"CarPlay") ou `null` |
| `MobileDataManager.isWifiConnected(ctx)` | a TELA está com internet ativa via WiFi |
| `HotRouterManager.readRoutedWifiNameBlocking()` *(opcional, ver fim)* | SSID da rede (pro texto "· \<nome\>") |

Tudo é **blocking/shell** → chamar **fora da main thread**.

## Regra de apresentação (a mesma lógica pro chip)

Função pura; devolve `(texto, nível, ícone)`. `texto == null` = esconder o card.

```kotlin
data class ConnChip(val text: String?, val level: String, val icon: String)

fun buildConnChip(
    mode: String,             // HotRouterManager.MODE_*
    wifiName: String?,        // SSID (WLAN roteando OU tela no WiFi); null -> "WiFi"
    controlEnabled: Boolean,  // MobileDataManager.isControlEnabled()
    blockReason: String?,     // MobileDataManager.blockReason(ctx)
    hotspotActive: Boolean,   // ServiceManager.getInstance().isHotspotOnAir()
    headUnitOnWifi: Boolean   // MobileDataManager.isWifiConnected(ctx)
): ConnChip {
    // CUIDADO: o daemon reporta WLAN/4G pelo UPLINK (pinga a wlan0), NÃO pelo AP -> mode==WLAN NÃO
    // implica hotspot ligado. O gate do "Roteando" é SÓ o hotspot real (isHotspotOnAir).
    val hotspotOn = hotspotActive
    val blocked = blockReason != null
    val ssid = wifiName?.takeIf { it.isNotBlank() }
    return when {
        // --- HotRouter roteando o hotspot: mostra o uplink do roteamento ---
        hotspotOn && mode == HotRouterManager.MODE_WLAN     -> ConnChip("Roteando · " + (ssid ?: "WiFi"), "good", "satellite")
        hotspotOn && mode == HotRouterManager.MODE_4G       -> ConnChip("Roteando · 4G", "warn", "cell")
        hotspotOn && mode == HotRouterManager.MODE_STARTING -> ConnChip("Hotspot · conectando", "muted", "loader")
        hotspotOn && mode == HotRouterManager.MODE_ERROR    -> ConnChip("Hotspot · erro", "bad", "alert")
        // --- HotRouter off/parado: por onde a TELA navega + o corte do 4G ---
        headUnitOnWifi            -> ConnChip("Tela · " + (ssid ?: "WiFi"), "good", "wifi")
        controlEnabled && blocked -> ConnChip("4G off · $blockReason", "bad", "cell_off")
        else                      -> ConnChip("Tela · 4G", "warn", "cell")
    }
}
```

**Por que o gate `isHotspotOnAir`:** o daemon do HotRouter reporta WLAN/4G mesmo com o hotspot do carro
**desligado** (ele só checa o uplink). Sem o gate, o card diria "Roteando" à toa. Com o hotspot off e o
HotRouter off, o card cai no ramo "Tela · …" e mostra a conexão real da multimídia.

## Renderização

- Poll **off-main** a cada ~1.5–2s **enquanto a barra está aberta** (nada em repouso).
- Chip = ícone + texto, com cor de destaque pelo **nível**:
  `good`=verde, `warn`=âmbar, `bad`=vermelho, `muted`=cinza.
- Ícones sugeridos (mapear pros seus): `satellite` (roteando WLAN), `wifi` (tela no WiFi),
  `cell` (4G), `cell_off` (4G cortado), `loader`/sync (conectando), `alert` (erro).

Esboço:

```kotlin
val (text, level, icon) = withContext(Dispatchers.IO) {
    val hr = HotRouterManager.getInstance()
    val mode = hr.readStatusBlocking().mode
    val onWifi = MobileDataManager.isWifiConnected(ctx)
    val ssid = if (mode == HotRouterManager.MODE_WLAN || onWifi) hr.readRoutedWifiNameBlocking() else null
    buildConnChip(
        mode, ssid,
        MobileDataManager.isControlEnabled(),
        MobileDataManager.blockReason(ctx),
        ServiceManager.getInstance().isHotspotOnAir(),
        onWifi
    ).let { Triple(it.text, it.level, it.icon) }
}
// desenhe `text` (se != null) com a cor de `level` e o ícone de `icon`
```

## SSID (opcional)

Pro texto mostrar o nome da rede ("Roteando · Minha-Rede" / "Tela · Minha-Rede") em vez de "WiFi",
adicione este método ao `HotRouterManager` (lê o SSID da `wlan0` via shell; neste head unit `iw`/
`wpa_cli`/`iwconfig` voltam vazio, então o fallback confiável é o `dumpsys netstats`). Se não quiser,
é só passar `null` como `wifiName` que o card usa "WiFi".

```java
/** SSID da Wi-Fi (wlan0) atual/roteada. null se não determinar. Chamar fora da main thread. */
public String readRoutedWifiNameBlocking() {
    final String IF = "wlan0"; // = WLAN_IF do hotrouter.sh
    try {
        String out = ShizukuUtils.runCommandAndGetOutput(new String[]{"sh","-c","iw dev "+IF+" link 2>/dev/null"});
        for (String line : out.split("\n")) { int i = line.indexOf("SSID:"); if (i>=0){ String v=line.substring(i+5).trim(); if(!v.isEmpty()) return v; } }
        out = ShizukuUtils.runCommandAndGetOutput(new String[]{"sh","-c","wpa_cli -i "+IF+" status 2>/dev/null"});
        for (String line : out.split("\n")) { String t=line.trim(); if (t.startsWith("ssid=")){ String v=t.substring(5).trim(); if(!v.isEmpty()) return v; } }
        // fallback confiável neste head unit: a rede WiFi ativa aparece no netstats como networkId="<nome>"
        out = ShizukuUtils.runCommandAndGetOutput(new String[]{"sh","-c","dumpsys netstats 2>/dev/null | grep -m1 networkId"});
        int n = out.indexOf("networkId=\""); if (n>=0){ int e=out.indexOf('"', n+11); if (e>n+11){ String v=out.substring(n+11,e).trim(); if(!v.isEmpty()) return v; } }
    } catch (Exception ignored) {}
    return null;
}
```
