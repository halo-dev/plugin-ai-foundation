## Why

当前三个 Endpoint 的职责边界模糊：`provider-types` 作为静态元数据查询被放在 `ProviderConsoleEndpoint` 的 Provider CRUD 里；`ProviderDebugEndpoint` 同时承载了 provider 级别的远程模型发现、连通性测试和 model 级别的对话测试；路由路径格式也不一致（有的带前导 `/` 有的不带）。随着功能增加，这种混合会导致维护困难、API 语义不清、前端生成的客户端代码组织混乱。

## What Changes

- **新建 `ProviderTypeConsoleEndpoint`**：将 `GET provider-types` 从 `ProviderConsoleEndpoint` 迁出，独立为 `ProviderTypeConsoleEndpoint`。provider-types 是系统级静态元数据，与 `AiProvider` 的 CRUD 没有必然关联。
- **删除 `ProviderDebugEndpoint`**：将其中三个端点按资源归属重新分配：
  - `GET /providers/{name}/models` → `ProviderConsoleEndpoint` 的 `GET providers/{name}/discover-models`，去掉本地回退逻辑，只做远程模型发现。
  - `POST /providers/{name}/connectivity` → `ProviderConsoleEndpoint` 的 `POST providers/{name}/connectivity`。
  - `POST /models/{name}/test-chat` → `ModelConsoleEndpoint` 的 `POST models/{name}/test-chat`。
- **统一路由路径格式**：去掉 `ProviderDebugEndpoint` 遗留的前导 `/`，所有路径使用相对路径。
- **重新生成 TypeScript API 客户端**：端点重组后，`./gradlew generateApiClient` 会生成新的 API 模块划分，前端需同步更新 import。

## Capabilities

### New Capabilities
<!-- 本次重构不引入新的能力，只是重组现有端点的归属。 -->

### Modified Capabilities
<!-- 本次重构不改变任何 spec 级别的行为要求，只调整实现层面的端点组织结构。 -->

## Impact

- **Backend**：`app/src/main/java/run/halo/aifoundation/endpoint/` 下的三个文件被重组为两个（新建 ProviderTypeConsoleEndpoint，删除 ProviderDebugEndpoint，修改 ProviderConsoleEndpoint 和 ModelConsoleEndpoint）。
- **Frontend**：`ui/src/api/generated/` 下的自动生成的客户端文件会重新组织，前端视图中的 API import 路径需要同步更新。
- **API 路径变化**：
  - `/providers/{name}/models` → `/providers/{name}/discover-models`
  - 原 `ProviderDebugEndpoint` 下的 `connectivity` 和 `test-chat` 路径不变，但归属的 tag/客户端类会变
