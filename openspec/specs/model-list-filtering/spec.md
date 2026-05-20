## Purpose

Define server-side filtering for configured AI model lists.

## Requirements

### Requirement: Model list supports fieldSelector and labelSelector
The system SHALL support filtering the model list via `fieldSelector` and `labelSelector` query parameters, consistent with Halo's standard extension list API.

#### Scenario: Filter models by providerName via fieldSelector
- **WHEN** user sends `GET /models?fieldSelector=spec.providerName=openai`
- **THEN** system returns only models whose `spec.providerName` equals `openai`

#### Scenario: List all models when selectors are absent
- **WHEN** user sends `GET /models` without `fieldSelector` or `labelSelector` parameters
- **THEN** system returns all models

### Requirement: providerName is indexed
The system SHALL maintain an index on `AiModel.spec.providerName` for efficient filtering.

#### Scenario: Index is used for providerName queries
- **WHEN** model list is filtered by `fieldSelector=spec.providerName=xxx`
- **THEN** query SHALL use the indexed field selector instead of full scan

### Requirement: Frontend uses server-side filtering
The system SHALL pass `fieldSelector` as a query parameter from the frontend instead of filtering client-side.

#### Scenario: Provider detail page loads associated models
- **WHEN** user navigates to a provider detail page
- **THEN** frontend calls `GET /models?fieldSelector=spec.providerName={name}` and renders returned models directly
