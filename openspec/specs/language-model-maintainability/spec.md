## Purpose
Ensure language model tool workflows remain maintainable by centralizing shared orchestration, response history assembly, and behavior-focused regression coverage.
## Requirements
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

### Requirement: Tool Response History Assembly Is Explicit
The system SHALL centralize response message history assembly for tool workflows.

#### Scenario: Tool history is assembled through a dedicated helper
- **WHEN** a generation step produces assistant text, reasoning, tool calls, approval requests, tool results, or tool errors
- **THEN** response messages SHALL be assembled through a dedicated helper
- **AND** the helper SHALL preserve the existing appendable message shapes used by callers to resume tool, external tool, and approval flows

#### Scenario: Tool history invariants remain covered
- **WHEN** the helper assembles approval or tool response history
- **THEN** it SHALL keep assistant tool calls paired with the corresponding approval request, tool result, or tool error according to the existing behavior
- **AND** tests SHALL cover pending approval, denied approval, external tool result, external tool error, repaired tool calls, and mixed tool-call batches

### Requirement: Language Model Tests Are Organized By Behavior
Language model tests SHALL be split into focused classes by behavior area while preserving current coverage.

#### Scenario: Tool-flow tests are focused
- **WHEN** maintainers need to update server-side tool execution, external tool execution, approval, or repair behavior
- **THEN** they SHALL be able to locate focused test classes for those behavior areas
- **AND** the original broad `LanguageModelImplTest` SHALL no longer be the only location for core tool-flow regression coverage

#### Scenario: Refactor preserves verification confidence
- **WHEN** the refactor is complete
- **THEN** focused language model tests SHALL pass
- **AND** `./gradlew compileJava` and OpenSpec strict validation SHALL pass

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
