const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {resolve} = require('node:path');
const {test, before, after} = require('node:test');
const {initializeTestEnvironment, assertFails, assertSucceeds} = require('@firebase/rules-unit-testing');
const {doc, getDoc, getDocs, collection, setDoc, updateDoc, deleteDoc, runTransaction, serverTimestamp, Timestamp, writeBatch} = require('firebase/firestore');
let env;
const google = uid => env.authenticatedContext(uid, {firebase: {sign_in_provider: 'google.com'}}).firestore();
const profile = (handle = 'Tourist') => ({schemaVersion: 1, handle, handleKey: `cf_${handle.toLowerCase()}`,
  bindingVersion: 1, loremRating: 800, consolidatedRating: null, progressVersion: 0, createdAt: serverTimestamp()});
const selection = (key, version = 1) => ({schemaVersion: 1, handleKey: key, bindingVersion: 1,
  selectionVersion: version, selectedAt: serverTimestamp()});
async function seed(uid, handles = []) {
  await env.withSecurityRulesDisabled(async c => {
    await setDoc(doc(c.firestore(), `users/${uid}`), {schemaVersion: 1, createdAt: serverTimestamp()});
    for (const h of handles) await setDoc(doc(c.firestore(), `users/${uid}/handles/cf_${h.toLowerCase()}`), profile(h));
  });
}
before(async () => { env = await initializeTestEnvironment({projectId: 'demo-lorem-local',
  firestore: {rules: readFileSync(resolve(__dirname, '../firestore.rules'), 'utf8')}}); });
after(async () => env?.cleanup());

test('same normalized handle in different Google accounts starts independently at 800', async () => {
  for (const [uid, name] of [['handles-a', 'Tourist'], ['handles-b', 'TOURIST']]) {
    await seed(uid); const db = google(uid); const ref = doc(db, `users/${uid}/handles/cf_tourist`);
    await assertSucceeds(setDoc(ref, profile(name)));
    assert.equal((await getDoc(ref)).data().loremRating, 800);
    assert.equal((await getDocs(collection(db, `users/${uid}/handles`))).size, 1);
  }
  await assertFails(getDoc(doc(google('handles-a'), 'users/handles-b/handles/cf_tourist')));
  await assertFails(getDocs(collection(google('handles-a'), 'users/handles-b/handles')));
});

test('handle schema refuses forged rating progress binding key clock or arbitrary metadata', async () => {
  const uid = 'handles-forged'; await seed(uid); const db = google(uid); const ref = doc(db, `users/${uid}/handles/cf_tourist`);
  const changes = [{loremRating: 3900}, {loremRating: 799}, {loremRating: '800'}, {loremRating: 800.5},
    {progressVersion: 1}, {consolidatedRating: 800}, {bindingVersion: 2}, {bindingVersion: '1'}, {schemaVersion: 2},
    {handleKey: 'cf_Tourist'}, {handle: 'Petr'}, {handle: 'a/b'}, {createdAt: Timestamp.fromMillis(0)},
    {uid}, {role: 'admin'}, {officialRating: 3900}];
  for (const change of changes) await assertFails(setDoc(ref, {...profile(), ...change}));
  await assertFails(setDoc(ref, {handle: 'Tourist', loremRating: 800}));
  await assertFails(setDoc(doc(db, `users/${uid}/handles/TOURIST`), profile()));
  await assertFails(setDoc(doc(db, `users/${uid}/handles/cf_petr`), profile()));
  await assertFails(setDoc(doc(google('missing-parent'), 'users/missing-parent/handles/cf_tourist'), profile()));
  assert.equal((await getDoc(ref)).exists(), false);
});

test('concurrent registration creates once and reinsert preserves progressed rating and consolidation', async () => {
  const uid = 'handles-concurrent'; await seed(uid); const db = google(uid); const ref = doc(db, `users/${uid}/handles/cf_tourist`);
  const register = () => runTransaction(db, async tx => { if ((await tx.get(ref)).exists()) return false;
    tx.set(ref, profile()); return true; });
  assert.equal((await Promise.all(Array.from({length: 6}, register))).filter(Boolean).length, 1);
  const created = (await getDoc(ref)).data().createdAt;
  await env.withSecurityRulesDisabled(c => updateDoc(doc(c.firestore(), ref.path), {
    loremRating: 1377, consolidatedRating: 1200, progressVersion: 9}));
  assert.equal(await register(), false);
  const data = (await getDoc(ref)).data();
  assert.equal(data.loremRating, 1377); assert.equal(data.consolidatedRating, 1200); assert.deepEqual(data.createdAt, created);
  await assertFails(setDoc(ref, profile())); await assertFails(updateDoc(ref, {loremRating: 800})); await assertFails(deleteDoc(ref));
});

test('selection advances version and references only the own existing binding without rating writes', async () => {
  const uid = 'handles-selection'; await seed(uid, ['Tourist', 'Petr']); const db = google(uid);
  const ref = doc(db, `users/${uid}/runtime/selection`);
  await assertSucceeds(setDoc(ref, selection('cf_tourist'))); await assertSucceeds(setDoc(ref, selection('cf_petr', 2)));
  assert.equal((await getDoc(ref)).data().handleKey, 'cf_petr');
  for (const bad of [selection('cf_tourist', 1), selection('cf_tourist', 4), selection('cf_missing', 3),
    {...selection('cf_tourist', 3), bindingVersion: 2}, {...selection('cf_tourist', 3), selectedAt: Timestamp.fromMillis(0)},
    {...selection('cf_tourist', 3), uid: 'other'}, {...selection('cf_tourist', 3), loremRating: 3900}]) await assertFails(setDoc(ref, bad));
  await assertFails(deleteDoc(ref)); await assertFails(setDoc(doc(google('handles-a'), ref.path), selection('cf_tourist', 3)));
  assert.equal((await getDoc(doc(db, `users/${uid}/handles/cf_tourist`))).data().loremRating, 800);
});

test('active account lock blocks switching including an atomic batch that tries deleting the lock', async () => {
  const uid = 'handles-locked'; await seed(uid, ['Tourist', 'Petr']); const db = google(uid);
  const ref = doc(db, `users/${uid}/runtime/selection`); await setDoc(ref, selection('cf_tourist'));
  await env.withSecurityRulesDisabled(c => setDoc(doc(c.firestore(), `users/${uid}/runtime/current`), {
    handleKey: 'cf_tourist', bindingVersion: 1, ipsumId: 'active-attempt'}));
  await assertFails(setDoc(ref, selection('cf_petr', 2)));
  const batch = writeBatch(db); batch.delete(doc(db, `users/${uid}/runtime/current`)); batch.set(ref, selection('cf_petr', 2));
  await assertFails(batch.commit()); assert.equal((await getDoc(ref)).data().handleKey, 'cf_tourist');
});

test('password anonymous custom and absent tokens cannot create handles or selection', async () => {
  const uid = 'handles-provider'; await seed(uid, ['Tourist']);
  const contexts = [env.unauthenticatedContext(), ...['password', 'anonymous', 'custom'].map(provider =>
    env.authenticatedContext(uid, {firebase: {sign_in_provider: provider}}))];
  for (const c of contexts) {
    await assertFails(setDoc(doc(c.firestore(), `users/${uid}/handles/cf_petr`), profile('Petr')));
    await assertFails(setDoc(doc(c.firestore(), `users/${uid}/runtime/selection`), selection('cf_tourist')));
    await assertFails(getDoc(doc(c.firestore(), `users/${uid}/handles/cf_tourist`)));
  }
});
