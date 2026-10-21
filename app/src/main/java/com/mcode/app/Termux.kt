package com.mcode.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Runs a command in Termux via RUN_COMMAND intent and waits for the result broadcast.
 */
object Termux {
    private const val TERMUX_PACKAGE = "com.termux"
    private const val RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val EXTRA_COMMAND = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    private const val EXTRA_RESULT_RECEIVER = "com.termux.RUN_COMMAND_RESULT_RECEIVER"

    suspend fun run(context: Context, command: String, workDir: String? = null): String =
        suspendCancellableCoroutine { cont ->
            val resultAction = "com.mcode.app.TERMUX_RESULT_${System.currentTimeMillis()}"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    try {
                        context.unregisterReceiver(this)
                    } catch (_: Exception) {}
                    val stdout = intent.getStringExtra("stdout") ?: ""
                    val stderr = intent.getStringExtra("stderr") ?: ""
                    val exit = intent.getIntExtra("exitCode", -1)
                    val out = buildString {
                        if (stdout.isNotBlank()) append(stdout)
                        if (stderr.isNotBlank()) {
                            if (isNotEmpty()) append("\n")
                            append("[stderr]\n").append(stderr)
                        }
                        if (exit != 0) {
                            if (isNotEmpty()) append("\n")
                            append("[exit $exit]")
                        }
                    }
                    if (cont.isActive) cont.resume(out.ifBlank { "(no output, exit $exit)" })
                }
            }

            // Simplified: many Termux versions expect a PendingIntent for results.
            // For robustness we also try direct broadcast path used by some plugins.
            val resultIntent = Intent(resultAction).setPackage(context.packageName)
            val pending = PendingIntent.getBroadcast(
                context, 0, resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )

            try {
                context.registerReceiver(receiver, android.content.IntentFilter(resultAction), Context.RECEIVER_NOT_EXPORTED)
            } catch (e: Exception) {
                if (cont.isActive) cont.resume("registerReceiver failed: ${e.message}")
                return@suspendCancellableCoroutine
            }

            val intent = Intent(RUN_COMMAND).apply {
                setPackage(TERMUX_PACKAGE)
                putExtra(EXTRA_COMMAND, "/data/data/com.termux/files/usr/bin/bash")
                putExtra(EXTRA_ARGUMENTS, arrayOf("-c", command))
                if (workDir != null) putExtra(EXTRA_WORKDIR, workDir)
                putExtra(EXTRA_BACKGROUND, true)
                putExtra(EXTRA_SESSION_ACTION, 0)
                putExtra("com.termux.RUN_COMMAND_RESULT_DIRECTORY", context.cacheDir.absolutePath)
                putExtra("com.termux.RUN_COMMAND_RESULT_SINGLE_FILE", true)
                putExtra("com.termux.RUN_COMMAND_RESULT_FILE_BASENAME", "mcode_result")
                putExtra(EXTRA_RESULT_RECEIVER, pending)
            }

            try {
                context.startService(intent) // some builds use startService
            } catch (_: Exception) {
                try {
                    context.sendBroadcast(intent)
                } catch (e: Exception) {
                    try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
                    if (cont.isActive) cont.resume("Termux launch failed: ${e.message}. Is Termux installed and permission granted?")
                }
            }

            cont.invokeOnCancellation {
                try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            }
        }
}

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Placeholder; results handled by dynamic receivers in Termux.run
    }
}
