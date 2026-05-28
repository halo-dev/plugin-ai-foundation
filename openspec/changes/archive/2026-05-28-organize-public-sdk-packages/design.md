## Context

The `api` module is the Java SDK consumed by other Halo plugins. It currently exposes all public SDK types from `run.halo.aifoundation`, including services, chat requests/results, model messages, content parts, stream parts, schema helpers, tools, embedding types, lifecycle events, options, and exceptions. This flat shape made early development fast, but it now weakens IDE discoverability and makes unrelated API surfaces appear coupled.

The previous change improved typed construction, JavaDoc, and implementation closure. This change finishes the public SDK organization work that was intentionally deferred: moving public types into coherent packages and adding quality gates to catch future half-migrations.

## Goals / Non-Goals

**Goals:**

- Move public SDK classes into cohesive Java packages that match the documented SDK mental model.
- Keep `run.halo.aifoundation` focused on top-level service access and a minimal entry surface.
- Update every internal call site, test, and documentation example to use the new packages.
- Add `package-info.java` documentation for SDK packages where it helps IDE browsing.
- Add a lightweight static quality check for public SDK package layout without introducing formatting or lint tooling.
- Keep the final build reproducible through `./gradlew build` and OpenSpec validation.

**Non-Goals:**

- No compatibility aliases, deprecated forwarding classes, or duplicate old-package types.
- No new provider behavior, generation behavior, embedding behavior, or UI redesign.
- No Spring AI types in the public API.
- No role/permission redesign.

## Decisions

### Decision: Use Responsibility-Based SDK Packages

The target public packages are:

- `run.halo.aifoundation`: top-level service locator/entry types only.
- `run.halo.aifoundation.chat`: text generation request/result/service types, finish reasons, stop conditions, timeouts, usage, and step controls.
- `run.halo.aifoundation.message`: model messages, roles, and message content parts.
- `run.halo.aifoundation.part`: generation content parts, reasoning parts, stream parts, and part kinds.
- `run.halo.aifoundation.schema`: JSON schema helpers and class-derived schema support.
- `run.halo.aifoundation.tool`: tool definitions, choices, calls, results, executors, and execution context.
- `run.halo.aifoundation.embedding`: embedding service, request/response, usage, warnings, lifecycle, utilities, and metadata.
- `run.halo.aifoundation.control`: shared request-control primitives used by multiple model APIs, such as cancellation.
- `run.halo.aifoundation.lifecycle`: generation lifecycle callbacks and event types.
- `run.halo.aifoundation.options`: provider option helpers and namespace types.
- `run.halo.aifoundation.exception`: public AI Foundation exceptions.
- `run.halo.aifoundation.model`: provider/model info value types that are not Halo Extension resources.

Alternative considered: keep all public classes in the root package and rely on JavaDoc. That keeps imports stable but does not fix IDE browsing or future API sprawl.

### Decision: Move Types Directly Without Compatibility Aliases

Because the plugin is unreleased, source packages should be changed directly and all repo call sites updated in the same change. Old-package compatibility wrappers would immediately create two public import paths for the same concept and make the SDK less standard.

Alternative considered: add deprecated aliases for one cycle. This would reduce consumer migration friction, but current consumers are internal/development-time and the user explicitly prefers clean API over compatibility code.

### Decision: Keep Console/OpenAPI DTO Shape Separate From SDK Package Names

The package move is a Java source organization change. It must not alter console REST paths or generated frontend API models unless backend endpoint DTOs actually changed. If endpoint serialization uses SDK types, the implementation must verify generated OpenAPI output remains acceptable or regenerate clients when necessary.

Alternative considered: separate all console DTOs from SDK DTOs in the same change. That may be a good future cleanup, but it expands scope beyond public SDK organization.

### Decision: Add Lightweight Static Quality Gates First

The change should add a practical check that runs in normal Gradle validation without adding Checkstyle or formatting tooling. The initial rule should target the contract-level regression of public SDK types remaining in the wrong package.

Alternative considered: add Checkstyle, formatter checks, or a heavier analysis stack immediately. That risks baseline noise and tool configuration churn. A smaller custom Gradle gate that reliably runs in `./gradlew build` is a better first step.

## Risks / Trade-offs

- [Risk] Package moves touch many files and can obscure semantic changes. → Mitigation: keep behavior changes out of scope, use mechanical moves, and validate with full build/tests.
- [Risk] Static analysis may report baseline issues unrelated to this change. → Mitigation: start with narrow rules and either fix relevant issues or document any unavoidable exclusions.
- [Risk] Public docs/examples may miss imports after package moves. → Mitigation: update `dev/dev.md`, JavaDoc snippets, and tests together.
- [Risk] OpenAPI generation could change if endpoint DTO package names leak into schemas. → Mitigation: run backend build and regenerate API client only if endpoint schema output changes.

## Migration Plan

1. Add package layout and move public `api` source files by responsibility.
2. Update all imports in `app`, `api` tests, `app` tests, and docs.
3. Add package-level JavaDoc and lightweight static quality tasks.
4. Run `./gradlew compileJava`, `./gradlew test`, `./gradlew build`, and `openspec validate organize-public-sdk-packages --strict`.
5. If OpenAPI output changes, run the generated client workflow and include generated updates.

Rollback strategy is a normal git revert of this focused package/quality change.

## Open Questions

- Whether future changes should add a broader static analysis tool after the current SDK package layout is stable.
- Whether `AiServices` should remain in the root package as the only static service locator, or move under a service package after Halo extension access is revisited.
