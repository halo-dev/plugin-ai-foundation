## Why

模型发现逻辑当前硬编码在 `ProviderDebugEndpoint` 中，包含 if-else 分支判断 provider 类型、硬编码 URL 映射、重复的 baseUrl 解析逻辑。而 `ProviderAdapter` 层已经拥有每个 provider 的完整配置（baseUrl、headers、API 构建方式），却完全不参与模型发现。这导致扩展新 provider 时必须同时修改 Adapter 和 Endpoint 两处，逻辑重复且不一致。

## What Changes

- **ProviderAdapter 接口新增 `discoverModels()` 方法**，返回 `Mono<List<DiscoveredModel>>`，将模型发现逻辑统一到 Adapter 层
- **新增 `DiscoveredModel` record**，包含 `modelId`、`displayName`、`capabilities`（如 CHAT、EMBEDDING）
- **AbstractProviderAdapter 提供默认 OpenAI-compatible 发现实现**（`GET /v1/models`）和命名启发式能力推断（`inferCapabilities`）
- **OllamaAdapter 覆盖 `discoverModels()`**，使用 Ollama 特有的 `GET /api/tags` 端点
- **ProviderDebugEndpoint 瘦化**，`listProviderModels` 只负责获取 Adapter 并调用 `discoverModels()`，移除所有硬编码的 provider 分支逻辑
- **前端 ModelDiscoveryModal 根据 `capabilities` 自动设置 `endpointType`**，移除手动选择 endpointType 的 UI
- **模型发现时不使用 ProviderClientCache**，直接通过 `ProviderAdapterFactory.create()` 临时创建 Adapter

## Capabilities

### New Capabilities
- `adapter-model-discovery`: Adapter 层模型发现能力，包括 `ProviderAdapter.discoverModels()` 接口、`DiscoveredModel` 数据结构、默认 OpenAI-compatible 实现与命名启发式能力推断、Ollama 覆盖实现

### Modified Capabilities
- `provider-debug-api`: `GET /providers/{name}/models` 的实现从硬编码逻辑改为委托给 Adapter，返回结构新增 capabilities 字段
- `console-model-management`: ModelDiscoveryModal 根据 capabilities 自动推断 endpointType，移除手动选择

## Impact

- **Backend**: `ProviderAdapter` 接口变更（新增方法），`AbstractProviderAdapter` 新增默认实现，`OllamaAdapter` 覆盖实现，`ProviderDebugEndpoint` 简化，新增 `DiscoveredModel` record，移除 Endpoint 中的 `resolveBaseUrl`/`fetchOllamaModels`/`fetchModelsFromProviderApi` 等方法
- **Frontend**: `ModelDiscoveryModal.vue` 移除 endpointType 手动选择，改为根据 capabilities 自动推断；`useModels.ts` 响应结构适配
- **API**: `GET /providers/{name}/models` 响应中每个 model 对象新增 `capabilities` 字段（字符串数组，如 `["chat"]`、`["embedding"]`）
- **Non-goals**: 不实现 provider 自定义模型发现端点配置；不实现模型能力探测性调用（仅用命名启发式）；不在 AiModel Extension 上持久化 capabilities 字段（capabilities 仅用于发现阶段辅助推断 endpointType）
