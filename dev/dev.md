# AI 基础设施插件 — 开发者集成指南

本文档面向需要在 Halo 插件中调用 AI 能力的开发者。

## 前置条件

- 本插件（ai-foundation）已安装并启用
- 已至少配置一个 AI 提供商和模型（通过 Halo 控制台 > AI 基础设施）
- 调用方插件已在 `plugin.yaml` 中声明对 `ai-foundation` 的插件依赖

## 添加依赖

在你的插件 `build.gradle` 中添加对 `api` 模块的 `compileOnly` 依赖：

```gradle
repositories {
    mavenLocal()
}

dependencies {
    compileOnly platform('run.halo.tools.platform:plugin:2.23.0')
    compileOnly 'run.halo.app:api'
    compileOnly 'run.halo.aifoundation:api:1.0.0-SNAPSHOT'
}
```

如果调用方插件项目已经配置了 Halo 插件平台和 `run.halo.app:api` 依赖，只需要额外补充 `run.halo.aifoundation:api`。

然后在调用方插件的 `plugin.yaml` 中声明运行时插件依赖：

```yaml
spec:
  pluginDependencies:
    ai-foundation: ">=1.0.0-SNAPSHOT"
```

开发环境中可以先在本项目根目录执行 `./gradlew :api:publishToMavenLocal`，将 SDK 发布到本地 Maven 仓库。

## 核心接口

### AiModelService

`AiModelService` 是入口服务，同时也是 Halo 服务端 Extension Point。由于 Halo 插件间的 Spring ApplicationContext 隔离，调用方插件不要直接通过 `@Autowired` 注入 `AiModelService`，而是注入 Halo 提供的 `ExtensionGetter` 并获取启用的服务实现：

```java
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

@Service
@RequiredArgsConstructor
public class MyAiService {

    private final ExtensionGetter extensionGetter;

    private Mono<AiModelService> aiModelService() {
        return extensionGetter.getEnabledExtension(AiModelService.class);
    }
}
```

它提供以下方法：

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `languageModel(String modelName)` | `Mono<LanguageModel>` | 获取指定语言模型实例（需在响应式上下文中订阅） |
| `embeddingModel(String modelName)` | `Mono<EmbeddingModel>` | 获取指定嵌入模型实例（需在响应式上下文中订阅） |
| `defaultLanguageModel()` | `Mono<LanguageModel>` | 获取默认语言模型实例 |
| `defaultEmbeddingModel()` | `Mono<EmbeddingModel>` | 获取默认嵌入模型实例 |
| `listModels()` | `Mono<List<ModelInfo>>` | 列出所有已配置的模型 |
| `listProviders()` | `Mono<List<ProviderInfo>>` | 列出所有已配置的提供商 |

### modelName 的格式

`modelName` 是 **AiModel 的 metadata.name**（即 Halo Extension 资源的名称），例如 `openai-prod-gpt-4o-a7f3k`。它不是提供商侧的 `modelId`，也不是 `providerName/modelId` 这样的组合字符串。

你可以通过 `listModels()` 获取所有可用模型的 `name` 字段。

## 语言模型调用

以下示例中的 `aiModelService` 均来自上文通过 `ExtensionGetter` 获取到的 `AiModelService` 实例。

### 简单文本生成

```java
LanguageModel model = aiModelService.languageModel("deepseek-prod-deepseek-chat").block();

model.generateText("你好，请介绍一下 Halo CMS")
    .subscribe(result -> {
        System.out.println(result.getText());
        System.out.println("Finish reason: " + result.getFinishReason());
    });
```

### 结构化文本生成

`GenerateTextRequest` 支持两种输入方式：

- `prompt`：单轮用户提示词
- `messages`：多轮消息历史

`prompt` 和 `messages` 必须二选一，不能同时传入。系统提示词建议使用顶层 `system` 字段。

```java
LanguageModel model = aiModelService.languageModel("deepseek-prod-deepseek-chat").block();

GenerateTextRequest request = GenerateTextRequest.builder()
    .system("你是一个 helpful 的助手")
    .messages(List.of(
        ModelMessage.user("你好"),
        ModelMessage.assistant("你好！有什么可以帮你？"),
        ModelMessage.user("请介绍一下 Halo CMS")
    ))
    .temperature(0.7)
    .maxOutputTokens(2048)
    .topP(0.9)
    .providerOptions(Map.of(
        "openai", Map.of("seed", 42)
    ))
    .build();

model.generateText(request)
    .subscribe(result -> {
        System.out.println(result.getText());
        if (result.getUsage() != null) {
            System.out.println("Input tokens: " + result.getUsage().getInputTokens());
            System.out.println("Output tokens: " + result.getUsage().getOutputTokens());
            System.out.println("Total tokens: " + result.getUsage().getTotalTokens());
        }
    });
```

### 流式文本生成（推荐用于聊天场景）

```java
LanguageModel model = aiModelService.languageModel("deepseek-prod-deepseek-chat").block();

GenerateTextRequest request = GenerateTextRequest.builder()
    .system("你是一个 helpful 的助手")
    .messages(List.of(
        ModelMessage.user("你好")
    ))
    .temperature(0.7)
    .maxOutputTokens(2048)
    .build();

model.streamText(request)
    .subscribe(part -> {
        switch (part.getType()) {
            case TextStreamPart.TYPE_TEXT_DELTA -> System.out.print(part.getDelta());
            case TextStreamPart.TYPE_FINISH -> {
                System.out.println("\n[生成结束] " + part.getFinishReason());
                if (part.getUsage() != null) {
                    System.out.println("Input tokens: " + part.getUsage().getInputTokens());
                    System.out.println("Output tokens: " + part.getUsage().getOutputTokens());
                }
            }
            case TextStreamPart.TYPE_ERROR -> System.err.println("Error: " + part.getErrorText());
            default -> {
                // start、text-start、text-end 等协议事件通常无需处理
            }
        }
    });
```

### GenerateTextRequest 参数

| 字段 | 类型 | 说明 |
|------|------|------|
| `system` | `String` | 系统提示词，建议优先使用此字段表达系统指令 |
| `prompt` | `String` | 单轮用户提示词，与 `messages` 二选一 |
| `messages` | `List<ModelMessage>` | 多轮消息列表，与 `prompt` 二选一 |
| `temperature` | `Double` | 采样温度（0~2） |
| `maxOutputTokens` | `Integer` | 最大生成 token 数 |
| `topP` | `Double` | 核采样参数 |
| `topK` | `Integer` | Top-K 采样参数 |
| `presencePenalty` | `Double` | Presence penalty |
| `frequencyPenalty` | `Double` | Frequency penalty |
| `stopSequences` | `List<String>` | 停止序列 |
| `providerOptions` | `Map<String, Map<String, Object>>` | 按提供商命名空间分组的特定选项 |

`providerOptions` 必须按提供商分组，避免不同服务商的私有参数冲突，例如：

```java
Map.of(
    "openai", Map.of("seed", 42),
    "ollama", Map.of("num_ctx", 4096)
)
```

### ModelMessage 构造

V1 的消息内容采用类似 AI SDK `ModelMessage` 的 parts 结构，但当前只支持文本 part。简单文本消息可以直接使用工厂方法：

```java
ModelMessage.user("用户消息");
ModelMessage.assistant("助手消息");
ModelMessage.system("系统消息");
```

如果需要显式构造文本 part：

```java
ModelMessage.builder()
    .role(ModelMessageRole.USER)
    .content(List.of(ModelMessagePart.text("用户消息")))
    .build();
```

注意：`image`、`file`、`tool-call`、`tool-result` 等非文本 part 是后续扩展方向，V1 调用时会被拒绝。

### GenerateTextResult 返回值

| 字段 | 类型 | 说明 |
|------|------|------|
| `text` | `String` | 完整生成文本 |
| `finishReason` | `FinishReason` | 统一结束原因，如 `STOP`、`LENGTH`、`CONTENT_FILTER`、`UNKNOWN` |
| `rawFinishReason` | `String` | 提供商或 Spring AI 返回的原始结束原因 |
| `usage` | `LanguageModelUsage` | token 使用量，可能为空 |
| `providerMetadata` | `Map<String, Object>` | 可序列化的提供商元数据 |

### TextStreamPart 事件

`streamText()` 返回 `Flux<TextStreamPart>`，常见事件顺序如下：

```text
start
text-start
text-delta*
text-end
finish
```

| `type` | 说明 |
|--------|------|
| `start` | 一条模型响应开始，包含 `messageId` |
| `text-start` | 一个文本块开始，包含文本块 `id` |
| `text-delta` | 增量文本，读取 `delta` |
| `text-end` | 文本块结束 |
| `finish` | 生成结束，包含 `finishReason` 和可选 `usage` |
| `error` | 流式生成出错，读取 `errorText` |

## 嵌入模型调用

### 简单嵌入

```java
EmbeddingModel model = aiModelService.embeddingModel("openai-prod-text-embedding-3-small").block();

model.embedQuery("这是一段需要向量化的文本")
    .subscribe(embedding -> {
        // embedding 是 float[] 数组
        System.out.println("维度: " + embedding.length);
    });
```

### 批量嵌入

```java
EmbeddingModel model = aiModelService.embeddingModel("openai-prod-text-embedding-3-small").block();

List<String> texts = List.of("文本1", "文本2", "文本3");

model.embed(texts)
    .subscribe(response -> {
        List<float[]> embeddings = response.getEmbeddings();
        for (float[] embedding : embeddings) {
            System.out.println("维度: " + embedding.length);
        }
    });
```

### 高级嵌入请求

```java
EmbeddingRequest request = EmbeddingRequest.builder()
    .inputs(List.of("文本1", "文本2"))
    .dimensions(1536)          // 指定输出维度
    .maxBatchSize(100)         // 每批最大数量
    .providerOptions(Map.of()) // 提供商特定选项
    .build();

model.embed(request)
    .subscribe(response -> {
        // 处理结果
    });
```

### EmbeddingModel 辅助方法

```java
int maxBatch = model.maxEmbeddingsPerCall();  // 每次调用最大嵌入数量
boolean parallel = model.supportsParallelCalls(); // 是否支持并行调用
```

## 列出模型和提供商

```java
// 列出所有模型
aiModelService.listModels()
    .subscribe(models -> {
        for (ModelInfo model : models) {
            System.out.println(model.getName() + " - " + model.getDisplayName());
        }
    });

// 列出所有提供商
aiModelService.listProviders()
    .subscribe(providers -> {
        for (ProviderInfo provider : providers) {
            System.out.println(provider.getName() + " - " + provider.getDisplayName() + " (" + provider.getPhase() + ")");
        }
    });
```

## 异常处理

| 异常 | 说明 | 处理方式 |
|------|------|---------|
| `ModelNotFoundException` | 模型不存在 | 检查 modelName 是否正确，或引导用户配置模型 |
| `ProviderDisabledException` | 提供商未启用 | 提示用户启用对应提供商 |
| `ProviderApiException` | 提供商 API 调用失败 | 通常为网络或密钥问题，记录日志并提示用户检查配置 |

## 完整示例

```java
@Service
@RequiredArgsConstructor
public class MyAiService {

    private final ExtensionGetter extensionGetter;

    public Mono<String> summarize(String content) {
        return extensionGetter.getEnabledExtension(AiModelService.class)
            .flatMap(aiModelService -> aiModelService.languageModel("deepseek-prod-deepseek-chat"))
            .flatMap(model -> model.generateText("请总结以下内容：\n" + content))
            .map(GenerateTextResult::getText);
    }

    public Mono<float[]> vectorize(String text) {
        return extensionGetter.getEnabledExtension(AiModelService.class)
            .flatMap(aiModelService -> aiModelService.embeddingModel("openai-prod-text-embedding-3-small"))
            .flatMap(model -> model.embedQuery(text));
    }
}
```

## 注意事项

1. **确保本插件已启用**：调用方插件应在 `plugin.yaml` 中声明 `pluginDependencies.ai-foundation`，并通过 `ExtensionGetter.getEnabledExtension(AiModelService.class)` 获取服务；调用 `languageModel()` 或 `embeddingModel()` 时，如果对应的提供商未启用，会通过 `Mono` error channel 抛出 `ProviderDisabledException`
2. **异步 API**：`languageModel()` 和 `embeddingModel()` 返回 `Mono`，需要在响应式上下文中通过 `.flatMap()` 链式调用，或在非响应式上下文中调用 `.block()` 获取实例。`generateText()`、`streamText()`、`embed()` 等方法同样返回 `Mono`/`Flux`
3. **模型名称**：使用 `listModels()` 获取准确的 `name` 字段，不要硬编码 modelId
4. **跨插件调用**：由于 Halo 插件 ApplicationContext 隔离，不能通过 `@Autowired` 注入 `AiModelService`，请使用 Halo 的 `ExtensionGetter` 获取启用的 `AiModelService` 扩展实现
