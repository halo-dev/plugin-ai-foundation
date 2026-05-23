## MODIFIED Requirements

### Requirement: AiModelService as Registry/Factory

The `api/` module SHALL expose an `AiModelService` interface acting as a Registry/Factory that resolves model names to capability-specific interfaces (`LanguageModel`, `EmbeddingModel`). `AiModelService` SHALL also be the Halo backend Extension Point used by consumer plugins to discover the AI Foundation service implementation.

#### Scenario: Service registry contract
- **WHEN** a consumer plugin declares `compileOnly 'run.halo.aifoundation:api:x.x.x'`
- **THEN** it SHALL have access to `AiModelService`, `LanguageModel`, `EmbeddingModel`, and all wrapper data types without adding Spring AI dependencies

#### Scenario: Extension Point marker contract
- **WHEN** a consumer plugin compiles against `AiModelService`
- **THEN** `AiModelService` SHALL be assignable to `org.pf4j.ExtensionPoint`

#### Scenario: ExtensionGetter service lookup
- **WHEN** a consumer plugin has a runtime dependency on the `ai-foundation` plugin
- **AND** the `ai-foundation` plugin is started and enabled
- **AND** the consumer calls `ExtensionGetter.getEnabledExtension(AiModelService.class)`
- **THEN** the system SHALL return the AI Foundation `AiModelService` implementation

#### Scenario: No static service locator required
- **WHEN** a consumer plugin needs to resolve a language or embedding model
- **THEN** the consumer SHALL obtain `AiModelService` through Halo's Extension Point lookup
- **AND** the public API SHALL NOT require `AiServices.getModelService()`

## ADDED Requirements

### Requirement: AiModelService Extension Point resources

The plugin SHALL declare Halo Extension Point metadata for the public AI model service and its built-in implementation.

#### Scenario: Extension Point definition exists
- **WHEN** the plugin resources are loaded
- **THEN** the system SHALL provide an `ExtensionPointDefinition` named `ai-model-service`
- **AND** the definition SHALL reference `run.halo.aifoundation.AiModelService`
- **AND** the definition type SHALL be `SINGLETON`

#### Scenario: Extension implementation definition exists
- **WHEN** the plugin resources are loaded
- **THEN** the system SHALL provide an `ExtensionDefinition` named `ai-foundation-ai-model-service`
- **AND** the definition SHALL reference `run.halo.aifoundation.service.AiModelServiceImpl`
- **AND** the definition SHALL point to the `ai-model-service` Extension Point

### Requirement: Consumer plugin dependency contract

Consumer plugins that require AI Foundation at runtime SHALL declare both a compile-time API dependency and a Halo plugin runtime dependency.

#### Scenario: Compile-only API dependency
- **WHEN** a consumer plugin uses AI Foundation Java types
- **THEN** its build SHALL declare the AI Foundation API module as `compileOnly`
- **AND** the consumer plugin artifact SHALL NOT bundle a second copy of the API classes

#### Scenario: Runtime plugin dependency
- **WHEN** a consumer plugin requires AI Foundation to invoke models
- **THEN** its `plugin.yaml` SHALL declare a `pluginDependencies` entry for `ai-foundation`
- **AND** Halo SHALL be able to start AI Foundation before the consumer plugin
