## ADDED Requirements

### Requirement: Documentation Covers Stabilized UI Message Runtime
Consumer documentation SHALL explain the stabilized UI message runtime in `dev/ui-message-stream.md`.

#### Scenario: UI message guide is the detailed entrypoint
- **WHEN** a plugin author needs UI message stream or frontend runtime guidance
- **THEN** `dev/ui-message-stream.md` SHALL be the detailed documentation entrypoint
- **AND** `dev/dev.md` SHALL NOT duplicate the detailed UI message runtime guide

#### Scenario: Dynamic data parts are documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide SHALL explain dynamic `data-*` part naming, required ids, persistent data updates, transient data events, and frontend `onData`

#### Scenario: Dynamic tool lifecycle is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide SHALL explain dynamic `tool-*` parts, lifecycle states, `onToolCall`, `addToolOutput`, `rejectToolCall`, and automatic continuation

#### Scenario: Deferred stream recovery boundary is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide SHALL explain that resume/reconnect/replay is deferred from this change
- **AND** it SHALL avoid documenting placeholder recovery APIs that are not wired into the runtime

#### Scenario: Java API usage is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide SHALL show framework-neutral Java request handling, validation, conversion, stream response creation, and finish persistence

#### Scenario: Vue runtime usage is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide SHALL show `useChat`, `DefaultChatTransport`, dynamic data rendering, dynamic tool rendering, `useCompletion`, and `experimental_useObject` examples

#### Scenario: Documentation reflects real wiring
- **WHEN** the guide describes a capability
- **THEN** that capability SHALL be wired through the Java API, npm runtime, or workbench dogfood path described by the implementation
- **AND** the guide SHALL NOT describe placeholder APIs that are not used by the runtime

## MODIFIED Requirements

### Requirement: Documentation Covers UI Message Reuse
Consumer documentation SHALL explain how plugin authors validate persisted UI messages and convert them back into model messages.

#### Scenario: UI message reuse workflow is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide shows validating persisted `UIMessage` values
- **AND** converting them into `ModelMessage`
- **AND** passing the converted messages to `GenerateTextRequest`

#### Scenario: Conversion warnings are documented
- **WHEN** a plugin author reads the UI message conversion documentation
- **THEN** the guide explains that data, source, file, pending tool state, and unsupported reasoning state may be skipped by default
- **AND** the guide shows how to inspect conversion warnings

#### Scenario: Data part extension is documented
- **WHEN** a plugin author reads the UI message conversion documentation
- **THEN** the guide shows registering data validators or converters by dynamic data name

#### Scenario: Tool part extension is documented
- **WHEN** a plugin author reads the UI message conversion documentation
- **THEN** the guide shows registering tool validators or converters by dynamic tool name

#### Scenario: Reasoning conversion boundary is documented
- **WHEN** a plugin author reads the UI message conversion documentation
- **THEN** the guide explains that `ReasoningPart.text()` is UI-visible reasoning text
- **AND** it explains that provider-specific opaque reasoning state belongs in `providerMetadata`
- **AND** it states that provider-specific preservation requires an explicit converter or future provider-aware helper

### Requirement: Documentation Covers UI Message Chat Handler
Consumer documentation SHALL explain how plugin authors use the framework-neutral UI message chat handler.

#### Scenario: Chat handler workflow is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide shows resolving a `LanguageModel`
- **AND** passing persisted `UIMessage` values or `UIMessageChatRequest` to the chat handler
- **AND** returning the handler response descriptor
- **AND** saving updated messages from finish

#### Scenario: Request customizer boundary is documented
- **WHEN** a plugin author reads the chat handler documentation
- **THEN** the guide explains that request customizers can set generation options
- **AND** it explains that customizers must not set `prompt` or `messages`

#### Scenario: Runtime services boundary is documented
- **WHEN** a plugin author reads the stream recovery documentation
- **THEN** the guide explains that this change does not add runtime stream recovery services
- **AND** ordinary UI message streaming does not require a services locator
