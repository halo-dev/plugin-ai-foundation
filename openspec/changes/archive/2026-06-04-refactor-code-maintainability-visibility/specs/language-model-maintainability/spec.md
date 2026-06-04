## ADDED Requirements

### Requirement: Language Generation Internals Have Visible Responsibilities
The language model implementation SHALL model generation, streaming, structured output, and tool workflow responsibilities with focused internal types and collaborators instead of broad helper methods and hidden positional state.

#### Scenario: Generation step state is grouped by type
- **WHEN** maintainers inspect language generation internals
- **THEN** stable per-step values such as request, step index, provider messages, execution messages, provider metadata, lifecycle hooks, and resolved step controls SHALL be grouped into explicit internal context/value types
- **AND** helper methods SHALL receive those types instead of repeatedly accepting the same step values as independent positional parameters

#### Scenario: Stream mapping and aggregation are focused
- **WHEN** provider stream responses are mapped, normalized, accumulated, and converted into final `StreamTextResult` state
- **THEN** stream parsing, event normalization, response aggregation, and final result assembly SHALL be separated into focused helpers
- **AND** stream part ordering, response messages, usage, warnings, reasoning, and provider metadata SHALL remain behaviorally unchanged

#### Scenario: Structured output parsing is focused
- **WHEN** structured output text is extracted, validated, repaired, or converted into final output
- **THEN** parsing, validation, fallback extraction, and error construction SHALL be separated into focused helpers where they currently share oversized methods
- **AND** existing structured output success and failure behavior SHALL remain unchanged

#### Scenario: Tool orchestration state is visible
- **WHEN** tool calls are validated, repaired, approved, executed, or recorded for continuation
- **THEN** repair handling, approval policy evaluation, server-side executor invocation, lifecycle wrapping, and batch accumulation SHALL be represented by focused internal collaborators or context/result objects
- **AND** helper methods SHALL NOT pass multiple mutable collections and repeated step invariants through long positional parameter lists

#### Scenario: Language behavior remains unchanged
- **WHEN** the language maintainability refactor is complete
- **THEN** existing streaming and non-streaming behavior for prompts, messages, reasoning, structured output, server-side tools, external tools, approval requests, approval responses, denied approvals, repair success, repair failure, warnings, timeout, cancellation, and lifecycle events SHALL remain unchanged
- **AND** focused language model tests SHALL continue to pass
