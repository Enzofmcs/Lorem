# AGENTS.md - Guia operacional do Lorem

> Instrucoes compactas para todas as sessoes. A documentacao detalhada esta dividida
> por assunto em [`docs/`](docs/README.md); leia apenas o contexto exigido pela tarefa.

## Produto e prioridades

**Lorem** e um aplicativo Android de treino individual para programacao competitiva.
Ele recomenda um problema inedito do Codeforces, conduz uma tentativa cronometrada
chamada **Ipsum** e produz feedback sobre desempenho.

Prioridades, nesta ordem:

1. Fluxo completo do Ipsum funcionando.
2. Correcao dos dados e das regras.
3. Utilidade real para o treinamento.
4. Simplicidade de desenvolvimento e manutencao.
5. Aparencia visual.

## Regras invariantes essenciais

- Identifique um problema por `contestId + index`, nunca apenas pelo nome.
- Um problema ja tentado, resolvido, usado em outro Ipsum ou pendente nao pode ser recomendado.
- So pode existir um Ipsum ativo.
- Submissoes anteriores ao inicio nao contam para o Ipsum atual.
- Um Ipsum encerrado nao pode alterar o rating duas vezes.
- AC posterior muda a classificacao da pendencia, mas nao recalcula o rating original.
- Rating, tags e categoria permanecem escondidos enquanto o Ipsum esta ativo.
- Use somente tags oficiais do Codeforces.
- Centralize a API e respeite no maximo uma requisicao a cada dois segundos.

As regras completas e o escopo fechado estao em [`docs/SPEC.md`](docs/SPEC.md).

## Base tecnica

- Kotlin, Jetpack Compose, um modulo, ViewModel + StateFlow e Coroutines.
- Room para dados estruturados e DataStore para configuracoes pequenas.
- Cliente HTTP centralizado, Navigation Compose e injecao manual simples.
- Sem Hilt ou backend proprio sem necessidade e decisao registrada.
- Gradle 8.11 ou superior e Java 17; o repositorio nao versiona JARs.

Preserve uma estrutura existente que ja seja funcional. Nao adicione dependencia,
backend ou abstracao sem necessidade concreta.

## Estado atual

- **Foco:** Historia 01 - Vincular o handle do Codeforces.
- **Ultima historia verificada:** Historia 00 - Estrutura basica.
- **Primeiro item pendente:** executar o smoke test manual da troca de handle em dispositivo.
- **Proxima entrega demonstravel:** confirmar manualmente a Historia 01 e iniciar a Historia 02.
- **Bloqueios conhecidos:** nenhum.

Progresso e aceite: [`docs/ROADMAP.md#historia-01---vincular-o-handle-do-codeforces`](docs/ROADMAP.md#historia-01---vincular-o-handle-do-codeforces).

## Politica de contexto

1. Leia sempre este `AGENTS.md`.
2. Identifique a tarefa e abra somente sua secao em [`docs/ROADMAP.md`](docs/ROADMAP.md),
   mais as dependencias indicadas nela.
3. Consulte em [`docs/SPEC.md`](docs/SPEC.md) apenas as partes das regras alteradas.
4. Consulte [`docs/DECISIONS.md`](docs/DECISIONS.md) quando houver duvida de produto
   ou arquitetura; nao invente uma decisao para destravar a tarefa.
5. Consulte [`docs/TESTING.md`](docs/TESTING.md) antes de definir a verificacao.
6. Inspecione primeiro os arquivos diretamente relacionados e amplie a busca somente
   quando houver evidencia de outras dependencias.
7. Evite varrer ou carregar todo o repositorio sem necessidade concreta.

## Forma de trabalho

1. Confirme no codigo o que ja existe para o item atual.
2. Apresente um microplano de 3 a 7 passos antes de editar.
3. Trabalhe apenas no item atual e em pre-requisitos indispensaveis.
4. Prefira uma entrega pequena funcionando de ponta a ponta.
5. Preserve arquitetura, padroes, escopo e regras; nao altere produto silenciosamente.
6. Use dados falsos somente quando claramente isolados do fluxo real.
7. Nao implemente itens fora do escopo nem recursos especulativos.

## Qualidade e verificacao

> A eficiencia de tokens e creditos e uma restricao operacional, nao um objetivo
> superior a qualidade. Otimize leitura, comandos e verificacoes redundantes, mas
> nunca deixe de testar uma regra critica apenas para economizar uso do Codex.

> O objetivo e reduzir trabalho redundante, e nao reduzir cobertura das partes
> criticas do sistema. Bugs evitaveis nas regras centrais custam mais do que testes
> direcionados.

- Classifique o risco e aplique o nivel correspondente de [`docs/TESTING.md`](docs/TESTING.md).
- Durante o desenvolvimento, prefira o teste direcionado; use a regressao completa
  nos marcos ou quando o risco justificar.
- Nao repita comando falho sem diagnosticar a causa nem repita build sem mudanca relevante.
- Nao alegue teste manual que nao executou. Registre `PENDENTE DE TESTE MANUAL`,
  forneca passos numerados e aguarde confirmacao antes de marcar o criterio.
- Nao marque `[x]` sem evidencia: `[ ]` significa nao comprovado e `BLOQUEADO:` exige
  informacao, decisao ou recurso externo.
- Nunca conclua uma historia com criterio obrigatorio pendente.

## Encerramento da sessao

1. Execute as verificacoes proporcionais definidas em `docs/TESTING.md`.
2. Atualize somente os checklists realmente comprovados em `docs/ROADMAP.md`.
3. Registre decisoes novas ou substituidas em `docs/DECISIONS.md`.
4. Acrescente a sessao e mantenha o historico curto em `docs/SESSIONS.md`.
5. Atualize o estado acima se o foco mudou.
6. Informe o que mudou, como foi verificado, testes manuais pendentes e proximo passo.

## Mapa da documentacao

- [`docs/SPEC.md`](docs/SPEC.md): produto, escopo e regras funcionais estaveis.
- [`docs/ROADMAP.md`](docs/ROADMAP.md): historias, aceite, progresso, dependencias e marcos.
- [`docs/DECISIONS.md`](docs/DECISIONS.md): decisoes abertas e registro historico.
- [`docs/TESTING.md`](docs/TESTING.md): estrategia proporcional a risco e comandos.
- [`docs/SESSIONS.md`](docs/SESSIONS.md): registro curto das sessoes recentes.

## Prompt padrao para futuras sessoes

```text
Leia AGENTS.md e identifique a tarefa atual. Consulte somente a secao necessaria do
ROADMAP.md e as partes relacionadas da especificacao ou decisoes quando relevantes.
Inspecione primeiro os arquivos diretamente relacionados. Implemente a menor mudanca
funcional correta. Use TESTING.md: testes direcionados durante o desenvolvimento e
verificacoes completas nos marcos adequados. Nao reduza testes de logica critica para
economizar creditos. Ao terminar, atualize o progresso e informe o que mudou, como foi
verificado, o que requer teste manual e o proximo passo.
```
