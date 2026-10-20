# Operação e compatibilidade do cliente 40–43

Requisitos: [história 40/#73](https://github.com/Enzofmcs/Lorem/issues/73).
Evidência local: [2026-10-20-cloud-40-43.json](evidence/2026-10-20-cloud-40-43.json).
Este registro não atesta publicação, OAuth real ou leitura administrativa remota.

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
- **Confirmação do responsável:** confirmação histórica das Rules35–37 em
  `2026-10-19-firebase-rules-35-37-publication.json`. Não cobre Rules38/39.
- **Projeto real:** nenhuma publicação ou leitura remota das Rules atuais nesta
  sessão; nenhuma autenticação Google real foi executada.
- **Configuração Android:** `app/google-services.json` ausente neste checkout.
  O build sem esse arquivo é reproduzível e apresenta configuração ausente;
  compilar com sucesso não comprova que o login esteja operacional.

## Sequência preparada para o responsável

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
   Este comando é um roteiro: não foi executado por esta tarefa.
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
