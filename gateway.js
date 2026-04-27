/**
 * TapOpenSource — Payment Gateway Module
 *
 * Pluggable gateway layer. Configure your provider in GATEWAY_CONFIG.
 * Supported: Mercado Pago, generic REST (Stripe, Cielo, Stone, PagSeguro, etc.)
 *
 * To integrate your gateway:
 *   1. Set GATEWAY_CONFIG.type to 'mercadopago', 'generic', or 'mock'
 *   2. For Mercado Pago: Set publicKey and accessToken
 *   3. For generic: Set endpoint and apiKey
 *   4. Adapt buildPayload() to match your provider's schema
 */

const GATEWAY_CONFIG = {
  // Gateway type: 'mercadopago', 'generic', or 'mock'
  type: 'mercadopago',
  
  // Mercado Pago credentials
  // Get yours at: https://www.mercadopago.com.br/developers/panel/credentials
  mercadopago: {
    publicKey: 'APP_USR-893fdc1d-857f-4e79-afba-54bd4f3b1c59',      // Chave pública (para tokenização)
    accessToken: 'APP_USR-5963527441161067-042621-59b6a4ba5c8d30a1fba4fabcb652769f-1516341798',  // Access token (para pagamentos)
  },

  // Generic gateway config
  generic: {
    endpoint: null,
    apiKey: 'YOUR_API_KEY',
  },

  // Timeout in ms
  timeoutMs: 20000,
};

const Gateway = (() => {

  /**
   * Build the request payload for generic gateways.
   * Adapt this to match your provider's API contract.
   */
  const buildPayload = ({ amount, type, cardToken, source, brand, expiry, holderName }) => ({
    amount,
    currency: 'BRL',
    payment_method: type,
    capture: true,
    card: {
      token: cardToken,
      entry_mode: 'contactless_nfc',
      token_source: source,
      brand: brand,
      expiry: expiry,
      holder_name: holderName,
    },
    metadata: {
      app: 'TapOpenSource',
      version: '1.0.0',
    },
  });

  /**
   * Process a payment through the configured gateway.
   */
  const charge = async (params) => {
    switch (GATEWAY_CONFIG.type) {
      case 'mercadopago':
        return chargeMercadoPago(params);
      case 'generic':
        return chargeGeneric(params);
      case 'mock':
      default:
        Log.info('gateway:mock_charge', { amount: params.amount, type: params.type });
        return _mockCharge(params);
    }
  };

  /**
   * Process payment via Mercado Pago
   */
  const chargeMercadoPago = async (params) => {
    const { publicKey, accessToken } = GATEWAY_CONFIG.mercadopago;

    // Validate credentials
    if (publicKey === 'YOUR_PUBLIC_KEY' || accessToken === 'YOUR_ACCESS_TOKEN') {
      Log.warn('gateway:mercadopago_credentials_not_configured');
      return {
        success: false,
        transactionId: null,
        authCode: null,
        message: 'Credenciais do Mercado Pago não configuradas. Configure publicKey e accessToken.',
      };
    }

    try {
      Log.info('gateway:mercadopago_charge_start', { amount: params.amount, type: params.type, brand: params.brand });

      // Step 1: Tokenize card
      const cardToken = await tokenizeCardMercadoPago(params, publicKey);
      if (!cardToken) {
        return {
          success: false,
          transactionId: null,
          authCode: null,
          message: 'Erro ao tokenizar cartão',
        };
      }

      Log.info('gateway:mercadopago_card_tokenized', { token: cardToken });

      // Step 2: Process payment
      return await processPaymentMercadoPago(params, cardToken, accessToken);

    } catch (err) {
      Log.error('gateway:mercadopago_error', { message: err.message });
      return {
        success: false,
        transactionId: null,
        authCode: null,
        message: `Erro: ${err.message}`,
      };
    }
  };

  /**
   * Tokenize card via Mercado Pago API
   */
  const tokenizeCardMercadoPago = async (params, publicKey) => {
    try {
      // Parse expiry (format YYMMDD or YYMM)
      const expiry = params.expiry || '2512';
      const expiryMonth = expiry.length >= 4 ? expiry.substring(2, 4) : '12';
      const expiryYear = expiry.length >= 2 ? `20${expiry.substring(0, 2)}` : '2025';

      // Extract card number from token (if available)
      // Note: Web NFC doesn't provide full PAN, this is a limitation
      // For real implementation, you'd need the Android bridge
      const cardNumber = params.cardToken || '4111111111111111'; // Placeholder

      const tokenRequest = {
        card_number: cardNumber,
        expiration_month: expiryMonth,
        expiration_year: expiryYear,
        security_code: '000', // CVV not available via NFC
        cardholder: {
          name: params.holderName || 'CARDHOLDER',
        },
      };

      const response = await fetch('https://api.mercadopago.com/v1/card_tokens', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${publicKey}`,
        },
        body: JSON.stringify(tokenRequest),
      });

      if (!response.ok) {
        const error = await response.json();
        Log.error('gateway:mercadopago_tokenize_error', { status: response.status, error });
        return null;
      }

      const tokenResponse = await response.json();
      return tokenResponse.id;

    } catch (err) {
      Log.error('gateway:mercadopago_tokenize_exception', { message: err.message });
      return null;
    }
  };

  /**
   * Process payment using Mercado Pago API
   */
  const processPaymentMercadoPago = async (params, cardToken, accessToken) => {
    try {
      const amountReais = params.amount / 100;

      // Map payment method
      const paymentMethodId = mapPaymentMethodMercadoPago(params.type, params.brand);

      const paymentRequest = {
        transaction_amount: amountReais,
        token: cardToken,
        description: `Pagamento via TapOpenSource NFC - ${params.type === 'debit' ? 'Débito' : 'Crédito'}`,
        installments: 1,
        payment_method_id: paymentMethodId,
        payer: {
          email: 'customer@tapopensource.dev',
        },
        capture: true,
        statement_descriptor: 'TapOpenSource',
      };

      const response = await fetch('https://api.mercadopago.com/v1/payments', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${accessToken}`,
          'X-Idempotency-Key': `${Date.now()}-${Math.floor(Math.random() * 10000)}`,
        },
        body: JSON.stringify(paymentRequest),
      });

      const paymentResponse = await response.json();

      if (!response.ok) {
        const errorMessage = paymentResponse.cause?.[0]?.description || paymentResponse.message || `Erro ${response.status}`;
        Log.warn('gateway:mercadopago_payment_declined', { status: response.status, message: errorMessage });
        return {
          success: false,
          transactionId: null,
          authCode: null,
          message: errorMessage,
        };
      }

      switch (paymentResponse.status) {
        case 'approved':
          Log.info('gateway:mercadopago_payment_approved', {
            transactionId: paymentResponse.id,
            authCode: paymentResponse.authorization_code || 'N/A',
          });
          return {
            success: true,
            transactionId: paymentResponse.id.toString(),
            authCode: paymentResponse.authorization_code || '------',
            message: 'Pagamento aprovado',
          };

        case 'rejected':
          Log.warn('gateway:mercadopago_payment_rejected', { statusDetail: paymentResponse.status_detail });
          return {
            success: false,
            transactionId: paymentResponse.id.toString(),
            authCode: null,
            message: `Pagamento recusado: ${translateStatusDetailMercadoPago(paymentResponse.status_detail)}`,
          };

        case 'pending':
        case 'in_process':
          Log.info('gateway:mercadopago_payment_pending', { statusDetail: paymentResponse.status_detail });
          return {
            success: false,
            transactionId: paymentResponse.id.toString(),
            authCode: null,
            message: `Pagamento pendente: ${translateStatusDetailMercadoPago(paymentResponse.status_detail)}`,
          };

        default:
          return {
            success: false,
            transactionId: paymentResponse.id.toString(),
            authCode: null,
            message: `Status desconhecido: ${paymentResponse.status}`,
          };
      }

    } catch (err) {
      Log.error('gateway:mercadopago_payment_exception', { message: err.message });
      return {
        success: false,
        transactionId: null,
        authCode: null,
        message: `Erro: ${err.message}`,
      };
    }
  };

  /**
   * Map payment type and brand to Mercado Pago payment_method_id
   */
  const mapPaymentMethodMercadoPago = (type, brand) => {
    const brandLower = (brand || '').toLowerCase();
    if (brandLower.includes('visa') && type === 'debit') return 'debvisa';
    if (brandLower.includes('visa') && type === 'credit') return 'visa';
    if (brandLower.includes('master') && type === 'debit') return 'debmaster';
    if (brandLower.includes('master') && type === 'credit') return 'master';
    if (brandLower.includes('maestro')) return 'maestro';
    if (brandLower.includes('elo') && type === 'debit') return 'debelo';
    if (brandLower.includes('elo') && type === 'credit') return 'elo';
    return type === 'debit' ? 'debvisa' : 'visa'; // Fallback
  };

  /**
   * Translate Mercado Pago status_detail to friendly messages
   */
  const translateStatusDetailMercadoPago = (statusDetail) => {
    const translations = {
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
  };

  /**
   * Process payment via generic gateway
   */
  const chargeGeneric = async (params) => {
    const { endpoint, apiKey } = GATEWAY_CONFIG.generic;

    if (!endpoint) {
      Log.warn('gateway:endpoint_not_configured');
      return {
        success: false,
        transactionId: null,
        authCode: null,
        message: 'Gateway endpoint não configurado',
      };
    }

    Log.info('gateway:charge_start', { amount: params.amount, type: params.type });
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), GATEWAY_CONFIG.timeoutMs);

    try {
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${apiKey}`,
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

      const result = {
        success: true,
        transactionId: data.id,
        authCode: data.authorization_code || data.auth_code || '------',
        message: 'Pagamento aprovado',
      };
      Log.info('gateway:charge_approved', { transactionId: result.transactionId, authCode: result.authCode });
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
  const _mockCharge = (params) => new Promise((resolve) => {
    const payload = buildPayload(params);
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
    console.log('💳 MOCK: Payload Gateway:');
    console.log(JSON.stringify(payload, null, 2));
    console.log(`Método: ${params.type === 'debit' ? 'DÉBITO' : 'CRÉDITO'}`);
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
