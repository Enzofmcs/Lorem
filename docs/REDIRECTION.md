# Nova direção do estudo e recuperação da experiência do Lorem

## Decisão de 20 de outubro de 2026

O próximo bloco prioriza duas entregas: uma investigação visual e reproduzível do
tempo até AC no Codeforces em Jupyter, e a recuperação das capacidades de interface
que ficaram fora da jornada Firebase. O estudo principal passa a descrever tempos
por rating do participante e do problema. Desenvolver uma IA melhor ou reunir uma
coorte de usuários do Ipsum deixa de ser condição para essa entrega acadêmica.

Este documento registra **planejamento**, não implementação. As histórias 40–49
ficam pendentes em [ROADMAP.md](ROADMAP.md). A base inspecionada é a main
`73c322af1b7eca111d41d255b23d138a19c4e554`, com a história 39 integrada.

## Por que mudar

O pipeline atual já coleta, prepara e analisa dados com censura, mas sua entrada
por CLI e relatórios fixos dificulta explorar e apresentar os resultados. A
comparação AFT versus referências é preservada como resultado anterior. O novo
centro é a pergunta: **para participantes com rating anterior próximo de x, qual é
a distribuição do tempo observado até AC em problemas de rating y durante contests?**

O estudo de 18/10 registra 274.073 tentativas válidas em 19 contests. O teste de
quatro grupos já foi examinado. Volume de linhas não representa igual número de
observações independentes nem valida todas as regiões x/y. Não retunar esse teste.

Na interface, as histórias 18–21 produziram telas completas. A história 38/PR 70
tornou Firebase padrão e passou a abrir `FirebaseLoginApp`/`CloudTrainingDestination`,
sem levar toda a navegação, filtros, gráficos e resultados do `LoremNavHost`.
As telas anteriores continuam no código. O ponto de recuperação é adaptar essas
capacidades à fonte cloud, preservando isolamento e integridade. O marco `931c410`
serve como referência de experiência, não como rollback do armazenamento.

## Contrato científico da nova análise

- Unidade: tentativa participante/problema/contest com submissão oficial e filtros
  válidos; ausência de submissão não significa tentativa fracassada.
- x é `oldRating` anterior ao contest, nunca rating atual, `newRating` ou Lorem.
  y é rating do problema observado na coleta, com essa limitação declarada.
- T é aproximação sequencial de tempo de parede. A=10min e B=28min resulta em
  18min para B. A API não informa início real de leitura/dedicação exclusiva.
- Preservar filtros de origem/data, participantes oficiais individuais e exclusão
  integral de contests especiais ou com versões fácil/difícil. Mostrar cobertura.
- Sem AC em janela válida é censura à direita; abandono pode ser informativo.
  Exibir a hipótese e os limites do KM, sem transformar censura em tempo de falha.
- Comparar faixas de x/y com bins de delta. A curva antiga por delta de 400 pontos
  não é uma estimativa exclusiva de um par exato; não usar fallback global oculto.
- Mostrar eventos, censuras, participantes, problemas, contests, em risco e
  incerteza. Célula esparsa ou sem seguimento fica indisponível com motivo.
- Resultados exploratórios no snapshot existente continuam descritivos. Nova
  alegação de previsão requer partição nova não examinada e grupos sem problemas
  compartilhados entre ajuste/teste. Participantes repetidos são limitação explícita.

## Quartis e orçamento propostos

O contrato **analítico**, a implementar na história 47, será
`analysis-time-bands-v2`: T1=P25, T2=P50, T3=P75 e T4=h (orçamento configurável,
120min na demonstração). T4 não é P100 nem quartil estatístico.

| Referência | Interpretação |
| --- | --- |
| t < P25 | Muito rápido em relação à distribuição de referência |
| P25 ≤ t < P50 | Mais rápido que a mediana |
| P50 ≤ t < P75 | Metade mais lenta da região central |
| P75 ≤ t ≤ h, quando P75 ≤ h | Lento em relação à referência, dentro do orçamento |
| t > h ou sem AC até h | Fora do orçamento ou AC não observado no prazo |

A região central usual é P25–P75. Não resolveu no prazo não significa incapacidade
permanente. AC exatamente em h continua elegível quando observado, coerente com a
fronteira inclusiva do app; sem AC em h permanece observação censurada.

Quantil não identificado fica null com razão. Quantis empatados não são afastados
artificialmente; se ultrapassarem h, mostrar marcos parciais e incompatibilidade
do orçamento. Não exigir uma escala completa para todas as células. Percentis só
dos resolvidos respondem a pergunta condicional distinta e ficam separados.

O exemplo antigo 1200/1600 retorna, pelo bin de delta, F(120)=0,622099 e mediana
91,4667min. P75/P90 não são identificados até 120min. Três probabilidades e uma
mediana não reconstroem a curva completa nem a distribuição exclusiva desse par.

**Compatibilidade:** `time-curve-v1` da história 39 mantém T1=P50, T2=P75,
T3=min(P90,2h), snapshots e semântica originais. A nova análise não reinterpreta
esses campos, não altera pontos ou Rules e não habilita a política experimental.
Integração futura exigirá decisão e validação próprias. D-012 segue no produto.

## Entregas Jupyter

| História | Notebook | Finalidade |
| --- | --- | --- |
| 44 | 00_dados_e_reproducao.ipynb | Ambiente, inventário, dados e reprodução |
| 45 | 01_qualidade_da_amostra.ipynb | Funil, sequência, tempos e censura |
| 46 | 02_ratings_e_tempos.ipynb | Estratos x/y, mapas de calor e delta |
| 47 | 03_curvas_e_quantis.ipynb | Curvas, quartis, orçamento e indisponibilidade |
| 48 | 04_estabilidade_e_limites.ipynb | Incerteza, sensibilidade e comparação histórica |
| 49 | 05_demonstracao.ipynb | Consulta interativa, exports e narrativa do TCC |

Os notebooks orquestram módulos de `lorem_study`; não duplicam sua implementação.
Coleta/preparo, baselines, AFT/avaliação/relatório terão seções de execução ou
referências reproduzíveis. Exportação Ipsum/transporte e simulações de pontos
permanecem em protocolos/apêndices opcionais, com n=0 real ou cenário sintético
claramente identificado. O histórico da 31 retirada do escopo é preservado.

O Git contém agregados, mas não o snapshot bruto nem o KM completo. A história 44
deve restaurá-los por caminhos explícitos e hashes ou registrar coleta opt-in em
nova execução. O modo agregado apresenta o que existe; não inventa microdados ou
novas curvas. Fixture serve à verificação técnica, nunca à conclusão empírica.
Run All é offline por padrão e não dispara coleta/retreino silenciosos.

## Recuperação da experiência em nuvem

| Recurso a recuperar | História |
| --- | --- |
| Contrato cliente/Rules e diagnóstico de configuração | 40 |
| Navegação, Home, Ipsum e resultado dedicados | 41 |
| Histórico/filtros, gráficos, consolidação e tema | 42 |
| Preparação compreensível, rede controlada e atualização de pendências | 43 |

Manter login Google, vários handles, inicial 800 por novo vínculo, saldo existente,
isolamento uid/handle, arquivo local somente leitura, exclusões, prazo absoluto,
resultado único e upsolve sem recalcular rating. Essas decisões não são revertidas
pela revisão de UX. O rating inicial e o arquivo separado devem ser explicados.

A história 39 acrescenta `snapshot.scoring` até no fallback. Rules anteriores
podem rejeitar novos inícios. Conferir compatibilidade em emulador e evidência de
publicação antes de atribuir o erro ao treino; não alegar estado remoto ou deploy
sem observação. Não há deploy autorizado por este planejamento.

## Ordem e conclusão

- Aplicativo: 40 → 41 → 42; 43 depende da 41 e pode seguir antes/depois da 42.
- Estudo: 44 → 45 → 46 → 47 → 48 → 49, independente de login/Firebase/coorte Ipsum.
- O estudo pode entregar descrição e limites sem uma nova avaliação preditiva;
  resultados novos exigem dados reais e identificação da execução.
- Cada história fecha somente critérios comprovados, com teste proporcional ao
  risco conforme TESTING.md. Não rodar Gradle para este planejamento documental.
- Preservar relatórios/partições/decisões antigos. Registrar evidência nova em nova
  execução e atualizar issues/roadmap sem confundir planejamento com conclusão.

As decisões D-045 a D-047 registram a direção. Histórias detalhadas, dependências e
links de acompanhamento ficam no [roadmap](ROADMAP.md).
