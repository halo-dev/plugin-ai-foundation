## ADDED Requirements

### Requirement: Existing providers expose native rerank support
The ZhiPu, DashScope, and SiliconFlow provider types SHALL expose native reranking support through provider metadata and runtime construction.

#### Scenario: ZhiPu provider advertises rerank support
- **WHEN** provider type metadata is requested for ZhiPu
- **THEN** supported model types SHALL include `rerank`
- **AND** supported adapter types SHALL include the neutral rerank adapter type

#### Scenario: DashScope provider advertises rerank support
- **WHEN** provider type metadata is requested for DashScope
- **THEN** supported model types SHALL include `rerank`
- **AND** supported adapter types SHALL include the neutral rerank adapter type

#### Scenario: SiliconFlow provider advertises rerank support
- **WHEN** provider type metadata is requested for SiliconFlow
- **THEN** supported model types SHALL include `rerank`
- **AND** supported adapter types SHALL include the neutral rerank adapter type

### Requirement: Existing providers construct reranking clients
The ZhiPu, DashScope, and SiliconFlow provider types SHALL construct provider-specific reranking clients using the configured provider resource, resolved API key, and provider model id.

#### Scenario: Resolve ZhiPu reranking model
- **WHEN** a reranking `AiModel` is backed by a ZhiPu provider
- **THEN** model resolution SHALL construct a ZhiPu reranking client

#### Scenario: Resolve DashScope reranking model
- **WHEN** a reranking `AiModel` is backed by a DashScope provider
- **THEN** model resolution SHALL construct a DashScope reranking client

#### Scenario: Resolve SiliconFlow reranking model
- **WHEN** a reranking `AiModel` is backed by a SiliconFlow provider
- **THEN** model resolution SHALL construct a SiliconFlow reranking client
