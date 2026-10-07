# Roteiro de teste parado: Impulse Display Pilot 0.1.0

## O que este teste responde

Se uma janela nativa simples pode ser desenhada no display público selecionado, mantendo a central
controlável e os instrumentos visíveis. **Não testa Waze nativo, mapa ou rota real.** Os dados são fixos
e a própria janela traz `SIMULAÇÃO · SEM WAZE`.

Nenhum teste em movimento. Use bancada ou veículo estacionado. Não acione marcha, ADAS ou condução
para tentar validar este piloto. Não altere permissões, resolução ou configurações de segurança.

## Antes

1. Confirme modelo, ano, versão do firmware e Android. Não compartilhe VIN, placa, endereço ou rota
2. Use apenas o APK assinado de teste identificado no PR, com SHA-256 conferido. O artefato
   `app-debug-unsigned.apk` não é instalável. Se ainda não houver candidato assinado, aguarde
3. A instalação é manual e opcional, com aprovação de quem controla a central. Este piloto não
   substitui o pacote Impulse e não deve pedir nenhuma permissão. Se pedir, pare e informe
4. Feche projeções experimentais concorrentes pelo fluxo normal. Não desative proteções ou instrumentos
5. Observe a aparência normal do painel. Tenha acesso imediato ao botão **PARAR PROJEÇÃO AGORA**

## A — diagnóstico sem projeção

1. Abra `Impulse Display Pilot`; o painel não deve mudar sozinho
2. Leia a lista: informe IDs, nomes, dimensões, rotação e flags exibidos
3. D0, privado, inválido ou apagado deve aparecer como indisponível. Nenhum candidato é selecionado sozinho
4. Se o display esperado não aparecer ou nenhum estiver elegível, registre isso e encerre.
   **Não tente forçar ID, permissões, Activity, shell ou acesso privado**
5. `Ver desenho simulado aqui` só mostra/esconde o exemplo na própria central

## B — uma janela por até 15 segundos

1. Escolha explicitamente o display secundário que deseja avaliar. Não presuma ID 3
2. Identifique visualmente uma área realmente livre. Ajuste esquerda/topo/largura/altura em porcentagem.
   Os valores iniciais não são uma calibração validada; os insets do firmware podem deslocar a janela
3. Marque as duas confirmações apenas se o veículo estiver parado e a área estiver livre de
   velocímetro, ADAS, alertas e indicadores importantes
4. Toque em `Abrir SIMULAÇÃO por até 15 segundos`
5. Confira imediatamente:
   - janela aparece no display selecionado e aproximadamente no retângulo escolhido
   - texto indica simulação, seta à direita/300 m, 6,2 km e chegada 18:30
   - nenhuma faixa escura, dimensão alterada, instrumento encoberto/apagado/congelado ou foco perdido
   - central continua respondendo; botão Parar fica acessível, mesmo com o conteúdo rolado
6. Se qualquer conteúdo nativo mudar de forma indevida, pressione **PARAR** imediatamente e reprove.
   A transparência não garante preservação do espelhamento Android/OEM
7. Sem tocar em nada, a janela deve sumir após aproximadamente 15 segundos e não voltar
8. Se receber `Abertura recusada (...)`, copie a classe do erro. Isso é resultado válido; não há fallback

## C — interrupções, apenas se B passou sem impacto visual

- Abra e toque Parar; toque Parar novamente. Não deve sobrar janela nem travar
- Abra/feche cinco vezes. Apenas uma janela por vez, sem duplicação ou degradação
- Abra e volte à Home ou a outro app. A janela deve desaparecer; voltar ao piloto exige nova seleção
  e novas confirmações, sem reabertura automática
- Altere o retângulo ou destino enquanto aberto. Deve parar e exigir confirmação novamente
- Deixe encerrar pelo tempo e abra de novo manualmente; não deve reaparecer por conta própria
- Em emulador/bancada que já permita rotação/remoção de display de maneira normal, teste esses eventos:
  deve parar, sem janela órfã; reconexão não reabre. Não force uma desconexão física no carro

Se a janela ou o painel não se recuperar, encerre o app pela interface normal e siga o procedimento
habitual de recuperação da central. Não continue os testes nessa sessão e não conduza com o painel alterado.

## Resposta ao grupo/PR

```text
Pilot 0.1.0 / SHA-256 do APK:
Modelo/ano / Android / firmware (sem VIN ou placa):
Displays listados (ID, tamanho, rotação, flags):
Destino e retângulo (%):
Apareceu onde esperado? Sim / Não / Recusado (erro):
ADAS, velocímetro e alertas ficaram intactos? Sim / Não / Não foi possível confirmar:
Parar / timeout 15 s / Home / repetir 5x: resultado de cada um:
Rotação/desconexão: testado em bancada / não testado:
Houve alteração do fundo/espelhamento, tela preta, travamento ou janela residual?
```

Foto opcional feita pelo operador, parado, pode mostrar só o retângulo/painel sem identificação ou
informação de navegação. O app não captura imagens. Não é necessário enviar logs gerais do carro.

## Critério de avanço

Só avançar para investigar **Waze nativo** se houver evidência de destino/retângulo corretos,
nenhum impacto nos instrumentos, e fechamento/recuperação confiáveis. Ausência de teste físico não
é aprovação. Mesmo com o piloto aprovado, integração/autorização e interface de Waze continuam abertas.
