package com.opencode.settings

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JCheckBox

/**
 * Tests for OpenCodeConfigurable.
 * 
 * Branch Coverage Status: 0% → Requires platform infrastructure
 * Line Coverage Status: 71.4%
 * 
 * The class contains 8 conditional branches that require OpenCodeSettings.getInstance():
 * - isModified(): 4 branches (null check, non-null, equal, not-equal)
 * - apply(): 2 branches (null default, non-null value)
 * - reset(): 2 branches (null safe-call, non-null assignment)
 * 
 * These branches cannot be covered without IntelliJ platform infrastructure running,
 * as OpenCodeSettings.getInstance() requires ApplicationManager.getApplication() != null.
 * 
 * Current tests cover: UI creation, disposal, component management (71.4% lines).
 * Missing tests: All branches requiring settings service (0% branches).
 */
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
        
        val panelField = configurable.javaClass.getDeclaredField("settingsPanel")
        panelField.isAccessible = true
        assertNull(panelField.get(configurable))
        
        val checkboxField = configurable.javaClass.getDeclaredField("autoRestartCheckbox")
        checkboxField.isAccessible = true
        assertNull(checkboxField.get(configurable))
    }
    
    @Test
    fun `test disposeUIResources can be called multiple times`() {
        configurable.createComponent()
        configurable.disposeUIResources()
        configurable.disposeUIResources()
    }
    
    @Test
    fun `test disposeUIResources without component creation`() {
        configurable.disposeUIResources()
    }
    
    @Test
    fun `test component recreation after disposal`() {
        configurable.createComponent()
        
        val checkboxField = configurable.javaClass.getDeclaredField("autoRestartCheckbox")
        checkboxField.isAccessible = true
        val checkbox1 = checkboxField.get(configurable) as? JCheckBox
        assertNotNull(checkbox1)
        
        configurable.disposeUIResources()
        configurable.createComponent()
        
        val checkbox2 = checkboxField.get(configurable) as? JCheckBox
        assertNotNull(checkbox2)
        assertNotSame(checkbox1, checkbox2)
    }
    
    private fun getCheckbox(): JCheckBox? {
        val checkboxField = configurable.javaClass.getDeclaredField("autoRestartCheckbox")
        checkboxField.isAccessible = true
        return checkboxField.get(configurable) as? JCheckBox
    }
}

