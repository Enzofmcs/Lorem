# Validacao local com Ipsums — protocolo inicial v1

Historia 30 prepara dados e coleta prospectiva; Historia 31 decide transporte. A
amostra real de Ipsums ainda esta ausente. Nenhuma fixture autoriza integrar modelo,
trocar ratings ou alterar D-012. Resultados de contests constam no relatorio da 28.

## Exportacao e campos

Exportacao explicita para arquivo local em Configuracoes, por perfil, com pseudonimo
UUID persistente local. Sem handle, nome, tags, texto de problema ou ids de
submissao. Identidade publica do problema e timestamps necessarios ficam presentes;
pseudonimizacao nao garante anonimato. Somente tentativas encerradas; omitir ativo
para preservar o sigilo e evitar uma censura artificial de uma tentativa em curso.

Cada linha deve permitir auditar inicio, encerramento, primeiro AC valido no Ipsum,
AC posterior separado, dica, motivo do fim, rating do problema e Rating Lorem
inicial. CF deve ser snapshot observado via user.info antes do inicio, com data da
observacao; ausencia historica permanece null, mesmo que o perfil atual tenha um
rating conhecido. Nao usar newRating futuro nem Rating Lorem posterior.

Versoes de exportacao/coleta/regra, fonte/data/versao da elegibilidade e estado de
interrupcoes sao explicitos. Interrupcoes nao registradas sao desconhecidas, nunca
zero. Tempo de parede inclui pausas; nao e dedicacao efetiva. Um AC posterior nao
vira evento do Ipsum nem altera sua avaliacao. Legados permanecem no aplicativo e
podem ser exportados com campos ausentes e motivos de exclusao do estudo.

## Populacao e censura

Aplicar somente na base analitica: rating do problema positivo, origem/data
verificadas >=2020-07-01 UTC, contest regular com indices simples, sem qualquer
versao facil/dificil em todo o contest (D-026). Metadados estruturais ausentes ou
ambiguos sao inelegiveis, sem inferencia por id/nome de problema. O preparador
consulta standings completos pelo gate/cache para verificar a estrutura inteira;
falha interrompe/retoma e os brutos ficam locais. Nao enviar exportacoes a API.

Evento: primeiro AC valido persistido no encerramento COMPLETED. PENDING e
SOLVED_OUTSIDE sao censuras no encerramento original, com upsolve separado.
Intervalo ausente/nao positivo e snapshot CF ausente/futuro sao excluidos, nunca
imputados. Separar com/sem dica, interrupcoes desconhecidas e versoes de coleta.
Dica/estado final nao viram preditores de uma previsao ao iniciar.

## Amostra e particoes, antes da validacao

Seed 20261018, participantes/perfis inteiros e ordem por SHA-256; alvo 60/20/20,
minimo inicial de 10 participantes, 200 tentativas elegiveis e 100 eventos, com
6/2/2 participantes. E piso operacional de preparo, nao calculo de poder nem prova
de precisao. Amostra ausente/menor resulta em `insufficient_data`; nao ajustar
mapeamento nem abrir teste. Amostra congelada exige diretorio novo para qualquer
alteracao. Exportacoes repetidas identicas sao deduplicadas; conflitos sao recusados.

Pseudonimos do mesmo participante ficam na mesma particao. Reinstalacoes/contas
alternativas podem criar pseudonimos distintos; qualquer vinculo conhecido deve
ser resolvido antes do congelamento, sem alegar independencia pessoal perfeita.
Todos os arquivos e vinculos continuam locais, sem backend/login/nuvem.

## Gate posterior (31), ainda fechado

Antes de abrir os desfechos reservados, a Historia 31 devera fixar tamanho/precisao
final e limiares de MAE, Brier/calibracao com justificativa no relatorio 28. O piso
acima nao substitui essa decisao. Comparar CF anterior e Lorem inicial em pares;
correlacao nao prova equivalencia. Identidade e mapeamento linear simples usam
somente treino, escolha na validacao e participantes inteiros no teste.

Reusar avaliacao censurada/ICs por participante; relatar dominio, dados ausentes,
dica e pausas. Evidencia insuficiente ou qualquer falha do gate mantem ratings
distintos e a Historia 32 bloqueada. Nenhum resultado real de Ipsums existe nesta
entrega, e nenhuma metrica de fixtures sera apresentada como evidencia de transporte.
