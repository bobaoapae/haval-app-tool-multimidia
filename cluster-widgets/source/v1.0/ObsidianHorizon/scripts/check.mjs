import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const themeRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const sourceHtml = fs.readFileSync(path.join(themeRoot, "index.html"), "utf8");
const css = fs.readFileSync(path.join(themeRoot, "src", "obsidian-horizon.css"), "utf8");
const javascript = fs.readFileSync(path.join(themeRoot, "src", "obsidian-horizon.js"), "utf8");
const manifest = fs.readFileSync(path.join(themeRoot, "theme.xml"), "utf8");
const framePath = path.join(themeRoot, "assets", "obsidian-horizon-frame.webp");
const outputPath = path.join(themeRoot, "dist", "app.html");

function assert(condition, message) {
    if (!condition) throw new Error(message);
}

for (const fragment of [
    "<name>Obsidian Horizon</name>",
    "<version>1.0.18</version>",
    "<contractVersion>v1.0</contractVersion>",
    "<minBridgeVersion>1.0.1</minBridgeVersion>",
    "<decentralized>true</decentralized>",
    "<x>500</x>",
    "<y>100</y>",
    "<width>910</width>",
    "<height>620</height>"
]) {
    assert(manifest.includes(fragment), `Manifesto sem ${fragment}`);
}

for (const id of [
    "clock-time",
    "inside-temperature",
    "outside-temperature",
    "drive-mode",
    "propulsion-mode",
    "gear-state",
    "odometer",
    "vehicle-speed",
    "power-kw",
    "power-whole",
    "engine-rpm",
    "battery-percent",
    "fuel-percent",
    "fuel-liters",
    "fuel-range",
    "battery-range",
    "context-card-nav",
    "context-card",
    "context-card-title",
    "context-back-hint",
    "context-panel-menu",
    "context-panel-settings",
    "context-panel-graphs",
    "context-panel-info",
    "context-info-general",
    "context-info-tires",
    "context-panel-trips",
    "context-panel-ac",
    "context-graph-bars",
    "context-setting-steer",
    "context-setting-regen",
    "context-setting-esp",
    "context-info-energy",
    "context-tire-front-left",
    "context-tire-front-right",
    "context-tire-rear-left",
    "context-tire-rear-right",
    "context-tire-status-front-left",
    "context-tire-status-front-right",
    "context-tire-status-rear-left",
    "context-tire-status-rear-right",
    "context-trip-a-distance",
    "context-trip-a-time",
    "context-trip-a-speed",
    "context-trip-a-consumption",
    "context-trip-b-distance",
    "context-trip-b-time",
    "context-trip-b-speed",
    "context-trip-b-consumption",
    "context-trip-b-reset-status",
    "context-ac-temp",
    "context-ac-temp-fill",
    "context-ac-fan",
    "context-ac-fan-level",
    "adas-viewport"
]) {
    assert(sourceHtml.includes(`id="${id}"`), `Elemento obrigatorio ausente: ${id}`);
}

for (const hook of ["window.control", "window.onDataChanged", "window.onKeyEvent", "window.onCardChanged", "window.cleanup"]) {
    assert(javascript.includes(hook), `Hook obrigatorio ausente: ${hook}`);
}

for (const capability of ["carPlayInDash", "projectionPreparingD3", "projectionMirrorInDash"]) {
    assert(javascript.includes(capability), `Estado de projecao ausente: ${capability}`);
}

for (const telemetryKey of [
    "car.ev_setting.energy_recovery_level",
    "car.drive_setting.steering_wheel_assist_mode",
    "car.drive_setting.esp_enable",
    "car.hvac.power_mode",
    "car.hvac.fan_speed",
    "car.hvac.driver_temperature",
    "car.hvac.cycle_mode",
    "car.hvac.auto_enable"
]) {
    assert(javascript.includes(telemetryKey), `Telemetria de card ausente: ${telemetryKey}`);
}

for (const tripKey of [
    "car.basic.cur_journey_odometer",
    "car.basic.cur_journey_drivetime",
    "car.basic.cur_journey_avg_fuel_consume",
    "car.basic.avg_vehicle_speed_since_startup",
    "car.basic.accumulated_odometer",
    "car.basic.accumulated_drivetime",
    "car.basic.avg_fuel_consumption",
    "car.basic.vehicle_speed_since_reset"
]) {
    assert(javascript.includes(tripKey), `Telemetria de viagem ausente: ${tripKey}`);
}

for (const tireKey of [
    "car.basic.tire_pressure_front_left",
    "car.basic.tire_pressure_front_right",
    "car.basic.tire_pressure_rear_left",
    "car.basic.tire_pressure_rear_right"
]) {
    assert(javascript.includes(tireKey), `Telemetria de pressao ausente: ${tireKey}`);
}

for (const menuLabel of ["INFORMAÇÕES", "GRÁFICOS", "AJUSTES", "TRIP A / TRIP B"]) {
    assert(sourceHtml.includes(menuLabel), `Opcao do menu ausente: ${menuLabel}`);
}

assert(sourceHtml.includes("<small>MENU</small>"), "Card central deve ser identificado como Menu");
assert(!sourceHtml.includes(">INFO</small>"), "Rotulo antigo Info nao pode permanecer");
assert(!sourceHtml.includes(">ENTER<"), "Tema nao deve exibir legenda de botao do simulador");
assert(!sourceHtml.includes("alternar"), "Tema nao deve exibir instrucao de navegacao");
assert(!javascript.includes("CARD DO VEÍCULO"), "Clima nao deve exibir o rotulo Card do Veículo");
assert(javascript.includes("kicker.hidden = acCard"), "Clima deve ocultar o kicker do card");
assert(javascript.includes('setText("context-card-counter", "CARD 03")'), "Clima deve usar o identificador padrao CARD 03");
assert(!/\.card-ac\s+\.context-card-counter\s*\{/.test(css), "Clima deve reutilizar a posicao padrao do contador");
assert(!css.includes(":has("), "CSS do tema deve permanecer compativel com o WebView embarcado");

for (const forbiddenOverlay of ["ADAS ORIGINAL HAVAL", "ÁREA NATIVA PRESERVADA", "MAPA CARPLAY"]) {
    assert(!sourceHtml.includes(forbiddenOverlay), `Overlay central proibido: ${forbiddenOverlay}`);
}

assert(css.includes("--portal-left: 500px"), "Limite esquerdo do viewport ADAS divergente");
assert(css.includes("--portal-right: 1410px"), "Limite direito do viewport ADAS divergente");
assert(css.includes("--header-height: 100px"), "Topo do viewport ADAS divergente");
assert(/\.top-item\s*\{[^}]*top:\s*18px/s.test(css), "Leituras laterais fora da faixa superior");
assert(/\.top-time\s*\{[^}]*left:\s*80px/s.test(css), "Hora fora do canto lateral esquerdo");
assert(/\.top-inside\s*\{[^}]*top:\s*auto[^}]*bottom:\s*18px[^}]*left:\s*52px/s.test(css), "Temperatura interna fora do canto inferior esquerdo");
assert(/\.top-outside\s*\{[^}]*top:\s*auto[^}]*right:\s*52px[^}]*bottom:\s*18px/s.test(css), "Temperatura externa fora do canto inferior direito");
assert(/\.top-inside \.micro-label,\s*\.top-outside \.micro-label\s*\{[^}]*display:\s*block[^}]*font-size:\s*12px/s.test(css), "Legendas das temperaturas inferiores ausentes");
assert(sourceHtml.indexOf('id="inside-temperature"') < sourceHtml.indexOf('>INTERNA</span>'), "Legenda Interna deve ficar abaixo da temperatura");
assert(sourceHtml.indexOf('id="outside-temperature"') < sourceHtml.indexOf('>EXTERNA</span>'), "Legenda Externa deve ficar abaixo da temperatura");
assert(/\.top-odometer\s*\{[^}]*right:\s*80px/s.test(css), "Odometro fora do canto lateral direito");
assert(/\.top-value\s*\{[^}]*font-size:\s*34px[^}]*font-weight:\s*700/s.test(css), "Leituras laterais do cabecalho permanecem pequenas");
assert(/\.drive-mode,\s*\.propulsion-mode\s*\{[^}]*font-size:\s*32px[^}]*font-weight:\s*740[^}]*letter-spacing:\s*0\.03em/s.test(css), "Tipografia dos modos centrais divergente");
assert(/\.gear-state\s*\{[^}]*font-size:\s*58px[^}]*font-weight:\s*680[^}]*letter-spacing:\s*-0\.025em/s.test(css), "Tipografia da marcha central divergente");
assert(css.includes("background: transparent"), "Transparencia central nao foi preservada");
assert(css.includes("--obsidian-carplay-left-shift: 330px"), "Deslocamento CarPlay do Obsidian divergente");
assert(css.includes("--obsidian-android-auto-left-shift: 330px"), "Deslocamento Android Auto do Obsidian divergente");
assert(/\.context-card\s*\{[^}]*right:\s*160px[^}]*width:\s*306px[^}]*height:\s*280px/s.test(css), "Card contextual fora da regiao direita");
assert(css.includes(".context-card-nav"), "Navegacao visual dos cards ausente");
assert(css.includes(".card-nav-item.is-active"), "Destaque do card ativo ausente");
assert(!/\.context-card\s*\{[^}]*background:/s.test(css), "Conteudo do card nao pode criar um quadro sobreposto");
assert(!css.includes(".preview-adas") && !css.includes(".preview-carplay"), "Preview nao pode cobrir o conteudo nativo");
assert(!css.includes("transition: all"), "transition: all nao e permitido");
assert(!/backdrop-filter|filter:\s*blur/i.test(css), "Filtros amplos nao sao permitidos");
assert(!/@keyframes|animation\s*:/i.test(css), "Animacao continua nao e permitida");
assert(!/<img\b/i.test(sourceHtml), "Nao sao permitidas imagens externas ao frame Base64");
assert((sourceHtml.match(/<svg\b/gi) || []).length === 0, "Tracados laterais em SVG devem permanecer removidos");
assert(!/https?:\/\//i.test(sourceHtml + css + javascript), "Dependencia externa encontrada");
assert(css.includes('../assets/obsidian-horizon-frame.webp'), "Moldura raster fonte ausente");
assert(fs.existsSync(framePath), "Moldura WebP ausente");
assert(!sourceHtml.includes("energy-mark"), "Icones de energia nao podem ser duplicados no HTML");
assert(!css.includes(".energy-mark"), "Icones de energia nao podem ser redesenhados no CSS");
assert(javascript.includes("var isRegen = power !== null && power < -0.1"), "Sinal de kW deve comandar consumo e regeneracao");
assert(javascript.includes('setInstrumentProgress("power", absolutePower === null ? 0 : absolutePower / 140)'), "Escala direta do indicador de consumo ausente");
assert(javascript.includes('setInstrumentProgress("speed", speed === null ? 0 : speed / 180)'), "Escala direta do indicador de velocidade ausente");
assert(!/motion-trace|motionTrace|paintMotionTrace|setMotionTarget|requestAnimationFrame|cancelAnimationFrame/.test(sourceHtml + css + javascript), "Animacao dos contornos laterais deve permanecer removida");
assert(!javascript.includes("buildRail("), "Barras antigas em blocos nao podem permanecer ativas");
assert(css.includes("--accent-electric-blue: #38aef5"), "Cor azul de consumo ausente");
assert(javascript.includes("clockTime: null"), "Estado ausente nao pode ser convertido em zero");
assert(javascript.includes('setText("power-whole"'), "Parte inteira da potencia nao e atualizada");
assert(javascript.includes("powerSign + Math.round(absolutePower)"), "Potencia principal deve ser exibida como numero inteiro");
assert(!sourceHtml.includes('id="power-fraction"') && !css.includes(".power-fraction"), "Casa decimal da potencia principal deve permanecer removida");
assert(/\.instrument-power\s+\.instrument-value-line\s*\{[^}]*top:\s*22px/s.test(css), "Potencia principal permanece colada ao menu");
assert(css.includes('"Avenir Next Condensed", "Roboto Condensed"'), "Familia numerica condensada ausente");
assert(css.includes("font-variant-numeric: lining-nums tabular-nums"), "Numeros dinamicos devem ser tabulares");
assert(javascript.includes("FUEL_TANK_CAPACITY_LITERS = 55"), "Conversao de combustivel para litros divergente do projeto");
assert(javascript.includes('setText("fuel-liters"'), "Leitura de combustivel em litros nao e atualizada");
assert(javascript.includes("if (currentCardId === 0) return"), "Card nativo deve ignorar os comandos do volante");
assert(javascript.includes("handleContextMenuKey"), "Navegacao do card de informacoes ausente");
assert(javascript.includes('contextMenuMode = "menu"'), "Menu e submenus nao estao separados");
assert(javascript.includes('view: "trips"'), "Submenu de Trip A e Trip B ausente");
assert(javascript.includes(' + " / 04"'), "Contador do menu deve refletir quatro submenus");
assert(javascript.includes('if (keyName === "ENTER")'), "Abertura de submenu pelo volante ausente");
assert(javascript.includes('if (keyName === "BACK")'), "Retorno do submenu pelo volante ausente");
assert(javascript.includes('keyName === "ENTER_LONG" && tripFocusIndex === 1'), "Trip B deve exigir OK pressionado para zerar");
assert(javascript.includes('triggerSystemAction("RESET_DRIVE_INFO")'), "Acao segura de zeragem do Trip B ausente");
assert(sourceHtml.includes("REINÍCIO AUTOMÁTICO"), "Trip A deve indicar reinicio automatico");
assert(sourceHtml.includes("SEGURE OK PARA ZERAR"), "Trip B deve orientar zeragem com OK pressionado");
assert((sourceHtml.match(/class="context-wheel-vertical-hint"/g) || []).length === 2, "Subniveis devem indicar os botoes verticais do volante");
assert(css.includes(".context-wheel-vertical-hint::before"), "Pictograma para cima do volante ausente");
assert(css.includes(".context-wheel-vertical-hint::after"), "Pictograma para baixo do volante ausente");
assert(/\.context-trip-block header span\s*\{[^}]*font-size:\s*21px/s.test(css), "Titulos Trip devem permanecer ampliados");
assert(/\.context-trip-block header strong\s*\{[^}]*font-size:\s*30px/s.test(css), "Distancias Trip devem permanecer ampliadas");
assert(javascript.includes('backHint.hidden = false'), "Indicador visual de Voltar ausente nos submenus");
assert(css.includes(".context-back-hint[hidden]"), "Indicador de Voltar nao possui estado oculto");
assert(/\.context-back-hint i::before\s*\{[^}]*width:\s*23px[^}]*height:\s*11px[^}]*border-radius:\s*0 8px 8px 0/s.test(css), "Curva do pictograma Voltar divergente do comando do volante");
assert(/\.context-back-hint i::after\s*\{[^}]*border-right:\s*8px solid currentColor/s.test(css), "Ponta solida do pictograma Voltar ausente");
assert(/\.context-graph-heading > span\s*\{[^}]*font-size:\s*19px/s.test(css), "Rotulo principal do grafico pequeno demais");
assert(/\.context-graph-heading strong\s*\{[^}]*font-size:\s*34px/s.test(css), "Valor principal do grafico pequeno demais");
assert(/\.context-graph-scale\s*\{[^}]*font-size:\s*15px/s.test(css), "Escala do grafico pequena demais");
assert(javascript.includes("GRAPH_SAMPLE_INTERVAL_MS = 250"), "Amostragem leve dos graficos ausente");
assert(javascript.includes("cycleContextSetting"), "Alteracao dos ajustes do veiculo ausente");
assert(javascript.includes('"car.drive_setting.drive_mode": "drivingMode"'), "Modo de conducao sem estado proprio");
assert(javascript.includes('"car.ev_setting.power_model_config": "evMode"'), "Propulsao sem estado proprio");
assert(javascript.includes('if (friendlyKey === "evModeLabel") return'), "Alias legado ainda pode sobrescrever a propulsao");
assert(!javascript.includes('if (friendlyKey === "evModeLabel") friendlyKey = "evMode"'), "Conducao e propulsao continuam acopladas");
assert(javascript.includes("handleAcKey"), "Navegacao do card de climatizacao ausente");
assert(javascript.includes("if (keyName === \"BACK\") return"), "BACK nao pode retirar a climatizacao do card 3");
assert(!javascript.includes('updateState("cardId"'), "cardId deve ser escrito somente por onCardChanged");
assert(/\.energy-fuel\s+\.energy-range\s*\{[^}]*left:\s*110px/s.test(css), "Autonomia de combustivel distante do grafico");
assert(/\.energy-battery\s+\.energy-range\s*\{[^}]*right:\s*120px/s.test(css), "Autonomia eletrica distante do grafico");
assert(/\.energy-header strong\s*\{[^}]*font-size:\s*32px[^}]*font-weight:\s*650/s.test(css), "Percentuais de energia pequenos demais");
assert(/\.energy-range,\s*\.fuel-volume strong\s*\{[^}]*font-size:\s*24px[^}]*font-weight:\s*600/s.test(css), "Autonomias pequenas demais");
assert(/\.card-nav-item small\s*\{[^}]*font-size:\s*18px/s.test(css), "Botoes dos cards pequenos demais");
assert(/\.context-menu-option strong\s*\{[^}]*font-size:\s*27px/s.test(css), "Opcoes do menu pequenas demais");
assert(/\.context-setting-row span\s*\{[^}]*font-size:\s*19px/s.test(css), "Rotulos dos ajustes pequenos demais");
assert(/\.context-setting-row strong\s*\{[^}]*font-size:\s*27px/s.test(css), "Valores dos ajustes pequenos demais");
assert(javascript.includes("VISIBLE_CONTEXT_OPTIONS = 3"), "Janela dos menus deve exibir no maximo tres opcoes");
assert(javascript.includes("renderSlidingWindow"), "Comportamento deslizante dos menus ausente");
assert(javascript.includes('renderSlidingWindow("context-panel-menu"'), "Menu principal nao usa janela deslizante");
assert(javascript.includes('renderSlidingWindow("context-panel-settings"'), "Ajustes nao usam janela deslizante");
assert(css.includes(".context-menu-option.is-window-visible"), "Estado visivel do menu deslizante ausente");
assert(css.includes(".context-setting-row.is-window-visible"), "Estado visivel dos ajustes deslizantes ausente");
assert(/\.context-menu-option small\s*\{[^}]*display:\s*none/s.test(css), "Descricoes secundarias do menu devem permanecer ocultas");
assert(/\.context-ac-control strong\s*\{[^}]*font-size:\s*56px/s.test(css), "Numeros da climatizacao pequenos demais");
assert(/\.context-ac-label\s*\{[^}]*clip-path:\s*inset\(50%\)/s.test(css), "Rotulos redundantes da climatizacao devem permanecer apenas acessiveis");
for (const compactAcState of ['"DESL."', '"MANUAL"', '"RECIRC."', '"EXTERNO"']) {
    assert(javascript.includes(compactAcState), `Estado compacto da climatizacao ausente: ${compactAcState}`);
}

assert(fs.existsSync(outputPath), "Execute npm run build antes do check");
const builtHtml = fs.readFileSync(outputPath, "utf8");
assert(!/\b(?:src|href)=["']\.\/src\//.test(builtHtml), "Build nao esta autocontido");
assert(builtHtml.includes("data:image/webp;base64,"), "Moldura Base64 ausente do bundle");
assert(!builtHtml.includes("../assets/obsidian-horizon-frame.webp"), "Bundle ainda referencia a moldura externa");
assert(Buffer.byteLength(builtHtml) < 900000, "Bundle maior que 900 KB");

console.log(`Obsidian Horizon valido: ${Buffer.byteLength(builtHtml)} bytes, viewport 500x100 -> 1410x720`);
