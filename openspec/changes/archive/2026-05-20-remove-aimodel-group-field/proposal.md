## Why

`AiModel.spec.group` is a free-form display field left over from the early model metadata design. After the capability-profile redesign, model organization should be driven by typed `modelType` and controlled `features`; keeping a separate group field creates an ambiguous second classification axis and makes future changes likely to reintroduce "default group" behavior by accident.

This plugin is still unreleased, so the schema can be simplified now without carrying compatibility code.

## What Changes

- **BREAKING** Remove `AiModel.spec.group` from the model resource contract.
- Remove model group input from the Console model create/edit form.
- Remove group-based collapsible sections from provider-scoped model lists.
- Remove group from Console model search/filter behavior and discovery-import defaults.
- Keep `modelType` and `features` as the supported model classification fields.

## Non-Goals

- This change does not add model folders, favorites, tags, or custom taxonomy support.
- This change does not alter provider grouping or the plugin Setting/ConfigMap `defaults` group used for default model slots.
- This change does not change runtime model lookup by `AiModel.metadata.name`.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `ai-provider-config`: `AiModel` resources no longer include or require `spec.group`.
- `console-model-management`: The Console no longer edits, displays, groups, filters, or batch-imports models by `spec.group`.

## Impact

- Backend `AiModel` schema and validation.
- OpenAPI schema and generated TypeScript client.
- Console model create/edit forms, provider model list rendering, search/filter metadata, and discovery-import flow.
- Backend and frontend tests that currently assert group persistence or grouped display.
