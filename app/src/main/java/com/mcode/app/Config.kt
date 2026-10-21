package com.mcode.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class Config(
    val baseUrl: String = "https://api.groq.com/openai/v1",
    val apiKey: String = "",
    val model: String = "llama-3.3-70b-versatile",
    val workdir: String = "/data/data/com.termux/files/home",
    val autoApprove: Boolean = false,
)

object Presets {
    val list = listOf(
        Triple("Groq (free)", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
        Triple("Gemini (free)", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash"),
        Triple("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
        Triple("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
        Triple("Ollama (local)", "http://localhost:11434/v1", "qwen2.5-coder"),
    )
}

class ConfigStore(context: Context) {
    private val prefs: SharedPreferences = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "mcode_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        context.getSharedPreferences("mcode_plain", Context.MODE_PRIVATE)
    }

    fun load(): Config {
        val d = Config()
        return Config(
            baseUrl = prefs.getString("baseUrl", d.baseUrl) ?: d.baseUrl,
            apiKey = prefs.getString("apiKey", d.apiKey) ?: d.apiKey,
            model = prefs.getString("model", d.model) ?: d.model,
            workdir = prefs.getString("workdir", d.workdir) ?: d.workdir,
            autoApprove = prefs.getBoolean("autoApprove", d.autoApprove),
        )
    }

    fun save(c: Config) {
        prefs.edit()
            .putString("baseUrl", c.baseUrl)
            .putString("apiKey", c.apiKey)
            .putString("model", c.model)
            .putString("workdir", c.workdir)
            .putBoolean("autoApprove", c.autoApprove)
            .apply()
    }
}
