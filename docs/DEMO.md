# Demonstracao do Lorem

O roteiro percorre as Historias 00-21. O ensaio em dispositivo e opcional; o aceite
funcional usa os testes automatizados, conforme D-014.

## Preparacao

Use Java 17 e SDK Android 35 para gerar o APK com `./gradlew assembleDebug`.
O artefato fica em `app/build/outputs/apk/debug/app-debug.apk`. Para a demonstracao
real, instale-o em Android 8.0 ou superior e use seu proprio handle do Codeforces.
O Lorem consulta somente dados publicos; as submissoes sao feitas no Codeforces.

## Tema e navegacao (conferencia opcional)

1. No cabecalho, abra Configuracoes. Em Aparencia, alterne Sistema, Claro e Escuro.
   Reabra o app: a escolha deve permanecer. Sistema acompanha o modo do aparelho.
2. Alterne Inicio, Historico e Estatisticas pela barra inferior. A aba selecionada
   tem indicador e rotulo; filtros, secao e rolagem devem permanecer ao voltar.
3. Abra Configuracoes a partir de uma aba e use Voltar: a mesma tela e retomada.
4. Com Ipsum ativo, use Voltar no cabecalho. A tentativa continua; retome pelo Inicio.
   Ipsum e Resultado usam telas dedicadas, sem barra de abas.
5. Opcionalmente, confira fonte em 200%, paisagem e TalkBack: rotulos, estado
   selecionado, acoes e mensagens devem continuar acessiveis por rolagem.

**TESTE MANUAL OPCIONAL - NAO EXECUTADO** nesta sessao.

## Jornada com AC no Ipsum

1. Informe o handle publico e toque Conectar Codeforces. Na Home, confira saudacao,
   Rating Lorem e referencia oficial. Aguarde a preparacao automatica: Iniciar Ipsum
   explica o bloqueio ate o historico e catalogo ficarem prontos. Ver preparacao ou
   Configuracoes mostra progresso/ultimo sucesso de cada etapa e recuperacao opcional.
   Nenhum clique de sincronizacao e exigido no fluxo normal.
2. Toque Iniciar Ipsum (ou Continuar Ipsum para retomar). Confira o problema e o
   cronometro; rating, categoria e fatores da recomendacao permanecem ocultos. Abra
   o problema por Abrir no Codeforces, acao principal. O timer mostra Tempo decorrido;
   nao ha prazo fatal. No TalkBack, ative Consultar tempo decorrido para ouvir o valor
   atual, sem anuncios por segundo.
3. Opcionalmente, confirme `Revelar topicos`. O uso da dica fica registrado uma vez.
4. Apos uma submissao errada, volte ao app: a consulta e imediata ao retomar e se
   repete a cada 15 s com a tela em RESUMED. Confira erros definitivos e ultimo
   sucesso. Depois do AC, o Resultado mostra tempo, erros, dica, rating e fatores
   da recomendacao. `Atualizar agora`/`Tentar novamente` sao recuperacao opcional.
5. No Resultado, confira AC/Encerrado sem AC, rating anterior -> atual e variacao,
   tempo, dica e erros. Abrir no Codeforces acessa o problema; Voltar ao inicio
   retorna ao Inicio. Use as abas Historico e Estatisticas. Confira a tentativa em
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
   Em Ver ultimo resultado, o AC posterior e sua data ficam separados do encerramento
   original sem AC; o rating anterior -> atual e sua perda original permanecem.
4. Abra Estatisticas: pendencias diminuem e upsolves aumentam; a taxa de AC durante
   Ipsums e a evolucao original permanecem iguais.
5. Reabra o app sem rede para consultar os dados locais. Se havia Ipsum ativo,
   o app retoma a tentativa e calcula o tempo pelos timestamps persistidos.

## Consolidacao

Na Home ha um resumo da faixa consolidada; Ver progresso da faixa expande os
requisitos da proxima faixa, tambem disponiveis em Estatisticas. Uma janela de 20
encerrados exige, na faixa avaliada, 8 tentativas,
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

`AppearanceNavigationTest` verifica abas, filtros, rolagem, secao, Configuracoes,
tema e restauracao com Room/DataStore. `PreferencesAppearanceRepositoryTest` fecha
e reabre o DataStore e confirma as tres opcoes e independencia do perfil.
`FoundationLayoutTest` gera renderizacoes em `app/build/reports/visual/` e verifica
fonte ampliada, paisagem, insets e semantica; `ThemeContrastTest` valida os pares
semanticos efetivamente usados. `HomeConfigurationLayoutTest` cobre as telas da
Historia 19 em claro/escuro, 360 dp, 320 dp/fonte 200% e paisagem, com erros inline,
loading e recuperacao por etapa. No Inicio, confira resumo local e Ver ultimo
resultado; configuracoes de perfil/tema/sync ficam fora do corpo principal.
`IpsumLayoutTest` cobre cronometro de 123 horas, confirmacoes com motivo e resultados
AC/sem AC/upsolve em claro/escuro, 320 dp/fonte 200%, 360 dp e paisagem.
A fonte ampliada tambem e aplicada aos recursos da janela de confirmacao.

## Limites da avaliacao do TCC

Registre tentativas, AC no Ipsum, autonomia, tempo, variedade de tags e upsolves
antes/depois do periodo de uso. Colete percepcao de confianca e controle de tempo
separadamente. O rating interno e a consolidacao sao indicadores do aplicativo;
sozinhos nao demonstram melhora de aprendizagem nem validam pedagogicamente os
limiares da D-015. Consulte o metodo de avaliacao em `SPEC.md`.

## Demonstracao do estudo quantitativo (22-28)

59 testes aprovados; [relatorio real](../study/results/2026-10-18/report/REPORT.md)
e [reproducao](../study/README.md#avaliacao-relatorio-e-reproducao-limpa-27-28).
A amostra tem 19 contests elegiveis, 274.073 tentativas validas e particoes 11/4/4.
O AFT Weibull foi escolhido na validacao e permaneceu fixo ao abrir o teste.

1. Abrir o relatorio real e seus CSVs/figuras. Mostrar n, eventos/censuras/exclusoes,
   cobertura, erros em minutos, Brier/calibracao conhecida e IPCW, ICs de 200 sorteios
   por grupo e intervalos preditivos separados dos ICs.
2. Conferir que KM por delta teve menor Brier integrado que o AFT nesta amostra;
   comparar MAE somente em suporte comum e reconhecer os quatro grupos reservados.
3. Executar `predict` para os dois ratings 1200/1600 e orcamentos 30/60/120,
   primeiro baseline e depois `--model aft`; mostrar quantis indisponiveis e dominio.
   A consulta e ilustrativa, nao um resultado individual observado.
4. Reproduzir com pacote instalado em venv limpo e diretorio novo via
   `scripts/reproduce.py`. Conferir zero chamadas de coleta e hashes identicos,
   inclusive modelos, metricas, tabelas e SVG/PNG. Backup bruto permanece local.

Nao usar fixtures como resultados reais. A hipotese sequencial mede parede,
abandono pode tornar censura informativa, ratings de problemas sao da coleta e
participantes repetem. Nenhuma mudanca no Rating Lorem/D-012. Validacao com Ipsums
e transporte dos ratings antecedem uso das previsoes no app. O corte de problemas
no app sera demonstrado na Historia 29.
