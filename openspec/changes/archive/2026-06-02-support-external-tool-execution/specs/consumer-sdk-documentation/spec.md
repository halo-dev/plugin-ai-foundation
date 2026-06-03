## ADDED Requirements

### Requirement: Documentation Covers External Tool Execution
Consumer documentation SHALL explain how plugin authors use no-executor tools for external execution.

#### Scenario: No-executor tool purpose is documented
- **WHEN** a plugin author reads the tools section
- **THEN** the guide SHALL explain that omitting a server-side executor is useful for client-side tools, queued jobs, or tools owned by another Halo plugin
- **AND** it SHALL distinguish external execution from server-side executor tools and approval-gated executor tools

#### Scenario: External tool call persistence is documented
- **WHEN** a plugin author reads the external tool section
- **THEN** the guide SHALL show saving returned response messages that contain assistant tool-call parts
- **AND** it SHALL explain that the SDK does not synthesize a tool result for no-executor tools

#### Scenario: External result continuation is documented
- **WHEN** a plugin author reads the external tool section
- **THEN** the guide SHALL show appending a tool message with `tool-result` and calling the model again
- **AND** it SHALL show that the later response messages should also be persisted

#### Scenario: External error continuation is documented
- **WHEN** a plugin author reads the external tool section
- **THEN** the guide SHALL show appending a tool message with `tool-error`
- **AND** it SHALL explain that the model can respond to the externally reported failure

#### Scenario: Streaming external tools are documented
- **WHEN** a plugin author reads the streaming documentation
- **THEN** the guide SHALL explain that `fullStream()` emits pending external tool calls
- **AND** `textStream()` remains answer-text-only
- **AND** continuation still requires a later request with appended tool result or tool error messages
