## ADDED Requirements

### Requirement: Reasoning content parts
The system SHALL provide model-independent reasoning content parts for assistant-generated reasoning.

#### Scenario: Reasoning part contains text
- **WHEN** a provider returns visible reasoning text
- **THEN** the generated content SHALL include a reasoning part containing that text
- **AND** the reasoning part type SHALL be distinct from text, tool call, tool result, and tool error parts

#### Scenario: Reasoning part preserves provider metadata
- **WHEN** a provider exposes metadata required to continue a reasoning-capable conversation
- **THEN** the reasoning part SHALL preserve that metadata under provider-specific metadata keys
- **AND** the public API SHALL NOT require callers to use provider-native message classes

#### Scenario: No reasoning returned
- **WHEN** a provider returns answer text without reasoning content
- **THEN** the generated content SHALL omit reasoning parts
- **AND** reasoning aggregate fields SHALL be empty or null according to their documented type

### Requirement: Reasoning result aggregation
The system SHALL expose reasoning output on final results and individual generation steps.

#### Scenario: Non-streaming result includes reasoning
- **WHEN** `generateText` receives reasoning content from the final provider step
- **THEN** `GenerateTextResult.reasoning` SHALL contain the reasoning parts from the final step
- **AND** `GenerateTextResult.reasoningText` SHALL contain the concatenated visible reasoning text when available

#### Scenario: Step includes reasoning
- **WHEN** a multi-step generation receives reasoning content in an intermediate provider step
- **THEN** the corresponding `GenerationStep.reasoning` SHALL contain that step's reasoning parts
- **AND** `GenerationStep.reasoningText` SHALL contain that step's concatenated visible reasoning text when available

#### Scenario: Reasoning tokens included in usage
- **WHEN** a provider reports reasoning token usage
- **THEN** the usage object SHALL expose reasoning token counts without replacing input, output, or total token counts

### Requirement: Reasoning round-trip
The system SHALL preserve reasoning content needed for follow-up provider requests.

#### Scenario: Tool continuation includes assistant reasoning
- **WHEN** a provider step returns reasoning content and tool calls
- **AND** the system continues generation with tool results
- **THEN** the next provider request SHALL include the assistant reasoning content in the provider-specific format required by that provider
- **AND** the system SHALL still expose the reasoning through Halo-owned public part types

#### Scenario: Caller-provided reasoning history
- **WHEN** a caller provides message history containing assistant reasoning parts with provider metadata
- **THEN** the implementation SHALL convert those parts to the provider-specific request representation when supported
- **AND** unsupported reasoning history SHALL be rejected before invoking the provider with an error identifying the unsupported part type

#### Scenario: DeepSeek thinking mode continuation
- **WHEN** a DeepSeek/OpenAI-compatible provider requires `reasoning_content` to be passed back after a thinking-mode tool call
- **THEN** the provider adapter SHALL preserve and pass back the required reasoning content on the follow-up request
- **AND** the request SHALL NOT fail solely because reasoning content was omitted by the framework layer

