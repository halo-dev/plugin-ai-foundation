## Why

AI Foundation now exposes provider-neutral reranking and RAG runtime contracts, but the current app runtime does not yet provide real provider-backed rerank adapters for the providers already integrated in this plugin. The model test workbench can test chat, embedding, and standalone rerank calls, but it still cannot validate the full RAG path with manual sources, optional reranking, source diagnostics, and UIMessage streaming.

## What Changes

- Add real provider-backed rerank adapters for the existing ZhiPu, DashScope, and SiliconFlow providers.
- Rename the current provider adapter type for rerank from a Cohere-specific name to a neutral rerank adapter type.
- Extend provider type metadata and model discovery so providers can expose rerank model support when the upstream model list explicitly declares rerank capability.
- Keep manual model creation available when remote discovery does not return rerank models.
- Add a console-only RAG test endpoint that accepts a single query, manually supplied sources, an optional rerank model, and RAG test options, then streams a UIMessage response.
- Extend the model test workbench with a RAG mode that visualizes retrieval sources, reranked order, scores, warnings, and the generated answer.
- Document how provider-backed rerank adapters and the RAG workbench test path should be used.

Non-goals:

- Do not add a knowledge base, vector store, crawler, or document indexing runtime to AI Foundation.
- Do not hardcode built-in recommended rerank model names in Java or Vue code.
- Do not infer rerank models from model id string heuristics when the provider does not explicitly expose model type or capability.
- Do not add a public Java SDK API for the RAG test endpoint; the endpoint is a console testing tool.
- Do not extend OpenAI-compatible provider configuration with arbitrary custom rerank paths in this change.
- Do not implement complex inline citation rendering or sentence-level citation alignment.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `reranking-core`: provider-backed rerank adapters and neutral rerank adapter metadata.
- `provider-type-registry`: provider type metadata must expose rerank support for ZhiPu, DashScope, and SiliconFlow.
- `adapter-model-discovery`: remote discovery may include rerank models only when the provider explicitly declares rerank type or capability.
- `console-model-management`: users can create and test rerank models for providers that support rerank adapters.
- `model-test-workbench`: the workbench gains a RAG test mode with manual sources and optional reranking.
- `ui-message-rag-runtime`: the console RAG test endpoint emits RAG lifecycle diagnostics over UIMessage stream data parts.
- `source-reference-runtime`: RAG test output surfaces final source references and source ordering diagnostics.
- `consumer-sdk-documentation`: docs explain provider-backed rerank usage and distinguish console RAG testing from production SDK composition.

## Impact

- API module:
  - Neutral rerank adapter type naming.
  - RAG/UIMessage data part constants or DTOs as needed for workbench diagnostics.
- App module:
  - ZhiPu, DashScope, and SiliconFlow provider classes gain `buildRerankingClient(...)`.
  - Provider-specific HTTP clients map request/response payloads into `RerankRequest` and `RerankResponse`.
  - Model discovery maps explicit remote rerank model metadata to `ModelType.RERANK`.
  - Console model endpoint gains a `test-rag` UIMessage stream endpoint.
- UI module:
  - Generated API client updates.
  - Model workbench gains a RAG mode and source/rerank diagnostics panel.
- Docs and OpenSpec:
  - Developer documentation and affected specs are updated.
