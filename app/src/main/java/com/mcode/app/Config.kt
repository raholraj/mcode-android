package com.mcode.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class LlmConfig(
    val baseUrl: String = "https://api.groq.com/openai/v1",
    val apiKey: String = "",
    val model: String = "llama-3.3-70b-versatile",
    val systemPrompt: String = DEFAULT_SYSTEM,
)

const val DEFAULT_SYSTEM = """You are mcode, an AI coding agent on Android. You can run shell commands via Termux and read/write files.
Be concise. Prefer small, focused changes. When you need to run a command or touch files, use the tools.
"""

class ConfigStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "mcode_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load(): LlmConfig = LlmConfig(
        baseUrl = prefs.getString("baseUrl", LlmConfig().baseUrl) ?: LlmConfig().baseUrl,
        apiKey = prefs.getString("apiKey", "") ?: "",
        model = prefs.getString("model", LlmConfig().model) ?: LlmConfig().model,
        systemPrompt = prefs.getString("systemPrompt", DEFAULT_SYSTEM) ?: DEFAULT_SYSTEM,
    )

    fun save(cfg: LlmConfig) {
        prefs.edit()
            .putString("baseUrl", cfg.baseUrl)
            .putString("apiKey", cfg.apiKey)
            .putString("model", cfg.model)
            .putString("systemPrompt", cfg.systemPrompt)
            .apply()
    }
}
