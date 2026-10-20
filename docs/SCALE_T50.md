# Ampliação do estudo: aproximadamente 400 contests e curvas t50

**Estado: histórias 50–56 implementadas e verificadas em 20/10/2026; 49/artigo preservados.**
A base é a main `cc880ec`, com o estudo 44–48 integrado na PR #87.
[Planejamento central #88](https://github.com/Enzofmcs/Lorem/issues/88); a demonstração 49
([#82](https://github.com/Enzofmcs/Lorem/issues/82)) permanece planejada.

O objetivo é comparar faixas de rating numa amostra maior e visualizar no mesmo
gráfico os tempos até 50% de probabilidade de AC observados e preditos pelos
modelos disponíveis. A solicitação seguinte autorizou implementar as histórias 50–56, excluindo a 49
e qualquer alteração ao artigo do TCC. O plano versionado foi congelado antes
da consulta nova ao universo; resultados somente são comprovados pelas evidências
da execução, sem chamar o alvo 400 de amostra adquirida.

## Gráfico e significado do eixo Y

- X: faixa de rating do exercício, com limites explícitos.
- Y: `t50 = inf{t: F_AC(t | faixa_usuario, faixa_problema) >= 0.5}`, em minutos.
- Cada cor representa uma faixa de **oldRating** do usuário.
- Estilos de linha distinguem KM observado e cada modelo preditivo; seletores
  permitem destacar uma faixa/um modelo ou várias curvas no mesmo gráfico.

t50 é a mediana estimada com eventos e censura. Percentil só dos AC responde a
outra pergunta. A analogia com expectativa de 50% no Elo é uma intuição, não uma
equação que converta dificuldade em probabilidade de AC por prazo; ratings iguais
não garantem esse resultado. Rating Lorem não entra como oldRating.

Seguimento verificável e orçamento h são objetos diferentes. A referência de
120min não limita artificialmente a distribuição: t50 conhecido >h permanece
conhecido; quantil não alcançado no seguimento fica null com motivo. AC no limite
é incluído. Censura não significa incapacidade permanente.

A grade congelada na história 50 é oldRating em faixas de 400 em [0,4000)
e problema em faixas de 200 em [800,3600), bordas [a,b), com sensibilidades
oldRating 200/400 e problema 100/200/400. Os valores finais, suporte e política de
ICs foram registrados no plano antes da consulta nova e dos contrastes. Dados fora da grade são contados,
não extrapolados ou descartados silenciosamente. Curvas não serão suavizadas ou
tornadas monotônicas à força; lacunas sem suporte continuam visíveis.

## O que significa ampliar para 400

O alvo é aproximadamente 400 contests completos selecionados pela elegibilidade
estrutural/metadados antes de seus desfechos, e não 400 concursos escolhidos por
sucesso. A viabilidade precisa ser conferida: as restrições atuais podem deixar
menos de 400 disponíveis. Registrar candidatos, recusas, selecionados, adquiridos
completos e elegíveis após joins. Se o universo for insuficiente, informar o
déficit; não ampliar época, população ou aceitar variantes sem decisão explícita.

Preservar CF FINISHED, origem verificável >=01/07/2020, oficiais individuais,
oldRating positivo anterior e exclusão integral de especiais/qualquer variante
fácil/difícil. Não imputar newRating/rating atual. O tempo segue a aproximação
sequencial existente, sem alegar início de leitura ou dedicação exclusiva.

A execução real de 20/10 teve 321 respostas para 20 contests adquiridos. Uma
projeção linear meramente ilustrativa seria 6.420 respostas para 400: o gate
>=2s sozinho impõe cerca de **3h34**, antes de latência, retries e diferenças de
tamanho/triagem. O backup comprimido de 238.668.830 bytes por essa execução
projetaria cerca de **4,77 GB** (decimal) em vinte vezes o volume; brutos, audit,
previsões e intermediários descomprimidos podem ocupar dezenas de GB. Essas
projeções não são medição nem promessa de custo da nova amostra.

A história 50 congela um orçamento realista e a 51 executa sob limites de
requisições, disco e RAM. O coletor anterior usava teto de triagem 100; a configuração de escala
usa 5.000 candidatos, com limite e universo completo registrados. Compressão/particionamento
deve preservar hashes dos bytes HTTP descomprimidos e leitura dos snapshots
antigos. Paradas por falta de recursos/rede não viram contests completos.

## Como comparar os modelos

Inventário inicial: AFT, KM global/por delta dos estudos existentes, respeitando
versão, domínio, população e protocolo de cada artefato. Modelos completos de
20/10 e seus backups têm proveniência registrada; o estudo de 18/10 não foi
recuperado integralmente. Suas consultas retidas não reconstroem curvas para
ratings arbitrários. Outros trabalhos externos entram quando houver referência,
fórmula/artefato e covariáveis verificáveis; não inventar modelos para preencher
a legenda.

Para a comparação principal por faixas, modelo e KM observado usam a mesma
população/pesos da célula. A CDF predita da faixa é a mistura das CDFs individuais,
e **só depois** se extrai t50. A média de medianas individuais não é a mediana da
mistura. Se modelos só cobrem um subconjunto, publicar cobertura/denominadores
e reestimar o observado nesse mesmo subconjunto, explicitamente identificado.

Uma consulta alternativa com x0 fixo e y explícito é um cenário de previsão,
separado da mistura real da faixa. AFT e KM podem fornecer CDFs; KM global pode
produzir linhas coincidentes entre faixas e o bin de delta perde níveis
absolutos. Não deslocar linhas para criar diferenças visuais.

D-012 fornece duração operacional, não CDF ou mediana calibrada. Pode aparecer
como referência opcional com legenda própria, sem ser rotulada como linha de
50%. Uma fórmula Elo de expectativa de vitória, sozinha, não prevê tempo até AC.

## Incerteza e validação

A amostra descritiva não vira validação independente porque cresceu. O teste
antigo de quatro grupos já foi examinado; a execução 20/10 também foi explorada.
Identificar sobreposição com esses dados e com treino/validação dos modelos.

Bootstrap usa grupos inteiros de problemas compartilhados, com reposição e
multiplicidade, seed e política anteriores aos contrastes. Registrar grupos
efetivos por região, usuários repetidos, réplicas sem t50 e proporção de
indisponibilidade. Manter como principal a política conservadora da 48:
não descartar réplicas ausentes para fabricar IC. Pisos de suporte são filtros
de exibição, não garantia de precisão/independência.

Por grupo, leave-one-group-out e sensibilidades de grade/janela são identificados,
sem escolher o melhor resultado como verdade. Se houver nova alegação preditiva,
reservar grupos novos não examinados antes de explorar seus desfechos, sem
compartilhar problemas conhecidos entre partições. Sem essa reserva, registrar
avaliação preditiva nova não realizada e entregar a descrição. Não exigir provar
superioridade de modelo ou efeito pedagógico para concluir o estudo descritivo.
Comparação entre épocas/correção temporal continua fora do escopo.

## Entregas e dependências

| História | GitHub | Entrega |
|---|---|---|
| 50 | [#89](https://github.com/Enzofmcs/Lorem/issues/89) | Protocolo da amostra de aproximadamente 400 contests e contrato do tempo até 50% de AC |
| 51 | [#90](https://github.com/Enzofmcs/Lorem/issues/90) | Coleta escalável, retomável e auditável para aproximadamente 400 contests |
| 52 | [#91](https://github.com/Enzofmcs/Lorem/issues/91) | Preparo em escala e matriz de cobertura por faixas de rating do usuário e do problema |
| 53 | [#92](https://github.com/Enzofmcs/Lorem/issues/92) | Gráfico de t50 por dificuldade do problema com curvas para faixas de rating do usuário |
| 54 | [#93](https://github.com/Enzofmcs/Lorem/issues/93) | Sobrepor previsões dos modelos anteriores às curvas t50 por faixa de usuário |
| 55 | [#94](https://github.com/Enzofmcs/Lorem/issues/94) | Incerteza e estabilidade das curvas ampliadas e comparação honesta dos modelos |
| 56 | [#95](https://github.com/Enzofmcs/Lorem/issues/95) | Demonstração reproduzível e pacote offline do estudo ampliado com curvas t50 e modelos |

Fluxo principal: **50 → 51 → 52 → 53 → 54 → 55 → 56**. O roadmap e as issues
registram pré-requisitos específicos. As sete foram autorizadas para implementação; a conclusão
de cada critério depende das verificações e da execução real registradas. Não renumerar nem concluir a 49 por
este bloco; sua demonstração geral pode fornecer componentes reutilizáveis.

O pacote leve da 56 contém notebooks executados/HTML, figuras, tabelas/curvas
agregadas e artefatos distribuíveis para mostrar os resultados ampliados offline.
O modo agregado precisa mostrar os gráficos reais novos quando houver agregados
suficientes; não prometer reanálise de observações ou bootstrap a partir deles.
Modo completo exige snapshot e modelos verificados. Backup comprimido com audit
tem local/canal explícito de obtenção e teste de restauração; não integra o Git
com os handles dos brutos. Run All padrão não coleta ou retreina.

## Verificação prevista e limites preservados

Testes dos módulos analíticos/de escala, API roteirizada, limites de recursos,
joins/agrupamentos, inversão de CDF/misturas, domínio, bootstrap e execução limpa
dos notebooks precedem qualquer marcação de aceite. Figuras usam exatamente
as tabelas exportadas, com unidades, fontes, versões, faixas e ausências.

A implementação aplica testes Python de níveis 2/3, kernels limpos, navegador
automatizado, replay offline e restauração, com evidência real separada das fixtures.
Não exige Gradle ou retreino. Android/Firebase, D-012, snapshots anteriores e time-curve-v1 desabilitada
permanecem preservados. Sem coorte Ipsum obrigatória nem reabertura da 31.
Conferência humana é opcional (D-014); evidência científica/automatizada não é.

## Plano congelado e viabilidade observada em 20/10/2026

[Configuração](../study/configs/scale-t50-v1.json),
[plano com timestamp/hashes/modelos/orçamento](../study/configs/scale-t50-plan-v1.json)
e [operação Linux/Windows](../study/SCALE_T50_README.md). A consulta nova encontrou
525 candidatos por metadados; 487 reconhecidos como rounds regulares; 162
excluídos positivamente pelo catálogo por versões 1/2, dando teto preliminar 325.
A triagem completa de standings chegou a **292 estruturalmente elegíveis**,
selecionados somente por metadados/estrutura antes das novas chamadas de status
e da análise ampliada: déficit de **108** em relação a 400. Standings contêm
resultados, mas estes não entram no algoritmo de seleção. O cache histórico de
20 contests já examinados foi importado com datas/hashes originais e também não
decide a seleção. As demais recusas e os hashes ficam no manifesto de triagem.

A grade final mantém oldRating 400 em [0,4000) × problema 200 em [800,3600).
Pisos principais: 50 tentativas, 20 eventos e 5 grupos; sensibilidades
25/10/3 e 100/40/10. Bootstrap: 200 réplicas, seed 2026102055, IC95 somente
quando todas identificam t50. O universo de reamostragem contém os grupos
conhecidos de contests completos, inclusive grupos sem linhas elegíveis em
algumas regiões; os grupos efetivos são contados por célula, sem bootstrap local
que ignore grupos vazios. Grupos externos de origem podem formar pontes
transitivas; pessoas repetidas não são consideradas independentes.

Os brutos são `.json.gz`, com hash dos bytes HTTP descomprimidos; preparo
por contest em processos separados reutiliza `prepare.py`, junta features,
labels e audit 1:1 e grava SQLite transacional. O objetivo de memória é
operacional e as medições por processo são publicadas; não há lista global com
centenas de contests. O cache anterior mantém seus timestamps e proveniência.

Resultados/backup/replay reais estão identificados em
[results/scale-t50-v1/REPORT.md](../study/results/scale-t50-v1/REPORT.md) e na
[evidência da implementação](../study/evidence/2026-10-20-scale-t50.json):
292 completos/278 elegíveis, 4.754.352 válidas, 99/140 t50 identificados e 78 ICs.
Dois kernels limpos ampliados e reanálise restaurada com paridade científica;
replay offline zero chamadas e backup/restauração verificados.
[Interpretação das sensibilidades](../study/results/scale-t50-v1/INTERPRETATION.md).
O pacote leve mostra resultados agregados, sem anunciar download do snapshot
completo ou execução nova de bootstrap no modo agregado.
