# language-multimodal-input Specification

## Purpose
TBD - created by archiving change add-multimodal-model-capabilities. Update Purpose after archive.
## Requirements
### Requirement: Language message media parts
The SDK SHALL support image and file parts in language model messages using Halo-owned media types.

#### Scenario: User image input
- **WHEN** a consumer sends a user message containing an image part
- **THEN** the SDK SHALL preserve the image content as model input
- **AND** the runtime SHALL validate media source, media type, media size, and model capability before provider invocation

#### Scenario: User file input
- **WHEN** a consumer sends a user message containing a file part
- **THEN** the SDK SHALL preserve the file content as model input
- **AND** the runtime SHALL validate media source, media type, media size, and model capability before provider invocation

#### Scenario: Assistant media history
- **WHEN** a saved assistant message contains a file or image part
- **THEN** the SDK SHALL allow it to be replayed as model history
- **AND** the runtime SHALL apply the same capability validation as for user media input

#### Scenario: System media is unsupported
- **WHEN** a system message contains an image or file part
- **THEN** the runtime SHALL reject that media input or skip it according to the configured conversion policy before provider invocation
- **AND** it SHALL NOT silently convert system media into user media

### Requirement: Language media capability validation
The runtime SHALL validate language model media inputs against the resolved model capability snapshot.

#### Scenario: Image input unsupported
- **WHEN** a request includes an image part
- **AND** the resolved model capability `language.imageInput` is not `true`
- **THEN** the runtime SHALL raise `UnsupportedModelCapabilityException`
- **AND** the exception SHALL identify the `language.imageInput` path

#### Scenario: File input unsupported
- **WHEN** a request includes a non-image file part
- **AND** the resolved model capability `language.fileInput` is not `true`
- **THEN** the runtime SHALL raise `UnsupportedModelCapabilityException`
- **AND** the exception SHALL identify the `language.fileInput` path

#### Scenario: Media type not covered
- **WHEN** a media part has a media type that is not covered by `language.inputMediaTypes`
- **THEN** the runtime SHALL raise `UnsupportedModelCapabilityException`
- **AND** it SHALL include expected and actual media type details

#### Scenario: Input source not supported
- **WHEN** a media part uses URL input
- **AND** the resolved model capability `language.inputSources` does not include `url`
- **THEN** the runtime SHALL raise `UnsupportedModelCapabilityException`
- **AND** it SHALL NOT download the URL as a fallback

### Requirement: Provider media mapping
The runtime SHALL map validated language media input to provider request formats without exposing provider-native types to consumer plugins.

#### Scenario: Data-backed media mapping
- **WHEN** validated media input is data-backed
- **THEN** provider implementations MAY map it through existing Spring AI media support where that preserves the media semantics
- **AND** the public SDK SHALL remain independent of Spring AI types

#### Scenario: URL-backed media mapping
- **WHEN** validated media input is URL-backed
- **THEN** provider implementations SHALL preserve native URL semantics for providers that support URL input
- **AND** they SHALL NOT rely on server-side URL download to simulate provider URL support

### Requirement: UI message file conversion
The SDK SHALL convert persisted UI message `FilePart` values into model media parts when reusing UI messages as model messages.

#### Scenario: FilePart image conversion
- **WHEN** a `UIMessage` with user or assistant role contains a `FilePart` whose media type matches `image/*`
- **THEN** conversion to model messages SHALL produce an image part backed by `DataContent`

#### Scenario: FilePart file conversion
- **WHEN** a `UIMessage` with user or assistant role contains a `FilePart` whose media type is known and does not match `image/*`
- **THEN** conversion to model messages SHALL produce a file part backed by `DataContent`

#### Scenario: FilePart missing media type
- **WHEN** a `FilePart` does not provide media type and it cannot be parsed from a data URL
- **THEN** conversion SHALL follow the configured unsupported-part policy
- **AND** it SHALL NOT infer media type from a regular URL extension

#### Scenario: Source parts remain references
- **WHEN** a `UIMessage` contains source URL or source document parts
- **THEN** conversion to model messages SHALL NOT automatically convert those source parts into file inputs

