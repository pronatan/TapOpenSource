package dev.tapopensource.app.gateway

import dev.tapopensource.app.log.LogClient
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * TapOpenSource — Gateway Client V2
 * AbacatePay integrado com logs detalhados
 */
object GatewayClientV2 {

    private const val ENDPOINT = "https://api.abacatepay.com/v2/checkouts/create"
    private const val API_KEY = "abc_prod_yeJaNm3pHDQGNREsDBKU4pat"
    private const val PRODUCT_ID = "prod_Fbzagare4CyeXH4mtJ5zUjpy"

    private val client = OkHttpClient.Builder()
        .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val JSON_TYPE = "application/json".toMediaType()

    data class ChargeResult(
        val success: Boolean,
        val transactionId: String?,
        val authCode: String?,
        val message: String,
        val checkoutUrl: String? = null,
    )

    fun charge(
        amountCents: Int,
        type: String,
        cardToken: String,
        source: String,
        brand: String = "Unknown",
        expiry: String = "N/A",
        holderName: String = "",
    ): ChargeResult {
        return try {
            // Produto tem preço R$ 0,01 - calcula quantity
            val productPrice = 1
            val quantity = maxOf(1, amountCents / productPrice)
            
            android.util.Log.d("Gateway", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            android.util.Log.d("Gateway", "🥑 ABACATEPAY - INICIANDO COBRANÇA")
            android.util.Log.d("Gateway", "Valor: R$ ${String.format("%.2f", amountCents / 100.0)}")
            android.util.Log.d("Gateway", "Tipo: ${if (type == "debit") "DÉBITO" else "CRÉDITO"}")
            android.util.Log.d("Gateway", "Bandeira: $brand")
            android.util.Log.d("Gateway", "Quantity: $quantity")
            android.util.Log.d("Gateway", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            
            LogClient.info("abacatepay:request_start", mapOf(
                "amount" to amountCents,
                "type" to type,
                "brand" to brand,
                "quantity" to quantity
            ))

            val payload = JSONObject().apply {
                put("items", org.json.JSONArray().put(
                    JSONObject().apply {
                        put("id", PRODUCT_ID)
                        put("quantity", quantity)
                    }
                ))
                put("methods", org.json.JSONArray().put("CARD"))
                put("externalId", "CARD-${System.currentTimeMillis()}")
                put("metadata", JSONObject().apply {
                    put("app", "TapOpenSource")
                    put("paymentType", type)
                    put("cardBrand", brand)
                    put("amount", amountCents)
                    put("description", "${if (type == "debit") "DÉBITO" else "CRÉDITO"} - $brand - R$ ${String.format("%.2f", amountCents / 100.0)}")
                })
            }

            val request = Request.Builder()
                .url(ENDPOINT)
                .post(payload.toString().toRequestBody(JSON_TYPE))
                .header("Authorization", "Bearer $API_KEY")
                .build()

            android.util.Log.d("Gateway", "🥑 Enviando para AbacatePay...")
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val data = JSONObject(body)
            
            android.util.Log.d("Gateway", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            android.util.Log.d("Gateway", "🥑 ABACATEPAY - RESPOSTA")
            android.util.Log.d("Gateway", "Status HTTP: ${response.code}")
            android.util.Log.d("Gateway", "Success: ${data.optBoolean("success")}")
            
            if (!response.isSuccessful) {
                val error = data.optString("error", "Erro ${response.code}")
                android.util.Log.e("Gateway", "❌ ERRO: $error")
                android.util.Log.d("Gateway", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                
                LogClient.error("abacatepay:http_error", mapOf(
                    "status" to response.code,
                    "error" to error,
                    "body" to body
                ))
                
                ChargeResult(false, null, null, error)
            } else {
                val success = data.optBoolean("success", false)
                if (!success) {
                    val error = data.optString("error", "Erro desconhecido")
                    android.util.Log.e("Gateway", "❌ FALHA: $error")
                    android.util.Log.d("Gateway", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    
                    LogClient.error("abacatepay:api_error", mapOf("error" to error))
                    ChargeResult(false, null, null, error)
                } else {
                    val dataObj = data.optJSONObject("data")
                    val txId = dataObj?.optString("id") ?: ""
                    val checkoutUrl = dataObj?.optString("url") ?: ""
                    val status = dataObj?.optString("status") ?: ""
                    
                    android.util.Log.d("Gateway", "✅ SUCESSO!")
                    android.util.Log.d("Gateway", "ID: $txId")
                    android.util.Log.d("Gateway", "Status: $status")
                    android.util.Log.d("Gateway", "URL: $checkoutUrl")
                    android.util.Log.d("Gateway", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    
                    LogClient.info("abacatepay:success", mapOf(
                        "transactionId" to txId,
                        "status" to status,
                        "checkoutUrl" to checkoutUrl,
                        "type" to type,
                        "amount" to amountCents
                    ))
                    
                    val msg = "Checkout criado - ${if (type == "debit") "DÉBITO" else "CRÉDITO"}"
                    ChargeResult(true, txId, status, msg, checkoutUrl)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Gateway", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            android.util.Log.e("Gateway", "❌ EXCEÇÃO: ${e.message}", e)
            android.util.Log.e("Gateway", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            
            LogClient.error("abacatepay:exception", mapOf(
                "message" to (e.message ?: "unknown"),
                "type" to e.javaClass.simpleName
            ))
            
            ChargeResult(false, null, null, e.message ?: "Erro de conexão")
        }
    }
}
