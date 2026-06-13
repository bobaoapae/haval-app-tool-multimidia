# Architecture Overview

Atualizado em: 2026-05-24

## O Que Foi Identificado

O projeto é um app Android chamado `HavalShisuku`, com application id `br.com.redesurftank.havalshisuku`. Ele opera como uma ferramenta de integração com a central Haval/GWM, renderizando um cluster customizado e gerenciando apps/projeções em displays secundários.

## Stack

- Android Kotlin/Java com Gradle Kotlin DSL.
- Jetpack Compose para telas do app principal.
- Shizuku e comandos shell para operações privilegiadas.
- WebView dentro de `Presentation` para renderizar dashboard HTML no cluster.
- Frontend em JavaScript/CSS/HTML com Parcel e empacotamento inline.
- Scripts shell em `tools/headunit-dev/` para deploy/logs/diagnóstico.

## Arquivos Relacionados

- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/br/com/redesurftank/App.java`
- `app/src/main/java/br/com/redesurftank/havalshisuku/services/ForegroundService.java`
- `app/src/main/java/br/com/redesurftank/havalshisuku/managers/ServiceManager.java`
- `app/src/main/java/br/com/redesurftank/havalshisuku/managers/ProjectorManager.java`
- `app/src/main/java/br/com/redesurftank/havalshisuku/projectors/InstrumentProjector2.kt`
- `cluster-widgets/default/`

## Riscos

- Alterações em bootstrap, Shizuku ou ServiceManager podem impedir inicialização.
- Alterações em display secundário podem quebrar cluster ou projeções.
- Alterações em WebView/JS podem gerar tela preta, loop ou flickering.

## Dúvidas a Confirmar

- Manter `docs/` em minúsculo como pasta canônica da documentação.
- Quais temas ainda são usados em produção.
- Qual firmware/modelo da central é alvo principal.
