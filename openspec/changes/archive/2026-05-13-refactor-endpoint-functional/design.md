## Context

当前项目 3 个 Endpoint 风格不一致：

- `ModelConsoleEndpoint`、`ProviderConsoleEndpoint`：注解式 `@RestController` + `@RequestMapping`
- `ProviderDebugEndpoint`：函数式 `CustomEndpoint`，但使用原生 `RouterFunctions.route()`（无 OpenAPI 文档生成）

Halo 社区主流插件（plugin-docsme、plugin-photos）已全部采用 `CustomEndpoint` + `SpringdocRouteBuilder.route()` 的统一函数式风格，可以内联声明 OpenAPI 元数据（operationId、tag、parameter、response 等）。

## Goals / Non-Goals

**Goals：**
- 统一所有 Endpoint 为函数式风格
- 统一使用 `SpringdocRouteBuilder.route()` 以获得内联 OpenAPI 文档能力
- 保持所有 API 的路径、请求/响应结构不变
- 测试覆盖保持不变

**Non-Goals：**
- 不修改业务逻辑（验证规则、错误处理、数据操作）
- 不修改 API URL 路径或数据结构
- 不涉及 Console UI
- 不引入新的依赖

## Decisions

### 1. 使用 `SpringdocRouteBuilder.route()` 而非 `RouterFunctions.route()`

`SpringdocRouteBuilder` 来自 `org.springdoc.webflux.core.fn.SpringdocRouteBuilder`，是 Springdoc 对 WebFlux 函数式路由的扩展。它允许在注册路由的同时内联声明 OpenAPI 元数据，无需依赖注解扫描。

替代方案：`RouterFunctions.route()`（原生 Spring）+ 单独的 OpenAPI 注解。不选，因为 Halo 社区插件已统一使用 `SpringdocRouteBuilder`，且内联声明更紧凑。

### 2. Handler 方法签名统一为 `Mono<ServerResponse> handler(ServerRequest)`

注解式中方法可以返回 `Flux<T>`、`Mono<T>`、`Mono<ResponseEntity<Void>>` 等。函数式风格统一返回 `Mono<ServerResponse>`，由 handler 内部调用 `ServerResponse.ok().bodyValue(...)` 或 `ServerResponse.noContent().build()` 构造响应。

### 3. `@ApiVersion` 注解替换为 `groupVersion()` 方法

`@ApiVersion` 是 Halo 为注解式 Controller 提供的快捷方式。`CustomEndpoint` 接口要求显式实现 `groupVersion()` 返回 `GroupVersion`。`ProviderDebugEndpoint` 已有此方法，新改的 Endpoint 需要补充。

### 4. 测试使用 `WebTestClient.bindToRouterFunction()`

注解式测试可以直接调用 endpoint 方法。函数式后需要绑定到 `RouterFunction`，通过 HTTP 语义（GET/POST/PUT/DELETE + URI）测试，更接近实际调用路径。

## Risks / Trade-offs

| 风险 | 缓解措施 |
|------|---------|
| `Flux<T>` 返回值改为 `Mono<ServerResponse>` 时，响应体包装可能遗漏（如 list 接口需要 bodyValue(List) 而非直接 bodyValue(Flux)） | 在 list handler 中先用 `.collectList()` 再 `bodyValue` |
| `ResponseEntity<Void>` 改为 `ServerResponse` 后，NO_CONTENT 状态码可能误为 200 | 显式调用 `.status(HttpStatus.NO_CONTENT).build()` |
| 测试重构后，mock 验证方式变化（从直接方法调用变为 WebTestClient exchange） | 保持测试覆盖点不变，逐一验证 |

## Migration Plan

1. 修改 `ModelConsoleEndpoint` → 验证编译通过
2. 修改 `ProviderConsoleEndpoint` → 验证编译通过
3. 修改 `ProviderDebugEndpoint` → 升级 builder
4. 重写 `ProviderConsoleEndpointTest`
5. 新增 `ModelConsoleEndpointTest`
6. 运行 `./gradlew test` 确认全部通过

## Open Questions

（无）
