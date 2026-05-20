## Purpose

Define metadata exposed by built-in provider types.

## Requirements

### Requirement: Built-in providers expose complete metadata
Every built-in provider class extending `AbstractAiProviderType` SHALL override `getDescription()`, `getIconUrl()`, `getWebsiteUrl()`, and `getDocumentationUrl()` to return non-null, non-empty values.

#### Scenario: Provider type list includes metadata
- **WHEN** a client calls `GET /apis/console.api.aifoundation.halo.run/v1alpha1/provider-types`
- **THEN** every built-in provider in the response has non-null values for `description`, `iconUrl`, `websiteUrl`, and `documentationUrl`

#### Scenario: Icon URLs resolve to existing static assets
- **WHEN** a client requests the `iconUrl` returned by any built-in provider
- **THEN** the URL resolves to an existing PNG image under `/plugins/ai-foundation/assets/static/brands/`
