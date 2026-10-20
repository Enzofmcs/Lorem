# Decisoes do Lorem

> Consulte este arquivo quando a tarefa depender de uma escolha de produto ou
> arquitetura. Nao escolha silenciosamente uma opcao apenas para continuar: registre
> a duvida e obtenha a definicao necessaria. Nao apague decisoes antigas; quando uma
> for substituida, marque-a como tal e acrescente a nova decisao.

## 11. Decisoes abertas

Nao preencher silenciosamente. Registrar a escolha em **Registro de decisoes** e atualizar as historias afetadas.

### DA-01 - Rating inicial

**Nova regra solicitada em 19/10/2026 (D-035), planejada na 35:**

- [x] Todo novo vinculo uid+handle inicia em 800, independentemente do rating CF.
- [ ] Permitir que o usuario escolha o rating inicial.
- [ ] Usar calibracao especial nos primeiros Ipsums.

A escolha historica de usar rating oficial (D-009) foi substituida prospectivamente.
Codigo atual ainda usa a regra antiga; login/troca de handle nao resetam progresso,
e snapshots antigos permanecem preservados conforme plano de migracao da 38.

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
- [ ] Historia 31: aplicar o pre-registro D-032 a uma coorte real; decidir
  identidade/mapeamento entre Rating CF e Lorem ou nao integrar. Limiares ja fixados.
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
| D-009 | 2026-09-22 | **Substituida prospectivamente pela D-035; registro historico.** O Rating Lorem inicial usa o rating oficial atual do Codeforces quando ele estiver disponivel; contas sem rating oficial iniciam em 800. | Aproveitar a melhor referencia publica existente sem impedir o uso por contas ainda nao ranqueadas e manter um ponto inicial coerente com a menor dificuldade dos problemas avaliados. | 01, 04, 08 |
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
| D-025 | 2026-10-18 | **Complementada pela D-033:** estudo/modelo continuam locais; novo bloco do app passa a prever login e Firebase. Integracao eventual local, com regras/modelos/snapshots versionados, avaliacoes antigas preservadas e sem requisito de backend/login/nuvem. D-012 continua vigente; corte de recomendacoes nao depende de sucesso do modelo. | Preservar funcionamento local e invariantes solicitados pelo usuario. | 29-32 |
| D-026 | 2026-10-18 | Por instrucao explicita do usuario, contests especiais ficam fora da analise. Qualquer contest com um problema em duas versoes (ex.: B1/B2, facil/dificil) e excluido integralmente, inclusive seus problemas simples e prefixos validos. Preservar brutos e registrar exclusoes; nao relaxar o filtro para obter amostra. O recorte vigente exige formato CF e ordem simples A/B/C. | Confirmar que a exclusao desses contests e intencional, e nao uma limitacao a remover para treinar o modelo. | 23-28 |
| D-027 | 2026-10-18 | Nova amostra regular-simple-v1: percorrer a ordem de hash/rodizio existente e verificar estrutura antes de congelar 20 contests. Reconhecimento conservador: CF FINISHED com Div. 1-4 ou Codeforces Global Round no nome, sem marcadores especiais; exigir A/B/C simples em standings. Registrar todos os candidatos/recusas e fontes; erros interrompem/retomam, sem pular candidato por falha. Nenhuma submissao, score ou resultado do modelo escolhe a amostra. Piloto anterior permanece imutavel. | Aplicar D-026 antes da coleta completa e evitar selecionar por sucesso ou descartar somente apos ajustar modelos. | 23-28 |
| D-028 | 2026-10-18 | AFT v1 usa NumPy 2.3.5/SciPy 1.17.0 opcionais, likelihoods log-normal/Weibull explicitas com censura e L2 {0,0.01,0.1}; centros/curva de censura apenas no treino. Selecao na validacao por Brier IPCW integrado de 0-120 min, grade 5 min, G>=0,05, ECE de casos conhecidos <=KM global+0,05; CV por grupos no treino e MAE como desempates. Gate falho exporta apenas diagnostico indisponivel. Regras fixadas antes do ajuste real; metodo e limites em study/MODEL.md. | Implementar a Historia 26 com numerica auditavel e poucas dependencias; manter teste reservado e nao presumir ganho de precisao. | 26-28 |

| D-029 | 2026-10-18 | Avaliacao reservada com AFT/particoes congelados, 200 replicacoes pareadas de grupos e suporte comum. KM por delta teve Brier integrado menor nesta amostra (0,122746 vs 0,126693; diferenca AFT-KM IC95% 0,001861 a 0,007770). Nao retunar no teste, nao integrar previsoes nem substituir D-012. Manter ratings distintos; novo experimento exige nova amostra/protocolo e validacao em Ipsums. | Evidencia real favorece referencia simples; quatro grupos, selecao/censura e ratings da coleta limitam conclusao e transporte. Metodos em study/MODEL.md e relatorio da 28. | 27-32 |

| D-030 | 2026-10-18 | Realizar o corte aprovado no Android com contest.list pelo gate, origem/data/fonte/versao persistidas e migracao aditiva. Catalogo legado sem prova nao habilita novas recomendacoes; cache verificado funciona offline e fallback nao relaxa o corte. Preservar historicos, ativos, pendencias e avaliacoes. | Historia 29 verificada: 226 debug/164 release, lint sem erros e APK; origem nao e inferida pelo id. | 29 |
| D-031 | 2026-10-18 | Base Ipsum local v1: snapshots CF observados antes do inicio e Lorem inicial, exportacao explicita por perfil/UUID sem ativo/identificadores diretos, ausencias legadas preservadas. Preparo verifica metadados inteiros/D-026 pelo gate; piso operacional 10 participantes/200 tentativas/100 eventos, 6/2/2 por participante/hash/seed. Conflitos/coorte congelada alterada recusados. Piso nao aprova transporte: 31 fixa limiares finais antes do teste e 32 continua bloqueada com dados insuficientes. | Evitar retroimputacao, confusao entre CF/Lorem, tempo de parede/dedicacao e testes de software/evidencia real. Nenhum export real fornecido; n=0/insufficient_data. | 30-32 |
| D-032 | 2026-10-19 | Pre-registro Ipsum transport v1 antes de qualquer coorte real: piso final 100 participantes/2.000 tentativas/1.000 eventos, 60/20/20 por participante; IC95% superior MAE<=20 min/Brier integrado<=0,15/ECE por orcamento<=0,10, com larguras <=10 min/0,05/0,10, dominio>=90%, mediana em >=95% dos eventos e suporte integral 0-120. Reusar hashes exatos da 28; identidade versus linear com peso igual por participante ajustado so no treino, escolha na validacao congelada antes do teste, bootstrap pareado 1.000 vezes por participante. Exigir limites no total e sem dica; pausas desconhecidas permanecem desconhecidas. Fixture nunca aprova transporte; limite favoravel real apenas habilita decisao de dominio, nao integracao automatica. | Limites operacionais justificados no pre-registro study/TRANSPORT_PROTOCOL.md a partir da referencia 28; contagens nao garantem poder. Dados reais ausentes mantem 31 incompleta/32 bloqueada, ratings distintos e D-012 vigente. | 31-32 |

### Contas e limite de treino solicitados em 19/10/2026

Requisitos confirmados pela solicitacao do usuario; **implementacao ainda pendente**
nas Historias 33-38. Detalhes de arquitetura/fronteira/migracao em FIREBASE.md sao
propostas tecnicas deste planejamento, nao comportamentos ja verificados.

| ID | Data | Decisao | Motivo | Historias afetadas |
|---|---|---|---|---|
| D-033 | 2026-10-19 | **Arquitetura Functions substituida pela D-039.** Login exclusivamente Google pelo Firebase Authentication e banco Firebase (Firestore). Planejar Functions para escritas oficiais e prazo independente do Android. Substitui a exclusao de Firebase/login do escopo do app; preserva o estudo e seus gates locais. | Solicitacao explicita do usuario; configuracao/deploy pertencem a implementacao futura. | 33-38; compatibilidade 32 |
| D-034 | 2026-10-19 | Rating e progresso separados por uid+handle normalizado; varios handles por conta e mesmo handle permitido em contas diferentes. Sem reserva global nem nova prova de posse Codeforces. Consulta de existencia nao autentica o dono do handle. | Usuario aceita handles repetidos e a dependencia das submissoes no Codeforces para o treino. | 34-36, 38 |
| D-035 | 2026-10-19 | Todo vinculo novo inicia em 800, tenha ou nao rating oficial CF. Substitui D-009 para novos vinculos. Sessao, sincronizacao ou troca/reinsercao restauram o saldo existente, sem reset; 800 nao e novo piso permanente. Preservar avaliacoes legadas em arquivo separado conforme proposta de migracao, sem importar seu saldo para vinculo novo. | Ponto inicial fixo solicitado pelo usuario, sem reescrever avaliacoes antigas. | 35-36, 38; regra inicial da 01 |
| D-036 | 2026-10-19 | **Agendamento/materializacao adaptados pela D-039; prazo preservado.** Prazo maximo absoluto de 2h por Ipsum, independente de app/rede. Sem AC valido no prazo, encerrar automaticamente como sem AC e aplicar D-012 uma vez. Planejar expiresAt autoritativo, fechamento no prazo e verificacao pendente quando API incompleta; AC enviado em prazo e recebido tarde nao vira upsolve. Avaliacoes encerradas antigas nao sao recalculadas. | Limite solicitado pelo usuario, com recuperacao e sem dupla avaliacao. Fronteira inclusiva, fila/varredura e migracao detalhadas como propostas tecnicas em FIREBASE.md. | 36-38; tempo/submissoes das 05-10 |

### Implementacao inicial do protocolo (22-25), 18/10/2026

- Python >=3.11,<3.14, runtime/testes somente stdlib; setuptools 75.8.0 para
  empacotar. KM pequeno com risco/empates verificaveis; nenhuma dependencia AFT
  antecipada. Configuracao e dicionario em [study/](../study/README.md).
- Selecao por hash/rodizio de divisao antes do ajuste; campos ausentes/quarentena
  separados, grupos transitivos, 6/2/2 minimo e treino exclusivo dos baselines.
  Sao realizacoes do protocolo D-021/023, nao mudancas de produto.
- Zero/negativo oldRating separado por historico insuficiente, sem imputacao pelo
  futuro; nenhum user.rating por linha. Expansao dessa populacao exige evidencia.
- Na implementacao inicial, o dominio autorizado estava bloqueado por 403; nenhuma
  tentativa de contorno. Bloqueio resolvido na continuacao de 18/10/2026: piloto
  1824/2062 adquirido por completo, com reproducao offline e 39 testes.
- A API agora exige standings anonimo completo com apenas contestId; status continua
  paginado. Adaptacao do contrato sem mudar populacao oficial, corte ou protocolo.
- Piloto manteve selecao congelada e exclusao integral por indices B1/B2 e E1/E2:
  zero intervalos validos. Nao ampliar a ordem nem substituir contests para melhorar
  cobertura; qualquer revisao futura exige protocolo versionado antes de novo ajuste.
  Nenhum ganho de precisao ou transporte comprovado.

### Tabela de cenarios da D-012

Exemplos matematicos da D-012 para Rating Lorem anterior 1200, sem calibracao.
O cenario de AC aos 150 min e historico/da funcao pura: apos implementar D-036,
novos Ipsums nao aceitam AC depois de 120 min; a formula nao foi recalibrada:

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

### Implementacao da base Firebase (33-34), 19/10/2026

| ID | Data | Decisao | Motivo | Historias afetadas |
|---|---|---|---|---|
| D-037 | 2026-10-19 | **Arquitetura Functions substituida pela D-039; preview/isolamento preservados.** Base em Functions Node 22/TypeScript, Firestore Rules fechadas e us-central1. Android usa Credential Manager, Google ID e Firebase Auth; verifica token Google e bootstrapAccount antes de liberar sessao. Preview com flag false por padrao ate 38; destino de handles e ponto de integracao da 35, sem reutilizar perfil/worker legado. | Entrega pequena das 33/34 sem expor dados locais sem uid nem antecipar handles/rating/timeout das 35-38. Scope de Auth na main dispatcher, geracao/cancelamento impedem callback antigo de restaurar sessao. | 33-34, integracao futura 35-38 |

O usuario respondeu que ainda nao tem projeto Firebase. Nenhum projeto real,
OAuth Google, billing, assinatura release ou deploy foi criado. Esses criterios
permanecem pendentes; emuladores e teste de SDK/estado nao comprovam OAuth real.
Guia: [FIREBASE_SETUP.md](FIREBASE_SETUP.md). Evidencia: [33/34](evidence/2026-10-19-firebase-33-34.json).

### Localizacao Firebase escolhida pelo usuario, 19/10/2026

| ID | Data | Decisao | Motivo | Historias afetadas |
|---|---|---|---|---|
| D-038 | 2026-10-19 | **nam5 preservado; compute/Tasks removidos pela D-039.** Usuario informou projeto Firebase criado e escolheu Firestore em nam5 (US), multirregiao. Manter Functions e futura fila Tasks em us-central1. Substitui a recomendacao inicial de mesma regiao para o banco; nao altera a constante Android/backend de Functions. | Escolha explicita do usuario; Firestore e acessado pelo projeto/database e a regiao da Function continua compativel. ID/JSON Android, criacao/configuracao do banco, OAuth e deploy ainda nao verificados; nao atualizar evidencia historica como se houvesse validacao real. | 33-38 |

### Restricao Spark e sincronizacao Android, 19/10/2026

| ID | Data | Decisao | Motivo | Historias afetadas |
|---|---|---|---|---|
| D-039 | 2026-10-19 | Usuario solicita refazer 33/34 respeitando Spark. Auth Google + Firestore nam5; sem billing/Functions/Tasks/Admin SDK/backend externo como requisito. Bootstrap Android por transacao online, uid capturado e schema minimo protegido por Rules; tokens/cache offline nao liberam sessao. Planejamento 35-38 passa a SDK/transacoes/Rules, verificacao CF/calculo Android e WorkManager agendado no inicio; atraso de sincronizacao nao estende prazo absoluto 2h. | Substitui a arquitetura de D-033/D-037 e compute de D-038. Rules nao verificam Codeforces contra um app modificado; nenhuma promessa de anti-fraude independente. Sem rede/dispositivo/sessao elegivel, sincronizacao pode aguardar nova execucao; nao prometer job de servidor nem execucao exata. Preservar uid+handle, inicial800, locks/delta unico, arquivo legado e gates31/32. | 33-38 |

### Handles no preview Spark, 19/10/2026

| ID | Data | Decisao | Motivo | Historias afetadas |
|---|---|---|---|---|
| D-040 | 2026-10-19 | Handles usam chave cf_ + lowercase(Locale.ROOT), grafia canonica user.info e criacao SDK transacional idempotente com 800/null/progressVersion=0. Rules create-only impedem reset; selecao cloud versionada exige vinculo proprio e ausencia do lock current. Room 13 acrescenta cache por uid/binding sem atribuir dados legados; ProfileLink/trabalhos cloud incluem uid/binding/selecao, e worker legado nao executa contexto cloud. Preview false por padrao ate 38; treino/progresso gravado pertencem a 36. | Aplicar D-034/D-035 no Spark, restaurar selecao apos reinstalacao, isolar contas de mesmo handle e respostas atrasadas, preservar D-012 e historicos antigos. SDK/Rules reais em emuladores verificam restauracao de saldo evoluido com fixture explicita, sem alegar treino Firebase ou prova de posse. Novas Rules precisam ser publicadas; confirmacao anterior cobre somente 33/34. | 35, contratos para 36-38 |

### Implementacao 35-37 em preview, 19/10/2026

| ID | Data | Decisao | Motivo | Historias afetadas |
|---|---|---|---|---|
| D-041 | 2026-10-19 | Implementar 35-37 em namespace Firestore por uid+handle, selecao Firestore versionada e ProfileLink por uid+handle+binding+selecao; cache de historico por uid+handle+binding+progresso; preservar Room legado para 38. Inicio atomico com marker por contestId+index e lock por uid; resultado/rating/consolidacao/versao/liberacao (delete de current) atomicos, usando D-012/D-015. Prazo ancorado em runtime/clock.checkedAt do servidor + elapsedRealtime/boot; manter nanos em cutoff e resultado sem AC, respeitando segundos CF. Jobs unicos de prazo e verificacao com rede/retry e identidade capturada. | Concretizar as tres proximas historias autorizadas sem atribuir cache legado a uma conta, reiniciar saldo, duplicar avaliacao ou estender prazo. Novo inicio exige cobertura CF completa e servidor; rede/pagina malformada/TESTING preservam lock e adiam saldo. Mantem Spark D-039 e flag false ate migracao/rollout 38. Novas Rules precisam de publicacao; confirmacao anterior e apenas da versao 33/34. | 35-38 |

### Escopo final e migracao, 19/10/2026

| ID | Data | Decisao | Motivo | Historias afetadas |
|---|---|---|---|---|
| D-042 | 2026-10-19 | Usuario confirma Google como unico provedor no projeto real e retira a historia 31 do escopo: "ignore essa história, ela não é plausivel pro escopo". A 32, dependente dessa validacao, fica fora desta entrega e sem previsoes habilitadas. Preservar codigo, protocolos e evidencias historicos sem inventar resultados nem marcar criterios empiricos como concluidos. | Fechar a configuracao pendente da 33/34 por confirmacao do responsavel e adequar o trabalho restante ao escopo solicitado. Nenhum OAuth/readback administrativo alegado. | 31-34, 38 |
| D-043 | 2026-10-19 | Room 14 acrescenta marcador local sem uid. Reconciliar ativo legado no inicio/prazo original, fixar primeira fronteira manual/timeout e aguardar cobertura CF completa. Finalizacao e congelamento sao atomicos; triggers tornam tentativas/submissoes/historico antigos somente leitura. Arquivo acessivel como dados do aparelho, separado da conta Google. Sincronizar somente IDs publicos de exclusao por uid+handle; nunca importar ratings/avaliacoes. Firebase passa a entrada padrao; exports cloud v2 usam pseudonimos aleatorios locais e contexto de identidade/800/prazo/confianca, separados do protocolo antigo. | Concluir adocao sem perder historico, duplicar avaliacao, conceder novas 2h ou voltar a recomendar problemas antigos em outro cliente. Permissoes de exclusions exigem publicar as Rules da 38. Mesmo handle em uids diferentes preserva saldos independentes; agrupamento de export e por handle nesta instalacao, sem prometer identificacao entre reinstalacoes. | 38 |


### Pontuação por curvas solicitada em 19/10/2026

| ID | Data | Decisão | Motivo | Escopo |
| --- | --- | --- | --- | --- |
| D-044 | 2026-10-19 | Implementar política experimental time-curve-v1: T1=p50, T2=p75, T3=min(p90,2h), somente com quantis identificados, suporte e ordem estrita. Marco ausente/colisão/escala incompatível usa D-012 integral, com motivo salvo no início. Modelo recebe CF anterior; saldo Lorem 800 nunca substitui esse valor. Bônus 1,25 e redução 0,5 são candidatos, dica não torna AC negativo e ganho zero é permitido em T3. Snapshot imutável e decomposição persistida no cloud; arquivo legado intacto. | Nova solicitação autoriza desenvolver o caminho de software antes excluído da 32, sem reabrir a 31 ou declarar transporte validado. Exemplo real 1200/1600 não identifica p75/p90 até 120 min. Manter ativação independente desabilitada; simulações não validam progressão pedagógica e teste examinado não serve para ajuste. | [Contrato e limites](TIME_SCORING.md), [cenários](../study/results/time-scoring-v1/REPORT.md), história 39 |


### Direção visual do estudo e paridade Firebase, 20/10/2026

| ID | Data | Decisão | Motivo | Escopo |
| --- | --- | --- | --- | --- |
| D-045 | 2026-10-20 | O estudo principal passa a descrever a distribuição do tempo observado até AC por rating anterior do participante e rating do problema, com notebooks que reutilizam lorem_study. Preservar comparação AFT/KM e resultados anteriores; 31 continua retirada e 32 fora da entrega. Sem coorte Ipsum obrigatória. | O autor precisa explorar e apresentar evidências palpáveis do Codeforces sem depender de recrutamento ou de um modelo mais complexo. | 44–49; REDIRECTION.md |
| D-046 | 2026-10-20 | Recuperar navegação, treino/resultado, filtros, gráficos, tema e atualização de pendências sobre Firestore, usando as telas 18–21 como referência. Conferir contrato cliente/Rules 38/39. Preservar Google/Spark, uid/handle, 800, prazo, exclusões e arquivo legado. | A 38 promoveu uma jornada cloud simplificada sem paridade com a experiência anterior; integridade de dados e quantidade de testes não demonstram paridade de recursos. | 40–43; sem rollback de dados nem deploy autorizado |
| D-047 | 2026-10-20 | Planejar contrato analítico analysis-time-bands-v2: T1=P25, T2=P50, T3=P75, T4=h operacional (120min na demonstração). Quantis ausentes/empatados e orçamento incompatível permanecem explícitos. Não resolveu no prazo não significa incapacidade permanente. Não reinterpretar time-curve-v1 p50/p75/p90, ativar política, alterar snapshots ou retunar teste examinado. | Separar quartis estatísticos, orçamento e regra de produto; impedir limites inventados e confusão com a história 39. | 46–49; contrato a implementar na 47 |
