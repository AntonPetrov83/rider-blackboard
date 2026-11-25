# Blackboard

A lightweight, project-specific Markdown scratchpad for JetBrains IDEs
(IntelliJ IDEA, PyCharm, WebStorm, Rider, CLion, GoLand, PhpStorm, RubyMine and others, 2026.2+).
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

The default shortcut `Alt+B` is registered for the `$default` keymap and therefore inherited by the other keymaps.
It was checked against the IntelliJ Platform 2026.2 (build 262) keymaps, including the keymap plugins JetBrains
publishes separately:

| Keymap | `Alt+B` before the plugin | Blackboard shortcut |
|---|---|---|
| Default (`$default`), Default for XWin / GNOME / KDE, macOS, macOS System Shortcuts, Visual Studio (+ 2022, OSX, for Mac), ReSharper (+ OSX), Visual Assist (+ OSX), VSCode (+ OSX), Sublime Text (+ Mac OS X), Eclipse (+ Mac OS X), NetBeans 6.5, QtCreator (+ Mac OS X), Xcode | free | `Alt+B` (`⌥B` on macOS) |
| Emacs | *Backward word* | none — Emacs keeps its binding |

Notes:

- In IDEs with a **Build** main menu (IntelliJ IDEA, Rider, ...) on Windows/Linux, the menu has the mnemonic `Alt+B`. IDE keymap shortcuts take precedence
  over main-menu mnemonics, so with Blackboard installed `Alt+B` toggles Blackboard; the Build menu remains
  reachable by pressing and releasing `Alt`, then `B`, and through its own shortcuts.
- On macOS, `⌥B` normally types a character (`∫` on the U.S. layout); with Blackboard installed it toggles
  Blackboard in the IDE instead. Remove the shortcut in the keymap if you need that character.
- To change or remove the shortcut: **Settings → Keymap**, search for *Toggle Blackboard*.

## Stack

| Component | Version |
|---|---|
| Supported IDEs | All IntelliJ-based IDEs, `sinceBuild = 262` (2026.2+); only `com.intellij.modules.platform` is required |
| Development SDK | JetBrains Rider 2026.2.3.1 (build 262.10968.170) |
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

### Install the ZIP into the IDE

**Settings → Plugins → ⚙ → Install Plugin from Disk…** → select `build/distributions/blackboard-<version>.zip`.

## Documentation
Check these sources before guessing APIs:

- IntelliJ Platform SDK index: https://plugins.jetbrains.com/docs/intellij/llms.txt
  Every page has a Markdown version: append `.md` to the URL (e.g. `plugin-actions.html.md`).
- Rider plugin development (incl. Rider extension point list): https://plugins.jetbrains.com/docs/intellij/rider.html
- ReSharper SDK / DevGuide (backend part of Rider plugins): https://www.jetbrains.com/help/resharper/sdk/
- Reference plugin template for Rider + ReSharper: https://github.com/JetBrains/resharper-rider-plugin
- Platform source code (when docs are insufficient): https://github.com/JetBrains/intellij-community