# Lorem

Lorem é um aplicativo Android de treino individual para programação competitiva. O produto recomenda exercícios inéditos do Codeforces adequados ao nível do usuário e acompanhará sua evolução.

Um **Ipsum** é a tentativa oficial e cronometrada de resolver um problema recomendado. A recomendação e o início persistente já estão implementados; cronômetro, submissões e avaliação serão desenvolvidos incrementalmente.

> O [`AGENTS.md`](AGENTS.md) é o guia operacional compacto. A fonte de verdade detalhada está dividida por assunto em [`docs/`](docs/README.md). Este README apresenta apenas a visão técnica de entrada.

## Estado atual

A base Android de módulo único concluiu a recomendação e o início da História 04:

- Kotlin, Jetpack Compose e Material 3;
- navegação que restaura o Ipsum ativo;
- escolha da tela inicial a partir de um perfil local validado;
- persistência atômica do perfil Codeforces com DataStore;
- consulta real ao `user.info`, centralizada por um limitador de requisições;
- catálogo e histórico locais em Room, com recomendação inédita e fallback auditável;
- persistência de um único Ipsum ativo antes da navegação;
- injeção manual e estado de UI exposto por `ViewModel`/`StateFlow`.

Ainda não há cronômetro, detecção de submissões, encerramento ou cálculo do rating após Ipsums.

## Escopo resumido

O Lorem terá perfil local associado a um handle público, catálogo e histórico do Codeforces, recomendação inédita, execução de um Ipsum, resultados, pendências, rating interno e estatísticas. Não fazem parte do escopo recursos sociais, backend próprio, Firebase, editor de código ou gamificação. Consulte [`docs/SPEC.md`](docs/SPEC.md) para as regras completas.

## Arquitetura inicial

- **`domain`**: modelos Kotlin e contratos independentes da interface Android;
- **`data`**: persistência DataStore, fakes determinísticos e ponto de integração remota;
- **`ui`**: composição raiz, `ViewModel`, tema, navegação e telas Compose;
- **`AppContainer`**: composição manual das dependências.

Composables recebem estado e ações: eles não acessam DataStore, Room ou serviços externos diretamente. Room preserva catálogo, histórico e Ipsums; DataStore mantém configurações pequenas.

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

Sem perfil salvo, o Lorem começa em **Configuração**. Valide um handle público do Codeforces para abrir **Início**; na próxima inicialização, o perfil restaurado faz **Início** ser o destino inicial.

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
├── docs/
├── build.gradle.kts
└── settings.gradle.kts
```

## Próximos passos

A próxima entrega é a **História 05 — Executar um Ipsum cronometrado**. Consulte o checklist em [`docs/ROADMAP.md`](docs/ROADMAP.md#historia-05---executar-um-ipsum-cronometrado) para os itens pendentes.
