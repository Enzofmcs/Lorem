# Curvas e pontuação: avaliação separada

Versão experimental time-curve-v1. Ativação: **desabilitada**.

## 1. Adequação das curvas

A avaliação preexistente permanece intacta: KM por delta teve MAE 15,135 min e
Brier integrado 0,122746; AFT teve 15,892 min/0,126693. São apenas quatro grupos
reservados, com censura e suporte desigual. Não retreinamos nem ajustamos o modelo
com esse teste. Esses números são contexto histórico, não uma nova seleção de
hiperparâmetros. Não existe validação empírica da política em Ipsums.

Na consulta retida CF 1200/problema 1600, KM: n=9969 e F(120)=0,622099.
p50=91,4667 min; p75/p90 indisponíveis no horizonte de 120 min. O número de eventos
não foi retido na consulta. Nenhum intervalo de confiança para esses quantis foi
retido. O aplicativo recebe indisponibilidade, sem converter p90 ausente em 2h.
O checkout não contém o KM completo; o artefato incluído é explicitamente parcial.
O exportador aceita o KM completo congelado para futuras regiões.

O filtro mínimo de tentativas/eventos e o domínio impedem extrapolação silenciosa,
mas não comprovam boa calibração por região. Antes de ativar, seriam necessários
intervalos de incerteza, suporte temporal suficiente por região e uma avaliação
independente no contexto de treino, com CF anterior observado. O saldo Lorem de
800 não entra como oldRating do modelo. Dados escassos ou problemas sem curva
válida usam D-012 com razão persistida. Isso pode produzir tratamento diferente
entre regiões e deve ser medido antes de qualquer ativação.

## 2. Mecânica dos multiplicadores

`scenarios.json`: **80 casos de paridade Python/Kotlin** e **120 trajetórias
sintéticas**, cada uma com dez tentativas. Ratings iniciais 800/1200/2000,
diferenças de dificuldade -400/0/+400/+800 (problema com piso 800), com/sem dica,
escalas de tempo constantes ou sequência mista. Problema acompanha o rating
corrente em cada passo. Essa dificuldade é contínua e idealizada: a simulação
não reproduz a quantização em 100 do recomendador, a disponibilidade do catálogo,
o sorteio por tags ou o esgotamento de problemas inéditos. Portanto não é uma
simulação integral de uso do aplicativo. Não há participantes nem resultados reais. Os limites
30/60/120 minutos são fixtures, não estimativas de um par real. Bônus 1,25 e
redução 0,5 são candidatos previamente fixados, sem otimização nesses cenários.

Para início em 1200, sem dica, variação após dez tentativas:

| Dificuldade relativa | Todas bônus | Todas normais | Todas reduzidas | Todas em T3 | Mista |
|---|---:|---:|---:|---:|---:|
| -400 | +140 | +110 | +60 | 0 | +2 |
| 0 | +300 | +240 | +120 | 0 | +92 |
| +400 | +460 | +370 | +190 | 0 | +184 |

Como referência mecânica, em dificuldade relativa 0, sem dica, a D-012 gera
+240/+240/+240/+120 nas quatro sequências constantes; a candidata gera
+300/+240/+120/0. Na mista são +128 pela D-012 e +92 pela candidata. São curvas
simuladas independentes, calculando o problema a partir do saldo de cada trajetória.

A análise de sensibilidade fixa nove combinações de bônus 1/1,1/1,25 e redução
0,25/0,5/0,75. Com base 24, o bônus varia de 24 a 30 e o ganho lento de 6 a 18;
normal permanece 24 e após T3 permanece zero. As invariantes mecânicas se mantêm,
mas os saldos variam substancialmente. Nenhuma combinação foi escolhida por
maximizar progresso nesses dados sintéticos.

Mista repete bônus, normal, reduzida, zero e falha duas vezes; falhas usam D-012.
Sem AC a duração/dica não modifica a perda. Em regiões suportadas, problemas
difíceis preservam ganho base maior e podem receber até 50 com bônus; o cap antigo
de 40 anularia parte do bônus. Dica nunca torna AC negativo: em ganho temporal 0
seu desconto também é 0. AC pode ser classificado como concluído com delta 0.

## Incentivos e limites observados

- Esperar não aumenta pontos: as faixas são não crescentes para todo ganho base
  de 0 a 40, com e sem dica. Desistir cedo não reduz perda sem AC.
- Existem saltos nas fronteiras: base 24 dá 30/24/12/0, sem dica; 22/18/9/0 com
  dica. Um milissegundo pode custar 6 ou 12 pontos nas fixtures. Pode incentivar
  submissão apressada e evitar pedir dica; deve ser avaliado em treino real.
- Resolução depois de T3 não aumenta rating, embora continue sendo aprendizado.
  Isso pode desestimular persistência; upsolve conserva o resultado original.
- Repetir problemas fáceis pode gerar progresso (+140 no cenário acima), mesmo
  com base menor. O produto exclui problemas já usados, mas isso não elimina
  escolha oportunista entre problemas inéditos. Simular somente sucessos não
  mede a relação real entre dificuldade, tempo e chance de AC.
- Modelos censurados podem não identificar p75/p90 justamente nos problemas
  difíceis. Aplicar fallback integral é explícito e estável, mas pode premiar
  mais que uma curva completa na faixa lenta. Não preencher limites com números
  arbitrários para esconder essa assimetria.

## Decisão

Não ativar a política com os dados atuais. O caminho de software e a decomposição
são verificáveis com fixtures; progressão razoável e qualidade das curvas em
Ipsums permanecem **não demonstradas**. Novos modelos exigem nova avaliação
pré-especificada, sem reutilizar o teste já examinado como material de ajuste.
A ativação exige gate independente no aplicativo e artefato habilitado: substituir
um arquivo não habilita a regra. O fallback D-012 é fixado por tentativa.

## Reprodução

Na raiz: `PYTHONPATH=study python3 study/scripts/simulate_time_scoring.py`.
Consulta: `PYTHONPATH=study python3 -m lorem_study lookup-time-curves --artifact
app/src/main/assets/time-curves-v1.json --old-rating 1200 --problem-rating 1600`.
Exportação: `PYTHONPATH=study python3 -m lorem_study export-time-curves --work
CAMINHO_DO_ESTUDO --artifact CAMINHO_JSON`. O comando só lê o modelo congelado;
não treina nem abre o teste. A exportação mantém ativação desabilitada.

A verificação do software está na [evidência da 39](../../../docs/evidence/2026-10-19-time-scoring-39.json):
321 testes debug/228 release, 15 SDK reais, 33 Auth/Rules e 91 Python aprovados,
sem ignorados; lint sem erros e APK. Esse resultado comprova o contrato de software,
não muda a decisão de ativação nem substitui avaliação empírica.
