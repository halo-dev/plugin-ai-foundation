## ADDED Requirements

### Requirement: UI message chat cancellation option
The SDK SHALL let callers provide a cancellation token to framework-neutral UI message chat handling.

#### Scenario: Chat handler injects cancellation token
- **WHEN** a caller configures a cancellation token on `UIMessageChatOptions`
- **THEN** the chat handler sets that token on the generated `GenerateTextRequest`

#### Scenario: Missing cancellation token keeps existing behavior
- **WHEN** a caller does not configure a cancellation token on `UIMessageChatOptions`
- **THEN** the chat handler does not create or inject a cancellation token automatically

#### Scenario: Request customizer cannot override cancellation token
- **WHEN** a UI message chat request customizer sets `cancellationToken`
- **THEN** the chat handler rejects the request before model invocation

### Requirement: UI message cancellation helper
The SDK SHALL provide a framework-neutral helper for caller-owned UI message cancellation.

#### Scenario: Caller creates cancellation helper
- **WHEN** a caller creates a UI message cancellation helper
- **THEN** the helper exposes a cancellation token
- **AND** the token is not cancelled initially

#### Scenario: Caller cancels helper
- **WHEN** a caller cancels the helper
- **THEN** the helper token reports cancellation requested

#### Scenario: Helper does not manage subscriptions
- **WHEN** a caller cancels the helper
- **THEN** the helper does not own or dispose Reactor subscriptions

### Requirement: UI message cancellation Reactor binding
The SDK SHALL provide Reactor binding helpers that cancel the helper only when a subscriber cancels.

#### Scenario: Flux subscriber cancel triggers helper cancellation
- **WHEN** a Flux wrapped by the cancellation helper receives a subscriber cancel signal
- **THEN** the helper is cancelled

#### Scenario: Mono subscriber cancel triggers helper cancellation
- **WHEN** a Mono wrapped by the cancellation helper receives a subscriber cancel signal
- **THEN** the helper is cancelled

#### Scenario: Normal completion does not trigger cancellation
- **WHEN** a wrapped Reactor publisher completes normally
- **THEN** the helper is not cancelled by completion

#### Scenario: Publisher error does not trigger helper cancellation
- **WHEN** a wrapped Reactor publisher fails with an error
- **THEN** the helper is not cancelled by the error signal

### Requirement: UI stream cancellation abort mapping
The SDK SHALL map recognized generation cancellation to an abort UI message chunk.

#### Scenario: Cancellation exception becomes abort
- **WHEN** a UI message stream fails with a recognized generation cancellation exception
- **THEN** the stream emits an `abort` chunk instead of an `error` chunk
- **AND** the stream completes normally after the abort chunk

#### Scenario: Cancelled token failure becomes abort
- **WHEN** a UI message stream fails while the configured cancellation token reports cancellation requested
- **THEN** the stream emits an `abort` chunk instead of an `error` chunk

#### Scenario: Cancellation does not invoke safe error text handler
- **WHEN** a UI message stream maps cancellation to abort
- **THEN** the configured safe error text handler is not invoked for that cancellation

#### Scenario: Non-cancellation errors remain errors
- **WHEN** a UI message stream fails with an error that is not recognized as cancellation
- **THEN** existing stream error handling behavior is preserved

### Requirement: UI stream cancellation finish aggregation
The SDK SHALL preserve finish aggregation when a UI message stream is aborted by cancellation.

#### Scenario: Abort finish exposes partial response message
- **WHEN** a UI message stream emits content and then aborts due to cancellation
- **THEN** finish aggregation exposes the partial response message
- **AND** the finish result marks `aborted` true

#### Scenario: Abort finish invokes onFinish
- **WHEN** a UI message chat stream aborts due to cancellation
- **THEN** the configured `onFinish` callback is invoked with the aborted finish result

#### Scenario: Abort finish has no error text
- **WHEN** a UI message stream maps expected cancellation to abort
- **THEN** the finish result does not expose cancellation as error text

### Requirement: UI stream terminal chunk invariant
The SDK SHALL ensure SDK-created UI message streams emit at most one terminal chunk.

#### Scenario: Finish wins before later cancellation
- **WHEN** a SDK-created UI message stream has already emitted `finish`
- **THEN** later cancellation does not emit an additional `abort` chunk

#### Scenario: Abort wins before later finish
- **WHEN** a SDK-created UI message stream has already emitted `abort`
- **THEN** later finish handling does not emit an additional `finish` chunk

#### Scenario: Error wins before later cancellation
- **WHEN** a SDK-created UI message stream has already emitted `error`
- **THEN** later cancellation does not emit an additional `abort` chunk
