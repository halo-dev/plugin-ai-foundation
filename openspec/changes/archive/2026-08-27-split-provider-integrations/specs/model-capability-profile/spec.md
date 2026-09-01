## ADDED Requirements

### Requirement: Explicit provider language capabilities
Language capabilities SHALL be declared per provider and refined per discovered or manually configured model rather than inherited from a universal optimistic default.

#### Scenario: Provider does not document audio input
- **WHEN** a provider exposes language models but does not document audio input for the selected adapter or model
- **THEN** audio input SHALL be disabled by default

#### Scenario: Model catalog supplies capabilities
- **WHEN** provider discovery returns authoritative vision, reasoning, tool, structured-output, or modality metadata
- **THEN** the model profile SHALL use those values and identify remote discovery as the source

#### Scenario: Identifier-only catalog uses adapter capability defaults
- **WHEN** a provider catalog does not identify model-specific capabilities
- **THEN** discovery SHALL initialize the model with the selected adapter's declared language
  capabilities
- **AND** SHALL mark the result as low confidence rather than remote capability evidence
- **AND** an administrator MAY disable capabilities unsupported by that specific model

### Requirement: Capabilities depend on adapter protocol
A model capability SHALL be enabled only when both the provider model and selected invocation adapter can represent it.

#### Scenario: Built-in tool available only through Responses
- **WHEN** a provider documents a built-in tool only for its Responses API
- **THEN** a Chat Completions model profile SHALL NOT advertise that tool capability
- **AND** the Responses model profile MAY advertise it when the public runtime can normalize its output
