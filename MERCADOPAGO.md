# 💳 Integração com Mercado Pago

Este guia explica como configurar e usar a integração com Mercado Pago no TapOpenSource.

## 📋 Pré-requisitos

1. **Conta no Mercado Pago**
   - Crie uma conta em: https://www.mercadopago.com.br
   - Acesse o painel de desenvolvedores: https://www.mercadopago.com.br/developers

2. **Credenciais de API**
   - Obtenha suas credenciais em: https://www.mercadopago.com.br/developers/panel/credentials
   - Você precisará de:
     - **Public Key** (Chave pública) - para tokenização de cartões
     - **Access Token** - para processar pagamentos

## 🔧 Configuração

### Android (Kotlin)

1. **Configure as credenciais** em `android/app/src/main/java/dev/tapopensource/app/gateway/MercadoPagoClient.kt`:

```kotlin
private const val PUBLIC_KEY = "APP_USR-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
private const val ACCESS_TOKEN = "APP_USR-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
```

2. **Ative o Mercado Pago** em `android/app/src/main/java/dev/tapopensource/app/gateway/GatewayClient.kt`:

```kotlin
private val GATEWAY_TYPE: GatewayType = GatewayType.MERCADO_PAGO
```

3. **Build e instale o APK**:

```bash
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Web (JavaScript)

1. **Configure as credenciais** em `gateway.js`:

```javascript
const GATEWAY_CONFIG = {
  type: 'mercadopago',
  
  mercadopago: {
    publicKey: 'APP_USR-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx',
    accessToken: 'APP_USR-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx',
  },
};
```

2. **Deploy para produção**:

```bash
# Cloudflare Pages
wrangler pages deploy . --project-name=tapopensource

# Ou qualquer outro hosting estático
```

## 🔄 Fluxo de Pagamento

### 1. Tokenização do Cartão

O app lê os dados do cartão via NFC e envia para a API do Mercado Pago:

```
POST https://api.mercadopago.com/v1/card_tokens
Authorization: Bearer PUBLIC_KEY

{
  "card_number": "4111111111111111",
  "expiration_month": "12",
  "expiration_year": "2025",
  "security_code": "000",
  "cardholder": {
    "name": "NOME DO TITULAR"
  }
}
```

**Resposta:**
```json
{
  "id": "abc123def456",
  "status": "active"
}
```

### 2. Processamento do Pagamento

Com o token do cartão, processa o pagamento:

```
POST https://api.mercadopago.com/v1/payments
Authorization: Bearer ACCESS_TOKEN
X-Idempotency-Key: unique-key-123

{
  "transaction_amount": 10.00,
  "token": "abc123def456",
  "description": "Pagamento via TapOpenSource NFC",
  "installments": 1,
  "payment_method_id": "visa",
  "payer": {
    "email": "customer@example.com"
  }
}
```

**Resposta (Aprovado):**
```json
{
  "id": 123456789,
  "status": "approved",
  "status_detail": "accredited",
  "authorization_code": "123456",
  "transaction_amount": 10.00
}
```

**Resposta (Recusado):**
```json
{
  "id": 123456789,
  "status": "rejected",
  "status_detail": "cc_rejected_insufficient_amount"
}
```

## 💳 Métodos de Pagamento Suportados

O TapOpenSource mapeia automaticamente a bandeira e tipo de pagamento:

| Bandeira | Débito | Crédito |
|----------|--------|---------|
| Visa | `debvisa` | `visa` |
| Mastercard | `debmaster` | `master` |
| Maestro | `maestro` | - |
| Elo | `debelo` | `elo` |

## 🔒 Segurança

### Dados Sensíveis

⚠️ **IMPORTANTE**: O PAN completo do cartão é sensível e deve ser tratado com cuidado:

- ✅ **Transmitido via HTTPS** para a API do Mercado Pago
- ✅ **Nunca logado** em logs ou console
- ✅ **Não armazenado** localmente
- ✅ **Tokenizado imediatamente** após leitura

### PCI-DSS Compliance

Para uso em produção, você deve:

1. **Certificação PCI-DSS** - Obrigatória para processar dados de cartão
2. **Homologação das bandeiras** - Visa, Mastercard, etc.
3. **Testes de segurança** - Penetration testing
4. **Criptografia end-to-end** - TLS 1.2+

### Network Security (Android)

O app já está configurado com `network_security_config.xml`:

```xml
<network-security-config>
  <base-config cleartextTrafficPermitted="false">
    <trust-anchors>
      <certificates src="system" />
    </trust-anchors>
  </base-config>
</network-security-config>
```

## 🧪 Testes

### Modo Mock

Para testar sem credenciais reais, use o modo mock:

**Android:**
```kotlin
private val GATEWAY_TYPE: GatewayType = GatewayType.MOCK
```

**Web:**
```javascript
const GATEWAY_CONFIG = {
  type: 'mock',
};
```

### Cartões de Teste

O Mercado Pago fornece cartões de teste para ambiente de desenvolvimento:

| Bandeira | Número | CVV | Validade |
|----------|--------|-----|----------|
| Visa | 4509 9535 6623 3704 | 123 | 11/25 |
| Mastercard | 5031 4332 1540 6351 | 123 | 11/25 |

**Documentação completa:** https://www.mercadopago.com.br/developers/pt/docs/checkout-api/integration-test/test-cards

### Testar Diferentes Cenários

Para simular diferentes respostas, use valores específicos:

- **Aprovado**: Qualquer valor
- **Recusado por saldo**: Use `transaction_amount: 1.01`
- **Recusado por dados inválidos**: Use cartão inválido

## 📊 Status de Pagamento

### Status Possíveis

| Status | Descrição |
|--------|-----------|
| `approved` | Pagamento aprovado e processado |
| `rejected` | Pagamento recusado |
| `pending` | Pagamento pendente de processamento |
| `in_process` | Pagamento em análise |
| `cancelled` | Pagamento cancelado |
| `refunded` | Pagamento estornado |

### Status Detail (Motivos de Recusa)

| Código | Tradução |
|--------|----------|
| `cc_rejected_insufficient_amount` | Saldo insuficiente |
| `cc_rejected_bad_filled_security_code` | Código de segurança inválido |
| `cc_rejected_bad_filled_date` | Data de validade inválida |
| `cc_rejected_call_for_authorize` | Entre em contato com o banco |
| `cc_rejected_card_disabled` | Cartão desabilitado |
| `cc_rejected_high_risk` | Transação de alto risco |

**Lista completa:** https://www.mercadopago.com.br/developers/pt/docs/checkout-api/response-handling/collection-results

## 🐛 Troubleshooting

### Erro: "Credenciais não configuradas"

**Causa:** Public Key ou Access Token não foram configurados.

**Solução:** Configure as credenciais conforme descrito na seção de Configuração.

### Erro: "Erro ao tokenizar cartão"

**Causas possíveis:**
- PAN do cartão inválido
- Data de validade inválida
- Credenciais incorretas
- Problema de rede

**Solução:** Verifique os logs para detalhes do erro.

### Erro: "cc_rejected_bad_filled_security_code"

**Causa:** CVV não disponível via NFC (limitação técnica).

**Solução:** O Mercado Pago pode aceitar transações sem CVV em alguns casos, mas isso depende da configuração da sua conta. Entre em contato com o suporte do Mercado Pago.

### Web NFC não funciona

**Causa:** Web NFC API não suporta leitura de cartões EMV, apenas tags NDEF.

**Solução:** Use o app Android nativo ou o Web-to-Native Bridge.

## 📚 Documentação Adicional

- **API Reference:** https://www.mercadopago.com.br/developers/pt/reference
- **Checkout API:** https://www.mercadopago.com.br/developers/pt/docs/checkout-api/landing
- **SDKs:** https://www.mercadopago.com.br/developers/pt/docs/sdks-library/landing
- **Webhooks:** https://www.mercadopago.com.br/developers/pt/docs/checkout-api/additional-content/your-integrations/notifications/webhooks

## 🤝 Suporte

### Mercado Pago

- **Suporte:** https://www.mercadopago.com.br/developers/pt/support
- **Fórum:** https://www.mercadopago.com.br/developers/pt/support/forum
- **Status da API:** https://status.mercadopago.com/

### TapOpenSource

- **Issues:** https://github.com/pronatan/TapOpenSource/issues
- **Discussões:** https://github.com/pronatan/TapOpenSource/discussions

## ⚠️ Limitações

### Web App

- ❌ **Web NFC API não lê cartões EMV** - Apenas tags NDEF
- ✅ **Solução:** Use o Android app ou Web-to-Native Bridge

### Android App

- ⚠️ **CVV não disponível via NFC** - Limitação do protocolo EMV contactless
- ⚠️ **Alguns cartões podem rejeitar** - Depende da configuração do emissor
- ⚠️ **PAN pode estar mascarado** - Alguns cartões não expõem PAN completo

### Geral

- ⚠️ **Requer certificação PCI-DSS** para produção
- ⚠️ **Homologação das bandeiras** necessária
- ⚠️ **Taxas do Mercado Pago** aplicam-se a todas as transações

## 📝 Changelog

### v1.1.0 (2026-04-26)
- ✨ Adicionada integração com Mercado Pago
- ✨ Suporte a tokenização de cartões
- ✨ Mapeamento automático de bandeiras
- ✨ Tradução de mensagens de erro
- 🔒 Melhorias de segurança no tratamento de PAN

---

**Feito com ⚡ por TapOpenSource**
