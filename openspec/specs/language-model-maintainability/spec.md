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
