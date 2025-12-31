package com.opencode.icons

import com.intellij.openapi.util.IconLoader

/**
 * Central registry of icons used throughout the OpenCode plugin.
 *
 * This object serves as a singleton container for all plugin-related icons, providing a
 * single source of truth for icon resources. Icons are loaded from the plugin's resource
 * directory using IntelliJ's [IconLoader].
 *
 * Usage example:
 * ```kotlin
 * action.templatePresentation.icon = OpenCodeIcons.ToolWindow
 * ```
 */
object OpenCodeIcons {
    /**
     * The primary icon for the OpenCode tool window.
     *
     * This icon is displayed in the IDE's tool window bar and represents the OpenCode
     * assistant. It's loaded from the SVG resource at `/icons/opencode.svg`.
     *
     * The icon is marked with [JvmField] for Java interop, allowing it to be accessed
     * directly from Java code without calling a getter method.
     */
    @JvmField
    val ToolWindow = IconLoader.getIcon("/icons/opencode.svg", javaClass)
}
