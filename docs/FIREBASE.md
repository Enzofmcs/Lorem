# Login, perfis Firebase e limite do Ipsum

Planejamento 33-38 aprovado em 19/10/2026 e **revisto para Spark por solicitacao
explicita do usuario (D-039)**. Authentication Google e Firestore, sem Functions,
Cloud Tasks, billing ou backend externo como requisito. Firestore escolhido:
**nam5 (US)**. O JSON Android recebido permanece local e ignorado pelo Git.

33/34 em preview: login, validacao de sessao e bootstrap transacional direto pelo
SDK Firestore. Projeto/provedor e publicacao remota das Rules ainda nao verificados;
conferencia humana e opcional (D-014). **35-38 continuam planejadas.**
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
| `users/{uid}/handles/{handleKey}` | Handle canonico, rating, consolidacao e versao; regras especificas na 35. |
| `users/{uid}/handles/{handleKey}/ipsums/{ipsumId}` | Tentativa, snapshots e resultado; transicoes especificas na 36. |
| `users/{uid}/runtime/current` | Lock transacional do unico ativo por uid; contrato da 36. |

Na 33/34, Auth atualiza/valida token e exige sign_in_provider=google.com.
O bootstrap captura o uid validado e usa `runTransaction`: le o proprio documento,
cria schemaVersion/createdAt apenas se ausente, com `serverTimestamp()`, e preserva
metadados existentes. A transacao precisa de confirmacao do servidor; cache e fila
offline nao liberam SignedIn. Rules permitem somente essa criacao minima por
Google no proprio uid, com createdAt=request.time; negam update/delete/list da
raiz, campos extras e todos os writes em handles/ipsums/runtime nesta entrega.
O cliente nao envia uid, e-mail ou nome no documento. Concorrencia, retry e
restauracao nao reescrevem createdAt. Falha de Rules e diferente de provedor errado;
rede/cota/configuracao ausentes deixam a sessao bloqueada com recuperacao.

O cadastro da 35 usa user.info, normaliza a grafia canonica (trim/lowercase,
Locale.ROOT) e codifica uma chave segura. Uma transacao cria somente se ausente;
Rules futuras exigirao rating inicial 800 e recusarao reset/sobrescrita indevida.
Nao introduzir reserva global, OAuth Codeforces ou desafio de posse.

Room e cache/arquivo legado; DataStore guarda configuracao/selecao. Dados pessoais,
leitores, workers e callbacks usam uid+handleKey+versao. Catalogo publico pode ser
compartilhado. Trocar conta remove UI/listeners/trabalhos anteriores; callbacks
atrasados nao liberam a identidade antiga. Com Ipsum ativo, nao trocar handle.
Sair nao reinicia o prazo; sincronizar exige voltar a uma sessao do uid correto.

## Ciclo do Ipsum planejado para Spark (35-38)

O Android reutiliza dominio D-012, criterio de origem da 29, importacao D-017,
API centralizada (minimo 2s), paginacao, deduplicacao e retry. Nao embarcar Python
nem habilitar a 32. Estes fluxos ainda nao estao implementados no preview.

Iniciar/encerrar usa SDK Firestore, chaves idempotentes e transacoes verificadas
pelas Rules: inicio+lock no mesmo commit; resultado+rating+versao+liberacao do lock
em outro commit atomico. Repeticao por worker, duplo toque ou segundo dispositivo
nao pode avaliar duas vezes. Planejar Rules por estado e getAfter() para conferir
consistencia entre documentos; nenhuma liberacao generica de writes por uid.

A hora inicial vem de serverTimestamp() imutavel. Para o prazo, persistir inicio,
duracao maxima e versao; **derivar expiresAt=startedAt+7.200.000ms** apos confirmar
o inicio no servidor. Nao tentar enviar serverTimestamp()+2h como transformacao
inexistente no SDK, nem usar relogio civil do aparelho como autoridade de inicio.
As Rules futuras poderao comparar request.time com o prazo derivado e negar
acoes de treino apos a fronteira, mesmo sem status de encerramento materializado.

Leitura offline e fechamento visual continuam; novo inicio e confirmacao de saldo
exigem Firestore acessivel. Resposta perdida depois de commit recupera o mesmo
id/progresso; nao criar fila de novos treinos offline nem sincronizacao generica.

## Prazo de 2h e WorkManager (Historia 37)

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
