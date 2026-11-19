import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import {join, resolve} from 'node:path';
import {createInterface} from 'node:readline/promises';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {parseArgs} from 'node:util';
import {MULTIPLE_MOCK_BATCHES, seedNormalHistory} from '../../tools/ipsum-mock/seed-normal-history.mjs';

const root = resolve(fileURLToPath(new URL('../../', import.meta.url)));
const require = createRequire(import.meta.url);
const projectPattern = /^[a-z][a-z0-9-]{4,29}$/;

export function configuredProject(directory, explicitProject) {
  const projects = new Set(explicitProject ? [explicitProject] : []);
  for (const relative of ['app/google-services.json', 'app/src/mock/google-services.json']) {
    let content;
    try {
      content = readFileSync(join(directory, relative), 'utf8');
    } catch (error) {
      if (error.code === 'ENOENT') continue;
      throw error;
    }
    let project;
    try {
      project = JSON.parse(content).project_info?.project_id;
    } catch {
      throw new Error(`${relative} precisa conter um JSON válido.`);
    }
    if (!projectPattern.test(project ?? '')) throw new Error(`ID do projeto ausente ou inválido em ${relative}.`);
    projects.add(project);
  }
  if (projects.size > 1) throw new Error('O Lorem e o Lorem Mock precisam apontar para o mesmo projeto Firebase.');
  const [project] = projects;
  if (!projectPattern.test(project ?? '')) throw new Error('Configure o google-services.json ou indique o projeto em --project.');
  return project;
}

async function firebaseSession(project, reauth) {
  const auth = require('firebase-tools/lib/auth.js');
  const {requireAuth} = require('firebase-tools/lib/requireAuth.js');
  const {Client} = require('firebase-tools/lib/apiv2.js');
  let account = auth.getProjectDefaultAccount(root);
  if (!account || reauth) {
    const login = require.resolve('firebase-tools/lib/bin/firebase.js');
    const args = [login, 'login', ...(reauth ? ['--reauth'] : [])];
    console.log('Entre no navegador com a conta que administra o projeto Firebase.');
    execFileSync(process.execPath, args, {cwd: root, stdio: 'inherit'});
    account = auth.getProjectDefaultAccount(root);
  }
  if (!account) throw new Error('O login administrativo do Firebase CLI não foi concluído.');
  await requireAuth({project, user: account.user, tokens: account.tokens});
  const firestore = new Client({urlPrefix: 'https://firestore.googleapis.com'});
  const identity = new Client({urlPrefix: 'https://identitytoolkit.googleapis.com'});
  return {
    firestore: async (path, body) => (await firestore.post(path, body, {timeout: 40_000})).body,
    identity: async (path, body) => (await identity.post(path, body, {timeout: 20_000})).body,
  };
}

export async function testUidByEmail(project, email, request) {
  const address = email.trim();
  if (!/^[^\s@]+@[^\s@]+$/.test(address)) throw new Error('Informe o e-mail da conta de teste.');
  const response = await request(`/v1/projects/${project}/accounts:lookup`, {email: [address]});
  const users = response.users ?? [];
  if (users.length !== 1 || !users[0].localId) {
    throw new Error('Conta de teste não encontrada. Entre no Lorem com esse e-mail e tente novamente.');
  }
  return users[0].localId;
}

async function ask(prompt) {
  const terminal = createInterface({input: process.stdin, output: process.stdout});
  try {
    return (await terminal.question(prompt)).trim();
  } finally {
    terminal.close();
  }
}

async function main() {
  const {values} = parseArgs({options: {
    project: {type: 'string'}, email: {type: 'string'}, uid: {type: 'string'},
    batch: {type: 'string'}, handle: {type: 'string'}, reauth: {type: 'boolean'}, help: {type: 'boolean'},
  }});
  if (values.help) {
    console.log('npm --prefix firebase run seed-normal-history -- [--email E-MAIL | --uid UID] [--handle HANDLE] [--batch ID] [--project PROJETO] [--reauth]');
    return;
  }
  const project = configuredProject(root, values.project);
  const emulator = process.env.FIRESTORE_EMULATOR_HOST;
  if (emulator && !values.uid) throw new Error('Informe --uid ao executar com o emulador.');
  let email = values.email;
  if (!values.uid && !email) {
    if (!process.stdin.isTTY) throw new Error('Informe a conta de teste em --email ou --uid.');
    email = await ask('E-mail da conta de teste que importou os Ipsums: ');
  }

  console.log(`Projeto Firebase: ${project}`);
  const session = emulator ? null : await firebaseSession(project, values.reauth);
  const uid = values.uid ?? await testUidByEmail(project, email, session.identity);
  const options = {
    project, uid, batch: values.batch, handle: values.handle, apply: true,
  };
  let result;
  try {
    result = await seedNormalHistory(options, emulator, session?.firestore);
  } catch (error) {
    if (error.code !== MULTIPLE_MOCK_BATCHES || !process.stdin.isTTY) throw error;
    const batch = await ask('Há mais de um lote. Cole o ID exibido no Lorem Mock: ');
    if (!batch) throw error;
    result = await seedNormalHistory({...options, batch}, emulator, session?.firestore);
  }
  const message = result.already ? 'Lote já carregado' : 'Carga concluída';
  console.log(`${message}. ${result.count} Ipsums · @${result.handle} · Rating Lorem ${result.rating}.`);
  console.log('Abra o Lorem normal no handle indicado e confira Histórico e Estatísticas.');
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  main().catch(error => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
