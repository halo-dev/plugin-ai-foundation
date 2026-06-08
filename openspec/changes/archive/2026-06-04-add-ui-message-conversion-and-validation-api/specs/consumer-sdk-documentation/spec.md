## ADDED Requirements

### Requirement: Documentation Covers UI Message Reuse
Consumer documentation SHALL explain how plugin authors validate persisted UI messages and convert them back into model messages.

#### Scenario: UI message reuse workflow is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide shows validating persisted `UIMessage` values
- **AND** converting them into `ModelMessage`
- **AND** passing the converted messages to `GenerateTextRequest`

#### Scenario: Conversion warnings are documented
- **WHEN** a plugin author reads the UI message conversion documentation
- **THEN** the guide explains that data, source, file, approval, and unsupported reasoning state may be skipped by default
- **AND** the guide shows how to inspect conversion warnings

#### Scenario: Data part extension is documented
- **WHEN** a plugin author reads the UI message conversion documentation
- **THEN** the guide shows registering data validators or converters by data name

#### Scenario: Reasoning conversion boundary is documented
- **WHEN** a plugin author reads the UI message conversion documentation
- **THEN** the guide explains that `ReasoningPart.text()` is UI-visible reasoning text
- **AND** it explains that provider-specific opaque reasoning state belongs in `providerMetadata`
- **AND** it states that provider-specific preservation requires an explicit converter or future provider-aware helper

### Requirement: Documentation Covers AI Foundation API Classloading Contract
Consumer documentation SHALL explain how plugin authors depend on the AI Foundation API without bundling duplicate API classes.

#### Scenario: Compile-only dependency is documented
- **WHEN** a plugin author reads the dependency setup section
- **THEN** the guide uses `compileOnly 'run.halo.aifoundation:api:...'`
- **AND** the guide does not instruct plugin authors to use `implementation 'run.halo.aifoundation:api:...'`

#### Scenario: Runtime plugin dependency is documented
- **WHEN** a plugin author reads the dependency setup section
- **THEN** the guide shows `pluginDependencies.ai-foundation`
- **AND** it explains that AI Foundation provides the API classes at runtime

#### Scenario: Duplicate API classes are warned against
- **WHEN** a plugin author reads the dependency setup section
- **THEN** the guide warns that bundling the API jar into a consumer plugin can cause classloader type mismatches
