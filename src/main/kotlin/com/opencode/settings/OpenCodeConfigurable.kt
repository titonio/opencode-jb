package com.opencode.settings

import com.intellij.openapi.options.Configurable
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class OpenCodeConfigurable : Configurable {
    
    private var settingsPanel: JPanel? = null
    private var autoRestartCheckbox: JCheckBox? = null
    
    override fun getDisplayName(): String = "OpenCode"
    
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
    
    override fun isModified(): Boolean {
        val settings = OpenCodeSettings.getInstance()
        return autoRestartCheckbox?.isSelected != settings.state.autoRestartOnExit
    }
    
    override fun apply() {
        val settings = OpenCodeSettings.getInstance()
        settings.state.autoRestartOnExit = autoRestartCheckbox?.isSelected ?: false
    }
    
    override fun reset() {
        val settings = OpenCodeSettings.getInstance()
        autoRestartCheckbox?.isSelected = settings.state.autoRestartOnExit
    }
    
    override fun disposeUIResources() {
        settingsPanel = null
        autoRestartCheckbox = null
    }
}
