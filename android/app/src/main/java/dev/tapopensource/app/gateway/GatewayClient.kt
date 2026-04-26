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
 */
object GatewayClient {

    // AbacatePay configuration
    // NOTA: Requer produtos pré-cadastrados no dashboard
    // Ative quando tiver produtos criados: https://app.abacatepay.com/products
    private val ENDPOINT: String? = null // null = modo mock
    // private val ENDPOINT = "https://api.abacatepay.com/v2/checkouts/create"
    private const val API_KEY = "abc_prod_yeJaNm3pHDQGNREsDBKU4pat"

    private val client = OkHttpClient.Builder()
        .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val JSON_TYPE = "application/json".toMediaType()

    data class ChargeResult(
        val success: Boolean,
        val transactionId: String?,
        val authCode: String?,
        val message: String,
        val checkoutUrl: String? = null, // URL do checkout AbacatePay
    )

    fun charge(
        amountCents: Int,
        type: String,          // "debit" | "credit"
        cardToken: String,
        source: String,
        brand: String = "Unknown",
        expiry: String = "N/A",
        holderName: String = "",
    ): ChargeResult {
        if (ENDPOINT == null) {
            LogClient.info("gateway:mock_charge", mapOf("amount" to amountCents, "type" to type))
            return mockCharge(amountCents, type)
        }

        return try {
            LogClient.info("gateway:charge_start", mapOf("amount" to amountCents, "type" to type))

            // AbacatePay checkout payload
            // IMPORTANTE: Requer produto pré-cadastrado no dashboard
            // Substitua "PRODUTO_ID_AQUI" pelo ID real do produto
            val payload = JSONObject().apply {
                put("items", org.json.JSONArray().put(
                    JSONObject().apply {
                        put("id", "PRODUTO_ID_AQUI") // ⚠️ SUBSTITUA pelo ID do produto
                        put("quantity", 1)
                    }
                ))
                put("methods", org.json.JSONArray().put("CARD"))
                put("externalId", "CARD-${System.currentTimeMillis()}")
                put("metadata", JSONObject().apply {
                    put("app", "TapOpenSource-Android")
                    put("version", "1.0.0")
                    put("paymentType", type)
                    put("cardBrand", brand)
                    put("cardToken", cardToken)
                    put("tokenSource", source)
                    put("holderName", holderName)
                    put("expiry", expiry)
                    put("amount", amountCents)
                    put("description", "${if (type == "debit") "Débito" else "Crédito"} - $brand - ${cardToken.take(6)}...${cardToken.takeLast(4)}")
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
                val checkoutUrl = data.optString("url")
                val status = data.optString("status")
                val auth = if (status == "PENDING") "PENDING" else status
                val msg = if (checkoutUrl.isNotEmpty()) "Checkout criado" else "Pagamento processado"
                LogClient.info("gateway:charge_approved", mapOf("transactionId" to txId, "checkoutUrl" to checkoutUrl))
                ChargeResult(true, txId, auth, msg, checkoutUrl)
            }
        } catch (e: Exception) {
            LogClient.error("gateway:fetch_error", mapOf("message" to (e.message ?: "unknown")))
            ChargeResult(false, null, null, e.message ?: "Erro de conexão")
        }
    }

    private fun mockCharge(amountCents: Int, type: String): ChargeResult {
        // Simula o payload que seria enviado ao AbacatePay
        val mockPayload = JSONObject().apply {
            put("items", org.json.JSONArray().put(
                JSONObject().apply {
                    put("id", "PRODUTO_ID_AQUI") // ⚠️ Criar produto no dashboard
                    put("quantity", 1)
                }
            ))
            put("methods", org.json.JSONArray().put("CARD"))
            put("externalId", "CARD-${System.currentTimeMillis()}")
            put("metadata", JSONObject().apply {
                put("app", "TapOpenSource")
                put("paymentType", type)
                put("amount", amountCents)
                put("description", "${if (type == "debit") "DÉBITO" else "CRÉDITO"} - R$ ${amountCents / 100.0}")
            })
        }
        
        android.util.Log.d("GatewayClient", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        android.util.Log.d("GatewayClient", "🥑 MOCK: Payload AbacatePay (Checkout API v2):")
        android.util.Log.d("GatewayClient", mockPayload.toString(2))
        android.util.Log.d("GatewayClient", "Endpoint: POST https://api.abacatepay.com/v2/checkouts/create")
        android.util.Log.d("GatewayClient", "Método: CARD (${if (type == "debit") "DÉBITO" else "CRÉDITO"})")
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
