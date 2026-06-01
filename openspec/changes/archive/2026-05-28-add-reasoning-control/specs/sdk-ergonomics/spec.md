## ADDED Requirements

### Requirement: Typed reasoning setting construction
The public Java SDK SHALL provide typed helpers for configuring request-scoped reasoning behavior without raw provider option maps.

#### Scenario: Caller disables reasoning with IDE guidance
- **WHEN** a plugin author builds a text generation request
- **THEN** the author SHALL be able to call an SDK helper for disabled reasoning rather than writing provider-native keys such as `"thinking"`

#### Scenario: Caller sets reasoning effort with IDE guidance
- **WHEN** a plugin author builds a text generation request for a reasoning-capable model
- **THEN** the author SHALL be able to select a documented reasoning effort enum value from the SDK

#### Scenario: Reasoning JavaDoc explains provider support
- **WHEN** a plugin author opens the reasoning setting type in an IDE
- **THEN** JavaDoc SHALL explain provider default behavior, enabled mode, disabled mode, effort levels, unsupported provider behavior, and provider option conflict behavior

### Requirement: Reasoning examples prefer typed SDK APIs
Developer documentation SHALL demonstrate typed reasoning settings before provider-specific raw options.

#### Scenario: Documentation shows fast response path
- **WHEN** documentation shows a latency-sensitive generation request
- **THEN** the example SHALL use the typed SDK helper for disabling reasoning

#### Scenario: Documentation keeps raw options as escape hatch
- **WHEN** documentation mentions provider-native reasoning options
- **THEN** it SHALL label raw `providerOptions` as an advanced escape hatch
- **AND** it SHALL state that raw provider-native reasoning options must not be combined with typed reasoning settings
