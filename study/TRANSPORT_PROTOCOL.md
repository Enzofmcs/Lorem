# Pre-registro de transporte Ipsum v1 — Historia 31

Fixado em 19/10/2026 **antes de qualquer coorte real de Ipsums**. Implementado em
[`transport.py`](lorem_study/transport.py) e [`transport_stats.py`](lorem_study/transport_stats.py).
O resultado atual e `not_collected`/`insufficient_data`, n=0. Este protocolo define
um experimento; nao comprova transporte nem autoriza a Historia 32.

## Referencia e limites escolhidos antes do teste

A [Historia 28](results/2026-10-18/report/REPORT.md) encontrou MAE de 15,135 min e
Brier integrado de 0,122746 para KM por delta; AFT teve 0,126693. Sao quatro grupos
de contests, outro dominio e erros de tempo apenas nos resolvidos. Nao usar esses
numeros para calcular poder como se fossem variancia de participantes em Ipsums.

Limites operacionais v1, definidos sem olhar desfechos de Ipsums:

| Condicao | Exigencia no teste |
|---|---|
| MAE nos eventos com mediana suportada | Limite superior do IC95% <=20 min; largura do IC <=10 min |
| Brier IPCW integrado | Grade 0–120 min, passo 5; limite superior do IC95% <=0,15; largura <=0,05 |
| Calibracao IPCW | ECE em cinco bins fixos, em cada orcamento 30/60/120; limite superior do IC95% <=0,10; largura <=0,10 |
| Dominio | Pelo menos 90% das tentativas dentro dos limites de rating/delta do treino do modelo de contest |
| Cobertura da mediana | Pelo menos 95% dos eventos com mediana disponivel; nao melhorar MAE descartando a maioria |
| Seguimento/censura | Todo o intervalo 0–120 suportado, sem unir lacunas; G(t-)>=0,05 |

20 min arredonda para cima a referencia de 15,135 e permite aproximadamente cinco
minutos adicionais de erro medio no novo dominio. 0,15 limita a degradacao
absoluta do Brier integrado a aproximadamente 0,027 em relacao a referencia;
nao e margem inferida por teste de nao inferioridade entre dominios. ECE de 0,10
limita a dez pontos percentuais a discrepancia media ponderada dos bins; pode
ocultar heterogeneidade, por isso publicar todos os bins e seus denominadores.
As larguras exigem precisao adicional ao simples cumprimento da estimativa pontual.
Sao criterios de triagem para uma decisao de dominio posterior, nao garantias de
utilidade pedagogica nem novos limites de pontuacao do app.

## Coorte e parada

Piso final: **100 participantes, 2.000 tentativas validas, 1.000 eventos**.
Manter o split inteiro por participante/hash/seed da Historia 30, alvo 60/20/20.
Exigir por particao ao menos **60/20/20 participantes, 1.200/400/400 tentativas e
600/200/200 eventos**. Vinte unidades reservadas permitem uma avaliacao por
participantes mais informativa que os quatro contests da 28, mas ainda nao
garantem poder, representatividade ou independencia pessoal. As larguras dos ICs
sao requisitos separados; atingir contagens nao aprova o gate.

Definir/congelar os arquivos e participantes antes da avaliacao; sem selecionar
ou substituir participantes por erros observados. Contagens insuficientes nao
produzem mapeamento nem metricas. O preparo da 30 congela ja no piso inicial
10/200/100: para a coorte final usar um diretorio novo, sem retunar em participantes
cujo teste ja foi avaliado. Aumentar amostra por observar erros exige outro
experimento prospectivo; nao reabrir o mesmo teste alterando limiares.

Aplicar os limites tanto a todo o teste quanto ao subconjunto **sem dica**, que
deve ter >=20 participantes, 200 tentativas e 100 eventos. Com dica e estado de
interrupcoes registrado/desconhecido sao diagnosticos separados. O export atual
nao mede pausas: ausencia e desconhecida, nunca zero; nao concluir sobre tempo
efetivo ou transporte com pausas. Hints sao estratos posteriores, nao preditores.

## Modelos e mapeamento

Reusar **exatamente** os artefatos KM por delta e AFT da 28, identificados pelos
hashes no plano. Nao retreinar distribuicao, faixas ou hiperparametros com Ipsums.
Congelar esses hashes e o protocolo antes de ler tentativas locais. CF positivo
deve ter sido observado antes do inicio; Lorem e o snapshot inicial. Preservar
ausencias, exclusoes D-026 e corte verificavel da 30. As tabelas preparadas e
particoes tem hashes vinculados e verificacao da ordem de participantes.

Comparar as quatro combinacoes de modelo com:

- Identidade: usar Lorem inicial como entrada numerica de rating, sem alegar equivalencia.
- Linear: `CF_estimado = intercepto + inclinacao * Lorem_inicial`, minimos quadrados
  apenas nos pares anteriores ao inicio do treino. Cada participante tem peso
  total igual, mesmo com mais tentativas; inclinacao nao positiva ou variancia
  nula torna o ajuste indisponivel. Nao arredondar ou limitar artificialmente.

Escolher na validacao pelo menor Brier IPCW integrado suportado em 0–120,
respeitando cobertura de dominio/mediana. Empates exatos preferem identidade,
depois KM por delta. CF anterior como entrada e D-012 com Lorem sao referencias
diagnosticas; nao podem vencer essa selecao. Nenhum dado futuro, dica, pausa,
identidade ou desfecho entra no mapeamento. Salvar `selection.json` imutavel antes
de calcular previsoes e erros do teste. Se nenhum candidato tiver suporte, nao
avaliar o teste.

## Metricas e incerteza

Relatar pares CF/Lorem em treino, validacao e teste: vies/diferenca absoluta com
IC95%, desvio/quantis da diferenca, Pearson com IC e suporte. Correlacao alta nao
e equivalencia; medidas por tentativa ponderam quem tem mais tentativas, enquanto
o ajuste linear da peso igual por participante. Bootstrap sempre agrupa a pessoa.

Reusar MAE/mediana/RMSE/vies, quantis preditivos e probabilidades/censura da 27.
A curva reversa KM de censura usa **apenas Ipsums do treino**, sem modificar a
distribuicao do modelo de contest. ECE IPCW soma por bin a diferenca entre eventos
ponderados e probabilidades e calcula os valores absolutos a cada reamostragem;
nao reamostrar linhas nem tirar media de erros absolutos por linha. Publicar
tambem Brier/log-loss nos casos conhecidos e calibracao conhecida, com seu vies
de selecao. ICs percentis com 1.000 replicacoes pareadas por participante,
seed 20261019; exigir >=90% replicacoes validas. ICs condicionam os ajustes de
modelo/mapeamento/censura, sem incluir sua variabilidade de treinamento.

Comparacoes selecionado–identidade e selecionado–CF usam as mesmas linhas e
horizonte comum para MAE/Brier. Mostrar todos os candidatos no teste, mas avaliar
o gate apenas para o escolhido na validacao; nunca trocar pelo vencedor do teste.
Problemas compartilhados, censura informativa/desistencia, mudanca de pseudonimo,
selecao de participantes e tempo de parede limitam as conclusoes. As probabilidades
podem depender de G estimado no treino representar o teste, hipotese nao comprovada.

## Decisao rastreavel

Sem dados, sem suporte, falha de precisao/limite ou somente fixture => **nao
integrar**; ratings distintos e D-012 preservados. O gate do app permanece fechado
em todas as saidas desta ferramenta. Mesmo um resultado numerico favoravel em
dados reais apenas permite uma decisao explicita da 31 sobre dominio e adequacao
do mapeamento; nao habilita automaticamente a 32. Nenhum requisito de teste manual
e usado para substituir evidencia cientifica.

Saidas locais: `plan.json`, `cohort.json` quando houver particoes, `selection.json`
quando houver amostra final, `metrics.json` quando a avaliacao for possivel,
`manifest.json` e `REPORT.md`. Nao publicar exportacoes nem pseudonimos; os dados
individuais e arquivos locais ficam fora do Git. A evidencia atual publicada
contem apenas protocolo, hashes e estado vazio, sem metricas ficticias.
