## Why

The UI Message backend API now has a complete first version across stream creation, aggregation, validation, conversion, chat handling, transport request modeling, metadata lifecycle, and cancellation. Before more features are added, the Java API needs a stabilization pass so consumer plugins get a coherent public surface, complete JavaDoc, and a clearer caller-facing guide.

This backend-only change stabilizes the Halo-owned Java UI Message API by comparing it with current AI SDK UI concepts, auditing the current architecture, allowing necessary API or architecture cleanup, and improving both JavaDoc and consumer documentation.

## What Changes

- Compare current AI SDK UI documentation and implementation concepts before changing code.
- Audit the current `run.halo.aifoundation.ui` public API surface, especially aggregation, conversion, validation, chat handling, metadata, data, cancellation, and transport boundaries.
- Stabilize naming, visibility, generic metadata ergonomics, option boundaries, and error/finish/cancellation semantics where the audit finds issues.
- Allow necessary aggregation/conversion/validation/chat-handler architecture adjustments if the audit shows the current design is not stable enough.
- Complete English JavaDoc for public UI Message API types, public methods, and public record components/attributes.
- Add short JavaDoc examples for primary entry points.
- Rewrite and reorder `dev/ui-message-stream.md` as a caller-oriented Chinese integration guide.
- Reduce mixed Chinese/English terminology in consumer documentation and prefer clear Chinese terms.
- Keep JavaDoc and the consumer guide aligned without adding a generated documentation coverage test.

## Non-goals

- Do not add a frontend npm helper.
- Do not add a WebFlux, Servlet, or Halo runtime adapter.
- Do not add stop endpoints, active stream registry, resume, reconnect, replay, or stream id behavior.
- Do not add provider-aware reasoning preservation as a new capability.
- Do not add new UI Message protocol chunk types unless an architecture audit proves an existing type is incorrect.
- Do not add a JavaDoc coverage test or source-scanning test.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui-message-stream`: Stabilize the backend Java UI Message API contract, including public API documentation, architecture boundaries, and allowed API polish.
- `consumer-sdk-documentation`: Rework the UI Message guide into a clearer caller-facing integration guide with consistent Chinese terminology and reduced long-form explanation.

## Impact

- Public API module:
  - `api/src/main/java/run/halo/aifoundation/ui/**`
  - Potential focused API polish or architecture cleanup under the same package.
- Tests for any API or behavior changes made during stabilization.
- JavaDoc across public UI Message API types, methods, and record components.
- `dev/ui-message-stream.md` documentation structure and wording.
- OpenSpec specs for `ui-message-stream` and `consumer-sdk-documentation`.
