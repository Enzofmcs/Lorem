import {execFileSync} from 'node:child_process';
import {pathToFileURL} from 'node:url';
import {parseArgs} from 'node:util';

export const MULTIPLE_MOCK_BATCHES = 'MULTIPLE_MOCK_BATCHES';
const count = 200;
const batchPattern = /^mock-[a-f0-9]{64}$/;
const handlePattern = /^[A-Za-z0-9_.-]{1,64}$/;
const string = value => ({stringValue: value});
const integer = value => ({integerValue: String(value)});
const isNull = field => field == null || Object.hasOwn(field, 'nullValue');
const asInteger = field => field?.integerValue == null ? NaN : Number(field.integerValue);
const asTimestamp = field => Date.parse(field?.timestampValue ?? '');
const fieldNames = [
  'handleKey', 'bindingVersion', 'version', 'snapshot', 'state', 'startedAt', 'limitMillis', 'ruleVersion',
  'hintAt', 'cutoffAt', 'closeReason', 'failure', 'result', 'processedAt', 'solvedOutsideAt',
];
const snapshotNames = [
  'handle', 'initialRating', 'contestId', 'index', 'name', 'rating', 'tags', 'originAt', 'originVerifiedAt',
  'originSource', 'originVersion', 'category', 'desiredMin', 'desiredMax', 'fallback', 'cfRating',
  'cfObservedAt', 'factors', 'recentOverlap',
];
const resultNames = ['endedAt', 'accepted', 'errors', 'failure', 'delta', 'finalRating', 'expectedTime'];
const hasFields = (object, names) => object && Object.keys(object).length === names.length
  && names.every(name => Object.hasOwn(object, name));

function check(condition, message) {
  if (!condition) throw new Error(message);
}

function firestore(project, emulatorHost, remoteRequest) {
  check(/^[a-z][a-z0-9-]{4,29}$/.test(project ?? ''), 'Informe o ID do projeto Firebase em --project.');
  if (emulatorHost) {
    check(project.startsWith('demo-') && /^(localhost|127\.0\.0\.1):[0-9]+$/.test(emulatorHost),
      'O emulador local exige um projeto demo- e um endereço de loopback.');
  }
  const database = `projects/${project}/databases/(default)/documents`;
  const origin = emulatorHost ? `http://${emulatorHost}` : 'https://firestore.googleapis.com';
  const token = emulatorHost ? 'owner' : remoteRequest ? null :
    execFileSync('gcloud', ['auth', 'print-access-token', '--quiet'],
      {encoding: 'utf8', stdio: ['ignore', 'pipe', 'inherit']}).trim();

  async function request(path, body) {
    if (!emulatorHost && remoteRequest) return remoteRequest(`/v1/${database}${path}`, body);
    const response = await fetch(`${origin}/v1/${database}${path}`, {
      method: 'POST',
      headers: {'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json'},
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(40_000),
    });
    const payload = await response.json();
    check(response.ok, `Firestore: ${payload.error?.message ?? response.statusText}`);
    return payload;
  }

  const name = path => `${database}/${path}`;
  return {
    name,
    request,
    async get(paths, transaction) {
      const rows = await request(':batchGet', {documents: paths.map(name), transaction});
      const found = new Map(rows.filter(row => row.found).map(row => [row.found.name, row.found]));
      return paths.map(path => found.get(name(path)));
    },
    async list(parent, collectionId, limit, transaction) {
      const rows = await request(`/${parent}:runQuery`, {
        structuredQuery: {from: [{collectionId}], limit}, transaction,
      });
      return rows.flatMap(row => row.document ? [row.document] : []);
    },
  };
}

function readRows(docs, batchId) {
  check(docs.length === count, `O lote ${batchId} precisa conter exatamente ${count} Ipsums.`);
  const sorted = docs.slice().sort((a, b) => asInteger(a.fields?.version) - asInteger(b.fields?.version));
  const problems = new Set();
  let rating = 800;
  let previousEnd = -Infinity;
  return sorted.map((doc, index) => {
    const fields = doc.fields ?? {};
    const snapshot = fields.snapshot?.mapValue?.fields;
    const result = fields.result?.mapValue?.fields;
    const started = asTimestamp(fields.startedAt);
    const ended = asTimestamp(result?.endedAt);
    const delta = asInteger(result?.delta);
    const finalRating = asInteger(result?.finalRating);
    const contest = asInteger(snapshot?.contestId);
    const problemIndex = snapshot?.index?.stringValue ?? '';
    const accepted = result?.accepted?.booleanValue;
    const problemRating = asInteger(snapshot?.rating);
    const tags = snapshot?.tags?.arrayValue?.values;
    const errors = asInteger(result?.errors);
    const expected = asInteger(result?.expectedTime);
    check(hasFields(fields, fieldNames) && hasFields(snapshot, snapshotNames) && hasFields(result, resultNames) &&
      asInteger(fields.version) === index && asInteger(fields.bindingVersion) === 1 && fields.state?.stringValue === 'FINAL' &&
      asInteger(fields.limitMillis) === 7_200_000 &&
      fields.handleKey?.stringValue === 'cf_mock_excel' && fields.ruleVersion?.stringValue === 'mock-excel-v1' &&
      snapshot.handle?.stringValue === 'mock_excel' &&
      snapshot?.originSource?.stringValue === 'mock:excel' && asInteger(snapshot?.initialRating) === rating &&
      Number.isInteger(contest) && contest > 0 && /^[A-Z][A-Z0-9]{0,9}$/.test(problemIndex) &&
      typeof snapshot.name?.stringValue === 'string' && snapshot.name.stringValue.length > 0 &&
      Number.isInteger(problemRating) && problemRating % 100 === 0 && problemRating >= 800 && problemRating <= 3500 &&
      Array.isArray(tags) && tags.length > 0 && tags.every(tag => typeof tag.stringValue === 'string') &&
      Number.isSafeInteger(asInteger(snapshot.originAt)) && Number.isSafeInteger(asInteger(snapshot.originVerifiedAt)) &&
      snapshot.originVersion?.stringValue === 'mock-excel-v1' && snapshot.category?.stringValue === 'CURRENT_LEVEL' &&
      asInteger(snapshot.desiredMin) === problemRating && asInteger(snapshot.desiredMax) === problemRating &&
      asInteger(snapshot.fallback) === 0 && asInteger(snapshot.recentOverlap) === 0 &&
      snapshot.factors?.arrayValue && (snapshot.factors.arrayValue.values ?? []).length === 0 &&
      isNull(snapshot.cfRating) && isNull(snapshot.cfObservedAt) &&
      started >= previousEnd && ended > started && ended - started <= 7_200_000 && asTimestamp(fields.processedAt) === ended &&
      Number.isInteger(errors) && errors >= 0 && Number.isInteger(expected) && expected >= 1_800_000 && expected <= 7_200_000 &&
      (isNull(fields.hintAt) || (asTimestamp(fields.hintAt) > started && asTimestamp(fields.hintAt) < ended)) &&
      (isNull(fields.solvedOutsideAt) || (!accepted && asTimestamp(fields.solvedOutsideAt) > ended)) &&
      (accepted ? isNull(result.failure) && isNull(fields.failure) && isNull(fields.cutoffAt) && isNull(fields.closeReason)
        : ['GIVE_UP', 'TIME_LIMIT_EXCEEDED'].includes(fields.closeReason?.stringValue) &&
          ['LOGIC_NOT_FOUND', 'TIME_EXPIRED'].includes(result.failure?.stringValue) &&
          fields.failure?.stringValue === result.failure?.stringValue && asTimestamp(fields.cutoffAt) === ended) &&
      typeof accepted === 'boolean' && Number.isInteger(delta) &&
      (accepted ? delta >= 1 && delta <= 40 : delta >= -32 && delta < 0) &&
      finalRating === Math.max(0, rating + delta), `Ipsum inválido na posição ${index + 1} do lote.`);
    const problem = `${contest}_${problemIndex}`;
    check(!problems.has(problem), `Problema repetido no lote: ${problem}.`);
    problems.add(problem);
    rating = finalRating;
    previousEnd = ended;
    return {fields, snapshot, problem, finalRating};
  });
}

export async function seedNormalHistory({project, uid, batch, handle, apply = false},
    emulatorHost = process.env.FIRESTORE_EMULATOR_HOST, remoteRequest) {
  check(/^[A-Za-z0-9_-]{1,128}$/.test(uid ?? ''), 'Informe o UID da conta Google de teste em --uid.');
  check(!batch || batchPattern.test(batch), 'Em --batch, informe o ID completo que começa por mock-.');
  check(!handle || handlePattern.test(handle), 'Em --handle, informe o handle Codeforces já cadastrado.');
  const db = firestore(project, emulatorHost, remoteRequest);
  const user = `users/${uid}`;
  const sourceParent = `loremMockImports/${uid}`;
  const {transaction} = await db.request(':beginTransaction', {});
  let committed = false;
  try {
    const [account, selection, active] = await db.get([user, `${user}/runtime/selection`, `${user}/runtime/current`], transaction);
    check(account, 'Entre no Lorem normal com a conta de teste e cadastre um handle antes de carregar o lote.');
    const key = handle ? `cf_${handle.toLowerCase()}` : selection?.fields?.handleKey?.stringValue;
    check(/^cf_[a-z0-9_.-]{1,64}$/.test(key ?? ''), 'Selecione um handle no Lorem normal ou use --handle.');
    const target = `${user}/handles/${key}`;

    if (!batch) {
      const batches = await db.list(sourceParent, 'batches', 2, transaction);
      check(batches.length !== 0, 'Nenhum lote do Lorem Mock foi encontrado neste projeto para o UID informado.');
      if (batches.length > 1) {
        const error = new Error('Essa conta tem mais de um lote. Informe --batch com o ID exibido no Lorem Mock.');
        error.code = MULTIPLE_MOCK_BATCHES;
        throw error;
      }
      batch = batches[0].name.split('/').at(-1);
      check(batchPattern.test(batch), 'O ID do lote salvo está inválido.');
    }

    const source = `${sourceParent}/batches/${batch}`;
    const sourceHandle = `${source}/handles/cf_mock_excel`;
    const firstId = `${batch}-1`;
    const lastId = `${batch}-${count}`;
    const [profile, metadata, mockProfile, first, last] = await db.get([
      target, source, sourceHandle, `${target}/ipsums/${firstId}`, `${target}/ipsums/${lastId}`,
    ], transaction);
    const fields = profile?.fields;
    const ownerHandle = fields?.handle?.stringValue;
    const binding = asInteger(fields?.bindingVersion);
    check(handlePattern.test(ownerHandle ?? '') && fields?.handleKey?.stringValue === key &&
      `cf_${ownerHandle.toLowerCase()}` === key && Number.isSafeInteger(binding) && binding >= 1,
    'O handle de teste precisa estar cadastrado no Lorem desse UID.');

    if (first || last) {
      check(first?.fields?.ruleVersion?.stringValue === 'mock-excel-v1' &&
        last?.fields?.ruleVersion?.stringValue === 'mock-excel-v1' &&
        asInteger(first.fields.bindingVersion) === binding && asInteger(last.fields.bindingVersion) === binding &&
        asInteger(first.fields.version) === 0 && asInteger(last.fields.version) === count - 1,
      'Foram encontrados documentos do lote inconsistentes no handle. A carga foi interrompida.');
      return {handle: ownerHandle, batch, count, rating: asInteger(last.fields.result.mapValue.fields.finalRating), already: true};
    }

    check(!active, 'Encerre o Ipsum ativo da conta de teste antes da carga.');
    check(asInteger(fields.progressVersion) === 0 && asInteger(fields.loremRating) === 800 &&
      isNull(fields.consolidatedRating) && isNull(fields.lastIpsumId) &&
      (isNull(fields.duelRatingMicros) || asInteger(fields.duelRatingMicros) === 800_000_000),
    'O handle já tem progresso. Selecione um handle de teste vazio.');
    check((await db.list(target, 'ipsums', 1, transaction)).length === 0 &&
      (await db.list(target, 'usedProblems', 1, transaction)).length === 0,
    'O handle já tem histórico de treino. Selecione um handle de teste vazio.');
    check(metadata?.fields?.mockDatasetId?.stringValue === batch &&
      asInteger(metadata.fields.mockIpsumCount) === count && asInteger(mockProfile?.fields?.progressVersion) === count,
    'O lote não foi encontrado para este UID ou está incompleto.');

    const rows = readRows(await db.list(sourceHandle, 'ipsums', count + 1, transaction), batch);
    const rating = rows.at(-1).finalRating;
    check(asInteger(mockProfile.fields.loremRating) === rating, 'O rating final do lote está inconsistente.');
    const summary = {handle: ownerHandle, batch, count, rating, already: false};
    if (!apply) return summary;

    const create = (path, fields) => ({
      update: {name: db.name(path), fields}, currentDocument: {exists: false},
    });
    const writes = rows.flatMap((row, index) => {
      const id = `${batch}-${index + 1}`;
      return [
        create(`${target}/ipsums/${id}`, {
          ...row.fields, handleKey: string(key), bindingVersion: integer(binding),
          snapshot: {mapValue: {fields: {...row.snapshot, handle: string(ownerHandle)}}},
        }),
        create(`${target}/usedProblems/${row.problem}`, {ipsumId: string(id)}),
      ];
    });
    writes.push({
      update: {name: profile.name, fields: {progressVersion: integer(count), loremRating: integer(rating), lastIpsumId: string(lastId)}},
      updateMask: {fieldPaths: ['progressVersion', 'loremRating', 'lastIpsumId']},
      currentDocument: {updateTime: profile.updateTime},
    });
    await db.request(':commit', {transaction, writes});
    committed = true;
    return summary;
  } finally {
    if (!committed) await db.request(':rollback', {transaction}).catch(() => {});
  }
}

async function main() {
  const {values} = parseArgs({options: {
    project: {type: 'string'}, uid: {type: 'string'}, batch: {type: 'string'},
    handle: {type: 'string'}, apply: {type: 'boolean'}, help: {type: 'boolean'},
  }});
  if (values.help) {
    console.log('node tools/ipsum-mock/seed-normal-history.mjs --project ID --uid UID [--handle HANDLE] [--batch ID_DO_LOTE] [--apply]');
    return;
  }
  const result = await seedNormalHistory(values);
  const target = `${result.count} Ipsums · @${result.handle} · Rating Lorem ${result.rating}`;
  if (result.already) console.log(`Lote já carregado. ${target}.`);
  else if (values.apply) console.log(`Carga concluída no projeto ${values.project}. ${target}.`);
  else console.log(`Prévia no projeto ${values.project}. ${target}. Acrescente --apply para gravar.`);
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  main().catch(error => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
