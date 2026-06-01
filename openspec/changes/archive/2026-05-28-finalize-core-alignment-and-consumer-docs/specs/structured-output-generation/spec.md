## ADDED Requirements

### Requirement: Structured output documentation is complete for consumers
Consumer documentation SHALL describe supported structured output workflows and validation behavior.

#### Scenario: Object output is documented
- **WHEN** a plugin author reads the structured output section
- **THEN** the guide SHALL show object output with SDK schema helpers
- **AND** it SHALL explain final parsed output and validation errors

#### Scenario: Array and choice outputs are documented
- **WHEN** a plugin author reads the structured output section
- **THEN** the guide SHALL describe array, element, choice, and raw JSON output modes

#### Scenario: Streaming structured output is documented
- **WHEN** a plugin author reads the streaming structured output section
- **THEN** the guide SHALL explain partial object snapshots, array element streaming, and final validation authority
