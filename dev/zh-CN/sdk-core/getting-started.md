# SDK Core：快速开始

简体中文 | [English](../../en/sdk-core/getting-started.md)

本页完成一条最小链路：声明插件依赖、获取 `AiModelService`、解析模型并生成文本。

## 1. 添加 Java 依赖

```groovy
dependencies {
    compileOnly "run.halo.aifoundation:api:1.0.0-SNAPSHOT"
    testImplementation "run.halo.aifoundation:api:1.0.0-SNAPSHOT"
}
```

使用 `SNAPSHOT` 时添加 Central Snapshots：

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
```

使用 `compileOnly`，不要把 API 重复打进调用方插件。AI Foundation 插件会在运行时提供相同
类型，重复打包可能让 Halo 的插件 classloader 中出现两份不兼容的类。

仓库的开发版本见根目录 `gradle.properties`。正式接入时应使用已发布版本，而不是照抄
本页的 `SNAPSHOT`。

## 2. 声明 Halo 插件依赖

在调用方的 `plugin.yaml` 中添加：

```yaml
spec:
    pluginDependencies:
        ai-foundation: "*"
```

这样 Halo 会保证 AI Foundation 在调用方插件之前可用。

## 3. 获取服务

Halo 插件拥有隔离的 Spring ApplicationContext，因此跨插件服务通过
`ExtensionGetter` 获取：

```java
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

@Service
@RequiredArgsConstructor
public class ArticleAiService {

    private final ExtensionGetter extensionGetter;

    private Mono<AiModelService> aiModelService() {
        return extensionGetter.getEnabledExtension(AiModelService.class);
    }
}
```

不要从另一个插件的 ApplicationContext 直接 `@Autowired AiModelService`。

## 4. 生成第一段文本

```java
import run.halo.aifoundation.chat.GenerateTextResult;

public Mono<String> summarize(String text) {
    return aiModelService()
        .flatMap(AiModelService::languageModel)
        .flatMap(model -> model.generateText("用一句话总结：\n" + text))
        .map(GenerateTextResult::getText);
}
```

`languageModel()` 使用管理员配置的默认语言模型。要使用指定模型：

```java
return aiModelService()
    .flatMap(service -> service.languageModel(modelName))
    .flatMap(model -> model.generateText("介绍 Halo CMS"));
```

`modelName` 是 `AiModel.metadata.name`。传入 `null` 或空白字符串时，行为与无参方法一样，
解析默认模型。

## 5. 解析其他模型

```java
Mono<EmbeddingModel> embedding = aiModelService()
    .flatMap(AiModelService::embeddingModel);

Mono<RerankingModel> reranker = aiModelService()
    .flatMap(AiModelService::rerankingModel);

Mono<ImageGenerationModel> image = aiModelService()
    .flatMap(AiModelService::imageGenerationModel);
```

每种模型都有对应的命名重载：

```java
service.embeddingModel(embeddingModelName);
service.rerankingModel(rerankModelName);
service.imageGenerationModel(imageModelName);
```

默认槽位未配置、模型不存在、模型被禁用或类型不匹配时，解析会失败。调用方应把这类错误转换为
业务可理解的提示，并让管理员完成配置。

## 6. 下一步

- [生成与流式文本](./generating-text.md)
- [生成结构化数据](./generating-structured-data.md)
- [工具调用与多步骤](./tools-and-tool-calling.md)
- [Embedding、Rerank 与 RAG](./embeddings-reranking-and-rag.md)
- [错误处理](./error-handling.md)
