# 共享工具

简体中文 | [English](../en/tool-catalog.md)

AI Foundation 提供内置工具，也允许插件提供自己的工具。在「AI Foundation → 共享工具」中，
工具按来源插件展示，相关操作收纳在可展开的工具组中，也可以查看、配置或启停单个工具。
在模型测试工作台中可以选择共享工具进行测试。

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
选择器使用紧凑的下拉多选，按来源和业务工具组展示，支持搜索工具、工具组和预设。

点击组前的复选框可整体选择或取消，也可展开后逐项调整。例如「文章」组包含列表、读取、创建草稿和编辑草稿，
可使用「只读查询」预设选择查询工具，或用「草稿编辑」预设添加编辑所需工具。
再次点击已选满的预设可取消其中的选择，其他已选工具会保留。
选择预设时只添加当前可用的工具，禁用或未配置的成员仍会展示，并提示不可用数量。
组选择和预设最终都保存具体工具 ID，工具组以后新增成员不会自动加入已有选择。

「全选」或搜索后的「全选当前结果」只会新增当前结果中的可用工具，保留其他已选工具。
「全部取消」清空所有选择，包括不可用的选择。

已禁用和未配置的工具仍会展示，但不可选。已选工具失效时会保留原有选择并显示提示，
可以在完成配置、启用工具后继续使用，或手动移除。

| 可选属性            | 用途                         |
| ------------------- | ---------------------------- |
| `placeholder`       | 自定义选择框占位文本         |
| `fullWidth`         | 使选择框铺满容器             |
| `pluginName`        | 只显示指定插件提供的工具     |
| `category`          | 按统一分类标识筛选，例如 `content` |
| `searchPlaceholder` | 自定义搜索框提示             |
| `disabled`          | 禁用选择和移除操作           |

自定义管理界面可以通过浏览器 SDK 读取工具目录：

```ts
import { listTools } from "@halo-dev/ai-foundation-sdk";

const tools = await listTools();
```

目录包含已禁用和未配置的工具。每项结果包含 `id`、来源插件、显示信息、标签、`enabled`、
`configured`、`status`，以及输入/输出 Schema、可选的 FormKit `configurationSchema` 和工具组 `group`。
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

需要获取全部可用工具定义时，可以不传工具 ID：

```java
return extensionGetter.getEnabledExtension(AiToolService.class)
    .switchIfEmpty(Mono.error(new IllegalStateException("AI Foundation 未启用")))
    .flatMap(AiToolService::resolveAllTools);
```

`resolveAllTools()` 返回已启用、配置完成的工具定义，并按照各工具的权限策略进行筛选；执行时仍须通过权限检查与确认要求。
如果只是展示完整目录，使用 `listTools()`；它还包含禁用和未配置的工具。
需要限制业务能力范围时，继续使用 `resolveTools(selectedToolIds)`。

## 向 AI Foundation 提供工具

实现 `AiToolProvider`，返回有限的 `ToolRegistration` 列表。每个工具需要唯一的本地名称、
对象类型的输入 Schema 和执行器；名称支持 1–63 个字母、数字、下划线或连字符。
`tools()` 应尽快完成，仅声明工具，具体操作放在执行器中。

同一个插件可以按业务提供多个 `AiToolProvider` Bean，无需再编写一个集中聚合的 Provider。
这些 Provider 共用插件的工具命名空间，因此本地工具名称不能重复；同一插件的分组预设可以引用不同 Provider 提供的工具。

```java
public final class ClockToolProvider implements AiToolProvider {
    @Override
    public Flux<ToolRegistration> tools() {
        return Flux.just(ToolRegistration.builder()
            .displayName("当前时间")
            .displayDescription("查询当前 UTC 时间")
            .category("utility")
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

### 声明工具权限

在注册工具时填写 `requiredPermissions`，由 AI Foundation 统一按当前用户的 Halo 权限校验：

```java
ToolRegistration.builder()
    .requiredPermissions(List.of(
        ToolPermission.named("content.halo.run", "posts", "get", "name")))
    .permissionDeniedBehavior(ToolPermissionDeniedBehavior.FILTER_ON_RESOLVE)
    // 其他工具定义……
    .build();
```

以上声明要求读取输入参数 `name` 指定的文章。列表等集合操作使用
`ToolPermission.resource("content.halo.run", "posts", "list")`；Halo 核心资源的 API 组填写空字符串。
批量名称可以使用 `items[].name` 这样的参数路径，每个目标都必须有权限。
列表中的权限必须全部满足；需要组合时，可以使用 `ToolPermission.allOf(...)` 或 `anyOf(...)`。
未声明权限的工具视为公开工具。

`permissionDeniedBehavior` 有两种选择：

- `FILTER_ON_RESOLVE`（默认）：解析时移除无权限工具，执行前再次校验。
- `DENY_ON_EXECUTE`：解析时保留工具；AI 调用时校验，权限不足则拒绝执行。

插件不需要实现工具级鉴权回调。文章归属、关联资源是否合法等业务约束仍由执行器处理；
例如，允许用户编辑本人文章并不代表可以编辑他人文章。工具确认也不会授予额外权限。

### 写入前要求用户审批

工具开发者在 `ToolDefinition` 中通过 `.requiresApproval(true)` 声明每次执行前需要用户确认，
也可以使用 `.approvalPolicy(ToolApprovalPolicy.always())`。按参数决定是否审批时，使用
`.approvalPredicate(context -> ...)`。

```java
ToolDefinition.builder()
    .name("update_draft")
    .description("Update an existing article draft after user confirmation.")
    .inputSchema(JsonSchema.object()
        .property("name", JsonSchema.string())
        .property("content", JsonSchema.string())
        .required("name", "content")
        .build())
    .requiresApproval(true)
    .executor(context -> updateDraft(context.getInput()))
    .build();
```

通过模型调用这类工具时，AI Foundation 先返回审批请求，暂不执行写入。调用方负责向用户展示
操作与参数；用户批准后提交审批响应，再继续生成。拒绝或尚未响应时不会执行该操作。
前端接入方式见[工具交互：审批](./sdk-ui/chatbot-tool-usage.md#审批)。

内置的草稿创建和编辑、发布和取消发布、分类标签维护、附件导入、评论回复和审核、菜单维护
均要求用户审批。审批通过后仍会检查当前用户的 Halo 权限。

### 分类、工具组和预设

分类用于跨插件筛选，使用以下统一标识：

| 标识 | 显示名称 |
| ---- | -------- |
| `content` | 内容 |
| `web` | 网络 |
| `media` | 媒体 |
| `site` | 站点 |
| `utility` | 实用 |
| `other` | 其他 |

更具体的信息使用工具组和 `tags` 表达，例如「文章」「Markdown」「草稿」。

通过可选的 `ToolRegistration.group` 声明业务工具组。同组工具使用相同的组定义：

```java
var articleGroup = ToolGroup.builder()
    .name("posts")
    .displayName("文章")
    .description("查询文章并维护草稿")
    .presets(List.of(ToolPreset.builder()
        .name("read")
        .displayName("文章检索")
        .toolNames(List.of("list_posts", "get_post"))
        .build()))
    .build();
```

将 `.group(articleGroup)` 设置到该组各工具的注册信息上。`name` 是插件内稳定的组标识；
预设的 `toolNames` 使用该来源、同一组中真实注册的工具本地名称，不能引用其他插件的工具。
工具目录中的组标识带有来源前缀，例如 `my-plugin/posts`。
未声明工具组的工具按单工具独立展示。

工具组不提供执行器或独立配置，配置和启停仍作用于具体工具。
预设用于帮助用户快速选择，不代表额外的执行权限，也不会增加传给模型的工具层级。

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

## 使用内置工具

| 工具     | 保存 ID                       | 说明                                 |
| -------- | ----------------------------- | ------------------------------------ |
| 站内搜索 | `ai-foundation/search_site`   | 搜索本站公开、已发布且未回收的内容   |
| 网页读取 | `ai-foundation/read_web_page` | 读取公网网页及 JSON、XML 等文本资源 |
| 联网搜索 | `ai-foundation/web_search`    | 通过 Brave Search 搜索互联网         |

联网搜索需要先配置 Brave Search API 密钥。在该工具卡片上点击「配置」，创建或选择密钥后保存。
密钥存放在 Halo Secret 中，工具配置只保存密钥引用。
使用可信 VPN/TUN 的 Fake-IP 模式时，也可在联网搜索工具自己的配置中开启「VPN 兼容模式」，默认关闭。

工具状态包括「未配置」「已启用」「已禁用」。未配置和已禁用的工具仍会展示，但不能在工具
选择器中选中。使用卡片上的开关可以控制工具是否允许使用；保存配置不会自动启用已禁用的工具。

网页读取支持 80、443 端口的公网 HTTP(S) 地址，可供各插件作为通用文本读取工具使用：

- HTML、XHTML：提取标题和正文，移除脚本等非正文内容，不执行 JavaScript。
- JSON（含 `+json`、NDJSON）、XML、RSS、Atom、Markdown、CSV、YAML、TOML 和其他常见文本：
  保留原始文本结构和换行。
- 自动处理压缩响应及最多 5 次安全跳转；支持响应声明的编码、常见 Unicode BOM，以及
  HTML、XML 中的编码声明。缺少内容类型或被标为通用下载的响应会检查内容是否为可读文本。

返回结果保留 `url`、`title`、`content`、`truncated`，并提供最终地址 `finalUrl` 和内容类型
`contentType`。在「共享工具 → 网页读取 → 配置」中可以调整：

| 配置 | 默认值 | 可配置范围 |
| ---- | ------ | ---------- |
| 下载大小上限 | 解压后 10 MiB | 1–100 MiB |
| 正文长度上限 | 100000 字符 | 1–2000000 字符 |

`truncated` 为 `true` 表示正文达到配置的长度上限后被截断，JSON、XML 等内容此时可能不完整。

该工具不提供登录会话，不支持必须执行 JavaScript 才能加载的内容，也不解析 PDF、Office、
图片、音视频或压缩包等二进制文件。

使用 VPN 的 Fake-IP 模式时，可在「共享工具 → 网页读取 → 配置」中开启「VPN 兼容模式」，
默认关闭。Halo 实际运行环境（包括容器）的网络也需经过 VPN/TUN。

兼容模式仅支持通过域名访问的 HTTPS 443 地址，适用于常见 Fake-IP 地址段 `198.18.0.0/15`
和 `fdfe:dcba:9876::/64`；其他私网地址和直接填写的 IP 地址仍不支持。

也可以在 Mihomo/Clash 的 `blacklist` 模式下，将目标域名加入 `fake-ip-filter`，使其返回真实
IP，此时无需开启兼容模式。参见 [Mihomo DNS 配置](https://wiki.metacubex.one/config/dns/#fake-ip-filter)。

## 使用 Halo 核心工具

以下工具按当前用户的 Halo 权限使用，可以组合为素材准备、内容编辑、发布和站点维护流程。
列表工具支持 `page`、`size` 和 `query`，默认每页 20 条，最多 100 条。

| 工具 | 保存 ID（前缀均为 `ai-foundation/`） | 用途 |
| ---- | ---------------------------------- | ---- |
| 文章列表、文章读取 | `list_posts`、`get_post` | 按关键词、分类或标签筛选文章，读取发布版或草稿 |
| 页面列表、页面读取 | `list_pages`、`get_page` | 查询独立页面并读取发布版或草稿 |
| 分类查询、标签查询 | `list_categories`、`list_tags` | 查找分类标签及其稳定标识 |
| 附件查询 | `list_attachments` | 查找已有素材，读取文件信息和可用的公开链接 |
| 站点信息 | `get_site_info` | 读取名称、介绍、地址、图标和语言 |
| 创建、编辑文章草稿 | `create_post_draft`、`update_post_draft` | 保存正文、分类、标签、封面和摘要 |
| 创建、编辑页面草稿 | `create_page_draft`、`update_page_draft` | 保存独立页面正文、封面和摘要 |
| 分类标签维护 | `create_category`、`update_category`、`create_tag`、`update_tag` | 创建、编辑分类和标签，支持分类层级 |
| 文章发布 | `compare_post_versions`、`publish_post`、`unpublish_post` | 比较草稿与发布版，确认后发布或取消发布 |
| 页面发布 | `compare_page_versions`、`publish_page`、`unpublish_page` | 比较并发布独立页面，或取消发布 |
| 附件管理 | `get_attachment`、`list_attachment_policies`、`list_attachment_groups`、`import_attachment` | 读取附件详情，选择存储位置并导入公网文件 |
| 评论管理 | `list_comments`、`get_comment`、`reply_comment`、`moderate_comment` | 查询评论和回复，回复或调整审核状态 |
| 导航查询 | `list_menus`、`get_menu` | 查询菜单及完整层级、菜单项版本 |
| 导航维护 | `create_menu`、`create_menu_item`、`update_menu_item`、`reorder_menu_items` | 创建菜单，维护自定义链接或内容链接，并调整同级顺序 |
| 文档读取 | `read_document` | 从公网地址或 Halo 附件提取 PDF、DOCX 文本 |

文章和页面列表默认只显示已发布的公开内容；设置 `includeUnpublished: true` 可包含草稿和
非公开内容，回收内容始终排除。分类查询的 `query` 匹配分类标识或别名，标签和附件还支持
显示名称。给文章设置分类、标签时，使用查询结果的 `name`，而非显示名称。

正文读取默认 `version: release`；读取草稿请指定 `version: head`。结果包含 `raw`、渲染正文
`content`、`rawType`、`metadataVersion` 和截断标记。文章读取、页面读取可以分别在工具卡片上
配置正文长度上限，默认 100000 字符。

草稿写入需要确认，以及当前用户对应的编辑权限。创建时提供
`title`、`slug` 和 HTML 正文（`raw` 或 `content`），默认 `rawType: HTML`。Markdown 需要同时
提供 `raw` 和渲染后的 HTML `content`，并使用支持该格式的编辑器。
编辑前先读取最新草稿，将其 `metadataVersion` 作为 `expectedVersion`；版本冲突时重新读取后
再决定修改。未提供的字段会保留，可以只修改标题、分类等信息而不重传正文。
更新正文时，省略 `rawType` 会沿用草稿的现有格式；Markdown 正文仍需同时提供原文和渲染后的 HTML。
更新只保存草稿，保留现有发布版；发布使用独立的发布工具。

分类和标签的查询结果也包含 `metadataVersion`，编辑时将其作为 `expectedVersion`。
创建时使用尚未占用的 `slug`；分类可以指定 `parent`，不指定则创建为顶层分类。

发布前先调用 `compare_post_versions` 或 `compare_page_versions`，查看草稿与发布版的差异。
发布时，将比较结果的 `metadataVersion`、`headSnapshot`、`headContentFingerprint` 分别作为
`expectedVersion`、`headSnapshot`、`expectedContentFingerprint` 传入。内容变化后需要重新比较并确认。
结果的 `reconciliationPending` 为 `true` 时，稍后重新查询发布状态。

导入附件时提供公网 `url` 和已有的 `policyName`，可同时指定 `groupName`、`filename`。
存储策略与分组工具仅返回选择所需的信息。导入工具卡片可独立配置下载上限，默认 20 MiB，
范围 1–100 MiB，并提供 VPN 兼容开关。

评论详情包含分页回复。回复使用当前登录用户的身份；如果主评论或被引用回复尚未审核，
需要相应的审核权限才能回复。新回复是否直接通过审核取决于当前用户的审核权限。
审核操作使用评论当前的 `metadataVersion`。

创建导航项时，可提供 `displayName`、`href`，也可以通过 `targetRef` 指向文章、页面、分类或标签。
编辑时提供菜单项的 `expectedVersion`。排序时传入当前同级的全部菜单项及其版本；遇到冲突先
重新读取菜单，再决定新的顺序。

### 读取 PDF、DOCX 文档

`read_document` 接受 `url` 或 `attachmentName`，两者只传一个。返回提取的正文和截断信息。
DOCX 采用轻量文本读取，支持常见段落、表格、页眉、页脚和脚注，不保证还原复杂排版、
自动编号或修订效果，也不会执行文档内的外部链接。
在工具卡片中可配置下载上限（默认 20 MiB）、正文长度（默认 200000 字符）、PDF 页数
（默认 200 页）及 VPN 兼容模式；最大分别为 100 MiB、2000000 字符和 2000 页。
超过正文或页数上限时会标记截断。加密、损坏的文档会返回错误；扫描 PDF 不提供 OCR，
没有文本层时会提示无法提取正文。HTML、JSON 等文本资源继续使用「网页读取」。

站内管理工具遵循当前用户的 Halo 权限，无需超级管理员身份。只读权限允许查询和读取，编辑、
发布、评论审核及附件导入需要相应权限。拥有投稿者或作者权限的用户只能操作自己的文章；
针对指定资源的授权不会扩大为读取全部资源的权限。内置管理工具默认在解析时排除无权限项，执行时仍会
检查目标资源。访客不能执行管理写入；公开站内搜索、站点信息和公网文档读取可公开使用，
通过附件标识读取文档则需要该附件的读取权限。

工具启用、工具选择和用户审批不会授予额外权限。插件开发者提供自己的工具时，也应校验当前
调用者对目标资源和操作的权限；不要在访客请求中注入管理员身份或使用固定管理员令牌代调。
