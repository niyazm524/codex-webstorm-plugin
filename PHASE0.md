# Phase 0 Findings (Discovery)

This document summarizes what we learned from the Codexia VS Code extension and outlines decisions/open questions for the WebStorm plugin.

## Sources inspected
- `/Users/niyaz/Projects/work/atb/codex-webstorm-plugin/resources/codex-app-server-docs.md`
- `/Users/niyaz/Projects/work/atb/codexia-vscode/src/codexService.ts`
- `/Users/niyaz/Projects/work/atb/codexia-vscode/src/eventHandler.ts`
- `/Users/niyaz/Projects/work/atb/codexia-vscode/src/types/protocol.ts`
- `/Users/niyaz/Projects/work/atb/codexia-vscode/docs/approve.md`

## Codex integration model (priority: app-server)
- Use `codex app-server` and communicate over stdin/stdout with JSONL messages.
- Protocol is JSON-RPC 2.0 style but omits the `"jsonrpc":"2.0"` header.
- Must send `initialize` request first, then `initialized` notification before any other method.
- Conversations are modeled as **threads**; each user request is a **turn** within a thread.
- Turns stream progress via `item/*` notifications; `item/agentMessage/delta` provides streaming text.
- The CLI can generate version-matched schemas: `codex app-server generate-ts` or `generate-json-schema`.

## Event types observed (app-server)
- Thread/turn lifecycle: `thread/started`, `turn/started`, `turn/completed`
- Items: `item/started`, `item/completed`
- Item deltas: `item/agentMessage/delta`, `item/commandExecution/outputDelta`, `item/fileChange/outputDelta`
- Turn aggregates: `turn/diff/updated`, `turn/plan/updated`, `thread/tokenUsage/updated`
- Errors: `error` event with `codexErrorInfo` and `turn.status = "failed"`

## Approval flow details (critical)
- Approvals are requests from the server with `item/commandExecution/requestApproval` or `item/fileChange/requestApproval`.
- Client responds with `{ "decision": "accept" | "decline" }` (optionally `acceptSettings` for commands).
- Requests include `threadId`, `turnId`, and `itemId` for scoping UI state.
- Completion arrives in `item/completed` with `status: completed | failed | declined`.

## Config patterns (app-server)
- `thread/start` and `turn/start` accept per-request overrides:
  - `model`, `effort`, `cwd`, `approvalPolicy`, `sandboxPolicy`, `summary`, `outputSchema`
- Sandbox policy types include `readOnly`, `workspaceWrite`, `dangerFullAccess`, and `externalSandbox`.
- `command/exec` provides one-off command execution under a sandbox without a thread.

## Implications for WebStorm plugin
- Prefer `codex app-server` protocol over legacy `codex proto`.
- Implement JSONL JSON-RPC message transport with initialize/initialized handshake.
- Model UI state around threads/turns/items; use item events as source of truth.
- Implement approvals via item-level approval requests and responses.
- Plan for session lifecycle, restart, and timeouts.
- Codex CLI is assumed to be available in `$PATH`.

## Open questions to resolve before Phase 1
- Do we want to support all `approvalPolicy` / `sandboxPolicy` options at launch?
- Should we maintain a long-lived app-server process or spawn per request?
- How should we persist chat history (thread list via app-server vs IDE storage)?
