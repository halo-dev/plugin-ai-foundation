## Context

当前有三个 Endpoint：

- `ProviderConsoleEndpoint`：Provider CRUD + `provider-types` 查询
- `ModelConsoleEndpoint`：Model CRUD
- `ProviderDebugEndpoint`：`/providers/{name}/models`（远程发现+本地回退）、`/providers/{name}/connectivity`（连通性测试）、`/models/{name}/test-chat`（模型对话测试）

问题：
1. `provider-types` 是静态元数据，与 `AiProvider` CRUD 无关，却被放在同一个 Endpoint
2. `ProviderDebugEndpoint` 同时混入了 provider 级别和 model 级别的操作
3. `ProviderDebugEndpoint` 的路由用了前导 `/`，与其他 Endpoint 不一致
4. `discover-models` 的本地回退逻辑让"发现"和"查询"的语义混淆

## Goals / Non-Goals

**Goals：**
- 按资源归属重新分配端点，每个 Endpoint 只负责一种资源的操作
- 删除 `ProviderDebugEndpoint`，消除"Debug"这个兜底分类
- `discover-models` 只做远程发现，不再回退到本地模型
- 统一路由路径格式（全部使用相对路径）

**Non-Goals：**
- 不改任何业务逻辑（连通性检查、对话测试、模型发现的实现不变）
- 不改 API 的请求/响应体结构
- 不改权限控制（目前项目没有 RBAC）

## Decisions

**Endpoint 划分原则：按资源归属**
- ProviderType 资源（静态类型元数据）→ `ProviderTypeConsoleEndpoint`
- Provider 资源（CRUD + provider 级别的子资源操作）→ `ProviderConsoleEndpoint`
- Model 资源（CRUD + model 级别的子资源操作）→ `ModelConsoleEndpoint`
- 不保留 DebugEndpoint，"debug"不是资源

**`discover-models` 去掉本地回退**
- Rationale：`discover` 语义是"去远程发现"，回退到本地模型会误导调用方。如果远程失败，应该让调用方明确知道，而不是 silently 返回本地数据。
- 远程返回空或报错时，直接返回空列表或抛错。

**路由路径全部使用相对路径**
- Rationale：Halo 的 `CustomEndpoint` 在注册时会自动拼接 group version 前缀。使用绝对路径（前导 `/`）虽然功能正常，但会给阅读者造成困惑，看起来像是根路径。

## Risks / Trade-offs

- **[Risk]** 前端代码依赖旧的分组类名（如 `ProviderDebugApi`），重构后 import 路径会变。
  **Mitigation**：重新生成客户端后，前端需要批量替换 import。由于前端使用生成的客户端而非硬编码路径，改动范围可控。
- **[Risk]** `discover-models` 去掉回退后，某些不支持远程发现的 provider（如配置错误的 Ollama）会返回空列表，前端需要处理这种场景。
  **Mitigation**：前端在 provider 详情页中，如果 `discover-models` 返回空，可以提示用户手动添加模型。这比之前"返回了数据但不知道是远程还是本地的"更透明。
