package com.github.niyazm524.codexwebstormplugin.toolWindow

import com.github.niyazm524.codexwebstormplugin.services.CodexAppServer
import com.github.niyazm524.codexwebstormplugin.services.CodexAppServerListener
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import org.json.JSONArray
import org.json.JSONObject
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.util.UUID
import javax.swing.AbstractAction
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.ScrollPaneConstants
import javax.swing.text.StyleConstants

class CodexToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val codexToolWindow = CodexToolWindow(toolWindow)
        val content =
                ContentFactory.getInstance().createContent(codexToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class CodexToolWindow(toolWindow: ToolWindow) : CodexAppServerListener {

        private enum class MessageKind {
            USER,
            ASSISTANT,
            SYSTEM,
            TOOL,
            DIFF,
        }

        private data class MessageAction(val label: String, val onClick: () -> Unit)

        private data class ChatMessage(
                val id: String,
                val kind: MessageKind,
                var content: String,
                val actions: List<MessageAction> = emptyList()
        )

        private val messages = mutableListOf<ChatMessage>()
        private val assistantMessageIndexByItemId = mutableMapOf<String, Int>()
        private val toolMessageIndexByItemId = mutableMapOf<String, Int>()
        private var diffMessageId: String? = null
        private var threadId: String? = null
        private var activeTurnId: String? = null
        private val workingDirectory = toolWindow.project.basePath ?: System.getProperty("user.dir")
        private val appServer = CodexAppServer(workingDirectory, this)
        private var appServerStarted = false
        private lateinit var messageList: JBPanel<JBPanel<*>>

        fun getContent() =
                JBPanel<JBPanel<*>>(BorderLayout()).apply {
                    val header =
                            JBPanel<JBPanel<*>>(BorderLayout()).apply {
                                isOpaque = false
                                border = JBUI.Borders.empty(10, 12, 6, 12)
                                add(
                                        JLabel("CODEX").apply {
                                            foreground = JBColor(0xCFCFCF, 0xCFCFCF)
                                        },
                                        BorderLayout.WEST
                                )
                            }

                    messageList =
                            JBPanel<JBPanel<*>>().apply {
                                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                                isOpaque = false
                            }
                    val transcriptScroll =
                            JBScrollPane(messageList).apply {
                                preferredSize = Dimension(420, 360)
                                horizontalScrollBarPolicy =
                                        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                            }

                    val inputField =
                            JBTextArea().apply {
                                lineWrap = true
                                wrapStyleWord = true
                                rows = 3
                                toolTipText = "Ask Codex... (Ctrl+Enter to send)"
                            }
                    val inputScroll =
                            JBScrollPane(inputField).apply {
                                preferredSize = Dimension(420, 90)
                                border = JBUI.Borders.empty()
                                horizontalScrollBarPolicy =
                                        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                            }
                    val sendButton =
                            JButton("Send").apply {
                                icon = AllIcons.Actions.RunAll
                            }
                    val attachButton =
                            JButton().apply {
                                toolTipText = "Attach image"
                                icon = AllIcons.General.Add
                            }
                    val modeCombo =
                            JComboBox(arrayOf("Chat", "Agent", "Agent with full access")).apply {
                                toolTipText = "Mode"
                            }
                    val modelCombo =
                            JComboBox(arrayOf("gpt-5.1-codex", "gpt-5-codex")).apply {
                                toolTipText = "Model"
                            }
                    val reasoningCombo =
                            JComboBox(arrayOf("low", "medium", "high", "very high")).apply {
                                toolTipText = "Reasoning effort"
                            }

                    fun sendMessage() {
                        val text = inputField.text.trim()
                        if (text.isEmpty()) return
                        inputField.text = ""
                        appendMessage(MessageKind.USER, text)
                        ensureThreadAndSend(text)
                    }

                    sendButton.addActionListener { sendMessage() }
                    inputField.inputMap.put(KeyStroke.getKeyStroke("ctrl ENTER"), "sendMessage")
                    inputField.actionMap.put(
                            "sendMessage",
                            object : AbstractAction() {
                                override fun actionPerformed(event: ActionEvent?) {
                                    sendMessage()
                                }
                            }
                    )

                    val inputPanel =
                            JPanel(BorderLayout(8, 0)).apply {
                                add(inputScroll, BorderLayout.CENTER)
                                add(sendButton, BorderLayout.EAST)
                            }

                    val optionsPanel =
                            JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                                isOpaque = false
                                border = JBUI.Borders.empty(6, 12, 6, 12)
                                add(attachButton)
                                add(modeCombo)
                                add(modelCombo)
                                add(reasoningCombo)
                            }

                    val clearButton =
                            JButton("Clear").apply {
                                toolTipText = "Clear chat history"
                                icon = AllIcons.Actions.GC
                                addActionListener {
                                    messages.clear()
                                    assistantMessageIndexByItemId.clear()
                                    toolMessageIndexByItemId.clear()
                                    diffMessageId = null
                                    renderTranscript()
                                }
                            }
                    val stopButton =
                            JButton("Stop").apply {
                                toolTipText = "Stop Codex app-server"
                                icon = AllIcons.Actions.Suspend
                                addActionListener { stopServer() }
                            }
                    val restartButton =
                            JButton("Restart").apply {
                                toolTipText = "Restart Codex app-server"
                                icon = AllIcons.Actions.Refresh
                                addActionListener { restartServer() }
                            }
                    val toolbarPanel =
                            JPanel(BorderLayout()).apply {
                                val buttons = JPanel().apply {
                                    isOpaque = false
                                    add(stopButton)
                                    add(restartButton)
                                    add(clearButton)
                                }
                                add(buttons, BorderLayout.EAST)
                            }
                    val topPanel =
                            JPanel(BorderLayout()).apply {
                                isOpaque = false
                                add(header, BorderLayout.WEST)
                                add(toolbarPanel, BorderLayout.EAST)
                            }
                    val bottomPanel =
                            JPanel(BorderLayout()).apply {
                                isOpaque = false
                                val stacked = JPanel().apply {
                                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                                    isOpaque = false
                                    add(inputPanel)
                                    add(optionsPanel)
                                }
                                add(stacked, BorderLayout.CENTER)
                            }

                    if (!appServerStarted) {
                        val result = appServer.start()
                        if (result.isFailure) {
                            appendMessage(
                                    MessageKind.SYSTEM,
                                    "Failed to start Codex app-server: ${result.exceptionOrNull()?.message}"
                            )
                        } else {
                            appServerStarted = true
                            appendMessage(MessageKind.SYSTEM, "Codex app-server started.")
                        }
                    }

                    add(topPanel, BorderLayout.NORTH)
                    add(transcriptScroll, BorderLayout.CENTER)
                    add(bottomPanel, BorderLayout.SOUTH)
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
                }
                "turn/completed" -> {
                    val turn = params.optJSONObject("turn")
                    val status = turn?.optString("status") ?: "unknown"
                    runOnUi { appendSystemMessage("Turn completed ($status).") }
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
                    runOnUi { appendSystemMessage("Codex error: $message") }
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

        private fun ensureThreadAndSend(text: String) {
            if (!appServerStarted) {
                val result = appServer.start()
                if (result.isFailure) {
                    appendSystemMessage("Failed to start Codex app-server.")
                    return
                }
                appServerStarted = true
            }

            val existingThread = threadId
            if (existingThread == null) {
                val params = JSONObject().put("cwd", workingDirectory)
                appServer
                        .sendRequest("thread/start", params)
                        .thenAccept { result ->
                            threadId = result.optJSONObject("thread")?.optString("id")
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
            val input =
                    JSONArray().put(JSONObject().put("type", "text").put("text", text))
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
                        val message =
                                appendMessage(
                                        MessageKind.TOOL,
                                        "Running command: $command"
                                )
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
                    val message =
                            appendMessage(
                                    MessageKind.TOOL,
                                    "Command output:\n$delta"
                            )
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
            }
        }

        private fun appendMessage(kind: MessageKind, text: String): ChatMessage {
            val message =
                    ChatMessage(
                            id = UUID.randomUUID().toString(),
                            kind = kind,
                            content = text
                    )
            messages.add(message)
            renderTranscript()
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
            renderTranscript()
        }

        private fun updateMessageContent(
                itemId: String,
                text: String,
                indexMap: Map<String, Int>
        ) {
            val index = indexMap[itemId] ?: return
            val message = messages.getOrNull(index) ?: return
            message.content = text
            renderTranscript()
        }

        private fun updateMessageById(messageId: String, text: String) {
            val message = messages.firstOrNull { it.id == messageId } ?: return
            message.content = text
            renderTranscript()
        }

        private fun runOnUi(action: () -> Unit) {
            SwingUtilities.invokeLater(action)
        }

        private fun renderTranscript() {
            if (!::messageList.isInitialized) return
            messageList.removeAll()
            messages.forEach { messageList.add(renderMessage(it)) }
            messageList.revalidate()
            messageList.repaint()
        }

        private fun renderMessage(message: ChatMessage): JComponent {
            val content =
                    when (message.kind) {
                        MessageKind.USER -> renderUserBubble(message.content)
                        MessageKind.ASSISTANT -> renderAssistantMessage(message.content)
                        MessageKind.SYSTEM -> renderSystemMessage(message.content)
                        MessageKind.TOOL -> renderToolMessage(message.content)
                        MessageKind.DIFF -> renderDiffMessage(message.content)
                    }

            if (message.actions.isEmpty()) {
                return content
            }

            return JBPanel<JBPanel<*>>(BorderLayout()).apply {
                isOpaque = false
                add(content, BorderLayout.CENTER)
                val actionsPanel = JPanel().apply { isOpaque = false }
                message.actions.forEach { action ->
                    actionsPanel.add(JButton(action.label).apply { addActionListener { action.onClick() } })
                }
                add(actionsPanel, BorderLayout.SOUTH)
            }
        }

        private fun renderUserBubble(text: String): JComponent {
            val bubble =
                    BubblePanel(
                            text,
                            { if (::messageList.isInitialized) messageList.width else 0 },
                            JBColor(0xE6F0FF, 0x2B3A55),
                            JBColor(0x1F4B99, 0xBBD0FF)
                    )

            return JBPanel<JBPanel<*>>(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(6, 12)
                add(bubble, BorderLayout.EAST)
                alignmentX = 1.0f
            }
        }

        private fun renderAssistantMessage(text: String): JComponent {
            val pane =
                    JTextPane().apply {
                        isEditable = false
                        isOpaque = false
                    }
            val doc = pane.styledDocument
            val contentStyle = pane.addStyle("content", null)
            StyleConstants.setForeground(contentStyle, JBColor(0x222222, 0xD5D8DC))
            doc.insertString(doc.length, text, contentStyle)

            return JBPanel<JBPanel<*>>(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(6, 12)
                add(pane, BorderLayout.CENTER)
            }
        }

        private open class RoundedPanel(private val radius: Int, private val fill: JBColor) :
                JBPanel<JBPanel<*>>(BorderLayout()) {
            init {
                isOpaque = false
            }

            override fun paintComponent(graphics: Graphics) {
                val g2 = graphics.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = fill
                g2.fillRoundRect(0, 0, width, height, radius, radius)
                g2.dispose()
                super.paintComponent(graphics)
            }
        }

        private class BubblePanel(
                text: String,
                private val containerWidthProvider: () -> Int,
                fill: JBColor,
                textColor: JBColor,
        ) : RoundedPanel(18, fill) {
            private val textArea =
                    JBTextArea().apply {
                        isEditable = false
                        isOpaque = false
                        lineWrap = true
                        wrapStyleWord = true
                        this.text = text
                        foreground = textColor
                        border = JBUI.Borders.empty()
                    }

            init {
                border = JBUI.Borders.empty(12, 16)
                add(textArea, BorderLayout.CENTER)
            }

            override fun getPreferredSize(): Dimension {
                val containerWidth = containerWidthProvider().coerceAtLeast(420)
                val maxWidth = (containerWidth * 0.7).toInt().coerceAtLeast(240)
                val horizontalPadding = 32
                val verticalPadding = 24
                textArea.setSize(maxWidth - horizontalPadding, Int.MAX_VALUE)
                val textSize = textArea.preferredSize
                return Dimension(
                        maxWidth.coerceAtMost(textSize.width + horizontalPadding),
                        textSize.height + verticalPadding
                )
            }

            override fun getMaximumSize(): Dimension = preferredSize
        }

        private fun renderSystemMessage(text: String): JComponent {
            return JBPanel<JBPanel<*>>(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(6, 12)
                val label =
                        JLabel("<html><i>${escapeHtml(text)}</i></html>").apply {
                            foreground = JBColor(0x6B6B6B, 0x9AA0A6)
                        }
                add(label, BorderLayout.CENTER)
            }
        }

        private fun renderToolMessage(text: String): JComponent {
            val panel =
                    JBPanel<JBPanel<*>>(BorderLayout()).apply {
                        isOpaque = false
                        border = JBUI.Borders.empty(6, 12)
                    }
            val label =
                    JLabel("<html><b>Tool:</b> ${escapeHtml(text)}</html>").apply {
                        foreground = JBColor(0x444444, 0xC0C5CE)
                    }
            panel.add(label, BorderLayout.CENTER)
            return panel
        }

        private fun renderDiffMessage(text: String): JComponent {
            val pane =
                    JTextPane().apply {
                        isEditable = false
                        isOpaque = true
                        background = JBColor(0xF7F7F7, 0x2B2D30)
                        border = JBUI.Borders.empty(8, 10)
                    }
            val doc = pane.styledDocument
            val contentStyle = pane.addStyle("diff", null)
            StyleConstants.setFontFamily(contentStyle, "Menlo")
            StyleConstants.setForeground(contentStyle, JBColor(0x2A2A2A, 0xD5D8DC))
            doc.insertString(doc.length, text, contentStyle)

            return JBPanel<JBPanel<*>>(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(6, 12)
                add(pane, BorderLayout.CENTER)
            }
        }

        private fun escapeHtml(text: String): String {
            return text
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\n", "<br/>")
        }
    }
}
