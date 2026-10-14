# 共享工具

简体中文 | [English](../en/tool-catalog.md)

AI Foundation 提供内置工具，也允许插件提供自己的工具。在「AI Foundation → 共享工具」中，
工具按来源插件分组展示，可以查看工具卡片、标签和状态，配置或启停工具。
在模型测试工作台中可以选择共享工具进行测试。

## 使用内置工具

| 工具     | 保存 ID                       | 说明                                 |
| -------- | ----------------------------- | ------------------------------------ |
| 站内搜索 | `ai-foundation/search_site`   | 搜索本站公开、已发布且未回收的内容   |
| 网页读取 | `ai-foundation/read_web_page` | 读取公网网页的标题和正文，不执行脚本 |
| 联网搜索 | `ai-foundation/web_search`    | 通过 Brave Search 搜索互联网         |

联网搜索需要先配置 Brave Search API 密钥。在该工具卡片上点击「配置」，创建或选择密钥后保存。
密钥存放在 Halo Secret 中，工具配置只保存密钥引用。

工具状态包括「未配置」「已启用」「已禁用」。未配置和已禁用的工具仍会展示，但不能在工具
选择器中选中。使用卡片上的开关可以控制工具是否允许使用；保存配置不会自动启用已禁用的工具。

网页读取支持 80、443 端口的公网 HTTP(S) 地址，不支持登录后才能查看的页面。遇到网页跳转时，
请提供跳转后的地址。返回的正文有长度上限，较长内容会被截断。

## 在插件设置中选择工具

在插件设置表单中使用 FormKit 输入类型 `aiToolSelector`：

```yaml
formSchema:
    - $formkit: aiToolSelector
      name: toolIds
      label: 共享工具
      help: 选择允许模型调用的共享工具
```

保存值为工具 ID 数组（`string[]`），例如 `['ai-foundation/search_site']`。
选择器使用紧凑的下拉多选，按工具来源分组，支持搜索。

「全选」或搜索后的「全选当前结果」只会新增当前结果中的可用工具，保留其他已选工具。
「全部取消」清空所有选择，包括不可用的选择。

已禁用和未配置的工具仍会展示，但不可选。已选工具失效时会保留原有选择并显示提示，
可以在完成配置、启用工具后继续使用，或手动移除。

| 可选属性            | 用途                         |
| ------------------- | ---------------------------- |
| `placeholder`       | 自定义选择框占位文本         |
| `fullWidth`         | 使选择框铺满容器             |
| `pluginName`        | 只显示指定插件提供的工具     |
| `category`          | 按注册分类值筛选，例如 `site` |
| `searchPlaceholder` | 自定义搜索框提示             |
| `disabled`          | 禁用选择和移除操作           |

自定义管理界面可以通过浏览器 SDK 读取工具目录：

```ts
import { listTools } from "@halo-dev/ai-foundation-sdk";

const tools = await listTools();
```

目录包含已禁用和未配置的工具。每项结果包含 `id`、来源插件、显示信息、标签、`enabled`、
`configured`、`status`，以及输入/输出 Schema 和可选的 FormKit `configurationSchema`。
目录不返回配置值，此接口需要管理员权限。

## 在后端调用所选工具

添加包含工具 API 的 Java SDK 依赖，并声明 AI Foundation 插件依赖。本地联调可以参考
[时钟插件示例](../../examples/plugin-tool-clock/README.md)中的 API JAR 依赖方式。

通过 `ExtensionGetter` 获取 `AiToolService`，在每次模型请求中将表单保存的工具 ID 重新解析为
`ToolDefinition`，再传给本次请求：

```java
return extensionGetter.getEnabledExtension(AiToolService.class)
    .switchIfEmpty(Mono.error(new IllegalStateException("AI Foundation 未启用")))
    .flatMap(service -> service.resolveTools(selectedToolIds))
    .flatMap(tools -> extensionGetter.getEnabledExtension(AiModelService.class)
        .flatMap(service -> service.languageModel(modelName))
        .flatMap(model -> model.generateText(GenerateTextRequest.builder()
            .prompt("搜索本站关于 Halo 的公开内容")
            .tools(tools)
            .stopWhen(StopCondition.stepCountIs(5))
            .build())));
```

保存和解析时使用 `ToolDescriptor.id`，格式为 `插件ID/本地工具名`。`name` 是模型调用名称，
不用于保存选择。空数组表示不使用共享工具；无效、重复或不可用的 ID 会导致解析失败。

业务接口应在服务端限制允许使用的工具；涉及权限的工具应在执行器中校验调用者权限。
审批、步骤控制等用法参见[工具调用指南](./sdk-core/tools-and-tool-calling.md)。

## 向 AI Foundation 提供工具

实现 `AiToolProvider`，返回有限的 `ToolRegistration` 列表。每个工具需要唯一的本地名称、
对象类型的输入 Schema 和执行器；名称支持 1–63 个字母、数字、下划线或连字符。
`tools()` 应尽快完成，仅声明工具，具体操作放在执行器中。

```java
public final class ClockToolProvider implements AiToolProvider {
    @Override
    public Flux<ToolRegistration> tools() {
        return Flux.just(ToolRegistration.builder()
            .displayName("当前时间")
            .displayDescription("查询当前 UTC 时间")
            .category("实用工具")
            .tags(List.of("时间", "只读"))
            .definition(ToolDefinition.builder()
                .name("current_time")
                .description("Get the current UTC time.")
                .inputSchema(JsonSchema.object().build())
                .executor(context -> Mono.just((Object) Map.of(
                    "utc", Instant.now().toString())))
                .build())
            .build());
    }
}
```

`displayName`、`displayDescription`、`category` 和 `tags` 用于管理界面；`ToolDefinition.description`
用于帮助模型理解工具的用途。同一插件内的工具名称不能重复。

### 为工具提供配置表单

通过 `ToolRegistration.configuration` 提供 `ToolConfiguration` 后，AI Foundation 会在工具卡片上
显示「配置」按钮，并在统一弹窗中渲染表单。插件可以直接使用 Halo 内置的 FormKit 输入，
例如 `text`、`select`、`secret`，只需提供 Schema 和配置读写回调。

- `schema`：`List<Map<String, Object>>`，工具配置表单的 FormKit Schema。
- `values`：`Supplier<Mono<Map<String, Object>>>`，读取当前配置的回调。
- `save`：`Function<Map<String, Object>, Mono<Void>>`，校验并保存配置的回调。

下面是一个需要配置问候语的工具，代码放在 Provider 的 `tools()` 方法中。`configService`
是**提供工具的插件自己实现的服务**，示例假设它提供 `Mono<Map<String, Object>> values()`
和 `Mono<Void> save(Map<String, Object> values)`，负责读取和保存本插件的配置。

```java
var configuration = ToolConfiguration.builder()
    .schema(List.of(Map.of(
        "$formkit", "text", "name", "greeting",
        "label", "问候语", "validation", "required")))
    .values(configService::values)
    .save(values -> {
        if (values.size() != 1
            || !(values.get("greeting") instanceof String greeting)
            || greeting.isBlank()) {
            return Mono.error(new IllegalArgumentException("请填写问候语"));
        }
        return configService.save(Map.of("greeting", greeting.trim()));
    })
    .build();

return configService.values()
    .defaultIfEmpty(Map.of())
    .map(current -> ToolRegistration.builder()
        .displayName("问候语")
        .configured(current.get("greeting") instanceof String greeting
            && !greeting.isBlank())
        .configuration(configuration)
        .definition(ToolDefinition.builder()
            .name("greeting")
            .description("Read the configured greeting.")
            .inputSchema(JsonSchema.object().build())
            .executor(context -> configService.values()
                .map(saved -> (Object) Map.of("greeting", saved.get("greeting"))))
            .build())
        .build())
    .flux();
```

`configured` 由插件判断是否满足配置要求；无需配置的工具默认为 `true`。未配置时，工具卡片
仍然显示，工具选择器中不可选。保存后，插件返回 `configured=true`，且工具处于启用状态时，
即可选择；已禁用的工具仍需手动启用。示例中的 `save` 同时检查允许的字段和字段值，
表单中的 `validation` 只负责前端提示。

外部服务需要密钥时，使用 Halo 的 `secret` 输入，配置中只保存 Secret 名称，`values` 回调
只返回该引用。插件在 `save` 中校验 Secret 及所需字段，在执行器中解析实际密钥。

### 注册 Provider

必选依赖 AI Foundation 的插件可以直接把 Provider 注册为 Spring Bean。可选依赖插件使用
条件配置类，并在 `plugin.yaml` 中声明 `ai-foundation?` 依赖：

```java
@Configuration
@ConditionalOnClass(name = "run.halo.aifoundation.tool.AiToolProvider")
public class ToolProviderConfiguration {
    @Bean
    ClockToolProvider clockToolProvider() {
        return new ClockToolProvider();
    }
}
```

使用条件配置时，不要给 Provider 类添加 `@Component`。完整的依赖声明、源码和构建步骤见
[时钟插件示例](../../examples/plugin-tool-clock/README.md)。
