## MODIFIED Requirements

### Requirement: Consumer guide is organized by SDK workflows
The project SHALL provide a consumer-facing SDK guide organized around plugin author workflows and typed APIs.

#### Scenario: Caller starts from quick start
- **WHEN** a plugin author opens `dev/dev.md`
- **THEN** the document SHALL first explain setup, runtime dependency, `AiModelService`, and `AiModel.metadata.name` lookup

#### Scenario: Caller finds feature workflows
- **WHEN** a plugin author scans the guide
- **THEN** it SHALL expose text, streaming, structured output, tools, typed settings, embeddings, reranking, images, errors, and testing
- **AND** it SHALL NOT include an advanced provider-options workflow

#### Scenario: Caller sees typed examples
- **WHEN** a section includes normal SDK usage
- **THEN** the example SHALL use public typed APIs without raw provider-native keys

### Requirement: Main SDK Guide Is Caller-First
The main consumer guide SHALL follow the order in which a plugin author adopts and uses the typed SDK.

#### Scenario: Setup comes before feature details
- **WHEN** a plugin author opens `dev/dev.md`
- **THEN** setup, service resolution, and model selection SHALL precede feature details

#### Scenario: Common workflows define the document order
- **WHEN** a plugin author scans the guide
- **THEN** workflows SHALL include text, streaming, tools, structured output, reasoning and metadata, cancellation and timeouts, embeddings, reranking, images, warnings, errors, and testing
- **AND** provider-native request options SHALL not appear as a caller workflow

### Requirement: Documentation reflects Spring AI RC1 caller-visible behavior
Consumer documentation SHALL describe caller-visible typed settings and warnings without requiring Spring AI or adapter implementation knowledge.

#### Scenario: No Spring AI migration internals in consumer guide
- **WHEN** a plugin author reads the guide
- **THEN** it SHALL NOT require understanding Spring AI builders, provider clients, or removed APIs

#### Scenario: Tool strict caveat is documented when needed
- **WHEN** provider strict tool behavior differs
- **THEN** the guide SHALL describe the caller-visible behavior and local validation

#### Scenario: Mapping caveats are documented
- **WHEN** a typed parameter depends on administrator mapping or adapter support
- **THEN** the guide SHALL explain omission and warning behavior
- **AND** it SHALL NOT recommend provider-native options

#### Scenario: Documentation validation covers changed examples
- **WHEN** documentation validation runs
- **THEN** it SHALL fail on stale `providerOptions`, stale imports, missing typed fields, or examples that require Spring AI classes

## ADDED Requirements

### Requirement: Consumer guide distinguishes administrator mappings from caller settings
The guide SHALL explain that plugin developers express intent through typed fields while super administrators configure Provider and Model translation.

#### Scenario: Developer reads parameter guidance
- **WHEN** a plugin author reads the settings section
- **THEN** the guide SHALL state that the caller does not need to know the selected Provider or Model wire format
- **AND** unsupported mapped parameters can produce warnings

### Requirement: Consumer guide documents provider metadata migration
The guide SHALL use `providerMetadata` for opaque response and continuation state.

#### Scenario: Existing providerOptions terminology is searched
- **WHEN** documentation validation scans public caller guidance
- **THEN** request `providerOptions` SHALL be absent
- **AND** provider-owned continuation examples SHALL use `providerMetadata`

