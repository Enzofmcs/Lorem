# Demonstracao do Lorem

O roteiro percorre as Historias 00-17. O ensaio em dispositivo e opcional; o aceite
funcional usa os testes automatizados, conforme D-014.

## Preparacao

Use Java 17 e SDK Android 35 para gerar o APK com `./gradlew assembleDebug`.
O artefato fica em `app/build/outputs/apk/debug/app-debug.apk`. Para a demonstracao
real, instale-o em Android 8.0 ou superior e use seu proprio handle do Codeforces.
O Lorem consulta somente dados publicos; as submissoes sao feitas no Codeforces.

## Jornada com AC no Ipsum

1. Valide o handle em Configuracao. Na Home, aguarde a preparacao automatica do
   historico e catalogo; acompanhe cada etapa. O primeiro Ipsum exige importacao
   bem-sucedida do historico. Use os botoes de recuperacao somente se necessario.
2. Inicie um Ipsum. Confira o problema e o cronometro; rating, categoria e fatores
   da recomendacao permanecem ocultos. Abra o problema no Codeforces.
3. Opcionalmente, confirme `Revelar topicos`. O uso da dica fica registrado uma vez.
4. Apos uma submissao errada, volte ao app: a consulta e imediata ao retomar e se
   repete a cada 15 s com a tela em RESUMED. Confira erros definitivos e ultimo
   sucesso. Depois do AC, o Resultado mostra tempo, erros, dica, rating e fatores
   da recomendacao. `Atualizar agora`/`Tentar novamente` sao recuperacao opcional.
5. Feche o Resultado, abra Historico e Estatisticas. Confira a tentativa em
   `Resolvidos em Ipsums`, os indicadores originais e a evolucao persistida.
6. Inicie outro Ipsum: o problema anterior nao participa do sorteio. A recomendacao
   prioriza tags ineditas, pouca pratica, maior falha, uso de dica e recencia, dentro
   das restricoes de rating e ineditismo.

## Jornada com pendencia e upsolve

1. Inicie outro Ipsum e escolha um motivo em `Encerrar sem AC`. O rating diminui
   uma unica vez e a tentativa aparece em `Pendentes`.
2. Resolva o problema posteriormente no Codeforces, fora da tentativa. Aguarde
   o proximo trabalho elegivel (intervalo desejado de 15 min, sujeito ao Android),
   ou use `Atualizar agora` no Historico para recuperacao. O primeiro AC posterior
   move o item para `Resolvidos fora de Ipsums`, preservando a avaliacao original.
3. Repita a atualizacao. A operacao nao duplica o item nem altera a avaliacao.
4. Abra Estatisticas: pendencias diminuem e upsolves aumentam; a taxa de AC durante
   Ipsums e a evolucao original permanecem iguais.
5. Reabra o app sem rede para consultar os dados locais. Se havia Ipsum ativo,
   o app retoma a tentativa e calcula o tempo pelos timestamps persistidos.

## Consolidacao

Na Home ha um resumo da faixa consolidada; em Estatisticas ha os requisitos da
proxima faixa. Uma janela de 20 encerrados exige, na faixa avaliada, 8 tentativas,
70% de AC, 5 ACs sem dica e diversidade de 3 tags em 3 conjuntos distintos.
A maior faixa alcancada permanece registrada, inclusive apos fracassos recentes;
o Rating Lorem estimado continua mudando conforme o desempenho. Upsolve nao cria
AC para consolidacao.

## Reproducao automatizada

```bash
./gradlew testDebugUnitTest --tests dev.lorem.app.CompleteAppJourneyTest --max-workers=2 --console=plain
./gradlew testDebugUnitTest --tests dev.lorem.app.data.local.FullTrainingJourneyTest --max-workers=2 --console=plain
./gradlew test lint assembleDebug --max-workers=2 --console=plain
```

`CompleteAppJourneyTest` percorre as telas com Room/DataStore reais e Codeforces
roteirizado somente no teste. `FullTrainingJourneyTest` cobre reinicio do repositorio,
migracao 9-10 validada pelo Room, recuperacao de rating/consolidacao, cliques
concorrentes, dica tardia e sincronizacao depois da troca de perfil. As jornadas
automaticas tambem cobrem WA/TESTING/AC sem botoes e resultado apos trabalho em
segundo plano/restauracao, aguardando RESUMED.

## Limites da avaliacao do TCC

Registre tentativas, AC no Ipsum, autonomia, tempo, variedade de tags e upsolves
antes/depois do periodo de uso. Colete percepcao de confianca e controle de tempo
separadamente. O rating interno e a consolidacao sao indicadores do aplicativo;
sozinhos nao demonstram melhora de aprendizagem nem validam pedagogicamente os
limiares da D-015. Consulte o metodo de avaliacao em `SPEC.md`.
