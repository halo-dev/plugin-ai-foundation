## 1. ModelConsoleEndpoint 函数式改造

- [x] 1.1 移除 `@RestController`、`@RequestMapping`、`@ApiVersion` 注解，改为 `implements CustomEndpoint`
- [x] 1.2 添加 `groupVersion()` 方法返回 `GroupVersion.parseAPIVersion("console.api.aifoundation.halo.run/v1alpha1")`
- [x] 1.3 引入 `SpringdocRouteBuilder.route()` 和 OpenAPI builders（`responseBuilder`、`requestBodyBuilder`、`parameterBuilder`）
- [x] 1.4 添加 `endpoint()` 方法，注册 `GET /models`、`GET /models/{name}`、`POST /models`、`PUT /models/{name}`、`DELETE /models/{name}` 路由
- [x] 1.5 将 `list()` 改为 `private Mono<ServerResponse> listModels(ServerRequest)`，内部 `.collectList()` 后 `ServerResponse.ok().bodyValue(list)`
- [x] 1.6 将 `get()` 改为 `private Mono<ServerResponse> getModel(ServerRequest)`，返回 `ServerResponse.ok().bodyValue(model)` 或 404
- [x] 1.7 将 `create()` 改为 `private Mono<ServerResponse> createModel(ServerRequest)`，返回 `ServerResponse.ok().bodyValue(created)`
- [x] 1.8 将 `update()` 改为 `private Mono<ServerResponse> updateModel(ServerRequest)`，返回 `ServerResponse.ok().bodyValue(updated)`
- [x] 1.9 将 `delete()` 改为 `private Mono<ServerResponse> deleteModel(ServerRequest)`，返回 `ServerResponse.noContent().build()`
- [x] 1.10 清理不再使用的 import（`@RestController`、`@RequestMapping` 等 Spring MVC 注解）

## 2. ProviderConsoleEndpoint 函数式改造

- [x] 2.1 移除 `@RestController`、`@RequestMapping`、`@ApiVersion` 注解，改为 `implements CustomEndpoint`
- [x] 2.2 添加 `groupVersion()` 方法
- [x] 2.3 引入 `SpringdocRouteBuilder.route()` 和 OpenAPI builders
- [x] 2.4 添加 `endpoint()` 方法，注册 `GET /providers`、`GET /providers/{name}`、`POST /providers`、`PUT /providers/{name}`、`DELETE /providers/{name}` 路由
- [x] 2.5 将 handler 方法改为 `Mono<ServerResponse>` 签名，构造 `ServerResponse` 返回
- [x] 2.6 清理不再使用的 import

## 3. ProviderDebugEndpoint 升级

- [x] 3.1 将 `RouterFunctions.route()` 替换为 `SpringdocRouteBuilder.route()`
- [x] 3.2 为每条路由添加 OpenAPI 元数据（`operationId`、`description`、`tag`、`parameter`、`response`）
- [x] 3.3 清理不再使用的 `RouterFunctions` import

## 4. 测试重写与补充

- [x] 4.1 重写 `ProviderConsoleEndpointTest`：使用 `WebTestClient.bindToRouterFunction(endpoint.endpoint())`
- [x] 4.2 `ProviderConsoleEndpointTest` 覆盖：list、get(200/404)、create(200/400)、update(200/404)、delete(204/400/404)
- [x] 4.3 新增 `ModelConsoleEndpointTest`：使用 `WebTestClient.bindToRouterFunction()`
- [x] 4.4 `ModelConsoleEndpointTest` 覆盖：list、get(200/404)、create(200/400/409)、update(200/404/409)、delete(204/404)

## 5. 验证

- [x] 5.1 `./gradlew test` 全部通过
- [x] 5.2 编译无警告（检查未使用 import）
