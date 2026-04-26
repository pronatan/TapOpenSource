package dev.tapopensource.app.log

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * TapOpenSource — Log Client
 * Envia eventos para o Cloudflare Worker de logs (fire-and-forget).
 */
object LogClient {

    private const val ENDPOINT =
        "https://tapopensource-logs.natanaelrodriguesfernandes521.workers.dev/log"

    private val client = OkHttpClient()
    private val JSON_TYPE = "application/json".toMediaType()

    fun info(event: String, data: Map<String, Any> = emptyMap()) = send("info", event, data)
    fun warn(event: String, data: Map<String, Any> = emptyMap()) = send("warn", event, data)
    fun error(event: String, data: Map<String, Any> = emptyMap()) = send("error", event, data)

    private fun send(level: String, event: String, data: Map<String, Any>) {
        try {
            val body = JSONObject().apply {
                put("level", level)
                put("event", event)
                put("data", JSONObject(data))
            }.toString().toRequestBody(JSON_TYPE)

            val request = Request.Builder()
                .url(ENDPOINT)
                .post(body)
                .build()

            // Fire-and-forget — não bloqueia a thread principal
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) { /* silencia */ }
                override fun onResponse(call: Call, response: Response) { response.close() }
            })
        } catch (_: Exception) {}
    }
}
