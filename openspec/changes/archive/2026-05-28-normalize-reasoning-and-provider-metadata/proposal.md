## Why

Non-streaming generation can currently lose or leak model reasoning when a provider returns it as tagged text instead of structured metadata. Provider metadata also mixes provider-native values with normalized SDK fields, and reasoning metadata is exposed under both camelCase and snake_case names, which makes the public contract harder to use correctly.

This change closes the already-built text generation surface so reasoning, answer text, structured output parsing, and provider metadata behave consistently for callers.

## What Changes

- Extract model reasoning from both structured provider metadata and known tagged reasoning blocks in generated text.
- Keep reasoning as first-class typed result data through `reasoning` and `reasoningText`; non-streaming answer text fields must not include stripped reasoning blocks.
- **BREAKING**: Stop emitting both `reasoningContent` and `reasoning_content` as normalized public metadata keys. Public normalized reasoning lives only in typed reasoning fields.
- **BREAKING**: Stop adding normalized SDK fields such as provider type, response id, and model id into top-level `providerMetadata`; those values must be exposed through typed request/response metadata instead.
- Ensure structured output parsing uses answer text after reasoning extraction so tagged reasoning does not break JSON/object/array/choice parsing.
- Add regression tests and consumer documentation for reasoning extraction, metadata layering, and structured output with reasoning.

## Non-Goals

- This change does not add new provider-specific reasoning controls.
- This change does not introduce compatibility aliases for previous metadata key names.
- This change does not redesign model discovery, provider configuration, or embedding behavior.
- This change does not expose provider-native SDK classes in the public API.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `reasoning-content-parts`: Require reasoning extraction from provider metadata and tagged text, with typed reasoning fields as the only normalized public surface.
- `ai-model-service`: Clarify text generation result metadata layering and require provider metadata to remain provider-specific instead of carrying normalized response fields.
- `structured-output-generation`: Require structured output validation and parsing to use text after reasoning extraction.
- `consumer-sdk-documentation`: Document the final public behavior for reasoning output and provider metadata without exposing implementation-only details.

## Impact

- Public Java SDK result semantics in `api/` for `GenerateTextResult`, `GenerationStep`, `ReasoningPart`, `GenerationRequestMetadata`, and `GenerationResponseMetadata`.
- Language model implementation and mapping code in `app/src/main/java/run/halo/aifoundation/service/language/`.
- Provider adapter metadata handling where provider-native reasoning values are converted into typed reasoning parts.
- Consumer guide updates in `dev/dev.md`.
