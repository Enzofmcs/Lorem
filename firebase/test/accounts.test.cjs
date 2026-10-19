const assert = require('node:assert/strict');
const {test, after} = require('node:test');
const {initializeApp, deleteApp} = require('firebase/app');
const {getAuth, connectAuthEmulator, signInWithCredential, GoogleAuthProvider, createUserWithEmailAndPassword} = require('firebase/auth');
const {getFirestore, connectFirestoreEmulator, doc, runTransaction, serverTimestamp, getDoc, terminate} = require('firebase/firestore');

// Every SDK is explicitly confined to loopback emulators and the demo project.
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST;
const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST;
if (!/^127\.0\.0\.1:\d+$/.test(authHost || '') || !/^127\.0\.0\.1:\d+$/.test(firestoreHost || '')) {
  throw new Error('Run via npm test with loopback emulators.');
}
const clients = [];
function client() {
  const app = initializeApp({projectId: 'demo-lorem-local', apiKey: 'emulator-only'}, `client-${clients.length}`);
  const auth = getAuth(app);
  const db = getFirestore(app);
  connectAuthEmulator(auth, `http://${authHost}`, {disableWarnings: true});
  const [host, port] = firestoreHost.split(':');
  connectFirestoreEmulator(db, host, Number(port));
  const c = {app, auth, db};
  clients.push(c);
  return c;
}
after(async () => {
  for (const c of clients) { await terminate(c.db); await deleteApp(c.app); }
});
async function google(c, sub) {
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const token = `${encode({alg: 'none', typ: 'JWT'})}.${encode({
    sub, email: `${sub}@example.invalid`, email_verified: true,
    iss: 'https://accounts.google.com', aud: 'emulator-only',
    iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 3600,
  })}.`;
  return (await signInWithCredential(c.auth, GoogleAuthProvider.credential(token))).user.uid;
}
// This exercises the shared protocol under real Auth Emulator tokens and Firestore Rules.
// The production Kotlin implementation is exercised separately by its SDK emulator tests.
async function bootstrap(c, uid) {
  assert.equal(c.auth.currentUser?.uid, uid);
  return runTransaction(c.db, async transaction => {
    const ref = doc(c.db, `users/${uid}`);
    if ((await transaction.get(ref)).exists()) return false;
    transaction.set(ref, {schemaVersion: 1, createdAt: serverTimestamp()});
    return true;
  });
}
test('real Google emulator tokens bootstrap concurrently once and preserve server timestamp', async () => {
  const c = client();
  const uid = await google(c, 'spark-concurrent');
  const results = await Promise.all(Array.from({length: 6}, () => bootstrap(c, uid)));
  assert.equal(results.filter(Boolean).length, 1);
  const ref = doc(c.db, `users/${uid}`);
  const original = (await getDoc(ref)).data();
  assert.deepEqual(Object.keys(original).sort(), ['createdAt', 'schemaVersion']);
  assert.equal(original.schemaVersion, 1);
  assert.ok(original.createdAt.toMillis() > Date.now() - 60_000);
  assert.ok(original.createdAt.toMillis() <= Date.now());
  assert.equal(await bootstrap(c, uid), false);
  assert.deepEqual((await getDoc(ref)).data(), original);
});
test('fresh client restores the same Google account without resetting metadata and other uid is isolated', async () => {
  const first = client();
  const uid = await google(first, 'spark-restored');
  assert.equal(await bootstrap(first, uid), true);
  const original = (await getDoc(doc(first.db, `users/${uid}`))).data();
  const second = client();
  assert.equal(await google(second, 'spark-restored'), uid);
  assert.equal(await bootstrap(second, uid), false);
  assert.deepEqual((await getDoc(doc(second.db, `users/${uid}`))).data(), original);
  const other = client();
  const otherUid = await google(other, 'spark-other');
  assert.notEqual(otherUid, uid);
  assert.equal(await bootstrap(other, otherUid), true);
  await assert.rejects(getDoc(doc(other.db, `users/${uid}`)), {code: 'permission-denied'});
});
test('real password emulator token cannot bootstrap an account', async () => {
  const c = client();
  const {user} = await createUserWithEmailAndPassword(c.auth, 'spark-password@example.invalid', 'local-test-only-123');
  await assert.rejects(bootstrap(c, user.uid), {code: 'permission-denied'});
});
