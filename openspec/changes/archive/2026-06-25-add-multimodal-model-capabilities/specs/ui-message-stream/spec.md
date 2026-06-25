## ADDED Requirements

### Requirement: UI message file parts convert to model media input
The SDK SHALL convert UI message file parts into model media input when callers reuse UI messages as model messages.

#### Scenario: User FilePart conversion
- **WHEN** a caller converts persisted user UI messages to model messages
- **AND** a user message contains a valid `FilePart`
- **THEN** the converter SHALL map it to an image or file model part according to media type

#### Scenario: Assistant FilePart conversion
- **WHEN** a caller converts persisted assistant UI messages to model messages
- **AND** an assistant message contains a valid `FilePart`
- **THEN** the converter SHALL map it to an image or file model part according to media type

#### Scenario: System FilePart conversion
- **WHEN** a caller converts persisted system UI messages to model messages
- **AND** a system message contains a `FilePart`
- **THEN** the converter SHALL treat the part as unsupported according to conversion options
- **AND** it SHALL NOT change the part role to user

### Requirement: Generated files can map to UI file chunks
The SDK SHALL allow generated file values to be represented in UI message streams without changing the UI message protocol.

#### Scenario: Generated file chunk
- **WHEN** a model stream or helper emits a generated file
- **THEN** the SDK SHALL be able to create a UI file chunk or file part carrying URL, media type, title or filename, data, and provider metadata when available

#### Scenario: UI file part remains protocol-shaped
- **WHEN** a caller persists or serializes a UI message file part
- **THEN** the part SHALL keep the existing UI message file fields
- **AND** it SHALL NOT require callers to embed a `GeneratedFile` object directly
