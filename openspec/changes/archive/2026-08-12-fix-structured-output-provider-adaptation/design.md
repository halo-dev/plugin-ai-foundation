## Context

`OutputSpec` is provider-neutral and already exposes `name`, `description`, `schema`, and `strict`.
The common OpenAI-compatible mapper currently turns every object, array, and choice schema into a
`json_schema` response format, while the wire serializer always writes `strict: true` and a fixed
name. This upgrades open schemas to a provider-specific strict subset without caller consent.

The provider options model also treats the existence of a structured-output options factory as
proof of native support. That is inaccurate: the common OpenAI-compatible path supports JSON Schema,
DeepSeek intentionally replaces it with JSON Object mode, and Ollama currently preserves typed chat
options but does not configure a native response format. As a result, strict warnings and fallback
warnings cannot describe actual runtime behavior.

Manual deployment testing reports that the failure became observable after `v1.0.0-beta.5`.
Repository history shows that the fixed `strict: true` serializer was already present in that tag;
the subsequent changes to the same model focused on tool-input streaming and continuation. The tag
therefore remains a regression-test baseline, but the source history does not support attributing
the hard-coded strict flag itself to a post-beta.5 commit.

Halo-owned prompt guidance, parsing, and validation already remain authoritative after provider
generation. The change should preserve those layers and make native enforcement an accurately
reported optimization rather than an implicit prerequisite.

## Goals / Non-Goals

**Goals:**

- Make `OutputSpec.strict` control the provider-native strict flag from request through wire format.
- Reject incompatible strict schemas locally with an actionable typed error and schema path.
- Represent native JSON Schema, JSON Object, and prompt-only support independently from the options
  factory used to preserve provider-specific parameters.
- Preserve non-strict schemas without silently closing objects or making optional fields required.
- Carry output name and description when the provider response format supports them.
- Produce truthful warnings and concrete prompt guidance for JSON-only or prompt-only adapters.
- Keep final parsing and validation provider-neutral and authoritative.

**Non-Goals:**

- Do not mutate consumer schemas into strict schemas because optional-field intent cannot be inferred
  safely.
- Do not change retry semantics or add automatic output repair.
- Do not change consumer plugins, Console UI, model capability discovery, or permissions.
- Do not guarantee provider conformance after a request has been accepted.

## Decisions

### 1. Model structured-output support as an internal provider profile

Add an internal provider support value to `LanguageModelProviderOptions` with three modes:

- `JSON_SCHEMA`: can receive native JSON Schema response formats and an explicit strict flag.
- `JSON_OBJECT`: can request valid JSON objects but cannot enforce the supplied schema natively.
- `PROMPT_ONLY`: retains provider-specific chat options while relying on instructions and local
  validation.

The common OpenAI-compatible factory declares `JSON_SCHEMA`, DeepSeek declares `JSON_OBJECT`, and
Ollama declares `PROMPT_ONLY` until its actual options builder configures a native format. Warnings
use this profile rather than testing whether a factory happens to exist.

Alternative considered: infer support from provider type names or factory presence. This repeats the
current ambiguity and prevents custom OpenAI-like configurations from accurately describing their
adapter behavior.

### 2. Treat strict as explicit opt-in

Only `Boolean.TRUE` selects strict native enforcement. `false` and `null` both select non-strict
behavior. A JSON Schema-capable adapter sends an explicit false strict value for object, array, and
choice requests so proxies cannot supply a stricter default. Raw JSON requests use JSON Object mode
when available because they do not carry a schema.

Alternative considered: omit the wire strict field for `null`. That delegates semantics to each
provider and recreates cross-provider ambiguity.

### 3. Validate instead of rewriting strict schemas

Introduce a focused strict-schema validator in the OpenAI-compatible support package. Before a
strict request is sent, it recursively checks the portable strict invariants used by the native
adapter, including:

- object schemas are closed with `additionalProperties: false`;
- every declared object property is listed in `required`;
- nested object schemas satisfy the same invariants;
- the root shape is compatible with the requested output type;
- unsupported strict composition keywords are rejected with their JSON path.

Violations raise a public structured-output schema exception that identifies the validation path.
The schema is never rewritten: automatically adding required properties or nullability would change
the caller's data contract.

Alternative considered: automatically add `additionalProperties: false` and required fields. This
would turn omitted values into required values without knowing whether `null` is valid and would be
a semantic data-contract change.

### 4. Make response-format serialization data-driven

Extend the internal OpenAI-compatible response-format value with name, description, and strict.
Serialization uses those values instead of the fixed `json_schema` name and hard-coded true flag.
Default names are stable and provider-valid when the caller omits one; invalid caller names fail
locally.

### 5. Preserve local authority for weaker native modes

JSON Object and prompt-only modes receive schema instructions and a compact example shape when an
example can be derived safely. Strict requests on those modes continue with local validation but
emit `structured-output-strict-not-guaranteed`. Prompt-only mappings also emit the existing
prompt-guidance warning. Object/JSON requests may use JSON Object mode. Array and choice requests
remain prompt-guided for every current adapter because the portable native strict subset requires
an object root; they are not wrapped because that would change the public output shape.

Alternative considered: wrap arrays and choices inside artificial objects. That changes generated
text, streamed deltas, and the public parsed output contract.

### 6. Make full provider diagnostics opt-in and correlatable

Use a dedicated `run.halo.aifoundation.diagnostics` logger at TRACE level. Each provider invocation
receives a new diagnostic identifier so retries and concurrent generations remain distinguishable.
When enabled, the OpenAI-compatible adapter records the request URL and JSON body, response status,
raw response or stream event, and normalized model output. The structured-output handler records the
requested output contract, extracted text, parsed value, or validation failure.

The diagnostics intentionally never record Authorization, API keys, or custom request headers.
Request and response bodies can contain private user content, so TRACE logging remains disabled by
default and is intended only for time-bounded local diagnosis.

Alternative considered: log only the validation exception at normal application levels. That loses
the raw provider payload and cannot distinguish transport instability, empty content, malformed JSON,
and local schema rejection—the exact failure classes this investigation needs to separate.

### 7. Classify explicit abnormal finish reasons after structured parsing fails

Keep `FinishReason` as result metadata for normal text generation: a partial text response ending in
`LENGTH` remains a usable result and must not become a global generation exception. Structured output
has a stronger completeness contract, so when final parsing or validation fails, enrich the failure
with the normalized and raw finish reasons.

If the normalized reason is `LENGTH`, `CONTENT_FILTER`, `TOOL_CALLS`, `ERROR`, or `OTHER`, return a
typed structured-output termination error with a reason-specific message. It remains a subtype of
the existing validation error so current consumer error handling continues to work. A `STOP` or
`UNKNOWN` result retains the original JSON/schema validation error. A syntactically valid, locally
valid structured value remains successful even if the provider reports `LENGTH`; this avoids
discarding a complete value merely because the provider reached its boundary after producing it.

Streaming validation errors carry the same exception type, normalized reason, and raw reason in the
terminal error part so `fullStream`, `output`, and `result` describe the same failure. Diagnostics add
a correlated termination event instead of replacing the lower-level parse event, preserving both the
immediate parse failure and its provider-level cause.

Alternative considered: reject every `LENGTH` response before parsing. That would incorrectly reject
a complete JSON value produced exactly at the token boundary and would make structured and plain-text
finish-reason behavior unnecessarily inconsistent.

### 8. Keep internal stream failure propagation type-safe

Do not encode Java exception identity into `TextStreamPart.providerMetadata` and later reconstruct it
from string constants. `TextStreamPart` is a public stream protocol object that can be mapped to UI
Message output, so it should continue to expose only safe error text and serializable diagnostic
metadata rather than a `Throwable` field.

Instead, the per-invocation `LanguageModelGenerationRun` retains the first terminal `Throwable`.
Every path that emits a terminal stream error records its real exception on that run. The replayed
`result` and `output` publishers then rethrow the retained runtime exception directly using normal
Java type relationships. A stream containing an externally constructed error part without an
in-process exception retains the existing generic fallback behavior.

Alternative considered: add `Throwable` to the public stream part. Even if ignored by the current UI
mapper, it would enlarge a public DTO with a non-serializable, potentially sensitive runtime object
and make future generic serialization unsafe.

### 9. Separate safe production summaries from full-content diagnostics

Keep the existing request-correlated TRACE diagnostics because they were necessary to distinguish a
provider transport failure from output-budget exhaustion, but do not enable them by default. Add a
single WARN event only after structured parsing or validation has finally failed. The event carries
the diagnostic identifier, exception and root-cause types, output type, normalized and raw finish
reasons, validation path, step, provider model/response identifiers, scalar token counts, and output
character count.

The WARN event never includes prompts, schemas, output text, response bodies, headers, credentials,
or raw provider usage. Successful requests and individual stream events remain silent at normal
levels. This gives production operators enough context to identify token exhaustion and provider
termination without creating per-request log volume or copying application content into logs.

Alternative considered: log every request completion at INFO. That would make ordinary AI traffic
produce frequent logs and still provide less signal than a failure-only summary.

## Risks / Trade-offs

- [Risk] Some OpenAI-compatible proxies may reject `json_schema` with `strict: false`. → Keep the
  support profile provider-owned and cover JSON Object/prompt-only fallbacks without changing local
  validation.
- [Risk] Strict-schema preflight may reject schemas accepted by one provider extension. → Validate a
  documented portable strict profile and keep non-strict mode available for broader schemas.
- [Risk] Additional prompt examples consume tokens. → Emit compact examples only for adapters that
  cannot enforce the schema natively.
- [Risk] Provider-generated JSON can still be empty or malformed. → Preserve typed validation errors
  and raw diagnostic context; automatic retries or repair remain a separate explicit contract.
- [Risk] Adding an internal provider option requires updating every builder construction. → Give the
  profile a safe prompt-only default and add provider matrix tests for every native factory.
- [Risk] Full request and response bodies may contain sensitive application content. → Keep them on
  a dedicated opt-in TRACE logger, exclude credentials and headers by construction, document the
  sensitivity, and disable it after diagnosis.
- [Risk] Consumers may currently match the generic invalid-JSON message. → Preserve
  `StructuredOutputValidationException` as the superclass while adding a typed termination subtype
  and reason-specific message.
- [Risk] A terminal stream error may be observed by more than one replay subscriber. → Retain the
  first exception in the per-generation run and rethrow that same immutable failure classification
  for every derived publisher.
- [Risk] Production diagnostics can leak generated content or become noisy. → Emit one
  failure-only WARN containing scalar metadata and lengths, while keeping all content-bearing events
  behind explicit TRACE configuration.

## Migration Plan

1. Add the internal provider support profile and strict-schema exception/validator.
2. Update OpenAI-compatible response-format construction and serialization.
3. Declare profiles for the common OpenAI-compatible path, DeepSeek, and Ollama.
4. Update warnings and prompt instructions while preserving final local parsing.
5. Run focused provider/language tests, the full backend suite, and strict OpenSpec validation.

Rollback is a normal branch revert; no persisted resources or generated API contracts change.

## Open Questions

- Live compatibility of third-party OpenAI-compatible gateways with explicit `strict: false` must be
  verified during manual provider testing; adapters can move to JSON Object or prompt-only mode
  without changing the public SDK contract.

## Live diagnostic evidence

Local Plugin Links reproduction with correlated TRACE diagnostics confirmed that the intermittent
failure is output-budget exhaustion rather than random schema rejection:

- GLM-5.2 accepted the non-strict JSON Schema request with HTTP 200, consumed 499 of 500 completion
  tokens as reasoning, returned `finish_reason: length`, and left assistant `content` empty.
- DeepSeek v4-pro accepted JSON Object mode and succeeded with 495 of 500 completion tokens, of which
  421 were reasoning tokens; only five completion tokens remained unused.
- DeepSeek v4-flash also succeeded in the captured attempt but consumed 446 of 500 completion tokens,
  including 372 reasoning tokens.

Plugin Links currently fixes manual extraction at `maxOutputTokens(500)`. Reasoning length varies
between invocations, so the same prompt and model may either leave enough room for the final JSON or
terminate at the length limit first. AI Foundation correctly reports invalid JSON after receiving
empty content, but a follow-up should make the length-limit cause explicit while the consumer raises
its budget or selects a non-reasoning configuration suitable for short extraction tasks.

The reported `v1.0.0-beta.5` boundary is not supported as a max-token serialization regression:
`v1.0.0-beta.4` already mapped `GenerateTextRequest.maxOutputTokens` to `max_tokens`, and beta.5
preserved that wire parameter while replacing the DeepSeek Spring AI client with the common
OpenAI-compatible adapter. Plugin Links introduced the fixed 500-token extraction request later in
its own `b8d91f38` change. The deployed version observation is therefore real operational evidence,
but the available source history does not justify assigning this failure to a beta.5 token-mapping
change.
