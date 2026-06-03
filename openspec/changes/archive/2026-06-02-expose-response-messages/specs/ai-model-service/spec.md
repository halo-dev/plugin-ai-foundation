## ADDED Requirements

### Requirement: Text Generation Results Expose Response Messages
Text generation results SHALL expose provider-neutral response messages that callers can append to later `GenerateTextRequest.messages` history.

#### Scenario: Text-only generation returns assistant message
- **WHEN** `LanguageModel.generateText(request)` completes with generated text and no tool calls
- **THEN** `GenerateTextResult` SHALL include a response message with role `ASSISTANT`
- **AND** the response message SHALL contain the generated answer text as a `text` content part

#### Scenario: Tool call generation returns appendable history
- **WHEN** `LanguageModel.generateText(request)` completes a tool-enabled multi-step generation
- **THEN** `GenerateTextResult` SHALL include response messages ordered as assistant tool-call history, tool result or tool error history, and later assistant answer history
- **AND** callers SHALL be able to append those response messages to the original request messages before a later model call

#### Scenario: Prompt request returns only generated response messages
- **WHEN** a caller sends `GenerateTextRequest.prompt`
- **THEN** `GenerateTextResult.responseMessages` SHALL contain only messages produced by the generation call
- **AND** it SHALL NOT synthesize a user message for the original prompt

### Requirement: Generation Steps Expose Step Response Messages
Each generation step SHALL expose the provider-neutral response messages produced during that step.

#### Scenario: Step contains assistant output
- **WHEN** a generation step produces text, reasoning, tool calls, or approval requests
- **THEN** the corresponding `GenerationStep` SHALL include an assistant response message containing those content parts

#### Scenario: Step contains server-side tool history
- **WHEN** a generation step executes, denies, or fails a server-side tool call
- **THEN** the corresponding `GenerationStep` SHALL include a tool response message containing the tool result or tool error parts

#### Scenario: Top-level messages preserve step order
- **WHEN** `GenerateTextResult` contains multiple generation steps
- **THEN** the top-level response messages SHALL preserve the same order as the step response messages concatenated by step index
