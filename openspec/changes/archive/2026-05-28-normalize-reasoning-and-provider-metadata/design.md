## Context

The language model API already exposes first-class reasoning fields (`reasoning` and
`reasoningText`) and a generic `providerMetadata` map. The current implementation only
captures reasoning reliably when Spring AI exposes provider reasoning through metadata keys
such as `reasoningContent` or `reasoning_content`. Some providers or compatible adapters can
instead return reasoning as tagged text, which leaves `reasoningText` empty and leaks the
tagged content into `text`, `outputText`, and structured output parsing.

The current metadata mapper also adds normalized SDK fields such as provider type, response id,
and model id into `providerMetadata`. Those values already have typed homes in request/response
metadata, so putting them in provider metadata blurs the difference between provider-native
metadata and normalized SDK metadata.

## Goals / Non-Goals

**Goals:**

- Make non-streaming results expose reasoning consistently.
- Keep answer text and structured output parsing free of extracted reasoning blocks.
- Use typed result fields as the normalized public reasoning surface.
- Keep `providerMetadata` limited to provider-specific pass-through metadata.
- Remove duplicate normalized reasoning key names from public metadata output.
- Add tests and consumer documentation so the behavior is easy to verify and use.

**Non-Goals:**

- Add new provider reasoning controls or model capability discovery.
- Preserve old metadata aliases for callers.
- Introduce provider-native classes into the public API.
- Redesign the whole language model service or stream protocol.

## Decisions

### Centralize Reasoning Extraction

Create a small language-model support component that accepts provider metadata and generated
text, then returns an immutable extraction result containing:

- extracted reasoning text, if any
- cleaned answer text
- reasoning provider metadata, if any
- extraction source for diagnostics and tests

The extractor will recognize provider metadata first and then known tagged text blocks such as
`<think>...</think>` and `<reasoning>...</reasoning>`. If provider metadata contains reasoning
and the answer text also contains known reasoning tags, metadata remains the authoritative
reasoning source and the known tags are still stripped from visible answer text.

Alternative considered: keep extraction in each mapper method. That would duplicate parsing
rules across non-streaming, tool-step aggregation, and structured output parsing.

### Typed Reasoning Fields Are Authoritative

The public normalized reasoning contract is `ReasoningPart.text`,
`GenerationStep.reasoningText`, and `GenerateTextResult.reasoningText`. Provider metadata can
preserve provider-native payloads only when needed for provider round-trip, and normalized
aliases such as `reasoningContent` or `reasoning_content` will not be emitted as public SDK
metadata.

Internally, provider adapters may still read multiple provider-native key names from Spring AI
metadata because those names describe upstream adapter input, not public compatibility behavior.
Any public output will be canonicalized into typed reasoning parts.

Alternative considered: choose one public map key such as `reasoning`. That would still
duplicate the typed API and invite callers to depend on map shape instead of Java types.

### Provider Metadata Is Provider-Specific

`providerMetadata` will only contain provider-specific pass-through metadata, normally
namespaced by provider type when the SDK creates it. Normalized SDK values must be exposed
through typed request/response metadata:

- provider type: request/response diagnostic metadata, not provider metadata
- response id: `GenerationResponseMetadata.id`
- model id: `GenerationResponseMetadata.model`
- headers/body/messages: existing response metadata fields

Alternative considered: keep normalized fields at top level for convenience. That creates
ambiguous semantics and makes provider metadata harder to pass through safely.

### Structured Output Uses Cleaned Text

Structured output parsing and validation will use answer text after reasoning extraction. This
prevents tagged reasoning from breaking JSON, array, object, or choice output validation while
still preserving the extracted reasoning in typed fields.

Alternative considered: require callers to disable reasoning for structured output. That makes
structured generation fragile and puts provider quirks on the caller.

## Risks / Trade-offs

- Tagged text parsing can remove legitimate user-visible XML-like content. → Only strip known
  balanced reasoning tags and add regression tests for normal XML-ish answer text.
- Provider metadata may contain provider-native fields named like normalized fields. → Preserve
  provider-native values only inside a provider namespace when they are not used as normalized
  output.
- Removing top-level normalized provider metadata changes behavior for current development
  callers. → The plugin is unreleased and the values have typed replacements.
- Stream handling is intentionally left unchanged in this change. → Keep tests focused on
  non-streaming generation and structured output, and revisit stream parsing only if a stream
  provider starts leaking tagged reasoning as text deltas.
