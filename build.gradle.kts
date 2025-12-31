import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.10.5"
    id("org.jetbrains.kotlinx.kover") version "0.7.5"
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.plugins.terminal")

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
    }

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Use Platform's bundled coroutines instead of forcing our own version
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    // Detekt formatting ruleset
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.6")

    // Testing dependencies
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.junit.vintage:junit-vintage-engine:5.10.0") // Support for JUnit 3/4 tests (BasePlatformTestCase)
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.github.artsok:rerunner-jupiter:2.1.6") // Flaky test retry support
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }
    
    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks.test {
    useJUnitPlatform()
    // Ensure the kover temp directory exists
    doFirst {
        file("${layout.buildDirectory.get()}/tmp/test").mkdirs()
    }
}

koverReport {
    defaults {
        html {
            onCheck = true
        }
        xml {
            onCheck = true
        }
    }
    
    filters {
        excludes {
            classes("*.icons.*")
            classes("*.toolwindow.OpenCodeToolWindowPanel")
            classes("*.editor.OpenCodeEditorPanel")
        }
    }
    
    verify {
        rule {
            minBound(35)
        }
    }
}

detekt {
    config.setFrom(files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false

    // Add source set exclusion
    source.setFrom(
        files(
            "src/main/kotlin",
            "src/main/java"
        )
    )
}
