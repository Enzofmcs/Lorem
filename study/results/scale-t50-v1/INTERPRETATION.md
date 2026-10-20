# Leitura descritiva dos resultados ampliados

Fonte: [analysis.json](analysis.json), snapshot `a08506d0997c7f318f9642605e60b1f442416f9623cb3d55b00fb423f080e14e`.
As comparações abaixo foram calculadas após a análise e não são hipóteses
confirmatórias. Não houve treino nem avaliação preditiva independente nova.

## O que fica identificado

Dos 140 pares de faixas, **99 têm t50 principal identificado** e
**78 desses têm IC95 conservador**. Há 6
medianas conhecidas acima de 120 minutos; o orçamento não as substitui por 120.
As ausências por motivo constam de [interpretation.json](interpretation.json).

Exemplos por faixas [a,b), em minutos. N inclui eventos e censuras; o IC usa
grupos inteiros e não estima incerteza de ajuste dos modelos.

| oldRating | Problema | N | Eventos | Censuras | Grupos | t50 | IC95 | Motivo |
|---|---|---:|---:|---:|---:|---:|---|---|
| [1200,1600) | [800,1000) | 862934 | 855521 | 7413 | 216 | 9.65 | [9.20, 10.13] |  |
| [1200,1600) | [1200,1400) | 245784 | 213030 | 32754 | 95 | 37.17 | [35.15, 39.40] |  |
| [1200,1600) | [1600,1800) | 79093 | 44774 | 34319 | 76 | 73.15 | [66.60, 81.73] |  |
| [1200,1600) | [2000,2200) | 11055 | 3024 | 8031 | 67 | 140.70 | ausente |  |
| [1200,1600) | [2400,2600) | 813 | 190 | 623 | 76 | ausente | ausente | quantile_not_reached_in_followup |
| [1200,1600) | [2800,3000) | 33 | 9 | 24 | 18 | ausente | ausente | insufficient_support:attempts,events |
| [1600,2000) | [800,1000) | 338681 | 338261 | 420 | 216 | 5.77 | [5.53, 6.05] |  |
| [1600,2000) | [1200,1400) | 119295 | 117048 | 2247 | 112 | 17.95 | [17.07, 19.00] |  |
| [1600,2000) | [1600,1800) | 73906 | 64517 | 9389 | 94 | 36.93 | [34.82, 39.69] |  |
| [1600,2000) | [2000,2200) | 29494 | 16916 | 12578 | 80 | 68.65 | [61.51, 76.79] |  |
| [1600,2000) | [2400,2600) | 5477 | 1441 | 4036 | 111 | 125.18 | ausente |  |
| [1600,2000) | [2800,3000) | 166 | 26 | 140 | 38 | 104.15 | ausente |  |

As curvas também apresentam reversões nas regiões difíceis. Para oldRating
[800,1200), t50 passa de 127.72 min no problema
[1800,2000) para 59.20 min em [2400,2600),
enquanto N cai de 5281 para 214.
Na primeira dessas células, 39/200
réplicas não identificam t50, impedindo o IC principal. Cada bin reúne quem
tentou aqueles problemas e manteve um prefixo válido; a composição muda entre
bins, com abandono e censura. Essa reversão descritiva não estabelece uma
redução intrínseca de dificuldade. Mantivemos os valores e as lacunas, sem
impor monotonicidade.

## Estabilidade de direção e disponibilidade

Para cada grade congelada, comparamos as faixas que começam em 1200 e 1600
no mesmo bin de problema, contando apenas pares com as duas medianas
identificadas. A largura do usuário muda a população; esta tabela descreve
persistência de direção, não igualdade dos estimandos nem um teste de hipótese.

| Largura usuário | Largura problema | Pares identificados | t50 menor na faixa 1600 | Igual | Maior | Pares indisponíveis |
|---:|---:|---:|---:|---:|---:|---:|
| 200 | 100 | 14 | 14 | 0 | 0 | 14 |
| 200 | 200 | 8 | 8 | 0 | 0 | 6 |
| 200 | 400 | 4 | 4 | 0 | 0 | 3 |
| 400 | 100 | 16 | 15 | 0 | 1 | 12 |
| 400 | 200 | 9 | 8 | 0 | 1 | 5 |
| 400 | 400 | 4 | 4 | 0 | 0 | 3 |

Na célula pré-escolhida **usuário [1200,1600), problema [1600,1800)**,
t50 é 73.15 min. Retirando um grupo por vez, as estimativas
identificadas variam de 71.52 a
74.12 min, com 0
retiradas sem quantil. Das 200 réplicas, 0
não identificaram mediana. Valores individuais por grupo, retiradas e réplicas
estão retidos no JSON; não foram descartados para fabricar um intervalo.

Sensibilidade de pisos muda a disponibilidade, não recalcula a mediana das
mesmas observações. Na grade principal:

| Piso N/eventos/grupos | Células identificadas |
|---|---:|
| 50/20/5 (principal) | 99 |
| 25/10/3 | 110 |
| 100/40/10 | 86 |

## Comparadores e janelas

Na célula acima, comparação por mistura sobre **o mesmo suporte comum**:

| N total | N comum | Cobertura | KM observado comum | AFT | KM delta | KM global |
|---:|---:|---:|---:|---:|---:|---:|
| 79093 | 79093 | 100.000% | 73.15 | 72.62 | 60.20 | 21.03 |

Estas diferenças de medianas descrevem este snapshot. Não são MAE/Brier,
não testam calibração individual e não estabelecem superioridade. Os modelos
foram congelados; dados anteriores de desenvolvimento/validação/teste estão
sobrepostos e já foram examinados.

Para a mesma célula, tempo sequencial tem t50 73.15 min;
o estimando alternativo até a última submissão tem
66.37 min,
e desde o início do contest tem
118.43 min.
São relógios/populações distintos; nenhum mede dedicação exclusiva.

Pessoas repetidas entre grupos, seleção de prefixos, censura potencialmente
informativa, rating do problema disponível na coleta e aliases desconhecidos
limitam a interpretação. Regiões sem suporte/quantil/IC não sustentam comparação.
Nenhum resultado demonstra eficácia pedagógica, causalidade ou equivalência
entre Rating Lorem e Codeforces; igualdade Elo não fixa probabilidade por tempo.
