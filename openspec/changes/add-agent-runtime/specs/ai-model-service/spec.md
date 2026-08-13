## MODIFIED Requirements

### Requirement: Tool Call Repair
The language model service SHALL support caller-provided recovery of invalid or unknown model-produced tool calls before approval, external handoff, or server-side execution.

#### Scenario: Recovery context identifies invalid known-tool input
- **WHEN** a provider returns a tool call whose name matches a request tool and whose input fails that tool's input schema
- **AND** the request includes a tool-call recovery callback
- **THEN** the system SHALL invoke the callback with failure kind `INVALID_INPUT`
- **AND** the context SHALL include the original call, matching tool, complete available tool list, validation details, step index, provider messages, request context, and provider metadata

#### Scenario: Repaired known-tool input executes
- **WHEN** recovery returns a call with the original tool name and call id
- **AND** the repaired input satisfies the original tool input schema
- **THEN** the system SHALL continue normal approval, external handoff, or server-side execution using the repaired input
- **AND** the step SHALL record a stable warning that the tool call was repaired
- **AND** `GenerateTextResult.responseMessages` SHALL contain the repaired assistant tool-call message before any matching tool result or error message

#### Scenario: Recovery context identifies an unknown tool
- **WHEN** a provider returns a named tool call that is absent from the current available tools
- **AND** the request includes a tool-call recovery callback
- **AND** at least one request tool is currently available
- **THEN** the system SHALL invoke the callback with failure kind `UNKNOWN_TOOL`
- **AND** the context SHALL include the original call, no matching tool, the complete available tool list, step state, messages, request context, and provider metadata

#### Scenario: Unknown tool is mapped to an available tool
- **WHEN** unknown-tool recovery returns the name of a currently available tool with the original call id
- **AND** the returned input satisfies that tool's input schema
- **THEN** the system SHALL continue normal approval, external handoff, or server-side execution using the resolved tool
- **AND** the step SHALL record a stable warning containing safe original-name and resolved-name diagnostics
- **AND** response messages SHALL contain only the resolved tool call for continuation

#### Scenario: Recovered tool preserves call identity
- **WHEN** recovery succeeds for invalid input or an unknown tool
- **THEN** the repaired call MUST retain the provider's original non-blank tool call id
- **AND** downstream approval, UI Message parts, results, errors, and continuation SHALL use that same id

#### Scenario: Recovered call is fully revalidated
- **WHEN** a recovery callback returns a tool call
- **THEN** the system SHALL validate name availability, input schema, approval policy, external-tool state, and executor eligibility before making the input available or executing it
- **AND** it MUST NOT trust callback output as already valid

#### Scenario: Recovery is not configured
- **WHEN** a provider returns invalid known-tool input or an unknown tool
- **AND** the request does not include a recovery callback
- **THEN** the system SHALL retain the existing safe validation or unknown-tool error
- **AND** it SHALL NOT execute the tool

#### Scenario: Unknown tool has no recovery target
- **WHEN** a provider returns an unknown tool and the current available tool set is empty
- **THEN** the system SHALL record an unknown-tool error without invoking recovery
- **AND** it SHALL NOT execute the tool

#### Scenario: Recovery fails safely
- **WHEN** the callback fails, returns no repaired call, changes the call id, returns a disallowed name, returns a missing tool, or returns input that still fails validation
- **THEN** the system SHALL record the original safe validation or unknown-tool error
- **AND** it SHALL NOT execute the tool
- **AND** it SHALL report a stable warning that recovery was attempted and failed

#### Scenario: Non-tool-input failures are not recovered
- **WHEN** a server-side executor fails, output schema validation fails, approval is denied, a tool times out, or generation is cancelled
- **THEN** the system SHALL use the existing error, denial, timeout, or cancellation behavior
- **AND** it SHALL NOT invoke tool-call recovery

#### Scenario: Streamed unknown tool resolves before availability
- **WHEN** a streamed tool call finishes with an unknown name and recovery succeeds
- **THEN** the runtime SHALL retain the accumulated input under the original call id
- **AND** it SHALL publish input availability only after the resolved tool and input pass validation
- **AND** the UI Message reducer SHALL finalize the resolved name on the existing call part instead of creating a second part

#### Scenario: Resolved tool callbacks run in canonical order
- **WHEN** streamed unknown-tool recovery resolves to a tool with input lifecycle callbacks
- **THEN** the runtime SHALL invoke one input-start callback, replay the accumulated input through the input-delta callback, and invoke one input-available callback in that order
- **AND** approval, external handoff, or execution MUST wait for those callbacks

#### Scenario: Recovery context is provider-neutral
- **WHEN** the callback receives failure details, tools, messages, and metadata
- **THEN** those values SHALL use AI Foundation public DTOs and provider-neutral maps
- **AND** the public API SHALL NOT expose Spring AI message, prompt, response, or exception types
