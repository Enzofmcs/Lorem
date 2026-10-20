# Estudo descritivo em Jupyter · 20/10/2026

Histórias 44–48 implementadas e executadas com dados reais novos. O usuário
autorizou uma nova coleta após a busca sem o backup de 18/10. O snapshot anterior
não foi recuperado; seus agregados e a avaliação permanecem preservados.

- Snapshot novo: `efcaea1c6c0183839314a3ad9e15ce9a55695ed2f62c31b61dccce6e7343ba03`.
- Plano prévio: `56b2b31dc25164ce2e7d06b909d80d751afc8288e9101de936f4bee6b0a27748`; seed 20261020,
  bins x/y de 400, sensibilidades 200/800, horizontes 30/60/120min.
- 321 respostas HTTP 200; 1,779,749,472 bytes de respostas.
  Retomada offline: zero chamadas e mesmo hash. Sem comparação entre épocas.
- 20 contests adquiridos, 19 elegíveis em 19 grupos. O 1572 não tinha oldRating
  verificável e não foi substituído. Apenas 1687 se sobrepõe aos 19 contests
  elegíveis do estudo anterior; a nova amostra inteira foi explorada descritivamente.
- 496,370 tentativas observadas = 313,893 eventos +
  41,903 censuras + 140,574 exclusões.
- 40 células passam pelo filtro de exibição 50 tentativas/20 eventos/dois grupos.
  Esse filtro não garante precisão; métricas sem seguimento ficam indisponíveis.

## Duas regiões de ratings

Limites inferiores inclusivos/superiores exclusivos; x é oldRating anterior,
y é rating do problema observado na coleta. São associações descritivas.

| Região x / y | Tentativas | Eventos | Censuras | Grupos | Mediana (min) | P75 (min) | P(AC≤120) |
|---|---:|---:|---:|---:|---:|---:|---:|
| [1200,1600) / [1600,2000) | 7842 | 3561 | 4281 | 10 | 93.5333 | indisponível | 0.594211 |
| [1600,2000) / [1600,2000) | 9643 | 7452 | 2191 | 12 | 51.5000 | 88.7500 | 0.835739 |

No primeiro estrato, P25=53,5833min; P75 não foi alcançado. Não substituir
por 120min: h é orçamento, não P100. A consulta antiga 1200/1600 usava bin de
**delta do treino**, com F(120)=0,622099; não equivale ao estrato x/y acima.
Não reconstruir curva completa a partir de probabilidades retidas.

## Estabilidade e limites

Na região x=[1200,1600), y=[1600,2000), bootstrap de dez grupos inteiros,
200 réplicas e seed 20261020. Há 389 participantes presentes em mais de um grupo.
P75 ficou ausente em 158/200 réplicas (79%); IC95% permanece null. P(AC≤120)
ficou sem seguimento em 2/200 réplicas (1%); IC também null sob a política prévia.
Nenhuma réplica ausente foi descartada para produzir precisão artificial.
Os ICs de P25 e P50, quando todas as réplicas identificam o quantil, estão em
stability.json. P25–P75 entre tentativas não é IC da estimativa.

A mediana da primeira região é 117,0833min com grade 200 e 97,9667min com grade
800. Não escolher a faixa mais favorável como verdade. O notebook mostra
estimandos sequencial, desde início e censura até última submissão, além de
resultados por grupo e omitindo um grupo por vez. Prefixos selecionados,
abandono informativo, rating de problema observado na coleta e possíveis aliases
não detectados permanecem limitações. T não mede dedicação exclusiva.

AFT/KM/D-012 antigos são reapresentados com métricas, denominadores e ICs
originais. O teste antigo de quatro contests já foi examinado; nenhum retuning.
A nova amostra/partições também foram exploradas: **avaliação preditiva nova não
realizada**. Nova alegação preditiva exige grupos novos não examinados. Sem
conclusão causal, pedagógica ou de capacidade permanente. D-012 e política
Android time-curve-v1 desabilitada permanecem intactos.

## Reprodução e preservação

[Guia](../../notebooks/README.md) e [evidência](../../evidence/2026-10-20-notebooks.json).
114 testes Python passaram, incluindo 23 testes novos de análise/restauração;
os 23 também passam sem site-packages. Quinze kernels limpos: cinco no modo real,
cinco agregado histórico e cinco sintético identificado. HTML com imagens embutidas,
notebooks executados e JSONs completos estão em work/notebooks-real-2026-10-20.

Os JSONs versionados aqui preservam agregados, suporte, razões de ausência e hashes
das curvas completas. Estas estão nos exports locais e podem ser regeneradas.
Não há handles, sample_id ou participant_key nos arquivos publicados. Os gráficos
usam as mesmas tabelas analíticas; SVG tem sal/hash e data estabilizados.

Backup local work/snapshot-backups/lorem-snapshot-2026-10-20.tar.gz:
238.668.830 bytes; SHA-256
`94b840d9cd799a7582ecc0ee3bdaf44a9947c7e472f98742880d19da58600b86`.
Inclui brutos, audit, features/labels, modelos e partições; omite apenas previsões
regeneráveis. Contém handles públicos e permanece fora do Git. Todos os 321
objetos brutos e oito arquivos principais foram conferidos lendo o archive.
Guarde esse arquivo para recuperar os dados em outro ambiente; agregados Git
não substituem o snapshot. Não houve deploy, mudança Android ou ativação de pontos.

TESTE MANUAL OPCIONAL - NÃO EXECUTADO (D-014).
