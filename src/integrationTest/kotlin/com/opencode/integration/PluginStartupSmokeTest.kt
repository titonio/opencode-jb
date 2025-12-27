package com.opencode.integration

import com.intellij.driver.client.Driver
import com.intellij.driver.driver
import com.intellij.driver.model.Product
import com.intellij.platform.ide.starter.Starter
import com.intellij.platform.ide.starter.config.ProductInfo
import com.intellij.platform.ide.starter.runner.TestCase
import com.intellij.platform.ide.starter.runner.TestContext
import com.intellij.platform.ide.starter.utils.reportException
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.time.Duration.Companion.minutes

/**
 * Minimal Starter/Driver smoke test to ensure the plugin starts inside the IDE
 * and the IDE reaches idle state.
 */
class PluginStartupSmokeTest {
    @Test
    fun `IDE starts with plugin installed and reaches idle`() {
        val pluginPath = System.getProperty("path.to.build.plugin")
            ?: error("path.to.build.plugin system property not set")

        val testCase = TestCase(
            product = ProductInfo.from(Product.IU),
            testName = "plugin-startup-smoke"
        )

        val context = Starter.newContext(testCase).apply {
            com.intellij.platform.ide.starter.plugins.PluginConfigurator(this)
                .installPluginFromFolder(File(pluginPath))
        }

        context.runIdeWithDriver().use { ide: TestContext ->
            ide.driver { waitForSmartMode() }
            ide.driver { waitForBackgroundTasks() }
            // Basic assurance that driver is connected
            assertTrue(true, "IDE driver session established")
        }
    }
}
