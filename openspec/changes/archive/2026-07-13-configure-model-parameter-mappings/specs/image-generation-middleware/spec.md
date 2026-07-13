## ADDED Requirements

### Requirement: Image middleware preserves mapped typed parameters
Image generation middleware request copies and helpers SHALL preserve all typed provider parameters, including `negativePrompt`, without carrying provider-native option maps.

#### Scenario: Middleware transforms unrelated fields
- **WHEN** middleware copies a request while changing an unrelated image setting
- **THEN** the copied request SHALL preserve `negativePrompt` and other typed fields

#### Scenario: Middleware supplies negative prompt
- **WHEN** middleware returns a request with `negativePrompt`
- **THEN** the provider invocation SHALL translate it through the effective administrator mapping
