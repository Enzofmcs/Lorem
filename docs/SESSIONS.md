# Registro curto de sessoes

Mantenha somente as cinco sessoes mais recentes. Cada entrada deve informar data,
historia ou tarefa, arquivos principais, implementacao, verificacoes automatizadas,
testes manuais pendentes ou confirmados, proximo passo e bloqueios. O Git preserva o
historico removido desta janela operacional.

| Data | Historia/tarefa | Arquivos principais | Implementacao | Verificacoes automatizadas | Teste manual | Proximo passo e bloqueios |
|---|---|---|---|---|---|---|
| 2026-09-22 | Historia 02 - persistencia estruturada do historico | `LoremDatabase.kt`, `ProblemHistoryEntity.kt`, `ProblemHistoryDao.kt`, `LocalLoremRepository.kt`, `ProblemHistoryPersistenceTest.kt`, `AppContainer.kt` | Room passou a armazenar o historico com chave composta `contestId + index`; o repositorio observa e grava importacoes por upsert, enquanto o perfil continua no DataStore; o container compoe os dois armazenamentos. | Nivel 3: verificacao de diff passou. O teste direcionado foi preparado para reabrir o banco e verificar entradas com e sem AC e idempotencia, mas sua execucao ficou bloqueada porque o ambiente nao possui Android SDK configurado. | Nao aplicavel: persistencia coberta por teste automatizado, pendente de execucao quando houver SDK. | Configurar `ANDROID_HOME`, executar testes direcionados e `assembleDebug`, e somente entao marcar os criterios de persistencia/idempotencia; BLOQUEADO: Android SDK ausente. |
| 2026-09-22 | Historia 02 - alinhamento do estado atual | `BuildProblemHistory.kt`, `BuildProblemHistoryTest.kt`, `ROADMAP.md`, `SESSIONS.md` | O estado atual do roadmap foi alinhado ao `AGENTS.md` e ao recorte implementado, que registra por `ProblemId` tanto problemas com AC quanto tentativas sem AC. | Nivel 2: `BuildProblemHistoryTest` direcionado passou e comprovou AC, tentativa sem AC, WA seguido de AC, veredito ausente e identidade por concurso e indice. | Nao aplicavel: regra de dominio isolada coberta por teste unitario. | Persistir o historico e tornar a atualizacao idempotente; sem bloqueio. |
| 2026-09-22 | Historia 02 - classificacao do historico | `ProblemHistory.kt`, `BuildProblemHistory.kt`, `BuildProblemHistoryTest.kt`, `ROADMAP.md` | Submissoes agrupadas exclusivamente por `ProblemId`; todo grupo e tentado e somente grupos com algum `OK` sao resolvidos. | Nivel 2: teste unitario direcionado com cinco cenarios de classificacao e identidade. | Nao aplicavel: regra de dominio isolada coberta por teste unitario. | Persistir o historico e tornar a atualizacao idempotente; sem bloqueio. |
| 2026-09-22 | Historia 02 - consulta do historico | `CodeforcesSubmission.kt`, `CodeforcesRepository.kt`, `CodeforcesApiRepository.kt`, doubles e testes | Contrato tipado e modelo de submissao adicionados; `user.status` consulta todas as paginas pelo gate, preserva submissoes repetidas e rejeita resultados parciais em falhas. | Nivel 3: testes direcionados do repositorio remoto e do fake, seguidos de `assembleDebug`. | Nao aplicavel: mudanca de integracao coberta por doubles deterministas. | Classificar AC e tentativas sem AC, deduplicar por problema e persistir o historico; sem bloqueio. |
| 2026-09-22 | Historia 01 - confirmacao manual | `AGENTS.md`, `ROADMAP.md`, `SESSIONS.md` | Historia 01 concluida e foco operacional avancado para a Historia 02. | Nao aplicavel: somente registro da evidencia manual e atualizacao documental. | Confirmado pelo usuario: o smoke test da troca de handle passou em dispositivo. | Iniciar a importacao do historico com `user.status`; sem bloqueio. |

## Registro anterior preservado

Em 2026-09-18, a sessao de planejamento criou o guia inicial, comparou o escopo com
as decisoes do produto e definiu como proximo passo iniciar a Historia 00. O detalhe
integral continua disponivel no historico do Git.
