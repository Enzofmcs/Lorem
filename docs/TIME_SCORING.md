# Política de pontos por curva de tempo — decisão v1

> **20/10/2026:** este contrato `time-curve-v1` continua preservado e desativado.
> O [novo estudo](REDIRECTION.md) planeja `analysis-time-bands-v2` (P25/P50/P75 e
> orçamento h). Seus T1/T2/T3/T4 não rebatizam os campos p50/p75/p90 desta política
> nem alteram snapshots, Rules ou pontuação. Integração exige decisão futura.

## Decisão anterior à implementação

T1 é a referência do par de ratings; T2 inicia a resolução lenta; T3 encerra
qualquer ganho positivo. As faixas são [0,T1), [T1,T2), [T2,T3) e [T3,+infinito).
A penalidade sem AC continua D-012, independente de tempo, curva ou dica.
O domínio recebe timestamps de época em milissegundos: duração de pontuação é
`AC.toMillis() - início.toMillis()`. Rules usam a mesma resolução, inclusive se
o servidor registra frações de milissegundo. A elegibilidade e o prazo absoluto
continuam comparando timestamps completos; essa resolução não estende as 2h.

Investigamos p50/p75/p90 como candidatos interpretáveis da distribuição de tempo
até AC **incluindo censura**, e não percentis somente dos resolvidos. Adotamos esses
candidatos na versão experimental `time-curve-v1`; não há evidência para afirmar
que são ótimos pedagogicamente. Empates que não produzam T1<T2<T3 tornam a região
indisponível. Não deslocamos marcos artificialmente para fabricar quatro faixas.
T3=min(p90,7200000ms) quando p90 é identificado; p90 ausente não vira 120 minutos.
Somente quantis dentro do horizonte observado são aceitos; se o suporte termina
antes, a indisponibilidade é explícita. Se o teto colidir com T1/T2, indisponível.

A consulta real retida em `study/results/2026-10-18/report/demo.json` mostra para
CF anterior 1200/problema 1600, KM por delta: n=9969, p(120)=0,622099,
p50=91,4667 min. Portanto **p75 e p90 não são identificados até 120 min**. AFT
apresenta p(120)=0,570021, p50=101,9328 min e p90 ausente. Esse exemplo não autoriza
T2/T3 nem valida o modelo. Não inferimos p75 interpolando probabilidades esparsas.

## Artefato e escala

`time-curves-v1.json` registra esquema, versão de modelo/política, hash da fonte,
origem do rating, agrupamento, domínio, probabilidades, quantis, suporte e razões.
O exportador aceita o KM congelado de treino: bins de delta já existentes,
sem retreino, sem fallback global e sem ler rótulos de validação/teste. Limites
inteiros em ms usam ceil para não antecipar uma fronteira. Cada região exige
mínimos do modelo, domínio e marcos ordenados; fora deles retorna indisponibilidade.
Os mínimos amostrais são filtros de software, não evidência de precisão.

O checkout contém o relatório real, mas não a tabela KM completa. O artefato
incluído preserva **somente a consulta exata 1200/1600**, com limites parciais e
indisponibilidade. Nunca generaliza essa consulta para todo delta=400. O comando
`export-time-curves` exporta todas as regiões do modelo completo quando fornecido.
Fixtures sintéticas ficam exclusivamente nos testes.

O rating de entrada é `codeforces_prior`, nunca o Rating Lorem. O início de
vínculo em 800 é saldo interno. O CF observado no início é registrado com sua
origem `codeforces:user.info`; transportá-lo de contests para treino individual
exige avaliação própria. Não existe mapeamento autorizado de Lorem para CF.

## Pontuação experimental e fallback

Ganho base: round(8+32*(1-E)), E=1/(1+10^((problema-Lorem)/400)), como D-012,
sem o fator antigo de tempo. Multiplicadores candidatos: bônus 1,25; normal 1;
redução 0,5; em T3 ou depois 0. Arredondamento half-up; dica subtrai
min(ganho temporal,max(2,round(25% do ganho temporal))). Ganho final pode ser zero.
Bônus pode atingir 50; não há cap antigo de 40 que anularia bônus em problemas difíceis.
A função recebe multiplicadores/versionamento; v1 admite bônus [1,1,25] e redução
[0,1], evitando entradas não finitas/ganhos ilimitados no contrato persistido.

O início salva versão, limites, multiplicadores, rating de entrada/origem, modelo,
artefato e motivo do fallback. Sem curva válida, sem CF anterior, escala errada ou
ativação desabilitada: **D-012 integral**, inclusive seu fator de tempo e mínimo 1.
Essa escolha preserva o produto enquanto a alternativa é avaliada. Snapshot
antigo sem política continua D-012. Snapshot novo malformado/versão desconhecida
falha explicitamente; não reinterpreta treino em andamento. Atualização do
artefato só afeta novos inícios. Avaliação lê exclusivamente o snapshot; resultado
salvo contém decomposição, não a recalcula na UI. Transação e estado FINAL
continuam garantindo aplicação única; upsolve altera apenas classificação.
Arquivo Room legado continua somente leitura, sem migração/reavaliação.

## Ativação

**Desabilitada em produção nesta versão.** A entrega implementa o caminho completo
e o fallback persistido. Não há coorte de Ipsums nem evidência de transporte, e o
modelo AFT não superou a referência no teste já examinado. Esta decisão substitui
a exclusão de desenvolvimento da antiga história 32 apenas no escopo de software
solicitado; não declara validada a integração estatística. Os multiplicadores
são hipóteses. Não usamos o teste reservado para ajustá-los. O relatório de cenários
separa invariantes mecânicos de adequação empírica, que permanece não demonstrada.

## Compatibilidade de publicação

As novas Rules aceitam snapshots antigos sem `scoring`, preservando seu mínimo 1,
e snapshots novos D-012 com motivo explícito. A política por curva exige
`timeGain` coerente com a faixa, o multiplicador, a dica e o delta. Um cliente
antigo não consegue gravar resultado D-012 para um AC regido pela curva. O campo
`expectedTime` continua como metadado de compatibilidade D-012; os três marcos da
nova política ficam em `snapshot.scoring.limits` e são a fonte de sua pontuação.

**Publicar as Rules novas antes de distribuir este cliente**: mesmo desabilitada,
a seleção de fallback acrescenta `snapshot.scoring`. Sem essa publicação, as Rules
anteriores recusam novos inícios com esse campo. Não houve publicação automática.
`ruleVersion=spark-2h-v1` continua descrevendo prazo/identidade; a versão de
pontuação está separada, em `scoring.policyVersion`. Export cloud v2 recebe campos
aditivos de snapshot/resultado; coorte local histórica permanece separada.
