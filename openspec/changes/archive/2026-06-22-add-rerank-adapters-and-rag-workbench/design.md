## Context

The previous RAG runtime change introduced provider-neutral reranking contracts, source references, RAG middleware, and a standalone rerank test path. The app runtime now has `RerankingModel`, `ProviderRerankingClient`, `ProviderClientCache#getOrCreateRerankingClient`, and a workbench rerank tab, but the provider classes already integrated in AI Foundation do not yet construct real rerank clients.

The next useful validation loop is provider-backed reranking inside the current plugin itself. ZhiPu, DashScope, and SiliconFlow are already integrated provider types and each has an official rerank API. Adding these adapters first validates the runtime without introducing a new provider solely for reranking.

## Goals / Non-Goals

**Goals:**

- Provide real rerank adapters for ZhiPu, DashScope, and SiliconFlow.
- Replace the Cohere-specific adapter type name currently used for rerank with a neutral `RERANK` adapter type.
- Support remote rerank model discovery only when the provider response explicitly exposes rerank type or capability.
- Add a console-only RAG test endpoint that uses manual sources, optional provider-backed reranking, and the existing RAG middleware.
- Add a RAG mode to the model test workbench for single-query RAG testing with source and rerank diagnostics.
- Keep the public Java SDK boundary focused on composable runtime primitives rather than console test endpoints.

**Non-Goals:**

- No knowledge base, vector store, or document indexing runtime.
- No hardcoded recommended model names in product code.
- No model id string heuristic for rerank discovery.
- No generic OpenAI-like custom rerank path support.
- No public SDK method for the console RAG test endpoint.
- No sentence-level inline citation renderer.

## Decisions

### Use a neutral rerank adapter type

`AdapterType.COHERE_RERANK` will be replaced with a provider-neutral rerank adapter type. The adapter type describes model purpose and runtime family; provider-specific request/response differences live in the provider class and its `ProviderRerankingClient`.

Alternative considered: keep `COHERE_RERANK` for all first-wave providers. This was rejected because ZhiPu, DashScope, and SiliconFlow do not use Cohere's API contract and the name would leak the wrong provider semantics into `AiModel.spec.adapterType`.

### Implement provider-specific HTTP clients

ZhiPu, DashScope, and SiliconFlow will each implement `buildRerankingClient(...)`. Each client maps AI Foundation `RerankRequest` into the provider's documented rerank request and normalizes the response into `RerankResponse`.

The runtime will keep common validation in `RerankingModelImpl`: query validation, document validation, timeout, cancellation, provider option warning behavior, and result index validation. Provider clients focus on HTTP shape, authentication, provider-specific options, and response parsing.

Alternative considered: create a single generic rerank HTTP client configured by path and JSON field names. This was rejected for the first phase because provider response schemas, error models, and option placement differ enough that a generic client would either be too weak or expose provider internals as user configuration.

### Discovery trusts explicit remote model metadata only

Remote model discovery can include rerank models when a provider returns explicit model type or capability metadata for reranking. If the provider returns only model ids, the provider must not infer rerank support from substrings such as `rerank` or `reranker`.

Manual model creation remains the fallback for new provider models or providers whose discovery APIs do not classify rerank models.

Alternative considered: use model id heuristics. This was rejected because rerank model names change quickly and false positives would create incorrect model records.

### RAG workbench uses manual sources

The RAG workbench test mode will accept a single query and manually supplied source candidates. The backend will wrap those candidates in a request-scoped `RagRetriever`, optionally rerank them through `RerankingModelRagSourceReranker`, then stream a response using the existing UIMessage stream path.

Alternative considered: connect the workbench to a real knowledge base or vector store. This was rejected because AI Foundation is the foundation plugin; storage and indexing ownership belongs to future feature work or consumer plugins.

### Add a console-only RAG test endpoint

The app will add a `test-rag` endpoint for the console model workbench. It will output UIMessage stream chunks so the frontend can reuse the existing runtime and display projection. The endpoint is not a Java SDK contract and should be documented as a workbench diagnostic tool.

Alternative considered: overload the general UIMessage chat endpoint with RAG test parameters. This was rejected because test-only source input, rerank model selection, and diagnostic options would complicate the normal chat API surface.

### Keep RAG workbench single-turn

The first RAG mode will run a single query at a time. It will not perform query rewriting, conversation-aware retrieval, history truncation, or source carry-over.

Alternative considered: multi-turn RAG. This was rejected because the core validation target is retrieval, optional reranking, context injection, source output, and final generation.

## Risks / Trade-offs

- Provider API shape drift -> Keep request/response mappers isolated per provider and cover them with unit tests using representative JSON.
- Discovery incompleteness -> Preserve manual model creation and avoid hardcoded model catalogs.
- RAG test endpoint misuse as production API -> Document it as console-only and keep SDK docs focused on `RagRetriever`, `RagLanguageModelMiddleware`, and `AiModelService.rerankingModel(...)`.
- Provider error inconsistency -> Wrap HTTP and protocol failures into stable AI Foundation errors while retaining raw provider diagnostics in messages or metadata.
- UI complexity -> Keep the first workbench mode single-query and source-panel oriented; defer inline citation and knowledge-base workflows.
