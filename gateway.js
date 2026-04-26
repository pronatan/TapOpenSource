/**
 * TapOpenSource — Payment Gateway Module
 *
 * Pluggable gateway layer. Configure your provider in GATEWAY_CONFIG.
 * Supported: generic REST (Stripe, Cielo, Stone, PagSeguro, etc.)
 *
 * To integrate your gateway:
 *   1. Set GATEWAY_CONFIG.endpoint to your API URL
 *   2. Set GATEWAY_CONFIG.apiKey (or use env injection at build time)
 *   3. Adapt buildPayload() to match your provider's schema
 */

const GATEWAY_CONFIG = {
  // AbacatePay API endpoint - ATIVADO
  endpoint: 'https://api.abacatepay.com/v2/checkouts/create',
  
  // AbacatePay API key
  apiKey: 'abc_prod_yeJaNm3pHDQGNREsDBKU4pat',

  // Timeout in ms
  timeoutMs: 15000,
};

const Gateway = (() => {

  /**
   * Build the request payload for AbacatePay Checkout.
   * IMPORTANTE: Requer produto pré-cadastrado no dashboard AbacatePay.
   * 
   * Para ativar:
   * 1. Acesse https://app.abacatepay.com/products
   * 2. Crie um produto (ex: "Pagamento NFC")
   * 3. Copie o ID do produto (ex: "prod_abc123xyz")
   * 4. Substitua 'PRODUTO_ID_AQUI' abaixo pelo ID real
   *
   * @param {{ amount: number, type: 'debit'|'credit', cardToken: string, source: string, brand: string, expiry: string, holderName: string }} params
   * @returns {object}
   */
  const buildPayload = ({ amount, type, cardToken, source, brand, expiry, holderName }) => {
    // Produto tem preço R$ 0,01 (1 centavo)
    // Quantidade = valor total / preço unitário
    const productPrice = 1; // R$ 0,01 em centavos
    const quantity = Math.max(1, Math.floor(amount / productPrice));
    
    return {
      items: [{
        id: 'prod_Fbzagare4CyeXH4mtJ5zUjpy', // ✅ Produto: Pagamento NFC (R$ 0,01)
        quantity: quantity, // Calcula quantity para atingir o valor desejado
      }],
      methods: ['CARD'], // Cartão de crédito/débito
      externalId: `CARD-${Date.now()}`,
      metadata: {
        app: 'TapOpenSource',
        version: '1.0.0',
        paymentType: type,
        cardBrand: brand,
        cardToken: cardToken,
        tokenSource: source,
        holderName: holderName,
        expiry: expiry,
        amount: amount, // valor real em centavos
        description: `${type === 'debit' ? 'Débito' : 'Crédito'} - ${brand} - R$ ${(amount / 100).toFixed(2)}`,
      },
    };
  };

  /**
   * Process a payment through the configured gateway.
   *
   * @param {{ amount: number, type: 'debit'|'credit', cardToken: string, source: string }} params
   * @returns {Promise<{ success: boolean, transactionId: string, authCode: string, message: string }>}
   */
  const charge = async (params) => {
    // Mock local — ativo quando endpoint não está configurado
    if (!GATEWAY_CONFIG.endpoint) {
      Log.info('gateway:mock_charge', { amount: params.amount, type: params.type });
      return _mockCharge(params);
    }

    Log.info('gateway:charge_start', { amount: params.amount, type: params.type });
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), GATEWAY_CONFIG.timeoutMs);

    try {
      const response = await fetch(GATEWAY_CONFIG.endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${GATEWAY_CONFIG.apiKey}`,
        },
        body: JSON.stringify(buildPayload(params)),
        signal: controller.signal,
      });

      clearTimeout(timeout);

      const data = await response.json();

      if (!response.ok) {
        const result = {
          success: false,
          transactionId: data.id || null,
          authCode: null,
          message: data.error?.message || data.message || `Erro ${response.status}`,
        };
        Log.warn('gateway:charge_declined', { status: response.status, message: result.message });
        return result;
      }

      // AbacatePay retorna checkout com URL de pagamento
      const result = {
        success: true,
        transactionId: data.id,
        authCode: data.status === 'PENDING' ? 'PENDING' : data.status,
        message: data.url ? 'Checkout criado - redirecione para pagamento' : 'Pagamento processado',
        checkoutUrl: data.url, // URL do checkout AbacatePay
      };
      Log.info('gateway:charge_approved', { transactionId: result.transactionId, checkoutUrl: result.checkoutUrl });
      return result;

    } catch (err) {
      clearTimeout(timeout);
      if (err.name === 'AbortError') {
        Log.error('gateway:timeout');
        return { success: false, transactionId: null, authCode: null, message: 'Timeout: gateway não respondeu.' };
      }
      Log.error('gateway:fetch_error', { message: err.message });
      return { success: false, transactionId: null, authCode: null, message: err.message };
    }
  };

  /**
   * Mock local para testes sem gateway real.
   * Simula latência de rede e aprova 80% das transações.
   * MOSTRA O PAYLOAD QUE SERIA ENVIADO AO ABACATEPAY.
   */
  const _mockCharge = (params) => new Promise((resolve) => {
    // Loga o payload que seria enviado ao AbacatePay
    const payload = buildPayload(params);
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
    console.log('🥑 MOCK: Payload AbacatePay (Checkout API v2):');
    console.log(JSON.stringify(payload, null, 2));
    console.log('Endpoint: POST https://api.abacatepay.com/v2/checkouts/create');
    console.log(`Método: CARD (${params.type === 'debit' ? 'DÉBITO' : 'CRÉDITO'})`);
    console.log(`Valor: R$ ${(params.amount / 100).toFixed(2)}`);
    console.log(`Bandeira: ${params.brand || 'N/A'}`);
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
    
    Log.info('gateway:mock_payload', payload);
    
    setTimeout(() => {
      const approved = Math.random() > 0.2;
      const result = approved ? {
        success: true,
        transactionId: 'MOCK-' + Math.random().toString(36).slice(2, 10).toUpperCase(),
        authCode: Math.floor(100000 + Math.random() * 900000).toString(),
        message: `Pagamento ${params.type === 'debit' ? 'DÉBITO' : 'CRÉDITO'} aprovado (mock)`,
      } : {
        success: false,
        transactionId: null,
        authCode: null,
        message: 'Transação recusada pelo emissor (mock)',
      };
      Log.info(approved ? 'gateway:mock_approved' : 'gateway:mock_declined', { 
        amount: params.amount, 
        type: params.type,
        brand: params.brand 
      });
      resolve(result);
    }, 1200 + Math.random() * 800);
  });

  return { charge };
})();
