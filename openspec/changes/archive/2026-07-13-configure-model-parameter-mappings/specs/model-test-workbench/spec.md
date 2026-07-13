## MODIFIED Requirements

### Requirement: Workbench exposes common chat parameters
The workbench SHALL expose the complete typed text-generation parameter surface without a provider-native JSON editor.

#### Scenario: User configures common parameters
- **WHEN** an administrator configures system, sampling, token, reasoning, log probability, or tool parallelism controls
- **THEN** the next test request SHALL include the corresponding typed values
- **AND** the system prompt SHALL remain top-level `system`

#### Scenario: Provider options editor is absent
- **WHEN** an administrator opens chat parameters
- **THEN** no `providerOptions` JSON field or JSON validation state SHALL be displayed

### Requirement: Workbench tests reranking models
The model test workbench SHALL provide reranking tests through the generated typed client.

#### Scenario: Run reranking test
- **WHEN** an administrator selects a reranking model and enters a query with candidate documents
- **THEN** the workbench SHALL call the generated endpoint and display ranked results with scores and original indexes

#### Scenario: Reranking parameters are typed
- **WHEN** an administrator configures reranking test settings
- **THEN** the workbench SHALL expose `topN` and runtime controls supported by the Console endpoint
- **AND** it SHALL NOT expose provider-options JSON

## ADDED Requirements

### Requirement: Workbench exposes typed parameters for every model domain
The workbench SHALL replace raw provider option inputs in language, embedding, reranking, image, and RAG test modes with their available typed fields.

#### Scenario: Image negative prompt is tested
- **WHEN** an administrator tests an image model
- **THEN** the workbench SHALL allow an optional negative prompt and display returned warnings

#### Scenario: Unsupported mapped field is tested
- **WHEN** a Console test uses a typed field marked unsupported by the effective mapping
- **THEN** the workbench SHALL show the backend warning without failing the request solely for that optional field

