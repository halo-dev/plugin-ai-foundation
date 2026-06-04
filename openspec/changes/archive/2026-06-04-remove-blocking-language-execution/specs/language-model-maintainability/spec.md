## MODIFIED Requirements

### Requirement: Language Model Tool Flow Is Centrally Orchestrated
The language model implementation SHALL centralize Reactor-native tool-step orchestration so streaming and non-streaming tool loops use the same resolution rules for executable tools, external tools, approval requests, tool repair, warnings, provider continuation, timeout, cancellation, and non-blocking execution.

#### Scenario: Streaming and non-streaming share tool-step resolution
- **WHEN** the implementation processes a provider step with tool calls
- **THEN** both `generateText` and `streamText` SHALL obtain recorded tool calls, approval requests, tool results, tool errors, warnings, and continuation eligibility from a shared tool-step orchestration component
- **AND** behavior for external tools, approval-required tools, repaired tool calls, asynchronous executor completion, and callback failures SHALL remain consistent across both execution modes

#### Scenario: Language model facade remains high-level
- **WHEN** maintainers inspect `LanguageModelImpl`
- **THEN** it SHALL primarily coordinate request validation, provider invocation, stream/non-stream entry points, and result aggregation
- **AND** it SHALL NOT directly duplicate low-level tool-step decision logic between streaming and non-streaming loops

#### Scenario: Language implementation has no unsafe Reactor blocking
- **WHEN** maintainers audit production language SDK implementation code
- **THEN** tool execution, tool repair, and generation lifecycle paths MUST NOT contain `block()`, `blockFirst()`, or `blockLast()`
- **AND** any remaining unavoidable synchronous provider call MUST be isolated behind bounded-elastic execution
