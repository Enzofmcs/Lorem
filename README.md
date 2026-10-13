# Lorem

Lorem é um aplicativo Android de treino individual para programação competitiva. O produto recomenda exercícios inéditos do Codeforces adequados ao nível do usuário e acompanha sua evolução.

Um **Ipsum** é a tentativa oficial e cronometrada de resolver um problema recomendado. Recomendação, cronômetro persistente, detecção de submissões, resultado, Rating Lorem, histórico, upsolve, estatísticas, variedade e consolidação de faixa já estão implementados.

> O [`AGENTS.md`](AGENTS.md) é o guia operacional compacto. A fonte de verdade detalhada está dividida por assunto em [`docs/`](docs/README.md). Este README apresenta apenas a visão técnica de entrada.

## Estado atual

A base Android de módulo único implementa as Histórias 00–14:

- Kotlin, Jetpack Compose e Material 3;
- navegação que restaura o Ipsum ativo;
- escolha da tela inicial a partir de um perfil local validado;
- persistência atômica do perfil Codeforces com DataStore;
- consulta real ao `user.info`, centralizada por um limitador de requisições;
- catálogo e histórico locais em Room, com recomendação inédita e fallback auditável;
- persistência de um único Ipsum ativo antes da navegação;
- encerramento, resultado e atualização única do Rating Lorem;
- histórico e detecção de AC posterior, preservando a avaliação original;
- estatísticas de AC, dica, tempo, erros e desempenho por rating e tag, com evolução do rating persistido;
- recomendação por necessidade e recência de tags, com fatores persistidos;
- consolidação permanente e requisitos objetivos para avançar de faixa;
- injeção manual e estado de UI exposto por `ViewModel`/`StateFlow`.

As Histórias 10 e 11 foram concluídas após a confirmação do usuário de todos os testes manuais em dispositivo em 13/10/2026. As definições dos indicadores e o conjunto de referência estão no [roadmap](docs/ROADMAP.md#historia-11---estatisticas-confiaveis).

## Escopo resumido

O Lorem tem perfil local associado a um handle público, catálogo e histórico do Codeforces, recomendação inédita, execução de um Ipsum, resultados, pendências, rating interno, estatísticas, variedade e consolidação. Não fazem parte do escopo recursos sociais, backend próprio, Firebase, editor de código ou gamificação. Consulte [`docs/SPEC.md`](docs/SPEC.md) para as regras completas.

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

## Entrega atual

As **Histórias 00–14 estão implementadas**. A recomendação considera exposição,
falha, dica e recência por tags oficiais, preservando rating e ineditismo. A maior
faixa consolidada permanece registrada, enquanto o Rating Lorem estimado acompanha
o desempenho atual. Home e Estatísticas explicam o progresso da próxima faixa.

O primeiro Ipsum exige sincronizar o histórico do perfil. Metadados e sincronização
preservam rating/consolidação; a retomada recupera o Ipsum ativo e o encerramento
mantém os dados originais imutáveis. A migração Room 9–10 preserva os resultados
anteriores e acrescenta os fatores da recomendação.

Validação final: **147 testes debug e 116 release sem falhas**, lint sem erros
(apenas aviso preexistente de ícone) e `assembleDebug` concluído. O APK está em
`app/build/outputs/apk/debug/app-debug.apk`.

Consulte o [roadmap concluído](docs/ROADMAP.md) e o [roteiro de demonstração](docs/DEMO.md).
Testes manuais são opcionais e não bloqueiam conclusão ou avanço; nenhuma conferência
em dispositivo foi alegada nesta sessão.
