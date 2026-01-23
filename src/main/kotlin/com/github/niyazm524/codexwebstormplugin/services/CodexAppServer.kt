package com.github.niyazm524.codexwebstormplugin.services

import com.intellij.openapi.diagnostic.thisLogger
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

interface CodexAppServerListener {
    fun onNotification(method: String, params: JSONObject)
    fun onRequest(id: Int, method: String, params: JSONObject)
}

class CodexAppServer(
    private val workingDirectory: String,
    private val listener: CodexAppServerListener?
) {
    private var process: Process? = null
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableFuture<JSONObject>>()

    fun start(): Result<Unit> {
        if (process != null) return Result.success(Unit)

        return runCatching {
            val builder =
                    ProcessBuilder("codex", "app-server")
                            .directory(java.io.File(workingDirectory))
                            .redirectErrorStream(true)
            process = builder.start()

            val reader = BufferedReader(InputStreamReader(process!!.inputStream))
            ioExecutor.submit {
                reader.forEachLine { line ->
                    handleLine(line)
                }
            }

            sendInitialize()
            sendInitialized()
        }
    }

    fun stop() {
        runCatching {
            process?.destroy()
            process = null
        }
    }

    fun isRunning(): Boolean = process != null

    fun sendRequest(method: String, params: JSONObject): CompletableFuture<JSONObject> {
        val id = nextId.getAndIncrement()
        val future = CompletableFuture<JSONObject>()
        pending[id] = future
        sendJson(JSONObject().put("method", method).put("id", id).put("params", params))
        return future
    }

    fun sendNotification(method: String, params: JSONObject = JSONObject()) {
        sendJson(JSONObject().put("method", method).put("params", params))
    }

    fun respondToRequest(id: Int, result: JSONObject) {
        sendJson(JSONObject().put("id", id).put("result", result))
    }

    private fun sendInitialize() {
        val params =
                JSONObject()
                        .put(
                                "clientInfo",
                                JSONObject()
                                        .put("name", "codex_webstorm")
                                        .put("title", "Codex WebStorm Plugin")
                                        .put("version", "0.1.0")
                        )
        sendJson(JSONObject().put("method", "initialize").put("id", 0).put("params", params))
    }

    private fun sendInitialized() {
        sendNotification("initialized")
    }

    private fun sendJson(payload: JSONObject) {
        val active = process ?: return
        active.outputStream.write((payload.toString() + "\n").toByteArray())
        active.outputStream.flush()
    }

    private fun handleLine(line: String) {
        if (line.isBlank()) return
        try {
            val json = JSONObject(line)
            val id = json.optInt("id", -1)
            val method = json.optString("method", "")

            if (id != -1 && json.has("result")) {
                pending.remove(id)?.complete(json.getJSONObject("result"))
                return
            }

            if (id != -1 && json.has("error")) {
                val error = json.getJSONObject("error").optString("message", "Unknown error")
                pending.remove(id)?.completeExceptionally(IllegalStateException(error))
                return
            }

            if (method.isNotBlank()) {
                val params = json.optJSONObject("params") ?: JSONObject()
                if (id != -1) {
                    listener?.onRequest(id, method, params)
                } else {
                    listener?.onNotification(method, params)
                }
            } else {
                thisLogger().info("codex app-server: $line")
            }
        } catch (error: Exception) {
            thisLogger().warn("Failed to parse app-server line: $line", error)
        }
    }
}
