#!/usr/bin/env node

/**
 * Teste com Credenciais de TESTE do Mercado Pago
 */

const https = require('https');

// Credenciais de TESTE
const PUBLIC_KEY = 'TEST-48c3ba02-39ca-41ab-91a5-ee3b05cd35e4';
const ACCESS_TOKEN = 'TEST-5963527441161067-042621-574c31db059bb0cac5a-1516341798';

// Cartão de teste
const TEST_CARD = {
  card_number: '5031755734530604',
  expiration_month: '12',
  expiration_year: '2026',
  security_code: '123',
  cardholder: {
    name: 'APRO'
  }
};

console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
console.log('🧪 Teste com Credenciais de TESTE');
console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

function makeRequest(options, data) {
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
    if (data) req.write(JSON.stringify(data));
    req.end();
  });
}

async function tokenizeCard() {
  console.log('📝 Tokenizando cartão de teste...\n');

  const options = {
    hostname: 'api.mercadopago.com',
    port: 443,
    path: '/v1/card_tokens',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${PUBLIC_KEY}`
    }
  };

  try {
    const response = await makeRequest(options, TEST_CARD);
    
    if (response.status === 201 || response.status === 200) {
      console.log('✅ Tokenização bem-sucedida!');
      console.log(`   Token: ${response.data.id}\n`);
      return response.data.id;
    } else {
      console.log('❌ Erro na tokenização:');
      console.log(JSON.stringify(response.data, null, 2));
      return null;
    }
  } catch (error) {
    console.error('❌ Erro:', error.message);
    return null;
  }
}

async function processPayment(cardToken) {
  console.log('💳 Processando pagamento...\n');

  const paymentData = {
    transaction_amount: 10.00,
    token: cardToken,
    description: 'Teste TapOpenSource',
    installments: 1,
    payment_method_id: 'master',
    payer: { email: 'test@test.com' }
  };

  const options = {
    hostname: 'api.mercadopago.com',
    port: 443,
    path: '/v1/payments',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${ACCESS_TOKEN}`,
      'X-Idempotency-Key': `test-${Date.now()}`
    }
  };

  try {
    const response = await makeRequest(options, paymentData);
    
    if (response.status === 201 || response.status === 200) {
      const payment = response.data;
      
      if (payment.status === 'approved') {
        console.log('✅ PAGAMENTO APROVADO!');
        console.log(`   ID: ${payment.id}`);
        console.log(`   Auth Code: ${payment.authorization_code || 'N/A'}\n`);
      } else {
        console.log(`⚠️  Status: ${payment.status}`);
        console.log(`   Detail: ${payment.status_detail}\n`);
      }
      return payment;
    } else {
      console.log('❌ Erro no pagamento:');
      console.log(JSON.stringify(response.data, null, 2));
      return null;
    }
  } catch (error) {
    console.error('❌ Erro:', error.message);
    return null;
  }
}

async function run() {
  const token = await tokenizeCard();
  if (!token) process.exit(1);
  
  await new Promise(r => setTimeout(r, 1000));
  
  const payment = await processPayment(token);
  if (!payment) process.exit(1);
  
  console.log('✅ Teste concluído!\n');
}

run().catch(console.error);
