package com.opencode.settings

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JCheckBox

class OpenCodeConfigurableTest {
    
    private lateinit var configurable: OpenCodeConfigurable
    
    @BeforeEach
    fun setUp() {
        configurable = OpenCodeConfigurable()
    }
    
    @Test
    fun `test display name is OpenCode`() {
        assertEquals("OpenCode", configurable.displayName)
    }
    
    @Test
    fun `test createComponent creates UI`() {
        val component = configurable.createComponent()
        assertNotNull(component)
    }
    
    @Test
    fun `test createComponent creates checkbox`() {
        configurable.createComponent()
        
        val checkboxField = configurable.javaClass.getDeclaredField("autoRestartCheckbox")
        checkboxField.isAccessible = true
        val checkbox = checkboxField.get(configurable) as? JCheckBox
        
        assertNotNull(checkbox)
        assertEquals("Automatically restart OpenCode when terminal exits", checkbox?.text)
    }
    
    @Test
    fun `test disposeUIResources cleans up`() {
        configurable.createComponent()
        configurable.disposeUIResources()
        
        // Verify fields are nulled
        val panelField = configurable.javaClass.getDeclaredField("settingsPanel")
        panelField.isAccessible = true
        assertNull(panelField.get(configurable))
        
        val checkboxField = configurable.javaClass.getDeclaredField("autoRestartCheckbox")
        checkboxField.isAccessible = true
        assertNull(checkboxField.get(configurable))
    }
}
