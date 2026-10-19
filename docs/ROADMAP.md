# Roadmap do Lorem

> Fonte de verdade para progresso, historias e criterios de aceite. Para economizar
> contexto, localize a historia pelo sumario e leia apenas sua secao e as dependencias
> indicadas. Regras detalhadas ficam em [`SPEC.md`](SPEC.md).

## Estado atual

**Foco atual:** Historias 35-37 implementadas em preview Spark: handles por conta, ciclo/rating Firestore e prazo de 2h. Proxima historia funcional 38. Historias 00-30 e gates cientificos 31/32 preservados.

**Ultima historia verificada:** Historia 37 no preview Spark, em 2026-10-19; [evidencia integrada da PR 69](evidence/2026-10-19-firebase-merge-69.json), preservando a [verificacao anterior 35-37](evidence/2026-10-19-firebase-35-37.json). Marco 30 preservado: 232 debug/170 release e 66 Python, contrato Android-Python com fixture explicita.

**Primeiro item funcional pendente:** Historia 38 (migracao/rollout). Historia 31 continua BLOQUEADA: nenhum export real de Ipsums fornecido; o pre-registro nao substitui a coorte real.

**Proxima entrega demonstravel:** migracao e jornada integrada da 38, com projeto real preparado. No estudo, coletar novas tentativas com snapshots anteriores e preparar coorte por participante; sem integrar previsoes antes da 31.

**Bloqueios conhecidos:** Ipsums reais n=0/insufficient_data. 32 exige evidencia e decisao favoravel da 31; dependencia 29 atendida. Conferencia manual opcional (D-014).

**Novo bloco planejado em 19/10/2026:** Historias 33-38, login Google/Firebase,
perfis por uid+handle com rating inicial 800 e limite absoluto de 2h por Ipsum.
35-37 implementadas em preview: handles, ciclo/rating Firestore e prazo absoluto 2h.
Proxima funcionalidade: 38 (migracao/rollout); nao depende dos dados reais da 31.
33/34 mantem confirmacao de Google unico pendente. Publicacao informada antes
cobre somente Rules 33/34; novas Rules 35-37 precisam ser publicadas.
[Contratos e migracao](FIREBASE.md) · [Configuracao](FIREBASE_SETUP.md).

## Como atualizar

- `[ ]`: ainda nao comprovado, mesmo que exista codigo parcial.
- `[x]`: implementado e verificado pelos criterios de aceite.
- `BLOQUEADO:`: depende de informacao, decisao ou recurso externo.
- Nunca conclua a historia enquanto algum criterio obrigatorio estiver pendente.
- Atualize somente itens para os quais exista evidencia automatizada ou confirmacao
  manual registrada em [`SESSIONS.md`](SESSIONS.md).
- Pela D-014, testes manuais sao opcionais: nao impedem concluir a historia ou
  iniciar a seguinte. Mantenha conferencias humanas fora dos criterios obrigatorios
  e nao as marque como executadas sem evidencia. Verificacoes automatizadas
  obrigatorias continuam bloqueando a conclusao quando falharem.

## Dependencias entre historias

As Historias **00-14 estao concluidas**. O bloco **12 -> 13 -> 14** foi implementado
e verificado nesta sessao.
Consulte historias anteriores somente para os contratos e evidencias necessarios.
A DA-04 da Historia 13 foi definida nesta conversa pela D-015; a implementacao deve
preservar a maior faixa alcancada. Veja [`DECISIONS.md`](DECISIONS.md).

## Plano executado das Historias 12-14

**Plano executado em 2026-10-13:** Historias 12, 13 e 14 concluidas com 147 testes
debug e 116 release sem falhas, lint sem erros e APK debug gerado.

Replanejamento em 2026-10-13, conferido contra `main` em
`9e434b0d76f5fddcae84d24486eddcdd19d7d3ac`. A [PR #28](https://github.com/Enzofmcs/Lorem/pull/28)
implementou a Historia 11 e foi integrada. Seu aceite e as confirmacoes das
Historias 10/11 permanecem registrados abaixo, sem reabrir ou refazer esses itens.

Base reutilizavel: `buildIpsumStatistics`, `IpsumStatistics`, `AttemptStatistics`,
`TagStatistics`, `StatisticsUiState`, tela com Resumo/Por rating/Por tag e
`StatisticsFixtures`. A D-013 continua sendo o contrato desses indicadores.

| Ordem | Historia | Base e dependencias | Entrega demonstravel | Verificacao para concluir |
|---|---|---|---|---|
| 1 | 12 - Variedade (concluida) | Metricas por tag da 11; filtros, categoria, fallback e `RandomSource` existentes. | Recomendacao tambem considera falha, dica e recencia, preservando rating e ineditismo. | Perfis artificiais, exposicao deduplicada, sorteio controlado, sigilo, persistencia e migracao dos fatores. |
| 2 | 13 - Consolidacao (concluida) | Historico original, campo `consolidatedRating` e DA-04 definida pela D-015. | Maior faixa alcancada preservada, separada do rating estimado, com faltantes da proxima faixa. | Limites, diversidade, janelas, recuperacao, isolamento por perfil e escritas concorrentes. |
| 3 | 14 - Robustez (concluida) | Fluxos 00-13 integrados e testes existentes. | Jornada completa reproduzivel com dados locais, falhas e recuperacao. | Jornada automatizada, Room/DataStore/migracoes e invariantes; regressao final `test lint assembleDebug`. |

Implemente cada historia em pequenas entregas verticais, na ordem indicada. Durante
as iteracoes, use testes direcionados de `TESTING.md`; ao encerrar uma historia
relevante ou marco, execute a regressao de Nivel 5. Testes manuais e ensaio humano
sao opcionais e nao bloqueiam avanco (D-014).

**Continuidade do bloco 12-14:** nao ha historia funcional pendente neste bloco. Para demonstracao
e avaliacao do TCC, consultar [`DEMO.md`](DEMO.md). Os passos abaixo registram o
planejamento executado e as evidencias de cada historia.

## Novo bloco: automacao e apresentacao

Solicitacao de 13/10/2026, conferida contra `main` em
`485f4131113a0098511ba2bdbab6519f35467568` ([PR #29](https://github.com/Enzofmcs/Lorem/pull/29)).
As Historias 00-14 e suas evidencias permanecem concluidas.

Ordem proposta: **15 -> 16 -> 17**, seguida de **18 -> 19 -> 20 -> 21**.
A 18 pode iniciar independentemente da automacao; 19 integra 16/17 e a 21 nao depende
da 20. [SYNC.md](SYNC.md) e [DESIGN.md](DESIGN.md) detalham os planos.
As Historias 15-17 foram implementadas e verificadas em 2026-10-15 e as Historias 18-21 em 2026-10-16; o bloco planejado esta concluido.

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
- [Historia 15 - Base segura para sincronização automática](#historia-15---base-segura-para-sincronizacao-automatica)
- [Historia 16 - Sincronização em segundo plano com WorkManager](#historia-16---sincronizacao-em-segundo-plano-com-workmanager)
- [Historia 17 - Detecção automática de submissões e status de atualização](#historia-17---deteccao-automatica-de-submissoes-e-status-de-atualizacao)
- [Historia 18 - Identidade visual, temas e navegação do Lorem](#historia-18---identidade-visual-temas-e-navegacao-do-lorem)
- [Historia 19 - Redesign da configuração e tela inicial](#historia-19---redesign-da-configuracao-e-tela-inicial)
- [Historia 20 - Redesign da experiência do Ipsum e resultado](#historia-20---redesign-da-experiencia-do-ipsum-e-resultado)
- [Historia 21 - Redesign do histórico e estatísticas](#historia-21---redesign-do-historico-e-estatisticas)

- [Historia 22 - Base Python e protocolo reproduzivel do estudo](#historia-22---base-python-e-protocolo-reproduzivel-do-estudo)
- [Historia 23 - Coleta auditavel de contests e ratings historicos](#historia-23---coleta-auditavel-de-contests-e-ratings-historicos)
- [Historia 24 - Tempos sequenciais, censura e cobertura das tentativas](#historia-24---tempos-sequenciais-censura-e-cobertura-das-tentativas)
- [Historia 25 - Referencias simples e particoes por contest](#historia-25---referencias-simples-e-particoes-por-contest)
- [Historia 26 - Modelo interpretavel de tempo ate AC](#historia-26---modelo-interpretavel-de-tempo-ate-ac)
- [Historia 27 - Avaliacao de erros, probabilidades e incerteza](#historia-27---avaliacao-de-erros-probabilidades-e-incerteza)
- [Historia 28 - Relatorio do estudo e reproducao completa para o TCC](#historia-28---relatorio-do-estudo-e-reproducao-completa-para-o-tcc)
- [Historia 29 - Corte verificavel de problemas nas novas recomendacoes](#historia-29---corte-verificavel-de-problemas-nas-novas-recomendacoes)
- [Historia 30 - Base local de validacao com tentativas Ipsum](#historia-30---base-local-de-validacao-com-tentativas-ipsum)
- [Historia 31 - Validar relacao entre Rating Codeforces e Rating Lorem](#historia-31---validar-relacao-entre-rating-codeforces-e-rating-lorem)
- [Historia 32 - Integracao local versionada das previsoes validadas](#historia-32---integracao-local-versionada-das-previsoes-validadas)

- [Historia 33 - Base Firebase Spark e configuracao reproduzivel](#historia-33---base-firebase-spark-e-configuracao-reproduzivel)
- [Historia 34 - Login Google e sessao com Firestore no Spark](#historia-34---login-google-e-sessao-com-firestore-no-spark)
- [Historia 35 - Handles por conta e Rating Lorem inicial em 800](#historia-35---handles-por-conta-e-rating-lorem-inicial-em-800)
- [Historia 36 - Ciclo oficial do Ipsum e rating persistidos no Firestore](#historia-36---ciclo-oficial-do-ipsum-e-rating-persistidos-no-firestore)
- [Historia 37 - Encerramento automatico do Ipsum em ate duas horas](#historia-37---encerramento-automatico-do-ipsum-em-ate-duas-horas)
- [Historia 38 - Migracao e jornada integrada de contas e prazo](#historia-38---migracao-e-jornada-integrada-de-contas-e-prazo)

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
- [x] Oferecer os motivos confirmados de fracasso.
- [x] Persistir o estado final de forma atomica.
- [x] Impedir encerramento ou pontuacao duplicados.
- [x] Preparar o espaco para explicar a alteracao de rating.

#### Motivos de fracasso

- [x] Nao entendi o enunciado.
- [x] Nao encontrei a logica.
- [x] Nao conhecia o conteudo.
- [x] Encontrei a solucao, mas nao consegui implementar.
- [x] Tive erro de implementacao.
- [x] Faltou tempo.

#### Exemplos de aceite

- [x] Um AC apresenta todas as informacoes escondidas.
- [x] Um encerramento sem AC nao prossegue sem motivo.
- [x] Reabrir o resultado nao altera os dados.

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

- [x] Resolver as decisoes DA-02 e DA-03.
- [x] Escrever uma tabela de cenarios antes da formula.
- [x] Definir o resultado esperado ao comparar usuario e problema.
- [x] Definir tempo esperado por diferenca de rating.
- [x] Definir penalidade da dica.
- [x] Definir ganho e perda maximos.
- [x] Implementar o calculo como funcao pura.
- [x] Salvar rating anterior, variacao e rating posterior.
- [x] Explicar a variacao na tela de resultado.
- [x] Garantir que AC posterior nao recalcule o Ipsum.
- [x] Criar testes unitarios para limites e cenarios comuns.

#### Propriedades que a formula precisa respeitar

- [x] No mesmo problema e tempo, AC sem dica vale mais que AC com dica.
- [x] No mesmo resultado, resolver um problema mais dificil vale mais.
- [x] No mesmo problema, ultrapassar muito o tempo esperado nao pode valer mais.
- [x] Nao resolver nao aumenta o rating.
- [x] O rating nunca e atualizado duas vezes pelo mesmo Ipsum.
- [x] O usuario consegue entender a explicacao sem conhecer a formula.

---

### Historia 09 - Historico e pendencias

**Historia:** Como usuario, quero revisar meus resultados e problemas ainda nao resolvidos.

#### Checklist

- [x] Criar aba `Resolvidos em Ipsums`.
- [x] Criar aba `Pendentes`.
- [x] Criar aba `Resolvidos fora de Ipsums`.
- [x] Mostrar problema, rating, tags, tempo, dica, erros, data e variacao.
- [x] Permitir abrir o problema no Codeforces.
- [x] Permitir filtrar por rating, tag e resultado.
- [x] Garantir que cada problema apareca em uma unica situacao atual.
- [x] Manter os dados originais do Ipsum depois de uma mudanca de aba.

#### Exemplos de aceite

- [x] AC durante o desafio aparece em `Resolvidos em Ipsums`.
- [x] Encerramento sem AC aparece em `Pendentes`.
- [x] Mudar o estado nao apaga tempo, dica ou erros originais.

#### Implementacao e verificacao em 2026-10-02

- A tela provisoria foi substituida por uma tela alimentada por `LoremRepository.ipsums`,
  com as tres situacoes mutuamente exclusivas, estado vazio, campos completos, link
  do Codeforces e filtros combinaveis de rating, tag e resultado/situacao.
- Como ainda nao existe evidencia persistida de AC posterior, `Resolvidos fora de
  Ipsums` permanece vazio no fluxo real; a classificacao pura ja exige evidencia
  explicita, sem consultar o historico geral nem alterar o Ipsum original.
- Foram adicionados testes unitarios de classificacao, exclusividade, preservacao e
  filtros, alem de testes Robolectric/Compose de campos, estados vazios, troca de aba,
  limpeza de filtros e abertura do link.
- Os testes direcionados passaram com Java 17 e Android SDK 35, comprovando os
  criterios funcionais e permitindo marcar os checklists acima.
- **CONFIRMADO PELO USUARIO EM DISPOSITIVO:** legibilidade da lista, combinacao dos
  filtros, troca entre as tres situacoes, limpeza e abertura no Codeforces funcionam
  como esperado.
- A explicacao do Rating Lorem da Historia 08 tambem foi confirmada como compreensivel
  pelo usuario e seu ultimo criterio foi marcado.

---

### Historia 10 - Detectar AC posterior

**Historia:** Como usuario, quero que um upsolve retire automaticamente o problema da lista de pendencias.

#### Checklist

- [x] Criar a acao `Atualizar pendencias`.
- [x] Consultar novas submissoes sem baixar desnecessariamente todo o historico.
- [x] Comparar apenas com os problemas pendentes.
- [x] Mover o problema para `Resolvidos fora de Ipsums` ao encontrar AC posterior.
- [x] Registrar a data do AC posterior.
- [x] Nao alterar o rating original.
- [x] Tornar a operacao idempotente.
- [x] Exibir resultado da sincronizacao.

#### Exemplos de aceite

- [x] Um pendente com AC novo muda de lista uma unica vez.
- [x] Um novo WA permanece pendente.
- [x] Atualizar repetidamente nao altera rating nem duplica registros.

#### Implementacao e verificacao em 2026-10-02

- A consulta limitada para no encerramento mais antigo das pendencias e valida cada
  submissao por `contestId + index`, veredito e instante estritamente posterior.
- A migracao 8-9 persiste o estado `SOLVED_OUTSIDE` e o primeiro AC posterior; a
  transicao condicional preserva integralmente o resultado e o Rating Lorem.
- Testes direcionados cobrem regras temporais, identidade, falhas remotas, paginacao,
  persistencia, idempotencia, migracao e estados da tela.
- CONFIRMADO PELO USUARIO EM 2026-10-13: todos os itens do checklist manual da
  Historia 10 passaram, incluindo AC posterior real, mudanca unica de situacao,
  data correta, preservacao de rating/tempo/dica/erros, repeticao idempotente,
  persistencia ao reabrir e manutencao da pendencia quando ha somente novo WA.

---

### Historia 11 - Estatisticas confiaveis

**Historia:** Como usuario, quero visualizar meus pontos fortes, fracos e evolucao.

#### Checklist

- [x] Mostrar Rating Lorem atual e evolucao.
- [x] Mostrar total de Ipsums e taxa de AC.
- [x] Mostrar taxa de AC sem dica e com dica.
- [x] Mostrar tempo medio geral e por rating.
- [x] Mostrar erros antes do AC.
- [x] Mostrar desempenho por faixa de rating.
- [x] Mostrar desempenho por tag.
- [x] Mostrar tags mais e menos praticadas.
- [x] Mostrar tags com maior taxa de falha.
- [x] Distinguir AC no Ipsum de AC posterior.
- [x] Validar cada agregado contra o historico.

#### Exemplos de aceite

- [x] Com um conjunto pequeno de dados conhecido, todos os totais podem ser conferidos manualmente.
- [x] Problemas com varias tags contribuem uma vez para cada tag, sem duplicar o total de Ipsums.
- [x] Resolver fora do Ipsum atualiza pendencias, mas nao a taxa de AC durante Ipsums.

#### Contrato dos indicadores (D-013)

- Uma tentativa encerrada por `id`, do handle atual (sem diferenciar maiusculas),
  incluindo `COMPLETED`, `PENDING` e `SOLVED_OUTSIDE`; ativos nao entram nem revelam
  suas tags, rating ou categoria pela tela de estatisticas.
- Taxa geral: AC no Ipsum / encerrados. Com/sem dica: AC no grupo / encerrados no
  respectivo grupo. Sem denominador, mostrar `Sem dados`, nunca 0% observado.
- Tempo medio: fim original - inicio, incluindo fracassos; tempos ausentes ou
  invalidos ficam fora da media, com a quantidade de tempos validos exibida.
- Erros: total e media anteriores ao AC somente em `COMPLETED`.
- Evolucao: ordenar encerramentos por data e id; mostrar rating anterior, delta e
  posterior persistidos, omitindo avaliacoes incompletas com aviso de quantidade.
- Por rating: agrupar pelo rating selecionado do problema (passos oficiais de 100).
- Por tag: cada tag oficial recebe uma contribuicao da tentativa; totais gerais
  nao somam os grupos. Tags do catalogo local sem exposicao aparecem com zero
  tentativas e taxa indisponivel. Sem catalogo, usar as tags do historico encerrado.
- Mais/menos praticadas: ordenar por tentativas, com desempate alfabetico. Maior
  taxa de falha: (encerrados - AC no Ipsum) / encerrados, excluindo tags sem falha
  ou sem amostra; desempatar por maior amostra e nome. Mostrar amostra junto da taxa.
- Upsolve preserva taxa de AC, falha, tempo, dica, erros e rating originais.

#### Conjunto conhecido para conferir o aceite

| Ipsum | Resultado | Dica | Tempo | Rating do problema | Tags |
|---|---|---|---:|---:|---|
| A | AC no Ipsum | nao | 30 min | 1200 | dp, graphs |
| B | AC no Ipsum | sim | 45 min | 1200 | graphs |
| C | Pendente | nao | 60 min | 1400 | dp |
| D | AC posterior | nao | 25 min | 1400 | dp, math |

Esperado: 4 encerrados, 2 AC no Ipsum (50%), sem dica 1/3 (33,3%),
com dica 1/1 (100%), tempo medio 40 min, 1 pendente e 1 AC posterior.
Com erros 1 e 2 nos AC, o total antes do AC e 3 e a media e 1,5.
Rating 1200: 2/2 AC e 37,5 min; rating 1400: 0/2 AC e 42,5 min.
`dp`: 1/3 AC; `graphs`: 2/2; `math`: 0/1. Uma tag `greedy` do catalogo
sem tentativa tem exposicao zero e nao entra entre as tags com falha.
Os snapshots de rating da fixture sao dados armazenados para testar agregacao,
nao uma nova especificacao da formula D-012.

#### Implementacao e verificacao em 2026-10-11

- Agregador puro `buildIpsumStatistics`, derivado dos dados locais e do perfil atual,
  com snapshots de rating, grupos por rating/tag e rankings auditaveis.
- `StatisticsScreen` substitui a rota provisoria por resumo, evolucao cronologica,
  visoes por rating/tag, amostras, estados vazio/carregando e retorno ao Inicio.
- O `StateFlow` combina perfil, Ipsums e catalogo; a troca de handle exclui dados
  anteriores e a navegacao protege contra um snapshot em cache do outro perfil.
- Testes de dominio conferem o conjunto conhecido, multiplas tags, repeticao,
  grupos com/sem dica, desempates, tempos ausentes, rating antigo incompleto,
  identidade da tentativa e preservacao de indicadores apos upsolve.
- Testes de ViewModel e Robolectric/Compose conferem atualizacoes locais, troca
  de perfil, estados vazios, todos os indicadores, secoes e jornada de navegacao.
- Na implementacao de 2026-10-11, a jornada manual da Historia 10 ainda estava
  pendente. O usuario confirmou todos os itens manuais das Historias 10 e 11
  em 2026-10-13, permitindo o encerramento de ambas.

#### Comandos e resultados desta sessao

Ambiente: Java 17.0.12, Gradle 8.11.1 e SDK Android 35; caches e ferramentas
em diretorios temporarios, sem alterar dependencias do projeto.

- `./gradlew testDebugUnitTest --tests dev.lorem.app.domain.BuildIpsumStatisticsTest --console=plain`:
  passou na primeira verificacao do agregador.
- `./gradlew testDebugUnitTest --tests dev.lorem.app.domain.BuildIpsumStatisticsTest --tests dev.lorem.app.ui.LoremViewModelTest --tests dev.lorem.app.ui.StatisticsScreenTest --tests dev.lorem.app.MainActivityTest --max-workers=2 --console=plain`:
  primeira tentativa falhou porque o diretorio de cache temporario do Robolectric
  nao existia; criado o diretorio, 36/37 passaram. A falha restante era a assertiva
  de visibilidade da Home que preserva a rolagem ao voltar; ajustado o teste para
  rolar ate o titulo antes de conferir a exibicao.
- `./gradlew testDebugUnitTest --tests dev.lorem.app.MainActivityTest --max-workers=2 --console=plain`:
  6/7 passaram durante o ajuste da assertiva acima.
- `./gradlew testDebugUnitTest --tests 'dev.lorem.app.MainActivityTest.statistics route uses saved training history offline and returns to home' --max-workers=2 --console=plain`:
  passou depois da correcao do teste de navegacao.
- `./gradlew test lint assembleDebug --max-workers=2 --console=plain`: passou;
  125 testes debug e 97 release, sem falhas. Lint: zero erros e o aviso preexistente
  `MissingApplicationIcon`. APK debug gerado com sucesso.
- `git diff --check` e validacao dos links/ancoras Markdown alterados: passaram.

#### Conferencia em dispositivo - confirmada em 2026-10-13

- [x] Confirmar legibilidade, navegacao e indicadores com o historico real.

**CONFIRMADO PELO USUARIO:** todos os itens do checklist manual passaram,
incluindo navegacao, Rating Lorem e evolucao, totais/taxas, grupos com/sem dica,
tempo e erros, grupos por rating/tag, multiplas tags, rankings, amostras vazias,
exclusao de Ipsum ativo, leitura e rotacao, uso offline e preservacao dos
indicadores originais apos AC posterior.

1. Na Home, abra `Estatisticas`. Confira Rating Lorem atual contra o perfil e
   quantidade de encerrados contra o Historico; um Ipsum ativo nao deve entrar.
2. No Resumo, confira AC, grupos com/sem dica, amostras de tempo e erros. Na evolucao,
   compare rating anterior, variacao e posterior com os Resultados dos Ipsums.
3. Em `Por rating`, confira os grupos, taxas e tempos. Em `Por tag`, confira um
   problema com varias tags: ele conta uma vez em cada tag e uma vez no total geral.
4. Confira tags sem pratica e amostras pequenas. Sem tentativas, a taxa deve dizer
   `Sem dados`; 0% deve representar tentativas reais sem AC.
5. Volte ao Inicio, feche/reabra o app sem internet e confira os mesmos dados locais.
   Rotacione o dispositivo e confira leitura, rolagem e troca entre as tres secoes.
6. Apos um AC posterior real, use `Atualizar pendencias` no Historico. Nas estatisticas,
   pendentes deve diminuir e AC posterior aumentar, mantendo taxa de AC no Ipsum,
   tempo medio, erros, dica e evolucao do rating. Repetir nao deve alterar os dados.

A implementacao e os criterios funcionais estao comprovados automaticamente.
Com a confirmacao manual do usuario registrada em 2026-10-13, as Historias 10 e 11
estao concluidas. O proximo foco e a Historia 12.

---

### Historia 12 - Recomendacao orientada por variedade

**Historia:** Como usuario, quero praticar estilos variados sem escolher manualmente os topicos.

**Dependencias:** Historias 04 e 11 concluidas; D-010, D-005 e D-013. **Risco:** Nivel 4 (recomendacao).

#### Base existente e ordem de selecao

`RecommendIpsum` ja sorteia a categoria, filtra ineditismo, busca a primeira camada
de rating disponivel ate 300 pontos e favorece tags ineditas/pouco praticadas.
Estender essa selecao; preservar `RandomSource`, `StartIpsum` e os contratos atuais.

1. Excluir problemas sem rating, tentados no Codeforces ou usados em qualquer Ipsum.
2. Sortear a categoria com 1/3 de chance e buscar a primeira camada elegivel de
   rating conforme D-010. Uma necessidade de tag nunca justifica ampliar o fallback.
3. Dentro dessa camada, ordenar por necessidade: tags nunca praticadas, menor
   exposicao, maior taxa de fracasso original e maior uso de dica, nessa ordem.
   Preferir um vetor de prioridades explicito a pesos arbitrarios que invertam a ordem.
4. Entre candidatos equivalentes, favorecer menor sobreposicao de tags com o ultimo
   Ipsum encerrado e sortear os empates restantes. Nao remover o unico candidato
   valido por repeticao de tag; problemas sem tags nao ganham prioridade artificial.

Exposicao usa problemas unicos da uniao do historico Codeforces e dos Ipsums, com
tags oficiais do catalogo/snapshot e identidade `contestId + index`. Desempenho e
uso de dica usam os Ipsums encerrados da Historia 11, pois o historico importado
nao contem tempo, dica ou resultado de um Ipsum. Uma taxa sem amostra nao indica
fraqueza. AC posterior nao melhora artificialmente a taxa de sucesso original.

#### Passos de implementacao

1. Reutilizar `buildIpsumStatistics(profile, ipsums, catalog)` e `TagStatistics`:
   `attempts.total`, `accepted`, `failures` e `hintCount` ja estao disponiveis. Derivar
   taxas com amostra; complementar a exposicao remota deduplicada por problema, com
   casos de catalogo incompleto e o mesmo problema presente nas duas fontes. Nao
   introduzir outro agregador de estatisticas nem alterar a D-013.
2. Usar comparacao lexicografica entre candidatos com tags: quantidade de tags
   ineditas (maior), exposicao total dessas tags (menor), media das taxas de falha
   com amostra (maior), media de uso de dica com amostra (maior) e sobreposicao com
   o ultimo Ipsum encerrado (menor). Sem amostra nao ha prioridade de fraqueza;
   candidatos sem tags ficam apos os candidatos com tags da mesma camada, sem
   receber vantagem por soma vazia. Sortear os empates finais. Preservar categoria,
   primeira camada de rating disponivel e limites de fallback.
3. Integrar falha, dica e desempate de recencia ao `RecommendIpsum`; testar perfis
   artificiais e sorteio injetado, sem depender de estatistica aleatoria nos testes.
4. Incluir fatores resumidos na recomendacao persistida por `StartIpsum`: tags,
   exposicao, fracasso, dica e desempate, alem da categoria/faixa/fallback ja salvos. Se exigir
   novos campos Room, criar migracao com valores compativeis para Ipsums antigos.
5. Disponibilizar os fatores para depuracao ou apos o encerramento, com testes que
   garantam sigilo enquanto ativo. Regressar filtros, fallback, unico ativo e
   persistencia; concluir com as verificacoes de marco da historia.

#### Cenarios de verificacao

- Empate de exposicao: maior falha prevalece; empatada a falha, maior uso de dica.
- Grafos nunca praticados prevalecem sobre tags dominadas na mesma camada de rating.
- Problema fraco ja tentado, pendente ou usado em Ipsum continua excluido.
- Necessidade forte a 400 pontos nao ultrapassa o limite de fallback de 300.
- Multiplas tags e ausencia de tags nao criam duplicidade nem prioridade indevida.
- Sorteios controlados alcancam mais de um candidato equivalente; menor sobreposicao
  evita repeticao quando ha alternativa equivalente. Um unico elegivel ainda funciona.

#### Checklist

- [x] Calcular exposicao, AC, falha e uso de dica por tag.
- [x] Criar uma pontuacao simples de necessidade por tag.
- [x] Priorizar problemas que atendam tags necessarias.
- [x] Manter rating e ineditismo como restricoes anteriores a tag.
- [x] Evitar repetir excessivamente a mesma tag.
- [x] Manter algum sorteio entre bons candidatos.
- [x] Registrar os fatores da recomendacao para depuracao.
- [x] Nao revelar a justificativa antes do fim do Ipsum.
- [x] Testar com perfis artificiais de historico.

#### Exemplos de aceite

- [x] Um usuario sem pratica de grafos recebe prioridade de grafos quando houver candidato adequado.
- [x] Uma tag fraca nao permite selecionar problema ja tentado.
- [x] Com alternativas equivalentes, recencia e sorteio controlado permitem variar problemas e tags; sem alternativa, o unico candidato valido continua selecionavel.

#### Implementacao

- `TagNeed`/`RecommendationFactors` usam os agregados da Historia 11, exposicao por
  problema unico, taxas somente com amostra e desempate por recencia/sorteio.
- Categoria, filtros de identidade, faixa e fallback da D-010 continuam anteriores
  a necessidade por tag. Dados de outro handle nao influenciam a recomendacao.
- `StartIpsum` salva os fatores em Room; migracao 9-10 adiciona um campo nullable
  compativel com registros antigos. Resultado revela a explicacao, mantendo-a
  ausente da tela ativa mesmo apos revelar a dica.
- `RecommendVarietyTest` verifica exposicao deduplicada, snapshots fora do catalogo,
  falha, dica, recencia, sorteio, tags ausentes, exclusoes, fallback e perfis.


---

### Historia 13 - Consolidacao de faixa

**Historia:** Como usuario, quero que meu nivel represente consistencia e variedade, nao acertos isolados.

**Dependencias:** Historia 11; manter compatibilidade com a 12. **Risco:** Nivel 4
(regra de dominio e persistencia do perfil).

**Regra definida (DA-04 / D-015):** em uma janela dos 20 encerrados mais recentes,
uma faixa `[F, F + 99]` exige 8 tentativas, 70% de AC no Ipsum, 5 ACs sem dica e,
nesses ACs sem dica, 3 tags oficiais em 3 conjuntos de tags distintos. Faixas comecam
em 800, em passos de 100. AC posterior permanece insucesso original.

**A maior faixa alcancada e permanente.** Perda de consistencia reduz o Rating Lorem
conforme D-012, mas nao reduz a consolidacao. Sem evidencia de marco, manter `null`.
A proxima faixa e a consolidada + 100; sem marco, usar a faixa do rating estimado
arredondada para baixo em passos de 100, com minimo 800. A avaliacao pode reconhecer
uma faixa superior quando ela satisfizer todos os criterios, sem exigir treinar
faixas inferiores ja distantes do nivel atual.

#### Passos de implementacao

1. Criar tabela de cenarios da D-015 antes do calculo: 7/8 tentativas, 69%/70% AC,
   4/5 ACs sem dica, 2/3 tags e conjuntos, AC isolado e repeticao de estilo. Incluir
   perda de evidencia recente preservando o marco e AC posterior sem promove-lo.
2. Implementar uma funcao pura (`CalculateConsolidatedRating`) que retorna faixa
   alcancada e requisitos cumpridos/faltantes da proxima faixa. Reutilizar os modelos
   originais e o contrato da 11; nao substituir seus agregados. Avaliar cada janela
   historica de ate 20 encerrados para recuperar marcos cuja escrita foi interrompida;
   a janela recente calcula o progresso atual. Nao contar ativo ou upsolve como AC.
3. Manter Rating Lorem estimado e consolidacao separados. Nao recalcular ratings
   antigos nem usar o rating estimado para substituir evidencia de consolidacao.
   O resultado e o maximo entre a faixa persistida e a maior faixa comprovada;
   ausencia de evidencia recente nao apaga o marco.
4. Recalcular apos mudancas relevantes no historico/perfil e persistir pelo campo
   `consolidatedRating` existente. Garantir idempotencia, dono correto e recuperacao
   apos reinicio; evitar que uma escrita de consolidacao sobrescreva rating mais
   recente ou que observar/salvar o perfil cause um ciclo de atualizacoes.
5. Mostrar na Home/Estatisticas rating estimado, faixa ou dados insuficientes e
   faltantes objetivos (tentativas, ACs, autonomia e diversidade). Cobrir dados
   vazios, limites, multitag, ordem temporal, avanco e preservacao apos falhas.
   Reutilizar `StatisticsScreen`/`StatisticsUiState` e Home para mostrar o progresso;
   encerrar o marco C com `test lint assembleDebug`.

#### Checklist

- [x] Resolver a decisao DA-04 (D-015, definida nesta conversa).
- [x] Definir faixas em passos de 100 pontos.
- [x] Definir quantidade minima de Ipsums na faixa.
- [x] Definir desempenho minimo.
- [x] Definir quantidade minima de AC sem dica.
- [x] Definir diversidade minima de tags.
- [x] Considerar desempenho recente sem apagar o historico.
- [x] Calcular `rating estimado` e `faixa consolidada` separadamente.
- [x] Mostrar o que falta para consolidar a proxima faixa.
- [x] Criar testes para avanco e permanencia da faixa, inclusive apos perda de consistencia.

#### Exemplo de aceite

- [x] O app pode mostrar `Rating estimado: 1518`, `Faixa consolidada: 1400` e explicar objetivamente o que falta.
- [x] Um unico AC dificil nao consolida uma nova faixa.
- [x] Diversos ACs do mesmo estilo nao simulam variedade suficiente.

#### Implementacao

- `calculateConsolidatedRating` avalia janelas ordenadas do historico original,
  recupera a maior faixa comprovada e nunca reduz um marco persistido. A janela
  recente produz contagens/faltantes da proxima faixa, conforme D-015.
- A reconciliacao local grava apenas o campo de consolidacao e os campos de rating
  necessarios; preserva dono, rating mais recente e o marco apos reinicio.
- Home mostra resumo sem deslocar a acao principal; Estatisticas mostra os requisitos
  completos. Os indicadores da Historia 11 e seu conjunto conhecido foram preservados.
- `CalculateConsolidatedRatingTest` cobre fronteiras, falta de amostra/autonomia,
  variedade, upsolve, ordenacao, identidade, perfil e permanencia apos fracassos.


---

### Historia 14 - Fluxo completo e robustez

**Historia:** Como usuario e autor do TCC, quero demonstrar o Lorem do inicio ao fim sem improvisacao.

**Dependencias:** Historias 00-13 concluidas. **Risco:** Nivel 4 nas recuperacoes e
Nivel 5 no encerramento do produto.

#### Passos de implementacao e verificacao

1. Mapear os criterios abaixo aos testes existentes e acrescentar somente lacunas.
   Reutilizar `MainActivityTest`, `StatisticsScreenTest`, `LoremViewModelTest`,
   `StatisticsFixtures`, `IpsumPersistenceTest`, `LoremDatabaseMigrationTest` e testes
   da API/gate. Preservar a suite e o conjunto conhecido da Historia 11 (media 40 min,
   4 encerrados); um fake sozinho nao comprova persistencia nem migracao real.
2. Automatizar duas jornadas com Codeforces falso e tempo/sorteio controlados:
   perfil -> sincronizacao -> recomendacao -> dica opcional -> WA -> AC -> resultado
   -> historico -> estatisticas -> proxima recomendacao; e encerramento sem AC ->
   pendencia -> AC posterior -> atualizacao idempotente. Validar consolidacao com
   um historico conhecido suficiente e inspecionar os dados, alem da navegacao.
3. Testar recuperacao com armazenamento real: recriacao da tela, nova instancia do
   repositorio sobre o mesmo banco/DataStore e restauracao de app com Ipsum ativo.
   Confirmar cronometro por timestamps e resultado/rating aplicado uma unica vez,
   incluindo interrupcao entre a transacao Room e reconciliacao do perfil.
4. Exercitar `FAILED`, falta de rede, limite da API, pagina incompleta, ausencia de
   candidatos, cliques concorrentes e troca de handle. Auditar escritas de perfil:
   `SynchronizeProblemHistory` hoje salva uma copia capturada antes da consulta;
   sincronizacao tardia nao pode sobrescrever novo rating ou consolidacao. Usar
   atualizacao atomica dos campos necessarios e testar a concorrencia. Sem rede,
   preservar historico/estatisticas e requisitos de ineditismo; permitir recuperacao.
5. Verificar estados vazios/carregando, rotulos acessiveis e acoes com Compose/lint;
   executar `./gradlew test`, `./gradlew lint` e `./gradlew assembleDebug`. Resolver
   erros relevantes e registrar comandos/resultados reais, sem duplicar execucoes.
6. Documentar roteiro de demonstracao reproduzivel e limites da avaliacao do TCC
   conforme `SPEC.md`. Ensaio humano e conferencia em dispositivo ficam opcionais,
   separados do aceite; concluir o marco D quando as evidencias obrigatorias passarem.

#### Checklist funcional

- [x] Perfil novo completa o fluxo inicial.
- [x] Historico remoto impede repeticoes.
- [x] Catalogo funciona apos sincronizacao.
- [x] Ipsum sobrevive a rotacao e reinicio do processo.
- [x] Dica fica registrada.
- [x] Submissoes sao detectadas corretamente.
- [x] Resultado altera o rating uma vez.
- [x] Pendencia pode virar AC externo.
- [x] Estatisticas batem com o historico.
- [x] Recomendacao considera variedade.
- [x] Consolidacao explica o progresso.

#### Checklist de qualidade

- [x] Estados de carregamento e vazio.
- [x] Mensagens de erro recuperaveis.
- [x] Funcionamento razoavel sem internet usando dados locais.
- [x] Protecao contra cliques repetidos.
- [x] Acessibilidade basica e textos legiveis.
- [x] Testes unitarios das regras centrais.
- [x] Testes de integracao usando repositorio falso.
- [x] Build, testes e lint sem erros relevantes.
- [x] Roteiro de demonstracao documentado e jornada correspondente verificada automaticamente.

#### Fluxo final de demonstracao

```text
handle -> sincronizacao -> recomendacao -> Ipsum -> dica opcional
-> submissao -> resultado -> historico -> estatisticas -> proxima recomendacao
```

#### Implementacao e mapa de evidencias

| Criterio | Evidencia automatizada |
|---|---|
| Primeiro acesso e importacao antes de recomendar | `CompleteAppJourneyTest`, `LoremViewModelTest`, `MainActivityTest`. |
| WA, dica, AC, resultado e proxima recomendacao | `CompleteAppJourneyTest` e `FullTrainingJourneyTest`, com armazenamento real e Codeforces isolado no teste. |
| Pendencia, upsolve e indicadores preservados | As mesmas jornadas, `UpdatePendingIpsumsTest`, `IpsumPersistenceTest` e os testes da Historia 11. |
| Restauracao e rating aplicado uma vez | Reabertura do banco/repositorio e simulacao de interrupcao Room -> DataStore em `FullTrainingJourneyTest`; marcador do ultimo Ipsum avaliado impede reaplicar resultado antigo apos um ciclo de rating. |
| Faixa permanente e isolamento de perfil | `CalculateConsolidatedRatingTest`, `FullTrainingJourneyTest`, `LoremViewModelTest` e protecoes de snapshots em navegacao. |
| Apenas um ativo e dica imutavel apos encerramento | Inicios concorrentes, indice unico, gravacao condicional da dica e testes de persistencia/jornada. |
| Falha e retorno do Codeforces | Suites de API/gate, sincronizacao, verificacao de submissoes e pendencias; jornada com erro de rede. |
| Navegacao, estado salvo e acessibilidade basica | Compose/Robolectric, controles Material com semantica, restauracao de estado de tentativa ativa e lint. |

- A sincronizacao atualiza somente o timestamp do dono consultado; metadados do
  perfil preservam rating/consolidacao atuais. Respostas tardias do perfil anterior
  nao substituem estados da interface do perfil novo.
- Iniciar novo Ipsum limpa estados de resultado/verificacao anteriores. Retomada
  automatica restaura o ativo, e Historico oferece retorno ao Inicio.
- Roteiro reproduzivel e limites do TCC: [`DEMO.md`](DEMO.md). Ensaio em dispositivo
  continua opcional; nao foi executado nesta sessao.


---

## Verificacao final das Historias 12-14 em 2026-10-13

Ambiente: Java 17, Gradle 8.11.1 e SDK Android 35. Verificacoes executadas:

- `./gradlew :app:compileDebugKotlin --max-workers=2 --console=plain`: passou.
- `./gradlew :app:testDebugUnitTest --tests dev.lorem.app.domain.RecommendIpsumTest --tests dev.lorem.app.domain.RecommendVarietyTest --max-workers=2 --console=plain`: passou.
- `./gradlew :app:testDebugUnitTest --tests dev.lorem.app.data.local.FullTrainingJourneyTest --max-workers=2 --console=plain`: 6 testes passaram, incluindo inicios concorrentes e dica apos encerramento.
- `./gradlew test lint assembleDebug --max-workers=2 --console=plain`: passou com o
  ultimo codigo; 147 testes debug e 116 release, zero falhas/erros. Lint: zero erros
  e somente o aviso preexistente `MissingApplicationIcon`. APK gerado em
  `app/build/outputs/apk/debug/app-debug.apk`.
- `git diff --check` e validacao de 50 links locais/ancoras: passaram, incluindo
  preservacao da base/contrato da Historia 11, IDs de decisoes e cinco sessoes.

As iteracoes direcionadas tambem cobriram limites de consolidacao, persistencia e
migracao, ViewModel, navegacao e a jornada Compose. Falhas de fixtures (Ipsum ativo
com avaliacao ja preenchida e builder de teste sem migracao) foram corrigidas;
esperas da UI passaram a aguardar dados/semantica e continuacoes do looper Android.
O caso de fronteira da taxa respeita o marco permanente: uma janela anterior ja
qualificada nao pode ser anulada por um resultado posterior abaixo de 70%.

**TESTE MANUAL OPCIONAL - NAO EXECUTADO:** nenhum teste em dispositivo foi alegado
nesta sessao. Isso nao bloqueia conclusao nem avanco, conforme D-014. As confirmacoes
anteriores das Historias 10/11 permanecem preservadas.

---

### Historia 15 - Base segura para sincronizacao automatica

**Historia:** Como usuário, quero que atualizações simultâneas preservem meu histórico, perfil e Rating Lorem.

**Status:** concluida; criterios verificados automaticamente em 2026-10-15.

**GitHub:** [#30 — História 15 — Base segura para sincronização automática](https://github.com/Enzofmcs/Lorem/issues/30).

**Dependencias:** Histórias 02, 03, 06, 08 e 10 já implementadas.

**Escopo:** Extrair a coordenação dos disparos manuais do ViewModel para um ponto compartilhado no AppContainer. Reutilizar o cliente, gate e casos de uso atuais. Preparar importação incremental, estado persistido e proteção contra concorrência antes de adicionar timers.

**Referencia:** [SYNC.md](SYNC.md).

#### Criterios de aceite

- [x] UI, workers e polling compartilham cliente e gate; o total de chamadas, incluindo paginação, nunca excede uma requisição a cada dois segundos.
- [x] Disparos da mesma operação/perfil são unificados; consultas do Ipsum podem prosseguir entre páginas da importação sem duplicar trabalhos.
- [x] Primeira importação completa e retomável; atualizações seguintes mesclam dados incrementais sem perder tentativa ou AC já salvo, usando contestId + index.
- [x] Checkpoint só avança após persistência; sobreposição e deduplicação por id toleram timestamps iguais, novas submissões entre páginas e veredictos ainda não definitivos.
- [x] Trocar/remover perfil cancela trabalhos antigos e invalida resultados por handle normalizado e versão do vínculo, inclusive na troca A → B → A.
- [x] Reutilizar markHistorySynchronized e updateProfileMetadata existentes: atualizações não regravam snapshots de Rating Lorem/faixa/handle; validar também a nova concorrência entre workers, consultas e troca de vínculo.
- [x] Último sucesso, progresso inicial e falha são persistidos por conjunto de dados; falha parcial/cancelamento preserva cache e não marca preparo como concluído.
- [x] Consulta, encerramento e upsolve concorrentes são idempotentes; rating é aplicado uma vez e AC posterior mantém a avaliação original.

#### Verificacao planejada

Nível 4: testes direcionados de API, domínio e Room/DataStore para paginação, cancelamento, recuperação, troca de perfil, gate e concorrência com rating. Cobrir migração se houver mudança de esquema; regressão de marco conforme TESTING.md. Conferencias em dispositivo sao opcionais conforme D-014; criterios funcionais exigem evidencia automatizada proporcional ao risco.

**Exemplo de aceite:** Uma importação disputa com AC e troca de handle: o perfil correto permanece salvo, as proteções atuais de rating/consolidação são preservadas e não há dupla avaliação.

#### Implementacao e evidencias em 2026-10-15

- `AppContainer.synchronizationCoordinator` unifica operacoes por conjunto/vinculo,
  usando o cliente e gate compartilhados. UI usa a mesma entrada que os futuros
  workers/polling. Gate monotônico; teste concorrente intercala paginas e consulta
  do Ipsum mantendo intervalos de pelo menos dois segundos.
- Importacao em lotes com sobreposicao, cursor depois da persistencia em Room,
  varredura adicional do inicio e atualizacoes incrementais por watermark. IDs
  provisórios ampliam o limite temporal ate receberem veredicto definitivo;
  evidencias de tentativa/AC nunca regridem. Histórico vazio exige sucesso real.
- Handle normalizado e versao do vinculo persistidos em DataStore; troca/remocao
  cancela operacoes e cada gravacao confere o vinculo sob o lock de troca de perfil.
  Inclusive validacao do handle e reconciliacao Room -> DataStore rejeitam A -> B -> A.
- Estado, ultimo sucesso, falha e progresso sao separados por conjunto/vinculo.
  Consultas tambem usam id do Ipsum: uma resposta antiga nao atualiza a tentativa
  seguinte. Esquema Room preservado; chaves novas de DataStore aceitam vinculo
  legado com versao zero, sem apagar perfil, rating, consolidacao ou historico.
- `SynchronizationPersistenceTest` (8 cenarios, Room/DataStore reais) comprova
  retomada apos reabertura, falha de pagina/gravacao, cancelamento, timestamps
  iguais, veredicto provisório, A -> B -> A, sucesso parcial, rating concorrente,
  encerramento/upsolve e isolamento entre tentativas. Testes da API, gate e
  ViewModel cobrem limites, disparos duplicados e respostas atrasadas na UI.
- Regressao preserva jornadas, migracoes, estatisticas D-013, rating D-012 e faixa
  permanente D-015. Agendamento e polling continuam para as Historias 16 e 17.

Comandos executados com Java 17, SDK 35 e Gradle 8.11.1 instalado em
`/workspace/toolchains/gradle-8.11.1/bin/gradle`; `JAVA_HOME`, `GRADLE_USER_HOME` e
`ANDROID_HOME` apontam para os toolchains desta sessao. O init script externo
configura proxy e diretorio de cache para os testes Robolectric, sem alterar o app.

```bash
/workspace/toolchains/gradle-8.11.1/bin/gradle :app:compileDebugKotlin --max-workers=2 --console=plain
/workspace/toolchains/gradle-8.11.1/bin/gradle :app:testDebugUnitTest --tests dev.lorem.app.data.local.SynchronizationPersistenceTest --tests dev.lorem.app.data.remote.CodeforcesRequestGateTest --tests dev.lorem.app.data.remote.CodeforcesApiRepositoryTest --tests dev.lorem.app.domain.VerifyIpsumSubmissionsTest --tests dev.lorem.app.domain.UpdatePendingIpsumsTest --tests dev.lorem.app.ui.LoremViewModelTest --max-workers=2 --console=plain --init-script /workspace/toolchains/test-runtime.init.gradle
/workspace/toolchains/gradle-8.11.1/bin/gradle :app:testDebugUnitTest --tests dev.lorem.app.data.local.SynchronizationPersistenceTest --tests dev.lorem.app.ui.LoremViewModelTest --max-workers=2 --console=plain --init-script /workspace/toolchains/test-runtime.init.gradle
/workspace/toolchains/gradle-8.11.1/bin/gradle test lint assembleDebug --max-workers=2 --console=plain --init-script /workspace/toolchains/test-runtime.init.gradle
```

Resultado final: **158 testes debug e 127 release sem falhas**, lint sem erros (apenas
`MissingApplicationIcon` preexistente), APK debug gerado. Testes direcionados passaram;
a ultima regressao inclui o caso adicional de resposta do Ipsum anterior e a
protecao correspondente no ViewModel. As iteracoes iniciais corrigiram chamadas
Kotlin com lambda final e fixtures antigas sem perfil ativo; nenhum teste funcional
permaneceu falhando. `git diff --check` e links locais/ancoras conferidos.

**TESTE MANUAL OPCIONAL - NAO EXECUTADO:** nenhuma conferencia em dispositivo foi
alegada. Isso nao bloqueia a conclusao (D-014). Proximo passo: Historia 16.

---

### Historia 16 - Sincronizacao em segundo plano com WorkManager

**Historia:** Como usuário, quero abrir o Lorem pronto para treinar, com dados do Codeforces atualizados automaticamente.

**Status:** implementada e verificada automaticamente em 2026-10-15.

**GitHub:** [#31 — História 16 — Sincronização em segundo plano com WorkManager](https://github.com/Enzofmcs/Lorem/issues/31).

**Dependencias:** História 15. A verificação do Ipsum em segundo plano será integrada pela História 17.

**Escopo:** Adicionar CoroutineWorkers e agendamento único com injeção manual. Intervalos iniciais: histórico/pendências 15 minutos, catálogo 24 horas e metadados do perfil 24 horas. São intervalos desejados; o Android pode atrasar execuções.

**Referencia:** [SYNC.md](SYNC.md).

#### Criterios de aceite

- [x] Validar o primeiro handle inicia automaticamente importação completa do histórico e catálogo, com estado/progresso por etapa e retomada de importações longas.
- [x] Existe um trabalho periódico único por perfil para histórico/pendências e um global para catálogo; reabrir o app não reinicia nem adia a agenda.
- [x] Workers exigem rede conectada; catálogo periódico também exige bateria não baixa. Troca/remoção de perfil cancela os trabalhos do dono anterior.
- [x] Falhas temporárias e rate limit usam retry/backoff exponencial a partir de 30 segundos, respeitando Retry-After quando disponível; erro permanente não entra em loop imediato.
- [x] Retorno ao app recupera dados ausentes/vencidos e estados abandonados após morte do processo, usando cache local e o coordenador compartilhado.
- [x] Antes de novo Ipsum, tentar atualizar submissões recentes com rede e revalidar o perfil; sem rede, preservar a política D-017 de cache após a primeira importação bem-sucedida, indicando dados locais. Histórico nunca importado bloqueia criação.
- [x] Histórico/pendências/metadados atualizam sem redefinir Rating Lorem; nenhum trabalho gera notificação ou abre uma tela espontaneamente.
- [x] Agenda persiste após encerramento normal do processo e reinício do aparelho; reabertura recupera atraso após parada forçada, sem prometer intervalo exato.

#### Verificacao e evidencias

Nível 4: work-testing para unicidade, constraints, sucesso parcial, retries, cancelamento e retomada; integração com dados reais em dispositivo. Verificações em dispositivo de offline, reinício e parada forçada/reabertura são opcionais (D-014). Executar regressão de marco. Conferencias em dispositivo sao opcionais conforme D-014; criterios funcionais exigem evidencia automatizada proporcional ao risco.

**Exemplo de aceite:** Após conectar um perfil, o usuário não precisa apertar botões de sincronização. Um upsolve é reconhecido no próximo trabalho elegível, preservando o rating original.

#### Implementacao

- `SynchronizationWorker`/`SynchronizationWorkerFactory` usam injecao manual do
  `AppContainer`, com o mesmo coordenador, cliente e gate. `LoremApplication`
  fornece a configuracao do WorkManager; apenas seu initializer automatico foi
  removido do Startup. O manifest mesclado preserva permissao/receiver de boot.
- A observacao do vinculo agenda preparacao por perfil e catalogo global. Periodicos
  usam `KEEP`, 15 min/24 h, rede conectada e bateria nao baixa no catalogo periodico.
  Recuperacao no `onStart` agenda somente conjuntos ausentes, vencidos ou com falha.
  A agenda do catalogo sobrevive a troca/remoção de perfil; trabalhos antigos sao
  cancelados por tag, inclusive quando deixados por outro processo.
- Metadados vencem em 24 h; cada etapa conserva sucesso/progresso/falha separados.
  Falha temporaria, HTTP 408/429/5xx e persistencia recebem retry exponencial de
  30 s; erros permanentes retornam failure, sem loop imediato. `Retry-After`
  (segundos ou data HTTP) e persistido globalmente e respeitado pelo gate.
- Cancelamento do ultimo consumidor interrompe a operacao compartilhada e aguarda
  seu checkpoint; outro consumidor mantem a importacao viva. Retomada nao descarta
  dados parciais nem marca importacao incompleta como pronta.
- Antes da recomendacao, tenta atualizar o historico com orcamento de 12 s. Se a
  primeira importacao ja terminou, falha/timeout permite usar cache com aviso e
  ultimo sucesso. O vinculo e revalidado e conferido atomicamente ao criar o Ipsum,
  inclusive em A -> B -> A. Nenhum worker inicia tela ou notificacao.
- O Inicio observa etapas/progresso persistidos e oferece recuperacao manual.
  A consulta de Ipsum ativo no worker e o polling continuam na Historia 17.

`SynchronizationWorkTest` usa `work-testing`, Room e DataStore reais para nove
cenarios: preparacao/sucesso parcial, retries/permanentes/Retry-After persistido,
A -> B -> A, estados abandonados/TTL, cancelamento com retomada de 1.500 registros,
consumidores concorrentes, criacao atomica, upsolve idempotente e agenda/constraints.
A observacao do perfil e testada sem clique manual; `TestDriver` libera constraints.
A agenda preserva id/proximo disparo; backoff exponencial de 30 s e conferido no
WorkSpec. ViewModel testa ineditismo apos refresh, duplo clique, cache offline,
timeout e troca de perfil; API/gate cobrem rate limit JSON/HTTP e Retry-After.

Comandos executados com Java 17, Gradle 8.11.1 e SDK 35:

```bash
./gradlew compileDebugKotlin --max-workers=2 --console=plain
./gradlew testDebugUnitTest --tests 'dev.lorem.app.data.sync.SynchronizationWorkTest' --tests 'dev.lorem.app.data.remote.CodeforcesRetryAfterTest' --tests 'dev.lorem.app.ui.LoremViewModelTest' --tests 'dev.lorem.app.data.local.SynchronizationPersistenceTest' --max-workers=2 --console=plain
./gradlew test lint assembleDebug --max-workers=2 --console=plain
./gradlew lint assembleDebug --max-workers=2 --console=plain
```

Regressao final: **174 testes debug e 143 release**, zero falhas/erros; lint sem
erros (aviso preexistente `MissingApplicationIcon`) e APK debug gerado. A primeira
passagem completa encontrou somente a assercao do texto antigo de sincronizacao
manual em `HomeScreenTest`; ela foi atualizada para a preparacao automatica e a
regressao foi repetida. As suites anteriores de rating, consolidacao, migracoes e
jornadas completas permanecem preservadas. A ultima conferencia de lint/APK passou
apos explicitar `exported=false` no provider do Startup. `git diff --check` e 80
links/ancoras locais conferidos.

**TESTE MANUAL OPCIONAL - NAO EXECUTADO**, conforme D-014. Conferencia sugerida:
conectar perfil e aguardar preparo sem botoes; interromper/reabrir uma importacao;
fazer upsolve e aguardar ciclo elegivel; conferir cache offline, reboot e parada
forcada/reabertura. A persistencia e os receivers usam o contrato do WorkManager;
nao foi alegada execucao desses cenarios em aparelho nem periodicidade exata.

---

### Historia 17 - Deteccao automatica de submissoes e status de atualizacao

**Historia:** Como usuário, quero que o Lorem reconheça minhas submissões sem cliques e mostre quando os dados foram atualizados.

**Status:** implementada e verificada automaticamente em 2026-10-15.

**GitHub:** [#32 — História 17 — Detecção automática de submissões e status de atualização](https://github.com/Enzofmcs/Lorem/issues/32).

**Dependencias:** Histórias 15 e 16; comportamento das Histórias 06–10 preservado.

**Escopo:** Consultar imediatamente ao entrar/retomar um Ipsum e depois a cada 15 segundos enquanto sua tela estiver visível e RESUMED. Usar o mesmo caso de uso também no trabalho de perfil em segundo plano. Tornar as ações manuais opções de recuperação.

**Referencia:** [SYNC.md](SYNC.md).

#### Criterios de aceite

- [x] Existe um único loop por Ipsum visível em RESUMED, com consulta imediata e intervalo inicial de 15 segundos; rotação e recomposição não multiplicam chamadas.
- [x] Polling para ao sair da tela, ir para segundo plano ou encerrar o Ipsum e retoma com consulta imediata; falhas recebem backoff sem bloquear o cronômetro.
- [x] O worker do perfil verifica um Ipsum ativo no próximo ciclo elegível; AC reconhecido em segundo plano aparece no resultado ao retornar, sem navegação ou notificação espontânea.
- [x] Ignorar submissões anteriores ao início, deduplicar ids, reconsultar veredictos provisórios e preservar primeiro AC/erros, dica e rating em disputas entre polling, worker e encerramento manual.
- [x] Início/Configurações informam preparação inicial e estado por dado, último sucesso, atualização em andamento e cache offline; sucesso parcial não aparece como atualização completa.
- [x] No Ipsum, exibir acompanhamento, última consulta e erro recuperável sem snackbar a cada ciclo; dados ocultos continuam protegidos.
- [x] Remover a dependência de Sincronizar histórico, Atualizar catálogo, Verificar submissões e Atualizar pendências para o fluxo normal; Tentar novamente/Atualizar agora reutilizam o coordenador.
- [x] Sem internet, manter treino ativo, histórico e estatísticas locais; recomendação com cache segue a D-017, exigindo primeira importação bem-sucedida e informando uso de dados salvos.

#### Verificacao e evidencias

Nível 4: testes de ciclo de vida com relógio falso, integração de AC/WA/TESTING, duplicidade e encerramento. Conferência manual opcional abrindo Codeforces, retornando, rotacionando, perdendo rede e fazendo upsolve. Conferencias em dispositivo sao opcionais conforme D-014; criterios funcionais exigem evidencia automatizada proporcional ao risco.

**Exemplo de aceite:** Um WA seguido de AC é detectado com a tela aberta sem tocar em Verificar submissões; rating muda uma vez. Ao voltar de segundo plano, a consulta recupera submissões pendentes.

#### Implementacao

- `IpsumSubmissionMonitor` pertence ao ViewModel e serializa sessoes visiveis.
  `LaunchedEffect(id, lifecycle)` usa `repeatOnLifecycle(RESUMED)`, consulta
  imediatamente e aguarda 15 s apos sucesso. Falhas usam 30/60/120 s (teto),
  voltando a 15 s apos recuperacao. Saida da rota, pausa, troca de vinculo e
  encerramento cancelam o loop; cronometro permanece independente.
- Worker consulta o ativo antes de importar historico, inclusive com os outros
  conjuntos ainda validos. Polling, worker e recuperacao compartilham coordenador,
  cliente e gate. Handle/versao/id esperados impedem respostas de outra tentativa.
- Veredictos provisórios sao reconsultados, ids definitivos deduplicados e
  submissoes anteriores ao inicio ignoradas. Room atualiza a contagem de erros
  durante o ativo. Persistencia local curta finaliza mesmo se reconhecer AC
  remover a tela e cancelar seu consumidor; HTTP continua cancelavel.
- A rota guarda o id iniciado/visualizado, aguarda dados locais carregados e so
  abre seu resultado em RESUMED. Worker nao navega nem notifica; retorno apos
  pausa/restauracao recupera o resultado sem reaplicar rating. Novo Ipsum nao
  reutiliza o resultado anterior durante o carregamento dos flows.
- Inicio e Configuracoes mostram historico/catalogo/pendencias/perfil, ultimo
  sucesso e falha/progresso separados. `failureKind` e um campo opcional novo em
  DataStore; formatos antigos continuam legiveis. Dados vencidos indicam cache
  pendente, sem anunciar atualizacao completa. Sem mudanca de esquema Room.
- Ipsum mostra acompanhamento, erros definitivos, ultima consulta e falha
  recuperavel sem snackbar por ciclo. Acoes `Atualizar agora`/`Tentar novamente`
  continuam no coordenador; preparo e verificacoes normais sao automaticos.
  D-017, sigilo de rating/categoria/tags e estatisticas locais sao preservados.

Evidencias automatizadas:

- `IpsumSubmissionMonitorTest`: ciclo de vida/rotacao, dois bindings concorrentes,
  cancelamento em rede, backoff/recuperacao, WA/TESTING/AC com ids duplicados e
  anteriores ao inicio, dica/rating, worker versus encerramento e upsolve.
  Relogio virtual usa repositorio em memoria; regras/persistencia usam Room e
  DataStore reais. A contagem exata de chamadas fica no teste de relogio isolado,
  evitando misturar avancos virtuais com a latencia real de armazenamento.
- `SynchronizationWorkTest`: worker reconhece WA e AC mesmo com historico fresco,
  preserva primeiro AC/erros e nao duplica rating ao repetir. Suites anteriores
  de concorrencia, migracoes, cache e reconexao do perfil permanecem preservadas.
- `CompleteAppJourneyTest`: WA -> TESTING -> AC sem tocar na recuperacao; sigilo de
  dados; AC em segundo plano sem navegacao, restauracao de estado pausado e
  resultado ao retomar, sem reabrir espontaneamente depois de fecha-lo.
- `SynchronizationStatusTest` e ViewModel: sucesso parcial/offline, ultimo sucesso
  preservado, dados vencidos e acao real de recuperacao em Configuracoes.

Comandos com Java 17, Gradle 8.11.1 e SDK 35:

```bash
./gradlew compileDebugKotlin --max-workers=2 --console=plain
./gradlew testDebugUnitTest --tests 'dev.lorem.app.domain.sync.IpsumSubmissionMonitorTest' --tests 'dev.lorem.app.data.sync.SynchronizationWorkTest' --tests 'dev.lorem.app.data.local.SynchronizationPersistenceTest' --tests 'dev.lorem.app.ui.LoremViewModelTest' --tests 'dev.lorem.app.CompleteAppJourneyTest' --tests 'dev.lorem.app.ui.IpsumScreenTest' --tests 'dev.lorem.app.ui.SynchronizationStatusTest' --max-workers=2 --console=plain
./gradlew test lint assembleDebug --max-workers=2 --console=plain
```

Regressao final: **184 testes debug e 150 release**, zero falhas/erros; lint sem
erros (somente `MissingApplicationIcon`, preexistente), APK debug gerado. Diff,
81 links/ancoras locais e oito criterios conferidos.
As jornadas aguardam a tela renderizada apos o estado Ready, evitando assercoes
durante a transicao de navegacao.

**TESTE MANUAL OPCIONAL - NAO EXECUTADO**, conforme D-014. Conferir em dispositivo:
WA/AC real no Codeforces, alternancia navegador/app, rotacao, perda de rede,
retomada e upsolve; nenhuma execucao em aparelho foi alegada.

---

### Historia 18 - Identidade visual, temas e navegacao do Lorem

**Historia:** Como usuário, quero uma interface consistente, legível e confortável para acompanhar meu treino.

**Status:** implementada e verificada automaticamente em 2026-10-16; conferencia em dispositivo opcional (D-014).

**GitHub:** [#33 — História 18 — Identidade visual, temas e navegação do Lorem](https://github.com/Enzofmcs/Lorem/issues/33).

**Dependencias:** Base Compose existente. Pode começar após o planejamento, independentemente da lógica das Histórias 15–17.

**Escopo:** Adotar a direção visual de caderno de treino: azul profundo, verde de destaque, superfícies discretas, cards de 20 dp e tipografia do sistema. Preparar claro/escuro, componentes usados no produto e navegação Início/Histórico/Estatísticas.

**Referencia:** [DESIGN.md](DESIGN.md).

#### Criterios de aceite

- [x] Criar ColorScheme claro/escuro com papéis semânticos, Typography e Shape; padrão segue sistema, escolha Sistema/Claro/Escuro persiste em Configurações.
- [x] Usar fundo #0B1220, superfície #151F30 e ação #6EE7BE no escuro; fundo #F5F7FA, superfície #FFFFFF e ação #006B55 no claro, conforme DESIGN.md.
- [x] Implementar somente os componentes compartilhados necessários: scaffold/cabeçalho, card de métrica, status de sync, badge, estado vazio/erro e ação principal.
- [x] Adicionar barra inferior com Início, Histórico e Estatísticas, rótulos e estado selecionado; preservar rolagem/back stack. Configurações ficam no cabeçalho.
- [x] Ipsum/Resultado usam fluxo dedicado com voltar; sair da tela não encerra a tentativa e restauração do ativo continua funcionando.
- [x] Validar contraste 4,5:1 no texto normal e 3:1 no texto grande/indicadores necessários, áreas de toque de pelo menos 48 dp e significado além da cor.
- [x] Tema/componentes suportam 320 dp, fonte 200%, paisagem, insets e TalkBack, sem alturas fixas que cortem conteúdo; previews mostram estados relevantes nos dois temas.

#### Verificacao e evidencias

Nível 1 para tokens/layout; Nível 3 para navegação e persistência de tema. Verificar compilação, smoke test visual e navegação. Testar comportamento necessário, sem testes que apenas espelhem cores/padding. Conferencias em dispositivo sao opcionais conforme D-014; criterios funcionais exigem evidencia automatizada proporcional ao risco.

**Exemplo de aceite:** Trocar tema preserva preferência após reabrir o app. Navegar pelas três abas, abrir e retomar um Ipsum funciona com fonte ampliada e rótulos acessíveis.


#### Implementacao

- Paleta fixa clara/escura com papeis semanticos, tipografia do sistema, numeros
  tabulares e formas de 14/20 dp. Sem cores dinamicas que substituam a identidade.
  Barras do sistema acompanham o tema e conteudo respeita safe drawing/IME.
- `PreferencesAppearanceRepository` usa o DataStore existente. Sistema e o padrao;
  valores desconhecidos usam Sistema. Claro/Escuro persistem apos fechar/reabrir
  o armazenamento e sobrevivem a troca/remocao do perfil. Falha de gravacao mantem
  a selecao salva e permite tentar novamente.
- Scaffold/cabecalho, metrica, acao principal, status de sync, badge e vazio/erro
  estao usados nas telas reais. Os redesigns especificos ficam nas Historias 19-21.
- Abas com icones vetoriais, rotulos sempre presentes e semantica selecionavel.
  `saveState`/`restoreState` preservam rolagem, filtros e secao. Configuracoes abrem
  no cabecalho e Voltar retorna a aba anterior. Ipsum/Resultado ficam fora das abas;
  voltar do treino nao o encerra. A restauracao conserva a intencao de navegar
  pelo Inicio, alem de continuar recuperando o ativo na abertura inicial.
- `LoremFoundationPreview` cobre claro, escuro, 320 dp/fonte 200% e paisagem.
  `FoundationLayoutTest` verifica rotulos completos, controles de 48 dp, semantica,
  troca de tema sem reiniciar a composicao, paisagem e insets de sistema/cutout
  simulados. Renderizacoes nativas conferidas em `app/build/reports/visual/`.
- `ThemeContrastTest` mede os pares usados: texto minimo **5,26:1 no claro** e
  **6,04:1 no escuro**; bordas necessarias sobre superficie **4,36:1 / 5,76:1**,
  indicador selecionado **6,49:1 / 10,89:1**. Estados usam texto/icone e selecao
  acessivel, sem depender somente da cor. As regras de dados ocultos permanecem
  cobertas pelas jornadas existentes.

Comandos com Java 17, Gradle 8.11.1 e SDK 35:

```bash
./gradlew compileDebugKotlin --max-workers=2 --console=plain
./gradlew testDebugUnitTest --tests '*PreferencesAppearanceRepositoryTest' --tests '*ThemeContrastTest' --tests '*AppearanceNavigationTest' --tests '*MainActivityTest' --tests '*CompleteAppJourneyTest' --tests '*LoremViewModelTest' --max-workers=2 --console=plain
./gradlew testDebugUnitTest --tests '*AppearanceNavigationTest' --tests '*FoundationLayoutTest' --max-workers=2 --console=plain
./gradlew testDebugUnitTest --tests '*FoundationLayoutTest' --tests '*ThemeContrastTest' --max-workers=2 --console=plain
./gradlew testDebugUnitTest --tests '*FoundationLayoutTest' --max-workers=2 --console=plain
./gradlew test lint assembleDebug --max-workers=2 --console=plain
```

Compilacao passou. As primeiras iteracoes dos testes novos exigiram rolar conteudo
fora do viewport, fornecer motivo valido no encerramento e usar desenho nativo
em vez de PixelCopy. A verificacao de texto tolera apenas um pixel de
arredondamento das metricas nativas; continua rejeitando corte e reticencias.
O primeiro lint apontou atributo XML de API 27 no minimo 26 e a ordem do parametro
Modifier. Removido o atributo (os icones usam WindowCompat) e corrigida a assinatura.
Os testes direcionados finais e a regressao completa passaram: **193 testes debug
e 154 release**, zero falhas/erros. Lint sem erros (somente o aviso preexistente
`MissingApplicationIcon`) e APK debug gerado. Diff e 81 links/ancoras locais
conferidos; sete criterios concluidos. Sem nova dependencia ou migracao Room.

**TESTE MANUAL OPCIONAL - NAO EXECUTADO**, conforme D-014. TalkBack, teclado,
rotacao, modos do aparelho e rede real permanecem conferencia humana opcional;
renderizacoes/testes automatizados nao sao apresentados como uso em dispositivo.

---

### Historia 19 - Redesign da configuracao e tela inicial

**Historia:** Como usuário, quero conectar meu perfil e iniciar um treino com clareza, sem uma tela cheia de botões técnicos.

**Status:** implementada e verificada automaticamente em 2026-10-16; conferencia em dispositivo opcional (D-014).

**GitHub:** [#34 — História 19 — Redesign da configuração e tela inicial](https://github.com/Enzofmcs/Lorem/issues/34).

**Dependencias:** Histórias 16, 17 e 18.

**Escopo:** Organizar o primeiro acesso em handle → validação → preparação automática. Na tela inicial, priorizar Rating Lorem e Iniciar/Continuar Ipsum; status de sincronização e detalhes do perfil ficam em segundo plano. Preservar o card de consolidação e seus requisitos já implementados.

**Referencia:** [DESIGN.md](DESIGN.md).

#### Criterios de aceite

- [x] Configuração mostra marca/promessa curta, handle público, ação Conectar Codeforces, validação inline e progresso/recuperação da preparação por etapa.
- [x] Início apresenta saudação/handle, card de Rating Lorem e rating oficial como referência, resumo de treinos locais e acesso ao último resultado.
- [x] Há uma única ação dominante: Iniciar Ipsum ou Continuar Ipsum; preparação/bloqueio explica a razão e cliques repetidos não criam múltiplas tentativas.
- [x] Retirar botões Abrir… e blocos técnicos de catálogo/histórico do corpo principal, usando a navegação e detalhes em Configurações.
- [x] Status discreto informa último sucesso/atualizando/offline; UI renderiza cache antes da rede sem marcar sucesso inexistente.
- [x] Perfil, tema e detalhes/recuperação da sincronização ficam em Configurações; faixa consolidada e progresso reutilizam a História 13, preservando marco permanente e ausência de evidência como null.
- [x] Validar primeiro acesso, perfil salvo, ativo, vazio, falha de rede e troca de handle em claro/escuro, 320 dp, fonte 200% e TalkBack.

#### Verificacao e evidencias

Nível 3: testes Compose/ViewModel direcionados para estados reais, preparação, navegação e duplo clique; smoke test visual/dispositivo. Reutilizar contratos de sync e recomendação. Conferencias em dispositivo sao opcionais conforme D-014; criterios funcionais exigem evidencia automatizada proporcional ao risco.

**Exemplo de aceite:** Um perfil novo chega à tela pronta para iniciar o primeiro Ipsum sem sincronização manual; um perfil com Ipsum ativo encontra Continuar Ipsum como ação principal.

#### Implementacao

- Primeiro acesso com marca/promessa, handle publico com prefixo visual `@`,
  teclado sem autocorrecao e acao Conectar Codeforces, inclusive pelo IME.
  Erros inline preservam o texto; consulta desabilita campo/botao e mostra progresso.
  Troca de handle mantem confirmacao e so substitui o perfil apos validacao.
- Inicio destaca saudacao/handle, Rating Lorem e rating oficial como referencia.
  Resumo conta somente Ipsums encerrados do perfil; AC posterior nao vira AC
  durante o treino. Ultimo resultado usa o maior instante de encerramento.
- Uma acao principal Iniciar/Continuar Ipsum. Primeira importacao incompleta ou
  catalogo indisponivel explicam o bloqueio e levam a Ver preparacao. Ativo pode
  continuar offline, independentemente da preparacao. Loading impede nova acao;
  ViewModel e gravacao atomica existentes continuam protegendo cliques concorrentes.
- Botoes/blocos tecnicos de historico/catalogo sairam do Inicio. Perfil, tema e
  quatro etapas de sincronizacao ficam em Configuracoes, com progresso, ultimo
  sucesso e recuperacao junto de cada etapa. Todas as acoes usam o coordenador.
- Inicio usa resumo discreto de preparacao/atualizacao/offline/vencimento. Sucesso
  parcial nunca aparece como Dados atualizados; so ha horario geral quando todas
  as etapas tem evidencia, usando o mais antigo dos ultimos sucessos. Sem primeira
  consulta concluida, a falha informa preparacao pendente em vez de inventar cache.
- Card de consolidacao reutilizado com requisitos expansiveis; ausencia de faixa
  permanece null e ausencia de amostra fica sem dados, preservando marco permanente.
  Sem alteracao de dominio, esquema Room, agenda, dependencia ou regra de rating.

Evidencias automatizadas:

- `HomeScreenTest`: bloqueio/liberacao por dados locais, loading/erro, Continuar,
  perfil vazio, cache offline, ultimo resultado e consolidacao sem amostra/marco.
- `MainActivityTest`: validacao/falha/rede/troca de handle e resumo isolado por perfil,
  sem contar ativos/upsolve como AC original, com ultimo resultado por data.
- `CompleteAppJourneyTest`: primeiro acesso passa pela preparacao automatica do
  servico compartilhado sem botoes de sync; conserva dica, WA/AC, pendencia e upsolve.
  `SynchronizationWorkTest` valida o agendamento/worker ja existente; ViewModel e
  `FullTrainingJourneyTest` preservam duplo clique, cache e persistencia critica.
- `SynchronizationStatusTest`: sucesso parcial/offline, primeira importacao sem
  sucesso ficticio, progresso em lotes e recuperacao por etapa.
- `HomeConfigurationLayoutTest`: renderizacoes nativas em claro/escuro, 360 dp,
  320 dp/fonte 200% e paisagem. Texto completo sem reticencias/corte de layout,
  nomes longos, validacao/carregamento, controles de 48 dp e acoes por semantica.
  Previews cobrem primeiro acesso, vazio/preparacao, perfil salvo, ativo e offline.
  `AppearanceNavigationTest` preserva tema, filtros, rolagem e restauracao.

Comandos com Java 17, Gradle 8.11.1 e SDK 35:

```bash
./gradlew compileDebugKotlin --max-workers=2 --console=plain
./gradlew testDebugUnitTest --tests '*HomeScreenTest' --tests '*SynchronizationStatusTest' --tests '*HomeConfigurationLayoutTest' --tests '*MainActivityTest' --tests '*CompleteAppJourneyTest' --tests '*AppearanceNavigationTest' --tests '*LoremViewModelTest' --tests '*SynchronizationWorkTest' --tests '*FullTrainingJourneyTest' --max-workers=2 --console=plain
./gradlew test lint assembleDebug --max-workers=2 --console=plain
```

O bootstrap offline inicial nao encontrava WorkManager no cache; a execucao online
baixou a dependencia ja declarada. A primeira rodada direcionada encontrou uma
expectativa antiga do titulo de Configuracoes; corrigida para o perfil conectado.
Compilacao e direcionados finais passaram. A regressao completa passou com
**198 testes debug e 154 release**, zero falhas/erros, lint sem erros
(somente `MissingApplicationIcon`, preexistente) e APK debug gerado. Diff e 81
links/ancoras locais conferidos. Sete criterios concluidos.

**TESTE MANUAL OPCIONAL - NAO EXECUTADO**, conforme D-014. TalkBack, IME, rotacao,
rede e Codeforces reais seguem conferencia humana opcional. As verificacoes de
semantica/renderizacao automatizadas nao representam uso humano de TalkBack.

---

### Historia 20 - Redesign da experiencia do Ipsum e resultado

**Historia:** Como usuário, quero me concentrar no problema durante o treino e entender claramente meu resultado ao terminar.

**Status:** implementada e verificada automaticamente em 2026-10-16; conferencia em dispositivo opcional (D-014).

**GitHub:** [#35 — História 20 — Redesign da experiência do Ipsum e resultado](https://github.com/Enzofmcs/Lorem/issues/35).

**Dependencias:** Histórias 17 e 18; regras das Histórias 05–08 preservadas.

**Escopo:** Tela de treino com problema/id, cronômetro decorrido grande, acompanhamento automático e Abrir no Codeforces como ação principal. Resultado com situação, variação de rating e métricas organizadas.

**Referencia:** [DESIGN.md](DESIGN.md).

#### Criterios de aceite

- [x] Destacar cronômetro como Tempo decorrido com números tabulares, nome/id e ação Abrir no Codeforces; não sugerir prazo fatal inexistente.
- [x] Mostrar submissões/erros definitivos, status e última consulta automática; ação manual de recuperação fica secundária.
- [x] Revelar tópicos e Encerrar sem AC mantêm confirmação, registro da dica e motivo obrigatório; encerrar tem destaque menor que abrir o problema.
- [x] Rating, categoria, tags e justificativa permanecem ocultos durante o ativo, inclusive em semântica/TalkBack; após dica, revelar apenas tags oficiais.
- [x] Resultado distingue AC e encerramento sem AC, exibe rating anterior → atual/variação explicada, tempo, dica e erros, com acesso ao problema e retorno ao Início.
- [x] AC posterior continua separado do resultado original; apresentar a tela não recalcula rating nem encerra novamente o Ipsum.
- [x] Preservar rotação, morte de processo, retomada do navegador e operação local offline; fonte 200% não corta cronômetro ou confirmações e TalkBack não anuncia cada segundo.

#### Verificacao e evidencias

Layout Nível 1, preservação das regras críticas Nível 4: testes direcionados de UI/semântica, navegação, dica, encerramento e rating. Smoke test de WA → AC real, retomada e fonte ampliada. Conferencias em dispositivo sao opcionais conforme D-014; criterios funcionais exigem evidencia automatizada proporcional ao risco.

**Exemplo de aceite:** É possível treinar e reconhecer AC automaticamente com uma tela focada. Nenhuma informação proibida vaza antes do resultado e a avaliação aparece exatamente uma vez.

#### Implementacao

- Treino com nome/id, cronometro destacado e acao principal Abrir no Codeforces.
  O tempo e decorrido pelo inicio persistido, sem prazo fatal. Tipografia tabular
  e ajuste pela largura real mantem horas longas em uma linha, inclusive em 320 dp
  com fonte 200%. O ticker visual pausa fora de RESUMED e recalcula ao retomar.
- Semantica do timer e estavel: a acao Consultar tempo decorrido le o tempo atual
  sob demanda. O valor visual por segundo fica fora da arvore de acessibilidade;
  ultimo valor consultado so muda por acao explicita, sem live region.
- Acompanhamento automatico, erros definitivos, ultimo sucesso de consulta e falha
  recuperavel ficam juntos; Atualizar agora/Tentar novamente sao secundarios.
  Revelar topicos e Encerrar sem AC tem destaque menor que abrir o problema.
- Confirmacao de dica, dialogo de encerramento e motivo selecionado sobrevivem a
  restauracao. Encerrar exige exatamente um motivo; a dica continua idempotente
  no contrato existente. Controles das confirmacoes e motivos tem minimo de 48 dp.
- Rating, categoria e fatores nao sao renderizados no ativo, inclusive nas duas
  arvores de semantica; somente tags oficiais aparecem depois da dica. A tela de
  Resultado tambem rejeita um payload ainda ativo antes de exibir dados protegidos.
- Resultado mostra AC no Ipsum ou Encerrado sem AC, rating anterior -> atual e
  variacao pelos snapshots, tempo/dica/erros/motivo, link do problema e Voltar ao
  inicio. Explicacoes existentes de dificuldade/tempo/dica foram preservadas.
  Ausencia de calculo salvo continua explicita; tipos de erro vazios mostram nenhum.
- AC posterior aparece em bloco separado com data, preservando o encerramento e
  rating originais. Navegacao aguarda o id solicitado antes de apresentar resultado
  em cache. Telas apenas leem dados; avaliacao e encerramento permanecem no fluxo
  transacional existente. Sem nova dependencia, esquema Room ou regra de dominio.
- A regressao expos uma corrida no coordenador: cancelamento apos salvar o sucesso
  podia sobrescreve-lo como Interrompido. Um resultado ja conhecido agora conclui
  seu registro de estado; HTTP permanece cancelavel. Cancelamento antes de conhecer
  o resultado continua marcado como interrupcao. Guardas de perfil preservadas.

Evidencias automatizadas:

- `IpsumScreenTest`: sigilo nas arvores mesclada e nao mesclada, dica cancelada/
  confirmada/restaurada e somente tags, motivos obrigatorios/restaurados, abertura
  da URL por identidade, estados de consulta e tempo correto apos pausa/restauracao.
  A leitura do timer fica estavel ate a proxima acao explicita de acessibilidade.
- `IpsumResultScreenTest`: AC, sem AC, upsolve com avaliacao original, legado sem
  tempo/rating salvo, rejeicao de ativo e acoes de problema/retorno.
- `IpsumLayoutTest`: imagens nativas em claro/escuro, 360 dp, 320 dp/fonte 200% e
  paisagem; nome/tags/motivo longos, timer de 123 horas, texto sem corte/reticencias,
  confirmacoes com area de 48 dp e fonte real de 200% tambem na janela do dialogo.
  A escala dos recursos Android e verificada no layout da confirmacao. Previews
  cobrem treino, dica/offline, AC e encerramento/upsolve.
- Novo caso deterministico em `IpsumSubmissionMonitorTest` injeta cancelamento apos
  a gravacao duravel de sucesso, falha na base anterior e passa com a correcao.
  Os casos de cancelamento em rede, worker compartilhado e isolamento de perfil
  continuam cobertos por monitor, `SynchronizationPersistenceTest` e WorkManager.
- `CompleteAppJourneyTest`, `AppearanceNavigationTest`, ViewModel, persistencia,
  `IpsumSubmissionMonitorTest` e `FullTrainingJourneyTest`: WA/TESTING/AC automatico,
  retomada/navegador/ciclo de vida, resultado apos trabalho em segundo plano,
  restauracao e leitura local offline, dica, primeiro AC e rating aplicado uma vez.

Comandos com Java 17, Gradle 8.11.1 e SDK 35:

```bash
./gradlew compileDebugKotlin --max-workers=2 --console=plain
./gradlew testDebugUnitTest --tests '*IpsumScreenTest' --tests '*IpsumResultScreenTest' --tests '*IpsumLayoutTest' --tests '*CompleteAppJourneyTest' --tests '*AppearanceNavigationTest' --tests '*LoremViewModelTest' --tests '*IpsumPersistenceTest' --tests '*IpsumSubmissionMonitorTest' --tests '*FullTrainingJourneyTest' --max-workers=2 --console=plain
./gradlew testDebugUnitTest --tests '*IpsumLayoutTest' --max-workers=2 --console=plain
./gradlew testDebugUnitTest --tests '*IpsumLayoutTest' --tests '*IpsumScreenTest' --max-workers=2 --console=plain
./gradlew testDebugUnitTest --tests '*IpsumSubmissionMonitorTest.cancellation after durable AC success cannot overwrite its recorded outcome' --max-workers=2 --console=plain
./gradlew testDebugUnitTest --tests '*IpsumSubmissionMonitorTest' --tests '*SynchronizationPersistenceTest' --tests '*SynchronizationWorkTest' --tests '*CompleteAppJourneyTest' --tests '*IpsumScreenTest' --tests '*IpsumResultScreenTest' --tests '*FullTrainingJourneyTest' --max-workers=2 --console=plain
./gradlew test lint assembleDebug --max-workers=2 --console=plain
```

As primeiras compilacoes exigiram corrigir imports de semantica. Nos direcionados,
foi corrigida uma assercao de viewport; a renderizacao encontrou controle de 40 dp
na confirmacao e diferenca entre premedicao e fonte real do timer em 200%.
Controles agora tem 48 dp e o timer verifica o tamanho efetivamente renderizado.
A validacao foi ampliada para aplicar escala nos recursos Android, pois dialogos
criam outra janela; a escala real e afirmada no teste. Uma regressao posterior
reproduziu a sobrescrita do sucesso por cancelamento apos AC. O caso novo foi
executado vermelho na base e verde com o registro de resultado conhecido; suites
relacionadas de cancelamento/persistencia/worker foram executadas. Direcionados e
regressao final passaram: **208 testes debug e 155 release**, zero falhas/erros,
lint sem erros (somente `MissingApplicationIcon`, preexistente) e APK debug gerado.
Diff e 81 links/ancoras locais conferidos. Sete criterios concluidos.

**TESTE MANUAL OPCIONAL - NAO EXECUTADO**, conforme D-014. TalkBack, submissao real
WA -> AC, navegador/rede reais e uso em aparelho seguem conferencia humana opcional.
Semantica/renderizacoes automatizadas nao representam ensaio humano com TalkBack.

---

### Historia 21 - Redesign do historico e estatisticas

**Historia:** Como usuário, quero revisar minhas tentativas e entender minha evolução em telas organizadas e fáceis de ler.

**Status:** implementada e verificada automaticamente em 2026-10-16; conferencia em dispositivo opcional (D-014).

**GitHub:** [#36 — História 21 — Redesign do histórico e estatísticas](https://github.com/Enzofmcs/Lorem/issues/36).

**Dependencias:** Histórias 17 e 18; contratos das Histórias 09–11 preservados. Pode ser feita após 19, independentemente da 20.

**Escopo:** Histórico em cards cronológicos com filtros claros; estatísticas com resumo, curva do rating persistido e barras por rating/tag. Toda visualização preserva amostras e definições da D-013.

**Referencia:** [DESIGN.md](DESIGN.md).

#### Criterios de aceite

- [x] Histórico apresenta nome/id, data, tempo, dica e situação textual por card, distinguindo AC no Ipsum, pendente e AC posterior.
- [x] Filtros ativos, Limpar filtros e estados vazios explicam ausência de dados ou ausência de resultados; preservar filtros/rolagem entre abas.
- [x] Upsolve mostra data do AC posterior separada da avaliação original, sem alterar rating nem taxa original de AC.
- [x] Estatísticas destacam resumo, evolução pelos snapshots persistidos e grupos por rating/tag; usar gráficos apenas com alternativa textual acessível.
- [x] Exibir denominadores/amostras, distinguir Sem amostra de zero e explicar que uma tentativa pode contribuir para várias tags; manter rankings e agregados auditáveis.
- [x] Preservar escopo por perfil, exclusão de Ipsums ativos e atualização por dados locais; não introduzir pontuação, conquistas ou métricas sem contrato.
- [x] Validar dados vazios/pequenos/grandes e nomes/tags longos, claro/escuro, offline, 320 dp, paisagem, fonte 200% e leitura por TalkBack.

#### Verificacao e evidencias

Nível 3 para UI/agregados existentes e Nível 4 se houver mudança de regra; reutilizar StatisticsFixtures e testes da D-013. Revisão visual e acessibilidade em dispositivo; regressão de marco ao concluir o bloco 18–21. Conferencias em dispositivo sao opcionais conforme D-014; criterios funcionais exigem evidencia automatizada proporcional ao risco.

**Exemplo de aceite:** O conjunto de referência da História 11 produz os mesmos totais após o redesign; cada gráfico pode ser conferido sem depender de cor ou leitura visual.


#### Implementacao

- Historico em `LazyColumn` com chaves por Ipsum, ordem cronologica do contrato
  existente e cards com nome/id, inicio, encerramento, tempo, dica e situacao textual.
  AC no Ipsum, Pendente e AC posterior usam badges sem depender apenas de cor.
- Rating e tag ficam em filtros avancados recolhidos. Filtros ativos e contagem
  de resultados continuam visiveis ao recolher; Limpar filtros restaura AC no Ipsum
  sem rating/tag. Estados vazios distinguem primeiro treino e ausencia de resultados.
  Filtros, painel e rolagem sobrevivem a abas/restauracao; troca de perfil os reinicia.
- Upsolve tem data propria e explica que preserva a avaliacao original, inclusive
  rating e taxa de AC. O link continua derivado de `contestId + index`.
- Estatisticas destacam rating atual, treinos, AC e amostras; curva mostra somente
  valores finais persistidos, em ordem de encerramento. Cada ponto tem linha textual
  com id, data, rating anterior, posterior e variacao. Series vazias, unitarias,
  constantes e densas sao suportadas sem reconstruir avaliacoes ausentes.
- Barras de AC por rating/tag acompanham percentuais e fracoes exatas. Graficos sao
  decorativos na semantica, com todos os dados disponiveis em texto. Rankings e
  indicadores reutilizam `buildIpsumStatistics` e a D-013 sem mudar o dominio.
- Sem amostra difere de 0%; o ranking sem falhas com tentativas observadas informa
  ausencia de falhas. Denominadores com/sem dica, tempos validos e erros antes do AC
  permanecem explicitos. A tela explica a contribuicao de uma tentativa a varias tags.
- Apenas dados locais do perfil sao exibidos; ativos nao entram e leituras nao
  recalculam rating. Sem dependencia nova, mudanca de banco ou regra de produto.

Evidencias automatizadas:

- `HistoryScreenTest`: campos/URL, tres situacoes, filtros compostos/recolhidos,
  restauracao, troca de perfil, vazios distintos, ativo oculto, erro recuperavel e
  lista de 500 tentativas com acesso a mais antiga.
- `StatisticsScreenTest` e suites originais `BuildIpsumStatisticsTest`/
  `BuildIpsumHistoryTest`: conjunto `StatisticsFixtures` preserva 4 tentativas,
  2 ACs, 50% de AC, media de 40 min e os snapshots; grupos, rankings e amostras,
  legado sem avaliacao, zero exposicao/falhas, serie unitaria/constante de 200 pontos.
- `AppearanceNavigationTest`: filtros reais de rating/tag/situacao e posicao do
  card preservados entre abas e restauracao, tema/configuracoes e isolamento.
- `HistoryStatisticsLayoutTest`: 30 imagens nativas nos dois temas, 360 dp,
  320 dp/fonte 200% e paisagem; nomes/tags longos, upsolve, offline, curva e amostra
  zero. Verifica texto sem corte/reticencias, controles de 48 dp e dados textuais.
  `HistoryStatisticsPreviews` adiciona estados ilustrativos isolados do app.

Verificacao Nivel 3 (UI/agregados existentes), seguida de Nivel 5 pelo marco 18-21.
Comandos com Java 17, Gradle 8.11.1 e SDK 35:

```bash
./gradlew compileDebugKotlin --max-workers=2 --console=plain
./gradlew testDebugUnitTest --tests '*HistoryScreenTest' --tests '*StatisticsScreenTest' --tests '*HistoryStatisticsLayoutTest' --tests '*AppearanceNavigationTest' --tests '*BuildIpsumStatisticsTest' --tests '*BuildIpsumHistoryTest' --max-workers=2 --console=plain
./gradlew testDebugUnitTest --tests '*AppearanceNavigationTest' --max-workers=2 --console=plain
./gradlew test lint assembleDebug --max-workers=2 --console=plain
```

O ambiente exigiu SDK/Java 17 e caches gravaveis em `work/`, proxy e trust store
configurados apenas na sessao. O primeiro teste nao conseguiu criar o lock do
Robolectric na home somente leitura; o cache foi redirecionado. Os testes de UI
foram adaptados para localizar controles via rolagem da lista lazy. Testes de telas,
layout, dominio e navegacao passaram apos os ajustes. Regressao final: **217 debug
+ 155 release**, zero falhas/erros; lint sem erros (somente o aviso preexistente
`MissingApplicationIcon`) e APK debug gerado. Diff e links locais conferidos.
Sete criterios concluidos; nenhuma historia planejada pendente.

**TESTE MANUAL OPCIONAL - NAO EXECUTADO**, conforme D-014. Uso humano de TalkBack,
Codeforces/rede reais e aparelho continuam conferencias opcionais. Semantica e
renderizacoes automatizadas nao equivalem a ensaio humano com TalkBack.

---

## Extensao quantitativa em Python

Planejamento de 18/10/2026 conferido contra `main` em
`931c410954cc851dd46862c3f1ed35c2adb3e580` ([PR #44](https://github.com/Enzofmcs/Lorem/pull/44)).
As issues 15-21 (#30-36) estao fechadas como concluidas; nenhuma issue ou PR estava
aberto antes deste bloco. As Historias 00-21 e seus checklists/evidencias permanecem
inalterados. A referencia local `origin/main` estava antiga; o SHA acima foi
confirmado diretamente no GitHub e coincide com o HEAD usado neste planejamento.

**Planejamento inicial:** entregou somente documentos e issues, com aceites pendentes.
**Implementacao de 22-25 em 18/10/2026:** subprojeto [study/](../study/README.md),
Instalacao limpa/wheel e jornada sintetica verificadas. A continuacao da PR #57
concluiu o piloto da Historia 23: 1824/2062 completos, 39 testes e reproducao offline.
As 47.100 tentativas foram excluidas pela regra de indices simples; nenhum intervalo
valido, treino ou avaliacao. [Evidencia real](../study/evidence/2026-10-18-pilot-real.json).
22/24/25 possuem aceite de software; isso nao comprova precisao preditiva.
**Continuacao da Historia 26:** triagem de 44 candidatos, 20 aquisicoes completas,
19 contests elegiveis (1589 sem rating verificavel dos problemas), 274.073 tentativas
validas e particoes 11/4/4. Baselines reais e grade AFT ajustados; Weibull com L2 0,1
selecionado apenas na validacao. 51 testes. [Evidencia](../study/evidence/2026-10-18-sample-aft.json)
e [metodo](../study/MODEL.md). Teste reservado; nenhuma precisao superior comprovada.
27-32 continuam pendentes. Nenhuma mudanca Android. D-012 continua vigente; D-020 a D-025 registram as decisoes
solicitadas. Metodo e limites em [QUANTITATIVE.md](QUANTITATIVE.md).

Primeira versao: **22 -> 23 -> 24 -> 25 -> 26 -> 27 -> 28 = 24 h estimadas**.
A estimativa pressupoe amostra limitada de contests completos e acesso a API/cache;
nao promete prazo de coleta, superioridade do modelo ou conclusao cientifica positiva.
Se faltar volume, entregar piloto e declarar insuficiencia, sem fabricar resultados.

Depois: **29**, e **30 -> 31 -> 32**. A 29 pode antecipar apos 23 e independe do
sucesso preditivo; 30 pode iniciar apos 28 independentemente de 29. A 32 exige
29 e evidencia/decisao favoravel da 31. Dados reais de Ipsums podem exigir tempo
de uso adicional, fora das horas de desenvolvimento. Backend/login/nuvem nao sao
pre-requisitos. Comparacao entre epocas, analises por ano e correcao temporal fora
do escopo.

| Historia | Issue | Esforco | Dependencias | Entrega demonstravel |
|---|---|---:|---|---|
| 22 - Base Python e protocolo reproduzivel do estudo | [#45](https://github.com/Enzofmcs/Lorem/issues/45) | 2 h | 00-21 concluidas | Em ambiente virtual limpo, executar a jornada sintetica e inspecionar o manifesto e o dicionario sem instalar Android. |
| 23 - Coleta auditavel de contests e ratings historicos | [#46](https://github.com/Enzofmcs/Lorem/issues/46) | 4 h | 22 | Retomar uma coleta interrompida e reconstruir as mesmas tabelas pelo cache, mostrando a fonte da data e do oldRating de uma participacao. |
| 24 - Tempos sequenciais, censura e cobertura das tentativas | [#47](https://github.com/Enzofmcs/Lorem/issues/47) | 4 h | 23 | Inspecionar uma linha desde as submissoes brutas ate seu tempo/evento ou motivo de exclusao, incluindo um caso sem AC. |
| 25 - Referencias simples e particoes por contest | [#48](https://github.com/Enzofmcs/Lorem/issues/48) | 3 h | 24 | Consultar as probabilidades e quantis do baseline para um par de ratings e conferir o manifesto sem contests compartilhados. |
| 26 - Modelo interpretavel de tempo ate AC | [#49](https://github.com/Enzofmcs/Lorem/issues/49) | 4 h | 25 | Comparar previsoes e fatores de tempo para pares de ratings e orcamentos explicitos, mostrando os limites de suporte. |
| 27 - Avaliacao de erros, probabilidades e incerteza | [#50](https://github.com/Enzofmcs/Lorem/issues/50) | 4 h | 26 | Abrir tabela comparativa e calibracao em 30/60/120 minutos com erros, ICs, tamanhos de amostra e horizontes sem suporte claramente indicados. |
| 28 - Relatorio do estudo e reproducao completa para o TCC | [#51](https://github.com/Enzofmcs/Lorem/issues/51) | 3 h | 27 | Outra pessoa executa o roteiro local a partir do snapshot e confere hashes e tabelas do relatorio apresentado no TCC. |
| 29 - Corte verificavel de problemas nas novas recomendacoes | [#52](https://github.com/Enzofmcs/Lorem/issues/52) | 6 h | 23 | Catalogo misto mostra que antigo/sem data nunca e recomendado; um problema antigo ja tentado continua no historico e nos impedimentos de repeticao, com avaliacao intacta. |
| 30 - Base local de validacao com tentativas Ipsum | [#53](https://github.com/Enzofmcs/Lorem/issues/53) | 4 h | 28 | Auditar um Ipsum pseudonimizado desde o inicio persistido ate seu evento/censura, demonstrando rating anterior e ausencia de alteracao da avaliacao. |
| 31 - Validar relacao entre Rating Codeforces e Rating Lorem | [#54](https://github.com/Enzofmcs/Lorem/issues/54) | 4 h | 30 | Apresentar relatorio de transporte e decisao rastreavel com amostra, limiares predefinidos, erros e ICs, inclusive quando a conclusao for nao integrar. |
| 32 - Integracao local versionada das previsoes validadas | [#55](https://github.com/Enzofmcs/Lorem/issues/55) | 4 h | 29, 31 | Consultar previsao local versionada e repetir offline; indisponibilidade do modelo preserva o treino, e uma avaliacao antiga permanece identica. |

### Marcos e decisoes

- **G - Estudo reproduzivel (22-28):** dados, modelos, erros, probabilidades com orcamento, incerteza e relatorio com limitacoes.
- **H - Recorte nas recomendacoes (29):** corte verificavel com historico e operacao local preservados.
- **I - Validacao no treino (30-31):** relacao entre ratings e transporte com evidencia ou decisao fundamentada de nao integrar.
- **J - Integracao condicional (32):** previsoes locais versionadas somente depois do gate cientifico.

Amostra/parametros de reproducao sao congelados no inicio do estudo. Limiares e
tamanho/precisao requeridos para Ipsums devem ser registrados antes de abrir o
teste da 31. Pontuacao D-012 nao sera substituida por estas historias sem decisao
explicita adicional. Veja DA-07 em [DECISIONS.md](DECISIONS.md).

### Historia 22 - Base Python e protocolo reproduzivel do estudo

**Historia:** Como pesquisador, quero executar um protocolo versionado em Python para reproduzir o estudo sem depender do app Android.

**Status:** implementada e verificada automaticamente (software/fixtures), em 2026-10-18; sem conclusao empirica.

**GitHub:** [#45 - Historia 22](https://github.com/Enzofmcs/Lorem/issues/45).

**Estimativa:** 2 h. **Fase:** MVP do estudo.

**Dependencias:** Historias 00-21 concluidas; sem nova dependencia funcional.

**Escopo:** Criar study/ isolado, ambiente fixado, configuracao, contratos de dados e uma jornada minima com fixtures claramente sinteticas. Implementacao futura; o planejamento atual nao cria codigo de coleta ou modelos.

**Referencia:** [QUANTITATIVE.md](QUANTITATIVE.md) e [TESTING.md](TESTING.md).

#### Criterios de aceite

- [x] Documentar Python 3.11+, ambiente virtual, dependencias fixadas e justificadas, semente, esquema e versoes de protocolo/modelo; instalacao independe de Gradle.
- [x] Disponibilizar comandos de coleta, preparo, treino, avaliacao e relatorio com configuracao explicita e modo offline por snapshot; mensagens distinguem etapas nao executadas de resultados reais.
- [x] Versionar dicionario e configuracao do corte inclusivo em 2020-07-01 UTC, populacao oficial individual, oldRating, delta, hipotese sequencial e censura conforme QUANTITATIVE.md.
- [x] Definir manifesto de selecao por contest/grupo, sementes, hashes, completude, cache e diretorios ignorados para brutos/artefatos grandes; nao versionar handles reais em fixtures.
- [x] Uma fixture sintetica percorre a interface minima do pipeline e produz manifesto deterministico; componentes ainda nao implementados sao identificados, sem simular resultado empirico.

#### Verificacao e evidencias

Nivel 2: smoke automatizado do comando minimo, configuracao invalida, determinismo e separacao entre fixtures e dados reais. Nao exigir treino/coleta que pertencem a 23-27.

**Entrega demonstravel:** Em ambiente virtual limpo, executar a jornada sintetica e inspecionar o manifesto e o dicionario sem instalar Android.

Ambiente virtual Python 3.12.14, setuptools 75.8.0 fixado, instalacao editavel, wheel com configuracao e CLI fora do diretorio do projeto passaram. Fixture deterministica percorre comandos e marca etapas 26-28 como nao implementadas. Sem dependencia externa de runtime/teste.

Comando: `cd study && .venv/bin/python -m unittest discover -s tests -v`:
**37 testes sem falhas**. [Guia](../study/README.md),
[contrato](../study/DATA_DICTIONARY.md) e
[evidencia registrada](../study/evidence/2026-10-18-validation.json).
**TESTE MANUAL OPCIONAL - NAO EXECUTADO** (D-014).

---

### Historia 23 - Coleta auditavel de contests e ratings historicos

**Historia:** Como pesquisador, quero snapshots completos da API para associar cada participante ao rating anterior ao contest e preservar a proveniencia.

**Status:** implementada e verificada automaticamente com piloto real em 2026-10-18; 2 contests completos, cobertura valida zero pelo protocolo atual. Sem avaliacao preditiva.

**GitHub:** [#46 - Historia 23](https://github.com/Enzofmcs/Lorem/issues/46).

**Estimativa:** 4 h. **Fase:** MVP do estudo.

**Dependencias:** Historia 22

**Escopo:** Coletor centralizado para metadados, problemas, participacoes, ratingChanges e submissoes. Piloto de dois contests e manifesto limitado antes do ajuste; alvo 20, minimo de avaliacao 10 completos.

**Regra confirmada pelo usuario (D-026):** contests especiais e contests com qualquer
problema em duas versoes (facil/dificil, como B1/B2) ficam integralmente fora da
analise, inclusive seus problemas simples. Preservar brutos e exclusoes; o proximo
conjunto de dados deve manter esse filtro. Regressao: 41 testes Python sem falhas.

**Referencia:** [QUANTITATIVE.md](QUANTITATIVE.md) e [TESTING.md](TESTING.md).

#### Criterios de aceite

- [x] Verificar inicio real do contest de origem >= 2020-07-01T00:00:00Z; nao inferir data por id; excluir origem/data nao verificavel e problemas sem rating com motivos.
- [x] Selecionar contests CF FINISHED e unir participacoes CONTESTANT individuais com contest.ratingChanges por contest/handle normalizado; usar oldRating, separar estreantes e quarentenar ausencias/conflitos sem imputacao futura.
- [x] Coletar todas as paginas necessarias de standings/status, com gate global >=2 s, cache, retry/backoff/Retry-After e retomada; falha parcial nunca vira contest completo ou historico vazio.
- [x] Preservar JSON bruto imutavel, origem, parametros, coleta UTC, hashes, vereditos finais e versao de snapshot; registrar rating do problema como disponivel na coleta, sem alegar snapshot historico.
- [x] Executar piloto de 2 contests; congelar selecao deterministica sem filtrar por sucesso, registrar volume/custo e grupos de problemas compartilhados; minimo insuficiente resulta em piloto declarado, sem metricas conclusivas.
- [x] Emitir funil de elegibilidade/completude e tabelas com ambos os ratings e delta; brutos e historicos antigos permanecem preservados.

#### Verificacao e evidencias

Nivel 3: API roteirizada para OK/FAILED, 429, pagina faltante, duplicata e retomada; limites de data, oldRating zero/ausente, equipe/virtual e origem ambigua. Confirmar piloto real e hashes sem exigir rede nos testes.

**Entrega demonstravel:** Retomar uma coleta interrompida e reconstruir as mesmas tabelas pelo cache, mostrando a fonte da data e do oldRating de uma participacao.

Piloto real concluido apos resolver o acesso de rede. Selecao original preservada:
1824 (Div. 1) e 2062 (Div. 1 + Div. 2). A API rejeitou paginacao em standings
(HTTP 400); coletor adaptado para resposta oficial completa com apenas contestId.
Status continua paginado: 4 + 18 paginas, 210.860 submissoes; 17.019 participacoes
oficiais com join exato de ratingChanges, nenhum veredito oficial pendente.

Custo auditado: 30 respostas HTTP (28 OK, 2 recusas preservadas), incluindo duas
consultas diagnosticas; 143.381.349 bytes HTTP e 29 objetos brutos unicos.
Coletores medidos: 10,318 + 46,335 s; esse tempo exclui diagnostico e edicao do codigo.
Snapshot `8e3f358a6525114b7ccd404f9b87c2c6e95767d3dcac7d8034542924ffd410a0`.
Retomada aproveitou cache; repeticao offline fez zero requisicoes e reconstruiu
features/labels/audit/manifest byte a byte. Hashes dos brutos e funis conferidos.

Qualidade: 47.100 tentativas observadas = 0 eventos + 0 censuras + 47.100 exclusoes
(44.952 por ordem nao suportada, 2.148 por historico insuficiente). Indices B1/B2
em 1824 e E1/E2 em 2062 tornam ambos inelegiveis pelo protocolo vigente. Nao trocar
os contests nem ampliar o protocolo apos observar a cobertura. Links de origem da
API registrados, sem vinculo cruzado detectado nestes dois; aliases entre divisoes
continuam sendo limitacao. `split` recusou os zero contests elegiveis; nenhum modelo
ou avaliacao produzido. Brutos/tabelas preservados em `study/work/pilot-real/`,
ignorados pelo Git; [evidencia sem handles](../study/evidence/2026-10-18-pilot-real.json)
registra custo, hashes por resposta, fatias, exclusoes e rastreio de oldRating/data.

Comando: `cd study && python -m unittest discover -s tests -v`:
**39 testes sem falhas**, incluindo contrato de standings completo e resposta malformada. [Guia](../study/README.md),
[contrato](../study/DATA_DICTIONARY.md) e
[evidencia registrada](../study/evidence/2026-10-18-pilot-real.json).
**TESTE MANUAL OPCIONAL - NAO EXECUTADO** (D-014).

---

### Historia 24 - Tempos sequenciais, censura e cobertura das tentativas

**Historia:** Como pesquisador, quero rotulos auditaveis de tempo e tentativas sem AC para testar a hipotese sequencial sem confundir disponibilidade com dedicacao.

**Status:** implementada e verificada automaticamente (software/fixtures), em 2026-10-18; sem conclusao empirica.

**GitHub:** [#47 - Historia 24](https://github.com/Enzofmcs/Lorem/issues/47).

**Estimativa:** 4 h. **Fase:** MVP do estudo.

**Dependencias:** Historia 23

**Escopo:** Derivar ancora, duracao, evento e qualidade por participante/contest/problema, preservando os brutos e todas as recusas. Usar o protocolo sequencial de QUANTITATIVE.md.

**Referencia:** [QUANTITATIVE.md](QUANTITATIVE.md) e [TESTING.md](TESTING.md).

#### Criterios de aceite

- [x] Usar creationTimeSeconds do primeiro envio com veredito final OK em [inicio, fimContest), excluindo upsolves; A ancora no inicio e B/C no primeiro AC do predecessor imediato.
- [x] Reproduzir ACs aos 10/28/65 minutos como duracoes 10/18/37 e identificar prefixo valido; definir ordem por problemas do contest, recusando indices ambiguos sem ordenacao ingenua.
- [x] Marcar saltos, inversoes, intercalacoes, retornos a problema aceito, empates/tempos nao positivos e inconsistencias; invalidar o intervalo contaminado e sucessores, preservando prefixos anteriores validos.
- [x] Preservar tentativas observadas sem AC como Y=C e evento=0 quando sequenciais e completas, com C=fimContest-ancora; disponibilidade nao equivale a dedicacao; sem submissao nao e tentativa negativa.
- [x] Representar coleta incompleta, veredito pendente e ancora invalida separadamente de censura; nao usar zero/infinito nem classificar abandono observavel como trabalho ate o fim.
- [x] Exportar funil e motivos por contest/faixa de rating com eventos, censuras e cobertura, alem da sensibilidade pela ultima submissao; features ficam separadas dos rotulos e da auditoria.

#### Verificacao e evidencias

Nivel 2 para transformacoes e Nivel 3 para esquema: fixtures de 10/28/65, WA->AC, multiplos ACs, limites do contest, salto, inversao, intercalacao, censura e incompletude; somas do funil reconciliadas.

**Entrega demonstravel:** Inspecionar uma linha desde as submissoes brutas ate seu tempo/evento ou motivo de exclusao, incluindo um caso sem AC.

Testes verificam 10/28/65 -> 10/18/37, primeiro AC, limites/upsolve, salto/inversao/intercalacao, prefixos preservados, censura versus incompletude, joins oldRating e origem/data/rating ausentes. Funil sintetico: 90 observadas = 40 eventos + 10 censuras + 40 excluidas; reconciliado por contest e faixas dos dois ratings/delta. Nao e resultado empirico.

Comando: `cd study && .venv/bin/python -m unittest discover -s tests -v`:
**37 testes sem falhas**. [Guia](../study/README.md),
[contrato](../study/DATA_DICTIONARY.md) e
[evidencia registrada](../study/evidence/2026-10-18-validation.json).
**TESTE MANUAL OPCIONAL - NAO EXECUTADO** (D-014).

---

### Historia 25 - Referencias simples e particoes por contest

**Historia:** Como pesquisador, quero referencias simples e uma separacao sem vazamento para medir se um modelo mais elaborado acrescenta utilidade.

**Status:** implementada e verificada automaticamente (software/fixtures), em 2026-10-18; sem conclusao empirica.

**GitHub:** [#48 - Historia 25](https://github.com/Enzofmcs/Lorem/issues/48).

**Estimativa:** 3 h. **Fase:** MVP do estudo.

**Dependencias:** Historia 24

**Escopo:** Congelar treino/validacao/teste por contests/grupos completos e ajustar Kaplan-Meier global/por delta. D-012 entra somente como referencia de tempo.

**Referencia:** [QUANTITATIVE.md](QUANTITATIVE.md) e [TESTING.md](TESTING.md).

#### Criterios de aceite

- [x] Persistir manifesto 60/20/20 por contest/grupo com semente, pelo menos 6/2/2 contests completos no minimo de 10; impedir contest ou grupo compartilhado em mais de uma particao.
- [x] Ajustar Kaplan-Meier com eventos e censuras do treino, global e por delta de 400 pontos com minimo de 50 tentativas/20 eventos; usar fallback global quando faltar suporte.
- [x] Produzir P(AC ate 30/60/120 min), mediana e quantis quando identificaveis; ausencia de mediana/seguimento e indisponibilidade explicita, nunca infinito.
- [x] Reproduzir a formula de tempo D-012 como comparador operacional, sem modifica-la no Android nem atribuir probabilidade inventada.
- [x] Garantir que transformacoes, faixas, fallback e funcao de censura sejam ajustados somente no treino; gravar artefato versionado e previsoes identificadas por particao.

#### Verificacao e evidencias

Nivel 2: vazamento por contest/grupo, CDF monotona em [0,1], censura total, mediana nao atingida, fallback de faixa e equivalencia numerica da D-012.

**Entrega demonstravel:** Consultar as probabilidades e quantis do baseline para um par de ratings e conferir o manifesto sem contests compartilhados.

Fixture sintetica de 10 contests gera 6/2/2; grupos transitivos e minimo inviavel testados. Treino: 30 tentativas, 24 eventos e 6 censuras. KM global/bins e curva de censura ajustados apenas no treino; alterar dados reservados nao altera o modelo. Limites 49/50 tentativas e 19/20 eventos, CDF/quantis/seguimento e D-012 conferidos. Nao houve ajuste ou avaliacao com dados reais.

Comando: `cd study && .venv/bin/python -m unittest discover -s tests -v`:
**37 testes sem falhas**. [Guia](../study/README.md),
[contrato](../study/DATA_DICTIONARY.md) e
[evidencia registrada](../study/evidence/2026-10-18-validation.json).
**TESTE MANUAL OPCIONAL - NAO EXECUTADO** (D-014).

---

### Historia 26 - Modelo interpretavel de tempo ate AC

**Historia:** Como pesquisador, quero estimar uma distribuicao interpretavel de tempo a partir dos ratings, incluindo observacoes censuradas.

**Status:** implementada e verificada com dados sinteticos e amostra real em 2026-10-18; avaliacao reservada e ganho preditivo continuam pendentes na 27.

**GitHub:** [#49 - Historia 26](https://github.com/Enzofmcs/Lorem/issues/49).

**Estimativa:** 4 h. **Fase:** MVP do estudo.

**Dependencias:** Historia 25

**Escopo:** AFT log-normal e alternativa Weibull, com grade pequena de regularizacao e selecao agrupada no treino/validacao. Nao pressupor superioridade sobre as referencias.

**Referencia:** [QUANTITATIVE.md](QUANTITATIVE.md) e [TESTING.md](TESTING.md).

#### Criterios de aceite

- [x] Ajustar AFT com duracao observada e indicador de evento, sem eliminar os sem AC validos; justificar biblioteca, distribuicoes e hipoteses de censura.
- [x] Usar oldRating/100 e delta/100, preservando ratingProblema na base; evitar colinearidade dos tres termos e atributos de desfecho como rank, erros, AC ou tempo realizado.
- [x] Comparar log-normal/Weibull e regularizacao {0,0.01,0.1} so no treino/validacao, com GroupKFold quando houver suporte; nao inspecionar teste para ajustar.
- [x] Exportar F(b), mediana e quantis 10/90 em minutos, coeficientes/fatores de tempo, dominio de treino e versoes; distinguir intervalo preditivo de confianca.
- [x] Verificar convergencia, monotonicidade e suporte; sinalizar extrapolacao, falha ou indisponibilidade em vez de ocultar estimativas invalidas; nao afirmar ganho de precisao antes de 27.

#### Verificacao e evidencias

Nivel 2: dados sinteticos com censura para recuperacao aproximada de sinal, invariantes da CDF/quantis, ausencia de features vazadas, reproducao de artefatos e falha de convergencia.

**Entrega demonstravel:** Comparar previsoes e fatores de tempo para pares de ratings e orcamentos explicitos, mostrando os limites de suporte.

Amostra regular-simple-v1 congelada apos verificar somente formato/problemas de
44 candidatos: 20 contests coletados integralmente, 19 elegiveis. Nenhum contest
substituido por sucesso; 1589 excluido por ratings ausentes. Dos 392.445 registros
observados, 234.945 eventos, 39.128 censuras e 118.372 exclusoes. Particoes por
contest/grupo: 11 treino, 4 validacao, 4 teste; 193.527/45.179/35.367 tentativas validas.

CLI `train` e `predict --model aft` implementadas. NumPy 2.3.5/SciPy 1.17.0 opcionais
e fixados; [likelihoods, gradientes e regras de selecao](../study/MODEL.md).
Log-normal/Weibull x L2 {0,0.01,0.1}, tres folds agrupados no treino e selecao na
validacao. Os seis candidatos convergiram com gradiente conferido; Weibull 0,1 foi
selecionado. Ajuste final: 193.527 observacoes = 167.618 eventos + 25.909 censuras.
Modelos/previsoes versionados localmente; teste nao usado no tuning.

51 testes sem falhas: gradientes analiticos versus numericos, recuperacao de sinal
com censura nas duas distribuicoes, falsa convergencia do otimizador, censura
conhecida/desconhecida no scoring interno, grupos, mutacao dos rotulos do teste
sem alterar modelo, determinismo, suporte/quantis/extrapolacao e CLI instalada.
Core sem site-packages e dependencias opcionais ausentes tambem verificados.
[Evidencia e hashes](../study/evidence/2026-10-18-sample-aft.json).

O scoring interno escolhe o modelo; nao e avaliacao reservada, IC ou comprovacao
de superioridade. Esses resultados pertencem a 27. Nenhuma mudanca Android.
**TESTE MANUAL OPCIONAL - NAO EXECUTADO** (D-014).

---

### Historia 27 - Avaliacao de erros, probabilidades e incerteza

**Historia:** Como pesquisador, quero medir os erros fora da amostra e a calibracao das probabilidades para conhecer a utilidade e a incerteza reais do estudo.

**Status:** implementada e verificada em 2026-10-18; aguardando revisao/integracao da PR.

**GitHub:** [#50 - Historia 27](https://github.com/Enzofmcs/Lorem/issues/50).

**Estimativa:** 4 h. **Fase:** MVP do estudo.

**Dependencias:** Historia 26

**Escopo:** Congelar escolha na validacao e avaliar uma vez nos contests reservados, comparando todas as referencias. Produzir tabelas e figuras com amostras e cobertura.

**Referencia:** [QUANTITATIVE.md](QUANTITATIVE.md) e [TESTING.md](TESTING.md).

#### Criterios de aceite

- [x] Selecionar na validacao por Brier integrado suportado e calibracao, usando MAE/interpretabilidade como secundarios; registrar escolha antes de abrir o teste, sem retunar nele.
- [x] Medir MAE, mediana do erro absoluto, RMSE e vies de tempo nos eventos do teste com denominador e ressalva de selecao; comparar D-012 e referencias.
- [x] Avaliar P(AC ate 30/60/120) com Brier/log-loss nos casos conhecidos, calibracao por bins com amostras e Brier IPCW/integrado com censuras; censurado antes do horizonte nao vira negativo.
- [x] Estimar censura somente no treino, informar suporte/pesos extremos e horizontes indisponiveis; medir cobertura/largura do intervalo preditivo de 80% e declarar os limites da avaliacao nos resolvidos.
- [x] Produzir ICs de 95% por bootstrap pareado de contests/grupos, 200 replicacoes com semente, incluindo diferencas contra o baseline; nao tratar linhas como independentes.
- [x] Reportar erros por faixas predefinidas dos dois ratings/delta e qualidade, funil de cobertura e sensibilidade de censura; sem analise por ano, correcao temporal ou alegacao de superioridade sem evidencia.

#### Verificacao planejada

Nivel 2: metricas conferidas em exemplos pequenos, censura antes/depois de b, IPCW sem suporte, bootstrap por grupo, split isolado e reproduzibilidade de tabelas/figuras.

**Entrega demonstravel:** Abrir tabela comparativa e calibracao em 30/60/120 minutos com erros, ICs, tamanhos de amostra e horizontes sem suporte claramente indicados.

Conferencias humanas opcionais (D-014); criterios automatizados e evidencia
cientifica exigidos acima continuam obrigatorios. Resultados e limitacoes no [relatorio real](../study/results/2026-10-18/report/REPORT.md).


Evidencia: 59 testes Python; plano vinculado ao AFT Weibull 0,1 antes dos rotulos,
200 replicacoes pareadas por grupo, quatro contests de teste/35.367 tentativas.
KM por delta apresentou MAE 15,135 min e Brier integrado 0,122746; AFT 15,892 e
0,126693. Comparacao de tempo pareada usa suporte comum. Relatorio contem n/ICs,
calibracao conhecida/IPCW, qualidade, fatias e sensibilidade. Nenhum retuning,
transporte para Ipsums ou mudanca Android. Reproducao limpa e hashes registrados
na [evidencia](../study/evidence/2026-10-18-evaluation-report.json).

---

### Historia 28 - Relatorio do estudo e reproducao completa para o TCC

**Historia:** Como autor do TCC, quero apresentar metodos e resultados reproduziveis com limitacoes para sustentar conclusoes proporcionais a evidencia.

**Status:** implementada e verificada em 2026-10-18; aguardando revisao/integracao da PR.

**GitHub:** [#51 - Historia 28](https://github.com/Enzofmcs/Lorem/issues/51).

**Estimativa:** 3 h. **Fase:** MVP do estudo.

**Dependencias:** Historia 27

**Escopo:** Fechar a primeira entrega Python com coleta/preparo/modelos/avaliacao, artefatos e relatorio. Nao integrar modelo nem mudar rating no app.

**Referencia:** [QUANTITATIVE.md](QUANTITATIVE.md) e [TESTING.md](TESTING.md).

#### Criterios de aceite

- [x] Reproduzir do snapshot congelado ao relatorio em ambiente limpo com comandos documentados; registrar versoes, sementes, hashes, manifesto, tempo/custo e dependencias.
- [x] Publicar codigo, dicionario, tabelas, figuras e relatorio com populacao, cobertura, erros de tempo, qualidade das probabilidades, ICs e incerteza preditiva separados.
- [x] Documentar hipotese sequencial, selecao por tentativa/prefixo, rating do problema na coleta, dados ausentes, censura/abandono, repeticao de participantes e limites de transporte para Ipsums.
- [x] Demonstrar dois ratings + orcamento -> estimativas, suporte e incerteza; resultados reais separados de fixtures, incluindo baseline vencedor ou evidencia insuficiente quando ocorrer.
- [x] Registrar conclusoes ainda abertas, decisao sobre novos experimentos e protocolo de validacao posterior com Ipsums/ratings; nao alegar melhoria pedagogica nem precisao comprovada sem experimento.

#### Verificacao planejada

Marco Python: testes de study/ e reproducao completa offline pelo snapshot; conferir artefatos/links e ausencia de valores fabricados. Build Android nao se aplica. Conferencia humana opcional (D-014).

**Entrega demonstravel:** Outra pessoa executa o roteiro local a partir do snapshot e confere hashes e tabelas do relatorio apresentado no TCC.

Conferencias humanas opcionais (D-014); criterios automatizados e evidencia
cientifica exigidos acima continuam obrigatorios. Resultados e limitacoes no [relatorio real](../study/results/2026-10-18/report/REPORT.md).


Evidencia: 59 testes Python; plano vinculado ao AFT Weibull 0,1 antes dos rotulos,
200 replicacoes pareadas por grupo, quatro contests de teste/35.367 tentativas.
KM por delta apresentou MAE 15,135 min e Brier integrado 0,122746; AFT 15,892 e
0,126693. Comparacao de tempo pareada usa suporte comum. Relatorio contem n/ICs,
calibracao conhecida/IPCW, qualidade, fatias e sensibilidade. Nenhum retuning,
transporte para Ipsums ou mudanca Android. Reproducao limpa e hashes registrados
na [evidencia](../study/evidence/2026-10-18-evaluation-report.json).

---

### Historia 29 - Corte verificavel de problemas nas novas recomendacoes

**Historia:** Como usuario do Lorem, quero receber somente novos problemas do recorte definido, mantendo intactos meu historico e minhas avaliacoes.

**Status:** implementada e verificada em 2026-10-18; aguardando revisao/integracao.

**GitHub:** [#52 - Historia 29](https://github.com/Enzofmcs/Lorem/issues/52).

**Estimativa:** 6 h. **Fase:** Posterior ao MVP; independente do ganho preditivo.

**Dependencias:** Historia 23

**Escopo:** Adicionar metadados de contest/data e regra de elegibilidade versionada ao Android. Ordem sugerida depois de 28; pode antecipar apos 23, sem esperar validacao do modelo.

**Referencia:** [QUANTITATIVE.md](QUANTITATIVE.md) e [TESTING.md](TESTING.md).

#### Criterios de aceite

- [x] Consultar metadados verificaveis do contest de origem via cliente/gate centralizado; exigir inicio >=2020-07-01T00:00:00Z, sem inferir pelo id e com origem/data ambigua inelegivel.
- [x] Persistir fonte/data verificada e versao da regra por migracao aditiva; catalogo legado sem data nao habilita novas recomendacoes ate verificacao, e cache verificado funciona offline.
- [x] Aplicar o corte em toda selecao/fallback, inclusive fora de rede, sem relaxa-lo se nao houver candidatos; oferecer ausencia de candidato recuperavel.
- [x] Preservar todos os historicos/submissoes antigos e sua exclusao de repeticoes, Ipsums anteriores/ativo, snapshots, pendencias, rating e avaliacoes; nao recalcular dados antigos.
- [x] Integrar atualizacao/retomada de metadados aos fluxos locais existentes e verificar idempotencia, falha parcial e concorrencia sem backend, login ou sincronizacao em nuvem.

#### Verificacao planejada

Niveis 3/4 e marco Android: testes de fronteira UTC, data ausente/origem ambigua, migracao/cache legado/offline, fallback, ativo e exclusoes historicas; regressao dos invariantes e test/lint/assembleDebug no marco.

**Entrega demonstravel:** Catalogo misto mostra que antigo/sem data nunca e recomendado; um problema antigo ja tentado continua no historico e nos impedimentos de repeticao, com avaliacao intacta.

Conferencias humanas opcionais (D-014); criterios automatizados e evidencia
cientifica exigidos acima continuam obrigatorios. Verificada por 226 testes debug/164 release, lint sem erros e assembleDebug.


Evidencia: duas consultas pelo gate (problemset + contest.list), datas inteiras UTC,
duplicatas conflitantes/missing inelegiveis e cache anterior preservado em falha.
Room 10->11 adiciona somente metadados nullable de origem no catalogo/Ipsums.
Recomendacao/fallback e tela inicial exigem prova; todo historico/ativo/rating
permanece preservado. Cadeia de migracao 9->11 validada por Room.

---

### Historia 30 - Base local de validacao com tentativas Ipsum

**Historia:** Como pesquisador, quero validar as previsoes em Ipsums com inicio registrado para medir a diferenca entre tempo inferido em contests e tempo de treino.

**Status:** software e protocolo implementados/verificados em 2026-10-18; coorte real ausente, sem conclusao empirica.

**GitHub:** [#53 - Historia 30](https://github.com/Enzofmcs/Lorem/issues/53).

**Estimativa:** 4 h. **Fase:** Validacao posterior; coleta pode exigir tempo de uso.

**Dependencias:** Historia 28

**Escopo:** Extracao local explicita e minimizada de tentativas; planejar coleta prospectiva apenas dos campos ausentes, sem alterar pontuacao ou inventar dados historicos.

**Referencia:** [QUANTITATIVE.md](QUANTITATIVE.md) e [TESTING.md](TESTING.md).

#### Criterios de aceite

- [x] Definir/exportar esquema pseudonimizado local com inicio, primeiro AC valido, encerramento, dica, interrupcoes conhecidas, versoes, rating do problema, CF anterior ao inicio e Rating Lorem inicial.
- [x] Preservar legados e representar ausencias; nao preencher rating CF passado com o atual nem com newRating de contest posterior; se faltar campo, documentar coleta prospectiva aditiva minima.
- [x] Distinguir AC no Ipsum de upsolve e nao recalcular avaliacao; tratar sem AC/desistencia e pausas explicitamente, sem chamar tempo de parede de dedicacao efetiva.
- [x] Aplicar recorte de problema/data verificavel na base do estudo sem excluir o historico do app; separar com/sem dica e informar funil/cobertura e suporte.
- [x] Fixar protocolo de amostra e particoes por participante para a validacao, mantendo todos os dados locais e sem exigir backend/login/nuvem; amostra ausente ou pequena e registrada como insuficiente.

#### Verificacao planejada

Niveis 2/3; Nivel 4 se houver migracao: fixtures de AC/sem AC/upsolve/dica/pausas, campo ausente, isolamento por perfil, exportacao local e snapshots preservados. Dados reais insuficientes impedem conclusao empirica, sem bloquear documentacao do protocolo.

**Entrega demonstravel:** Auditar um Ipsum pseudonimizado desde o inicio persistido ate seu evento/censura, demonstrando rating anterior e ausencia de alteracao da avaliacao.

Conferencias humanas opcionais (D-014); criterios automatizados e evidencia
cientifica exigidos acima continuam obrigatorios. Fixtures comprovam o software; nenhum resultado real de transporte foi produzido.


Evidencia: 66 testes Python e 232 debug/170 release; lint sem erros e APK gerado.
Room 11->12 adiciona snapshots CF nullable, sem preencher legados pelo perfil atual.
CF observado antes do inicio fica imutavel. Exportacao explicita local por UUID,
sem ativo/handle/nome/tags/ids de submissao; AC posterior nao vira evento do Ipsum.
Encoder Android com Room/StartIpsum/AC gera fixture marcada synthetic, aceita pelo
preparador Python. API consulta somente metadados/estrutura, com gate/cache e
D-026 inteiro. Protocolo/particoes por participante fixados; n real=0 resulta em
insufficient_data, sem particoes/metricas/integracao. [Protocolo](../study/IPSUM_PROTOCOL.md),
[evidencia](evidence/2026-10-18-android-30.json) e
[estado da coorte](../study/evidence/2026-10-18-ipsum-data-status.json).

---

### Historia 31 - Validar relacao entre Rating Codeforces e Rating Lorem

**Historia:** Como pesquisador, quero avaliar se ratings e previsoes se transportam para Ipsums antes de autorizar seu uso no aplicativo.

**Status:** PARCIAL / BLOQUEADO para conclusao empirica: nenhum export real de Ipsums fornecido (n=0/insufficient_data). Pre-registro e ferramenta de avaliacao implementados em 2026-10-19; comparacao/decisao reais ainda pendentes.

**GitHub:** [#54 - Historia 31](https://github.com/Enzofmcs/Lorem/issues/54).

**Estimativa:** 4 h. **Fase:** Gate de evidencia posterior.

**Dependencias:** Historia 30

**Escopo:** Comparar ratings anteriores a cada tentativa e avaliar transporte das probabilidades/tempos. Nao presumir que Rating Lorem e Codeforces sejam intercambiaveis.

**Referencia:** [QUANTITATIVE.md](QUANTITATIVE.md) e [TESTING.md](TESTING.md).

#### Criterios de aceite

- [x] Fixar antes de abrir o teste Ipsum tamanho/precisao de amostra e limiares aceitaveis de MAE/calibracao/Brier conforme resultados de 28; registrar decisao e justificativa.
- [ ] Comparar pares CF anterior ao inicio e Lorem inicial: diferenca/vies, dispersao, correlacao e suporte; nunca usar Lorem posterior ao resultado nem inferir equivalencia pela correlacao.
- [ ] Comparar identidade e mapeamento simples ajustado so no treino, com participantes inteiros reservados e sem vazamento por tentativas do mesmo usuario; tratar campos historicos ausentes explicitamente.
- [ ] Avaliar erro de tempo, P(AC ate b), calibracao, censura e incerteza em Ipsums, com/sem dica e pausas; relatar deslocamento de dominio e cobertura.
- [ ] Registrar decisao: manter ratings distintos, adotar mapeamento validado ou nao integrar. Evidencia insuficiente/falha de limiar mantem 32 bloqueada; nao alterar D-012 nem avaliacoes existentes.

#### Verificacao planejada

Nivel 2 e reproducao do relatorio: splits por participante, ajuste sem teste, rating antes do resultado, metricas por orcamento/censura e gate reproduzivel. Nao substituir evidencia ausente por fixture.

**Entrega demonstravel:** Apresentar relatorio de transporte e decisao rastreavel com amostra, limiares predefinidos, erros e ICs, inclusive quando a conclusao for nao integrar.

Conferencias humanas opcionais (D-014); criterios automatizados e evidencia
cientifica exigidos acima continuam obrigatorios. Sem resultados comprovados
neste planejamento.

Pre-registro em [TRANSPORT_PROTOCOL.md](../study/TRANSPORT_PROTOCOL.md), D-032:
100 participantes/2.000 tentativas/1.000 eventos, IC95% e cobertura/suporte.
`evaluate-ipsums` vincula hashes, ajusta mapeamento apenas no treino, congela escolha
na validacao e avalia por participante. Testes sinteticos verificam software, nao
os quatro criterios empiricos acima; estes permanecem desmarcados. Estado real
vazio e plano em [evidencia da 31](../study/evidence/2026-10-19-transport/manifest.json).

---

### Historia 32 - Integracao local versionada das previsoes validadas

**Historia:** Como usuario, quero consultar estimativas validadas com orcamento e incerteza, mantendo o funcionamento local e minhas avaliacoes anteriores.

**Status:** BLOQUEADO: evidencia e decisao favoravel da Historia 31. Historia 29 implementada; nenhum modelo/previsao integrado ao app.

**GitHub:** [#55 - Historia 32](https://github.com/Enzofmcs/Lorem/issues/55).

**Estimativa:** 4 h. **Fase:** Condicional; fora das 24 h.

**Dependencias:** Historia 29, Historia 31

**Escopo:** Integracao minima somente depois de decisao favoravel da 31 e contrato de apresentacao registrado. Ate esse gate, nao implementar nem habilitar previsoes no app.

**Referencia:** [QUANTITATIVE.md](QUANTITATIVE.md) e [TESTING.md](TESTING.md).

#### Criterios de aceite

- [ ] Registrar evidencia/decisao favoravel da 31, dominio, limiares, mapeamento de rating e ponto de apresentacao antes de implementar; caso contrario manter a historia bloqueada.
- [ ] Consumir artefato pequeno e versionado localmente, sem Python embarcado/backend/login/nuvem, validando esquema e versoes de modelo, elegibilidade e mapeamento.
- [ ] Exibir estimativa de tempo, probabilidade ligada a orcamento explicito e incerteza/suporte sem vazar rating/tags/categoria durante Ipsum ativo; informar indisponibilidade/extrapolacao de forma clara.
- [ ] Persistir snapshots das novas previsoes e versoes, preservar historicos/avaliacoes antigas e pontuacao D-012; qualquer mudanca de formula exige outra decisao explicita.
- [ ] Manter operacao offline e fallback operacional D-012 quando artefato faltar/for incompativel, sem fabricar probabilidade; validar equivalencia numerica Python/Android e recuperacao.

#### Verificacao planejada

Niveis 3/4 e marco Android: fixtures de paridade numerica, versao/artefato invalido, offline/fallback, sigilo do ativo, migracao aditiva/snapshots e rating aplicado uma vez; regressao de test/lint/assembleDebug.

**Entrega demonstravel:** Consultar previsao local versionada e repetir offline; indisponibilidade do modelo preserva o treino, e uma avaliacao antiga permanece identica.

Conferencias humanas opcionais (D-014); criterios automatizados e evidencia
cientifica exigidos acima continuam obrigatorios. Sem resultados comprovados
neste planejamento.

---

## Novo bloco: contas Firebase e prazo maximo do Ipsum (33-38)

Solicitacao de **19/10/2026**, planejada contra `main` em
`2092c2f299e1590d791f93e59bdf5e60189bc707`. **33/34 refeitas para Spark (D-039):
base/login e JSON verificados localmente; Rules publicadas conforme usuario e confirmacao de Google unico pendente.
35-37 implementadas em preview; 38 planejada. Novas Rules 35-37 aguardam publicacao.** Contratos e casos de borda:
[FIREBASE.md](FIREBASE.md). Decisoes D-033 a D-039 em [DECISIONS.md](DECISIONS.md).

Ordem recomendada: **33 -> 34 -> 35 -> 36 -> 37 -> 38**. O bloco e independente
da conclusao empirica da 31; a 32 permanece bloqueada. As 00-30 e seus snapshots
nao sao reabertos. Estimativas preliminares somam **44-72 h de trabalho**, incluem
SDK/Rules/WorkManager/migracao/verificacao, excluem espera por configuracao e podem
ser refinadas na 33; nao pertencem ao orcamento de 24h do estudo Python.

| Requisito do usuario | Historias responsaveis |
|---|---|
| Login apenas Google via Firebase | 33-34 |
| Rating por conta+handle, handles repetidos entre contas, Firestore | 35-36 |
| Cada vinculo novo comeca em 800 e nao reseta ao retornar | 35 e migracao 38 |
| Ipsum de ate 2h, timeout automatico sem AC | 37 e regressao/migracao 38 |

Spark nao exige billing/Functions/Tasks/backend externo. Configuracao Google e
Rules precisam ser publicadas no projeto pelo responsavel. Sem novo passo de
posse CF ou exclusividade global de handle. Prazo absoluto nao depende de app
aberto; WorkManager/sincronizacao podem executar depois, sem estender as 2h.
Verificacao CF ocorre no Android e nao e comprovada independentemente pelas Rules.
Consulta incompleta fecha treino, mas adia resultado para preservar AC em prazo.
Conferencias humanas opcionais D-014.

### Historia 33 - Base Firebase Spark e configuracao reproduzivel

**Historia:** Como desenvolvedor, quero configurar Authentication Google e Firestore no plano Spark para persistir contas sem exigir billing ou servicos pagos.

**Status:** PARCIAL, refeita para Spark (D-039): base, Rules e bootstrap direto no Firestore implementados; usuario confirmou Rules publicadas. Confirmacao de Google como unico provedor ainda pendente; sem leitura administrativa pelo agente. JSON Android local validado; Firestore escolhido nam5 (US). Sem Functions/Tasks/backend externo. [Configuracao](FIREBASE_SETUP.md).

**GitHub:** [#60 - Historia 33](https://github.com/Enzofmcs/Lorem/issues/60).

**Estimativa preliminar:** 4-6 h.

**Dependencias:** Nenhuma no novo bloco; preserva arquitetura das 14-17.

**Referencia:** [FIREBASE.md](FIREBASE.md), [TESTING.md](TESTING.md) e D-039.

#### Criterios de aceite

- [x] Documentar Spark sem cartao/billing, quotas, app/package, OAuth Web, assinaturas debug efetivas e Firestore nam5; release/Play ficam para quando houver distribuicao assinada. IDs/JSON/aliases permanecem locais, sem credenciais administrativas no app.
- [x] Remover dependencia/configuracao/fontes de Functions, Cloud Tasks e Admin SDK; versionar Rules/indices e tooling Auth/Firestore Emulator, com publicacao somente de Rules/indices no Spark.
- [x] Permitir somente create de users/{uid} pelo proprio Google: esquema exato schemaVersion inteiro 1 e createdAt=request.time, usando serverTimestamp; negar sobrescrita/update/delete/list raiz e campos extras, inclusive uid/rating/PII.
- [x] Preservar leituras isoladas por uid/provedor e negar escritas de handles/ipsums/runtime ate contratos especificos das 35/36; nenhuma permissao generica por estar autenticado.
- [x] Verificar em emuladores tokens ausentes/nao Google, acesso cruzado, campos/horarios forjados, batch indevido e transacoes concorrentes/idempotentes, incluindo SDK Android real.
- [x] Registrar publicacao das Rules no projeto pelo responsavel: usuario confirmou pelo chat apos receber firebase/firestore.rules do commit d4a569f. [Registro](evidence/2026-10-19-firebase-rules-publication.json); sem leitura administrativa pelo agente e sem exigir novo deploy.
- [ ] Confirmar Google como unico provedor ativado no Auth real. PENDENTE: confirmacao solicitada ao usuario; JSON/build/emuladores nao demonstram ausencia de outros provedores.

**Entrega demonstravel:** Configuracao Spark e criacao protegida da propria conta, sem Function, custo de billing obrigatorio ou acesso a outra conta.

**Verificacao planejada:** Niveis 3/4: Rules/protocolo com Auth+Firestore Emulator, bootstrap SDK Kotlin e marco Android; testar falhas de rede/cota/permissao sem sessao falsa. Conferencia humana opcional (D-014).

**Evidencia:** dez testes Auth/Firestore/Rules em Node 24/Java 21; 23 testes direcionados Android, incluindo dois do bootstrap Kotlin real em emuladores, sem falhas/ignorados. Regressao: 256 debug (253 executados/3 opt-in ignorados), 188 release sem falhas; lint sem erros e APK. Os tres opt-in passaram na execucao direcionada. [Evidencia Spark](evidence/2026-10-19-firebase-spark-33-34.json). Registros anteriores preservados como historicos. Usuario confirmou publicacao das Rules posteriormente; Google unico ainda aguarda confirmacao.

---

### Historia 34 - Login Google e sessao com Firestore no Spark

**Historia:** Como usuario, quero entrar somente com Google, recuperar minha sessao e ter minha conta criada sem depender do plano Blaze.

**Status:** PARCIAL, refeita para Spark (D-039): Credential Manager/Google ID/Firebase Auth e bootstrap SDK Firestore implementados; usuario confirmou Rules publicadas. Falta confirmar Google unico no Auth real. Destino Seus handles e a integracao da 35, sem cadastro/treino legado. [Configuracao](FIREBASE_SETUP.md).

**GitHub:** [#61 - Historia 34](https://github.com/Enzofmcs/Lorem/issues/61).

**Estimativa preliminar:** 4-6 h.

**Dependencias:** Historia 33 Spark.

**Referencia:** [FIREBASE.md](FIREBASE.md), [TESTING.md](TESTING.md) e D-039.

#### Criterios de aceite

- [x] Oferecer somente Entrar com Google via Credential Manager/Google ID/Firebase Auth, sem senha/cadastro paralelo/anonimo nem chamada a Functions.
- [x] Revalidar token/provedor ao restaurar/retomar e liberar sessao somente apos transacao Firestore online confirmada. Criar metadados uma vez se ausentes; restauracao, concorrencia e retry preservam createdAt e demais dados existentes compativeis.
- [x] Capturar uid validado no bootstrap e conferir identidade durante retry/retorno; chavear destino Seus handles por uid, como ponto de integracao da 35. Nao implementar cadastro de handles nesta historia nem exibir perfil legado.
- [x] Tratar cancelamento, rede/offline, quota Spark, token revogado/expirado e Rules ausentes/configuracao incorreta; exibir carregamento/erro e recuperacao sem confundir permissao negada com provedor errado ou autorizar por cache/fila offline.
- [x] Sair/trocar conta remove UI e cancela observers/callbacks/trabalhos anteriores; resposta atrasada nunca restaura uid antigo. Sair nao apaga dados nem reinicia prazo de futuros Ipsums.
- [x] Registrar separadamente a publicacao das Rules confirmada pelo usuario, sem apresenta-la como leitura administrativa ou OAuth testado. [Registro](evidence/2026-10-19-firebase-rules-publication.json).
- [ ] Registrar confirmacao de Google como unico provedor no projeto real; pergunta de configuracao enviada ao usuario. Teste interativo do app continua opcional (D-014).

**Entrega demonstravel:** Login Google, restauracao, saida e troca com conta Firestore criada idempotentemente; nenhuma Function e nenhum dado legado exposto. Selecao/cadastro de handles pertence a 35.

**Verificacao planejada:** Niveis 3/4: sessao/callback atrasado, SDK Firestore real em emulador, concorrencia, errors/quota/retry, UI e worker legado inerte; marco test/lint/assembleDebug. Conferencia humana opcional (D-014).

**Evidencia:** 23 testes direcionados com flag true, SDK Firestore Kotlin real, bootstrap concorrente, uid capturado, sessao, erros/rede/cota/Rules, UI/decoder e entrada; todos passaram. Marco completo da 33 aprovado. [Evidencia Spark](evidence/2026-10-19-firebase-spark-33-34.json). OAuth real nao comprovado; flag lorem.firebaseAuthEnabled permanece false por padrao ate a 38.

---

### Historia 35 - Handles por conta e Rating Lorem inicial em 800

**Historia:** Como usuario logado, quero cadastrar e alternar handles, mantendo um Rating Lorem independente para cada um.

**Status:** IMPLEMENTADA E VERIFICADA EM PREVIEW (Spark, D-040); novas Rules ainda precisam ser publicadas. Ciclo de treino 36-37 integrado na PR 69; rollout da 38 permanece pendente.

**GitHub:** [#62 - Historia 35](https://github.com/Enzofmcs/Lorem/issues/62).

**Estimativa preliminar:** 8-12 h.

**Dependencias:** Historias 33 e 34.

**Referencia:** [FIREBASE.md](FIREBASE.md) e [TESTING.md](TESTING.md).

#### Criterios de aceite

- [x] Validar existencia em user.info e persistir grafia canonica/chave normalizada em users/{uid}/handles/{handleKey}; nao exigir prova de posse nem reserva global do handle.
- [x] Criar cada vinculo novo por transacao do SDK Firestore, com Rules exigindo Rating Lorem exatamente 800, para CF ranqueado ou sem rating; mesmo uid/handle existente e retornado sem reset.
- [x] Permitir o mesmo handle em contas Google diferentes, com documentos, ratings, historicos e consolidacao independentes; diferenca de caixa no mesmo uid nao cria outro saldo.
- [x] Listar/adicionar/selecionar handles e restaurar o progresso de cada um; login, sincronizacao, reinstalacao e reinsercao nao sobrescrevem o saldo existente. 800 nao vira piso da formula D-012.
- [x] Migrar chaves de cache e ProfileLink para uid+handle+versao, isolar leituras/gravacoes/trabalhos e bloquear troca de handle durante Ipsum ativo; catalogo publico pode ser compartilhado.

**Entrega demonstravel da 35:** Contas A/B cadastram o mesmo handle em 800; alternar handles restaura saldo/consolidacao e selecao. SDK/Room novos simulam reinstalacao sem cache anterior. Progresso evoluido e lock sao fixtures locais identificadas; nao foram produzidos por treino em nuvem. A jornada integrada original (treinar em A sem mudar B) depende do ciclo da 36. Leitura de historicos ja e isolada por uid nas Rules; escrita/uso no treino continua na 36.

**Verificacao:** Niveis 4/5: 16 Auth/Rules, 42 Android direcionados sem falhas/ignorados, incluindo cinco SDK/emuladores e tres Compose. Criacao concorrente/idempotente, CF alto/unrated, caixa/Locale.ROOT, mesmo handle em dois uids, cache/worker atrasado, logout/troca de conta, lock e restauracao sem reset cobertos; migracao aditiva Room 12->13 e cadeia 9->13 preservam legado. Regressao/build/lint e hashes na [evidencia da 35](evidence/2026-10-19-firebase-handles-35.json). Nenhum deploy/live OAuth alegado.

Conferencias humanas opcionais (D-014); os testes automatizados exigidos sao obrigatorios.

---

**Integracao com 36-37:** contrato cf_/loremRating/progressVersion, selecao versionada, cache Room 13 e identidades da PR 68 preservados. Verificacoes da combinacao registradas separadamente em [evidencia do merge](evidence/2026-10-19-firebase-merge-69.json).

### Historia 36 - Ciclo oficial do Ipsum e rating persistidos no Firestore

**Historia:** Como usuario, quero que tentativas e evolucao fiquem salvas na minha conta e que cada resultado altere meu rating uma unica vez.

**Status:** IMPLEMENTADA E VERIFICADA EM PREVIEW Spark. Flag padrao false ate 38; novas Rules aguardam publicacao no projeto real. Confirmacao de Google unico da base 33/34 segue pendente. Sem OAuth/deploy reais alegados.

**GitHub:** [#63 - Historia 36](https://github.com/Enzofmcs/Lorem/issues/63).

**Estimativa preliminar:** 12-20 h.

**Dependencias:** Historias 33-35; reutiliza regras das 04-17 e corte da 29.

**Referencia:** [FIREBASE.md](FIREBASE.md) e [TESTING.md](TESTING.md).

#### Criterios de aceite

- [x] Iniciar por transacao SDK Firestore autenticada com idempotency key, serverTimestamp, snapshot do rating atual e lock: no maximo um Ipsum ativo por uid entre handles/dispositivos, com invariantes nas Rules.
- [x] Validar candidato, ineditismo, metadados/corte da 29 e cobertura inicial do historico no Android; Rules validam estrutura/transicoes e imutabilidade dos snapshots. Documentar que Rules nao comprovam o veredito Codeforces de um cliente modificado (D-039).
- [x] Consultar/verificar submissoes no Android com API centralizada, gate de pelo menos 2s, paginacao, deduplicacao e retries; coordenar UI/WorkManager para evitar polling duplicado. Nao prometer verificacao independente no servidor no Spark.
- [x] Persistir snapshots, dica, resultado, consolidacao e versoes; transacionar resultado+rating+lock pelo SDK e aplicar D-012 uma vez. Rules verificam campos/transicoes, versao e consistencia getAfter entre documentos; sem permitir reset ou dupla avaliacao.
- [x] Recuperar o mesmo ativo/progresso apos reinstalacao ou segundo dispositivo, preservando sigilo de tags/rating/categoria no ativo; AC posterior somente classifica upsolve.
- [x] Permitir consulta ao cache offline, mas exigir servidor para novo inicio e confirmacao de saldo; tratar timeout de resposta apos commit sem criar outro Ipsum ou delta.

**Entrega demonstravel:** Iniciar em um dispositivo, recuperar no outro e receber um unico resultado/rating mesmo repetindo os comandos.

**Verificacao planejada:** Nivel 4 e marco: transacoes concorrentes entre dispositivos/handles, resposta perdida/retry, escrita direta negada, D-012, gate, exclusoes e metadados, snapshots e sigilo; suite de Rules/SDK em emuladores e test/lint/assembleDebug.

**Evidencia automatizada:** SDK Kotlin de producao em Auth/Firestore Emulator, Rules com dois uids/dispositivos, dominio, UI e WorkManager; marco Android em [2026-10-19-firebase-35-37.json](evidence/2026-10-19-firebase-35-37.json). Inicio+marker+lock e resultado+rating+versao+liberacao atomicos; snapshots/dica, idempotencia, recuperacao, D-012/D-015 existentes, cache e sigilo verificados. Rules nao verificam o veredito CF independentemente do Android.

TESTE MANUAL OPCIONAL - NAO EXECUTADO (D-014).

---

### Historia 37 - Encerramento automatico do Ipsum em ate duas horas

**Historia:** Como usuario, quero que um Ipsum feche automaticamente ao atingir duas horas e conte como tentativa sem AC quando nao houver AC valido no prazo.

**Status:** IMPLEMENTADA E VERIFICADA EM PREVIEW Spark. Flag padrao false ate 38; novas Rules aguardam publicacao no projeto real. Confirmacao de Google unico da base 33/34 segue pendente. Sem OAuth/deploy reais alegados.

**GitHub:** [#64 - Historia 37](https://github.com/Enzofmcs/Lorem/issues/64).

**Estimativa preliminar:** 8-16 h.

**Dependencias:** Historia 36; contratos de prazo e reconciliacao em FIREBASE.md.

**Referencia:** [FIREBASE.md](FIREBASE.md) e [TESTING.md](TESTING.md).

#### Criterios de aceite

- [x] Persistir startedAt=serverTimestamp, duracao maxima 7.200.000ms e versao da regra; derivar expiresAt apos confirmacao do inicio. Nao pausar/reiniciar por lifecycle/logout/rede/aparelho/relogio civil; nao inventar transformacao serverTimestamp()+2h.
- [x] Fechar a experiencia no prazo com app aberto/offline e tratar vencimento em toda leitura/comando. Agendar WorkManager unico por uid/handle/ipsum no inicio confirmado, com rede/retry e reparo no resume/reboot; Spark sem Functions/Tasks/TTL. Execucao tardia nao estende prazo; dispositivo impedido ou sem sessao pode exigir nova abertura/login para sincronizar.
- [x] Adotar fronteira UTC inclusiva para AC enviado entre inicio e expiresAt, com precisao real do CF; AC no limite vence timeout, e depois do limite e somente upsolve.
- [x] Verificar completamente a janela e vereditos pendentes antes do resultado definitivo; se API/rede falhar, fechar em CLOSED_AWAITING_VERIFICATION, fazer retry e impedir novo Ipsum ate fixar saldo. AC em prazo descoberto tarde conta como sucesso, sem segunda avaliacao.
- [x] Sem AC elegivel, gravar fim efetivo em expiresAt, TIME_LIMIT_EXCEEDED, estado pendente, duracao de 2h e delta sem AC D-012 exatamente uma vez; processedAt tardio fica separado.
- [x] Atualizar UI, cronometro/tempo restante, resultado, historico, estatisticas e upsolve; resolver corrida AC/desistencia/timeout atomicamente. Encerramento antecipado usa sua propria fronteira e nunca aceita AC posterior.

**Entrega demonstravel:** Relogio controlado cruza 2h com app fechado; ao retornar, tentativa esta encerrada no prazo e rating sem AC foi aplicado uma vez. Demonstrar tambem AC em prazo recebido tarde.

**Verificacao planejada:** Nivel 4: antes/exatamente/depois de 2h, anterior ao inicio, AC tardio/TESTING/paginacao incompleta, offline/reboot/relogio alterado, job atrasado/perdido e concorrencia; invariantes e marco Rules/SDK/Android.

**Evidencia automatizada:** SDK Kotlin de producao em Auth/Firestore Emulator, Rules com dois uids/dispositivos, dominio, UI e WorkManager; marco Android em [2026-10-19-firebase-35-37.json](evidence/2026-10-19-firebase-35-37.json). Fronteiras inclusivas, fracao submilissegundo, API indisponivel, entrada/pagina incompleta, TESTING, AC elegivel recebido tarde, timeout sem AC, upsolve sem recalculo, referencia monotonic/boot e reparo de job unico verificados. WorkManager continua sujeito ao sistema/rede/sessao.

TESTE MANUAL OPCIONAL - NAO EXECUTADO (D-014).

---

### Historia 38 - Migracao e jornada integrada de contas e prazo

**Historia:** Como usuario atual ou novo, quero adotar o login e os perfis em nuvem preservando meus registros anteriores e entendendo meu novo progresso.

**Status:** PLANEJADA; todos os criterios funcionais pendentes.

**GitHub:** [#65 - Historia 38](https://github.com/Enzofmcs/Lorem/issues/65).

**Estimativa preliminar:** 8-12 h.

**Dependencias:** Historias 34-37; compatibilidade com exportacao da 30 e gates 31/32.

**Referencia:** [FIREBASE.md](FIREBASE.md) e [TESTING.md](TESTING.md).

#### Criterios de aceite

- [ ] Implementar migracao idempotente/aditiva com arquivo local legado somente leitura, snapshots/rating/consolidacao/duracoes antigas intactos e contexto de conta correto; nunca atribuir o arquivo a outro uid.
- [ ] Explicar arquivo anterior versus progresso Firebase: vinculo novo inicia 800, vinculo em nuvem existente restaura saldo; nao importar rating CF/legado para o novo saldo nem recalcular avaliacoes antigas.
- [ ] Encerrar/reconciliar ativo legado com inicio preservado e limite de 2h antes do treino novo; preservar exclusoes de tentativas/Ipsums antigos e reimportar historico CF sem tornar problemas antigos ineditos.
- [ ] Automatizar jornada Google/sessao, dois uids com mesmo handle, varios handles, reinstalacao/segundo dispositivo, falha apos commit, logout, expiracao e AC posterior, usando fakes apenas no escopo de teste.
- [ ] Versionar identidade/rating inicial/prazo dos novos snapshots e exports sem UID/e-mail/handle diretos; documentar compatibilidade e agrupamento de handles repetidos antes de usar novas coortes na 31, mantendo a 32 bloqueada.
- [ ] Atualizar demonstracao e guia de configuracao/deploy/recuperacao; registrar evidencias de Rules/SDK Firestore Spark e test/lint/assembleDebug antes de liberar o novo fluxo integrado. Teste manual permanece opcional.

**Entrega demonstravel:** Migrar instalacao com historico e ativo, entrar com Google, criar perfil 800, completar/expirar um Ipsum e recuperar os dados sem vazamento ou dupla avaliacao.

**Verificacao planejada:** Niveis 4/5: migracao com interrupcao/retry e nuvem preexistente, fronteiras do prazo, isolamento, export e regressao dos invariantes; suites Rules/SDK/Android, lint e APK. Nao executar build apenas por este planejamento.

Conferencias humanas opcionais (D-014); os testes automatizados exigidos sao obrigatorios.

---

## 10. Marcos demonstraveis

- **A - Espinha funcional (00-07):** handle, recomendacao, Ipsum, submissao e resultado.
- **B - Memoria e avaliacao (08-10):** rating, historico, pendencias e upsolve.
- **C - Orientacao de estudo (11-13):** estatisticas, variedade e consolidacao.
- **D - Entrega (14):** fluxo completo testado, explicavel e demonstravel.
- **E - Automacao (15-17):** preparacao, manutencao e deteccao sem cliques obrigatorios.
- **F - Apresentacao (18-21):** temas e telas consistentes, acessiveis e demonstraveis.
- **G-J - Extensao quantitativa (22-32):** progresso e gates registrados nas respectivas historias; 31/32 continuam pendentes de evidencia real.
- **K - Contas e dados Firebase (33-36):** 33/34 refeitas para Spark e em preview, configuracao remota pendente; handles, 800 inicial e ciclo das 35/36 ainda planejados.
- **L - Prazo e adocao (37-38):** limite de 2h com recuperacao, migracao e jornada integrada verificados; planejado.

---
