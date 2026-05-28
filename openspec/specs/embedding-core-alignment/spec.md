## Purpose

Define provider-neutral AI API-aligned embedding controls, diagnostics, batching behavior, similarity utility, and console verification workflow.
## Requirements
### Requirement: provider-neutral embedding controls
The system SHALL provide provider-neutral embedding settings comparable to provider-neutral AI API embeddings, including namespaced provider options, parallel call limit, retry budget, timeout/cancellation, and request headers.

#### Scenario: Advanced request carries embedding settings
- **WHEN** a Java caller builds an `EmbeddingRequest` with inputs, provider options, headers, max retries, max parallel calls, timeouts, and a cancellation token
- **THEN** the public request type SHALL represent those controls without exposing Spring AI or provider-native request classes

#### Scenario: provider-neutral settings are represented
- **WHEN** a caller needs the provider-neutral embedding settings for provider options, parallel requests, retries, abort or timeout, and custom headers
- **THEN** `EmbeddingRequest` SHALL provide equivalent Java fields or controls for each setting
- **AND** timeout and abort behavior SHALL be represented by Halo's timeout and cancellation token types

#### Scenario: Invalid parallel limit rejected
- **WHEN** a Java caller sends an `EmbeddingRequest` with `maxParallelCalls` less than 1
- **THEN** the embedding call SHALL fail before invoking the provider

#### Scenario: Retry budget disables retries
- **WHEN** a Java caller sends an `EmbeddingRequest` with `maxRetries = 0`
- **THEN** the embedding layer SHALL attempt each provider batch call at most once

#### Scenario: Cancellation remains request-scoped
- **WHEN** a Java caller cancels the request through the cancellation token before a batch provider call starts
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
The console SHALL provide an embedding test workflow for enabled embedding models so administrators can manually verify embedding settings and provider diagnostics.

#### Scenario: Embedding model can be tested
- **WHEN** an enabled embedding model appears in a model list
- **THEN** the console SHALL expose a test action for that model

#### Scenario: Embedding settings can be submitted
- **WHEN** an administrator opens the test workbench in embedding mode
- **THEN** the console SHALL allow editing inputs, dimensions, max batch size, max parallel calls, max retries, provider options, and headers

#### Scenario: Embedding diagnostics displayed
- **WHEN** an embedding test request succeeds
- **THEN** the console SHALL display vector count, vector dimensions, vector previews, first-pair cosine similarity when available, usage, warnings, and response diagnostics

### Requirement: Embedding Settings Are Discoverable And Documented
Embedding request settings SHALL be documented and discoverable through typed APIs where practical, including dimensions, max batch size, provider options, and request-level metadata.

#### Scenario: Caller configures dimensions
- **WHEN** a plugin author configures embedding dimensions
- **THEN** JavaDoc and examples explain provider support behavior and validation outcomes

#### Scenario: Caller configures provider options
- **WHEN** a plugin author needs provider-specific embedding options
- **THEN** the SDK exposes a documented typed helper where available or a clearly labeled escape hatch

### Requirement: Embedding Fields Are Fully Supported Or Removed
Embedding public request fields SHALL either have implemented provider mapping and tests or be removed from the SDK.

#### Scenario: Header-like field is unsupported
- **WHEN** an embedding request field cannot be honored by the current provider integration
- **THEN** the field is removed instead of remaining as a warning-only or no-op property

#### Scenario: Provider option is supported
- **WHEN** an embedding provider option remains public
- **THEN** tests verify it reaches the provider request or produces documented validation behavior

