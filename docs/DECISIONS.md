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

- [ ] Definir expectativa pela diferenca usuario-problema.
- [ ] Definir peso do tempo.
- [ ] Definir penalidade por revelar tags.
- [ ] Definir ganho e perda maximos.
- [ ] Definir comportamento dos primeiros Ipsums de calibracao.

### DA-03 - Tempo esperado

- [ ] Tempo fixo para todos os problemas.
- [ ] Tempo calculado pela diferenca de rating.
- [ ] Tempo apenas como estatistica, sem afetar rating.

### DA-04 - Criterios de consolidacao

- [ ] Quantidade minima de tentativas.
- [ ] Taxa minima de AC.
- [ ] Quantidade minima de AC sem dica.
- [ ] Diversidade minima de tags.
- [ ] Janela de desempenho recente.

### DA-05 - Limite do fallback de recomendacao

- [ ] Quantos passos de 100 podem ser explorados.
- [ ] Se o app deve pedir confirmacao ao sair muito da faixa.

### DA-06 - Atualizacao automatica

- [ ] Apenas botoes manuais e verificacao ao retornar ao app.
- [ ] Verificacao periodica enquanto a tela do Ipsum estiver aberta.
- [ ] Trabalho em segundo plano.

Recomendacao inicial de simplicidade: botoes manuais mais verificacao ao retornar ao app, sem trabalho permanente em segundo plano.

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

---
