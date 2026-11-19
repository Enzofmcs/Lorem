const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {resolve} = require('node:path');
const {after, before, test} = require('node:test');
const {assertFails, assertSucceeds, initializeTestEnvironment} = require('@firebase/rules-unit-testing');
const {collection, doc, getDoc, getDocs, serverTimestamp, setDoc, updateDoc, writeBatch} = require('firebase/firestore');

const uid = 'mock-test-owner';
const batchId = 'mock-' + 'a'.repeat(64);
const path = `loremMockImports/${uid}/batches/${batchId}`;
const handle = `${path}/handles/cf_mock_excel`;
const metadata = {mockDatasetId: batchId, mockIpsumCount: 200};
const attempt = {state: 'FINAL', handleKey: 'cf_mock_excel', ruleVersion: 'mock-excel-v1'};
let env;

function client(user, provider = 'google.com') {
  return env.authenticatedContext(user, {firebase: {sign_in_provider: provider}}).firestore();
}

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-lorem-local',
    firestore: {rules: readFileSync(resolve(__dirname, '../firestore.rules'), 'utf8')},
  });
});
after(async () => env?.cleanup());

test('a Google account can store a 200-Ipsum mock batch alongside its regular training account', async () => {
  const db = client(uid);
  const account = doc(db, `users/${uid}`);
  await assertSucceeds(setDoc(account, {schemaVersion: 1, createdAt: serverTimestamp()}));
  const batch = writeBatch(db);
  batch.set(doc(db, path), metadata);
  batch.set(doc(db, handle), {progressVersion: 200, loremRating: 1883});
  for (let number = 1; number <= 200; number++) {
    batch.set(doc(db, `${handle}/ipsums/${number}`), attempt);
    batch.set(doc(db, `${handle}/usedProblems/${900000 + number}_A`), {ipsumId: String(number)});
  }
  await assertSucceeds(batch.commit());
  assert.equal((await getDocs(collection(db, `${handle}/ipsums`))).size, 200);
  assert.deepEqual((await getDoc(doc(db, path))).data(), metadata);
  assert.deepEqual(Object.keys((await getDoc(account)).data()).sort(), ['createdAt', 'schemaVersion']);
  await assertFails(updateDoc(doc(db, path), {mockIpsumCount: 199}));
  await assertFails(setDoc(doc(db, `users/${uid}/handles/cf_mock_excel/ipsums/1`), attempt));
});

test('mock batches are inaccessible to other users, other providers and anonymous clients', async () => {
  const clients = [client('mock-test-other'), client(uid, 'password'), env.unauthenticatedContext().firestore()];
  for (const db of clients) {
    await assertFails(getDoc(doc(db, path)));
    await assertFails(setDoc(doc(db, `${handle}/ipsums/201`), attempt));
  }
  const malformedId = `loremMockImports/${uid}/batches/malformed`;
  await assertFails(setDoc(doc(client(uid), malformedId), {mockDatasetId: 'malformed', mockIpsumCount: 200}));
});
