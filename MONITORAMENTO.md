# 📊 Guia de Monitoramento - TapOpenSource + AbacatePay

## 🎯 APK Atualizado

✅ **Versão**: 26/04/2026 14:35:41  
✅ **Tamanho**: 7.0 MB  
✅ **AbacatePay**: ATIVO  
✅ **Logs**: DETALHADOS  

## 📱 Instalar APK

```bash
adb install -r TapOpenSource.apk
```

## 🔍 Monitorar Logs em Tempo Real

### Android Logcat

```bash
# Ver todos os logs do Gateway
adb logcat | grep Gateway

# Ver apenas sucessos
adb logcat | grep "✅ SUCESSO"

# Ver apenas erros
adb logcat | grep "❌"

# Ver logs do AbacatePay
adb logcat | grep "🥑 ABACATEPAY"
```

### Cloudflare Worker

```bash
# Ver últimos 20 logs
curl "https://tapopensource-logs.natanaelrodriguesfernandes521.workers.dev/logs?token=tap2024secret" | jq '.logs[:20]'

# Ver apenas logs do AbacatePay
curl "https://tapopensource-logs.natanaelrodriguesfernandes521.workers.dev/logs?token=tap2024secret" | jq '.logs[] | select(.event | contains("abacatepay"))'

# Limpar logs
curl -X DELETE "https://tapopensource-logs.natanaelrodriguesfernandes521.workers.dev/logs?token=tap2024secret"
```

## 📋 Exemplo de Logs

### ✅ Sucesso

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🥑 ABACATEPAY - INICIANDO COBRANÇA
Valor: R$ 10.00
Tipo: DÉBITO
Bandeira: Visa
Quantity: 1000
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🥑 Enviando para AbacatePay...
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🥑 ABACATEPAY - RESPOSTA
Status HTTP: 200
Success: true
✅ SUCESSO!
ID: bill_abc123xyz
Status: PENDING
URL: https://app.abacatepay.com/pay/bill_abc123xyz
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### ❌ Erro

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🥑 ABACATEPAY - INICIANDO COBRANÇA
Valor: R$ 0.50
Tipo: CRÉDITO
Bandeira: Mastercard
Quantity: 50
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🥑 Enviando para AbacatePay...
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🥑 ABACATEPAY - RESPOSTA
Status HTTP: 400
Success: false
❌ ERRO: Total price must be at least 100 cents
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

## 🧪 Testar Agora

### 1. Limpar logs antigos
```bash
curl -X DELETE "https://tapopensource-logs.natanaelrodriguesfernandes521.workers.dev/logs?token=tap2024secret"
```

### 2. Iniciar monitoramento
```bash
adb logcat -c  # Limpa logcat
adb logcat | grep Gateway
```

### 3. No app
1. Digite **R$ 10,00** (mínimo R$ 1,00)
2. Selecione **DÉBITO** ou **CRÉDITO**
3. Toque em **"Cobrar"**
4. Aproxime um **cartão EMV**

### 4. Acompanhar logs

**No terminal** (logcat):
- Veja a requisição sendo enviada
- Veja a resposta do AbacatePay
- Veja o checkout ID criado

**No Cloudflare**:
```bash
curl "https://tapopensource-logs.natanaelrodriguesfernandes521.workers.dev/logs?token=tap2024secret" | jq '.logs[0]'
```

## 📊 Eventos Logados

| Evento | Descrição |
|--------|-----------|
| `abacatepay:request_start` | Iniciando requisição |
| `abacatepay:success` | Checkout criado com sucesso |
| `abacatepay:http_error` | Erro HTTP (400, 500, etc) |
| `abacatepay:api_error` | Erro da API (success: false) |
| `abacatepay:exception` | Exceção (timeout, rede, etc) |

## 🔧 Troubleshooting

### Erro: "Total price must be at least 100 cents"
**Solução**: Digite valor mínimo de R$ 1,00

### Erro: "No products found"
**Solução**: Produto já está configurado (`prod_Fbzagare4CyeXH4mtJ5zUjpy`)

### Erro: "Unauthorized"
**Solução**: API Key já está configurada corretamente

### Sem logs no Cloudflare
**Solução**: Verifique conexão com internet do dispositivo

## 📈 Dashboard AbacatePay

Veja os checkouts criados:
```
https://app.abacatepay.com/checkouts
```

Cada transação aparece com:
- ID do checkout
- Valor
- Status (PENDING, PAID, EXPIRED)
- Metadata (tipo, bandeira, etc)

## ✅ Checklist de Teste

- [ ] APK instalado (versão 14:35:41)
- [ ] Logs limpos
- [ ] Logcat monitorando
- [ ] Valor ≥ R$ 1,00
- [ ] Cartão EMV aproximado
- [ ] Checkout criado no AbacatePay
- [ ] Logs aparecem no Cloudflare
- [ ] URL de pagamento gerada

---

**Maquininha virtual processando pagamentos reais!** 🚀💳
