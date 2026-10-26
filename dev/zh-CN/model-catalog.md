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

- `reasoning` 不会启用推理历史回放，也不修改参数映射。
- 图片输入保守声明 PNG/JPEG；音频仅在支持音频的 Chat Completions 适配器下声明 WAV/MP3；
  PDF 仅在 Responses 适配器下声明。视频和 `attachment` 不直接启用文件能力。
- 目录不会启用原生 URL 输入，不会把媒体类型扩大为任意 MIME 通配范围。
- 图片输出不推导文生图、图生图、蒙版或图片数量；首期不补全非语言模型。

导入时保存能力快照与 `spec.catalogMatch`（目录供应商、精确模型 ID、同步时间、补全字段）。
`capabilitySources` 新增可选来源值 `catalog`，供应商与目录混合的领域保留供应商来源，
具体由目录补全的字段通过 `catalogMatch.fields` 区分。模型发现本身的 `discoverySource` 不改写成供应商证据。

手动刷新只重新加载目录和发现结果，保留弹窗中管理员已编辑的选择，不写入已保存模型。
导入前切换调用接口会丢弃原接口的目录媒体声明。之后编辑保存的模型仍按现有规则标记手动覆盖。

此变更保留已有 Java/npm 方法、Extension 字段及其序列化值，新增字段均可选，无需迁移已有模型。
已有模型不会因目录上线或刷新而改变能力；调用时只读取已保存配置。

## 参考

- [models.dev API 与数据定义](https://github.com/anomalyco/models.dev#api)
- [OpenCode 目录缓存实现](https://github.com/anomalyco/opencode/blob/dev/packages/core/src/models-dev.ts)

参考其后台刷新与缓存思路；本项目通过独立的供应商/端点白名单和精确 ID 匹配限制自动补全范围。
