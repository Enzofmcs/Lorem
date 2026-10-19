const fs = require('node:fs');
const path = require('node:path');

const file = process.argv[2] || path.resolve(__dirname, '../../app/google-services.json');
try {
  const config = JSON.parse(fs.readFileSync(file, 'utf8'));
  const client = config.client?.find(c =>
    c.client_info?.android_client_info?.package_name === 'dev.lorem.app');
  const oauth = client?.oauth_client?.find(c => c.client_type === 3);
  if (!config.project_info?.project_id || !client?.client_info?.mobilesdk_app_id ||
      !client.api_key?.some(k => k.current_key) || !oauth?.client_id) {
    throw new Error('O JSON precisa conter o app dev.lorem.app e o cliente OAuth Web (tipo 3). Ative Google no Firebase e baixe o arquivo atualizado.');
  }
  console.log(`Configuracao Android valida: projeto ${config.project_info.project_id}, pacote dev.lorem.app, OAuth Web presente.`);
  console.log('Esta verificacao nao comprova provedor habilitado, assinaturas cadastradas, Rules ou deploy real.');
} catch (error) {
  console.error(error.code === 'ENOENT' ?
    'Falta app/google-services.json. Crie/configure o projeto seguindo docs/FIREBASE_SETUP.md.' :
    error instanceof SyntaxError ? 'O arquivo de configuracao nao e um JSON valido.' : error.message);
  process.exitCode = 1;
}
