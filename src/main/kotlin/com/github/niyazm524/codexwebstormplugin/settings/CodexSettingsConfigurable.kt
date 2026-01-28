package com.github.niyazm524.codexwebstormplugin.settings

import com.github.niyazm524.codexwebstormplugin.services.CodexExecutableLocator
import com.github.niyazm524.codexwebstormplugin.services.CodexSettingsState
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import java.awt.FlowLayout
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class CodexSettingsConfigurable : Configurable {
    private val settings = service<CodexSettingsState>()
    private var component: JPanel? = null
    private val executableField = TextFieldWithBrowseButton()
    private val envField =
            JBTextArea().apply {
                rows = 4
                lineWrap = true
                wrapStyleWord = true
                margin = Insets(4, 6, 4, 6)
            }

    override fun getDisplayName(): String = "Codex"

    override fun createComponent(): JComponent {
        executableField.addBrowseFolderListener(
                "Select Codex Executable",
                "Choose the codex CLI executable",
                null,
                FileChooserDescriptorFactory.createSingleFileDescriptor()
        )
        executableField.textField.toolTipText = "Path to codex executable"
        val detectButton =
                JButton("Detect").apply {
                    toolTipText = "Try to detect codex automatically"
                    addActionListener {
                        val detected = CodexExecutableLocator.detectCodexExecutable()
                        if (detected != null) {
                            executableField.text = detected
                        }
                    }
                }

        component =
                FormBuilder.createFormBuilder()
                        .addLabeledComponent("Codex executable", executableField)
                        .addComponent(
                                JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                                    add(detectButton)
                                }
                        )
                        .addLabeledComponent(
                                "Extra environment (KEY=VALUE, one per line)",
                                envField
                        )
                        .panel
        reset()
        return component as JPanel
    }

    override fun isModified(): Boolean {
        val stored = settings.getCodexExecutablePath().orEmpty()
        val storedEnv = settings.getExtraEnv().orEmpty()
        return stored != executableField.text.orEmpty() ||
                storedEnv != envField.text.orEmpty()
    }

    override fun apply() {
        settings.setCodexExecutablePath(executableField.text)
        settings.setExtraEnv(envField.text)
    }

    override fun reset() {
        executableField.text = settings.getCodexExecutablePath().orEmpty()
        envField.text = settings.getExtraEnv().orEmpty()
    }

    override fun disposeUIResources() {
        component = null
    }
}
