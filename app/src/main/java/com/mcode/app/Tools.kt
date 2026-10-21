package com.mcode.app

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

object ToolSchema {
    private fun tool(name: String, desc: String, vararg params: String): JSONObject {
        val props = JSONObject()
        val req = JSONArray()
        params.forEach {
            props.put(it, JSONObject().put("type", "string"))
            req.put(it)
        }
        val fn = JSONObject()
            .put("name", name)
            .put("description", desc)
            .put("parameters", JSONObject().put("type", "object").put("properties", props).put("required", req))
        return JSONObject().put("type", "function").put("function", fn)
    }

    val all: JSONArray = JSONArray()
        .put(tool("bash", "Run a shell command in Termux (working dir is the project folder)", "command"))
        .put(tool("read_file", "Read a text file", "path"))
        .put(tool("write_file", "Create or overwrite a file", "path", "content"))
        .put(tool("edit_file", "Replace one exact, unique string in a file", "path", "old", "new"))
        .put(tool("list_dir", "List a directory (use '.' for current)", "path"))
        .put(tool("grep", "Search text recursively in a path", "pattern", "path"))
}

class ToolRunner(private val ctx: Context) {
    fun needsApproval(name: String) = name == "bash" || name == "write_file" || name == "edit_file"

    fun describe(name: String, a: JSONObject): String = when (name) {
        "bash" -> "$ " + a.optString("command")
        "read_file" -> "read " + a.optString("path")
        "write_file" -> "write " + a.optString("path") + " (" + a.optString("content").length + " chars)"
        "edit_file" -> "edit " + a.optString("path")
        "list_dir" -> "ls " + a.optString("path")
        "grep" -> "grep " + a.optString("pattern") + " in " + a.optString("path")
        else -> name
    }

    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    private suspend fun writeFile(path: String, content: String, wd: String): ExecResult {
        val b64 = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
        val cmd = "mkdir -p \"\$(dirname " + q(path) + ")\" && printf '%s' " + q(b64) + " | base64 -d > " + q(path)
        return TermuxBridge.exec(ctx, cmd, wd)
    }

    suspend fun run(name: String, a: JSONObject, wd: String): String {
        return try {
            when (name) {
                "bash" -> TermuxBridge.exec(ctx, a.getString("command"), wd).text()
                "read_file" -> TermuxBridge.exec(ctx, "cat " + q(a.getString("path")), wd).text()
                "list_dir" -> TermuxBridge.exec(ctx, "ls -la " + q(a.optString("path", ".")), wd).text()
                "grep" -> TermuxBridge.exec(
                    ctx,
                    "grep -rnI --exclude-dir=.git -e " + q(a.getString("pattern")) + " " +
                        q(a.optString("path", ".")) + " | head -200",
                    wd,
                ).text()
                "write_file" -> {
                    val r = writeFile(a.getString("path"), a.getString("content"), wd)
                    if (r.code == 0) "OK" else r.text()
                }
                "edit_file" -> {
                    val path = a.getString("path")
                    val old = a.getString("old")
                    val new = a.getString("new")
                    val r = TermuxBridge.exec(ctx, "cat " + q(path), wd)
                    if (r.code != 0) return r.text()
                    if (old.isEmpty()) return "Error: 'old' is empty"
                    var count = 0
                    var i = r.stdout.indexOf(old)
                    while (i >= 0) {
                        count++
                        i = r.stdout.indexOf(old, i + old.length)
                    }
                    if (count != 1) return "Error: 'old' found $count times, must be exactly 1."
                    val w = writeFile(path, r.stdout.replace(old, new), wd)
                    if (w.code == 0) "OK" else w.text()
                }
                else -> "Unknown tool: $name"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
