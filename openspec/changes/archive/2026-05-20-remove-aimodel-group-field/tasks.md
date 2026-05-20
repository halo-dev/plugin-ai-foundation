## 1. Backend Schema And API

- [x] 1.1 Remove `group` from `AiModel.AiModelSpec` and any backend DTO or endpoint mapping that writes it.
- [x] 1.2 Update backend validation and model creation/update tests so `AiModel` no longer requires, persists, or normalizes `spec.group`.
- [x] 1.3 Confirm model lookup, uniqueness, default-slot eligibility, and provider reference validation continue to use `metadata.name`, `(spec.providerName, spec.modelId)`, `modelType`, and `features`.

## 2. Generated Client

- [x] 2.1 Run `./gradlew generateApiClient` after backend schema changes.
- [x] 2.2 Verify generated OpenAPI and TypeScript models no longer expose `AiModelSpec.group`.

## 3. Console UI

- [x] 3.1 Remove the model `group` field from create/edit form state and request payloads.
- [x] 3.2 Replace provider model list grouping with a flat model list that shows model type and feature tags.
- [x] 3.3 Remove `group` from client-side search keys, filters, and discovery-import defaults.
- [x] 3.4 Update UI tests and snapshots to assert no "未分组" section or group input is rendered.

## 4. Specs And Validation

- [x] 4.1 Update any existing OpenSpec artifacts that still describe `AiModel.spec.group` as required or supported.
- [x] 4.2 Run backend checks: `./gradlew compileJava`, `./gradlew compileTestJava`, and affected backend tests.
- [x] 4.3 Run frontend checks: `pnpm -C ui type-check` and `pnpm -C ui test:unit`.
- [x] 4.4 Run `openspec validate remove-aimodel-group-field --strict`.
