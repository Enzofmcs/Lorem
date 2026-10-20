# Jornada cloud e preparação (40–43)

Implementação das [issues 73–76](https://github.com/Enzofmcs/Lorem/issues/72),
preservando os cálculos e componentes anteriores. Não muda o contrato científico
nem ativa time-curve-v1. [Operação Firebase](FIREBASE_OPERATION.md).

## Matriz de paridade

| Capacidade anterior | Destino cloud | Origem e proteção |
| --- | --- | --- |
| Shell, abas e voltar (18) | Início/Histórico/Estatísticas e Configurações; treino/resultado dedicados | Navigation Compose; estado salvo por uid, handle e bindingVersion |
| Rating e último resultado (19) | HomeScreen | CloudHandle e resultados FINAL do vínculo atual |
| Dica, motivo, timer e resultado (20) | IpsumScreen/IpsumResultScreen | Snapshot oficial; relógio ancorado ao servidor; nenhuma reavaliação na UI |
| Filtros de situação, rating e tag (21) | HistoryScreen, listas preguiçosas e detalhes | Apenas FINAL; ativo excluído mesmo durante consulta de histórico |
| Curva, frações, dica e consolidação (21) | StatisticsScreen e ConsolidationCard | buildIpsumStatistics/calculateConsolidatedRating; valores persistidos |
| Tema e perfil (19) | Configurações | AppearanceRepository; troca de handle bloqueada durante ativo |
| Preparação e retry (15–17) | Início e Configurações | Etapas/history, catalog, profile, saving; comando idempotente |
| Pendências automáticas (17) | Sessão em RESUMED e CloudPendingWorker | Lotes de até 20, uma janela CF compartilhada, sem reavaliar saldo |
| Arquivo anterior (38) | Configurações e entrada sem login | Somente leitura; sem atribuir uid ou somar às estatísticas cloud |

O resultado cloud guarda contagem de erros, mas não os tipos individuais: a tela
informa essa ausência em vez de exibir “nenhum”. Perfil oficial atual ausente não
é preenchido pelo saldo Lorem. Snapshots anteriores preservam sua avaliação.

## Preparação e budgets

Uma única instância no repositório compartilha trava entre iniciar, reconciliar e
verificar pendências. Todas as chamadas passam pelo cliente CF e gate >=2s já
existentes, incluindo Retry-After. Workers locais não gravam dados cloud.

- Histórico público em memória por uid/handle/binding, validade máxima 24h. Mesmo
  com cache, cada início exige atualização incremental completa. Um cache antigo
  exige nova varredura. Recriar o processo perde apenas a otimização.
- Até 8 páginas de 1.000 por preparação; sobreposição de meia página e IDs
  verificados permitem retomar em retry. Depois de uma carga completa paginada,
  conferir novamente o topo antes de recomendar. Páginas malformadas/incompletas,
  quebra de sobreposição e falhas não autorizam treino.
- Catálogo público reutilizável por 24h, com metadados oficiais de origem. Perfil
  Codeforces atualizado antes de iniciar. Exclusões e tentativas cloud continuam
  consultadas no servidor, com marker/lock/transação e corte de origem preservados.
- Pendências: até 20 por lote em rotação, até 4 páginas CF por execução com cursor
  retomável e isolamento de contexto. Só uma janela completa classifica AC
  posterior; a transação altera exclusivamente `solvedOutsideAt`.
- Cooldown automático de 15min por contexto, inclusive depois de falha; atualização
  manual pode antecipar nova tentativa. Tentativa ativa tem prioridade. A UI só
  consulta o ativo a cada 15s enquanto RESUMED; cronômetro não consulta rede.
- Trabalho periódico único por uid, rede CONNECTED, retry exponencial e cancelamento
  por conta. Não é um serviço independente do dispositivo nem horário exato.
- Histórico Firestore fechado tem cache de até 60s, evitando um cache permanente
  que esconda upsolves de outro aparelho. Alterar progresso ou gravar upsolve
  invalida a entrada correspondente.

## Medição controlada

`CloudPreparationTransportTest` usa o cliente/API reais com transporte sintético,
6 submissões e páginas de 4 (7 no segundo início). As contagens são determinísticas:

| Cenário | Fluxo anterior | Preparação nova |
| --- | --- | --- |
| Primeiro início | 2 páginas + catálogo/origem 2 + perfil 1 = 5 | 4 páginas incluindo sobreposição/topo + 2 + 1 = 7 |
| Segundo início | 2 páginas + 2 + 1 = 5 | 1 página incremental + perfil 1 = 2 |

O custo extra da carga fria é explícito. `lastMetrics` registra páginas, cargas de
catálogo/perfil, duração monotônica, uso incremental e conclusão, inclusive falhas.
O teste do gate registra 14s na carga fria e 4s na quente neste cenário com tempo simulado (inclui a espera entre endpoints). **Latência em aparelho/rede real não medida**;
nenhuma promessa de redução de tempo de início decorre dessa contagem.
