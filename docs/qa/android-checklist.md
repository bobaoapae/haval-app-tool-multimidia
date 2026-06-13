# Android Checklist

- `./gradlew :app:assembleDebug`
- `./gradlew :app:testDebugUnitTest`
- Manifest não perdeu services/receivers/providers.
- `ForegroundService` inicia.
- Shizuku permission OK.
- `ProjectorManager` detecta displays.
- `DisplayAppLauncher` não move app errado.
- Logs sem crash fatal.
- Nenhum force-stop proibido em CarPlay.
