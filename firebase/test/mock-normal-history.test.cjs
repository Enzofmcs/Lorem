const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {resolve} = require('node:path');
const {test, before, after} = require('node:test');
const {initializeTestEnvironment, assertFails, assertSucceeds} = require('@firebase/rules-unit-testing');
const {collection, doc, getDoc, getDocs, serverTimestamp, setDoc, Timestamp, writeBatch} = require('firebase/firestore');

const project = 'demo-lorem-local';
const uid = 'seed-normal-owner';
const batch = 'mock-' + 'c'.repeat(64);
const count = 200;
const user = `users/${uid}`;
const handle = `${user}/handles/cf_demo`;
const source = `loremMockImports/${uid}/batches/${batch}`;
const start = Date.UTC(2025, 0, 1);
let env, seedNormalHistory;

const google = uid => env.authenticatedContext(uid, {firebase: {sign_in_provider: 'google.com'}}).firestore();
const profile = handle => ({schemaVersion: 1, handle, handleKey: `cf_${handle.toLowerCase()}`,
  bindingVersion: 1, loremRating: 800, consolidatedRating: null, progressVersion: 0, createdAt: serverTimestamp()});

function mockAttempt(index) {
  const startedAt = start + index * 7_260_000;
  const accepted = index % 10 !== 0;
  const delta = accepted ? 10 : -8;
  const initialRating = 800 + index * 10 - Math.ceil(index / 10) * 18;
  const endedAt = Timestamp.fromMillis(startedAt + (accepted ? 60_000 : 7_200_000));
  const snapshot = {handle: 'mock_excel', initialRating, contestId: 900000 + index, index: 'A', name: `Ipsum ${index + 1}`,
    rating: 800, tags: ['implementation'], originAt: startedAt, originVerifiedAt: startedAt,
    originSource: 'mock:excel', originVersion: 'mock-excel-v1', category: 'CURRENT_LEVEL',
    desiredMin: 800, desiredMax: 800, fallback: 0, cfRating: null, cfObservedAt: null, factors: [], recentOverlap: 0};
  const failure = accepted ? null : 'TIME_EXPIRED';
  return {handleKey: 'cf_mock_excel', bindingVersion: 1, version: index, snapshot, state: 'FINAL',
    startedAt: Timestamp.fromMillis(startedAt), limitMillis: 7_200_000, ruleVersion: 'mock-excel-v1',
    hintAt: null, cutoffAt: accepted ? null : endedAt, closeReason: accepted ? null : 'TIME_LIMIT_EXCEEDED',
    failure, result: {endedAt, accepted, errors: 0, failure, delta, finalRating: initialRating + delta, expectedTime: 3_600_000},
    processedAt: endedAt, solvedOutsideAt: null};
}

before(async () => {
  env = await initializeTestEnvironment({projectId: project,
    firestore: {rules: readFileSync(resolve(__dirname, '../firestore.rules'), 'utf8')}});
  ({seedNormalHistory} = await import('../../tools/ipsum-mock/seed-normal-history.mjs'));
  const db = google(uid);
  await setDoc(doc(db, user), {schemaVersion: 1, createdAt: serverTimestamp()});
  await setDoc(doc(db, handle), profile('Demo'));
  await setDoc(doc(db, `${user}/handles/cf_other`), profile('Other'));
  await setDoc(doc(db, `${user}/runtime/selection`), {schemaVersion: 1, handleKey: 'cf_demo', bindingVersion: 1,
    selectionVersion: 1, selectedAt: serverTimestamp()});

  const writes = writeBatch(db);
  const sourceHandle = `${source}/handles/cf_mock_excel`;
  writes.set(doc(db, source), {mockDatasetId: batch, mockIpsumCount: count});
  writes.set(doc(db, sourceHandle), {progressVersion: count, loremRating: mockAttempt(count - 1).result.finalRating});
  for (let i = 0; i < count; i++) writes.set(doc(db, `${sourceHandle}/ipsums/${i + 1}`), mockAttempt(i));
  await writes.commit();
});
after(async () => env?.cleanup());

test('the normal account reads the 200 promoted Ipsums, progress and markers through the existing rules', async () => {
  const db = google(uid);
  const preview = await seedNormalHistory({project, uid});
  assert.equal(preview.handle, 'Demo');
  assert.equal(preview.count, count);
  assert.equal((await getDocs(collection(db, `${handle}/ipsums`))).size, 0);

  const loaded = await seedNormalHistory({project, uid, apply: true});
  assert.deepEqual(loaded, preview);
  const rows = (await assertSucceeds(getDocs(collection(db, `${handle}/ipsums`)))).docs
    .map(row => row.data()).sort((a, b) => a.version - b.version);
  assert.equal(rows.length, count);
  assert.equal(rows.filter(row => row.result.accepted).length, 180);
  assert.equal(rows[0].snapshot.initialRating, 800);
  assert.ok(rows.every((row, index) => row.snapshot.handle === 'Demo' && row.handleKey === 'cf_demo' &&
    row.version === index && row.bindingVersion === 1 && row.snapshot.originSource === 'mock:excel'));
  const stored = (await getDoc(doc(db, handle))).data();
  assert.equal(stored.loremRating, loaded.rating);
  assert.equal(stored.progressVersion, count);
  assert.equal(stored.lastIpsumId, `${batch}-${count}`);
  assert.equal((await getDocs(collection(db, `${handle}/usedProblems`))).size, count);
  assert.equal((await getDocs(collection(db, `${user}/handles/cf_other/ipsums`))).size, 0);
  assert.equal((await getDoc(doc(db, source))).exists(), true);
  await assertFails(getDocs(collection(google('seed-unrelated'), `${handle}/ipsums`)));

  assert.equal((await seedNormalHistory({project, uid, apply: true})).already, true);
  assert.equal((await getDocs(collection(db, `${handle}/ipsums`))).size, count);
});

test('an active account or a handle with existing training history blocks the load', async () => {
  const other = `${user}/handles/cf_other`;
  const lock = `${user}/runtime/current`;
  await env.withSecurityRulesDisabled(context => setDoc(doc(context.firestore(), lock), {handleKey: 'cf_demo', ipsumId: 'active'}));
  await assert.rejects(seedNormalHistory({project, uid, batch, handle: 'Other', apply: true}), /Ipsum ativo/);
  await env.withSecurityRulesDisabled(async context => {
    const {deleteDoc} = require('firebase/firestore');
    const db = context.firestore();
    await deleteDoc(doc(db, lock));
    await setDoc(doc(db, `${other}/ipsums/existing`), mockAttempt(0));
  });
  await assert.rejects(seedNormalHistory({project, uid, batch, handle: 'Other', apply: true}), /já tem histórico/);
  assert.equal((await getDocs(collection(google(uid), `${other}/ipsums`))).size, 1);
});
