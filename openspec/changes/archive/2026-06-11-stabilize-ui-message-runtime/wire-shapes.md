# UI Message Runtime Wire Shapes

This note freezes the intended wire shapes before implementation edits. It is not consumer documentation; `dev/ui-message-stream.md` remains the public guide.

## Dynamic Data Part

```json
{
  "type": "data-weather",
  "id": "weather-1",
  "name": "weather",
  "data": {
    "city": "Hangzhou",
    "status": "sunny"
  },
  "transient": false
}
```

Rules:

- `name` MUST match `^[A-Za-z][A-Za-z0-9_-]*$`.
- `type` MUST equal `data-${name}`.
- `id` MUST be non-blank.
- Non-transient data updates persisted message state by `type + id`.
- Transient data triggers runtime callbacks only and MUST NOT mutate persisted messages.

## Dynamic Tool Part

```json
{
  "type": "tool-getWeather",
  "toolName": "getWeather",
  "toolCallId": "call-1",
  "state": "input-available",
  "input": {
    "city": "Hangzhou"
  },
  "providerMetadata": {}
}
```

Rules:

- `toolName` MUST match `^[A-Za-z][A-Za-z0-9_-]*$`.
- `type` MUST equal `tool-${toolName}`.
- `toolCallId` MUST be non-blank.
- Valid states are `input-streaming`, `input-available`, `approval-requested`, `output-available`, and `output-error`.
- `output-available` and `output-error` are terminal tool states.
- Approval denial is represented as `output-error`.

## Stream Chunk Identity

- Text and reasoning chunks reduce by block `id`.
- Source and file chunks reduce by their stable source/file id.
- Dynamic data chunks reduce by `type + id` when not transient.
- Dynamic tool chunks reduce by `toolCallId`.
- Terminal chunks update stream terminal state and do not become message parts.

## Request Boundary

- Normal chat submission remains `UIMessageChatRequest` with `id`, `messages`, `trigger`, and optional `messageId`.
- Resume is not part of this change. It remains a future reconnect/replay contract and must not be modeled as a chat trigger.
