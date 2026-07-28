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
Schema support depends on the selected model and its administrator mapping.
