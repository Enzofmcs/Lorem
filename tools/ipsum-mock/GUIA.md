# Lorem Mock · Importar 200 Ipsums de um Excel

O **Lorem Mock** é um APK para Android 8 ou superior, com pacote `dev.lorem.app.mock`. A variante **mock** usa o **projeto Firebase** indicado em `app/src/mock/google-services.json`. Quando falta configuração, o APK informa na tela qual etapa falta. Os registros gravados aparecem em **Histórico** e **Estatísticas**.

## Abrir no Android Studio e configurar o Firebase

1. Faça checkout da branch `codex/importador-ipsums` e sincronize o Gradle. Em **Build Variants**, selecione **mock** para o módulo `app`.
2. No Firebase Console, abra o projeto que vai receber os lotes e confira se o Firestore padrão e o provedor **Google** em **Authentication** estão ativos. Cadastre um app Android com o pacote `dev.lorem.app.mock`. Registre a SHA-1 e a SHA-256 da variante **mock**, exibidas por `./gradlew :app:signingReport`.
3. Baixe o `google-services.json` atualizado e coloque em `app/src/mock/google-services.json`. O Git ignora esse arquivo. Em **Firestore Database → Rules**, use o arquivo completo [`firebase/firestore.rules`](../../firebase/firestore.rules) em um projeto que já usa as regras do Lorem. Se houver ajustes feitos diretamente no Console, incorpore-os antes de publicar. Em um projeto dedicado ao importador, use [`firestore.mock-cloud.rules`](firestore.mock-cloud.rules).
4. Execute **Run** ou rode `./gradlew :app:assembleMock` no terminal. O APK é gerado em `app/build/outputs/apk/mock/app-mock.apk`. Abra o Lorem Mock, confira o ID do projeto no app, entre com Google e importe a planilha.

Os lotes gravados nesse modo ficam em `loremMockImports/{uid}/batches/{ID do lote}/handles/cf_mock_excel/ipsums`. O acesso exige a conta Google proprietária do lote. O modo Firebase usa internet.

## Mostrar os dados nas telas normais do Lorem

1. No **Lorem**, entre com a conta Google de teste e selecione um handle cadastrado sem histórico. Encerre qualquer Ipsum ativo dessa conta.
2. No Windows com **Node.js 24**, abra [`Carregar-Ipsums.cmd`](Carregar-Ipsums.cmd) com dois cliques. O atalho usa o projeto dos arquivos `google-services.json`. No primeiro uso, faça login no navegador com a conta que administra esse projeto.
3. Informe o e-mail da **conta de teste** que gravou o lote. Aguarde **Carga concluída** e abra **Histórico** ou **Estatísticas** no Lorem normal.

O atalho usa o handle selecionado e pede o ID do lote se a conta tiver mais de um. Pelo terminal, rode `npm --prefix firebase ci` e use `npm --prefix firebase run seed-normal-history` na raiz. Esse comando aceita `--email`, `--uid`, `--batch`, `--handle` e `--reauth` depois de `--`.

### Carga pelo Cloud Shell

No **Firebase Console**, copie o **ID do projeto** e o **UID** da conta de teste em **Authentication → Usuários**. Abra o **Cloud Shell** do Google Cloud com uma conta que pode ler e gravar no Firestore do projeto. Rode:

   ```bash
   curl -fsSLo seed-normal-history.mjs https://raw.githubusercontent.com/Enzofmcs/Lorem/refs/heads/codex/importador-ipsums/tools/ipsum-mock/seed-normal-history.mjs
   node seed-normal-history.mjs --project ID_DO_PROJETO --uid UID_DE_TESTE --apply
   ```

O comando usa o handle selecionado no Lorem e localiza o lote já gravado. Com vários lotes, passe `--batch ID_DO_LOTE`. Para indicar outro handle cadastrado, use `--handle NOME`. Para conferir o projeto, o handle e o rating antes de gravar, execute o comando sem `--apply`.

A carga copia os 200 Ipsums e seus marcadores para `users/{uid}/handles/{handleKey}` em uma única transação e atualiza o progresso do handle. Depois de receber **Carga concluída**, volte ao **Lorem**, abra **Histórico** ou **Estatísticas** e confira os resultados. Para repetir a demonstração com o mesmo lote, o comando reconhece a carga existente. As Rules publicadas do Lorem permitem a leitura normal desse histórico.

## 1. Prepare o Excel

Abra `ipsums-200-exemplo.xlsx`. A aba **Ipsums** já contém 200 registros simulados. Para usar os seus dados, mantenha os cabeçalhos na primeira linha e substitua os valores das 200 linhas de dados. Salve como **Pasta de Trabalho do Excel (.xlsx)**. Se os valores vierem de fórmulas, copie e use **Colar especial → Valores**.

| Coluna | O que preencher |
| --- | --- |
| `contest_id`, `indice` | ID inteiro positivo e índice como `A` ou `B2`. Cada par aparece uma vez no lote. |
| `nome` | Título do problema. |
| `rating_problema` | Rating de 800 a 3500, em múltiplos de 100. |
| `tags` | Tags oficiais do Codeforces separadas por `\|`, como `math\|greedy`. |
| `inicio_utc` | Data e hora em UTC. Use o tipo data do Excel ou texto ISO como `2025-01-01T18:00:00Z`. |
| `duracao_min` | Tempo da tentativa, maior que zero e de até 120 minutos. Os períodos das tentativas precisam ser distintos e sem sobreposição. |
| `resultado` | `AC` para resolução no Ipsum, `PENDENTE` para tentativa encerrada sem AC e `UPSOLVE` para resolução posterior. |
| `erros` | Número inteiro de erros, incluindo zero. |
| `dica_min` | Opcional. Minutos desde o início até a dica, antes do encerramento. |
| `upsolve_utc` | Obrigatório no resultado `UPSOLVE`. Registre a data e a hora em UTC depois do fim da tentativa. Deixe vazio nos demais casos. |

O Rating Lorem parte de 800 e é calculado em ordem cronológica com a regra D-012 do aplicativo. O upsolve mantém a avaliação original da tentativa. A planilha preenche o histórico e a evolução de Rating Lorem. Ela contém IDs, nomes e resultados simulados.

## 2. Inicie o Firestore Emulator para usar o modo local

Para usar o modo local, compile na raiz com `./gradlew -Plorem.mockUseEmulator=true :app:assembleMock`. No Windows, use `gradlew.bat`. Instale **Node.js 24**, **Java 21** e **Android SDK Platform-Tools**. Abra um terminal em `tools/ipsum-mock` e rode:

```bash
npx --yes firebase-tools@15.34.0 emulators:start --only firestore --project demo-lorem-ipsums --config firebase.json --export-on-exit=./dados-locais
```

Mantenha esse terminal aberto durante a importação. O Firestore usa a porta **8082**. A interface de conferência abre no computador em `http://127.0.0.1:4002`. O projeto começa com `demo-` e fica no computador.

## 3. Instale o APK e copie a planilha

Inicie um dispositivo virtual no Android Studio ou conecte um celular Android por USB, ative **Depuração USB** e aceite a autorização do computador. Em outro terminal, na pasta `tools/ipsum-mock`, rode:

```bash
adb devices
adb install -r ../../app/build/outputs/apk/mock/app-mock.apk
adb reverse tcp:8082 tcp:8082
adb push ipsums-200-exemplo.xlsx /sdcard/Download/
```

Se houver mais de um dispositivo, use `adb -s SERIAL` em cada comando. Para importar seus dados, troque `ipsums-200-exemplo.xlsx` pelo nome da sua planilha. A configuração `adb reverse` é necessária no modo local.

## 4. Importe e confira

1. Abra **Lorem Mock**, toque em **Selecionar Excel (.xlsx)** e escolha o arquivo em **Downloads**.
2. Confira a prévia. A planilha de exemplo mostra **160 ACs, 20 pendências e 20 upsolves**.
3. Toque em **Gravar 200 Ipsums**. Aguarde **200 Ipsums gravados e conferidos no Firestore**.
4. Abra **Histórico** para ver os registros e **Estatísticas** para conferir o total, os resultados e a evolução de Rating Lorem.
5. No modo local, abra a interface do emulador, escolha **Firestore** e navegue até `users/{ID do lote}/handles/cf_mock_excel/ipsums`. No modo Firebase, confira a coleção `loremMockImports` no Console. O ID do lote pode ser copiado da aba **Importar**.

A mesma planilha gera o mesmo ID de lote mesmo se o Excel salvar o arquivo com outra formatação. Reimporte para concluir uma operação interrompida ou recuperar um lote existente. Se mudar os valores das tentativas, o APK grava um novo lote com outro ID.

Para guardar os dados no computador, pare o emulador com **Ctrl+C** e aguarde a exportação. Na próxima execução, acrescente `--import=./dados-locais` ao comando da etapa 2.

## Compilar pela linha de comando

Use SDK Android 35 e JDK 17 para o Gradle.

```bash
git fetch origin
git switch --track origin/codex/importador-ipsums
./gradlew :app:assembleMock
```

Use `gradlew.bat` no Windows.
