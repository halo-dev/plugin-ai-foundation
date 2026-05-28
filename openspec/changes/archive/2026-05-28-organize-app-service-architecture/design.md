## Context

The backend implementation has already been split into several helper classes, but those classes still live in `run.halo.aifoundation.service`. That package now mixes Spring service entry points, per-model runtime implementations, request validation, mapping, streaming protocol normalization, tool execution, and structured output handling.

The public SDK has already been organized by responsibility. The `app` module needs the same level of cohesion, while preserving the current public API and provider type model. The important runtime constraint is that `AiModelServiceImpl` is referenced by a Halo `ExtensionDefinition`, so moving it requires updating resources and tests as part of the same change.

## Goals / Non-Goals

**Goals:**
- Make the `app` package layout express backend responsibilities clearly.
- Keep `service` focused on Spring service boundaries instead of implementation helper storage.
- Introduce internal interfaces for meaningful service ports that are injected or tested independently.
- Keep implementation-only collaborators package-private where practical.
- Preserve existing public SDK behavior and provider behavior.
- Add a lightweight architecture guard that prevents new service helper classes from being placed in the flat `service` package.

**Non-Goals:**
- Do not change the `api` module public contracts.
- Do not introduce compatibility aliases for old implementation package names.
- Do not add checkstyle, formatter plugins, or Gradle formatting tools.
- Do not split provider type classes into separate metadata and adapter classes.
- Do not change UI behavior.

## Decisions

### Organize implementation code by backend feature under the app package

The implementation should move toward cohesive backend feature packages:

- `run.halo.aifoundation.service`: Spring-facing service interfaces and implementations only.
- `run.halo.aifoundation.service.model`: model/provider resource resolution and default slot resolution helpers used by service entry points.
- `run.halo.aifoundation.service.language`: language model runtime implementation and generation orchestration.
- `run.halo.aifoundation.service.language.mapping`: language request, message, option, response, and tool-call mapping.
- `run.halo.aifoundation.service.language.stream`: stream result building and stream protocol normalization.
- `run.halo.aifoundation.service.language.tool`: server-side tool execution support.
- `run.halo.aifoundation.service.language.structured`: structured output parsing, validation, and stream derivation.
- `run.halo.aifoundation.service.embedding`: embedding model runtime implementation and embedding request execution helpers.

Alternative considered: move everything directly under top-level packages such as `run.halo.aifoundation.language` and `run.halo.aifoundation.embedding`. That would create split package names that overlap with public SDK packages from the `api` module, so the service subtree is clearer for implementation code.

### Use interfaces only for real Spring service boundaries

The public `AiModelService` is already the cross-plugin service contract. Inside `app`, new interfaces should be added only where they define a meaningful injected boundary, such as:

- model/provider resolution used by `AiModelServiceImpl`
- creation of per-model language model runtimes
- creation of per-model embedding model runtimes

Mapping, normalization, and value aggregation helpers should not get interfaces unless tests or independent implementations need them.

Alternative considered: add interfaces for every class whose name ends in `Service`. That increases indirection without improving architecture because most collaborators are deterministic helpers.

### Keep runtime implementations small enough to review

Moving packages alone is not enough. `LanguageModelImpl` and `EmbeddingModelImpl` should be reviewed for extractable responsibilities during implementation. Work that can be clearly isolated, such as request preparation, stream result conversion, embedding batching, retry handling, and metadata aggregation, should move into package-private collaborators.

Alternative considered: only move existing files. That would make package names prettier while leaving the main quality issue intact.

### Update Halo extension metadata with implementation package changes

If `AiModelServiceImpl` moves, `app/src/main/resources/extensions/ai-model-service-extension-point.yaml` and tests must reference the new implementation class name. This keeps extension lookup aligned with compiled code.

Alternative considered: leave `AiModelServiceImpl` in the old package as a wrapper. The project is unreleased, so compatibility wrappers are unnecessary and would preserve the old package as an attractive place for future code.

## Risks / Trade-offs

- [Risk] Moving many classes can produce broad import churn. -> Mitigation: keep behavior changes minimal, use IDE/compiler feedback, and run backend tests after the package move.
- [Risk] A moved service implementation can break Halo extension discovery. -> Mitigation: update extension YAML and add or retain tests that assert the resource class name matches `AiModelServiceImpl.class.getName()`.
- [Risk] Excessive interfaces can make the code harder to follow. -> Mitigation: add interfaces only for injected service ports and keep helper classes concrete.
- [Risk] Package layout checks can become brittle. -> Mitigation: check only high-level invariants, such as disallowing helper-like classes directly in the flat service package, not exact file counts or formatting style.

## Migration Plan

1. Add the new package structure and move current service collaborators into the closest responsibility package.
2. Introduce internal service interfaces for model resolution and runtime factories where constructor injection benefits from a boundary.
3. Update imports, component annotations, extension YAML, tests, and documentation references.
4. Add a focused architecture test or Gradle verification task for the `app` implementation package layout.
5. Run `./gradlew compileJava` and focused tests first, then `./gradlew build`.

Rollback is a normal source revert before release; no runtime data migration is involved.

## Open Questions

- None. The implementation can choose exact class names for internal interfaces while preserving the package responsibilities above.
