## ADDED Requirements

### Requirement: Tool Approval Documentation Covers Two-Call Workflow
Consumer documentation SHALL explain how plugin authors declare, receive, approve, deny, and resume tool execution approvals.

#### Scenario: Approval declaration is documented
- **WHEN** a plugin author reads the tools section
- **THEN** the guide SHALL show how to configure always-required approval and dynamic approval on `ToolDefinition`

#### Scenario: Approval request handling is documented
- **WHEN** a plugin author reads the tools section
- **THEN** the guide SHALL show how to find `tool-approval-request` parts in non-streaming and streaming results
- **AND** it SHALL explain that generation completes instead of blocking for approval

#### Scenario: Approval response resume is documented
- **WHEN** a plugin author reads the tools section
- **THEN** the guide SHALL show how to append a `tool-approval-response` to message history and call the model again
- **AND** it SHALL explain the approved and denied outcomes

#### Scenario: Approval persistence caveat is documented
- **WHEN** a plugin author reads the tools section
- **THEN** the guide SHALL warn that callers must persist returned response messages after approval execution to avoid replaying the same approved tool call
