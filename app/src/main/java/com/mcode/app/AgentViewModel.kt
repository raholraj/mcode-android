package com.mcode.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class Kind { USER, ASSISTANT, TOOL, ERROR }

data class ChatItem(val id: Long, val kind: Kind, val text: String, val detail: String? = null)

data class Approval(val text: String, val deferred: CompletableDeferred<Boolean>)

class AgentViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ConfigStore(app)
    private val runner = ToolRunner(app)
    private val llm = LlmClient()

    var config by mutableStateOf(store.load())
        private set
    val items = mutableStateListOf<ChatItem>()
    var busy by mutableStateOf(false)
        private set
    var approval by mutableStateOf<Approval?>(null)
        private set

    private var history: JSONArray = newHistory()
    private var notesLoaded = false
    private var nextId = 0L
    private var job: Job? = null

    private fun systemPrompt() =
        "You are mcode, an autonomous coding agent inside an Android app. Your tools run in a " +
            "Termux (Linux) shell with working directory ${config.workdir}. Inspect before you edit, " +
            "make minimal changes, verify by running commands, and keep replies short."

    private fun newHistory() = JSONArray().put(
        JSONObject().put("role", "system").put("content", systemPrompt())
    )

    private fun add(kind: Kind, text: String, detail: String? = null): Int {
        items.add(ChatItem(nextId++, kind, text, detail))
        return items.size - 1
    }

    fun saveConfig(c: Config) {
        val changed = c.workdir != config.workdir
        config = c
        store.save(c)
        if (changed) clear()
    }

    fun clear() {
        approval?.deferred?.complete(false)
        job?.cancel()
        items.clear()
        history = newHistory()
        notesLoaded = false
    }

    fun stop() {
        approval?.deferred?.complete(false)
        job?.cancel()
    }

    fun answer(ok: Boolean) {
        approval?.deferred?.complete(ok)
    }

    fun testTermux() {
        viewModelScope.launch {
            val idx = add(Kind.TOOL, "Termux test", "running...")
            val r = TermuxBridge.exec(getApplication(), "echo OK; pwd; whoami", config.workdir)
            items[idx] = items[idx].copy(detail = r.text())
        }
    }

    fun send(text: String) {
        val t = text.trim()
        if (busy || t.isEmpty()) return
        if (t == "/clear") {
            clear()
            return
        }
        add(Kind.USER, t)
        history.put(JSONObject().put("role", "user").put("content", t))
        busy = true
        job = viewModelScope.launch {
            try {
                loadProjectNotes()
                loop()
            } catch (e: CancellationException) {
                add(Kind.ERROR, "Stopped")
            } catch (e: Exception) {
                add(Kind.ERROR, e.message ?: "Unknown error")
            } finally {
                busy = false
            }
        }
    }

    /** Loads CLAUDE.md / AGENTS.md / MCODE.md from the project folder once per chat. */
    private suspend fun loadProjectNotes() {
        if (notesLoaded) return
        notesLoaded = true
        val r = TermuxBridge.exec(
            getApplication(),
            "cat MCODE.md CLAUDE.md AGENTS.md 2>/dev/null",
            config.workdir,
        )
        if (r.code == 0 && r.stdout.isNotBlank()) {
            history.put(
                JSONObject().put("role", "system")
                    .put("content", "Project notes:\n" + r.stdout.take(6000))
            )
        }
    }

    private suspend fun loop() {
        for (step in 1..25) {
            val msg = withContext(Dispatchers.IO) { llm.chat(config, history, ToolSchema.all) }
            val content = if (msg.isNull("content")) "" else msg.optString("content", "")
            val calls = msg.optJSONArray("tool_calls")
            val hasCalls = calls != null && calls.length() > 0

            val entry = JSONObject().put("role", "assistant")
                .put("content", if (content.isBlank() && hasCalls) JSONObject.NULL else content)
            if (hasCalls) entry.put("tool_calls", calls)
            history.put(entry)

            if (content.isNotBlank()) add(Kind.ASSISTANT, content)
            if (!hasCalls) return

            for (i in 0 until calls!!.length()) {
                val tc = calls.getJSONObject(i)
                val fn = tc.getJSONObject("function")
                val name = fn.getString("name")
                val args = try {
                    JSONObject(fn.optString("arguments", "{}").ifBlank { "{}" })
                } catch (e: Exception) {
                    JSONObject()
                }
                val title = runner.describe(name, args)
                val idx = add(Kind.TOOL, title, "waiting...")
                val allowed = !runner.needsApproval(name) || config.autoApprove || askApproval(title)
                val result = if (allowed) {
                    items[idx] = items[idx].copy(detail = "running...")
                    runner.run(name, args, config.workdir)
                } else {
                    "User denied this action."
                }
                items[idx] = items[idx].copy(detail = result)
                history.put(
                    JSONObject().put("role", "tool")
                        .put("tool_call_id", tc.optString("id"))
                        .put("content", result)
                )
            }
        }
        add(Kind.ERROR, "Step limit reached (25). Send another message to continue.")
    }

    private suspend fun askApproval(text: String): Boolean {
        val d = CompletableDeferred<Boolean>()
        approval = Approval(text, d)
        try {
            return d.await()
        } finally {
            approval = null
        }
    }
}
