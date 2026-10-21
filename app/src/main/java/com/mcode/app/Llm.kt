package com.mcode.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LlmClient(private val config: LlmConfig) {

    suspend fun chat(messages: List<Map<String, Any>>, tools: JSONArray? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("model", config.model)
                put("messages", JSONArray(messages.map { JSONObject(it) }))
                if (tools != null) {
                    put("tools", tools)
                    put("tool_choice", "auto")
                }
            }

            val url = URL(config.baseUrl.trimEnd('/') + "/chat/completions")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                doOutput = true
                connectTimeout = 60_000
                readTimeout = 120_000
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream.bufferedReader().readText()
            if (code !in 200..299) throw RuntimeException("LLM $code: $text")
            JSONObject(text)
        }
}
