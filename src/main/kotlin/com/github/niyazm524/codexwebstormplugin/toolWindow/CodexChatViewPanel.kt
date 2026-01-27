package com.github.niyazm524.codexwebstormplugin.toolWindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FontMetrics
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.AbstractAction
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ScrollPaneConstants

class CodexChatViewPanel(
        private val messageRenderer: CodexMessageRenderer,
) {
    private data class DropdownOption(val label: String, val icon: Icon)
    private data class DropdownModel(
            val options: List<DropdownOption>,
            var selected: DropdownOption
    )

    private val sendIcon = loadVscodeIcon("SvgArrowUp")
    private val stopIcon = loadVscodeIcon("SvgPauseCircle")

    private val messageList =
            JBPanel<JBPanel<*>>().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                alignmentY = 0.0f
            }
    private val messageContainer =
            JPanel(BorderLayout()).apply {
                isOpaque = false
                add(messageList, BorderLayout.NORTH)
            }
    private val chatNameLabel =
            JLabel("New chat").apply {
                foreground = JBColor(0xCFCFCF, 0xCFCFCF)
                minimumSize = Dimension(0, preferredSize.height)
                maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
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
                icon = sendIcon
                toolTipText = "Send"
                isFocusPainted = false
                addActionListener { handleSendOrStop() }
            }
    private val newChatButton =
            JButton().apply {
                icon = loadVscodeIcon("SvgCompose")
                toolTipText = "New chat"
                isFocusPainted = false
            }

    private var onSend: ((String) -> Unit)? = null
    private var onBack: (() -> Unit)? = null
    private var onInterrupt: (() -> Unit)? = null
    private var onClear: (() -> Unit)? = null
    private var onNewChat: (() -> Unit)? = null
    private var isStreaming = false
    private var chatTitleRaw = "New chat"
    private var titleRowPanel: JPanel? = null
    private var titleLeftPanel: JPanel? = null
    private var titleRightPanel: JPanel? = null

    fun build(): JComponent {
        val header =
                JBPanel<JBPanel<*>>(BorderLayout()).apply {
                    isOpaque = false
                    border = JBUI.Borders.empty(10, 6, 6, 12)
                    val titleRow =
                            JPanel(BorderLayout()).apply {
                                isOpaque = false
                                titleRowPanel = this
                                add(
                                        JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                                            isOpaque = false
                                            add(backButton)
                                            add(Box.createHorizontalStrut(4))
                                            titleLeftPanel = this
                                        },
                                        BorderLayout.WEST
                                )
                                add(chatNameLabel, BorderLayout.CENTER)
                                add(
                                        JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                                            isOpaque = false
                                            border = JBUI.Borders.empty(0, 6, 0, 0)
                                            add(newChatButton)
                                            titleRightPanel = this
                                        },
                                        BorderLayout.EAST
                                )
                                addComponentListener(
                                        object : ComponentAdapter() {
                                            override fun componentResized(event: ComponentEvent) {
                                                updateChatTitleDisplay(this@apply, chatNameLabel)
                                            }
                                        }
                                )
                            }
                    add(titleRow, BorderLayout.CENTER)
                }

        val transcriptScroll =
                JBScrollPane(messageContainer).apply {
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
        val chatIcon = loadVscodeIcon("SvgChat")
        val agentIcon = loadVscodeIcon("SvgRobot")
        val fullAccessIcon = loadVscodeIcon("SvgSkip")
        val modelIcon = IconUtil.scale(loadVscodeIcon("SvgCube"), null, 1.9f)
        val reasoningMinimalIcon = loadVscodeIcon("SvgReasoningMinimal")
        val reasoningLowIcon = loadVscodeIcon("SvgReasoningLow")
        val reasoningMediumIcon = loadVscodeIcon("SvgReasoningMedium")
        val reasoningHighIcon = loadVscodeIcon("SvgReasoningHigh")
        val reasoningExtraHighIcon = loadVscodeIcon("SvgReasoningExtraHigh")
        val modeModel =
                DropdownModel(
                        listOf(
                                DropdownOption("Chat", chatIcon),
                                DropdownOption("Agent", agentIcon),
                                DropdownOption("Agent with full access", fullAccessIcon),
                        ),
                        DropdownOption("Chat", chatIcon),
                )
        val modelModel =
                DropdownModel(
                        listOf(
                                DropdownOption("gpt-5.1-codex", modelIcon),
                                DropdownOption("gpt-5-codex", modelIcon),
                        ),
                        DropdownOption("gpt-5.1-codex", modelIcon),
                )
        val reasoningModel =
                DropdownModel(
                        listOf(
                                DropdownOption("minimal", reasoningMinimalIcon),
                                DropdownOption("low", reasoningLowIcon),
                                DropdownOption("medium", reasoningMediumIcon),
                                DropdownOption("high", reasoningHighIcon),
                                DropdownOption("extra high", reasoningExtraHighIcon),
                        ),
                        DropdownOption("medium", reasoningMediumIcon),
                )
        val modeAction = CodexDropdownAction("Agent mode", modeModel)
        val modelAction = CodexDropdownAction("Model", modelModel)
        val reasoningAction = CodexDropdownAction("Reasoning effort", reasoningModel)
        val toolbarGroup =
                DefaultActionGroup().apply {
                    add(modeAction)
                    add(modelAction)
                    add(reasoningAction)
                }
        val toolbar =
                ActionManager.getInstance()
                        .createActionToolbar("CodexChatToolbar", toolbarGroup, true)

        applySquareButton(attachButton)

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
                    add(toolbar.component)
                }
        toolbar.targetComponent = optionsPanel

        // Clear button removed per request.

        backButton.addActionListener { onBack?.invoke() }
        newChatButton.addActionListener { onNewChat?.invoke() }
        applySquareButton(backButton)
        applySquareButton(newChatButton)
        applySquareButton(sendButton)
        applySquareButton(attachButton)

        val topPanel =
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(header, BorderLayout.CENTER)
                }
        val bottomPanel =
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    val stacked =
                            JPanel().apply {
                                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                                isOpaque = false
                                add(inputPanel)
                                add(
                                        JPanel(BorderLayout()).apply {
                                            isOpaque = false
                                            add(optionsPanel, BorderLayout.WEST)
                                            add(
                                                    JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
                                                            .apply {
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
        chatTitleRaw = title
        chatNameLabel.text = title
        titleRowPanel?.let { updateChatTitleDisplay(it, chatNameLabel) }
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

    fun setOnNewChat(handler: () -> Unit) {
        onNewChat = handler
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
            sendButton.icon = stopIcon
            sendButton.toolTipText = "Stop"
        } else {
            sendButton.icon = sendIcon
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

    private fun updateChatTitleDisplay(titleRow: JPanel, label: JLabel) {
        if (titleRow.width <= 0) return
        val leftWidth = titleLeftPanel?.preferredSize?.width ?: 0
        val rightWidth = titleRightPanel?.preferredSize?.width ?: 0
        val availableWidth =
                (titleRow.width
                        - leftWidth
                        - rightWidth
                        - JBUI.scale(8))
                        .coerceAtLeast(JBUI.scale(80))
        val metrics = label.getFontMetrics(label.font)
        label.text = ellipsize(chatTitleRaw, metrics, availableWidth)
    }

    private fun ellipsize(text: String, metrics: FontMetrics, maxWidth: Int): String {
        if (text.isBlank() || metrics.stringWidth(text) <= maxWidth) return text
        val ellipsis = "…"
        val targetWidth = maxWidth - metrics.stringWidth(ellipsis)
        if (targetWidth <= 0) return ellipsis
        var end = text.length
        while (end > 0 && metrics.stringWidth(text.substring(0, end)) > targetWidth) {
            end--
        }
        return text.substring(0, end).trimEnd() + ellipsis
    }

    private fun loadVscodeIcon(name: String): Icon {
        return IconLoader.getIcon("/icons/vscode/$name.svg", CodexChatViewPanel::class.java)
    }

    private class CodexDropdownAction(
            private val label: String,
            private val model: DropdownModel,
    ) : ComboBoxAction() {
        init {
            templatePresentation.icon = model.selected.icon
            templatePresentation.text = ""
        }
        override fun createPopupActionGroup(button: JComponent): DefaultActionGroup {
            val group = DefaultActionGroup()
            model.options.forEach { option ->
                group.add(
                        object : AnAction(option.label, null, option.icon) {
                            override fun actionPerformed(e: AnActionEvent) {
                                model.selected = option
                            }
                        }
                )
            }
            return group
        }

        override fun update(e: AnActionEvent) {
            e.presentation.icon = model.selected.icon
            e.presentation.text = ""
            e.presentation.description = "$label: ${model.selected.label}"
        }

        override fun getActionUpdateThread() =
                com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
    }
}
