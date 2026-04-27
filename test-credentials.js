#!/usr/bin/env node

/**
 * TapOpenSource - Teste de Credenciais do Mercado Pago
 * 
 * Verifica se as credenciais estão válidas e a conta está configurada
 */

const https = require('https');

const PUBLIC_KEY = 'APP_USR-893fdc1d-857f-4e79-afba-54bd4f3b1c59';
const ACCESS_TOKEN = 'APP_USR-5963527441161067-042621-59b6a4ba5c8d30a1fba4fabcb652769f-1516341798';

console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
console.log('🔐 Teste de Credenciais - Mercado Pago');
console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

function makeRequest(options) {
  return new Promise((resolve, reject) => {
    const req = https.request(options, (res) => {
      let body = '';
      res.on('data', (chunk) => body += chunk);
      res.on('end', () => {
        try {
          const json = JSON.parse(body);
          resolve({ status: res.statusCode, data: json });
        } catch (e) {
          resolve({ status: res.statusCode, data: body });
        }
      });
    });
    req.on('error', reject);
    req.end();
  });
}

async function testAccessToken() {
  console.log('1️⃣  Testando Access Token...');
  console.log(`   Token: ${ACCESS_TOKEN.substring(0, 30)}...\n`);

  const options = {
    hostname: 'api.mercadopago.com',
    port: 443,
    path: '/v1/account/settings',
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${ACCESS_TOKEN}`
    }
  };

  try {
    const response = await makeRequest(options);
    
    if (response.status === 200) {
      console.log('✅ Access Token VÁLIDO!');
      console.log(`   User ID: ${response.data.id || 'N/A'}`);
      console.log(`   Site ID: ${response.data.site_id || 'N/A'}`);
      console.log(`   País: ${response.data.country_id || 'N/A'}`);
      console.log(`   Categoria: ${response.data.category_id || 'N/A'}\n`);
      return true;
    } else if (response.status === 401) {
      console.log('❌ Access Token INVÁLIDO ou EXPIRADO');
      console.log(`   Mensagem: ${response.data.message || 'Não autorizado'}\n`);
      return false;
    } else {
      console.log(`⚠️  Status inesperado: ${response.status}`);
      console.log(`   Resposta:`, JSON.stringify(response.data, null, 2), '\n');
      return false;
    }
  } catch (error) {
    console.error('❌ Erro na requisição:', error.message, '\n');
    return false;
  }
}

async function testPublicKey() {
  console.log('2️⃣  Testando Public Key...');
  console.log(`   Key: ${PUBLIC_KEY.substring(0, 30)}...\n`);

  // Tenta obter métodos de pagamento (endpoint público)
  const options = {
    hostname: 'api.mercadopago.com',
    port: 443,
    path: '/v1/payment_methods',
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${PUBLIC_KEY}`
    }
  };

  try {
    const response = await makeRequest(options);
    
    if (response.status === 200 && Array.isArray(response.data)) {
      console.log('✅ Public Key VÁLIDA!');
      console.log(`   Métodos de pagamento disponíveis: ${response.data.length}`);
      
      // Lista alguns métodos
      const methods = response.data.slice(0, 5).map(m => m.id).join(', ');
      console.log(`   Exemplos: ${methods}...\n`);
      return true;
    } else if (response.status === 401) {
      console.log('❌ Public Key INVÁLIDA ou EXPIRADA');
      console.log(`   Mensagem: ${response.data.message || 'Não autorizado'}\n`);
      return false;
    } else {
      console.log(`⚠️  Status inesperado: ${response.status}`);
      console.log(`   Resposta:`, JSON.stringify(response.data, null, 2), '\n');
      return false;
    }
  } catch (error) {
    console.error('❌ Erro na requisição:', error.message, '\n');
    return false;
  }
}

async function checkAccountStatus() {
  console.log('3️⃣  Verificando status da conta...\n');

  const options = {
    hostname: 'api.mercadopago.com',
    port: 443,
    path: '/users/me',
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${ACCESS_TOKEN}`
    }
  };

  try {
    const response = await makeRequest(options);
    
    if (response.status === 200) {
      const user = response.data;
      console.log('✅ Informações da conta:');
      console.log(`   ID: ${user.id || 'N/A'}`);
      console.log(`   Email: ${user.email || 'N/A'}`);
      console.log(`   Nickname: ${user.nickname || 'N/A'}`);
      console.log(`   País: ${user.country_id || 'N/A'}`);
      console.log(`   Site: ${user.site_id || 'N/A'}`);
      console.log(`   Status: ${user.status || 'N/A'}\n`);
      return true;
    } else {
      console.log(`⚠️  Não foi possível obter informações da conta`);
      console.log(`   Status: ${response.status}\n`);
      return false;
    }
  } catch (error) {
    console.error('❌ Erro na requisição:', error.message, '\n');
    return false;
  }
}

async function runTests() {
  const accessTokenValid = await testAccessToken();
  await new Promise(resolve => setTimeout(resolve, 500));
  
  const publicKeyValid = await testPublicKey();
  await new Promise(resolve => setTimeout(resolve, 500));
  
  const accountOk = await checkAccountStatus();

  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log('📊 Resultado dos Testes:');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log(`   Access Token: ${accessTokenValid ? '✅ Válido' : '❌ Inválido'}`);
  console.log(`   Public Key: ${publicKeyValid ? '✅ Válida' : '❌ Inválida'}`);
  console.log(`   Conta: ${accountOk ? '✅ OK' : '⚠️  Verificar'}`);
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

  if (accessTokenValid && publicKeyValid) {
    console.log('✅ Credenciais configuradas corretamente!\n');
    console.log('💡 Próximos passos:');
    console.log('   1. As credenciais estão válidas');
    console.log('   2. O erro E603 pode ser temporário do Mercado Pago');
    console.log('   3. Tente novamente em alguns minutos');
    console.log('   4. Ou teste com um cartão real via NFC no app Android\n');
    console.log('📱 Para testar no Android:');
    console.log('   1. Instale o APK: TapOpenSource.apk');
    console.log('   2. Abra o app');
    console.log('   3. Digite um valor (ex: R$ 1,00)');
    console.log('   4. Aproxime um cartão real');
    console.log('   5. Verifique o resultado\n');
  } else {
    console.log('❌ Há problemas com as credenciais!\n');
    console.log('🔧 Ações necessárias:');
    console.log('   1. Verifique se copiou as credenciais completas');
    console.log('   2. Acesse: https://www.mercadopago.com.br/developers/panel/credentials');
    console.log('   3. Gere novas credenciais se necessário');
    console.log('   4. Atualize os arquivos:');
    console.log('      - android/app/src/main/java/.../MercadoPagoClient.kt');
    console.log('      - gateway.js\n');
  }
}

runTests().catch(error => {
  console.error('\n❌ Erro fatal:', error);
  process.exit(1);
});
