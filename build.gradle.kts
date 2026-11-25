import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.20.1"
    id("org.jetbrains.changelog") version "2.2.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    // IntelliJ Platform 2026.2 (262) is compiled for Java 25.
    jvmToolchain(25)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val localPath = providers.gradleProperty("platformLocalPath").orNull
        if (localPath.isNullOrBlank()) {
            rider(providers.gradleProperty("platformVersion"))
        } else {
            local(localPath)
        }
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
    // Matches the platform's bundled coroutines (1.10.2-intellij); core itself comes from the IDE.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2") {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    }
}

intellijPlatform {
    pluginConfiguration {
        id = providers.gradleProperty("pluginGroup")
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        // "What's New" on the Marketplace page: the CHANGELOG.md section of this version (or Unreleased).
        // A block-local reference, so the lambda captures neither `project` nor the script (configuration cache).
        val changelogExtension = project.changelog
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelogExtension) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            // Verify locally against the same Rider build the plugin is compiled against, never download extra IDEs
            // (the plugin only uses the IntelliJ Platform; JetBrains Marketplace verifies every other IDE on upload)
            // (recommended() would fetch several multi-GB builds).
            val localPath = providers.gradleProperty("platformLocalPath").orNull
            if (localPath.isNullOrBlank()) {
                // The SDK artifact already resolved for compilation (Gradle cache), not the installer.
                create(IntelliJPlatformType.Rider, providers.gradleProperty("platformVersion")) {
                    useInstaller = false
                }
            } else {
                local(localPath)
            }
        }
    }
}

changelog {
    groups.empty()
    repositoryUrl = "https://github.com/AntonPetrov83/rider-blackboard"
}
