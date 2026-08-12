# SDK Core: Structured output

[简体中文](../../zh-CN/sdk-core/generating-structured-data.md) | English

Attach an `OutputSpec` to a normal text request:

```java
JsonSchema schema = JsonSchema.object()
    .property("title", JsonSchema.string())
    .property("summary", JsonSchema.string())
    .required("title", "summary");

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("Summarize this article.")
    .output(OutputSpec.object(schema))
    .build();

return model.generateText(request)
    .map(result -> result.getOutput());
```

`OutputSpec.object(...)`, `OutputSpec.array(...)`, and `OutputSpec.choice(...)` cover object,
array, and enumerated string output. `StructuredSchema` combines a JSON schema with local parsing
and validation when the caller needs a typed Java value.

For streams:

```java
StreamTextResult stream = model.streamText(request);
Flux<Object> snapshots = stream.partialOutputStream();
Mono<Object> finalValue = stream.output();
```

Array output also exposes `elementStream()` for completed, validated elements. Partial snapshots
are presentation state; persist only the final validated value.

The final result keeps:

- `outputText`: JSON text used for parsing.
- `output`: parsed and validated value.
- normal text, steps, usage, warnings, and provider metadata.

Invalid final JSON or failed local validation raises `StructuredOutputValidationException`.

## Non-strict and strict output

`OutputSpec.strict` is an explicit opt-in. Only `true` requests provider-native strict schema
enforcement. An omitted value or `false` remains non-strict: AI Foundation does not add
`additionalProperties`, change `required`, or otherwise rewrite the caller's schema. Final local
parsing and validation still run.

A portable strict object schema must:

- set `additionalProperties: false` on every object, including nested objects;
- list every property in that object's `required` array;
- model a value that may be absent as a required nullable value.

```java
Map<String, Object> strictSchema = Map.of(
    "type", "object",
    "additionalProperties", false,
    "properties", Map.of(
        "title", Map.of("type", "string"),
        "description", Map.of("type", List.of("string", "null"))
    ),
    "required", List.of("title", "description")
);

OutputSpec output = OutputSpec.builder()
    .type(OutputType.OBJECT)
    .name("article_summary")
    .description("An article summary")
    .schema(strictSchema)
    .strict(true)
    .build();
```

An incompatible strict schema raises `StructuredOutputSchemaException` before provider invocation.
Its `validationPath` identifies the incompatible location. AI Foundation does not repair the schema
automatically because doing so could change the caller's data contract.

## Provider formats and fallback

An adapter may support native JSON Schema, JSON Object mode, or prompt guidance only. Native support
improves conformance but never replaces final local validation:

- JSON Schema adapters forward the schema, name, description, and actual strict value for object
  roots.
- The portable native strict subset requires an object root. Array and choice output keep their
  original shape through prompt guidance and local validation instead of being wrapped.
- JSON Object adapters use JSON mode for object/JSON output. Array and choice output keep their
  original top-level shape and use prompt guidance plus local validation.
- Prompt-only adapters add schema instructions and a compact example without claiming native schema
  enforcement.

Inspect result or stream-step warnings for stable downgrade codes:

| Warning code                                | Meaning                                                |
| ------------------------------------------- | ------------------------------------------------------ |
| `structured-output-prompt-guidance`         | The requested shape relies on instructions and local validation. |
| `structured-output-strict-not-guaranteed`   | Strict was requested but the adapter cannot guarantee it natively. |

A provider can still return empty or malformed content after accepting a request. Treat the final
local validation result as authoritative.

`StructuredOutputSchemaException` describes an incompatible strict request schema.
`StructuredOutputValidationException` describes model output that cannot be parsed or validated.

If the provider reports an explicit abnormal finish reason and no valid structured value can be
parsed, AI Foundation raises `StructuredOutputTerminationException`. It remains a subtype of
`StructuredOutputValidationException`, so existing error mappings continue to work. Consumers that
need a recovery policy can inspect `getFinishReason()`, `getRawFinishReason()`, `getUsage()`, and
`getResponse()`.

For example, `FinishReason.LENGTH` means the model reached its output token limit before producing a
valid structured value, so the error identifies token exhaustion instead of only reporting invalid
JSON. `CONTENT_FILTER`, `TOOL_CALLS`, `ERROR`, and other explicit provider reasons retain their own
semantics. A complete value that passes local validation still succeeds when the provider reports
`LENGTH`, with that finish reason preserved on the result. Plain-text generation also remains a
successful partial result on `LENGTH`; callers can inspect `GenerateTextResult.getFinishReason()` to
decide whether to continue or notify the user.
Prefer logging the exception type and `validationPath`. Record `outputText` only in controlled logs,
and never expose raw model output to untrusted users because it may contain application or provider
data.

AI Foundation emits one WARN summary when structured output finally fails. The summary includes the
`diagnosticId`, failure and root-cause types, output and finish-reason metadata, validation path,
model/response identifiers, scalar token counts, and output character count. It does not include
prompts, schemas, generated output, provider response bodies, headers, credentials, or raw usage.
Successful requests and individual stream events do not produce these production summaries.

For time-bounded local diagnosis of intermittent provider responses, enable the dedicated TRACE
logger temporarily:

```yaml
logging:
  level:
    run.halo.aifoundation.diagnostics: TRACE
```

One `diagnosticId` correlates the request body, HTTP status, raw response, normalized model output,
and structured parsing result for an actual provider invocation. Each retry receives a new ID. The
facility excludes Authorization, API keys, and custom request headers, but request and response
bodies can still contain private application content. Full-content TRACE logging is disabled by
default and should be turned off after diagnosis, with captured logs handled accordingly.

Schema support depends on the selected model and its administrator mapping.
