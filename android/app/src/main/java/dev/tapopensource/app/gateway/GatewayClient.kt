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
    private val ENDPOINT: String? = "https://api.abacatepay.com/v1/billing"
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
        val pixUrl: String? = null, // URL para pagamento PIX
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

            // AbacatePay billing payload
            val payload = JSONObject().apply {
                put("frequency", "ONE_TIME")
                put("methods", org.json.JSONArray().put("PIX"))
                put("products", org.json.JSONArray().put(
                    JSONObject().apply {
                        put("externalId", "CARD-${System.currentTimeMillis()}")
                        put("name", "Pagamento via $brand")
                        put("description", "${if (type == "debit") "Débito" else "Crédito"} - ${cardToken.take(6)}...${cardToken.takeLast(4)} - $expiry")
                        put("quantity", 1)
                        put("price", amountCents)
                    }
                ))
                put("metadata", JSONObject().apply {
                    put("app", "TapOpenSource-Android")
                    put("version", "1.0.0")
                    put("paymentType", type)
                    put("cardBrand", brand)
                    put("cardToken", cardToken)
                    put("tokenSource", source)
                    put("holderName", holderName)
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
                val pixUrl = data.optString("url")
                val auth = if (pixUrl.isNotEmpty()) "PIX-PENDING" else "------"
                val msg = if (pixUrl.isNotEmpty()) "Cobrança PIX criada" else "Pagamento aprovado"
                LogClient.info("gateway:charge_approved", mapOf("transactionId" to txId, "pixUrl" to pixUrl))
                ChargeResult(true, txId, auth, msg, pixUrl)
            }
        } catch (e: Exception) {
            LogClient.error("gateway:fetch_error", mapOf("message" to (e.message ?: "unknown")))
            ChargeResult(false, null, null, e.message ?: "Erro de conexão")
        }
    }

    private fun mockCharge(amountCents: Int, type: String): ChargeResult {
        Thread.sleep((1200 + (Math.random() * 800).toLong()))
        val approved = Math.random() > 0.2
        return if (approved) {
            val txId = "MOCK-" + (1000000..9999999).random()
            val auth = (100000..999999).random().toString()
            LogClient.info("gateway:mock_approved", mapOf("amount" to amountCents, "type" to type))
            ChargeResult(true, txId, auth, "Pagamento aprovado (mock)")
        } else {
            LogClient.info("gateway:mock_declined", mapOf("amount" to amountCents, "type" to type))
            ChargeResult(false, null, null, "Transação recusada pelo emissor (mock)")
        }
    }
}
