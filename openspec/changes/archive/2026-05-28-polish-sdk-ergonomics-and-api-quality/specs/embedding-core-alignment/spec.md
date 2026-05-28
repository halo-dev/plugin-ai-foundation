## ADDED Requirements

### Requirement: Embedding Settings Are Discoverable And Documented
Embedding request settings SHALL be documented and discoverable through typed APIs where practical, including dimensions, max batch size, provider options, and request-level metadata.

#### Scenario: Caller configures dimensions
- **WHEN** a plugin author configures embedding dimensions
- **THEN** JavaDoc and examples explain provider support behavior and validation outcomes

#### Scenario: Caller configures provider options
- **WHEN** a plugin author needs provider-specific embedding options
- **THEN** the SDK exposes a documented typed helper where available or a clearly labeled escape hatch

### Requirement: Embedding Fields Are Fully Supported Or Removed
Embedding public request fields SHALL either have implemented provider mapping and tests or be removed from the SDK.

#### Scenario: Header-like field is unsupported
- **WHEN** an embedding request field cannot be honored by the current provider integration
- **THEN** the field is removed instead of remaining as a warning-only or no-op property

#### Scenario: Provider option is supported
- **WHEN** an embedding provider option remains public
- **THEN** tests verify it reaches the provider request or produces documented validation behavior
