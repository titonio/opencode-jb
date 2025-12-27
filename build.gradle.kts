import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.10.5"
    id("org.jetbrains.kotlinx.kover") version "0.7.5"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Integration test source set using IntelliJ Starter/Driver
sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val integrationTestImplementation = configurations.getByName("integrationTestImplementation")
integrationTestImplementation.extendsFrom(configurations.getByName("testImplementation"))
configurations.getByName("integrationTestRuntimeOnly")
    .extendsFrom(configurations.getByName("testRuntimeOnly"))

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.plugins.terminal")

        testFramework(TestFrameworkType.Starter)
    }

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Use Platform's bundled coroutines instead of forcing our own version
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    // Testing dependencies
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.junit.vintage:junit-vintage-engine:5.10.0") // Support for JUnit 3/4 tests (BasePlatformTestCase)
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.github.artsok:rerunner-jupiter:2.1.6") // Flaky test retry support

    // IntelliJ Starter + Driver for integration tests
    add("integrationTestImplementation", "com.jetbrains.intellij.tools:ide-starter-squashed:253.28294.334")
    add("integrationTestImplementation", "com.jetbrains.intellij.tools:ide-starter-junit5:253.28294.334")
    add("integrationTestImplementation", "com.jetbrains.intellij.tools:ide-starter-driver:253.28294.334")
    add("integrationTestImplementation", "com.jetbrains.intellij.driver:driver-client:253.28294.334")
    add("integrationTestImplementation", "com.jetbrains.intellij.driver:driver-sdk:253.28294.334")
    add("integrationTestImplementation", "com.jetbrains.intellij.driver:driver-model:253.28294.334")

    add("integrationTestImplementation", "org.junit.jupiter:junit-jupiter:5.10.0")
    add("integrationTestImplementation", "org.kodein.di:kodein-di-jvm:7.20.2")
    add("integrationTestImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")
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
    filter {
        // Legacy BasePlatformTestCase suites fail to initialize IntelliJ on CI; run via integrationTest instead
        excludeTestsMatching("com.opencode.editor.*PlatformTest*")
        excludeTestsMatching("com.opencode.test.platform.*PlatformTest*")
        excludeTestsMatching("com.opencode.utils.FileUtilsPlatformTest")
        excludeTestsMatching("com.opencode.ui.SessionListDialogPlatformTest")
    }
    // Ensure the kover temp directory exists
    doFirst {
        file("${layout.buildDirectory.get()}/tmp/test").mkdirs()
    }
}

val integrationTestSourceSet = sourceSets.named("integrationTest").get()

tasks.register<Test>("integrationTest") {
    description = "Runs integration (Starter/Driver) tests"
    group = "verification"
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    val pluginDir = tasks.named<PrepareSandboxTask>("prepareSandbox").get().pluginDirectory.get().asFile
    systemProperty("path.to.build.plugin", pluginDir.absolutePath)
    dependsOn(tasks.named("prepareSandbox"))
}

tasks.check {
    dependsOn(tasks.named("integrationTest"))
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
