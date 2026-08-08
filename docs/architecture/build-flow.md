# Build Flow

Atualizado em: 2026-05-24

## Android

Build debug:

```bash
./gradlew :app:assembleDebug
```

Testes unitários:

```bash
./gradlew :app:testDebugUnitTest
```

O projeto usa `settings.gradle.kts` com apenas o módulo `:app`.

## Frontend

Simulador unificado de temas:

```bash
cd cluster-widgets
npm install
npm run dev
```

Validação do catálogo local:

```bash
npm run check
```

Esse check cobre descoberta dos temas, adaptador de telemetria dos pacotes legados e geometria da
camada frontal fixa (`READY`, placa e ESP) no canvas `1920x720`.

Build do tema Default v1.0:

```bash
cd cluster-widgets/source/v1.0/default
npm run build
```

Build do Minimalist OTA:

```bash
cd cluster-widgets/source/v1.0/minimalist
npm run build
```

O Theme Lab serve fontes e pacotes existentes, mas não substitui o build individual do tema.

## Deploy

```bash
./tools/headunit-dev/headunit.sh deploy-apk
./tools/headunit-dev/headunit.sh deploy-air-control
```

`deploy-apk` constrói APK, sobe servidor HTTP local e pede para a central baixar via curl. `deploy-air-control` envia HTML para `/data/local/tmp/app.html`.

## Riscos

- Build de frontend pode atualizar `app/src/main/res/raw/app.html`.
- Deploy depende de conectividade entre central e host local.
- Release depende de secrets de assinatura.

## A Confirmar

- Estratégia de migração dos pacotes legados Sport para fonte editável no contrato v1.0.
