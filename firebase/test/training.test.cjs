const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {resolve} = require('node:path');
const {test, before, after} = require('node:test');
const {initializeTestEnvironment, assertFails, assertSucceeds} = require('@firebase/rules-unit-testing');
const {doc, getDoc, setDoc, updateDoc, deleteDoc, writeBatch, runTransaction, serverTimestamp, Timestamp} = require('firebase/firestore');

let env;
let sequence = 0;
const ms = value => Timestamp.fromMillis(value);
const google = uid => env.authenticatedContext(uid, {firebase: {sign_in_provider: 'google.com'}}).firestore();
const path = (uid, key = 'cf_tourist') => `users/${uid}/handles/${key}`;
const lockPath = uid => `users/${uid}/runtime/current`;
const handleData = (key = 'cf_tourist') => ({schemaVersion: 1, handle: key.slice(3), handleKey: key, bindingVersion: 1, loremRating: 800, consolidatedRating: null, progressVersion: 0, createdAt: serverTimestamp()});
const snapshot = (key = 'cf_tourist', contestId = 2000) => ({handle: key.slice(3), initialRating: 800, contestId, index: 'A', name: 'Synthetic problem', rating: 800,
  tags: ['math'], originAt: 1593561600000, originVerifiedAt: 1800000000000, originSource: 'codeforces:contest.list',
  originVersion: 'origin-20200701-v1', category: 'CURRENT_LEVEL', desiredMin: 800, desiredMax: 800, fallback: 0,
  cfRating: 3500, cfObservedAt: 1800000000000, factors: [], recentOverlap: 0});
const attemptData = (key = 'cf_tourist', contestId = 2000) => ({handleKey: key, bindingVersion: 1, version: 0, snapshot: snapshot(key, contestId), state: 'ACTIVE',
  startedAt: serverTimestamp(), limitMillis: 7200000, ruleVersion: 'spark-2h-v1', hintAt: null, cutoffAt: null,
  closeReason: null, failure: null, result: null, processedAt: null, solvedOutsideAt: null});
before(async () => {
  env = await initializeTestEnvironment({projectId: 'demo-lorem-local', firestore: {rules: readFileSync(resolve(__dirname, '../firestore.rules'), 'utf8')}});
});
after(async () => env?.cleanup());
async function account() {
  const uid = `training-${++sequence}`;
  const db = google(uid);
  await setDoc(doc(db, `users/${uid}`), {schemaVersion: 1, createdAt: serverTimestamp()});
  return {uid, db};
}
async function start(c, key = 'cf_tourist', id = 'one', contestId = 2000, change = x => x) {
  await ensureSelection(c);
  const batch = writeBatch(c.db);
  batch.set(doc(c.db, `${path(c.uid, key)}/ipsums/${id}`), change(attemptData(key, contestId)));
  batch.set(doc(c.db, `${path(c.uid, key)}/usedProblems/${contestId}_A`), {ipsumId: id});
  batch.set(doc(c.db, lockPath(c.uid)), {handleKey: key, ipsumId: id, checkedAt: serverTimestamp()});
  return batch.commit();
}
async function ensureSelection(c) {
  const ref = doc(c.db, `users/${c.uid}/runtime/selection`);
  if (!(await getDoc(ref)).exists()) await setDoc(ref, {schemaVersion: 1, handleKey: 'cf_tourist',
    bindingVersion: 1, selectionVersion: 1, selectedAt: serverTimestamp()});
}
async function fixture(age = 7201000, cutoff = null, scoring = null) {
  const c = await account();
  const started = Date.now() - age;
  const data = {...attemptData(), startedAt: ms(started)};
  if (scoring) data.snapshot.scoring = scoring;
  if (cutoff != null) Object.assign(data, {state: 'CLOSED_AWAITING_VERIFICATION', cutoffAt: ms(started + cutoff),
    closeReason: 'GIVE_UP', failure: 'LOGIC_NOT_FOUND'});
  await env.withSecurityRulesDisabled(async context => {
    const db = context.firestore();
    await setDoc(doc(db, path(c.uid)), handleData());
    await setDoc(doc(db, `${path(c.uid)}/ipsums/one`), data);
    await setDoc(doc(db, `${path(c.uid)}/usedProblems/2000_A`), {ipsumId: 'one'});
    await setDoc(doc(db, lockPath(c.uid)), {handleKey: 'cf_tourist', ipsumId: 'one', checkedAt: serverTimestamp()});
  });
  return {...c, started, data};
}
async function finish(c, {accepted = false, ended = c.started + 7200000, delta = accepted ? 24 : -20, id = 'one', timeGain = null} = {}) {
  const old = (await getDoc(doc(c.db, `${path(c.uid)}/ipsums/${id}`))).data();
  const result = {endedAt: ms(ended), accepted, errors: 0, failure: accepted ? null : old.failure,
    delta, finalRating: Math.max(0, 800 + delta), expectedTime: 3600000};
  if (timeGain) result.timeGain = timeGain;
  const batch = writeBatch(c.db);
  batch.update(doc(c.db, `${path(c.uid)}/ipsums/${id}`), {state: 'FINAL', result, processedAt: serverTimestamp()});
  batch.update(doc(c.db, path(c.uid)), {loremRating: result.finalRating, progressVersion: 1, consolidatedRating: null, lastIpsumId: id});
  batch.delete(doc(c.db, lockPath(c.uid)));
  return batch.commit();
}

test('same canonical handle belongs independently to two Google uids, only creation may use 800', async () => {
  const a = await account(), b = await account();
  await assertSucceeds(setDoc(doc(a.db, path(a.uid)), handleData()));
  await assertSucceeds(setDoc(doc(b.db, path(b.uid)), handleData()));
  assert.equal((await getDoc(doc(a.db, path(a.uid)))).data().loremRating, 800);
  await assertFails(getDoc(doc(b.db, path(a.uid))));
  await assertFails(setDoc(doc(a.db, path(a.uid)), handleData()));
  for (const bad of [{...handleData(), loremRating: 3500}, {...handleData(), progressVersion: 1}, {...handleData(), role: 'admin'},
    {...handleData(), handleKey: 'other'}, {...handleData(), createdAt: ms(0)}]) {
    await assertFails(setDoc(doc(a.db, path(a.uid, 'other')), bad));
  }
  await assertFails(setDoc(doc(a.db, path(a.uid, 'Tourist')), {...handleData(), handleKey: 'Tourist'}));
});

test('start atomically creates server timestamp, immutable marker and uid lock', async () => {
  const c = await account();
  await setDoc(doc(c.db, path(c.uid)), handleData());
  await assertSucceeds(start(c));
  const active = (await getDoc(doc(c.db, `${path(c.uid)}/ipsums/one`))).data();
  assert.ok(active.startedAt instanceof Timestamp);
  assert.equal(active.limitMillis, 7200000);
  await assertFails(start(c, 'cf_tourist', 'two', 2001));
  await assertFails(updateDoc(doc(c.db, `${path(c.uid)}/ipsums/one`), {startedAt: serverTimestamp()}));
  await assertFails(updateDoc(doc(c.db, `${path(c.uid)}/usedProblems/2000_A`), {ipsumId: 'two'}));
  await assertFails(setDoc(doc(c.db, lockPath(c.uid)), {handleKey: null, ipsumId: null, checkedAt: serverTimestamp()}));
});

test('transactions from two devices and handles cannot acquire two active locks', async () => {
  const c = await account();
  for (const key of ['cf_tourist', 'cf_petr']) await setDoc(doc(c.db, path(c.uid, key)), handleData(key));
  await ensureSelection(c);
  const commands = ['cf_tourist', 'cf_petr'].map(key => {
    const db = google(c.uid);
    return runTransaction(db, async tx => {
    const lock = await tx.get(doc(db, lockPath(c.uid)));
    if (lock.exists() && lock.data().ipsumId != null) throw new Error('Already active');
    tx.set(doc(db, `${path(c.uid, key)}/ipsums/${key}`), attemptData(key));
    tx.set(doc(db, `${path(c.uid, key)}/usedProblems/2000_A`), {ipsumId: key});
    tx.set(doc(db, lockPath(c.uid)), {handleKey: key, ipsumId: key, checkedAt: serverTimestamp()});
    });
  });
  const results = await Promise.allSettled(commands);
  assert.equal(results.filter(x => x.status === 'fulfilled').length, 1);
});

test('start refuses missing atomic writes forged clock old origin rating or extra fields', async () => {
  const c = await account();
  await setDoc(doc(c.db, path(c.uid)), handleData());
  await assertFails(setDoc(doc(c.db, `${path(c.uid)}/ipsums/one`), attemptData()));
  for (const change of [x => ({...x, startedAt: ms(0)}), x => ({...x, limitMillis: 7200001}),
    x => ({...x, snapshot: {...x.snapshot, initialRating: 9999}}),
    x => ({...x, snapshot: {...x.snapshot, originAt: 1593561599999}}), x => ({...x, extra: 'forged'})]) {
    await assertFails(start(c, 'cf_tourist', 'one', 2000, change));
  }
});

test('hint is persisted once before expiry and cannot change snapshots', async () => {
  const c = await fixture(1000);
  const ref = doc(c.db, `${path(c.uid)}/ipsums/one`);
  await assertSucceeds(updateDoc(ref, {hintAt: serverTimestamp()}));
  await assertFails(updateDoc(ref, {hintAt: serverTimestamp()}));
  await assertFails(updateDoc(ref, {snapshot: {...c.data.snapshot, initialRating: 1}}));
  const expired = await fixture();
  await assertFails(updateDoc(doc(expired.db, `${path(expired.uid)}/ipsums/one`), {hintAt: serverTimestamp()}));
});

test('timeout persists absolute original boundary and late processing applies exactly one delta', async () => {
  const c = await fixture();
  const ref = doc(c.db, `${path(c.uid)}/ipsums/one`);
  await assertFails(finish(c));
  await assertFails(updateDoc(ref, {state: 'CLOSED_AWAITING_VERIFICATION', cutoffAt: serverTimestamp(), closeReason: 'TIME_LIMIT_EXCEEDED', failure: 'TIME_EXPIRED'}));
  await assertSucceeds(updateDoc(ref, {state: 'CLOSED_AWAITING_VERIFICATION', cutoffAt: ms(c.started + 7200000), closeReason: 'TIME_LIMIT_EXCEEDED', failure: 'TIME_EXPIRED'}));
  await assertFails(setDoc(doc(c.db, lockPath(c.uid)), {handleKey: null, ipsumId: null, checkedAt: serverTimestamp()}));
  await assertSucceeds(finish(c));
  assert.equal((await getDoc(doc(c.db, path(c.uid)))).data().loremRating, 780);
  const final = (await getDoc(ref)).data();
  assert.equal(final.result.endedAt.toMillis() - final.startedAt.toMillis(), 7200000);
  assert.ok(final.processedAt.toMillis() > final.result.endedAt.toMillis());
  await assertFails(finish(c));
  await assertFails(updateDoc(doc(c.db, path(c.uid)), {loremRating: 800}));
  await assertSucceeds(updateDoc(ref, {solvedOutsideAt: ms(Date.now() - 1)}));
  assert.equal((await getDoc(doc(c.db, path(c.uid)))).data().loremRating, 780);
});

test('late discovered AC exactly on deadline succeeds but after deadline cannot rewrite original rating', async () => {
  const c = await fixture();
  const ref = doc(c.db, `${path(c.uid)}/ipsums/one`);
  await updateDoc(ref, {state: 'CLOSED_AWAITING_VERIFICATION', cutoffAt: ms(c.started + 7200000), closeReason: 'TIME_LIMIT_EXCEEDED', failure: 'TIME_EXPIRED'});
  await assertFails(finish(c, {accepted: true, ended: c.started + 7200001}));
  await assertSucceeds(finish(c, {accepted: true}));
  assert.equal((await getDoc(doc(c.db, path(c.uid)))).data().loremRating, 824);
  await assertFails(updateDoc(ref, {result: {...(await getDoc(ref)).data().result, delta: 40}}));
});

test('manual close fixes earlier boundary so timeout or later AC cannot supersede it', async () => {
  const c = await fixture(1000);
  const ref = doc(c.db, `${path(c.uid)}/ipsums/one`);
  await assertSucceeds(updateDoc(ref, {state: 'CLOSED_AWAITING_VERIFICATION', cutoffAt: serverTimestamp(), closeReason: 'GIVE_UP', failure: 'LOGIC_NOT_FOUND'}));
  const cutoff = (await getDoc(ref)).data().cutoffAt.toMillis();
  await assertFails(updateDoc(ref, {cutoffAt: ms(c.started + 7200000), closeReason: 'TIME_LIMIT_EXCEEDED', failure: 'TIME_EXPIRED'}));
  await assertFails(finish(c, {accepted: true, ended: cutoff + 1}));
  await assertSucceeds(finish(c, {ended: cutoff}));
});

test('finalization must include matching rating version and lock release in same commit', async () => {
  const c = await fixture(1000, 500);
  const ref = doc(c.db, `${path(c.uid)}/ipsums/one`);
  const result = {accepted: false, endedAt: ms(c.started + 500), failure: 'LOGIC_NOT_FOUND', delta: -20, finalRating: 780, errors: 0, expectedTime: 3600000};
  await assertFails(updateDoc(ref, {state: 'FINAL', result, processedAt: serverTimestamp()}));
  await assertFails(updateDoc(doc(c.db, path(c.uid)), {loremRating: 780, progressVersion: 1, lastIpsumId: 'one'}));
  await assertSucceeds(finish(c, {ended: c.started + 500}));
  await assertFails(start(c, 'cf_tourist', 'two', 2000));
});

test('training account A never changes same handle rating history or consolidation in account B', async () => {
  const a = await fixture(1000, 500), b = await account();
  await setDoc(doc(b.db, path(b.uid)), handleData());
  const original = (await getDoc(doc(b.db, path(b.uid)))).data();
  await finish(a, {ended: a.started + 500});
  assert.equal((await getDoc(doc(a.db, path(a.uid)))).data().loremRating, 780);
  assert.deepEqual((await getDoc(doc(b.db, path(b.uid)))).data(), original);
  assert.equal((await getDoc(doc(b.db, `${path(b.uid)}/ipsums/one`))).exists(), false);
  await assertFails(getDoc(doc(b.db, `${path(a.uid)}/ipsums/one`)));
  await assertFails(getDoc(doc(b.db, `${path(a.uid)}/usedProblems/2000_A`)));
});

test('existing story35 selection survives clock confirmation and cannot switch atomically with a start', async () => {
  const c = await account();
  for (const key of ['cf_tourist', 'cf_petr']) await setDoc(doc(c.db, path(c.uid, key)), handleData(key));
  await ensureSelection(c);
  const selected = doc(c.db, `users/${c.uid}/runtime/selection`);
  const original = (await getDoc(selected)).data();
  const clock = doc(c.db, `users/${c.uid}/runtime/clock`);
  await assertSucceeds(setDoc(clock, {checkedAt: serverTimestamp()}));
  await assertSucceeds(setDoc(clock, {checkedAt: serverTimestamp()}));
  assert.equal((await getDoc(doc(c.db, lockPath(c.uid)))).exists(), false);
  assert.deepEqual((await getDoc(selected)).data(), original);
  await assertFails(setDoc(clock, {checkedAt: ms(0)}));
  await assertFails(setDoc(clock, {checkedAt: serverTimestamp(), handleKey: 'cf_petr'}));
  await assertFails(start(c, 'cf_petr', 'wrong-selection'));
  await assertFails(start(c, 'cf_tourist', 'bad-binding', 2000, a => ({...a, bindingVersion: 2})));
  const batch = writeBatch(c.db);
  batch.set(selected, {...original, handleKey: 'cf_petr', selectionVersion: 2, selectedAt: serverTimestamp()});
  batch.set(doc(c.db, `${path(c.uid, 'cf_petr')}/ipsums/atomic-selection`), attemptData('cf_petr'));
  batch.set(doc(c.db, `${path(c.uid, 'cf_petr')}/usedProblems/2000_A`), {ipsumId: 'atomic-selection'});
  batch.set(doc(c.db, lockPath(c.uid)), {handleKey: 'cf_petr', ipsumId: 'atomic-selection', checkedAt: serverTimestamp()});
  await assertFails(batch.commit());
  assert.deepEqual((await getDoc(selected)).data(), original);
  await assertSucceeds(start(c));
  await assertFails(deleteDoc(doc(c.db, lockPath(c.uid))));
});

test('legacy exclusions synchronize only public problem ids and are immutable across retries and accounts', async () => {
  const a = await account(), b = await account();
  for (const c of [a, b]) await setDoc(doc(c.db, path(c.uid)), handleData());
  const ref = doc(a.db, `${path(a.uid)}/exclusions/2000_A`);
  const data = {contestId: 2000, index: 'A', source: 'legacy-device-v1'};
  await assertSucceeds(setDoc(ref, data)); await assertSucceeds(setDoc(ref, data));
  await assertFails(updateDoc(ref, {index: 'B'})); await assertFails(deleteDoc(ref));
  await assertFails(getDoc(doc(b.db, ref.path)));
  await assertFails(setDoc(doc(a.db, `${path(a.uid)}/exclusions/2001_A`), {...data, uid: a.uid}));
  await assertFails(setDoc(doc(a.db, `${path(a.uid)}/exclusions/9999_A`), data));
  await assertFails(setDoc(doc(a.db, `${path(a.uid, 'cf_missing')}/exclusions/2000_A`), data));
  await assertFails(start(a));
  await assertSucceeds(start(b));
  assert.equal((await getDoc(doc(a.db, path(a.uid)))).data().loremRating, 800);
});

test('an atomic exclusion cannot be combined with a new attempt on the excluded problem', async () => {
  const c = await account(); await setDoc(doc(c.db, path(c.uid)), handleData()); await ensureSelection(c);
  const batch = writeBatch(c.db);
  batch.set(doc(c.db, `${path(c.uid)}/exclusions/2000_A`), {contestId: 2000, index: 'A', source: 'legacy-device-v1'});
  batch.set(doc(c.db, `${path(c.uid)}/ipsums/one`), attemptData());
  batch.set(doc(c.db, `${path(c.uid)}/usedProblems/2000_A`), {ipsumId: 'one'});
  batch.set(doc(c.db, lockPath(c.uid)), {handleKey: 'cf_tourist', ipsumId: 'one', checkedAt: serverTimestamp()});
  await assertFails(batch.commit());
});


const scoring = () => ({policyVersion: 'time-curve-v1', limits: {t1: 1800000, t2: 3600000, t3: 7200000},
  bonusMultiplier: 1.25, reductionMultiplier: 0.5, modelVersion: 'synthetic-test-only', artifactSha256: 'a'.repeat(64),
  ratingSource: 'codeforces_prior', inputRating: 3500, unavailableReason: null});
const gainDetails = (band, factor, timed) => ({policyVersion: 'time-curve-v1', band, baseGain: 40,
  multiplier: factor, timedGain: timed, hintDeduction: 0, gain: timed});

test('new starts persist fallback or ordered curve policy and reject malformed scale bounds or version', async () => {
  for (const policy of [scoring(), {...scoring(), policyVersion: 'D-012', limits: null, unavailableReason: 'activation_disabled'}]) {
    const c = await account();
    await setDoc(doc(c.db, path(c.uid)), handleData());
    await assertSucceeds(start(c, 'cf_tourist', 'one', 2000, x => ({...x, snapshot: {...x.snapshot, cfObservedAt: Date.now() - 1000, scoring: policy}})));
    await assertFails(updateDoc(doc(c.db, `${path(c.uid)}/ipsums/one`), {'snapshot.scoring': {...policy, limits: {t1: 1, t2: 2, t3: 3}}}));
  }
  for (const policy of [{...scoring(), ratingSource: 'lorem'}, {...scoring(), inputRating: 800},
    {...scoring(), policyVersion: 'future'}, {...scoring(), limits: {t1: 1, t2: 1, t3: 2}},
    {...scoring(), limits: {t1: 1, t2: 2, t3: 7200001}}, {...scoring(), bonusMultiplier: 99}]) {
    const c = await account();
    await setDoc(doc(c.db, path(c.uid)), handleData());
    await assertFails(start(c, 'cf_tourist', 'one', 2000, x => ({...x, snapshot: {...x.snapshot, cfObservedAt: Date.now() - 1000, scoring: policy}})));
  }
});

test('curve accepts bonus 50 and zero at T3 while legacy and fallback still reject zero', async () => {
  for (const [elapsed, details] of [[1799999, gainDetails('BONUS', 1.25, 50)],
    [1800000, gainDetails('NORMAL', 1, 40)], [3600000, gainDetails('REDUCED', 0.5, 20)],
    [7200000, gainDetails('ZERO', 0, 0)]]) {
    const c = await fixture(7201000, null, scoring());
    await assertFails(finish(c, {accepted: true, ended: c.started + elapsed, delta: details.gain}));
    await assertSucceeds(finish(c, {accepted: true, ended: c.started + elapsed, delta: details.gain, timeGain: details}));
    await assertFails(finish(c, {accepted: true, ended: c.started + elapsed, delta: details.gain, timeGain: details}));
  }
  for (const policy of [null, {...scoring(), policyVersion: 'D-012', limits: null, unavailableReason: 'activation_disabled'}]) {
    const c = await fixture(7201000, null, policy);
    await assertFails(finish(c, {accepted: true, delta: 0}));
    await assertFails(finish(c, {accepted: true, delta: 0, timeGain: gainDetails('ZERO', 0, 0)}));
  }
});

test('curve rejects inconsistent breakdown and retains failure and upsolve unchanged', async () => {
  const c = await fixture(7201000, null, scoring());
  await assertFails(finish(c, {accepted: true, delta: 20, timeGain: gainDetails('REDUCED', 0.5, 20)})); // actually T3
  const ref = doc(c.db, `${path(c.uid)}/ipsums/one`);
  await updateDoc(ref, {state: 'CLOSED_AWAITING_VERIFICATION', cutoffAt: ms(c.started + 7200000), closeReason: 'TIME_LIMIT_EXCEEDED', failure: 'TIME_EXPIRED'});
  await assertSucceeds(finish(c));
  const original = (await getDoc(ref)).data().result;
  await assertSucceeds(updateDoc(ref, {solvedOutsideAt: ms(Date.now() - 1)}));
  assert.deepEqual((await getDoc(ref)).data().result, original);
});


test('curve bands use the same epoch milliseconds as Android with a submillisecond server start', async () => {
  for (const [elapsed, details] of [[1800000, gainDetails('NORMAL', 1, 40)],
    [3600000, gainDetails('REDUCED', 0.5, 20)], [7200000, gainDetails('ZERO', 0, 0)]]) {
    const c = await fixture(7201000, null, scoring());
    const original = ms(c.started);
    await env.withSecurityRulesDisabled(async context => {
      await updateDoc(doc(context.firestore(), `${path(c.uid)}/ipsums/one`), {
        startedAt: new Timestamp(original.seconds, original.nanoseconds + 500000)});
    });
    await assertSucceeds(finish(c, {accepted: true, ended: c.started + elapsed, delta: details.gain, timeGain: details}));
  }
});
