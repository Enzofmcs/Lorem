# Rating Dolor e duelo virtual

**Nome na interface: Rating Dolor.** O rating anterior continua **Rating Lorem**.
Ambos aparecem separados no início, resultado, histórico e estatísticas cloud.
O saldo Dolor salvo aparece mesmo com novos duelos desabilitados; ausência de
avaliação é indicada explicitamente, sem inventar saldo ou reutilizar o atual
em resultados antigos. Campos persistidos e identificadores técnicos mantidos.

A política `virtual-duel-v1` entrou como preview na história 57. A história 59
ativa o Dolor no início padrão de novos Ipsums cloud. O Dolor guia a recomendação
e a referência do duelo. O resultado aplica Dolor e Lorem (D-012) em saldos
independentes na mesma transação Firestore.

## Contrato e escolhas do protótipo

- Inicialização em **800** para um vínculo sem saldo Dolor.
  O CF anterior continua metadado. As simulações com inícios 800/1400/2000 comparam
  inicialização distante/próxima da habilidade do gerador; não justificam usar CF
  como prior real.
- **K=32** fixo nos novos Ipsums. Cálculo, snapshot, Rules e simulações
  também suportam K=64. Não existe fase provisória com término escolhido depois
  do resultado. Não se demonstrou K ótimo.
- **H=120 minutos**, absoluto e congelado. AC exatamente no limite conta;
  sincronização tardia não aumenta o prazo. Desistência usa a penalidade de não
  resolver até H, mesmo se ocorrer cedo; a razão fica separada do rating.
- A tela Início informa a modalidade **sem dicas** antes do novo Ipsum. O snapshot
  fixa essa modalidade. A tela ativa omite a ação de dica e Repository/Rules
  aplicam a regra da tentativa gravada. As tentativas antigas com dicas seguem
  o contrato gravado no início.
- Rating e probabilidades em **milionésimos inteiros**, sem arredondamento de saldo
  a cada tentativa e sem piso/clamp. A interface usa seis casas. Ao sair do domínio
  do modelo, a tela Início informa a indisponibilidade antes de criar tentativa,
  marcador de uso ou lock.
- A recomendação e a expectativa seguinte usam o saldo que o próprio duelo
  atualiza. A recomendação reutiliza a normalização em centenas e o mínimo de
  dificuldade 800 do produto; isso não é piso aplicado ao saldo experimental.

Se L=F(T−), A=F(T), Q=F(H), em partes por milhão:

```text
deltaMicrosAC    = (K/2) × (1000000 − L − A)
deltaMicrosSemAC = −(K/2) × Q
novoSaldoMicros  = saldoAnteriorMicros + deltaMicros
```

K par torna essas contas exatas em inteiros. A esperança é zero **sob a própria
referência quantizada**, incluindo a massa sem AC e meio crédito por empates.
AC lento pode perder pontos e nunca é pior que falha na mesma referência/H.
Não aplicar base Elo, fator D-012, desconto científico de dicas ou fator da 39.

## Modelo, suporte e proveniência

Fonte completa: `study/results/scale-t50-v1/models/baseline.json`, KM congelado de
20/10, treino com 175.213 tentativas (amostra anterior; **não** o agregado ampliado
com 4.754.352 tentativas). Hash da fonte e hash de treino estão no artefato.
As fontes de 18/10 e os modelos antigos permanecem intactos. O agregado ampliado
não permite reconstruir CDFs apenas de t50; nenhuma aquisição nova é necessária
para reproduzir este protótipo.

`app/src/main/assets/virtual-duel-v1.json` contém CDFs completas até H em sete bins
D−R de largura 400: −1200, −800, −400, 0, 400, 800 e 1200, com extremo superior
exclusivo. Rating de entrada [21,3857] e dificuldade [800,3500], além do suporte
do bin. Mínimos 50 tentativas/20 eventos e seguimento >=H. Bins −2400/−2000/−1600
excluídos; não extrapolamos censura antes de H como falha em H. `availability`
retorna snapshot disponível ou motivo `out_of_domain`/`unsupported_region`.

O KM bruto cruza a ordem de dificuldade em 4.514 pares de bins/segundos. O modelo
escolhido é uma **nova hipótese**, `km-delta-isotonic-20261020-v1`: projeção
isotônica decrescente da CDF por delta, ponderada por n, em cada segundo. É
monótona no tempo e na dificuldade/habilidade dentro do domínio, com platôs e
saltos entre bins. O maior ajuste absoluto é 0,040405 de probabilidade. Não
reescreve curvas descritivas nem é anunciado como modelo validado/superior.
Quantização em 1 ppm dá erro máximo de 0,000064 ponto para K=64 frente à CDF
projetada não quantizada, por avaliação AC.

A comparação com o AFT lognormal suave congelado está em `scenarios.json`. Por
exemplo, F(120) é 0,611844 no bin delta=400; o AFT dá 0,620752 em 1200/1600 e
0,404863 em 2000/2400. Em 1400/1399 → 1400/1400, a referência por bins salta
0,911503 → 0,729676, enquanto o AFT muda suavemente 0,878533 → 0,878031.
Continuidade não prova superioridade; os estimandos e a dependência em nível
absoluto diferem. Escolhemos o modelo por bins para um primeiro caminho completo
reproduzível, identificando sua limitação. Não há nova validação independente.

## Persistência e compatibilidade

`snapshot.virtualDuel` congela política, modelo/hash, saldo de entrada, dificuldade,
bin, K, H e auxílio. A identidade da tentativa, uid/handle, vínculo, progressVersion,
início e prazo são os documentos externos imutáveis já usados pelo produto.
`result.virtualDuel` contém saldo anterior/delta/novo e F(T−)/F(T)/F(H). O perfil
mantém `duelRatingMicros`. Resultado, dois saldos, progressVersion e liberação do
lock são uma única transação. UI/exportação leem o resultado salvo; upsolve não
reavalia nada. Erro/rede/TESTING/histórico incompleto adiam a avaliação.

As tentativas anteriores ao duelo conservam os resultados originais. Um
cliente com novos duelos desabilitados consegue finalizar duelo existente
usando o catálogo congelado. Cliente antigo não pode
omitir a decomposição experimental na finalização; as Rules recusam, mantendo
lock/saldo para recuperação por cliente compatível. Com a flag desabilitada,
novos Ipsums usam somente Lorem e mantêm o saldo Dolor gravado. Um modelo futuro exige manter versões antigas
para tentativas existentes; substituir arquivo e reutilizar hash é recusado.

O Git grava o JSON do modelo com quebras LF. O Android normaliza CRLF para LF
antes de verificar o SHA-256 usado no snapshot. Se o arquivo estiver ausente ou
inválido, a conta e o histórico continuam acessíveis. O início do Ipsum informa
a indisponibilidade do modelo, e avaliações Dolor em andamento aguardam uma
versão do app com o catálogo válido.

Rules validam identidade, vínculo, prior, bin, versão/hash, H/K, ausência de dica,
CDF no prazo, limites, aritmética e atualização atômica. **No Spark, continuam
sem comprovação independente do AC e sem reexecutar a consulta completa F(T) do
cliente**. Fixam F(H) e verificam a decomposição/limites; um cliente modificado
pode mentir sobre AC/tempo/CDF dentro desses limites. Não tratar o resultado como
prova antifraude ou dado de avaliação independente.

## Executar e desativar prospectivamente

Exportação e simulação, sem rede ou microdados adicionais:

```bash
python3 study/scripts/build_virtual_duel.py
python3 study/scripts/simulate_virtual_duel.py
PYTHONPATH=study python3 -m unittest discover -s study/tests -q
```

Para testar o fluxo padrão com as Rules compatíveis em ambiente de teste:

```bash
./gradlew assembleDebug
```

O resultado mostra saldos anterior e novo, variação e tempo. O APK exige configuração
Firebase/OAuth local, como o produto atual; SDK de integração usa exclusivamente
Auth/Firestore em `demo-lorem-local`, nunca o projeto real.

Para desativar novos duelos, compilar com `-Plorem.virtualDuelEnabled=false`.
Manter o catálogo e as Rules compatíveis para finalizar tentativas existentes com
seus snapshots.

## Evidências

[Relatório de cenários](../study/results/virtual-duel-v1/REPORT.md),
[cenários/fixtures](../study/results/virtual-duel-v1/scenarios.json),
[diagnóstico do modelo](../study/results/virtual-duel-v1/model.json) e
[evidência automatizada](evidence/2026-10-23-virtual-duel-57.json).

TESTE MANUAL OPCIONAL - NÃO EXECUTADO (D-014).
