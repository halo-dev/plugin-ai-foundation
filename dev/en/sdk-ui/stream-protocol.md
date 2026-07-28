# SDK UI: Stream protocol

[简体中文](../../zh-CN/sdk-ui/stream-protocol.md) | English

Halo UI Message streams use Server-Sent Events. Each `data:` frame contains one JSON
`UIMessageChunk`; `data: [DONE]` ends the stream.

```http
Content-Type: text/event-stream
X-Halo-AI-UI-Message-Stream: v1
Cache-Control: no-cache
```

## Chunk lifecycle

| Stage                  | Types                                                                              |
| ---------------------- | ---------------------------------------------------------------------------------- |
| Message and step start | `start`, `start-step`                                                              |
| Text                   | `text-start`, `text-delta`, `text-end`                                             |
| Reasoning              | `reasoning-start`, `reasoning-delta`, `reasoning-end`                              |
| Sources and files      | `source-url`, `source-document`, `file`                                            |
| Data and metadata      | `data-*`, `message-metadata`                                                       |
| Tool input             | `tool-input-start`, `tool-input-delta`, `tool-input-available`, `tool-input-error` |
| Tool output            | `tool-output-available`, `tool-output-error`                                       |
| Tool approval          | `tool-approval-request`, `tool-approval-response`                                  |
| Finish                 | `finish-step`, `finish`, `error`, `abort`                                          |

Text and reasoning deltas require their matching start event and are accumulated by ID. A
`start-step` event persists as a fieldless `step-start` part; `finish-step` is diagnostic only.

Persistent data is replaced by type and ID. Transient data is delivered but not stored. Metadata
chunks merge into `UIMessage.metadata`.

Tool input deltas contain `toolCallId` and `inputTextDelta`; the tool name comes from the preceding
start event. Final input replaces best-effort partial JSON. Providers may emit only
`tool-input-available`, which is also valid. Canonical tool chunks reduce into one dynamic
`tool-${toolName}` part per call ID.

Source and file IDs are stable replacement keys. Finish, error, and abort update terminal state
without becoming message parts.

Malformed JSON, invalid ordering, unsupported chunk shapes, and runtime schema failures are
protocol errors. The `[DONE]` marker is not a JSON chunk and is accepted without persistence.
