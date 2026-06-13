# Restaurar Voz e Navegacao OEM

Procedimento registrado em 2026-05-31 depois de desativar servicos OEM para reduzir carga durante
diagnostico de travamento/ANR do Impulse.

## Pacotes Afetados

- Navegacao: `com.neusoft.na.navigation`
- Voz principal: `com.iflytek.cutefly.speechclient.hmi`
- Voz auxiliar: `com.beantechs.voice.adapter`
- Cliente de voz: `com.beantechs.voiceclient`
- Voiceprint: `com.beantechs.voiceprintservice`

## Restauracao Rapida

Com a central acessivel por Telnet:

```bash
HEADUNIT_HOST=192.168.15.100 ./tools/headunit-dev/restore-oem-voice-navigation.sh
```

## Restauracao Manual

Se precisar executar direto no shell/Telnet da central:

```sh
for pkg in \
  com.neusoft.na.navigation \
  com.iflytek.cutefly.speechclient.hmi \
  com.beantechs.voice.adapter \
  com.beantechs.voiceclient \
  com.beantechs.voiceprintservice; do
  cmd package install-existing --user 0 "$pkg" 2>/dev/null || true
  pm enable --user 0 "$pkg" 2>/dev/null || pm enable "$pkg" 2>/dev/null || true
done
```

## Verificacao

```sh
pm list packages | grep -E 'neusoft.na.navigation|iflytek|voice'
pm list packages -d | grep -E 'neusoft.na.navigation|iflytek|voice' || true
ps -A | grep -E 'neusoft.na.navigation|iflytek|voice' || true
```

Resultado esperado:

- `pm list packages` deve listar os pacotes restaurados.
- `pm list packages -d` nao deve listar esses pacotes como `disabled-user`.
- Os processos podem voltar automaticamente; se nao voltarem, reiniciar a central ou abrir a funcao
  OEM correspondente deve relancar os servicos.

## Observacoes

- `cmd package install-existing --user 0` reativa um app de sistema removido apenas para o usuario
  atual; nao baixa APK externo.
- `pm enable --user 0` desfaz `pm disable-user`.
- Se algum pacote retornar `not installed for 0` antes da restauracao, o comando
  `install-existing` deve ser executado antes do `pm enable`.
- `com.beantechs.voice.adapter` nao deve ser tratado como apenas UI de voz. Ele expoe
  `com.beantechs.voice.adapter.VoiceAdapterService`, usado pelo `ServiceManager` como fonte de
  binder para dados veiculares/DVR/modelo. Com ele ausente, o Impulse consegue iniciar na build
  atual, mas dados completos da central podem ficar degradados ate restaurar o pacote e reiniciar
  ou abrir a funcao OEM correspondente.
