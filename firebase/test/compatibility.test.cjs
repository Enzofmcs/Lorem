const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {resolve} = require('node:path');
const {test} = require('node:test');
const {initializeTestEnvironment, assertFails, assertSucceeds} = require('@firebase/rules-unit-testing');
const {doc, setDoc, getDoc, writeBatch, serverTimestamp} = require('firebase/firestore');

// Frozen, unmodified Rules 38 from 26f58f0. Each version has its own emulator project.
test('current D012 client starts on Rules39 and is refused by original Rules38', async () => {
  for (const [version, file, permitted] of [
    ['38', 'fixtures/firestore-38.rules', false], ['39', '../firestore.rules', true],
  ]) {
    const env = await initializeTestEnvironment({projectId: `demo-lorem-compat-${version}`,
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
      const batch = writeBatch(db);
      const attempt = `${base}/handles/cf_tourist/ipsums/compat`;
      batch.set(doc(db, attempt), {handleKey: 'cf_tourist', bindingVersion: 1, version: 0, snapshot, state: 'ACTIVE',
        startedAt: serverTimestamp(), limitMillis: 7200000, ruleVersion: 'spark-2h-v1', hintAt: null, cutoffAt: null,
        closeReason: null, failure: null, result: null, processedAt: null, solvedOutsideAt: null});
      batch.set(doc(db, `${base}/handles/cf_tourist/usedProblems/2000_A`), {ipsumId: 'compat'});
      batch.set(doc(db, `${base}/runtime/current`), {handleKey: 'cf_tourist', ipsumId: 'compat', checkedAt: serverTimestamp()});
      await (permitted ? assertSucceeds(batch.commit()) : assertFails(batch.commit()));
      const stored = await getDoc(doc(db, attempt));
      assert.equal(stored.exists(), permitted);
      if (permitted) assert.deepEqual(stored.data().snapshot.scoring, scoring);
    } finally { await env.cleanup(); }
  }
});
