## ADDED Requirements

### Requirement: Documentation Covers Chat Transport Request Contract
Consumer documentation SHALL explain how plugin authors use the framework-neutral chat transport request contract.

#### Scenario: Request body shape is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide shows the default chat request fields `id`, `messages`, `trigger`, and `messageId`
- **AND** it explains that the shape aligns with AI SDK-style HTTP chat transport submissions

#### Scenario: Manual WebFlux glue is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide shows parsing a request body into the chat request model
- **AND** it shows returning `UIMessageStreamResponse` headers and body through WebFlux without a SDK-provided adapter

#### Scenario: Custom endpoint fields are documented
- **WHEN** a plugin author needs extra endpoint fields
- **THEN** the guide explains that callers can wrap the chat request model in their own endpoint DTO
- **AND** it does not present a fixed extra body or request metadata protocol

### Requirement: Documentation Covers Chat Transport Trigger Semantics
Consumer documentation SHALL distinguish normal submission, regeneration, retry, stop, and resume behavior.

#### Scenario: Submit behavior is documented
- **WHEN** a plugin author reads the chat transport documentation
- **THEN** the guide explains that submit uses the provided UI messages as conversation history

#### Scenario: Regenerate behavior is documented
- **WHEN** a plugin author reads the chat transport documentation
- **THEN** the guide explains that regenerate requires an assistant message id
- **AND** it explains that the target assistant message and later messages are excluded before model invocation

#### Scenario: Regenerate and provider retry are distinguished
- **WHEN** a plugin author reads the chat transport documentation
- **THEN** the guide explains that regenerate is a user-level new model invocation
- **AND** provider retry remains controlled by generation request settings such as `maxRetries`

#### Scenario: Stop and abort are documented as cancellation
- **WHEN** a plugin author reads the chat transport documentation
- **THEN** the guide explains that stop is not a request trigger
- **AND** it explains that HTTP or reactive cancellation can be mapped to generation cancellation support by the caller

#### Scenario: Resume remains deferred
- **WHEN** a plugin author reads the chat transport documentation
- **THEN** the guide states that resume stream requires a future reconnect contract, active stream registry, and replay or continuation strategy
