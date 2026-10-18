# Contrato de dados v1

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
