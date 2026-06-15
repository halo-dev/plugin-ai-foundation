## Context

The Vue runtime package already owns stream parsing, reducer state, data callbacks, tool callbacks, approval continuation, and workbench dogfooding. Message metadata and persisted dynamic data parts are still accepted as unknown values. Callers can validate them after the fact, but invalid values may already be visible in runtime state. The backend Java API has separate `UIMessageValidators`; this change adds frontend runtime validation for stream data as it enters the chat state.

## Goals / Non-Goals

**Goals:**

- Add `messageMetadataSchema` and `dataPartSchemas` to `Chat` and `useChat`.
- Support synchronous Standard Schema-style validation without adding a required schema-library dependency.
- Validate merged message metadata after metadata updates.
- Validate persisted data part payloads by data `name`.
- Store parsed schema output in runtime state and callbacks.
- Route schema failures through a dedicated error type and the existing chat error lifecycle.
- Lightly dogfood hooks in the model test workbench.

**Non-Goals:**

- Java backend schema hooks or changes to backend UI message validators.
- Validation for `setMessages`, constructor-provided history, or manually inserted message state.
- Validation for transient data events.
- Asynchronous schemas.
- Deep TypeScript union inference for each configured data part.
- Stream resume, direct transports, or file upload support.

## Decisions

1. Use a small Standard Schema adapter.

   The runtime will accept schema-like values through a lightweight internal adapter and avoid binding the package to Zod, Valibot, ArkType, or any other specific validator. The adapter will be synchronous. If a schema reports asynchronous behavior, the runtime fails with a clear schema validation error.

2. Validate stream entry points only.

   Schema hooks run while applying streamed chunks. They do not run when callers call `setMessages` or provide initial history. This keeps history restoration and optimistic application state under caller control while still protecting incoming stream data.

3. Validate merged metadata.

   Metadata chunks use the existing merge behavior first. The merged value is then validated, and the parsed result becomes `message.metadata`. This avoids rejecting partial metadata patches that are only valid after merging with current state.

4. Validate persistent data parts by name.

   `dataPartSchemas` keys are data names such as `weather`, not protocol types such as `data-weather`. Existing protocol validation still ensures `type` matches `data-${name}`. Transient data remains callback-only and is not validated by these hooks in the first version.

5. Store parsed values.

   Successful schema validation may normalize, default, or strip values. The parsed value is what the reducer stores and what persistent data callbacks receive. Unconfigured schemas preserve existing behavior and values.

6. Treat schema failure as runtime protocol failure.

   A new `AIUISchemaValidationError` will be public and distinguishable with `instanceof`. It should extend the existing protocol/runtime error family so chat status, `error`, `onError`, and `onFinish(isError = true)` keep working. The active stream is aborted after failure, and the failing chunk is not committed to message state.

## Risks / Trade-offs

- Standard Schema variants differ slightly -> Keep the adapter narrow, tested, and documented; avoid supporting asynchronous schemas in the first version.
- Parsed metadata may not be object-shaped -> Allow the schema output type to define metadata shape and document how non-object metadata interacts with merge behavior.
- Workbench dogfooding could become noisy -> Use only broad schemas and no new controls so the workbench remains focused on chat and tool behavior.
- Error lifecycle changes can surprise tests -> Preserve the existing `onFinish` call in error paths and add focused regression tests for `isError = true`.
