# Decision models

[简体中文](../../zh-CN/sdk-core/decision-models.md) | English

Decision models answer closed questions about shared text or JSON state. Consumer plugins own thresholds
and subsequent actions. `DecisionModel` is provider-neutral; TypeSafe / Jev is the first built-in
provider. Other adapters can reuse model resolution, the workbench, and usage recording.
These APIs are new in the development build and require matching plugin and
Java/npm SDK versions; the published `1.0.0` API does not include them.

## Configure TypeSafe / Jev

Create a **TypeSafe (Jev)** provider and select a Halo Secret for its API key. The default service root
is `https://api.typesafe.ai`; the adapter appends `/v1/systemone` and `/v1/models`. Import a discovered
model or manually enter an accepted provider model ID with model type `decision` and adapter
`typesafe-system-one`. Model discovery uses the account's catalog, not a hardcoded model list.
Optionally configure the default decision model, then use the Console's Decision workbench.

The adapter supports text/JSON state and `YES_NO`, `CHOICE`, and `SCORE` questions. Choices support
2–255 options; scores support 2–10 ordered levels. Images, Box questions, text generation, tool execution,
and automatic business routing are outside this adapter's current scope. Unsupported native options
are rejected explicitly.

## Java

```java
var request = DecisionRequest.builder()
    .state(Map.of("comment", comment))
    .questions(Map.of(
        "spam", DecisionQuestion.yesNo("Is this unsolicited advertising?"),
        "intent", DecisionQuestion.choice("What is the intent?",
            Map.of("question", "A question", "feedback", "Feedback", "other", "Other")),
        "relevance", DecisionQuestion.score("How relevant is this to plugin setup?",
            List.of("Unrelated", "Partially related", "Highly related"))))
    .build();

return extensionGetter.getEnabledExtension(AiModelService.class)
    .flatMap(service -> service.decisionModel(modelName))
    .flatMap(model -> model.decide(request));
```

Decision types live in `run.halo.aifoundation.decision`. Resolve the service with Halo's
`ExtensionGetter`; `modelName` means `AiModel.metadata.name`. Null/blank or the no-argument overload
uses the default decision model. Request metadata stays in local audit records and is not sent upstream.

`YES_NO.probability` is the probability of yes, not a boolean. `CHOICE` requires a selected option;
`SCORE` requires a finite level index in `[0, number of levels - 1]`, which may be fractional and need
not be calculated from a distribution. Both accept optional `probabilities` and `confidence`; a score
also accepts an optional `legend`. These fields are independent and may be absent or null. Missing
diagnostics are never synthesized. When present, a distribution must cover every option and sum to one,
and a legend must cover every score level.

Confidence is a provider-reported value in `[0, 1]` whose meaning depends on the provider. TypeSafe
uses distribution concentration; this is not a universal confidence definition or a correctness guarantee.
YES_NO has no separate confidence. Unknown token counts remain null. The response reports the served model.
The runtime validates request fields, provider limits, response question IDs/types, and any supplied diagnostics.

`model.capabilities()` returns supported question types and limits. Selectors can filter by
`modelType: decision` and `requiredCapabilities.decision`. A total timeout uses `GenerationTimeouts`
and raises `DecisionTimeoutException`. Subscription cancellation cancels HTTP work; cancellation tokens
are checked before and after inference and raise `DecisionCancelledException`.

`DecisionCapability` also exposes `choiceProbabilities`, `choiceConfidence`, `scoreProbabilities`,
`scoreConfidence`, and `scoreLegend`: true means supported, false means unsupported, and null means
unknown. For example, require `requiredCapabilities.decision.choiceProbabilities: true` to select
models capable of returning choice distributions. Supported does not mean present in every response;
consumers must still check for absent values.

## Other providers

Add an internal `AiProviderType` implementation and a `decision` adapter implementing
`buildDecisionClient(...)` and `decisionCapabilities()`. Map the provider's protocol to the public
request and result types. Label-only and score-only providers do not need to fabricate diagnostics
or change the shared inference runtime. A boolean-only classifier can use a two-option `CHOICE`;
`YES_NO` retains its probability contract rather than converting booleans to invented probabilities.

Keep protocol-required fields in the adapter's validation. The TypeSafe client still requires its
distributions, confidence, and score legends; the optional public contract does not hide a malformed
TypeSafe response.

## Browser SDK

Expose your own authenticated consumer-plugin endpoint accepting `DecisionRequest` and returning
`DecisionResponse`. Provider keys stay server-side.

```ts
import { createDecisionClient } from '@halo-dev/ai-foundation-sdk'

const client = createDecisionClient({
  api: '/apis/your-plugin.example/v1alpha1/decide', // Implement this endpoint in your plugin.
  credentials: 'same-origin',
})
const result = await client.decide({
  state: { comment: 'Thanks for the article!' },
  questions: { spam: { type: 'YES_NO', instructions: 'Is this spam?' } },
})
```

The client supports custom fetch, headers and AbortSignal and validates typed response probabilities.
It targets your endpoint, not the administrator-only Console test API. That test API accepts a string
`state` plus `stateFormat: TEXT | JSON`; the server parses JSON before inference.

Existing APIs and persisted values are preserved, and new AiModelService methods have compatible defaults.
Older releases/clients cannot recognize the new model and adapter enum values: update consuming SDKs,
and remove decision resources/default slots before downgrading. Automated tests cover the protocol,
runtime and workbench; evaluate Chinese/domain-specific accuracy and thresholds with your own data.

References: [System One](https://docs.typesafe.ai/api), [model discovery](https://docs.typesafe.ai/api/list-models),
[confidence](https://docs.typesafe.ai/confidence).
