# Blackboard — Product & Technical Specification

**Version:** 1.0 (MVP)  
**Project:** Blackboard  
**Type:** JetBrains Rider Plugin  
**Language:** Kotlin  
**Target IDE:** JetBrains Rider  
**Development IDE:** IntelliJ IDEA  
**Priority:** Working MVP first

---

## 1. Project Overview

Blackboard is a lightweight, project-specific scratchpad integrated directly into JetBrains Rider.

Its purpose is to let developers quickly capture thoughts, TODOs, code snippets, debugging notes, and temporary information without leaving their IDE or creating separate text files.

**Core philosophy:**

> Open → Write → Close → Continue coding.

Blackboard must feel like a native part of Rider, not a separate notes application.

### Primary goals

1. Open notes instantly using a keyboard shortcut.
2. Write notes without navigating files or dialogs.
3. Automatically save all changes.
4. Keep notes isolated per project.
5. Support Markdown.
6. Provide a clean, distraction-free interface.
7. Preserve notes across IDE restarts.

### Non-goals for MVP

- Cloud synchronization.
- User accounts.
- AI functionality.
- Collaboration.
- Kanban boards.
- Complex task management.
- External databases.
- Rich-text WYSIWYG editing.

---

## 2. Technology Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| Platform | IntelliJ Platform SDK |
| Build | Gradle Kotlin DSL |
| Gradle Plugin | IntelliJ Platform Gradle Plugin 2.x |
| UI | Swing + JetBrains UI components |
| IDE integration | ToolWindow API |
| Persistence | Project-local Markdown file |
| Settings | PersistentStateComponent, if needed |
| Logging | IntelliJ Logger |
| Tests | JUnit + IntelliJ Platform test framework |

### Compatibility requirements

- Target JetBrains Rider first.
- Select a specific installed Rider build as the initial compatibility target.
- Use the appropriate Java toolchain and IntelliJ Platform SDK version for that Rider build.
- Do not hardcode JDK or platform versions without checking compatibility.
- Configure the Gradle plugin for Rider, not IntelliJ IDEA.
- Do not add ReSharper backend components unless genuinely required.
- Verify plugin compatibility using the available JetBrains plugin verification tooling.

---

## 3. User Experience

### 3.1 Tool Window

Blackboard appears as a standard JetBrains Tool Window on the right side of Rider.

It should support:

- Opening and closing.
- Docking and undocking using standard IDE behavior.
- Resizing.
- IDE light and dark themes.
- Remembering its position through standard Tool Window behavior.
- A compact layout suitable for narrow panels.

The Tool Window title must be `Blackboard`.

### 3.2 Main Layout

```text
┌──────────────────────────────────┐
│ Blackboard                  ⋮  × │
├──────────────────────────────────┤
│ 📁 Current Project               │
├──────────────────────────────────┤
│                                  │
│  Write something...              │
│                                  │
│  ┌────────────────────────────┐  │
│  │ # Notes                    │  │
│  │                            │  │
│  │ - Fix payment retry        │  │
│  │ - Check timeout behavior   │  │
│  │                            │  │
│  │ ## Ideas                   │  │
│  │ Move validation to         │  │
│  │ middleware                 │  │
│  │                            │  │
│  │ ## Snippets                │  │
│  │ ```csharp                  │  │
│  │ await service.Run();       │  │
│  │ ```                        │  │
│  │                            │  │
│  └────────────────────────────┘  │
│                                  │
├──────────────────────────────────┤
│ ✓ Saved automatically           │
└──────────────────────────────────┘
```

This is a conceptual layout, not a pixel-perfect design specification.

### 3.3 Interaction Principles

- The editor must be the main focus.
- Avoid unnecessary toolbars and controls.
- No explicit Save button.
- No modal dialogs during normal note-taking.
- Prefer native IDE components.
- Use existing Rider colors, fonts, borders, and spacing.
- Do not implement a custom theme.

---

## 4. Functional Requirements

### FR-001: Open Blackboard

Blackboard must be accessible through:

1. Rider's Tool Window menu.
2. The Tool Window stripe.
3. A configurable keyboard shortcut.

Suggested default shortcut: `Alt+B`.

The shortcut must be registered using IntelliJ's Action System.

Do not override an existing IDE shortcut silently. If a conflict exists, users must be able to configure the shortcut through the IDE Keymap settings.

**Acceptance criteria:**

- Blackboard opens using the Tool Window stripe.
- The registered action can activate Blackboard.
- Focus moves to the note editor when Blackboard opens through its dedicated action.
- Standard IDE mechanisms can close or hide the panel.

### FR-002: Markdown Editor

The primary editor is a single continuous Markdown document.

Required capabilities:

- Plain-text editing.
- Multiline content.
- Copy/paste.
- Undo/redo.
- Markdown syntax highlighting where supported.
- Standard keyboard navigation.
- Scrolling.
- IDE font and theme integration.

Supported Markdown content includes:

- Headings.
- Lists.
- Checklists.
- Links.
- Inline code.
- Fenced code blocks.
- Blockquotes.
- Bold and italic text.

MVP does not require rendered Markdown preview.

**Implementation preference:**

Investigate using IntelliJ's Editor API with a Markdown file type.

If Markdown language support requires an optional dependency, declare it correctly or provide a plain-text fallback.

Do not assume that the Markdown plugin is installed or enabled.

### FR-003: Automatic Saving

All changes must be saved automatically.

Requirements:

- Use a debounced save mechanism.
- Save after 1 s without typing; while the user keeps typing, save at least every 5 s.
- Do not write to disk on every keystroke.
- Flush pending changes when the project closes or the editor is disposed, where lifecycle permits.
- Never block the UI thread with disk I/O.
- Handle write errors gracefully.
- Never silently discard unsaved content.

**Acceptance criteria:**

1. Type text into Blackboard.
2. Wait for autosave.
3. Close the Tool Window.
4. Reopen it.
5. Verify the text remains.
6. Restart Rider.
7. Verify the text remains.

### FR-004: Project-Specific Notes

Each project must have its own independent Blackboard.

Example:

```text
Project A → Notes A
Project B → Notes B
Project C → Notes C
```

Opening multiple Rider projects must not mix their notes.

Use project-level services rather than application-global note state.

### FR-005: Local Persistence

The default storage location for MVP is:

```text
<project-root>/.idea/blackboard.md
```

Requirements:

- Create the file automatically on first save.
- Load existing content when the Tool Window initializes.
- Use UTF-8 encoding.
- Preserve Markdown content.
- Do not overwrite an existing file with empty content during initialization.
- Avoid creating the file until necessary.
- Support projects without a writable base directory gracefully.
- Handle file read/write failures without crashing Rider.

**Important:** The file must not automatically be added to `.gitignore`.

Users should retain control over whether their notes are committed to Git.

For projects with multiple content roots or no stable project base path, define a safe fallback using the IDE's project storage facilities.

### FR-006: Focus Management

When Blackboard opens through the dedicated action:

- Focus the editor.
- Restore the previous caret position where practical.
- Allow immediate typing.

When Blackboard closes:

- Return focus to the previously active editor when practical.
- Avoid unexpected focus changes when Blackboard is opened by clicking its Tool Window tab.

### FR-007: Empty State

For a new project, show an empty editable document with a subtle placeholder:

`Write something...`

The placeholder must disappear when the user begins typing.

Do not automatically insert example notes into the saved document.

### FR-008: Status Indicator

Provide a small, unobtrusive save status at the bottom of the panel.

Possible states:

- `Saved`
- `Saving...`
- `Unable to save`

Do not show notifications for successful saves.

On persistent save failures, provide a clear indication and a way to retry without losing the in-memory text.

---

## 5. Architecture

Use a simple separation of responsibilities.

### Suggested Project Structure

```text
blackboard/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── README.md
├── gradle/
│   └── wrapper/
└── src/
    ├── main/
    │   ├── kotlin/
    │   │   └── com/blackboard/
    │   │       ├── actions/
    │   │       │   └── ToggleBlackboardAction.kt
    │   │       ├── services/
    │   │       │   └── BlackboardService.kt
    │   │       ├── storage/
    │   │       │   └── BlackboardStorage.kt
    │   │       └── ui/
    │   │           ├── BlackboardToolWindowFactory.kt
    │   │           └── BlackboardPanel.kt
    │   └── resources/
    │       └── META-INF/
    │           └── plugin.xml
    └── test/
        └── kotlin/
            └── com/blackboard/
                └── ...
```

This structure is a recommendation, not a requirement to introduce unnecessary abstractions.

### 5.1 BlackboardToolWindowFactory

Responsibilities:

- Register and create the Tool Window content.
- Initialize the Blackboard panel.
- Connect the UI to the current project.
- Respect IDE disposal lifecycle.

### 5.2 BlackboardPanel

Responsibilities:

- Render the note editor.
- Manage UI focus.
- Display save status.
- Listen for document changes.
- Forward changes to the service.

The panel should not directly implement filesystem persistence.

### 5.3 BlackboardService

Project-level service.

Responsibilities:

- Maintain note state.
- Load notes.
- Coordinate debounced saves.
- Prevent concurrent writes from corrupting data.
- Expose save status.
- Handle lifecycle events.

Avoid unnecessary application-global state.

### 5.4 BlackboardStorage

Responsibilities:

- Resolve the note file path.
- Read Markdown content.
- Write Markdown content.
- Handle missing files.
- Report I/O failures.
- Preserve data integrity.

Use appropriate IntelliJ Platform filesystem APIs or standard Java NIO where suitable.

Do not modify PSI or invoke write actions unless required by the chosen API.

---

## 6. Data Safety

Notes may contain important temporary information, so reliability is critical.

Requirements:

1. Do not replace a non-empty note with empty content due to initialization races.
2. Do not overwrite newer content with an older queued save.
3. Avoid concurrent writes to the same file.
4. Handle project shutdown safely.
5. Report save failures.
6. Preserve the current in-memory content after failed saves.
7. Consider external file modifications.

For MVP, if the Markdown file changes externally while Blackboard has unsaved edits, do not silently overwrite either version.

Implement a basic conflict-safe behavior, or clearly surface a conflict requiring user action.

Where practical, use atomic file replacement to reduce the risk of partial writes.

---

## 7. Performance Requirements

Blackboard should feel instantaneous.

Targets:

| Operation | Target |
|---|---|
| Open Tool Window | Under 200 ms after initialization |
| Editor input | No perceptible lag |
| Autosave debounce | 1 s idle, at most 5 s while typing |
| Typical note size | Up to 1 MB |
| Project startup | No blocking disk I/O on UI thread |

Performance targets are goals, not claims about existing implementation.

Use background execution for potentially blocking operations and follow the threading rules of the target IntelliJ Platform version.

Avoid unnecessary dependencies.

---

## 8. UI Guidelines

The interface must look native to Rider.

### Colors

Use IDE theme colors.

Do not hardcode dark-theme backgrounds or text colors.

### Typography

Use IDE-provided editor fonts and settings where possible.

### Icons

Use JetBrains platform icons or compatible bundled icons.

### Spacing

Keep the interface compact.

The panel should remain usable at approximately 300 px width.

### Accessibility

- Keyboard-operable controls.
- Visible focus.
- Appropriate accessible labels.
- No color-only status indicators.
- Respect IDE font scaling.

---

## 9. Future Features — Not MVP

Design the code so these features can be added later, but do not implement them initially.

### Phase 2: Context Integration

- Right-click selected code → `Add to Blackboard`.
- Automatically include a file reference.
- Navigate from a note to a source file and line.
- Copy selected code as a fenced Markdown block.

### Phase 3: Note Organization

- Multiple named notes.
- Pinned notes.
- Search across notes.
- Tags.
- TODO filtering.
- Card view.

### Phase 4: Advanced Features

- Optional Git-branch-specific notes.
- Global notes shared between projects.
- Configurable storage location.
- Markdown preview.
- Import/export.
- Optional synchronization.

---

## 10. Testing Requirements

At minimum, test the following scenarios.

### Storage Tests

- Missing file returns empty content.
- Existing file loads correctly.
- UTF-8 and Unicode content survive save/reload.
- Markdown formatting remains unchanged.
- File creation works.
- Write errors are handled.
- Concurrent saves cannot overwrite newer content.

### Integration Tests

- Tool Window initializes successfully.
- Notes remain isolated per project.
- Notes survive panel reopening.
- Notes survive IDE restart.
- Action registration works.
- No errors occur when closing a project with pending changes.

### Manual UI Tests

- Dark theme.
- Light theme.
- Narrow Tool Window.
- Long documents.
- Large pasted code blocks.
- Keyboard navigation.
- Undo/redo.
- Multiple open projects.

---

## 11. Definition of Done

The MVP is complete only when:

- [ ] Plugin builds successfully.
- [ ] Plugin runs in a sandbox Rider instance.
- [ ] Blackboard appears in the right Tool Window area.
- [ ] User can type and edit Markdown.
- [ ] Changes save automatically.
- [ ] Notes persist after Rider restart.
- [ ] Different projects have different notes.
- [ ] Keyboard action is registered and configurable.
- [ ] No blocking file operations occur on the UI thread.
- [ ] Save errors do not silently destroy content.
- [ ] UI works in dark and light themes.
- [ ] Basic tests pass.
- [ ] Plugin ZIP can be built.
- [ ] README includes installation and development instructions.

---

## 12. Implementation Plan

### Step 1 — Bootstrap

Create the Kotlin Gradle plugin project.

Configure:

- IntelliJ Platform Gradle Plugin.
- Rider target platform.
- Compatible Java toolchain.
- `plugin.xml`.
- Plugin metadata.
- Sandbox execution.

Verify the empty plugin loads in Rider.

### Step 2 — Tool Window

Create the Blackboard Tool Window.

Add an editable text component.

Verify typing and focus behavior.

### Step 3 — Persistence

Implement project-local Markdown storage.

Load existing notes and save modifications.

Verify persistence across restarts.

### Step 4 — Autosave

Implement debouncing, status reporting, and safe lifecycle handling.

### Step 5 — Keyboard Action

Register the Blackboard action and provide the suggested shortcut when available.

### Step 6 — Polish

Improve spacing, themes, empty state, and focus handling.

### Step 7 — Tests and Packaging

Run tests, build the plugin ZIP, and verify installation in Rider.

---

## 13. Instructions for the Coding Agent

You are implementing Blackboard, a lightweight JetBrains Rider plugin written in Kotlin.

Follow these rules:

1. **Implement the MVP only.** Do not add cards, AI, synchronization, databases, or advanced features.
2. Use current, documented IntelliJ Platform APIs compatible with the selected Rider build.
3. Inspect the local environment and available Rider SDK/build before choosing dependency versions.
4. If Rider is not installed or the required SDK cannot be resolved, explain the blocker rather than pretending the plugin was tested.
5. Prefer simple, maintainable code over abstractions.
6. Follow IntelliJ Platform threading, disposal, and lifecycle requirements.
7. Keep filesystem logic separate from UI code.
8. Never silently lose or overwrite user notes.
9. Use native JetBrains UI components.
10. Ensure the plugin targets Rider rather than only IntelliJ IDEA.
11. Write meaningful automated tests.
12. After each implementation stage, run the available build or test checks and fix failures.
13. Do not claim successful Rider execution without actually running it.
14. Document all setup steps and known limitations.

### Expected Deliverables

Provide:

- Complete source code.
- Gradle build configuration.
- Plugin descriptor.
- Automated tests.
- README with development instructions.
- Instructions for running the plugin in a Rider sandbox.
- Instructions for building and installing the plugin ZIP.
- A summary of what was implemented.
- A list of anything not tested or still incomplete.

### Final Success Scenario

A developer opens Rider, activates Blackboard, writes:

```markdown
# Today

- [ ] Fix retry policy
- [ ] Review payment timeout

## Idea

Extract validation into middleware.
```

The developer returns to coding, closes Blackboard, and later restarts Rider.

When Blackboard opens again, the exact notes are still present.

**This scenario must work reliably before adding any further features.**

---

**End of specification.**