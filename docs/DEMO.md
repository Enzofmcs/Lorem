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

## Demonstracao das Historias 29-30

1. Atualizar catalogo: fonte/data verificadas ficam em cache. Antigo/sem data nunca
   habilita nova recomendacao, incluindo fallback. Continuar um ativo antigo ainda
   funciona; historicos, pendencias e avaliacoes continuam intactos.
2. Atualizar perfil antes de novos Ipsums. A observacao CF anterior fica no snapshot;
   atualizar o perfil depois nao altera CF/Lorem iniciais ou notas anteriores.
3. Configuracoes -> Dados do estudo -> Exportar tentativas. Escolher arquivo local;
   o ativo e omitido, sem handle/nome/tags/ids de submissao. Legados ficam ausentes.
4. Preparar com `prepare-ipsums` conforme [guia](../study/README.md#base-local-de-ipsums-30).
   Verificar metadados oficiais/D-026, funil, snapshots e particoes por participante.
   Sem exports reais, mostrar n=0/insufficient_data e nenhuma metrica de transporte.

Roteiro de dispositivo opcional, nao executado. 31/32 continuam bloqueadas por
falta de dados/evidencia; nao habilitar previsoes nem alterar a D-012.

Para demonstrar a ferramenta da 31 sem dados ficticios, executar `evaluate-ipsums`
conforme [guia Python](../study/README.md#avaliacao-local-de-transporte-31-evidencia-real-pendente).
O [pre-registro](../study/TRANSPORT_PROTOCOL.md) e os hashes ficam visiveis;
sem exports, o relatorio registra n=0/insufficient_data e nao calcula metricas.

## Jornada integrada 38

1. Atualizar a instalacao sem limpar dados e usar as Rules 38 no projeto Spark.
2. Conferir Arquivo local deste aparelho: historico/rating anteriores separados
   da conta Google e somente leitura, sem importar saldo.
3. Se havia ativo, preservar inicio/prazo. Concluir no Codeforces ou encerrar com
   motivo; sem rede/TESTING, aguardar verificacao antes de abrir outro treino.
4. Entrar com Google, cadastrar handle e observar 800 somente se o vinculo e novo.
5. Completar/expirar Ipsum, repetir atualizacao e recuperar em outro cliente:
   resultado aplicado uma vez e problemas do arquivo nao voltam a ser candidatos.
6. Trocar conta: mesmo handle pode iniciar seu proprio saldo, sem receber o rating
   do arquivo ou de outra conta. Export local omite identificadores diretos/ativo.

Conferencia humana opcional; nao alegada como executada. Historia 31 retirada do
escopo pelo usuario, 32 fora desta entrega; nao demonstrar previsoes estatisticas.

## Pontuação experimental por curvas para o TCC (D-044)

Apresentar [decisão](TIME_SCORING.md) e [relatório de cenários](../study/results/time-scoring-v1/REPORT.md).
A política está desabilitada: não há alegação de progressão pedagógica validada.
A consulta real CF 1200/problema 1600 tem p50 disponível, mas p75/p90 ausentes até
120 min; mostrar a resposta explícita de indisponibilidade no CLI. O modelo KM
completo não acompanha este checkout, e a consulta retida não representa todo bin.

Para demonstrar as quatro faixas, usar exclusivamente os testes identificados como
sintéticos: `TimeScoringTest`, `TimeCurveCatalogTest`, `CloudTrainingScreenTest` e o
cenário SDK `SDK freezes curve snapshot restores zero gain and retries without
changing progress`. Este último inicia, salva, restaura com outro seletor, conclui
um AC com zero e sincroniza novamente: saldo 800, versão 1, snapshot original e
explicação persistida. Não usar essas fixtures como resultados de treinamento real.
Durante o ativo, não mostrar ratings, tags não reveladas, categoria ou pontuação.
No resultado, explicar faixa, ganho base, ajuste temporal e desconto de dica.

TESTE MANUAL OPCIONAL - NÃO EXECUTADO (D-014).

## Demonstração cloud 40–43

Pré-requisitos e estado remoto: [FIREBASE_OPERATION.md](FIREBASE_OPERATION.md).
Na conta configurada, abrir Configurações e selecionar handle; voltar ao Início,
iniciar com progresso por etapa, consultar o problema e confirmar dica/motivo.
Recriar a tela durante o treino recupera o mesmo ativo e seu prazo. Encerrar leva
ao resultado persistido; voltar ao Início e abrir o último resultado. Durante
um ativo, voltar ao Início permite consultar histórico/estatísticas de tentativas
encerradas sem exibir o problema ativo nessas listas. Filtrar pendências/AC posterior,
abrir resultado/Codeforces e conferir gráfico/frações. Em Configurações, mudar tema
e consultar separadamente o arquivo local; trocar handle continua bloqueado no ativo.
Pendências são verificadas automaticamente em lotes; para demonstração controlada,
“Atualizar agora” antecipa uma execução. Offline/fechado aguardando verificação
preserva o ativo e nunca concede outras duas horas. Não alegar OAuth/deploy real a
partir de fixtures dos emuladores. TESTE MANUAL OPCIONAL - NÃO EXECUTADO (D-014).
