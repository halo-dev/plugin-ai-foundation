## ADDED Requirements

### Requirement: Console configures Provider parameter mappings
The Console SHALL provide a Chinese advanced settings area for super administrators to inspect and override Provider parameter mappings.

#### Scenario: Provider form shows built-in default
- **WHEN** an administrator edits a Provider
- **THEN** each supported parameter SHALL show its built-in mapping and effective source
- **AND** the administrator SHALL be able to inherit, select a compatible template, or mark the parameter unsupported

### Requirement: Console configures Model mapping exceptions
The Console SHALL let super administrators configure per-parameter Model inheritance, overrides, and unsupported states.

#### Scenario: New or discovered Model defaults to inherit
- **WHEN** a Model is created manually or from discovery
- **THEN** its parameters SHALL inherit Provider mappings unless the administrator explicitly changes them

#### Scenario: Model form configures fixed reasoning intents
- **WHEN** an administrator chooses a custom reasoning mapping
- **THEN** the form SHALL always display enabled, disabled, low, medium, and high rows
- **AND** each row SHALL independently configure whether it is supported, its request field, scalar value type, and request value
- **AND** backend validation SHALL remain authoritative

#### Scenario: Model type limits mapping fields
- **WHEN** an administrator edits a Model
- **THEN** the form SHALL show only parameters and templates compatible with that Model type and Provider adapter

### Requirement: Mapping controls use Halo-native conditional inputs
Provider and Model mapping controls SHALL use Halo components and FormKit inputs and SHALL expose only fields relevant to the selected mapping choice.

#### Scenario: Administrator returns to inherited mode
- **WHEN** an administrator changes a parameter from a registered or custom mapping to inherited/default mode
- **THEN** custom and template-specific inputs SHALL disappear
- **AND** stale template configuration SHALL be removed from the submitted selection

#### Scenario: Administrator selects a registered mapping
- **WHEN** an administrator opens a parameter mapping choice
- **THEN** one FormKit select SHALL offer inheritance/default, compatible registered mappings, unsupported, and custom-field choices
- **AND** selecting a registered mapping SHALL persist its template without showing a duplicate native-field input

#### Scenario: Administrator edits a custom native field
- **WHEN** an administrator selects the custom-field choice
- **THEN** the form SHALL show one FormKit text input initialized from the current effective template field
- **AND** the administrator MAY submit a constrained compatible field/path override while retaining that template's placement and value conversion

#### Scenario: Model form separates advanced domains
- **WHEN** a language or image Model exposes mappings and capability details
- **THEN** the form SHALL render parameter mappings and Model capabilities as sibling panels with distinct titles
- **AND** neither panel SHALL visually or structurally contain the other

#### Scenario: Collapsible panel is closed
- **WHEN** an administrator closes a parameter-mapping or capability panel
- **THEN** its content SHALL occupy no layout height
- **AND** the Console SHALL NOT render an empty card body below the trigger

### Requirement: Console prioritizes common parameter mappings
Provider and Model mapping panels SHALL keep the complete compatible mapping catalog available while initially presenting a concise common subset.

#### Scenario: Administrator opens parameter mappings
- **WHEN** an administrator first expands a Provider or Model parameter-mapping panel
- **THEN** language mappings SHALL initially show maximum output tokens, temperature, Top P, and reasoning
- **AND** image mappings SHALL initially show image count, size, and aspect ratio
- **AND** embedding dimensions and reranking result count SHALL remain directly visible

#### Scenario: Administrator requests additional mappings
- **WHEN** compatible mappings exist outside the common subset
- **THEN** a Halo-native action SHALL let the administrator show or hide the additional mappings without removing their stored configuration

#### Scenario: Additional mapping has an explicit selection
- **WHEN** an additional parameter contains an explicit Provider or Model selection
- **THEN** that parameter SHALL remain visible even while additional inherited/default mappings are collapsed

### Requirement: Inline FormKit mapping fields stay aligned
Mapping editors SHALL preserve Halo and FormKit-native field rendering while aligning multiple inputs placed on the same row.

#### Scenario: One inline field includes help text
- **WHEN** one FormKit field in an inline mapping row renders help text and its sibling fields do not
- **THEN** all field labels and controls SHALL remain top-aligned
- **AND** the help text SHALL remain below its owning control without shifting sibling controls
