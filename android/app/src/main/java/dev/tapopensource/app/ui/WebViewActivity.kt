package dev.tapopensource.app.ui

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import dev.tapopensource.app.nfc.EmvReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * WebView Activity que carrega a web app e expõe NFC nativo via JavaScript Interface
 */
class WebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var nfcAdapter: NfcAdapter? = null
    private var pendingNfcRead: PendingNfcRead? = null

    data class PendingNfcRead(val amount: Int, val type: String)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            
            // Expõe interface JavaScript
            addJavascriptInterface(AndroidNFCInterface(), "AndroidNFC")
        }

        setContentView(webView)

        // Carrega a web app (pode ser local ou remota)
        val webAppUrl = "https://tapopensource.pages.dev"
        webView.loadUrl(webAppUrl)

        // Inicializa NFC
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
    }

    override fun onResume() {
        super.onResume()
        enableNfcForegroundDispatch()
    }

    override fun onPause() {
        super.onPause()
        disableNfcForegroundDispatch()
    }

    private fun enableNfcForegroundDispatch() {
        nfcAdapter?.let { adapter ->
            val intent = Intent(this, javaClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
            val filters = arrayOf(IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED))
            val techLists = arrayOf(arrayOf(IsoDep::class.java.name))
            adapter.enableForegroundDispatch(this, pendingIntent, filters, techLists)
        }
    }

    private fun disableNfcForegroundDispatch() {
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }
            
            tag?.let { handleNfcTag(it) }
        }
    }

    private fun handleNfcTag(tag: Tag) {
        val pending = pendingNfcRead ?: return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isoDep = IsoDep.get(tag) ?: throw Exception("IsoDep não disponível")
                isoDep.connect()
                
                val reader = EmvReader(isoDep)
                val cardData = reader.readCard()
                
                isoDep.close()
                
                // Converte para JSON
                val json = JSONObject().apply {
                    put("pan", cardData.pan)
                    put("expiry", cardData.expiry)
                    put("holderName", cardData.holderName)
                    put("brand", cardData.brand)
                }
                
                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript(
                        "window.onNfcSuccess('${json.toString().replace("'", "\\'")}')",
                        null
                    )
                }
                
                Log.d(TAG, "Card read success: ${cardData.brand}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Card read error", e)
                
                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript(
                        "window.onNfcError('${e.message?.replace("'", "\\'")}')",
                        null
                    )
                }
            } finally {
                pendingNfcRead = null
            }
        }
    }

    /**
     * Interface JavaScript exposta para a web app
     */
    inner class AndroidNFCInterface {
        
        @JavascriptInterface
        fun startNfcRead(amount: Int, type: String) {
            Log.d(TAG, "startNfcRead called: amount=$amount, type=$type")
            pendingNfcRead = PendingNfcRead(amount, type)
        }
        
        @JavascriptInterface
        fun cancelNfcRead() {
            Log.d(TAG, "cancelNfcRead called")
            pendingNfcRead = null
        }
        
        @JavascriptInterface
        fun vibrate(patternJson: String) {
            try {
                // Parse JSON array [100, 50, 100]
                val pattern = patternJson
                    .trim('[', ']')
                    .split(",")
                    .map { it.trim().toLong() }
                    .toLongArray()
                
                @Suppress("DEPRECATION")
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (getSystemService(VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
                } else {
                    getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
                }
                
                vibrator.vibrate(pattern, -1)
            } catch (e: Exception) {
                Log.e(TAG, "Vibrate error", e)
            }
        }
    }

    companion object {
        private const val TAG = "WebViewActivity"
    }
}
