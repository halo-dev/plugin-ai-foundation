## ADDED Requirements

### Requirement: Structured streaming projections preserve text stream semantics
The system SHALL keep structured output as generated text in `fullStream` and `textStream` while exposing parsed structured values through output projections.

#### Scenario: Object output streams JSON text
- **WHEN** a request uses object output and the model streams JSON text
- **THEN** `textStream()` MUST emit the JSON text deltas and `output()` MUST resolve the parsed validated object after completion

#### Scenario: Choice output streams answer text
- **WHEN** a request uses choice output
- **THEN** `textStream()` MUST emit the selected choice text and `output()` MUST resolve only if the final value is one of the allowed choices

### Requirement: Partial output stream emits unvalidated partial values
The system SHALL expose best-effort partial structured output values without treating them as final schema validation success.

#### Scenario: Partial object is incomplete
- **WHEN** a streamed partial object is missing required final schema fields
- **THEN** `partialOutputStream()` MAY emit that partial value and MUST NOT mark final validation as successful

#### Scenario: Final object is invalid
- **WHEN** the complete structured output fails schema validation
- **THEN** `output()` and final result generation MUST fail with structured validation error details

### Requirement: Element stream emits completed validated array elements
The system SHALL emit array elements from `elementStream()` only after each element is complete and validates against the element schema.

#### Scenario: Completed element validates
- **WHEN** an array output stream completes an element that matches the element schema
- **THEN** `elementStream()` MUST emit that element

#### Scenario: Incomplete element does not emit
- **WHEN** an array output stream contains an incomplete current element
- **THEN** `elementStream()` MUST NOT emit that incomplete element

#### Scenario: Invalid completed element fails
- **WHEN** an array output stream completes an element that violates the element schema
- **THEN** `elementStream()` MUST fail with structured validation error details
