## ADDED Requirements

### Requirement: MiMo provider-owned Chat and Responses semantics
The MiMo provider SHALL expose distinct provider-owned Chat Completions and Responses adapters using the documented MiMo endpoint and event schemas.

#### Scenario: Invoke MiMo Responses
- **WHEN** a MiMo model selects the Responses adapter
- **THEN** the runtime SHALL send a MiMo Responses request
- **AND** SHALL normalize MiMo text, reasoning, function-call, annotation, and usage events

#### Scenario: Invoke MiMo Chat
- **WHEN** a MiMo model selects the Chat adapter
- **THEN** the runtime SHALL use MiMo's documented Chat Completions schema and constraints

### Requirement: MiMo reasoning and tool constraints
The MiMo adapter SHALL enforce documented reasoning, sampling, tool choice, and continuation constraints.

#### Scenario: Thinking mode tool continuation
- **WHEN** a MiMo thinking response contains reasoning and tool calls
- **THEN** the continuation SHALL preserve required reasoning content
- **AND** non-`auto` tool-choice values SHALL be omitted to match MiMo's documented normalization

#### Scenario: Thinking mode sampling normalization
- **WHEN** a MiMo request enables thinking and contains `temperature` or `top_p`
- **THEN** the adapter SHALL omit those fields before invocation because MiMo applies fixed sampling defaults in thinking mode
- **AND** SHALL preserve explicitly configured sampling fields when thinking is disabled

### Requirement: MiMo provider metadata
The MiMo adapter SHALL retain documented cache, web-search, reasoning-token, annotation, and provider usage details as provider-neutral fields or provider metadata.

#### Scenario: Web search annotations returned
- **WHEN** MiMo returns web-search annotations and usage
- **THEN** normalized output SHALL expose supported sources
- **AND** additional search usage SHALL be retained as provider metadata
