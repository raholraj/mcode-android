package com.mcode.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class ExecResult(val stdout: String, val stderr: String, val code: Int, val errmsg: String) {
    fun text(): String {
        val s = buildString {
            append(stdout)
            if (stderr.isNotBlank()) append(stderr)
            if (errmsg.isNotBlank()) append("\n[termux error] ").append(errmsg)
            if (code != 0) append("\n[exit code ").append(code).append("]")
        }
        val out = s.ifBlank { "(no output)" }
        return if (out.length > 8000) out.take(8000) + "\n...[truncated]" else out
    }
}

/** Runs shell commands inside Termux through its RUN_COMMAND intent. */
object TermuxBridge {
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<ExecResult>>()
    private val counter = AtomicInteger(1)
    private const val BASH = "/data/data/com.termux/files/usr/bin/bash"

    suspend fun exec(context: Context, command: String, workdir: String): ExecResult {
        val id = counter.getAndIncrement()
        val deferred = CompletableDeferred<ExecResult>()
        pending[id] = deferred

        val resultIntent = Intent(context, TermuxResultReceiver::class.java).putExtra("exec_id", id)
        val pi = PendingIntent.getBroadcast(
            context, id, resultIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE,
        )
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", BASH)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", workdir)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pi)
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            pending.remove(id)
            return ExecResult("", "", -1, "Termux not reachable: ${e.message}. Check README setup.")
        }
        return withTimeoutOrNull(120_000) { deferred.await() }
            ?: run {
                pending.remove(id)
                ExecResult("", "", -1, "Command timed out (120s)")
            }
    }

    fun complete(id: Int, result: ExecResult) {
        pending.remove(id)?.complete(result)
    }
}

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("exec_id", -1)
        val b = intent.getBundleExtra("result")
        val result = ExecResult(
            stdout = b?.getString("stdout").orEmpty(),
            stderr = b?.getString("stderr").orEmpty(),
            code = b?.getInt("exitCode", 0) ?: 0,
            errmsg = b?.getString("errmsg").orEmpty(),
        )
        TermuxBridge.complete(id, result)
    }
}
