# AGENTS.md - Guia operacional do Lorem

> Instrucoes compactas para todas as sessoes. A documentacao detalhada esta dividida
> por assunto em [`docs/`](docs/README.md); leia apenas o contexto exigido pela tarefa.

## Produto e prioridades

**Lorem** e um aplicativo Android de treino individual para programacao competitiva.
Ele recomenda um problema inedito do Codeforces, conduz uma tentativa cronometrada
chamada **Ipsum** e produz feedback sobre desempenho.

Prioridades, nesta ordem:

1. Fluxo completo do Ipsum funcionando.
2. Correcao dos dados e das regras.
3. Utilidade real para o treinamento.
4. Simplicidade de desenvolvimento e manutencao.
5. Aparencia visual.

## Regras invariantes essenciais

- Identifique um problema por `contestId + index`, nunca apenas pelo nome.
- Um problema ja tentado, resolvido, usado em outro Ipsum ou pendente nao pode ser recomendado.
- So pode existir um Ipsum ativo; no bloco Firebase 33-38, a garantia passa a ser por uid entre handles/dispositivos.
- Bloco 33-38 aprovado: Spark (D-039), Google/Firestore, uid+handle, 800 por vinculo novo e prazo absoluto 2h. 35-37 implementadas em preview; 38 (migracao/rollout) pendente. Confirmacao de Google unico pendente. Publicacao confirmada pelo usuario cobre somente Rules 33/34; as novas Rules 35-37 ainda precisam de publicacao. WorkManager nao garante instante fisico e nunca estende prazo. Contratos em docs/FIREBASE.md e configuracao em docs/FIREBASE_SETUP.md.
- Handles iguais em uids diferentes sao permitidos; nao adicionar verificacao extra de posse CF. Login/troca de handle nao reiniciam rating; avaliacoes antigas permanecem preservadas.
- Submissoes anteriores ao inicio nao contam para o Ipsum atual.
- Um Ipsum encerrado nao pode alterar o rating duas vezes.
- AC posterior muda a classificacao da pendencia, mas nao recalcula o rating original.
- Rating, tags e categoria permanecem escondidos enquanto o Ipsum esta ativo.
- Use somente tags oficiais do Codeforces.
- Centralize a API e respeite no maximo uma requisicao a cada dois segundos.
- Novas recomendacoes deverao exigir data verificavel do contest de origem >=
  01/07/2020; implementado na Historia 29. Preservar historicos antigos
  e sua exclusao de repeticoes. Nao inferir data pelo id.
- Estudo Python: contests especiais e contests com qualquer problema em duas
  versoes (ex.: B1/B2, facil/dificil) ficam integralmente fora da analise, inclusive
  seus problemas simples. Exclusao intencional do usuario (D-026); preservar brutos.

As regras completas e o escopo fechado estao em [`docs/SPEC.md`](docs/SPEC.md).

## Base tecnica

- Kotlin, Jetpack Compose, um modulo, ViewModel + StateFlow e Coroutines.
- Room para dados estruturados e DataStore para configuracoes pequenas.
- Cliente HTTP centralizado, Navigation Compose e injecao manual simples.
- Sem Hilt ou backend proprio sem necessidade e decisao registrada.
- Gradle 8.11 ou superior e Java 17; o repositorio nao versiona JARs.

Preserve uma estrutura existente que ja seja funcional. Nao adicione dependencia,
backend ou abstracao sem necessidade concreta.

## Estado atual

- **Bloco Firebase Spark em 19/10/2026 (D-039):** 35-37 implementadas em preview por `-Plorem.firebaseAuthEnabled=true`, padrao false ate 38. Handles canonicos por uid, 800 somente na criacao, recomendacao CF verificada, snapshots imutaveis, lock por uid, resultado/rating/consolidacao atomicos e prazo 2h com WorkManager. Cache/selecao novos isolados por uid+handle+versao; Room legado preservado, migracao na 38. Rules 33/34 publicadas conforme usuario; novas Rules 35-37 ainda nao publicadas pelo agente. Google unico pendente; OAuth real nao testado; configuracao Android local ignorada pode precisar ser restaurada neste checkout. Nao habilita a 32.
- **Marco 35-37:** 20 testes Rules/Auth, 278 debug (277 executados/1 smoke opt-in aprovado separadamente), 200 release; nove testes com SDK Kotlin real em emuladores, incluindo cache offline/precisao do prazo. Lint sem erros (58 avisos) e APK preview. JSON ignorado ausente neste checkout; entrada bloqueada sem configuracao, conforme smoke. Evidencia: docs/evidence/2026-10-19-firebase-35-37.json. TESTE MANUAL OPCIONAL - NAO EXECUTADO (D-014).
- **Marco Spark 33/34:** dez testes Auth/Rules e 23 direcionados Android com bootstrap Kotlin real/emuladores; regressao 256 debug (253 executados/3 opt-in ignorados e aprovados separadamente) e 188 release, sem falhas; lint sem erros e APK preview. Evidencia em docs/evidence/2026-10-19-firebase-spark-33-34.json. Node 24/Java 21 para tooling, JDK 17 Android. Historias parciais por estado remoto, nao por conferencia humana opcional.
- **Limite Spark:** Android consulta Codeforces/calcula resultado; Rules nao comprovam AC de cliente modificado. 35-37 usam SDK/transacoes/Rules e WorkManager agendado no inicio, reparado no resume/login. Atrasos/sessao/rede podem adiar gravacao, nunca estender 2h. Fechamento sem verificacao completa preserva lock e saldo. Proxima historia funcional: 38.
- **Historico preservado:** evidencias anteriores em docs/evidence/2026-10-19-firebase-33-34.json e docs/evidence/2026-10-19-firebase-android-config.json descrevem Functions/configuracao inicial, nao a arquitetura Spark. D-039 substitui compute de D-033/D-037/D-038; nam5/preview/isolamento preservados.
- **Contrato do novo bloco:** [Firebase, prazo e migracao](docs/FIREBASE.md). Requisitos novos substituem a exclusao anterior de login/Firebase e o rating inicial D-009 somente prospectivamente (D-033 a D-039).

- **Foco:** Historias 35-37 implementadas em preview; proxima historia funcional 38 (migracao/rollout). Historias 00-30 preservadas; 31 com pre-registro/ferramenta e evidencia real pendente.
- **Ultima historia verificada:** Historia 37 no preview Spark; evidencia em docs/evidence/2026-10-19-firebase-35-37.json. Marco 30 de 2026-10-18 preservado: 232 debug/170 release e 66 Python, exportacao local/contrato Android-Python e snapshots anteriores.
- **Primeiro item funcional pendente:** Historia 38. Estudo 31 continua BLOQUEADO para conclusao empirica por ausencia de coorte real; limiares fixados em TRANSPORT_PROTOCOL.md/D-032 antes do teste.
- **Proxima entrega demonstravel:** migracao/rollout da 38 apos preparar o projeto real. Em paralelo, o estudo ainda exige coletar/exportar coorte real e aplicar evaluate-ipsums com modelos congelados da 28, sem retunar teste.
- **Bloqueios conhecidos:** nenhum export real fornecido (n=0, insufficient_data). 32 depende de evidencia/decisao favoravel da 31; 29 atendida. Teste manual opcional (D-014).

Ferramenta/pre-registro 31: [TRANSPORT_PROTOCOL.md](study/TRANSPORT_PROTOCOL.md),
84 testes Python e [evidencia](study/evidence/2026-10-19-transport/verification.json).
Piso final 100 participantes/2.000 tentativas/1.000 eventos mais precisao dos ICs;
nao substituir por piso de preparo nem fixtures. `evaluate-ipsums` nunca habilita
integracao automaticamente, mesmo com limites favoraveis: exigir decisao real da 31.

Marco Android 29-30: 232 testes debug e 170 release sem falhas; lint sem erros
(avisos de dependencias/icone) e `assembleDebug` concluido. Room 9->12 validado.
Progresso e aceite: [`docs/ROADMAP.md`](docs/ROADMAP.md).
Roteiro: [`docs/DEMO.md`](docs/DEMO.md).
Protocolo e progresso: [estudo quantitativo](docs/QUANTITATIVE.md) e
[Historia 22](docs/ROADMAP.md#historia-22---base-python-e-protocolo-reproduzivel-do-estudo).
Python: [guia e comandos](study/README.md), [contrato](study/DATA_DICTIONARY.md) e
[evidencia sintetica](study/evidence/2026-10-18-validation.json) e
[piloto real](study/evidence/2026-10-18-pilot-real.json). Amostra e modelo:
[evidencia da 26](study/evidence/2026-10-18-sample-aft.json) e
[metodo AFT](study/MODEL.md). Avaliacao reservada documentada na 28; ganho do AFT sobre a melhor referencia nao comprovado. Usar configs/regular-simple-v1.json nas novas coletas.
[Relatorio real](study/results/2026-10-18/report/REPORT.md) e
[metricas/ICs](study/results/2026-10-18/evaluation/metrics.json): KM por delta teve
MAE 15,135 min e Brier integrado 0,122746; AFT 15,892/0,126693. Somente quatro grupos
reservados; erros de tempo nos resolvidos tem vies e suporte diferente. D-012
continua em producao; nenhum retuning no teste ou transporte para Ipsums autorizado.
Comparacao entre epocas e correcao temporal fora do escopo.

## Politica de contexto

1. Leia sempre este `AGENTS.md`.
2. Identifique a tarefa e abra somente sua secao em [`docs/ROADMAP.md`](docs/ROADMAP.md),
   mais as dependencias indicadas nela.
3. Consulte em [`docs/SPEC.md`](docs/SPEC.md) apenas as partes das regras alteradas.
4. Consulte [`docs/DECISIONS.md`](docs/DECISIONS.md) quando houver duvida de produto
   ou arquitetura; nao invente uma decisao para destravar a tarefa.
5. Consulte [`docs/TESTING.md`](docs/TESTING.md) antes de definir a verificacao.
6. Inspecione primeiro os arquivos diretamente relacionados e amplie a busca somente
   quando houver evidencia de outras dependencias.
7. Evite varrer ou carregar todo o repositorio sem necessidade concreta.

## Forma de trabalho

1. Confirme no codigo o que ja existe para o item atual.
2. Apresente um microplano de 3 a 7 passos antes de editar.
3. Trabalhe apenas no item atual e em pre-requisitos indispensaveis.
4. Prefira uma entrega pequena funcionando de ponta a ponta.
5. Preserve arquitetura, padroes, escopo e regras; nao altere produto silenciosamente.
6. Use dados falsos somente quando claramente isolados do fluxo real.
7. Nao implemente itens fora do escopo nem recursos especulativos.

## Qualidade e verificacao

> A eficiencia de tokens e creditos e uma restricao operacional, nao um objetivo
> superior a qualidade. Otimize leitura, comandos e verificacoes redundantes, mas
> nunca deixe de testar uma regra critica apenas para economizar uso do Codex.

> O objetivo e reduzir trabalho redundante, e nao reduzir cobertura das partes
> criticas do sistema. Bugs evitaveis nas regras centrais custam mais do que testes
> direcionados.

- Classifique o risco e aplique o nivel correspondente de [`docs/TESTING.md`](docs/TESTING.md).
- Durante o desenvolvimento, prefira o teste direcionado; use a regressao completa
  nos marcos ou quando o risco justificar.
- Nao repita comando falho sem diagnosticar a causa nem repita build sem mudanca relevante.
- Pela D-014, testes manuais sao opcionais e nao bloqueiam conclusao ou passagem
  para a proxima historia. Avance com criterios obrigatorios comprovados e
  verificacoes automatizadas proporcionais ao risco.
- Nao alegue teste manual que nao executou. Registre `TESTE MANUAL OPCIONAL - NAO
  EXECUTADO` fora do checklist obrigatorio, sem aguardar confirmacao para avancar.
- Nao marque `[x]` sem evidencia: `[ ]` significa nao comprovado e `BLOQUEADO:` exige
  informacao, decisao ou recurso externo.
- Nunca conclua uma historia com criterio obrigatorio pendente.

## Encerramento da sessao

1. Execute as verificacoes proporcionais definidas em `docs/TESTING.md`.
2. Atualize somente os checklists realmente comprovados em `docs/ROADMAP.md`.
3. Registre decisoes novas ou substituidas em `docs/DECISIONS.md`.
4. Acrescente a sessao e mantenha o historico curto em `docs/SESSIONS.md`.
5. Atualize o estado acima se o foco mudou.
6. Informe o que mudou, como foi verificado, conferencias manuais opcionais e proximo passo.

## Mapa da documentacao

- [`docs/SPEC.md`](docs/SPEC.md): produto, escopo e regras funcionais estaveis.
- [`docs/ROADMAP.md`](docs/ROADMAP.md): historias, aceite, progresso, dependencias e marcos.
- [`docs/DECISIONS.md`](docs/DECISIONS.md): decisoes abertas e registro historico.
- [`docs/TESTING.md`](docs/TESTING.md): estrategia proporcional a risco e comandos.
- [`docs/FIREBASE.md`](docs/FIREBASE.md): planejamento 33-38, identidade/Firestore, rating inicial 800, prazo de 2h e migracao.
- [`docs/FIREBASE_SETUP.md`](docs/FIREBASE_SETUP.md): base 33/34, preview Android, projeto/OAuth/deploy pendentes, testes em emuladores e recuperacao.
- [`docs/SESSIONS.md`](docs/SESSIONS.md): registro curto das sessoes recentes.
- [`docs/DEMO.md`](docs/DEMO.md): demonstracao reproduzivel e avaliacao do TCC.
- [`docs/QUANTITATIVE.md`](docs/QUANTITATIVE.md): protocolo, estimandos, dados, censura, avaliacao e gates da extensao Python.

## Prompt padrao para futuras sessoes

```text
Leia AGENTS.md e identifique a tarefa atual. Consulte somente a secao necessaria do
ROADMAP.md e as partes relacionadas da especificacao ou decisoes quando relevantes.
Inspecione primeiro os arquivos diretamente relacionados. Implemente a menor mudanca
funcional correta. Use TESTING.md: testes direcionados durante o desenvolvimento e
verificacoes completas nos marcos adequados. Nao reduza testes de logica critica para
economizar creditos. Ao terminar, atualize o progresso e informe o que mudou, como foi
verificado, conferencias manuais opcionais e o proximo passo. Testes manuais nao
bloqueiam o avanco entre historias.
```

Validacao com Ipsums: [protocolo](study/IPSUM_PROTOCOL.md),
[estado real vazio](study/evidence/2026-10-18-ipsum-data-status.json) e
[evidencia Android](docs/evidence/2026-10-18-android-30.json). CF historico ausente
nao e preenchido pelo perfil atual; pausas desconhecidas nao viram zero. Manter
32 bloqueada e nao apresentar fixtures como evidencia de transporte.
