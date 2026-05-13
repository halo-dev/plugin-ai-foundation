## Context

当前模型发现逻辑完全硬编码在 `ProviderDebugEndpoint` 中：`fetchModelsFromProviderApi()` 用 if-else 判断 `providerType`，`resolveBaseUrl()` 用 switch 硬编码 8 种 provider 的默认 URL，AiHubMix 的特殊 header 也作为 if 分支散落在 Endpoint 里。同时，`ProviderAdapter` 层已经拥有每个 provider 的完整配置（默认 baseUrl、API 构建方式、header 定制），却完全不参与模型发现。这导致扩展新 provider 时必须同时修改 Adapter 和 Endpoint 两处，逻辑重复且容易不一致。

## Goals / Non-Goals

**Goals:**
- 将模型发现逻辑统一到 `ProviderAdapter` 层，使每个 Adapter 自行负责"如何与自己的 provider 通信获取模型列表"
- 新增 `DiscoveredModel` 数据结构，包含 `modelId`、`displayName`、`capabilities`，让发现结果携带能力信息
- 通过命名启发式自动推断模型能力（chat/embedding），前端据此自动设置 `endpointType`
- `ProviderDebugEndpoint` 瘦化为纯委托层，不再包含任何 provider 特定的发现逻辑

**Non-Goals:**
- 不实现模型能力探测性调用（只做命名启发式，不尝试调 API 验证模型能力）
- 不在 `AiModel` Extension 上持久化 `capabilities` 字段（capabilities 仅用于发现阶段辅助推断 endpointType）
- 不支持 provider 自定义模型发现端点配置
- 不改变 `ProviderClientCache` 的缓存机制（模型发现不使用缓存）

## Decisions

### Decision 1: `discoverModels()` 放在 `ProviderAdapter` 接口上

**选择**: 在 `ProviderAdapter` 接口新增 `Mono<List<DiscoveredModel>> discoverModels()` 实例方法

**替代方案**:
- 独立的 `ModelDiscoveryService` — 引入新的服务层，但与 Adapter 已有的配置（baseUrl、headers、WebClient）重复
- 静态方法 — 无法利用 Adapter 实例已绑定的 provider 和 apiKey 信息

**理由**: Adapter 已经拥有 provider 的全部连接信息（baseUrl、apiKey、WebClient builder），让它在同一位置负责发现是最自然的职责归属。新 provider 只需实现 Adapter 即可，不需要再改 Endpoint。

### Decision 2: 默认 OpenAI-compatible 实现放在 `AbstractProviderAdapter`

**选择**: `AbstractProviderAdapter` 提供 `discoverModels()` 的默认实现，执行 `GET {baseUrl}/v1/models`，解析 `data[].id`

**替代方案**:
- 每个 Adapter 各自实现 — 大部分 provider 都是 OpenAI-compatible，重复代码多
- 抽取独立的 `OpenAiCompatibleDiscovery` 工具类 — 增加间接层，不如放在继承体系中

**理由**: 当前 9 种 provider 中只有 Ollama 的发现协议不同。默认实现覆盖 8/9 的场景，Ollama 覆盖即可。子类可通过覆盖 `customizeDiscoveryRequest(WebClient.RequestHeadersSpec)` 或直接覆盖 `discoverModels()` 来定制（如 AiHubMix 的额外 header）。

### Decision 3: 命名启发式推断 capabilities

**选择**: `AbstractProviderAdapter` 提供 `inferCapabilities(String modelId)` 方法，默认规则：
- modelId 包含 `embed` → `{EMBEDDING}`
- 其他 → `{CHAT}`

**替代方案**:
- 不推断，让用户手动选 — 体验差，用户容易选错
- 调用模型 API 探测 — 太慢（每个模型一次 API 调用），且有些 provider 不支持
- 解析 provider list API 返回的元数据 — OpenAI `/v1/models` 不返回能力信息，不可靠

**理由**: 命名启发式覆盖 95% 场景（embedding 模型几乎都带 `embed` 关键词），零额外 API 调用，子类可覆盖微调（如 SiliconFlow 的 `BAAI/bge-*` 命名规则）。

### Decision 4: 模型发现不使用 `ProviderClientCache`

**选择**: 模型发现时通过 `ProviderAdapterFactory.create(provider, apiKey)` 临时创建 Adapter，用完即弃

**替代方案**:
- 复用 `ProviderClientCache` — 缓存是面向高频推理设计的，模型发现频率低，且缓存 key 需要 apiKey，增加缓存管理的复杂度

**理由**: 模型发现是低频操作（用户手动触发），不需要缓存带来的性能优化。临时创建的 Adapter 构造成本低（只是 new 对象 + 构建 WebClient/RestClient builder），不存在性能问题。

### Decision 5: 前端自动推断 endpointType

**选择**: `ModelDiscoveryModal` 根据后端返回的 `capabilities` 自动设置 `endpointType`：
- `{CHAT}` → 对应 provider 的 chat endpointType（如 `openai-chat`、`ollama-chat`）
- `{EMBEDDING}` → 对应 provider 的 embedding endpointType（如 `openai-embedding`）

**替代方案**:
- 保留手动选择但预填值 — 折中方案，增加 UI 复杂度但保留灵活性
- 让后端直接返回 endpointType — 耦合了发现逻辑和模型存储逻辑

**理由**: capabilities 是更本质的能力描述，endpointType 是存储层的派生值。前端做这个映射更灵活，且逻辑简单。同时移除手动选择 UI，简化用户操作。

## Risks / Trade-offs

- **[命名启发式误判]** → 如 `deepseek-embedding` 会被正确识别为 embedding，但假设出现一个名称含 `embed` 但实际是 chat 模型的边缘情况会误判。缓解：用户创建后仍可在编辑页面手动修改 endpointType
- **[Ollama 模型能力判断粗糙]** → Ollama 的 `/api/tags` API 不返回模型能力信息，所有非 embed 模型默认归为 chat。缓解：Ollama 场景下 embedding 模型极少，误判概率低
- **[Adapter 构造需要 apiKey]** → `ProviderAdapterFactory.create()` 要求传入 apiKey，但某些 provider（如 Ollama）不需要。缓解：Ollama 传 null/空字符串即可，Adapter 内部已处理
