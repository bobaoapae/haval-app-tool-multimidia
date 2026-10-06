# Analise reversa comparativa do APK VoltDash

Data: 2026-08-04

APK analisado: `/Users/marcelofp/Downloads/app-release (4).apk`

Escopo: identificar temas e melhorias reutilizaveis no Haval Impulse, sem incorporar codigo nesta
sessao. Todo o subsistema comercial de ativacao/licenciamento foi deliberadamente excluido da
contagem, da recomendacao e do plano de implementacao.

## Resultado executivo

Foram identificados **13 candidatos reutilizaveis**:

- **2 temas novos**: Sport Colors e Sport Colors Leve;
- **11 blocos de melhoria independentes**;
- **3 mudancas adicionais nao recomendadas ou ja superadas**, fora da contagem;
- **1 subsistema de licenciamento excluido integralmente**, tambem fora da contagem.

O APK nao deve ser tratado como uma versao mais nova completa do Impulse. Ele usa o mesmo package,
`br.com.redesurftank.havalshisuku`, e compartilha 121 das suas 127 chaves de preferencias com o
projeto atual, mas declara `versionCode=68` e `versionName=1.0.0.67`. O projeto atual possui 19
chaves que nao existem no APK, incluindo o controle mais novo de dados moveis, HotRouter e brilho
solar. A estrategia correta e portar funcionalidades isoladas, nunca substituir a base atual.

## Identificacao e confiabilidade da analise

| Evidencia | Resultado |
|---|---|
| SHA-256 do APK | `66ecff0297285c06ccb8009596c60615fb3cfa6869fd53563b6947235c7cf370` |
| Tamanho | 59.888.535 bytes |
| Label | VoltDash |
| Package | `br.com.redesurftank.havalshisuku` |
| Android | compile SDK 36; min/target SDK 28 |
| Assinatura | APK v2, certificado identificado como VoltDash |
| Revisao gravada no APK | `b6c72977fdbe58babdaa8413632374f86797793d` |
| Disponibilidade da revisao | nao existe nos objetos ou refs remotos acessiveis neste checkout |
| Resultado JADX | 3.586 fontes e 631 recursos extraidos; 25 erros de decompilacao |

Os erros do JADX atingem a fidelidade do fonte reconstruido, nao impedem o inventario de recursos,
strings, preferencias e fluxos principais. O codigo decompilado deve ser usado como especificacao de
comportamento, nao como fonte pronta para copiar. Para um merge auditavel, o ideal e obter do autor o
commit ou a arvore-fonte correspondente.

## Temas encontrados

| ID | Tema | Conteudo | Avaliacao |
|---|---|---|---|
| T1 | Sport Colors | Seis modos, sete paletas, Now Playing, dados de viagem/TPMS e limite de velocidade. | Condicional: visualmente rico, mas com risco de FPS. |
| T2 | Sport Colors Leve | Mesmo layout e funcoes; metadado afirma remover rastro borrado do ponteiro, onda do Digital e letreiro do album. | Primeira opcao para port controlado, mas a vantagem de performance ainda nao esta provada. |

Os dois pacotes usam `index.html`, versao de metadado `0.16.44` e bounds `x=0`, `y=62`,
`width=1920`, `height=596`, compativeis com o envelope atual de 1920x720. Ambos preservam os pontos
globais da bridge `window.control`, `window.focus`, `window.showScreen` e `window.cleanup`.

Os seis modos — Normal, Analogico V2, Digital, Mapa, Mapa Graduado e Mapa Limpo — foram tratados
como parte dos temas, e nao como seis melhorias ou um candidato adicional. Em eventual port, eles
devem ser adicionados sem remover os modos Reduzido e Clean que ja existem no Impulse.

### Riscos estaticos dos temas

| Metrica simples | Impulse atual | Sport Colors | Sport Colors Leve |
|---|---:|---:|---:|
| Tamanho do HTML | 1.690.920 B | 1.922.591 B | 1.924.842 B |
| Ocorrencias de `requestAnimationFrame` | 3 | 12 | 12 |
| Ocorrencias de `blur(...)` | 5 | 12 | 12 |
| Ocorrencias de `box-shadow` | 59 | 91 | 91 |
| Ocorrencias de `setInterval` | 5 | 6 | 7 |

Essas contagens nao medem FPS, mas demonstram que o Lite nao removeu o codigo pesado do bundle e e
ligeiramente maior que o Sport Colors. Pode haver desativacao em runtime, mas isso precisa ser
confirmado em simulador e na central com `top`, `dumpsys meminfo`, logs, flickering e FPS percebido.

Outros problemas de empacotamento:

- o titulo interno do HTML cita `0.16.6`, diferente do `0.16.44` do `theme.xml`;
- os dois thumbnails sao identicos entre si e ao thumbnail Default atual, portanto nao diferenciam
  os temas no carousel;
- os HTMLs contem um mapa mock remoto em `tiempreendimentos.com`, condicionado a
  `native-mock-enabled`; essa dependencia precisa ser removida do bundle de producao;
- nao foi encontrada a arvore-fonte modular desses HTMLs, apenas o bundle minificado/autocontido.

## Onze blocos de melhoria

| ID | Melhoria realmente nova em relacao ao baseline | Dependencia principal | Risco | Recomendacao |
|---|---|---|---|---|
| M1 | Instalacao offline de temas embarcados, com comparacao de versao e troca por arquivo `.part` | `ThemeManager` e assets Android | Baixo/medio | Portar, aproveitando para corrigir identidade `name/folderName` e reload duplo do manager atual. |
| M2 | Cleanup completo do lifecycle WebView: remover listener de dados e limpar filas/maps pendentes no `onStop` | `InstrumentProjector2` e `ServiceManager` | Medio | Reimplementar com referencia nomeada ao listener e testes de abre/fecha; o atual registra callback anonimo e nao o remove. |
| M3 | Sete paletas selecionaveis pelo controle: Red Sport, Red GT, Ocean Blue, Green, Dark Blue, Amber Gold e Purple GT | `CURRENT_CLUSTER_COLOR`, tela e bridge | Baixo/medio | Portar; manter fallback quando o tema nao suportar `colorTheme`. |
| M4 | Tela dedicada Now Playing no cluster, com capa, titulo, duracao, progresso, play/pause e radio | pipeline de midia atual + nova tela do tema | Medio | Portar sobre os monitores de midia atuais, sem substituir a coleta mais nova de CarPlay/AA. |
| M5 | Painel opcional de viagem e TPMS no Analogico V2 | propriedades de viagem, pressoes/temperaturas e bridge | Medio/alto | Portar apos provar quais propriedades existem em cada firmware e unidade. |
| M6 | Placa de limite de velocidade/TSR no cluster | propriedades `car.map.tsr.*` e bridge | Medio/alto | Prototipar com fallback oculto; a constante existe hoje, mas o dado ainda nao e entregue ao frontend. |
| M7 | Opcao para ocultar o velocimetro nos modos de mapa | preferencia + bridge + CSS | Baixo | Bom primeiro merge visual; validar todos os modos e projecoes no display 3. |
| M8 | Leitura de midia USB OEM, incluindo metadata, progresso e capa | prefs privadas do MediaCenter, Shizuku e cache | Medio/alto | Portar com limite de tamanho, cleanup de cache e deteccao de firmware; nao copiar arquivo ilimitado. |
| M9 | Diagnostico runtime: excecao global, stalls da main thread e heartbeat de memoria/CPU/threads/FD/storage | logger persistente | Medio | Portar com amostragem, rate limit e gate de diagnostico para evitar custo permanente. |
| M10 | Teste e reparo de escrita das SharedPreferences via ownership/permissoes | Shizuku e paths de `shared_prefs` | Medio | Portar de forma estreita, validando UID/path e sem `chown -R` amplo. |
| M11 | Modo de servico reversivel que tira customizacoes do ar, restaura estado OEM e troca o launcher visivel | snapshot, shell, bottom bar, overscan e aliases | Alto | Nao portar como esta. Redesenhar como perfil de manutencao sem desabilitar launchers de terceiros. |

M3, M4, M5, M6 e M7 aparecem visualmente dentro dos temas, mas foram contados separadamente porque
exigem implementacao Android/bridge independente; copiar somente o HTML deixaria estados sem fonte de
dados ou controles inoperantes.

## Proveniencia e permissao de reutilizacao

O repositorio publico de distribuicao informa que o fonte do VoltDash e privado e que os temas sao
entregues dentro do APK. O aviso `LICENSE-VOLTDASH` reserva os direitos sobre modificacoes, arte,
temas e codigo proprios, preservando separadamente a licenca MIT das porcoes originais do Haval
Impulse:

- <https://github.com/gustavoclimaco/voltdash-dist/blob/main/README.md>
- <https://github.com/gustavoclimaco/voltdash-dist/blob/main/LICENSE-VOLTDASH>

Isso e diferente do licenciamento comercial/ativacao que foi excluido tecnicamente. Antes de copiar
os HTMLs ou codigo proprio, deve-se obter autorizacao do autor para incorporar essas partes ao
Impulse e, idealmente, receber o fonte correspondente. Sem isso, o caminho seguro e implementar os
comportamentos de forma independente a partir desta especificacao comparativa.

## Mudancas fora da contagem

| Mudanca | Motivo para nao contar como melhoria reutilizavel |
|---|---|
| Ocultar automaticamente o icone/launcher do Shizuku | executa `pm disable` sem caminho claro de restauracao; comportamento invasivo e desnecessario. |
| Preferir dados do telefone quando o CarPlay estiver ativo | ja foi superado pelo `MobileDataManager` atual e sua opcao geral de bloqueio em projecao. Portar criaria duas politicas concorrentes. |
| Copiar manifest/servicos do APK em bloco | inclui aliases, exportacoes e configuracoes de uma base antiga; deve ser revisado item a item, nunca mesclado integralmente. |

## Licenciamento deliberadamente excluido

Foram identificados `ActivationManager`, `SettingsGate`, armazenamento/estado de ativacao e checks de
ativacao no projetor e na tela de informacoes. Nenhum deles foi contado como melhoria e nenhum deve
ser levado ao Impulse. Endpoints, regras, dados e UI desse subsistema nao fazem parte do plano.

Essa exclusao precisa ocorrer na fronteira Android e nao apenas visualmente: qualquer port do
`InstrumentProjector2`, navegacao ou configuracoes deve ser reconstruido sem imports, gates ou
estados de ativacao.

## Ordem recomendada para escolha e implementacao

### Lote A - melhor relacao valor/risco

1. T2 + M1: Sport Colors Leve como tema embarcado e atualizavel.
2. M2: cleanup de listener e filas no lifecycle da WebView.
3. M3: sete paletas.
4. M7: ocultar velocimetro no mapa.
5. M9: diagnostico runtime com gate.
6. M10: reparo estreito de preferencias.

### Lote B - depende da bridge e de dados reais

1. M4: Now Playing usando o pipeline de midia atual.
2. M5: viagem/TPMS.
3. M6: limite de velocidade/TSR.
4. M8: midia USB OEM.

### Lote C - somente apos redesenho de seguranca

1. T1: tema completo, depois de medir contra o Lite.
2. M11: perfil de manutencao sem desabilitacao indiscriminada de apps.

## Contrato minimo de validacao de qualquer port

- obter o fonte original do autor quando possivel; nao colar Java decompilado;
- preservar `window.control`, `window.focus`, `window.showScreen`, `window.cleanup`, fila de JS e
  heartbeat injetado pelo Android;
- empacotar tema por fonte/build reproduzivel, nao editar apenas o HTML gerado;
- manter bounds `0,62 / 1920x596` e nao aplicar bounds de tema a CarPlay ou Android Auto;
- garantir reload unico, fallback para `R.raw.app` e rollback atomico;
- build do tema, `./gradlew :app:testDebugUnitTest` e `./gradlew :app:assembleDebug`;
- simulador 1920x720 para todos os modos, menu, AC e estados sem dados;
- na central parada: display 3, troca de temas, cleanup, flickering, memoria/CPU e coexistencia com
  CarPlay/Android Auto;
- manter CarPlay e Android Auto isolados durante toda a implementacao.

## Limitacoes desta sessao

- nenhuma instalacao, deploy, comando veicular ou teste fisico foi executado;
- nenhuma funcionalidade foi incorporada ao projeto;
- a revisao-fonte gravada no APK nao estava disponivel para um diff Git exato;
- performance, propriedades TPMS/TSR e compatibilidade de firmware permanecem **A confirmar** na
  central real.

## Follow-up de implementacao

Na branch `codex/voltdash-selected-features-20260804`, foram posteriormente implementados os itens
selecionados T1, T2 e M2-M8 por reimplementacao integrada sobre a base atual. Codigo de
ativacao/licenciamento permaneceu excluido.

Os bundles T1/T2 foram preservados byte a byte e receberam manifesto SHA-256/validador. M2-M7
foram ligados ao `InstrumentProjector2` com polling condicionado aos temas Sport, teardown
explicito e bridge com quoting/allowlists. M8 usa Binder MediaCenter como caminho primario e um
fallback limitado de preferencias/arquivo, sem substituir os pipelines atuais de CarPlay e Android
Auto.

O build v295 foi instalado na central com o veiculo parado, e os temas foram copiados manualmente
para o diretorio privado do app porque M1 nao foi selecionado. Build, testes e verificacao de
pacote/processo passaram; validacao visual, FPS, TPMS/TSR em firmware real e pendrive USB continuam
**A confirmar** fisicamente.
