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
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
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
            
            val port = Random.nextInt(16384, 65536)
            val isWindows = System.getProperty("os.name").lowercase().contains("win")

            val command = if (isWindows) {
                "opencode --port $port"
            } else {
                "export _EXTENSION_OPENCODE_PORT=$port; export OPENCODE_CALLER=intellij; opencode --port $port"
            }

            val terminalView = TerminalToolWindowManager.getInstance(project)
            val widget = terminalView.createLocalShellWidget(project.basePath, terminalName)
            widget.background = UIUtil.getPanelBackground()
            
            panel.setContent(widget)
            
            widgetPorts[widget] = port
            widget.executeCommand(command)
            
            ApplicationManager.getApplication().executeOnPooledThread {
                 waitForConnection(port)
            }
        }
    }

    fun openTerminal(newTab: Boolean = false, initialFile: String? = null) {
        val termToolWindow = ToolWindowManager.getInstance(project).getToolWindow("Terminal")
        termToolWindow?.show()

        var widget: JBTerminalWidget? = null

        if (!newTab) {
            // Find existing widget
            val iterator = widgetPorts.iterator()
            while (iterator.hasNext()) {
                val (w, _) = iterator.next()
                // Check if widget is still valid (simple check)
                if (!w.isDisplayable) {
                     iterator.remove()
                     continue
                }
                widget = w
                break
            }
        }

        if (widget != null) {
            val port = widgetPorts[widget]
            if (port != null && initialFile != null) {
                appendPromptAsync(port, "In $initialFile")
            }
            return
        }
        
        // Create new terminal
        val port = Random.nextInt(16384, 65536)
        val isWindows = System.getProperty("os.name").lowercase().contains("win")

        val command = if (isWindows) {
            "opencode --port $port"
        } else {
            "export _EXTENSION_OPENCODE_PORT=$port; export OPENCODE_CALLER=intellij; opencode --port $port"
        }

        val terminalView = TerminalToolWindowManager.getInstance(project)
        val newWidget = terminalView.createLocalShellWidget(project.basePath, terminalName)
        widget = newWidget
        widgetPorts[newWidget] = port
        
        newWidget.executeCommand(command)

        if (initialFile != null) {
             ApplicationManager.getApplication().executeOnPooledThread {
                 if (waitForConnection(port)) {
                     appendPrompt(port, "In $initialFile")
                 }
             }
        }
    }

    fun addFilepath(filepath: String) {
        val iterator = widgetPorts.iterator()
        var activeWidget: JBTerminalWidget? = null
        
        while (iterator.hasNext()) {
            val (w, _) = iterator.next()
            if (!w.isDisplayable) {
                 iterator.remove()
                 continue
            }
            activeWidget = w
            break // Just take the first one
        }
        
        if (activeWidget != null) {
            val port = widgetPorts[activeWidget]
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
    
    private fun javax.swing.JComponent.isAncestorOf(c: javax.swing.JComponent): Boolean {
        var p = c.parent
        while (p != null) {
            if (p == this) return true
            p = p.parent
        }
        return false
    }
}
