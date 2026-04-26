# 🥑 Guia de Configuração AbacatePay

## Status Atual

✅ **API Key configurada**: `abc_prod_yeJaNm3pHDQGNREsDBKU4pat`  
✅ **Endpoint correto**: `https://api.abacatepay.com/v2/checkouts/create`  
✅ **Método de pagamento**: CARD (débito/crédito)  
⚠️ **Modo**: MOCK (aguardando criação de produto)

## 🎯 O que falta para ativar

### 1. Criar produto no dashboard AbacatePay

1. Acesse: https://app.abacatepay.com/products
2. Clique em "Criar Produto"
3. Preencha:
   - **Nome**: Pagamento NFC
   - **Descrição**: Pagamento via cartão NFC
   - **Preço**: R$ 0,01 (será sobrescrito pelo valor real)
4. Copie o **ID do produto** (ex: `prod_abc123xyz`)

### 2. Configurar o ID do produto no código

#### Web (gateway.js)

Linha ~30:
```javascript
const buildPayload = ({ amount, type, cardToken, source, brand, expiry, holderName }) => ({
  items: [{
    id: 'prod_abc123xyz', // ⚠️ COLE O ID DO PRODUTO AQUI
    quantity: 1,
  }],
  // ...
});
```

#### Android (GatewayClient.kt)

Linha ~50:
```kotlin
put("items", org.json.JSONArray().put(
    JSONObject().apply {
        put("id", "prod_abc123xyz") // ⚠️ COLE O ID DO PRODUTO AQUI
        put("quantity", 1)
    }
))
```

### 3. Ativar o endpoint

#### Web (gateway.js)

Linha ~15:
```javascript
const GATEWAY_CONFIG = {
  endpoint: 'https://api.abacatepay.com/v2/checkouts/create', // ⚠️ DESCOMENTE
  // endpoint: null, // ⚠️ COMENTE ESTA LINHA
  apiKey: 'abc_prod_yeJaNm3pHDQGNREsDBKU4pat',
  timeoutMs: 15000,
};
```

#### Android (GatewayClient.kt)

Linha ~20:
```kotlin
private val ENDPOINT = "https://api.abacatepay.com/v2/checkouts/create" // ⚠️ DESCOMENTE
// private val ENDPOINT: String? = null // ⚠️ COMENTE ESTA LINHA
```

### 4. Rebuild e deploy

```bash
# Web
wrangler pages deploy . --project-name=tapopensource

# Android
cd android
./gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk ../TapOpenSource.apk
```

## 📊 Modo Mock (Atual)

Enquanto o produto não é criado, o app funciona em **modo mock**:

✅ Simula aprovação/recusa de pagamentos  
✅ Mostra o payload que seria enviado ao AbacatePay  
✅ Loga no console e no Cloudflare Worker  
✅ Processa débito e crédito  

### Exemplo de log do mock:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🥑 MOCK: Payload AbacatePay (Checkout API v2):
{
  "items": [
    {
      "id": "PRODUTO_ID_AQUI",
      "quantity": 1
    }
  ],
  "methods": ["CARD"],
  "externalId": "CARD-1735234567890",
  "metadata": {
    "app": "TapOpenSource",
    "version": "1.0.0",
    "paymentType": "debit",
    "cardBrand": "Visa",
    "cardToken": "NDExMSAqKioqICoqKiogMTExMXwxMi8yNQ==",
    "tokenSource": "emv_contactless",
    "holderName": "JOAO DA SILVA",
    "expiry": "12/25",
    "amount": 1000,
    "description": "DÉBITO - Visa - 4111...1111"
  }
}
Endpoint: POST https://api.abacatepay.com/v2/checkouts/create
Método: CARD (DÉBITO)
Valor: R$ 10.00
Bandeira: Visa
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

## 🧪 Testando

### 1. Instale o APK
```bash
adb install -r TapOpenSource.apk
```

### 2. Teste uma transação
1. Abra o app
2. Digite R$ 10,00
3. Selecione DÉBITO ou CRÉDITO
4. Aproxime um cartão EMV
5. Veja o payload no logcat:
```bash
adb logcat | grep GatewayClient
```

### 3. Verifique os logs
- **Android**: `adb logcat | grep "🥑 MOCK"`
- **Web**: Console do navegador (F12)
- **Cloudflare**: https://tapopensource-logs.natanaelrodriguesfernandes521.workers.dev/logs?token=tap2024secret

## 📖 Documentação AbacatePay

- **Docs**: https://docs.abacatepay.com
- **Checkout API**: https://docs.abacatepay.com/pages/checkouts
- **Dashboard**: https://app.abacatepay.com
- **Produtos**: https://app.abacatepay.com/products

## 🔄 Fluxo completo (quando ativado)

1. **Usuário aproxima cartão** → App lê dados EMV
2. **App cria checkout** → POST /v2/checkouts/create
3. **AbacatePay retorna** → `{ id, url, status: "PENDING" }`
4. **App redireciona** → Cliente finaliza pagamento na URL
5. **Webhook notifica** → Pagamento confirmado
6. **App atualiza status** → Transação concluída

## ⚠️ Importante

- **Produto obrigatório**: AbacatePay requer produto pré-cadastrado
- **Preço dinâmico**: O valor do produto é sobrescrito pelo `amount` do metadata
- **Método CARD**: Suporta débito e crédito automaticamente
- **Webhook**: Configure para receber notificações de pagamento

---

**Pronto para ativar assim que o produto for criado!** 🚀
