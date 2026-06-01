## 1. Public API Audit

- [x] 1.1 Audit `api/` package layout, oversized classes, duplicated constants, public constructors, raw map usage, unsupported fields, and JavaDoc gaps.
- [x] 1.2 Identify all public fields that are ignored, warning-only, compatibility-only, or not covered by tests, and decide remove versus fully implement.
- [x] 1.3 Define the target package organization for services, messages, parts, schemas, tools, output, options, embeddings, lifecycle events, and exceptions.

## 2. Typed Schema And Option SDK

- [x] 2.1 Add SDK schema helper APIs for object, string, number, integer, boolean, array, enum, description, required fields, nested properties, and additional metadata.
- [x] 2.2 Update tool definition APIs to accept typed schema helpers and serialize them to the existing provider-neutral schema representation.
- [x] 2.3 Update structured output APIs to accept typed schema/output helpers for object, enum, array, and no-schema output.
- [x] 2.4 Keep `ProviderOptions` provider-neutral, document raw provider options as explicit escape hatches, and avoid hardcoding helpers for only a subset of providers.

## 3. Part Model Cleanup

- [x] 3.1 Replace broad nullable Part DTO construction with type-safe factories or subtype abstractions for model message parts.
- [x] 3.2 Replace broad nullable Part DTO construction with type-safe factories or subtype abstractions for generation content parts.
- [x] 3.3 Replace stream part string constants and invalid field combinations with type-safe stream part construction.
- [x] 3.4 Remove obsolete duplicated Part constants and constructors after all call sites use the new abstractions.

## 4. Implementation Refactor And Behavior Closure

- [x] 4.1 Split language generation validation, message conversion, provider option mapping, tool orchestration, structured output handling, stream normalization, lifecycle events, and result assembly into focused collaborators.
- [x] 4.2 Update embedding request handling so every remaining setting and provider option has real mapping, validation, tests, and documentation.
- [x] 4.3 Remove or fully implement unsupported public request fields across text generation, structured output, tools, streaming, and embeddings.
- [x] 4.4 Ensure provider integrations still preserve provider type versus provider resource name semantics and do not expose Spring AI types through the public API.
- [x] 4.5 Move public API classes into coherent subpackages, or explicitly document why the package move is being deferred beyond this change.
- [x] 4.6 Reduce `LanguageModelImpl` by extracting at least stream assembly, tool orchestration, and result mapping into dedicated package-private collaborators.
- [x] 4.7 Extract provider chat option construction into a dedicated package-private collaborator so headers, tool choice, and Spring AI option mapping are no longer embedded in `LanguageModelImpl`.

## 5. Documentation

- [x] 5.1 Add JavaDoc to public service interfaces, request/result types, Part abstractions, schema helpers, tool/output helpers, provider option helpers, lifecycle events, and exceptions.
- [x] 5.2 Update `dev/dev.md` examples to show typed SDK construction first for messages, tools, structured output, provider options, and embeddings.
- [x] 5.3 Document raw map/provider-specific escape hatches only where they remain intentionally supported.

## 6. Tests And Validation

- [x] 6.1 Add API-level tests for schema helper serialization, typed tool definitions, typed output specs, provider options, and invalid Part construction.
- [x] 6.2 Add implementation tests for tool-call round trips, structured output behavior, stream protocol invariants, and embedding setting propagation.
- [x] 6.3 Run focused backend tests for changed areas and `./gradlew compileJava`.
- [x] 6.4 Run `openspec validate polish-sdk-ergonomics-and-api-quality --strict` and confirm the change is apply-ready.
