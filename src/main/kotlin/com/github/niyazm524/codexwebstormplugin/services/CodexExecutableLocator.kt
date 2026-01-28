package com.github.niyazm524.codexwebstormplugin.services

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.diagnostic.thisLogger
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.toList

object CodexExecutableLocator {
    fun detectCodexExecutable(): String? {
        findOnPath()?.let { return it }
        findInNvm()?.let { return it }
        findWithShell()?.let { return it }
        return null
    }

    fun detectShellPath(): String? {
        return runCatching {
                    val process =
                            ProcessBuilder("/bin/zsh", "-lc", "echo \$PATH").start()
                    val output = process.inputStream.bufferedReader().readText().trim()
                    if (output.isNotBlank()) output else null
                }
                .onFailure { error ->
                    thisLogger().debug("Failed to detect shell PATH", error)
                }
                .getOrNull()
    }

    private fun findOnPath(): String? {
        return PathEnvironmentVariableUtil.findInPath("codex")?.absolutePath
    }

    private fun findInNvm(): String? {
        val home = System.getProperty("user.home") ?: return null
        val nvmRoot = Paths.get(home, ".nvm", "versions", "node")
        if (!Files.isDirectory(nvmRoot)) return null
        val candidates =
                Files.list(nvmRoot).use { stream ->
                    stream.filter { Files.isDirectory(it) }
                            .toList()
                            .sortedWith { a, b ->
                                compareVersions(a.fileName.toString(), b.fileName.toString())
                            }
                }
        for (dir in candidates.asReversed()) {
            val candidate = dir.resolve("bin").resolve("codex")
            if (Files.isExecutable(candidate)) {
                return candidate.toString()
            }
        }
        return null
    }

    private fun findWithShell(): String? {
        return runCatching {
                    val process = ProcessBuilder("/bin/zsh", "-lc", "command -v codex").start()
                    val output = process.inputStream.bufferedReader().readText().trim()
                    if (output.isNotBlank()) output else null
                }
                .onFailure { error ->
                    thisLogger().debug("Failed to detect codex via shell", error)
                }
                .getOrNull()
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = parseVersion(left)
        val b = parseVersion(right)
        val max = maxOf(a.size, b.size)
        for (i in 0 until max) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    private fun parseVersion(value: String): List<Int> {
        val cleaned = value.trim().removePrefix("v")
        return cleaned.split(".").mapNotNull { it.toIntOrNull() }
    }
}
