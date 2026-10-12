# Cobertura do Apex GT 1.0.15

Inspeção da fonte e miniatura reais, 11/10/2026. Prefixos abaixo são as chaves observadas; a prévia não se inscreve nelas e não envia comandos.

| Informação | Chave ou origem observada | Adaptação |
|---|---|---|
| Combustível % | car.basic.remain_fuel_percentage | Texto flutuante inferior esquerdo, percentual |
| Combustível em litros | Percentual × 55 L na fonte | L EST.; 55 L apenas no mock configurado |
| Autonomia combustível | car.ev_info.fuel_mode_remain_odometer | Texto flutuante inferior esquerdo, km |
| Bateria % | car.ev_info.cur_battery_power_percentage | Texto flutuante inferior direito, percentual |
| Autonomia elétrica | car.ev_info.electric_mode_remain_odometer | Texto flutuante inferior direito, km |
| Autonomia total | Soma das duas autonomias | Texto inferior central, EST.; exige ambas as entradas |
| Trip A | car.basic.cur_journey_odometer | Canto superior esquerdo, sem faixa de fundo |
| Consumo médio | car.basic.cur_journey_avg_fuel_consume | Canto superior direito: km/L; zero em L/100 km |
| Odômetro | car.basic.total_odometer | Cabeçalho direito |
| Temperaturas | car.basic.inside_temp / outside_temp | Cabeçalho esquerdo |
| Unidade de temperatura | car.configure.default_temp_unit | 0=°C, 1=°F, sem converter novamente o valor |
| Marcha | car.basic.gear_status | Cabeçalho central; 0/1=N, 2=D, 3=P, 4=R |
| Condução | car.drive_setting.drive_mode | NORMAL/SPORT/ECO/NEVE/AREIA/LAMA/AWD |
| Propulsão | car.ev_setting.power_model_config | HEV/EVP/EV |
| Hora | Relógio local no Apex | Hora simulada na prévia |
| RPM térmico | car.basic.engine_speed | Entre os dois anéis inferiores, somente >0 |
| Velocidade | car.basic.vehicle_speed + ajuste ApexSpeed no app | Velocidade simulada central e na asa esquerda 0–200; curva urbana não linear; fonte Rajdhani; ajuste do app não integrado |
| Potência da bateria | car.ev_info.power_battery_voltage × cur_charge_current /1000 | Asa direita: consumo positivo azul / zero neutro / recuperação negativa verde. Polaridade do sinal real não validada |
| Potência de tração | Canal separado apenas ilustrativo nesta prévia; chave real não confirmada | Canal interno ilustrativo para o snapshot, não é mais a escala esquerda; a asa esquerda agora mostra velocidade km/h |

## Menus locais adaptados do Apex GT

- Pneus: car.basic.tire_pressure_front_left / front_right / rear_left / rear_right → PSI
- Trip A: cur_journey_drivetime e avg_vehicle_speed_since_startup
- Trip B: accumulated_odometer, accumulated_drivetime, avg_fuel_consumption e vehicle_speed_since_reset
- Direção: car.drive_setting.steering_wheel_assist_mode
- Recuperação: car.ev_setting.energy_recovery_level
- ESP configurado: car.drive_setting.esp_enable (estado de ajuste, não luz de advertência)
- HVAC: car.hvac.power_mode, fan_speed, driver_temperature, auto_enable e cycle_mode

Gráficos de 18 amostras, navegação e estados de ajustes/AC foram adaptados nesta revisão; detalhes em MENU-COVERAGE.md. Todos os valores são demonstrativos. Com dados indisponíveis, a mesma estrutura fica com “--”.

## Omissões conhecidas e pontos não confirmados

- Contour/Vector do Apex não implementados: linha DISPLAY fica explicitamente indisponível
- Comandos reais de ajuste/HVAC e zeragem de Trip B não são enviados; o controlador altera somente dados simulados
- Projeção CarPlay/Android Auto, `clusterEnabled` e preferências do host
- Área de ADAS/mapa e indicadores OEM: a geometria central original do Apex não é compatível automaticamente com o velocímetro central solicitado
- READY/TSR/símbolos de assistência: composição nativa/laboratório, não widgets de produção verificados aqui
- Sem mapeamento Apex confirmado para carga/plug conectado, bateria 12 V, temperatura dos pneus, portas ou cintos
- Capacidade real do tanque, máximos de potência, unidades reais dos sensores e funcionamento físico no veículo continuam não validados

Fonte principal: https://github.com/bobaoapae/haval-app-tool-multimidia/blob/preview/cluster-widgets/source/v1.0/ApexGT/src/apex-gt.js
Menus: https://github.com/bobaoapae/haval-app-tool-multimidia/blob/preview/cluster-widgets/source/v1.0/ApexGT/src/apex-menus.js
