## ADDED Requirements

### Requirement: Kimi provider type identity
The system SHALL register a built-in provider type with providerType `kimi`, display name `Kimi`, and default base URL `https://api.moonshot.cn`.

#### Scenario: Provider type appears in registry
- **WHEN** the plugin starts and Spring discovers all `AiProviderType` beans
- **THEN** a bean with `getProviderType()` returning `"kimi"` is available
- **AND** `getDisplayName()` returns `"Kimi"`
- **AND** `isBuiltIn()` returns `true`
- **AND** `requiresBaseUrl()` returns `false`
- **AND** `getDefaultBaseUrl()` returns `"https://api.moonshot.cn"`

#### Scenario: Kimi appears in console provider type list
- **WHEN** the console fetches `GET /apis/console.api.aifoundation.halo.run/v1alpha1/provider-types`
- **THEN** the response includes an entry with providerType `kimi` and displayName `Kimi`

### Requirement: Kimi chat model construction
The system SHALL build a `ChatModel` using Spring AI's `OpenAiApi` configured with Kimi's base URL and the user's API key.

#### Scenario: Build chat model with default base URL
- **WHEN** `buildChatModel` is called with an `AiProvider` that has no custom base URL and a valid API key
- **THEN** an `OpenAiChatModel` is constructed with base URL `https://api.moonshot.cn`, completions path `/v1/chat/completions`, and the provided API key

#### Scenario: Build chat model with custom base URL
- **WHEN** `buildChatModel` is called with an `AiProvider` that specifies a custom base URL
- **THEN** the custom base URL is used instead of the default

### Requirement: Kimi does not support embeddings
The system SHALL report that the Kimi provider type does not support embeddings.

#### Scenario: Embedding support check
- **WHEN** `supportsEmbeddings()` is called on the Kimi provider type
- **THEN** it returns `false`

#### Scenario: Supported endpoint types
- **WHEN** `getSupportedEndpointTypes()` is called on the Kimi provider type
- **THEN** it returns `["openai-chat"]` only
