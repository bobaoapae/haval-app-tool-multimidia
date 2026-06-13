# Persistent Bottom Bar Architecture (Android 9)

Este documento detalha o funcionamento e a arquitetura da barra inferior customizada e persistente utilizando o `WindowManager` e o `overscan`.

## Visão Geral
A implementação depende fortemente em manipular as restrições físicas da área de renderização percebida pelo sistema e adicionar uma camada flutuante.

### 1. Gestão de Layout com wm overscan
Ao invés de exibir a barra interativa apenas como sobreposição (o que faria as UIs de terceiros renderizarem atrás dela e tornaria botões inalcançáveis), utilizamos o shell script do sistema interno:
`wm overscan [left],[top],[right],[bottom]`

Este processo:
- Aplica um corte falso na renderização de tela no nível do SurfaceFlinger.
- Informa aos ActivityManagers e InputMethodManagers (Teclados) qual é o escopo real do dispositivo.
- A barra entra neste buraco invisível utilizando layouts customizados.

### 2. Rendering (WindowManager)
Um serviço Android de foreground será o host dessa View persistente.
Para ser desenhado sobrepondo tudo, mas ainda ficar restrito ao local:
- **Type**: `TYPE_APPLICATION_OVERLAY`
- **Flags Importantes**: 
  - `FLAG_NOT_FOCUSABLE`: Para não roubar o foco permanente da tela. Se tiver que usar o teclado na barra, precisará gerenciar esse recurso momentaneamente.
  - `FLAG_LAYOUT_IN_SCREEN`: Isso assegura que o WindowManager ignorará limites convencionais, como a nova zona "morta" que o overscan criou, posicionando nossa View precisamente ali.

### 3. Permissões
É demandado o consentimento explícito:
- `SYSTEM_ALERT_WINDOW`: Para flutuar com TYPE_APPLICATION_OVERLAY.
Privilégios para execução do Shell Requer Root ou App rodando via Shizuku. O projeto Haval Shisuku possui infraestrutura implementada.

#### 4. Controle Dinâmico (HVAC & System)
- **AC Fan Speed**: Implementado via `CAR_HVAC_FAN_SPEED`. Utiliza uma UI de 7 passos com renderização em `Canvas` para feedback visual imediato.
- **AC Recirculate & Control Groups**: Implementado via `CAR_HVAC_CYCLE_MODE` (0 = ar externo, 1 = recircular). Está agrupado na seção de climatização (14% de largura da barra inferior) junto com os botões de Sync (`CAR_HVAC_SYNC_ENABLE`) e Auto (`CAR_HVAC_AUTO_ENABLE`), posicionado à esquerda do Sync e utilizando feedback visual ativo e reativo através de um botão premium (`Icons.Default.Autorenew`).
- **Desativação Completa de Temperatura Anômala/Desativada**: Se o AC for desligado ou a leitura da temperatura for anômala (ex: single-zone desativando o lado do passageiro, retornando `"--"` ou `-1`), calcula-se `isTempValid = isEnabled && !isAbnormal`. Se `isTempValid` for falso: a opacidade é reduzida para `0.4f`, os botões de ajuste fino (`Add`/`Remove`) são desativados, o indicador de temperatura exibe `"--"` e a possibilidade de clique para abrir o menu flutuante (overlay) é completamente bloqueada no nível do Compose.
- **Navegação (Shizuku)**: Os comandos de sistema (Back, Home, Recents) são executados via `input keyevent` em threads separadas (`CoroutineScope`) para evitar bloqueio da UI Thread e garantir latência próxima de zero.

### 5. Sliders Interativos Flutuantes (Overlays)
Para permitir ajustes precisos e ergonômicos no cockpit, implementou-se um sistema de sliders verticais interativos:
- **Disparo por Clique & Posicionamento Dinâmico**: Ao clicar nos valores de temperatura, velocidade de ventilação ou volume, o componente captura seu centro horizontal em tempo real utilizando `.onGloballyPositioned` (`centerX = coordinates.positionInRoot().x + coordinates.size.width / 2f`). Esta coordenada horizontal é enviada a `BottomBarState.sliderPositionX` junto com o respectivo tipo no enum `SliderType`, fazendo o menu slider flutuar posicionado exatamente no eixo vertical do botão clicado.
- **Expansão de Região de Toque (Touchable Region)**: O serviço `BottomBarService` monitora o estado ativo do slider. Se `BottomBarState.activeSliderType != null`, a região interativa da janela do WindowManager é dinamicamente estendida para abranger a tela toda, permitindo a captura de gestos do slider e toques externos de cancelamento, sem interferir no comportamento persistente usual.
- **Sliders Customizados Premium (`VerticalSlider`)**:
  - **Aesthetics & Feedback**: Containers com design premium translúcido escuro (`#13151A`, 95% opacidade), bordas sutis e sem o rótulo de texto redundante no topo para maximizar o minimalismo visual.
  - **Gradiente Térmico e Escala de Temperatura**: Barra vertical de temperatura com gradiente contínuo e indicador circular móvel. A escala do termômetro é dotada de traços horizontais (tick marks) de graduação flanking a cada 2°C (totalizando 9 marcações entre 16°C e 32°C) com 25% de opacidade branca para excelente clareza.
  - **Passos Coniformes (Ventilação/Volume)**: Barras com larguras progressivas (mais finas na base, mais largas no topo) com preenchimento na cor azul ativo `#2196F3` para destacar a magnitude selecionada.
  - **Dimensionamento Dinâmico (Responsive Height)**: A trilha vertical do slider foi reescrita utilizando peso dinâmico (`weight(1f)`) e preenchimento de bordas vertical, permitindo que a altura do componente seja definida pelo modificador do chamador (ex: altura estendida para `320.dp` nas temperaturas para facilidade de ajuste fino de 0.5 em 0.5 passos).
  - **Desativação Defensiva Integrada**: Quando o AC é desativado (`isACEnabled == false`), a leitura da temperatura nos sliders e na barra é omitida (exibindo `"--"`), a interação por toque é bloqueada no nível de entrada do ponteiro e o indicador ativo (gradiente e indicador circular) é ocultado.
  - **Entrada de Toques via Raw Pointer Input**: Implementado utilizando `awaitPointerEventScope` e `awaitFirstDown` para manipulação em baixo nível (Low-Level Pointer Capture). Esse paradigma garante responsividade máxima em tempo real, evitando concorrência ou perda de pacotes de toque mesmo sob telas cheias imersivas como YouTube ou Waze.
