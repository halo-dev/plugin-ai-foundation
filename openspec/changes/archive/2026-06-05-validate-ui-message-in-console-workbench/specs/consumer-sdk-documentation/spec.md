## ADDED Requirements

### Requirement: Main SDK Guide Is Caller-First
The main consumer SDK guide SHALL be organized around the order in which a plugin author adopts and uses the SDK.

#### Scenario: Setup comes before feature details
- **WHEN** a plugin author opens `dev/dev.md`
- **THEN** the guide first explains dependency setup, runtime plugin dependency, `AiModelService` resolution, and model selection
- **AND** it avoids starting with advanced or implementation-oriented details

#### Scenario: Common workflows define the document order
- **WHEN** a plugin author scans `dev/dev.md`
- **THEN** the guide presents common workflows before advanced options
- **AND** those workflows include text generation, streaming, tools, structured output, reasoning and metadata, cancellation and timeouts, embeddings, provider options, errors, and testing

### Requirement: Main SDK Guide Is Concise And Scannable
The main consumer SDK guide SHALL reduce long prose and make common workflows easy to scan.

#### Scenario: Dense prose is replaced by focused structure
- **WHEN** the guide explains an SDK workflow
- **THEN** it prefers short sections, small tables, and focused code examples over long explanatory paragraphs

#### Scenario: Mixed terminology is reduced
- **WHEN** the guide explains caller-facing concepts
- **THEN** it uses consistent Chinese terminology where possible
- **AND** it preserves exact Java API names in code formatting

#### Scenario: Caller examples use public APIs
- **WHEN** the guide includes Java examples
- **THEN** the examples use public SDK API types and methods
- **AND** they avoid requiring knowledge of internal provider adapters or console endpoint implementation

### Requirement: Main SDK Guide Links To Dedicated UI Message Guide
The main consumer SDK guide SHALL introduce UI Message usage without duplicating the dedicated UI Message guide.

#### Scenario: UI Message stream has a short entry point
- **WHEN** a plugin author reads the streaming section in `dev/dev.md`
- **THEN** the guide explains when to use `UIMessageStream` and `UIMessage`
- **AND** it links to `dev/ui-message-stream.md` for the complete backend UI Message workflow

#### Scenario: Detailed UI Message content stays in dedicated guide
- **WHEN** UI Message details involve chunk aggregation, metadata lifecycle, UI Message validation, conversion, regeneration, or cancellation
- **THEN** the main guide summarizes the topic briefly
- **AND** the detailed instructions remain in `dev/ui-message-stream.md`

### Requirement: Main SDK Guide Records Deferred Frontend Work
The main consumer SDK guide SHALL keep frontend helper and runtime deferrals visible without presenting them as current backend features.

#### Scenario: Deferred UI Message work is clearly marked
- **WHEN** the guide mentions frontend helpers, WebFlux adapters, stop endpoints, resume, reconnect, stream registries, or provider-aware reasoning preservation
- **THEN** it marks them as deferred work
- **AND** it does not describe them as currently available public APIs
