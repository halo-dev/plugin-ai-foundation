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

### 简单对话

```java
LanguageModel model = aiModelService.languageModel("deepseek-prod-deepseek-chat").block();

model.chat("你好，请介绍一下 Halo CMS")
    .subscribe(response -> {
        System.out.println(response);
    });
```

### 流式对话（推荐用于聊天场景）

```java
LanguageModel model = aiModelService.languageModel("deepseek-prod-deepseek-chat").block();

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
            .flatMap(model -> model.chat("请总结以下内容：\n" + content));
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
2. **异步 API**：`languageModel()` 和 `embeddingModel()` 返回 `Mono`，需要在响应式上下文中通过 `.flatMap()` 链式调用，或在非响应式上下文中调用 `.block()` 获取实例。`chat()`、`streamChat()`、`embed()` 等方法同样返回 `Mono`/`Flux`
3. **模型名称**：使用 `listModels()` 获取准确的 `name` 字段，不要硬编码 modelId
4. **跨插件调用**：由于 Halo 插件 ApplicationContext 隔离，不能通过 `@Autowired` 注入 `AiModelService`，请使用 Halo 的 `ExtensionGetter` 获取启用的 `AiModelService` 扩展实现

## 前端：AiModelSelector 模型选择器组件

`AiModelSelector` 是本插件提供的前端 Vue 组件，注册为全局组件名 `AiModelSelector`。其他插件可以在插件设置页的 FormKit Schema 中直接使用，让用户在 UI 上选择一个已配置的 AI 模型。

**保存的值**：选中后保存的是 `AiModel` 资源的 `metadata.name`，即上文 `modelName` 格式。可直接传给 `AiModelService.languageModel(modelName)` 或 `embeddingModel(modelName)` 使用。

### 在 FormKit Schema 中使用（推荐）

在插件设置页的 `configMaps` schema 中，通过 `$cmp` 方式引用：

```yaml
formSchema:
  - $cmp: AiModelSelector
    props:
      name: languageModelName
      label: 语言模型
      help: 选择用于文章生成的语言模型
```

带筛选条件（只显示语言模型）：

```yaml
formSchema:
  - $cmp: AiModelSelector
    props:
      name: languageModelName
      label: 语言模型
      modelType: LANGUAGE
      clearable: true
      placeholder: 请选择语言模型

  - $cmp: AiModelSelector
    props:
      name: embeddingModelName
      label: 嵌入模型
      modelType: EMBEDDING
```

### 全部可用 Props

| Prop | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | `string` | — | **必填**。对应 FormKit 表单字段名，决定保存到 ConfigMap 的 key |
| `label` | `string` | — | 字段标签文字 |
| `help` | `string` | — | 字段说明文字（显示在选择器下方） |
| `modelValue` | `string` | — | 当前选中的模型名（`v-model` 绑定用） |
| `modelType` | `string` | — | 筛选模型类型，可选值：`LANGUAGE` / `EMBEDDING` |
| `providerName` | `string` | — | 只显示指定提供商名称（`AiProvider` 的 `metadata.name`）下的模型 |
| `providerType` | `string` | — | 只显示指定提供商类型（如 `openai`、`deepseek`）下的模型 |
| `enabled` | `boolean` | — | 若为 `true`，只显示已启用的模型；不传则显示全部 |
| `available` | `boolean` | `true` | 若为 `true`，只显示可用（已就绪）的模型 |
| `requiredFeatures` | `string \| string[]` | — | 只显示具备指定 feature 的模型，如 `"FUNCTION_CALL"` 或 `["VISION", "FUNCTION_CALL"]` |
| `placeholder` | `string` | `请选择模型` | 未选中时的占位文字 |
| `searchPlaceholder` | `string` | `搜索...` | 搜索框占位文字 |
| `clearable` | `boolean` | `true` | 是否显示清除按钮 |
| `disabled` | `boolean` | `false` | 是否禁用 |

### 在 Vue 组件中使用（v-model）

如果不是通过 FormKit Schema，而是直接在 Vue 模板中使用：

```vue
<template>
  <AiModelSelector
    v-model="selectedModel"
    label="语言模型"
    model-type="LANGUAGE"
    help="用于文章摘要生成"
  />
</template>

<script setup lang="ts">
import { ref } from 'vue'

const selectedModel = ref<string>()
</script>
```

> 直接在 Vue 模板中使用时，无需传 `name` prop，通过 `v-model` 双向绑定即可。
