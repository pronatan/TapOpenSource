package dev.tapopensource.app.ui

import android.nfc.tech.IsoDep
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tapopensource.app.gateway.GatewayClient
import dev.tapopensource.app.log.LogClient
import dev.tapopensource.app.nfc.EmvException
import dev.tapopensource.app.nfc.EmvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AppState {
    object Amount : AppState()
    object WaitingNfc : AppState()
    object Processing : AppState()
    data class Result(
        val success: Boolean,
        val message: String,
        val transactionId: String? = null,
        val authCode: String? = null,
        val amountCents: Int = 0,
        val type: String = "",
    ) : AppState()
}

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow<AppState>(AppState.Amount)
    val state: StateFlow<AppState> = _state

    var amountCents = 0
        private set
    var paymentType = "debit"
        private set

    fun appendDigit(digit: Int) {
        if (amountCents > 9_999_999) return
        amountCents = amountCents * 10 + digit
    }

    fun backspace() { amountCents /= 10 }
    fun clear()     { amountCents = 0 }
    fun setType(type: String) { paymentType = type }

    fun startCharge() {
        if (amountCents == 0) return
        LogClient.info("app:charge_initiated", mapOf("amount" to amountCents, "type" to paymentType))
        _state.value = AppState.WaitingNfc
    }

    fun cancelNfc() { _state.value = AppState.Amount }

    fun onNfcTag(isoDep: IsoDep) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = AppState.Processing

            try {
                isoDep.connect()
                LogClient.info("nfc:tag_connected", mapOf("maxTransceiveLength" to isoDep.maxTransceiveLength))

                val card = EmvReader.read(isoDep)
                LogClient.info("nfc:emv_read_success", mapOf(
                    "aid"     to card.aid,
                    "pan"     to card.pan,
                    "expiry"  to card.expiry,
                    "name"    to card.cardholderName,
                    "source"  to "isodep",
                ))

                // Token = PAN mascarado + expiry em base64 (nunca envia PAN completo)
                val token = android.util.Base64.encodeToString(
                    "${card.pan}|${card.expiry}".toByteArray(), android.util.Base64.NO_WRAP
                )
                
                // Detecta bandeira pelo AID
                val brand = when {
                    card.aid.startsWith("A0000000031010") || card.aid.startsWith("A0000000032010") -> "Visa"
                    card.aid.startsWith("A0000000041010") -> "Mastercard"
                    card.aid.startsWith("A0000000043060") -> "Maestro"
                    card.aid.startsWith("A0000001523010") -> "Elo"
                    else -> "Unknown"
                }

                val result = GatewayClient.charge(
                    amountCents = amountCents,
                    type        = paymentType,
                    cardToken   = token,
                    source      = "emv_contactless",
                    brand       = brand,
                    expiry      = card.expiry,
                    holderName  = card.cardholderName,
                )

                _state.value = AppState.Result(
                    success       = result.success,
                    message       = result.message,
                    transactionId = result.transactionId,
                    authCode      = result.authCode,
                    amountCents   = amountCents,
                    type          = paymentType,
                )

            } catch (e: EmvException) {
                LogClient.error("nfc:emv_error", mapOf("message" to e.message.orEmpty()))
                _state.value = AppState.Result(false, "Erro ao ler cartão: ${e.message}")
            } catch (e: Exception) {
                LogClient.error("nfc:tag_error", mapOf("message" to e.message.orEmpty()))
                _state.value = AppState.Result(false, "Aproxime novamente devagar.")
            } finally {
                try { isoDep.close() } catch (_: Exception) {}
            }
        }
    }

    fun reset() {
        amountCents = 0
        _state.value = AppState.Amount
    }
}
