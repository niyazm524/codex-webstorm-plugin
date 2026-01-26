package com.github.niyazm524.codexwebstormplugin.toolWindow

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.text.StyleConstants

class CodexMessageRenderer(private val containerWidthProvider: () -> Int) {

    fun renderMessage(message: ChatMessage): JComponent {
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
                        markdownToHtml(text),
                        containerWidthProvider,
                        JBColor(0xE6F0FF, 0x2B3A55)
                )

        return JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
            isOpaque = false
            border = JBUI.Borders.empty(2, 12, 6, 12)
            add(bubble)
            maximumSize = Dimension(Int.MAX_VALUE, bubble.preferredSize.height + 8)
        }
    }

    private fun renderAssistantMessage(text: String): JComponent {
        val pane =
                ConstrainedHtmlPanel(
                        markdownToHtml(text),
                        containerWidthProvider,
                        maxWidthRatio = 0.96,
                        minWidth = 320
                )
        return JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            border = JBUI.Borders.empty(6, 12)
            add(pane)
            maximumSize = Dimension(Int.MAX_VALUE, pane.preferredSize.height + 8)
        }
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

    private fun markdownToHtml(text: String): String {
        val flavour = GFMFlavourDescriptor()
        val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(text)
        val body = HtmlGenerator(text, parsedTree, flavour).generateHtml()
        return """
            <html>
              <head>
                <style>
                  body { margin: 0; padding: 0; overflow-wrap: anywhere; word-wrap: break-word; }
                  p { margin: 0; padding: 0; }
                  ul, ol { margin: 0; padding-left: 1.2em; }
                  pre { margin: 0; white-space: pre-wrap; word-break: break-word; }
                  code { font-family: Menlo, monospace; white-space: pre-wrap; word-break: break-word; }
                  code { font-family: Menlo, monospace; }
                </style>
              </head>
              <body>$body</body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br/>")
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
            html: String,
            private val containerWidthProvider: () -> Int,
            fill: JBColor,
    ) : RoundedPanel(18, fill) {
        private val content =
                JEditorPane("text/html", html).apply {
                    isEditable = false
                    isOpaque = false
                    putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
                    border = JBUI.Borders.empty()
                }

        init {
            border = JBUI.Borders.empty(6, 16)
            add(content, BorderLayout.CENTER)
        }

        override fun getPreferredSize(): Dimension {
            val containerWidth = containerWidthProvider().coerceAtLeast(420)
            val maxWidth = (containerWidth * 0.7).toInt().coerceAtLeast(240)
            val horizontalPadding = 32
            val verticalPadding = 12
            content.setSize(maxWidth - horizontalPadding, Int.MAX_VALUE)
            val textSize = content.preferredSize
            return Dimension(
                    maxWidth.coerceAtMost(textSize.width + horizontalPadding),
                    textSize.height + verticalPadding
            )
        }

        override fun getMaximumSize(): Dimension = preferredSize
    }

    private class ConstrainedHtmlPanel(
            html: String,
            private val containerWidthProvider: () -> Int,
            private val maxWidthRatio: Double,
            private val minWidth: Int,
    ) : JBPanel<JBPanel<*>>(BorderLayout()) {
        private val content =
                JEditorPane("text/html", html).apply {
                    isEditable = false
                    isOpaque = false
                    putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
                    border = JBUI.Borders.empty()
                }

        init {
            isOpaque = false
            add(content, BorderLayout.CENTER)
        }

        override fun getPreferredSize(): Dimension {
            val containerWidth = containerWidthProvider().coerceAtLeast(420)
            val maxWidth = (containerWidth * maxWidthRatio).toInt().coerceAtLeast(minWidth)
            content.setSize(maxWidth, Int.MAX_VALUE)
            val textSize = content.preferredSize
            return Dimension(maxWidth, textSize.height)
        }

        override fun getMaximumSize(): Dimension = preferredSize

        override fun getMinimumSize(): Dimension = preferredSize
    }
}
