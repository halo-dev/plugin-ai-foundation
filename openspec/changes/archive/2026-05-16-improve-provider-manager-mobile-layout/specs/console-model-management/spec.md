## MODIFIED Requirements

### Requirement: Provider workspace layout
The Console UI SHALL use a provider-centric master-detail workspace that remains usable across desktop and mobile viewport widths.

#### Scenario: Aggregated provider workspace
- **WHEN** an admin opens the AI model configuration page on a desktop-width viewport
- **THEN** the left side SHALL display the provider list
- **AND** selecting a provider SHALL open a right-side detail workspace for that provider
- **AND** the workspace SHALL include both provider configuration and the models belonging to that provider
- **AND** switching to a different provider SHALL refresh the model list to show only models belonging to the newly selected provider

#### Scenario: Mobile provider workspace
- **WHEN** an admin opens the AI model configuration page on a mobile-width viewport
- **THEN** the provider list SHALL NOT reserve a fixed-width left column
- **AND** the provider list SHALL be displayed above the provider detail workspace
- **AND** the provider detail workspace SHALL remain visible and usable without horizontal compression
- **AND** the provider list and provider detail workspace SHALL each remain scrollable when their content exceeds the available height

#### Scenario: Compact provider detail content
- **WHEN** an admin views provider detail content on a narrow viewport
- **THEN** provider action controls SHALL wrap or stack instead of causing horizontal overflow
- **AND** provider metadata fields SHALL stack or reduce columns so labels and values remain readable
- **AND** model list content SHALL remain accessible without being squeezed by the provider list
