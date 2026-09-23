# Roadmap do Lorem

> Fonte de verdade para progresso, historias e criterios de aceite. Para economizar
> contexto, localize a historia pelo sumario e leia apenas sua secao e as dependencias
> indicadas. Regras detalhadas ficam em [`SPEC.md`](SPEC.md).

## Estado atual

**Foco atual:** Historia 07 - Encerrar e apresentar o resultado.

**Ultima historia verificada:** Historia 06 - Detectar submissoes durante o Ipsum.

**Primeiro item pendente:** permitir encerramento manual sem AC.

**Proxima entrega demonstravel:** encerrar manualmente e apresentar o resultado do Ipsum.

**Bloqueios conhecidos:** nenhum.

## Como atualizar

- `[ ]`: ainda nao comprovado, mesmo que exista codigo parcial.
- `[x]`: implementado e verificado pelos criterios de aceite.
- `BLOQUEADO:`: depende de informacao, decisao ou recurso externo.
- Nunca conclua a historia enquanto algum criterio obrigatorio estiver pendente.
- Atualize somente itens para os quais exista evidencia automatizada ou confirmacao
  manual registrada em [`SESSIONS.md`](SESSIONS.md).

## Dependencias entre historias

O caminho principal e sequencial: **00 -> 01 -> 02 -> ... -> 14**. Consulte historias
anteriores somente quando o item atual depender de contrato ou evidencia nelas. As
historias 08 e 13 tambem dependem das decisoes abertas explicitamente citadas em seus
checklists; veja [`DECISIONS.md`](DECISIONS.md).

## Sumario rapido

- [Historia 00 - Estrutura basica](#historia-00---estrutura-basica)
- [Historia 01 - Vincular o handle do Codeforces](#historia-01---vincular-o-handle-do-codeforces)
- [Historia 02 - Importar o historico do Codeforces](#historia-02---importar-o-historico-do-codeforces)
- [Historia 03 - Carregar o catalogo de problemas](#historia-03---carregar-o-catalogo-de-problemas)
- [Historia 04 - Recomendar e iniciar um Ipsum](#historia-04---recomendar-e-iniciar-um-ipsum)
- [Historia 05 - Executar um Ipsum cronometrado](#historia-05---executar-um-ipsum-cronometrado)
- [Historia 06 - Detectar submissoes durante o Ipsum](#historia-06---detectar-submissoes-durante-o-ipsum)
- [Historia 07 - Encerrar e apresentar o resultado](#historia-07---encerrar-e-apresentar-o-resultado)
- [Historia 08 - Calcular o Rating Lorem](#historia-08---calcular-o-rating-lorem)
- [Historia 09 - Historico e pendencias](#historia-09---historico-e-pendencias)
- [Historia 10 - Detectar AC posterior](#historia-10---detectar-ac-posterior)
- [Historia 11 - Estatisticas confiaveis](#historia-11---estatisticas-confiaveis)
- [Historia 12 - Recomendacao orientada por variedade](#historia-12---recomendacao-orientada-por-variedade)
- [Historia 13 - Consolidacao de faixa](#historia-13---consolidacao-de-faixa)
- [Historia 14 - Fluxo completo e robustez](#historia-14---fluxo-completo-e-robustez)

### Historia 00 - Estrutura basica

**Historia:** Como desenvolvedor, quero uma base pequena e testavel para implementar o fluxo completo sem retrabalho.

#### Checklist

- [x] Criar ou confirmar o projeto Android com Kotlin e Compose.
- [x] Definir pacotes ou camadas para dados, dominio e interface.
- [x] Criar navegacao entre Configuracao, Inicio, Ipsum, Resultado, Historico e Estatisticas.
- [x] Configurar armazenamento local.
- [x] Criar interfaces para acesso ao Codeforces e ao banco local.
- [x] Criar implementacoes falsas para testar o fluxo sem depender da rede.
- [x] Confirmar os comandos de build, teste e lint neste arquivo.
- [x] Executar build inicial com sucesso.

#### Criterios de aceite

- [x] O aplicativo abre sem erro.
- [x] E possivel navegar entre telas provisórias.
- [x] Um dado de teste continua existindo depois de fechar e reabrir o app.
- [x] As telas nao acessam diretamente rede ou banco.

---

### Historia 01 - Vincular o handle do Codeforces

**Historia:** Como usuario, quero informar meu handle para o Lorem acompanhar meu treino.

#### Checklist

- [x] Criar campo e acao de confirmacao do handle.
- [x] Validar o handle com `user.info`.
- [x] Exibir identificacao e rating oficial quando disponiveis.
- [x] Salvar o perfil localmente.
- [x] Restaurar o perfil ao reabrir o app.
- [x] Permitir atualizar ou trocar o handle com confirmacao.
- [x] Tratar usuario inexistente, falta de internet, limite da API e resposta `FAILED`.

#### Exemplos de aceite

- [x] **Dado** um handle valido, **quando** confirmar, **entao** a Home e aberta e o perfil permanece salvo.
- [x] **Dado** um handle invalido, **quando** confirmar, **entao** o usuario permanece na tela e recebe uma mensagem clara.
- [x] **Dada** uma falha de rede, **quando** tentar novamente, **entao** o app nao perde o texto digitado nem trava.

---

### Historia 02 - Importar o historico do Codeforces

**Historia:** Como usuario, nao quero receber nenhum exercicio que ja tentei ou resolvi.

#### Checklist

- [x] Buscar todas as submissoes necessarias com `user.status`.
- [x] Paginar quando uma unica resposta nao cobrir o historico.
- [x] Mapear problemas por `contestId + index`.
- [x] Registrar problemas com AC.
- [x] Registrar problemas tentados sem AC.
- [x] Deduplicar varias submissoes do mesmo problema.
- [x] Salvar o historico localmente.
- [x] Atualizar sem duplicar registros.
- [x] Mostrar estado de sincronizacao e falhas recuperaveis.

#### Exemplos de aceite

- [x] Um problema com qualquer submissao anterior e considerado tentado.
- [x] WA seguido de AC aparece como resolvido, mas continua marcado como ja tentado.
- [x] Sincronizar duas vezes produz o mesmo conjunto de problemas.

---

### Historia 03 - Carregar o catalogo de problemas

**Historia:** Como sistema, quero um catalogo local para recomendar rapidamente sem repetir chamadas desnecessarias.

#### Checklist

- [x] Buscar `problemset.problems`.
- [x] Salvar identificacao, nome, rating e tags.
- [x] Ignorar no sorteio problemas sem rating.
- [x] Atualizar o catalogo sem apagar historico dos Ipsums.
- [x] Registrar a data da ultima atualizacao.
- [x] Permitir usar o catalogo salvo quando a rede estiver indisponivel.

#### Exemplos de aceite

- [x] A recomendacao pode ser calculada localmente depois da sincronizacao.
- [x] Atualizar o catalogo nao duplica problemas.
- [x] Problemas sem rating nunca entram como candidatos.

---

### Historia 04 - Recomendar e iniciar um Ipsum

**Historia:** Como usuario, quero apertar um botao e receber um problema inedito adequado ao meu nivel.

#### Checklist

- [x] Criar a acao `Iniciar novo Ipsum`.
- [x] Impedir um segundo Ipsum quando houver outro ativo.
- [x] Sortear a categoria com probabilidades de 1/3.
- [x] Aplicar a faixa de rating correspondente.
- [x] Remover todos os problemas inelegiveis.
- [x] Aplicar a prioridade inicial de variedade.
- [x] Sortear entre os melhores candidatos restantes.
- [x] Persistir o Ipsum e o horario antes de abrir o link.
- [x] Registrar eventual fallback de rating.
- [x] Nao revelar rating, tags ou categoria.

#### Exemplos de aceite

- [x] Nenhum problema do historico remoto ou local pode ser escolhido.
- [x] Fechar o app imediatamente apos iniciar nao perde o Ipsum.
- [x] Repetir a selecao com dados controlados respeita as tres categorias.
- [x] Se nao existir candidato exato, o fallback e previsivel e registrado.

---

### Historia 05 - Executar um Ipsum cronometrado

**Historia:** Como usuario, quero resolver o problema enquanto o Lorem acompanha tempo e uso da dica.

#### Checklist

- [x] Mostrar titulo, identificador e link do problema.
- [x] Calcular o tempo pelo horario persistido, nao por um contador descartavel.
- [x] Restaurar o tempo correto apos rotacao, fechamento e retorno ao app.
- [x] Nao oferecer pausa.
- [x] Criar `Revelar topicos` com confirmacao.
- [x] Registrar uso e horario da dica apenas uma vez.
- [x] Mostrar tags somente depois da confirmacao.
- [x] Criar `Verificar submissoes`.
- [x] Criar `Encerrar sem AC` com confirmacao.

#### Exemplos de aceite

- [x] Reabrir o app dez minutos depois mostra aproximadamente dez minutos adicionais.
- [x] Consultar as tags fica registrado mesmo que o app seja fechado.
- [x] Tocar novamente na dica nao cria outro evento.

---

### Historia 06 - Detectar submissoes durante o Ipsum

**Historia:** Como usuario, quero que o Lorem reconheca erros e o primeiro AC feitos durante o desafio.

#### Checklist

- [x] Consultar `user.status` sob demanda.
- [x] Considerar somente o problema do Ipsum ativo.
- [x] Ignorar submissoes anteriores ao horario de inicio.
- [x] Deduplicar por identificador da submissao.
- [x] Registrar WA, TLE, RTE e demais vereditos.
- [x] Contar erros anteriores ao primeiro AC.
- [x] Tratar submissao ainda em teste sem finalizar incorretamente.
- [x] Encerrar no primeiro AC valido.
- [x] Respeitar o intervalo minimo da API.
- [x] Verificar novamente ao retornar para a tela, quando apropriado.

#### Exemplos de aceite

- [x] Uma submissao antiga do mesmo problema nao encerra o Ipsum.
- [x] WA seguido de AC registra um erro e encerra com sucesso.
- [x] Uma submissao em teste nao e tratada como falha definitiva.
- [x] Varias verificacoes nao duplicam a mesma submissao.

---

### Historia 07 - Encerrar e apresentar o resultado

**Historia:** Como usuario, quero entender claramente o que aconteceu no Ipsum.

#### Checklist

- [x] Revelar rating, categoria e tags.
- [x] Mostrar tempo total.
- [x] Mostrar quantidade e tipos de erros.
- [x] Mostrar se houve dica.
- [x] Exigir um motivo ao encerrar sem AC.
- [ ] Oferecer os motivos confirmados de fracasso.
- [ ] Persistir o estado final de forma atomica.
- [ ] Impedir encerramento ou pontuacao duplicados.
- [ ] Preparar o espaco para explicar a alteracao de rating.

#### Motivos de fracasso

- [ ] Nao entendi o enunciado.
- [ ] Nao encontrei a logica.
- [ ] Nao conhecia o conteudo.
- [ ] Encontrei a solucao, mas nao consegui implementar.
- [ ] Tive erro de implementacao.
- [ ] Faltou tempo.

#### Exemplos de aceite

- [x] Um AC apresenta todas as informacoes escondidas.
- [x] Um encerramento sem AC nao prossegue sem motivo.
- [ ] Reabrir o resultado nao altera os dados.

---

### Historia 08 - Calcular o Rating Lorem

**Historia:** Como usuario, quero um feedback coerente sobre meu nivel individual.

#### Entradas obrigatorias

- rating do usuario antes do Ipsum;
- rating do problema;
- AC ou ausencia de AC;
- tempo gasto;
- uso ou nao da dica de tags.

#### Checklist

- [ ] Resolver as decisoes DA-02 e DA-03.
- [ ] Escrever uma tabela de cenarios antes da formula.
- [ ] Definir o resultado esperado ao comparar usuario e problema.
- [ ] Definir tempo esperado por diferenca de rating.
- [ ] Definir penalidade da dica.
- [ ] Definir ganho e perda maximos.
- [ ] Implementar o calculo como funcao pura.
- [ ] Salvar rating anterior, variacao e rating posterior.
- [ ] Explicar a variacao na tela de resultado.
- [ ] Garantir que AC posterior nao recalcule o Ipsum.
- [ ] Criar testes unitarios para limites e cenarios comuns.

#### Propriedades que a formula precisa respeitar

- [ ] No mesmo problema e tempo, AC sem dica vale mais que AC com dica.
- [ ] No mesmo resultado, resolver um problema mais dificil vale mais.
- [ ] No mesmo problema, ultrapassar muito o tempo esperado nao pode valer mais.
- [ ] Nao resolver nao aumenta o rating.
- [ ] O rating nunca e atualizado duas vezes pelo mesmo Ipsum.
- [ ] O usuario consegue entender a explicacao sem conhecer a formula.

---

### Historia 09 - Historico e pendencias

**Historia:** Como usuario, quero revisar meus resultados e problemas ainda nao resolvidos.

#### Checklist

- [ ] Criar aba `Resolvidos em Ipsums`.
- [ ] Criar aba `Pendentes`.
- [ ] Criar aba `Resolvidos fora de Ipsums`.
- [ ] Mostrar problema, rating, tags, tempo, dica, erros, data e variacao.
- [ ] Permitir abrir o problema no Codeforces.
- [ ] Permitir filtrar por rating, tag e resultado.
- [ ] Garantir que cada problema apareca em uma unica situacao atual.
- [ ] Manter os dados originais do Ipsum depois de uma mudanca de aba.

#### Exemplos de aceite

- [ ] AC durante o desafio aparece em `Resolvidos em Ipsums`.
- [ ] Encerramento sem AC aparece em `Pendentes`.
- [ ] Mudar o estado nao apaga tempo, dica ou erros originais.

---

### Historia 10 - Detectar AC posterior

**Historia:** Como usuario, quero que um upsolve retire automaticamente o problema da lista de pendencias.

#### Checklist

- [ ] Criar a acao `Atualizar pendencias`.
- [ ] Consultar novas submissoes sem baixar desnecessariamente todo o historico.
- [ ] Comparar apenas com os problemas pendentes.
- [ ] Mover o problema para `Resolvidos fora de Ipsums` ao encontrar AC posterior.
- [ ] Registrar a data do AC posterior.
- [ ] Nao alterar o rating original.
- [ ] Tornar a operacao idempotente.
- [ ] Exibir resultado da sincronizacao.

#### Exemplos de aceite

- [ ] Um pendente com AC novo muda de lista uma unica vez.
- [ ] Um novo WA permanece pendente.
- [ ] Atualizar repetidamente nao altera rating nem duplica registros.

---

### Historia 11 - Estatisticas confiaveis

**Historia:** Como usuario, quero visualizar meus pontos fortes, fracos e evolucao.

#### Checklist

- [ ] Mostrar Rating Lorem atual e evolucao.
- [ ] Mostrar total de Ipsums e taxa de AC.
- [ ] Mostrar taxa de AC sem dica e com dica.
- [ ] Mostrar tempo medio geral e por rating.
- [ ] Mostrar erros antes do AC.
- [ ] Mostrar desempenho por faixa de rating.
- [ ] Mostrar desempenho por tag.
- [ ] Mostrar tags mais e menos praticadas.
- [ ] Mostrar tags com maior taxa de falha.
- [ ] Distinguir AC no Ipsum de AC posterior.
- [ ] Validar cada agregado contra o historico.

#### Exemplos de aceite

- [ ] Com um conjunto pequeno de dados conhecido, todos os totais podem ser conferidos manualmente.
- [ ] Problemas com varias tags contribuem uma vez para cada tag, sem duplicar o total de Ipsums.
- [ ] Resolver fora do Ipsum atualiza pendencias, mas nao a taxa de AC durante Ipsums.

---

### Historia 12 - Recomendacao orientada por variedade

**Historia:** Como usuario, quero praticar estilos variados sem escolher manualmente os topicos.

#### Checklist

- [ ] Calcular exposicao, AC, falha e uso de dica por tag.
- [ ] Criar uma pontuacao simples de necessidade por tag.
- [ ] Priorizar problemas que atendam tags necessarias.
- [ ] Manter rating e ineditismo como restricoes anteriores a tag.
- [ ] Evitar repetir excessivamente a mesma tag.
- [ ] Manter algum sorteio entre bons candidatos.
- [ ] Registrar os fatores da recomendacao para depuracao.
- [ ] Nao revelar a justificativa antes do fim do Ipsum.
- [ ] Testar com perfis artificiais de historico.

#### Exemplos de aceite

- [ ] Um usuario sem pratica de grafos recebe prioridade de grafos quando houver candidato adequado.
- [ ] Uma tag fraca nao permite selecionar problema ja tentado.
- [ ] Varias recomendacoes nao produzem sempre o mesmo problema ou a mesma tag.

---

### Historia 13 - Consolidacao de faixa

**Historia:** Como usuario, quero que meu nivel represente consistencia e variedade, nao acertos isolados.

#### Checklist

- [ ] Resolver a decisao DA-04.
- [ ] Definir faixas em passos de 100 pontos.
- [ ] Definir quantidade minima de Ipsums na faixa.
- [ ] Definir desempenho minimo.
- [ ] Definir quantidade minima de AC sem dica.
- [ ] Definir diversidade minima de tags.
- [ ] Considerar desempenho recente sem apagar o historico.
- [ ] Calcular `rating estimado` e `faixa consolidada` separadamente.
- [ ] Mostrar o que falta para consolidar a proxima faixa.
- [ ] Criar testes para avanco, permanencia e perda de consistencia.

#### Exemplo de aceite

- [ ] O app pode mostrar `Rating estimado: 1518`, `Faixa consolidada: 1400` e explicar objetivamente o que falta.
- [ ] Um unico AC dificil nao consolida uma nova faixa.
- [ ] Diversos ACs do mesmo estilo nao simulam variedade suficiente.

---

### Historia 14 - Fluxo completo e robustez

**Historia:** Como usuario e autor do TCC, quero demonstrar o Lorem do inicio ao fim sem improvisacao.

#### Checklist funcional

- [ ] Perfil novo completa o fluxo inicial.
- [ ] Historico remoto impede repeticoes.
- [ ] Catalogo funciona apos sincronizacao.
- [ ] Ipsum sobrevive a rotacao e reinicio do processo.
- [ ] Dica fica registrada.
- [ ] Submissoes sao detectadas corretamente.
- [ ] Resultado altera o rating uma vez.
- [ ] Pendencia pode virar AC externo.
- [ ] Estatisticas batem com o historico.
- [ ] Recomendacao considera variedade.
- [ ] Consolidacao explica o progresso.

#### Checklist de qualidade

- [ ] Estados de carregamento e vazio.
- [ ] Mensagens de erro recuperaveis.
- [ ] Funcionamento razoavel sem internet usando dados locais.
- [ ] Protecao contra cliques repetidos.
- [ ] Acessibilidade basica e textos legiveis.
- [ ] Testes unitarios das regras centrais.
- [ ] Testes de integracao usando repositorio falso.
- [ ] Build, testes e lint sem erros relevantes.
- [ ] Roteiro de demonstracao ensaiado.

#### Fluxo final de demonstracao

```text
handle -> sincronizacao -> recomendacao -> Ipsum -> dica opcional
-> submissao -> resultado -> historico -> estatisticas -> proxima recomendacao
```

---

## 10. Marcos demonstraveis

- **A - Espinha funcional (00-07):** handle, recomendacao, Ipsum, submissao e resultado.
- **B - Memoria e avaliacao (08-10):** rating, historico, pendencias e upsolve.
- **C - Orientacao de estudo (11-13):** estatisticas, variedade e consolidacao.
- **D - Entrega (14):** fluxo completo testado, explicavel e demonstravel.

---
