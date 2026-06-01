## ADDED Requirements

### Requirement: Text Generation Metadata Layering
Text generation results SHALL separate normalized SDK metadata from provider-specific metadata.

#### Scenario: Provider metadata excludes normalized response fields
- **WHEN** text generation completes successfully
- **THEN** `GenerateTextResult.providerMetadata` SHALL NOT include normalized top-level `providerType`, `id`, or `model` entries
- **AND** response id and model id SHALL be exposed through `GenerationResponseMetadata`
- **AND** provider type SHALL be exposed only through SDK diagnostic metadata when available

#### Scenario: Step provider metadata follows the same contract
- **WHEN** a generation step completes
- **THEN** `GenerationStep.providerMetadata` SHALL follow the same provider-specific metadata contract as the top-level result
- **AND** step response id and model id SHALL be exposed through step response metadata when available

#### Scenario: Provider-specific metadata remains available
- **WHEN** a provider returns provider-specific metadata that is safe to expose
- **THEN** the generation result MAY include that metadata in `providerMetadata`
- **AND** SDK-generated provider metadata SHALL be namespaced by provider type

#### Scenario: Request metadata carries invocation diagnostics
- **WHEN** text generation records SDK invocation diagnostics such as provider type
- **THEN** those diagnostics SHALL be exposed through request or response metadata
- **AND** they SHALL NOT be mixed into provider-specific metadata
