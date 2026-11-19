const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {resolve} = require('node:path');
const {test} = require('node:test');
const {initializeTestEnvironment, assertFails, assertSucceeds} = require('@firebase/rules-unit-testing');
const {doc, setDoc, getDoc, writeBatch, serverTimestamp} = require('firebase/firestore');

// Unmodified historical rules from 26f58f0 (38) and e36361a (39).
for (const [id, file, dolor, permitted] of [
  ['38-lorem', 'fixtures/firestore-38.rules', false, false],
  ['39-lorem', 'fixtures/firestore-39.rules', false, true],
  ['39-dolor', 'fixtures/firestore-39.rules', true, false],
  ['current-lorem', '../firestore.rules', false, true],
  ['current-dolor', '../firestore.rules', true, true],
]) {
  test(`${id}: ${permitted ? 'accepts' : 'rejects'} the start atomically`, async () => {
    const env = await initializeTestEnvironment({projectId: `demo-lorem-compat-${id}`,
      firestore: {rules: readFileSync(resolve(__dirname, file), 'utf8')}});
    try {
      const db = env.authenticatedContext('compat-user', {firebase: {sign_in_provider: 'google.com'}}).firestore();
      const base = 'users/compat-user';
      await setDoc(doc(db, base), {schemaVersion: 1, createdAt: serverTimestamp()});
      await setDoc(doc(db, `${base}/handles/cf_tourist`), {schemaVersion: 1, handle: 'tourist', handleKey: 'cf_tourist',
        bindingVersion: 1, loremRating: 800, consolidatedRating: null, progressVersion: 0, createdAt: serverTimestamp()});
      await setDoc(doc(db, `${base}/runtime/selection`), {schemaVersion: 1, handleKey: 'cf_tourist',
        bindingVersion: 1, selectionVersion: 1, selectedAt: serverTimestamp()});
      const scoring = {policyVersion: 'D-012', limits: null, bonusMultiplier: 1.25, reductionMultiplier: .5,
        modelVersion: null, artifactSha256: null, ratingSource: 'codeforces_prior', inputRating: null,
        unavailableReason: 'activation_disabled'};
      const snapshot = {handle: 'tourist', initialRating: 800, contestId: 2000, index: 'A', name: 'Synthetic compatibility problem',
        rating: 800, tags: ['math'], originAt: 1593561600000, originVerifiedAt: Date.now() - 1000,
        originSource: 'codeforces:contest.list', originVersion: 'origin-20200701-v1', category: 'CURRENT_LEVEL',
        desiredMin: 800, desiredMax: 800, fallback: 0, cfRating: null, cfObservedAt: null, factors: [], recentOverlap: 0, scoring};
      if (dolor) snapshot.virtualDuel = {policy: 'virtual-duel-v1', model: 'km-delta-isotonic-20261020-v1',
        sha256: '69954b61bc111b2b2b1c33efef7e66978a74a378244ef0f41f834f9d6a8daea3', ratingMicros: 800000000,
        difficulty: 800, curveMin: 0, k: 32, horizonMillis: 7200000, assistance: 'unassisted'};
      const batch = writeBatch(db);
      const attempt = doc(db, `${base}/handles/cf_tourist/ipsums/compat`);
      const marker = doc(db, `${base}/handles/cf_tourist/usedProblems/2000_A`);
      const lock = doc(db, `${base}/runtime/current`);
      batch.set(attempt, {handleKey: 'cf_tourist', bindingVersion: 1, version: 0, snapshot, state: 'ACTIVE',
        startedAt: serverTimestamp(), limitMillis: 7200000, ruleVersion: 'spark-2h-v1', hintAt: null, cutoffAt: null,
        closeReason: null, failure: null, result: null, processedAt: null, solvedOutsideAt: null});
      batch.set(marker, {ipsumId: 'compat'});
      batch.set(lock, {handleKey: 'cf_tourist', ipsumId: 'compat', checkedAt: serverTimestamp()});
      await (permitted ? assertSucceeds(batch.commit()) : assertFails(batch.commit()));
      const [stored, used, active] = await Promise.all([getDoc(attempt), getDoc(marker), getDoc(lock)]);
      assert.deepEqual([stored.exists(), used.exists(), active.exists()], [permitted, permitted, permitted]);
      if (permitted) assert.deepEqual(stored.data().snapshot, snapshot);
    } finally { await env.cleanup(); }
  });
}
