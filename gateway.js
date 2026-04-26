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
  // AbacatePay API endpoint
  endpoint: 'https://api.abacatepay.com/v1/billing',

  // AbacatePay API key
  apiKey: 'abc_prod_yeJaNm3pHDQGNREsDBKU4pat',

  // Timeout in ms
  timeoutMs: 15000,
};

const Gateway = (() => {

  /**
   * Build the request payload for AbacatePay.
   * Creates a billing with PIX payment method.
   *
   * @param {{ amount: number, type: 'debit'|'credit', cardToken: string, source: string, brand: string, expiry: string, holderName: string }} params
   * @returns {object}
   */
  const buildPayload = ({ amount, type, cardToken, source, brand, expiry, holderName }) => ({
    frequency: 'ONE_TIME',
    methods: ['PIX'],
    products: [{
      externalId: `CARD-${Date.now()}`,
      name: `Pagamento via ${brand || 'Cartão'}`,
      description: `${type === 'debit' ? 'Débito' : 'Crédito'} - ${cardToken?.slice(0, 6)}...${cardToken?.slice(-4)} - ${expiry || 'N/A'}`,
      quantity: 1,
      price: amount, // em centavos
    }],
    metadata: {
      app: 'TapOpenSource',
      version: '1.0.0',
      paymentType: type,
      cardBrand: brand,
      cardToken: cardToken,
      tokenSource: source,
      holderName: holderName,
    },
  });

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

      // AbacatePay retorna billing com URL de pagamento PIX
      const result = {
        success: true,
        transactionId: data.id,
        authCode: data.url ? 'PIX-PENDING' : '------',
        message: data.url ? 'Cobrança PIX criada' : 'Pagamento aprovado',
        pixUrl: data.url, // URL para pagamento PIX
      };
      Log.info('gateway:charge_approved', { transactionId: result.transactionId, pixUrl: result.pixUrl });
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
   */
  const _mockCharge = ({ amount, type }) => new Promise((resolve) => {
    setTimeout(() => {
      const approved = Math.random() > 0.2;
      const result = approved ? {
        success: true,
        transactionId: 'MOCK-' + Math.random().toString(36).slice(2, 10).toUpperCase(),
        authCode: Math.floor(100000 + Math.random() * 900000).toString(),
        message: 'Pagamento aprovado (mock)',
      } : {
        success: false,
        transactionId: null,
        authCode: null,
        message: 'Transação recusada pelo emissor (mock)',
      };
      Log.info(approved ? 'gateway:mock_approved' : 'gateway:mock_declined', { amount, type });
      resolve(result);
    }, 1200 + Math.random() * 800);
  });

  return { charge };
})();
