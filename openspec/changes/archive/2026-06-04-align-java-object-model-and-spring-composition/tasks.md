## 1. Java Architecture Scan

- [x] 1.1 Record Java object-model and Spring composition scan findings covering production Java files under `app/src/main/java` and public API boundary risks under `api/src/main/java`.
- [x] 1.2 Classify direct `new` usage as behavior dependency, value object, DTO/record, exception, framework request/builder, provider adapter, or per-call accumulator.
- [x] 1.3 Identify implementation candidates for this batch, including `LanguageModelImpl`, `DefaultLanguageModelFactory`, `EmbeddingModelImpl`, `DefaultEmbeddingModelFactory`, provider option construction, and endpoint business helpers.
- [x] 1.4 Document explicit exclusions such as public DTOs, extension models, provider route declarations, and intentionally cohesive provider classes.

## 2. Language Runtime Composition

- [x] 2.1 Add focused tests that characterize language runtime factory composition and provider-option propagation before changing constructor ownership.
- [x] 2.2 Introduce a Spring-managed language runtime composition factory or equivalent assembler that creates provider-specific language collaborators.
- [x] 2.3 Refactor `DefaultLanguageModelFactory` to delegate runtime assembly to the new composition boundary.
- [x] 2.4 Refactor `LanguageModelImpl` so validators, mappers, options builders, response mappers, reasoning extractors, structured-output handlers, tool executors, tool coordinators, approval resolvers, and history assemblers are supplied by constructor or composition object rather than created internally.
- [x] 2.5 Preserve all existing prompt, message, reasoning, streaming, structured-output, tool, approval, repair, timeout, cancellation, and lifecycle behavior.

## 3. Embedding Runtime Composition

- [x] 3.1 Add focused tests that characterize embedding runtime factory composition, batching limits, provider options, headers, lifecycle, timeout, and parallel execution behavior.
- [x] 3.2 Introduce a Spring-managed embedding runtime composition factory or equivalent assembler for planner, aggregator, provider options, and Spring AI embedding model wrapping.
- [x] 3.3 Refactor `DefaultEmbeddingModelFactory` and `EmbeddingModelImpl` so behavior-bearing collaborators are supplied through the composition boundary.
- [x] 3.4 Preserve embedding request validation, batching, ordering, retries, usage aggregation, provider metadata, cancellation, timeout, and lifecycle behavior.

## 4. Provider And Endpoint Boundaries

- [x] 4.1 Review concrete provider classes for repeated provider option and adapter construction that can move into support factories without changing one-class-per-provider architecture.
- [x] 4.2 Extract provider support factories or strategy objects only where duplication hides provider capability decisions.
- [x] 4.3 Review console endpoints for validation, request enrichment, test-tool assembly, and mapping helpers that should become Spring-managed collaborators.
- [x] 4.4 Keep OpenAPI route declarations in endpoint classes unless a route-building extraction demonstrably preserves generated API behavior.

## 5. Cross-Cutting Spring Practices

- [x] 5.1 Decide whether any concrete cross-cutting concern warrants Spring AOP or advice in this change.
- [x] 5.2 If AOP/advice is introduced, add focused tests proving the concern applies across multiple runtime paths and does not hide domain orchestration.
- [x] 5.3 If no AOP/advice is introduced, document why explicit collaborators are the chosen Spring best-practice boundary for this codebase.

## 6. Validation

- [x] 6.1 Run focused language model tests for generation, streaming, structured output, tools, approval, repair, timeout, cancellation, and lifecycle behavior.
- [x] 6.2 Run focused embedding tests for batching, headers, provider options, lifecycle, retry, timeout, cancellation, and ordering behavior.
- [x] 6.3 Run focused endpoint/provider tests for any moved validation, mapping, provider option, or adapter construction behavior.
- [x] 6.4 Run `./gradlew compileJava`.
- [x] 6.5 Run `openspec validate align-java-object-model-and-spring-composition --strict`.
- [x] 6.6 Run `git diff --check`.
