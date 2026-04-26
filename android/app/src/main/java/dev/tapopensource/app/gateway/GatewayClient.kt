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

    // Configure aqui seu gateway (Cielo, Stone, Stripe, etc.)
    private val ENDPOINT: String? = null
    private const val API_KEY = "YOUR_API_KEY"

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
    ): ChargeResult {
        if (ENDPOINT == null) {
            LogClient.info("gateway:mock_charge", mapOf("amount" to amountCents, "type" to type))
            return mockCharge(amountCents, type)
        }

        return try {
            LogClient.info("gateway:charge_start", mapOf("amount" to amountCents, "type" to type))

            val payload = JSONObject().apply {
                put("amount", amountCents)
                put("currency", "BRL")
                put("payment_method", type)
                put("capture", true)
                put("card", JSONObject().apply {
                    put("token", cardToken)
                    put("entry_mode", "contactless_nfc")
                    put("token_source", source)
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
                .header("X-App-Source", "TapOpenSource-Android")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val data = JSONObject(body)

            if (!response.isSuccessful) {
                val msg = data.optJSONObject("error")?.optString("message") ?: "Erro ${response.code}"
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
