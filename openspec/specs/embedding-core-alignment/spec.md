## Purpose

Define provider-neutral AI API-aligned embedding controls, diagnostics, batching behavior, similarity utility, and console verification workflow.
## Requirements
### Requirement: provider-neutral embedding controls
The system SHALL provide provider-neutral embedding settings including dimensions, batching, parallel call limit, retry budget, timeout/cancellation, request headers, metadata, and context, without caller-native option maps.

#### Scenario: Advanced request carries embedding settings
- **WHEN** a Java caller builds an `EmbeddingRequest` with inputs and optional typed controls
- **THEN** the public request type SHALL represent those controls without exposing Spring AI or provider-native request classes

#### Scenario: Dimensions use effective mapping
- **WHEN** a caller supplies embedding dimensions
- **THEN** the runtime SHALL translate dimensions through the effective Provider/Model mapping

#### Scenario: Invalid parallel limit rejected
- **WHEN** a Java caller sends an `EmbeddingRequest` with `maxParallelCalls` less than 1
- **THEN** the embedding call SHALL fail before invoking the provider

#### Scenario: Retry budget disables retries
- **WHEN** a Java caller sends an `EmbeddingRequest` with `maxRetries = 0`
- **THEN** the embedding layer SHALL attempt each provider batch call at most once

#### Scenario: Cancellation remains request-scoped
- **WHEN** a Java caller cancels the request before a batch provider call starts
- **THEN** the embedding call SHALL stop before invoking that batch
- **AND** the error SHALL be reported through the embedding lifecycle error callback when configured

### Requirement: Embedding response diagnostics
The system SHALL return embedding responses with vectors and diagnostics, including usage, response metadata, provider metadata, and warnings when available.

#### Scenario: Usage returned from provider response
- **WHEN** a provider embedding response includes token usage
- **THEN** `EmbeddingResponse.usage` SHALL expose the token count in provider-neutral fields

#### Scenario: Metadata returned from provider response
- **WHEN** a provider embedding response includes response metadata such as model, id, headers, or raw provider attributes
- **THEN** `EmbeddingResponse.response` and `EmbeddingResponse.providerMetadata` SHALL expose safe provider-neutral diagnostics

#### Scenario: Unsupported option warning
- **WHEN** a caller sends an embedding control that the provider implementation cannot apply
- **THEN** `EmbeddingResponse.warnings` SHALL include a stable warning code identifying the unsupported control
- **AND** the warning SHALL NOT leak credentials or raw request bodies

### Requirement: Batch embedding aggregation
The system SHALL aggregate multi-batch embedding results in the same order as input texts while preserving diagnostics across provider calls.

#### Scenario: Parallel batches preserve input order
- **WHEN** an advanced embedding request is split into multiple batches and executed concurrently
- **THEN** the returned embeddings SHALL be ordered exactly like the original input list

#### Scenario: Batch usage is accumulated
- **WHEN** multiple batch provider responses include usage
- **THEN** the final `EmbeddingResponse.usage` SHALL aggregate the available token counts across all completed batches

#### Scenario: Batch metadata remains inspectable
- **WHEN** multiple batch provider responses include response metadata
- **THEN** the final `EmbeddingResponse.response` SHALL expose the last provider response metadata
- **AND** provider metadata SHALL retain batch-level diagnostics when available

### Requirement: Embedding cosine similarity utility
The public API SHALL provide a utility for calculating cosine similarity between two embedding vectors.

#### Scenario: Similarity calculated
- **WHEN** a caller passes two non-empty vectors with the same length to the cosine similarity utility
- **THEN** the utility SHALL return their cosine similarity as a finite number

#### Scenario: Invalid vectors rejected
- **WHEN** a caller passes null, empty, or different-length vectors to the cosine similarity utility
- **THEN** the utility SHALL reject the input with an argument error

### Requirement: Console embedding test workflow
The console SHALL provide an embedding test workflow for enabled embedding models using typed settings and provider diagnostics.

#### Scenario: Embedding model can be tested
- **WHEN** an enabled embedding model appears in a model list
- **THEN** the console SHALL expose a test action for that model

#### Scenario: Embedding settings can be submitted
- **WHEN** an administrator opens the test workbench in embedding mode
- **THEN** the console SHALL allow editing inputs, dimensions, max batch size, max parallel calls, max retries, and headers
- **AND** it SHALL NOT expose a provider-options JSON editor

#### Scenario: Embedding diagnostics displayed
- **WHEN** an embedding test request succeeds
- **THEN** the console SHALL display vector count, vector dimensions, vector previews, first-pair cosine similarity when available, usage, warnings, and response diagnostics

### Requirement: Embedding Settings Are Discoverable And Documented
Embedding request settings SHALL be documented and discoverable through typed APIs, including dimensions, max batch size, parallelism, retries, headers, lifecycle, timeout, cancellation, metadata, and context.

#### Scenario: Caller configures dimensions
- **WHEN** a plugin author configures embedding dimensions
- **THEN** JavaDoc and examples SHALL explain administrator mapping, provider support, and warning behavior

#### Scenario: Caller needs a provider-native embedding setting
- **WHEN** a provider feature has no provider-neutral typed embedding semantic
- **THEN** the public request SHALL NOT expose a raw escape hatch
- **AND** adding a typed field and mapping template SHALL require a reviewed SDK change

### Requirement: Embedding Fields Are Fully Supported Or Removed
Embedding public provider parameters SHALL have typed mapping behavior and tests or SHALL be absent from the SDK.

#### Scenario: Mapped field is supported
- **WHEN** an embedding parameter remains public
- **THEN** tests SHALL verify its effective template reaches the provider request

#### Scenario: Effective mapping is unsupported
- **WHEN** a caller supplies dimensions and the effective mapping is unsupported
- **THEN** the runtime SHALL omit dimensions and include a stable warning

#### Scenario: Raw option helper is inspected
- **WHEN** a consumer inspects the embedding request builder
- **THEN** no provider-option field or helper SHALL be present

### Requirement: Embedding documentation covers simple and advanced workflows
Consumer documentation SHALL explain simple embedding calls, advanced typed requests, batching, mappings, warnings, and similarity helpers.

#### Scenario: Single and batch embeddings are documented
- **WHEN** a plugin author reads the embeddings section
- **THEN** the guide SHALL show query embedding, batch embedding, and `EmbeddingResponse` usage

#### Scenario: Advanced embedding settings are documented
- **WHEN** a plugin author reads the embeddings settings section
- **THEN** the guide SHALL cover dimensions, max batch size, retries, parallelism, headers, lifecycle, timeouts, cancellation, metadata, and context
- **AND** it SHALL explain that administrators configure native dimensions translation

#### Scenario: Similarity helper is documented
- **WHEN** a plugin author needs vector similarity
- **THEN** the guide SHALL show the public cosine similarity helper and its validation behavior

### Requirement: Embedding controls survive Spring AI RC1 migration
The embedding runtime SHALL preserve provider-neutral typed controls and diagnostics while applying administrator mappings over current provider clients.

#### Scenario: Mapped dimensions are applied
- **WHEN** an embedding request includes dimensions with a supported effective template
- **THEN** the adapter SHALL pass the translated dimensions to the provider

#### Scenario: Unsupported dimensions are reported
- **WHEN** the effective dimensions mapping is unsupported
- **THEN** the adapter SHALL omit dimensions and return a stable warning

#### Scenario: Request-scoped embedding headers are applied
- **WHEN** an embedding request includes request-scoped headers
- **THEN** a provider adapter that supports request-scoped headers SHALL include those headers
- **AND** an adapter that cannot support them SHALL report a stable warning

#### Scenario: Embedding usage and diagnostics remain provider-neutral
- **WHEN** a provider response includes usage or response metadata
- **THEN** `EmbeddingResponse` SHALL expose usage, response metadata, warnings, and provider metadata without exposing Spring AI response classes

### Requirement: Embedding convenience methods
The embedding API SHALL provide convenience methods for single values and many values while preserving typed advanced request support.

#### Scenario: Embed single value
- **WHEN** a caller embeds a single text value
- **THEN** the embedding model SHALL return its vector without requiring a one-item list

#### Scenario: Embed many values
- **WHEN** a caller embeds many text values
- **THEN** the embedding model SHALL return embeddings in input order

#### Scenario: Advanced request remains available
- **WHEN** a caller needs dimensions, batching, retries, headers, cancellation, lifecycle, timeout, metadata, or context
- **THEN** the caller SHALL be able to use `EmbeddingRequest` without provider-native options

