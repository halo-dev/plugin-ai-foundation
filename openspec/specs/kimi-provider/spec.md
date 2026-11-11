# kimi-provider Specification

## Purpose
Define the built-in Kimi provider integration, including provider identity, default endpoint metadata, and supported model behavior.

## Requirements

### Requirement: Kimi provider type identity
The system SHALL register a built-in provider type with providerType `kimi`, display name `Kimi`, and default base URL `https://api.moonshot.cn/v1`.

#### Scenario: Provider type appears in registry
- **WHEN** the plugin starts and Spring discovers all `AiProviderType` beans
- **THEN** a bean with `getProviderType()` returning `"kimi"` is available
- **AND** `getDisplayName()` returns `"Kimi"`
- **AND** `isBuiltIn()` returns `true`
- **AND** `requiresBaseUrl()` returns `false`
- **AND** `getDefaultBaseUrl()` returns `"https://api.moonshot.cn/v1"`

#### Scenario: Kimi appears in console provider type list
- **WHEN** the console fetches `GET /apis/console.api.aifoundation.halo.run/v1alpha1/provider-types`
- **THEN** the response includes an entry with providerType `kimi` and displayName `Kimi`

### Requirement: Kimi chat model construction
The system SHALL build a `ChatModel` using Spring AI's `OpenAiApi` configured with Kimi's base URL and the user's API key.

#### Scenario: Build chat model with default base URL
- **WHEN** `buildChatModel` is called with an `AiProvider` that has no custom base URL and a valid API key
- **THEN** an `OpenAiChatModel` is constructed with base URL `https://api.moonshot.cn/v1`, completions path `/chat/completions`, and the provided API key

#### Scenario: Build chat model with custom base URL
- **WHEN** `buildChatModel` is called with an `AiProvider` that specifies a custom base URL
- **THEN** the custom base URL is used instead of the default

### Requirement: Kimi provider-owned chat semantics
The Kimi provider SHALL own multimodal message conversion, reasoning history, structured output, tool calling, prompt caching, usage, and discovery behavior.

#### Scenario: Continue a Kimi reasoning tool call
- **WHEN** a Kimi assistant turn contains reasoning and tool calls followed by tool results
- **THEN** the next request SHALL preserve the provider-required reasoning content and tool call identity

#### Scenario: Kimi prompt cache key
- **WHEN** an administrator mapping supplies a supported Kimi prompt cache key
- **THEN** the provider request SHALL serialize it using Kimi's documented field

#### Scenario: Kimi multimodal input
- **WHEN** a selected Kimi model and adapter support image or video input
- **THEN** the provider SHALL serialize caller data or `ms://` file references in Kimi's documented content format and reject arbitrary external URLs

#### Scenario: Configure Kimi reasoning controls
- **WHEN** a Kimi model requires a generation-specific reasoning field or value
- **THEN** the administrator SHALL express that translation through the model's reasoning mapping
- **AND** the provider SHALL serialize the mapped field without inspecting the model identifier

#### Scenario: Continue a partial assistant prefix
- **WHEN** the Kimi provider option enables Partial Mode and the final message is an assistant text prefix
- **THEN** the provider SHALL set `partial: true` on that message and reject incompatible structured output or tool-call history
