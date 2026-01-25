package com.github.niyazm524.codexwebstormplugin.toolWindow

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ScrollPaneConstants

class CodexChatViewPanel(
    private val messageRenderer: CodexMessageRenderer,
) {
    private val messageList =
            JBPanel<JBPanel<*>>().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
            }
    private val chatNameLabel =
            JLabel("New chat").apply {
                foreground = JBColor(0xCFCFCF, 0xCFCFCF)
            }
    private val backButton =
            JButton(AllIcons.Actions.Back).apply {
                toolTipText = "Back to chats"
                isVisible = false
            }
    private val inputField =
            JBTextArea().apply {
                lineWrap = true
                wrapStyleWord = true
                rows = 3
                toolTipText = "Ask Codex... (Ctrl+Enter to send)"
            }
    private val sendButton =
            JButton("Send").apply {
                icon = AllIcons.Actions.RunAll
                addActionListener { handleSendOrStop() }
            }

    private var onSend: ((String) -> Unit)? = null
    private var onBack: (() -> Unit)? = null
    private var onInterrupt: (() -> Unit)? = null
    private var onClear: (() -> Unit)? = null
    private var isStreaming = false

    fun build(): JComponent {
        val header =
                JBPanel<JBPanel<*>>(BorderLayout()).apply {
                    isOpaque = false
                    border = JBUI.Borders.empty(10, 12, 6, 12)
                    val titleRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                        isOpaque = false
                        add(backButton)
                        add(chatNameLabel)
                    }
                    add(titleRow, BorderLayout.CENTER)
                }

        val transcriptScroll =
                JBScrollPane(messageList).apply {
                    preferredSize = Dimension(420, 360)
                    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                }

        val inputScroll =
                JBScrollPane(inputField).apply {
                    preferredSize = Dimension(420, 90)
                    border = JBUI.Borders.empty()
                    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
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

        inputField.inputMap.put(KeyStroke.getKeyStroke("ctrl ENTER"), "sendMessage")
        inputField.actionMap.put(
                "sendMessage",
                object : AbstractAction() {
                    override fun actionPerformed(event: ActionEvent?) {
                        handleSendOrStop()
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
                    addActionListener { onClear?.invoke() }
                }
        val toolbarPanel =
                JPanel(BorderLayout()).apply {
                    val buttons = JPanel().apply {
                        isOpaque = false
                        add(clearButton)
                    }
                    add(buttons, BorderLayout.EAST)
                }

        backButton.addActionListener { onBack?.invoke() }

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

        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(topPanel, BorderLayout.NORTH)
            add(transcriptScroll, BorderLayout.CENTER)
            add(bottomPanel, BorderLayout.SOUTH)
        }
    }

    fun renderMessages(messages: List<ChatMessage>) {
        messageList.removeAll()
        messages.forEach { messageList.add(messageRenderer.renderMessage(it)) }
        messageList.revalidate()
        messageList.repaint()
    }

    fun messageContainerWidth(): Int {
        return messageList.width
    }

    fun setChatTitle(title: String) {
        chatNameLabel.text = title
    }

    fun setBackVisible(visible: Boolean) {
        backButton.isVisible = visible
    }

    fun clearInput() {
        inputField.text = ""
    }

    fun setOnSend(handler: (String) -> Unit) {
        onSend = handler
    }

    fun setOnBack(handler: () -> Unit) {
        onBack = handler
    }

    fun setOnClear(handler: () -> Unit) {
        onClear = handler
    }

    fun setOnInterrupt(handler: () -> Unit) {
        onInterrupt = handler
    }

    fun setStreaming(streaming: Boolean) {
        isStreaming = streaming
        if (streaming) {
            sendButton.text = "Stop"
            sendButton.icon = AllIcons.Actions.Suspend
        } else {
            sendButton.text = "Send"
            sendButton.icon = AllIcons.Actions.RunAll
        }
    }

    private fun handleSendOrStop() {
        if (isStreaming) {
            onInterrupt?.invoke()
            return
        }
        val text = inputField.text.trim()
        if (text.isBlank()) return
        inputField.text = ""
        onSend?.invoke(text)
    }
}
