# Estrategia de testes e verificacoes

## Principio de qualidade

> A eficiencia de tokens e creditos e uma restricao operacional, nao um objetivo
> superior a qualidade. Otimize leitura, comandos e verificacoes redundantes, mas
> nunca deixe de testar uma regra critica apenas para economizar uso do Codex.

O objetivo e reduzir trabalho redundante, nao a cobertura das partes criticas. Bugs
evitaveis que comprometem regras centrais do Lorem custam mais do que testes
direcionados. Classifique primeiro o risco real da mudanca; se houver duvida entre
dois niveis, use o mais alto.

## Conclusao e avanco entre historias

Por orientacao do usuario em 2026-10-13 (D-014), testes manuais sao opcionais e nao
sao requisito para concluir uma historia ou iniciar a seguinte. Criterios
funcionais obrigatorios continuam exigindo evidencia automatizada proporcional ao
risco; falhas nesses criterios continuam bloqueando a conclusao.

Separe verificacoes automatizadas de conferencias em dispositivo. Uma conferencia
nao executada deve aparecer como `TESTE MANUAL OPCIONAL - NAO EXECUTADO`, sem marcar
que houve validacao humana e sem aguardar confirmacao para avancar. Essa regra
substitui a exigencia antiga de aguardar smoke tests manuais.

## Nivel 1 - alteracao simples ou visual

Exemplos: texto, layout, espacamento, navegacao simples, estado visual, botao e
mensagem de erro.

- Nao execute automaticamente toda a suite nem `lint + test + assembleDebug`.
- Faca somente a compilacao minima se houver risco de erro de compilacao.
- Verifique comportamento funcional relevante com os testes Compose/Robolectric
  existentes quando apropriado; nao crie teste que apenas replique texto ou layout.
- Pode fornecer um smoke test manual curto como conferencia visual opcional.
- Nunca afirme que executou um teste manual que depende do usuario; registre
  `TESTE MANUAL OPCIONAL - NAO EXECUTADO`, sem bloquear o avanco.

## Nivel 2 - logica isolada

Exemplos: filtros, deduplicacao, calculo, regra de dominio, selecao de problemas e
manipulacao de estados.

- Execute apenas os testes unitarios diretamente relacionados.
- Se uma regra relevante nao tiver teste adequado, crie um teste pequeno e especifico.
- Evite a suite inteira durante iteracoes quando o teste direcionado fornece a evidencia.

## Nivel 3 - integracao

Exemplos: Room, DataStore, API do Codeforces, persistencia, sincronizacao,
serializacao, Repository e mudancas que atravessam varias camadas.

- Execute os testes relevantes da integracao.
- Execute uma verificacao de compilacao ou build apropriada ao caminho modificado.
- Cubra falhas recuperaveis, idempotencia e limites externos quando aplicaveis.

## Nivel 4 - alto risco

Exemplos: Rating Lorem, maquina de estados do Ipsum, garantia de um unico Ipsum
ativo, prevencao de duplicidade, recomendacao, migracao de banco, arquitetura,
concorrencia e persistencia critica.

- Nao economize testes necessarios apenas para reduzir creditos.
- Combine testes unitarios, integracao e regressao dos invariantes afetados ate obter
  confianca real.
- Verifique caminhos de sucesso, falha, repeticao e recuperacao relevantes.

## Nivel 5 - marco do projeto

Priorize `test`, `lint` e `assembleDebug` completos:

- ao concluir uma historia relevante ou um bloco de historias;
- antes de um PR importante ou demonstracao;
- antes de considerar uma fase estavel;
- quando alteracoes amplas justificarem regressao completa.

Nao execute os tres automaticamente depois de cada pequena edicao. Um PR somente
documental nao exige build Android, lint ou testes do aplicativo.

## Regras para comandos

- Prefira teste direcionado ao global durante o desenvolvimento.
- Nao repita o mesmo comando que falhou sem analisar primeiro a causa.
- Nao refaca um build completo sem alteracao relevante desde o ultimo build.
- Evite comandos redundantes e logs enormes quando uma saida resumida basta.
- Em uma falha, isole primeiro o erro relevante; amplie a investigacao com evidencia.
- Nao abra emulador nem execute testes instrumentados automaticamente salvo quando
  forem realmente necessarios ao risco ou criterio de aceite.
- Registre exatamente os comandos executados e seus resultados.
- Economia operacional nunca justifica ignorar risco tecnico importante.

## Smoke tests manuais opcionais

Podem ser delegados, quando apropriado: abertura do app, presenca de botao ou texto,
navegacao, aparencia, mensagem de erro, digitacao basica, revelar tags, iniciar ou
encerrar um Ipsum pela interface, fechar/reabrir o app, conferir historico ou tela de
estatisticas.

Forneca passos numerados e resultados observaveis, por exemplo:

1. Abra a tela X.
2. Execute Y.
3. Aguarde Z.
4. Confirme que W aparece e que nao ocorre comportamento indevido.

Registre `TESTE MANUAL OPCIONAL - NAO EXECUTADO` enquanto nao houver execucao ou
confirmacao. Mantenha essa conferencia fora do checklist obrigatorio e avance sem
aguarda-la. Teste manual complementa, mas nao substitui teste automatizado de regra
critica; nunca registre uma conferencia nao executada como aprovada.

## Regras que permanecem prioritarias

Preserve ou crie testes automatizados para:

- identidade por `contestId + index`;
- exclusao de problema anteriormente tentado de um novo Ipsum;
- deduplicacao de submissoes e sincronizacao idempotente;
- WA seguido de AC;
- distincao entre submissoes anteriores e posteriores ao inicio;
- garantia de apenas um Ipsum ativo;
- rating nao recalculado duas vezes;
- AC posterior sem recalculo do rating original;
- filtros da recomendacao, fallback de rating e variedade de tags;
- calculo do Rating Lorem e consolidacao de faixa;
- estatisticas e persistencia critica;
- parsing das respostas `OK` e `FAILED` da API.

Nao remova esses testes por economia.

## Matriz minima do produto

- [x] **Regras puras:** identidade, deduplicacao, filtros, sorteio, fallback, tags,
  tempo, submissoes, rating, consolidacao e estatisticas.
- [x] **Integracao:** `OK`/`FAILED`, limite da API, falta de rede, atualizacao
  idempotente e persistencia.
- [x] **Jornada automatizada:** primeiro acesso, handle invalido, retomada, dica, WA
  seguido de AC, falha, AC posterior, historico e estatisticas, usando fakes isolados,
  Compose/Robolectric e persistencia real nos cenarios apropriados.

Conferencia em dispositivo e ensaio humano de demonstracao sao opcionais e nao
integram a matriz obrigatoria.

## Ambiente e comandos completos

### Planejamento e estudo Python

O planejamento inicial foi documental (Nivel 1). A implementacao 22-25 aplica
Niveis 2/3 para regras, API/cache e pipeline; build Android nao se aplica. Nao
confundir verificacao sintetica com coleta real ou evidencia de precisao.

Usar testes isolados em `study/` com fixtures sinteticas identificadas, sem rede
ou Gradle. Comando verificado na continuacao: `cd study && python -m unittest discover
-s tests -v` (51 testes com dependencias opcionais da 26 instaladas). Instalar o comando e reproduzir a jornada conforme
[study/README.md](../study/README.md); wheel/default config e CLI fora da pasta verificados. Cobertura obrigatoria: corte inclusivo/data ausente, oldRating e
joins, paginacao/429/retomada, 10/28/65 -> 10/18/37, primeiro AC/upsolve, salto,
inversao/intercalacao, censura versus coleta incompleta, ausencia de tentativa,
split por contest/grupo, transformacoes somente no treino, metricas de censura e
probabilidades/quantis. IPCW sem suporte deve produzir indisponibilidade explicita.

Piloto real 23 em 2026-10-18: duas aquisicoes completas e retomada/cache offline
com hashes identicos. Standings completo apenas com contestId (sem limite artificial
de page_size) e metadados malformados cobertos por testes. Status com pagina final
curta, joins oficiais/oldRating, hashes, funis e recusa de split insuficiente
conferidos nos dados reais. Cobertura valida zero por indices B1/B2 e E1/E2;
[evidencia](../study/evidence/2026-10-18-pilot-real.json) nao comprova precisao.

Historia 26: NumPy 2.3.5/SciPy 1.17.0 opcionais, fixados em requirements-model.lock.
51 testes em venv: gradientes numericos/analiticos e sinal censurado nas duas
familias, convergencia com gradiente (incluindo falso sucesso do otimizador), CDF/
quantis, suporte e extrapolacao, IPCW interno/censura desconhecida, folds por grupos,
rotulos do teste alterados sem mudar modelo e CLI train/predict. Core executado com
`python -S` sem site-packages; falta de dependencias AFT gera erro explicito.
Wheel 0.2.0 contem fontes/default exatos e CLI funciona fora da pasta.
Amostra real: 20 adquiridos/19 elegiveis, particoes 11/4/4 e treino 193.527 tentativas
com eventos e censuras. Teste reservado para 27; [evidencia](../study/evidence/2026-10-18-sample-aft.json).
Nenhum codigo Android alterado; build/lint Android nao se aplicam a esta continuacao.

Marco 27-28: 59 testes Python aprovados, incluindo exemplos calculados de MAE/RMSE/
vies, censura no limite, casos desconhecidos, G/pesos sem suporte, bootstrap de
clusters/mediana com multiplicidade, zeros/uns no log-loss, hashes/gate/modelo
congelado e reproducao de CSV/SVG/PNG. Rodar `cd study && .venv/bin/python -m
unittest discover -s tests -q`. `scripts/reproduce.py` usa pacote instalado em venv
novo e diretorio vazio, do cache/snapshot ao relatorio, com zero requisicoes.
[Relatorio](../study/results/2026-10-18/report/REPORT.md) e
[evidencia de reproducao](../study/evidence/2026-10-18-evaluation-report.json).
Nenhuma melhoria pedagogica ou transporte para Ipsums foi demonstrado.

Historia 29 e Niveis 3/4: datas/cache/migracao e recomendacao com historico antigo,
offline/fallback, ativo e rating preservados. Historias 30/31 exigem splits por
participante, ratings anteriores e tratamento de dica/pausas/ausencias. Historia
32 exige gate cientifico da 31, paridade numerica Python/Android, versoes, snapshots,
offline/fallback e regressao dos invariantes. Android test/lint/assembleDebug so
quando o respectivo codigo for implementado e o risco justificar, nos marcos.

### Aplicativo Android

O repositorio nao versiona JARs. Use Gradle 8.11 ou superior com Java 17. Os scripts
`./gradlew` e `gradlew.bat` baixam e validam o bootstrap oficial quando necessario.
Nos marcos de Nivel 5:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

No Windows sem WSL, execute os mesmos comandos em um terminal com o ambiente exigido.

## Evidencia do marco final em 2026-10-13

`./gradlew test lint assembleDebug --max-workers=2 --console=plain` passou:
147 testes debug e 116 release, zero falhas/erros; lint sem erros, com o aviso
preexistente `MissingApplicationIcon`. APK debug gerado.

As jornadas `CompleteAppJourneyTest` e `FullTrainingJourneyTest` usam Room/DataStore
reais e Codeforces falso apenas no escopo de teste. Cobrem primeiro acesso,
importacao obrigatoria, dica, WA/AC, resultado, nova recomendacao, pendencia/upsolve,
estatisticas, recuperacao e isolamento por perfil. A suite original da Historia 11
foi preservada. O roteiro humano de `DEMO.md` continua opcional.

## Marco 29-30 em 2026-10-18

232 testes debug/170 release, zero falhas; `test lint assembleDebug` concluido.
Lint sem erros, com avisos de versoes de dependencias e icone. JDK 17.0.20.1,
Gradle 8.11.1 e SDK 35 no cloud. Caches Java/Robolectric gravaveis e proxy foram
configurados em init script local; TLS usa as CAs do ambiente. Nao versionar SDK,
JDK, caches, APKs ou esse init script. Em ambiente Android normal usar o comando
Gradle do projeto. Evidencias: [29](evidence/2026-10-18-android-29.json) e
[30](evidence/2026-10-18-android-30.json).

Regras verificadas: corte UTC inclusivo/sem inferencia por id, origem ausente ou
conflitante, duas consultas no gate/falha parcial, cache offline/fallback, migracao
Room 9->12 com snapshots/rating/pendencias intactos, CF anterior imutavel e legado
nullable, exportacao por perfil/concorrencia e omissao do ativo. Encoder Android
com armazenamento real de teste produz fixture synthetic para o preparador Python.

66 testes Python; `python -S -m lorem_study prepare-ipsums --offline` sem inputs
funciona sem dependencias opcionais e registra n=0/not_collected/insufficient_data,
zero requisicoes e gate fechado. Particoes por participante, dedup/conflitos,
legados/rating futuro, dica/upsolve e exclusao integral D-026 cobertos sem rede.

TESTE MANUAL OPCIONAL - NAO EXECUTADO: seletor de arquivo local no dispositivo.
A falta de coorte real de Ipsums bloqueia a conclusao cientifica da 31, nao a
verificacao de software nem a passagem da 30 conforme D-014.

## Ferramenta pre-registrada da 31 em 2026-10-19

Nivel 2: 84 testes Python com dependencias opcionais, sem falhas; 16 testes
numericos e dois de core novos. Sem site-packages (`python -S`), 55 executados e
29 opcionais ignorados. Corte/exclusao D-026, preparo e Android permanecem com
suas evidencias anteriores; nenhuma fonte Android alterada nesta continuacao.

Cobertura nova: participantes inteiros/hash e checksum, mapeamento com peso igual
por participante somente em snapshots anteriores, correlacao versus deslocamento,
ECE IPCW reamostrado por participante, suporte 0-120, CI/precisao/dominio/mediana,
bins de delta para ratings continuos, selecao persistida antes do teste e
imutabilidade sob mudanca dos desfechos do teste. Fixture sintetica, mesmo com
gate numerico favoravel, nunca autoriza transporte/integracao. Sem suporte na
validacao o teste permanece fechado; sem coorte final nenhuma metrica e produzida.

Pacote/CLI 0.4.0 instalado fora da arvore de fontes, estado vazio reproduzivel e
avaliacao CF anterior byte a byte conferidos na [evidencia da 31](../study/evidence/2026-10-19-transport/verification.json).
Nenhum teste real de Ipsums executado: n=0. Historia 31 incompleta; 32 bloqueada.
TESTE MANUAL OPCIONAL - NAO EXECUTADO (D-014).


## Planejamento 33-38: Firebase e limite de 2h

Este planejamento e Nivel 1: revisar coerencia, links, dependencias, escopo do diff
e checklists pendentes. Nao exige testes/build Android ou provisionar Firebase.

Na implementacao Spark (D-039), Auth/Firestore/Rules e migracoes sao Niveis 3/4.
Testar Rules/SDK em Emulator Suite: isolamento uid+handle, provedor Google,
sem token, acesso cruzado, escrita forjada, 800 somente na criacao, lock por uid e
finalizacao unica sob retry/concorrencia. Sem Functions/Tasks/Admin SDK; SDK Android
real em emulador precisa confirmar criacao/idempotencia/uid. OAuth real exige
configuracao verificada; emulador nao
comprova integracao com o provedor.

A 37 exige relogio controlado e casos em 1h59m59s, 2h exatas e apos o prazo; AC
enviado antes/no/depois do limite, veredito atrasado/TESTING, consulta incompleta,
API indisponivel, jobs perdidos/duplicados/atrasados, offline/reboot/relogio civil
alterado e corrida com encerramento manual. Fechamento no prazo nao pode esperar
a rede; avaliacao final aguarda verificacao suficiente e e aplicada uma vez.

Na 38, verificar arquivo legado intacto, ativo migrado sem reinicio de prazo,
exclusoes antigas, nuvem existente sem reset, troca de contas e handles, workers
antigos, segundo dispositivo, paridade D-012 e exports sem identificadores diretos.
No marco integrado: suite Rules/SDK, `test`, `lint`, `assembleDebug`.
Conferencias humanas continuam opcionais D-014; nenhuma execucao real e alegada
por estes criterios planejados. Contratos detalhados em [FIREBASE.md](FIREBASE.md).

## Implementacao 35-37 em 19/10/2026

Nivel 4 durante o desenvolvimento; Nivel 5 no marco. Suite Rules usa somente
Auth/Firestore Emulator `demo-lorem-local`, Node 24/Java 21. Os testes Kotlin
opt-in exercitam `FirestoreTrainingRepository` real, com JDK 17 e SDK 35. Flags/
hosts dos emuladores sao inputs das tarefas de teste, para nao reutilizar resultado
ignorado depois de ativar o opt-in. Nenhum OAuth ou deploy real e alegado.

```bash
npm --prefix firebase test
./gradlew :app:testDebugUnitTest --tests 'dev.lorem.app.domain.cloud.*' --tests 'dev.lorem.app.ui.cloud.*' --tests 'dev.lorem.app.ui.CloudTrainingScreenTest' --tests 'dev.lorem.app.data.cloud.*' --tests 'dev.lorem.app.data.remote.CodeforcesApiRepositoryTest'
```

Com Auth/Firestore Emulator iniciados separadamente conforme FIREBASE_SETUP.md:

```bash
LOREM_FIREBASE_EMULATORS=true FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 ./gradlew test lint assembleDebug --max-workers=2 --console=plain
./gradlew :app:testDebugUnitTest --tests 'dev.lorem.app.FirebaseEntryTest' assembleDebug -Plorem.firebaseAuthEnabled=true --max-workers=2 --console=plain
```

Cobertura: criacao concorrente e canonica em 800 para CF alto/unrated; dois uids
com mesmo handle e dois dispositivos/handles concorrentes; snapshots, dica,
marker de problema usado, resposta perdida/retry, rating/consolidacao atomicos e
versao unica; SDK/cache offline sem novo inicio; fronteiras de 2h e precisao
submilissegundo; falha da API, dados malformados/consulta incompleta e TESTING;
AC elegivel descoberto tarde, timeout sem AC e upsolve sem recalculo. Relogio
monotonic/boot, job unico/reparo/uid/versao e sigilo/fechamento UI tambem cobertos.
O teste de cache usa LRU em memoria e INSTRUMENTATION_TEST para manter o looper
principal ativo nas continuacoes do SDK. O app usa o cache persistente padrao do
Firestore. Fixtures de timeout usam somente REST local com bypass de Rules do emulador;
nao sao credenciais administrativas nem evidencia de treinos reais da 31.

Resultados e hashes: [evidencia 35-37](evidence/2026-10-19-firebase-35-37.json).
TESTE MANUAL OPCIONAL - NAO EXECUTADO (D-014). Ativacao padrao/migracao ficam na 38.

## Evidencia historica da base 33/34 antes de Spark em 19/10/2026

- Backend: `npm --prefix firebase/functions test`, Node 22, Java 21, seis testes
  sem falhas em Auth/Firestore/Functions Emulator. Chamadas reais ao endpoint
  callable local cobrem provedor, uid forjado e criacao concorrente/idempotente;
  Rules cobrem leitura cruzada, escrita direta e acessos anonimos/senha.
- Android: sete testes de sessao, tres ViewModel, tres Compose, tres do decoder Google ID e guard de worker
  legado, seguidos de `./gradlew test lint assembleDebug --max-workers=2 --console=plain`.
  250 debug (249 executados/1 opt-in ignorado por flag false) e 184 release, sem
  falhas; lint sem erros e APK. Conferir avisos/evidencia antes de reproduzir.
- Smoke com flag true: `./gradlew :app:testDebugUnitTest -Plorem.firebaseAuthEnabled=true
  --tests 'dev.lorem.app.FirebaseEntryTest' --max-workers=2 --console=plain`, 1/1,
  sem falhas/ignorados. Perfil legado nao aparece e configuracao ausente bloqueia
  login. O APK foi gerado pelo build padrao; a flag e usada somente na execucao isolada do smoke.
- `npm --prefix firebase/functions run check-config` sem JSON retorna codigo 1
  e explica a configuracao ausente, como esperado.

[Registro verificavel](evidence/2026-10-19-firebase-33-34.json) e
[guia de reproducao](FIREBASE_SETUP.md). Nenhum login Google/OAuth, projeto ou
deploy real foi comprovado nessa execucao. Depois, o usuario criou o projeto,
escolheu nam5 para Firestore (D-038) e forneceu o JSON Android. A evidencia
historica foi preservada. Historias 33/34 parciais. TESTE MANUAL OPCIONAL - NAO EXECUTADO (D-014).

## Evidencia historica da configuracao Android recebida em 19/10/2026

Nivel 3: JSON instalado em arquivo ignorado; alias test e registro do projeto
tambem locais/ignorados, sem alias de producao. Package dev.lorem.app, appId,
cliente OAuth Web e SHA-1 Android do JSON conferidos contra o APK assinado.
`check-config` passou, e o build executou `processDebugGoogleServices`.

O smoke `FirebaseEntryTest` passou a cobrir a entrada com ou sem recursos Google
Services: o perfil legado permanece oculto, e o botao fica habilitado somente
no cenario configurado. Executado com JSON real e flag true junto dos testes de
sessao, ViewModel, Compose e decoder Google ID: **17 testes sem falhas/ignorados**.
`lintDebug` sem erros (58 avisos de versoes/icone), `assembleDebug` concluido com
`FIREBASE_AUTH_ENABLED=true` e compilacao TypeScript concluida. Comando completo,
hash do APK, suites e hashes dos fontes em
[evidencia da configuracao](evidence/2026-10-19-firebase-android-config.json).

`firebase projects:list --json --non-interactive` retornou erro de autenticacao;
nao ha conta administrativa conectada nesta CLI. Nao executado deploy, consulta
administrativa do banco/provedores nem login Google real. Estado remoto e
assinaturas de release/Play continuam pendentes. TESTE MANUAL OPCIONAL - NAO
EXECUTADO (D-014).

## Historias 33/34 refeitas para Spark (D-039), 19/10/2026

Niveis 3/4 e marco: sem Functions/Tasks/Admin SDK de runtime. SDK Android executa
transacao online sob Rules create-only para metadados; novas Rules nao habilitam
escritas de progresso. Permissao negada significa configuracao/Rules; nao confundir
com provedor errado. Cota Spark, rede e sessao invalida nao liberam destino privado.

- `npm --prefix firebase ci` com Node 24.19.0; lockfile somente de tooling, sem
  runtime Node implantado. `npm --prefix firebase test`: dez testes sem falhas em
  Auth/Firestore Emulator, Java 21.0.12.1, loopback/projeto demo-lorem-local.
  Criacao concorrente idempotente, tokens Google/senha emulados, restauracao em
  outro cliente, isolamento, esquema exato, timestamps e batch forjado cobertos.
- SDK Kotlin de producao contra emuladores: 23 testes direcionados Android com
  flag true, sem falhas/ignorados, incluindo dois da suite
  FirestoreAccountBootstrapEmulatorTest. Seis transacoes concorrentes criam uma
  conta sem sobrescrever createdAt; uid divergente nao inicia escrita. Demais
  suites cobrem oito cenarios de sessao, tres de erro SDK, tres ViewModel, tres
  Compose, tres decoder Google ID e um smoke MainActivity com JSON real.
- `./gradlew test lint assembleDebug --max-workers=2 --console=plain`, JDK 17:
  256 debug (253 executados/3 opt-in ignorados) e 188 release, zero falhas/erros;
  lint sem erros (58 avisos de versoes/icone) e APK. Os tres ignorados (entrada
  preview e dois SDK/emulador) passaram na execucao direcionada acima.
- `npm --prefix firebase run check-config` passou com o JSON local ignorado.
  APK preview gerado com flag true separadamente; runtime sem firebase-functions.
  A migracao Firestore trouxe Guava somente em runtime e um placeholder vazio
  ListenableFuture ao compile: dependencia explicita da mesma versao Guava ja
  usada pelo SDK restabelece a API WorkManager sem duplicar classes de runtime.

[Evidencia Spark](evidence/2026-10-19-firebase-spark-33-34.json) registra comandos,
suites, hashes e certificado. [Guia atualizado](FIREBASE_SETUP.md).
`firebase projects:list --json --non-interactive` continua sem autenticacao;
sem publicacao de Rules, consulta administrativa de provedor/banco/plano ou OAuth
Google real. Evidencias Functions/JSON anteriores intactas, sem reapresenta-las
como teste Spark. TESTE MANUAL OPCIONAL - NAO EXECUTADO (D-014).

## Publicacao das Rules confirmada pelo usuario, 19/10/2026

Atualizacao documental de Nivel 1, depois da integracao do PR 66 em main. O usuario
confirmou a publicacao pelo Console apos receber o arquivo Spark verificado.
[Registro separado](evidence/2026-10-19-firebase-rules-publication.json) identifica a
fonte da confirmacao e a referencia/hash fornecida; nao equivale a leitura do
ruleset ativo pelo agente ou login Google real. JSONs de testes anteriores intactos.
Sem mudanca de runtime/Rules; nao repetir Gradle, lint, APK ou emuladores. Verificar
diff, links, cinco sessoes e estado das issues. Somente Google no Auth real ainda
aguarda confirmacao; conferencia humana do app permanece opcional (D-014).
