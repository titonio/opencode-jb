package com.opencode.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.JBTerminalWidget
import com.intellij.util.ui.UIUtil
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Service(Service.Level.PROJECT)
class OpenCodeService(private val project: Project) {
    private val client by lazy {
        OkHttpClient.Builder()
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }
    private val terminalName = "OpenCode"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    
    // Track widgets and their ports
    private val widgetPorts = mutableMapOf<JBTerminalWidget, Int>()

    fun registerWidget(widget: JBTerminalWidget, port: Int) {
        widgetPorts[widget] = port
    }

    fun unregisterWidget(widget: JBTerminalWidget) {
        widgetPorts.remove(widget)
    }

    fun initToolWindow(toolWindow: ToolWindow) {
        println("OpenCodeService.initToolWindow called")
        val contentFactory = com.intellij.ui.content.ContentFactory.getInstance()
        
        // Ensure that if content already exists, we don't duplicate it or leave it in a weird state
        toolWindow.contentManager.removeAllContents(true)

        ApplicationManager.getApplication().invokeLater {
            val panel = com.intellij.openapi.ui.SimpleToolWindowPanel(true, true)
            panel.background = UIUtil.getPanelBackground()
            val content = contentFactory.createContent(panel, "", false)
            toolWindow.contentManager.addContent(content)
            
            // Set tool window type to be a sliding window or docked, allowing full height
            toolWindow.isAvailable = true
            toolWindow.isShowStripeButton = true
            toolWindow.setType(com.intellij.openapi.wm.ToolWindowType.SLIDING, null)
            
            println("Creating terminal widget...")
            val (widget, port) = createTerminalWidget()
            println("Created terminal widget on port $port")
            
            panel.setContent(widget.component)
        }
    }

    fun createTerminalWidget(): Pair<JBTerminalWidget, Int> {
        val port = Random.nextInt(16384, 65536)
        val isWindows = System.getProperty("os.name").lowercase().contains("win")

        val widgetDisposable = com.intellij.openapi.Disposable { }
        com.intellij.openapi.util.Disposer.register(project, widgetDisposable)

        // Use LocalTerminalDirectRunner to avoid manual PTY handling
        val runner = object : org.jetbrains.plugins.terminal.LocalTerminalDirectRunner(project) {
            // Override configureStartupOptions to inject our OpenCode command and environment
            override fun configureStartupOptions(baseOptions: org.jetbrains.plugins.terminal.ShellStartupOptions): org.jetbrains.plugins.terminal.ShellStartupOptions {
                val envs = mutableMapOf<String, String>()
                envs["_EXTENSION_OPENCODE_PORT"] = port.toString()
                envs["OPENCODE_CALLER"] = "intellij"
                if (!isWindows) {
                    envs["TERM"] = "xterm-256color"
                }
                
                return baseOptions.builder()
                    .shellCommand(listOf("opencode", "--port", port.toString()))
                    .envVariables(envs)
                    .build()
            }
        }

        // Use startShellTerminalWidget - the modern replacement for deprecated createTerminalWidget
        val startupOptions = org.jetbrains.plugins.terminal.ShellStartupOptions.Builder()
            .workingDirectory(project.basePath ?: System.getProperty("user.home"))
            .build()
        val terminalWidget = runner.startShellTerminalWidget(widgetDisposable, startupOptions, true)
        
        // Convert TerminalWidget to JBTerminalWidget for compatibility
        val widget = com.intellij.terminal.JBTerminalWidget.asJediTermWidget(terminalWidget)
            ?: throw IllegalStateException("Failed to create JBTerminalWidget")

        widgetPorts[widget] = port
        
        ApplicationManager.getApplication().executeOnPooledThread {
             waitForConnection(port)
        }
        
        return Pair(widget, port)
    }

    fun openTerminal(initialFile: String? = null) {
        println("OpenCodeService.openTerminal called: initialFile=$initialFile")
        
        // Show the OpenCode tool window (not the standard Terminal window)
        val openCodeToolWindow = ToolWindowManager.getInstance(project).getToolWindow("OpenCode")
        if (openCodeToolWindow == null) {
            println("OpenCode tool window is null - not initialized yet")
            return
        }
        
        println("Showing OpenCode tool window, isVisible=${openCodeToolWindow.isVisible}")
        
        // Activate the tool window (this will trigger initToolWindow if not already done)
        openCodeToolWindow.activate {
            println("OpenCode tool window activated")
            
            // Wait a bit for initialization to complete if needed
            ApplicationManager.getApplication().invokeLater {
                sendFileToOpenCode(initialFile)
            }
        }
    }
    
    private fun sendFileToOpenCode(initialFile: String?) {
        // Find the OpenCode terminal widget (there should be one from initToolWindow)
        val widget = widgetPorts.keys.firstOrNull()
        println("Found widget: $widget, widgetPorts size: ${widgetPorts.size}")
        
        if (widget != null && initialFile != null) {
            val port = widgetPorts[widget]
            println("Sending file to port $port: $initialFile")
            if (port != null) {
                appendPromptAsync(port, initialFile)
            }
        } else if (initialFile != null) {
            println("Widget not found, cannot send file: $initialFile")
        }
    }

    fun addFilepath(filepath: String) {
        // Find the OpenCode terminal widget
        val widget = widgetPorts.keys.firstOrNull { it.isDisplayable }
        
        if (widget != null) {
            val port = widgetPorts[widget]
            if (port != null) {
                appendPromptAsync(port, filepath)
            }
        }
    }

    private fun appendPromptAsync(port: Int, text: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                appendPrompt(port, text)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun appendPrompt(port: Int, text: String) {
        val json = "{\"text\": \"$text\"}"
        val request = Request.Builder()
            .url("http://localhost:$port/tui/append-prompt")
            .post(json.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
        }
    }

    private fun waitForConnection(port: Int): Boolean {
        var tries = 10
        while (tries > 0) {
            try {
                Thread.sleep(200)
                val request = Request.Builder()
                    .url("http://localhost:$port/app")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return true
                }
            } catch (e: Exception) {
                // Ignore
            }
            tries--
        }
        return false
    }
}
