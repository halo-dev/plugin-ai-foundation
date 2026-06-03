## ADDED Requirements

### Requirement: Documentation Covers Tool Call Repair
Consumer documentation SHALL explain how plugin authors use tool-call repair for invalid model-produced tool input.

#### Scenario: Repair purpose is documented
- **WHEN** a plugin author reads the tools section
- **THEN** the guide SHALL explain that tool-call repair is for known tool calls whose input fails schema validation
- **AND** it SHALL distinguish repair from approval, external tool execution, and executor error handling

#### Scenario: Repair callback usage is documented
- **WHEN** a plugin author reads the repair section
- **THEN** the guide SHALL show how to configure repair logic on `GenerateTextRequest`
- **AND** it SHALL show that repair receives the invalid tool call and validation context

#### Scenario: Repaired execution persistence is documented
- **WHEN** a plugin author reads the repair section
- **THEN** the guide SHALL explain that successful repair returns response messages containing the repaired assistant tool call and matching tool result
- **AND** it SHALL show that callers should persist those response messages for later continuation

#### Scenario: Repair boundaries are documented
- **WHEN** a plugin author reads the repair section
- **THEN** the guide SHALL state that unknown tools, approval denials, executor failures, output schema failures, timeouts, and cancellation are not repaired
- **AND** it SHALL recommend keeping repair conservative and observable

#### Scenario: Streaming repair is documented
- **WHEN** a plugin author reads the streaming documentation
- **THEN** the guide SHALL explain that `fullStream()` emits repaired tool calls and tool results as tool events
- **AND** `textStream()` remains answer-text-only
