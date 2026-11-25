## 1. 诊断模型与错误归类

- [x] 1.1 在 `run.halo.aifoundation.provider.diagnostics` 新增 `ProviderCheckTarget`、`ProviderCheckResult`、`ProviderFailureCategory`、`ProviderSkipReason`、`ProviderHealthCheck`、`ProviderHealthReport`，并为字段补充 OpenAPI `@Schema` 说明；通过序列化测试验证缺失字段、枚举和空列表的读写兼容性。
- [x] 1.2 扩展 `AiProvider.AiProviderStatus`，追加 `failureCategory` 和 `checks`，保留 `phase`、`message`、`lastCheckedAt`；通过 Extension 序列化测试验证旧资源反序列化后 `checks` 为空、`failureCategory` 为 null。
- [x] 1.3 实现 `ProviderFailureAnalyzer`，遍历 cause 链识别 DNS、连接、TLS、代理、超时、401/403、404/405、429、5xx、Secret 绑定和配置错误，并提取 JSON 错误体的 `error.message`、`message` 或 `msg`；通过表驱动单测覆盖每个分类与未知异常兜底。
- [x] 1.4 为诊断文本增加 URL userinfo 脱敏，并统一使用 `AiFoundationDiagnostics.redactSensitiveText`；通过包含 `Authorization`、`api-key`、Bearer、URL 凭据和超长正文的测试验证输出不含凭据且长度受限。
- [x] 1.5 实现配置检查，覆盖供应商类型缺失、`requiresBaseUrl` 时 Base URL 缺失或非法、代理端口非法、Secret 绑定无法解析；通过单元测试验证配置失败时不会调用 `discoverModels()`。

## 2. 检查执行与状态持久化

- [x] 2.1 实现 `ProviderHealthChecker` 快速检查：执行一次带 30 秒超时的 `discoverModels()`，并根据成功或异常生成 `CONFIGURATION`、`NETWORK`、`AUTHENTICATION`、`MODEL_DISCOVERY` 四项结果；通过成功、401、403、404、405、429、5xx、连接失败、DNS 失败、TLS 失败和超时测试验证分类与跳过原因。
- [x] 2.2 实现深度检查：按供应商查询启用模型，通过 `AiModelService` 执行一次最小 Chat 和 Embedding 调用；通过无模型、供应商禁用、模型调用成功、模型调用失败场景验证 `PASSED`、`FAILED`、`SKIPPED` 及 `skipReason`。
- [x] 2.3 实现报告汇总：按固定顺序生成六项检查，计算 `phase`、`failureCategory` 和 `status.message`，并保证 `SKIPPED` 不影响整体状态；通过混合结果测试验证主失败项选择和摘要。
- [x] 2.4 实现状态持久化：写前重新读取 `AiProvider`，只替换 `status`，对乐观锁冲突最多重试 3 次；通过并发 spec 修改模拟验证不会覆盖 spec，并验证冲突重试。
- [x] 2.5 修改 `ProviderConsoleEndpoint`：移除旧的 `ConnectivityResult` 和 `Map` 响应，改用 `ProviderHealthChecker`，支持 `deep` 查询参数，失败仍返回 200；通过端点测试验证结构化响应、状态持久化和 404。
- [x] 2.6 在 `PUT providers/{name}` 中比较影响健康的字段，变更时把状态重置为 `UNKNOWN` 并清空 `checks`；通过 baseUrl、端点路径、Secret、代理、providerType 和仅显示名变更的测试验证重置边界。

## 3. API 契约与生成客户端

- [x] 3.1 为 `POST providers/{name}/connectivity` 的 `deep` 参数补充 OpenAPI 参数说明，并为结构化响应补充 operation 描述；通过 `generateOpenApiDocs` 检查 schema 包含六项目标和分类枚举。
- [x] 3.2 执行 `./gradlew generateApiClient` 重新生成 OpenAPI 和 `ui/src/api/generated`，确认没有手工修改生成文件；通过 `git diff` 检查仅包含预期新增字段和客户端方法签名。
- [x] 3.3 确认 `ProviderInfo`、`ModelOptionProvider` 和既有 Console 响应未增加诊断字段或改变语义；通过现有 `ModelOptionConsoleEndpointTest` 和 SDK 兼容性测试回归。

## 4. Console 展示

- [x] 4.1 新增 `ui/src/utils/provider-health.ts`，提供状态、分类、检查目标、跳过原因的中文标签与可操作提示，并处理旧数据缺失字段；通过 `provider-health.test.ts` 覆盖全部枚举和空状态。
- [x] 4.2 更新 `ProviderListItem.vue`：异常时展示分类和截断摘要，未检查时显示“未检查”，保持现有选中态和删除中状态；通过组件或工具测试验证展示文本和状态点。
- [x] 4.3 更新 `ProviderDetail.vue`：在顶部卡片的“状态信息” DescriptionItem 行内展示六项检查、HTTP 状态、耗时、失败提示和可展开的脱敏原始摘要，默认折叠；检查完成后使用返回值更新当前 Provider 状态。
- [x] 4.4 增加“深度检查（会实际调用模型）”入口，与默认“检查连通性”区分；通过类型检查和交互测试验证参数传递为 `deep=true`。
- [x] 4.5 供应商异常状态只由顶部“状态信息”行承载，不在 `<ProviderModelList>` 上方重复展示 `VAlert`；通过组件测试验证异常状态出现在状态信息行（含 error 状态点）。
- [x] 4.6 修复 `reloadProviderQueries` 对 `Ref` 的入参解包，确保检查完成后详情和列表刷新；更新 `use-providers-fetch.test.ts` 验证传入字符串查询键。
- [x] 4.7 运行 `corepack pnpm test:unit`、`corepack pnpm type-check`、`corepack pnpm lint`，确认前端回归和生成客户端类型通过。

## 5. 验证与文档

- [x] 5.1 运行 `./gradlew :app:test`、`./gradlew spotlessCheck` 和 `./gradlew build`，确认后端诊断、端点和既有供应商/模型测试全部通过。（`./gradlew build` 全绿：624 项前端单测、后端测试与 spotlessCheck 均通过；本机 Node 需附加 `NODE_OPTIONS=--localstorage-file=...` 才能运行既有的 UsageStatistics 测试。）
- [x] 5.2 在 Halo 开发实例中分别用错误 Base URL、错误 API Key、不可达地址、正常配置验证快速检查，并在有模型的供应商上验证深度检查；记录响应、状态和页面表现。（本地 Halo 2.26 dev 实例 + 本地 OpenAI 兼容 mock 验证：5 种快速检查场景、深度检查、状态重置、页面列表/详情/告警/展开原始摘要与检查后即时刷新均符合预期。）
- [x] 5.3 检查错误响应体、日志和 `AiProvider.status` 中不出现 API Key、Authorization 或 URL userinfo；保留测试证据。（dev 实例中扫描了 5 个供应商的 `status` 与 Halo 日志，未出现有效/无效 API Key 或 Bearer 值。）
- [x] 5.4 核对 proposal、design、tasks 与两份 spec delta 的行为一致，运行 OpenSpec 校验（若本机可用）并修正过期描述。（本机未安装 `openspec` CLI，已人工逐条核对 proposal/design/tasks/spec delta，未发现过期描述。）
