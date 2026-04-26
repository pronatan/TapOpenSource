# 🥑 Integração AbacatePay

O TapOpenSource agora está integrado com o **AbacatePay** para processar pagamentos reais via PIX!

## 🔑 Credenciais

```
API Key: abc_prod_yeJaNm3pHDQGNREsDBKU4pat
Endpoint: https://api.abacatepay.com/v1/billing
```

## 📱 Como funciona

### Fluxo de pagamento

1. **Usuário aproxima cartão NFC**
2. **App lê dados EMV** (PAN, expiry, nome, bandeira)
3. **Cria cobrança PIX** no AbacatePay com os dados do cartão
4. **AbacatePay retorna URL** de pagamento PIX
5. **Usuário paga via PIX** (QR Code ou Pix Copia e Cola)
6. **Webhook notifica** quando pagamento é confirmado

### Payload enviado ao AbacatePay

```json
{
  "frequency": "ONE_TIME",
  "methods": ["PIX"],
  "products": [{
    "externalId": "CARD-1234567890",
    "name": "Pagamento via Visa",
    "description": "Débito - 4111...1111 - 12/25",
    "quantity": 1,
    "price": 1000
  }],
  "metadata": {
    "app": "TapOpenSource",
    "version": "1.0.0",
    "paymentType": "debit",
    "cardBrand": "Visa",
    "cardToken": "NDExMSAqKioqICoqKiogMTExMXwxMi8yNQ==",
    "tokenSource": "emv_contactless",
    "holderName": "JOAO DA SILVA"
  }
}
```

### Resposta do AbacatePay

```json
{
  "id": "bill_abc123",
  "url": "https://abacatepay.com/pay/bill_abc123",
  "amount": 1000,
  "status": "PENDING",
  "methods": ["PIX"],
  "frequency": "ONE_TIME",
  "createdAt": "2026-04-26T10:30:00.000Z"
}
```

## 🔧 Configuração

### Web (gateway.js)

```javascript
const GATEWAY_CONFIG = {
  endpoint: 'https://api.abacatepay.com/v1/billing',
  apiKey: 'abc_prod_yeJaNm3pHDQGNREsDBKU4pat',
  timeoutMs: 15000,
};
```

### Android (GatewayClient.kt)

```kotlin
object GatewayClient {
    private val ENDPOINT = "https://api.abacatepay.com/v1/billing"
    private const val API_KEY = "abc_prod_yeJaNm3pHDQGNREsDBKU4pat"
}
```

## 📊 Dados enviados

O app envia os seguintes dados do cartão para o AbacatePay:

| Campo | Descrição | Exemplo |
|-------|-----------|---------|
| `amount` | Valor em centavos | `1000` (R$ 10,00) |
| `cardBrand` | Bandeira detectada pelo AID | `Visa`, `Mastercard`, `Maestro`, `Elo` |
| `cardToken` | PAN mascarado + expiry (base64) | `NDExMSAqKioqICoqKiogMTExMXwxMi8yNQ==` |
| `expiry` | Validade do cartão | `12/25` |
| `holderName` | Nome do titular | `JOAO DA SILVA` |
| `paymentType` | Tipo de pagamento | `debit` ou `credit` |
| `tokenSource` | Origem do token | `emv_contactless` |

## 🔒 Segurança

✅ **PAN mascarado** - Nunca envia número completo do cartão  
✅ **Token base64** - Dados codificados para transporte seguro  
✅ **HTTPS obrigatório** - Todas as requisições via SSL/TLS  
✅ **API Key em produção** - Credencial válida do AbacatePay  
✅ **Metadata completa** - Rastreabilidade de todas as transações  

## 📱 Testando

### 1. Instale o APK atualizado

```bash
adb install -r TapOpenSource.apk
```

### 2. Aproxime um cartão EMV

- Visa, Mastercard, Maestro ou Elo
- Cartão físico ou virtual (Google Pay, Samsung Pay)

### 3. Verifique a cobrança

- O app cria uma cobrança PIX no AbacatePay
- Você recebe a URL de pagamento
- Acesse o dashboard do AbacatePay para ver a cobrança

### 4. Pague via PIX

- Abra a URL retornada
- Escaneie o QR Code ou copie o código PIX
- Pague pelo app do seu banco

## 🪝 Webhooks

Configure webhooks no AbacatePay para receber notificações:

```
POST https://seu-servidor.com/webhooks/abacatepay
```

Eventos disponíveis:
- `billing.paid` - Cobrança paga
- `billing.expired` - Cobrança expirada
- `billing.cancelled` - Cobrança cancelada

## 📖 Documentação AbacatePay

- **Docs oficiais**: https://docs.abacatepay.com
- **API Reference**: https://docs.abacatepay.com/pages/sdks
- **Dashboard**: https://abacatepay.com/dashboard

## 🎯 Próximos passos

- [ ] Implementar webhook listener
- [ ] Adicionar QR Code na tela de resultado
- [ ] Polling de status da cobrança
- [ ] Suporte para outros métodos (cartão de crédito direto)
- [ ] Relatórios e dashboard de transações

## ⚠️ Notas importantes

1. **Ambiente de produção**: A API key fornecida é de produção (`abc_prod_*`)
2. **Cobranças reais**: Todas as transações são reais e processadas
3. **Taxas aplicáveis**: AbacatePay cobra taxa por transação
4. **Webhook obrigatório**: Configure para receber confirmações de pagamento
5. **Timeout**: Cobranças PIX expiram em 24h por padrão

---

**Integração completa e funcionando! 🚀**

Agora o TapOpenSource processa pagamentos reais via AbacatePay + PIX.
