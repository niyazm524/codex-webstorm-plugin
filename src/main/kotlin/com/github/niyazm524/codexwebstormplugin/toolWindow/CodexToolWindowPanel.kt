package com.github.niyazm524.codexwebstormplugin.toolWindow

import com.github.niyazm524.codexwebstormplugin.services.CodexAppServer
import com.github.niyazm524.codexwebstormplugin.services.CodexAppServerListener
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.wm.ToolWindow
import org.json.JSONArray
import org.json.JSONObject
import java.awt.CardLayout
import java.util.UUID
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

class CodexToolWindowPanel(toolWindow: ToolWindow) : CodexAppServerListener {

    private val log = thisLogger()
    private val messages = mutableListOf<ChatMessage>()
    private val assistantMessageIndexByItemId = mutableMapOf<String, Int>()
    private val toolMessageIndexByItemId = mutableMapOf<String, Int>()
    private var diffMessageId: String? = null
    private var threadId: String? = null
    private var activeTurnId: String? = null
    private var bulkRenderDepth = 0
    private var pendingChatTitle: String? = null
    private val chatSessions = mutableListOf<ChatSession>()
    private val workingDirectory = toolWindow.project.basePath ?: System.getProperty("user.dir")
    private val appServer = CodexAppServer(workingDirectory, this)
    private var appServerStarted = false

    private lateinit var messageRenderer: CodexMessageRenderer
    private lateinit var chatViewPanel: CodexChatViewPanel
    private val chatListPanel = CodexChatListPanel()
    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)

    fun getContent(): JComponent {
        messageRenderer = CodexMessageRenderer { chatViewPanel.messageContainerWidth() }
        chatViewPanel = CodexChatViewPanel(messageRenderer)
        chatViewPanel.setOnSend { text -> handleSend(text) }
        chatViewPanel.setOnBack { showChatList() }
        chatViewPanel.setOnNewChat { startNewChat() }
        chatViewPanel.setOnClear { clearChat() }
        chatViewPanel.setOnInterrupt { interruptTurn() }

        chatListPanel.setOnSelect { session -> openChat(session) }
        chatListPanel.setOnNewChat { startNewChat() }

        cardPanel.add(chatListPanel.build(), "list")
        cardPanel.add(chatViewPanel.build(), "chat")

        startAppServerIfNeeded()
        updateChatList()
        showChatView()

        return cardPanel
    }

    override fun onNotification(method: String, params: JSONObject) {
        when (method) {
            "thread/started" -> {
                val thread = params.optJSONObject("thread")
                threadId = thread?.optString("id") ?: threadId
            }
            "turn/started" -> {
                val turn = params.optJSONObject("turn")
                activeTurnId = turn?.optString("id") ?: activeTurnId
                runOnUi { chatViewPanel.setStreaming(true) }
            }
            "turn/completed" -> {
                val turn = params.optJSONObject("turn")
                val status = turn?.optString("status") ?: "unknown"
                runOnUi {
                    appendSystemMessage("Turn completed ($status).")
                    chatViewPanel.setStreaming(false)
                }
                activeTurnId = null
            }
            "item/started" -> handleItemStarted(params)
            "item/completed" -> handleItemCompleted(params)
            "item/agentMessage/delta" -> handleAgentMessageDelta(params)
            "item/commandExecution/outputDelta" -> handleCommandOutputDelta(params)
            "item/fileChange/outputDelta" -> handleFileChangeOutputDelta(params)
            "turn/diff/updated" -> handleDiffUpdated(params)
            "error" -> {
                val error = params.optJSONObject("error")
                val message = error?.optString("message") ?: "Unknown error"
                runOnUi {
                    appendSystemMessage("Codex error: $message")
                    chatViewPanel.setStreaming(false)
                }
                activeTurnId = null
            }
            else -> Unit
        }
    }

    override fun onRequest(id: Int, method: String, params: JSONObject) {
        when (method) {
            "item/commandExecution/requestApproval" -> {
                val command = params.optString("parsedCmd", params.optString("command", ""))
                val reason = params.optString("reason", "")
                val prompt =
                        if (reason.isBlank()) {
                            "Approve command execution: $command"
                        } else {
                            "Approve command execution: $command\nReason: $reason"
                        }
                runOnUi {
                    appendActionMessage(
                            MessageKind.TOOL,
                            prompt,
                            listOf(
                                    MessageAction("Approve") {
                                        appServer.respondToRequest(
                                                id,
                                                JSONObject().put("decision", "accept")
                                        )
                                    },
                                    MessageAction("Deny") {
                                        appServer.respondToRequest(
                                                id,
                                                JSONObject().put("decision", "decline")
                                        )
                                    }
                            )
                    )
                }
            }
            "item/fileChange/requestApproval" -> {
                val reason = params.optString("reason", "")
                val prompt =
                        if (reason.isBlank()) {
                            "Approve file changes?"
                        } else {
                            "Approve file changes?\nReason: $reason"
                        }
                runOnUi {
                    appendActionMessage(
                            MessageKind.TOOL,
                            prompt,
                            listOf(
                                    MessageAction("Approve") {
                                        appServer.respondToRequest(
                                                id,
                                                JSONObject().put("decision", "accept")
                                        )
                                    },
                                    MessageAction("Deny") {
                                        appServer.respondToRequest(
                                                id,
                                                JSONObject().put("decision", "decline")
                                        )
                                    }
                            )
                    )
                }
            }
            else -> {
                appServer.respondToRequest(id, JSONObject().put("decision", "decline"))
            }
        }
    }

    override fun onAppServerExit(exitCode: Int, lastOutputLine: String?) {
        runOnUi {
            val details =
                    if (lastOutputLine.isNullOrBlank()) {
                        "exit code $exitCode"
                    } else {
                        "exit code $exitCode, last output: $lastOutputLine"
                    }
            appendSystemMessage("Codex app-server stopped ($details).")
            chatViewPanel.setStreaming(false)
        }
        appServerStarted = false
    }

    override fun onAppServerError(message: String) {
        runOnUi {
            appendSystemMessage(message)
            chatViewPanel.setStreaming(false)
        }
        appServerStarted = false
    }

    private fun startAppServerIfNeeded() {
        if (appServerStarted) return
        val result = appServer.start()
        if (result.isFailure) {
            appendSystemMessage("Failed to start Codex app-server: ${result.exceptionOrNull()?.message}")
        } else {
            appServerStarted = true
            appendSystemMessage("Codex app-server started.")
            refreshChatListFromServer()
        }
    }

    private fun handleSend(text: String) {
        appendMessage(MessageKind.USER, text)
        if (threadId == null && pendingChatTitle == null) {
            pendingChatTitle = deriveChatTitle(text)
            chatViewPanel.setChatTitle(pendingChatTitle ?: "New chat")
        }
        ensureThreadAndSend(text)
    }

    private fun ensureThreadAndSend(text: String) {
        startAppServerIfNeeded()
        val existingThread = threadId
        if (existingThread == null) {
            val params = JSONObject().put("cwd", workingDirectory)
            appServer
                    .sendRequest("thread/start", params)
                    .thenAccept { result ->
                        val newThreadId = result.optJSONObject("thread")?.optString("id")
                        if (!newThreadId.isNullOrBlank()) {
                            threadId = newThreadId
                            val title = pendingChatTitle ?: "New chat"
                            if (chatSessions.none { it.id == newThreadId }) {
                                chatSessions.add(
                                        ChatSession(
                                                newThreadId,
                                                title,
                                                System.currentTimeMillis() / 1000
                                        )
                                )
                                runOnUi { updateChatList() }
                            }
                        }
                        sendTurn(text)
                    }
                    .exceptionally { error ->
                        runOnUi { appendSystemMessage("Failed to start thread: ${error.message}") }
                        null
                    }
        } else {
            sendTurn(text)
        }
    }

    private fun sendTurn(text: String) {
        val currentThread = threadId ?: return
        val input = JSONArray().put(JSONObject().put("type", "text").put("text", text))
        val params =
                JSONObject()
                        .put("threadId", currentThread)
                        .put("input", input)
                        .put("cwd", workingDirectory)
        appServer
                .sendRequest("turn/start", params)
                .exceptionally { error ->
                    runOnUi { appendSystemMessage("Failed to start turn: ${error.message}") }
                    null
                }
    }

    private fun handleItemStarted(params: JSONObject) {
        val item = params.optJSONObject("item") ?: return
        val type = item.optString("type")
        val itemId = item.optString("id", "")

        when (type) {
            "agentMessage" -> {
                runOnUi {
                    val message = appendMessage(MessageKind.ASSISTANT, "")
                    if (itemId.isNotBlank()) {
                        assistantMessageIndexByItemId[itemId] = messages.indexOf(message)
                    }
                }
            }
            "commandExecution" -> {
                val command = item.optString("command", "")
                runOnUi {
                    val message = appendMessage(MessageKind.TOOL, "Running command: $command")
                    if (itemId.isNotBlank()) {
                        toolMessageIndexByItemId[itemId] = messages.indexOf(message)
                    }
                }
            }
            "fileChange" -> {
                runOnUi {
                    appendMessage(MessageKind.TOOL, "File changes proposed.")
                }
            }
            "mcpToolCall" -> {
                val server = item.optString("server", "mcp")
                val tool = item.optString("tool", "")
                val status = item.optString("status", "running")
                val label = "MCP: $server/$tool ($status)"
                runOnUi {
                    val message = appendMessage(MessageKind.TOOL, label)
                    if (itemId.isNotBlank()) {
                        toolMessageIndexByItemId[itemId] = messages.indexOf(message)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun handleItemCompleted(params: JSONObject) {
        val item = params.optJSONObject("item") ?: return
        val type = item.optString("type")
        val itemId = item.optString("id", "")

        when (type) {
            "agentMessage" -> {
                val text = item.optString("text", "")
                if (text.isNotBlank()) {
                    runOnUi { updateMessageContent(itemId, text, assistantMessageIndexByItemId) }
                }
            }
            "fileChange" -> {
                val changes = item.optJSONArray("changes") ?: return
                runOnUi {
                    for (index in 0 until changes.length()) {
                        val change = changes.optJSONObject(index) ?: continue
                        val path = change.optString("path")
                        val diff = change.optString("diff")
                        appendMessage(MessageKind.TOOL, "Change: $path")
                        if (diff.isNotBlank()) {
                            appendMessage(MessageKind.DIFF, diff)
                        }
                    }
                }
            }
            "mcpToolCall" -> {
                val status = item.optString("status", "completed")
                val result = item.optString("result", "")
                val label =
                        if (result.isNotBlank()) {
                            "MCP completed ($status): $result"
                        } else {
                            "MCP completed ($status)."
                        }
                runOnUi {
                    val index = toolMessageIndexByItemId[itemId]
                    if (index == null) {
                        appendMessage(MessageKind.TOOL, label)
                    } else {
                        val message = messages.getOrNull(index) ?: return@runOnUi
                        message.content = label
                        renderTranscript()
                    }
                }
            }
            else -> Unit
        }
    }

    private fun handleAgentMessageDelta(params: JSONObject) {
        val itemId = params.optString("itemId", "")
        val delta = params.optString("delta", "")
        if (itemId.isBlank() || delta.isBlank()) return
        runOnUi {
            val index = assistantMessageIndexByItemId[itemId] ?: return@runOnUi
            val message = messages.getOrNull(index) ?: return@runOnUi
            message.content += delta
            renderTranscript()
        }
    }

    private fun handleCommandOutputDelta(params: JSONObject) {
        val itemId = params.optString("itemId", "")
        val delta = params.optString("delta", "")
        if (itemId.isBlank() || delta.isBlank()) return
        runOnUi {
            val index = toolMessageIndexByItemId[itemId]
            if (index == null) {
                val message = appendMessage(MessageKind.TOOL, "Command output:\n$delta")
                toolMessageIndexByItemId[itemId] = messages.indexOf(message)
            } else {
                val message = messages.getOrNull(index) ?: return@runOnUi
                message.content += delta
                renderTranscript()
            }
        }
    }

    private fun handleFileChangeOutputDelta(params: JSONObject) {
        val delta = params.optString("delta", "")
        if (delta.isBlank()) return
        runOnUi { appendMessage(MessageKind.TOOL, delta) }
    }

    private fun handleDiffUpdated(params: JSONObject) {
        val diff = params.optString("diff", "")
        if (diff.isBlank()) return
        runOnUi {
            val existingId = diffMessageId
            if (existingId == null) {
                val message = appendMessage(MessageKind.DIFF, diff)
                diffMessageId = message.id
            } else {
                updateMessageById(existingId, diff)
            }
        }
    }

    private fun appendSystemMessage(text: String) {
        appendMessage(MessageKind.SYSTEM, text)
    }

    private fun appendMessage(kind: MessageKind, text: String): ChatMessage {
        val message =
                ChatMessage(
                        id = UUID.randomUUID().toString(),
                        kind = kind,
                        content = text
                )
        messages.add(message)
        if (bulkRenderDepth == 0) {
            renderTranscript()
        }
        return message
    }

    private fun appendActionMessage(
            kind: MessageKind,
            text: String,
            actions: List<MessageAction>
    ) {
        val message =
                ChatMessage(
                        id = UUID.randomUUID().toString(),
                        kind = kind,
                        content = text,
                        actions = actions
                )
        messages.add(message)
        if (bulkRenderDepth == 0) {
            renderTranscript()
        }
    }

    private fun updateMessageContent(
            itemId: String,
            text: String,
            indexMap: Map<String, Int>
    ) {
        val index = indexMap[itemId] ?: return
        val message = messages.getOrNull(index) ?: return
        message.content = text
        if (bulkRenderDepth == 0) {
            renderTranscript()
        }
    }

    private fun updateMessageById(messageId: String, text: String) {
        val message = messages.firstOrNull { it.id == messageId } ?: return
        message.content = text
        if (bulkRenderDepth == 0) {
            renderTranscript()
        }
    }

    private fun runOnUi(action: () -> Unit) {
        SwingUtilities.invokeLater(action)
    }

    private fun renderTranscript() {
        val startNs = System.nanoTime()
        chatViewPanel.renderMessages(messages)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
        if (bulkRenderDepth > 0 || elapsedMs > 50) {
            log.info("renderMessages count=${messages.size} took ${elapsedMs}ms (bulk=$bulkRenderDepth)")
        }
    }

    private fun updateChatList() {
        chatListPanel.setSessions(chatSessions)
    }

    private fun clearChat() {
        messages.clear()
        assistantMessageIndexByItemId.clear()
        toolMessageIndexByItemId.clear()
        diffMessageId = null
        chatViewPanel.setStreaming(false)
        renderTranscript()
    }

    private fun startNewChat() {
        threadId = null
        activeTurnId = null
        pendingChatTitle = null
        messages.clear()
        assistantMessageIndexByItemId.clear()
        toolMessageIndexByItemId.clear()
        diffMessageId = null
        chatViewPanel.setChatTitle("New chat")
        showChatView()
    }

    private fun openChat(session: ChatSession) {
        threadId = session.id
        activeTurnId = null
        pendingChatTitle = null
        messages.clear()
        assistantMessageIndexByItemId.clear()
        toolMessageIndexByItemId.clear()
        diffMessageId = null
        chatViewPanel.setChatTitle(session.title)
        appendSystemMessage("Opened chat: ${session.title}")
        loadChatHistory(session.id)
        showChatView()
    }

    private fun showChatList() {
        chatViewPanel.setBackVisible(false)
        cardLayout.show(cardPanel, "list")
        refreshChatListFromServer()
    }

    private fun showChatView() {
        chatViewPanel.setBackVisible(true)
        cardLayout.show(cardPanel, "chat")
        renderTranscript()
    }

    private fun deriveChatTitle(text: String): String {
        val trimmed = text.trim().replace(Regex("\\s+"), " ")
        return if (trimmed.length <= 40) trimmed else trimmed.substring(0, 37) + "..."
    }

    private fun interruptTurn() {
        val currentThread = threadId
        val currentTurn = activeTurnId
        if (currentThread.isNullOrBlank() || currentTurn.isNullOrBlank()) {
            appendSystemMessage("No active turn to interrupt.")
            chatViewPanel.setStreaming(false)
            return
        }
        val params = JSONObject().put("threadId", currentThread).put("turnId", currentTurn)
        appServer
                .sendRequest("turn/interrupt", params)
                .exceptionally { error ->
                    runOnUi {
                        appendSystemMessage("Failed to interrupt turn: ${error.message}")
                        chatViewPanel.setStreaming(false)
                    }
                    null
                }
    }

    private fun refreshChatListFromServer() {
        if (!appServerStarted) return
        val params = JSONObject().put("limit", 50).put("sortKey", "updated_at")
        appServer
                .sendRequest("thread/list", params)
                .thenAccept { result ->
                    val data = result.optJSONArray("data") ?: return@thenAccept
                    val sessions = mutableListOf<ChatSession>()
                    for (index in 0 until data.length()) {
                        val item = data.optJSONObject(index) ?: continue
                        val id = item.optString("id")
                        val preview = item.optString("preview", "Chat")
                        if (id.isNotBlank()) {
                            val updatedAt = item.optLong("updatedAt", 0)
                            sessions.add(
                                    ChatSession(
                                            id,
                                            preview.ifBlank { "Chat" },
                                            updatedAt.takeIf { it > 0 }
                                    )
                            )
                        }
                    }
                    chatSessions.clear()
                    chatSessions.addAll(sessions)
                    runOnUi { updateChatList() }
                }
                .exceptionally { error ->
                    runOnUi { appendSystemMessage("Failed to load chats: ${error.message}") }
                    null
                }
    }

    private fun loadChatHistory(id: String) {
        if (!appServerStarted) return
        val params = JSONObject().put("threadId", id)
        val startNs = System.nanoTime()
        appServer
                .sendRequest("thread/resume", params)
                .thenAccept { result ->
                    val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                    val thread = result.optJSONObject("thread")
                    val items = thread?.optJSONArray("items")
                    val turns = thread?.optJSONArray("turns")
                    log.info(
                            "thread/resume $id completed in ${elapsedMs}ms (items=${items?.length() ?: 0}, turns=${turns?.length() ?: 0})"
                    )
                    if (items != null) {
                        renderItemsFromArray(items)
                    } else if (turns != null) {
                        renderTurnsFromArray(turns)
                    } else {
                        runOnUi { appendSystemMessage("No history payload returned for this chat.") }
                    }
                }
                .exceptionally { error ->
                    runOnUi { appendSystemMessage("Failed to load history: ${error.message}") }
                    null
                }
    }

    private fun renderItemsFromArray(items: JSONArray) {
        val startNs = System.nanoTime()
        val itemCount = items.length()
        runOnUi {
            val beforeCount = messages.size
            withBulkRender {
                appendItemsFromArray(items)
            }
            val appended = messages.size - beforeCount
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
            log.info("History render: items=$itemCount appended=$appended took ${elapsedMs}ms")
        }
    }

    private fun renderTurnsFromArray(turns: JSONArray) {
        val startNs = System.nanoTime()
        val turnCount = turns.length()
        runOnUi {
            withBulkRender {
                for (index in 0 until turns.length()) {
                    val turn = turns.optJSONObject(index) ?: continue
                    val items = turn.optJSONArray("items") ?: continue
                    appendItemsFromArray(items)
                }
            }
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
            log.info("History render: turns=$turnCount took ${elapsedMs}ms")
        }
    }

    private fun appendItemsFromArray(items: JSONArray) {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val type = item.optString("type")
            when (type) {
                "userMessage" -> {
                    val content = item.optJSONArray("content")?.optJSONObject(0)?.optString("text")
                    if (!content.isNullOrBlank()) {
                        appendMessage(MessageKind.USER, content)
                    }
                }
                "agentMessage" -> {
                    val text = item.optString("text")
                    if (text.isNotBlank()) {
                        appendMessage(MessageKind.ASSISTANT, text)
                    }
                }
            }
        }
    }

    private inline fun withBulkRender(action: () -> Unit) {
        bulkRenderDepth++
        try {
            action()
        } finally {
            bulkRenderDepth--
            if (bulkRenderDepth == 0) {
                renderTranscript()
            }
        }
    }

    private fun stopServer() {
        appServer.stop()
        appServerStarted = false
        threadId = null
        activeTurnId = null
        appendSystemMessage("Codex app-server stopped.")
    }

    private fun restartServer() {
        appServer.stop()
        appServerStarted = false
        threadId = null
        activeTurnId = null
        val result = appServer.start()
        if (result.isFailure) {
            appendSystemMessage(
                    "Failed to restart Codex app-server: ${result.exceptionOrNull()?.message}"
            )
        } else {
            appServerStarted = true
            appendSystemMessage("Codex app-server restarted.")
            refreshChatListFromServer()
        }
    }

}
