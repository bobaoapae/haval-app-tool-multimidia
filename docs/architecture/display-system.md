# Display System

Atualizado em: 2026-06-01

## O Que Foi Identificado

O app usa displays Android secundários para renderizar camadas e apps:

- Display 0: central principal, 1920x720.
- Display 1: HDMI 1920x720 usado por `InstrumentProjector` como camada legada de refresh/HUD.
- Display 2: HDMI 1280x720. Na central testada em 2026-06-01, não é o HUD físico.
- Display 3: cluster principal, HDMI 1920x720, usado por `InstrumentProjector2` e por apps/projeções.
- Display 4096: HUD físico, built-in 480x240, `uniqueId=local:5`, `FLAG_PRIVATE`.

`ProjectorManager` procura displays via `DisplayManager` e instancia presentations quando o display
alvo existe. Se um display ainda não existe, registra listener. Na central testada em 2026-06-01,
`DisplayManager.getDisplays()` expôs apenas 0, 1, 2 e 3; o HUD 4096 é privado e não ficou disponível
para `Presentation`.

O shell/ActivityManager conseguiu iniciar Activities no HUD privado com `am start --display 4096`.
Isso deve ser tratado como caminho manual/experimental, não como fluxo automático do app, porque uma
Activity fullscreen pode cobrir/substituir visualmente a projeção nativa do HUD.

## Arquivos Relacionados

- `ProjectorManager.java`
- `BaseProjector.kt`
- `InstrumentProjector.kt`
- `InstrumentProjector2.kt`
- `DisplayAppLauncher.kt`
- `HudNavigationActivity.kt`

## Regras Observadas

- O cluster usa fullscreen lógico de 1920x720 em fluxos críticos.
- Apps em display 3 podem exigir bounds customizados, exceto CarPlay que deve ficar fullscreen físico.
- A WebView do cluster é transparente e pode coexistir com apps nativos.
- O HUD físico usa 480x240. Qualquer overlay Android nele deve ser compacto, transparente, sem foco
  e sem toque.
- `screencap -d 5` captura camadas Android do HUD físico (`local:5`), mas não captura a projeção
  HUD nativa do carro. Quando não há janela Android no HUD, a captura pode sair preta mesmo com o
  HUD visível fisicamente.
- `screencap -d 4096` gerou arquivo vazio na central testada.
- `am start --display 4` falhou com `SecurityException`; referências antigas ao HUD como display 4
  devem ser consideradas obsoletas para esta central.

## Riscos

- Bounds incorretos podem deslocar ou ocultar projeções.
- `mAppBounds` pode diferir de `mBounds`.
- Remover stack errada pode tirar app do display 3.
- Abrir o próprio Impulse no HUD 4096 substitui visualmente o HUD nativo e não é um teste válido de
  sobreposição.
- Como o HUD nativo não aparece em `screencap`, a coexistência overlay + HUD nativo precisa de
  validação visual/manual no carro.

## A Confirmar

- Mapeamento completo dos displays em todas as versões da central.
- Comportamento do display 1 além da camada de refresh descrita no código.
- Se uma Activity Android translucida no display 4096 realmente compõe por cima do HUD nativo sem
  apagá-lo no hardware óptico.
