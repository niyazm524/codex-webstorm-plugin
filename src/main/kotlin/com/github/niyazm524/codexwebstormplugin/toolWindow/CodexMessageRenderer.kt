package com.github.niyazm524.codexwebstormplugin.toolWindow

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.text.StyleConstants
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

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
                actionsPanel.add(
                        JButton(action.label).apply { addActionListener { action.onClick() } }
                )
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
                        minWidth = 0
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
        val (summary, details, autoExpand) = buildToolSummaryAndDetails(text)
        val collapsible =
                ToolCollapsiblePanel(
                        summary,
                        details,
                        containerWidthProvider,
                        autoExpand = autoExpand
                )
        val panel =
                JBPanel<JBPanel<*>>(BorderLayout()).apply {
                    isOpaque = false
                    border = JBUI.Borders.empty(6, 12)
                }
        panel.add(collapsible, BorderLayout.CENTER)
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
                  body { margin: 0; padding: 0; width: 100%; overflow-wrap: anywhere; word-wrap: break-word; }
                  p { margin: 0; padding: 0; white-space: normal; }
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
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br/>")
    }

    private fun buildToolSummaryAndDetails(text: String): Triple<String, String, Boolean> {
        val trimmed = text.trim()
        val runningPrefix = "Running command:"
        val outputPrefix = "Command output:"
        if (trimmed.startsWith(runningPrefix)) {
            val after = trimmed.removePrefix(runningPrefix).trimStart()
            val splitAt = after.indexOf('\n')
            val command = if (splitAt == -1) after else after.substring(0, splitAt).trimEnd()
            val output = if (splitAt == -1) "" else after.substring(splitAt + 1).trimStart()
            val details = buildString {
                append("Command:\n")
                append(command)
                if (output.isNotBlank()) {
                    append("\n\nOutput:\n")
                    append(output)
                }
            }
            val autoExpand = output.isNotBlank()
            return Triple(command.ifBlank { trimmed }, details, autoExpand)
        }
        if (trimmed.startsWith(outputPrefix)) {
            return Triple(outputPrefix, trimmed, true)
        }
        val autoExpand = trimmed.length > 200 || trimmed.contains('\n')
        return Triple(trimmed, trimmed, autoExpand)
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
            val containerWidth = containerWidthProvider().coerceAtLeast(1)
            val maxWidth = (containerWidth * maxWidthRatio).toInt().coerceAtLeast(minWidth)
            content.setSize(maxWidth, Int.MAX_VALUE)
            val textSize = content.preferredSize
            return Dimension(maxWidth, textSize.height)
        }

        override fun getMaximumSize(): Dimension = preferredSize

        override fun getMinimumSize(): Dimension = preferredSize
    }

    private class ToolCollapsiblePanel(
            private val summaryText: String,
            private val detailsText: String,
            private val containerWidthProvider: () -> Int,
            private val autoExpand: Boolean,
    ) : JBPanel<JBPanel<*>>(BorderLayout()) {
        private val summaryArea =
                JBTextArea().apply {
                    isEditable = false
                    isOpaque = false
                    lineWrap = false
                    wrapStyleWord = false
                    border = JBUI.Borders.empty()
                    foreground = JBColor(0x444444, 0xC0C5CE)
                }
        private val detailsArea =
                JBTextArea().apply {
                    isEditable = false
                    isOpaque = false
                    lineWrap = true
                    wrapStyleWord = false
                    border = JBUI.Borders.empty(6, 12, 8, 12)
                    foreground = JBColor(0x444444, 0xC0C5CE)
                    font = java.awt.Font("Menlo", java.awt.Font.PLAIN, font.size)
                    text = detailsText
                }
        private val detailsScroll =
                JBScrollPane(detailsArea).apply {
                    isOpaque = false
                    viewport.isOpaque = false
                    border = JBUI.Borders.empty()
                    horizontalScrollBarPolicy =
                            javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
                    verticalScrollBarPolicy =
                            javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
                }
        private val toggleButton =
                JButton(com.intellij.icons.AllIcons.General.ArrowRight).apply {
                    isFocusPainted = false
                    isContentAreaFilled = false
                    border = JBUI.Borders.empty()
                    margin = java.awt.Insets(0, 0, 0, 0)
                    preferredSize = Dimension(JBUI.scale(16), JBUI.scale(16))
                    minimumSize = preferredSize
                    maximumSize = preferredSize
                }
        private val detailsPanel =
                JBPanel<JBPanel<*>>(BorderLayout()).apply {
                    isOpaque = false
                    isVisible = false
                    add(detailsScroll, BorderLayout.CENTER)
                }
        private val detailsWrapper =
                JBPanel<JBPanel<*>>(BorderLayout()).apply {
                    isOpaque = false
                    border = JBUI.Borders.empty(0, 12, 0, 12)
                    add(detailsPanel, BorderLayout.CENTER)
                }

        init {
            isOpaque = false
            val header =
                    JBPanel<JBPanel<*>>(BorderLayout()).apply {
                        isOpaque = false
                        border = JBUI.Borders.empty(6, 8, 6, 8)
                        add(toggleButton, BorderLayout.WEST)
                        add(summaryArea, BorderLayout.CENTER)
                        addComponentListener(
                                object : ComponentAdapter() {
                                    override fun componentResized(event: ComponentEvent) {
                                        updateSummaryText(width)
                                    }
                                }
                        )
                    }
            val headerClick =
                    object : MouseAdapter() {
                        override fun mouseClicked(event: MouseEvent) {
                            toggle()
                        }
                    }
            header.addMouseListener(headerClick)
            summaryArea.addMouseListener(headerClick)
            toggleButton.addActionListener { toggle() }

            add(header, BorderLayout.NORTH)
            add(detailsWrapper, BorderLayout.CENTER)
            updateSummaryText(containerWidthProvider())
            if (autoExpand) {
                toggle()
            }
        }

        private fun toggle() {
            detailsPanel.isVisible = !detailsPanel.isVisible
            toggleButton.icon =
                    if (detailsPanel.isVisible) {
                        com.intellij.icons.AllIcons.General.ArrowDown
                    } else {
                        com.intellij.icons.AllIcons.General.ArrowRight
                    }
            detailsScroll.preferredSize =
                    if (detailsPanel.isVisible) {
                        Dimension(detailsScroll.preferredSize.width, JBUI.scale(220))
                    } else {
                        Dimension(0, 0)
                    }
            revalidate()
            repaint()
        }

        private fun updateSummaryText(containerWidth: Int) {
            if (containerWidth <= 0) return
            val maxWidth = (containerWidth * 0.96).toInt().coerceAtLeast(320)
            val available = maxWidth - JBUI.scale(36)
            val metrics = summaryArea.getFontMetrics(summaryArea.font)
            summaryArea.text = truncateToLines(summaryText, metrics, available, 4)
        }

        private fun truncateToLines(
                text: String,
                metrics: java.awt.FontMetrics,
                maxWidth: Int,
                maxLines: Int,
        ): String {
            val lines = wrapLines(text, metrics, maxWidth)
            if (lines.size <= maxLines) return lines.joinToString("\n")
            val truncated = lines.take(maxLines).toMutableList()
            truncated[truncated.lastIndex] = appendEllipsis(truncated.last(), metrics, maxWidth)
            return truncated.joinToString("\n")
        }

        private fun wrapLines(
                text: String,
                metrics: java.awt.FontMetrics,
                maxWidth: Int,
        ): List<String> {
            if (maxWidth <= 0) return listOf(text)
            val result = mutableListOf<String>()
            val paragraphs = text.split("\n")
            for (paragraph in paragraphs) {
                val words = paragraph.split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (words.isEmpty()) {
                    result.add("")
                    continue
                }
                var line = ""
                for (word in words) {
                    val candidate = if (line.isEmpty()) word else "$line $word"
                    if (metrics.stringWidth(candidate) <= maxWidth) {
                        line = candidate
                    } else {
                        if (line.isNotEmpty()) {
                            result.add(line)
                            line = ""
                        }
                        if (metrics.stringWidth(word) <= maxWidth) {
                            line = word
                        } else {
                            var chunk = ""
                            for (ch in word) {
                                val chunkCandidate = chunk + ch
                                if (metrics.stringWidth(chunkCandidate) <= maxWidth) {
                                    chunk = chunkCandidate
                                } else {
                                    if (chunk.isNotEmpty()) {
                                        result.add(chunk)
                                    }
                                    chunk = ch.toString()
                                }
                            }
                            line = chunk
                        }
                    }
                }
                if (line.isNotEmpty()) {
                    result.add(line)
                }
            }
            return result
        }

        private fun appendEllipsis(
                line: String,
                metrics: java.awt.FontMetrics,
                maxWidth: Int,
        ): String {
            val ellipsis = "…"
            if (metrics.stringWidth(line + ellipsis) <= maxWidth) return line + ellipsis
            var end = line.length
            while (end > 0 && metrics.stringWidth(line.substring(0, end) + ellipsis) > maxWidth) {
                end--
            }
            return line.substring(0, end).trimEnd() + ellipsis
        }
    }
}
