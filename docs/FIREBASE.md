# Login, perfis Firebase e limite do Ipsum

Planejamento solicitado em **19/10/2026**. **33/34 parciais:** base Firebase e
login Android implementados/verificados em testes. Depois da verificacao, o
usuario informou projeto criado e escolheu Firestore nam5 (US); Functions
continuam em us-central1. ID/JSON Android, Google OAuth e deploy reais pendentes; destino
autenticado preparado para a 35, sem cadastro de handles ou treino em nuvem.
**35-38 ainda planejadas.** [Configuracao e estado real](FIREBASE_SETUP.md).
Historias **33-38** em [ROADMAP.md](ROADMAP.md). Base do planejamento:
`2092c2f299e1590d791f93e59bdf5e60189bc707` (`main`).

Ordem de execucao: [Historia 33](https://github.com/Enzofmcs/Lorem/issues/60) -> [Historia 34](https://github.com/Enzofmcs/Lorem/issues/61) -> [Historia 35](https://github.com/Enzofmcs/Lorem/issues/62) -> [Historia 36](https://github.com/Enzofmcs/Lorem/issues/63) -> [Historia 37](https://github.com/Enzofmcs/Lorem/issues/64) -> [Historia 38](https://github.com/Enzofmcs/Lorem/issues/65).

## Requisitos e limites do bloco

- Login exclusivamente com Google pelo Firebase Authentication.
- Firestore para conta, vinculos de handles, Rating Lorem e tentativas oficiais.
- Cada par `(Firebase uid, handle normalizado)` tem progresso independente.
  Duas contas Google podem cadastrar o mesmo handle; nao existe reserva global.
- Todo vinculo novo inicia com **800**, mesmo que o rating oficial CF seja maior.
  Login, sincronizacao, troca de handle e reinstalacao nao reiniciam progresso.
- Ipsum dura no maximo **7.200.000 ms (2 horas corridas)**. Sem AC valido nessa
  janela, encerra automaticamente e recebe a avaliacao de tentativa sem AC.

O cadastro usa `user.info` para validar existencia e obter a grafia do handle.
Isso nao prova posse. Conforme a premissa aceita pelo usuario, nao adicionar
desafio de verificacao, OAuth Codeforces nem exclusividade; as submissoes publicas
do handle continuam sendo o sinal do treino. Login Google identifica a conta
Lorem e nao autentica o proprietario do handle Codeforces.

## Identidade, persistencia e rating

Contrato proposto para implementacao, ajustavel nos nomes sem alterar isolamento:

| Caminho Firestore | Conteudo e identidade |
|---|---|
| `users/{uid}` | Metadados minimos da conta e criacao pelo servidor. |
| `users/{uid}/handles/{handleKey}` | Handle canonico/normalizado, rating atual, consolidacao, versao do progresso e datas do servidor. |
| `users/{uid}/handles/{handleKey}/ipsums/{ipsumId}` | UUID estavel, problema, snapshots, horarios, dica, submissoes verificadas, resultado e versoes das regras. |
| `users/{uid}/runtime/current` | Ponteiro/lock transacional do unico Ipsum ativo da conta, inclusive entre handles e dispositivos. |

`handleKey` e a normalizacao consistente da grafia canonica retornada pelo CF
(trim/lowercase, Locale.ROOT), codificada de forma segura para o caminho. A mesma
normalizacao tambem vale no cache e no backend. No mesmo uid, adicionar novamente
o mesmo handle devolve o vinculo existente. Sob outro uid, cria outro vinculo
independente em 800. Remover/reinserir um vinculo nao e um mecanismo de reset;
exclusao definitiva de conta nao faz parte deste bloco.

O backend cria o documento em transacao com rating 800, somente se ainda nao existir.
O cliente nao escolhe nem sobrescreve o rating inicial. 800 e o ponto inicial,
nao um novo piso permanente: a formula D-012 e a consolidacao D-015 permanecem.
O rating CF e metadado separado, inclusive para snapshots anteriores ao treino.

Auth valida o token Google/Firebase e deriva o uid do token, nunca de um campo
enviado pelo cliente. Rules permitem ler somente o proprio caminho e negam
escritas diretas de rating, resultados, relogios e lock. Functions com Admin SDK
tambem validam uid, ownership do caminho, estado e comandos, pois Admin ignora Rules.
O termo ownership aqui significa dono do documento Lorem, nao posse do handle CF.

Room continua como cache e arquivo legado; DataStore guarda configuracoes e
selecao. Todos os dados pessoais, leitores, workers e callbacks passam a usar
`uid + handleKey + versao do vinculo`. O catalogo publico pode continuar compartilhado.
Trocar conta cancela observadores/trabalhos antigos e retira seus dados da interface.
Trocar handle restaura seu proprio progresso. Com Ipsum ativo, bloquear a troca
de handle ate encerra-lo; sair da conta nao cancela nem prolonga o prazo no servidor.

## Ciclo oficial e autoridade do servidor

Firebase Functions expoe os comandos minimos de criar vinculo, iniciar Ipsum,
revelar dica e encerrar/verificar. Comandos usam chave de idempotencia; inicio
grava tentativa e lock juntos, com hora do servidor e snapshot do rating vigente.
O backend verifica candidato/ineditismo e cobertura do historico publico antes de
aceitar o inicio; nao confia em rating, resultado ou horarios do aparelho.
Reutilizar o criterio da 29 e a importacao obrigatoria D-017.

Submissoes oficiais sao consultadas pelo backend no Codeforces. Reutilizar o gate,
deduplicacao e politica de retries; coordenar chamadas concorrentes do backend
para respeitar uma requisicao a cada dois segundos, evitando polling duplicado
do app para a mesma tentativa. Nao aceitar `accepted=true` ou veredito do cliente.
O backend aplica a mesma formula pura D-012, com fixtures de paridade Android.
Nao embarcar Python nem integrar previsoes da 32.

Resultado, delta, rating final, versao do progresso e liberacao do lock sao
persistidos atomicamente. Retry, duplo toque, worker e segundo dispositivo nao
podem aplicar outro delta. Sem conexao, leitura do cache e encerramento visual
continuam; iniciar outro Ipsum e confirmar rating exigem servidor acessivel.
Nao implementar fila de novos treinos offline nem sincronizacao generica de conflitos.

## Prazo absoluto de duas horas

No inicio persistir `startedAt` do servidor, `expiresAt = startedAt + 2h` e
`timingRuleVersion`. O prazo nao pausa ao minimizar, reiniciar, sair da conta,
trocar de dispositivo ou perder conexao. Tempo esperado da D-012 e outra medida
(30-120 min) e nao substitui o prazo maximo.

Proposta para desempate de fronteira: submissao com `creationTimeSeconds` do CF
convertido para a mesma escala UTC e elegivel se `startedAt <= createdAt <= expiresAt`.
Antes do inicio nao conta; depois do limite e upsolve. No instante do limite,
AC elegivel prevalece sobre timeout, independentemente da ordem dos callbacks.
Comparar com a precisao real dos timestamps, sem arredondar o inicio para tras.

Ao atingir o prazo, a tentativa deixa de aceitar acoes de treino. Sem AC elegivel,
persistir encerramento em `expiresAt`, motivo tecnico `TIME_LIMIT_EXCEEDED` e
estado final sem AC/pendente; usar delta sem AC da D-012 uma unica vez. `processedAt`
registra processamento tardio separadamente; duracao contabilizada nao supera 2h.
AC apos o limite muda somente a situacao de upsolve, preservando a avaliacao.

### App fechado, falha de rede e veredito atrasado

- Agendar Cloud Tasks para chamar uma Function em `expiresAt`. Usar um job de
  recuperacao para tentativas vencidas, inclusive falha entre criar o Ipsum e
  enfileirar a tarefa; repetir tarefa/varredura e seguro pela idempotencia.
- Filas e sistemas operacionais podem executar depois do horario. O prazo e uma
  regra de validade: toda leitura/comando considera vencido quando `now >= expiresAt`,
  mesmo antes da materializacao pelo job. O encerramento efetivo permanece no
  prazo, nunca no horario tardio do worker. Nao prometer execucao fisica exata.
- No Android, contador derivado do prazo, hora de servidor sincronizada e referencia
  monotonic durante o processo; mudanca do relogio civil nao estende a tentativa.
  Reavaliar no resume/reboot. Sem referencia confiavel apos reinicio offline,
  bloquear continuacao e reconciliar com o servidor; nunca conceder mais 2h.
- O app fecha visualmente no limite mesmo sem rede. WorkManager auxilia retomada,
  mas nao e o mecanismo de precisao de 2h. Firestore TTL nao executa essa regra
  e nao deve apagar o historico.
- Antes de finalizar como sem AC, obter consulta completa da janela relevante e
  esperar vereditos ainda `TESTING`. Falha da API ou consulta incompleta nao prova
  ausencia de AC. Usar estado separado `CLOSED_AWAITING_VERIFICATION`: o treino
  esta fechado no prazo, resultado/rating aguardam verificacao automatica com retry.
  Nao iniciar proximo Ipsum ate fixar o rating anterior; informar esse estado na UI.
- AC enviado dentro da janela e confirmado depois e sucesso do Ipsum, encerrado
  no horario da submissao; nao e upsolve nem uma segunda avaliacao. Se nao houver
  AC elegivel, confirmar sem AC com fim no prazo. Publicar delta somente depois
  dessa verificacao; nunca recalcular uma avaliacao finalizada. A leitura tardia
  de um AC nao deve ser confundida com uma submissao feita apos o limite.

Para desistir antes das 2h, o limite de verificacao e o horario do encerramento
antecipado. Encerramentos simultaneos resolvem em uma transacao deterministica,
mantendo a primeira fronteira efetiva e sem premiar AC posterior a ela.

## Migracao e limites de compatibilidade

Proposta conservadora para a Historia 38: manter avaliacoes locais anteriores em
arquivo separado, somente leitura, sem recalcular snapshots, ratings, consolidacao
ou duracoes antigas. Um vinculo Firebase novo comeca em 800; nao importar o rating
oficial CF nem transportar o rating do arquivo para o saldo novo. Mostrar a
distincao na migracao para que ela nao pareca perda silenciosa de progresso.
Na nuvem ja existente, restaurar o saldo existente e nao reiniciar em 800.

Preservar exclusoes de problemas antigos e reimportar historico publico do handle:
o novo saldo nao torna problemas ja tentados ou usados elegiveis. Tratar ativo
legado antes da transicao, preservando inicio e aplicando o limite de 2h sem
reiniciar o cronometro; encerrar/reconciliar e arquivar antes de liberar treino novo.
Copiar o arquivo somente para o contexto correto da primeira migracao confirmada
pela sessao, com identificador idempotente; ele nunca vira dado de outra conta.

Historias 31/32 continuam com seus gates cientificos. Rating inicial fixo, timeout
e identidade em nuvem precisam de versoes nos novos exports; nao misturar novas
tentativas com protocolo congelado sem revisar compatibilidade/agrupamento por
participante (inclusive handles repetidos entre contas). UID, e-mail e handle
nao entram no export pseudonimizado. Nenhuma coleta ou upload de estudo e automatico.

## Configuracao e entrega futura

Historia 33 deve registrar projeto Firebase escolhido, app Android/package,
SHA-1/SHA-256 das assinaturas debug/release, configuracao do provedor Google e
cliente OAuth, regiao Firestore/Functions/Tasks e projetos separados de teste/producao.
Documentar requisitos de billing/quotas para Functions/Tasks, Emulator Suite,
Rules/indices versionados, deploy e recuperacao. Validar configuracao ausente com
erro claro; nao versionar credenciais administrativas/chaves privadas.
Projeto/billing/provisionamento reais seguem pendentes. A base local usa
Functions Node 22/TypeScript e regiao us-central1, Rules/indices versionados e
emuladores loopback; nao existe fila Tasks implementada (37). O Android usa
Credential Manager/Google ID/Firebase Auth com flag de preview desligada por
padrao. O [guia executavel](FIREBASE_SETUP.md) registra configuracao e recuperacao.

Entregar em pequenas etapas, com rollout integrado somente depois da 38. O bloco
nao depende da conclusao empirica da 31 nem habilita a 32. Mantem sigilo de tags,
rating/categoria durante o ativo, identidades de problema e avaliacoes unicas.

## Verificacao obrigatoria futura

Rules/Functions em emuladores: sem token, provedor diferente, conta cruzada,
escrita forjada, duplicidade de criacao, retries, duas sessoes e fechamento concorrente.
Google OAuth real requer configuracao do projeto; emulador isolado nao comprova
o fluxo do provedor. Integrar verificacao Android proporcional e evidencia da
configuracao; conferencia humana continua opcional pela D-014.

Fronteiras com relogio controlado: 1h59m59s, exatamente 2h e apos 2h; API atrasada,
veredito pendente, paginacao incompleta, offline/reboot/relogio alterado, job perdido,
AC antes do inicio, no limite e depois, fechamento repetido e uma unica variacao.
Migracao: ratings legados intactos, novo vinculo 800, nuvem existente restaurada,
mesmo handle em duas contas, mesmo handle com caixa diferente, troca de conta,
exclusoes antigas e cache isolado. No marco, `test`, `lint`, `assembleDebug` e
suite do backend; build Android nao se aplica ao planejamento documental atual.
