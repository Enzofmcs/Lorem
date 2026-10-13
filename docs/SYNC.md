# Sincronização automática com o Codeforces

Proposta de implementação de 13/10/2026 para as Histórias 15–17. Os mecanismos
descritos aqui ainda não foram implementados. A solicitação de atualização automática
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
