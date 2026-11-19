# Verificação das histórias 58–60

## Testes

- `npm --prefix firebase test` executou 37 testes de Auth e Rules.
- `./gradlew test lint assembleDebug --max-workers=2 --console=plain` executou
  364 testes debug e 250 release sem falhas ou ignorados, com 21 testes do
  SDK Android contra os emuladores Auth/Firestore. O lint registrou 58 avisos e
  zero erros. O APK debug foi compilado.
- O teste de mudança de faixa parte de Lorem 800 e Dolor 845. Após um AC rápido,
  o Dolor cruza 850 e recomenda dificuldade 900. A faixa normalizada do Lorem
  continua em 800.

## Capturas

Capturas Android a 320 dp com dados de teste. A versão anterior é o commit
[`721c1e3`](https://github.com/Enzofmcs/Lorem/commit/721c1e368f5f72dd3d86c9ce80fa659dcc57826d).
O teste Compose/Robolectric captura os mesmos estados em tema claro e escuro,
com fonte em 100% e 200%.

| Tela | Claro | Escuro | Claro, fonte 200% | Escuro, fonte 200% |
| --- | --- | --- | --- | --- |
| Início sem tentativa | [Antes](antes/inicio-light.png) · [Depois](depois/inicio-light.png) | [Antes](antes/inicio-dark.png) · [Depois](depois/inicio-dark.png) | [Antes](antes/inicio-light-font200.png) · [Depois](depois/inicio-light-font200.png) | [Antes](antes/inicio-dark-font200.png) · [Depois](depois/inicio-dark-font200.png) |
| Retomada | [Antes](antes/retomada-light.png) · [Depois](depois/retomada-light.png) | [Antes](antes/retomada-dark.png) · [Depois](depois/retomada-dark.png) | [Antes](antes/retomada-light-font200.png) · [Depois](depois/retomada-light-font200.png) | [Antes](antes/retomada-dark-font200.png) · [Depois](depois/retomada-dark-font200.png) |
| Ipsum ativo | [Antes](antes/ativo-light.png) · [Depois](depois/ativo-light.png) | [Antes](antes/ativo-dark.png) · [Depois](depois/ativo-dark.png) | [Antes](antes/ativo-light-font200.png) · [Depois](depois/ativo-light-font200.png) | [Antes](antes/ativo-dark-font200.png) · [Depois](depois/ativo-dark-font200.png) |
| Resultado | [Antes](antes/resultado-light.png) · [Depois](depois/resultado-light.png) | [Antes](antes/resultado-dark.png) · [Depois](depois/resultado-dark.png) | [Antes](antes/resultado-light-font200.png) · [Depois](depois/resultado-light-font200.png) | [Antes](antes/resultado-dark-font200.png) · [Depois](depois/resultado-dark-font200.png) |
| Gráfico de evolução | | | [Claro](depois/grafico-light-font200.png) | [Escuro](depois/grafico-dark-font200.png) |

O gráfico capturado inclui um Ipsum anterior ao Dolor e um Ipsum com variações
Lorem de +24 e Dolor de −8. O ponto selecionado mostra o saldo de cada
avaliação gravada. O teste de navegação também verifica uma variação Dolor de
+0, uma variação fracionária e a troca de handle.

Os controles principais foram exercitados por meio da árvore de semântica do
Compose. TalkBack em dispositivo ficou pendente.
