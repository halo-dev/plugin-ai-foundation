## ADDED Requirements

### Requirement: UI message validation
The SDK SHALL provide validation helpers for persisted `UIMessage<M>` conversations.

#### Scenario: Validate throws on invalid messages
- **WHEN** a caller validates UI messages with invalid structure or caller-defined validation issues
- **THEN** `UIMessageValidators.validate(...)` throws an `InvalidUIMessageException`
- **AND** the exception exposes the collected validation issues

#### Scenario: Safe validation returns issues
- **WHEN** a caller safely validates UI messages with invalid structure or caller-defined validation issues
- **THEN** `UIMessageValidators.safeValidate(...)` returns a result marked invalid
- **AND** the result exposes all collected validation issues without mutating the input messages

#### Scenario: Valid messages are returned unchanged
- **WHEN** validation succeeds
- **THEN** the validated result contains the original `UIMessage<M>` values
- **AND** generic metadata type `M` remains preserved

#### Scenario: Validator hook failures become issues
- **WHEN** a caller-provided metadata, data, or tool validator throws
- **THEN** safe validation records a `validator.exception` issue instead of losing previously collected issues

### Requirement: UI message validation extension hooks
The SDK SHALL let callers validate application-specific metadata, data parts, and tool parts.

#### Scenario: Metadata validator receives typed metadata
- **WHEN** a caller registers a metadata validator for `UIMessage<M>`
- **THEN** the validator receives metadata as type `M`

#### Scenario: Data validator can be registered by name
- **WHEN** a caller registers a data validator for a specific data part name
- **THEN** that validator runs only for matching `DataPart` instances

#### Scenario: Tool validator can inspect tool parts
- **WHEN** a caller registers a tool validator
- **THEN** the validator can inspect tool call, tool result, tool error, and tool approval request parts

### Requirement: Base UI message structural validation
The SDK SHALL validate the provider-neutral structure required for safe reuse of UI messages.

#### Scenario: Message identity and role are required
- **WHEN** a UI message is missing id or role
- **THEN** validation reports an issue for that message

#### Scenario: Part identity is validated
- **WHEN** a part that requires an id, name, or tool call id is missing that value
- **THEN** validation reports an issue for that part

#### Scenario: Tool results match prior tool calls
- **WHEN** a tool result or tool error references a tool call id that does not appear in prior UI message history
- **THEN** validation reports a tool pairing issue

#### Scenario: Tool call final output is unique
- **WHEN** a tool call id has multiple conflicting final results or errors
- **THEN** validation reports a tool pairing issue

#### Scenario: Approval requests remain UI state
- **WHEN** validation sees a tool approval request part
- **THEN** validation does not treat it as an executed tool result

### Requirement: UI message to model message conversion
The SDK SHALL convert validated UI messages into provider-neutral model messages.

#### Scenario: Conversation roles convert
- **WHEN** UI messages contain `SYSTEM`, `USER`, and `ASSISTANT` roles
- **THEN** conversion maps them to the corresponding model message roles

#### Scenario: Text parts convert to model text content
- **WHEN** a UI message contains text parts
- **THEN** conversion includes the accumulated text as model message content

#### Scenario: Tool call and result parts convert when supported
- **WHEN** UI messages contain structurally valid tool call, tool result, or tool error parts
- **THEN** conversion maps them to provider-neutral model tool content where the public model message model supports it

#### Scenario: Empty converted messages are skipped by default
- **WHEN** a UI message has no parts that convert to model content
- **THEN** conversion skips the message by default
- **AND** the conversion result records a `message.empty-after-conversion` warning

### Requirement: UI-only part conversion policy
The SDK SHALL treat UI-only parts conservatively during conversion.

#### Scenario: Data parts are skipped without a converter
- **WHEN** conversion sees a `DataPart` without a registered converter for its name
- **THEN** conversion does not include that part in model content by default
- **AND** the conversion result records a warning when warnings are enabled

#### Scenario: Source and file parts are skipped by default
- **WHEN** conversion sees a `SourceUrlPart` or `FilePart`
- **THEN** conversion does not fetch, read, or convert that part by default
- **AND** the conversion result records a warning when warnings are enabled

#### Scenario: Approval requests are skipped by default
- **WHEN** conversion sees a `ToolApprovalRequestPart`
- **THEN** conversion does not convert it into a tool result
- **AND** the conversion result records a warning when warnings are enabled

#### Scenario: Unsupported part policy can fail
- **WHEN** a caller configures unsupported part policy as fail
- **THEN** conversion fails instead of silently skipping unsupported parts

### Requirement: UI message conversion warnings
The SDK SHALL expose observable warnings for skipped or unsupported conversion behavior.

#### Scenario: Full conversion result exposes warnings
- **WHEN** a caller uses the full conversion API
- **THEN** the result exposes model messages and conversion warnings

#### Scenario: Warning identifies message and part
- **WHEN** conversion records a warning for a part
- **THEN** the warning identifies the message id, role, part type, part id when available, code, and safe message

#### Scenario: Ergonomic conversion returns messages only
- **WHEN** a caller uses the ergonomic conversion helper
- **THEN** the helper returns only `List<ModelMessage>`
- **AND** callers that need diagnostics can use the full conversion result API

### Requirement: UI message conversion extension hooks
The SDK SHALL let callers explicitly convert application-specific UI parts.

#### Scenario: Data converter can be registered by name
- **WHEN** a caller registers a data converter for a specific data part name
- **THEN** matching data parts can produce model content

#### Scenario: Custom part converter can handle unsupported parts
- **WHEN** a caller registers a custom part converter
- **THEN** the converter can turn otherwise unsupported UI parts into model content

#### Scenario: Metadata remains available to custom converters
- **WHEN** a custom converter runs for `UIMessage<M>`
- **THEN** the conversion context exposes typed message metadata as `M`

### Requirement: Reasoning conversion boundary
The SDK SHALL distinguish visible reasoning text from provider-specific opaque reasoning state.

#### Scenario: Reasoning text is not prompt text by default
- **WHEN** conversion sees a `ReasoningPart`
- **THEN** the converter does not append `ReasoningPart.text()` as ordinary model prompt text by default

#### Scenario: Provider state preservation is explicit
- **WHEN** reasoning conversion is configured to preserve provider state
- **THEN** default conversion preserves no provider-specific state unless a converter handles the `providerMetadata`
- **AND** conversion records a warning when preservation was requested but unsupported

#### Scenario: Caller can explicitly include reasoning text
- **WHEN** a caller configures reasoning conversion to include text as context
- **THEN** the converter may include visible reasoning text in model content according to that explicit option
