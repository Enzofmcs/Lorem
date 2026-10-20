# Contrato de dados v1

> **Planejamento 44–49:** [nova direção](../docs/REDIRECTION.md). Este arquivo
> preserva contratos existentes. `analysis-time-bands-v2` será uma saída analítica
> separada, com agrupamento x/y, suporte, quantis nullable e orçamento h; não é
> mudança já implementada de schema nem dos snapshots `time-curve-v1`.

Configuracao: `schema_version=1`, `protocol_version=sequential-v1`,
`eligibility_version=origin-20200701-v1`, modelo `kaplan-meier-v1`. Datas e duracoes
brutas sao segundos UTC; previsoes sao minutos. `null` significa ausente/nao
identificavel, nunca zero ou infinito. `data_kind` distingue `synthetic` de `real`.

## Proveniencia e completude

| Campo/artefato | Contrato |
|---|---|
| Referencia HTTP | `method`, `params`, `url`, `collected_at_utc`, `http_status`, `sha256`, `data_kind`; hash dos bytes em raw/. |
| `selection` | Lista de contest_ids escolhida por hash/rodizio de divisao, semente e config_sha256; list_ref comprova datas. |
| `contests[id].complete` | Todos os endpoints/paginas necessarios coletados e sem veredito oficial pendente. Falha nunca e resposta vazia. |
| `contests[id].standings_scope` | Resposta oficial completa com apenas contestId; status continua paginado. Snapshots anteriores podem ter standings paginado. |
| `prepared.complete_contest_ids` | Contests completos **e elegiveis** para particao, sem compartilhar linhas entre conjuntos. |
| `origin_links` | Grupos com identidade verificavel e fonte, sem deduplicar por nome nem inferir data por id. |
| `snapshot_sha256` | Hash da estrutura JSON canonica, separado do hash dos bytes HTTP. |

## features.jsonl

| Campo | Significado e origem |
|---|---|
| `sample_id` | SHA-256 de contest, participante pseudonimo, contest de origem e index. Chave de join, nao feature. |
| `contest_id` | Contest da participacao, utilizado apenas para join/particao/auditoria. |
| `participant_key` | SHA-256 estavel do handle normalizado (strip/casefold), pseudonimo e nao anonimato. Nao e preditor. |
| `problem_contest_id`, `problem_index` | Identidade original verificavel do problema. |
| `old_rating` | `oldRating` de contest.ratingChanges por participante/contest, nunca newRating/rating atual; ausente fica null. |
| `problem_rating` | Rating de problemset.problems no snapshot da coleta; nao afirmar disponibilidade historica. |
| `delta` | problem_rating - old_rating; null se algum rating faltar. |

Somente os ratings/delta podem orientar o baseline. Datas, rank, score, AC, erros,
contagens de submissoes e duracao realizada nao sao atributos de entrada.

## labels.jsonl e audit.jsonl

Unidade: problema com submissao oficial observada em `[inicio, inicio+duracao)`.
Nao gerar negativo para problema nao tentado. `creationTimeSeconds` do primeiro
envio com veredito **final OK** define AC; o horario do julgamento nao e usado.
Upsolves, virtual/practice e submissoes fora da janela sao excluidos e contados.

| Campo | Contrato |
|---|---|
| `quality`, `reason` | valid/excluded e motivo principal auditavel. Prioridade: participante/completude, problema, intervalo; sem dupla contagem. Coleta incompleta nao vira censura. |
| `duration_seconds` | Y=min(T,C), estritamente positivo para validos; null para excluidos. |
| `event` | 1=AC observado, 0=censurado a direita, null=inelegivel/incompleto. |
| `sensitivity_last_submission_seconds` | Janela alternativa ate ultima submissao, somente quando positiva e valida; nao e dedicacao real. |
| `anchor_seconds` (audit) | Inicio para A; primeiro AC do predecessor imediato para seguintes. Null se predecessor invalido. |
| `first_ac_seconds` (audit) | Primeiro AC observado na janela, inclusive para auditar registros excluidos. |
| `observed_end_seconds` (audit) | Primeiro AC ou fim do contest para censuras validas; null se intervalo invalido. |
| `available_seconds` (audit) | C=fimContest-ancora; disponibilidade sob hipotese sequencial, nao trabalho efetivo. |
| `submission_ids`, refs (audit) | Rastreio a raw/status, ratingChanges, catalogo e metadados de origem. |

Motivos incluem: missing_old_rating, conflicting_rating_changes,
not_recognized_regular_cf_round,
insufficient_prior_rating_history, ambiguous_problem_origin,
origin_date_missing_or_before_cutoff, missing_problem_rating,
unsupported_problem_order, incomplete_collection, conflicting_submission_identity,
predecessor_not_valid, inverted_or_nonpositive_ac, submission_before_anchor,
interleaved_or_skipped_problem e unresolved_verdict. Razoes em ingles sao codigos
estaveis, nao rotulos de incapacidade. Tempos positivos nao provam dedicacao exclusiva.

## Particoes e referencias

`splits.json` guarda grupos, seed e particoes train/validation/test por contests.
Minimo 6/2/2 (10 completos/elegiveis), grupos transitivos indivisiveis. Sem divisao
viavel, interromper e manter apenas piloto; nao dividir registros do mesmo contest.

Modelo: conjuntos de risco, eventos/censuras e sobrevivencia em cada tempo,
dominio dos ratings no treino, bins suportados e curva reversa de censura. Eventos
e censuras empatados usam eventos antes de censuras; na curva reversa o denominador
das censuras remove os eventos empatados (`n-d`). Transformacoes/faixas/censura
nunca usam teste/validacao. Predicoes exportadas nao contem rotulos de resultado.

Probabilidade e `1-S(60*b)`, b explicito em minutos; mediana e quantis 10/90 podem
ser null. `out_of_domain` sinaliza extrapolacao e `support` informa suporte por
horizonte. `d012_expected_minutes` reproduz clamp(60+delta/10,30,120), com arredondamento
positivo equivalente ao Kotlin; `d012_probability=null`. Artefatos e previsoes
referenciam seu hash e versao. Intervalo preditivo empirico nao e intervalo de confianca.

## AFT v1

Metodo em [MODEL.md](MODEL.md). `aft/model.json` usa `model_version=aft-v1` e inclui
`fit` (distribuicao, penalidade, coeficientes por 100 pontos, centros/escalas do
treino, escala da distribuicao, fatores de tempo, dominio, convergencia e hash do
treino). `selection` registra grade, folds, scores internos da validacao e gate de
calibracao. A curva de censura e ajustada somente no treino. Versoes NumPy/SciPy
ficam no artefato. Rotulos do teste nao participam de selecao/transformacoes.

Previsoes incluem probabilidades por orcamento, mediana e quantis 10/90 em minutos,
suporte de cada horizonte/quantil e extrapolacao de rating. Seguimento insuficiente
ou gate falho retorna `null`; nenhum valor infinito. Intervalo e preditivo,
parametrico, sem alegacao de IC ou precisao comprovada. `manifest.json` liga hashes
de modelo, tabelas, particoes e previsoes. A avaliacao cientifica reservada e a 27.

## Avaliacao e relatorio (27-28)

- `evaluation/plan.json`: hashes dos dois modelos, preparo/particoes, seed, grade,
  bins, suporte minimo, 200 replicacoes e regra de sensibilidade, antes dos rotulos.
- `evaluation/metrics.json`: agregados reais/sinteticos identificados, n e eventos/
  censuras, quatro referencias, cada metrica com `estimate`, `n`, `ci95` e
  `bootstrap_valid`; ausencias ficam null. Diferencas usam `n_common`. Sem ids de
  participante, previsoes por linha ou handles nos agregados publicados.
- `quality_funnel_test` conta validos/exclusoes por motivo; excluidos nao recebem
  erros imputados. `rating_slices`: faixas fixas de 400 pontos, incluindo delta
  negativo por piso; contagens, metricas e ICs por metodo.
- `support`: conhecidos/desconhecidos, G esquerdo, maior peso e intervalo continuo
  de integracao. `sensitivity_last_submission`: previsoes fixas, G do treino
  alternativo e ausencias explicitas. Nao e novo rotulo de dedicacao real.
- `report/`: REPORT/REPRODUCE, CSVs, SVG/PNG, demo e manifesto SHA-256. IC das
  metricas nao e intervalo preditivo 10/90. Log-loss limita somente log em 1e-15.
- `scripts/reproduce.py`: reconstrucao offline em destino novo; `reproduction.json`
  registra versoes/tempos, zero requisicoes e igualdade de cada arquivo.

## Exportacao local de Ipsums (30)

Envelope `ipsum-local-v1`: schema_version, data_kind (ipsum_local/synthetic), UUID
participant_key, exported_at_epoch_ms, application_version, active_omitted e attempts.
O codigo nunca serializa o objeto Ipsum inteiro: whitelist exclui handle/nome/tags/
ids de submissao e todos os dados do ativo. sample_id e SHA256 do pseudonimo+id local.

Por tentativa: problem_contest_id/index; started/ended/first_ac/upsolve_ac/hint em
**epoch ms**; status/motivo do fim; hint_used (legado sem prova fica null),
problem_rating, cf_rating_before/observed_at/source, lorem_rating_before e versoes
recording/rating_rule/eligibility. Origem tem data/fonte/verificacao; interruptions
fica null quando nao registrado, wall_clock_only=true. Ausencia nao vira zero.

CF usa somente user.info observado antes do inicio e e persistido no novo Ipsum;
nao usar perfil atual/newRating futuro. Primeiro AC do COMPLETED e o encerramento
persistido pelo app; upsolve permanece separado e nao recalcula a avaliacao.
Room 11->12 adiciona tres campos nullable; DataStore guarda timestamp CF e UUID por
perfil. Limpar/trocar perfil nao liga exportacoes de perfis distintos.

Preparo `ipsum-participant-v1`: confere metadata oficial inteira do contest,
D-026, origem/data, janelas, snapshots CF e dica; quality_reasons admite mais de um
motivo por linha, sem dupla contagem de excluidos. duration_seconds e evento 0/1
existem somente para validos. PENDING/SOLVED_OUTSIDE censuram no encerramento
original; o AC posterior nao e evento do Ipsum. Split e por participante inteiro,
com hash/seed fixos. Export conflitante/coorte congelada alterada e recusado.
Cobertura e valid_attempts/observed_attempts, indefinida sem observacoes.
Manifesto sem dados reais registra not_collected/insufficient_data; nao ha metricas
nem decisao de integracao sem evidencia da 31.

### Export cloud da historia 38

ipsum-cloud-v2 usa schema_version=2, data_kind=ipsum_cloud, whitelist de tentativas
FINAL e omite ativo/aguardando verificacao. identity_version=installation-handle-v1,
initial_rating_policy=binding-800-v1, time_limit_ms=7200000,
clock_policy=server-start-monotonic-v1 e verification_trust=android-codeforces-client-v1.
participant_key e UUID aleatorio por handle nesta instalacao: o mesmo handle em
uids diferentes fica agrupado aqui. sample_id inclui namespace opaco aleatorio por
uid/handle/binding para nao colidir sequencias. UID/e-mail/handle/namespace direto
nao sao exportados. Reinstalacoes nao sao automaticamente identificadas/agrupadas.

O preparador historico rejeita cloud v2 explicitamente antes de rede: diferencas
na identidade, inicial 800, prazo e confianca nao podem ser misturadas silenciosamente.
A 31 foi retirada do escopo pelo usuario; a 32 fica fora da entrega e sem previsoes.
O export e recurso local opcional da 38, sem coleta/upload automatico.
