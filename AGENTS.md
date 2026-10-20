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
- Bloco Firebase 33-38 (D-039/D-043): entrada Google/Firestore padrao, 800 por novo vinculo, prazo absoluto 2h e arquivo local somente leitura na 38. Google unico, OAuth autorizado e atualizacao das Rules confirmados pelo usuario; adicionar google-services.json local permanece pendente (confirmacao de 20/10/2026). Room 14 preserva legado sem uid e sincroniza somente IDs de problemas excluidos. WorkManager pode atrasar a gravacao, nunca estender prazo.
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

- **Diagnóstico Google após PR85, 20/10/2026:** usuário agora observa “A entrada foi interrompida pelo Google”; falha localizada no provedor antes da troca Firebase, sem causa específica comprovada. Logs distinguem retorno da Activity de status Google e preservam apenas códigos numéricos, sem mensagens brutas. 33 testes direcionados sem falhas/ignorados e APK. [Evidência](docs/evidence/2026-10-20-google-provider-diagnostic.json). Aguardando códigos do aparelho; nenhum ajuste OAuth/Rules ou mudança de fluxo.

- **Correção pós-43: entrada Google, 20/10/2026:** retorno silencioso reportado após escolher conta. Revalidar explicitamente a identidade atual após troca do token; não depender de novo evento para mesmo uid. Interrupção/credencial ausente exibem feedback, troca/validação limitadas a 30s e sem reinício por resume. Logs de etapas/códigos sob `LoremAuth`, sem tokens/e-mail/uid/mensagens brutas. 343 debug/245 release sem falhas ou ignorados, 16 SDK reais incluídos, 43 direcionados de autenticação e 14 rechecados após reforçar a asserção de uid; lint sem erros (58 avisos) e APK. [Evidência](docs/evidence/2026-10-20-google-login-fix.json). OAuth/Rules confirmados pelo usuário; causa do provedor no aparelho ainda não observada. JSON ausente neste checkout; nenhum deploy ou login Google real pelo agente.

Atualização operacional em 20/10/2026: [OAuth e Rules confirmados pelo responsável](docs/evidence/2026-10-20-firebase-oauth-rules-confirmation.json). Marcos anteriores abaixo preservam o estado observado na ocasião.

- **Histórias 44–48, 20/10/2026:** cinco notebooks Jupyter reutilizam módulos Python; modos agregado/full/sintético, plano x/y prévio, quartis/orçamento v2 e bootstrap por grupos. Após busca sem backup de 18/10, usuário autorizou coleta nova: 20 adquiridos/19 elegíveis, 355.796 tentativas válidas. 114 testes Python sem falhas/ignorados; 23 novos também sem site-packages; 15 kernels limpos, cinco reais. Replay offline zero chamadas e mesmo hash; backup local de 238.668.830 bytes com audit e 321 brutos conferidos. [Relatório](study/results/2026-10-20-notebooks/REPORT.md), [evidência](study/evidence/2026-10-20-notebooks.json), [comandos/backup](study/notebooks/README.md). Antigo snapshot não recuperado; nova análise descritiva, sem avaliação preditiva nova. D-012/time-curve-v1/Android intactos; 49 fora do bloco.

- **Histórias 40–43, 20/10/2026:** experiência cloud recuperada com shell/telas anteriores, histórico/gráficos/filtros, tema/conta em Configurações e ativo restaurável. Preparação por etapas com cache público/incremental verificado, budgets e pendências automáticas em lotes sob a trava compartilhada. 332 debug/237 release sem falhas ou ignorados, 15 SDK reais em emuladores, 34 Auth/Rules; lint sem erros (58 avisos) e APK. [Evidência](docs/evidence/2026-10-20-cloud-40-43.json), [paridade/budgets](docs/CLOUD_EXPERIENCE.md), [operação](docs/FIREBASE_OPERATION.md). Rules39 inalteradas; cliente D-012 recusado por Rules38 e aceito pelas atuais em emulador. OAuth e atualização das Rules confirmados pelo usuário em 20/10/2026; resta adicionar o JSON local. Sem deploy/readback/login real pelo agente. [Confirmação](docs/evidence/2026-10-20-firebase-oauth-rules-confirmation.json). D-012 ativa e time-curve-v1 desabilitada.

- **Evolução após 38 — D-044/história 39:** política experimental de pontos por
  curva implementada no cloud, com função pura, snapshot imutável, ganho zero,
  decomposição no resultado e exportação. [Contrato](docs/TIME_SCORING.md),
  [cenários](study/results/time-scoring-v1/REPORT.md). Ativação desabilitada: CF
  anterior não equivale a Lorem 800, e a consulta 1200/1600 não identifica p75/p90
  em 120min. Artefato real incluído é parcial; modelo KM completo ausente deste
  checkout. Exportador suporta bins do modelo completo sem ler o teste. Fallback
  D-012 é persistido ao iniciar. A nova solicitação autoriza o software da 39,
  sem reabrir a avaliação empírica retirada da 31. Publicação das novas Rules ainda
  necessária; não executar deploy automaticamente. Verificação: 321 debug/228
  release, 15 SDK reais, 33 Auth/Rules e 91 Python sem falhas/ignorados; lint sem
  erros e APK. [Evidência](docs/evidence/2026-10-19-time-scoring-39.json).

- **Bloco Firebase Spark:** 33/34 fechadas com confirmacao de Google unico pelo usuario; 35-37 integradas na PR 69. Historia 38 implementada e verificada: arquivo local Room 14, reconciliacao de ativo, exclusoes entre clientes e export v2; Firebase por padrao. JSON Android ausente neste checkout; sem OAuth real. Evidencia da configuracao/escopo: docs/evidence/2026-10-19-firebase-google-scope-confirmation.json. Novas Rules 38 precisam de publicacao.
- **Integracao PR 69 (D-041):** historia 35 da main preservada (D-040), treino 36-37 adaptado aos contratos existentes; current existe somente durante um ativo e clock separado confirma prazo. Verificados 27 Auth/Rules, 300 debug executados/1 smoke opt-in separado, 216 release e 13 SDK reais em emuladores; lint sem erros e APK. Evidencia da combinacao em docs/evidence/2026-10-19-firebase-merge-69.json; registros historicos 35 e 35-37 preservados.
- **Marco 35-37 anterior a integracao:** 20 testes Rules/Auth, 278 debug (277 executados/1 smoke opt-in aprovado separadamente), 200 release; nove testes com SDK Kotlin real em emuladores, incluindo cache offline/precisao do prazo. Lint sem erros (58 avisos) e APK preview. JSON ignorado ausente neste checkout; entrada bloqueada sem configuracao, conforme smoke. Evidencia: docs/evidence/2026-10-19-firebase-35-37.json. TESTE MANUAL OPCIONAL - NAO EXECUTADO (D-014).
- **Marco Spark 33/34:** dez testes Auth/Rules e 23 direcionados Android com bootstrap Kotlin real/emuladores; regressao 256 debug (253 executados/3 opt-in ignorados e aprovados separadamente) e 188 release, sem falhas; lint sem erros e APK preview. Evidencia em docs/evidence/2026-10-19-firebase-spark-33-34.json. Node 24/Java 21 para tooling, JDK 17 Android. Historias parciais por estado remoto, nao por conferencia humana opcional.
- **Limite Spark:** Android consulta Codeforces/calcula resultado; Rules nao comprovam AC de cliente modificado. 35-37 usam SDK/transacoes/Rules e WorkManager agendado no inicio, reparado no resume/login. Atrasos/sessao/rede podem adiar gravacao, nunca estender 2h. Fechamento sem verificacao completa preserva lock e saldo. A 38 mantem esse limite e congela o arquivo legado apos reconciliar o ativo.
- **Historico preservado:** evidencias anteriores em docs/evidence/2026-10-19-firebase-33-34.json e docs/evidence/2026-10-19-firebase-android-config.json descrevem Functions/configuracao inicial, nao a arquitetura Spark. D-039 substitui compute de D-033/D-037/D-038; nam5/preview/isolamento preservados.
- **Contrato do novo bloco:** [Firebase, prazo e migracao](docs/FIREBASE.md). Requisitos novos substituem a exclusao anterior de login/Firebase e o rating inicial D-009 somente prospectivamente (D-033 a D-039).

- **Foco atual:** 44–48 implementadas/verificadas com nova coleta real; 40–43 e correções Google das PRs 85/86 preservadas; pendências operacionais explícitas no runbook. **Marco anterior:** entrega final 33-38 implementada/verificada. Historia 31 retirada do escopo pelo usuario (D-042); 32 fica fora desta entrega por dependencia, sem previsoes. Historias 00-30 e evidencias anteriores preservadas.
- **Última história verificada:** 48; evidência Jupyter 2026-10-20 acima. Marco Android anterior: 43. **Marco anterior, preservado:** História 38, 310 debug/223 release sem falhas/ignorados, 14 SDK reais em emuladores, 29 Auth/Rules, 85 Python; lint sem erros e APK. Evidencia em docs/evidence/2026-10-19-android-38.json. Marco 30 de 2026-10-18 preservado: 232 debug/170 release e 66 Python, exportacao local/contrato Android-Python e snapshots anteriores.
- **Primeiro item funcional pendente:** nenhum nas histórias 44–48; 49 segue planejada em #72 e fora deste bloco; adição do JSON local e recompilação continuam com o usuário; OAuth/Rules confirmados. A 31 nao e mais trabalho pendente do produto; foi retirada do escopo por solicitacao do usuario. A 32 nao sera implementada nesta entrega.
- **Proxima entrega demonstravel:** cinco notebooks 44–48 com amostra real nova, curvas x/y, quartis/orçamento e incerteza; 49 planejada separadamente. Jornada cloud anterior preservada.
- **Configuracao real restante:** adicionar google-services.json local e recompilar; usuário confirmou OAuth autorizado e Rules atualizadas em 20/10/2026. Nenhuma leitura administrativa remota pelo agente. Teste manual continua opcional (D-014).

Registro historico da 31 retirada do escopo (D-042), sem trabalho empirico pendente nesta entrega: [TRANSPORT_PROTOCOL.md](study/TRANSPORT_PROTOCOL.md),
84 testes Python e [evidencia](study/evidence/2026-10-19-transport/verification.json).
Piso final 100 participantes/2.000 tentativas/1.000 eventos mais precisao dos ICs;
registro historico, sem substituir por fixtures. `evaluate-ipsums` nunca habilita
integracao automaticamente; a 32 permanece fora desta entrega (D-042).

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
32 desativada/fora desta entrega (D-042), sem apresentar fixtures como evidencia de transporte.
