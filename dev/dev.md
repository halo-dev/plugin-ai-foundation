# AI 基础设施插件 — 开发者集成指南

本文档面向需要在 Halo 插件中调用 AI 能力的开发者。

## 前置条件

- 本插件（ai-foundation）已安装并启用
- 已至少配置一个 AI 提供商和模型（通过 Halo 控制台 > AI 基础设施）

## 添加依赖

在你的插件 `build.gradle` 中添加对 `api` 模块的依赖：

```gradle
repositories {
    mavenLocal()
}

dependencies {
    compileOnly 'run.halo.aifoundation:api:1.0.0-SNAPSHOT'
}
```

然后先在本项目根目录执行 `./gradlew :api:publishToMavenLocal`，将 SDK 发布到本地 Maven 仓库。

## 核心接口

### AiModelService

`AiModelService` 是入口服务，由于 Halo 插件间的 Spring ApplicationContext 隔离，不能通过 `@Autowired` 注入，需通过静态定位器 `AiServices` 获取：

```java
AiModelService aiModelService = AiServices.getModelService();
```

它提供以下方法：

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `languageModel(String modelName)` | `LanguageModel` | 获取指定语言模型实例 |
| `embeddingModel(String modelName)` | `EmbeddingModel` | 获取指定嵌入模型实例 |
| `listModels()` | `Mono<List<ModelInfo>>` | 列出所有已配置的模型 |
| `listProviders()` | `Mono<List<ProviderInfo>>` | 列出所有已配置的提供商 |

### modelName 的格式

`modelName` 是 **AiModel 的 metadata.name**（即 Halo Extension 资源的名称），格式为 `providerResourceName/modelId`，例如 `openai/gpt-4o`。

你可以通过 `listModels()` 获取所有可用模型的 `name` 字段。

## 语言模型调用

### 简单对话

```java
LanguageModel model = aiModelService.languageModel("deepseek/deepseek-chat");

model.chat("你好，请介绍一下 Halo CMS")
    .subscribe(response -> {
        System.out.println(response);
    });
```

### 流式对话（推荐用于聊天场景）

```java
LanguageModel model = aiModelService.languageModel("deepseek/deepseek-chat");

ChatRequest request = ChatRequest.builder()
    .messages(List.of(
        Message.system("你是一个 helpful 的助手"),
        Message.user("你好")
    ))
    .temperature(0.7)
    .maxTokens(2048)
    .build();

model.streamChat(request)
    .subscribe(chunk -> {
        switch (chunk.getType()) {
            case TEXT -> System.out.print(chunk.getContent());
            case FINISH -> {
                System.out.println("\n[对话结束]");
                if (chunk.getUsage() != null) {
                    System.out.println("Prompt tokens: " + chunk.getUsage().getPromptTokens());
                    System.out.println("Completion tokens: " + chunk.getUsage().getCompletionTokens());
                }
            }
            case ERROR -> System.err.println("Error: " + chunk.getContent());
        }
    });
```

### ChatRequest 参数

| 字段 | 类型 | 说明 |
|------|------|------|
| `messages` | `List<Message>` | 对话消息列表 |
| `temperature` | `Double` | 采样温度（0~2） |
| `maxTokens` | `Integer` | 最大生成 token 数 |
| `topP` | `Double` | 核采样参数 |
| `providerOptions` | `Map<String, Object>` | 提供商特定选项 |

### Message 构造

```java
Message.user("用户消息");
Message.assistant("助手消息");
Message.system("系统提示词");
```

## 嵌入模型调用

### 简单嵌入

```java
EmbeddingModel model = aiModelService.embeddingModel("openai/text-embedding-3-small");

model.embedQuery("这是一段需要向量化的文本")
    .subscribe(embedding -> {
        // embedding 是 float[] 数组
        System.out.println("维度: " + embedding.length);
    });
```

### 批量嵌入

```java
EmbeddingModel model = aiModelService.embeddingModel("openai/text-embedding-3-small");

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
public class MyAiService {

    public Mono<String> summarize(String content) {
        AiModelService aiModelService = AiServices.getModelService();
        LanguageModel model = aiModelService.languageModel("deepseek/deepseek-chat");
        return model.chat("请总结以下内容：\n" + content);
    }

    public Mono<float[]> vectorize(String text) {
        AiModelService aiModelService = AiServices.getModelService();
        EmbeddingModel model = aiModelService.embeddingModel("openai/text-embedding-3-small");
        return model.embedQuery(text);
    }
}
```

## 注意事项

1. **确保本插件已启用**：调用 `AiServices.getModelService()` 时，如果 ai-foundation 插件未启动，会抛出 `IllegalStateException`；调用 `languageModel()` 或 `embeddingModel()` 时，如果对应的提供商未启用，会抛出 `ProviderDisabledException`
2. **异步 API**：所有方法返回 `Mono` 或 `Flux`，请在响应式上下文中使用，或调用 `.block()`（不推荐在响应式链中阻塞）
3. **模型名称**：使用 `listModels()` 获取准确的 `name` 字段，不要硬编码 modelId
4. **跨插件调用**：由于 Halo 插件 ApplicationContext 隔离，不能通过 `@Autowired` 注入 `AiModelService`，请使用 `AiServices.getModelService()` 静态方法获取
