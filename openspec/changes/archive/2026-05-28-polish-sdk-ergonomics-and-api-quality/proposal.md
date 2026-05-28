## Why

The current Java SDK already covers many AI SDK Core concepts, but several public APIs are still stringly typed or map-heavy, and some implementation paths are large enough that correctness is hard to reason about. Before adding more feature surface, this change closes the loop on existing capabilities so callers get IDE-discoverable, standard, and fully usable APIs rather than shape-only compatibility.

## What Changes

- Introduce type-safe SDK ergonomics for JSON schema, tool definitions, output specs, provider options, and common request construction so callers no longer need to handwrite magic strings such as `"type"` and `"object"` for normal usage.
- **BREAKING** Refine public SDK package/type organization where needed, because the plugin is still unreleased and should prefer a clean API over compatibility shims.
- **BREAKING** Replace stringly typed Part construction with Java-oriented abstractions and factory methods that make valid message/content/stream parts discoverable and invalid combinations harder to create.
- Add JavaDoc to the public `api` module, prioritizing service interfaces, request/response objects, schema/tool/output builders, Part abstractions, and lifecycle/exception types.
- Audit implemented request fields and remove stale, unsupported, or compatibility-only properties; supported fields must have real behavior, validation, tests, and documentation.
- Refactor oversized implementation classes, especially language generation and embedding paths, into cohesive collaborators with single responsibilities while preserving provider-neutral SDK semantics.
- Update `dev/dev.md` and focused tests so each SDK feature has a copyable usage path and an executable proof that behavior works end to end.

## Non-Goals

- This is not a new model modality change: it does not add image generation, speech, transcription, rerank, or video APIs.
- This is not a provider expansion change: it does not add new provider types.
- This is not a console UI redesign; UI changes are limited to generated client updates or small adjustments needed by backend/public API changes.
- This change will not keep old stringly constructors, deprecated aliases, or compatibility-only fields when they conflict with a cleaner SDK.

## Capabilities

### New Capabilities

- `sdk-ergonomics`: Defines the public Java SDK quality bar: type-safe builders/helpers, JavaDoc, package organization, provider option helpers, and examples that make normal usage discoverable in IDEs.

### Modified Capabilities

- `ai-model-service`: Requires public text-generation APIs to expose type-safe message/content/part construction, documented request/result objects, and supported fields with real behavior.
- `structured-tool-io`: Requires tool schema and tool-call APIs to support typed SDK construction instead of caller-authored raw JSON schema maps for normal cases.
- `structured-output-generation`: Requires structured output schemas and output specs to be builder-friendly, documented, and behaviorally tested.
- `stream-protocol-invariants`: Requires stream Part abstractions to preserve AI SDK-compatible lifecycle ordering while avoiding invalid nested or mixed Part states.
- `embedding-core-alignment`: Requires embedding request settings and provider options to be documented, type-safe where practical, and either fully supported or removed.

## Impact

- `api/`: public SDK types, package organization, JavaDoc, builders/factories, Part abstractions, and request/result definitions.
- `app/`: service implementation refactors, provider option mapping, validation, warnings, stream conversion, structured output/tool handling, embedding request handling, and tests.
- `dev/dev.md`: user-facing examples must demonstrate the convenient typed SDK path instead of raw magic strings.
- Tests: focused unit and integration-style tests for SDK construction, schema/tool/output behavior, Part validity, stream invariants, and removal of unsupported properties.
- Generated OpenAPI/client files may change only if console DTOs or backend endpoints are affected.
