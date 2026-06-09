## ADDED Requirements

### Requirement: Workbench dogfoods ai-ui-vue chat runtime
The model test workbench SHALL use `@halo-dev/ai-ui-vue` for its UIMessage chat stream state and parsing path while preserving the existing console UI.

#### Scenario: Workbench uses package chat transport
- **WHEN** the administrator sends a chat test message through the workbench
- **THEN** the workbench SHALL use the package chat runtime or transport to send a `UIMessageChatRequest`
- **AND** it SHALL consume the Halo UIMessage stream response through the package stream parser

#### Scenario: Workbench preserves UI rendering
- **WHEN** the workbench is migrated to the package runtime
- **THEN** the visible console layout, Chinese labels, Markdown rendering, and Halo component usage SHALL remain owned by the console UI
- **AND** the package SHALL NOT introduce visual components for this migration

#### Scenario: Workbench preserves cancellation
- **WHEN** the administrator stops an in-progress package-backed chat response
- **THEN** the active stream request SHALL be aborted
- **AND** the partial assistant message SHALL remain visible

#### Scenario: Workbench preserves tool continuation
- **WHEN** the administrator supplies external tool results, tool errors, or approval responses
- **THEN** the workbench SHALL use package helpers or equivalent package state mutations to persist the matching assistant parts
- **AND** it SHALL resubmit the updated conversation through the Halo UIMessage chat endpoint
