# 模型能力目录

「从供应商获取模型」始终使用供应商自己的模型发现接口。后台下载的
[models.dev 目录](https://models.dev/api.json)只补全这些结果中的语言模型能力，不会把公共目录
中的模型加入账号可用列表，也不参与模型调用、价格计算或存量模型更新。

## 同步与降级

- 插件启动时读取 `plugins/ai-foundation/models-dev-cache.json`，没有缓存或缓存超过 24 小时时后台同步。
- 此后每 24 小时检查一次，导入弹窗支持「刷新能力目录」。并发刷新共用一次请求。
- 请求仅访问固定的 HTTPS 地址，不附带供应商密钥，不跟随重定向；超时 15 秒，响应上限 16 MiB。
- 完整解析成功后才替换内存快照，并原子写入本地缓存。网络错误、无效 JSON、异常结构保留最近成功数据。
- 没有可用缓存时继续使用供应商信息和现有默认规则。目录同步失败不会阻止发现和导入。
- 弹窗展示最近成功同步时间、过期状态和同步失败信息。「同步时间」是本站获取快照的时间，
  不代表每个模型在上游的修改时间。

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
配置了聊天路径覆盖，或目录模型另行指定了不同 API 地址时，也不自动补全。
OpenRouter、AIHubMix 使用各自目录身份，不尝试匹配其转发模型的原厂目录。

## 能力合并与来源

优先级为管理员选择、供应商明确元数据、models.dev 精确匹配、已有默认规则。
供应商特性列表或布尔字段中的显式否定值会保留；仅有模型类型和适配器默认能力不算供应商能力证据。

目录补全工具调用、结构化输出、推理和输入模态，并受所选调用适配器支持范围限制：

- `reasoning` 不决定推理历史回放，也不修改参数映射；没有显式回放配置时仍继承供应商默认值。
- 图片输入保守声明 PNG/JPEG；音频仅在支持音频的 Chat Completions 适配器下声明 WAV/MP3；
  PDF 仅在 Responses 适配器下声明。视频和 `attachment` 不直接启用文件能力。
- 目录不会启用原生 URL 输入，不会把媒体类型扩大为任意 MIME 通配范围。
- 图片输出不推导文生图、图生图、蒙版或图片数量；首期不补全非语言模型。

导入时保存能力快照与 `spec.catalogMatch`（目录供应商、精确模型 ID、同步时间、补全字段）。
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

## 升级、旧客户端与回退

此变更保留已有 Java/npm 方法、能力来源枚举及其序列化值，新增字段均可选，无需迁移已有模型。
已有模型不会因目录上线或刷新而改变能力；调用时只读取已保存配置。
发布版本 v1.0.0 至 v1.3.0 的来源解析器拒绝未知值，因此不新增 `catalog` 枚举值，
也不把目录数据标记为供应商信息。旧客户端可以继续解析原有字段；严格拒绝未知 JSON 属性的
客户端需要允许新增的可选字段，不能视为所有自定义客户端均已验证。

回退到 v1.3.0 时，旧版会忽略 `catalogMatch`，无法展示目录来源，也没有新版的精确媒体类型保护。
尤其是启用了 `audio-input` 的模型，旧版默认逻辑会在明确的 WAV/MP3 范围外追加 `audio/*`。
因此旧版可读取新保存的数据，不代表回退后的能力解释完全相同。需要回退时，应恢复升级前备份，
或在旧版使用前重新检查升级后导入或编辑的模型及其媒体能力。

## 参考

- [models.dev API 与数据定义](https://github.com/anomalyco/models.dev#api)
- [OpenCode 目录缓存实现](https://github.com/anomalyco/opencode/blob/dev/packages/core/src/models-dev.ts)

参考其后台刷新与缓存思路；本项目通过独立的供应商/端点白名单和精确 ID 匹配限制自动补全范围。
