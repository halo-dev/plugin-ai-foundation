# media-content Specification

## Purpose
TBD - created by archiving change add-multimodal-model-capabilities. Update Purpose after archive.
## Requirements
### Requirement: Shared media content input
The SDK SHALL provide a shared `DataContent` value object for caller-provided media content.

#### Scenario: URL content input
- **WHEN** a consumer plugin creates media content from a URL
- **THEN** the SDK SHALL represent the URL as a distinct `url` source
- **AND** it SHALL NOT download the URL
- **AND** it SHALL NOT infer `mediaType` from the URL path or extension

#### Scenario: Data content input
- **WHEN** a consumer plugin creates media content from base64 data
- **THEN** the SDK SHALL require a non-blank `mediaType`
- **AND** it SHALL represent the content as a `data` source
- **AND** it SHALL reject values that also set `url`

#### Scenario: Data URL normalization
- **WHEN** a consumer plugin creates media content from a `data:<mediaType>;base64,...` URL
- **THEN** the SDK SHALL parse the media type
- **AND** it SHALL normalize the value to base64 `data` plus `mediaType`
- **AND** it SHALL NOT keep the full data URL as the primary data representation

### Requirement: Generated file value
The SDK SHALL provide a shared `GeneratedFile` value object for files returned by model invocations.

#### Scenario: Provider returns file data
- **WHEN** a provider returns generated file bytes or base64 content
- **THEN** the SDK SHALL expose the content through `GeneratedFile.base64`
- **AND** it SHALL expose the returned media type when available

#### Scenario: Provider returns file URL
- **WHEN** a provider returns a generated file URL
- **THEN** the SDK SHALL expose the URL through `GeneratedFile.url`
- **AND** it SHALL NOT download the URL into base64 data

### Requirement: Media validation exceptions
The SDK SHALL expose typed media exceptions for recoverable caller handling.

#### Scenario: Invalid media content
- **WHEN** media content has an invalid structure, invalid data URL, undecodable base64, conflicting source fields, or missing required media type
- **THEN** the runtime SHALL fail before invoking the provider
- **AND** it SHALL raise `InvalidMediaContentException`
- **AND** the exception SHALL include safe details such as media type, filename, message index, and part index when available

#### Scenario: Oversized media content
- **WHEN** media content exceeds a configured media part or total request resource limit
- **THEN** the runtime SHALL fail before invoking the provider
- **AND** it SHALL raise `MediaContentTooLargeException`
- **AND** the exception SHALL include `maxBytes`, `actualBytes`, and the limit scope

### Requirement: Framework media resource limits
The runtime SHALL enforce configurable media resource limits without acting as a business-level file policy.

#### Scenario: Data media size limit
- **WHEN** a request includes data-backed media
- **THEN** the runtime SHALL evaluate size using decoded bytes
- **AND** it MAY use base64 length as a fast pre-check for clearly oversized content

#### Scenario: Total request media size limit
- **WHEN** a request includes multiple data-backed media items
- **THEN** the runtime SHALL enforce a configured total decoded media size limit
- **AND** it SHALL identify total request scope when rejecting an oversized request

#### Scenario: URL structural limits
- **WHEN** a request includes URL-backed media
- **THEN** the runtime SHALL enforce configured URL count and URL length limits
- **AND** it SHALL allow only supported URL schemes such as `http` and `https`
- **AND** it SHALL NOT count remote file bytes toward decoded media size because it does not download URLs

### Requirement: No global media type allowlist
AI Foundation SHALL NOT impose a global media type allowlist for consumer plugin uploads.

#### Scenario: Consumer plugin owns business policy
- **WHEN** a consumer plugin accepts user-selected files
- **THEN** that plugin SHALL decide which media types and file sizes are allowed for its business workflow
- **AND** AI Foundation SHALL only validate media structure, resource limits, and model capability compatibility

