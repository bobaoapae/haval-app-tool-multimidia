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

Tema Default:

```bash
cd cluster-widgets/default
npm run build
```

O `package.json` do Default usa `bun run clear` dentro do script de build. Isso exige confirmar se `bun` está instalado ou ajustar ambiente.

Temas Basic e Basic Light usam `npm run clear` no script de build.

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

- Comando preferido para todos os temas: `npm` ou `bun`.
- Estratégia de versionamento dos pacotes de tema em `cluster-widgets/Themes`.
