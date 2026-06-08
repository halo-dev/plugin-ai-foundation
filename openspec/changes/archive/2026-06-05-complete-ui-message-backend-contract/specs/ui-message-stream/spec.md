## ADDED Requirements

### Requirement: UI Message Tool Approval Response Part
The SDK SHALL provide a persisted UI Message part for caller approval or denial of a pending tool approval request.

#### Scenario: Approval response is a persisted assistant part
- **WHEN** a caller stores a UI message after approving or denying a tool approval request
- **THEN** the approval response is represented as a `tool-approval-response` UI message part
- **AND** the UI message role remains `ASSISTANT`

#### Scenario: Approval response required fields
- **WHEN** an approval response part is validated
- **THEN** it MUST include `approvalId`
- **AND** it MUST include `approved`
- **AND** `toolCallId`, `toolName`, `reason`, and `providerMetadata` MAY be preserved when present

#### Scenario: Duplicate approval response is invalid
- **WHEN** a UI message history contains more than one approval response for the same `approvalId`
- **THEN** validation fails with a duplicate approval response issue

#### Scenario: Denied approval does not synthesize tool error
- **WHEN** a caller denies a tool approval request
- **THEN** the SDK stores only the approval response state
- **AND** it SHALL NOT synthesize a `tool-error` part for the denial

### Requirement: UI Message Tool Continuation Validation
The SDK SHALL validate persisted tool continuation state before converting UI messages to model messages.

#### Scenario: Tool result references prior tool call
- **WHEN** a UI message contains a `tool-result` part
- **THEN** validation requires a prior matching `tool-call` by `toolCallId`

#### Scenario: Tool error references prior tool call
- **WHEN** a UI message contains a `tool-error` part
- **THEN** validation requires a prior matching `tool-call` by `toolCallId`

#### Scenario: Approval response references prior approval request
- **WHEN** a UI message contains a `tool-approval-response` part
- **THEN** validation requires a prior matching `tool-approval-request` by `approvalId`

#### Scenario: Terminal tool output is unique
- **WHEN** a UI message history contains multiple final `tool-result` or `tool-error` parts for the same `toolCallId`
- **THEN** validation fails

#### Scenario: Denied approval forbids tool output
- **WHEN** a UI message history contains an approval response with `approved = false`
- **AND** a later `tool-result` or `tool-error` appears for the same approved tool call
- **THEN** validation fails

#### Scenario: Pending tool state is allowed
- **WHEN** a UI message history contains a pending `tool-call` or `tool-approval-request` without a final response
- **THEN** validation allows the history
- **AND** the caller decides when to continue generation

### Requirement: UI Message Tool Boundary Conversion
The SDK SHALL convert persisted assistant UI messages to provider-neutral model messages while preserving tool boundaries.

#### Scenario: Tool response splits assistant segments
- **WHEN** an assistant UI message contains assistant parts, tool response parts, and later assistant parts
- **THEN** conversion emits assistant model content before the tool response
- **AND** emits tool model content for the tool response
- **AND** emits later assistant model content after the tool response

#### Scenario: Consecutive tool responses share tool message
- **WHEN** multiple consecutive `tool-result`, `tool-error`, or `tool-approval-response` parts appear
- **THEN** conversion MAY emit them in one `ModelMessage.tool(...)`
- **AND** their order is preserved

#### Scenario: Approval response converts to tool message part
- **WHEN** an approval response part is converted
- **THEN** it becomes a `ModelMessagePart` with type `tool-approval-response`
- **AND** it is emitted in a `TOOL` model message

#### Scenario: Tool result and approval response can share tool message
- **WHEN** an approved tool flow contains `tool-approval-response` followed by `tool-result`
- **THEN** conversion MAY emit both parts in the same `TOOL` model message
- **AND** the approval response precedes the tool result

### Requirement: UI Message Reasoning Continuation
The SDK SHALL resolve UI reasoning continuation automatically when streaming from UI messages.

#### Scenario: Supported model preserves reasoning
- **WHEN** a UI message contains a reasoning part with text or provider metadata
- **AND** `UIMessageChatHandlers` streams through a language model whose capabilities support reasoning history
- **THEN** conversion emits a `ModelMessagePart` with type `reasoning`
- **AND** visible reasoning text and provider metadata are preserved

#### Scenario: Unsupported model drops reasoning
- **WHEN** a UI message contains a reasoning part
- **AND** `UIMessageChatHandlers` streams through a language model whose capabilities do not support reasoning history
- **THEN** conversion drops the reasoning part
- **AND** records a conversion warning

#### Scenario: Direct converter keeps provider state
- **WHEN** a caller directly converts UI messages with `UIMessageConverters`
- **AND** reasoning conversion is left as `AUTO`
- **THEN** `AUTO` behaves as `PRESERVE_PROVIDER_STATE`

#### Scenario: Provider support remains model-owned
- **WHEN** UI Message conversion needs to decide whether reasoning history can be preserved
- **THEN** it uses the selected `LanguageModel` capabilities
- **AND** callers do not query provider resources to make this decision

#### Scenario: Empty reasoning skipped when preserving
- **WHEN** a reasoning part has no text and no provider metadata
- **AND** conversion is preserving reasoning
- **THEN** conversion skips it
- **AND** records a conversion warning

#### Scenario: Strict reasoning conversion fails on empty reasoning
- **WHEN** strict reasoning conversion is configured
- **AND** a reasoning part has no text and no provider metadata
- **THEN** conversion fails

#### Scenario: Reasoning follows tool boundary order
- **WHEN** reasoning parts appear before or after tool responses in a UI message
- **THEN** conversion keeps reasoning in the assistant model segment that corresponds to its part order

### Requirement: UI Message Transport Codec
The SDK SHALL provide a framework-neutral Map-based transport codec for UI Message HTTP boundaries.

#### Scenario: Decode part from map
- **WHEN** a caller provides a map containing a supported UI message part `type`
- **THEN** the codec returns the corresponding typed `UIMessagePart`

#### Scenario: Decode message from map
- **WHEN** a caller provides a map containing `id`, `role`, `parts`, and optional `metadata`
- **THEN** the codec returns a typed `UIMessage<Map<String, Object>>`

#### Scenario: Decode chat request from map
- **WHEN** a caller provides a map containing UI messages, trigger, and optional message id
- **THEN** the codec returns a `UIMessageChatRequest<Map<String, Object>>`

#### Scenario: Typed metadata mapper
- **WHEN** a caller supplies a metadata mapper
- **THEN** the codec uses the mapper to create typed UI message metadata

#### Scenario: Encode transport maps
- **WHEN** a caller encodes a part, message, or chat request
- **THEN** the codec returns framework-neutral maps
- **AND** optional null fields are omitted from encoded maps

#### Scenario: Unknown transport type fails
- **WHEN** a transport map contains an unknown UI message part type
- **THEN** the codec fails with `InvalidUIMessageException`

#### Scenario: Transport codec does not parse JSON
- **WHEN** a caller receives JSON over HTTP
- **THEN** the caller remains responsible for JSON parsing
- **AND** the SDK codec only converts map/list structures into typed UI Message values

### Requirement: Console Workbench UI Message Tool Continuation
The console model test workbench SHALL dogfood UI Message tool continuation through the public backend contract.

#### Scenario: Console endpoint uses public transport codec
- **WHEN** the console UI Message endpoint receives a chat request
- **THEN** it converts transport request data through the public UI Message transport codec
- **AND** it does not maintain a separate private UI Message part decoder

#### Scenario: Approval accepted continues generation
- **WHEN** a console user approves a UI Message tool approval request
- **THEN** the workbench appends a `tool-approval-response` part to the relevant assistant UI message
- **AND** resends the UI Message history to continue generation

#### Scenario: Approval denied continues generation
- **WHEN** a console user denies a UI Message tool approval request
- **THEN** the workbench appends a denied `tool-approval-response` part
- **AND** resends the UI Message history without synthesizing a tool error

#### Scenario: External tool result continues generation
- **WHEN** a console user supplies an external tool result in UI Message mode
- **THEN** the workbench appends a `tool-result` part to the relevant assistant UI message
- **AND** resends the UI Message history to continue generation

#### Scenario: External tool error continues generation
- **WHEN** a console user supplies an external tool error in UI Message mode
- **THEN** the workbench appends a `tool-error` part to the relevant assistant UI message
- **AND** resends the UI Message history to continue generation

#### Scenario: Deferred continuation warning removed
- **WHEN** a console user handles tool continuation in UI Message mode
- **THEN** the workbench does not emit the `ui-message-tool-continuation-deferred` warning

### Requirement: UI Message Backend Contract Boundaries
The SDK SHALL keep the finalized first-version backend contract separate from deferred runtime and frontend helper work.

#### Scenario: No tool UI role
- **WHEN** UI Message tool continuation is implemented
- **THEN** `UIMessageRole` contains only system, user, and assistant roles

#### Scenario: No WebFlux adapter dependency
- **WHEN** UI Message transport and response helpers are implemented
- **THEN** the API module does not depend on Spring WebFlux

#### Scenario: Runtime features remain deferred
- **WHEN** the backend contract is completed
- **THEN** active stream registries, stop endpoints, resume, reconnect, replay, and stream id contracts remain deferred

#### Scenario: Npm helper remains deferred
- **WHEN** the backend contract is completed
- **THEN** no public npm helper package or frontend helper API is introduced
