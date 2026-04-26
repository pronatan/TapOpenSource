package dev.tapopensource.app.ui

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import dev.tapopensource.app.R
import dev.tapopensource.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setupNumpad()
        setupTypeToggle()
        setupButtons()
        observeState()
    }

    // ── NFC Foreground Dispatch ───────────────────────────────

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        nfcAdapter?.enableForegroundDispatch(this, pending, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == NfcAdapter.ACTION_TECH_DISCOVERED ||
            intent.action == NfcAdapter.ACTION_TAG_DISCOVERED) {
            val tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, android.nfc.Tag::class.java)
                ?: return
            val isoDep = IsoDep.get(tag) ?: return
            vibrate(longArrayOf(0, 60, 40, 60))
            vm.onNfcTag(isoDep)
        }
    }

    // ── UI Setup ──────────────────────────────────────────────

    private fun setupNumpad() {
        // Mapeia IDs dos botões do numpad diretamente via findViewById
        val digitMap = mapOf(
            R.id.key0 to 0, R.id.key1 to 1, R.id.key2 to 2,
            R.id.key3 to 3, R.id.key4 to 4, R.id.key5 to 5,
            R.id.key6 to 6, R.id.key7 to 7, R.id.key8 to 8,
            R.id.key9 to 9
        )
        digitMap.forEach { (id, digit) ->
            findViewById<Button>(id).setOnClickListener {
                vm.appendDigit(digit); updateAmount()
            }
        }
        findViewById<Button>(R.id.key_backspace).setOnClickListener { vm.backspace(); updateAmount() }
        findViewById<Button>(R.id.key_clear).setOnClickListener    { vm.clear();     updateAmount() }
    }

    private fun setupTypeToggle() {
        binding.btnDebit.setOnClickListener {
            vm.setType("debit")
            binding.btnDebit.isSelected  = true
            binding.btnCredit.isSelected = false
        }
        binding.btnCredit.setOnClickListener {
            vm.setType("credit")
            binding.btnDebit.isSelected  = false
            binding.btnCredit.isSelected = true
        }
        binding.btnDebit.isSelected = true
    }

    private fun setupButtons() {
        binding.btnCharge.setOnClickListener {
            vibrate(longArrayOf(0, 80))
            vm.startCharge()
        }
        binding.btnCancelNfc.setOnClickListener { vm.cancelNfc() }
        binding.btnNewCharge.setOnClickListener  { vm.reset(); updateAmount() }
        
        // Botão para abrir modo Web
        findViewById<Button>(R.id.btn_web_mode).setOnClickListener {
            startActivity(Intent(this, WebViewActivity::class.java))
        }
    }

    // ── State Observer ────────────────────────────────────────

    private fun observeState() {
        lifecycleScope.launch {
            vm.state.collect { state ->
                binding.screenAmount.visibility     = View.GONE
                binding.screenNfc.visibility        = View.GONE
                binding.screenProcessing.visibility = View.GONE
                binding.screenResult.visibility     = View.GONE

                when (state) {
                    is AppState.Amount -> {
                        binding.screenAmount.visibility = View.VISIBLE
                        updateAmount()
                    }
                    is AppState.WaitingNfc -> {
                        binding.screenNfc.visibility = View.VISIBLE
                        binding.nfcStatus.text = "Aproxime o cartão..."
                        binding.nfcPulse.startAnimation()
                    }
                    is AppState.Processing -> {
                        binding.nfcPulse.stopAnimation()
                        binding.screenProcessing.visibility = View.VISIBLE
                    }
                    is AppState.Result -> {
                        binding.nfcPulse.stopAnimation()
                        binding.screenResult.visibility = View.VISIBLE
                        renderResult(state)
                        if (state.success) vibrate(longArrayOf(0, 100, 50, 100, 50, 200))
                        else               vibrate(longArrayOf(0, 200, 100, 200))
                    }
                }
            }
        }
    }

    private fun updateAmount() {
        val value = vm.amountCents / 100.0
        val formatted = String.format(Locale("pt", "BR"), "%,.2f", value)
            .replace(".", "#")  // Troca ponto temporário
            .replace(",", ".")  // Vírgula vira ponto de milhar
            .replace("#", ",")  // Ponto temporário vira vírgula decimal
        binding.amountValue.text  = "R$ $formatted"
        
        // Cor: cinza quando vazio, preto quando tem valor
        if (vm.amountCents == 0) {
            binding.amountValue.setTextColor(0xFFD1D5DB.toInt()) // Cinza claro
        } else {
            binding.amountValue.setTextColor(0xFF111827.toInt()) // Preto
        }
        
        // Botão Cobrar: desabilitado quando valor = 0
        binding.btnCharge.isEnabled = vm.amountCents > 0
    }

    private fun renderResult(r: AppState.Result) {
        binding.resultIcon.setImageResource(
            if (r.success) R.drawable.ic_check_circle
            else R.drawable.ic_x_circle
        )
        binding.resultTitle.text   = if (r.success) "Aprovado!" else "Recusado"
        binding.resultMessage.text = r.message

        if (r.success && r.transactionId != null) {
            binding.resultDetails.visibility = View.VISIBLE
            fun row(rowId: Int, label: String, value: String) {
                val container = binding.root.findViewById<View>(rowId)
                container.findViewById<android.widget.TextView>(R.id.row_label).text = label
                container.findViewById<android.widget.TextView>(R.id.row_value).text = value
            }
            val valueFormatted = String.format(Locale("pt", "BR"), "%,.2f", r.amountCents / 100.0)
                .replace(".", "#").replace(",", ".").replace("#", ",")
            row(R.id.row_amount, "Valor", "R$ $valueFormatted")
            row(R.id.row_type,   "Tipo",   if (r.type == "debit") "Débito" else "Crédito")
            row(R.id.row_auth,   "Autorização", r.authCode ?: "------")
            row(R.id.row_txid,   "ID",     r.transactionId)
        } else {
            binding.resultDetails.visibility = View.GONE
        }
    }

    // ── Vibration ─────────────────────────────────────────────

    private fun vibrate(pattern: LongArray) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                getSystemService<VibratorManager>()?.defaultVibrator
                    ?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                getSystemService<Vibrator>()?.vibrate(pattern, -1)
            }
        } catch (_: Exception) {}
    }
}
