# Lorem

Lorem é um aplicativo Android de treino individual para programação competitiva. O produto recomenda exercícios inéditos do Codeforces adequados ao nível do usuário e acompanha sua evolução.

Um **Ipsum** é a tentativa oficial e cronometrada de resolver um problema recomendado. Recomendação, cronômetro persistente, detecção de submissões, resultado, Rating Lorem, histórico, upsolve, estatísticas, variedade e consolidação de faixa já estão implementados.

> O [`AGENTS.md`](AGENTS.md) é o guia operacional compacto. A fonte de verdade detalhada está dividida por assunto em [`docs/`](docs/README.md). Este README apresenta apenas a visão técnica de entrada.

## Histórias 40–43

Jornada Firebase recuperada: abas Início/Histórico/Estatísticas, treino e resultado
persistido, filtros/gráficos, tema/conta/handles em Configurações e arquivo local
separado. Preparação por etapas com retomada e cobertura incremental verificada;
AC posterior automático em lotes limitados, sem alterar a avaliação original.

332 debug/237 release sem falhas ou ignorados, 15 SDK reais em emuladores, 34 Auth/Rules; lint sem erros (58 avisos) e APK.
[Paridade e custo de rede](docs/CLOUD_EXPERIENCE.md) ·
[Compatibilidade e operação Firebase](docs/FIREBASE_OPERATION.md) ·
[Evidência](docs/evidence/2026-10-20-cloud-40-43.json).
OAuth autorizado e Rules atualizadas foram confirmados pelo usuário em 20/10/2026.
Resta adicionar `app/google-services.json` localmente e recompilar.
[Registro da confirmação](docs/evidence/2026-10-20-firebase-oauth-rules-confirmation.json); nenhum deploy ou login real pelo agente. D-012 permanece vigente, time-curve-v1 desabilitada.

## Estado atual

A base Android de módulo único implementa as Histórias 00–21 e 29–30:

- Kotlin, Jetpack Compose e Material 3;
- navegação que restaura o Ipsum ativo;
- escolha da tela inicial a partir de um perfil local validado;
- persistência atômica do perfil Codeforces com DataStore;
- consulta real ao `user.info`, centralizada por um limitador de requisições;
- catálogo e histórico locais em Room, com recomendação inédita e fallback auditável;
- persistência de um único Ipsum ativo antes da navegação;
- encerramento, resultado e atualização única do Rating Lorem;
- histórico e detecção de AC posterior, preservando a avaliação original;
- estatísticas de AC, dica, tempo, erros e desempenho por rating e tag, com evolução do rating persistido;
- recomendação por necessidade e recência de tags, com fatores persistidos;
- consolidação permanente e requisitos objetivos para avançar de faixa;
- preparação automática e manutenção com WorkManager, com progresso, retomada e retries;
- acompanhamento de submissões em RESUMED e no worker, com estado por conjunto de dados;
- importação incremental e proteção por versão do vínculo do perfil;
- atualização antes da recomendação e uso de cache após a primeira importação;
- data oficial de origem verificada nas novas recomendações, com cache local e históricos preservados;
- snapshots anteriores e exportação local de tentativas encerradas para o estudo, sem preencher dados históricos ausentes;
- injeção manual e estado de UI exposto por `ViewModel`/`StateFlow`.

As Histórias 10 e 11 foram concluídas após a confirmação do usuário de todos os testes manuais em dispositivo em 13/10/2026. As definições dos indicadores e o conjunto de referência estão no [roadmap](docs/ROADMAP.md#historia-11---estatisticas-confiaveis).

## Login e perfis Firebase

Solicitacao de 19/10/2026, refeita para **Spark (D-039)**: **33/34 implementadas**,
com login Google e bootstrap transacional direto no Firestore; sem Functions,
Cloud Tasks, billing ou backend externo como requisito. Rules preservam isolamento
por uid e transicoes atomicas entre tentativas, rating e lock.
JSON/OAuth Web/SHA-1 debug validados localmente; Firestore escolhido nam5 (US).
Usuario confirmou a publicacao das Rules combinadas 35-37 apos a PR 69;
[registro da confirmacao](docs/evidence/2026-10-19-firebase-rules-35-37-publication.json), sem leitura administrativa pelo agente.
Google como unico provedor confirmado pelo usuario (D-042). [Configuracao Spark](docs/FIREBASE_SETUP.md).

A historia 38 torna o fluxo Firebase padrao; o build continua reproduzivel sem JSON e mostra configuracao ausente.
**35-37 implementadas:** adicionar/selecionar handles, 800 por vinculo novo,
progresso Firestore independente, ciclo do Ipsum com rating unico, historico,
estatisticas, upsolve e prazo absoluto 2h. A 38 preserva dados legados em arquivo local somente leitura, sem importar saldos para a nuvem.
Android verifica Codeforces/calcula rating e sincroniza por SDK/Rules;
WorkManager pode executar com app fechado, mas atrasar. O prazo nao aumenta
por atraso e o processamento pode aguardar conectividade/sessao. Rules nao
comprovam AC de cliente modificado; nao prometer verificacao independente no servidor.

[Historias e ordem](docs/ROADMAP.md#novo-bloco-contas-firebase-e-prazo-maximo-do-ipsum-33-38) ·
[Contratos, configuracao, offline e migracao](docs/FIREBASE.md).
A historia 31 foi retirada do escopo pelo usuario; a 32 fica fora desta entrega e as previsoes permanecem desativadas.

Integracao da PR 69 preserva a historia 35 da main: chaves `cf_`, selecao Firestore versionada, cache Room 13 e identidades por uid/binding/selecao. Treino atualiza `loremRating`/`progressVersion` e remove o lock atomicamente. [Evidencia integrada](docs/evidence/2026-10-19-firebase-merge-69.json).

## Escopo resumido

O Lorem tem perfil local associado a um handle público, catálogo e histórico do Codeforces, recomendação inédita, execução de um Ipsum, resultados, pendências, rating interno, estatísticas, variedade e consolidação. O preview 33-37 acrescenta login/progresso Firebase e limite de 2h; a 38 acrescenta migracao/arquivo local e ativacao padrao. Nao fazem parte do escopo recursos sociais, backend proprio fora desse bloco, editor de codigo ou gamificacao. Consulte [`docs/SPEC.md`](docs/SPEC.md) para as regras completas.

## Arquitetura inicial

- **`domain`**: modelos Kotlin e contratos independentes da interface Android;
- **`data`**: persistência DataStore, fakes determinísticos e ponto de integração remota;
- **`ui`**: composição raiz, `ViewModel`, tema, navegação e telas Compose;
- **`AppContainer`**: composição manual das dependências.

Composables recebem estado e ações: eles não acessam DataStore, Room ou serviços externos diretamente. Room preserva catálogo, histórico e Ipsums; DataStore mantém configurações pequenas.

## Requisitos de ambiente

- Android Studio com suporte a projetos Gradle Kotlin DSL;
- JDK 17;
- Android SDK 35;
- um emulador ou dispositivo com Android 8.0 (API 26) ou superior.

O repositório não versiona arquivos `.jar`. Na primeira execução, os scripts `gradlew`
e `gradlew.bat` baixam o bootstrap oficial do Gradle 8.11.1 para o cache do usuário e
validam seu SHA-256 antes de executá-lo. Por isso, a primeira execução requer internet;
as seguintes reutilizam o arquivo validado em `~/.gradle/wrapper/lorem`.

## Abrir no Android Studio

1. Abra o diretório raiz do repositório no Android Studio.
2. Aguarde a sincronização do Gradle e a instalação do SDK solicitada pela IDE.
3. Selecione a configuração `app` e um dispositivo compatível.
4. Execute o aplicativo.

Sem perfil salvo, o Lorem começa em **Configuração**. Valide um handle público do Codeforces para abrir **Início**; na próxima inicialização, o perfil restaurado faz **Início** ser o destino inicial.

## Verificações

Na raiz do projeto:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

## Estrutura de diretórios

```text
.
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/dev/lorem/app/
│       │   │   ├── data/
│       │   │   ├── domain/
│       │   │   └── ui/
│       │   │       ├── navigation/
│       │   │       └── screens/
│       │   ├── res/
│       │   └── AndroidManifest.xml
│       └── test/
├── gradle/
├── AGENTS.md
├── docs/
├── build.gradle.kts
└── settings.gradle.kts
```

## Entrega atual

As **Histórias 00–21 estão implementadas**. Conectar um perfil agenda a importação
completa do histórico e catálogo. WorkManager mantém histórico/pendências a cada
15 minutos desejados e catálogo/metadados a cada 24 horas, sujeito ao Android.
Reabrir o app preserva a agenda e recupera dados ausentes ou vencidos.

A preparação mostra progresso por etapa e retoma importações interrompidas. O
primeiro Ipsum exige histórico importado; depois, falha de atualização permite
treinar com cache indicado na tela. Rating Lorem, consolidação e avaliações
originais permanecem preservados. O Ipsum consulta submissões ao entrar/retomar
e a cada 15 segundos em RESUMED, com backoff nas falhas. O worker também verifica
o ativo; o resultado é apresentado ao retornar à sua tela.

A identidade visual usa azul profundo e verde, com temas Sistema/Claro/Escuro
persistidos em Configurações. As abas Início, Histórico e Estatísticas preservam
estado e rolagem; Configurações ficam no cabeçalho. Ipsum e Resultado têm navegação
dedicada com voltar, mantendo a tentativa ativa ao sair da tela.

O primeiro acesso usa Conectar Codeforces e validação inline. O Início destaca
Rating Lorem, Iniciar/Continuar Ipsum, resumo local e último resultado. Perfil, tema
e recuperação por etapa ficam em Configurações; o status de atualização permanece
discreto no Início. A faixa consolidada preserva seu marco e permite expandir os
requisitos da próxima faixa.

O treino destaca tempo decorrido e Abrir no Codeforces; dica e encerramento mantêm
confirmação. O resultado mostra avaliação persistida, variação, tempo, dica e erros,
com AC posterior separado. A leitura acessível do timer é feita sob demanda.

O histórico usa cards cronológicos, filtros avançados e estado preservado entre
abas. AC posterior tem data própria e mantém a avaliação original. As estatísticas
apresentam curva de rating persistido e barras por rating/tag com valores textuais,
frações e amostras; ausência de amostra é distinta de zero.

Validação final: **217 testes debug e 155 release sem falhas**, lint sem erros
(apenas aviso preexistente de ícone) e `assembleDebug` concluído. O APK está em
`app/build/outputs/apk/debug/app-debug.apk`.

Consulte o [roadmap](docs/ROADMAP.md) e o [roteiro de demonstração](docs/DEMO.md).
**TESTE MANUAL OPCIONAL - NAO EXECUTADO** nesta sessão, conforme D-014.

## Próximas histórias

O planejamento de 18/10/2026 acrescenta **22–28 — Estudo quantitativo em Python**:
coleta e preparo auditáveis, estimativa de tempo até AC, probabilidades para um
orçamento explícito, avaliação e relatório reproduzível. São **24 horas estimadas**
para uma primeira versão. As Histórias 22–28 estão implementadas; a suite atual tem 66 testes Python, incluindo o preparo da 30.
A amostra tem 19 contests elegíveis e 274.073 tentativas válidas. A avaliação de
35.367 tentativas em quatro contests reservados favoreceu a referência simples
por delta em relação ao AFT: MAE 15,1 vs 15,9 min nos resolvidos, com denominadores
e ICs documentados. O AFT foi preservado sem retuning. D-012 continua no app.
[Relatório, tabelas e gráficos reais](study/results/2026-10-18/report/REPORT.md).
O estudo ainda não valida transporte para Ipsums nem melhoria pedagógica. Contests
especiais e com versões permanecem integralmente excluídos.
Consulte os [comandos Python](study/README.md) e o [contrato de dados](study/DATA_DICTIONARY.md).

**29–30 concluídas:** corte verificável desde 01/07/2020 em toda recomendação/fallback,
metadados persistidos, CF conhecido antes do início e exportação local de Ipsums
encerrados em Configurações. Legados/ativos/avaliações preservados; D-012 vigente.
[Protocolo e comandos de Ipsums](study/IPSUM_PROTOCOL.md).

**31–32 bloqueadas:** nenhum export real foi fornecido. Precisamos de uma coorte
real para avaliar os ratings e as previsões antes da integração. O piso de preparo
não comprova precisão. A [ferramenta e o pré-registro da 31](study/TRANSPORT_PROTOCOL.md)
já definem a comparação e os limites antes do teste real. O estudo não inclui comparação entre épocas/correção temporal.
[Progresso e critérios](docs/ROADMAP.md) · [Evidência Android](docs/evidence/2026-10-18-android-30.json).

O planejamento de 13/10/2026 acrescenta **15–17 — Sincronização automática** e
**18–21 — Redesign**. As Histórias 00–14 e suas evidências permanecem preservadas;
as Histórias 15–17 estão implementadas e verificadas: coordenador compartilhado,
importação incremental retomável, proteção por vínculo, WorkManager e acompanhamento
automático. A base visual e a navegação da História 18 também estão verificadas.
Configuração/Início (19) e Ipsum/Resultado (20) estão implementados e verificados.
Histórico/Estatísticas (21) também estão verificados, concluindo o planejamento atual.

A proposta combina WorkManager em segundo plano, consulta frequente durante o
Ipsum visível e temas claro/escuro em azul profundo e verde. Consulte o
[roadmap](docs/ROADMAP.md#historia-15---base-segura-para-sincronizacao-automatica),
[estratégia de sincronização](docs/SYNC.md) e [direção visual](docs/DESIGN.md).

A 38 acrescenta exclusoes persistidas por uid+handle: apenas IDs publicos de
problemas sao sincronizados, sem avaliacoes/saldos do arquivo local. Publicar as
Rules atuais antes de instalar esta versao; a confirmacao anterior cobre 35-37.
O arquivo antigo e consultavel mesmo sem login. Exports sao locais e opcionais;
o protocolo cloud v2 nao e misturado ao protocolo historico do estudo.

[Verificacao da historia 38](docs/evidence/2026-10-19-android-38.json): 310 debug, 223 release, 29 Auth/Rules e 85 Python, sem falhas; lint sem erros e APK. Teste manual opcional nao executado.
