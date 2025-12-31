package com.opencode.settings

import com.intellij.openapi.options.Configurable
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Configurable UI for OpenCode plugin settings.
 *
 * This class implements IntelliJ's [Configurable] interface to provide a settings panel
 * in the IDE's Settings/Preferences dialog. It allows users to configure OpenCode
 * behavior such as automatic restart when the terminal process exits.
 *
 * The settings are persisted through [OpenCodeSettings] and include:
 * - Auto-restart on exit: Whether OpenCode should automatically restart after termination
 *
 * Lifecycle:
 * - [createComponent]: Creates the UI components when the settings dialog opens
 * - [reset]: Loads current settings into the UI
 * - [isModified]: Checks if user made changes
 * - [apply]: Saves user changes to persistent storage
 * - [disposeUIResources]: Cleans up UI references when dialog closes
 */
class OpenCodeConfigurable : Configurable {

    private var settingsPanel: JPanel? = null
    private var autoRestartCheckbox: JCheckBox? = null

    override fun getDisplayName(): String = "OpenCode"

    /**
     * Creates the settings UI component.
     *
     * @return The root JComponent containing all settings controls
     */
    override fun createComponent(): JComponent {
        val panel = JPanel(BorderLayout())
        val formPanel = JPanel()
        formPanel.layout = BoxLayout(formPanel, BoxLayout.Y_AXIS)

        // Auto-restart checkbox
        autoRestartCheckbox = JCheckBox("Automatically restart OpenCode when terminal exits")
        formPanel.add(autoRestartCheckbox)

        // Help text
        val helpLabel = JLabel(
            "<html><font size='-2' color='gray'>" +
                "When enabled, OpenCode will automatically restart when the process exits.<br>" +
                "When disabled, you'll need to click the restart button manually." +
                "</font></html>"
        )
        formPanel.add(helpLabel)

        panel.add(formPanel, BorderLayout.NORTH)
        settingsPanel = panel

        return panel
    }

    /**
     * Checks if any settings have been modified by the user.
     *
     * @return true if the current UI state differs from saved settings, false otherwise
     */
    override fun isModified(): Boolean {
        val settings = OpenCodeSettings.getInstance()
        return autoRestartCheckbox?.isSelected != settings.state.autoRestartOnExit
    }

    /**
     * Applies the current UI settings to persistent storage.
     * Saves the selected options to OpenCodeSettings.
     */
    override fun apply() {
        val settings = OpenCodeSettings.getInstance()
        settings.state.autoRestartOnExit = autoRestartCheckbox?.isSelected ?: false
    }

    /**
     * Resets the UI to match the currently saved settings.
     * Reverts any unsaved changes in the settings panel.
     */
    override fun reset() {
        val settings = OpenCodeSettings.getInstance()
        autoRestartCheckbox?.isSelected = settings.state.autoRestartOnExit
    }

    /**
     * Disposes of UI resources when the settings panel is closed.
     * Cleans up references to Swing components to prevent memory leaks.
     */
    override fun disposeUIResources() {
        settingsPanel = null
        autoRestartCheckbox = null
    }
}
