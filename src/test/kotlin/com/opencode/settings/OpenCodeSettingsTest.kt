package com.opencode.settings

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OpenCodeSettingsTest {
    
    @Test
    fun `test default state has autoRestartOnExit false`() {
        val state = OpenCodeSettings.State()
        assertFalse(state.autoRestartOnExit)
    }
    
    @Test
    fun `test state can be modified`() {
        val state = OpenCodeSettings.State()
        state.autoRestartOnExit = true
        assertTrue(state.autoRestartOnExit)
    }
    
    @Test
    fun `test state preserves values`() {
        val state = OpenCodeSettings.State(autoRestartOnExit = true)
        assertTrue(state.autoRestartOnExit)
    }
    
    @Test
    fun `test settings loads and saves state`() {
        val settings = OpenCodeSettings()
        val initialState = settings.state
        assertNotNull(initialState)
        
        // Modify state
        initialState.autoRestartOnExit = true
        
        // Load same state
        settings.loadState(initialState)
        assertEquals(initialState.autoRestartOnExit, settings.state.autoRestartOnExit)
    }
    
    @Test
    fun `test getState returns current state`() {
        val settings = OpenCodeSettings()
        val state = settings.state
        state.autoRestartOnExit = true
        
        val retrievedState = settings.getState()
        assertTrue(retrievedState.autoRestartOnExit)
    }
}
