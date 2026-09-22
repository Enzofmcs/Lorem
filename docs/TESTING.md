# Estrategia de testes e verificacoes

## Principio de qualidade

> A eficiencia de tokens e creditos e uma restricao operacional, nao um objetivo
> superior a qualidade. Otimize leitura, comandos e verificacoes redundantes, mas
> nunca deixe de testar uma regra critica apenas para economizar uso do Codex.

O objetivo e reduzir trabalho redundante, nao a cobertura das partes criticas. Bugs
evitaveis que comprometem regras centrais do Lorem custam mais do que testes
direcionados. Classifique primeiro o risco real da mudanca; se houver duvida entre
dois niveis, use o mais alto.

## Nivel 1 - alteracao simples ou visual

Exemplos: texto, layout, espacamento, navegacao simples, estado visual, botao e
mensagem de erro.

- Nao execute automaticamente toda a suite nem `lint + test + assembleDebug`.
- Faca somente a compilacao minima se houver risco de erro de compilacao.
- Quando a validacao visual for mais eficiente, forneca um smoke test manual curto.
- Nunca afirme que executou um teste manual que depende do usuario; registre
  `PENDENTE DE TESTE MANUAL`.

## Nivel 2 - logica isolada

Exemplos: filtros, deduplicacao, calculo, regra de dominio, selecao de problemas e
manipulacao de estados.

- Execute apenas os testes unitarios diretamente relacionados.
- Se uma regra relevante nao tiver teste adequado, crie um teste pequeno e especifico.
- Evite a suite inteira durante iteracoes quando o teste direcionado fornece a evidencia.

## Nivel 3 - integracao

Exemplos: Room, DataStore, API do Codeforces, persistencia, sincronizacao,
serializacao, Repository e mudancas que atravessam varias camadas.

- Execute os testes relevantes da integracao.
- Execute uma verificacao de compilacao ou build apropriada ao caminho modificado.
- Cubra falhas recuperaveis, idempotencia e limites externos quando aplicaveis.

## Nivel 4 - alto risco

Exemplos: Rating Lorem, maquina de estados do Ipsum, garantia de um unico Ipsum
ativo, prevencao de duplicidade, recomendacao, migracao de banco, arquitetura,
concorrencia e persistencia critica.

- Nao economize testes necessarios apenas para reduzir creditos.
- Combine testes unitarios, integracao e regressao dos invariantes afetados ate obter
  confianca real.
- Verifique caminhos de sucesso, falha, repeticao e recuperacao relevantes.

## Nivel 5 - marco do projeto

Priorize `test`, `lint` e `assembleDebug` completos:

- ao concluir uma historia relevante ou um bloco de historias;
- antes de um PR importante ou demonstracao;
- antes de considerar uma fase estavel;
- quando alteracoes amplas justificarem regressao completa.

Nao execute os tres automaticamente depois de cada pequena edicao. Um PR somente
documental nao exige build Android, lint ou testes do aplicativo.

## Regras para comandos

- Prefira teste direcionado ao global durante o desenvolvimento.
- Nao repita o mesmo comando que falhou sem analisar primeiro a causa.
- Nao refaca um build completo sem alteracao relevante desde o ultimo build.
- Evite comandos redundantes e logs enormes quando uma saida resumida basta.
- Em uma falha, isole primeiro o erro relevante; amplie a investigacao com evidencia.
- Nao abra emulador nem execute testes instrumentados automaticamente salvo quando
  forem realmente necessarios ao risco ou criterio de aceite.
- Registre exatamente os comandos executados e seus resultados.
- Economia operacional nunca justifica ignorar risco tecnico importante.

## Smoke tests manuais delegaveis

Podem ser delegados, quando apropriado: abertura do app, presenca de botao ou texto,
navegacao, aparencia, mensagem de erro, digitacao basica, revelar tags, iniciar ou
encerrar um Ipsum pela interface, fechar/reabrir o app, conferir historico ou tela de
estatisticas.

Forneca passos numerados e resultados observaveis, por exemplo:

1. Abra a tela X.
2. Execute Y.
3. Aguarde Z.
4. Confirme que W aparece e que nao ocorre comportamento indevido.

Marque `PENDENTE DE TESTE MANUAL` e aguarde a confirmacao do usuario antes de marcar
o criterio como comprovado. Teste manual complementa, mas nao substitui teste
automatizado de regra critica.

## Regras que permanecem prioritarias

Preserve ou crie testes automatizados para:

- identidade por `contestId + index`;
- exclusao de problema anteriormente tentado de um novo Ipsum;
- deduplicacao de submissoes e sincronizacao idempotente;
- WA seguido de AC;
- distincao entre submissoes anteriores e posteriores ao inicio;
- garantia de apenas um Ipsum ativo;
- rating nao recalculado duas vezes;
- AC posterior sem recalculo do rating original;
- filtros da recomendacao, fallback de rating e variedade de tags;
- calculo do Rating Lorem e consolidacao de faixa;
- estatisticas e persistencia critica;
- parsing das respostas `OK` e `FAILED` da API.

Nao remova esses testes por economia.

## Matriz minima do produto

- [ ] **Regras puras:** identidade, deduplicacao, filtros, sorteio, fallback, tags,
  tempo, submissoes, rating, consolidacao e estatisticas.
- [ ] **Integracao:** `OK`/`FAILED`, limite da API, falta de rede, atualizacao
  idempotente e persistencia.
- [ ] **Jornada manual:** primeiro acesso, handle invalido, retomada, dica, WA seguido
  de AC, falha, AC posterior, historico e estatisticas.

## Ambiente e comandos completos

O repositorio nao versiona JARs. Use Gradle 8.11 ou superior com Java 17. Os scripts
`./gradlew` e `gradlew.bat` baixam e validam o bootstrap oficial quando necessario.
Nos marcos de Nivel 5:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

No Windows sem WSL, execute os mesmos comandos em um terminal com o ambiente exigido.
