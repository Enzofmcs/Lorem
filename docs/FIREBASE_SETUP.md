# Configurar e verificar Firebase (Historias 33-34)

## Estado real da entrega

Na verificacao inicial de 19/10/2026 ainda nao havia projeto Firebase.
Depois, o usuario criou o projeto, escolheu **nam5 (US)** para o Firestore e
forneceu o JSON Android. A configuracao foi instalada localmente e validada:
package `dev.lorem.app`, appId/projeto, cliente OAuth Web e SHA-1 de debug
correspondente ao certificado deste ambiente. ID, JSON e alias `test` ficam em
arquivos ignorados pelo Git. Functions continuam em **us-central1**.
Isso nao comprova o estado remoto do banco, exclusividade do provedor ou deploy.
Nao ha credencial administrativa no repositorio ou no ambiente desta entrega.
As Historias 33/34 permanecem **parciais** ate verificar Google/Firebase e publicar
o backend. A CLI nao possui conta administrativa autenticada neste ambiente:
`firebase projects:list --json --non-interactive` retornou erro de autenticacao.
A evidencia com emuladores nao comprova OAuth Google real.

Implementado: Rules fechadas por uid/provedor; indices versionados; Function
`bootstrapAccount`; testes HTTP em Auth/Firestore/Functions Emulator; Credential
Manager + Google ID + Firebase Auth Android; restauracao/validacao da sessao,
bootstrap idempotente, erros, cancelamento e saida/troca de conta.

O destino autenticado `Seus handles` e apenas o ponto de integracao da Historia 35.
Nao cadastra handles nem abre o perfil/treino legado. Rating 800, multiplos handles,
Ipsums na nuvem, timeout 2h e migracao continuam nas Historias 35-38.

Verificacao com o JSON recebido: 17 testes direcionados sem falhas/ignorados,
`lintDebug` sem erros (58 avisos de versoes/icone) e `assembleDebug` com login
habilitado. O certificado do APK corresponde ao cliente Android do JSON.
TypeScript compilado; nenhum deploy ou OAuth real executado.
[Evidencia desta configuracao](evidence/2026-10-19-firebase-android-config.json),
separada da evidencia inicial sem projeto.

## Executar verificacoes locais sem projeto real

Requisitos do backend: **Node 22**, npm e **Java 21** para a versao fixada do
Firestore Emulator. Android continua com **JDK 17**, SDK 35 e Gradle do projeto.
As versoes exatas das bibliotecas/CLI estao no package-lock.json.

Na raiz do repositorio:

```bash
npm --prefix firebase/functions ci
npm --prefix firebase/functions test
```

O comando compila TypeScript e executa os emuladores em `demo-lorem-local`.
Auth/Firestore/Functions escutam somente em `127.0.0.1` (9099/8080/5001).
Nao requer login administrativo. A suite usa identidades sinteticas aceitas
somente pelo Auth Emulator, sem colocar botao ou provedor falso no aplicativo.

Em ambientes com home restrito, configurar caches gravaveis usando
`XDG_CACHE_HOME`, `XDG_CONFIG_HOME`, `FIREBASE_EMULATORS_PATH` e o parametro
`npm --cache=<diretorio>`, preservando proxy e certificados do ambiente.
Sem identidade GCP configurada, `METADATA_SERVER_DETECTION=none` evita procurar
credenciais em metadata servers durante testes estritamente locais.
Nao definir variaveis de emulador em processos que fazem deploy/producao.

Verificacoes Android direcionadas e marco:

```bash
./gradlew :app:testDebugUnitTest --tests 'dev.lorem.app.data.auth.GoogleSessionRepositoryTest' --tests 'dev.lorem.app.ui.auth.AuthenticationViewModelTest' --tests 'dev.lorem.app.ui.AuthenticationScreenTest' --tests 'dev.lorem.app.data.sync.SynchronizationWorkTest'
./gradlew test lint assembleDebug
./gradlew :app:testDebugUnitTest -Plorem.firebaseAuthEnabled=true --tests 'dev.lorem.app.FirebaseEntryTest'
```

O ultimo teste verifica que um perfil legado salvo nao aparece no fluxo Firebase.
Com JSON, verifica a entrada real com recursos gerados pelo Google Services e
botao Google habilitado; sem JSON, exige o bloqueio por configuracao ausente.
Nao abre o seletor Google nem autentica uma conta real.

## Criar e registrar o projeto real

1. Usar o projeto Firebase ja criado para a validacao inicial. Separar producao
   em outro projeto; nao usar dados reais nos emuladores. Registrar IDs escolhidos e
   ambientes em `firebase-projects.local.json` (ignorado pelo Git), com nome,
   package, regiao, fingerprints e estado das etapas abaixo.
2. Adicionar app Android com package **`dev.lorem.app`**. Obter fingerprints com
   `./gradlew :app:signingReport`: cadastrar SHA-1 e SHA-256 de debug e depois
   das assinaturas de release/Play App Signing efetivamente utilizadas.
   Nenhuma assinatura de release foi inventada nesta entrega.
3. Em Authentication, habilitar **somente Google**, configurar e-mail de suporte
   e consentimento OAuth. Manter senha, anonimo e demais provedores desabilitados.
   Se o consentimento estiver em teste, cadastrar as contas de teste necessarias.
4. Criar Firestore em modo producao/Rules fechadas na localizacao escolhida pelo
   usuario: **`nam5` (US), multirregiao**. Manter **`us-central1`** para Functions
   (e a futura fila Tasks). Essa combinacao e compativel: o SDK/Admin acessa o
   banco pelo projeto/database, e o cliente Android chama a regiao da Function.
   Escolher nam5 para o banco nao exige alterar a constante de Functions nem
   recriar o projeto. A criacao/configuracao remota ainda sera verificada.
5. Ativar billing **Blaze** quando necessario para Functions/Cloud Build/Artifact
   Registry. Conferir precos, quotas e orcamentos/alertas no projeto escolhido.
   O limite de cinco instancias de bootstrap nao e um teto de custo. Cloud Tasks
   pertence a Historia 37; nao existe fila/tarefa provisionada neste bloco.
6. Baixar novamente `google-services.json` apos habilitar Google e cadastrar as
   assinaturas; salvar em **`app/google-services.json`**. O plugin gera o recurso
   `default_web_client_id` do cliente OAuth **Web**, usado pelo Credential Manager.
   Nao usar o client ID Android como serverClientId. O arquivo local e ignorado
   pelo Git; nao adicionar chaves privadas ou service-account.json ao projeto.
7. Configurar `.firebaserc` e registrar status/fingerprints em arquivo local. Nesta
   entrega foi criado somente o alias `test` com o ID recebido. Nao criar um alias
   de producao com o mesmo projeto por conveniencia. `.firebaserc.example` continua
   sendo um exemplo, com placeholders a substituir em cada ambiente.

Validar estrutura do arquivo Android:

```bash
npm --prefix firebase/functions run check-config
```

Essa verificacao confirma package/appId/projeto/cliente OAuth Web presentes;
nao confirma credenciais, configuracao remota, provedor, assinaturas ou deploy.
Sem o arquivo, retorna erro claro e codigo 1. O build Android continua possivel.
O SHA-1 do cliente Android no JSON recebido tambem foi comparado ao certificado
do APK; isso nao substitui a consulta da configuracao vigente no Console.

## Deploy e habilitacao para desenvolvimento

Usar a CLI instalada pelo lockfile, autenticar a conta administrativa autorizada
no proprio ambiente do responsavel e conferir o projeto **de teste** antes de
executar. Em `firebase/functions`:

```bash
npx firebase login
npx firebase deploy --project test --only firestore:rules,firestore:indexes,functions:accounts --config ../../firebase.json
```

`test` deve resolver para o projeto de validacao registrado no `.firebaserc` local;
em outro checkout, configurar esse alias ou usar o ID explicito. Em Cloud Shell
ou outra maquina remota, usar `npx firebase login --no-localhost` quando necessario.
O deploy nao configura o provedor Google no Auth; a etapa do Console continua necessaria.
As Functions usam a identidade de runtime fornecida pelo Google, sem chave privada
embarcada. O cliente chama bootstrapAccount sem campos; o uid vem do token Google
validado. A transacao cria apenas schemaVersion/createdAt, preservando metadados
existentes e sem copiar e-mail/nome para Firestore.

Para abrir o novo login no Android:

```bash
./gradlew assembleDebug -Plorem.firebaseAuthEnabled=true
```

Por padrao, a flag e **false**, seguindo o rollout integrado posterior a 38.
Com true e sem configuracao, aparece indisponibilidade e o botao fica desabilitado;
nao existe fallback para uma conta falsa. Com configuracao, o login usa Google real.
Entrar so libera o destino autenticado depois de atualizar/validar o token Google
e confirmar bootstrapAccount. Falha de rede nao e tratada como sessao confirmada.

O fluxo Firebase nao importa/exibe o perfil legado. Workers legados sao cancelados
e ficam inertes pelo guard da fabrica; o novo fluxo ainda nao agenda workers por
uid. Controles, destino e respostas pendentes perdem o contexto ao sair/trocar
conta. O SDK Firebase persiste a identidade; o app revalida ao restaurar/retomar.
Sair do Auth nao apaga a conta/dados; nao existe ainda Ipsum em nuvem neste bloco.

## Aceite real e recuperacao

Apos provisionar, registrar evidencia da configuracao (IDs, regiao e fingerprints,
sem tokens), provedor unico, Rules/deploy, entrada Google, sessao restaurada,
saida/troca de conta e ausencia de dados legados. Se o login falhar, verificar
package, assinatura instalada, OAuth Web, provedor, rede, regiao e deploy da Function.
`NOT_FOUND`/contrato de bootstrap inesperado deixam o fluxo indisponivel; nao
ignorar a verificacao para liberar uma sessao.

Para recuperar um deploy de teste, selecionar o commit verificado anterior e
republicar Rules/Functions no **mesmo projeto de teste**, sem apagar documentos
ou recalcular progresso. Para retirar o preview Android, gerar o build padrao
sem a flag: os dados legados nao foram migrados/apagados. Producao e migracao
permanecem fora desta entrega. Conferencia humana e opcional (D-014); ausencia
de projeto/configuracao real e uma dependencia externa, nao teste manual pendente.
