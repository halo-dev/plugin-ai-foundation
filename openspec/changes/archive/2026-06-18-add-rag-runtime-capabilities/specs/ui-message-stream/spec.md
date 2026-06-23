## ADDED Requirements

### Requirement: UI message chat prepares requests asynchronously
UI message chat handling SHALL support asynchronous request preparation before model invocation.

#### Scenario: Async prepare customizes request
- **WHEN** a caller configures an async prepare hook
- **THEN** the hook can attach request middleware or update generation options before `LanguageModel.streamText` is called

### Requirement: UI message stream maps source references
UI message stream mapping SHALL preserve source references emitted by model streams or RAG middleware.

#### Scenario: Source chunk becomes persisted source
- **WHEN** a stream emits a source reference chunk
- **THEN** the UI message reducer persists a corresponding source part in the assistant message

#### Scenario: RAG data validates by name
- **WHEN** a stream emits standard RAG custom data
- **THEN** UI message validation and conversion can recognize the standard data names without requiring every consumer to invent a schema
