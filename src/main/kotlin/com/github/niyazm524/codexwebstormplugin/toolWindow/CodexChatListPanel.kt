package com.github.niyazm524.codexwebstormplugin.toolWindow

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel

class CodexChatListPanel {
    private val list = JList<String>()
    private var onSelect: ((ChatSession) -> Unit)? = null
    private var onNewChat: (() -> Unit)? = null
    private var sessions: List<ChatSession> = emptyList()

    fun build(): JComponent {
        list.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                val index = list.selectedIndex
                if (index >= 0 && index < sessions.size) {
                    onSelect?.invoke(sessions[index])
                }
            }
        }

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
            add(
                    JLabel("Chats").apply {
                        foreground = JBColor(0xCFCFCF, 0xCFCFCF)
                    },
                    BorderLayout.NORTH
            )
            add(JBScrollPane(list), BorderLayout.CENTER)
            add(
                    JButton("New chat").apply {
                        addActionListener { onNewChat?.invoke() }
                    },
                    BorderLayout.SOUTH
            )
        }
    }

    fun setSessions(items: List<ChatSession>) {
        sessions = items
        list.setListData(items.map { it.title }.toTypedArray())
    }

    fun setOnSelect(handler: (ChatSession) -> Unit) {
        onSelect = handler
    }

    fun setOnNewChat(handler: () -> Unit) {
        onNewChat = handler
    }
}
