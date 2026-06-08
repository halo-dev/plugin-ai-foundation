## ADDED Requirements

### Requirement: UI message chat handler
The SDK SHALL provide a framework-neutral UI message chat handler that composes validation, conversion, model streaming, UI stream response creation, and finish aggregation.

#### Scenario: Handler returns a full chat result
- **WHEN** a caller starts a UI message chat stream with a language model and UI messages
- **THEN** the handler returns a result exposing the UI message stream, UI message stream response, validation result, conversion result, and finish signal

#### Scenario: Handler validates UI messages
- **WHEN** the handler receives UI messages
- **THEN** it validates them with `UIMessageValidators`
- **AND** invalid messages fail fast with `InvalidUIMessageException`

#### Scenario: Handler converts UI messages
- **WHEN** validation succeeds
- **THEN** the handler converts UI messages with `UIMessageConverters`
- **AND** conversion warnings remain exposed on the chat result without blocking the model call by default

#### Scenario: Empty converted model messages fail
- **WHEN** UI messages produce no model messages after conversion
- **THEN** the handler fails before calling the language model

### Requirement: UI message chat handler request construction
The SDK SHALL let callers configure generation settings while preserving handler ownership of prompt messages.

#### Scenario: Request customizer configures non-input fields
- **WHEN** a caller configures system instructions, tools, provider options, output, lifecycle, cancellation, timeout, or sampling fields through the request customizer
- **THEN** those fields are applied to the generated `GenerateTextRequest`
- **AND** converted model messages are used as the request messages

#### Scenario: Request customizer must not set prompt
- **WHEN** a request customizer sets `prompt`
- **THEN** the handler rejects the request before model invocation

#### Scenario: Request customizer must not set messages
- **WHEN** a request customizer sets `messages`
- **THEN** the handler rejects the request before model invocation

#### Scenario: System can be supplied through request or UI messages
- **WHEN** converted UI messages contain a system message and the request customizer also sets top-level system text
- **THEN** the handler does not merge or reject either source
- **AND** the generated request preserves the existing provider-neutral request semantics

### Requirement: UI message chat handler options
The SDK SHALL expose the existing UI message stream, conversion, validation, and error handling options through the chat handler.

#### Scenario: Handler accepts resolved language model
- **WHEN** a caller configures the handler
- **THEN** a `LanguageModel` is required
- **AND** the handler does not resolve models through `AiModelService`

#### Scenario: Handler accepts original UI messages
- **WHEN** a caller configures UI messages
- **THEN** those messages are used for validation, conversion, and original message finish aggregation

#### Scenario: Handler supports optional continuation message
- **WHEN** a caller supplies an existing assistant `UIMessage`
- **THEN** finish aggregation can continue that message using existing continuation semantics

#### Scenario: Handler supports static metadata and message id generation
- **WHEN** a caller configures metadata supplier or message id generator
- **THEN** the handler passes those options to UI stream creation

#### Scenario: Handler supports validation and conversion customizers
- **WHEN** a caller configures validation or conversion options
- **THEN** the handler uses those options before model invocation

#### Scenario: Handler supports serializer
- **WHEN** a caller supplies a UI chunk serializer
- **THEN** the response descriptor exposes serializer-backed SSE body frames

#### Scenario: Handler supports stream error handling
- **WHEN** a caller configures safe error text, read error callback, or terminate-on-error
- **THEN** those options are applied to UI stream creation and finish aggregation

### Requirement: UI message chat finish handling
The SDK SHALL expose finish aggregation without duplicating model stream execution.

#### Scenario: Handler exposes finish mono
- **WHEN** the UI message chat stream completes
- **THEN** the chat result finish signal emits the `UIMessageStreamFinish`

#### Scenario: Finish contains updated messages
- **WHEN** original UI messages are configured on the handler
- **THEN** the finish result contains original messages with the response message appended or continued

#### Scenario: Caller onFinish can persist messages
- **WHEN** a caller configures an on-finish callback
- **THEN** the callback receives the same finish result exposed by the chat result finish signal

#### Scenario: onFinish failure is observable
- **WHEN** the caller on-finish callback throws
- **THEN** the chat result finish signal fails with that error

#### Scenario: Finish depends on stream consumption
- **WHEN** the UI stream is not consumed
- **THEN** the handler does not force model execution only to complete finish

### Requirement: UI message chat handler boundaries
The SDK SHALL keep transport, persistence, and provider-specific behavior outside the first chat handler.

#### Scenario: Handler does not parse HTTP bodies
- **WHEN** a caller uses the chat handler
- **THEN** the caller must already provide Java UI message values

#### Scenario: Handler does not persist messages automatically
- **WHEN** the chat stream finishes
- **THEN** the SDK exposes finish data but does not write to caller storage

#### Scenario: Handler does not expose underlying stream result
- **WHEN** a caller receives a UI message chat result
- **THEN** the result does not expose the underlying `StreamTextResult`

#### Scenario: Handler remains reactive
- **WHEN** a caller uses the chat handler
- **THEN** the handler exposes reactive stream views and does not provide a blocking helper
