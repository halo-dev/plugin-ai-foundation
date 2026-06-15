## ADDED Requirements

### Requirement: Documentation Covers UI Runtime Schema Hooks
Consumer documentation SHALL explain frontend runtime schema hooks for UI message metadata and persisted dynamic data parts.

#### Scenario: UI message guide documents schema hook scope
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide SHALL explain `messageMetadataSchema` and `dataPartSchemas`
- **AND** it SHALL state that these hooks validate frontend runtime stream data
- **AND** it SHALL state that they do not replace backend `UIMessageValidators`

#### Scenario: UI message guide documents data schema keys
- **WHEN** a plugin author configures data part schemas
- **THEN** the guide SHALL show schemas keyed by data part name
- **AND** it SHALL avoid requiring callers to key schemas by full `data-*` protocol type

#### Scenario: UI message guide documents schema failure behavior
- **WHEN** a plugin author reads the schema hook section
- **THEN** the guide SHALL explain that schema failures enter the normal chat error lifecycle
- **AND** it SHALL mention that the active stream is aborted and the failing update is not committed

#### Scenario: Package README mentions schema hooks
- **WHEN** a user reads the package README
- **THEN** it SHALL include a concise example of configuring metadata and data part schemas
