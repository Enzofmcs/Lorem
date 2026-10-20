# Estudo Python do Lorem

Implementacao das Historias 22-28, com preparo local da 30 e ferramenta pre-registrada da 31: coleta auditavel, tentativas sequenciais,
particoes por contest, Kaplan-Meier/D-012, AFT censurado e avaliacao reservada.
Amostra real: 19 contests elegiveis, 274.073 tentativas validas. Teste: quatro
contests e 35.367 tentativas. KM por delta apresentou menor erro que o AFT nesta
amostra; transporte para Ipsums ainda nao foi validado.

[Relatorio real](results/2026-10-18/report/REPORT.md),
[tabelas/figuras](results/2026-10-18/report/manifest.json) e
[metricas/ICs completos](results/2026-10-18/evaluation/metrics.json).

Protocolo: [QUANTITATIVE.md](../docs/QUANTITATIVE.md). Contratos:
[DATA_DICTIONARY.md](DATA_DICTIONARY.md). O modelo Python permanece separado do aplicativo. As 29-30 adicionam corte, snapshots e exportacao; a pontuacao D-012 permanece vigente.

## Ambiente e comandos

Python **>=3.11,<3.14**; verificado em 3.12.14. Coleta/preparo/baselines usam a
biblioteca padrao: `urllib` preserva proxy/CA/TLS, `unittest` testa sem rede e o
produto-limite Kaplan-Meier e pequeno e verificavel por conjuntos de risco. Nenhuma
dependencia de modelo e exigida nessas etapas. AFT usa NumPy 2.3.5/SciPy 1.17.0,
opcionais e fixados em `requirements-model.lock`; metodo em [MODEL.md](MODEL.md).
A dependencia de empacotamento e fixada em `requirements.lock`/`pyproject.toml`.

Na pasta `study/`, sem instalar pacotes:

```bash
python -m venv .venv
python -m lorem_study --help
python -m unittest discover -s tests -v
```

Para instalar o comando tambem fora desta pasta (POSIX):

```bash
.venv/bin/python -m pip install -r requirements.lock
.venv/bin/python -m pip install --no-build-isolation --no-deps -e .
.venv/bin/lorem-study --help
```

No Windows, use `.venv\Scripts\python.exe` e `.venv\Scripts\lorem-study.exe`.
O lock de escrita usa a biblioteca padrao do respectivo sistema; libera ao sair
do processo. A execucao automatizada desta sessao foi em Linux, nao em Windows.
Nenhum comando exige Gradle, Android, backend, login ou nuvem.

## Jornada sintetica, sem rede

Escolha um diretorio vazio. A fixture gera dez contests artificiais com ratings,
WA/AC sequenciais, censura, saltos, intercalacoes e estreantes. Passa pelo mesmo
coletor, com transporte e relogio explicitamente sinteticos.

```bash
python -m lorem_study fixture --work work/demo
python -m lorem_study prepare --work work/demo
python -m lorem_study split --work work/demo
python -m lorem_study train-baseline --work work/demo
python -m lorem_study predict --work work/demo --old-rating 1200 --problem-rating 1200 --budgets 30 60 120
python -m lorem_study report --work work/demo
```

As saidas conservam `data_kind=synthetic`. A fixture nao sobrescreve dados existentes.
Duas execucoes em diretorios novos geram manifestos e modelos identicos. Exemplo
esperado **somente na fixture**: 90 tentativas observadas, 40 eventos, 10 censuras,
40 exclusoes; particao 6/2/2; treino com 30 tentativas (24 eventos, 6 censuras).
Para os ratings do exemplo, mediana 18 min, probabilidades 0,6/0,8/indisponivel em
30/60/120 min; q90 nao identificado. Esses numeros nao medem a precisao do Lorem.

`train` ajusta AFT com dependencias opcionais. `evaluate` abre o teste com modelos
congelados e plano/hash imutavel. `report` produz relatorio, CSVs, figuras SVG/PNG e
manifesto; sem avaliacao previa retorna apenas status explicito, sem metricas.

## Coleta real e retomada

```bash
python -m lorem_study collect --work work/pilot-regular --pilot --config configs/regular-simple-v1.json
python -m lorem_study collect --work work/pilot-regular --pilot --config configs/regular-simple-v1.json --offline
python -m lorem_study prepare --work work/pilot-regular
```

O piloto congela dois contests. Um piloto nao satisfaz o minimo de dez contests
para particoes/ajuste; `split` retorna erro, sem produzir uma avaliacao aparente.
Depois de medir seu custo, use **outro diretorio** para a amostra-alvo:

```bash
python -m lorem_study collect --work work/sample-real --config configs/regular-simple-v1.json
python -m lorem_study prepare --work work/sample-real
python -m lorem_study split --work work/sample-real
python -m lorem_study train-baseline --work work/sample-real
```

Padroes versionados em [default.json](lorem_study/default.json): corte inclusivo
`2020-07-01T00:00:00Z`, semente 20261018, alvo 20, piloto 2, minimo 10, paginas de
10000, ate 200 paginas de status por contest, intervalo minimo 2 s, 3 retries,
uma reconsulta de paginas com veredito pendente e horizontes 30/60/120 min.
Copie a configuracao e passe `--config caminho.json` **antes da primeira coleta**
para registrar outro volume/custo. Corte, protocolo e versao nao podem mudar
silenciosamente. A selecao congela antes do ajuste; a ordem usa hash/rodizio de
divisoes, nunca desempenho ou AC. Id nao substitui data.

Para novas coletas reais, usar [regular-simple-v1.json](configs/regular-simple-v1.json).
O default original permanece para reproduzir fixtures/snapshots anteriores. A
triagem conservadora reconhece CF FINISHED com Div. 1-4 ou Codeforces Global Round
no nome, sem marcadores especiais, e exige problemas A/B/C simples em standings.
Percorre a ordem de hash/rodizio ate 20 estruturas elegiveis (limite 100 candidatos),
registrando tambem os recusados. Erro interrompe/retoma o candidato, sem substitui-lo.
Nenhuma submissao, pontuacao ou desfecho escolhe a amostra. So depois congela
selection.json e baixa os historicos. Ratings/completude podem reduzir o numero
final elegivel; essa perda fica explicita antes do ajuste, sem trocar por sucesso.

Na execucao de 18/10/2026: 44 candidatos verificados, 20 adquiridos; 1589 perdeu
elegibilidade por falta de ratings verificaveis no catalogo. Os 19 restantes
produziram 392.445 tentativas observadas: 234.945 eventos, 39.128 censuras e
118.372 exclusoes. Treino 193.527, validacao 45.179 e teste reservado 35.367
tentativas validas. O piloto anterior 1824/2062 foi preservado.

## Modelo AFT (Historia 26)

```bash
.venv/bin/python -m pip install -r requirements-model.lock
.venv/bin/python -m lorem_study train --work work/sample-real
.venv/bin/python -m lorem_study predict --model aft --work work/sample-real --old-rating 1200 --problem-rating 1600 --budgets 30 60 120
```

No Windows, usar `.venv\Scripts\python.exe`. Para a amostra desta sessao,
o diretorio e `work/sample-real-regular-v1/`. O comando de treino exige preparo e
particoes validos. Compara seis candidatos (duas distribuicoes x tres penalidades),
com folds por grupos dentro do treino e selecao na validacao. Teste permanece
reservado para a 27. `--model baseline` continua sendo o padrao de `predict`.

Exporta coeficientes/fatores por 100 pontos, CDF, mediana e quantis 10/90; os quantis
sao intervalo preditivo, nao IC. Convergencia e gradiente sao conferidos; falha,
gate de calibracao, seguimento insuficiente e extrapolacao ficam explicitos.
[Metodo e limites](MODEL.md); modelos/previsoes ficam no diretorio `aft/`.

Standings de contests comuns agora aceitam apenas GET anonimo com contestId; o
coletor guarda a resposta oficial completa em uma referencia, sem from/count ou
showUnofficial. O page_size limita somente as paginas de status. O preparo continua
filtrando CONTESTANT individual; virtual/practice/equipes permanecem fora.

Repetir a coleta no mesmo diretorio retoma paginas verificadas do cache; nao refaz
contests completos nem converte falha em historico vazio. `--offline` proibe rede;
cache ausente ou corrompido causa erro. Nao misturar diretórios reais e sinteticos.
Para novos snapshots/rejulgamentos depois de concluir a coleta, usar outro
diretorio; uma reconsulta de pendentes preserva os bytes anteriores. Erros de
rede/HTTP/FAILED ficam explicitos; falha de pagina ou veredito oficial pendente
mantem o contest incompleto e fora do ajuste. Chamadas devem partir de um unico
coletor; nao execute coletores paralelos com outros diretorios/clients contra a API.

## Artefatos locais e auditoria

`work/` e ignorado pelo Git. Preserve-o para reproduzir sem rede:

| Caminho no diretorio de trabalho | Conteudo |
|---|---|
| `selection.json` | Selecao congelada, config/hash, semente e referencia da lista de contests. |
| `screening.json`, `screenings/<hash>.json` | Decisoes estruturais e fontes de todos os candidatos, checkpoint e versao imutavel anterior ao ajuste. |
| `raw/<sha256>.json`, `responses/` | Bytes imutaveis (inclusive erros HTTP) e proveniencia por endpoint/parametros/data UTC. |
| `cache.json` | Indice retomavel de respostas OK; nunca inclui FAILED como resultado vazio. |
| `snapshots/<hash>.json`, `snapshot.json` | Versoes imutaveis e ponteiro/checkpoint de coleta. |
| `prepared/features.jsonl` | Chaves e somente dois ratings/delta como entradas centrais. |
| `prepared/labels.jsonl`, `audit.jsonl` | Tempos/evento/qualidade e rastreio aos brutos; nao entram como features. |
| `prepared/manifest.json` | Funil, cobertura, exclusoes, hashes, fatias de rating e limitacoes. |
| `splits.json` | Contests/grupos inteiros reservados; minimo 6/2/2, proximos de 60/20/20. |
| `baseline/artifacts/<hash>.json`, `model.json` | Modelos versionados e ponteiro local; ajuste exclusivamente no treino. |
| `baseline/predictions.jsonl`, `manifest.json` | Previsoes sem rotulos por particao e hashes do modelo/dados. |
| `aft/artifacts/<hash>.json`, `model.json`, `manifest.json`, `predictions.jsonl` | AFT selecionado na validacao, coeficientes/diagnosticos/versoes e previsoes sem rotulos por particao. |
| `status.json` | Estado de etapas executadas; nenhum resultado cientifico presumido. |

Hashes de objetos JSON usam representacao canonica UTF-8, chaves ordenadas e sem
NaN/infinito; hashes de `raw/` usam os **bytes HTTP exatos**. Tabelas e modelo sao
conferidos antes de ajustar. Nenhum handle real e necessario em arquivos Git;
os brutos locais contem handles publicos. O hash de handle e pseudonimo estavel,
nao anonimato garantido. Relatorios nao devem publicar identificadores/brutos.

O preparo separa completude da aquisicao de elegibilidade para `split`: formato
CF encerrado, indices simples A/B/C, populacao rated oficial individual e ao menos
um problema com origem/data/rating verificaveis. Por instrucao do usuario (D-026),
contests especiais e contests com qualquer par de versoes (B1/B2, facil/dificil)
ficam integralmente fora da analise, inclusive os problemas simples desses contests.
Os brutos e motivos continuam preservados para auditoria.
Problemas sem data/origem ou rating,
estreantes/oldRating ausente/conflitante, virtual/practice/equipes e sequencias
invalidas permanecem auditaveis. oldRating positivo na lista oficial e a evidencia
inicial de historico; zero/negativo e separado por insuficiencia, sem imputacao futura.
Nao chamar `user.rating` para cada participante. Quando necessario, seu acesso ja
passa pelo cliente centralizado, mas a expansao dessa populacao nao foi antecipada.

Grupos conhecidos podem ser declarados em `shared_contest_groups` com `contest_ids`
e `source` verificavel. Origens compartilhadas em standings formam grupos
transitivos, inclusive por nos externos. Nao unir por nome de problema. A API nao
garante detectar todos os aliases entre divisoes; essa limitacao permanece no estudo.
As consultas da API nao sao atomicas: moderacao/rejulgamento pode alterar respostas
entre paginas. Datas/hashes e novas versoes tornam a amostra auditavel, sem afirmar
que a API forneceu um snapshot historico atomico.

KM global e por delta de 400 pontos (minimo 50 tentativas/20 eventos no treino),
com fallback global. Curva de censura e dominio tambem usam so treino. Horizontes
alem do seguimento sao indisponiveis, salvo sobrevivencia ja zero. Quantis nao
atingidos retornam `null`; faixas fora do treino sao marcadas como extrapolacao.
Os quantis 10/90 representam variacao preditiva empirica, **nao IC**. D-012 e
somente referencia operacional de tempo e nao recebe probabilidade inventada.

## Evidencia atual e reproducao

[Amostra/modelo de 18/10/2026](evidence/2026-10-18-sample-aft.json) e
[inventario de respostas/hashes](evidence/2026-10-18-sample-responses.jsonl).
Aquisicao: 308 respostas OK, 1.654.835.689 bytes, 633,889 s; 152.420 participacoes
oficiais e 2.386.518 submissoes. A triagem recusou 24 dos 44 candidatos antes da
selecao. Os 20 adquiridos produziram 19 elegiveis, sem substituicao por desfecho.

Seis candidatos e seus folds convergiram com gradiente conferido; a validacao
escolheu Weibull/L2 0,1. A avaliacao reservada posterior esta no relatorio da 28; o candidato nao foi retunado.
Coleta offline (zero requisicoes), preparo, particoes, baselines e AFT foram
repetidos no mesmo ambiente, produzindo os mesmos bytes/hashes em todas as tabelas,
modelos e previsoes. O core tambem foi executado sem site-packages; wheel/CLI 0.2.0
verificados com as dependencias fixadas.

Backup local: `work/regular-simple-v1-2026-10-18.tar.gz` (157 MiB; SHA-256 na
evidencia). Preserva brutos/cache/proveniencia, selecao/triagem/snapshots, features,
labels, manifests, particoes e modelos. Contem handles publicos e fica fora do Git.
Audit e previsoes grandes sao regeneraveis e foram omitidos desse backup.

Para restaurar em outra copia do repositorio, extrair o arquivo em um diretorio
`work/` vazio. Instalar os locks acima e executar:

```bash
python -m lorem_study collect --work work/sample-real-regular-v1 --config configs/regular-simple-v1.json --offline
python -m lorem_study prepare --work work/sample-real-regular-v1
python -m lorem_study split --work work/sample-real-regular-v1
python -m lorem_study train-baseline --work work/sample-real-regular-v1
python -m lorem_study train --work work/sample-real-regular-v1
```

Usar o Python do ambiente com os locks instalados. A evidencia registra hashes de
arquivos e versoes; igualdade byte a byte foi verificada neste Linux/Python/NumPy/
SciPy. Nova coleta da API pode mudar bytes, datas e julgamentos. Preservar o cache
e necessario para a reproducao exata. Avaliacao e relatorio tambem sao deterministas.

## Historico do piloto inicial

[Evidencia do piloto real de 18/10/2026](evidence/2026-10-18-pilot-real.json):
1824/2062 completos, 17.019 participacoes oficiais, 210.860 submissoes e 47.100
tentativas observadas. Foram 30 respostas HTTP, incluindo 2 recusas do contrato
antigo de standings e 2 consultas diagnosticas; 143.381.349 bytes HTTP, 29 brutos
unicos. Coletores medidos: 56,653 s, excluindo diagnostico e edicao.

As 47.100 tentativas foram excluidas: 44.952 por indices nao suportados (B1/B2 em
1824, E1/E2 em 2062) e 2.148 por historico insuficiente. A ordem simples atual
recusa o contest inteiro, inclusive prefixos A/B/C. Esse comportamento foi mantido;
nao houve substituicao dos sorteados, expansao de protocolo, treino ou avaliacao.
Dois contests adquiridos e zero elegiveis ficam abaixo do minimo de dez; split
recusa a operacao. A amostra futura exige outro diretorio e mantera a exclusao
integral de contests especiais/com versoes, conforme D-026.

O diretorio local `work/pilot-real/` preserva brutos e tabelas. Os comandos de coleta
offline e preparo acima reproduzem o snapshot e todas as tabelas byte a byte;
a evidencia registra hashes de arquivos e de cada resposta. Nova consulta pode
mudar os bytes; reproducao exata exige preservar o cache. `origin_links` registra
as origens da API, sem vinculo cruzado detectado no piloto; aliases entre divisoes
nao sao descartados por essa ausencia. Nenhum handle ou tabela real foi ao Git.

39 testes sem falhas, incluindo resposta completa de standings maior que page_size,
retomada offline e metadados malformados. Testes e jornada sintetica verificam
software; nenhuma melhoria preditiva esta comprovada. Politica de rede deve permitir
`codeforces.com` pela rota normal do ambiente, preservando proxy/TLS.

## Avaliacao, relatorio e reproducao limpa (27-28)

Instalar `requirements-report.lock` (inclui NumPy/SciPy do modelo e Matplotlib
3.10.8 com dependencias transitivas fixadas, compativeis com Python >=3.11).
Matplotlib gera figuras cientificas independentes SVG/PNG; nenhuma dependencia
nova e exigida na coleta/preparo/KM. Metodos em [MODEL.md](MODEL.md).

```bash
.venv/bin/python -m pip install -r requirements-report.lock
.venv/bin/python -m lorem_study evaluate --work work/sample-real-regular-v1
.venv/bin/python -m lorem_study report --work work/sample-real-regular-v1
```

`evaluation/plan.json` vincula modelos/particoes antes dos rotulos do teste. Entradas
alteradas sao recusadas, sem retuning; repetir as mesmas entradas e reproducao.
200 replicacoes pareadas de grupos inteiros, seed 20261018, ICs de 95%. Quatro
grupos limitam a incerteza estimada; participantes repetidos nao sao independentes.
MAE nos resolvidos tem vies; comparacoes usam o mesmo suporte. Intervalo preditivo
10/90 e IC da metrica sao objetos diferentes. IPCW nao transforma censura precoce
em negativo nem trunca pesos sem suporte. CSVs incluem n, IC, bins e ausencias.

Para reconstruir **todos os estagios** em ambiente limpo, a partir do snapshot:

```bash
python -m venv work/reproduction-venv
work/reproduction-venv/bin/python -m pip install -r requirements.lock -r requirements-report.lock
work/reproduction-venv/bin/python -m pip install --no-build-isolation --no-deps .
work/reproduction-venv/bin/python scripts/reproduce.py work/sample-real-regular-v1 work/reproduced
```

O destino deve ser novo. O script usa o pacote instalado, fora da arvore de fontes;
reconstroi collect offline -> prepare -> split -> baselines -> AFT -> evaluate ->
report. `reproduction.json` registra tempos/versoes/hashes e compara os artefatos
com o original; codigo de saida nao zero indica falha/diferenca. Brutos imutaveis
podem ser hardlinks locais; ponteiros mutaveis sao copiados. Instalacao pode usar
rede; a reproducao dos dados nao faz requisicoes. Nova coleta nao e reproducao.
Resultados publicados sao agregados e sem handles; backup bruto permanece local.

## Base local de Ipsums (30)

No aplicativo: Configuracoes -> Dados do estudo -> Exportar tentativas. Escolher
um arquivo local. Somente encerrados, pseudonimo persistente por perfil; nenhum
handle, nome, tag ou id de submissao. O ativo e omitido. Dados antigos ausentes
continuam null. Atualizar o perfil antes de novas tentativas permite registrar
uma observacao CF anterior; uma atualizacao posterior nunca preenche o passado.
O arquivo e pseudonimizado, nao garante anonimato; manter somente local.

```bash
python -m lorem_study prepare-ipsums --work work/ipsum-validation --inputs /caminho/export-1.json /caminho/export-2.json
python -m lorem_study prepare-ipsums --work work/ipsum-validation --inputs /caminho/export-1.json /caminho/export-2.json --offline
```

Core stdlib, sem NumPy/Android. A primeira chamada verifica metadata oficial de
cada contest via standings completos, gate >=2 s e brutos/cache locais; nao envia
exportacoes, pseudonimos ou timestamps. Standings brutos contem handles publicos
e nao devem ir ao Git. Sem cache, `--offline` falha e permite retomar; falha nao
vira falso negativo/censura. Contests especiais/com qualquer versao sao excluidos
integralmente. CF anterior/fonte/data e Lorem inicial sao snapshots; dica e pausas
sao estratos de qualidade, nunca features futuras.

`ipsum/plan.json`, `attempts.jsonl`, `manifest.json` e, somente com suporte,
`partitions.json`. [Protocolo](IPSUM_PROTOCOL.md): piso inicial 10 participantes,
200 tentativas validas, 100 eventos e particoes por participante 6/2/2, seed fixa.
Duplicatas identicas sao deduplicadas; conflitos/alteracoes de coorte congelada
sao recusados. O piso nao substitui os [limiares/precisao finais da 31](TRANSPORT_PROTOCOL.md).

Nenhum export real foi fornecido nesta sessao. Comando sem `--inputs` e `--offline`
registra n=0/`not_collected`/`insufficient_data`, zero chamadas, sem criar particoes
ou metricas. 31/32 permanecem bloqueadas. A fixture `tests/fixtures/ipsum-export-android-v1.json`
foi produzida pelo encoder Android com Room/StartIpsum/AC controlados e e
explicitamente `synthetic`; nunca substitui evidencia empirica.

## Avaliacao local de transporte (31, evidencia real pendente)

Pacote 0.4.0. O [pre-registro](TRANSPORT_PROTOCOL.md) fixa o piso final em 100
participantes/2.000 tentativas/1.000 eventos e exige precisao dos ICs, suporte,
cobertura de dominio/mediana e limites de MAE/Brier/calibracao. Reusar os modelos
congelados da 28; nenhum retreino no teste. Preparar a coorte final em diretorio
novo; a particao por participante e verificada pelo hash/seed da 30.

```bash
python -m lorem_study prepare-ipsums --work work/ipsum-transport-v1 --offline
python -m lorem_study evaluate-ipsums --work work/ipsum-transport-v1 --source-work work/sample-real-regular-v1
```

O primeiro comando sem `--inputs` demonstra a ausencia real atual. Quando houver
exports reais, usar `--inputs` com os caminhos explicitos no preparo, sem
`--offline` na primeira consulta dos metadados. O segundo comando e sempre local,
sem chamadas de rede. Se os artefatos da 28 faltarem, reproduzi-los pelo roteiro
anterior antes de executar. Ele congela `ipsum/transport/plan.json` antes de ler
tentativas; verifica checksums/particoes e, se insuficiente, escreve somente
estado/relatorio, sem ajuste nem metricas. Esse caminho continua stdlib.

Com amostra suficiente, instalar `requirements-model.lock`. Ajuste linear usa so
treino, escolha entre identidade/linear e KM/AFT usa validacao; `selection.json`
e persistido antes da avaliacao do teste. `metrics.json` inclui pares de ratings,
erros/censura, ICs por participante, estratos e comparacoes pareadas com suporte
comum. `REPORT.md` e `manifest.json` apontam decisao e hashes. Reexecucao identica
reproduz os arquivos; alterar dados, modelos ou plano congelados e recusado.

Fixtures nunca aprovam transporte; limites numericos favoraveis em dados reais
ainda exigem decisao explicita de dominio na 31. O gate do app permanece fechado
e D-012 vigente. Historia 31 nao esta concluida; 32 continua bloqueada.


## Curvas consultáveis e cenários de pontos (D-044)

[Contrato](../docs/TIME_SCORING.md) e [relatório](results/time-scoring-v1/REPORT.md).
A política fica desabilitada; exportar modelo não a ativa. Sem KM completo neste
checkout, o artefato Android preserva somente a consulta retida 1200/1600 e sua
indisponibilidade, sem generalizar uma consulta para todo o bin.

Na raiz do repositório:

```bash
PYTHONPATH=study python3 -m lorem_study export-time-curves --work CAMINHO_ESTUDO --artifact /tmp/time-curves.json
PYTHONPATH=study python3 -m lorem_study lookup-time-curves --artifact /tmp/time-curves.json --old-rating 1200 --problem-rating 1600
PYTHONPATH=study python3 -m lorem_study export-time-curves --retained-demo study/results/2026-10-18/report/demo.json --artifact app/src/main/assets/time-curves-v1.json
PYTHONPATH=study python3 study/scripts/simulate_time_scoring.py
```

Consulta retorna limites inteiros estritamente ordenados ou indisponibilidade com
motivo. Escala aceita: CF anterior, nunca saldo Lorem. Exportador lê somente o KM
congelado e seus bins de treino; não abre o teste ou ajusta parâmetros. Probabilidades
30/60/120 min, quantis p50/p75/p90, marcos parciais, domínio, suporte, hash e versões
integram o JSON. Nenhum fallback global silencioso. Simulações são sintéticas e
separadas da avaliação estatística.

## Investigação em Jupyter (44–48)

[Cinco notebooks e instruções](notebooks/README.md), ambiente fixado em
`requirements-notebooks.lock`, plano descritivo `analysis-time-bands-v2` e
[contrato/metodologia](../docs/REDIRECTION.md). Pacote 0.5.0. Execute a partir da raiz:

```bash
python study/scripts/run_notebooks.py --output work/notebooks-aggregate
```

O interpretador deve ser o do ambiente de notebooks documentado. Cada notebook
usa kernel limpo; HTML com imagens embutidas, JSONs com proveniência e figuras
SVG/PNG ficam na pasta nova. Run All é offline. Modos full/synthetic exigem
caminhos explícitos e não substituem silenciosamente dados ausentes.

O snapshot/backup de 18/10 não está nesta cópia nem nas fontes GitHub acessíveis.
Os agregados reais permanecem íntegros; uma nova amostra real foi autorizada
pelo usuário, com configuração `configs/notebooks-2026-10-20.json` e plano fixado
antes da coleta. Ela é descritiva, identificada separadamente e não recupera o
passado. O guia explica coleta, hashes, restauração, sensibilidades e limites.
