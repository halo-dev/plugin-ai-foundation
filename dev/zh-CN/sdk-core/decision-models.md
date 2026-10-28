# 决策模型

简体中文 | [English](../../en/sdk-core/decision-models.md)

决策模型针对共享内容回答封闭问题，返回概率判断、候选选择或有序评分。业务插件决定阈值和后续动作。
`DecisionModel` 是供应商中立的公共契约，内置 TypeSafe 与 AIHubMix 决策适配器；其他提供商可以通过独立
适配器接入相同的模型解析、工作台和用量统计。
这些 API 是本次开发新增能力，使用时需要包含此功能的 AI Foundation 插件和 Java/npm SDK；已发布的
`1.0.0` API 不包含它们。

## 配置 TypeSafe / Jev

1. 在提供商管理中选择 **TypeSafe (Jev)**，通过 Halo Secret 配置 API Key。
2. 默认 Base URL 为 `https://api.typesafe.ai`（服务根地址）。可配置代理；路径由适配器追加。
3. 从提供商发现并导入模型，或手动填写供应方允许的模型 ID。模型类型选择 `decision`，适配器为
   `typesafe-system-one`。模型列表来自 `GET /v1/models`，没有硬编码版本列表。
4. 可在默认模型页设置默认决策模型，在工作台的“决策”页输入文本或 JSON，并添加多个问题。

当前适配器支持文本与 JSON 状态，以及 `YES_NO`、`CHOICE`、`SCORE`。每个单选问题最多 255 个选项，
评分问题为 2–10 个有序等级。不提供图片/Box、文本生成、工具执行或自动业务路由。
原生参数不支持时会明确拒绝，不会静默忽略。

## 通过 AIHubMix 使用 Jev

在提供商管理中选择 **AIHubMix** 并配置它的 API Key，默认 Base URL 为 `https://aihubmix.com/v1`。
打开模型发现并刷新列表，导入目录返回的 Jev 模型（如 `jev-latest`）。目录中的 `decision` 类型会映射为
决策模型，适配器为 `aihubmix-system-one`；也支持手动填写目录中可用的模型 ID，不限定 Jev 版本。

调用使用 AIHubMix 的 `POST /v1/systemone`，带上 AIHubMix 的鉴权与 `APP-Code` 请求头。
服务根地址和以 `/v1` 结尾的 Base URL 均可使用；适配器不会重复追加 `/v1`。支持的问题类型和限制与
上述 System One 适配一致，导入后可直接在“决策”工作台测试，无需另建 TypeSafe 提供商。

## Java 调用

```java
import java.util.List;
import java.util.Map;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.decision.DecisionQuestion;
import run.halo.aifoundation.decision.DecisionRequest;

var request = DecisionRequest.builder()
    .state(Map.of("comment", "这篇文章很有帮助，请问如何配置插件？"))
    .questions(Map.of(
        "spam", DecisionQuestion.yesNo("这条评论是否为垃圾广告？"),
        "intent", DecisionQuestion.choice("评论的主要意图是什么？", Map.of(
            "question", "提问", "feedback", "反馈", "other", "其他")),
        "relevance", DecisionQuestion.score("与插件配置主题有多相关？",
            List.of("无关", "部分相关", "高度相关"))))
    .metadata(Map.of("feature", "comment-review"))
    .build();

return extensionGetter.getEnabledExtension(AiModelService.class)
    .flatMap(service -> service.decisionModel(modelName))
    .flatMap(model -> model.decide(request));
```

`modelName` 为 `AiModel.metadata.name`；无参或空白名称使用默认决策模型。跨插件仍通过
`ExtensionGetter` 获取服务。`metadata` 仅供本地审计，不会发送给提供商。

- `YES_NO` 的 `probability` 是“是”的概率，不会自动转为布尔值。TypeSafe 的 `noul` 在适配器内部转换。
- `CHOICE` 必须返回请求中的一个 `choice`；`probabilities` 和 `confidence` 各自可选。
- `SCORE` 必须返回 `[0, 等级数 - 1]` 内的 `score`，允许小数，不要求由概率加权计算。
  等级 `legend`、`probabilities` 和 `confidence` 各自可选。
- 附加字段缺失或为 `null` 时保持缺失，不填零、不制造分布，也不从分数反推置信度。
  如果提供概率分布，必须包含全部选项且总和为 1；如果提供图例，必须覆盖全部评分等级。
- `confidence` 是提供商返回的 `[0, 1]` 数值，含义取决于提供商，不能统一解释为答案正确率。
  TypeSafe 的置信度描述分布集中程度；其他提供商可以采用不同定义。`YES_NO` 没有单独的置信度字段。
- `response.model` 保留实际服务模型，token 数量未知时保持 `null`，不能按零用量处理。
- 请求验证先于推理；返回结果的问题 ID、类型、选项、数值范围和概率分布也会校验。

`DecisionRequest.timeouts` 接受 `GenerationTimeouts.totalTimeout`，超时抛出
`DecisionTimeoutException`。取消 Reactor 订阅会取消 HTTP 请求；`cancellationToken` 在调用前和返回时
检查，抛出 `DecisionCancelledException`。通过 `model.capabilities()` 查看问题类型和数量限制。
模型选择器可使用 `modelType: decision` 和 `requiredCapabilities.decision` 过滤。

`DecisionCapability` 还声明 `choiceProbabilities`、`choiceConfidence`、`scoreProbabilities`、
`scoreConfidence` 和 `scoreLegend`：`true` 表示支持，`false` 表示不支持，`null` 表示未知。
例如，要求 `requiredCapabilities.decision.choiceProbabilities: true` 可以筛选能够返回单选概率分布的
模型。声明支持不保证每次响应均包含该字段，业务代码仍需判断是否存在。

## 新增其他提供商

在插件内部新增 `AiProviderType` 实现和对应的 `decision` 适配器，提供 `buildDecisionClient(...)`
与 `decisionCapabilities()`，将供应方协议映射成公共请求和结果。只返回标签或评分的提供商无需补造
诊断数据，也无需修改公共推理流程。二元分类若只返回布尔结果，可用两个选项的 `CHOICE` 表达；
`YES_NO` 保留“是”的概率契约，不把布尔值伪装成概率。

提供商协议的强制字段在适配器内校验。例如 TypeSafe 本身要求返回的分布、置信度和评分图例仍由
TypeSafe 客户端严格解析；公共层放宽可选字段不会掩盖它的异常响应。

## 浏览器调用自己的插件接口

消费插件可以接收 `DecisionRequest`、调用 `AiModelService`，并把 `DecisionResponse` 作为 JSON 返回。
使用消费插件自己的鉴权和业务校验；浏览器不直接持有提供商 API Key。

```ts
import { createDecisionClient } from '@halo-dev/ai-foundation-sdk'

const client = createDecisionClient({
  api: '/apis/your-plugin.example/v1alpha1/decide', // 由消费插件实现
  credentials: 'same-origin',
})
const result = await client.decide({
  state: { comment: '这篇文章很有帮助' },
  questions: { spam: { type: 'YES_NO', instructions: '是否为垃圾广告？' } },
})
const answer = result.answers.spam
if (answer?.type === 'YES_NO') {
  console.log(answer.probability)
}
```

客户端支持自定义 `fetch`、请求头和 `AbortSignal`，并检查响应的决策类型及概率。它调用上述消费插件
接口，不调用仅限管理员的 Console 测试端点。Console 测试端点为便于表单与 OpenAPI 客户端使用，接收
字符串 `state` 和可选的 `stateFormat: TEXT | JSON`；JSON 格式在服务端解析后才交给模型。

## 兼容性与验证

原有语言、嵌入、重排和图像 API 及持久化值保持不变。新服务方法提供默认实现，旧的
`AiModelService` 实现可继续加载；新功能仍要求匹配版本的运行时。
新增 `decision` 模型类型、`typesafe-system-one` / `aihubmix-system-one` 适配器及默认决策槽位后，旧版插件/生成客户端不能
识别新枚举。升级时一起更新相关 SDK；降级前移除新模型资源并清空决策默认槽位。

自动测试覆盖协议、模型解析、验证、取消、超时和工作台。实际中文业务效果与阈值应使用自己的数据评估。

协议依据：[System One](https://docs.typesafe.ai/api)、[模型发现](https://docs.typesafe.ai/api/list-models)、
[概率与置信度](https://docs.typesafe.ai/confidence)、[AIHubMix Jev 接口](https://aihubmix.com/model/jev-latest/llms.txt)、
[AIHubMix 模型目录](https://docs.aihubmix.com/cn/api/Models-API)。
