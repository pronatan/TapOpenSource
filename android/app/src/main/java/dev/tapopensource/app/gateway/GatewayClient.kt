package dev.tapopensource.app.gateway

import dev.tapopensource.app.log.LogClient
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * TapOpenSource — Gateway Client
 * Plugável: troque ENDPOINT e API_KEY pelo seu provedor.
 * Mock ativo quando ENDPOINT == null.
 * 
 * Suporta:
 * - Mercado Pago (via MercadoPagoClient)
 * - Gateways genéricos via REST API
 * - Modo mock para testes
 */
object GatewayClient {

    // Gateway configuration
    private val GATEWAY_TYPE: GatewayType = GatewayType.MERCADO_PAGO  // Altere aqui
    private val ENDPOINT: String? = null  // Para gateways genéricos
    private const val API_KEY = "YOUR_API_KEY"  // Para gateways genéricos
    
    enum class GatewayType {
        MERCADO_PAGO,  // Usa MercadoPagoClient
        GENERIC,       // Usa ENDPOINT genérico
        MOCK           // Modo de teste
    }

    private val client = OkHttpClient.Builder()
        .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val JSON_TYPE = "application/json".toMediaType()

    data class ChargeResult(
        val success: Boolean,
        val transactionId: String?,
        val authCode: String?,
        val message: String,
    )

    fun charge(
        amountCents: Int,
        type: String,          // "debit" | "credit"
        cardToken: String,
        source: String,
        brand: String = "Unknown",
        expiry: String = "N/A",
        holderName: String = "",
        pan: String = "",      // PAN completo (necessário para Mercado Pago)
    ): ChargeResult {
        return when (GATEWAY_TYPE) {
            GatewayType.MERCADO_PAGO -> {
                // Usa integração com Mercado Pago
                if (pan.isEmpty()) {
                    LogClient.error("gateway:missing_pan", mapOf("message" to "PAN necessário para Mercado Pago"))
                    return ChargeResult(false, null, null, "Erro: dados do cartão incompletos")
                }
                
                val mpResult = MercadoPagoClient.charge(
                    amountCents = amountCents,
                    type = type,
                    pan = pan,
                    expiry = expiry,
                    holderName = holderName,
                    brand = brand
                )
                
                ChargeResult(
                    success = mpResult.success,
                    transactionId = mpResult.transactionId,
                    authCode = mpResult.authCode,
                    message = mpResult.message
                )
            }
            
            GatewayType.GENERIC -> {
                // Usa gateway genérico via REST API
                if (ENDPOINT == null) {
                    LogClient.warn("gateway:endpoint_not_configured")
                    return ChargeResult(false, null, null, "Gateway endpoint não configurado")
                }
                chargeGeneric(amountCents, type, cardToken, source, brand, expiry, holderName)
            }
            
            GatewayType.MOCK -> {
                // Modo mock para testes
                LogClient.info("gateway:mock_charge", mapOf("amount" to amountCents, "type" to type))
                mockCharge(amountCents, type, brand)
            }
        }
    }

    /**
     * Processa pagamento via gateway genérico
     */
    private fun chargeGeneric(
        amountCents: Int,
        type: String,
        cardToken: String,
        source: String,
        brand: String,
        expiry: String,
        holderName: String,
    ): ChargeResult {

    /**
     * Processa pagamento via gateway genérico
     */
    private fun chargeGeneric(
        amountCents: Int,
        type: String,
        cardToken: String,
        source: String,
        brand: String,
        expiry: String,
        holderName: String,
    ): ChargeResult {
        return try {
            LogClient.info("gateway:charge_start", mapOf("amount" to amountCents, "type" to type))

            // Gateway payload genérico
            val payload = JSONObject().apply {
                put("amount", amountCents)
                put("currency", "BRL")
                put("payment_method", type)
                put("capture", true)
                put("card", JSONObject().apply {
                    put("token", cardToken)
                    put("entry_mode", "contactless_nfc")
                    put("token_source", source)
                    put("brand", brand)
                    put("expiry", expiry)
                    put("holder_name", holderName)
                })
                put("metadata", JSONObject().apply {
                    put("app", "TapOpenSource-Android")
                    put("version", "1.0.0")
                })
            }

            val request = Request.Builder()
                .url(ENDPOINT)
                .post(payload.toString().toRequestBody(JSON_TYPE))
                .header("Authorization", "Bearer $API_KEY")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val data = JSONObject(body)

            if (!response.isSuccessful) {
                val msg = data.optJSONObject("error")?.optString("message") 
                    ?: data.optString("message")
                    ?: "Erro ${response.code}"
                LogClient.warn("gateway:charge_declined", mapOf("status" to response.code, "message" to msg))
                ChargeResult(false, null, null, msg)
            } else {
                val txId = data.optString("id")
                val auth = data.optString("authorization_code").ifEmpty { data.optString("auth_code", "------") }
                LogClient.info("gateway:charge_approved", mapOf("transactionId" to txId, "authCode" to auth))
                ChargeResult(true, txId, auth, "Pagamento aprovado")
            }
        } catch (e: Exception) {
            LogClient.error("gateway:fetch_error", mapOf("message" to (e.message ?: "unknown")))
            ChargeResult(false, null, null, e.message ?: "Erro de conexão")
        }
    }

    private fun mockCharge(amountCents: Int, type: String, brand: String = "Unknown"): ChargeResult {
        // Simula processamento de pagamento
        val mockPayload = JSONObject().apply {
            put("amount", amountCents)
            put("currency", "BRL")
            put("payment_method", type)
            put("capture", true)
            put("metadata", JSONObject().apply {
                put("app", "TapOpenSource")
                put("paymentType", type)
                put("description", "${if (type == "debit") "DÉBITO" else "CRÉDITO"} - R$ ${amountCents / 100.0}")
            })
        }
        
        android.util.Log.d("GatewayClient", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        android.util.Log.d("GatewayClient", "💳 MOCK: Payload Gateway:")
        android.util.Log.d("GatewayClient", mockPayload.toString(2))
        android.util.Log.d("GatewayClient", "Método: ${if (type == "debit") "DÉBITO" else "CRÉDITO"}")
        android.util.Log.d("GatewayClient", "Valor: R$ ${String.format("%.2f", amountCents / 100.0)}")
        android.util.Log.d("GatewayClient", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        LogClient.info("gateway:mock_payload", mapOf(
            "amount" to amountCents,
            "type" to type,
            "payload" to mockPayload.toString()
        ))
        
        Thread.sleep((1200 + (Math.random() * 800).toLong()))
        val approved = Math.random() > 0.2
        return if (approved) {
            val txId = "MOCK-" + (1000000..9999999).random()
            val auth = (100000..999999).random().toString()
            LogClient.info("gateway:mock_approved", mapOf("amount" to amountCents, "type" to type))
            ChargeResult(true, txId, auth, "Pagamento ${if (type == "debit") "DÉBITO" else "CRÉDITO"} aprovado (mock)")
        } else {
            LogClient.info("gateway:mock_declined", mapOf("amount" to amountCents, "type" to type))
            ChargeResult(false, null, null, "Transação recusada pelo emissor (mock)")
        }
    }
}
