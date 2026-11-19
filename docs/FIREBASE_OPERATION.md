# Operação e compatibilidade do cliente Firebase

Requisitos: [história 40/#73](https://github.com/Enzofmcs/Lorem/issues/73).
Evidência local: [2026-10-20-cloud-40-43.json](evidence/2026-10-20-cloud-40-43.json).
O responsável confirmou em 20/10/2026 OAuth autorizado e Rules atualizadas: [confirmação do responsável](evidence/2026-10-20-firebase-oauth-rules-confirmation.json). O agente não executou deploy, leitura administrativa remota ou login Google real.

## Matriz de compatibilidade

| Cliente | Rules 35–37 | Rules 38 (`26f58f0`) | Rules 39 (`e36361a`) | Rules atuais (57+) |
| --- | --- | --- | --- | --- |
| 38 sem `snapshot.scoring` | Não cobre exclusões legadas | Contrato original | Leitura e transições legadas | Leitura e transições legadas |
| D-012 com `snapshot.scoring` | Incompatível | Início recusado | Início aceito | Início aceito |
| APK padrão da história 59 com `snapshot.virtualDuel` | Incompatível | Incompatível | Início recusado (`PERMISSION_DENIED`) | Início aceito |
| time-curve-v1 | Incompatível | Incompatível | Suporte técnico, ativação desabilitada | Suporte técnico, ativação desabilitada |

A avaliação D-012 também grava `snapshot.scoring`. Desde a história 59, o APK
padrão inclui `snapshot.virtualDuel`. O teste `compatibility.test.cjs` usa projetos
de demonstração com cópias integrais das Rules 38 e 39 e com as Rules da `main`.
A recusa do início preserva a atomicidade do Ipsum, do marcador de uso e da trava.

## Estados observados, sem misturar evidências

- **Código local:** Rules atuais testadas nos emuladores Auth/Firestore. SHA-256,
  base Git, contagens e APK registrados no JSON de evidência.
- **Confirmação do responsável:** em 20/10/2026, o usuário informou OAuth já autorizado e Rules do Firebase já alteradas. [confirmação do responsável](evidence/2026-10-20-firebase-oauth-rules-confirmation.json). A confirmação anterior 35–37 permanece histórica. Hash e projeto publicados não foram informados nesta atualização.
- **Observação do agente:** nenhum deploy, leitura remota das Rules ou login Google real executado pelo agente. A confirmação do usuário é o registro disponível do estado remoto.
- **Configuração Android:** o usuário adicionará `app/google-services.json` localmente; o arquivo permanece ausente neste checkout do agente.
  O build sem esse arquivo é reproduzível e apresenta configuração ausente;
  compilar com sucesso não comprova que o login esteja operacional.

## Publicação das regras no projeto do app

O registro de 20/10/2026 é anterior às Rules do Dolor. Se o APK da história 59
receber `PERMISSION_DENIED` ao salvar um novo Ipsum, confira no projeto usado
pelo app se a versão publicada aceita `snapshot.virtualDuel`.

1. Conferir o projeto Firebase já escolhido, aplicativo `dev.lorem.app` e região
   existente. Não criar outro projeto nem alterar região/dados para recuperar.
2. Restaurar localmente `app/google-services.json`, conferir projeto/pacote,
   cliente OAuth Web, Google habilitado e SHA-1/SHA-256 do certificado que assina
   o APK. Usar `npm --prefix firebase run check-config`
   e os requisitos de [FIREBASE_SETUP.md](FIREBASE_SETUP.md). Não versionar o JSON.
3. Revisar `firebase/firestore.rules` da `main` e publicar apenas as regras no
   projeto confirmado. Na raiz do repositório, com o Firebase CLI autenticado,
   executar `firebase deploy --only firestore:rules --project <id-confirmado>`.
4. Registrar separadamente commit/hash publicado, projeto, horário e confirmação
   do responsável; se houver leitura administrativa, registrar sua origem e o
   hash observado sem trocar confirmação por readback inexistente.
5. Se o APK instalado já aponta para o projeto correto, tocar em **Iniciar Ipsum**
   novamente após a publicação. Se foi necessário corrigir a configuração do APK,
   recompilar e atualizar preservando os dados locais.

## Diagnóstico e recuperação

| Sintoma | Ação recuperável |
| --- | --- |
| Configuração ausente | Restaurar JSON/OAuth correto e recompilar; não criar usuário fictício |
| OAuth inválido | Conferir cliente Web, pacote e certificado; voltar ao login |
| `PERMISSION_DENIED` ao iniciar | Conferir sessão, projeto do APK e compatibilidade das Rules publicadas com `snapshot.scoring` e `snapshot.virtualDuel` |
| Rede/cota/`UNAVAILABLE` | Mostrar cache/offline, preservar tentativa e repetir quando disponível |
| Página CF incompleta, erro ou budget atingido | Nenhuma recomendação é autorizada; retomar preparação no mesmo contexto |
| Resposta perdida após commit | Repetir o mesmo commandId; recuperar ativo/resultado sem novo saldo ou prazo |
| `CLOSED_AWAITING_VERIFICATION` | Preservar lock/prazo, conferir submissões completas; adiar gravação se necessário |
| Conta/handle mudou | Descartar resposta antiga; recarregar o contexto validado |

Não reverter para Rules38 enquanto o cliente atual grava `snapshot.scoring`.
Para recuperação de publicação, usar um par cliente/Rules verificado, preservando
permissões de exclusões e leitura de snapshots anteriores. Não apagar documentos,
limpar armazenamento, regravar avaliações ou reativar escrita no arquivo local.
No Spark, o Android consulta o Codeforces e calcula o resultado. Rules não provam
AC de cliente modificado; WorkManager exige aparelho/rede/sessão e pode atrasar.

## Diagnóstico do retorno da conta Google

Correção pós-43: [evidência](evidence/2026-10-20-google-login-fix.json). O usuário relatou que o seletor
fecha e o botão volta a Entrar com Google sem mensagem. O aplicativo agora informa
interrupção/credencial ausente e retoma explicitamente a validação após a troca do
token, mesmo sem nova emissão do listener ou com a mesma conta. Firebase e
validação de sessão têm espera limitada a 30s, com falha recuperável; nunca entrar
apenas com token trocado ou conta em cache.

No Logcat do Android Studio, usar `tag:LoremAuth`. Pelo terminal:

```bash
adb logcat -v time -s LoremAuth
```

| Última etapa/código | O que indica |
| --- | --- |
| google_picker_opened | Solicitação enviada ao seletor |
| credential_provider_cancelled / google_picker_interrupted | Google interrompeu a entrega; o Firebase ainda não recebeu uma credencial |
| google_credential_unavailable | Provedor não entregou credencial Google válida |
| google_credential_received → firebase_exchange_started | Credencial entregue; troca no Firebase começou |
| firebase_sdk_error:CODIGO:MOTIVO / firebase_exchange_failed:MOTIVO | Falha do SDK, com código operacional para diagnóstico |
| session_token_validated → account_bootstrap_started | Sessão Google validada; aguardando confirmação da conta no Firestore |
| account_bootstrap_succeeded → session_ready → ui_auth_state:signed_in | Sessão confirmada e exibida pela interface |
| session_failed:NETWORK | Falha de rede ou limite de espera; repetir após recuperar conectividade |

Após a PR85, o usuário passou a ver “A entrada foi interrompida pelo Google”.
Isso localiza a falha antes da troca Firebase, mas não comprova cancelamento manual.
O diagnóstico adicional preserva somente códigos numéricos do SDK:

| Detalhe de credential_provider_* | Interpretação |
| --- | --- |
| origin:activity_result:result:0 | A Activity retornou RESULT_CANCELED; não comprova que o usuário apertou cancelar |
| origin:google_api:status:16 | Google retornou cancelamento via API; verificar detail_code e logs do provedor |
| origin:google_api:status:10 | Erro de configuração do cliente; conferir pacote, certificado do APK instalado e cliente Web conforme FIREBASE_SETUP.md |
| detail_code:NUMERO | Código adicional do provedor, quando presente no formato reconhecido |
| origin:unknown | SDK não expôs um código no formato reconhecido; nenhuma mensagem bruta é registrada |

Não há retentativa automática que reabra o seletor após cancelamento. Se o provedor
retornar apenas RESULT_CANCELED, complementar o Logcat com os tags do SDK
`CredentialManager`, `GetSignInIntent` e `HiddenActivity`, revisando dados pessoais
antes de compartilhar. [Verificação do diagnóstico](evidence/2026-10-20-google-provider-diagnostic.json).

Os logs registram nomes de etapas e códigos, sem tokens, UID, e-mail ou mensagens
brutas de exceções. A causa do retorno do provedor no aparelho não foi observada
neste ambiente; a configuração OAuth/Rules confirmada pelo usuário permanece
registrada. Nenhum deploy nem modificação de OAuth/Rules integra esta correção.

## Rating Dolor no fluxo padrão

O APK padrão usa as Rules compatíveis com o duelo virtual, introduzidas na
história 57. Verifique a versão das Rules no projeto de destino antes de
distribuir. Para suspender novos duelos, compile com
`-Plorem.virtualDuelEnabled=false` e mantenha o catálogo e as Rules para finalizar
as tentativas já iniciadas. [Operação e compatibilidade do duelo virtual](VIRTUAL_DUEL.md)
