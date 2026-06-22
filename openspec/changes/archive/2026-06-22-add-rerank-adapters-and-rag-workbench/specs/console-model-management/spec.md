## ADDED Requirements

### Requirement: Console creates provider-backed rerank models
The console SHALL allow administrators to create reranking models for providers whose metadata declares native rerank support.

#### Scenario: Create provider-backed rerank model
- **WHEN** an administrator selects ZhiPu, DashScope, or SiliconFlow while creating an AI model
- **THEN** the console SHALL allow selecting model type `rerank`
- **AND** the model SHALL be saved with the neutral rerank adapter type

#### Scenario: Provider does not declare rerank support
- **WHEN** an administrator selects a provider whose metadata does not declare native rerank support
- **THEN** the console SHALL NOT present provider-backed rerank as a supported model type for that provider

### Requirement: Console tests provider-backed rerank models
The console SHALL support testing provider-backed rerank models through the generated rerank test API.

#### Scenario: Test native rerank model
- **WHEN** an administrator opens a configured ZhiPu, DashScope, or SiliconFlow reranking model in the workbench
- **THEN** the rerank test mode SHALL call the generated rerank endpoint
- **AND** ranked results, scores, original indexes, warnings, and provider metadata SHALL be displayed when returned
