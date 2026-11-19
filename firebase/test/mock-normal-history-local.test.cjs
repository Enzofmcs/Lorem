const assert = require('node:assert/strict');
const {mkdirSync, mkdtempSync, rmSync, writeFileSync} = require('node:fs');
const {tmpdir} = require('node:os');
const {join} = require('node:path');
const {after, before, test} = require('node:test');

const project = 'demo-lorem-local';
let configuredProject, testUidByEmail, directory;

before(async () => {
  ({configuredProject, testUidByEmail} = await import('../scripts/seed-normal-history-local.mjs'));
  directory = mkdtempSync(join(tmpdir(), 'lorem-config-'));
  mkdirSync(join(directory, 'app', 'src', 'mock'), {recursive: true});
});
after(() => rmSync(directory, {recursive: true, force: true}));

test('the launcher infers the project and refuses divergent Android Firebase configurations', () => {
  const config = project_id => JSON.stringify({project_info: {project_id}});
  const official = join(directory, 'app', 'google-services.json');
  const mock = join(directory, 'app', 'src', 'mock', 'google-services.json');
  assert.throws(() => configuredProject(directory), /google-services.json/);
  assert.equal(configuredProject(directory, project), project);
  writeFileSync(official, config(project));
  assert.equal(configuredProject(directory), project);
  writeFileSync(mock, config(project));
  assert.equal(configuredProject(directory), project);
  assert.throws(() => configuredProject(directory, 'demo-unrelated'), /mesmo projeto/);
  writeFileSync(mock, config('demo-unrelated'));
  assert.throws(() => configuredProject(directory), /mesmo projeto/);
});

test('the account email resolves to its UID through the Auth endpoint', async () => {
  const host = process.env.FIREBASE_AUTH_EMULATOR_HOST;
  assert.match(host, /^(127\.0\.0\.1|localhost):\d+$/);
  const email = 'lorem-seed-launcher@example.invalid';
  async function request(path, body) {
    const response = await fetch(`http://${host}${path}`, {method: 'POST',
      headers: {Authorization: 'Bearer owner', 'Content-Type': 'application/json'}, body: JSON.stringify(body)});
    const payload = await response.json();
    assert.ok(response.ok, JSON.stringify(payload));
    return payload;
  }
  const created = await request('/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator',
    {email, password: 'test-fixture-only', returnSecureToken: false});
  const adminRequest = (path, body) => request(`/identitytoolkit.googleapis.com${path}`, body);
  assert.equal(await testUidByEmail(project, ` ${email} `, adminRequest), created.localId);
  await assert.rejects(testUidByEmail(project, 'absent@example.invalid', adminRequest), /não encontrada/);
});
