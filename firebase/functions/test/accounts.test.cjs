const assert = require('node:assert/strict');
const {test} = require('node:test');

// These unsigned identities are accepted ONLY by the local Auth Emulator.
// Every test calls the real callable HTTP endpoint, including its Auth middleware.
const project = 'demo-lorem-local';
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST;
const functionsHost = '127.0.0.1:5001';
if (!authHost || !process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Run via npm test with emulators.');

async function googleIdentity(sub) {
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const token = `${encode({alg: 'none', typ: 'JWT'})}.${encode({
    sub, email: `${sub}@example.invalid`, email_verified: true,
    iss: 'https://accounts.google.com', aud: 'emulator-only',
    iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 3600,
  })}.`;
  const response = await fetch(`http://${authHost}/identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=emulator-only`, {
    method: 'POST', headers: {'content-type': 'application/json'},
    body: JSON.stringify({postBody: `id_token=${token}&providerId=google.com`,
      requestUri: 'http://localhost', returnSecureToken: true}),
  });
  const body = await response.json();
  assert.equal(response.status, 200, JSON.stringify(body));
  return body;
}
async function call(token, data = {}) {
  const response = await fetch(`http://${functionsHost}/${project}/us-central1/bootstrapAccount`, {
    method: 'POST', headers: {'content-type': 'application/json', ...(token ? {authorization: `Bearer ${token}`} : {})},
    body: JSON.stringify({data}),
  });
  return {status: response.status, body: await response.json()};
}
async function account(uid) {
  // Admin calls stay confined to Firestore Emulator; no credentials or real project.
  const {getApps, initializeApp} = require('firebase-admin/app');
  const {getFirestore} = require('firebase-admin/firestore');
  if (!getApps().length) initializeApp({projectId: project});
  return (await getFirestore().doc(`users/${uid}`).get()).data();
}

test('callable rejects missing or invalid token and non-Google sign-in', async () => {
  assert.equal((await call()).status, 401);
  // Functions Emulator can forward a malformed bearer as an identity without
  // provider claims; the handler must still reject it. Production also verifies JWTs.
  assert.ok([401, 403].includes((await call('not-a-token')).status));
  const response = await fetch(`http://${authHost}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator-only`, {
    method: 'POST', headers: {'content-type': 'application/json'},
    body: JSON.stringify({email: 'password@example.invalid', password: 'local-test-only-123', returnSecureToken: true}),
  });
  const user = await response.json();
  assert.equal(response.status, 200);
  assert.equal((await call(user.idToken)).status, 403);
  assert.equal(await account(user.localId), undefined);
});

test('concurrent Google bootstrap creates once with server time and preserves creation', async () => {
  const user = await googleIdentity('account-concurrent');
  const results = await Promise.all(Array.from({length: 6}, () => call(user.idToken)));
  assert.ok(results.every(r => r.status === 200), JSON.stringify(results));
  assert.equal(results.filter(r => r.body.result.created).length, 1);
  const original = await account(user.localId);
  assert.equal(original.schemaVersion, 1);
  assert.ok(original.createdAt.toMillis() <= Date.now());
  assert.ok(original.createdAt.toMillis() > Date.now() - 60_000);
  assert.deepEqual(Object.keys(original).sort(), ['createdAt', 'schemaVersion']);
  assert.equal((await call(user.idToken)).body.result.created, false);
  assert.deepEqual(await account(user.localId), original);
});

test('uid and arbitrary fields cannot redirect an Admin SDK write', async () => {
  const user = await googleIdentity('account-path');
  assert.equal((await call(user.idToken, {uid: 'forged-victim', rating: 9999})).status, 400);
  assert.equal(await account('forged-victim'), undefined);
  assert.equal(await account(user.localId), undefined);
  assert.equal((await call(user.idToken)).status, 200);
  const another = await googleIdentity('account-other');
  assert.equal((await call(another.idToken)).body.result.created, true);
  assert.notEqual(user.localId, another.localId);
});
