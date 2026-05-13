## ADDED Requirements

（无新增 spec-level 行为要求）

## MODIFIED Requirements

（无需求变更，仅实现风格统一）

## REMOVED Requirements

（无）

---

本次变更为纯重构，所有 Endpoint 的 URL 路径、请求/响应数据结构、业务逻辑（验证规则、错误处理）均保持不变。变更范围仅限于实现风格：从注解式 `@RestController` 统一为函数式 `CustomEndpoint` + `SpringdocRouteBuilder`。
