## ADDED Requirements

### Requirement: Dynamic UI message part names
The SDK SHALL support dynamic `data-*` and `tool-*` UI message part discriminators with strict Halo protocol validation.

#### Scenario: Dynamic data and tool names allow dashed identifiers
- **WHEN** the SDK validates a dynamic data part name or tool name
- **THEN** the name MUST match `^[A-Za-z][A-Za-z0-9_-]*$`

#### Scenario: Data type matches name
- **WHEN** a data part or chunk has `name = "weather"`
- **THEN** its `type` MUST be `data-weather`
- **AND** mismatched type and name values MUST fail validation

#### Scenario: Tool type matches toolName
- **WHEN** a tool part or chunk has `toolName = "getWeather"`
- **THEN** its `type` MUST be `tool-getWeather`
- **AND** mismatched type and toolName values MUST fail validation

### Requirement: Dynamic data part lifecycle
The SDK SHALL model custom UI data as dynamic `data-*` parts with stable identity and transient event behavior.

#### Scenario: Data id is required
- **WHEN** a data part or chunk is validated
- **THEN** it MUST include a non-blank `id`

#### Scenario: Persistent data updates by type and id
- **WHEN** the reader receives multiple non-transient data chunks with the same `type` and `id`
- **THEN** the response message SHALL contain one data part updated to the latest data value

#### Scenario: Transient data is callback-only state
- **WHEN** the reader receives a transient data chunk
- **THEN** the reader SHALL NOT add or update any `UIMessage.parts` entry for that chunk
- **AND** the transient chunk SHALL remain available to stream consumers as a stream event

#### Scenario: Transient data does not patch persisted data
- **WHEN** a transient data chunk has the same `type` and `id` as an existing persisted data part
- **THEN** the persisted data part SHALL remain unchanged

### Requirement: Dynamic tool part lifecycle
The SDK SHALL model each tool call as one dynamic `tool-*` part whose state represents the current lifecycle.

#### Scenario: Tool input streams into one part
- **WHEN** the stream emits tool input chunks for a tool call id
- **THEN** the reader SHALL create or update one matching `tool-*` part with state `input-streaming`

#### Scenario: Tool input availability is persisted
- **WHEN** the stream emits a complete tool input for a tool call id
- **THEN** the matching `tool-*` part SHALL have state `input-available`
- **AND** the part SHALL expose the parsed input

#### Scenario: Tool approval waits on the same part
- **WHEN** the stream emits a tool approval request for a tool call id
- **THEN** the matching `tool-*` part SHALL have state `approval-requested`
- **AND** the part SHALL expose approval id and input needed by the UI

#### Scenario: Tool output completes the same part
- **WHEN** a caller supplies a tool output for a tool call id
- **THEN** the matching `tool-*` part SHALL have state `output-available`
- **AND** the part SHALL expose the output

#### Scenario: Tool error completes the same part
- **WHEN** a caller supplies a tool error or rejects a tool approval
- **THEN** the matching `tool-*` part SHALL have state `output-error`
- **AND** the part SHALL expose safe error text

## MODIFIED Requirements

### Requirement: UI message part model
The SDK SHALL provide a typed Java part model for accumulated UI message state.

#### Scenario: Parts expose type discriminator for serialization
- **WHEN** a caller serializes a UI message containing parts with the default project JSON mapper
- **THEN** each part includes a stable `type` discriminator

#### Scenario: Parts represent accumulated state
- **WHEN** text, reasoning, dynamic data, or dynamic tool chunks are aggregated
- **THEN** the resulting part contains accumulated state rather than per-delta parts

#### Scenario: Data part provides typed access
- **WHEN** a caller reads a dynamic data part
- **THEN** the caller can read its `type`, `name`, `id`, data payload, and transient marker
- **AND** the caller can cast the data through a typed accessor

#### Scenario: Tool part provides lifecycle access
- **WHEN** a caller reads a dynamic tool part
- **THEN** the caller can read its `type`, `toolName`, `toolCallId`, lifecycle state, input, output, error text, approval details, and provider metadata

### Requirement: Chunk aggregation rules
The SDK SHALL aggregate UI chunks into message parts using stable content rules.

#### Scenario: Text and reasoning accumulate by block id
- **WHEN** text or reasoning deltas arrive for a block id
- **THEN** the reader appends each delta to the matching part for that block id

#### Scenario: Stable source and file parts replace by id
- **WHEN** source or file chunks repeat with the same stable id
- **THEN** the reader replaces the previous matching part instead of appending duplicates

#### Scenario: Data parts replace by type and id
- **WHEN** non-transient dynamic data chunks repeat with the same `type` and `id`
- **THEN** the reader replaces the previous matching data part instead of appending duplicates

#### Scenario: Transient data is not persisted
- **WHEN** the stream emits a dynamic data chunk marked transient
- **THEN** the reader does not add or update that data in `UIMessage.parts`

#### Scenario: Tool lifecycle replaces by toolCallId
- **WHEN** dynamic tool chunks repeat for the same `toolCallId`
- **THEN** the reader updates one matching tool part instead of appending separate call, result, error, or approval response parts

### Requirement: UI message validation extension hooks
The SDK SHALL let callers validate application-specific metadata, data parts, and tool parts.

#### Scenario: Metadata validator receives typed metadata
- **WHEN** a caller registers a metadata validator for `UIMessage<M>`
- **THEN** the validator receives metadata as type `M`

#### Scenario: Data validator can be registered by name
- **WHEN** a caller registers a data validator for a specific dynamic data part name
- **THEN** that validator runs only for matching `data-*` parts

#### Scenario: Tool validator can be registered by tool name
- **WHEN** a caller registers a tool validator for a specific dynamic tool name
- **THEN** that validator runs only for matching `tool-*` parts

#### Scenario: Unknown payloads pass base validation
- **WHEN** no caller payload validator is registered for a valid dynamic data or tool part
- **THEN** validation SHALL still allow the part after base protocol validation succeeds

### Requirement: Base UI message structural validation
The SDK SHALL validate the provider-neutral structure required for safe reuse of UI messages.

#### Scenario: Message identity and role are required
- **WHEN** a UI message is missing id or role
- **THEN** validation reports an issue for that message

#### Scenario: Dynamic part identity is validated
- **WHEN** a dynamic data or tool part is missing required identity fields
- **THEN** validation reports an issue for that part

#### Scenario: Dynamic name consistency is validated
- **WHEN** a dynamic data or tool part has a `type` that does not match its `name` or `toolName`
- **THEN** validation reports an issue for that part

#### Scenario: Tool state fields are validated
- **WHEN** a dynamic tool part has state `output-error`
- **THEN** validation requires safe error text

#### Scenario: Terminal tool output is unique
- **WHEN** a message history contains more than one conflicting terminal state for the same `toolCallId`
- **THEN** validation reports a tool lifecycle issue

#### Scenario: Pending tool state is allowed
- **WHEN** a message history contains a dynamic tool part in `input-available` or `approval-requested`
- **THEN** validation allows the history
- **AND** the caller decides when to continue generation

### Requirement: UI message to model message conversion
The SDK SHALL convert validated UI messages into provider-neutral model messages.

#### Scenario: Conversation roles convert
- **WHEN** UI messages contain `SYSTEM`, `USER`, and `ASSISTANT` roles
- **THEN** conversion maps them to the corresponding model message roles

#### Scenario: Text parts convert to model text content
- **WHEN** a UI message contains text parts
- **THEN** conversion includes the accumulated text as model message content

#### Scenario: Tool output states convert when supported
- **WHEN** UI messages contain structurally valid dynamic tool parts with state `output-available` or `output-error`
- **THEN** conversion maps them to provider-neutral model tool content where the public model message model supports it

#### Scenario: Pending tool states do not convert as output
- **WHEN** UI messages contain dynamic tool parts with state `input-streaming`, `input-available`, or `approval-requested`
- **THEN** conversion does not synthesize a tool result for those pending states

#### Scenario: Empty converted messages are skipped by default
- **WHEN** a UI message has no parts that convert to model content
- **THEN** conversion skips the message by default
- **AND** the conversion result records a `message.empty-after-conversion` warning

### Requirement: UI-only part conversion policy
The SDK SHALL treat UI-only parts conservatively during conversion.

#### Scenario: Data parts are skipped without a converter
- **WHEN** conversion sees a dynamic data part without a registered converter for its name
- **THEN** conversion does not include that part in model content by default
- **AND** the conversion result records a warning when warnings are enabled

#### Scenario: Source and file parts are skipped by default
- **WHEN** conversion sees a `SourceUrlPart` or `FilePart`
- **THEN** conversion does not fetch, read, or convert that part by default
- **AND** the conversion result records a warning when warnings are enabled

#### Scenario: Pending tool states are skipped by default
- **WHEN** conversion sees a dynamic tool part that is not in a terminal output state
- **THEN** conversion does not convert it into a tool result
- **AND** the conversion result records a warning when warnings are enabled

#### Scenario: Unsupported part policy can fail
- **WHEN** a caller configures unsupported part policy as fail
- **THEN** conversion fails instead of silently skipping unsupported parts

### Requirement: UI message chat transport deferred boundaries
The SDK SHALL keep stop endpoints, resume/reconnect, and HTTP framework adapters outside the chat transport request contract.

#### Scenario: Stop is not a chat trigger
- **WHEN** the SDK exposes chat transport triggers
- **THEN** it does not expose a stop trigger
- **AND** stop remains a transport cancellation concern

#### Scenario: Resume is not a send trigger
- **WHEN** the SDK exposes chat transport triggers
- **THEN** it does not expose a resume trigger
- **AND** resume remains future work for a separate reconnect or replay contract

#### Scenario: WebFlux adapter is not required
- **WHEN** a caller uses the request contract and chat handler from the API module
- **THEN** the caller can parse HTTP JSON and write the `UIMessageStreamResponse` with their own web framework code

### Requirement: UI Message Tool Continuation Validation
The SDK SHALL validate persisted dynamic tool lifecycle state before converting UI messages to model messages.

#### Scenario: Tool output references prior tool input
- **WHEN** a UI message contains a `tool-*` part in state `output-available`
- **THEN** validation requires the same tool part identity to contain or reference input for that tool call

#### Scenario: Tool error references prior tool input
- **WHEN** a UI message contains a `tool-*` part in state `output-error`
- **THEN** validation requires the same tool part identity to contain or reference input for that tool call

#### Scenario: Terminal tool output is unique
- **WHEN** a UI message history contains multiple conflicting terminal states for the same `toolCallId`
- **THEN** validation fails

#### Scenario: Approval denial is terminal error
- **WHEN** a user denies a pending tool approval
- **THEN** the persisted tool part state SHALL be `output-error`
- **AND** validation SHALL treat it as a completed tool lifecycle

#### Scenario: Pending tool state is allowed
- **WHEN** a UI message history contains a pending dynamic tool part without output
- **THEN** validation allows the history
- **AND** the caller decides when to continue generation

### Requirement: UI Message Tool Boundary Conversion
The SDK SHALL convert persisted assistant UI messages to provider-neutral model messages while preserving dynamic tool boundaries.

#### Scenario: Tool output splits assistant segments
- **WHEN** an assistant UI message contains assistant parts, dynamic tool output parts, and later assistant parts
- **THEN** conversion emits assistant model content before the tool output
- **AND** emits tool model content for the dynamic tool output
- **AND** emits later assistant model content after the tool output

#### Scenario: Consecutive tool outputs share tool message
- **WHEN** multiple consecutive dynamic tool parts are in `output-available` or `output-error` state
- **THEN** conversion MAY emit them in one `ModelMessage.tool(...)`
