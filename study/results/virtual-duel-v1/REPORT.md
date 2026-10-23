# Duelo virtual — relatório experimental da história 57

O estudo fornece distribuições de tempo; a transformação em pontos, K, prazo e
inicialização são escolhas de jogo. Não há evidência de equivalência CF/Lorem,
benefício pedagógico ou calibração em Ipsums. A história 49 não integra a entrega.

## Simulação reproduzível

Gerador: CDFs completas exportadas, com restrição isotônica identificada como nova
hipótese. Habilidade fixa 1400, problemas adaptados ao rating estimado em centenas
e domínio [800,3000], 300 tentativas por trajetória, 30 réplicas, seed 20261023+r.
A alternativa de contexto multiplica tempos amostrados por 1,25 antes do corte H.
D-012 usa os mesmos problemas/resultados, preservando piso 0 e fórmula oficial.

| Início | K | Fator de tempo | Média final experimental | Desvio padrão | Média D-012 nos mesmos problemas |
|---|---|---|---|---|---|
| 800 | 32 | 1.0 | 1329.74 | 55.05 | 2171.17 |
| 800 | 32 | 1.25 | 1110.46 | 94.75 | 2120.33 |
| 800 | 64 | 1.0 | 1352.15 | 41.32 | 1972.30 |
| 800 | 64 | 1.25 | 1291.40 | 62.53 | 2097.67 |
| 1400 | 32 | 1.0 | 1346.71 | 34.51 | 2022.27 |
| 1400 | 32 | 1.25 | 1306.34 | 44.62 | 2319.37 |
| 1400 | 64 | 1.0 | 1353.90 | 46.17 | 1989.43 |
| 1400 | 64 | 1.25 | 1307.48 | 56.72 | 2174.57 |
| 2000 | 32 | 1.0 | 1369.17 | 66.30 | 1882.17 |
| 2000 | 32 | 1.25 | 1314.23 | 22.65 | 2081.67 |
| 2000 | 64 | 1.0 | 1353.53 | 45.33 | 2034.53 |
| 2000 | 64 | 1.25 | 1299.61 | 73.28 | 2172.30 |

Há aproximação à habilidade sob o próprio gerador, sem identificação pontual
garantida: bins produzem platôs e saltos. Inícios baixos e tempos 25% mais lentos
alteram bastante a trajetória. Os resultados não selecionam K ótimo nem medem
superioridade empírica sobre D-012. A distribuição foi usada para gerar e avaliar;
isso não é validação independente. Nenhuma trajetória saiu do suporte nestes cenários.

## Equilíbrio e arredondamento

Os testes somam exatamente massas × delta para cada curva/K, incluindo empate de
não resolução: esperança zero sob a referência quantizada. Conservam ordem temporal,
limites ±K/2 e AC não pior que falha. Saldo em milionésimos não perde frações por
tentativa. Esse resultado é condicional ao modelo/contexto: recomendação adaptativa
preserva a propriedade apenas quando a referência condicional está calibrada.

Não há piso/clamp na trajetória experimental. Introduzir piso destrói equilíbrio
perto da fronteira: em saldo 0, perdas truncadas criariam deriva positiva. A saída
do domínio bloqueia novos inícios experimentais, sem truncar o resultado anterior.
Desistência usa F(H) original; selecionar abandono conforme dificuldade percebida
altera a distribuição de resultados e não conserva a alegação de esperança zero.
Auxílio, seleção de usuários/problemas e contexto de contest versus treino também
podem deslocar a calibração. Recompensa por esforço é outro conceito, sem XP aqui.

## Referência da issue

Valores K=64 usando KM bruto congelado de 20/10 no bin [400,800), n=7942:

| AC em minutos | Delta |
|---|---|
| 15 | +27.045888 |
| 30 | +18.182592 |
| 60 | +5.421184 |
| 90 | -2.416672 |
| 120 | -7.158016 |

Sem AC até 120 min: −19,579008. K=32 divide por dois. Estes valores são da
curva fonte; o artefato projetado é identificado separadamente. A projeção não
muda a referência deste bin nos pontos exibidos. 144 fixtures calculadas do
artefato real verificam paridade Kotlin/Python, fronteiras e empates.

O modelo contínuo AFT e a diferença de bins/níveis estão comparados numericamente
em `scenarios.json`; a restrição de ordem, seus 4514 cruzamentos brutos e ajuste
máximo 0,040405 estão em `model.json`. Não houve retreino de AFT, nova aquisição
obrigatória ou alteração nos estudos históricos.

A demonstração Android/Firestore é um teste de software com modelo congelado
real e transporte Codeforces controlado. Não é uma observação de treino humano.
O caso persistido/restaurado e usado no próximo início pode ser reproduzido em
Python pelo artefato e pelos campos da evidência.

Contrato/operação: [VIRTUAL_DUEL.md](../../../docs/VIRTUAL_DUEL.md).
TESTE MANUAL OPCIONAL - NÃO EXECUTADO (D-014).
