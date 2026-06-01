## ADDED Requirements

### Requirement: Discovery profile normalization
The system SHALL normalize provider discovery metadata into the existing model capability profile fields without relying on provider-specific static model catalogs.

#### Scenario: Remote confirmed model type
- **WHEN** provider discovery receives a model from a remote API response or typed endpoint context that explicitly identifies the model type
- **THEN** the discovered profile SHALL set the corresponding `modelType`
- **AND** it SHALL set an adapter type supported by the provider type when a safe match exists
- **AND** it SHALL set `discoverySource = remote`
- **AND** it SHALL set `discoveryConfidence = high`

#### Scenario: Remote confirmed model features
- **WHEN** provider discovery receives explicit remote feature metadata for a supported model type
- **THEN** the discovered profile SHALL map only supported feature values into `features`
- **AND** it SHALL NOT infer detailed features such as vision, tool calling, structured output, or reasoning from model names alone

#### Scenario: Low confidence rule inference
- **WHEN** provider discovery only has a model ID and no remote type or feature metadata
- **THEN** the discovered profile MAY apply generic low-confidence rules such as recognizing IDs containing `embed`
- **AND** any such inferred profile SHALL set `discoverySource = rule`
- **AND** it SHALL set `discoveryConfidence = low`

#### Scenario: No static catalog classification
- **WHEN** a model ID does not contain generic inference tokens and remote metadata does not identify its type
- **THEN** the backend SHALL NOT classify it using provider-specific model family catalogs
- **AND** the admin SHALL be able to correct the model type and features before import

## MODIFIED Requirements

### Requirement: Discovery evidence metadata
The system SHALL track how a model capability profile was obtained and how reliable the system considers it.

#### Scenario: Persist discovery evidence
- **WHEN** a model profile is created from discovery, provider catalog, heuristics, or manual admin input
- **THEN** the profile SHALL include a source value of `remote`, `catalog`, `rule`, or `manual`
- **AND** the profile SHALL include a confidence value of `high`, `medium`, or `low`
- **AND** provider-specific discovery SHALL use `remote` only when the model profile is confirmed by remote fields or typed remote endpoint context
- **AND** low-confidence model-name heuristics SHALL use `rule`

#### Scenario: Manual confirmation
- **WHEN** an admin edits and saves a discovered model profile
- **THEN** the saved profile SHALL be treated as admin-confirmed model metadata
- **AND** weak discovery evidence SHALL NOT prevent the admin from correcting the model type or features
- **AND** high-confidence discovery evidence SHALL NOT prevent the admin from correcting the model type or features
