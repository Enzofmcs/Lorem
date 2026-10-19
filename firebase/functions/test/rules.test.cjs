const {readFileSync} = require('node:fs');
const {resolve} = require('node:path');
const {test, before, after} = require('node:test');
const {initializeTestEnvironment, assertFails, assertSucceeds} = require('@firebase/rules-unit-testing');
const {doc, collection, getDoc, getDocs, setDoc, updateDoc, deleteDoc} = require('firebase/firestore');

let env;
const google = uid => env.authenticatedContext(uid, {firebase: {sign_in_provider: 'google.com'}}).firestore();
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-lorem-local',
    firestore: {rules: readFileSync(resolve(__dirname, '../../firestore.rules'), 'utf8')},
  });
  await env.withSecurityRulesDisabled(async context => {
    const db = context.firestore();
    for (const uid of ['rules-a', 'rules-b']) {
      await setDoc(doc(db, `users/${uid}`), {schemaVersion: 1});
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

test('client cannot create accounts, forge progress, change clocks or delete history', async () => {
  const db = google('rules-a');
  await assertFails(setDoc(doc(db, 'users/rules-a'), {schemaVersion: 99}));
  await assertFails(setDoc(doc(db, 'users/rules-new'), {schemaVersion: 1}));
  await assertFails(updateDoc(doc(db, 'users/rules-a/handles/tourist'), {loremRating: 9999}));
  await assertFails(updateDoc(doc(db, 'users/rules-a/handles/tourist/ipsums/one'), {status: 'COMPLETED', expiresAt: 99999}));
  await assertFails(setDoc(doc(db, 'users/rules-a/runtime/current'), {ipsumId: 'forged'}));
  await assertFails(deleteDoc(doc(db, 'users/rules-a/handles/tourist/ipsums/one')));
});

test('anonymous, absent and password sessions cannot read even their own account', async () => {
  for (const db of [env.unauthenticatedContext().firestore(),
    env.authenticatedContext('rules-a', {firebase: {sign_in_provider: 'anonymous'}}).firestore(),
    env.authenticatedContext('rules-a', {firebase: {sign_in_provider: 'password'}}).firestore()]) {
    await assertFails(getDoc(doc(db, 'users/rules-a')));
    await assertFails(getDocs(collection(db, 'users/rules-a/handles')));
  }
  await assertFails(getDoc(doc(google('rules-a'), 'unlisted/document')));
});
