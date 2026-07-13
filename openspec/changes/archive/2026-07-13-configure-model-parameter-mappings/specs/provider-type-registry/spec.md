## ADDED Requirements

### Requirement: Provider types declare parameter mapping metadata
Each `AiProviderType` SHALL declare built-in mapping defaults and compatible template IDs for its supported adapters without declaring a model catalog.

#### Scenario: Provider type metadata is listed
- **WHEN** the Provider type Console endpoint returns a Provider type
- **THEN** the response SHALL include mapping templates and defaults grouped by model type and provider-neutral parameter
- **AND** the response SHALL continue to distinguish Provider type identity from Provider resource identity

#### Scenario: Provider supports no template for a parameter
- **WHEN** a Provider adapter cannot serialize a public mapped parameter
- **THEN** its built-in declaration SHALL mark that parameter unsupported

#### Scenario: Provider metadata does not infer model behavior
- **WHEN** a Provider type declares mapping defaults
- **THEN** it SHALL describe adapter behavior only
- **AND** it SHALL NOT branch on known model IDs or model-name prefixes
