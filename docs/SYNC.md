# Sincronização automática com o Codeforces

Plano de implementação de 13/10/2026 para as Histórias 15–17. A coordenação da História 15 e o
agendamento da História 16 estão implementados e verificados. O polling e a
consulta do ativo no worker continuam planejados na História 17. A solicitação de atualização automática
substitui a restrição de produto da D-011; veja D-018 em [DECISIONS.md](DECISIONS.md).

## Estratégia

| Dado ou evento | Quando atualizar | Mecanismo |
|---|---|---|
| Primeiro perfil validado | Importar histórico completo e catálogo automaticamente | Trabalho único de preparação, com progresso persistido |
| Histórico e pendências | Intervalo desejado de 15 minutos | Trabalho periódico único por perfil no WorkManager |
| Catálogo compartilhado | Intervalo desejado de 24 horas | Trabalho periódico único global no WorkManager |
| Retorno ao app | Recuperar dados ausentes ou vencidos; verificar Ipsum ativo | Coroutines e coordenador compartilhado |
| Iniciar novo Ipsum | Atualizar submissões recentes antes da seleção | Coordenador, seguido da recomendação existente |
| Ipsum visível e app em `RESUMED` | Consultar imediatamente e depois de 15 em 15 segundos | Coroutine vinculada ao ciclo de vida e ao id do Ipsum |
| Ipsum em segundo plano | Verificar no próximo trabalho de perfil elegível | Mesmo caso de uso da consulta em primeiro plano |
| Metadados públicos do perfil | Na preparação e quando vencidos há 24 horas | `user.info`, sem reinicializar o Rating Lorem |

Os intervalos são parâmetros iniciais propostos. WorkManager aceita periodicidade
mínima de 15 minutos e o Android pode atrasar ou pular execuções por rede, Doze e
economia de bateria. Não representa um relógio exato nem detecção instantânea.
Uma parada forçada impede o funcionamento normal até a reabertura do app; remover
da lista de recentes não equivale a forçar parada. A retomada deve recuperar o atraso.

O Codeforces expõe consultas HTTP. O plano usa essa API pública documentada e não
depende de um canal push. Todas as chamadas continuam respeitando **no máximo uma
requisição a cada dois segundos**, incluindo paginação, workers, botões e polling.

## Coordenação e persistência

- Reutilizar `AppContainer`, repositories e casos de uso. A UI e os workers devem
  usar a mesma instância do cliente, limitador e coordenador no processo padrão.
  Não criar um cliente independente para cada worker nem adicionar Hilt ou backend.
- Unificar disparos repetidos por operação/perfil e impedir gravações concorrentes
  sobre a mesma informação. Consultas do Ipsum devem poder prosseguir entre páginas
  de importações grandes, sem furar o limitador ou iniciar importações duplicadas.
- Persistir estado e último sucesso separados para histórico, catálogo, perfil e
  verificação de pendências/submissões. Sucesso parcial não significa “tudo atualizado”.
  Separar “nenhum dado” de um histórico validamente vazio.
- Associar cada execução ao handle normalizado e a uma versão do vínculo local.
  Ao trocar/remover o perfil, cancelar trabalhos antigos e rejeitar seus resultados
  antes de persistir ou publicar estado; cancelamento sozinho não basta.
- Atualizar apenas os campos remotos e de sincronização do perfil. O fluxo atual
  já usa `markHistorySynchronized` e `updateProfileMetadata` para evitar snapshots
  antigos (PR #29). Preservar essas escritas parciais e acrescentar proteção por
  versão de vínculo e coordenação entre workers, tela e troca de perfil.
- Primeira importação completa; próximas consultas incrementais com sobreposição,
  deduplicação por id da submissão e reconciliação de veredictos ainda não definitivos.
  Reutilizar `submissionHistorySince` onde adequado. Um checkpoint só avança após
  persistência bem-sucedida do lote; falha/cancelamento de página não perde dados.
  Paginação deve tolerar submissões chegando entre páginas e timestamps iguais.
- Mesclar agregados incrementais com histórico salvo: nunca apagar a evidência de
  tentativa ou AC por receber apenas páginas recentes. Identidade do problema continua
  sendo `contestId + index`. Importações longas devem retomar do progresso persistido
  quando o worker for interrompido, sem marcar a importação incompleta como pronta.
- AC no Ipsum e upsolve reutilizam os casos de uso atuais. Persistir submissões,
  encerramento e efeito de rating de forma idempotente, inclusive na disputa entre
  worker, retorno à tela, polling e encerramento manual.

## Base implementada na História 15

`AppContainer.synchronizationCoordinator` é a entrada compartilhada para UI e os
workers e futuro polling. Reutiliza uma única instância de `CodeforcesApiRepository`
e seu gate de dois segundos, com relógio monotônico. Operações iguais do mesmo
vínculo compartilham a execução; o lock de coordenação não envolve a importação
inteira e cada página libera o gate para consultas do Ipsum.

O vínculo persistido contém handle normalizado e versão crescente. Troca/remoção
cancela operações do perfil anterior; cada gravação também confere essa versão
sob o mesmo mutex usado para trocar o perfil. A proteção continua válida em
A → B → A e na reconciliação Room → DataStore. Catálogo é global e continua válido
entre perfis. Metadados e timestamp usam as escritas parciais existentes.

`PreferencesSynchronizationStore` persiste estado por conjunto/vínculo (também por id do Ipsum nas consultas), último
sucesso, falha e cursor. A primeira importação percorre páginas de 1.000 registros
com sobreposição de 500; a retomada usa o cursor salvo. Room mescla evidências por
`contestId + index` antes de gravar o próximo cursor em DataStore. Interrupção
entre as duas gravações permite repetir o lote sem apagar tentativa/AC. Uma
varredura adicional do início recupera submissões chegadas durante a paginação.

Atualizações seguintes param após o watermark anterior com um minuto de
sobreposição, incluindo timestamps iguais. IDs provisórios conservam sua data
até receberem veredicto definitivo, ampliando a consulta quando necessário.
Sobreposição deduplica IDs por lote; as evidências por problema são monotônicas.
`importedCount` indica registros processados, incluindo sobreposição, e não uma
contagem de submissões únicas. Histórico vazio só fica pronto após consulta
bem-sucedida. Falha/cancelamento preserva cache, cursor e último sucesso anterior.

Estado pequeno usa chaves novas em DataStore; o esquema Room não mudou. Vínculos
legados começam na versão zero, mantendo histórico, rating e consolidação.
`SynchronizationPersistenceTest` usa Room/DataStore reais para retomada, falha de
gravação, cancelamento, A → B → A, sucesso parcial, timestamps iguais e disputas
entre consulta, encerramento, upsolve e atualização do perfil. O gate e a API têm
testes próprios de interleaving e paginação.

## Agendamento e falhas

Usar `CoroutineWorker`, `enqueueUniqueWork` para preparação e
`enqueueUniquePeriodicWork` para manutenção. Reabrir o app não deve recriar o
trabalho periódico e adiar indefinidamente a próxima execução. Exigir rede conectada;
para o catálogo periódico, também bateria não baixa. Cadastro válido agenda trabalho;
troca de handle substitui apenas trabalhos do perfil, preservando o catálogo global.

Timeout, rede, HTTP 429 e falhas temporárias recebem backoff exponencial a partir de
30 segundos, respeitando `Retry-After` quando disponível. Distinguir handle inválido
e resposta inválida persistente de erro transitório, sem repetição imediata infinita.
Propagar cancelamento em vez de convertê-lo em sucesso ou falha de API. Recuperar
estados “sincronizando” abandonados após morte do processo.

Ao abrir o app, exibir Room/DataStore imediatamente e atualizar somente o necessário.
Antes de um novo Ipsum, tentar atualizar o histórico com rede e verificar se o
perfil continua igual. Preservar a D-017: a primeira importação bem-sucedida é
obrigatória; depois, falta de rede permite usar cache, indicando dados salvos e
último sucesso. Sem histórico importado, explicar a preparação pendente e oferecer
nova tentativa. Catálogo salvo pode ser usado durante uma falha de atualização.
Um Ipsum em andamento mantém cronômetro e ações locais offline.

## WorkManager implementado na História 16

O `SynchronizationScheduler` observa alterações do vínculo, agenda preparação de
histórico/pendências/metadados por perfil e catálogo global, e mantém os periódicos
com `KEEP`. `MainActivity.onStart` recupera conjuntos ausentes, vencidos ou com
falha, sem substituir ou adiar a agenda. A observação cancela trabalhos de vínculos
antigos encontrados no banco do WorkManager, inclusive após recriação do processo.
Catálogo continua global. O Android determina a execução efetiva por suas constraints.

`SynchronizationWorkerFactory` injeta o `BackgroundSynchronization` do AppContainer.
`LoremApplication` implementa `Configuration.Provider`; o initializer automático do
WorkManager é removido sem remover os demais inicializadores do AndroidX Startup.
A biblioteca mantém os serviços/receivers e a permissão de boot necessários.
Não há foreground worker, notificação ou navegação disparada por esse trabalho.

Cada etapa conserva seu sucesso e validade (15 min para histórico/pendências,
24 h para metadados/catálogo); retry não refaz uma etapa ainda válida. Falhas
parciais não impedem as outras etapas, exceto durante `Retry-After`. Rede,
HTTP 408/429/5xx e falha de persistência usam backoff exponencial de 30 s;
resposta inválida/erro permanente termina a tentativa, com recuperação manual ou
em outro ciclo elegível. A API reconhece também rate limit em respostas `FAILED`.
`Retry-After` em segundos/data HTTP gera um prazo global em DataStore; workers
adiam a tentativa e todas as chamadas passam pelo mesmo gate antes de sair à rede.

No início do processo, estados abandonados são liberados preservando o cursor.
Operações com consumidores ativos são preservadas. Cancelar o último consumidor
cancela o trabalho compartilhado e aguarda sua gravação de interrupção; se outro
consumidor continua esperando, a importação permanece ativa. O worker pode retomar
lotes depois de interrupção pelo Android sem perder evidências já gravadas.

O Início observa histórico/catálogo persistidos com progresso e falha por etapa.
Antes de um novo Ipsum com importação inicial já concluída, o ViewModel tenta
atualizar o histórico com orçamento de 12 s; falha/timeout conserva o cache e exibe
“Usando dados salvos” com o último sucesso. O perfil é revalidado após a rede e seu
vínculo é conferido sob o mesmo mutex da criação em Room, inclusive em A → B → A.
Histórico nunca importado continua bloqueando a criação, conforme D-017.

`SynchronizationWorkTest`, API/gate e ViewModel cobrem o comportamento; consulte
[as evidências da História 16](ROADMAP.md#historia-16---sincronizacao-em-segundo-plano-com-workmanager).
A verificação do Ipsum ativo pelo worker e o loop de 15 s pertencem à História 17.

## Experiência

A preparação inicial é automática e informa “Preparando seu treino”, histórico e
catálogo, com erro recuperável por etapa. Depois disso, a tela inicial tem um estado
discreto: “Atualizado há 2 min”, “Atualizando…” ou “Sem conexão · dados salvos”.
Detalhes por conjunto de dados ficam em Configurações; horário só muda após sucesso.
Botões de sincronização deixam de ser etapas obrigatórias. “Tentar novamente” e
“Atualizar agora” continuam disponíveis para recuperação, usando o coordenador.

No Ipsum, mostrar “Acompanhando submissões”, última consulta e falhas sem spam de
snackbars. Parar polling ao sair da tela, perder `RESUMED` ou encerrar o Ipsum;
retomar com consulta imediata, sem multiplicar loops após rotação. AC reconhecido em
segundo plano atualiza os dados; o resultado aparece ao retornar, sem abrir tela ou
mandar notificação espontânea. AC posterior não altera a avaliação original.

## Verificação planejada

Histórias 15–17 têm risco de Nível 4: usar relógio e repositoríos falsos para
concorrência, rate limit, checkpoint, cancelamento, troca de perfil, rating e
idempotência; Room/DataStore e migrações quando necessários; `work-testing` para
unicidade, constraints e retries; testes de ciclo de vida para polling. Validar em
dispositivo preparação, AC real, upsolve, offline, rotação, morte de processo,
reinício e retomada após parada forçada como conferência manual opcional (D-014).
Os critérios funcionais exigem evidência automatizada proporcional ao risco.
Não aceitar a história só porque um timer
funcionou com o app aberto. Aplicar regressão de marco conforme [TESTING.md](TESTING.md).

## Fontes

- [Android — definição de trabalhos, periodicidade, constraints e backoff](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).
- [Android — trabalhos únicos e cancelamento](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work).
- [Codeforces — API pública e limite de chamadas](https://codeforces.com/apiHelp).
