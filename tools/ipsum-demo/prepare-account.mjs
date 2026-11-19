import {readFile} from 'node:fs/promises';
import {isDeepStrictEqual, parseArgs} from 'node:util';
import {pathToFileURL} from 'node:url';
import {gunzipSync} from 'node:zlib';
import {firestore} from '../ipsum-mock/seed-normal-history.mjs';

const handle = 'lerolerolero123491432';
const key = `cf_${handle}`;
const count = 200;
const firstDolor = 800_000_000;
const progressFields = ['progressVersion', 'loremRating', 'duelRatingMicros', 'consolidatedRating', 'lastIpsumId'];

function check(condition, message) {
  if (!condition) throw new Error(message);
}

function encode(value) {
  if (value == null) return {nullValue: null};
  if (value instanceof Date) return {timestampValue: value.toISOString()};
  if (typeof value === 'boolean') return {booleanValue: value};
  if (typeof value === 'number') return Number.isSafeInteger(value)
    ? {integerValue: String(value)} : {doubleValue: value};
  if (typeof value === 'string') return {stringValue: value};
  if (Array.isArray(value)) return {arrayValue: {values: value.map(encode)}};
  return {mapValue: {fields: fields(value)}};
}

function fields(value) {
  return Object.fromEntries(Object.entries(value).map(([name, entry]) => [name, encode(entry)]));
}

function decode(value) {
  if (Object.hasOwn(value, 'nullValue')) return null;
  if (value.timestampValue) return new Date(value.timestampValue);
  if (value.mapValue) return data(value.mapValue);
  if (value.arrayValue) return (value.arrayValue.values ?? []).map(decode);
  if (value.integerValue != null) return Number(value.integerValue);
  return value.stringValue ?? value.booleanValue ?? value.doubleValue;
}

function data(doc) {
  return Object.fromEntries(Object.entries(doc?.fields ?? {}).map(([name, value]) => [name, decode(value)]));
}

const docId = document => document.name.split('/').at(-1);

function baseline(dataset, binding) {
  const {rows, source, rating, dolorMicros, consolidated = null} = dataset;
  const id = dataset.dataset;
  check(/^tcc-v1-[0-9a-f]{64}$/.test(id) && id === `tcc-v1-${source?.sha256}` &&
    source?.ability === 1000 && rows?.length === count, 'A carga precisa ser a saída do replay de 200 Ipsums.');
  const ipsums = new Map();
  const markers = new Map();
  let lorem = 800;
  let dolor = firstDolor;
  let previousEnd = 0;
  for (const [version, row] of rows.entries()) {
    const startedAt = new Date(row.startedAt);
    const endedAt = new Date(row.result.endedAt);
    const {snapshot, result, closeReason, problemKey} = row;
    const duel = result.virtualDuel;
    check(row.version === version && row.id === `${id}-${version + 1}` &&
      problemKey === `${snapshot.contestId}_${snapshot.index}` && /^[1-9][0-9]*_[A-Za-z0-9]+$/.test(problemKey) &&
      problemKey !== '4_A' && !markers.has(problemKey) &&
      snapshot.handle === handle && snapshot.initialRating === lorem &&
      Number.isSafeInteger(result.finalRating) && result.finalRating === Math.max(0, lorem + result.delta) &&
      snapshot.virtualDuel?.ratingMicros === dolor && duel?.beforeMicros === dolor &&
      Number.isSafeInteger(duel.afterMicros) && duel.afterMicros === dolor + duel.deltaMicros &&
      startedAt.getTime() >= previousEnd && endedAt > startedAt && endedAt - startedAt <= 7_200_000,
    `Ipsum ${version + 1} inválido no replay.`);
    ipsums.set(row.id, {
      handleKey: key, bindingVersion: binding, version, snapshot, state: 'FINAL',
      startedAt, limitMillis: 7_200_000, ruleVersion: 'tcc-import-v1', hintAt: null,
      cutoffAt: result.accepted ? null : endedAt, closeReason: closeReason ?? null,
      failure: result.failure ?? null, result: {...result, endedAt}, processedAt: endedAt, solvedOutsideAt: null,
    });
    markers.set(problemKey, {ipsumId: row.id});
    lorem = result.finalRating;
    dolor = duel.afterMicros;
    previousEnd = endedAt.getTime();
  }
  check(rating === lorem && dolorMicros === dolor, 'Os saldos finais não conferem com o último Ipsum.');
  return {id, ipsums, markers, progress: {progressVersion: count, loremRating: lorem,
    duelRatingMicros: dolor, consolidatedRating: consolidated, lastIpsumId: `${id}-${count}`}};
}

export async function prepareAccount({project, uid, dataset, apply = false, restore = false},
    emulatorHost = process.env.FIRESTORE_EMULATOR_HOST) {
  check(/^[A-Za-z0-9_-]{1,128}$/.test(uid ?? ''), 'Informe --uid com o UID da conta Google de demonstração.');
  const db = firestore(project, emulatorHost);
  const user = `users/${uid}`;
  const selectionPath = `${user}/runtime/selection`;
  const activePath = `${user}/runtime/current`;
  const target = `${user}/handles/${key}`;
  const {transaction} = await db.request(':beginTransaction', {});
  let committed = false;
  try {
    const [account, selectionDoc, activeDoc, profile] = await db.get([user, selectionPath, activePath, target], transaction);
    const selected = data(selectionDoc);
    const state = data(profile);
    const binding = state.bindingVersion;
    check(account && profile && state.handleKey === key && state.handle?.toLowerCase() === handle &&
      selected.handleKey === key && selected.bindingVersion === binding && Number.isSafeInteger(binding) && binding >= 1,
    `Entre no Lorem normal com o UID informado e selecione @${handle}.`);
    const expected = baseline(dataset, binding);
    // Retain the exact Codeforces capitalization used by the binding.
    for (const attempt of expected.ipsums.values()) attempt.snapshot = {...attempt.snapshot, handle: state.handle};
    const [ipsums, markers] = await Promise.all([
      db.list(target, 'ipsums', count + 2, transaction),
      db.list(target, 'usedProblems', count + 2, transaction),
    ]);

    const create = (path, body) => ({update: {name: db.name(path), fields: fields(body)}, currentDocument: {exists: false}});
    const remove = doc => ({delete: doc.name, currentDocument: {updateTime: doc.updateTime}});
    const updateProfile = body => ({
      update: {name: profile.name, fields: fields(body)},
      updateMask: {fieldPaths: Object.keys(body)}, currentDocument: {updateTime: profile.updateTime},
    });
    const summary = {handle: state.handle, dataset: expected.id, ...expected.progress};
    let writes;

    if (state.demoDataset == null) {
      check(!restore, 'Este perfil ainda não foi preparado com a amostra.');
      check(!activeDoc && state.progressVersion === 0 && state.loremRating === 800 &&
        (state.duelRatingMicros == null || state.duelRatingMicros === firstDolor) &&
        state.consolidatedRating == null && state.lastIpsumId == null && ipsums.length === 0 && markers.length === 0,
      'Encontrado progresso preexistente ou Ipsum ativo. Nenhum dado foi gravado.');
      writes = [
        ...Array.from(expected.ipsums, ([id, body]) => create(`${target}/ipsums/${id}`, body)),
        ...Array.from(expected.markers, ([id, body]) => create(`${target}/usedProblems/${id}`, body)),
        updateProfile({...expected.progress, demoDataset: expected.id}),
      ];
      summary.status = apply ? 'preparado' : 'prévia da preparação';
    } else {
      check(state.demoDataset === expected.id, 'O perfil foi preparado com outra amostra.');
      const samples = ipsums.filter(doc => expected.ipsums.has(docId(doc)));
      const sampleMarkers = markers.filter(doc => expected.markers.has(docId(doc)));
      check(samples.length === count && sampleMarkers.length === count &&
        samples.every(doc => isDeepStrictEqual(data(doc), expected.ipsums.get(docId(doc)))) &&
        sampleMarkers.every(doc => isDeepStrictEqual(data(doc), expected.markers.get(docId(doc)))),
      'O histórico de demonstração diverge do replay. Nenhum dado foi gravado.');
      const active = data(activeDoc);
      const extra = ipsums.filter(doc => !expected.ipsums.has(docId(doc)));
      const extraMarkers = markers.filter(doc => !expected.markers.has(docId(doc)));
      const atBaseline = progressFields.every(name => state[name] === expected.progress[name]);

      if (atBaseline && extra.length === 0 && extraMarkers.length === 0 && !activeDoc) {
        return {...summary, status: 'já está no Ipsum 200'};
      }
      check(restore, 'Há atividade após a carga. Use --restore para conferir a restauração ao Ipsum 200.');
      check(extra.length === 1 && extraMarkers.length === 1, 'Há progresso além do ensaio Watermelon. A restauração foi interrompida.');
      const [liveDoc] = extra;
      const [marker] = extraMarkers;
      const live = data(liveDoc);
      const liveId = docId(liveDoc);
      check(live.handleKey === key && live.bindingVersion === binding && live.version === count &&
        live.ruleVersion === 'spark-2h-v1' && live.snapshot?.contestId === 4 && live.snapshot?.index === 'A' &&
        live.snapshot.initialRating === expected.progress.loremRating &&
        live.snapshot.virtualDuel?.ratingMicros === expected.progress.duelRatingMicros &&
        docId(marker) === '4_A' && data(marker).ipsumId === liveId,
      'O treino extra não corresponde ao Ipsum 201 de demonstração.');
      const activeRehearsal = atBaseline && active.handleKey === key && active.ipsumId === liveId &&
        ['ACTIVE', 'CLOSED_AWAITING_VERIFICATION'].includes(live.state);
      const finalizedRehearsal = !activeDoc && state.progressVersion === count + 1 && state.lastIpsumId === liveId &&
        live.state === 'FINAL' && state.loremRating === live.result?.finalRating &&
        state.duelRatingMicros === live.result?.virtualDuel?.afterMicros;
      check(activeRehearsal || finalizedRehearsal, 'O estado do ensaio mudou. Nenhum dado foi gravado.');
      writes = [remove(liveDoc), remove(marker), updateProfile(expected.progress)];
      if (activeRehearsal) writes.push(remove(activeDoc));
      summary.status = apply ? 'restaurado ao Ipsum 200' : 'prévia da restauração ao Ipsum 200';
    }
    if (apply) {
      await db.request(':commit', {transaction, writes});
      committed = true;
    }
    return summary;
  } finally {
    if (!committed) await db.request(':rollback', {transaction}).catch(() => {});
  }
}

async function main() {
  const {values} = parseArgs({options: {
    project: {type: 'string'}, uid: {type: 'string'}, baseline: {type: 'string'},
    apply: {type: 'boolean'}, restore: {type: 'boolean'}, help: {type: 'boolean'},
  }});
  if (values.help) {
    console.log('node tools/ipsum-demo/prepare-account.mjs --project PROJETO --uid UID [--baseline baseline.json] [--restore] [--apply]');
    return;
  }
  const payload = values.baseline
    ? await readFile(values.baseline)
    : gunzipSync(await readFile(new URL('rodada-05-baseline.json.gz', import.meta.url)));
  const dataset = JSON.parse(payload.toString('utf8'));
  console.log(JSON.stringify(await prepareAccount({...values, dataset}), null, 2));
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  main().catch(error => { console.error(error.message); process.exitCode = 1; });
}
