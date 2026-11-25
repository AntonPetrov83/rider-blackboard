# Changelog

All notable changes to Blackboard are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - 2026-11-25

### Changed

- Blackboard now works in all IntelliJ-based IDEs (IntelliJ IDEA, PyCharm, WebStorm, CLion, GoLand and others), not only Rider.

## [0.1.1] - 2026-11-25

### Added

- Outline tool window and action icon in the New UI style, with light and dark variants.

## [0.1.0] - 2026-11-25

### Added

- Blackboard tool window with a per-project Markdown note, stored as `blackboard.md` in the project's `.idea` folder.
- *Toggle Blackboard* action (`Alt+B`): opens the note with the caret ready for typing, hides it and returns focus to the previous editor.
- Automatic saving after 1 s of idle time, at least every 5 s while typing, and when the project closes.
- Atomic writes, save status line with Retry, failure notifications and detection of external file changes.
- Markdown syntax highlighting (plain text fallback when the Markdown plugin is disabled).
- Notes use the IDE default editor font and the current color scheme.
