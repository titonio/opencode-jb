package com.opencode.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * Stores plugin configuration for OpenCode.
 *
 * This service manages persistent settings that are saved across IDE restarts.
 * Settings are stored in `opencode.xml` and automatically loaded by IntelliJ's persistence framework.
 */
@State(
    name = "OpenCodeSettings",
    storages = [Storage("opencode.xml")]
)
@Service(Service.Level.APP)
class OpenCodeSettings : PersistentStateComponent<OpenCodeSettings.State> {

    /**
     * Holds persistent settings for the OpenCode plugin.
     *
     * These settings are automatically saved and loaded by IntelliJ's persistence framework.
     * Changes to properties are persisted when the IDE closes or settings are modified.
     *
     * @property autoRestartOnExit Whether to automatically restart the OpenCode server when the IDE exits.
     * If true, the server will be restarted on the next IDE startup.
     */
    data class State(
        @Suppress("DataClassShouldBeImmutable")
        var autoRestartOnExit: Boolean = false
    )

    private var myState = State()

    /**
     * Returns the current state of the settings.
     *
     * This method is called by IntelliJ's persistence framework to save the current settings.
     *
     * @return The current State object containing all plugin settings
     */
    override fun getState(): State = myState

    /**
     * Loads the settings state from persisted storage.
     *
     * This method is called by IntelliJ's persistence framework to restore the settings
     * from the persisted storage when the IDE starts or settings are reloaded.
     *
     * @param state The State object to load, containing persisted settings values
     */
    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        /**
         * Retrieves the OpenCode settings service instance.
         *
         * @return The singleton OpenCodeSettings instance
         */
        @JvmStatic
        fun getInstance(): OpenCodeSettings = service<OpenCodeSettings>()
    }
}
