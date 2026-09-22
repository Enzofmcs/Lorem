# Lorem

Lorem é um aplicativo Android de treino individual para programação competitiva. O produto deverá recomendar exercícios inéditos do Codeforces adequados ao nível do usuário e acompanhar sua evolução.

Um **Ipsum** é a tentativa oficial e cronometrada de resolver um problema recomendado. O fluxo completo de recomendação, submissões e avaliação será desenvolvido de forma incremental; ele ainda não faz parte desta estrutura inicial.

> O [`AGENTS.md`](AGENTS.md) é a fonte de verdade para produto, regras de domínio, decisões, escopo e progresso. Este README apresenta apenas a visão técnica de entrada.

## Estado atual

A História 00 fornece uma base Android de módulo único com:

- Kotlin, Jetpack Compose e Material 3;
- seis destinos provisórios navegáveis;
- escolha da tela inicial a partir de um pequeno perfil local;
- persistência desse dado de demonstração com DataStore;
- contratos de repositório e implementações falsas sem acesso à rede;
- injeção manual e estado de UI exposto por `ViewModel`/`StateFlow`.

Não há integração real com Codeforces, recomendação, cronômetro ou cálculo de rating nesta etapa.

## Escopo resumido

O Lorem terá perfil local associado a um handle público, catálogo e histórico do Codeforces, recomendação inédita, execução de um Ipsum, resultados, pendências, rating interno e estatísticas. Não fazem parte do escopo recursos sociais, backend próprio, Firebase, editor de código ou gamificação. Consulte o `AGENTS.md` para as regras completas.

## Arquitetura inicial

- **`domain`**: modelos Kotlin e contratos independentes da interface Android;
- **`data`**: persistência DataStore, fakes determinísticos e ponto de integração remota;
- **`ui`**: composição raiz, `ViewModel`, tema, navegação e telas Compose;
- **`AppContainer`**: composição manual das dependências.

Composables recebem estado e ações: eles não acessam DataStore ou serviços externos diretamente. Room será considerado apenas quando histórias futuras exigirem dados estruturados.

## Requisitos de ambiente

- Android Studio com suporte a projetos Gradle Kotlin DSL;
- JDK 17;
- Android SDK 35;
- um emulador ou dispositivo com Android 8.0 (API 26) ou superior.

O repositório não versiona arquivos `.jar`. Na primeira execução, os scripts `gradlew`
e `gradlew.bat` baixam o bootstrap oficial do Gradle 8.11.1 para o cache do usuário e
validam seu SHA-256 antes de executá-lo. Por isso, a primeira execução requer internet;
as seguintes reutilizam o arquivo validado em `~/.gradle/wrapper/lorem`.

## Abrir no Android Studio

1. Abra o diretório raiz do repositório no Android Studio.
2. Aguarde a sincronização do Gradle e a instalação do SDK solicitada pela IDE.
3. Selecione a configuração `app` e um dispositivo compatível.
4. Execute o aplicativo.

Sem dado salvo, o Lorem começa em **Configuração**. Salve o nome de teste para abrir **Início**; na próxima inicialização, o dado restaurado faz **Início** ser o destino inicial.

## Verificações

Na raiz do projeto:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

## Estrutura de diretórios

```text
.
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/dev/lorem/app/
│       │   │   ├── data/
│       │   │   ├── domain/
│       │   │   └── ui/
│       │   │       ├── navigation/
│       │   │       └── screens/
│       │   ├── res/
│       │   └── AndroidManifest.xml
│       └── test/
├── gradle/
├── AGENTS.md
├── build.gradle.kts
└── settings.gradle.kts
```

## Próximos passos

A próxima entrega é a **História 01 — Vincular o handle do Codeforces**. Ela substituirá o dado local temporário por um perfil validado, sem antecipar as demais funcionalidades descritas no `AGENTS.md`.
