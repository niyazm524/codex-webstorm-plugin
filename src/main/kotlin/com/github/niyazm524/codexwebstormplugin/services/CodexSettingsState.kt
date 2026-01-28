package com.github.niyazm524.codexwebstormplugin.services

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Files
import java.nio.file.Paths

@State(name = "CodexSettings", storages = [Storage("codex.xml")])
class CodexSettingsState : PersistentStateComponent<CodexSettingsState.State> {
    data class State(
            var codexExecutablePath: String? = null,
            var extraEnv: String? = null,
            var bootstrapPath: String? = null,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun getCodexExecutablePath(): String? = state.codexExecutablePath

    fun setCodexExecutablePath(path: String?) {
        state.codexExecutablePath = path?.trim().takeIf { !it.isNullOrBlank() }
    }

    fun getExtraEnv(): String? = state.extraEnv

    fun setExtraEnv(value: String?) {
        state.extraEnv = value?.trim().takeIf { !it.isNullOrBlank() }
    }

    fun getBootstrapPath(): String? = state.bootstrapPath

    fun setBootstrapPath(value: String?) {
        state.bootstrapPath = value?.trim().takeIf { !it.isNullOrBlank() }
    }

    fun getOrDetectCodexExecutablePath(): String? {
        val current = state.codexExecutablePath
        if (!current.isNullOrBlank() && isExecutable(current)) return current
        val detected = CodexExecutableLocator.detectCodexExecutable()
        if (detected != null) {
            thisLogger().info("Detected codex executable at $detected")
            state.codexExecutablePath = detected
            return detected
        }
        return null
    }

    private fun isExecutable(path: String): Boolean {
        val normalized = FileUtil.expandUserHome(path)
        val file = Paths.get(normalized)
        return Files.isExecutable(file)
    }
}
