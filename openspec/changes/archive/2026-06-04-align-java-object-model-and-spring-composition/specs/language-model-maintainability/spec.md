## ADDED Requirements

### Requirement: Language Runtime Composition Is Spring-Managed
The language model runtime SHALL be assembled through explicit Spring-managed composition boundaries instead of hiding behavior collaborator creation inside `LanguageModelImpl`.

#### Scenario: Language model factory owns runtime composition
- **WHEN** a resolved provider chat model is wrapped as a `LanguageModel`
- **THEN** the language model factory SHALL assemble or request a composition object containing request validation, message mapping, chat options mapping, response mapping, reasoning extraction, structured output handling, tool execution, tool-step coordination, approval resolution, and history assembly collaborators
- **AND** `LanguageModelImpl` SHALL receive those collaborators through constructor parameters or a named composition object
- **AND** `LanguageModelImpl` SHALL remain focused on orchestration of generation and streaming flows

#### Scenario: Provider-specific language options remain visible
- **WHEN** provider-specific language behavior such as reasoning support, tool-calling options, structured-output options, or request headers is configured
- **THEN** the selected `LanguageModelProviderOptions` and related strategy choices SHALL be visible at the factory/composition boundary
- **AND** provider-specific behavior SHALL NOT be inferred from scattered constructor calls inside `LanguageModelImpl`

#### Scenario: Language composition supports decoration
- **WHEN** maintainers add metrics, lifecycle guarding, logging, timeout policy, or other cross-cutting behavior to language execution
- **THEN** the design SHALL allow that behavior through explicit collaborators, decorators, or justified Spring advice
- **AND** the behavior SHALL NOT require editing unrelated mapping, validation, tool, or structured-output algorithms

### Requirement: Language Runtime Tests Cover Composition
Language runtime refactors SHALL include tests that protect the factory and collaborator graph.

#### Scenario: Factory composition is tested
- **WHEN** language runtime collaborators move out of `LanguageModelImpl`
- **THEN** tests SHALL verify that the factory passes provider type, provider options, and Spring AI chat model dependencies into the runtime composition correctly
- **AND** existing generation, streaming, reasoning, structured-output, tool, approval, repair, timeout, cancellation, and lifecycle tests SHALL continue to pass
