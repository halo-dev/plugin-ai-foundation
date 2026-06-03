## ADDED Requirements

### Requirement: Stream Text Result Exposes Approval Requests
`StreamTextResult` SHALL expose pending tool approval requests through full stream and final result projections.

#### Scenario: Full stream contains approval request
- **WHEN** a stream emits a tool call that requires approval
- **THEN** `fullStream()` SHALL emit a `tool-approval-request` part containing the approval id and normalized tool call data

#### Scenario: Final result contains approval request
- **WHEN** a streamed generation completes with pending approval requests
- **THEN** `result()` SHALL expose those approval requests in the final accumulated generation result
- **AND** it SHALL not report tool results for tools that were not executed

#### Scenario: Convenience projections avoid duplicate execution
- **WHEN** multiple `StreamTextResult` projections are consumed for a stream with approval requests
- **THEN** each approval request SHALL be produced at most once by the shared generation execution
- **AND** no tool executor SHALL run until a later request provides approval
