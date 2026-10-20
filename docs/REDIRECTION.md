# Estudo visual do tempo até AC

Direção aprovada no [planejamento #72](https://github.com/Enzofmcs/Lorem/issues/72)
e implementada no bloco 44–48: investigar a distribuição do tempo observado até AC
para participantes em faixas de **oldRating x** e problemas de **rating y**.
Os notebooks reutilizam o núcleo Python. AFT/KM/D-012 e a avaliação de 18/10
ficam preservados como comparação secundária; não se exige coorte Ipsum.
A história 31 permanece retirada, a 32 desabilitada e a 49 fora deste bloco.

## Contrato e método

Unidade: tentativa oficial individual participante/problema/contest com submissão.
Sem submissão não é tentativa fracassada. oldRating deve ser positivo e anterior
ao contest. Rating do problema é observado na coleta, não garantidamente histórico.
Origem verificável desde 01/07/2020; preservar exclusão integral de contests
especiais/com variantes. Prefixos e sequências válidas selecionam a população.

T é tempo de parede aproximado pela sequência: A em 10min, B em 28min → 18min
para B. A API não revela dedicação exclusiva/início real de leitura. Ausência de
AC em janela válida é censura à direita. Abandono informativo limita o KM.

Plano principal versionado em
[analysis-time-bands-v2.json](../study/configs/analysis-time-bands-v2.json):
faixas x/y de 400 em [0,4000), inferior inclusivo/superior exclusivo; sensibilidades
200/800; horizontes 30/60/120min; filtro de exibição 50 tentativas/20 eventos/dois
grupos. Esse piso não comprova precisão. Sem fallback global, interpolação de
células ou extrapolação oculta. Mostrar amostra, eventos/censuras, participantes,
problemas, contests/grupos, em risco e razões de indisponibilidade.

**analysis-time-bands-v2:** T1=P25, T2=P50, T3=P75; T4=h é orçamento, nunca P100.
P25–P75 descreve variação entre tentativas. Não confundir com IC da estimativa.
Quantis ausentes são null com motivo; empates/faixas vazias e limites acima de h
permanecem explícitos. AC exatamente em h é incluído; censura não vira fracasso,
e AC depois de h fica separado. Não resolver no prazo não significa incapacidade
permanente. Percentis só dos resolvidos respondem a outra pergunta.

A consulta histórica 1200/1600 pertence ao bin de delta [400,800) do treino,
não ao par exato. F(120)=0,622099<0,75: P75 não é identificado até 120min.
As três probabilidades/mediana retidas não reconstroem a curva ou P25.

## Estabilidade e força da evidência

Exploração de todo o snapshot é **descritiva**. O teste histórico de quatro
contests foi examinado e não é retunado. Bootstrap usa grupos inteiros de problemas
compartilhados, com reposição e multiplicidade; 200 réplicas, seed 20261020.
Réplicas sem quantil são preservadas; política conservadora não calcula IC95%
quando qualquer réplica não identifica a estatística. Informar proporção ausente.
Usuários repetidos continuam limitação, mesmo com muitos registros.

Sensibilidades de janela (sequencial, desde início, até última submissão para
censurados) são outros estimandos/observações. Não selecionar o resultado mais
favorável como verdade. Nova alegação preditiva exige novos grupos não examinados,
sem problemas compartilhados entre ajuste/teste; avaliação preditiva nova não
realizada por este bloco. Não bloquear o TCC inteiro por ausência dessa avaliação.

## Execuções e reprodução

[Guia e comandos](../study/notebooks/README.md) descrevem ambiente fixado,
execução limpa, exportação HTML/JSON/SVG/PNG, modos aggregate/full/synthetic,
restauração por SHA-256 e coleta opt-in em diretório novo. Run All é offline e
não dispara coleta/retreino. Outputs públicos não contêm handles/pseudônimos.

O backup de 18/10 não foi encontrado no GitHub (branches científicas, código,
releases e artefatos Actions) nem nos caminhos acessíveis do ambiente. Os agregados
reais foram verificados; não permitem reconstruir microdados. O usuário autorizou
em 20/10 uma nova coleta real do zero: configuração/seed e plano congelados antes
da aquisição, em diretório separado, preservando os artefatos anteriores.
A evidência desta execução distingue explicitamente recuperação antiga de
amostra nova. Dados brutos contêm handles públicos e permanecem fora do Git.

## Compatibilidade do produto

`time-curve-v1` da história 39 conserva p50/p75/p90, snapshots e semântica;
continua desabilitada. Este contrato analítico não altera pontos, Rules, saldo,
prazo ou contas. D-012 permanece vigente. Firebase 40–43 e suas evidências
ficam preservados. [Roadmap](ROADMAP.md) registra os critérios efetivamente
comprovados do novo bloco.
