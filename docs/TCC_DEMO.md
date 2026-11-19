# Ensaio do TCC no Lorem

## Preparar a conta

1. Na branch `codex/demo-tcc-lorem`, coloque o `google-services.json` do pacote `dev.lorem.app` em `app/google-services.json`. Gere o APK normal com a variante **debug** no Android Studio, usando a assinatura já autorizada para Google no Firebase. Pela linha de comando, use `./gradlew :app:assembleDebug` com JDK 17 e Android SDK 35. O APK fica em `app/build/outputs/apk/debug/app-debug.apk`.
2. Publique as [Rules desta branch](../firebase/firestore.rules) no Firestore do mesmo projeto Firebase. Se houver regras personalizadas no Console, incorpore a mudança de Watermelon às regras já usadas pelo projeto.
3. No celular, instale o APK, entre uma vez com a conta Google de demonstração e selecione o handle `lerolerolero123491432`. Use um perfil sem histórico de Ipsums. Copie o **UID** de **Authentication → Usuários** no Firebase Console e feche o app.
4. No checkout da branch, com Node.js 24 e uma conta administrativa autenticada no `gcloud`, rode a prévia. Confira o projeto, o UID e os dois saldos. Repita com `--apply` para gravar.

   ```bash
   node tools/ipsum-demo/prepare-account.mjs --project PROJETO --uid UID
   node tools/ipsum-demo/prepare-account.mjs --project PROJETO --uid UID --apply
   ```

   A carga usa a rodada 05 incluída nesta branch, detecta progresso preexistente antes de gravar e reconhece a mesma carga se ela já estiver no Ipsum 200. A conta e a seleção permanecem no Firestore. Ao reentrar com Google, o Lorem recupera o histórico direto do projeto.

## Percurso no aparelho

1. Reabra o **Lorem**, entre com a mesma conta Google e confirme o handle. Em **Estatísticas → Resumo → Evolução dos ratings**, selecione o ponto da direita, **Ipsum 200**. Confira **Lorem 1883**, **Dolor 939,59648** e a linha tracejada **Habilidade simulada = 1000**.
2. Volte a **Início** e toque **Iniciar Ipsum**. Confira **Watermelon** com o cronômetro ativo. Aguarde uma consulta para mostrar que o AC anterior ficou fora do treino.
3. Envie uma nova solução com o handle vinculado depois que o cronômetro estiver em curso. Volte ao Lorem para a consulta automática e veja o **Resultado do Ipsum** ao obter AC. O tempo e os erros deste treino determinam a avaliação. [Codeforces 4A — Watermelon](https://codeforces.com/contest/4/problem/A)
4. Volte a **Estatísticas**, selecione o último ponto ou use **Próximo Ipsum** a partir do ponto 200. Confira **Ipsum 201**, **4A · Watermelon**, os dois saldos com seus respectivos deltas e **Ipsums encerrados: 201**.

## Restaurar o Ipsum 200

Feche o app. No mesmo projeto e UID, rode a prévia e depois acrescente `--apply`. O comando restaura os dois saldos do ponto 200 e remove os registros do treino Watermelon deste ensaio, inclusive se ele ainda estiver ativo. Se houver outros treinos depois da carga, o comando interrompe a restauração. O próximo ensaio exigirá outro AC no Codeforces.

```bash
node tools/ipsum-demo/prepare-account.mjs --project PROJETO --uid UID --restore
node tools/ipsum-demo/prepare-account.mjs --project PROJETO --uid UID --restore --apply
```

## Amostra e conferência

A rodada 05 usa a semente `3913827388`, habilidade 1000 e ambos os ratings iniciais em 800. O arquivo `Lorem_Ipsums(3).xlsx` não estava acessível. A planilha disponível documenta que foi derivada dele. A aba **Ipsums** fornece 182 ACs, 18 pendências, as dificuldades e as durações. A conversão atribui IDs, nomes e horários simulados, preenche zero nos erros ausentes e arredonda as durações ao múltiplo de 3 ms mais próximo. A aba **Dados originais** preserva os ratings de referência. [Planilha da rodada 05](https://drive.google.com/file/d/1Rc1XJALC-j9w3HRasane-ObfxuxtVFX5/view)

| Rating | Saldo 200 na planilha | Saldo 200 no Lorem | Linhas com divergência |
| --- | ---: | ---: | ---: |
| Lorem | 1883 | 1883 | 0 de 200 |
| Dolor | 928,929396 | 939,596480 | 200 de 200 |

O replay usa a mesma finalização do app. Para Dolor, o app consulta as curvas KM isotônicas do modelo `virtual-duel-v1` e calcula em milionésimos. Já no primeiro treino, o delta é +11,636384 no app e +12,888255 na planilha. Depois de 200 treinos, o saldo do app é 10,667084 pontos maior. O próprio saldo calculado pelo app alimenta o próximo treino e a curva em Estatísticas.

Para reproduzir o cálculo com Python e `openpyxl`, rode os comandos na raiz do projeto. O SHA-256 da planilha usada foi `b301166038d3649a26603d9c222a3ce9ada45647d035b18976b5b66de2e5c453`.

```bash
python tools/ipsum-demo/extract-round.py /caminho/lorem-rodada-05-200-ipsums.xlsx /tmp/lorem-rodada-05.json
LOREM_DEMO_INPUT=/tmp/lorem-rodada-05.json ./gradlew :app:testDebugUnitTest --tests '*TccDemoHistoryTest'
```

O replay gera `baseline.json`, `comparison.csv` e `report.json` em `app/build/outputs/tcc-demo`. Use `--baseline caminho/baseline.json` para preparar a conta com outra extração compatível.

## Verificação

- Teste automatizado com SDK Android, Firebase Auth e Firestore emulados para login e reentrada, perfil selecionado, carga idempotente, bloqueio de progresso preexistente, início e encerramento, exclusão de AC e erros antigos, atualização única dos dois ratings e restauração.
- Teste Compose do app normal para a navegação pelo gráfico, botão **Iniciar Ipsum**, tela ativa, resultado e seleção dos pontos 200 e 201.
- A API pública confirmou dois erros e um AC prévios em Watermelon para o handle.
- Teste em aparelho físico pendente. O ensaio online requer o APK com a assinatura autorizada, as Rules publicadas, sua sessão Google e um novo AC do seu handle no Codeforces.
