# Codex WebStorm Plugin

![Build](https://github.com/niyazm524/codex-webstorm-plugin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

Codex WebStorm Plugin brings a dedicated chat tool window to WebStorm, inspired by JetBrains AI Assistant, with a focus on seamless local Codex CLI workflows. The goal is a high-quality, native-feeling UI that lives in the IDE and can drive code changes through an assistant conversation.

## Why this plugin
- Keep a persistent chat panel inside WebStorm so prompts, context, and responses stay with the project.
- Make Codex CLI feel like a first-class IDE assistant without leaving the editor.
- Provide a clean foundation to add rich UX over time (context selection, file references, diffs, action buttons).

## Current scope
- Tool window for chat UI.
- Project-level session with message history.

## Planned features
- Chat UI aligned with JetBrains UX: message list, rich markdown, inline code blocks, and action buttons.
- Context controls: quick add current file, selection, or whole folder to the prompt.
- Streaming responses and cancellable runs.
- "Apply" flow: preview patch and apply changes.
- Session management: named chats, persisted history.
- Telemetry-free local-first mode using Codex CLI.

## Architecture notes (early ideas)
The likely integration path is to run the Codex CLI from the plugin and stream responses back into the UI:
- Spawn a long-lived process (or per-request process) and stream stdout for tokens.
- Maintain a message history in the plugin and send only the required context.
- Map IDE files and selections to Codex CLI input to enable targeted edits.

This may require a lightweight protocol between the plugin and Codex CLI. I need to confirm whether "ACP" is the right protocol or if we should use a simpler JSON streaming format that mirrors how the VS Code extension communicates with Codex.

## Open questions
- How should Codex CLI be invoked from the IDE: bundled binary, user-provided path, or auto-detected?
- Should we use ACP for communication, or is a simpler streaming JSON protocol sufficient?
- Do we want to mirror the VS Code extension behavior exactly, or only the surface UX?
- Where should chat history live: in workspace settings, local storage, or a tool window service?

<!-- Plugin description -->
Codex WebStorm Plugin adds a dedicated chat tool window to WebStorm, inspired by JetBrains AI Assistant.
It integrates with the Codex CLI to provide local-first code assistance directly inside the IDE.

Features:
- Docked chat tool window with message history
- Optional project context injection (files, selection, folders)
- Streaming responses and cancellable runs (planned)
- Patch preview and apply flow (planned)
<!-- Plugin description end -->

## Installation
- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "Codex WebStorm Plugin"</kbd> >
  <kbd>Install</kbd>

- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/niyazm524/codex-webstorm-plugin/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
