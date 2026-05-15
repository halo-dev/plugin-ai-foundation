# AI Foundation 插件代码审查报告

**日期**: 2026-05-14  
**范围**: 后端 (`api/`, `app/`) + 前端 (`ui/src`)

---

## 严重问题（必须修复）

### ~~1. ProviderClientCache 缓存键不包含 modelId，导致同 provider 下不同模型共享 ChatModel~~ ✅ FIXED

**位置**: `app/src/main/java/run/halo/aifoundation/provider/support/ProviderClientCache.java:57-71`

缓存 key 仅为 `provider.metadata.name`，但 `buildChatModel` 接收了 `modelId` 参数并在 `defaultOptions` 中设置模型 ID。同一个 provider 下的多个模型会共享同一个 ChatModel 实例。

**后果**: 调用 `gpt-3.5-turbo` 可能实际使用的是缓存中的 `gpt-4` 的 ChatModel。

```java
// 当前问题代码
return chatModelCache.computeIfAbsent(name, k -> {
    return type.buildChatModel(provider, apiKey, modelId); // modelId 只在首次缓存时生效
});
```

**修复方向**: 缓存 key 应为 `providerName + "/" + modelId` 的复合 key。

---

### ~~2. ProviderClientCache 对 null EmbeddingModel 会抛出 NPE~~ ✅ FIXED

**位置**: `ProviderClientCache.java:64-71`

`AbstractAiProviderType.buildEmbeddingModel()` 默认返回 `null`（`DeepSeekProvider`、`KimiProvider`、`MiniMaxProvider` 等均不覆盖此方法）。`ConcurrentHashMap.computeIfAbsent()` 的 mapping function 返回 null 时会直接抛出 `NullPointerException`。

**后果**: 当为不支持 embedding 的 provider 创建 embedding model 时，NPE 在 `computeIfAbsent` 内部抛出，**不会走到 `AiModelServiceImpl` 中预期的 `if (springEmbeddingModel == null)` 判断**。

---

### ~~3. "连通性检查"根本没有实际调用远程 API~~ ✅ FIXED

**位置**: `app/src/main/java/run/halo/aifoundation/endpoint/ProviderConsoleEndpoint.java:243-256`

`performConnectivityCheck` 仅构建 `ChatModel` 实例就返回 "OK"，完全没有发送任何网络请求。

```java
// 问题代码：只是构建对象，没有实际调用
type.buildChatModel(provider, apiKey, "test");
return new ConnectivityResult(true, "OK");
```

**后果**: 用户看到"连通性正常"，但可能 API key 错误、网络不通或 baseUrl 不可达。

**修复方向**: 实际发送一条测试请求（如简单的 chat completion），验证远程服务可达性。

---


### ~~5. AiModelServiceImpl 完全不检查模型的 enabled 状态~~ ✅ FIXED

**位置**: `app/src/main/java/run/halo/aifoundation/service/AiModelServiceImpl.java:47-89`

`languageModel()` 和 `embeddingModel()` 检查了 provider 是否启用，但**完全没有检查 model 自身的 `spec.enabled`**。被禁用的模型仍可被消费插件调用。

**修复**: 新增 `ModelDisabledException`，在 `languageModel()` 和 `embeddingModel()` 中 fetch model 后立即检查 `aiModel.getSpec().isEnabled()`。

---

### ~~6. ModelForm 的 endpointType 是前端硬编码~~ ✅ FIXED

**位置**: `ui/src/views/components/ModelForm.vue:22-26`

```ts
const endpointTypeOptions = [
  { value: 'openai-chat', label: 'OpenAI Chat' },
  { value: 'openai-embedding', label: 'OpenAI Embedding' },
  { value: 'ollama-chat', label: 'Ollama Chat' },
]
```

与项目规范"**Frontend must not hardcode provider type lists**"冲突。用户可以为 Ollama provider 选择 `openai-embedding` 这种不合法的组合。

**修复方向**: 从当前 provider type 的 `supportedEndpointTypes` 动态获取选项。

---

## 中等问题（建议修复）

### ~~7. Provider 更新后缓存不会自动失效~~ ✅ FIXED

**位置**: `ProviderClientCache.java:73-77`

缓存仅在 `invalidate()` / `invalidateAll()` 时被清理，但没有任何地方在 Provider 被更新时调用这些方法。用户修改 API key 或 baseUrl 后，缓存中仍是旧的 ChatModel/EmbeddingModel 实例。

---

### ~~8. SecretResolver 对缺失/空 secret 返回空字符串而非报错~~ ✅ FIXED

**位置**: `app/src/main/java/run/halo/aifoundation/provider/support/SecretResolver.java:21-46`

当 secret 不存在或没有数据时返回 `""`，导致后续 API 调用传入空 API key，远程服务返回 401/403，错误信息是 provider 的原始响应而非清晰的"API key 未配置"。

**修复**: 当 `secretName` 非空但 secret 不存在、或 secret 的 `stringData` 为空时，抛出 `IllegalArgumentException` 并附带清晰错误信息。`null`/`blank` 的 `secretName` 仍返回 `""`（适配 Ollama 等无需 API key 的 provider）。

---

### ~~9. Model 和 Provider 的编辑/删除走 Core API，与创建走不同路径~~ ✅ FIXED

**位置**: 多个前端文件

| 操作 | 使用的 API |
|------|-----------|
| Provider 创建 | `aiConsoleApiClient.provider.createProvider()` (console API) |
| Provider 编辑 | `aiCoreApiClient.provider.updateAiProvider()` (core API) |
| Model 创建 | `aiConsoleApiClient.model.createModel()` (console API) |
| Model 删除 | `aiCoreApiClient.model.deleteAiModel()` (core API) |

**后果**: console endpoint 中的业务校验（provider type 校验、重复模型检查、关联模型检查）被绕过。

**修复**:
- ProviderConsoleEndpoint 新增 `GET /providers/{name}` 和 `PUT /providers/{name}`
- ModelConsoleEndpoint 新增 `DELETE /models/{name}`
- 前端 `ProviderDetail.vue`/`ProviderEditingModal.vue`/`ProviderModelListItem.vue` 改为使用 console API
- 重新生成 TypeScript API client

---

### ~~10. ModelsDiscoveryModal 导入模型无错误处理和回滚~~ ✅ FIXED

**位置**: `ui/src/views/components/ModelsDiscoveryModal.vue:52-87`

串行导入且无任何 try/catch，一旦中间某个模型创建失败，部分模型已导入、部分未导入，用户无法得知哪些成功了。

```ts
for (const model of data) {
  await aiConsoleApiClient.model.createModel({ ... }) // 第3个失败时前2个已创建
}
```

**修复**: 改为 `Promise.allSettled` 并行导入，分别统计成功/失败数量。失败时 Toast 展示具体失败的模型 ID 和错误原因。仅在导入成功时才刷新列表缓存。

---

### ~~11. Provider 创建时没有校验 requiresBaseUrl~~ ✅ FIXED (with #9)

**位置**: `app/src/main/java/run/halo/aifoundation/endpoint/ProviderConsoleEndpoint.java:117-139`

对于 `ollama` 和 `openailike` 这类 `requiresBaseUrl=true` 的 provider，缺少 baseUrl 时创建仍会通过，但后续调用 `resolveBaseUrl()` 时会抛 `IllegalArgumentException`。

**修复**: 随第9条重构 `validateAndSaveProvider` 时一并加入。创建和更新 provider 时都会检查 `type.requiresBaseUrl()`，未填 baseUrl 直接返回 400。

---

### ~~13. `generateName` 使用 `toLocaleLowerCase()` 依赖运行时 locale~~ ✅ FIXED

**位置**: `ui/src/views/components/ModelCreationModal.vue:25-26`, `ModelsDiscoveryModal.vue:59`

`toLocaleLowerCase()` 在某些 locale（如土耳其语）中将 `I` 转换为 `ı` 而非 `i`，可能导致资源名包含非预期字符。应使用 `toLowerCase()`。

**修复**: `ModelCreationModal.vue` 和 `ModelsDiscoveryModal.vue` 中的 `toLocaleLowerCase()` 均改为 `toLowerCase()`。

---

### ~~14. LanguageModelImpl.streamChat 吞掉错误~~ ✅ FIXED

**位置**: `app/src/main/java/run/halo/aifoundation/service/LanguageModelImpl.java:44-48`

```java
.doOnError(e -> log.error("[{}] Streaming error", providerType, e))
.onErrorReturn(buildErrorChunk("Stream error"))
```

流式错误被转换为 `ChunkType.ERROR`，调用方无法区分正常内容和真正错误，且丢失了原始异常信息。

**修复**: 移除 `.onErrorReturn(buildErrorChunk(...))`，让异常正常传播。调用方可以通过 `.onErrorResume` 或 subscribe 的 error handler 来处理异常。

---

### ~~15. validateModel 的唯一性检查非原子性~~ ✅ FIXED

**位置**: `app/src/main/java/run/halo/aifoundation/endpoint/ModelConsoleEndpoint.java:174-210`

`fetch provider` 和 `list existing models` 是两个独立查询。高并发下两个相同请求可能同时通过校验，创建重复模型。

**修复**:
- 后端 `createModel` 使用确定性 resource name（`providerName-modelId`），利用 Halo Extension 资源名的唯一性约束防止重复创建
- `validateModel` 移除低效的 `list` 重复检查，仅保留 spec 校验和 provider 存在性检查
- 新增 `checkModelUniqueness` 方法，通过 `fetch` 单条查询检查重复（比 `list` 所有模型快得多）
- 前端 `ModelCreationModal` 和 `ModelsDiscoveryModal` 不再传 `generateName`

---

## 数据设计问题

### 16. AiModel 的 endpointType 没有与 provider 支持的类型做关联校验

`endpointType` 是 required 字段，但后端允许存入 provider 不支持的 endpoint type。数据层面的约束缺失。

---

### 17. 缺少 provider→model 的级联删除机制

`ProviderConsoleEndpoint` 在删除 provider 前检查了关联模型，但这只是 console API 层的保护。如果通过 Halo Core API 直接删除 Provider，会留下"孤儿" AiModel 记录（`spec.providerName` 指向不存在的 provider）。

---

### 18. ModelInfo / ProviderInfo 缺少关键状态字段

- `ModelInfo` 没有 `enabled` 字段，消费插件无法知道模型是否可用。
- `ProviderInfo` 没有 `lastCheckedAt` 字段，UI 无法展示上次连通性检查时间。

---

### 19. AiProvider 没有 update console endpoint

Provider 的配置变更（修改 API key、baseUrl、代理设置）只能通过 Core API 的 generic update 完成，console endpoint 层无法做业务校验（如校验新 baseUrl 格式、校验新 apiKeySecretName 是否存在）。

---

### 20. AiModel 的 `supportedTextDelta` 使用 `Boolean` 包装类

**位置**: `app/src/main/java/run/halo/aifoundation/extension/AiModel.java:38`

```java
private Boolean supportedTextDelta;
```

可能为 null，但代码中没有 null 的默认处理逻辑。建议改为 `boolean` 基本类型，默认 `true`。

---

## 用户体验问题

### 21. 模型发现导入是串行的，大量模型时体验差

**位置**: `ui/src/views/components/ModelsDiscoveryModal.vue:57-80`

可以用 `Promise.allSettled` 并行导入，配合进度提示改善体验。

---

### 23. TestChatModal 不支持流式输出

UI 显示"测试对话"，但 `test-chat` endpoint 返回完整响应（`Mono<Map>`），不是 SSE 流。大模型长回复时用户需等待整个响应完成后才能看到内容。

---

### 24. ProviderListItem 没有显示 provider 的启用/禁用状态

**位置**: `ui/src/views/components/ProviderListItem.vue`

禁用的 provider 和启用的 provider 在列表中视觉完全一致，用户无法一眼区分。

---

### 25. ProviderDetail 连通性检查失败无 UI 反馈

**位置**: `ui/src/views/components/ProviderDetail.vue:88-101`

`testConnectivityMutation` 只有 `onSuccess`，没有 `onError`。检查失败时状态仍显示旧值，用户看不到错误信息。

---

### 26. 模型列表没有显示 enabled 状态和能力标签

**位置**: `ui/src/views/components/ProviderModelListItem.vue`

列表只显示 `displayName` 和 `modelId`，不显示模型是否启用、支持什么能力（chat/embedding），用户需要点编辑才能看到。

---

## 其他代码问题

### 27. AiModelServiceImpl 在 reactive 上下文中调用 `.block()`

**位置**: `app/src/main/java/run/halo/aifoundation/service/AiModelServiceImpl.java:121-137`

`fetchAiModel`、`fetchProvider`、`resolveApiKey` 都在 reactive pipeline 中调用了 `.block()`。虽然 `languageModel()` 和 `embeddingModel()` 是同步 API，但如果消费插件在 Netty event loop 线程中调用，会阻塞 IO 线程。

---

### 28. EmbeddingModelImpl 的 `partition` 方法对 `size <= 0` 处理不当

**位置**: `app/src/main/java/run/halo/aifoundation/service/EmbeddingModelImpl.java:130-139`

```java
if (size <= 0) {
  return List.of(list); // 把所有内容塞进一个 batch
}
```

当 `maxEmbeddingsPerCall` 为 0（如 DeepSeekProvider 返回 0）时，应禁用 batching 或抛异常，而不是把整个列表作为一个 batch 发送。