## ADDED Requirements

### Requirement: Capability-aware model options
The aggregated model options API SHALL include model capability snapshots and support capability-based filtering.

#### Scenario: Model option includes capabilities
- **WHEN** a client requests model options
- **THEN** each returned option SHALL include the effective model capabilities when available
- **AND** it SHALL include capability source information by domain when available

#### Scenario: Structured capability filter
- **WHEN** a client requests model options with a structured `requiredCapabilities` query value
- **THEN** the endpoint SHALL parse the requirement
- **AND** it SHALL return options that satisfy all requested positive capability conditions after other typed filters are applied

#### Scenario: Reject invalid capability filter
- **WHEN** a client sends malformed JSON, unsupported capability paths, unsupported source values, or invalid media type patterns in `requiredCapabilities`
- **THEN** the endpoint SHALL reject the request with a 400 response

### Requirement: Capability-aware availability details
The model options API SHALL explain capability mismatch without hiding all diagnostic information.

#### Scenario: Capability mismatch makes option unavailable
- **WHEN** a model does not satisfy the requested capabilities
- **THEN** the returned option MAY be marked unavailable when unavailable options are requested
- **AND** `unavailableReason` SHALL identify capability mismatch
- **AND** `unavailableDetails` SHALL include missing capability paths, expected values, and actual values when safe to expose

#### Scenario: Available-only filter
- **WHEN** a client requests only available model options
- **THEN** models that fail enabled/provider/capability availability SHALL be omitted

#### Scenario: Capability matching uses all-of semantics
- **WHEN** a requirement contains multiple capability conditions or list entries
- **THEN** a model SHALL satisfy all conditions and list entries to match

#### Scenario: Media type coverage matching
- **WHEN** a required media type pattern is compared with model-supported media type patterns
- **THEN** the model SHALL match only if its supported range covers the required range
- **AND** supported `image/*` SHALL cover required `image/png`
- **AND** supported `image/png` SHALL NOT cover required `image/*`
