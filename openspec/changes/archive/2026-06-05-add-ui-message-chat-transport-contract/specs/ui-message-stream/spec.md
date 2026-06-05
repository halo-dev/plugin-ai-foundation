## ADDED Requirements

### Requirement: UI message chat transport request
The SDK SHALL provide a framework-neutral Java request model for AI SDK-style chat transport submissions.

#### Scenario: Request contains default chat transport fields
- **WHEN** a caller receives a chat submission from an HTTP transport
- **THEN** the request model exposes chat id, UI messages, trigger, and optional message id

#### Scenario: Request remains framework neutral
- **WHEN** a caller uses the request model from the published API module
- **THEN** the request model does not require Spring WebFlux, Servlet, Jackson, or Halo runtime types

#### Scenario: Request messages are typed
- **WHEN** a caller declares a metadata type for UI messages
- **THEN** the request model exposes messages as `List<UIMessage<M>>`

### Requirement: UI message chat transport triggers
The SDK SHALL define chat transport triggers for normal submission and user-driven regeneration.

#### Scenario: Submit trigger starts from provided messages
- **WHEN** a chat request uses the submit trigger
- **THEN** the handler uses the request messages as the model conversation history

#### Scenario: Regenerate trigger requires message id
- **WHEN** a chat request uses the regenerate trigger without a message id
- **THEN** the handler rejects the request before model invocation

#### Scenario: Regenerate target must exist
- **WHEN** a chat request uses the regenerate trigger with a message id that does not match an existing message
- **THEN** the handler rejects the request before model invocation

#### Scenario: Regenerate target must be assistant
- **WHEN** a chat request uses the regenerate trigger with a message id that matches a non-assistant message
- **THEN** the handler rejects the request before model invocation

#### Scenario: Regenerate trims old response history
- **WHEN** a chat request regenerates an assistant message
- **THEN** the handler excludes that assistant message and all later messages before validation, conversion, and model invocation

#### Scenario: Regenerate is not provider retry
- **WHEN** a caller uses the regenerate trigger
- **THEN** the SDK treats it as a new model invocation from trimmed UI history
- **AND** provider retry remains controlled by generation request settings

### Requirement: UI message chat handler request overload
The SDK SHALL allow the chat handler to start from the chat transport request model.

#### Scenario: Handler accepts request model
- **WHEN** a caller starts a UI message chat stream with a language model and chat request
- **THEN** the handler applies the request trigger and returns the existing chat result projections

#### Scenario: Handler exposes validation for effective messages
- **WHEN** a chat request is accepted by the handler
- **THEN** validation runs against the effective message history after trigger processing

#### Scenario: Handler exposes conversion for effective messages
- **WHEN** a chat request is accepted by the handler
- **THEN** conversion runs against the effective message history after trigger processing

#### Scenario: Finish aggregation uses effective messages
- **WHEN** a chat request is accepted by the handler
- **THEN** finish aggregation uses the effective message history as original messages

### Requirement: UI message chat transport deferred boundaries
The SDK SHALL keep resume, stop endpoints, and HTTP framework adapters outside the first transport request contract.

#### Scenario: Stop is not a chat trigger
- **WHEN** the SDK exposes chat transport triggers
- **THEN** it does not expose a stop trigger
- **AND** stop remains a transport cancellation concern

#### Scenario: Resume is not a send trigger
- **WHEN** the SDK exposes chat transport triggers
- **THEN** it does not expose a resume trigger
- **AND** resume remains future work for a separate reconnect contract

#### Scenario: WebFlux adapter is not required
- **WHEN** a caller uses the request contract and chat handler from the API module
- **THEN** the caller can parse HTTP JSON and write the `UIMessageStreamResponse` with their own web framework code
