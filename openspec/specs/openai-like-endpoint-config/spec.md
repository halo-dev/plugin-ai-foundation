## ADDED Requirements

### Requirement: OpenAI-compatible endpoint overrides
The system SHALL allow `openailike` provider instances to configure chat, embedding, rerank, and image endpoint paths independently from Base URL.

#### Scenario: Defaults are used when endpoint fields are empty
- **WHEN** an `openailike` provider omits endpoint path fields
- **THEN** the system SHALL use default paths for chat, embedding, rerank, and image calls

#### Scenario: User endpoint values override defaults
- **WHEN** an `openailike` provider defines endpoint path fields
- **THEN** the system SHALL use the configured paths for the matching model capability calls

#### Scenario: Endpoint paths remain relative to Base URL
- **WHEN** an administrator saves endpoint path settings
- **THEN** the system SHALL reject absolute endpoint URLs
- **AND** the system SHALL keep Base URL as the host-level configuration

### Requirement: OpenAI-compatible rerank support
The `openailike` provider SHALL support rerank models through the configured or default rerank endpoint using the standard rerank request shape.

#### Scenario: Rerank adapter is available
- **WHEN** provider type metadata is requested for `openailike`
- **THEN** rerank SHALL be included in the supported model or adapter capabilities

#### Scenario: Rerank request uses configured endpoint
- **WHEN** a rerank model owned by an `openailike` provider is invoked
- **THEN** the request SHALL be sent to Base URL plus the configured rerank endpoint path

### Requirement: Console endpoint fields and previews
The console provider form SHALL show OpenAI-compatible endpoint fields above proxy host and proxy port, with effective URL previews that combine Base URL and endpoint path.

#### Scenario: Endpoint fields appear for OpenAI-compatible provider
- **WHEN** an administrator creates or edits an `openailike` provider
- **THEN** the form SHALL show chat, embedding, rerank, and image endpoint path fields above proxy host and proxy port

#### Scenario: Endpoint previews update with Base URL and path
- **WHEN** an administrator changes Base URL or an endpoint path
- **THEN** each endpoint field help text SHALL preview the effective URL that will be used

#### Scenario: Built-in providers do not show endpoint override fields
- **WHEN** an administrator creates or edits a built-in provider
- **THEN** the form SHALL NOT show the OpenAI-compatible endpoint override fields
