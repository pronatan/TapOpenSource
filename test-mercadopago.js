#!/usr/bin/env node

/**
 * TapOpenSource - Teste de Integração com Mercado Pago
 * 
 * Este script testa a integração com Mercado Pago via CLI:
 * 1. Tokeniza um cartão de teste
 * 2. Processa um pagamento
 */

const https = require('https');

// Credenciais configuradas
const PUBLIC_KEY = 'APP_USR-893fdc1d-857f-4e79-afba-54bd4f3b1c59';
const ACCESS_TOKEN = 'APP_USR-5963527441161067-042621-59b6a4ba5c8d30a1fba4fabcb652769f-1516341798';

// Cartão de teste do Mercado Pago (sempre aprova)
// Fonte: https://www.mercadopago.com.br/developers/pt/docs/checkout-api/integration-test/test-cards
const TEST_CARD = {
  card_number: '5031755734530604',  // Mastercard de teste (aprovado)
  expiration_month: '12',
  expiration_year: '2026',
  security_code: '123',
  cardholder: {
    name: 'APRO'  // Nome especial que sempre aprova
  }
};

console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
console.log('🧪 Teste de Integração - Mercado Pago');
console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

/**
 * Faz requisição HTTPS
 */
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

/**
 * Passo 1: Tokenizar cartão
 */
async function tokenizeCard() {
  console.log('📝 Passo 1: Tokenizando cartão de teste...');
  console.log(`   Cartão: ${TEST_CARD.card_number}`);
  console.log(`   Titular: ${TEST_CARD.cardholder.name}\n`);

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
      console.log(`   Token ID: ${response.data.id}`);
      console.log(`   Status: ${response.data.status}`);
      console.log(`   Primeiros 6 dígitos: ${response.data.first_six_digits || 'N/A'}`);
      console.log(`   Últimos 4 dígitos: ${response.data.last_four_digits || 'N/A'}\n`);
      return response.data.id;
    } else {
      console.error('❌ Erro na tokenização:');
      console.error(`   Status: ${response.status}`);
      console.error(`   Resposta:`, JSON.stringify(response.data, null, 2));
      return null;
    }
  } catch (error) {
    console.error('❌ Erro na requisição:', error.message);
    return null;
  }
}

/**
 * Passo 2: Processar pagamento
 */
async function processPayment(cardToken) {
  console.log('💳 Passo 2: Processando pagamento...');
  console.log(`   Valor: R$ 10,00`);
  console.log(`   Método: Mastercard Crédito\n`);

  const paymentData = {
    transaction_amount: 10.00,
    token: cardToken,
    description: 'Teste TapOpenSource - Pagamento via NFC',
    installments: 1,
    payment_method_id: 'master',  // Mastercard crédito
    payer: {
      email: 'test@tapopensource.dev'
    },
    capture: true,
    statement_descriptor: 'TapOpenSource'
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
    
    console.log(`📊 Status HTTP: ${response.status}\n`);

    if (response.status === 201 || response.status === 200) {
      const payment = response.data;
      
      console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
      
      if (payment.status === 'approved') {
        console.log('✅ PAGAMENTO APROVADO!');
        console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
        console.log(`   ID da Transação: ${payment.id}`);
        console.log(`   Código de Autorização: ${payment.authorization_code || 'N/A'}`);
        console.log(`   Valor: R$ ${payment.transaction_amount.toFixed(2)}`);
        console.log(`   Status: ${payment.status}`);
        console.log(`   Status Detail: ${payment.status_detail}`);
        console.log(`   Método de Pagamento: ${payment.payment_method_id}`);
        console.log(`   Tipo: ${payment.payment_type_id}`);
        console.log(`   Data de Criação: ${payment.date_created}`);
        console.log(`   Data de Aprovação: ${payment.date_approved || 'N/A'}`);
      } else if (payment.status === 'rejected') {
        console.log('❌ PAGAMENTO RECUSADO');
        console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
        console.log(`   ID da Transação: ${payment.id}`);
        console.log(`   Status: ${payment.status}`);
        console.log(`   Status Detail: ${payment.status_detail}`);
        console.log(`   Motivo: ${translateStatusDetail(payment.status_detail)}`);
      } else {
        console.log('⏳ PAGAMENTO PENDENTE');
        console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
        console.log(`   ID da Transação: ${payment.id}`);
        console.log(`   Status: ${payment.status}`);
        console.log(`   Status Detail: ${payment.status_detail}`);
      }
      
      console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');
      
      // Mostra resposta completa
      console.log('📄 Resposta completa da API:');
      console.log(JSON.stringify(payment, null, 2));
      
      return payment;
    } else {
      console.log('❌ ERRO NO PAGAMENTO');
      console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
      console.log(`   Status HTTP: ${response.status}`);
      console.log(`   Mensagem: ${response.data.message || 'Erro desconhecido'}`);
      
      if (response.data.cause) {
        console.log('\n   Causas:');
        response.data.cause.forEach(cause => {
          console.log(`   - ${cause.code}: ${cause.description}`);
        });
      }
      
      console.log('\n📄 Resposta completa:');
      console.log(JSON.stringify(response.data, null, 2));
      
      return null;
    }
  } catch (error) {
    console.error('❌ Erro na requisição:', error.message);
    return null;
  }
}

/**
 * Traduz status_detail para português
 */
function translateStatusDetail(statusDetail) {
  const translations = {
    'accredited': 'Pagamento aprovado e creditado',
    'cc_rejected_insufficient_amount': 'Saldo insuficiente',
    'cc_rejected_bad_filled_security_code': 'Código de segurança inválido',
    'cc_rejected_bad_filled_date': 'Data de validade inválida',
    'cc_rejected_bad_filled_other': 'Dados do cartão inválidos',
    'cc_rejected_call_for_authorize': 'Entre em contato com o banco',
    'cc_rejected_card_disabled': 'Cartão desabilitado',
    'cc_rejected_duplicated_payment': 'Pagamento duplicado',
    'cc_rejected_high_risk': 'Transação de alto risco',
    'cc_rejected_max_attempts': 'Limite de tentativas excedido',
    'cc_rejected_other_reason': 'Recusado pelo banco',
  };
  return translations[statusDetail] || statusDetail;
}

/**
 * Executa o teste completo
 */
async function runTest() {
  console.log('🔑 Credenciais configuradas:');
  console.log(`   Public Key: ${PUBLIC_KEY.substring(0, 20)}...`);
  console.log(`   Access Token: ${ACCESS_TOKEN.substring(0, 20)}...\n`);

  // Passo 1: Tokenizar
  const cardToken = await tokenizeCard();
  
  if (!cardToken) {
    console.log('\n❌ Teste falhou na tokenização.');
    process.exit(1);
  }

  // Aguarda 1 segundo
  await new Promise(resolve => setTimeout(resolve, 1000));

  // Passo 2: Processar pagamento
  const payment = await processPayment(cardToken);
  
  if (!payment) {
    console.log('\n❌ Teste falhou no processamento do pagamento.');
    process.exit(1);
  }

  console.log('\n✅ Teste concluído com sucesso!');
  console.log('\n💡 Próximos passos:');
  console.log('   1. Verifique a transação no painel do Mercado Pago');
  console.log('   2. URL: https://www.mercadopago.com.br/activities');
  console.log('   3. Instale o APK no dispositivo Android');
  console.log('   4. Teste com um cartão real via NFC\n');
}

// Executa o teste
runTest().catch(error => {
  console.error('\n❌ Erro fatal:', error);
  process.exit(1);
});
