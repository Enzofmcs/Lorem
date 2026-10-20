# Curvas ampliadas de tempo até 50% de AC (histórias 50–56)

O notebook [06_escala_t50.ipynb](notebooks/06_escala_t50.ipynb) e o painel HTML
apresentam X=faixa de rating do exercício e Y=t50 em minutos, com cores por
oldRating anterior e estilos por observado/modelo. A história 49 e o artigo do
TCC ficam fora desta entrega. O contrato científico está em
[docs/SCALE_T50.md](../docs/SCALE_T50.md).

## Abrir os resultados offline

Baixe o código da PR ou faça checkout da branch da implementação. Os arquivos
em [results/scale-t50-v1](results/scale-t50-v1) são resultados reais agregados;
consulte o `REPORT.md` nessa pasta para quantidades efetivas, perdas e hashes.
A [leitura dos resultados](results/scale-t50-v1/INTERPRETATION.md) compara
regiões, sensibilidades e limites diretamente das tabelas reais.
O alvo 400 não cabe no universo: a triagem de 525 candidatos encontrou **292**
contests estruturalmente compatíveis, mantendo o corte e a exclusão integral de
variantes/especiais. Selecionados, completos e elegíveis após joins são contagens
diferentes. Não substituir os recusados por contests fora do protocolo.

1. Abra `study/results/scale-t50-v1/dashboard.html` num navegador local. Todos os
   controles funcionam sem internet ou kernel; não é um link de site hospedado.
2. Selecione faixas de usuário e modelos. O modo por faixas usa misturas nas
   tentativas reais; o modo fixo usa ratings exatos retidos. Cada lacuna tem um
   motivo na tabela, e cada ponto mostra suporte/cobertura/IC.
3. Baixe CSV/JSON/SVG/PNG pelos botões. Os gráficos estáticos e o notebook
   executado também ficam na pasta publicada. O HTML exportado do notebook
   incorpora figuras/tabelas estáticas e o painel interativo, sem CDN; as células
   Python exigem kernel para novas consultas. Execute/confie no notebook para usar o iframe
   no Jupyter. O `dashboard.html` é a alternativa direta.

O download leve inclui **agregados**, curvas/pontos, modelos congelados, figuras,
notebook executado e HTML. Ele não contém brutos, handles, audit ou microdados e
não permite alegar que joins/bootstrap foram refeitos. A análise retém ICs e
réplicas já calculados. `delivery-manifest.json` lista checksums dos arquivos;
a própria evidência aponta o hash desse manifesto.

## Jupyter no Linux e Windows

Use Python 3.11–3.13 (execução de referência em 3.12). Na raiz do repositório:

```bash
python3 -m venv work/notebook-venv
work/notebook-venv/bin/python -m pip install -r study/requirements-notebooks.lock
work/notebook-venv/bin/python -m pip install --no-build-isolation --no-deps -e study
work/notebook-venv/bin/python -m jupyterlab study/notebooks/06_escala_t50.ipynb
```

PowerShell:

```powershell
py -3.12 -m venv work/notebook-venv
work/notebook-venv/Scripts/python.exe -m pip install -r study/requirements-notebooks.lock
work/notebook-venv/Scripts/python.exe -m pip install --no-build-isolation --no-deps -e study
work/notebook-venv/Scripts/python.exe -m jupyterlab study/notebooks/06_escala_t50.ipynb
```

O modo padrão é `aggregate`, com HTTP bloqueado no bootstrap, sem coleta/treino.
A célula inicial identifica a fonte e verifica os arquivos. Os parâmetros de
faixa/modelo/orçamento podem ser editados nas células; o painel oferece filtros
sem kernel. Os cenários fixos podem ser consultados com quaisquer ratings
positivos dentro do domínio, usando os modelos locais.

Para executar em kernel limpo e exportar outra cópia (diretório de saída vazio):

```bash
work/notebook-venv/bin/python study/scripts/run_scale_notebook.py --output work/scale-demo
```

No Windows, substitua o executável por `work/notebook-venv/Scripts/python.exe`.
O runner cria kernelspec isolado para o próprio interpretador; não exige um kernel
global. Windows é suportado pelo código e documentado; os testes efetivamente
executados constam da evidência, sem afirmar teste num Windows real.

## Reanálise completa e restauração

O backup completo é **separado do Git e do pacote leve**. O relatório/evidência
registram caminho real no ambiente de execução, tamanho, SHA-256, manifesto e
teste de restauração. Esse caminho local não é um link público de download e o
código da PR não transfere o backup. Acesso aos arquivos desse ambiente ou
transferência específica do arquivo é necessário para baixar os microdados.
O snapshot antigo de 18/10 continua não recuperado; não é pré-requisito escondido.

Após obter o arquivo e seu checksum, restaure em uma pasta inexistente:

```bash
work/notebook-venv/bin/python -m lorem_study.scale_backup restore CAMINHO_BACKUP.tar.gz work/scale-restored --sha256 SHA256_DO_RELATORIO
```

A restauração verifica inventário/hashes de todos os arquivos, bytes HTTP
**descomprimidos**, snapshot e integridade SQLite. O pacote contém
`snapshot/`, `analysis/`, `models/` e `backup-manifest.json`. Partições mantêm
features/labels/audit comprimidos. Hardlinks derivados para brutos são reconstruídos
por `scale_prepare`; nenhum symlink dependente do caminho da máquina é necessário.

```bash
work/notebook-venv/bin/python -m lorem_study.scale_collect work/scale-restored/snapshot --config study/configs/scale-t50-v1.json --plan study/configs/scale-t50-plan-v1.json --offline
work/notebook-venv/bin/python -m lorem_study.scale_prepare work/scale-restored/snapshot --output work/scale-restored/analysis
work/notebook-venv/bin/python study/scripts/run_scale_notebook.py --mode full --full-root work/scale-restored/analysis --output work/scale-reanalysis
```

O replay deve usar zero requisições e manter o snapshot. O preparo usa o mesmo
`prepare.py` por contest em processos separados, junta três streams 1:1 por
sample_id e grava SQLite transacional. Retomadas verificam partições/hashes,
sem repetir amostras. O modo full refaz estatísticas/bootstrap sobre o SQLite;
para refazer os joins desde os brutos, use uma pasta preparada nova. Nenhuma
reanálise modifica o snapshot/modelos ou retreina. O backup contém handles públicos
nos brutos e pseudônimos no audit; mantenha-o fora do Git.

A aquisição/preparo medem RAM por processo no Linux/macOS; Windows registra RAM
como indisponível, sem inventar zero. Hardlinks têm fallback por cópia e reserva
de espaço. Os tempos, versões e medições reais publicados se referem ao ambiente
observado, não a promessa de desempenho em outra máquina.

## Nova aquisição explícita

O plano [scale-t50-plan-v1.json](configs/scale-t50-plan-v1.json) foi congelado
antes da consulta nova ao universo e da análise ampliada. A configuração deriva
de `regular-simple-v1`, com semente nova, alvo 400 e teto de 5.000 candidatos.
Para repetir com metadados atuais, escolha **um diretório novo**, preservando
os snapshots publicados:

```bash
work/notebook-venv/bin/python -m lorem_study.scale_collect work/new-scale/snapshot --config study/configs/scale-t50-v1.json --plan study/configs/scale-t50-plan-v1.json
work/notebook-venv/bin/python -m lorem_study.scale_prepare work/new-scale/snapshot --output work/new-scale/analysis
```

Repita o mesmo comando para retomar a execução congelada. `--offline` recusa
cache ausente sem rede. `--universe-only` permite verificar viabilidade antes
das chamadas de standings/status. `--import-cache CAMINHO_SNAPSHOT` é opcional,
com verificação dos bytes/hashes e registro das datas originais; metadados do
universo são consultados novamente, sem chamar o cache antigo de coleta nova.
Brutos `.json.gz` preservam SHA-256 dos bytes HTTP descomprimidos, e JSON legado
continua legível. Nenhuma submissão/AC/resultado decide a seleção estrutural.

O gate persistido entre reinícios e o cliente único mantêm intervalo >=2s;
Retry-After/backoff e paginação completa continuam obrigatórios. Os limites do
plano são 10.000 tentativas de rede, 12 GiB de brutos comprimidos, reserva de
4 GiB livres e 128 MiB por resposta. O objetivo de RAM de 2 GiB é uma projeção
operacional por processo, medida/relatada; o coletor limita cada resposta e o
preparo isola cada contest. Se um limite duro for atingido, a execução para com
checkpoint, sem fabricar completude. Não aumente orçamento ou elegibilidade
silenciosamente para chamar a amostra de 400.

```bash
work/notebook-venv/bin/python -m lorem_study.scale_analysis work/new-scale/analysis --plan study/configs/scale-t50-plan-v1.json --models study/results/scale-t50-v1/models --output work/new-scale/results
```

Essa análise é nova e descritiva; os metadados/rating de problema podem ter mudado.
Amostras já exploradas não viram teste independente. Uma nova alegação preditiva
exigiria reserva de grupos antes de olhar os desfechos, não uma troca de rótulo.

## Limites de interpretação e verificação

- t50 é primeira passagem de F_AC>=0,5 com censura; não é mediana só dos AC,
  duração D-012, dedicação exclusiva ou capacidade individual. Ausência é null
  com motivo, nunca zero/120/infinito.
- Bordas são [a,b), oldRating principal de 400, problema de 200. Fora da grade é
  contado. Suporte/censura/grupos e razões são exportados, sem fallback oculto.
- Bootstrap reamostra grupos inteiros com multiplicidade. Qualquer réplica sem
  quantil impede o IC conservador principal. Pessoas repetem entre grupos;
  ligações conhecidas não garantem detecção de todos os aliases.
- Modelos são congelados, comparados por mistura de CDF sobre suporte comum.
  Cenário fixo é separado, KM global pode coincidir e KM delta perde níveis
  absolutos. Fontes externas não verificadas e artefatos ausentes não ganham
  linhas inventadas. D-012 é somente referência operacional, fora do eixo t50.
- IC observado mede amostragem por grupos, sem incerteza de ajuste dos modelos.
  Sensibilidades de grade, pisos e janelas não são uma seleção do resultado mais
  favorável. Android/D-012/time-curve-v1 e o artigo não mudam.

Testes de referência:

```bash
work/notebook-venv/bin/python -m unittest discover -s study/tests -p 'test_scale*.py' -v
work/notebook-venv/bin/python -m unittest discover -s study/tests -v
```

**TESTE MANUAL OPCIONAL - NÃO EXECUTADO.** A evidência lista verificações
sintéticas, equivalência em dados reais, kernels limpos, navegador automatizado,
replay e restauração, separadamente das conclusões científicas.
