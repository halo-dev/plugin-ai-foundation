## ADDED Requirements

### Requirement: RAG documentation teaches SDK composition
Consumer documentation SHALL teach plugin authors how to compose RAG with SDK primitives in caller-focused terms.

#### Scenario: Minimal RAG example is documented
- **WHEN** a plugin author reads the RAG documentation
- **THEN** the guide SHALL show a minimal `RagRetriever`, `RagLanguageModelMiddleware`, and `generateText` example
- **AND** it SHALL show reading display sources from `GenerateTextResult.getSources()`

#### Scenario: UI Message RAG streaming is documented
- **WHEN** a plugin author reads the RAG documentation
- **THEN** the guide SHALL show a UI Message streaming example using `streamText` and `toUIMessageStreamResponse`
- **AND** it SHALL explain that sources are emitted as `source-url` or `source-document` parts

### Requirement: RAG documentation demonstrates caller-defined data parts
Consumer documentation SHALL demonstrate custom `data-*` usage for RAG status without defining framework-standard RAG data names.

#### Scenario: Custom RAG status data is shown
- **WHEN** a plugin author wants to stream retrieval or rerank status to a frontend
- **THEN** the guide SHALL show using a caller-defined `data-*` part name and payload
- **AND** the guide SHALL NOT describe the example data part name as built in or required

### Requirement: RAG documentation is organized for callers
Consumer documentation SHALL keep RAG content in a clear sequence and avoid duplicated or out-of-order sections.

#### Scenario: RAG sections are readable
- **WHEN** a plugin author scans the developer guide
- **THEN** RAG-related sections SHALL be ordered consistently
- **AND** examples SHALL describe how to use the SDK rather than internal implementation state
