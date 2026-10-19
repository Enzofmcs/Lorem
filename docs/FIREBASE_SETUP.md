# Configurar Firebase Spark (Historias 33-37)

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
cobre a base 33/34; o contrato combinado 35-37 requer publicar as Rules atuais.
Nenhum indice adicional e necessario (indexes e fieldOverrides vazios).

33/34 aguardam somente a confirmacao da configuracao Google unica no projeto.
OAuth/login real nao foram executados pelo agente. Conferencia humana do app e
opcional (D-014); emulador nao comprova OAuth. [Registro da publicacao](evidence/2026-10-19-firebase-rules-publication.json).

O preview agora implementa 35-37: handles, treino/rating Firestore e prazo 2h.
**A publicacao confirmada anteriormente cobre somente as Rules 33/34.** O arquivo
firebase/firestore.rules mudou para validar handles, markers de problemas usados,
tentativas e lock. Publicar essa nova versao pelo procedimento abaixo antes de
usar os novos fluxos no projeto real. Nenhum novo indice e necessario. Nao houve
deploy remoto nesta implementacao. A confirmacao de Google unico permanece pendente.
Migracao do arquivo local e ativacao padrao continuam na 38.

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

Flag false por padrao ate rollout 38. Sem configuracao nao existe conta falsa;
com true, somente token validado e transacao confirmada liberam os handles e treino.
Rede, cota Spark ou Rules incorretas deixam a sessao bloqueada com retry. Sair/
trocar limpa a UI e callbacks antigos; workers legados ficam cancelados/inertes.
Nenhum perfil local e importado pelo preview, e sair nao exclui dados da conta.

Cota gratuita esgotada nao habilita cobranças automaticamente. Tratar erro e
aguardar disponibilidade; nao recomendar Blaze como pre-requisito desta entrega.
[Planos oficiais](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans).

Para recuperar Rules de teste, republicar o commit Spark verificado anterior no
mesmo projeto, sem apagar documentos. Nao usar as Rules anteriores que negavam
create de conta nem voltar ao APK que dependia de Functions. Para retirar preview,
gerar APK sem a flag; historico legado nao foi migrado/apagado. Produção/migracao
continuam na 38. Evidencias anteriores (Functions/configuracao inicial) sao
historicas, nao validacao da versao Spark.

## Compatibilidade da integracao PR 69

A versao combinada preserva o esquema da historia 35 na main: handles `cf_`,
`loremRating`, `bindingVersion`, `progressVersion` e selecao Firestore versionada.
Room 13 e namespaces de ProfileLink/workers permanecem intactos. O lock
`runtime/current` existe apenas durante um Ipsum e e excluido junto com o resultado;
`runtime/clock` confirma tempo sem bloquear selecao. Publicar o arquivo Rules
combinado inteiro; nao usar as Rules da branch anterior como versao atual.
[Evidencia integrada](evidence/2026-10-19-firebase-merge-69.json); evidencias anteriores preservadas.
