# Starlight — estudo de desenvolvimento

**Não é um tema instalável.** Esta pasta está em `dev/studies`, fora de `source/v1.0` e `Themes/v1.0`. Não contém `theme.xml`, não entra no catálogo OTA e não altera o APK. A PR destina-se à revisão contra `preview`.

## Abrir

- Theme Lab: `cd cluster-widgets && npm ci && npm run dev`, depois use os links **Estudos em desenvolvimento**
- `index.html`: desenho final da revisão 10, com dados e menus simulados. Também abre como arquivo local
- `contract-lab.html`: fixtures explícitas do contrato v1.0 ligadas ao mesmo renderer; sem conexão ao carro

O desenho, efeitos e menus aprovados foram preservados. Somente `index.html` ganhou identificação/guardas de desenvolvimento e `renderer.js` ganhou uma entrada explícita de estado, sem alterar o caminho de demonstração. `source-manifest.json` registra os hashes da revisão recebida. As fontes e suas licenças são locais.

## Limites verificáveis

- O Canvas tem fundo opaco e velocímetro em `(960,308)`, dentro do vão que o Apex reserva ao centro ADAS (`520,120,880,530`)
- O numeral 120 ancora dentro da zona TSR e o 80 dentro da zona ESP. A zona READY também precisa de auditoria de efeitos. As coordenadas vêm de `dev/fixed-cluster-foreground.js`; não representam uma nova medição no veículo
- A sobreposição visual do centro foi aceita pelo proprietário; a prioridade real de avisos e estados assistivos ainda precisa ser estabelecida antes de uma implementação instalável
- Os menus de ajustes, AC e Trip B continuam simulações locais. Seus descritores não são executados como comandos
- O laboratório mostra velocidade CAN bruta, sem a calibração do HUD aplicada pelo Apex. Bateria é derivada exclusivamente de tensão × corrente; a polaridade física não foi validada
- Dados ausentes permanecem `--`; zero é válido; o valor numérico acima da escala é mantido, com geometria saturada. O laboratório não usa os trilhos temporais de velocidade/bateria da demonstração
- Nenhuma página chama `window.Android`. A presença desse objeto impede a inicialização; o adaptador aceita somente um mock explícito, não registra callbacks globais do host e encerra ao perder essa condição
- Testes offline e browser não comprovam desempenho embarcado, composição OEM nem segurança de condução

## Validação

```sh
cd cluster-widgets/dev/studies/Starlight
npm ci
npm test
```

`npm test` cobre desenho/legibilidade, decoder e estados simulados, controlador, adaptador de contrato, renderer com fixtures e barreira de empacotamento. `test:browser` é separado; veja o relatório da PR para o ambiente efetivamente executado. Resultados gerados em `qa/` ficam fora do Git.

`DESIGN-REVISION-10.md`, `APEX-COVERAGE.md` e `MENU-COVERAGE.md` preservam o contexto do estudo. Referências a exportações de vídeo nesses documentos pertencem à entrega visual anterior; vídeos e scripts de exportação não fazem parte desta PR.

## Obsidian

Obsidian será tratado em uma PR separada pelo fluxo já iniciado pelo proprietário. Esta mudança contém somente Starlight e não duplica aquela implementação.

## Para tornar instalável

1. Validar quais avisos/estados nativos continuam visíveis e resolver as colisões com TSR/ESP sem alterar silenciosamente o desenho aprovado
2. Escolher composição e limites do centro com base no compositor real
3. Portar lifecycle, calibração, projeção e controles explícitos usando o contrato existente; sem ampliar a bridge
4. Medir WebView/CPU/GPU e lifecycle no ambiente autorizado; só então preparar manifesto e pacote em PR própria ou revisão desta PR

Nenhuma instalação, ativação, merge ou publicação foi realizada por estes arquivos.
