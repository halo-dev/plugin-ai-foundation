## Context

See `proposal.md` for motivation. The app currently exposes 14 built-in provider types plus one configurable OpenAI-compatible type. Most built-ins instantiate the same 1,193-line OpenAI-compatible chat model and share optimistic language capabilities; only Ollama and several image/rerank clients already use provider-owned transports.

`AiModel.spec.adapterType` is persisted, but `AiProviderType.buildChatModel` receives only provider, key, and model ID, and `ProviderClientCache` keys language clients by provider resource and model ID. Consequently a model cannot safely select Chat versus Responses for the same provider-side model ID.

Research was performed against each provider's current official API documentation on 2026-08-24
and re-audited on 2026-08-27. Official provider documentation is the sole recorded authority for
endpoints, authentication, request and response shapes, streaming events, capabilities, and model
discovery.

The released public Java and npm contracts are provider-neutral and must remain so. Provider and model resource names, provider references, model IDs, Halo Secret references, and existing GVKs remain stable.

## Goals / Non-Goals

**Goals:**

- Make provider policy local: modifying OpenAI request options, Responses events, tools, or usage must not implicitly change another built-in provider.
- Make the selected adapter an actual runtime protocol choice and cache boundary.
- Preserve canonical Halo messages, results, stream parts, warnings, capabilities, and provider metadata across provider implementations.
- Keep common code limited to protocol-neutral transport and deliberately reusable wire-protocol primitives.
- Implement and validate one provider at a time, removing its dependency on the generic client only after its contract suite passes.

**Non-Goals:**

- Do not redesign `AiModelService`, expose Spring AI/provider DTOs, or accept arbitrary caller-supplied provider option maps.
- Do not add public speech, transcription, video, music, realtime, or file-management model APIs in this change.
- Do not duplicate every HTTP/SSE utility in every package merely to create visual separation.
- Do not silently claim provider capabilities based only on model-name substring guesses.

## Decisions

### 1. Provider-owned packages are the unit of protocol policy

Built-ins move under `run.halo.aifoundation.provider.<provider>` with their provider type, model clients/codecs, options, usage/error conversion, discovery mapper, and tests. The configurable fallback moves under `provider.openailike` and is the only package allowed to instantiate `OpenAiCompatible*` clients.

Common code remains outside those packages:

- `provider.transport`: WebClient/RestClient creation, proxy, timeouts, safe diagnostics, status/error body capture, JSON posting, and cancellation.
- `provider.stream`: incremental byte decoding, SSE framing, JSON event dispatch, and terminal handling.
- `provider.chat`: canonical prompt/message/media/tool conversion contracts and normalized response/stream builders.
- `provider.protocol.chatcompletions` and `provider.protocol.responses`: reusable wire primitives with no provider defaults, endpoints, headers, model rules, or capability claims.

Each provider supplies a provider-owned codec/profile to those primitives. An OpenAI codec change is confined to `provider.openai`; a shared protocol primitive changes only when the common wire contract itself changes and is covered by every consuming provider's contract suite.

Alternative considered: keep subclassing `OpenAiCompatibleChatModel`. Rejected because provider identity, defaults, request shape, stream parsing, and usage would remain coupled. Copying the entire client per provider was also rejected because fixes would diverge and duplicated parsing is not craftsmanship.

### 2. Runtime client construction includes the resolved adapter

Change internal provider factories to receive a `ProviderModelRef` containing provider resource, provider-side model ID, model type, and resolved adapter. The language cache key becomes `(provider resource name, model ID, adapter type)`; other domains use the same key discipline where multiple adapters are possible.

`AiProviderType` gains provider-owned adapter resolution and legacy alias normalization. Adapter selection remains backend-authoritative and the Console consumes registry metadata.

Alternative considered: infer the protocol from model ID in `buildChatModel`. Rejected because the same model can support multiple protocols and model-name rules drift.

### 3. Introduce provider-owned adapter identities and centralize legacy normalization

Add explicit adapter values for built-in language and native embedding transports, including separate Chat and Responses values where both are supported. Existing values (`openai-chat`, `openai-embedding`, `openai-image`, and existing native image/rerank/Ollama values) remain deserializable.

Legacy built-in models are normalized at one boundary:

1. Resolver maps a legacy generic adapter to the provider's recommended adapter for invocation.
2. Validator/assembler emits the normalized provider-owned adapter on the next authoritative save or rediscovery.
3. Generic OpenAI-compatible models keep the existing generic adapter unchanged.

No provider client contains legacy branching. This is the minimum compatibility needed for released persisted Extension data.

Alternative considered: reinterpret `openai-chat` differently inside every provider. Rejected because it preserves the coupling and scatters compatibility code.

### 4. Responses and Chat are explicit protocols, not automatic fallback

Providers that officially support both expose two adapters. Discovery recommends the protocol listed in the matrix below; administrators may select the other documented adapter. A failed Responses request never silently retries through Chat because that could duplicate billable work or tool side effects.

Console adapter metadata carries adapter-scoped feature sets. Model editing and discovery derive
available capabilities from the selected adapter; the provider-level set remains only a union used
for provider overview and compatibility with older clients.

The Responses primitive normalizes item/event lifecycles for text, reasoning, function calls, sources/annotations, files, finish, error, and usage. Provider packages explicitly declare supported event/item types and reject or preserve unknown data as sanitized provider metadata. Stateful fields such as `previous_response_id` are not used until a provider-neutral conversation-state contract exists; multi-step Halo tool execution continues by replaying canonical messages.

Adapter presentation metadata is explicit. The registry never derives a label or description from a
persisted adapter value, because a suffix is not a protocol contract: in particular, `ollama-chat`
uses Ollama's native `/api/chat` rather than `/v1/chat/completions`. Shared protocol families may
reuse one presentation descriptor, while a genuinely distinct wire contract owns a dedicated
descriptor. The current official documentation establishes the following language-interface
matrix:

| Provider | Recommended interface | Other selectable interfaces |
|---|---|---|
| OpenAI | Responses API | Chat Completions |
| DeepSeek | Chat Completions | Responses API, Messages API |
| DashScope | Chat Completions | Responses API, Messages API |
| Doubao | Responses API | Chat Completions |
| ERNIE | Responses API | Chat Completions, Messages API |
| Gitee MoArk | Chat Completions | Responses API, Messages API |
| Kimi | Chat Completions | none |
| MiniMax | Messages API | Chat Completions, Responses API |
| OpenRouter | Chat Completions | Responses API, Messages API |
| SiliconFlow | Chat Completions | Messages API |
| Xiaomi MiMo | Responses API | Chat Completions, Messages API |
| Zhipu AI | Chat Completions | Messages API |
| AIHubMix | Responses API | Chat Completions, Messages API |
| OpenAI-compatible fallback | Chat Completions | none |
| Ollama | native `/api/chat` | Chat Completions, Responses API, Messages API |

This inventory admits a protocol only when the public Halo language-model contract can represent
its messages, streaming lifecycle, tool calls, structured output, and usage without inventing or
discarding semantics. Prompt-only Completions APIs, asynchronous jobs, Realtime APIs, agent-only
surfaces, and product-plan-specific coding gateways are therefore documented exclusions rather
than selectable language adapters. A protocol that is limited to particular vendor models remains
selectable without model-name inspection; the administrator's explicit adapter selection and model
mapping are authoritative.

AIHubMix currently documents synchronous `:generateContent` but not the corresponding streaming
endpoint. It is intentionally not exposed as a language adapter until the gateway publishes a
streaming contract that can preserve Halo's incremental output semantics.

DashScope's native protocol selects distinct text-generation and multimodal-generation endpoints
per model. It is not exposed until model configuration can select that endpoint variant explicitly;
guessing from a model identifier or advertising one fixed endpoint would violate the model-agnostic
provider contract.

The Console auto-selects and hides the interface control when the selected model domain has only
one adapter. It shows an explicit “调用接口” control only when the administrator has a real choice,
and the automatically selected adapter remains part of the submitted model state.

Alternative considered: make Responses an internal implementation detail selected by model-name regex. Rejected because transport behavior and supported tools would be invisible in persisted configuration.

### 5. Capabilities are explicit and constrained by the selected adapter

Remove the universal cross-provider language feature default. Each provider adapter declares the
capabilities its wire protocol can represent. Structured remote metadata refines those defaults
when available; an identifier-only catalog starts with every capability of the recommended adapter
as a usability default and remains low-confidence until an administrator refines it. Effective
capabilities are constrained by:

- provider adapter capabilities established from official documentation;
- authoritative remote model metadata when available;
- administrator model overrides;
- capabilities the public Halo runtime can normalize.

Discovery sources continue to distinguish remote metadata, documented provider rules, and manual configuration. A documented provider domain without a Halo contract is recorded in research but not advertised as another domain.

The 2026-08-27 re-audit keeps protocol support separate from model support. DeepSeek Chat,
Responses, and Messages can all represent the documented experimental vision input, and MiniMax
Responses can represent image and video input; the selected model mapping remains authoritative for
whether a concrete model accepts those inputs. No provider inspects a model identifier. Conversely,
Messages adapters do not inherit audio support from a provider's Chat or Responses surface:
DashScope, OpenRouter, and Zhipu advertise only the media their Messages contracts can encode.

Structured-output support follows the selected wire contract. DashScope and OpenRouter Messages
map object schemas to their documented `output_config.format` JSON Schema shape. Messages adapters
without a documented native format use Halo's prompt instruction and response validation path and
do not emit Chat-style `response_format` fields. This fallback preserves the user-facing structured
output feature without claiming native enforcement that the provider has not published.

### 6. Provider implementation matrix

The default adapter is the first adapter recommended for newly discovered language models; alternate protocols remain explicit. “Deferred domain” means the provider supports it but this change lacks a matching provider-neutral public contract.

| Package | Language protocol | Existing native domains retained/added | Provider-specific behavior implemented | Primary evidence |
|---|---|---|---|---|
| `openai` | Responses default; Chat explicit | embedding, image generation/editing | reasoning effort, structured output, function and normalizable built-in tools, response items/events, multipart image editing, usage details, organization/project headers when configured | [OpenAI Responses migration](https://developers.openai.com/api/docs/guides/migrate-to-responses), [OpenAI tools](https://developers.openai.com/api/docs/guides/tools), [OpenAI image API](https://github.com/openai/openai-python/blob/main/src/openai/resources/images.py) |
| `deepseek` | dedicated Chat default; Responses and Messages explicit | none | protocol-specific thinking/effort mappings; documented image input for Chat, Responses, and Messages; stateless Responses reasoning/web-search replay, JSON Schema, direct reasoning events and documented ignored-field omission; beta-only Chat strict tools/schema; cache hit/miss usage | [DeepSeek thinking mode](https://api-docs.deepseek.com/guides/thinking_mode/), [image understanding](https://api-docs.deepseek.com/zh-cn/guides/vision), [Chat API](https://api-docs.deepseek.com/api/create-chat-completion), [Responses guide](https://api-docs.deepseek.com/zh-cn/guides/responses_api/), [Responses API](https://api-docs.deepseek.com/api/create-response/), [Messages guide](https://api-docs.deepseek.com/guides/anthropic_api/) |
| `dashscope` | dedicated compatible Chat default; Responses and Messages explicit | native embedding, rerank, image; video-generation domain deferred | protocol-specific reasoning fields; Chat image/video/audio and Messages image/video content shapes; Messages native `output_config.format` JSON Schema and sampling range; adapter-scoped audio capability; regional/native endpoint families; dense/sparse embedding metadata; typed model catalog; native usage | [Chat](https://help.aliyun.com/zh/model-studio/qwen-api-via-openai-chat-completions), [Responses](https://help.aliyun.com/en/model-studio/qwen-api-via-openai-responses), [Messages](https://help.aliyun.com/zh/model-studio/anthropic-api-messages), [embedding](https://help.aliyun.com/en/model-studio/embedding), [rerank](https://help.aliyun.com/zh/model-studio/rerank), [Wan image](https://help.aliyun.com/zh/model-studio/wan-image-generation-api-reference), [model catalog](https://help.aliyun.com/en/model-studio/list-models) |
| `doubao` | Responses default; Chat explicit | text/multimodal embedding, image; video/3D generation domains deferred | built-in web/image/knowledge/MCP tool items, `doubao_app_call` blocks, Chat and Responses image/video/audio input shapes, native thinking, sparse and per-modality vectors, sequential image generation, detailed tool usage, low-confidence compatible `/models` fallback | [Responses quickstart](https://www.volcengine.com/docs/82379/1795150), [Responses tools](https://www.volcengine.com/docs/82379/1958524?lang=zh), [Ark Runtime Go SDK](https://github.com/volcengine/ark-runtime-go), [multimodal embedding](https://api.volcengine.com/api-explorer/?action=EmbeddingsMultimodal&groupName=%E5%90%91%E9%87%8F&serviceCode=ark&version=2024-01-01), [image generation](https://api.volcengine.com/api-explorer/debug?action=ImageGenerations&groupName=%E5%9B%BE%E7%89%87%E7%94%9F%E6%88%90API&serviceCode=ark&version=2024-01-01) |
| `ernie` | Responses default; Chat and Messages explicit | text/joint text-image embedding, rerank, image generation/editing; speech/video/OCR deferred | opt-in Responses storage, knowledge tools, Chat web search/cache/thinking budget and effort, detailed cache/search/reasoning usage, state-free replay, typed remote catalog | [Qianfan text generation](https://cloud.baidu.com/doc/qianfan-api/s/3m7of64lb), [Responses](https://cloud.baidu.com/doc/qianfan-api/s/vmhejnuy8), [model catalog](https://cloud.baidu.com/doc/qianfan-api/s/Dmba8k71y), [embedding](https://cloud.baidu.com/doc/qianfan-api/s/Fm7u3ropn), [rerank](https://cloud.baidu.com/doc/qianfan-api/s/2m7u4zt74), [image generation](https://cloud.baidu.com/doc/qianfan-api/s/8m7u6un8a) |
| `gitee` | Chat default; Responses and Messages explicit | text/multimodal embedding, text/multimodal rerank, image; video/3D deferred | `guided_json`/`guided_choice`, request-scoped failover, explicitly mapped native image options, detailed `operations` catalog, dense/base64 embedding responses, contract-selected multimodal rerank route | [MoArk text generation](https://ai.gitee.com/docs/products/apis/texts/text-generation/), [embedding and rerank](https://ai.gitee.com/docs/products/apis/embeddings/), [image generation](https://ai.gitee.com/docs/products/apis/images-vision/text2image), [live OpenAPI](https://ai.gitee.com/v1/yaml) |
| `kimi` | dedicated Chat | file upload/Formula execution deferred | mapping-driven reasoning controls, preserved reasoning, Partial Mode, JSON Schema, prompt cache key, data/`ms://` image-video input, standard Formula tool definitions, discovery capability fields | [Kimi chat](https://platform.kimi.com/docs/api/chat), [model parameters](https://platform.kimi.com/docs/api/models-overview), [official Formula tools](https://platform.kimi.com/docs/guide/use-official-tools) |
| `minimax` | Anthropic Messages default; Chat and Responses explicit | native image; speech/video-generation/music/files domains deferred | protocol-specific Messages/Responses reasoning, current sampling and ignored-field semantics, service tier, documented Chat/Messages image-video input and Responses image-video input, active cache control, signed interleaved-thinking replay, native image options/application errors, and identifier-only language catalog without model-name inference | [Messages API](https://platform.minimax.io/docs/api-reference/text-anthropic-api), [Chat API](https://platform.minimax.io/docs/api-reference/text-openai-api), [Responses API](https://platform.minimax.io/docs/api-reference/responses-create), [model list](https://platform.minimax.io/docs/api-reference/models/openai/list-models), [image API](https://platform.minimax.io/docs/api-reference/image-generation-t2i) |
| `ollama` | dedicated native `/api/chat` default; OpenAI Chat, stateless Responses, and Messages explicit | provider-owned native `/api/embed`; dedicated experimental `/v1/images/generations` | lossless assistant thinking/tool replay; native `options.think` kept separate from Messages `thinking`; native JSON Schema; base64 vision; NDJSON stream/tool lifecycle; narrow Responses fields; Messages base64-only images and tool-choice/metadata constraints; authenticated local/cloud roots; `/api/tags` enriched by `/api/show` capability metadata | [native Chat](https://docs.ollama.com/api/chat), [thinking](https://docs.ollama.com/capabilities/thinking), [tool calling](https://docs.ollama.com/capabilities/tool-calling), [structured output](https://docs.ollama.com/capabilities/structured-outputs), [embedding](https://docs.ollama.com/api/embed), [model details](https://docs.ollama.com/api-reference/show-model-details), [OpenAI compatibility](https://docs.ollama.com/api/openai-compatibility), [Anthropic compatibility](https://docs.ollama.com/api/anthropic-compatibility) |
| `openrouter` | dedicated Chat default; Responses and Messages explicit | text/multimodal embedding, multimodal rerank, dedicated image | protocol-specific reasoning controls; ordered/fallback/parameter/ZDR routing; model fallbacks and plugins; exact reasoning replay; stateless Responses; Messages text/image/PDF/tools and native `output_config.format` JSON Schema support without audio overclaim; upstream provider/cache/reasoning/cost metadata; joint text-image embeddings; typed per-domain discovery | [provider routing](https://openrouter.ai/docs/guides/routing/provider-selection), [Chat API](https://openrouter.ai/docs/api/api-reference/chat/create-a-chat-completion), [Responses API](https://openrouter.ai/docs/api/api-reference/responses/create-responses), [Messages API](https://openrouter.ai/docs/api/api-reference/anthropic-messages/create-a-message), [embedding API](https://openrouter.ai/docs/api/reference/embeddings), [rerank API](https://openrouter.ai/docs/api/api-reference/rerank/create-rerank), [dedicated image API](https://openrouter.ai/docs/guides/overview/multimodal/image-generation) |
| `siliconflow` | dedicated Chat default; Messages explicit | text/multimodal embedding, rerank, image; audio/video generation and standalone Completions domains deferred | Chat-only native thinking/budget; Messages uses only its documented text/tool/sampling surface; Chat FIM and image/video/audio input; Interleaved Thinking replay; mixed text-image embedding input; mapping-driven dimensions/chunk/image fields; typed discovery; usage/errors | [Chat](https://docs.siliconflow.com/en/api-reference/chat-completions/chat-completions), [Messages](https://docs.siliconflow.com/en/api-reference/chat-completions/messages), [Interleaved Thinking](https://docs.siliconflow.com/en/userguide/guides/interleaved-thinking), [embedding](https://docs.siliconflow.cn/cn/api-reference/embeddings/create-embeddings), [rerank](https://docs.siliconflow.com/en/api-reference/rerank/create-rerank), [image](https://docs.siliconflow.com/en/api-reference/images/images-generations), [model list](https://docs.siliconflow.com/en/api-reference/models/get-model-list) |
| `mimo` | Responses default; Chat and Messages explicit | ASR/TTS deferred | protocol-specific reasoning controls, thinking-mode sampling omission, verbatim Responses reasoning replay, Chat image/audio/video and video granularity, Messages image input, strict tools, Chat-only web-search annotations/usage, low-confidence identifier-only discovery | [MiMo Chat](https://mimo.mi.com/docs/en-US/api/chat/openai-api), [MiMo Responses](https://mimo.mi.com/docs/en-US/api/chat/responses), [MiMo Messages](https://mimo.mi.com/docs/en-US/api/chat/anthropic-api), [model list](https://mimo.mi.com/docs/en-US/api/model/list-models), [model capabilities](https://mimo.mi.com/docs/en-US/quick-start/model) |
| `zhipu` | dedicated Chat default; Messages explicit | embedding, rerank, synchronous image; async chat/image, video generation, ASR/TTS, and Realtime deferred | protocol-specific thinking controls; preserved/clearable thinking; streamed function inputs; Chat image/video/file/audio conversion; Messages image input without Chat-only audio or response-format claims; Search/Retrieval/MCP tools; source/cache/filter metadata; protocol-shape validation; low-confidence compatible `/models` fallback | [Zhipu Chat](https://docs.bigmodel.cn/api-reference/%E6%A8%A1%E5%9E%8B-api/%E5%AF%B9%E8%AF%9D%E8%A1%A5%E5%85%A8), [Messages compatibility](https://docs.bigmodel.cn/cn/guide/develop/claude/introduction), [thinking mode](https://docs.bigmodel.cn/cn/guide/capabilities/thinking-mode), [streaming tools](https://docs.bigmodel.cn/cn/guide/capabilities/stream-tool), [embedding](https://docs.bigmodel.cn/api-reference/%E6%A8%A1%E5%9E%8B-api/%E6%96%87%E6%9C%AC%E5%B5%8C%E5%85%A5), [rerank](https://docs.bigmodel.cn/api-reference/%E6%A8%A1%E5%9E%8B-api/%E6%96%87%E6%9C%AC%E9%87%8D%E6%8E%92%E5%BA%8F), [image](https://docs.bigmodel.cn/api-reference/%E6%A8%A1%E5%9E%8B-api/%E5%9B%BE%E5%83%8F%E7%94%9F%E6%88%90) |
| `aihubmix` | Responses default; Chat and Messages explicit | text/multimodal Jina embedding, rerank, synchronous native image; async image/video/TTS/STT deferred | `APP-Code` across domains, native Responses tools, Chat reasoning-detail replay, Messages compatibility, Jina text/image input with `embedding_format`, text rerank, structured catalog capability mapping, explicit prediction image route and mapped fields | [Responses](https://docs.aihubmix.com/en/api/Responses-API), [Messages](https://docs.aihubmix.com/en/api/Anthropic-API), [model API](https://docs.aihubmix.com/en/api/Models-API), [APP-Code](https://docs.aihubmix.com/en/api/App-code), [Jina embedding](https://docs.aihubmix.com/cn/api/Jina-AI), [rerank](https://docs.aihubmix.com/cn/api/Rerank), [image API](https://docs.aihubmix.com/en/api/Image-Gen) |
| `openailike` | generic configurable Chat | generic embedding, rerank, image | base URL, endpoint overrides, headers, conservative generic capabilities, tolerant metadata extraction | [OpenAI Chat](https://platform.openai.com/docs/api-reference/chat), [embeddings](https://platform.openai.com/docs/api-reference/embeddings), [images](https://platform.openai.com/docs/api-reference/images) |

Doubao's official management catalog remains unavailable through the Ark inference API key because
`ListFoundationModels` uses separate Volcengine HMAC credentials. The provider therefore retains
the shared compatible `GET /models` fallback used by existing inference-plane configurations. It
does not claim that route as a Doubao-specific documented catalog: upstream failures are surfaced,
and identifier-only successes are imported as low-confidence language profiles without inspecting
identifier text.

OpenAI, DeepSeek, MiMo, and MiniMax each own their documented model-list request even where the
wire response is an OpenAI-shaped identifier-only `data` array. This keeps authentication,
endpoint evolution, and future metadata handling inside the relevant provider package instead of
mistaking a familiar response shape for a generic protocol. An identifier-only item defaults to a
language model so administrators can import it without classifying every entry manually. That
business default is deliberately separate from capability evidence: the item receives the
provider's recommended language adapter, every capability declared by that adapter, and low
confidence. These capabilities are import-time usability defaults rather than claims derived from
the remote model record. Administrators may disable capabilities unsupported by a specific model,
and provider code still never inspects model identifiers.

Qianfan discovery uses the documented inference-plane `GET /v2/models` endpoint and the same
Bearer API key as generation. Its explicit `type` field determines the Halo model domain with high
confidence; `architecture.input_modalities` contributes only the modalities it actually declares.
Unsupported `text2video` entries are filtered instead of being mislabeled as image models. The
provider defaults to Responses but forces `store=false` because Halo replays canonical messages
and does not yet expose a provider-neutral server-side conversation-state contract. Chat remains
explicitly selectable for its documented `web_search`, `cache_id`, `thinking_budget`,
`thinking_strategy`, and `reasoning_effort` controls. Current Qianfan documentation is
authoritative for wire behavior.

Gitee AI follows a provider-owned package boundary, with the current Gitee documentation and live
OpenAPI defining its wire behavior. Chat remains the default because Gitee documents its complete
request/response contract there;
Responses is explicitly selectable because both the guide and OpenAPI now expose it but its schema
is intentionally generic. Structured JSON is translated to Gitee's `guided_json` rather than sent
as OpenAI `response_format`. The model catalog is requested with `include_details=true` and its
`operations` entries determine language, embedding, rerank, and image domains; unsupported domains
are filtered. Gitee failover may select and bill a different successful compute model, so every
provider-owned client sends `X-Failover-Enabled: false` by default and lets a request header opt in.

Kimi is implemented directly from its current API documentation. In particular, the adapter sends
`max_completion_tokens` instead of the deprecated `max_tokens`. Differences in reasoning controls
between model generations are expressed by administrator-owned model mappings;
the provider never parses a Kimi model identifier. Image and video inputs are serialized only from
caller-owned data or `ms://` file
references because Kimi does not accept arbitrary external URLs and AI Foundation does not
download them. Formula tools remain standard function tools obtained and executed through the
separate Formula API; the chat adapter therefore preserves the normal tool-callback contract
instead of inventing a provider-only tool type. File upload and Formula execution clients remain
deferred until the public SDK has matching contracts.

OpenRouter preserves signed and encrypted `reasoning_details`, annotations, the selected upstream
provider, cached and reasoning token details, and request cost. It applies the documented reasoning
replay rules before continuing a conversation. Image generation uses the documented dedicated
`POST /images` and `GET /images/models` APIs. Embeddings use `/embeddings` plus
`/embeddings/models`; rerank uses `/rerank`
and now exposes the official text/image document shape through the provider-neutral `RerankDocument`
contract. Domain discovery is isolated so one unavailable catalog does not discard successful
results from the other catalogs, and catalog endpoint plus architecture metadata—not model-name
substrings—determines every discovered domain and capability.

SiliconFlow uses a provider-owned package boundary and reusable Chat protocol machinery, while its
current official documentation exclusively controls wire behavior. The provider preserves
`reasoning_content` verbatim because the current Interleaved Thinking guide requires replay for
tool steps; native `enable_thinking`, `thinking_budget`, `min_p`, and Chat `prefix`/`suffix` FIM
fields are validated by protocol shape before network I/O. Embeddings enforce the documented
32-input limit while optional dimensions remain explicit. Chat accepts the documented image,
video, and audio URL content parts without selecting behavior from a model identifier. Rerank forwards explicit chunk controls
and converts top-level `tokens` to usage without dropping the raw token object. Image generation
uses explicitly mapped field names and rejects undocumented option combinations instead of
selecting request fields from the model identifier. Typed `sub_type` discovery requests
only the four public Halo domains and merges text-to-image plus image-to-image evidence for the
same model; audio, video, and standalone Completions remain unadvertised until matching public
contracts exist.

MiMo uses a provider-owned Responses and Chat split, while the July 2026 official MiMo
documentation controls every wire field. Responses is the default and accepts only the documented
text/image surface; it accepts explicitly mapped `reasoning` fields, translates MiMo's
`response.reasoning_text.*` events, and
retains the complete sanitized reasoning output item for verbatim multi-turn tool replay. Explicit
Chat can accept explicitly mapped `thinking.type` and owns the wider
image/audio/video protocol shapes, including documented video `fps` and
`media_resolution`. Both adapters preserve `auto` tool choice and omit other values because the
MiMo service documents that exact backend normalization. While thinking is active, they
omit `temperature` and `top_p` because MiMo does not support customizing those fields and applies
its documented fixed defaults. The Chat-only `web_search` tool is namespaced under MiMo provider options and keeps
returned citation, cache, search, and reasoning-token details in normalized/raw metadata. Because
`GET /models` provides IDs but no domain type, discovery uses the provider-declared default
language domain and recommended Responses adapter with low confidence. It never derives domains or
capabilities from identifier text; administrators remain responsible for correcting or excluding
entries from deferred speech domains until Halo has provider-neutral ASR and TTS contracts.

Zhipu is implemented from the 2026-08-24 BigModel OpenAPI rather than treating its familiar Chat
JSON as an OpenAI contract. Model-specific reasoning effort, thinking values, and tool-stream
settings are administrator-owned model mappings; the adapter validates and serializes the protocol
without inspecting model identifiers. `thinking.clear_thinking=false` preserves verbatim
assistant `reasoning_content`. Provider options expose validated Web Search, Retrieval, and MCP tools,
while response sources, prompt-cache details, request IDs, and content filters remain available as
provider metadata. Chat media input accepts the official image/video/file and base64 audio shapes.
Embedding, rerank, and synchronous image generation use
separate clients with protocol-shape, dimension, batch, size, quality, and watermark validation;
model-specific restrictions remain in model configuration or the upstream API. BigModel does not
document a structured inference-plane model catalog; the provider therefore uses only the shared
compatible `/models` fallback, surfaces upstream failures, and marks identifier-only results as
low-confidence language profiles without parsing them. Async Chat/Image, video generation,
ASR/TTS, and Realtime remain deferred behind distinct contracts. The current official OpenAPI
controls every provider-specific wire behavior and model rule.

AIHubMix is implemented as a gateway-owned provider package because its public contract is wider
than a single OpenAI-compatible endpoint. Responses is the recommended language adapter, including
the documented Web Search Preview, Code Interpreter, Image Generation, MCP, and Computer Use tool
types; explicit Chat retains `reasoning_content`, structured `reasoning_details`, annotations,
strict function schemas, image input, and byte-backed audio input. `builtinTools` is rejected on
Chat instead of being silently dropped. The shared `APP-Code` header is applied to every inference
domain and to the separate `/api/v1/models` catalog. Dense embedding validates the documented
2048-input batch, accepts explicit positive dimensions, decodes float and base64 vectors, and keeps
raw usage. Rerank accepts text documents and its documented `return_documents` option. Image
generation uses the native synchronous `/v1/models/{provider}/{model}/predictions` route rather
than `/images/generations`; every model uses a catalog-provided route or an explicit
`providerOptions.aihubmix.model_path`, and model-specific field mappings remain explicit. Async task
responses fail explicitly because polling is a
separate protocol. Catalog discovery parses both current comma-delimited fields and legacy arrays,
recommends the provider-owned Responses/embedding/rerank/image adapters, and filters video, TTS,
and STT domains until matching public contracts exist. Current AIHubMix documentation controls all
wire behavior.

Official provider documentation is authoritative.

### 7. Spring AI is retained only as an internal model SPI

The application still benefits from Spring AI's provider-neutral `ChatModel`, message/media,
prompt/options, response/usage, tool-callback, and embedding interfaces. Those types connect the
provider-owned protocol clients to the existing language and embedding runtime without leaking
through the public `api` module. The application does not use Spring AI `ChatClient`, chat memory,
retry infrastructure, or any Spring AI provider implementation.

The dependency is therefore narrowed from `spring-ai-client-chat` to `spring-ai-model`. Dedicated
`spring-ai-deepseek`, `spring-ai-ollama`, and `spring-ai-retry` artifacts were removed with the old
provider implementations. At Spring AI 2.0.0 this removes 326,370 bytes of direct jar artifacts
(70,556 + 97,083 + 9,610 + 149,121). The retained model, commons, and template jars total 499,379
bytes before packaging. In the clean 10,532,912-byte plugin jar, `org/springframework/ai` occupies
390,208 compressed bytes (3.70%) and contains no DeepSeek or Ollama provider classes. Replacing
that remaining SPI would require duplicating broadly used model/message/tool contracts for a
smaller saving than the provider-specific modules already removed, so this change retains the
core SPI and rejects future provider-module dependencies.

### 8. Unique provider options use administrator mappings and typed provider options

Portable request fields continue through existing public request DTOs and effective parameter
mappings. Provider-specific settings use provider-namespaced `providerOptions` maps on text,
embedding, rerank, and image requests so an option cannot leak into another provider. Portable
fields remain authoritative when both forms address the same wire field. Common, administrator-
managed controls continue to use typed mapping templates with documented applicability;
provider-only features such as Qianfan built-in tools, web-search policy, cache IDs, embedding
`user`, image prompt extension and watermark, plus Gitee `guided_choice`, image inference steps,
and guidance scale remain explicit namespaced options. Response-only details flow through
sanitized provider metadata.

Reasoning defaults are protocol defaults, not model catalogs. Each provider declares the mapping
for its recommended adapter, while runtime resolution selects the Chat- or Responses-shaped default
for the model's actual adapter. Provider and model mappings can override that value, and an intent
the current protocol cannot express remains unmapped with a diagnostic. No reasoning default is
selected from model identifiers.

Built-in provider tools are exposed only when their inputs and outputs can be represented by existing provider-neutral tool/source/file parts. Otherwise the adapter preserves safe metadata and emits a stable warning instead of misrepresenting the tool as a local function.

### 9. Tests are protocol contracts, not class-shape checks

For each provider, add mock-server fixtures covering:

- URL, authentication, headers, and request JSON;
- non-streaming text/reasoning/tool/structured-output responses;
- fragmented SSE boundaries, cumulative versus delta tool arguments, terminal events, and malformed events;
- usage and provider metadata conversion;
- multimodal serialization for supported media;
- status and provider error normalization;
- discovery and capability evidence;
- adapter cache separation and legacy normalization.

Each fixture records the official documentation URL and retrieval date. Tests must not assert only that a client class has a new name. Optional live smoke tests are enabled by provider-specific environment variables, avoid write-like/billable media generation by default, and never log secrets.

After each provider migration, run its package tests plus shared protocol/runtime tests. At milestones run all `app` tests; before completion run the full Gradle build, UI unit/lint/type checks, generated-client verification when applicable, and browser validation for changed Console flows using the provided local administrator account.

### 10. Repeated predicates are named without centralizing provider policy

Repeated technical classification belongs in a focused shared abstraction only when multiple
providers use the same mechanism. The shared layer may determine whether a reference has an
allowed leading protocol marker or whether a Jackson node is absent, but every provider retains
its own accepted-reference policy and error semantics. Provider reasoning metadata uses one shared
namespace reader because its envelope is a normalized runtime contract rather than provider wire
policy.

Single-use protocol rules remain local and are expressed through guard clauses or small named
predicates. Short mathematical range checks and intrinsically atomic conditions remain inline;
removing every boolean operator would obscure rather than improve the contract. Generic `Utils`
containers and helpers that merely rename one call site are rejected.

## Tool approval continuation integrity

UI Message continuation follows the assistant-message identity rule: when submitted history ends
with an assistant message, the next stream continues that message and exposes its existing id in
the response start chunk. Provider-internal response ids are generation diagnostics and must not
split one tool lifecycle into multiple persisted assistant messages.

Conversion also treats an approval response as an intermediate lifecycle snapshot. If later
history contains a terminal result or error for the same tool call, that terminal snapshot is
authoritative and the earlier approval snapshot is omitted from provider history. This preserves
the required `assistant(tool call) -> tool(result or error)` sequence without provider-specific
repair code and keeps histories produced before the identity fix usable.

## Risks / Trade-offs

- **[Large change surface]** Provider, runtime cache, adapters, discovery, mappings, tests, and UI metadata change together → migrate provider-by-provider behind passing contract tests and keep the generic fallback untouched until the end.
- **[Official APIs evolve during implementation]** Current docs may change → store retrieval dates and links, re-check each provider immediately before its implementation, and prefer remote capability metadata over model-name guesses.
- **[Responses semantics exceed current public contracts]** Stateful conversations and some built-in tools cannot be represented → use canonical message replay, expose only normalizable parts, and defer stateful/provider-native public controls.
- **[Persisted adapter compatibility]** Existing resources contain generic adapter values → normalize once at resolver/validation boundaries and preserve deserialization; do not add per-provider legacy paths.
- **[Shared primitive regression]** A low-level SSE or protocol primitive can affect multiple providers → every consumer owns fixtures, and shared changes run the complete provider contract suite.
- **[No credentials for all providers]** Real endpoints cannot be exhaustively exercised → deterministic contract fixtures are mandatory and credential-gated smoke tests report skips separately.
- **[More adapter choices in Console]** Administrators may not understand Chat versus Responses → backend metadata supplies concise Chinese labels/descriptions and recommends one documented default.

## Migration Plan

1. Add common transport/protocol contracts, provider model reference, adapter-aware cache keys, explicit capabilities, and legacy normalization while all existing clients still pass.
2. Implement the generic OpenAI-compatible package as the permanent fallback and freeze its contract.
3. Migrate built-ins one at a time in the matrix order: OpenAI, DeepSeek, DashScope, Doubao, Ernie, Gitee, Kimi, MiniMax, Ollama, OpenRouter, SiliconFlow, MiMo, Zhipu, AiHubMix.
4. For each provider, re-check official docs, add fixtures/options/discovery, switch its factory to the provider-owned client, run focused and shared tests, then remove its use of generic builders.
5. Regenerate API clients if adapter metadata changes generated schemas, update dynamic Console rendering, and validate in browser.
6. Remove obsolete built-in generic builder methods and optimistic defaults only after no built-in provider references them.
7. Run full verification and inspect the final dependency graph for forbidden built-in-to-`openailike` edges.

Rollback is code-only: revert provider migrations in reverse order while retaining adapter deserialization. Because no bulk Extension migration is performed, rollback does not require restoring persisted data; newly saved provider-owned adapter values remain recognized by the release containing this change and must be translated if backporting to an older release is required.
