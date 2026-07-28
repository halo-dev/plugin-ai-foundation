# SDK UI: Completion and object streams

[简体中文](../../zh-CN/sdk-ui/completion-and-object-generation.md) | English

These composables consume plain text responses, not Halo UI Message SSE.

## Completion

```ts
const completion = useCompletion({
    id: "title",
    api: "/apis/example.halo.run/v1alpha1/completion",
    body: { temperature: 0.2 },
    onFinish(prompt, value) {
        console.debug(prompt, value);
    },
});

await completion.complete("Write a title");
```

The composable exposes completion/input refs, error and loading state, `complete`, `stop`,
`setCompletion`, `setInput`, and form helpers. Configuration and per-call headers, body, and
credentials are merged; the actual `prompt` cannot be overridden by extra body data.

## Incremental objects

```ts
const object = experimental_useObject<{ title: string; summary: string }>({
    api: "/apis/example.halo.run/v1alpha1/object",
    schema: jsonSchema({
        type: "object",
        properties: {
            title: { type: "string" },
            summary: { type: "string" },
        },
        required: ["title", "summary"],
    }),
});

await object.submit({ article });
```

The request includes `input`, JSON `schema`, and an `output` object. During the stream,
`object.value` contains best-effort `DeepPartial<T>` snapshots. The final JSON is parsed and
validated before `onFinish`.

Supported schemas are JSON Schema, synchronous `safeParse` / `parse` adapters, synchronous
Standard Schema, or adapters that export JSON Schema with `toJSONSchema` / `toJsonSchema`.
Asynchronous runtime schema validation is not supported.

`prepareRequest` and `fromOpenAPIRequestArgs` integrate generated OpenAPI parameter creators.
Empty responses, invalid JSON, and final schema failures set `error`; `stop()` cancels the active
request.
