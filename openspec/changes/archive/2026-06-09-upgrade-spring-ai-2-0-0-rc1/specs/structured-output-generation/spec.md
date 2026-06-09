## ADDED Requirements

### Requirement: Structured output mappings support Spring AI RC1
The system SHALL map provider-neutral structured output requests to Spring AI 2.0.0-RC1-compatible provider options while preserving final parsing and validation behavior.

#### Scenario: OpenAI-compatible object output maps to RC1 response format
- **WHEN** a request uses object structured output for an OpenAI-compatible provider
- **THEN** the provider adapter SHALL map the request to the RC1 response format API when provider-native structured output is supported
- **AND** final output parsing and schema validation SHALL still use Halo-owned structured output handling

#### Scenario: JSON schema output maps to RC1 response format
- **WHEN** a request uses strict object output with a JSON Schema
- **AND** the selected provider supports provider-native JSON schema response format
- **THEN** the provider request SHALL include the schema using the RC1 provider-supported response format
- **AND** local final validation SHALL still run before completing the public result

#### Scenario: Unsupported native structured output downgrades safely
- **WHEN** the selected RC1 provider adapter cannot represent the requested native structured output mode
- **THEN** the generation SHALL continue only if prompt guidance plus local validation can preserve the public structured output contract
- **AND** the result or stream step SHALL include a stable warning for the downgrade

#### Scenario: Structured output with tools remains final-answer scoped
- **WHEN** a structured output request also includes tools
- **THEN** tool-call steps SHALL continue using the Halo tool loop
- **AND** structured output parsing SHALL apply to the final answer step as before the Spring AI upgrade
