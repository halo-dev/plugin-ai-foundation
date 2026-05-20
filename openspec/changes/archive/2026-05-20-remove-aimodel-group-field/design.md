## Context

`AiModel.spec.group` was introduced as a lightweight Console organization hint before the model metadata design was settled. The capability-profile redesign now gives the model resource two typed classification fields: `modelType` for primary purpose and `features` for optional behavior.

Keeping `group` beside those fields makes the model schema harder to explain. It also encourages future implementation work to add default groups, shared import defaults, or group filters that duplicate the typed model profile.

The plugin is unreleased, so the field can be removed without compatibility shims or data migration support.

## Goals / Non-Goals

**Goals:**

- Remove the free-form `AiModel.spec.group` field from the backend schema and API contract.
- Make model organization in the Console depend on typed model profile fields, not arbitrary grouping text.
- Keep the decision explicit in OpenSpec so future model-management work does not reintroduce default model groups.
- Regenerate the API client after the field is removed.

**Non-Goals:**

- Add a replacement tagging, folder, category, or custom taxonomy system.
- Change `AiProvider` grouping, provider type grouping, or the Setting/ConfigMap `defaults` group for default model slots.
- Change model lookup semantics; public Java APIs still resolve models by `AiModel.metadata.name`.
- Preserve old persisted `spec.group` values or add compatibility cleanup code.

## Decisions

### Decision 1: Remove `spec.group` instead of hiding it

`group` should be removed from the resource contract, not merely hidden from the Console. Hiding it would leave a public field that plugin authors and future code could still treat as meaningful.

Alternative considered: keep `group` as an advanced metadata field. This keeps the ambiguity alive and weakens the model profile boundary.

### Decision 2: Use `modelType` and `features` for Console context

The model list should show and filter by controlled model metadata such as `modelType` and `features`. Those fields are validated, useful to runtime selection, and directly aligned with default model slot eligibility.

Alternative considered: group models by a derived label such as language/embedding/rerank. That can be useful visually later, but it should be derived from `modelType` rather than persisted as user-authored `group`.

### Decision 3: Do not introduce migration or compatibility code

The plugin is still unreleased, so there is no need to read old `spec.group` values, copy them into labels, or preserve them during updates. Tests and generated schemas should assert the new clean shape.

Alternative considered: tolerate unknown `spec.group` in incoming resources. Halo Extension storage may still physically contain unknown fields, but AI Foundation code should not model, validate, write, or depend on them.

## Risks / Trade-offs

- [Risk] Admins lose a lightweight visual grouping tool. -> Mitigation: show model type and feature tags prominently; add a proper taxonomy later only if there is a clear product need.
- [Risk] Provider model lists become long without collapsible groups. -> Mitigation: keep keyword and typed filters as the primary navigation path.
- [Risk] Future work confuses ConfigMap `defaults` with model groups. -> Mitigation: this change explicitly limits `defaults` to plugin settings and forbids reintroducing `AiModel.spec.group`.

## Migration Plan

1. Remove backend field and any server-side reads/writes of `AiModel.spec.group`.
2. Regenerate OpenAPI and TypeScript API client artifacts.
3. Remove Console form controls, grouped rendering, group search keys, and discovery-import defaults.
4. Update tests and OpenSpec specs to assert no first-class model group field.

Rollback would require re-adding the schema field, generated API shape, UI controls, and grouped list behavior. No data migration is planned.

## Open Questions

- None.
