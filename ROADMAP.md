# Roadmap

This document tracks the planned milestones for the Codex WebStorm Plugin. It is intentionally lightweight and will evolve as we validate the integration approach.

## Phase 0: Discovery and constraints
- Confirm how the Codex CLI is invoked (binary location, config, auth).
- Inspect the VS Code extension behavior (protocol, streaming, context packing).
- Decide on protocol: ACP vs minimal JSON streaming.
- Define UX scope for the first usable prototype.

## Phase 1: Minimal chat tool window
- Build a basic tool window UI (message list + input box).
- Create a project-level chat session model.
- Store session state in memory and ensure it survives tool window reopen.
- Stub "Send" to a fake responder for UI iteration.

## Phase 2: Codex CLI integration
- Add a configurable CLI path with validation.
- Spawn the CLI process and stream responses into the UI.
- Handle cancellation, errors, and timeouts.
- Log and surface failures with actionable messaging.

## Phase 3: Context controls
- Add buttons to attach current file, selection, or folder.
- Normalize context payload (paths, snippets, metadata).
- Make context visible per message (chips or badges).

## Phase 4: Apply changes flow
- Show patch previews with diff viewer.
- Implement "Apply" and "Reject" actions.
- Track applied changes in the session history.

## Phase 5: Persistence and polish
- Persist chat history per project.
- Add named sessions and basic search.
- Improve UI visuals to match JetBrains design patterns.

## Phase 6: QA and release
- Add tests for CLI integration and UI behavior.
- Verify performance on large projects.
- Prepare Marketplace metadata and docs.
