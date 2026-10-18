# Alerta de radar (radares fixos)

Avisa radares fixos à frente usando o GPS da própria central, no HUD (display 4096) e no card de navegação do tema do cluster, com som. Fica em **Recursos → Alerta de Radar** (primeiro card: liga/desliga + **Configurar**). Desligado por padrão.

Código: `app/src/main/java/br/com/redesurftank/havalshisuku/radar/`.

## Fluxo

```
GPS da central (LocationManager.GPS_PROVIDER, 1 Hz, com velocidade e rumo)
  + car.basic.vehicle_speed (velocímetro do carro, decide "acima do limite")
        │
        ▼
RadarAlertEngine (Kotlin puro, testado)  ◄── RadarIndex (grade 0,02°) ◄── RadarRepository
        │                                                                   ▲
        ├─► HudOverlayController.onRadar  → card no HUD                      │
        ├─► app.navigation.radar (JSON)   → tbtCard.setRadar no tema          │
        └─► RadarSoundPlayer (tons gerados, uso NAVIGATION_GUIDANCE)          │
                                                                             │
RadarUpdater (1x por dia) ── Overpass (OSM) + ANTT ── files/radar/*.csv.gz ──┘
                         └── cópia no APK: assets/radar/*.csv.gzip
```

`RadarManager` liga tudo: começa em `ServiceManager` (junto do HUD), escuta GPS só com o recurso ligado e publica a chave apenas quando muda (distância em passos de 10 m).

## Dados

| Fonte | O que traz | Licença | Medido em 2026-10-17 |
|---|---|---|---|
| OpenStreetMap `highway=speed_camera` via Overpass | radares de todo o Brasil (bbox inclui faixas de fronteira) | ODbL | 10.441 câmeras, 8.916 com `maxspeed`, 1.682 com sentido resolvido |
| ANTT "Dados dos Radares" (CKAN, CSV ISO-8859-1, um recurso novo por mês) | 1.256 radares ativos de rodovias federais concedidas, limite leve/pesado, sentido crescente/decrescente | CC-BY | 948 com sentido resolvido; **53% não existem no OSM** |

- Mesclagem (`RadarSources.merge`): ANTT e OSM a menos de 60 m e com sentido compatível (≤ 60°) são o mesmo radar; ANTT vence em limite e sentido, OSM completa o que falta. Total: 11.305.
- Sentido no OSM: `direction` em graus/pontos cardeais = sentido do tráfego fiscalizado; `forward`/`backward` usa o rumo da via sob o nó (a consulta traz a geometria dessas vias); `both`/ausente = desconhecido.
- Sentido na ANTT: "Crescente" = tráfego no sentido do km crescente. O arquivo não tem geometria, então o rumo vem do radar ANTT mais próximo (em km, 0,5–40 km) na mesma rodovia; descartado se a linha reta não bate com a diferença de km (trecho sinuoso ou dado ruim).
- Outras fontes avaliadas: Maparadar/Waze/Radarbot são proprietárias (sem licença de reuso); listas de DER/prefeituras estão espalhadas em PDF/painéis; o cadastro nacional aprovado na Câmara (09/2026) ainda não existe.

## Atualização (`RadarUpdater`)

- Ao iniciar (e ao ligar o recurso): se a última atualização **bem-sucedida** tem menos de 24 h, não faz nada. Senão espera a central ter internet (até 30 min) e baixa.
- OSM: consulta completa sempre (~500 KB gzip, 30–70 s no servidor). O Overpass não tem "mudou desde?" barato: o diff de uma semana levou 2 min 48 s (mais que o download inteiro).
- ANTT: lê o metadado CKAN (poucos KB) e só baixa o CSV quando o recurso mais novo mudou (na prática, uma vez por mês).
- Sucesso = OSM atualizado (ANTT é melhor esforço). Falha mantém os dados atuais e tenta de novo em 5, 15 e 60 min nesse boot.
- Gravação atômica (arquivo temporário + rename) e validação (≥ 5.000 câmeras OSM, ≥ 300 ANTT; coordenadas na região).
- **TLS:** `overpass-api.de` só aceita TLS 1.3 e o Android 9 da central para no 1.2 ("Handshake failed" no carro). O downloader usa uma instância privada do Conscrypt (`org.conscrypt:conscrypt-android`) só para essas conexões; o resto do app continua na pilha da plataforma. Espelho com TLS 1.2: `maps.mail.ru`. `overpass.kumi.systems` e `overpass.private.coffee` estavam dando HTTP 500 para qualquer consulta em 2026-10-17.
- Privacidade: a consulta é sempre o Brasil inteiro; nenhum servidor recebe a posição do carro.
- Cópia no APK: `assets/radar/{osm,antt}.csv.gzip` (~107 KB). Extensão `.gzip` de propósito: o AGP descompacta assets `.gz` e tira a extensão. O leitor aceita gzip ou texto puro. Atualizar antes de release com `scripts/radar/Update-RadarSeed.ps1` (roda `RadarSeedGenerator`, o mesmo código Kotlin do carro).

## Regras do alerta (`RadarAlertEngine`)

- Rumo: o da GPS acima de 2 m/s; parado, mantém o último; sem rumo da GPS, deriva do deslocamento (≥ 15 m).
- Um radar vira alvo quando está dentro da distância de aproximação, **à frente** (≤ 25° do rumo; ≤ 50° a menos de 80 m) e, se o sentido dele é conhecido, fiscaliza o nosso sentido (≤ 50°).
- O alvo fica até ser **passado** (distância cresce 25 m além do mínimo), ficar 500 m além da aproximação, ou 3 leituras seguidas a > 80° do rumo (entrou numa transversal).
- Radar passado fica mudo por 10 min ou até 500 m além da aproximação.
- Eventos: `APPROACH` (som da aproximação), `NEAR` (som do alerta próximo), `OVERSPEED` (passou do limite durante o alerta; som do alerta próximo, uma vez por radar). "Som só acima do limite" corta os sons quando não está acima; a placa aparece sempre.

## Saídas

- **HUD** (`HudOverlayModel.Radar`, `HudOverlayRenderer.drawRadar`): placa com anel vermelho, limite no centro, câmera pequena no canto e distância embaixo, na área das setas; vermelho quando acima do limite; sem limite conhecido, câmera no centro. Enquanto há radar, ocupa o lugar do turn-by-turn; barras/arcos continuam. Com o alerta ligado e "mostrar no HUD", a janela do HUD fica de pé mesmo no layout "original" (que então desenha só o radar), para o alerta nunca esperar um launch via Shizuku.
- **Tema** (`app.navigation.radar`, contrato v1.0 aditivo; ver `themes-contract-v1.md`): o card compartilhado (`shared/tbt/tbtCard.js`, `setRadar`) mostra a placa R-19 no lugar da seta, "Radar · 60 km/h" e a distância; borda vermelha quando acima. Default 1.4.46 e Minimalist 1.0.6.
- **Som** (`RadarSoundPlayer`): tons gerados (Bipe, Bipe duplo, Sino, Alerta, ou sem som) com volume por evento, `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` + foco transitório "may duck". A central não tem motor TTS (`TTS_SERVICE` vazio), por isso não há voz.

## Teste e diagnóstico

- Configurar → **Simular a 50/72 km/h**: passa por um radar de 60 a 700 m em ~50 s (HUD, tema e sons), mesmo com o recurso desligado.
- Debug: `adb shell am start --display 4096 -f 0x10010000 -n br.com.redesurftank.havalshisuku/.hud.HudOverlayActivity --es radarDemo over` (ou `on`).
- Logs: tag `Radar` (Log.w). Rastros duráveis em cluster-diagnostics: `radar_update`, `radar_alert`, `radar_no_location_permission`.

## Riscos / a confirmar

- Só radares fixos; cobertura OSM varia por cidade; radares móveis não entram.
- 84% das câmeras OSM não dizem o sentido: em cidade, uma câmera numa paralela que esteja "à frente" no cone de 25° ainda pode alertar.
- Volume real do canal de navegação no carro: a confirmar no Testar.
- Dependência de servidores públicos do Overpass (com fallback); se todos falharem, segue com a cópia anterior.
