# Estudo ampliado: curvas de tempo até 50% de AC

**Dados reais, comparação descritiva; avaliação preditiva independente não realizada.**
Histórias 50–56. A história 49 e o artigo do TCC não são alterados.

## Amostra efetiva

- Alvo: 400; universo de metadados: 525 candidatos.
- Triagem estrutural completa: **292 selecionados**, déficit estrutural 108.
- Aquisição: **292 completos**; **278 elegíveis após joins**.
- 6,900,178 observações = 4,131,895 eventos + 622,457 censuras + 2,145,826 exclusões.
- Participantes (handles pseudonimizados) distintos válidos: 378,445; problemas distintos válidos: 1,430.
- 292 grupos conhecidos; 272,036 participantes se repetem entre grupos.
- 99/140 células com t50 principal identificado; 78 dessas com IC conservador disponível.
- Fora da grade: 630,649 observações, 0 válidas.

O universo não comporta 400 sob os filtros existentes. Não foram aceitos especiais,
versões fácil/difícil ou origens anteriores ao corte para preencher a meta.
As perdas, motivos, grupos, denominadores e sobreposição com amostras examinadas
estão em [coverage.json](coverage.json). Não some pessoas distintas entre células.

## Abrir e comparar

[Painel HTML interativo offline](dashboard.html), [CSV gerador](t50.csv),
[JSON completo/ICs/réplicas](analysis.json), [cenários fixos](fixed-queries.json),
[KM observado](figures/t50-observado.png) e [modelos sobrepostos](figures/t50-modelos.png).
Há versões SVG das duas figuras. [Notebook fonte](../../notebooks/06_escala_t50.ipynb)
e [guia Linux/Windows, modo completo e aquisição](../../SCALE_T50_README.md).
Quando incluídos, `06_escala_t50.ipynb` e `.html` nesta pasta são as cópias executadas.

X é faixa de rating do exercício [a,b); Y é primeira passagem da CDF de AC >=0,5,
em minutos. Cores representam oldRating prévio, estilos representam métodos.
Mediana considera censura; não é percentil só dos AC. O orçamento h=120min é
referência, sem cortar quantis conhecidos. Ausências são null com motivo, sem
interpolação de lacunas. O painel oferece faixas/modelos, suporte, cenários fixos,
destaque e exportação. Seus controles funcionam sem kernel/rede.

## Modelos e incerteza

AFT, KM global e KM por delta de 20/10 são congelados, com hashes e domínio em
[plan.json](plan.json). A população/pesos são os mesmos no suporte comum; o KM
observado é reestimado nesse subconjunto. Primeiro mistura-se a CDF nas covariáveis
reais e depois se inverte. Cenário fixo é separado. KM global pode coincidir entre
faixas; KM delta perde níveis absolutos. Sem extrapolação/fallback oculto.
Consulta parcial de 18/10 e trabalhos externos sem artefato verificável não
produzem curvas arbitrárias. D-012 é duração operacional, sem CDF/t50.

Bootstrap usa 200 réplicas de grupos globais inteiros com multiplicidade,
inclusive grupos/células com zero linhas elegíveis. Grupos efetivos são reportados
por célula. Qualquer réplica sem quantil torna o IC principal ausente, sem descartar
réplicas. O JSON retém réplicas, resultados por grupo, leave-one-group-out,
sensibilidades de grade/pisos e estimandos. ICs são de amostragem do observado;
os parâmetros dos modelos ficam fixos, sem estimar incerteza de ajuste.

Trata-se de tempo sequencial de parede, com seleção de prefixos e possível censura
informativa; não mede dedicação exclusiva/capacidade individual. Pessoas repetidas
e aliases não conhecidos limitam independência. Ratings iguais não garantem 50%
de AC por prazo. Nenhuma superioridade preditiva, efeito pedagógico ou transporte
para Ipsums é estabelecido por este aumento de N. Android/D-012/time-curve-v1
continuam preservados.

## Proveniência e recursos

- Snapshot: `a08506d0997c7f318f9642605e60b1f442416f9623cb3d55b00fb423f080e14e`.
- Plano: `9a30e084eff38412f61759f67a97ed60bb6ea760ea785825b71875cda1945368`; seleção: `ec51e85ae5364f50674e5867bf3b439af65a56388bb505e6efb3d918fddad4ff`.
- Aquisição nova: 4417 tentativas de rede, intervalo mínimo medido 2.000016s.
- Janela: 2026-10-20T17:32:52.289196Z até 2026-10-20T20:14:15.548903Z; 9683.3s de parede.
- Bytes HTTP novos: 24,217,193,517; páginas status no snapshot: 4106.
- Cache importado: 319 objetos, com datas/hashes originais, não chamados de coleta atual.
- Maior RSS de worker de preparo: 966758400 bytes; SQLite: 2893377536 bytes.

O preparo reutiliza o preparador existente por contest, em processos isolados,
com streams comprimidos, joins 1:1 verificados e SQLite transacional. A equivalência
com o caminho anterior foi conferida em fixture e nos 20 contests reais anteriores.
As medições acima são desta execução, sem projetá-las como medição de 400.

## Backup separado e verificações

O Git/pacote leve contém agregados e modelos, **não** snapshot/audit com handles.

Backup completo local: `/workspace/Lorem/work/snapshot-backups/lorem-scale-t50-2026-10-20.tar.gz`; 4,089,037,530 bytes; SHA-256 `b900b862310c8c5be0362004288cfc146f7397a405fd07947d3ae6ce3f184a90`.
Esse caminho identifica onde o arquivo existe no ambiente, sem ser um link público.
Acesso ao ambiente ou transferência específica é necessário para obter o arquivo.

Restauração verificada: 11207 arquivos, 4730 objetos HTTP descomprimidos; SQLite `ok`.

Replay offline: 0 requisições; snapshot `a08506d0997c7f318f9642605e60b1f442416f9623cb3d55b00fb423f080e14e`.

Testes automatizados e kernels limpos detalhados na evidência. **TESTE MANUAL OPCIONAL - NÃO EXECUTADO.**
