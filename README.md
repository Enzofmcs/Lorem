# Lorem

Lorem é um aplicativo Android de treino individual para programação competitiva. O produto recomenda exercícios inéditos do Codeforces adequados ao nível do usuário e acompanha sua evolução.

Um **Ipsum** é a tentativa oficial e cronometrada de resolver um problema recomendado. Recomendação, cronômetro persistente, detecção de submissões, resultado, Rating Lorem, histórico, upsolve, estatísticas, variedade e consolidação de faixa já estão implementados.

> O [`AGENTS.md`](AGENTS.md) é o guia operacional compacto. A fonte de verdade detalhada está dividida por assunto em [`docs/`](docs/README.md). Este README apresenta apenas a visão técnica de entrada.

## Estado atual

A base Android de módulo único implementa as Histórias 00–17:

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
- preparação automática e manutenção com WorkManager, com progresso, retomada e retries;
- acompanhamento de submissões em RESUMED e no worker, com estado por conjunto de dados;
- importação incremental e proteção por versão do vínculo do perfil;
- atualização antes da recomendação e uso de cache após a primeira importação;
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

As **Histórias 00–20 estão implementadas**. Conectar um perfil agenda a importação
completa do histórico e catálogo. WorkManager mantém histórico/pendências a cada
15 minutos desejados e catálogo/metadados a cada 24 horas, sujeito ao Android.
Reabrir o app preserva a agenda e recupera dados ausentes ou vencidos.

A preparação mostra progresso por etapa e retoma importações interrompidas. O
primeiro Ipsum exige histórico importado; depois, falha de atualização permite
treinar com cache indicado na tela. Rating Lorem, consolidação e avaliações
originais permanecem preservados. O Ipsum consulta submissões ao entrar/retomar
e a cada 15 segundos em RESUMED, com backoff nas falhas. O worker também verifica
o ativo; o resultado é apresentado ao retornar à sua tela.

A identidade visual usa azul profundo e verde, com temas Sistema/Claro/Escuro
persistidos em Configurações. As abas Início, Histórico e Estatísticas preservam
estado e rolagem; Configurações ficam no cabeçalho. Ipsum e Resultado têm navegação
dedicada com voltar, mantendo a tentativa ativa ao sair da tela.

O primeiro acesso usa Conectar Codeforces e validação inline. O Início destaca
Rating Lorem, Iniciar/Continuar Ipsum, resumo local e último resultado. Perfil, tema
e recuperação por etapa ficam em Configurações; o status de atualização permanece
discreto no Início. A faixa consolidada preserva seu marco e permite expandir os
requisitos da próxima faixa.

O treino destaca tempo decorrido e Abrir no Codeforces; dica e encerramento mantêm
confirmação. O resultado mostra avaliação persistida, variação, tempo, dica e erros,
com AC posterior separado. A leitura acessível do timer é feita sob demanda.

Validação final: **208 testes debug e 155 release sem falhas**, lint sem erros
(apenas aviso preexistente de ícone) e `assembleDebug` concluído. O APK está em
`app/build/outputs/apk/debug/app-debug.apk`.

Consulte o [roadmap](docs/ROADMAP.md) e o [roteiro de demonstração](docs/DEMO.md).
**TESTE MANUAL OPCIONAL - NAO EXECUTADO** nesta sessão, conforme D-014.

## Próximas histórias

O planejamento de 13/10/2026 acrescenta **15–17 — Sincronização automática** e
**18–21 — Redesign**. As Histórias 00–14 e suas evidências permanecem preservadas;
as Histórias 15–17 estão implementadas e verificadas: coordenador compartilhado,
importação incremental retomável, proteção por vínculo, WorkManager e acompanhamento
automático. A base visual e a navegação da História 18 também estão verificadas.
Configuração/Início (19) e Ipsum/Resultado (20) estão implementados e verificados.
Resta o redesign de Histórico/Estatísticas (21), última história do planejamento.

A proposta combina WorkManager em segundo plano, consulta frequente durante o
Ipsum visível e temas claro/escuro em azul profundo e verde. Consulte o
[roadmap](docs/ROADMAP.md#historia-15---base-segura-para-sincronizacao-automatica),
[estratégia de sincronização](docs/SYNC.md) e [direção visual](docs/DESIGN.md).
