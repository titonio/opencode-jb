package com.opencode.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@State(
    name = "OpenCodeSettings",
    storages = [Storage("opencode.xml")]
)
@Service(Service.Level.APP)
class OpenCodeSettings : PersistentStateComponent<OpenCodeSettings.State> {
    
    data class State(
        var autoRestartOnExit: Boolean = false
    )
    
    private var myState = State()
    
    override fun getState(): State = myState
    
    override fun loadState(state: State) {
        myState = state
    }
    
    companion object {
        fun getInstance(): OpenCodeSettings = service<OpenCodeSettings>()
    }
}
