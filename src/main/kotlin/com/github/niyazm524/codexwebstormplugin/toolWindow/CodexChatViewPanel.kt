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
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ScrollPaneConstants
import javax.swing.DefaultListCellRenderer
import javax.swing.Icon

class CodexChatViewPanel(
    private val messageRenderer: CodexMessageRenderer,
) {
    private data class ComboOption(val label: String, val icon: Icon)

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
                isFocusPainted = false
            }
    private val inputField =
            JBTextArea().apply {
                lineWrap = true
                wrapStyleWord = true
                rows = 3
                toolTipText = "Ask Codex... (Ctrl+Enter to send)"
                emptyText.text = "Ask Codex..."
            }
    private val sendButton =
            JButton().apply {
                icon = AllIcons.Actions.RunAll
                toolTipText = "Send"
                isFocusPainted = false
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
                    border = JBUI.Borders.empty(10, 6, 6, 12)
                    val titleRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                        isOpaque = false
                        add(backButton)
                        add(Box.createHorizontalStrut(4))
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
                    isFocusPainted = false
                }
        val modeCombo =
                JComboBox(
                        arrayOf(
                                ComboOption("Chat", AllIcons.Actions.Lightning),
                                ComboOption("Agent", AllIcons.Actions.RunAll),
                                ComboOption("Agent with full access", AllIcons.Actions.Resume),
                        )
                ).apply {
                    toolTipText = "Agent mode"
                }
        val modelCombo =
                JComboBox(
                        arrayOf(
                                ComboOption("gpt-5.1-codex", AllIcons.General.Settings),
                                ComboOption("gpt-5-codex", AllIcons.General.Settings),
                        )
                ).apply {
                    toolTipText = "Model"
                }
        val reasoningCombo =
                JComboBox(
                        arrayOf(
                            ComboOption("low", AllIcons.Actions.Lightning),
                            ComboOption("medium", AllIcons.Actions.Lightning),
                            ComboOption("high", AllIcons.Actions.Lightning),
                            ComboOption("very high", AllIcons.Actions.Lightning),
                        )
                ).apply {
                    toolTipText = "Reasoning effort"
                }

        applySquareButton(attachButton)
        applyIconCombo(modeCombo)
        applyIconCombo(modelCombo)
        applyIconCombo(reasoningCombo)

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
                JPanel(BorderLayout()).apply {
                    border = JBUI.Borders.empty(6, 8, 6, 8)
                    add(inputScroll, BorderLayout.CENTER)
                }

        val optionsPanel =
                JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                    isOpaque = false
                    border = JBUI.Borders.empty(4, 6, 6, 12)
                    add(attachButton)
                    add(modeCombo)
                    add(modelCombo)
                    add(reasoningCombo)
                }

        // Clear button removed per request.

        backButton.addActionListener { onBack?.invoke() }
        applySquareButton(backButton)
        applySquareButton(sendButton)
        applySquareButton(attachButton)

        val topPanel =
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(header, BorderLayout.WEST)
                }
        val bottomPanel =
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    val stacked = JPanel().apply {
                        layout = BoxLayout(this, BoxLayout.Y_AXIS)
                        isOpaque = false
                        add(inputPanel)
                        add(
                                JPanel(BorderLayout()).apply {
                                    isOpaque = false
                                    add(optionsPanel, BorderLayout.WEST)
                                    add(
                                            JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                                                isOpaque = false
                                                add(sendButton)
                                            },
                                            BorderLayout.EAST
                                    )
                                }
                        )
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
            sendButton.icon = AllIcons.Actions.Suspend
            sendButton.toolTipText = "Stop"
        } else {
            sendButton.icon = AllIcons.Actions.RunAll
            sendButton.toolTipText = "Send"
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

    private fun applySquareButton(button: JButton) {
        val size = 36
        button.preferredSize = Dimension(size, size)
        button.minimumSize = Dimension(size, size)
        button.maximumSize = Dimension(size, size)
    }

    private fun applyIconCombo(combo: JComboBox<ComboOption>) {
        combo.renderer =
                object : DefaultListCellRenderer() {
                    override fun getListCellRendererComponent(
                            list: JList<*>?,
                            value: Any?,
                            index: Int,
                            isSelected: Boolean,
                            cellHasFocus: Boolean
                    ): java.awt.Component {
                        val label =
                                super.getListCellRendererComponent(
                                        list,
                                        value,
                                        index,
                                        isSelected,
                                        cellHasFocus
                                ) as JLabel
                        val option = value as? ComboOption
                        if (option != null) {
                            label.icon = option.icon
                            label.text = if (index == -1) "" else option.label
                        }
                        return label
                    }
                }
        val size = Dimension(36, 36)
        combo.preferredSize = size
        combo.minimumSize = size
        combo.maximumSize = size
    }
}
