## Why

当前项目 Endpoint 风格不统一：`ModelConsoleEndpoint` 和 `ProviderConsoleEndpoint` 使用 `@RestController` + `@RequestMapping` 注解式风格，而 `ProviderDebugEndpoint` 已使用 `CustomEndpoint` 但搭配的是原生 `RouterFunctions.route()`。Halo 社区主流插件（plugin-docsme、plugin-photos）已全部采用 `CustomEndpoint` + `SpringdocRouteBuilder.route()` 的函数式风格，可内联生成 OpenAPI 文档并保持代码一致性。本次变更将所有 Endpoint 统一为社区惯用的函数式风格。

## What Changes

- **ModelConsoleEndpoint**：`@RestController` → `implements CustomEndpoint`，路由改为 `SpringdocRouteBuilder.route()`，返回值统一为 `Mono<ServerResponse>`
- **ProviderConsoleEndpoint**：`@RestController` → `implements CustomEndpoint`，路由改为 `SpringdocRouteBuilder.route()`，返回值统一为 `Mono<ServerResponse>`
- **ProviderDebugEndpoint**：`RouterFunctions.route()` → `SpringdocRouteBuilder.route()`，补充 OpenAPI 文档元数据
- **ProviderConsoleEndpointTest**：重写为 `WebTestClient.bindToRouterFunction()` 风格
- **ModelConsoleEndpointTest**：新增，覆盖 list/get/create/update/delete

## Capabilities

### New Capabilities
<!-- 无新增 spec-level 行为，仅重构实现风格 -->
（无）

### Modified Capabilities
<!-- 无需求变更，仅重构实现风格 -->
（无）

## Impact

- `app/src/main/java/run/halo/aifoundation/endpoint/ModelConsoleEndpoint.java`
- `app/src/main/java/run/halo/aifoundation/endpoint/ProviderConsoleEndpoint.java`
- `app/src/main/java/run/halo/aifoundation/endpoint/ProviderDebugEndpoint.java`
- `app/src/test/java/run/halo/aifoundation/endpoint/ProviderConsoleEndpointTest.java`
- `app/src/test/java/run/halo/aifoundation/endpoint/ModelConsoleEndpointTest.java`（新增）

## Non-Goals

- 不修改任何 API 的 URL 路径或请求/响应数据结构
- 不修改业务逻辑（验证规则、错误处理逻辑保持不变）
- 不涉及 Console UI 代码
