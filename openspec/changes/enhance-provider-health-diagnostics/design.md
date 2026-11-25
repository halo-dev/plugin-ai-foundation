# 供应商健康诊断设计

## Context

动机见 `proposal.md`，外部行为见 `specs/provider-health-diagnostics/spec.md` 与 `specs/provider-debug-api/spec.md`。

现有连通性检查位于 `ProviderConsoleEndpoint`，只调用 `AiProviderType.discoverModels()`，把结果压成 `AiProvider.status` 的 `phase`、`message`、`lastCheckedAt` 三个字段。`SecretResolver` 的绑定错误不会进入结果，而是直接让接口失败；异常也没有归类，网络、认证、端点配置和供应商故障无法区分。

AI Foundation 已发布，`AiProvider` / `AiModel` 的 GVK、资源名、供应商引用、远端模型 ID、Secret 引用和公共 Java/npm 类型都必须保持稳定。本次只追加状态字段和 Console 诊断展示，不改变模型解析、发现结果、provider CRUD 校验或公共 SDK 契约。

## Goals / Non-Goals

**Goals:**

- 用一次真实探测得到可区分的配置、网络、认证和模型发现结论。
- 为常见失败给出稳定分类，并保留经过脱敏和截断的原始错误摘要。
- 把最近一次检查完整持久化到 `AiProvider.status`，让列表和详情都能读取。
- 深度检查显式开启，避免默认检查产生模型调用费用。
- 任何检查失败都不让 Secret、Authorization、URL userinfo 或完整响应体进入持久化数据与接口响应。

**Non-Goals:**

- 不做后台定时检查、告警或自动禁用供应商。
- 不为每个供应商实现专有错误码解析。
- 不新增 `AiModel` 状态字段或模型级健康资源。
- 不改变 `ProviderInfo` 的公共字段和语义。
- 不把检查结果用于阻断模型调用；它只用于诊断和展示。

## Decisions

### 扩展 `AiProvider.status`，不新建资源

新增字段全部可空或带默认值，属于向后兼容的追加：

```java
class AiProviderStatus {
    Phase phase = UNKNOWN;                     // 保留
    String message;                            // 保留，安全摘要
    Instant lastCheckedAt;                     // 保留
    ProviderFailureCategory failureCategory;   // 新增，成功时为 null
    List<ProviderHealthCheck> checks = List.of(); // 新增，最近一次检查的分项结果
}
```

诊断领域类型放在 `run.halo.aifoundation.provider.diagnostics`，与现有 `provider.support` 枚举被 `extension` 引用的做法一致：

```java
enum ProviderCheckTarget { CONFIGURATION, NETWORK, AUTHENTICATION, MODEL_DISCOVERY, CHAT, EMBEDDING }
enum ProviderCheckResult { PASSED, FAILED, SKIPPED }
enum ProviderFailureCategory {
    CONFIGURATION, AUTHENTICATION, NETWORK, RATE_LIMITED,
    PROVIDER_ERROR, CAPABILITY_UNSUPPORTED, UNKNOWN
}
enum ProviderSkipReason {
    NOT_REQUESTED, NO_MODEL, PROVIDER_DISABLED,
    BLOCKED_BY_PREVIOUS_FAILURE, UNSUPPORTED
}

class ProviderHealthCheck {
    ProviderCheckTarget target;
    ProviderCheckResult result;
    ProviderFailureCategory category;   // FAILED 时设置
    ProviderSkipReason skipReason;      // SKIPPED 时设置
    String message;                     // 安全摘要
    String detail;                      // 脱敏、截断后的原始摘要
    Integer httpStatus;
    Long latencyMs;
    Instant checkedAt;
}
```

不新建 `AiProviderHealth` 扩展资源：状态本来就在 `AiProvider.status`，列表接口已经返回它，`ProviderInfo.phase/lastCheckedAt` 也已经建立在该字段上。单独资源会带来同步、级联删除和读取成本，却没有额外收益。

### 一次探测推导网络、认证和模型发现

快速检查的执行顺序：

1. **配置**（纯本地，不发请求）：供应商类型是否存在、`requiresBaseUrl` 时 Base URL 是否合法、代理配置是否合法、Secret 绑定是否可解析。
2. **模型发现**：调用一次 `discoverModels()`，并施加检查专用的短超时。
3. 根据这次调用的结果或异常拆出三项：
   - 连接、DNS、TLS、代理、超时异常：`NETWORK` 失败；认证和模型发现标记为 `SKIPPED / BLOCKED_BY_PREVIOUS_FAILURE`。
   - HTTP 401/403：`NETWORK` 通过，`AUTHENTICATION` 失败，模型发现 `SKIPPED`。
   - HTTP 404/405：网络通过，模型发现失败，归类为 `CONFIGURATION` 或 `CAPABILITY_UNSUPPORTED`。
   - HTTP 429：网络与认证通过，模型发现失败，归类为 `RATE_LIMITED`。
   - HTTP 5xx：网络与认证通过，模型发现失败，归类为 `PROVIDER_ERROR`。
   - 成功：网络、认证、模型发现全部通过。

这样不会为了区分三个结论重复发请求，也不会把“模型发现接口不可用”误报成“整个供应商不可达”。需要注意：部分供应商的发现接口使用独立 host 或原生目录地址，因此 `NETWORK` 的结论描述为“发现接口所在端点的可达性”，UI 文案不能宣称已验证聊天端点。聊天/Embedding 端点由深度检查覆盖。

### 快速检查与显式深度检查

默认 `POST providers/{name}/connectivity` 只执行配置、网络、认证、模型发现，绝不调用模型。`deep=true` 时额外执行：

- `CHAT`：选择该供应商下第一个启用的语言模型，通过 `AiModelService.languageModel(name)` 发起 `prompt="ping"`、`maxOutputTokens=16`、非流式的生成请求。
- `EMBEDDING`：选择第一个启用的 Embedding 模型，通过 `AiModelService.embeddingModel(name)` 执行一次 `embedValue("ping")`。

没有对应模型、供应商类型不支持该能力或供应商被禁用时，记为 `SKIPPED` 并给出 `skipReason`。深度检查可能消耗供应商额度，Console 必须明确标注。

每次运行以本次执行的目标集合替换 `status.checks`，不合并历史结果；每一项都带 `checkedAt`，避免把不同时间的结论拼成同一份报告。

### 稳定分类码与前端本地化提示

后端只返回稳定枚举值，不返回中文提示。Console 在 `ui/src/utils/provider-health.ts` 中映射：

| 分类 | 中文提示 |
| --- | --- |
| `CONFIGURATION` | 请检查 Base URL、端点路径、代理和 API Key 绑定。 |
| `AUTHENTICATION` | 请确认 API Key 有效、未过期，或该供应商是否需要凭据。 |
| `NETWORK` | 请检查服务器出网、DNS、TLS 证书和代理配置。 |
| `RATE_LIMITED` | 供应商限流，请稍后重试。 |
| `PROVIDER_ERROR` | 供应商服务异常，请稍后重试或查看其状态页。 |
| `CAPABILITY_UNSUPPORTED` | 该供应商或模型不支持当前接口，请更换模型或调整端点配置。 |
| `UNKNOWN` | 请查看原始错误摘要并检查供应商配置。 |

`status.message` 仍是安全摘要（例如 `HTTP 401 Unauthorized`），供列表和 API 消费；厂商原始错误只放在 `checks[].detail`，UI 默认折叠展示。

### 错误归类

`ProviderFailureAnalyzer` 遍历异常 cause 链，识别：

- `ProviderHttpException`、`WebClientResponseException`、`ProviderApiException` 的状态码；
- `UnknownHostException`（DNS）、`ConnectException` / `WebClientRequestException`（连接）、`SSLException`（TLS）、`TimeoutException` / `SocketTimeoutException`（超时）；
- Secret 绑定错误与配置解析错误；
- 其余异常落到 `UNKNOWN`，只保留异常简单类名。

从 JSON 错误体优先提取 `error.message`、`message` 或 `msg` 作为摘要；提取失败时使用脱敏后的原始文本。所有文本经过 `AiFoundationDiagnostics.redactSensitiveText`，并补充 URL userinfo 脱敏。`status.message` 限制 300 字符，单条 `detail` 限制 1024 字符。

### 状态持久化与并发

- 检查期间不持有旧资源副本；写状态前重新 `fetch` 当前 `AiProvider`，只替换 `status`，避免覆盖并发修改的 spec。
- 对乐观锁冲突做有限重试（最多 3 次）。
- 检查施加 30 秒超时，避免沿用 5 分钟响应超时。
- 检查结果以 200 返回，即使所有检查都失败；只有供应商不存在返回 404，未知供应商类型返回 400。
- Secret 解析失败归类为 `AUTHENTICATION` 目标上的 `CONFIGURATION` 失败，并继续写状态。

### 配置变更使旧结果失效

`PUT providers/{name}` 在检测到以下字段变化时重置状态：`providerType`、`baseUrl`、聊天/Embedding/Rerank/Image 端点路径、`apiKeySecretName`、`proxyHost`、`proxyPort`。`maxInMemorySize`、显示名称和启用状态不触发重置。重置后的状态为：

```text
phase = UNKNOWN
message = "Provider configuration changed; run a connectivity check"
failureCategory = null
checks = []
```

保留 `lastCheckedAt` 以便 UI 显示“上次检查时间”，但不再展示旧的分项结果。只修改显示名称或启用状态不触发重置。

### 与 `AiModel` 的边界

不新增 `AiModel.status`。模型调用是否可能受影响由所属供应商的 `status.phase` 推导：供应商的异常状态统一由详情顶部「状态信息」行承载，不再在模型列表上方重复展示告警，模型列表本身也不复制供应商诊断字段。将来的模型级健康检查应定义独立的模型检查目标，不能复用供应商的 `checks`。

### 公共契约

- `ProviderInfo` 不新增字段；`phase` 和 `lastCheckedAt` 继续映射 `AiProvider.status`。
- `ModelOptionProvider.phase/lastCheckedAt` 保持不变。
- `AiProvider.status` 新增字段为可选，旧数据无需迁移。
- OpenAPI 和 TypeScript 客户端通过 `./gradlew generateApiClient` 重新生成。

## API 形状

```http
POST /apis/console.api.aifoundation.halo.run/v1alpha1/providers/{name}/connectivity?deep=false
```

响应示例：

```json
{
  "providerName": "openai-prod",
  "phase": "ERROR",
  "failureCategory": "AUTHENTICATION",
  "message": "HTTP 401 Unauthorized",
  "checkedAt": "2026-11-24T10:00:00Z",
  "checks": [
    { "target": "CONFIGURATION", "result": "PASSED", "checkedAt": "..." },
    { "target": "NETWORK", "result": "PASSED", "httpStatus": 401, "latencyMs": 420, "checkedAt": "..." },
    { "target": "AUTHENTICATION", "result": "FAILED", "category": "AUTHENTICATION",
      "httpStatus": 401, "message": "HTTP 401 Unauthorized",
      "detail": "{\"error\":{\"message\":\"Invalid API key\"}}", "checkedAt": "..." },
    { "target": "MODEL_DISCOVERY", "result": "SKIPPED",
      "skipReason": "BLOCKED_BY_PREVIOUS_FAILURE", "checkedAt": "..." },
    { "target": "CHAT", "result": "SKIPPED", "skipReason": "NOT_REQUESTED", "checkedAt": "..." },
    { "target": "EMBEDDING", "result": "SKIPPED", "skipReason": "NO_MODEL", "checkedAt": "..." }
  ]
}
```

## Console 行为

- **供应商列表**：状态点旁显示 `正常` / `异常 · 认证` / `未检查`；异常时在名称下方显示截断的 `status.message`，hover 展示全文。
- **供应商详情**：
  - 顶部展示状态、分类、上次检查时间。
  - 诊断结果放在顶部卡片的“状态信息”行内，默认折叠为一行摘要；展开后按固定顺序展示六类检查，包含结果、HTTP 状态、耗时和跳过原因。
  - 失败项展示中文可操作提示；有代理配置且为网络失败时，额外提示检查代理。
  - 原始错误摘要默认折叠，避免干扰普通管理员。
  - 不在模型列表上方重复展示告警横幅，异常信息只由顶部「状态信息」行承载。
- **按钮**：主按钮“检查连通性”执行快速检查；下拉项“深度检查（会实际调用模型）”执行 `deep=true`。
- **刷新**：检查请求成功后，用返回的报告更新当前 Provider 的 `status`，并刷新供应商列表；同时修复 `reloadProviderQueries` 对 Ref 的解包。

## Risks / Trade-offs

- **发现接口不代表聊天端点**：网络和认证结论来自发现请求，部分供应商的发现 host 与聊天 host 不同。通过文案明确“模型发现接口”，并用深度检查覆盖聊天/Embedding。
- **深度检查产生费用和延迟**：默认关闭，只在管理员明确选择时执行；单次调用限制在最小 token 数。
- **状态写入冲突**：检查耗时较长，写状态前重新读取资源并有限重试；极端情况下最后一次检查结果覆盖另一次检查结果，这是可接受的最后写入语义。
- **状态体积**：六条检查加脱敏摘要，单条 detail 上限 1KB，整体保持 KB 级。
- **分类精度**：只覆盖通用 HTTP 和传输层语义，不对供应商专有错误码做匹配；无法归类时返回 `UNKNOWN`，仍保留原始摘要。
- **旧状态兼容**：旧资源没有 `checks` 和 `failureCategory`，UI 必须按缺失字段渲染为“未检查/无分项结果”。

## Migration Plan

- 无数据迁移。新增状态字段为可选，旧 `AiProvider` 资源直接可读。
- 部署后首次检查会生成完整 `checks`；未检查的供应商保持 `UNKNOWN`。
- 生成 OpenAPI 与 TypeScript 客户端；Console 与后端必须同版本发布，旧 Console 会忽略新增字段。
- 不修改公共 Java/npm SDK，不需要消费者升级。

## Validation

- 后端单测覆盖：配置失败、DNS/连接/TLS/超时/代理失败、401/403、404/405、429、5xx、Secret 绑定失败、成功路径、深度检查跳过原因、脱敏与截断、乐观锁重试。
- 端点测试覆盖：结构化响应、失败仍返回 200、`deep` 参数、状态持久化、404、未知供应商类型。
- 前端单测覆盖：分类/目标/跳过原因的展示映射、列表摘要、详情诊断分组与折叠、无独立告警横幅。
- 回归：`./gradlew :app:test`、`./gradlew spotlessCheck`、`./gradlew generateApiClient`、`corepack pnpm test:unit`、`corepack pnpm type-check`、`corepack pnpm lint`。
- 可选人工验证：在 Halo 开发实例中用错误 Base URL、错误 API Key、不可达地址和正常配置各执行一次快速检查，并在有模型的供应商上执行一次深度检查。
