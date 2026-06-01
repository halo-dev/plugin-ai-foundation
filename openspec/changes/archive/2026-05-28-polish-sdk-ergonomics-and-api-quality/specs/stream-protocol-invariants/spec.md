## ADDED Requirements

### Requirement: Stream Parts Are Type-Safe And Protocol-Compatible
Stream part APIs SHALL use Java-oriented abstractions or factories for each stream part kind while preserving the provider-neutral lifecycle protocol.

#### Scenario: Text stream lifecycle
- **WHEN** a provider emits text deltas
- **THEN** the SDK emits typed text-start, text-delta, and text-end parts without nesting unrelated part kinds inside the text lifecycle

#### Scenario: Reasoning stream lifecycle
- **WHEN** a provider emits reasoning deltas
- **THEN** the SDK emits typed reasoning-start, reasoning-delta, and reasoning-end parts as an independent lifecycle from text

### Requirement: Invalid Stream Part Shapes Are Prevented
The SDK SHALL prevent stream parts from carrying fields that are invalid for their part kind.

#### Scenario: Caller creates a stream part
- **WHEN** code constructs a stream part through public APIs
- **THEN** the construction path exposes only fields valid for that specific part kind

#### Scenario: Provider mapping emits invalid data
- **WHEN** provider mapping attempts to create a stream part with an invalid shape
- **THEN** stream validation fails before the invalid part is exposed to SDK callers
