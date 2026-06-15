## ADDED Requirements

### Requirement: Documentation Covers Custom UIMessage Stream Reading
Consumer documentation SHALL explain how callers can read existing UIMessage streams without using the full chat runtime.

#### Scenario: Reader is documented as custom consumer path
- **WHEN** a user reads the package README or UI message stream guide
- **THEN** the documentation SHALL state that standard chat interfaces should use `Chat` or `useChat`
- **AND** it SHALL present `readUIMessageStream` for callers that already manage requests, state, or non-Vue runtime integration themselves

#### Scenario: Fetch example handles HTTP status outside reader
- **WHEN** the documentation shows `readUIMessageStream` with `fetch`
- **THEN** the example SHALL handle `response.ok` before calling the helper
- **AND** it SHALL pass the successful `Response` to `readUIMessageStream`
- **AND** it SHALL NOT imply that the helper sends requests or owns HTTP error parsing

#### Scenario: Reader callbacks are explained
- **WHEN** the documentation describes reader callbacks
- **THEN** it SHALL distinguish raw `onChunk` from accepted `onMessage`, `onData`, and `onToolCall` events
- **AND** it SHALL mention that `onToolCall` is notification-only and does not automatically submit tool output

#### Scenario: Reader limitations are documented
- **WHEN** the documentation describes reader scope
- **THEN** it SHALL state that the helper does not implement resume, reconnect, replay, text streams, object streams, or automatic tool continuation
