# Blackboard

A lightweight, project-specific Markdown scratchpad for JetBrains Rider.
See [specs/writing-block.md](specs/writing-block.md) for the full specification.

## Usage

- **Open:** click the *Blackboard* stripe button on the right, use **View → Tool Windows → Blackboard**,
  or press `Alt+B` (the *Toggle Blackboard* action, also available via **Find Action**).
- **Toggle Blackboard** (action id `Blackboard.Toggle`):
  - hidden, or shown without focus → shows Blackboard and puts the caret into the note, so you can type at once;
  - shown and focused → hides it and returns focus to the editor you came from (or to the active editor if
    that one is gone).
- Opening via the stripe button keeps the platform's standard focus behaviour.
- Caret position and scroll are kept in memory while the project is open (not saved to disk).

### Keyboard shortcut

The default shortcut `Alt+B` is registered for the `$default` keymap and therefore inherited by the keymaps
Rider ships with. It was checked against the Rider 2026.2 (build 262) keymaps:

| Keymap | `Alt+B` before the plugin | Blackboard shortcut |
|---|---|---|
| Default (`$default`), Visual Studio, Visual Studio 2022, ReSharper, Visual Assist, VSCode, Sublime Text | free | `Alt+B` |
| Emacs | *Backward word* | none — Emacs keeps its binding |

Notes:

- On Windows/Linux the main menu item **Build** has the mnemonic `Alt+B`. IDE keymap shortcuts take precedence
  over main-menu mnemonics, so with Blackboard installed `Alt+B` toggles Blackboard; the Build menu remains
  reachable by pressing and releasing `Alt`, then `B`, and through its own shortcuts.
- To change or remove the shortcut: **Settings → Keymap**, search for *Toggle Blackboard*.

## Stack

| Component | Version |
|---|---|
| Target IDE | JetBrains Rider 2026.2.3.1 (build 262.10968.170), `sinceBuild = 262` |
| Language | Kotlin 2.4.21 |
| JDK toolchain | 25 (auto-provisioned via Foojay; the 262 platform is compiled for Java 25) |
| Build | Gradle 9.9.0 (wrapper), IntelliJ Platform Gradle Plugin 2.20.1 |

## Development

Open the project folder in IntelliJ IDEA (**File → Open** → select the folder) and let Gradle import it.
The Gradle daemon JVM is pinned to JDK 25 in `gradle/gradle-daemon-jvm.properties` (downloaded automatically if missing). In IDEA keep *Settings → Build Tools → Gradle → Gradle JVM* set to "Daemon JVM criteria" — do not let it download a newer JDK.

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

## Documentation
Check these sources before guessing APIs:

- IntelliJ Platform SDK index: https://plugins.jetbrains.com/docs/intellij/llms.txt
  Every page has a Markdown version: append `.md` to the URL (e.g. `plugin-actions.html.md`).
- Rider plugin development (incl. Rider extension point list): https://plugins.jetbrains.com/docs/intellij/rider.html
- ReSharper SDK / DevGuide (backend part of Rider plugins): https://www.jetbrains.com/help/resharper/sdk/
- Reference plugin template for Rider + ReSharper: https://github.com/JetBrains/resharper-rider-plugin
- Platform source code (when docs are insufficient): https://github.com/JetBrains/intellij-community