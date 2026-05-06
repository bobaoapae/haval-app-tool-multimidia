# Headunit Dev Toolkit - Motivo e Benefício

## Contexto

O fluxo de desenvolvimento na central multimídia era lento e manual:

1. build do APK;
2. acesso telnet;
3. cópia para `/data/local/tmp`;
4. reinstalação;
5. restart de app/tela;
6. coleta manual de logs.

Esse processo aumentava o tempo por iteração e a chance de erro operacional.

## Objetivo do `tools/headunit-dev`

Padronizar um fluxo de desenvolvimento rápido e reproduzível para:

- deploy de APK;
- hot deploy de frontend (`app.html`) sem reinstalar APK;
- coleta de diagnóstico e discovery;
- execução remota de comandos utilitários.

## Benefício para o time

- reduz tempo de validação por alteração;
- simplifica onboarding (menos comandos ad-hoc);
- melhora consistência entre desenvolvedores;
- facilita troubleshooting com saídas organizadas.

## Limite de escopo e segurança

- foco em desenvolvimento, logs e diagnóstico;
- sem alteração de controles críticos de condução/freio/airbag/ADAS/powertrain;
- fallback preservado para o comportamento empacotado do APK.

## Referências

- `tools/headunit-dev/headunit.sh`
- `tools/headunit-dev/WORKFLOW.md`
