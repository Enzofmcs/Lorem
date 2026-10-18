# AFT v1: metodo de ajuste e selecao

Estas regras foram fixadas na implementacao da Historia 26, antes do ajuste da
amostra real. Naquele marco o teste permaneceu reservado para a Historia 27. O modelo nao muda
o aplicativo Android nem a D-012.

## Dependencias e verossimilhanca

NumPy 2.3.5 e SciPy 1.17.0, fixados em `requirements-model.lock`, sao dependencias
opcionais somente de AFT/scoring. Coleta, preparo e Kaplan-Meier continuam usando
stdlib. SciPy fornece `log_ndtr`, CDF/inversa normal e L-BFGS-B; a verossimilhanca
de sobrevivencia e pequena, explicita e testada por gradientes numericos e dados
sinteticos com censura. Evita acrescentar uma pilha completa de processamento
tabular/sobrevivencia para dois modelos de quatro parametros.

Unidade: minutos. Para tempo observado `y > 0`, evento `e` em {0,1} e `mu = X beta`:

- Log-normal: `z=(log(y)-mu)/sigma`, `log f=-log(y)-log(sigma)-z²/2-log(2*pi)/2`,
  `log S=log_ndtr(-z)`.
- Weibull AFT: `z=(log(y)-mu)/sigma`, `log f=z-exp(z)-log(sigma)-log(y)`,
  `log S=-exp(z)`. Shape Weibull = `1/sigma`.
- Minimizar a media de `-e*log(f)-(1-e)*log(S)` mais
  `lambda*(beta_old²+beta_delta²)/2`. Censuras validas entram no ajuste; intercepto
  e escala nao recebem penalidade.

Entradas: `oldRating/100` e `delta/100`, centradas pelas medias **do treino**.
O terceiro rating e mantido na base, sem entrar como termo colinear. Rank, AC,
score, erros, ids, duracao realizada e timestamps nao sao preditores.
`exp(beta_old)` e fator de tempo por +100 em oldRating mantendo delta;
`exp(beta_delta)` e por +100 em delta mantendo oldRating. Nao sao efeitos causais.

Exigir >=20 tentativas validas, >=5 eventos e matriz de desenho de posto completo.
L-BFGS-B usa maxiter 500, ftol 1e-11, gtol 1e-6; limites numericos de intercepto
[-20,20], slopes [-5,5], log(sigma) [-3,3]. As coordenadas do otimizador usam desvio
padrao do treino para estabilidade; os coeficientes exportados retornam a unidade
de 100 pontos. Exigir gradiente absoluto maximo <=1e-3 na escala exportada,
independentemente da mensagem de sucesso do otimizador.
Solucao no limite, falha de convergencia,
parametro nao finito ou uso do guard de overflow Weibull no ajuste final torna
o candidato indisponivel, com motivo. O guard atua somente no line-search, sem
modificar uma solucao aceita. Nao substituir falha por previsao fixa.

## Particoes e selecao

Usar `splits.json` congelado, minimo de 6/2/2 contests completos e elegiveis.
Contests e grupos compartilhados ficam inteiros em uma particao. Verificar hashes
das tabelas e joins. Ajuste e curva de censura usam somente treino. A validacao
escolhe o candidato; nenhum rotulo do teste participa da escolha.

Grade predefinida: log-normal/Weibull x lambda {0,0.01,0.1}.
Tres folds agrupados dentro do treino quando houver >=3 grupos e suporte minimo
de ajuste em cada treino de fold; transformacoes/censura sao refeitas por fold.
Falhas de folds ficam explicitas no diagnostico.

Escolha primaria: menor Brier IPCW integrado na validacao entre candidatos com
calibracao aceitavel. Grade de integracao: 0 a 120 min em passos de 5, restrita
ao seguimento do treino e suporte de censura. Integracao por trapezios normalizada
pelo intervalo suportado. Desempates: Brier medio dos folds, MAE nos eventos da
validacao, preferencia por log-normal e maior regularizacao.

Calibracao: ECE em cinco bins fixos de probabilidade, nos casos conhecidos de
30/60/120 min; media nos horizontes suportados. Exigir ECE <= ECE da referencia
KM global do treino +0,05. Esse subconjunto tem vies de selecao, declarado no
artefato; nao substitui avaliacao cientifica/calibracao IPCW da Historia 27.
Se nenhum candidato passar, exportar apenas modelo diagnostico com gate falho;
consultas publicadas retornam probabilidades indisponiveis. O fato de um AFT
convergir nao comprova que supera o baseline.

## IPCW interno

Curva de censura reversa ajustada no treino com eventos antes de censuras em
empates, conforme o contrato de dados. Para um horizonte `b`:

- Evento em `y <= b`: erro `(1-p)²/G(y-)`.
- Sem AC observado ate `b`, incluindo censura em `y=b`: erro `p²/G(b-)`.
- Censura em `y < b`: desfecho desconhecido; contribuicao zero, mantendo o
  denominador de todas as linhas de validacao.

O limite esquerdo corresponde a observar censura em ou apos o horizonte, conforme
o protocolo. Se G relevante <0,05, declarar horizonte indisponivel; nao truncar
pesos extremos nem transformar desconhecidos em negativos. Horizontes alem do
seguimento do treino tambem sao indisponiveis. Reportar contagens conhecidas e
desconhecidas, G, intervalo de integracao e denominador.

## Exportacao e limites

Guardar modelo, coeficientes/fatores, escala, dominio/min-max do treino, hash de
treino/validacao, grade/diagnosticos, versoes, dependencias e hash do artefato.
Exportar previsoes separadas por particao, sem rotulos ou resultados realizados.
O teste pode receber previsoes depois de congelar o modelo; seus rotulos ficam
reservados para a avaliacao posterior.

CDF: log-normal `Phi(z)`; Weibull `1-exp(-exp(z))`. Quantis:
`exp(mu+sigma*Phi^-1(q))` ou `exp(mu+sigma*log(-log(1-q)))`.
Mediana e intervalo preditivo 10/90 sao minutos, nao intervalos de confianca.
Quantis/horizontes alem do seguimento sao `null` com motivo; ratings fora do
dominio observado recebem sinalizacao de extrapolacao. CDF usa funcoes estaveis,
sem esconder probabilidades invalidas por clamp.

Censura condicionalmente independente, forma parametrica, parede versus dedicacao,
selecao de prefixos e repeticao de participantes sao hipoteses/limitacoes. Precisao,
ICs, comparacao reservada com todas as referencias e transporte para Ipsums ficam
nas Historias 27-31.

## Avaliacao reservada v1 (Historias 27-28)

`evaluate` grava `evaluation/plan.json` antes de ler rotulos do teste. Vincula hashes
exatos dos modelos, dados e particoes; uma repeticao com entradas diferentes e
recusada. O candidato ja escolhido na 26 permanece fixo. Referencias: KM global,
KM por delta/fallback e D-012 somente para tempo. Nenhum ajuste usa teste.

Tempos usam mediana publicada, somente em eventos com previsao suportada. MAE,
mediana absoluta, RMSE e vies `previsto-observado` exibem denominadores. Quantis
10/90 indisponiveis nao viram infinito; cobertura/largura de 80% usam eventos com
ambas as pontas disponiveis. Comparacoes pareadas restringem ambos os modelos ao
mesmo subconjunto de previsoes disponiveis, inclusive tempo.

Brier/log-loss conhecidos, calibracao em cinco bins `[0,.2),...,[.8,1]` e
estimativas IPCW usam 30/60/120 minutos. Log-loss usa epsilon `1e-15` somente no
logaritmo; probabilidades publicadas nao mudam. Calibracao conhecida e selecionada;
a fracao de eventos IPCW por bin inclui desconhecidos no denominador, pode exceder
um e nao e truncada. G usa treino, limite esquerdo e minimo 0,05 definidos na 26.
Integracao usa o prefixo continuo suportado da grade 0-120/5 minutos; nenhuma
integracao atravessa horizontes sem suporte. Cada horizonte informa G, peso maximo,
conhecidos/desconhecidos e disponibilidade.

Bootstrap: 200 sorteios com reposicao de grupos inteiros reservados, PCG64/seed do
split. Mesmos sorteios para todos os modelos, curvas de censura fixas do treino,
sem refit. Cada sorteio preserva todas as linhas dos grupos escolhidos com sua
multiplicidade; metricas sao ponderadas por tentativa, nao media simples de
contests. IC percentil 2,5/97,5%, inclusive diferencas e calibracao por bin. Exigir
>=90% de replicacoes validas para publicar IC e informar quantidade. Poucos grupos
produzem ICs instaveis; nao incluem incerteza de ajuste nem eliminam dependencia
por participantes repetidos entre contests. Intervalo preditivo e IC sao distintos.

Fatias fixas de 400 pontos para ambos os ratings e delta, qualidade/exclusoes com
funil. Sensibilidade alternativa encurta somente censuras ate ultima submissao
positiva conhecida: mantem previsoes/modelos, reestima G no treino alternativo e
reavalia observacoes do teste, sem selecionar modelo/regra pelo resultado. Ausencia
de janela alternativa fica excluida e contada. Nao e novo tempo de dedicacao.
