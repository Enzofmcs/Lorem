const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {resolve} = require('node:path');
const {test, before, after} = require('node:test');
const {initializeTestEnvironment, assertFails, assertSucceeds} = require('@firebase/rules-unit-testing');
const {doc, collection, getDoc, getDocs, setDoc, updateDoc, deleteDoc, serverTimestamp, Timestamp, writeBatch} = require('firebase/firestore');

let env;
const google = uid => env.authenticatedContext(uid, {firebase: {sign_in_provider: 'google.com'}}).firestore();
const metadata = () => ({schemaVersion: 1, createdAt: serverTimestamp()});
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-lorem-local',
    firestore: {rules: readFileSync(resolve(__dirname, '../firestore.rules'), 'utf8')},
  });
  await env.withSecurityRulesDisabled(async context => {
    const db = context.firestore();
    for (const uid of ['rules-a', 'rules-b']) {
      await setDoc(doc(db, `users/${uid}`), metadata());
      await setDoc(doc(db, `users/${uid}/handles/tourist`), {loremRating: 800});
      await setDoc(doc(db, `users/${uid}/handles/tourist/ipsums/one`), {status: 'ACTIVE'});
      await setDoc(doc(db, `users/${uid}/runtime/current`), {ipsumId: 'one'});
    }
  });
});
after(async () => env?.cleanup());

test('Google user reads only its account, handles, attempts and runtime', async () => {
  const db = google('rules-a');
  for (const suffix of ['', '/handles/tourist', '/handles/tourist/ipsums/one', '/runtime/current']) {
    await assertSucceeds(getDoc(doc(db, `users/rules-a${suffix}`)));
    await assertFails(getDoc(doc(db, `users/rules-b${suffix}`)));
  }
  await assertSucceeds(getDocs(collection(db, 'users/rules-a/handles')));
  await assertFails(getDocs(collection(db, 'users/rules-b/handles')));
  await assertFails(getDocs(collection(db, 'users')));
});

test('Google user creates only its own minimal metadata with server time', async () => {
  const db = google('rules-create');
  const ref = doc(db, 'users/rules-create');
  await assertSucceeds(setDoc(ref, metadata()));
  const data = (await getDoc(ref)).data();
  assert.deepEqual(Object.keys(data).sort(), ['createdAt', 'schemaVersion']);
  assert.equal(data.schemaVersion, 1);
  assert.ok(data.createdAt instanceof Timestamp);
  await assertFails(setDoc(doc(db, 'users/rules-victim'), metadata()));
});

test('missing extra malformed or forged metadata and client clock are refused', async () => {
  const db = google('rules-invalid');
  const ref = doc(db, 'users/rules-invalid');
  const invalid = [
    {}, {schemaVersion: 1}, {createdAt: serverTimestamp()},
    {...metadata(), schemaVersion: 2}, {...metadata(), schemaVersion: '1'},
    {...metadata(), schemaVersion: 1.5}, {...metadata(), schemaVersion: true},
    {...metadata(), createdAt: Timestamp.fromMillis(0)},
    {...metadata(), createdAt: 'now'}, {...metadata(), createdAt: null},
    ...['uid', 'email', 'displayName', 'loremRating', 'handles', 'role'].map(field => ({...metadata(), [field]: 'forged'})),
  ];
  for (const data of invalid) await assertFails(setDoc(ref, data));
  assert.equal((await getDoc(ref)).exists(), false);
});

test('existing account cannot be overwritten updated or deleted including identical writes', async () => {
  const db = google('rules-a');
  const ref = doc(db, 'users/rules-a');
  const original = (await getDoc(ref)).data();
  await assertFails(setDoc(ref, original));
  await assertFails(setDoc(ref, metadata(), {merge: true}));
  await assertFails(updateDoc(ref, {schemaVersion: 99}));
  await assertFails(deleteDoc(ref));
  assert.deepEqual((await getDoc(ref)).data(), original);
});

test('client cannot forge progress change clocks or delete history', async () => {
  const db = google('rules-a');
  await assertFails(setDoc(doc(db, 'users/rules-a/handles/new'), {loremRating: 800}));
  await assertFails(updateDoc(doc(db, 'users/rules-a/handles/tourist'), {loremRating: 9999}));
  await assertFails(updateDoc(doc(db, 'users/rules-a/handles/tourist/ipsums/one'), {status: 'COMPLETED', expiresAt: 99999}));
  await assertFails(setDoc(doc(db, 'users/rules-a/runtime/current'), {ipsumId: 'forged'}));
  await assertFails(deleteDoc(doc(db, 'users/rules-a/handles/tourist/ipsums/one')));
});

test('metadata create cannot smuggle progress in an atomic batch', async () => {
  const db = google('rules-batch');
  const batch = writeBatch(db);
  batch.set(doc(db, 'users/rules-batch'), metadata());
  batch.set(doc(db, 'users/rules-batch/handles/tourist'), {loremRating: 9999});
  await assertFails(batch.commit());
  assert.equal((await getDoc(doc(db, 'users/rules-batch'))).exists(), false);
});

test('missing anonymous password and custom sessions cannot read or create even their account', async () => {
  const contexts = [env.unauthenticatedContext(), ...['anonymous', 'password', 'custom'].map(provider =>
    env.authenticatedContext('rules-a', {firebase: {sign_in_provider: provider}})), env.authenticatedContext('rules-a')];
  for (const context of contexts) {
    const db = context.firestore();
    await assertFails(getDoc(doc(db, 'users/rules-a')));
    await assertFails(getDocs(collection(db, 'users/rules-a/handles')));
    await assertFails(setDoc(doc(db, 'users/rules-a'), metadata()));
  }
  for (const provider of ['anonymous', 'password', 'custom']) {
    const uid = `rules-denied-${provider}`;
    const db = env.authenticatedContext(uid, {firebase: {sign_in_provider: provider}}).firestore();
    await assertFails(setDoc(doc(db, `users/${uid}`), metadata()));
  }
  await assertFails(getDoc(doc(google('rules-a'), 'unlisted/document')));
});
