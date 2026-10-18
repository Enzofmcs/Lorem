# Decisoes do Lorem

> Consulte este arquivo quando a tarefa depender de uma escolha de produto ou
> arquitetura. Nao escolha silenciosamente uma opcao apenas para continuar: registre
> a duvida e obtenha a definicao necessaria. Nao apague decisoes antigas; quando uma
> for substituida, marque-a como tal e acrescente a nova decisao.

## 11. Decisoes abertas

Nao preencher silenciosamente. Registrar a escolha em **Registro de decisoes** e atualizar as historias afetadas.

### DA-01 - Rating inicial

- [x] Usar rating oficial atual do Codeforces.
- [ ] Permitir que o usuario escolha o rating inicial.
- [ ] Usar um valor padrao e calibrar pelos primeiros Ipsums.

### DA-02 - Formula exata do Rating Lorem

- [x] Definir expectativa pela diferenca usuario-problema.
- [x] Definir peso do tempo.
- [x] Definir penalidade por revelar tags.
- [x] Definir ganho e perda maximos.
- [x] Nao aplicar regra especial de calibracao aos primeiros Ipsums.

### DA-03 - Tempo esperado

- [ ] Tempo fixo para todos os problemas.
- [x] Tempo calculado pela diferenca de rating.
- [ ] Tempo apenas como estatistica, sem afetar rating.

### DA-04 - Criterios de consolidacao

Resolvida pela D-015, aprovada na conversa em 2026-10-13:

- [x] Minimo de 8 tentativas na faixa.
- [x] Minimo de 70% de AC durante o Ipsum.
- [x] Minimo de 5 ACs sem dica.
- [x] Nos ACs sem dica, 3 tags oficiais distintas em 3 conjuntos de tags diferentes.
- [x] Janela dos 20 Ipsums encerrados mais recentes, ordenados por encerramento e ID.
- [x] Faixa consolidada e um marco permanente: nao diminui com falhas posteriores.

Faixas `[F, F + 99]`, de 800 em diante, em passos de 100, pelo rating do problema.
Problemas sem tags contam para tentativas e AC, mas nao para diversidade. AC
posterior preserva o insucesso original. A maior faixa que cumpriu os criterios em
uma janela fica consolidada; sem evidencia, `null`. A proxima faixa e a consolidada
+ 100; sem marco, avaliar a faixa atual do rating estimado (minimo 800).

A proposta anterior de reduzir consolidacao ao sair evidencia da janela foi
substituida: o Rating Lorem ja acompanha quedas de desempenho. Reavaliar janelas
historicas permite recuperar um marco mesmo apos interrupcao da persistencia.

#### Cenarios da D-015 antes da implementacao

| Evidencia na faixa em uma janela | Resultado esperado |
|---|---|
| 8 tentativas, 6 ACs, 5 sem dica, 3 tags em 3 conjuntos | Consolidar a faixa (75% AC). |
| 7 tentativas com todos os demais requisitos | Nao consolidar; falta amostra. |
| 8 tentativas, 5 ACs | Nao consolidar; taxa 62,5%. |
| 10 tentativas, 7 ACs e demais requisitos | Consolidar; fronteira exata de 70%. |
| 8 tentativas, 6 ACs, somente 4 sem dica | Nao consolidar; falta autonomia. |
| 3 tags sempre no mesmo conjunto | Nao consolidar; falta variedade de conjuntos. |
| Faixa ja alcancada seguida de 20 fracassos | Preservar o marco e avaliar progresso recente separadamente. |
| AC posterior em uma tentativa originalmente sem AC | Nao promover sucesso nem mudar a consolidacao por esse AC. |

### DA-05 - Limite do fallback de recomendacao

- [x] Resolvida por D-010: explorar ate tres passos de 100.
- [x] Resolvida por D-010: nao pedir confirmacao neste MVP.

### DA-06 - Atualizacao automatica

- Direcao solicitada pelo usuario em 2026-10-13: atualizacoes automaticas, sem depender de sincronizacao manual.
- Plano proposto na D-018: WorkManager para manutencao e polling com ciclo de vida para Ipsum visivel.
- Historias 15-17 definem implementacao/aceite; intervalos e limitacoes em [SYNC.md](SYNC.md).
- Estado do codigo: coordenacao, WorkManager, polling em RESUMED e verificacao do ativo no worker implementados (15-17).

### DA-07 - Transporte do estudo para Ipsums e integracao

O protocolo de [QUANTITATIVE.md](QUANTITATIVE.md) registra escolhas metodologicas
iniciais testaveis, nao resultados. As decisoes fornecidas pelo usuario estao nas
D-020 a D-025. Nao ha escolha pendente que impeca iniciar a Historia 22.

- [ ] Historia 28: avaliar resultados reais, suporte, erros e necessidade de novos experimentos.
- [ ] Historia 31: fixar tamanho/precisao e limiares aceitaveis antes do teste Ipsum;
  decidir identidade/mapeamento entre Rating CF e Lorem ou nao integrar.
- [ ] Historia 32: registrar dominio e apresentacao da integracao somente se a 31
  fornecer evidencia favoravel; manter bloqueada se a evidencia for insuficiente.

A formula D-012 nao foi substituida. Mudanca de pontuacao exige decisao propria.
Espera por amostra real e gate cientifico, nao exigencia de teste manual do app.

---

## 14. Registro de decisoes

Use o formato abaixo. Nao apagar decisoes antigas; marque quando forem substituidas.

| ID | Data | Decisao | Motivo | Historias afetadas |
|---|---|---|---|---|
| D-001 | 2026-09-18 | O desafio individual se chama Ipsum. | Criar linguagem propria e clara no produto. | 04-14 |
| D-002 | 2026-09-18 | Problemas ja tentados ou resolvidos nunca entram em novos Ipsums. | Garantir ineditismo real. | 02, 04 |
| D-003 | 2026-09-18 | A unica dica interna sera revelar as tags oficiais do Codeforces. | Oferecer ajuda mensuravel sem criar conteudo paralelo. | 05, 08, 11 |
| D-004 | 2026-09-18 | AC posterior muda a classificacao da pendencia, mas nao o rating original. | Preservar as condicoes avaliadas no Ipsum. | 08-10 |
| D-005 | 2026-09-18 | O Lorem usa somente tags oficiais do Codeforces. | Evitar complexidade desnecessaria. | 03, 11-13 |
| D-006 | 2026-09-18 | Rating estimado e faixa consolidada sao conceitos separados. | Evitar que poucos acertos isolados representem dominio amplo. | 11-13 |
| D-007 | 2026-09-18 | A estrutura inicial persiste somente um perfil local temporario com DataStore; integracoes do Codeforces permanecem falsas. | Comprovar persistencia e separacao de camadas sem antecipar Room, rede ou regras das historias seguintes. | 00, 01 |
| D-008 | 2026-09-22 | Arquivos JAR nao serao versionados; os scripts do Gradle baixam o bootstrap oficial para o cache e validam SHA-256. | Manter o diff da PR totalmente textual sem perder os comandos `./gradlew`. | 00 |
| D-009 | 2026-09-22 | O Rating Lorem inicial usa o rating oficial atual do Codeforces quando ele estiver disponivel; contas sem rating oficial iniciam em 800. | Aproveitar a melhor referencia publica existente sem impedir o uso por contas ainda nao ranqueadas e manter um ponto inicial coerente com a menor dificuldade dos problemas avaliados. | 01, 04, 08 |
| D-010 | 2026-09-23 | A recomendacao normaliza o Rating Lorem para o multiplo de 100 mais proximo, com minimo 800; sem candidato na faixa da categoria, explora primeiro as camadas adjacentes mais proximas em passos de 100, ate 300 pontos, preservando variedade e sorteio entre empates, sem confirmacao. Persiste categoria e faixa originais, rating escolhido e distancia do fallback; sem candidato no limite, nao cria Ipsum e oferece erro recuperavel. | Tornar o fallback previsivel, auditavel e limitado sem interromper o fluxo simples do MVP. | 04, 12 |
| D-011 | 2026-09-23 | **Substituida pela D-018; WorkManager implementado na Historia 16 e polling na 17.** As submissoes do Ipsum sao verificadas pelo botao e ao retornar para a tela/app, sem polling, `WorkManager`, servico ou trabalho permanente em segundo plano. | Manter atualizacao suficiente para o treino com menor complexidade, consumo e risco de consultas duplicadas. | 06 |
| D-012 | 2026-10-02 | O Rating Lorem usa expectativa Elo pela diferenca usuario-problema. AC parte de `round(8 + 32 * (1 - expectativa))`; sem AC perde `round(8 + 24 * expectativa)`, independentemente de desistir cedo. O tempo esperado e `clamp(60 + diferenca/10, 30, 120)` minutos; AC depois do esperado perde gradualmente ate metade do ganho no dobro do tempo, sem bonus por rapidez. Revelar tags reduz o ganho em cerca de 25%, no minimo 2 pontos. Ganhos ficam entre 1 e 40 e perdas entre -1 e -32. Nao ha calibracao especial. | Produzir feedback previsivel que valoriza dificuldade, tempo e autonomia sem permitir bonus por demora nem dupla pontuacao. | 08 |
| D-013 | 2026-10-11 | As estatisticas usam uma tentativa encerrada por id do Ipsum e por perfil, excluem ativos e contam AC somente em `COMPLETED`. Taxas com/sem dica usam o respectivo grupo como denominador. Tempo medio inclui tentativas sem AC, usa o intervalo original ate o encerramento e informa a amostra de tempos validos. Erros antes do AC usam somente `COMPLETED`. Rating evolui pelos snapshots persistidos, sem reconstruir avaliacoes ausentes. Rating do problema usa `selectedRating`; cada tag oficial recebe uma contribuicao por tentativa e o catalogo salvo inclui exposicao zero. AC posterior altera somente a situacao atual. Sem amostra, a metrica fica indisponivel; ordenacoes empatadas usam nome da tag. | Formalizar as definicoes do planejamento aprovado para que indicadores sejam auditaveis e nao confundam upsolve com desempenho durante o desafio. | 11 |
| D-014 | 2026-10-13 | Testes manuais passam a ser opcionais e nao bloqueiam conclusao nem avanco entre historias. Criterios obrigatorios exigem evidencia automatizada proporcional ao risco; conferencias humanas nao executadas ficam separadas, sem alegar aprovacao. Substitui a exigencia operacional de aguardar confirmacao manual. | Orientacao explicita do usuario nesta conversa. | 12-14 e futuras sessoes |
| D-015 | 2026-10-13 | Consolidar a maior faixa de 100 pontos com 8 tentativas, 70% de AC, 5 ACs sem dica e 3 tags em 3 conjuntos distintos numa janela de 20 Ipsums encerrados. Preservar permanentemente a maior faixa alcancada; avaliar janelas historicas para recuperar marcos. | Plano aprovado pelo usuario com ajuste: quedas recentes ja reduzem o Rating Lorem, enquanto a faixa registra consistencia demonstrada. | 13-14 |
| D-016 | 2026-10-13 | Dentro da primeira camada elegivel de rating, candidatos com tags oficiais usam prioridade lexicografica: mais tags ineditas, menor exposicao total, maior media de falha com amostra, maior uso de dica com amostra e menor sobreposicao com o ultimo Ipsum encerrado; sortear empates. Exposicao deduplica historico remoto e snapshots por problema. Persistir fatores e revela-los somente apos o encerramento. | Executar o plano da Historia 12 preservando as restricoes da D-010 e o contrato da D-013. | 12, 14 |
| D-017 | 2026-10-13 | O primeiro Ipsum exige uma importacao bem-sucedida do historico do perfil; ausencia de sincronizacao nao representa historico vazio. Depois disso, recomendacao e estatisticas podem usar dados locais. | Preservar o ineditismo confirmado pela D-002 no primeiro acesso e permitir recuperacao sem rede com dados existentes. | 04, 14 |
| D-018 | 2026-10-13 | O usuario solicita atualizacao automatica e sugere WorkManager. Proposta: preparacao unica, historico/pendencias a cada 15 min, catalogo/metadados a cada 24 h e submissoes a cada 15 s apenas com Ipsum visivel em RESUMED; verificar ativo tambem no worker. Intervalos desejados sujeitos ao Android. Reutilizar gate/coordenador, escritas parciais existentes, cache e politica offline D-017. | Eliminar cliques obrigatorios de sincronizacao, preservando rating, consolidacao e invariantes. Substitui a restricao da D-011. Historias 15-17 implementadas e verificadas. | 15-17 |
| D-019 | 2026-10-13 | Direcao visual proposta: azul profundo e verde, temas claro/escuro, tokens semanticos, navegacao inferior, hierarquia centrada no treino e dados com amostra. DESIGN.md e referencia SVG detalham a proposta; nao altera metricas ou introduz gamificacao. | Atender a solicitacao de tornar o app bonito e apresentavel, preservando Compose/Material 3 e comportamento do bloco 00-14. Base da Historia 18 implementada e verificada: escolha Sistema/Claro/Escuro global no DataStore, sem depender do perfil; abas restauram estado. Configuracao e Inicio (19) implementados e verificados, reutilizando consolidacao, preparacao/cache e coordenador existentes; sem nova regra de produto. Ipsum/Resultado (20) implementados e verificados: cronometro adaptavel com leitura acessivel sob demanda, confirmacoes restauraveis e resultado pelos snapshots existentes. Historico/Estatisticas (21) implementados e verificados: cards e filtros restauraveis, curva persistida e barras com alternativas textuais e amostras; D-013 preservada. Bloco 18-21 concluido. | 18-21 |

### Extensao quantitativa solicitada em 18/10/2026

| ID | Data | Decisao | Motivo | Historias afetadas |
|---|---|---|---|---|
| D-020 | 2026-10-18 | Base e novas recomendacoes usam exclusivamente problemas cujo contest de origem iniciou em ou apos 01/07/2020 UTC, com data verificavel; ausencia/ambiguidade torna inelegivel. Nunca inferir pelo id. Historicos, avaliacoes e impedimentos de repeticao antigos sao preservados. | Recorte explicito do usuario; data e criterio de elegibilidade, nao feature temporal. Implementacao futura no estudo e na 29. | 22-24, 29-32 |
| D-021 | 2026-10-18 | Comecar com participacoes oficiais individuais em contests encerrados e oldRating de contest.ratingChanges associado ao participante/contest. Guardar os dois ratings e delta. Auditar estreantes, ausencias e conflitos sem imputacao futura. Comparacao entre epocas, analises por ano e correcao temporal ficam fora do escopo. | Evitar rating posterior e ampliar a auditabilidade da populacao definida pelo usuario. | 22-28 |
| D-022 | 2026-10-18 | Hipotese operacional sequencial A/B/C: tempo de A desde inicio, seguintes desde primeiro AC do predecessor. Usar horario da primeira submissao com veredito final OK dentro do contest, sem upsolve. Preservar brutos, marcar saltos/inversoes/intercalacoes e medir cobertura. Sem AC nao vira zero/infinito/incapacidade; distinguir disponibilidade, dedicacao e observacao incompleta. | Medida aproximada solicitada; protocolo de censura e selecao em QUANTITATIVE.md e hipotese a verificar. | 24-28, 30-31 |
| D-023 | 2026-10-18 | Primeira entrega: estudo Python reproduzivel com referencia simples, modelo interpretavel, erros de tempo, probabilidades para orcamento explicito e incerteza. Separar contests completos de treino/teste e impedir atributos de desfecho. Historias 22-28 somam 24 h estimadas; nenhum resultado esta comprovado. | Escopo e restricao de esforco do usuario; protocolo inicial KM/AFT e escolha por validacao permanecem testaveis. | 22-28 |
| D-024 | 2026-10-18 | Validar posteriormente com Ipsums de inicio registrado e comparar Rating CF anterior ao inicio com Rating Lorem inicial antes de transportar o modelo. Tamanho/limiares definidos antes do teste; correlacao nao prova equivalencia e evidencia insuficiente impede integracao. | Evitar aplicar diretamente estimativas de contest ao treino ou ratings de significados diferentes. | 30-32 |
| D-025 | 2026-10-18 | Integracao eventual local, com regras/modelos/snapshots versionados, avaliacoes antigas preservadas e sem requisito de backend/login/nuvem. D-012 continua vigente; corte de recomendacoes nao depende de sucesso do modelo. | Preservar funcionamento local e invariantes solicitados pelo usuario. | 29-32 |

### Tabela de cenarios da D-012

Exemplos para Rating Lorem anterior 1200, sem calibracao:

| Cenario | Rating do problema | Tempo esperado | Tempo gasto | Dica | Variacao |
|---|---:|---:|---:|---|---:|
| AC no nivel | 1200 | 60 min | 45 min | nao | +24 |
| AC no nivel com dica | 1200 | 60 min | 45 min | sim | +18 |
| AC mais dificil | 1600 | 100 min | 90 min | nao | +37 |
| AC muito acima do esperado | 1200 | 60 min | 150 min | nao | +12 |
| Sem AC, tentativa completa | 1200 | 60 min | 60 min | indiferente | -20 |
| Sem AC, desistência precoce | 1200 | 60 min | 1 min | indiferente | -20 |
| Sem AC em problema mais dificil | 1600 | 100 min | 100 min | indiferente | -10 |
| Sem AC em problema mais facil | 800 | 30 min | 30 min | indiferente | -30 |

---
