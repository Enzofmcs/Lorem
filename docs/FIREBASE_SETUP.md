# Configurar Firebase Spark (Historias 33-38)

## Entrega e limites

Por solicitacao do usuario, a D-039 substitui a arquitetura Functions/Tasks por
**Spark: Authentication Google + Firestore**. Nao requer Blaze, cartao, billing,
Cloud Functions, Cloud Tasks ou servico externo. Firestore escolhido: **nam5 (US)**.

O JSON recebido foi instalado em app/google-services.json (ignorado pelo Git).
Package dev.lorem.app, projeto/appId, OAuth Web e SHA-1 do certificado debug foram
conferidos. ID, alias test e progresso da configuracao ficam apenas nos arquivos
locais .firebaserc e firebase-projects.local.json. Nao foi criado alias de producao.
Nenhuma chave privada/credencial administrativa e necessaria no app.

O login usa Credential Manager/Google ID/Firebase Auth; revalida token Google e
executa uma transacao SDK Firestore em users/{uid}. Cria somente schemaVersion=1
e createdAt=serverTimestamp se ausente, nunca sobrescreve metadados existentes.
As Rules validam uid, provedor, esquema e request.time. Cache/fila offline nao
confirmam sessao. Sem Rules publicadas, a falha de permissao aparece como erro de
configuracao recuperavel; nao e confundida com login por provedor errado.

O usuario **confirmou a publicacao das Rules pelo Console** depois de receber o
arquivo da versao Spark. O PR 66 ja foi integrado em main. A publicacao fica
registrada como informacao do usuario, sem leitura administrativa pelo agente;
a CLI deste ambiente continua sem conta administrativa conectada. A confirmacao
cobria a base 33/34. Depois da PR 69, o usuario confirmou a publicacao das Rules
combinadas 35-37; [registro separado](evidence/2026-10-19-firebase-rules-35-37-publication.json).
Sem leitura administrativa do ruleset pelo agente.
Nenhum indice adicional e necessario (indexes e fieldOverrides vazios).

33/34 tem Google unico confirmado pelo usuario; o registro nao e leitura administrativa.
OAuth/login real nao foram executados pelo agente. Conferencia humana do app e
opcional (D-014); emulador nao comprova OAuth. [Registro da publicacao](evidence/2026-10-19-firebase-rules-publication.json).

O preview agora implementa 35-37: handles, treino/rating Firestore e prazo 2h.
A publicacao das Rules combinadas foi confirmada pelo usuario apos a PR 69.
O arquivo firebase/firestore.rules valida handles, selecao, markers, tentativas,
clock e lock. O procedimento abaixo fica como referencia para futuras alteracoes,
sem exigir repetir a publicacao da mesma versao. A 38 acrescenta permissoes que precisam ser publicadas; nenhum novo indice e necessario.
Google unico foi confirmado pelo usuario (D-042).
A 38 implementa arquivo local/migracao e torna Firebase padrao; publicar tambem suas permissoes de exclusao.

No checkout de verificacao 35-37, o google-services.json ignorado nao estava
presente. O APK preview foi verificado com o bloqueio explicito de configuracao
ausente; SDK/Rules usaram exclusivamente o projeto demo dos emuladores. Restaurar
o JSON Android local antes de usar o Firebase real. Isso nao invalida a conferencia
historica do JSON na 33/34 nem comprova OAuth neste checkout.

## Evidencia local Spark

Dez testes Auth/Firestore/Rules e 23 direcionados Android (incluindo bootstrap
Kotlin real em emuladores) passaram. Marco Android: 253 debug executados, tres
opt-in ignorados no padrao e comprovados separadamente, 188 release, zero falhas;
lint sem erros (58 avisos) e APK. Configuracao local validada, preview com flag
true e runtime sem Firebase Functions.
[Evidencia verificavel](evidence/2026-10-19-firebase-spark-33-34.json).

## Configurar o projeto sem billing

1. Manter o projeto no **Spark** e concluir a criacao do Firestore padrao em modo
   producao, localizacao nam5 (US). Nao recriar o banco para mudar regiao.
2. Em Authentication, habilitar **somente Google**, configurar e-mail de suporte
   e consentimento OAuth; cadastrar testadores quando o consentimento exigir.
   Senha, anonimo e demais provedores ficam desabilitados no projeto real.
3. Registrar Android package **dev.lorem.app**. Usar ./gradlew :app:signingReport
   para cadastrar SHA-1/SHA-256 das assinaturas realmente utilizadas. O debug deste
   ambiente foi conferido; release/Play so quando essas assinaturas existirem.
4. Baixar google-services.json atualizado com Google/OAuth Web e salvar em
   app/google-services.json. O cliente OAuth **Web** gera default_web_client_id;
   nao usar o client ID Android como serverClientId.
5. Criar .firebaserc com alias test apontando ao projeto de validacao. Usar
   .firebaserc.example como modelo, sem copiar o projeto test para production.
   Registrar IDs, package, plano, localizacao, fingerprints e status em
   firebase-projects.local.json. Todos esses arquivos locais sao ignorados.

Na raiz do repositorio:

```bash
npm --prefix firebase ci
npm --prefix firebase run check-config
```

O validador confere estrutura/package/appId/projeto/OAuth Web, sem imprimir chave
ou client ID. Ausencia/malformacao retorna erro claro e codigo 1. Nao consulta
provedores, billing ou Rules remotas e nao comprova login Google.

## Publicar somente Rules e indices no Spark

Requisito local do tooling: **Node 24** (SDK de testes exige >=24.12). Nao existe
runtime Node em producao. Na pasta firebase/:

```bash
npx firebase login
npx firebase deploy --project test --only firestore:rules,firestore:indexes --config ../firebase.json
```

Em Cloud Shell/maquina remota usar login --no-localhost se necessario. O alias test
deve existir no .firebaserc local, ou substituir pelo ID explicito do projeto.
Esse deploy nao habilita Google nem billing e nao publica Functions. Nao enviar
tokens, senhas ou chave de conta de servico. A conta humana autorizada publica as
Rules; cada usuario Android continua escrevendo com seu proprio token, sem Admin SDK.

## Executar testes locais

Tooling: Node 24/npm e **Java 21** para Firestore Emulator. Android: SDK 35,
Gradle 8.11.1/JDK 17. Lockfile em firebase/package-lock.json; dependencias sao
somente ferramentas de desenvolvimento. Nao ha fonte TypeScript/Function.

```bash
npm --prefix firebase ci
npm --prefix firebase test
```

Executa Auth/Firestore Emulator em demo-lorem-local, loopback 127.0.0.1:9099/8080,
sem conta administrativa ou dados reais. Tokens Google sinteticos sao aceitos
somente pelo Auth Emulator. A suite usa esses tokens com o SDK Firestore e as
Rules reais: concorrencia, restauracao, timestamps, uid/provedor e payload forjado.

Para exercitar o bootstrap Kotlin de producao contra os emuladores, inicia-los em
um terminal (Java 21):

```bash
cd firebase
npx firebase emulators:start --project demo-lorem-local --only auth,firestore --config ../firebase.json
```

Em outro terminal, na raiz com JDK 17/SDK Android:

```bash
LOREM_FIREBASE_EMULATORS=true FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 ./gradlew :app:testDebugUnitTest --tests 'dev.lorem.app.data.auth.FirestoreAccountBootstrapEmulatorTest'
```

A suite Kotlin usa um FirebaseApp nomeado exclusivo e projeto demo, nunca o JSON
real. Sem opt-in, esses dois testes sao ignorados no build comum. Em ambiente com
home restrito, usar caches gravaveis via XDG_CACHE_HOME/XDG_CONFIG_HOME/
FIREBASE_EMULATORS_PATH e npm --cache, preservando proxy/CA. Sem identidade GCP,
METADATA_SERVER_DETECTION=none evita buscar credenciais em metadata servers locais.

Testes direcionados Android de sessao/falhas/UI/entrada:

```bash
./gradlew :app:testDebugUnitTest --tests 'dev.lorem.app.data.auth.GoogleSessionRepositoryTest' --tests 'dev.lorem.app.data.auth.FirebaseFailureTest' --tests 'dev.lorem.app.ui.auth.AuthenticationViewModelTest' --tests 'dev.lorem.app.ui.AuthenticationScreenTest' --tests 'dev.lorem.app.ui.auth.GoogleCredentialRequestTest' --tests 'dev.lorem.app.FirebaseEntryTest' -Plorem.firebaseAuthEnabled=true
```

O smoke verifica MainActivity com/sem Google Services: botao habilitado quando
configurado, bloqueio explicito quando ausente, sem exibir perfil legado. Nao
abre seletor nem autentica uma conta Google real. No marco: test/lint/assembleDebug.

## Preview e recuperacao

```bash
./gradlew assembleDebug -Plorem.firebaseAuthEnabled=true
```

Firebase true por padrao a partir da 38. Sem configuracao nao existe conta falsa;
com true, somente token validado e transacao confirmada liberam os handles e treino.
Rede, cota Spark ou Rules incorretas deixam a sessao bloqueada com retry. Sair/
trocar limpa a UI e callbacks antigos; workers legados ficam cancelados/inertes.
Nenhum perfil local e importado pelo preview, e sair nao exclui dados da conta.

Cota gratuita esgotada nao habilita cobranças automaticamente. Tratar erro e
aguardar disponibilidade; nao recomendar Blaze como pre-requisito desta entrega.
[Planos oficiais](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans).

Para recuperar Rules de teste, republicar o commit Spark verificado anterior no
mesmo projeto, sem apagar documentos. Nao usar as Rules anteriores que negavam
create de conta nem voltar ao APK que dependia de Functions. A partir da 38, nao usar uma versao antiga para voltar a escrever no arquivo
congelado. Atualizar sem limpar dados, manter o arquivo somente leitura e usar
as Rules atuais com exclusions. O override false e somente para desenvolvimento
antes da migracao, sem desbloquear escrita no arquivo ja congelado. Evidencias anteriores (Functions/configuracao inicial) sao
historicas, nao validacao da versao Spark.

## Compatibilidade da integracao PR 69

A versao combinada preserva o esquema da historia 35 na main: handles `cf_`,
`loremRating`, `bindingVersion`, `progressVersion` e selecao Firestore versionada.
Room 13 e namespaces de ProfileLink/workers permanecem intactos. O lock
`runtime/current` existe apenas durante um Ipsum e e excluido junto com o resultado;
`runtime/clock` confirma tempo sem bloquear selecao. Publicar o arquivo Rules
combinado inteiro; nao usar as Rules da branch anterior como versao atual.
[Evidencia integrada](evidence/2026-10-19-firebase-merge-69.json); evidencias anteriores preservadas.

## Proximo passo apos a publicacao confirmada das Rules

1. Google unico ja foi confirmado pelo usuario; conferir novamente somente se alterar provedores.
2. Garantir o google-services.json Android atualizado em app/google-services.json;
   ele continua ausente neste checkout e nao deve ser versionado.
3. Atualizar o projeto com a main que contem a PR 69 e compilar, na raiz:
   ./gradlew assembleDebug -Plorem.firebaseAuthEnabled=true.
4. Abrir o APK preview; login Google, cadastro de handle e inicio/recuperacao de
   Ipsum sao uma conferencia manual opcional, nao um gate da proxima historia.

A 38 implementa migracao e habilita o fluxo por padrao no aplicativo. As Rules
35-37 publicadas anteriormente precisam receber as novas permissoes de exclusao. Nenhuma execucao OAuth
real foi comprovada pelo registro de publicacao.

## Publicacao da versao 38

O arquivo firebase/firestore.rules inclui a colecao exclusions, com IDs publicos
de problemas antigos e regras de imutabilidade/isolamento. Publicar o arquivo
inteiro desta entrega antes de usar o APK 38 no projeto real. A confirmacao de
publicacao das Rules 35-37 permanece historica, sem reapresenta-la como deploy 38.
Nao ha Functions, novo indice ou billing. Nao apagar documentos para recuperar.

Com google-services.json local, ./gradlew assembleDebug ja usa Firebase por padrao.
Sem o arquivo, o APK informa configuracao ausente. O arquivo local antigo aparece
separado da conta, e um ativo nao reconciliado bloqueia somente novos treinos.
Desinstalar/limpar dados remove o arquivo local; para preserva-lo, atualizar o APK
sem limpar armazenamento. Exports cloud sao opcionais, locais e nao usam UID/e-mail/handle.
