## ADDED Requirements

### Requirement: Documentation reflects Spring AI RC1 caller-visible behavior
Consumer documentation SHALL remain focused on public SDK workflows while describing any caller-visible behavior changes or caveats introduced by the Spring AI 2.0.0-RC1 migration.

#### Scenario: No Spring AI migration internals in consumer guide
- **WHEN** a plugin author reads `dev/dev.md`
- **THEN** the guide SHALL NOT require understanding Spring AI RC1 model builders, `OpenAIClient`, provider adapter internals, or removed Spring AI M2 APIs

#### Scenario: Tool strict caveat is documented when needed
- **WHEN** the RC1 migration cannot preserve provider-native strict tool schema behavior for every provider that previously claimed support
- **THEN** the consumer guide SHALL describe the affected provider behavior in caller-visible terms
- **AND** the guide SHALL state that local input validation still runs

#### Scenario: Provider option caveats are documented
- **WHEN** the RC1 migration changes whether a provider can apply request-scoped headers, structured output native mode, tool choice modes, or embedding provider options
- **THEN** the consumer guide SHALL document the caller-visible supported behavior and warning semantics

#### Scenario: Documentation validation covers changed examples
- **WHEN** documentation validation runs after the migration
- **THEN** it SHALL fail on stale public SDK examples, missing required sections, or references that imply consumers must use Spring AI classes
