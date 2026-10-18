# Estudo quantitativo de tempo e AC

Planejamento de 18/10/2026; **avaliacao preditiva com dados reais ainda nao executada**.
Software 22-26 verificado com 51 testes. Nova amostra: 20 contests coletados,
19 elegiveis, 274.073 tentativas validas; baselines e AFT ajustados com particoes
11/4/4. Weibull 0,1 selecionado na validacao. [Evidencia atual](../study/evidence/2026-10-18-sample-aft.json)
e [metodo AFT](../study/MODEL.md). Precisao/teste reservado continuam na 27.
O [piloto anterior](../study/evidence/2026-10-18-pilot-real.json), com variantes
B1/B2 e E1/E2, permanece preservado e excluido.
[Guia Python](../study/README.md) e [contrato](../study/DATA_DICTIONARY.md). Este protocolo
orienta as Historias 22-28 (primeira versao em aproximadamente 24 h de trabalho) e
29-32 (aplicacao e validacao posteriores). Progresso e issues em [ROADMAP.md](ROADMAP.md).
As Historias 00-21 permanecem concluidas. A D-012 continua sendo a regra em producao.

## Pergunta, estimando e limites

A partir do rating do participante **antes do contest** e do rating do problema,
estimar a distribuicao do tempo ate o primeiro AC e `P(T <= b)`, com orcamento
explicito `b` em minutos (inicialmente 30, 60 e 120). Entregar mediana e intervalo
preditivo de 80% (quantis 10/90), quando identificaveis, junto das probabilidades.
Intervalo preditivo entre tentativas e intervalo de confianca da estimativa sao
incertezas diferentes e devem ser apresentados separadamente.

`T` e uma **aproximacao de tempo decorrido sob a hipotese sequencial**, condicionada
a uma tentativa observada e aos filtros abaixo. Nao mede atividade cognitiva,
tempo de teclado nem a chance de um participante qualquer escolher tentar um problema.
A selecao de prefixos sequenciais pode favorecer determinados competidores; medir
sua cobertura. Precisao, calibracao e transportabilidade para treino sao perguntas
de pesquisa, nao resultados garantidos. Nao interpretar ausencia de AC como
incapacidade permanente nem usar apenas resolvidos como populacao do modelo.

Comparacao entre epocas, analises por ano e correcao temporal dos ratings estao
fora do escopo. Datas servem para elegibilidade e delimitacao do contest; nao sao
atributos do modelo. O estudo deve fortalecer o TCC com codigo Python reproduzivel,
protocolo, medidas de erro e discussao de validade; nao comprova ganho pedagogico.

## Populacao, corte e proveniencia

- Problemas identificados por `contestId + index`. Exigir inicio verificavel do
  **contest de origem** em ou apos `2020-07-01T00:00:00Z` (inclusivo). A mesma
  regra vale para a base e, na Historia 29, para novas recomendacoes. Nunca deduzir
  data pelo id. Origem/data ausente ou ambigua: inelegivel com motivo registrado.
- Comecar por contests Codeforces encerrados (`FINISHED`), formato CF, com lista
  ordenada simples A, B, C etc., participacoes `CONTESTANT`, individuais (um membro,
  sem equipe) e presentes em `contest.ratingChanges`. Excluir virtual, practice,
  unofficial, equipes e formatos/indices ambiguos da analise primaria, preservando
  brutos e contagens. Nao ordenar `A1/A2` lexicograficamente como se fossem A/B.
- Por orientacao explicita do usuario (D-026), contests especiais ficam fora da
  analise. Se **qualquer problema** tiver duas versoes, como B1/B2 (facil/dificil),
  excluir **todo o contest**, incluindo problemas simples e prefixos A/B/C.
  Essa exclusao e intencional; preservar os brutos, contabilizar o motivo e manter
  a regra na proxima amostra. Nao aproveitar somente a versao facil ou dificil.
- Associar `oldRating` por `(contestId, handle normalizado)` a participacao oficial.
  Nao usar rating atual, `newRating`, rank ou resultado do proprio contest como entrada.
  Conflitos, mudancas sem correspondencia e rating ausente ficam em quarentena.
  `oldRating=0` de ingresso nao e habilidade zero: separar estreantes; exigir evidencia
  de ao menos uma participacao rated anterior para o conjunto principal. Historico
  insuficiente/indisponivel e uma exclusao auditada, sem imputacao pelo futuro.
  oldRating positivo e consistente na lista oficial e a evidencia inicial; consultar
  user.rating apenas para ambiguidades, com cache, sem exigir uma chamada por linha.
- O rating do problema pode ser o publicado na coleta, pois a API nao garante
  snapshots historicos. Registrar fonte/data/hash e explicitar essa limitacao;
  nao alegar que esse rating estava disponivel no inicio do contest.
- Problema compartilhado entre divisoes/origens requer vinculo verificavel, nunca
  deduplicacao por nome. Se a origem nao puder ser estabelecida, excluir. Vinculos
  conhecidos de problemas compartilhados devem ficar no mesmo grupo de avaliacao.

## Coleta limitada e reproduzivel

O subprojeto Python isolado em `study/` esta implementado, executavel sem Android,
com Python >=3.11,<3.14, ambiente virtual, core stdlib, empacotamento fixado,
semente e configuracao versionadas. CLI de coleta/preparo, particoes, baselines e
AFT implementada; modelo usa NumPy 2.3.5/SciPy 1.17.0 opcionais. Avaliacao reservada
e relatorio cientifico 27-28 continuam identificados como nao implementados. Comandos no
[guia Python](../study/README.md); notebook nao e necessario neste bloco.

Fontes do cliente centralizado (user.rating reservado para ambiguidades, nao usado no recorte inicial): `contest.list`, `contest.standings`, `contest.status`,
`contest.ratingChanges`, `problemset.problems` e `user.rating` para verificar
historico anterior quando necessario. Centralizar requisicoes (intervalo minimo
de 2 s), cache por endpoint/parametros, paginacao completa, retry com backoff,
`Retry-After`, retomada e erros `OK/FAILED`. No contrato observado no piloto,
standings de contests comuns exigem GET anonimo apenas com contestId: coletar a
resposta oficial completa sem from/count/showUnofficial. Status segue paginado;
o filtro oficial/individual aplica-se a todos os dados. Uma falha de pagina nao equivale a
ausencia de submissao. Nunca treinar/avaliar um contest com coleta incompleta.

Piloto: 2 contests completos para testar volume e qualidade. Alvo inicial: 20
contests elegiveis, selecionados por hash com semente fixa da lista candidata,
com diversidade de divisao/rating, sem selecionar pelo AC ou pelo desempenho do
modelo. O id pode identificar/amostrar, jamais substituir a verificacao da data.
Incluir todas as participacoes elegiveis e suas tentativas nesses contests. Fixar
o manifesto antes de ajustar modelos. Se o volume exceder o orcamento, reduzir
contests completos e registrar a mudanca antes do treino, mantendo pelo menos 10
(6 treino, 2 validacao, 2 teste); abaixo disso, entregar somente piloto e declarar
avaliacao insuficiente. As 24 h sao trabalho estimado, nao prazo de coleta garantido.

A configuracao regular-simple-v1 aplica triagem de metadados/estrutura na ordem de
hash/rodizio antes de congelar os 20 selecionados. Reconhecimento conservador de
round regular: CF FINISHED com Div. 1-4 ou Codeforces Global Round, sem marcadores
especiais; exigir ordem A/B/C simples. Salvar todos os candidatos/recusas e refs.
Limite inicial 100 candidatos. Erro interrompe e retoma o mesmo candidato; nunca
selecionar por submissao/score/AC. Essa triagem nao garante ratings/populacao
posteriores: manter perdas auditadas, sem trocar contests por sucesso (D-027).

Preservar JSON bruto imutavel, parametros, coleta UTC, versao do esquema e hashes;
novas consultas/rejulgamentos geram nova versao. Manifesto registra paginas,
completude, elegibilidade, motivos de exclusao e versoes de regras. Dados grandes
e handles nao precisam ir ao Git: usar chaves pseudonimas nas tabelas/relatorio,
manter brutos locais e instrucoes de reobtencao/cache, fixtures sinteticas pequenas
identificadas e checksums dos artefatos. Fixtures nao sao resultados empiricos.

## Unidade, campos e hipotese sequencial

Unidade: `(participante, contest, problema)` com pelo menos uma submissao oficial
dentro da janela `[inicio, inicio + duracao)`. Ausencia de submissao significa
tentativa nao observada; nao criar exemplos negativos para todo problema do contest.
Usar `creationTimeSeconds` da submissao cujo veredito **final** e `OK`, nao o
horario em que o servidor terminou o julgamento. Upsolve fica fora dos rotulos.
Vereditos pendentes exigem reconsulta; sem resolucao, registro fica incompleto.

Guardar pelo menos: chaves, inicio/duracao do contest e sua fonte, `oldRating`,
rating do problema, `delta = ratingProblema - oldRating`, ids/horarios/vereditos das
submissoes, primeiro AC, ancora, fim observado, duracao observada, indicador de
evento, qualidade/completude, razoes de inelegibilidade e versoes. Features e
rotulos devem estar em tabelas separadas. Campos de desfecho sao so auditoria.

Para um prefixo valido, ancora de A = inicio do contest; ancora de cada problema
seguinte = primeiro AC no predecessor imediato. Entao `tempo = primeiroAC - ancora`.
ACs em A=10, B=28 e C=65 minutos geram **10, 18 e 37** minutos.

| Situacao observada | Regra primaria |
|---|---|
| A, B, C em ordem, sem submissao antecipada/intercalada | Aceitar prefixo e calcular diferencas entre ancoras. |
| Salto: B tentado/aceito sem AC em A | Preservar brutos; B nao tem ancora valida. Cortar o prefixo ao primeiro conflito. |
| Inversao: AC em B antes de A | Nao usar diferenca negativa nem reordenar pelos ACs; marcar violacao. |
| Submissao em B antes do AC em A, ou retorno a A enquanto B esta em andamento | Marcar intercalacao; invalidar o intervalo contaminado e seus sucessores, preservando prefixo anterior comprovado. |
| Reenvio em problema ja aceito durante o intervalo seguinte | Tratar como intercalacao; nao presumir dedicacao exclusiva. |
| ACs empatados, tempo <= 0, horario fora da janela ou identidade conflitante | Quarentena com motivo; nunca corrigir silenciosamente. |
| Proximo problema tentado, sem AC ate o fim, sem violacoes posteriores | Preservar observacao censurada a direita pelo fim do contest. |
| Problema nao tentado | Nao inferir falha nem tempo. |

A verificacao usa toda a sequencia coletada e marca cada intervalo, sem apagar
linhas recusadas. Nao invalidar retroativamente um intervalo concluido antes de
qualquer violacao. Primeiro envio nao substitui a ancora nem mede inicio do trabalho.
Medir funil por contest, faixa de rating e motivo: participacoes, tentativas,
eventos, censuras, violacoes, perdas por dados e cobertura final. Nao segmentar por ano.

## Sem AC e observacao incompleta

Para tentativa sequencial valida, definir `C = fimContest - ancora`, `Y = min(T,C)`
e `evento=1` se houve AC; sem AC, `Y=C` e `evento=0`. `C` e **tempo disponivel** sob
a hipotese, nao tempo efetivamente dedicado. Inatividade e abandono nao observados
podem tornar a censura informativa; declarar essa ameaca e fazer sensibilidade.
Submissoes intercaladas/saltos ficam auditaveis fora do ajuste principal, incluindo
os casos sem AC. Nao registrar zero, infinito ou rotulo de incapacidade.

Coleta parcial, veredito indefinido ou ancora invalida recebem estado de qualidade
proprio, nao uma censura administrativa artificial. Recoletar ou excluir do ajuste
com motivo. Abandono observavel/inversao nao significa seguir trabalhando ate o fim.
Relatar tambem analise de sensibilidade que delimita observacao na ultima submissao
(hipotese alternativa, nao novo tempo real), sem escolher a regra pelo teste.

O ajuste por sobrevivencia deve incluir eventos e censuras validos. Para avaliar
`AC ate b`: positivo se AC ate `b`, negativo se observado sem AC ate `b` (inclusive
AC depois de `b`), e desconhecido se censurado antes de `b`. Nao converter este
ultimo em negativo; reportar cobertura e usar ponderacao por probabilidade inversa
de censura (IPCW), estimada somente no treino, quando houver suporte. Exibir tambem
metricas no subconjunto com status conhecido, com seu vies de selecao explicitado.

## Referencias e modelo interpretavel

1. **Referencia simples:** Kaplan-Meier global e, havendo suporte no treino,
   por faixas de delta de 400 pontos (minimo inicial: 50 tentativas e 20 eventos;
   caso contrario usar global). Produz CDF empirica e quantis; mediana nao atingida
   e indisponivel, nao infinito. Incluir a formula D-012 como referencia de tempo
   operacional, sem inventar uma probabilidade para ela.
2. **Modelo inicial:** AFT log-normal com censura a direita. Comparar Weibull AFT
   como alternativa pequena e interpretavel e regularizacao predefinida
   `{0, 0.01, 0.1}`. Escolher pela validacao agrupada, documentando diagnosticos,
   convergencia e coeficientes como fatores de tempo; distribuicao e adequacao
   sao hipoteses a testar. A biblioteca de sobrevivencia deve ser justificada/fixada.
3. Guardar os dois ratings e sua diferenca, mas evitar colinearidade exata: ajustar
   com `oldRating/100` e `delta/100` (equivalente aos dois ratings), nao os tres
   termos lineares simultaneamente. Centrar/transformar usando somente o treino.
   Rank, score, AC, erros, numero de submissoes, duracao realizada, sucessor e
   timestamps de resultado nao entram como preditores. Orcamento `b` e parametro
   de consulta da CDF, nao informacao sobre o desfecho.

AFT v1 implementado conforme [MODEL.md](../study/MODEL.md), com likelihoods
explicitas e SciPy numerico fixado. Selecao interna usa Brier IPCW integrado em
0-120 min/5 min, G>=0,05 e gate ECE conhecido <=KM global+0,05, definidos antes do
ajuste (D-028). Gradiente <=1e-3 valida convergencia independentemente da mensagem
do otimizador. As coordenadas numericas usam apenas o desvio padrao do treino;
coeficientes exportados continuam por 100 pontos. Avaliacao reservada/ICs sao a 27.

Exportar `P(T<=b)=F(b|ratings)`, mediana e quantis 10/90 em minutos. Probabilidades
devem pertencer a [0,1] e nao diminuir com `b`. Fora do dominio observado ou sem
suporte de seguimento, indicar extrapolacao/indisponibilidade; nao esconder com clamp.
Nao prometer superar o baseline: se ele vencer ou nao houver evidencia, registra-lo.
Expandir modelos somente apos diagnosticar erros, em planejamento posterior.

## Separacao, erros e incerteza

Fixar listas de contests antes do treino: alvo 60/20/20% (treino/validacao/teste),
com semente e grupos de contests compartilhados. Nenhum contest/grupo aparece em
mais de uma particao. Transformacoes, faixas, censura e tuning usam treino/validacao;
teste permanece reservado ate congelar a escolha. Usar GroupKFold por contest no
treino se a amostra permitir; nao fazer split aleatorio de linhas.

| Aspecto | Evidencia planejada |
|---|---|
| Erro de tempo | MAE, mediana do erro absoluto, RMSE e vies assinado em minutos nos eventos do teste, com denominador e aviso de que esse subconjunto favorece resolvidos. |
| Distribuicao com censura | Brier IPCW em 30/60/120 e integrado no horizonte com suporte; informar funcao de censura, pesos extremos e horizontes indisponiveis. |
| Probabilidades | Brier e log-loss nos casos conhecidos de cada orcamento; diagramas/tabelas de calibracao, amostras e incerteza por bin. Zero/um tratado numericamente de modo documentado, sem alterar a probabilidade publicada. |
| Intervalos | Cobertura e largura do intervalo preditivo de 80% nos eventos, com ressalva de selecao; checar tambem consistencia com a avaliacao censurada. |
| Incerteza da comparacao | Bootstrap pareado por contest/grupo (200 replicacoes iniciais, semente fixa), IC de 95% de metricas e diferencas frente a referencia; nao reamostrar linhas como independentes. |
| Erros sistematicos | Fatias predefinidas de rating do participante, problema, delta e qualidade, com tamanho/eventos/censuras. Sem ranking por ano nem correcao temporal. |

Escolha na validacao: menor Brier integrado suportado, sem degradacao relevante de
calibracao; MAE e interpretabilidade como criterios secundarios. Mostrar todas as
referencias no teste, sem retunar nele. Poucos contests/eventos ou ICs largos
significam evidencia insuficiente, nao precisao comprovada. Repeticao por usuario
e problemas compartilhados limitam independencia; registrar e, se viavel depois
do MVP, verificar transporte para participantes nao vistos. Sem ids como features.

## Entrega de 24 horas e continuacao

Historias 22-28: ambiente/protocolo (2 h), coleta (4 h), rotulos (4 h), referencias
(3 h), AFT (4 h), avaliacao (4 h) e relatorio/reproducao (3 h): **24 h estimadas**.
Uma execucao local deve reconstruir tabelas, previsoes, metricas e figuras a partir
do snapshot. Entregar manifestos/hashes, dicionario, testes, artefatos dos modelos,
resultados reais e relatorio com metodos, erros, incerteza, cobertura e ameacas a
validade. Exemplo demonstravel: dois ratings e orcamento -> estimativas + incerteza
e suporte. Sem resultados ate a execucao; falha de coleta/ajuste tambem e reportavel.

Historia 29 aplica o corte no Android independentemente do sucesso preditivo, depois
do estudo inicial na ordem sugerida (pode antecipar apos 23). Verificar
`contest.list` e persistir metadados/proveniencia. Migracao aditiva: cache legado
sem data nao habilita novos candidatos ate verificacao; cache verificado continua
local/offline. Historicos, exclusoes por qualquer tentativa antiga, snapshots,
avaliacoes, pendencias e Ipsum ja ativo permanecem preservados. Versionar a regra
de elegibilidade; fallback nunca relaxa o corte nem inventa data.

Historias 30-31 validam com Ipsums, cujo inicio ja e registrado. Prever extracao
local explicita, minimizada e pseudonimizada; registrar inicio, primeiro AC valido,
encerramento sem AC, dica, interrupcoes conhecidas, versoes, rating CF disponivel
antes do inicio e Rating Lorem inicial. Campo historico ausente permanece ausente;
nao preencher retroativamente com rating atual. Tempo de parede ainda inclui
pausas. Desistencia pode ser censura informativa e requer analise propria.

Antes de transportar, comparar **Rating CF anterior ao Ipsum** e **Rating Lorem
inicial** em pares: diferenca/vies, dispersao, correlacao e calibracao das previsoes.
Correlacao nao prova intercambiabilidade. Separar participantes inteiros para
validacao, sem ajustar mapeamento no teste nem usar rating Lorem posterior ao
resultado. Avaliar identidade versus mapeamento simples ajustado so no treino,
com/sem dica separadamente. Definir tamanho/precisao requeridos e limiares de erro
com base no estudo antes de abrir o teste Ipsum; amostra insuficiente mantem gate
fechado. Dica e pausas sao condicoes de validacao, nao features futuras vazadas.

Historia 32 so inicia com evidencia da 31 e decisao registrada sobre dominio,
limiares aceitaveis e forma de apresentar a previsao. Integracao local pequena,
artefato versionado, predicoes novas auditaveis e fallback D-012 se indisponivel;
nao requer backend, login, Python embarcado ou nuvem. Nao recalcular avaliacoes
antigas nem substituir a formula de pontuacao sem nova decisao explicita.

## Decisoes pendentes nos marcos

- Historia 22: congelar dependencias, manifesto/volume apos piloto e parametros de
  reproducao, documentando o custo. Nao depende de nova escolha de produto.
- Historia 28: concluir se ha evidencia de utilidade e quais erros/dominios exigem
  novo experimento; nenhum ganho de precisao esta comprovado agora.
- Historia 31: pactuar tamanho/limiares de validacao com Ipsums antes do teste e
  decidir manter ratings distintos, mapear ou nao integrar. Gate cientifico,
  nao dependencia de teste manual do app.
- Historia 32: registrar alcance da integracao autorizada pela evidencia; mudanca
  de pontuacao D-012 exige decisao propria. Corte de problemas nao depende disso.
