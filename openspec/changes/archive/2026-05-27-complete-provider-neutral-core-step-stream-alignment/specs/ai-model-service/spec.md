## ADDED Requirements

### Requirement: Language model request exposes step control
The provider-neutral language model request SHALL expose step control fields for stop conditions and step preparation without leaking provider implementation types.

#### Scenario: Java caller sets stop condition
- **WHEN** a Java caller builds a text generation request with a stop condition
- **THEN** the language model service MUST apply that condition during generation

#### Scenario: Java caller sets prepare callback
- **WHEN** a Java caller builds a text generation request with a prepare-step callback
- **THEN** the language model service MUST invoke the callback before each model step

### Requirement: Language model result exposes source and file content parts
The provider-neutral language model result SHALL be able to represent provider sources and generated files when a provider returns them.

#### Scenario: Provider returns sources
- **WHEN** a provider response includes source references
- **THEN** the generated result content MUST expose those sources through provider-neutral content parts

#### Scenario: Provider returns files
- **WHEN** a provider response includes generated files
- **THEN** the generated result content MUST expose those files through provider-neutral content parts

### Requirement: API remains independent from Spring AI
The public language model service contract SHALL NOT require callers to depend on Spring AI classes for step control, stream parts, source parts, file parts, or tool execution.

#### Scenario: Consumer plugin compiles against api module
- **WHEN** a consumer plugin depends only on the published `api` module
- **THEN** it MUST be able to build text-generation requests and read generation results without Spring AI compile dependencies
