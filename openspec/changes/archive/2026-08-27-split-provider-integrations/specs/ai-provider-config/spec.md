## ADDED Requirements

### Requirement: Provider endpoint families
A built-in provider SHALL resolve each supported model domain and protocol against the provider-documented endpoint family, including distinct regional or native endpoint roots where required.

#### Scenario: Native embedding endpoint differs from chat endpoint
- **WHEN** a provider documents a native embedding base URL distinct from its compatible chat base URL
- **THEN** the embedding adapter SHALL use the native endpoint family
- **AND** changing chat protocol code SHALL NOT alter embedding requests

#### Scenario: Generic endpoint overrides
- **WHEN** an administrator configures endpoint overrides for the generic OpenAI-compatible provider
- **THEN** those overrides SHALL continue to apply only to that provider resource

### Requirement: Provider authentication ownership
Each provider adapter SHALL apply only the authentication headers documented for that provider while resolving credentials from the existing Halo Secret reference.

#### Scenario: Provider requires a nonstandard header
- **WHEN** official documentation requires a provider-specific authentication or application header
- **THEN** the provider-owned adapter SHALL add it
- **AND** the credential SHALL NOT be copied into plaintext Extension fields or diagnostics
