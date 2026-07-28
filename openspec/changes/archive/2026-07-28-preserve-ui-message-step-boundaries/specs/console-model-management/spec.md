## ADDED Requirements

### Requirement: Console configures reasoning-history support
The Console SHALL let super administrators configure a language model's reasoning-history capability as inherited, supported, or unsupported.

#### Scenario: New model inherits provider behavior
- **WHEN** an administrator leaves reasoning-history support at “继承供应商”
- **THEN** the saved model SHALL keep `capabilities.language.reasoningHistory` unknown
- **AND** runtime behavior SHALL use the provider default

#### Scenario: Administrator enables model support
- **WHEN** an administrator selects “支持” for reasoning-history support
- **THEN** the saved model SHALL set `capabilities.language.reasoningHistory = true`

#### Scenario: Administrator disables model support
- **WHEN** an administrator selects “不支持” for reasoning-history support
- **THEN** the saved model SHALL set `capabilities.language.reasoningHistory = false`

#### Scenario: Existing value is editable
- **WHEN** an administrator edits a language model with an explicit reasoning-history value
- **THEN** the advanced capability editor SHALL display the corresponding tri-state selection
