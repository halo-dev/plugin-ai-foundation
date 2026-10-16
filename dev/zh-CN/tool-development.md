# 工具代码维护规范

本规范用于维护 AI Foundation 的工具 SDK、工具服务和内置工具。插件接入方式见
[共享工具目录](./tool-catalog.md)。

## 按业务组织

- 按资源业务组织工具，而不是按“查询、草稿、发布”等技术操作拆分。工具的注册信息、输入 Schema、权限声明和
  执行方法放在同一个业务类中。例如，`HaloPostTools` 集中维护文章的列表、读取、草稿编辑、版本对比与发布；
  `HaloPageTools` 维护页面的对应操作，`HaloCategoryTools` 和 `HaloTagTools` 分别维护分类与标签。
- 附件列表、详情、存储策略、分组和导入归入 `HaloAttachmentTools`；站点信息和站内搜索归入 `HaloSiteTools`。
  网页读取、联网搜索和文档读取保留各自 Provider。执行器或配置存储等协作类可以独立，避免再次集中注册工具。
- 每个业务类直接实现 `AiToolProvider` 并声明为 Spring `@Component`。工具服务通过
  `List<AiToolProvider>` 自动收集当前插件中的 Provider，不维护中央注册列表，也不按工具 ID 推断权限。
- 第三方插件提供的 `AiToolProvider` 仍由工具服务通过 Halo 的 `ExtensionGetter` 发现。新增内置工具集合只需
  提供本地 Provider 组件，无须修改工具服务或额外的聚合类。
- 公共辅助类只复用稳定结构，如分组、注册默认值和基础字段 Schema。避免引入一个新的工具总目录。
- 改动注册信息时，检查工具 ID、描述、分组预设、权限和审批策略；描述应明确适用场景、参数来源及相关工具。

## Javadoc

每个 Java 类型应使用标准 Javadoc，说明职责、关键约束和有意义的用法。公开方法说明参数、返回值和
调用方需要处理的错误；存在类型、方法或字段引用时使用 `{@link ...}` 或 `{@code ...}`。
纯粹重述类名、方法名的注释不能替代说明。示例应使用有效 API 和实际支持的输入：

```java
/**
 * Reads one article's current draft for a subsequent edit.
 *
 * <pre>{@code
 * tools.getPost(Map.of("name", "article-name", "version", "head"));
 * }</pre>
 *
 * @param input article name and requested content version
 * @return content and metadataVersion; a later update must use that version
 */
```

## 控制流

- 循环最多嵌套两层，包含 `for`、增强 `for`、`while` 和 `do-while` 的混合嵌套。
- 先处理无效输入和不满足条件的分支，再进入正常路径。能够返回、抛错或继续下一次循环时，减少后续嵌套。
- 将承担不同职责的判断拆成命名明确的校验方法；不要仅用多层 Stream 或 Lambda 隐藏复杂度。
- 拆分响应式代码时保持订阅时机、取消处理和副作用顺序。批量写入前检查全部目标权限；版本冲突需要重新读取。

## 区分两种 Schema

工具输入是 JSON Schema，用于描述模型可传入的参数，和配置表单分别维护。

工具配置表单使用 Halo 原生 FormKit Schema，与支付方式配置等扩展保持相同的
`List<Map<String, Object>>` 契约。内置表单位于
`app/src/main/resources/tools/configuration/*.yaml`，由 `ToolConfigurationSchemas` 加载：

```yaml
schema:
  - $formkit: number
    name: maxContentCharacters
    label: 正文长度上限（字符）
    value: 100000
    number: integer
    min: 1
    max: 2000000
    validation: required|number|between:1,2000000
```

数值、布尔值使用 YAML 原生类型。默认值、取值范围及保存校验由后端负责；表单校验用于反馈。
密钥使用 Halo Secret 引用，禁止保存明文凭据。第三方插件可以自行提供标准 FormKit Schema，
不依赖内置工具的资源加载辅助类。

## 格式化与验证

工具 SDK、实现、测试和时钟示例统一使用 Halo 相同版本的 Spotless 和 Palantir Java Format，
同时格式化 Javadoc：

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:test :api:javadoc
```

注册拆分应验证完整目录和执行分派；权限改动应覆盖匿名、角色授权、名称限制和执行时权限变化；
配置资源改动应验证字段类型、默认值、保存校验和打包资源。
