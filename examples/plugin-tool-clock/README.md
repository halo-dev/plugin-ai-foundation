# 共享工具测试示例

安装后可测试第三方工具注册、工具卡片配置、全局启停，以及插件设置中的共享工具选择器。
需要 Halo 2.26 及以上版本，并启用包含共享工具功能的 AI Foundation。

| 工具 ID | 用途 | 配置要求 |
| --- | --- | --- |
| `tool-clock-example/current_time` | 查询当前 UTC 时间 | 无需配置 |
| `tool-clock-example/formatted_time` | 按指定时区和格式输出当前时间 | 配置时区后可选 |

## 构建与安装

使用 JDK 21 或可用的 Java 21 工具链，在仓库根目录执行：

```bash
./gradlew :api:jar
./gradlew -p examples/plugin-tool-clock build
```

在 Halo Console 的「插件」中上传安装生成的
`examples/plugin-tool-clock/build/libs/plugin-tool-clock-1.0.0.jar`，并启用「共享工具测试示例」。
使用前先安装并启用本分支构建的 AI Foundation。

示例通过 `compileOnly` 使用本地 API JAR，声明 `ai-foundation? >=1.0.0` 可选依赖。
工具选择器由 AI Foundation 注册，示例直接使用 Halo 插件设置，无需构建前端资源。

## 验证工具卡片

1. 打开「AI Foundation → 共享工具」，找到来源为 `tool-clock-example` 的两个工具。
2. 当前 UTC 时间工具可直接使用。格式化时间工具初始显示「未配置」，在选择器中不可选。
3. 点击格式化时间工具的「配置」，填写时区，例如 `Asia/Shanghai` 或 `UTC`，选择输出格式并保存。
   输出格式支持日期和时间（`datetime`）、日期（`date`）、时间（`time`）。
4. 保存后确认工具可用。通过卡片开关禁用工具，确认其状态变化，随后重新启用。
   保存配置不会自动启用已禁用的工具。

卡片表单使用 Halo 内置 FormKit 输入；时区和格式保存在本示例的 `clockTool` 配置组中，
字段为 `zoneId`、`format`。插件会在服务端校验时区和格式。

## 验证插件设置中的选择器

1. 在「插件」中打开「共享工具测试示例」的设置，进入「工具选择」。
2. 在「允许使用的共享工具」中选择当前时间工具、已完成配置的格式化时间工具，或其他来源的可用工具。
3. 保存设置并刷新页面，确认选择仍然保留。
4. 在共享工具页面禁用一个已选工具，再回到设置，确认该选择仍保留并标记为不可用。
   启用工具后可继续使用，也可以在选择器中手动移除。

选择结果按工具 ID 数组保存在 `selector.toolIds` 中，供后端通过 `AiToolService` 解析。
本示例提供工具与设置表单；如需体验模型调用，可在 AI Foundation 模型测试工作台中另行选择
这些工具，使用已配置的语言模型发送“查询当前时间”等提示。工作台的选择独立于本示例保存的设置。

第三方工具接入方式参见[共享工具指南](../../dev/zh-CN/tool-catalog.md)。
