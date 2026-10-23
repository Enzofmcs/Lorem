# História 57 — duelo virtual experimental

A implementação acrescenta uma trajetória separada `virtual-duel-v1` aos treinos
cloud, com cálculo Python/Kotlin, modelo completo versionado e atualização atômica
no Firestore. A história 49 permanece fora desta entrega. D-012 continua sendo o
rating padrão; o resultado experimental não é somado ao saldo D-012.

## Contrato e escolhas do protótipo

- Inicialização provisória em **800**, sem importar CF ou converter o saldo legado.
  O CF anterior continua metadado. As simulações com inícios 800/1400/2000 comparam
  inicialização distante/próxima da habilidade do gerador; não justificam usar CF
  como prior real. A adoção geral/inicialização definitiva segue não decidida.
- **K=32** fixo no cliente experimental. Cálculo, snapshot, Rules e simulações
  também suportam K=64. Não existe fase provisória com término escolhido depois
  do resultado. Não se demonstrou K ótimo.
- **H=120 minutos**, absoluto e congelado. AC exatamente no limite conta;
  sincronização tardia não aumenta o prazo. Desistência usa a penalidade de não
  resolver até H, mesmo se ocorrer cedo; a razão fica separada do rating.
- Modalidade **sem dicas**, definida pelo início experimental. O botão de revelar
  tags é omitido e Repository/Rules recusam a dica. A compilação padrão mantém a
  prática com dicas/D-012. Não é possível retirar do rating um duelo em andamento.
- Rating e probabilidades em **milionésimos inteiros**, sem arredondamento de saldo
  a cada tentativa e sem piso/clamp. A interface usa seis casas. Ao sair do domínio
  do modelo, novo início experimental fica indisponível antes de criar tentativa,
  marker ou lock; nunca troca de regra depois de observar o resultado.
- Recomendação experimental e expectativa seguinte usam o saldo que o próprio duelo
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

Tentativas antigas continuam sem os campos novos e não são recalculadas. Um
cliente padrão novo consegue finalizar duelo existente mesmo com novos inícios
desabilitados: o catálogo congelado continua incluído. Cliente antigo não pode
omitir a decomposição experimental na finalização; as Rules recusam, mantendo
lock/saldo para recuperação por cliente compatível. Novos treinos padrão preservam
`duelRatingMicros` sem alterá-lo. Um modelo futuro exige manter versões antigas
para tentativas existentes; substituir arquivo e reutilizar hash é recusado.

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

Para preview experimental, **depois de preparar as Rules em ambiente de teste**:

```bash
./gradlew assembleDebug -Plorem.virtualDuelEnabled=true
```

A flag é false por padrão. O início informa modalidade sem dicas e saldo separado;
resultado mostra saldo anterior/novo, delta e tempo. O APK exige configuração
Firebase/OAuth local, como o produto atual; SDK de integração usa exclusivamente
Auth/Firestore em `demo-lorem-local`, nunca o projeto real.

Para desativar novos duelos, distribuir a compilação padrão (flag false),
**mantendo catálogo e Rules compatíveis**. Finalizar tentativas existentes com seus
snapshots; não apagar campos, reduzir Rules ao esquema antigo, migrar saldos ou
reescrever resultados. Publicação real das Rules e adoção padrão não foram
executadas por esta implementação. Sem migração automática, XP ou história 49.

## Evidências

[Relatório de cenários](../study/results/virtual-duel-v1/REPORT.md),
[cenários/fixtures](../study/results/virtual-duel-v1/scenarios.json),
[diagnóstico do modelo](../study/results/virtual-duel-v1/model.json) e
[evidência automatizada](evidence/2026-10-23-virtual-duel-57.json).

TESTE MANUAL OPCIONAL - NÃO EXECUTADO (D-014).
