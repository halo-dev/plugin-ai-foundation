## ADDED Requirements

### Requirement: Reasoning And Metadata Documentation
Consumer documentation SHALL explain how callers read reasoning output and provider metadata.

#### Scenario: Reasoning output is documented
- **WHEN** a plugin author reads the text generation documentation
- **THEN** the guide SHALL show how to read `reasoningText` and reasoning parts
- **AND** it SHALL explain that answer text excludes extracted reasoning content

#### Scenario: Provider metadata layering is documented
- **WHEN** a plugin author reads the result metadata documentation
- **THEN** the guide SHALL distinguish response metadata from provider-specific metadata
- **AND** it SHALL direct callers to typed response fields for response id and model id

#### Scenario: Raw metadata aliases are not recommended
- **WHEN** a plugin author reads reasoning documentation
- **THEN** the guide SHALL NOT instruct callers to depend on raw `reasoningContent` or `reasoning_content` metadata keys
- **AND** examples SHALL use typed SDK fields for normal reasoning access
