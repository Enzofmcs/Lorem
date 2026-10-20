# Notebooks do estudo (44–48)

Cinco notebooks orquestram módulos testados de `lorem_study`. A pergunta é a
**distribuição do tempo observado até AC para faixas de oldRating x e rating do
problema y**, com censura. O modo padrão reapresenta agregados reais de 18/10;
o modo completo reanalisa uma execução identificada. AFT/KM/D-012 históricos
permanecem comparação secundária, sem retuning. A demonstração 49 é outro escopo.

## Ambiente e execução limpa

Na raiz do repositório, Python >=3.11,<3.14:

```bash
python -m venv work/notebook-venv
work/notebook-venv/bin/python -m pip install -r study/requirements-notebooks.lock
work/notebook-venv/bin/python -m pip install --no-build-isolation --no-deps -e study
work/notebook-venv/bin/python study/scripts/run_notebooks.py --output work/notebooks-aggregate
```

No Windows, substituir `work/notebook-venv/bin/python` por
`work\notebook-venv\Scripts\python.exe`. Usar `python` do venv no PowerShell depois
de ativá-lo também funciona. Os scripts usam pathlib e o mesmo interpretador para
o kernel; não dependem de um kernelspec global. Windows é suportado no código,
mas a execução desta entrega foi verificada em Linux/Python 3.12.

O runner usa **cinco kernels novos**, interrompe no primeiro erro e exporta
`.ipynb` executado + HTML com imagens embutidas, JSONs e SVG/PNG independentes.
`execution.json` registra hashes dos fontes/outputs e células executadas.
O destino deve estar vazio; fontes e resultados antigos são protegidos.
Nenhum comando de coleta/treino faz parte de Run All. HTTP está desabilitado no
bootstrap do notebook. Instalar as dependências acima pode usar rede.

Para exploração local:

```bash
work/notebook-venv/bin/python -m jupyterlab study/notebooks
```

As variáveis opcionais `LOREM_STUDY_MODE`, `LOREM_STUDY_WORK`,
`LOREM_STUDY_OUTPUT`, `LOREM_STUDY_PLAN` são herdadas pelo kernel. O padrão é
`aggregate`, sem caminho de snapshot. Reiniciar o kernel ao trocar contexto.
Editar x/y/h nos notebooks 03/04 e os filtros contest/faixa no 01 é suficiente;
a grade principal e as sensibilidades são explícitas no plano JSON.

## Modos e fontes

| Modo | Fonte e comportamento |
|---|---|
| aggregate | Relatório, evidência e métricas reais versionados de 18/10. Verifica hashes. Não reconstrói microdados, KM completo, ICs novos ou grade x/y. |
| full | `--work` explícito; snapshot bruto, features/labels/audit, splits e modelos completos com checksums. Falta/corrupção é erro; nunca cai silenciosamente em agregado. |
| synthetic | Fixture isolada, identificada em todos os exports/figuras. Verificação técnica, nunca evidência empírica. |

O plano `configs/analysis-time-bands-v2.json` congela antes da leitura de
observações: bins de 400 em [0,4000), borda inferior inclusiva, sensibilidades
200/800, horizontes 30/60/120min, suporte de exibição 50 tentativas/20 eventos/
dois grupos, semente 20261020 e 200 réplicas por grupos. O suporte é filtro de
exibição, não prova de precisão. Dados fora da grade e quantis ausentes não
recebem fallback. `scope=all_descriptive` não é validação independente. Para
candidatos a validação, criar outro plano/destino com `scope=train`.

## Recuperar a execução de 18/10

Backup: `regular-simple-v1-2026-10-18.tar.gz` (163.846.376 bytes), SHA-256
`86305d7f0c7152c80733b1019aae95480f03bea07b52742266b72ea4b94012c9`.
O arquivo não está no Git, nas releases/artefatos Actions nem nas pastas acessíveis
do ambiente desta sessão. Referência: `evidence/2026-10-18-sample-aft.json`.
Não há acesso ao armazenamento de outros GPTs/sessões. Agregados não provam recuperação.

```bash
python study/scripts/restore_notebook_snapshot.py CAMINHO/regular-simple-v1-2026-10-18.tar.gz work/restored-2026-10-18
```

O restaurador verifica os bytes antes de extrair, recusa travessia de diretórios/
links e exige destino novo. Localize `snapshot.json` dentro do destino. O backup
omitiu audit e previsões regeneráveis. Faça uma **cópia** desse diretório e execute
`python -m lorem_study prepare --work COPIA`; confira os hashes históricos.
Reprodução completa (inclui ajuste explícito dos modelos):

```bash
python study/scripts/reproduce.py ORIGEM_RESTAURADA work/reproduction-2026-10-18
```

É um comando externo opt-in, não uma célula. Não reproduzir sobre a origem.
Para os notebooks, executar:

```bash
python study/scripts/run_notebooks.py --mode full --work COPIA_COMPLETA --output work/notebooks-recovered
```

Um snapshot novo não recupera a execução antiga; a comparação usa seus agregados
preservados. `full_snapshot_recovered` indica disponibilidade do snapshot fornecido,
não identidade automática com o de 18/10; hashes distinguem as execuções.

## Nova amostra real autorizada em 20/10

A nova coleta usa `configs/notebooks-2026-10-20.json`, derivado do seletor
`regular-simple-v1`, com semente 20261020 fixada antes da coleta. Preserva
origem/data, exclusão integral de variantes/especiais, gate >=2s, retry e brutos
imutáveis. Sem substituição por desfecho, sem sobrescrever 18/10. Os comandos
opt-in, a partir da raiz, com o pacote instalado:

```bash
python -m lorem_study collect --work work/study-2026-10-20/snapshot --config study/configs/notebooks-2026-10-20.json
python -m lorem_study prepare --work work/study-2026-10-20/snapshot
python -m lorem_study split --work work/study-2026-10-20/snapshot
python -m lorem_study train-baseline --work work/study-2026-10-20/snapshot
python -m lorem_study train --work work/study-2026-10-20/snapshot
python study/scripts/run_notebooks.py --mode full --work work/study-2026-10-20/snapshot --output work/notebooks-real-2026-10-20
```

Coleta acessa Codeforces somente; falha parcial não autoriza análise de contest
incompleto. `collect --offline` retoma só o cache com checksums. O runner lê
modelos, nunca os ajusta. Os novos modelos ficam identificados separadamente dos
históricos. Toda a nova amostra exibida é exploratória/descritiva; **avaliação
preditiva nova não realizada**, inclusive se houver sobreposição com contests da
amostra anterior. Novas alegações preditivas exigem nova reserva de grupos sem
problemas compartilhados e sem exploração prévia. Repetição de usuários é limite.

## Smoke sintético

```bash
python study/scripts/notebook_fixture.py work/notebook-fixture
python study/scripts/run_notebooks.py --mode synthetic --work work/notebook-fixture --plan work/notebook-fixture/smoke-plan.json --output work/notebooks-synthetic
```

A fixture usa o pipeline existente e gera um plano **somente para smoke**, com
suporte de exibição 5/2/2 para exercitar curvas/ICs em uma amostra técnica pequena.
Não altera o plano real. Dataset, gráficos e exports são rotulados synthetic.

## Artefatos e interpretação

Cada JSON exporta versão/schema/protocolo, plano/hash, fonte/hash de snapshot,
agrupamento, suporte, unidade, estimador e horizonte. Dados individuais/pseudônimos
não são publicados. Os gráficos consomem as mesmas tabelas exportadas, sem
recalcular resultados na apresentação. `inputs.json` recusa misturar fontes no
mesmo destino; use nova pasta ao mudar plano/snapshot.

T1=P25/T2=P50/T3=P75 e T4=h pertencem a `analysis-time-bands-v2`. P25–P75 é
variação entre tentativas; não é IC. Empates/faixas vazias e orçamento incompatível
são preservados. AC em h é incluído; AC depois/censura/seguimento ausente são
separados. Bootstrap mantém multiplicidade de grupos e réplicas sem quantil;
IC95% é null se houver qualquer réplica sem a estatística, com proporção de ausência.
Não resolver no orçamento não equivale a incapacidade permanente.

A política Android `time-curve-v1` conserva p50/p75/p90 e permanece desabilitada.
D-012, Rules, contas, arquivo legado e estudo histórico não são alterados.


## Backup da nova execução verificada

`work/snapshot-backups/lorem-snapshot-2026-10-20.tar.gz`: 238.668.830 bytes,
SHA-256 `94b840d9cd799a7582ecc0ee3bdaf44a9947c7e472f98742880d19da58600b86`.
Inclui audit, features/labels, brutos, modelos e partições; omite apenas previsões
regeneráveis. 321 objetos brutos e oito arquivos principais foram verificados
lendo o archive. Contém handles públicos, fica fora do Git e deve ser preservado
para transportar a execução entre ambientes. Restaurar em destino novo:

```bash
python study/scripts/restore_notebook_snapshot.py work/snapshot-backups/lorem-snapshot-2026-10-20.tar.gz work/restored-new --sha256 94b840d9cd799a7582ecc0ee3bdaf44a9947c7e472f98742880d19da58600b86
python study/scripts/run_notebooks.py --mode full --work work/restored-new/study-2026-10-20/snapshot --output work/notebooks-restored-new
```

[Resultados e limitações reais](../results/2026-10-20-notebooks/REPORT.md) e
[evidência de execução](../evidence/2026-10-20-notebooks.json). Não confundir esse
backup novo com a recuperação do arquivo de 18/10, que segue ausente.
