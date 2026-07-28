# SDK UI：Completion 与 Object Stream

简体中文 | [English](../../en/sdk-ui/completion-and-object-generation.md)

`useCompletion` 和 `experimental_useObject` 读取普通文本 response stream，不使用 Halo
UI Message SSE。它们适合比聊天更简单的单输入界面。

## Completion

```ts
import { useCompletion } from "@halo-dev/ai-foundation-sdk";

const completion = useCompletion({
    id: "title-generator",
    api: "/apis/example.halo.run/v1alpha1/completion/stream",
    body: { temperature: 0.2 },
    onFinish(prompt, value) {
        console.debug("completed", { prompt, value });
    },
    onError(error) {
        console.error(error);
    },
});

await completion.complete("为文章生成一个标题");
```

返回值：

```ts
completion.id
completion.completion
completion.input
completion.error
completion.isLoading
completion.complete(...)
completion.stop()
completion.setCompletion(...)
completion.setInput(...)
completion.handleInputChange(...)
completion.handleSubmit(...)
```

这些状态字段是 Vue ref。

## Completion 表单

```vue
<script setup lang="ts">
import { useCompletion } from "@halo-dev/ai-foundation-sdk";

const completion = useCompletion({
    api: "/apis/example.halo.run/v1alpha1/completion/stream",
});
</script>

<template>
    <form @submit="completion.handleSubmit">
        <input :value="completion.input.value" @input="completion.handleInputChange" />
        <button :disabled="completion.isLoading.value">生成</button>
        <button type="button" @click="completion.stop()">停止</button>
    </form>

    <p>{{ completion.completion.value }}</p>
    <p v-if="completion.error.value">
        {{ completion.error.value.message }}
    </p>
</template>
```

## Completion 请求

默认发送：

```json
{
    "prompt": "为文章生成一个标题"
}
```

配置级 body、单次 body 与 prompt 会合并：

```ts
await completion.complete("写一个标题", {
    body: { postName: "welcome" },
    headers: { "X-Request-Id": requestId },
    credentials: "include",
});
```

`prompt` 始终使用本次实际输入，不会被 body 覆盖。

后端必须返回 `Response.body` 可读取的纯文本字节流。不要返回 UI Message chunk 或
`data: ...` SSE frame；`useCompletion` 会把收到的字节直接拼接为 completion。

## Object Stream

```ts
import { experimental_useObject, jsonSchema } from "@halo-dev/ai-foundation-sdk";

type ArticleSummary = {
    title: string;
    summary: string;
    keywords: string[];
};

const summary = experimental_useObject<ArticleSummary, { content: string }>({
    id: "article-summary",
    api: "/apis/example.halo.run/v1alpha1/object/stream",
    schema: jsonSchema<ArticleSummary>({
        type: "object",
        properties: {
            title: { type: "string" },
            summary: { type: "string" },
            keywords: {
                type: "array",
                items: { type: "string" },
            },
        },
        required: ["title", "summary", "keywords"],
    }),
});

const result = await summary.submit({
    content: articleContent,
});
```

返回值：

```ts
summary.id
summary.object
summary.text
summary.error
summary.isLoading
summary.submit(...)
summary.stop()
summary.clear()
```

`object` 是 `DeepPartial<T> | undefined`：流式过程中只保证当前 JSON 能 best-effort 解析，
字段可能尚未出现。

## Object 请求

默认 body：

```json
{
    "input": {
        "content": "..."
    },
    "schema": {
        "type": "object"
    },
    "output": {
        "type": "object",
        "schema": {
            "type": "object"
        }
    }
}
```

后端返回正在形成的 JSON 文本，例如：

```text
{"title":"Halo","summary":"...
```

SDK 会持续解析 partial snapshot。stream 结束后，它会执行完整 `JSON.parse` 和 schema 校验；
只有最终校验通过，`submit()` 才返回 `T` 并调用 `onFinish`。

## 支持的 Schema

`schema` 可使用：

- JSON Schema 对象。
- 具有 `safeParse` 或 `parse` 的同步 schema。
- 同步 Standard Schema adapter。

示例使用具有 `safeParse` 的业务 schema：

```ts
const schema = {
    safeParse(value: unknown) {
        if (value && typeof value === "object" && "title" in value && typeof value.title === "string") {
            return {
                success: true as const,
                data: value as { title: string },
            };
        }
        return {
            success: false as const,
            error: { message: "title is required" },
        };
    },
};
```

对象流只支持同步 schema。异步校验不会被等待。

## 准备 OpenAPI 请求

```ts
const object = experimental_useObject({
    schema,
    prepareRequest: async ({ body }) => {
        const args = await paramCreator.testModelObjectStream("model-name", body);
        return fromOpenAPIRequestArgs(args, body);
    },
});
```

`useCompletion` 也支持同名 `prepareRequest`。它允许复用生成 client 的 URL、header 和 query
参数，同时仍由浏览器原生 `fetch` 读取流。

## 错误与取消

- 非 2xx response 会读取文本并抛 `AIUIError`。
- 空 response body 会失败。
- Object 最终 JSON 或 schema 校验失败会进入 `error`。
- `stop()` 会 abort 当前请求；用户取消不会调用 `onError`。
- 失败时 `complete()` / `submit()` 返回 `undefined`。

Object API 名称带 `experimental_`，调用方升级版本时应特别核对类型变化。
