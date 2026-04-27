package dev.tapopensource.app.gateway

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dev.tapopensource.app.log.LogClient
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * TapOpenSource — Mercado Pago Integration
 * 
 * Integração com Mercado Pago usando tokenização + API de pagamentos.
 * 
 * Fluxo:
 * 1. Tokeniza dados do cartão EMV via API do Mercado Pago
 * 2. Processa pagamento usando o token gerado
 * 
 * Documentação: https://www.mercadopago.com.br/developers/pt/docs
 */
object MercadoPagoClient {

    private const val TAG = "MercadoPagoClient"
    
    // Configuração - substitua com suas credenciais
    // Obtenha em: https://www.mercadopago.com.br/developers/panel/credentials
    private const val PUBLIC_KEY = "APP_USR-893fdc1d-857f-4e79-afba-54bd4f3b1c59"  // Chave pública (para tokenização)
    private const val ACCESS_TOKEN = "APP_USR-5963527441161067-042621-59b6a4ba5c8d30a1fba4fabcb652769f-1516341798"  // Access token (para pagamentos)
    
    // URLs da API do Mercado Pago
    private const val BASE_URL = "https://api.mercadopago.com"
    private const val CARD_TOKEN_URL = "$BASE_URL/v1/card_tokens"
    private const val PAYMENT_URL = "$BASE_URL/v1/payments"

    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val JSON_TYPE = "application/json".toMediaType()

    // Data classes para API do Mercado Pago
    
    data class CardTokenRequest(
        @SerializedName("card_number") val cardNumber: String,
        @SerializedName("expiration_month") val expirationMonth: String,
        @SerializedName("expiration_year") val expirationYear: String,
        @SerializedName("security_code") val securityCode: String = "000",  // CVV não disponível via NFC
        @SerializedName("cardholder") val cardholder: CardholderData,
    )

    data class CardholderData(
        val name: String,
        val identification: IdentificationData? = null,
    )

    data class IdentificationData(
        val type: String = "CPF",
        val number: String = "00000000000",  // Placeholder - idealmente coletar do usuário
    )

    data class CardTokenResponse(
        val id: String,
        @SerializedName("public_key") val publicKey: String,
        @SerializedName("card_id") val cardId: String?,
        @SerializedName("status") val status: String,
        @SerializedName("date_created") val dateCreated: String,
        @SerializedName("date_last_updated") val dateLastUpdated: String,
        @SerializedName("first_six_digits") val firstSixDigits: String?,
        @SerializedName("last_four_digits") val lastFourDigits: String?,
    )

    data class PaymentRequest(
        @SerializedName("transaction_amount") val transactionAmount: Double,
        val token: String,
        val description: String,
        val installments: Int = 1,
        @SerializedName("payment_method_id") val paymentMethodId: String,
        val payer: PayerData,
        @SerializedName("capture") val capture: Boolean = true,
        @SerializedName("statement_descriptor") val statementDescriptor: String = "TapOpenSource",
    )

    data class PayerData(
        val email: String = "customer@tapopensource.dev",  // Placeholder
        val identification: IdentificationData? = null,
    )

    data class PaymentResponse(
        val id: Long,
        val status: String,  // approved, rejected, pending, etc.
        @SerializedName("status_detail") val statusDetail: String,
        @SerializedName("authorization_code") val authorizationCode: String?,
        @SerializedName("transaction_amount") val transactionAmount: Double,
        @SerializedName("payment_method_id") val paymentMethodId: String,
        @SerializedName("payment_type_id") val paymentTypeId: String,
        @SerializedName("date_created") val dateCreated: String,
        @SerializedName("date_approved") val dateApproved: String?,
    )

    data class ErrorResponse(
        val message: String,
        val error: String?,
        val status: Int,
        val cause: List<ErrorCause>?,
    )

    data class ErrorCause(
        val code: String,
        val description: String,
    )

    data class ChargeResult(
        val success: Boolean,
        val transactionId: String?,
        val authCode: String?,
        val message: String,
    )

    /**
     * Processa um pagamento via Mercado Pago
     * 
     * @param amountCents Valor em centavos (ex: 1000 = R$ 10,00)
     * @param type Tipo de pagamento: "debit" ou "credit"
     * @param pan Número do cartão (PAN) extraído via NFC
     * @param expiry Data de validade no formato YYMMDD
     * @param holderName Nome do titular do cartão
     * @param brand Bandeira do cartão (Visa, Mastercard, etc.)
     */
    fun charge(
        amountCents: Int,
        type: String,
        pan: String,
        expiry: String,
        holderName: String,
        brand: String,
    ): ChargeResult {
        // Validação de credenciais
        if (PUBLIC_KEY == "YOUR_PUBLIC_KEY" || ACCESS_TOKEN == "YOUR_ACCESS_TOKEN") {
            LogClient.warn("mercadopago:credentials_not_configured")
            return ChargeResult(
                success = false,
                transactionId = null,
                authCode = null,
                message = "Credenciais do Mercado Pago não configuradas. Configure PUBLIC_KEY e ACCESS_TOKEN."
            )
        }

        try {
            LogClient.info("mercadopago:charge_start", mapOf(
                "amount" to amountCents,
                "type" to type,
                "brand" to brand
            ))

            // Passo 1: Tokenizar o cartão
            val cardToken = tokenizeCard(pan, expiry, holderName)
            if (cardToken == null) {
                return ChargeResult(
                    success = false,
                    transactionId = null,
                    authCode = null,
                    message = "Erro ao tokenizar cartão"
                )
            }

            LogClient.info("mercadopago:card_tokenized", mapOf("token" to cardToken))

            // Passo 2: Processar pagamento
            return processPayment(amountCents, type, cardToken, brand)

        } catch (e: Exception) {
            LogClient.error("mercadopago:charge_error", mapOf("message" to e.message.orEmpty()))
            Log.e(TAG, "Erro ao processar pagamento", e)
            return ChargeResult(
                success = false,
                transactionId = null,
                authCode = null,
                message = "Erro: ${e.message}"
            )
        }
    }

    /**
     * Tokeniza os dados do cartão via API do Mercado Pago
     */
    private fun tokenizeCard(pan: String, expiry: String, holderName: String): String? {
        try {
            // Parse expiry (formato YYMMDD)
            val expiryMonth = if (expiry.length >= 4) expiry.substring(2, 4) else "12"
            val expiryYear = if (expiry.length >= 2) "20${expiry.substring(0, 2)}" else "2025"

            val tokenRequest = CardTokenRequest(
                cardNumber = pan,
                expirationMonth = expiryMonth,
                expirationYear = expiryYear,
                cardholder = CardholderData(
                    name = holderName.ifEmpty { "CARDHOLDER" }
                )
            )

            val requestBody = gson.toJson(tokenRequest).toRequestBody(JSON_TYPE)
            val request = Request.Builder()
                .url(CARD_TOKEN_URL)
                .post(requestBody)
                .header("Authorization", "Bearer $PUBLIC_KEY")
                .header("Content-Type", "application/json")
                .build()

            Log.d(TAG, "Tokenizando cartão...")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Erro ao tokenizar: ${response.code} - $responseBody")
                LogClient.error("mercadopago:tokenize_error", mapOf(
                    "status" to response.code,
                    "response" to responseBody
                ))
                return null
            }

            val tokenResponse = gson.fromJson(responseBody, CardTokenResponse::class.java)
            Log.d(TAG, "Cartão tokenizado: ${tokenResponse.id}")
            return tokenResponse.id

        } catch (e: Exception) {
            Log.e(TAG, "Erro na tokenização", e)
            LogClient.error("mercadopago:tokenize_exception", mapOf("message" to e.message.orEmpty()))
            return null
        }
    }

    /**
     * Processa o pagamento usando o token do cartão
     */
    private fun processPayment(
        amountCents: Int,
        type: String,
        cardToken: String,
        brand: String,
    ): ChargeResult {
        try {
            val amountReais = amountCents / 100.0

            // Mapeia tipo de pagamento e bandeira para payment_method_id do Mercado Pago
            val paymentMethodId = mapPaymentMethod(type, brand)

            val paymentRequest = PaymentRequest(
                transactionAmount = amountReais,
                token = cardToken,
                description = "Pagamento via TapOpenSource NFC - ${if (type == "debit") "Débito" else "Crédito"}",
                installments = 1,
                paymentMethodId = paymentMethodId,
                payer = PayerData(),
                capture = true,
                statementDescriptor = "TapOpenSource"
            )

            val requestBody = gson.toJson(paymentRequest).toRequestBody(JSON_TYPE)
            val request = Request.Builder()
                .url(PAYMENT_URL)
                .post(requestBody)
                .header("Authorization", "Bearer $ACCESS_TOKEN")
                .header("Content-Type", "application/json")
                .header("X-Idempotency-Key", generateIdempotencyKey())
                .build()

            Log.d(TAG, "Processando pagamento de R$ $amountReais...")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Erro no pagamento: ${response.code} - $responseBody")
                val errorResponse = try {
                    gson.fromJson(responseBody, ErrorResponse::class.java)
                } catch (e: Exception) {
                    null
                }

                val errorMessage = errorResponse?.cause?.firstOrNull()?.description
                    ?: errorResponse?.message
                    ?: "Erro ${response.code}"

                LogClient.warn("mercadopago:payment_declined", mapOf(
                    "status" to response.code,
                    "message" to errorMessage
                ))

                return ChargeResult(
                    success = false,
                    transactionId = null,
                    authCode = null,
                    message = errorMessage
                )
            }

            val paymentResponse = gson.fromJson(responseBody, PaymentResponse::class.java)
            Log.d(TAG, "Resposta do pagamento: ${paymentResponse.status}")

            return when (paymentResponse.status) {
                "approved" -> {
                    LogClient.info("mercadopago:payment_approved", mapOf(
                        "transactionId" to paymentResponse.id.toString(),
                        "authCode" to (paymentResponse.authorizationCode ?: "N/A")
                    ))
                    ChargeResult(
                        success = true,
                        transactionId = paymentResponse.id.toString(),
                        authCode = paymentResponse.authorizationCode ?: "------",
                        message = "Pagamento aprovado"
                    )
                }
                "rejected" -> {
                    LogClient.warn("mercadopago:payment_rejected", mapOf(
                        "statusDetail" to paymentResponse.statusDetail
                    ))
                    ChargeResult(
                        success = false,
                        transactionId = paymentResponse.id.toString(),
                        authCode = null,
                        message = "Pagamento recusado: ${translateStatusDetail(paymentResponse.statusDetail)}"
                    )
                }
                "pending", "in_process" -> {
                    LogClient.info("mercadopago:payment_pending", mapOf(
                        "statusDetail" to paymentResponse.statusDetail
                    ))
                    ChargeResult(
                        success = false,
                        transactionId = paymentResponse.id.toString(),
                        authCode = null,
                        message = "Pagamento pendente: ${translateStatusDetail(paymentResponse.statusDetail)}"
                    )
                }
                else -> {
                    ChargeResult(
                        success = false,
                        transactionId = paymentResponse.id.toString(),
                        authCode = null,
                        message = "Status desconhecido: ${paymentResponse.status}"
                    )
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar pagamento", e)
            LogClient.error("mercadopago:payment_exception", mapOf("message" to e.message.orEmpty()))
            return ChargeResult(
                success = false,
                transactionId = null,
                authCode = null,
                message = "Erro: ${e.message}"
            )
        }
    }

    /**
     * Mapeia tipo de pagamento e bandeira para payment_method_id do Mercado Pago
     */
    private fun mapPaymentMethod(type: String, brand: String): String {
        return when {
            brand.equals("Visa", ignoreCase = true) && type == "debit" -> "debvisa"
            brand.equals("Visa", ignoreCase = true) && type == "credit" -> "visa"
            brand.equals("Mastercard", ignoreCase = true) && type == "debit" -> "debmaster"
            brand.equals("Mastercard", ignoreCase = true) && type == "credit" -> "master"
            brand.equals("Maestro", ignoreCase = true) -> "maestro"
            brand.equals("Elo", ignoreCase = true) && type == "debit" -> "debelo"
            brand.equals("Elo", ignoreCase = true) && type == "credit" -> "elo"
            else -> if (type == "debit") "debvisa" else "visa"  // Fallback
        }
    }

    /**
     * Traduz status_detail do Mercado Pago para mensagens amigáveis
     */
    private fun translateStatusDetail(statusDetail: String): String {
        return when (statusDetail) {
            "cc_rejected_insufficient_amount" -> "Saldo insuficiente"
            "cc_rejected_bad_filled_security_code" -> "Código de segurança inválido"
            "cc_rejected_bad_filled_date" -> "Data de validade inválida"
            "cc_rejected_bad_filled_other" -> "Dados do cartão inválidos"
            "cc_rejected_call_for_authorize" -> "Entre em contato com o banco"
            "cc_rejected_card_disabled" -> "Cartão desabilitado"
            "cc_rejected_duplicated_payment" -> "Pagamento duplicado"
            "cc_rejected_high_risk" -> "Transação de alto risco"
            "cc_rejected_max_attempts" -> "Limite de tentativas excedido"
            "cc_rejected_other_reason" -> "Recusado pelo banco"
            else -> statusDetail
        }
    }

    /**
     * Gera uma chave de idempotência única para evitar pagamentos duplicados
     */
    private fun generateIdempotencyKey(): String {
        return "${System.currentTimeMillis()}-${(1000..9999).random()}"
    }
}
