## Context

The previous maintainability change reduced oversized helpers and long positional parameter flows, but a deeper Java architecture issue remains: several runtime services still assemble their own behavior graph internally. The clearest example is `DefaultLanguageModelFactory` constructing `LanguageModelImpl`, while the `LanguageModelImpl` constructor creates validators, mappers, chat option builders, response mappers, reasoning extractors, structured output handlers, tool executors, tool coordinators, and approval resolvers with `new`.

The Java scan found three categories:

- Runtime composition hotspots: `LanguageModelImpl`, `EmbeddingModelImpl`, `DefaultLanguageModelFactory`, `DefaultEmbeddingModelFactory`, and console endpoints.
- Acceptable direct construction: DTOs, records, exceptions, builders, Spring AI request objects, WebClient/RestClient builders, and small immutable value objects.
- Provider architecture hotspots: provider classes repeatedly construct provider options and Spring AI adapters, but the project architecture intentionally keeps one `@Component` per provider type.

The next refactor should move runtime behavior composition toward Spring-managed factories and strategy interfaces without breaking the provider type architecture or exposing Spring AI types through the public API.

## Goals / Non-Goals

**Goals:**

- Make Java domain roles explicit: factories create runtime models, collaborators own mapping/validation/tool/structured-output responsibilities, and services orchestrate rather than self-assemble.
- Move behavior-bearing collaborators behind Spring-managed components, factories, or strategy interfaces when they are replaceable, observable, or cross-cutting.
- Define direct-construction rules so `new` is not treated as automatically wrong.
- Preserve all existing language generation, embedding, provider, endpoint, caching, timeout, cancellation, lifecycle, tool, approval, repair, and structured output behavior.
- Keep the change backend-only unless generated clients change unexpectedly.

**Non-Goals:**

- Do not redesign provider discovery or split one provider type into several Spring beans.
- Do not convert DTOs, exceptions, records, or pure value builders into Spring beans.
- Do not introduce AOP where a normal collaborator or Reactor operator is clearer.
- Do not change public SDK method signatures or expose Spring AI implementation types.
- Do not add compatibility layers; the plugin is unreleased.

## Decisions

### Decision 1: Introduce runtime composition factories instead of making every collaborator a singleton bean

`LanguageModelImpl` and `EmbeddingModelImpl` are per-model runtime adapters because each instance depends on provider resource/model resolution and Spring AI model instances. Their collaborators also need provider-specific options. The refactor should introduce Spring-managed factory components such as `LanguageModelRuntimeFactory` and `EmbeddingModelRuntimeFactory` that assemble immutable per-model collaborator graphs.

Alternatives considered:

- Make `LanguageModelImpl` a singleton Spring bean. Rejected because it depends on per-model `ChatModel`, provider type, and provider options.
- Keep constructor `new` calls. Rejected because it keeps object graph ownership hidden inside the facade.
- Use Spring prototype scope for every tiny helper. Rejected because this would add container ceremony without improving domain clarity.

### Decision 2: Extract behavior-bearing collaborator interfaces only at extension seams

Interfaces should represent meaningful variation, such as request validation policy, provider option construction, runtime model assembly, lifecycle interception, or tool execution orchestration. Classes with one stable algorithm may remain concrete if they are injected through a factory and covered by tests.

Alternatives considered:

- Add interfaces for every mapper/helper. Rejected because it would create a faux object model with no real polymorphism.
- Leave all helpers as package-private classes. Accepted only for pure algorithm helpers that are not useful extension seams.

### Decision 3: Use AOP selectively for cross-cutting concerns

Spring AOP may be introduced for concerns that cut across language and embedding runtime calls, such as metrics/timing, lifecycle event guarding, or standardized error logging. It should not replace explicit domain orchestration for tool execution, structured output, request validation, or provider option mapping.

Alternatives considered:

- Add AOP around all `LanguageModel` and `EmbeddingModel` methods. Rejected unless a concrete cross-cutting concern is identified and tests prove behavior preservation.
- Keep lifecycle/error handling inside large runtime methods forever. Rejected where extracting a lifecycle collaborator or advice makes the flow clearer.

### Decision 4: Keep provider type classes cohesive but extract repeated option/adaptor construction

Concrete providers should remain the primary identity/metadata/behavior unit. Repeated construction of `LanguageModelProviderOptions`, `EmbeddingModelProviderOptions`, and OpenAI-compatible embedding adapters can move into provider support factories or strategy objects when duplication obscures provider-specific choices.

Alternatives considered:

- Split each provider into metadata bean, chat adapter bean, embedding adapter bean, and discovery bean. Rejected because it contradicts the existing provider type system.
- Keep every provider option constructor inline. Rejected where repeated constructor arguments make provider capabilities hard to inspect.

### Decision 5: Move endpoint business helpers behind injected collaborators

Endpoints may keep route declarations, because they are OpenAPI metadata and request routing. Validation, request enrichment, console-only tool assembly, provider/model mapping, and query construction should be collaborators when they hold business rules or are tested independently.

Alternatives considered:

- Split route declarations into separate router builder classes. Deferred because it does not solve the object model problem and risks OpenAPI drift.
- Move all endpoint private helpers immediately. Rejected; promote only helpers with business rules or repeated behavior.

## Risks / Trade-offs

- Runtime factory indirection can make tracing harder. Mitigation: keep factories package-local, named by runtime role, and covered by composition tests.
- Overusing interfaces can create ceremony. Mitigation: add interfaces only when there is a real variation or cross-cutting boundary.
- Spring AOP can obscure control flow. Mitigation: use it only for narrow cross-cutting concerns with explicit tests.
- Provider refactors can accidentally change provider capability metadata. Mitigation: preserve provider metadata tests and add provider option characterization tests before moving constructors.
- Public API boundaries could leak Spring AI details. Mitigation: keep all Spring AI types inside `app` and add compile-level checks through existing module boundaries.

## Migration Plan

1. Record Java architecture scan findings for direct construction, package responsibilities, Spring components, and candidate seams in the OpenSpec change artifacts.
2. Introduce language runtime composition factories and move `LanguageModelImpl` collaborator creation out of its constructor.
3. Introduce embedding runtime composition factories and move planner/aggregator/options assembly out of `EmbeddingModelImpl` where appropriate.
4. Extract endpoint and provider support collaborators only where behavior is hidden or duplicated.
5. Add focused tests for factory composition and behavior preservation.
6. Run `./gradlew compileJava`, focused backend tests, and OpenSpec strict validation.

Rollback is normal git revert because this plugin is unreleased and the refactor is internal.

## Open Questions

- Which cross-cutting concern, if any, is concrete enough to justify AOP in this change: timing, lifecycle guarding, error logging, or none?
- Should runtime factory classes be package-private where possible, or public within the app module for test visibility?
