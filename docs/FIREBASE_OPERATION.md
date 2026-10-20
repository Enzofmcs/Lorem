# Operação e compatibilidade do cliente 40–43

Requisitos: [história 40/#73](https://github.com/Enzofmcs/Lorem/issues/73).
Evidência local: [2026-10-20-cloud-40-43.json](evidence/2026-10-20-cloud-40-43.json).
O responsável confirmou em 20/10/2026 OAuth autorizado e Rules atualizadas: [confirmação do responsável](evidence/2026-10-20-firebase-oauth-rules-confirmation.json). O agente não executou deploy, leitura administrativa remota ou login Google real.

## Matriz de compatibilidade

| Cliente | Rules 35–37 | Rules 38 (`26f58f0`) | Rules atuais (39, mantidas em 40–43) |
| --- | --- | --- | --- |
| 38 sem `snapshot.scoring` | Não cobre exclusões legadas | Contrato original | Leitura/transições legadas preservadas |
| 39 e 40–43, D-012 com `snapshot.scoring` | Não distribuir nesta combinação | Início recusado, confirmado no emulador | Início, AC/timeout, retry e aplicação única testados com SDK real |
| time-curve-v1 | Incompatível | Incompatível | Suporte técnico preservado; **ativação desabilitada** |

O fallback também grava `scoring`; desativar a curva não torna o cliente compatível
com as Rules antigas. O teste `compatibility.test.cjs` usa projetos de demonstração
separados para ambas as versões. A fixture Rules38 é cópia integral de
`26f58f0:firebase/firestore.rules`, sem relaxamento ou alteração do arquivo atual.
D-012 permanece a política vigente; o saldo inicial de um novo vínculo é 800.

## Estados observados, sem misturar evidências

- **Código local:** Rules atuais testadas nos emuladores Auth/Firestore. SHA-256,
  base Git, contagens e APK registrados no JSON de evidência.
- **Confirmação do responsável:** em 20/10/2026, o usuário informou OAuth já autorizado e Rules do Firebase já alteradas. [confirmação do responsável](evidence/2026-10-20-firebase-oauth-rules-confirmation.json). A confirmação anterior 35–37 permanece histórica. Hash e projeto publicados não foram informados nesta atualização.
- **Observação do agente:** nenhum deploy, leitura remota das Rules ou login Google real executado pelo agente. A confirmação do usuário é o registro disponível do estado remoto.
- **Configuração Android:** o usuário adicionará `app/google-services.json` localmente; o arquivo permanece ausente neste checkout do agente.
  O build sem esse arquivo é reproduzível e apresenta configuração ausente;
  compilar com sucesso não comprova que o login esteja operacional.

## Próximo passo e roteiro de referência

OAuth e atualização das Rules já foram confirmados pelo responsável; não há solicitação de novo deploy. Resta adicionar o JSON local e recompilar. Os passos de publicação abaixo ficam como referência para futuras atualizações.

1. Conferir o projeto Firebase já escolhido, aplicativo `dev.lorem.app` e região
   existente. Não criar outro projeto nem alterar região/dados para recuperar.
2. Restaurar localmente `app/google-services.json`, conferir projeto/pacote,
   cliente OAuth Web, Google habilitado e SHA-1/SHA-256 do certificado que assina
   o APK. Usar `npm --prefix firebase run check-config`
   e os requisitos de [FIREBASE_SETUP.md](FIREBASE_SETUP.md). Não versionar o JSON.
3. Comparar o SHA-256 de `firebase/firestore.rules` com a evidência e revisar o
   arquivo inteiro. Publicar **explicitamente pelo responsável** com a seleção do
   projeto real conferida, por exemplo
   `firebase deploy --only firestore:rules --project <id-confirmado>`.
   Este comando é referência para atualizações futuras; a atualização atual foi confirmada pelo usuário. O agente não o executou.
4. Registrar separadamente commit/hash publicado, projeto, horário e confirmação
   do responsável; se houver leitura administrativa, registrar sua origem e o
   hash observado sem trocar confirmação por readback inexistente.
5. Compilar o APK configurado e atualizar sem limpar dados. Conferência visual
   opcional: Google → handle → iniciar → AC/timeout → resultado → reabrir.
   Conservar o arquivo local e as exclusões; nunca importar seu saldo para cloud.

## Diagnóstico e recuperação

| Sintoma | Ação recuperável |
| --- | --- |
| Configuração ausente | Restaurar JSON/OAuth correto e recompilar; não criar usuário fictício |
| OAuth inválido | Conferir cliente Web, pacote e certificado; voltar ao login |
| `PERMISSION_DENIED` ao iniciar | Conferir contrato/versão publicada e sessão; não remover `scoring` nem abrir permissões |
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

Os logs registram nomes de etapas e códigos, sem tokens, UID, e-mail ou mensagens
brutas de exceções. A causa do retorno do provedor no aparelho não foi observada
neste ambiente; a configuração OAuth/Rules confirmada pelo usuário permanece
registrada. Nenhum deploy nem modificação de OAuth/Rules integra esta correção.
