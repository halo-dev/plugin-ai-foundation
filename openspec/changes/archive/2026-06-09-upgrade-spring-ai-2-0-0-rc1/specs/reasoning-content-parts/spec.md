## ADDED Requirements

### Requirement: Reasoning support survives final OpenAiChatModel
The system SHALL preserve reasoning extraction, reasoning usage, and reasoning history round-trip for reasoning-capable OpenAI-compatible providers without subclassing Spring AI RC1 `OpenAiChatModel`.

#### Scenario: DeepSeek reasoning output is extracted
- **WHEN** a DeepSeek-compatible reasoning model returns reasoning content through RC1 response metadata or provider-native response fields
- **THEN** the generation result and stream steps SHALL expose reasoning through provider-neutral reasoning fields
- **AND** answer text SHALL remain separate from reasoning text

#### Scenario: Reasoning history is forwarded for continuation
- **WHEN** a request contains assistant reasoning history required by a reasoning-capable provider
- **THEN** the provider adapter SHALL convert the provider-neutral reasoning part into the provider-supported request field
- **AND** generic language model runtime code SHALL remain provider-neutral

#### Scenario: Tool continuation preserves reasoning
- **WHEN** a reasoning-capable model returns reasoning content and tool calls
- **AND** generation continues after tool execution
- **THEN** the next provider request SHALL include the assistant reasoning content required for continuation
- **AND** the tool call and tool result history SHALL remain intact

#### Scenario: Unsupported reasoning history is rejected
- **WHEN** a caller sends assistant reasoning history to a provider that does not support reasoning history
- **THEN** the request SHALL fail before provider invocation
- **AND** the error SHALL remain provider-neutral and safe for logs
