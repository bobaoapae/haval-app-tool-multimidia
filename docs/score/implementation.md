# Implementação do Score de Consistência da Viagem

## Escopo

- A gestão da funcionalidade fica no app Impulse, na nova aba `Score`.
- O cluster recebe apenas o estado discreto `tripAnalysisActive`, exibindo um ícone pequeno enquanto a viagem está em análise.
- O score usa a telemetria já monitorada pelo `ServiceManager`, evitando duplicar fontes de dados.

## Dados reutilizados

- Velocidade: `CAR_BASIC_VEHICLE_SPEED`
- Hodômetro: `CAR_BASIC_TOTAL_ODOMETER`
- Estado do veículo: `CAR_BASIC_ENGINE_STATE`
- Consumo instantâneo combustível: `CAR_BASIC_INSTANT_FUEL_CONSUMPTION`
- Consumo instantâneo energia: `CAR_EV_INFO_INSTANT_ENERGY_CONSUMPTION`
- Regeneração: `CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL`
- Corrente da bateria: `CAR_EV_INFO_POWER_BATTERY_CURRENT` ou `CAR_EV_INFO_CUR_CHARGE_CURRENT`

## Pesos do score

- Variação de velocidade: 25%
- Aceleração controlada: 20%
- Frenagem/regeneração: 20%
- Consumo: 15%
- Estabilidade: 15%
- Contexto: 5%

## Persistência

- Sessão ativa/pausada: `tripConsistency.activeSession`
- Último relatório: `tripConsistency.lastReport`
- Indicador do cluster: `tripConsistencyClusterActive`

## Verificação

- Testes unitários do algoritmo: `./gradlew :app:testDebugUnitTest`
- Build do app: `./gradlew :app:assembleDebug`
- Build do cluster HTML: `cd cluster-widgets/air-control && npm run build`
