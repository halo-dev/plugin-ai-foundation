## ADDED Requirements

### Requirement: Workbench Dogfoods UI Runtime Schema Hooks
The console model test workbench SHALL lightly exercise UI runtime schema hooks through its existing `useChat` integration.

#### Scenario: Workbench configures broad metadata schema
- **WHEN** the workbench creates its UI message chat runtime
- **THEN** it SHALL configure a message metadata schema that accepts absent metadata and object-shaped metadata
- **AND** it SHALL NOT add new administrator controls for metadata schema configuration

#### Scenario: Workbench configures broad data schemas
- **WHEN** the workbench consumes known test data parts
- **THEN** it SHALL configure data part schemas that reject undefined persistent data payloads
- **AND** it SHALL NOT validate transient data through those schemas

#### Scenario: Workbench schema failures use runtime error display
- **WHEN** a schema validation failure occurs in the workbench chat runtime
- **THEN** the existing chat error display path SHALL show the runtime error
- **AND** the workbench SHALL NOT add a separate schema-specific error panel
