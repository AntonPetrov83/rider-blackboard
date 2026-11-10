# Blackboard

A lightweight, project-specific Markdown scratchpad for JetBrains Rider.
See [specs/writing-block.md](specs/writing-block.md) for the full specification.

## Stack

| Component | Version |
|---|---|
| Target IDE | JetBrains Rider 2026.2.3.1 (build 262.10968.170), `sinceBuild = 262` |
| Language | Kotlin 2.4.21 |
| JDK toolchain | 25 (auto-provisioned via Foojay; the 262 platform is compiled for Java 25) |
| Build | Gradle 9.9.0 (wrapper), IntelliJ Platform Gradle Plugin 2.20.1 |

## Development

Open the project folder in IntelliJ IDEA (**File → Open** → select the folder) and let Gradle import it.
Gradle itself needs JDK 17+ to run (set *Settings → Build Tools → Gradle → Gradle JVM*).

The first sync downloads the Rider SDK (~ several GB). To use your installed Rider instead,
add to `~/.gradle/gradle.properties`:

```properties
platformLocalPath=C:/Users/<you>/AppData/Local/Programs/Rider
```

### Common tasks

```shell
./gradlew runIde          # run the plugin in a sandbox Rider instance
./gradlew test            # run tests
./gradlew buildPlugin     # build the installable ZIP -> build/distributions/
./gradlew verifyPlugin    # run the JetBrains Plugin Verifier
```

### Install the ZIP into Rider

**Settings → Plugins → ⚙ → Install Plugin from Disk…** → select `build/distributions/blackboard-<version>.zip`.
