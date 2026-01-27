package com.github.niyazm524.codexwebstormplugin.toolWindow

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ScrollPaneConstants

class CodexChatListPanel {
    private val list = JList<ChatSession>()
    private var onSelect: ((ChatSession) -> Unit)? = null
    private var onNewChat: (() -> Unit)? = null
    private var sessions: List<ChatSession> = emptyList()
    private var hoveredIndex = -1

    fun build(): JComponent {
        list.cellRenderer = ChatListCellRenderer { hoveredIndex }
        list.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                val index = list.selectedIndex
                if (index >= 0 && index < sessions.size) {
                    onSelect?.invoke(sessions[index])
                }
            }
        }
        list.addMouseMotionListener(
                object : MouseMotionAdapter() {
                    override fun mouseMoved(event: MouseEvent) {
                        val index = list.locationToIndex(event.point)
                        val bounds = if (index >= 0) list.getCellBounds(index, index) else null
                        val nextIndex = if (bounds != null && bounds.contains(event.point)) index else -1
                        if (nextIndex != hoveredIndex) {
                            hoveredIndex = nextIndex
                            list.repaint()
                        }
                    }
                }
        )
        list.addMouseListener(
                object : MouseAdapter() {
                    override fun mouseExited(event: MouseEvent) {
                        if (hoveredIndex != -1) {
                            hoveredIndex = -1
                            list.repaint()
                        }
                    }
                }
        )

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
            add(
                    JLabel("Chats").apply {
                        foreground = JBColor(0xCFCFCF, 0xCFCFCF)
                    },
                    BorderLayout.NORTH
            )
            add(
                    JBScrollPane(list).apply {
                        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                    },
                    BorderLayout.CENTER
            )
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
        list.setListData(items.toTypedArray())
    }

    fun setOnSelect(handler: (ChatSession) -> Unit) {
        onSelect = handler
    }

    fun setOnNewChat(handler: () -> Unit) {
        onNewChat = handler
    }

    private class ChatListCellRenderer(
            private val hoveredIndexProvider: () -> Int
    ) : ListCellRenderer<ChatSession> {
        override fun getListCellRendererComponent(
                list: JList<out ChatSession>,
                value: ChatSession?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
        ): Component {
            val titleText = value?.title.orEmpty()
            val timeText = value?.updatedAtSeconds?.let { formatRelativeTime(it) }.orEmpty()
            val titleLabel = JLabel()
            val timeLabel = JLabel(timeText).apply {
                foreground = JBColor(0x8A8A8A, 0xA0A4AA)
            }
            val panel =
                    JPanel(BorderLayout()).apply {
                        border = JBUI.Borders.empty(6, 10)
                        isOpaque = true
                        background = when {
                            isSelected -> list.selectionBackground
                            index == hoveredIndexProvider() -> JBColor(0x2F3133, 0x3A3D41)
                            else -> list.background
                        }
                        add(titleLabel, BorderLayout.CENTER)
                        add(
                                JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                                    isOpaque = false
                                    border = JBUI.Borders.empty(0, 8, 0, 0)
                                    add(timeLabel)
                                },
                                BorderLayout.EAST
                        )
                    }
            val maxWidth = (list.width - panel.insets.left - panel.insets.right).coerceAtLeast(120)
            val availableTitleWidth = maxWidth - timeLabel.preferredSize.width - 16
            titleLabel.text = ellipsize(titleText, list.getFontMetrics(list.font), availableTitleWidth)
            titleLabel.foreground =
                    if (isSelected) list.selectionForeground else JBColor(0xDADDE2, 0xDADDE2)
            return panel
        }

        private fun ellipsize(text: String, metrics: java.awt.FontMetrics, maxWidth: Int): String {
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

        private fun formatRelativeTime(updatedAtSeconds: Long): String {
            val nowSeconds = System.currentTimeMillis() / 1000
            val delta = (nowSeconds - updatedAtSeconds).coerceAtLeast(0)
            return when {
                delta < 60 -> "1m"
                delta < 3600 -> "${delta / 60}m"
                delta < 86400 -> "${delta / 3600}h"
                delta < 2592000 -> "${delta / 86400}d"
                else -> "${delta / 2592000}mo"
            }
        }
    }
}
