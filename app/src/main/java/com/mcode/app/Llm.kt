package com.mcode.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Works with any OpenAI-compatible /chat/completions endpoint. Call from a background thread. */
class LlmClient {
    fun chat(cfg: Config, messages: JSONArray, tools: JSONArray): JSONObject {
        val body = JSONObject()
            .put("model", cfg.model)
            .put("messages", messages)
            .put("tools", tools)
        val conn = URL(cfg.baseUrl.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 30_000
            conn.readTimeout = 180_000
            conn.setRequestProperty("Content-Type", "application/json")
            if (cfg.apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("HTTP $code: ${text.take(400)}")
            return JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        } finally {
            conn.disconnect()
        }
    }
}
