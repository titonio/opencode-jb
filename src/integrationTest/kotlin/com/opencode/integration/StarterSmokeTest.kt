package com.opencode.integration

import com.intellij.driver.client.Driver
import com.intellij.driver.driver
import com.intellij.driver.model.Product
import com.intellij.platform.ide.starter.Starter
import com.intellij.platform.ide.starter.config.ConfigurationStorage
import com.intellij.platform.ide.starter.config.ProductInfo
import com.intellij.platform.ide.starter.di.di
import com.intellij.platform.ide.starter.runner.CurrentTestMethod
import com.intellij.platform.ide.starter.runner.Scheduler
import com.intellij.platform.ide.starter.runner.TestCase
import com.intellij.platform.ide.starter.runner.TestContext
import com.intellij.platform.ide.starter.telemetry.TraceManager
import com.intellij.platform.ide.starter.utils.reportException
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.time.Duration.Companion.minutes

class StarterSmokeTest {
    @Test
    fun `ide starts with plugin installed`() {
        val pluginPath = System.getProperty("path.to.build.plugin")
            ?: error("path.to.build.plugin system property not set")

        val testName = CurrentTestMethod.get() ?: "starter-smoke"

        // Configure Starter context
        val testCase = TestCase(
            product = ProductInfo.from(Product.IU),
            testName = testName
        )

        val context = Starter.newContext(testCase).apply {
            // Install plugin from prepared sandbox folder
            com.intellij.platform.ide.starter.plugins.PluginConfigurator(this)
                .installPluginFromFolder(File(pluginPath))
        }

        // Run IDE with Driver and wait for idle
        context.runIdeWithDriver().use { ide ->
            ide.driver { waitForSmartMode() }
            ide.driver { waitForBackgroundTasks() }
        }
    }
}
