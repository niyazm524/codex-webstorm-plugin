package com.github.niyazm524.codexwebstormplugin.services

import com.intellij.openapi.components.service
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
    fun onAppServerExit(exitCode: Int, lastOutputLine: String?) {}
    fun onAppServerError(message: String) {}
}

class CodexAppServer(
    private val workingDirectory: String,
    private val listener: CodexAppServerListener?
) {
    private val settings = service<CodexSettingsState>()
    private var process: Process? = null
    @Volatile private var lastOutputLine: String? = null
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableFuture<JSONObject>>()

    fun start(): Result<Unit> {
        if (process != null) return Result.success(Unit)

        return runCatching {
            val executable =
                    settings.getOrDetectCodexExecutablePath()
                            ?: error(
                                    "Codex executable not found. Set it in Settings | Tools | Codex."
                            )
            val builder =
                    ProcessBuilder(executable, "app-server")
                            .directory(java.io.File(workingDirectory))
                            .redirectErrorStream(true)
            val env = builder.environment()
            val shellPath = resolveBootstrapPath()
            if (!shellPath.isNullOrBlank()) {
                val existingPath = env["PATH"].orEmpty()
                env["PATH"] =
                        if (existingPath.isBlank()) {
                            shellPath
                        } else {
                            "$shellPath:$existingPath"
                        }
            }
            applyExtraEnv(env)
            process = builder.start()

            val reader = BufferedReader(InputStreamReader(process!!.inputStream))
            ioExecutor.submit {
                reader.forEachLine { line ->
                    handleLine(line)
                }
            }
            Thread {
                        val exitCode = process?.waitFor() ?: return@Thread
                        process = null
                        listener?.onAppServerExit(exitCode, lastOutputLine)
                    }
                    .apply { isDaemon = true }
                    .start()

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
        try {
            active.outputStream.write((payload.toString() + "\n").toByteArray())
            active.outputStream.flush()
        } catch (error: Exception) {
            process = null
            listener?.onAppServerError(
                    "Codex app-server connection closed: ${error.message ?: "stream closed"}"
            )
        }
    }

    private fun handleLine(line: String) {
        if (line.isBlank()) return
        lastOutputLine = line
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

    private fun resolveBootstrapPath(): String? {
        val cached = settings.getBootstrapPath()
        if (!cached.isNullOrBlank()) return cached
        val detected = CodexExecutableLocator.detectShellPath()
        if (!detected.isNullOrBlank()) {
            settings.setBootstrapPath(detected)
        }
        return detected
    }

    private fun applyExtraEnv(env: MutableMap<String, String>) {
        val extra = settings.getExtraEnv().orEmpty()
        if (extra.isBlank()) return
        extra.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach
            val idx = trimmed.indexOf('=')
            if (idx <= 0) return@forEach
            val key = trimmed.substring(0, idx).trim()
            val value = trimmed.substring(idx + 1).trim()
            if (key.isNotBlank()) {
                env[key] = value
            }
        }
    }
}
