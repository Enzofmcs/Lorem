# Estudo Python do Lorem

Implementacao das Historias 22-25: coleta com proveniencia, preparacao de tentativas,
particoes por contest e referencias Kaplan-Meier/D-012. **Piloto real da Historia 23
concluido: 1824/2062 completos, com cache offline. Cobertura valida zero pela regra
vigente de indices simples; nenhum modelo ou avaliacao real produzido.** Dados
sinteticos verificam o codigo. AFT, avaliacao de precisao/calibracao/ICs e relatorio cientifico
completo pertencem as Historias 26-28 e nao estao implementados.

Protocolo: [QUANTITATIVE.md](../docs/QUANTITATIVE.md). Contratos:
[DATA_DICTIONARY.md](DATA_DICTIONARY.md). O aplicativo Android e sua pontuacao nao mudam.

## Ambiente e comandos

Python **>=3.11,<3.14**; verificado em 3.12.14. Runtime e testes usam somente a
biblioteca padrao: `urllib` preserva proxy/CA/TLS, `unittest` testa sem rede e o
produto-limite Kaplan-Meier e pequeno e verificavel por conjuntos de risco. Nenhuma
biblioteca estatistica e antecipada para as Historias 26/27. A dependencia opcional
de empacotamento e fixada em `requirements.lock`/`pyproject.toml`.

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

`train` (AFT) e `evaluate` retornam erro explicito de etapa nao implementada. `report`
mostra apenas o estado do pipeline, sem fabricar metricas ou relatorio cientifico.

## Coleta real e retomada

```bash
python -m lorem_study collect --work work/pilot-real --pilot
python -m lorem_study collect --work work/pilot-real --pilot --offline
python -m lorem_study prepare --work work/pilot-real
```

O piloto congela dois contests. Um piloto nao satisfaz o minimo de dez contests
para particoes/ajuste; `split` retorna erro, sem produzir uma avaliacao aparente.
Depois de medir seu custo, use **outro diretorio** para a amostra-alvo:

```bash
python -m lorem_study collect --work work/sample-real
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
| `raw/<sha256>.json`, `responses/` | Bytes imutaveis (inclusive erros HTTP) e proveniencia por endpoint/parametros/data UTC. |
| `cache.json` | Indice retomavel de respostas OK; nunca inclui FAILED como resultado vazio. |
| `snapshots/<hash>.json`, `snapshot.json` | Versoes imutaveis e ponteiro/checkpoint de coleta. |
| `prepared/features.jsonl` | Chaves e somente dois ratings/delta como entradas centrais. |
| `prepared/labels.jsonl`, `audit.jsonl` | Tempos/evento/qualidade e rastreio aos brutos; nao entram como features. |
| `prepared/manifest.json` | Funil, cobertura, exclusoes, hashes, fatias de rating e limitacoes. |
| `splits.json` | Contests/grupos inteiros reservados; minimo 6/2/2, proximos de 60/20/20. |
| `baseline/artifacts/<hash>.json`, `model.json` | Modelos versionados e ponteiro local; ajuste exclusivamente no treino. |
| `baseline/predictions.jsonl`, `manifest.json` | Previsoes sem rotulos por particao e hashes do modelo/dados. |
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

## Evidencia e proximo marco

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
