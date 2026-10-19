# Login, perfis Firebase e limite do Ipsum

Planejamento 33-38 aprovado em 19/10/2026 e **revisto para Spark por solicitacao
explicita do usuario (D-039)**. Authentication Google e Firestore, sem Functions,
Cloud Tasks, billing ou backend externo como requisito. Firestore escolhido:
**nam5 (US)**. O JSON Android recebido permanece local e ignorado pelo Git.

33/34 em preview: login, validacao de sessao e bootstrap transacional direto pelo
SDK Firestore. Usuario confirmou a publicacao das Rules; a configuracao de Google
unico ainda aguarda confirmacao. Nao houve leitura administrativa pelo agente
nem login Google real. Conferencia humana e opcional (D-014). **35-37 implementadas
em preview; 38 planejada. Novas Rules 35-37 aguardam publicacao.**
[Estado e comandos](FIREBASE_SETUP.md); [historias e aceite](ROADMAP.md).
As evidencias antigas descrevem a implementacao anterior e permanecem intactas.

Ordem: [33](https://github.com/Enzofmcs/Lorem/issues/60) ->
[34](https://github.com/Enzofmcs/Lorem/issues/61) ->
[35](https://github.com/Enzofmcs/Lorem/issues/62) ->
[36](https://github.com/Enzofmcs/Lorem/issues/63) ->
[37](https://github.com/Enzofmcs/Lorem/issues/64) ->
[38](https://github.com/Enzofmcs/Lorem/issues/65).

## Requisitos e limite de confianca

- Login exclusivamente Google pelo Firebase Authentication.
- Firestore persiste conta, handles, ratings e tentativas sob o uid autenticado.
- Cada par `(uid, handle normalizado)` tem progresso independente. Mesmo handle
  em contas diferentes e permitido, sem prova extra de posse Codeforces.
- Cada vinculo novo inicia exatamente em **800**. Login, troca de handle,
  reinstalacao e nova sincronizacao nao reiniciam saldo. 800 nao e piso da D-012.
- Ipsum dura no maximo **7.200.000 ms (2h corridas)**; sem AC elegivel na janela,
  recebe o resultado sem AC. D-012 e consolidacao D-015 permanecem.

No Spark, o Android consulta o Codeforces, recomenda e calcula resultados.
As Rules verificam identidade/provedor, campos, timestamps, imutabilidade e
transicoes/atomicidade possiveis. **Rules nao consultam a API Codeforces nem
comprovam que um cliente modificado realmente obteve um AC.** Nao prometer
validacao independente no servidor ou protecao contra fraude do proprio usuario.
A limitacao foi explicitada na escolha Spark; isolamento entre contas e
prevencao de dupla avaliacao continuam obrigatorios. Nao abrir gravacoes
irrestritas para compensar a ausencia de Functions.

## Conta e persistencia

| Caminho | Conteudo e regra |
|---|---|
| `users/{uid}` | Somente schemaVersion=1 e createdAt do servidor; criar uma vez. |
| `users/{uid}/handles/{handleKey}` | Handle canonico, rating, consolidacao e versao. Criacao em 800; atualizacao somente com resultado atomico. |
| `users/{uid}/handles/{handleKey}/ipsums/{ipsumId}` | Tentativa, snapshots imutaveis, prazo, dica e resultado unico. |
| `users/{uid}/handles/{handleKey}/usedProblems/{contestId_index}` | Marker imutavel criado junto do inicio; impede repetir problema entre dispositivos. |
| `users/{uid}/runtime/current` | Lock do unico ativo por uid e checkedAt do servidor para ancorar o prazo. |

Na 33/34, Auth atualiza/valida token e exige sign_in_provider=google.com.
O bootstrap captura o uid validado e usa `runTransaction`: le o proprio documento,
cria schemaVersion/createdAt apenas se ausente, com `serverTimestamp()`, e preserva
metadados existentes. A transacao precisa de confirmacao do servidor; cache e fila
offline nao liberam SignedIn. Rules permitem somente essa criacao minima na raiz
pelo proprio Google, com createdAt=request.time; negam update/delete/list da raiz
e campos extras. Os writes nas subcolecoes seguem contratos especificos 35-37.
O cliente nao envia uid, e-mail ou nome no documento. Concorrencia, retry e
restauracao nao reescrevem createdAt. Falha de Rules e diferente de provedor errado;
rede/cota/configuracao ausentes deixam a sessao bloqueada com recuperacao.

O cadastro da 35 usa user.info, normaliza a grafia canonica (trim/lowercase,
Locale.ROOT) e valida o alfabeto publico CF para uma chave segura. Uma transacao
cria somente se ausente; Rules exigem rating inicial 800 e recusam reset/sobrescrita indevida.
Nao introduzir reserva global, OAuth Codeforces ou desafio de posse.

Room e cache/arquivo legado; DataStore guarda configuracao/selecao. Dados pessoais,
leitores, workers e callbacks usam uid+handleKey+versao. Catalogo publico pode ser
compartilhado. Trocar conta remove UI/listeners/trabalhos anteriores; callbacks
atrasados nao liberam a identidade antiga. Com Ipsum ativo, nao trocar handle.
Sair nao reinicia o prazo; sincronizar exige voltar a uma sessao do uid correto.

## Ciclo do Ipsum implementado no preview (35-37)

O Android reutiliza dominio D-012, criterio de origem da 29, importacao D-017,
API centralizada (minimo 2s), paginacao, deduplicacao e retry. Nao embarcar Python
nem habilitar a 32. Selecao DataStore usa namespace por uid; ProfileLink e cache
de historico usam uid+handle+versao. O SDK Firestore mantem cache nos caminhos
privados completos. Nenhum cache Room sem uid e reaproveitado; arquivo/migracao
legado pertencem a 38, sem alteracao destrutiva do esquema local.

Iniciar/encerrar usa SDK Firestore, chaves idempotentes e transacoes verificadas
pelas Rules: inicio+lock no mesmo commit; resultado+rating+versao+liberacao do lock
em outro commit atomico. Repeticao por worker, duplo toque ou segundo dispositivo
nao pode avaliar duas vezes. Rules por estado e getAfter() conferem
consistencia entre documentos; nenhuma liberacao generica de writes por uid.

A hora inicial vem de serverTimestamp() imutavel. Para o prazo, persistir inicio,
duracao maxima e versao; **derivar expiresAt=startedAt+7.200.000ms** apos confirmar
o inicio no servidor. Nao tentar enviar serverTimestamp()+2h como transformacao
inexistente no SDK, nem usar relogio civil do aparelho como autoridade de inicio.
As Rules comparam request.time com o prazo derivado e negam
acoes de treino apos a fronteira, mesmo sem status de encerramento materializado.

Leitura offline e fechamento visual continuam; novo inicio e confirmacao de saldo
exigem Firestore acessivel. Resposta perdida depois de commit recupera o mesmo
id/progresso; nao criar fila de novos treinos offline nem sincronizacao generica.

## Prazo de 2h e WorkManager (Historia 37 implementada)

Agendar **no inicio confirmado**, nao ao receber evento de fechamento, um trabalho
unico por uid/handle/ipsum para reconciliar no prazo, com rede e retry. Worker
consulta Codeforces e grava no Firestore como o proprio usuario. Antes de ler ou
gravar, confirmar uid/versao atuais; nunca executar o trabalho A com a conta B.
Se logout cancelar trabalhos ou a sessao for revogada, a proxima entrada do mesmo
uid reconsulta vencidos e reprograma; o prazo original permanece.

WorkManager pode executar com o app fechado e retomar apos reboot, mas pode ser
adiado por Doze, falta de rede ou restricoes do sistema. Celular desligado, app
forcado a parar/desinstalado e falta de autenticacao impedem materializacao imediata.
Nao ha job no servidor no Spark: se nenhum dispositivo elegivel executar, o banco
so sera atualizado quando houver uma nova execucao/sincronizacao. Nao prometer
atualizacao fisica no instante exato. [Garantias Android](https://developer.android.com/develop/background-work/background-tasks/persistent).

- Toda leitura/comando considera expirado quando now>=expiresAt; a tentativa nunca
  ganha tempo extra porque o worker atrasou. Reparar agendamento perdido no resume
  ou ao recuperar ativo de outro dispositivo. TTL nao executa esta regra.
- Com app aberto, usar prazo confirmado e referencia monotonic; relogio civil
  alterado nao estende o treino. Apos reboot offline sem referencia confiavel,
  bloquear continuacao ate reconciliar; nunca conceder novas 2h.
- Submissao CF e elegivel se startedAt<=creationTime<=expiresAt, respeitada a
  precisao real de segundos. Antes do inicio nao conta; depois e somente upsolve.
  AC no limite vence timeout independentemente da ordem de callbacks.
- Ao vencer, fechar a experiencia. API falha, paginacao incompleta e TESTING nao
  provam ausencia de AC: usar CLOSED_AWAITING_VERIFICATION, retry e impedir novo
  Ipsum ate fixar saldo. AC em prazo descoberto tarde e sucesso, sem segunda
  avaliacao; AC enviado depois do limite nao recalcula o rating original.
- Sem AC elegivel apos verificacao completa, persistir fim efetivo em expiresAt,
  TIME_LIMIT_EXCEEDED e delta sem AC uma vez. processedAt registra o processamento
  tardio separadamente; duracao contabilizada nao supera 2h.
- Desistencia antecipada usa sua propria fronteira; corridas entre AC/desistencia/
  timeout resolvem na mesma transacao, preservando a primeira fronteira efetiva.

## Migracao e estudo

A 38 preserva avaliacoes, ratings, snapshots, consolidacao e duracoes locais antigas
em arquivo somente leitura, sem atribuir dados a outro uid. Novo vinculo Firebase
comeca em 800; nao importar rating CF/legado. Nuvem preexistente restaura saldo.
Preservar exclusoes de problemas antigos e reimportar historico publico; saldo novo
nao torna tentativas antigas ineditas. Reconciliar ativo legado sem reiniciar inicio.

31/32 mantem gates cientificos. Versionar identidade/rating inicial/prazo e limite
de confianca nos exports. Revisar compatibilidade e agrupamento de handles
repetidos antes de novas coortes. UID/e-mail/handle nao entram no export; nenhuma
coleta de estudo ou upload e automatico.

## Configuracao e verificacao

Spark sem cartao/billing. Auth Google e Firestore dentro das cotas gratuitas;
quota esgotada e erro recuperavel, nao motivo para habilitar cobranças
automaticamente. [Planos oficiais](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans).
Registrar projeto/app, OAuth Web, assinaturas efetivamente usadas e banco nam5.
Sem Functions/Tasks, nao existe regiao de compute, service account nem deploy de
codigo servidor. Tooling em firebase/ usa Node 24 e Java 21 para emuladores;
Android continua JDK 17. Publicar **somente Rules/indices**, conforme
[FIREBASE_SETUP.md](FIREBASE_SETUP.md). Configuracao local e ignorada pelo Git.

Verificar Rules com tokens ausentes/nao Google, dois uids, campos/horarios forjados,
criacao concorrente e preservacao de metadados. Exercitar a implementacao Android
real contra emuladores, falhas de rede/cota/Rules, sessao restaurada e troca de
conta. Emuladores nao comprovam OAuth real nem estado remoto do provedor. Na 37,
testar fronteiras 2h, TESTING, worker atrasado/perdido, offline/reboot e delta unico.
Rollout integrado somente apos 38; conferencia humana permanece opcional (D-014).

## Implementacao 35-37 e detalhes de recuperacao

`FirestoreTrainingRepository` valida a identidade em cada operacao e callback de
transacao. Cria handles com grafia canonica, chave lowercase e rating 800 somente
quando o documento nao existe. A selecao DataStore e por uid; um lock remoto ativo
sempre determina o handle efetivo, inclusive quando outro dispositivo iniciou o
treino. A lista de tentativas usa cache uid+handle+versao para evitar reler todo
o historico a cada polling. Catalogo e historico CF precisam de consulta completa
antes de um novo inicio; nao existe fila de novos treinos offline.

O inicio grava tentativa, marker `contestId_index` e lock no mesmo commit. O id
do comando e reutilizado em retries; recuperar um id existente tambem repara o
agendamento. O resultado usa D-012/D-015 existentes e atualiza tentativa, perfil,
versao e lock no mesmo commit. Rules exigem conservacao de saldo, versao seguinte,
snapshot imutavel e transicao unica. A verificacao CF e o calculo numerico sao do
Android, com o limite de confianca Spark declarado acima.

`checkedAt=serverTimestamp` ancora o horario ao `elapsedRealtime` e boot atual.
A referencia e conservadora pelo instante de envio da consulta, nunca recua com
nova confirmacao e nao usa o relogio civil. Pode fechar visualmente um pouco antes
por latencia; Rules e timestamps completos decidem o encerramento definitivo.
Sem referencia confiavel depois de reiniciar o processo/boot, a interface bloqueia
continuacao ate consultar o servidor. Cache offline e visivel dentro de uma
sessao ja validada; restauracao do login continua exigindo a validacao online 34.

O timestamp original do Firestore pode ter fracao menor que 1ms. O cutoff soma
7.200 segundos preservando seus nanos; um resultado sem AC copia o cutoff exato.
O Codeforces informa segundos inteiros: o primeiro segundo elegivel e o primeiro
instante CF maior ou igual ao inicio completo, e o ultimo e menor ou igual ao
cutoff. `processedAt` nao participa da duracao nem da elegibilidade.

WorkManager tem um trabalho unico de prazo por uid+handle+ipsum, registrado antes
de devolver o inicio a UI. Ao fechar antecipadamente, um trabalho de verificacao
separado pode executar imediatamente, sem esperar as duas horas. Ambos usam
rede, backoff e guard de uid/versao/id; UI e worker compartilham mutex/gate CF.
Retomar/login repara jobs ausentes. Conta diferente torna o job obsoleto; sem
sessao ele aguarda retry ou a entrada do uid original. Logout cancela jobs da
conta que saiu. As garantias de atraso Android acima continuam aplicaveis.

API indisponivel, entrada malformada, pagina incompleta ou TESTING nunca provam
falha: o treino fechado mantem lock e rating, com retry. A avaliacao aguarda janela
completa sem vereditos pendentes, inclusive com AC conhecido, para preservar o
horario do primeiro AC e o rating. Com AC elegivel decide sucesso; sem AC decide falha.
Desistencia fixa sua fronteira por horario do servidor e nao e substituida por
timeout posterior. AC depois da fronteira apenas marca upsolve; a UI oferece
verificacao do AC posterior sem alterar resultado/rating originais.

Verificacao automatizada usa somente `demo-lorem-local`: Rules com duas contas/
dispositivos e o SDK Kotlin de producao. Fixtures de timeout alteram apenas o
inicio no emulador de teste, incluindo fracao submilissegundo; nao sao treinos
reais nem evidencia cientifica da 31. [Evidencias 35-37](evidence/2026-10-19-firebase-35-37.json).
