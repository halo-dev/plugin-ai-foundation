# 模型能力目录

「从供应商获取模型」始终使用供应商自己的模型发现接口。后台下载的
[models.dev v2 目录](https://models.dev/experimental/v2.0/api.json)补全这些结果中的语言模型能力、
参数映射和约束。它不会把公共目录中的模型加入账号可用列表，也不参与价格计算。
模型调用只读取导入或编辑时保存的配置，不实时访问目录。

## 同步与降级

- 插件启动时恢复 `plugins/ai-foundation/models-dev-cache.json`，随后在后台尝试一次远程同步，
  即使磁盘缓存仍在有效期内，也会检查远程数据。
- 此后每 24 小时检查一次，导入弹窗支持「刷新能力目录」。并发刷新共用一次请求。
- 请求仅访问固定的 HTTPS 地址，不附带供应商密钥，不跟随重定向；超时 15 秒，响应上限 16 MiB。
- 请求启用 gzip，使用 ETag / `If-None-Match` 条件请求；304 复用已有快照。
- 当前实现仍下载完整 v2 目录，没有按模型发起查询。本地仅缓存实际使用的能力、协议和限制字段，
  不保存价格、品牌、SDK 包名等信息，并仅保留本项目身份白名单中的供应商。
  缓存带版本和来源地址；旧 v1 缓存可以临时补全能力，
  但不生成 v2 参数配置，并会在后台重新同步。
- 完整解析成功后才替换内存快照，并原子写入本地缓存。网络错误、无效 JSON、异常结构保留最近成功数据。
- 远程请求失败、超时或返回无效数据后，从有效磁盘缓存和随插件提供的副本中选择兜底数据：
  优先 v2，再比较原始抓取时间。较旧的内置副本不会覆盖更新的成功缓存；内置 v2 可以替代旧 v1 缓存。
- 两份本地数据都不可用时，继续使用供应商信息和现有默认规则。目录同步失败不会阻止发现和导入。
- 弹窗展示最近成功同步时间、过期状态和同步失败信息。「同步时间」是当前快照的获取或远程确认时间，
  内置副本保留发布前的获取时间，不代表每个模型在上游的修改时间。

## 随版本发布的本地副本

完整 v2 数据保存在 `app/src/main/resources/models-dev/api-v2.json.gz`，随插件 JAR 分发。
这份副本与运行时裁剪后的 `models-dev-cache.json` 分开维护，保证首次安装且没有联网成功记录时也能兜底。
副本不是只包含当前支持供应商的子集；加载后才通过同一个生产解析器裁剪到实际使用的范围。

同目录 `metadata.json` 记录固定来源、Schema 版本、原始抓取时间、供应商/模型数量和解压数据的 SHA-256；
`LICENSE` 保留 models.dev 的许可声明。读入前校验来源、版本、时间、校验值和 16 MiB 解压上限。
加载内置副本不会把当前启动时间写成抓取时间，也不会覆盖运行时磁盘缓存。

### 本地维护副本

仓库副本目前不配置 GitHub Actions 自动更新。普通构建、nightly 和正式发布均使用已提交的副本，
构建过程不访问 models.dev。插件运行时仍会按上述规则尝试远程同步并在失败时使用本地数据。

在项目根目录使用 Gradle 更新仓库副本，不需要额外安装 Python 或其他 CLI：

```bash
./gradlew updateModelsDevSnapshot
```

仅离线校验已保存的副本：

```bash
./gradlew verifyModelsDevSnapshot
```

数据内容没有变化时不重写副本或抓取时间。下载失败、结构错误或完整性校验失败时，
更新任务保留原文件并报告失败。更新后重新构建插件，新副本才会进入 JAR；
两个任务都不依赖前端构建或 Halo 服务，也不会随普通构建自动联网。

## 匹配边界

模型 ID 大小写敏感，必须与目录键完全相同，不推断别名、版本后缀、模型家族或原厂名称。
后端还校验供应商类型和完整 API 基地址；只规范化主机大小写、HTTPS 默认端口和末尾斜杠。

| 本地供应商 | models.dev 身份 | 允许的 API 基地址 |
| --- | --- | --- |
| OpenAI | `openai` | `https://api.openai.com/v1` |
| DeepSeek | `deepseek` | `https://api.deepseek.com`、`https://api.deepseek.com/v1` |
| Kimi | `moonshotai-cn`、`moonshotai` | `https://api.moonshot.cn/v1`、`https://api.moonshot.ai/v1`，分别匹配 |
| 百炼 | `alibaba-cn`、`alibaba` | `https://dashscope.aliyuncs.com/compatible-mode/v1`、`https://dashscope-intl.aliyuncs.com/compatible-mode/v1`，分别匹配 |
| 硅基流动 | `siliconflow-cn`、`siliconflow` | `https://api.siliconflow.cn/v1`、`https://api.siliconflow.com/v1`，分别匹配 |
| 智谱 | `zhipuai`、`zai` | `https://open.bigmodel.cn/api/paas/v4`、`https://api.z.ai/api/paas/v4`，分别匹配 |
| 豆包 | `volcengine` | `https://ark.cn-beijing.volces.com/api/v3` |
| OpenRouter | `openrouter` | `https://openrouter.ai/api/v1` |
| AIHubMix | `aihubmix` | `https://aihubmix.com/v1` |
| TokenHub | `tencent-tokenhub` | `https://tokenhub.tencentmaas.com/v1` |

未列出的端点、Coding Plan、OpenAI 兼容类型、Ollama 和私有部署保持原有发现结果。
MiniMax 的普通套餐和 Coding Plan 共用端点，现有配置无法区分套餐，因此首期不自动匹配。
配置了聊天路径覆盖，或所选协议在目录中声明的 API 地址、路径与实际适配器不符时，也不自动补全。
DeepSeek、百炼、智谱和 Z.AI 的 Messages 专用地址按现有适配器实际路由匹配，
不使用 Chat Completions 的地址代替。
OpenRouter、AIHubMix 使用各自目录身份，不尝试匹配其转发模型的原厂目录。

## 能力合并与来源

优先级为管理员选择、供应商明确元数据、models.dev 精确匹配、已有默认规则。
供应商特性列表或布尔字段中的显式否定值会保留；仅有模型类型和适配器默认能力不算供应商能力证据。

目录补全工具调用、结构化输出、推理和输入模态，并受所选调用适配器支持范围限制：

- `reasoning` 不决定推理历史回放；没有显式回放配置时仍继承供应商默认值。
- 图片输入保守声明 PNG/JPEG；音频仅在支持音频的 Chat Completions 适配器下声明 WAV/MP3；
  PDF 仅在 Responses 适配器下声明。视频和 `attachment` 不直接启用文件能力。
- 目录不会启用原生 URL 输入，不会把媒体类型扩大为任意 MIME 通配范围。
- 图片输出不推导文生图、图生图、蒙版或图片数量；首期不补全非语言模型。

导入时保存能力快照与 `spec.catalogMatch`（目录供应商、精确模型 ID、同步时间、补全字段）。
模型参数另存为 `spec.catalogParameters`，其中绑定供应商类型、API 基地址、精确模型 ID 和适配器，
包含自动生成的映射及约束。`featureEvidence` 单独记录供应商对工具调用、结构化输出和推理的明确声明，
包括显式不支持；空对象表示没有供应商证据，字段缺失表示尚未记录来源的旧快照。
管理员的 `spec.parameterMappings` 和 `spec.parameterConstraints` 保持独立，不会被目录同步覆盖。
`capabilitySources` 保留已发布的 `remote`、`built-in`、`manual`、`unknown` 四种值。
目录补全的领域在没有供应商证据时使用 `unknown`，有供应商证据时保留 `remote`；
具体目录来源和补全字段由 `catalogMatch` 单独记录，Console 据此展示来源。
模型发现本身的 `discoverySource` 不改写成供应商证据。

手动刷新只重新加载目录和发现结果，保留弹窗中管理员已编辑的选择，不写入已保存模型。
导入和编辑保存使用相同的能力合并规则。切换调用接口会丢弃原接口的目录媒体声明；
手动修改能力只移除相应字段的目录归属，不扩大其他输入模态的格式范围。
`catalogMatch` 的身份信息在字段全部被手动覆盖后仍保留，空 `fields` 不再展示目录补全提示。
目录导入模型的显式媒体列表（包括空列表）始终作为精确范围，只有未配置的值才继承默认值；
不带 `catalogMatch` 的旧模型继续使用原有默认规则。

## 模型参数与 SDK

参数映射优先级为：**模型手动覆盖 → 供应商手动覆盖 → 模型目录配置 → 供应商内置默认值**。
`INHERIT` 表示使用下一层。目录字段缺失表示未知并继续继承；显式不支持会禁用对应映射。
管理员显式设置映射时，可以覆盖对应参数的目录限制；模型手动约束最后生效。

| v2 信息 | 本项目行为 |
| --- | --- |
| `temperature`、`top_p`、`top_k` | 标记模型是否接受对应采样参数；不虚构数值范围或默认值 |
| `limit.context/input/output` | 保存上下文、输入、输出上限；输出上限用于请求校验，不作为默认生成长度 |
| `api[protocol].compatibility.max_tokens_field` | 为已支持的 Chat Completions 模板选择输出限制字段；v2 明确定义省略时使用 `max_completion_tokens`，显式 `max_tokens` 表示旧字段 |
| `reasoning.toggle/effort/budget` | 用已审阅的适配器字段生成模型级推理意图映射及预算约束 |
| `tools.supported`、协议的 `tools.choice/strict` | 校验工具调用和选择模式；不支持原生 strict 时保留 SDK 本地工具校验 |
| `structured_output` | 不支持原生结构化输出时使用现有提示词降级和本地结果校验 |
| `store/stream_usage/prompt_cache_key/service_tier` | 限制对应原生选项及额外服务等级；保留 `auto/default` 基础语义，明确不支持流式用量时不发送 `include_usage` |
| MIME 和输入模态 | 与适配器可编码范围相交，不自动启用不支持的输入协议 |

上下文和输入 Token 上限供配置与调用方参考；本项目没有针对所有模型的精确 tokenizer，
因此不会把字符数当成 Token 数进行强制校验。原生字段中的 `reasoning_field` 描述响应中的推理内容，
不把它当作请求推理强度字段。
Messages 协议必须提供输出长度，未显式设置时仍沿用适配器默认值；当默认值超过模型上限时才下调。
调用者显式设置的 SDK 或原生输出长度超过上限仍会报错，不静默截断。

Java/npm SDK 继续使用 `LOW`、`MEDIUM`、`HIGH` 三层意图，开发者无需知道用户选择的模型。
例如 DeepSeek 模型声明 `low/high/max` 时，三层分别映射为 `low/high/max`；
GPT 模型声明 `minimal/low/medium/high` 时，三层分别为 `low/medium/high`。
Kimi 只声明推理开关的模型使用 `thinking.type`，声明强度的模型继续使用 `reasoning_effort`；
例如 K2.6 可以开关推理，K3.1 的三层强度为 `low/high/max`。
模型要求同时开启推理时，同一意图原子写入开关与强度，不会只发送强度。
豆包和硅基流动的模型即使与供应商默认推理模式不同，也可通过已确认的 `reasoning_effort`
模板映射目录中的强度值；其他协议仍须有对应的已确认模板。

百炼和硅基流动的 Chat Completions 预算模型使用已确认的 `thinking_budget` 字段，
低、中、高预设为 1024、4096、8192 Token，并受目录预算上下限约束；这些值是本项目策略，
不是 models.dev 提供的默认值。其他协议只有预算信息而缺少已确认的请求字段时，不猜测映射。
只有固定推理、没有开关或强度控制的模型不会继承不适用的供应商推理档位。
`none` 和 `default` 不作为三层强度参与排序；未知的新强度值保留在约束中，等待显式适配。

目录未提供的惩罚参数、seed、logprobs、并行工具调用等继续使用现有映射；
非语言模型也保持原有实现。工具搜索、自由格式工具、显式缓存和会话更新能力尚无对应的完整运行时，
不会因目录宣称支持而自动开放。

Console 与运行时共用 `POST /model-parameters/resolve` 解析有效配置。编辑器显示继承值，
测试工作台据此限制可选参数和输出上限；普通调用、流式调用及 `prepareStep` 调整都经过服务端约束校验。
原生采样、结构化输出、输出 Token、推理及其他受限选项在协议编码、供应商字段转换及参数合并完成后、
发送 HTTP 请求前校验，
因此原生参数不能绕过模型限制，已被 SDK 参数覆盖的旧值也不会造成误拒。
创建或编辑模型时，表单自动通过 `POST /model-parameters/catalog`，按当前模型 ID、供应商和调用接口
读取目录参数，不需要额外点击采用按钮，也不触发供应商调用。匹配结果只进入编辑草稿，保存时才写入模型；
手工映射与约束保持独立。保存会等待当前匹配请求，切换模型或调用接口时取消旧请求并忽略过期结果。
编辑已保存模型时，按同一模型身份读取已保存的供应商证据，再用当前目录更新映射与约束；
不会把上次目录补全的能力当成供应商声明。旧快照只有来源可核实时才迁移，否则保守保留。
目录更新同时同步未被手动覆盖的工具调用、结构化输出和推理能力；管理员在等待目录响应期间的选择也会保留。
模型选择器用与运行时相同的有效约束校正这三项能力，因此已有不一致的能力快照不会绕过筛选条件。
目录不可用或没有匹配时，保留同身份的有效既有快照，或继续使用供应商继承配置。
单独刷新目录不会更新已保存模型，切换模型 ID、供应商或适配器后旧自动参数不再生效。

Console 按用途组织配置：

- 「模型能力」统一设置工具调用、结构化输出和推理能力。变更对应能力时，同步更新运行时约束，避免模型选择器
  声明支持而调用时又被另一处配置禁用。已有手动支持约束会体现在能力选项中；修改其他能力不会改写它们。
- 「参数映射」设置 Temperature、Top P、Top K 和推理请求。推理开关及原生强度值直接通过意图映射配置，
  不再另列推理开关或原生强度白名单。修改推理映射时清除旧手动 `supported/toggle/efforts` 覆盖，
  保留推理预算；仅打开表单不会修改旧约束。
- 「Token 限制」只保留上下文、输入、输出 Token 上限，以及推理预算上下限。
- 原生工具严格模式、响应存储、流式用量统计、提示词缓存键、工具选择模式和服务等级仍有独立的协议用途，
  放在「供应商原生参数」中，默认自动采用 models.dev 的模型及协议声明，显示实际目录值；
  未声明表示没有可用的目录约束，不等于不支持。手动修改后才保存为当前模型的覆盖配置。

这些调整不删除持久化字段，也不会改变 SDK 接口。目录约束仍参与服务端校验与降级处理。

## 升级、旧客户端与回退

此变更保留已有 Java/npm 方法、能力来源枚举及其序列化值，新增字段均可选，无需迁移已有模型。
已有模型不会因目录上线或刷新而改变能力；调用时只读取已保存配置。
新增参数字段默认为空，旧模型仍沿用原有继承行为。旧版本不会执行新增模型约束和复合推理映射，
回退前应恢复备份或重新检查这些模型的手工映射，不能仅凭 JSON 可读取判断运行行为相同。
发布版本 v1.0.0 至 v1.3.0 的来源解析器拒绝未知值，因此不新增 `catalog` 枚举值，
也不把目录数据标记为供应商信息。旧客户端可以继续解析原有字段；严格拒绝未知 JSON 属性的
客户端需要允许新增的可选字段，不能视为所有自定义客户端均已验证。

回退到 v1.3.0 时，旧版会忽略 `catalogMatch`，无法展示目录来源，也没有新版的精确媒体类型保护。
尤其是启用了 `audio-input` 的模型，旧版默认逻辑会在明确的 WAV/MP3 范围外追加 `audio/*`。
因此旧版可读取新保存的数据，不代表回退后的能力解释完全相同。需要回退时，应恢复升级前备份，
或在旧版使用前重新检查升级后导入或编辑的模型及其媒体能力。

## 参考

- [models.dev API 与数据定义](https://github.com/anomalyco/models.dev#api)
- [v2 Schema](https://github.com/anomalyco/models.dev/blob/dev/packages/core/src/schema-v2.ts)
- [百炼推理参数](https://www.alibabacloud.com/help/en/model-studio/deep-thinking)
- [硅基流动 Chat Completions 参数](https://api-docs.siliconflow.cn/docs/api/chat-completions-post)
- [OpenCode 目录缓存实现](https://github.com/anomalyco/opencode/blob/dev/packages/core/src/models-dev.ts)

参考其后台刷新与缓存思路；本项目通过独立的供应商/端点白名单和精确 ID 匹配限制自动补全范围。