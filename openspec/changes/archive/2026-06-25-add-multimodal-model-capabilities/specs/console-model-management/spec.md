## ADDED Requirements

### Requirement: Console displays discovered capability summaries
The Console SHALL show concise capability summaries when administrators discover or import provider models.

#### Scenario: Discovery modal capability summary
- **WHEN** discovered models include fine-grained capabilities
- **THEN** the discovery modal SHALL display concise capability labels such as image input, file input, text-to-image, image-to-image, mask input, or URL input where applicable
- **AND** it SHALL NOT require administrators to inspect raw JSON to understand the main capability signals

#### Scenario: Unknown capability summary
- **WHEN** a discovered model has unknown fine-grained capability data
- **THEN** the Console SHALL avoid presenting unknown capabilities as supported

### Requirement: Console advanced capability editing
The Console SHALL allow administrators to edit model capabilities in an advanced model configuration area.

#### Scenario: Language capability editor
- **WHEN** an administrator edits a language model
- **THEN** the advanced area SHALL allow editing image input, file input, supported input media types, and supported input sources

#### Scenario: Image generation capability editor
- **WHEN** an administrator edits an image generation model
- **THEN** the advanced area SHALL allow editing text-to-image, image-to-image, mask input, max images per call, supported sizes, supported aspect ratios, and output media types

#### Scenario: Capability source display
- **WHEN** the advanced capability area is shown
- **THEN** the Console SHALL display lightweight source labels by capability domain
- **AND** it SHALL distinguish manual overrides from discovered or rule-provided capability data

### Requirement: Capability synchronization preserves manual overrides
The Console SHALL support explicit capability synchronization from discovery while preserving manual override domains.

#### Scenario: Sync non-manual capability domain
- **WHEN** discovery finds updated capability data for an existing model
- **AND** the affected capability domain is not manually overridden
- **THEN** the Console MAY allow the administrator to synchronize that domain into the existing model

#### Scenario: Preserve manual capability domain
- **WHEN** discovery finds updated capability data for a domain that was manually overridden
- **THEN** the Console SHALL NOT overwrite that domain without an explicit administrator action
