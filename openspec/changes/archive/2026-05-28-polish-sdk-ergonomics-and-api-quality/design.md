## Context

The plugin has reached a broad functional baseline for text generation, streaming, tools, structured output, lifecycle controls, and embeddings. The remaining risk is that the public Java SDK can look aligned with provider-neutral AI API while still forcing callers into raw maps, magic strings, and under-documented request fields, and that large implementation classes make behavioral guarantees hard to maintain.

The API module is the product surface for other Halo plugins. It must be IDE-discoverable, Java-oriented, and provider-neutral. The app module should keep Spring AI as an implementation detail and use focused collaborators to enforce the SDK contract.

## Goals / Non-Goals

**Goals:**

- Make normal SDK usage type-safe and discoverable through builders, factories, enums, and JavaDoc.
- Ensure every supported public request field has real behavior, validation, tests, and documentation.
- Remove stale, compatibility-only, or misleading public properties rather than preserving old surfaces.
- Replace stringly Part construction with Java-oriented abstractions while preserving protocol-compatible stream semantics.
- Split oversized implementation classes into cohesive collaborators so future behavior is testable in isolation.
- Keep examples in `dev/dev.md` aligned with the preferred SDK path.

**Non-Goals:**

- Do not add new AI capabilities outside the existing text/tool/output/stream/embedding scope.
- Do not expose Spring AI classes from the public API.
- Do not add compatibility shims for old constructors, constants, or fields.
- Do not redesign the console UI except where generated API updates require small adjustments.

## Decisions

### Decision: Prefer typed SDK builders over raw maps for common schemas

The SDK will provide Java helpers for JSON Schema objects, properties, arrays, enums, required fields, descriptions, and additional provider-neutral schema attributes. Tool input/output schemas and structured output schemas will accept these helpers directly while still serializing to the provider-neutral representation needed by the implementation.

Alternative considered: keep accepting only `Map<String, Object>` and improve documentation. This keeps the API small but leaves callers with no IDE guidance and no compile-time hints for valid schema shapes.

### Decision: Keep raw extension points narrow and explicit

Provider options and advanced schema metadata can still support escape hatches where provider diversity requires them, but normal examples and public APIs must lead with typed helpers. Any raw map path must be documented as an escape hatch and covered by validation so it cannot silently override typed fields in surprising ways.

Alternative considered: remove raw maps entirely. That would be cleaner, but provider-specific options and uncommon schema keywords still need an extensibility path.

### Decision: Model Parts as typed abstractions with factory methods

Message content parts, generation content parts, and stream parts will move away from broad mutable DTOs with many nullable fields and duplicated string constants. The public API should expose factory methods and/or subtype-based abstractions that encode valid combinations, such as text, reasoning, tool call, tool result, source, file, and finish metadata.

Alternative considered: keep one DTO per Part family and add more validation. That reduces file churn but still leaves invalid construction easy and keeps the constant-heavy design the user wants to move away from.

### Decision: Refactor app implementation around behavior boundaries

`LanguageModelImpl` and related large classes will be split around responsibilities such as request validation, message conversion, provider option mapping, tool orchestration, structured output parsing, stream part normalization, lifecycle event publication, and result assembly. Public behavior remains anchored in existing specs; tests should target collaborators where possible.

Alternative considered: only add helper methods in the existing class. That is faster short-term, but it keeps correctness concentrated in one large file and makes the next alignment pass risky.

### Decision: Treat JavaDoc and examples as part of the SDK contract

Public API classes in `api/` will have JavaDoc for purpose, lifecycle, field semantics, unsupported-provider behavior, and minimal examples where useful. `dev/dev.md` will show typed construction first and avoid requiring callers to memorize JSON schema keywords.

Alternative considered: rely on tests as documentation. Tests prove behavior but do not help plugin authors at coding time.

## Risks / Trade-offs

- Public API churn may be large because compatibility is intentionally not preserved. Mitigation: keep changes scoped to SDK quality and update all examples/tests in the same change.
- Typed Part abstractions may need careful JSON serialization/deserialization if console endpoints reuse API objects. Mitigation: separate public SDK construction needs from console DTO needs when necessary.
- Over-refactoring could delay functional fixes. Mitigation: refactor only around existing feature boundaries and require behavior-preserving tests before deeper cleanup.
- Provider-specific settings can become too restrictive if helpers are over-designed. Mitigation: keep documented escape hatches while making typed helpers the default path.

## Migration Plan

1. Audit public API fields, constructors, constants, package layout, JavaDoc coverage, and examples.
2. Introduce typed helpers and Part abstractions in `api/`.
3. Update `app/` implementation and tests to consume the refined API without compatibility adapters.
4. Remove obsolete fields/constants/constructors and update `dev/dev.md`.
5. Run focused backend tests, `./gradlew compileJava`, and regenerate frontend API clients only if endpoint DTOs changed.

Rollback is not planned at API level because the plugin is unreleased. If a specific refactor causes instability, revert that focused implementation slice while keeping validated SDK improvements.

## Open Questions

- Whether Java sealed interfaces are acceptable for the public API baseline, or whether static factory classes should be preferred for wider Java compatibility.
- Whether schema helpers should live under a dedicated `schema` package or a broader SDK utility package after package cleanup.
- Whether console endpoint DTOs should remain separate from public SDK request/response types to avoid serialization constraints shaping the SDK.
