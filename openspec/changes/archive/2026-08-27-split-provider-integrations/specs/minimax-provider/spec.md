## ADDED Requirements

### Requirement: MiniMax provider-owned clients
The MiniMax provider SHALL own its recommended Messages, explicit Chat, and native image clients, supported parameters, errors, usage, and model-domain declarations.

#### Scenario: Invoke MiniMax with the recommended protocol
- **WHEN** a MiniMax language model is invoked
- **THEN** the runtime SHALL use MiniMax's Anthropic-compatible Messages adapter by default
- **AND** the OpenAI-compatible Chat adapter SHALL remain an explicit per-model selection

#### Scenario: Continue after interleaved thinking
- **WHEN** MiniMax returns signed thinking blocks before a tool call
- **THEN** the runtime SHALL expose normalized reasoning and tool events
- **AND** SHALL replay the complete signed thinking blocks unchanged in the continuation request

#### Scenario: Apply current language-model controls
- **WHEN** a caller configures reasoning, sampling, media, or active prompt caching
- **THEN** the selected adapter SHALL validate its documented protocol shape
- **AND** model-specific reasoning availability and values SHALL be controlled by explicit model mappings rather than model-family detection
- **AND** sampling boundaries, ignored fields, service tiers, and multimodal blocks SHALL follow the current selected protocol contract
- **AND** active cache controls SHALL be serialized using the Messages protocol contract

#### Scenario: Generate a MiniMax image
- **WHEN** a MiniMax image model is invoked
- **THEN** the runtime SHALL call the native image-generation endpoint
- **AND** SHALL validate documented dimensions, batch size, subject reference, provider options, and application-level errors

#### Scenario: Discover unsupported MiniMax media domain
- **WHEN** MiniMax documents speech, video, music, or file capabilities without a matching Halo public model contract
- **THEN** those models SHALL NOT be mislabeled as language or image-generation models
- **AND** the runtime SHALL NOT infer an undocumented inference-plane model catalog
