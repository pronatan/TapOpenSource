#!/usr/bin/env node

/**
 * TapOpenSource - Teste usando MCP do Mercado Pago
 * 
 * Este script usa o servidor MCP oficial do Mercado Pago
 * para fazer a integração correta
 */

const { spawn } = require('child_process');
const readline = require('readline');

const ACCESS_TOKEN = 'APP_USR-5963527441161067-042621-59b6a4ba5c8d30a1fba4fabcb652769f-1516341798';

console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
console.log('🔌 Teste usando MCP do Mercado Pago');
console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

console.log('📡 Conectando ao servidor MCP...\n');

// Inicia o servidor MCP do Mercado Pago
const mcp = spawn('npx', [
  '-y',
  'mcp-remote',
  'https://mcp.mercadopago.com/mcp',
  '--header',
  `Authorization:Bearer ${ACCESS_TOKEN}`
], {
  stdio: ['pipe', 'pipe', 'pipe']
});

let buffer = '';
let requestId = 1;

// Processa saída do MCP
mcp.stdout.on('data', (data) => {
  buffer += data.toString();
  
  // Processa mensagens JSON completas
  const lines = buffer.split('\n');
  buffer = lines.pop(); // Mantém linha incompleta no buffer
  
  lines.forEach(line => {
    if (line.trim()) {
      try {
        const message = JSON.parse(line);
        console.log('📨 Resposta MCP:', JSON.stringify(message, null, 2));
        
        // Se recebeu lista de ferramentas, tenta criar um pagamento
        if (message.result && message.result.tools) {
          console.log('\n✅ Ferramentas disponíveis:');
          message.result.tools.forEach(tool => {
            console.log(`   - ${tool.name}: ${tool.description}`);
          });
          
          // Tenta criar um pagamento de teste
          setTimeout(() => createTestPayment(), 2000);
        }
      } catch (e) {
        console.log('📄 Saída:', line);
      }
    }
  });
});

mcp.stderr.on('data', (data) => {
  console.error('⚠️  Erro MCP:', data.toString());
});

mcp.on('close', (code) => {
  console.log(`\n🔌 Servidor MCP encerrado (código: ${code})`);
  process.exit(code);
});

// Envia requisição para listar ferramentas
function listTools() {
  const request = {
    jsonrpc: '2.0',
    id: requestId++,
    method: 'tools/list',
    params: {}
  };
  
  console.log('📤 Enviando requisição:', JSON.stringify(request, null, 2), '\n');
  mcp.stdin.write(JSON.stringify(request) + '\n');
}

// Cria um pagamento de teste
function createTestPayment() {
  console.log('\n💳 Tentando criar pagamento de teste...\n');
  
  const request = {
    jsonrpc: '2.0',
    id: requestId++,
    method: 'tools/call',
    params: {
      name: 'create_payment',
      arguments: {
        transaction_amount: 10.00,
        description: 'Teste TapOpenSource via MCP',
        payment_method_id: 'master',
        payer: {
          email: 'test@tapopensource.dev'
        }
      }
    }
  };
  
  console.log('📤 Criando pagamento:', JSON.stringify(request, null, 2), '\n');
  mcp.stdin.write(JSON.stringify(request) + '\n');
  
  // Encerra após 5 segundos
  setTimeout(() => {
    console.log('\n⏱️  Tempo esgotado, encerrando...');
    mcp.kill();
  }, 5000);
}

// Aguarda inicialização e lista ferramentas
setTimeout(() => {
  console.log('📋 Listando ferramentas disponíveis...\n');
  listTools();
}, 1000);

// Timeout geral
setTimeout(() => {
  console.log('\n⏱️  Timeout geral, encerrando...');
  mcp.kill();
}, 15000);
