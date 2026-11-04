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
RadarUpdater (fontes independentes, vias 7–30 dias) ── Overpass + ANTT ── files/radar/*.csv.gz ──┘
                         └── cópia no APK: assets/radar/{osm,antt,roads}.csv.gzip
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
- **Vias sob os radares** (`RadarRoads`, 2026-10-18): a via OSM de carro mais próxima de cada radar (≤ 30 m), cortada em ±200 m ao longo da via, com `oneway`. 11.278 de 11.305 radares (99,8%) ligados a uma via; 8.820 (78%) com sentido definido (via de mão única decide; senão o `direction` do radar; senão os dois sentidos). 10.222 trechos, 460 KB no APK. A consulta pede `way(around.c:25)` para os nós OSM e `way(around:25,lat,lon)` para os 759 radares ANTT que não existem no OSM; na mescla OSM+ANTT fica a posição OSM (é a que está sobre a via).
- Outras fontes avaliadas: Maparadar/Waze/Radarbot são proprietárias (sem licença de reuso); listas de DER/prefeituras estão espalhadas em PDF/painéis; o cadastro nacional aprovado na Câmara (09/2026) ainda não existe.

## Atualização (`RadarUpdater`)

- Ao iniciar (e ao ligar o recurso): cada fonte tem seu próprio estado de sucesso. OSM e metadados ANTT têm intervalo de 24 h; uma fonte com falha não fica bloqueada pelo sucesso da outra. A central espera internet por até 30 min.
- OSM: consulta completa quando vence seu intervalo (~500 KB gzip, 30–70 s no servidor). O Overpass não tem "mudou desde?" barato: o diff de uma semana levou 2 min 48 s (mais que o download inteiro).
- ANTT: lê o metadado CKAN (poucos KB). Baixa o CSV se ID, data de modificação ou URL mudar, se o cache local estiver ausente/inválido, ou se o último CSV salvo tiver 7 dias, mesmo com metadados iguais. A verificação de metadados não renova a idade do CSV. "Atualizar agora" também refaz o CSV.
- Vias: 30 dias quando as posições dos radares não mudam; posições novas/movidas tornam a consulta elegível após 7 dias desde a última cópia de vias válida (download ou APK). A impressão digital usa posições distintas, ordenadas e arredondadas a 6 casas, não velocidade/sentido/ordem. Só é salva após gravar as vias. Migração sem impressão digital usa a mesma idade da cópia existente, sem reiniciar a contagem. "Atualizar agora" não ignora esse limite de vias. Elegibilidade não garante atualização se o recurso estiver desligado ou sem internet.
- A consulta de vias continua nacional: ~6 MB gzip / ~30 MB JSON e ~2 min no servidor; a resposta vai para um arquivo temporário e é lida em streaming (Gson `JsonReader`, duas passadas), sem consulta baseada na localização do carro.
- Sucesso geral = todas as etapas necessárias concluídas. Falhas parciais mantêm os caches atuais e tentam de novo em 5, 15 e 60 min nesse boot, sem baixar novamente fontes já atualizadas. A repetição de um pedido manual também repete apenas as fontes que falharam, mesmo que seus dados anteriores ainda estejam frescos. Logs incluem estado por fonte e a tela exibe o erro parcial.
- Gravação atômica (temporário + rename), sem apagar o destino se a substituição falhar. ID/data/URL e datas de sucesso só avançam após a gravação. Cache ausente, ilegível, de fonte incorreta ou pequeno demais não herda um intervalo de sucesso; usa a cópia do APK enquanto tenta novamente. Mantida validação de download (≥ 5.000 câmeras OSM, ≥ 300 ANTT, ≥ 3.000 trechos; coordenadas na região).
- **TLS:** `overpass-api.de` só aceita TLS 1.3 e o Android 9 da central para no 1.2 ("Handshake failed" no carro). O downloader usa uma instância privada do Conscrypt (`org.conscrypt:conscrypt-android`) só para essas conexões; o resto do app continua na pilha da plataforma. Espelho com TLS 1.2: `maps.mail.ru`. `overpass.kumi.systems` e `overpass.private.coffee` estavam dando HTTP 500 para qualquer consulta em 2026-10-17.
- Privacidade: a consulta é sempre o Brasil inteiro; nenhum servidor recebe a posição do carro.
- Cópia no APK: `assets/radar/{osm,antt}.csv.gzip` (~107 KB). Extensão `.gzip` de propósito: o AGP descompacta assets `.gz` e tira a extensão. O leitor aceita gzip ou texto puro. Atualizar antes de release com `scripts/radar/Update-RadarSeed.ps1` (roda `RadarSeedGenerator`, o mesmo código Kotlin do carro).

## Regras do alerta (`RadarAlertEngine`)

Só alerta radar **na via em que o carro está e do qual está se aproximando** (2026-10-18; antes um cone de 25° pegava ruas paralelas e transversais — ver drive de 18/10: alerta a 5 m e aproximações que nunca chegavam a "perto").

- Rumo: o da GPS acima de 2 m/s; parado, mantém o último; sem rumo da GPS, deriva do deslocamento (≥ 15 m).
- **Radar com via:** o carro precisa estar a ≤ 20 m da via (além do trecho guardado a via é tomada como reta, com folga de 4°), com rumo a ≤ 35° da direção da via (num sentido ou no outro), num sentido que o radar fiscaliza, e com o radar à frente. A distância da base, ao longo da via, entra em toda decisão já com 20 m a menos: aproximação, alerta próximo, passado e o número no HUD/tema. Um ajuste de 200 m dispara com o radar a 220 m e a placa mostra 200 m; ao chegar a 0 m (20 m antes da coordenada) o radar já conta como passado e o alerta some (dono, 2026-10-23: o 0 m antigo às vezes aparecia depois do radar).
- **Radar sem via** (0,2%): precisa estar quase na linha do rumo (≤ 15 m de lado + 2°).
- **Aproximando:** a distância tem de cair ≥ 2 m entre duas leituras seguidas antes do primeiro aviso.
- O alvo fica até ser **passado** (chegar ao ponto 20 m antes da coordenada), a distância crescer 25 m além do mínimo, ou 3 leituras seguidas fora da via (folga maior enquanto é alvo: 35 m / 50°).
- Radar passado fica mudo por 10 min ou até 500 m além da aproximação.
- Distâncias (Configurar): longe 200/250/**300**/350/500 m, perto **50**/100/150 m (padrão em negrito).
- Eventos: `APPROACH` (som da aproximação), `NEAR` (som do alerta próximo), `OVERSPEED` (passou do limite durante o alerta; som do alerta próximo, uma vez por radar). "Som só acima do limite" corta os sons quando não está acima; a placa aparece sempre.

## Prioridade da navegação (`RadarPriority`)

- Com rota do Android Auto, se a próxima manobra vem **antes** do radar (`distance_m` da manobra < distância do radar), o HUD e o card do tema continuam com a seta; o radar só aparece quando for a próxima coisa na via (a rota pode sair da via antes do radar — aí ele some sozinho por sair da via).
- O som do radar espera (`RadarSoundGate`): toca quando o radar aparece (o mais forte que ficou pendente: perto > aproximação); se o radar some antes, o som é descartado.
- A decisão é do host: enquanto a manobra tem prioridade, `app.navigation.radar` vai inativo e o HUD não recebe radar, então temas não precisam comparar distâncias.

## Saídas

- **HUD** (`HudOverlayModel.Radar`, `HudOverlayRenderer.drawRadar`): placa com anel vermelho, limite no centro, câmera pequena no canto e distância embaixo, na área das setas; vermelho quando acima do limite; sem limite conhecido, câmera no centro. Enquanto há radar, ocupa o lugar do turn-by-turn; barras/arcos continuam. Com o alerta ligado e "mostrar no HUD", a janela do HUD fica de pé mesmo no layout "original" (que então desenha só o radar), para o alerta nunca esperar um launch via Shizuku.
- **Tema** (`app.navigation.radar`, contrato v1.0 aditivo; ver `themes-contract-v1.md`): o card compartilhado (`shared/tbt/tbtCard.js`, `setRadar`) mostra a placa R-19 no lugar da seta, "Radar · 60 km/h" e a distância; borda vermelha quando acima. Default 1.4.46 e Minimalist 1.0.6.
- **Som** (`RadarSoundPlayer`): tons gerados (Bipe, Bipe duplo, Sino, Alerta, ou sem som) com volume por evento, `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` + foco transitório "may duck". A central não tem motor TTS (`TTS_SERVICE` vazio), por isso não há voz.

## Teste e diagnóstico

- Configurar → **Simular a 50/72 km/h**: passa por um radar de 60 a 700 m em ~50 s (HUD, tema e sons), mesmo com o recurso desligado.
- Debug: `adb shell am start --display 4096 -f 0x10010000 -n br.com.redesurftank.havalshisuku/.hud.HudOverlayActivity --es radarDemo over` (ou `on`).
- Logs: tag `Radar` (Log.w), mas o logcat do carro gira em minutos. Rastros duráveis em cluster-diagnostics: `radar_loaded` (total, com via), `radar_update` (inclui `osm_state`, `antt_state`, `roads_state`, contagens OSM/ANTT e radares ligados a vias), `radar_alert` (evento, distância, `road`, `tbtFirst` e `cam` = posição pública do radar, para conferir um alerta errado depois; a posição do carro não é gravada), `radar_no_location_permission`.

## Riscos / a confirmar

- Só radares fixos; cobertura OSM varia por cidade; radares móveis não entram.
- Via lateral colada (marginal a < 20 m, mesmo sentido) ainda pode ser confundida com a via do radar; a confirmar dirigindo.
- Radar novo sem geometria usa a regra sem via até uma atualização elegível de vias (7 dias desde a cópia anterior quando as posições mudam), com o recurso ligado, rede disponível e fonte respondendo.
- Volume real do canal de navegação no carro: a confirmar no Testar.
- Dependência de servidores públicos do Overpass (com fallback); se todos falharem, segue com a cópia anterior.
