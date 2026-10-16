# Direção visual do Lorem

Direção de 13/10/2026 para as Histórias 18–21. A base da História 18 está implementada:
paleta semântica clara/escura, preferência persistida, componentes e navegação.
Configuração/Início (19) e Ipsum/Resultado (20) também estão implementados.
O próximo e último redesign planejado é Histórico/Estatísticas (21).
Este documento e a [referência visual](design/lorem-concept.svg) orientam o redesign;
os números da referência são exemplos ilustrativos, não dados do usuário.

## Conceito

Base do planejamento conferida no GitHub em `485f413` (PR #29): Histórias 00–14 concluídas.

Um caderno de treino preciso e tranquilo: azul profundo, verde como destaque,
superfícies discretas, bastante espaço e números legíveis. A ação principal é
treinar; histórico e estatísticas ajudam a entender o treino. Evitar transformar
a tela inicial numa lista de tarefas de sincronização ou introduzir gamificação.

Material 3 e Compose continuam como base. Tipografia do sistema, ícones vetoriais
coerentes e componentes próprios pequenos bastam. Suporte claro/escuro desde o
início, padrão seguindo o sistema e escolha persistida em Configurações. A proposta
usa uma paleta fixa para identidade consistente; cores dinâmicas ficam fora dela.

## Tokens iniciais

| Papel | Claro | Escuro |
|---|---|---|
| Fundo | `#F5F7FA` | `#0B1220` |
| Superfície | `#FFFFFF` | `#151F30` |
| Superfície elevada / contorno | `#E6EBF2` | `#26354B` |
| Texto principal | `#142238` | `#F1F5FA` |
| Texto secundário | `#526177` | `#A8B6CC` |
| Ação principal | `#006B55` | `#6EE7BE` |
| Texto sobre ação | `#FFFFFF` | `#0B1220` |
| AC / confirmação | `#006B55` | `#6EE7BE` |
| Pendente / atenção | `#805500` | `#F5CD79` |
| Erro / encerramento | `#B4233A` | `#FF8FA3` |

Mapear esses valores para papéis semânticos de `ColorScheme`, sem espalhar hex nas
telas. Cor nunca é o único indicador: adicionar texto e ícone. Validar os pares
efetivamente usados: contraste mínimo de 4,5:1 para texto normal e 3:1 para texto
grande e indicadores necessários. Contornos discretos são decorativos; controles
interativos precisam de contraste próprio.

- Espaçamento: grade de 4 dp, padding de tela de 24 dp, gaps de 8/16/24 dp.
- Cards: raio de 20 dp; botões de 14 dp, altura mínima de 52 dp; chips compactos
  com área de toque de ao menos 48 dp. Evitar sombras fortes e gradientes decorativos.
- Títulos de tela: 28 sp; seção: 20 sp; corpo: 16 sp; legenda: 12–14 sp.
  Rating e cronômetro: 40–48 sp com números tabulares. Respeitar escala de fonte;
  tamanhos são referências, não alturas fixas de conteúdo.
- Componentes: `LoremScaffold`, cabeçalho, card de métrica, indicador de sync,
  badge de resultado, estado vazio/erro e botão principal. Criar apenas componentes
  usados nas telas; não montar uma biblioteca genérica sem necessidade.

## Navegação

Barra inferior com **Início**, **Histórico** e **Estatísticas**, sempre com rótulos.
Configurações acessíveis no cabeçalho. Preservar estado/rolagem ao trocar de aba,
com back stack previsível. Ipsum e Resultado usam fluxo dedicado com botão de
voltar; durante o treino, saída da tela não encerra a tentativa. Restauração do
Ipsum ativo após morte do processo continua obrigatória.

## Telas

**Configuração.** Marca e promessa curta, campo de handle com `@` visual, explicação
de que usa dados públicos e botão “Conectar Codeforces”. Validação inline e estado
de carregamento no botão. Depois, preparação automática de histórico e catálogo
com progresso por etapa, sem exigir cliques para sincronizar. Perfil, tema e
detalhes da sincronização ficam organizados em seções no acesso posterior.

**Início.** Saudação e handle no cabeçalho, Rating Lorem em card destacado e rating
oficial como referência secundária. Uma ação dominante: “Iniciar Ipsum” ou “Continuar
Ipsum”. Resumo compacto dos treinos locais e acesso ao último resultado. Estado de
sync discreto, sem lista de endpoints ou contagens técnicas. Preservar a faixa
consolidada e seus requisitos da História 13, reutilizando o contrato existente;
ausência de evidência continua representada por `null`, sem inventar valor.

**Ipsum.** Nome/id do problema, cronômetro grande com rótulo “Tempo decorrido”,
status da consulta automática e submissões/erros definitivos. “Abrir no Codeforces”
é a ação principal. “Revelar tópicos” é secundária e confirma o registro da dica;
“Encerrar sem AC” é uma ação de menor destaque com confirmação e motivo. Rating,
categoria, tags e justificativa da recomendação não aparecem enquanto proibidos,
inclusive em semântica de acessibilidade. Depois da dica, só tags oficiais são
reveladas. O cronômetro é decorrido, sem sugerir prazo fatal inexistente.

**Resultado.** AC ou encerramento sem AC em título claro, rating anterior → atual
e variação explicada, tempo, dica e erros em bloco compacto. Dados e justificativa
do problema revelados somente conforme as regras. Ação “Voltar ao início”, acesso
ao problema/pendência e espaço para a explicação já existente do Rating Lorem.
Nada de confete, conquista ou rating recalculado pela tela.

**Histórico.** Lista cronológica de cards com nome/id, data, tempo, dica e situação
textual. Filtros para as três situações: AC no Ipsum, pendente e AC posterior.
Filtros avançados recolhidos, indicação de filtros ativos e “Limpar filtros”. No
upsolve, distinguir avaliação original e data do AC posterior. Um estado vazio
explica a ausência de tentativas ou de resultados para aquele filtro.

**Estatísticas.** Resumo de treinos, AC e tempo com denominadores/amostras; curva
de Rating Lorem pelos snapshots persistidos; barras horizontais para rating e
tags com rótulos. Gráficos apenas quando ajudarem, acompanhados de dados acessíveis.
“Sem amostra” é diferente de zero. Grupos por tag não devem sugerir que suas
contagens somadas equivalem ao total de treinos. Rankings permanecem auditáveis.

## Comportamento e acessibilidade

Conteúdo local aparece antes da rede. Carregamento de uma ação não bloqueia toda a
tela. Erro fica junto da informação afetada, com recuperação clara. Não exibir
snackbar a cada polling nem trocar “último sucesso” por “última tentativa”.

Suportar largura de 320 dp, paisagem, teclado/IME, barras do sistema e fonte em
200%, com rolagem quando necessária. TalkBack deve anunciar rótulos e situações,
sem ler o cronômetro a cada segundo nem revelar dados ocultos. Gráficos precisam
de alternativa textual. Animar apenas mudanças úteis, sem depender de animações.

## Entregas e validação

18: tema, tokens, componentes e navegação. 19: configuração e Início. 20: Ipsum e
Resultado. 21: Histórico e Estatísticas. O aceite funcional está no [roadmap](ROADMAP.md).

Guardar previews Compose com estados relevantes por tela. Fazer revisão visual em
claro/escuro e smoke test em dispositivo para fonte ampliada, TalkBack, rotação,
offline e dados reais. Testar navegação e proteção de dados ocultos; reaproveitar
testes do domínio e não criar testes que apenas repetem padding ou cores.
Conferências em dispositivo são opcionais (D-014), sem bloquear avanço; critérios
funcionais permanecem obrigatórios com evidência automatizada proporcional ao risco.


A base implementada usa `LoremTheme`/`Color`/`Type` e os componentes em
`ui/components`. `LoremFoundationPreview` cobre claro, escuro, 320 dp/fonte 200%
e paisagem. `FoundationLayoutTest` renderiza os componentes e Configurações com
gráficos nativos do Robolectric em `app/build/reports/visual/`, verifica texto
completo, controles de 48 dp, semântica selecionável e insets simulados. Essas
renderizações automatizadas não equivalem a ensaio humano em dispositivo.

A História 19 aplica essa direção a Configuração e Início. Perfil, Aparência e
Preparação/sincronização têm seções próprias. O Início usa uma ação dominante,
resumo dos treinos locais e progresso expansível da faixa. `HomeConfigurationPreviews`
e `HomeConfigurationLayoutTest` cobrem os estados e geram imagens em
`app/build/reports/visual/`, incluindo primeiro acesso com erro/carregamento,
recuperação offline e Continuar Ipsum, nos dois temas e tamanhos de referência.

A História 20 aplica essa direção a Ipsum e Resultado. O timer tabular adapta o
tamanho à largura e à fonte real; sua leitura acessível é acionada sob demanda,
sem anúncio a cada segundo. Resultado usa snapshots de rating e distingue AC
posterior da avaliação original. `IpsumPreviews` e `IpsumLayoutTest` cobrem os dois
temas, 360 dp, 320 dp/fonte 200%, paisagem, textos longos e confirmações em outra
janela com escala de fonte do Android. Imagens ficam em `app/build/reports/visual/`.
