## ADDED Requirements

### Requirement: Documentation Covers Response Message Persistence
Consumer documentation SHALL explain how plugin authors persist response messages after text generation, tool execution, and approval continuation.

#### Scenario: Basic response messages are documented
- **WHEN** a plugin author reads the text generation documentation
- **THEN** the guide SHALL explain that `GenerateTextResult.responseMessages` contains model-produced messages that can be appended to stored conversation history
- **AND** it SHALL clarify that prompt-based callers must persist their own user message if they want later conversational continuation

#### Scenario: Tool loop persistence is documented
- **WHEN** a plugin author reads the tools section
- **THEN** the guide SHALL show appending response messages after a multi-step tool call
- **AND** it SHALL explain that the appended messages include assistant tool-call history and tool result or tool error history

#### Scenario: Approval continuation persistence is documented
- **WHEN** a plugin author reads the tool approval section
- **THEN** the guide SHALL show persisting the caller-supplied tool-approval-response together with the later returned response messages
- **AND** it SHALL explain that this prevents approved or denied approvals from being replayed

#### Scenario: Streaming persistence is documented
- **WHEN** a plugin author reads the streaming text documentation
- **THEN** the guide SHALL show reading response messages from `StreamTextResult.result()`
- **AND** it SHALL explain that `textStream()` remains answer-text-only
