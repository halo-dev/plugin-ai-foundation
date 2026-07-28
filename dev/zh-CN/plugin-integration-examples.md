# 在 Halo 插件中集成 AI Foundation

简体中文 | [English](../en/plugin-integration-examples.md)

本页把插件依赖、后端调用、UI Message endpoint、Vue 前端和模型设置串成一条完整接入路径。

## 1. 声明依赖

调用方只在编译期依赖 Java API：

```groovy
repositories {
    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        mavenContent {
            snapshotsOnly()
        }
    }
    mavenCentral()
}

dependencies {
    compileOnly "run.halo.aifoundation:api:1.0.0-SNAPSHOT"

    testImplementation "run.halo.aifoundation:api:1.0.0-SNAPSHOT"
}
```

正式版本发布后应替换为对应版本。使用 `compileOnly` 可以避免把 SDK API 重复打进调用方插件。

如果插件的核心功能依赖 AI Foundation，在 `plugin.yaml` 中声明必选依赖：

```yaml
spec:
    pluginDependencies:
        ai-foundation: "*"
```

如果只有部分功能使用 AI Foundation，可以声明可选依赖：

```yaml
spec:
    pluginDependencies:
        ai-foundation?: "*"
```

使用可选依赖时，调用方应根据 AI Foundation 的启用状态条件化注册相关 Bean，并在功能入口显示
明确的不可用状态。所有引用 `run.halo.aifoundation` 类型的运行时路径都要受该条件保护。

> [!TIP]
> 使用可选依赖时，可以在插件元数据中推荐用户安装 AI Foundation：
>
> ```yaml
> metadata:
>     annotations:
>         "store.halo.run/recommended-apps": '["app-acslk9nu"]'
> ```

## 2. 获取 `AiModelService`

Halo 插件的 Spring ApplicationContext 相互隔离。调用方通过 `ExtensionGetter` 获取服务：

```java
package run.halo.example.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

@Component
@RequiredArgsConstructor
public class AiFoundationClient {

    private final ExtensionGetter extensionGetter;

    public Mono<AiModelService> aiModelService() {
        return extensionGetter.getEnabledExtension(AiModelService.class)
            .switchIfEmpty(Mono.error(() ->
                new IllegalStateException("AI Foundation 未启用")));
    }
}
```

不要在调用方插件中直接注入 `AiModelService`。`modelName` 使用
`AiModel.metadata.name`，不要保存 Provider 类型或供应方原始模型 ID。

## 3. 实现文章摘要服务

```java
package run.halo.example.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;

@Service
@RequiredArgsConstructor
public class ArticleSummaryService {

    private final AiFoundationClient aiFoundationClient;

    public Mono<String> summarize(String modelName, String title, String content) {
        var request = GenerateTextRequest.builder()
            .system("你是文章编辑助手。只返回摘要正文。")
            .prompt("""
                请为下面的文章生成不超过 120 字的摘要。

                标题：%s

                正文：
                %s
                """.formatted(title, content))
            .temperature(0.2)
            .maxOutputTokens(256)
            .build();

        return aiFoundationClient.aiModelService()
            .flatMap(service -> service.languageModel(modelName))
            .flatMap(model -> model.generateText(request))
            .map(GenerateTextResult::getText);
    }
}
```

不需要让管理员选择模型时，可以解析默认模型：

```java
return aiFoundationClient.aiModelService()
    .flatMap(AiModelService::languageModel)
    .flatMap(model -> model.generateText(request));
```

调用方可以从 `GenerateTextResult` 同时读取 `usage`、`warnings`、`responseMessages`、
`reasoning` 和响应 metadata。继续工具步骤或多轮上下文时，应保存 `responseMessages`，不能只保存
最终文本。

## 4. 添加服务端工具和多步骤

下面的工具查询调用方插件自己的文章服务工具：

```java
ToolDefinition searchPosts = ToolDefinition.builder()
    .name("search_posts")
    .description("按关键词搜索 Halo 文章。")
    .inputSchema(JsonSchema.object()
        .property("keyword", JsonSchema.string().description("文章标题或关键词"))
        .required("keyword"))
    .executor(context -> {
        var keyword = String.valueOf(context.getInput().get("keyword"));
        return postService.search(keyword).map(result -> (Object) result);
    })
    .build();
```

把工具加入请求，并限制模型步骤数：

```java
var request = GenerateTextRequest.builder()
    .system("你是站点内容助手。需要站点数据时使用工具。")
    .messages(List.of(ModelMessage.user(question)))
    .tools(List.of(searchPosts))
    .toolChoice(ToolChoice.auto())
    .stopWhen(StopCondition.stepCountIs(4))
    .maxOutputTokens(1024)
    .build();

return aiFoundationClient.aiModelService()
    .flatMap(AiModelService::languageModel)
    .flatMap(model -> model.generateText(request));
```

工具的 `executor` 返回 `Mono<Object>`。业务权限、输入归一化和资源访问仍由调用方工具实现。
`StopCondition` 是请求的执行预算，应根据工具成本设置明确上限。

## 5. 暴露 UI Message 聊天 Endpoint

如果前端使用 `Chat` 或 `useChat`，后端可以用 `UIMessageChatHandlers` 完成消息校验、模型消息转换
和 stream 映射：

```java
public Mono<UIMessageStreamResponse> stream(
    String modelName,
    UIMessageChatRequest<Void> chatRequest
) {
    return aiFoundationClient.aiModelService()
        .flatMap(service -> service.languageModel(modelName))
        .map(model -> UIMessageChatHandlers.<Void>streamText(options -> options
            .model(model)
            .chatRequest(chatRequest)
            .request(builder -> builder
                .system("你是站点助手。")
                .maxRetries(2)))
            .response());
}
```

WebFlux Endpoint 把 `UIMessageStreamResponse` 返回给浏览器：

```java
public Mono<ServerResponse> chat(ServerRequest request) {
    return request.bodyToMono(new ParameterizedTypeReference<
            UIMessageChatRequest<Void>>() {
        })
        .flatMap(chatRequest -> stream(configuredModelName(), chatRequest))
        .flatMap(response -> ServerResponse.ok()
            .headers(headers -> headers.setAll(response.headers()))
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(response.body(), String.class));
}
```

`response.body()` 已包含编码后的 SSE 内容和结束标记。不要再次给每一项添加 `data:`。

如果需要在结束时保存消息：

```java
var chat = UIMessageChatHandlers.<ChatMetadata>streamText(options -> options
    .model(model)
    .chatRequest(chatRequest)
    .onFinish(finish -> conversationStore.save(
        chatRequest.id(),
        finish.messages())));

return chat.response();
```

持久化、访问控制、速率限制和会话所有权检查应在调用方 Endpoint 中完成。

## 6. 在 Vue 中消费聊天流

安装 npm SDK：

```bash
pnpm add @halo-dev/ai-foundation-sdk
```

最小 Vue 组件：

```vue
<script setup lang="ts">
import { DefaultChatTransport, messageText, useChat } from "@halo-dev/ai-foundation-sdk";
import { ref } from "vue";

const input = ref("");

const chat = useChat({
    id: "article-assistant",
    transport: new DefaultChatTransport({
        api: "/apis/example.halo.run/v1alpha1/chat",
    }),
    onError(error) {
        console.error("AI chat failed", error);
    },
});

async function send() {
    const text = input.value.trim();
    if (!text || chat.isLoading.value) {
        return;
    }
    input.value = "";
    await chat.sendMessage({ text });
}
</script>

<template>
    <section>
        <article v-for="message in chat.messages.value" :key="message.id">
            <strong>{{ message.role }}</strong>
            <p>{{ messageText(message) }}</p>
        </article>

        <p v-if="chat.error.value">{{ chat.error.value.message }}</p>

        <form @submit.prevent="send">
            <input v-model="input" :disabled="chat.isLoading.value" />
            <button :disabled="chat.isLoading.value">发送</button>
            <button type="button" @click="chat.stop()">停止</button>
        </form>
    </section>
</template>
```

正式聊天界面应按 `message.parts` 分别渲染 text、reasoning、source、file、data 和 tool part。
`messageText` 适合只需要纯文本的简单界面。

不使用 Vue 时，可以直接创建 `Chat`：

```ts
const chat = new Chat({
    id: "public-chat",
    transport: new DefaultChatTransport({
        api: "/apis/example.halo.run/v1alpha1/chat",
    }),
    onFinish({ messages }) {
        localStorage.setItem("chat-history", JSON.stringify(messages));
    },
});

await chat.sendMessage({ text: "你好" });
```

## 7. 在设置中选择模型

AI Foundation 注册了 FormKit `aiModelSelector`。语言模型设置：

```yaml
formSchema:
    - $formkit: aiModelSelector
      name: modelName
      label: 对话模型
      modelType: language
      available: true
      validation: required
      placeholder: 请选择语言模型
```

需要工具调用的语言模型：

```yaml
- $formkit: aiModelSelector
  name: agentModelName
  label: Agent 模型
  modelType: language
  available: true
  requiredFeatures:
      - tool-call
  validation: required
```

图像生成模型：

```yaml
- $formkit: aiModelSelector
  name: imageModelName
  label: 图片生成模型
  modelType: image-generation
  available: true
  clearable: true
```

设置保存的值就是 `AiModel.metadata.name`，可以直接传给对应的 `AiModelService` 方法。

继续查阅：

- [SDK Core](./sdk-core/README.md)
- [SDK UI](./sdk-ui/README.md)
- [模型选择器](./model-selector.md)
