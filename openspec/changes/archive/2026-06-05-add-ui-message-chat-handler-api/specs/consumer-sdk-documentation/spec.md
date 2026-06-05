## ADDED Requirements

### Requirement: Documentation Covers UI Message Chat Handler
Consumer documentation SHALL explain how plugin authors use the framework-neutral UI message chat handler.

#### Scenario: Chat handler workflow is documented
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide shows resolving a `LanguageModel`
- **AND** passing persisted `UIMessage` values to the chat handler
- **AND** returning the handler response descriptor
- **AND** saving updated messages from finish

#### Scenario: Request customizer boundary is documented
- **WHEN** a plugin author reads the chat handler documentation
- **THEN** the guide explains that request customizers can set generation options
- **AND** it explains that customizers must not set `prompt` or `messages`

#### Scenario: Conversion warnings are documented for handler users
- **WHEN** a plugin author reads the chat handler documentation
- **THEN** the guide shows reading conversion warnings from the chat result

#### Scenario: Finish reactivity is documented
- **WHEN** a plugin author reads the chat handler documentation
- **THEN** the guide explains that finish is completed when the UI stream is consumed

### Requirement: Documentation Tracks Deferred UI Message Chat Work
Consumer documentation SHALL record chat-handler-adjacent work that remains intentionally deferred.

#### Scenario: Dynamic metadata callback is deferred
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide states that dynamic message metadata callbacks and metadata merge lifecycle are future work

#### Scenario: HTTP transport contract is deferred
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide states that request body parsing and frontend transport schema will be designed with the future npm helper

#### Scenario: Transport cancellation mapping is deferred
- **WHEN** a plugin author reads the UI message stream guide
- **THEN** the guide states that HTTP disconnect, frontend stop, abort chunks, and cancellation-source mapping are future work
