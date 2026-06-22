## 1. Rerank Adapter Contract

- [x] 1.1 Rename the Cohere-specific rerank adapter type to a neutral rerank adapter type across API, app, tests, generated docs, and UI usages.
- [x] 1.2 Update provider metadata serialization and model creation logic to preserve neutral rerank adapter semantics.
- [x] 1.3 Add or update backend tests proving rerank model creation and provider type metadata use the neutral adapter type.

## 2. Provider-Backed Rerank Adapters

- [x] 2.1 Implement a ZhiPu reranking client that maps `RerankRequest` to the provider rerank API and normalizes results, usage, warnings, and provider metadata.
- [x] 2.2 Implement a DashScope reranking client that maps `RerankRequest` to the provider rerank API and normalizes results, usage, warnings, and provider metadata.
- [x] 2.3 Implement a SiliconFlow reranking client that maps `RerankRequest` to the provider rerank API and normalizes results, usage, warnings, and provider metadata.
- [x] 2.4 Wire ZhiPu, DashScope, and SiliconFlow provider types to advertise rerank support and construct their provider-specific reranking clients.
- [x] 2.5 Add unit tests for request mapping, response mapping, provider error handling, unsupported option warnings, and result index validation for all three provider adapters.

## 3. Remote Rerank Model Discovery

- [x] 3.1 Extend ZhiPu discovery to include rerank models only when remote metadata explicitly declares rerank type or capability.
- [x] 3.2 Extend DashScope discovery to include rerank models only when remote metadata explicitly declares rerank type or capability.
- [x] 3.3 Extend SiliconFlow discovery to include rerank models only when remote metadata explicitly declares rerank type or capability.
- [x] 3.4 Ensure discovery does not infer rerank support from model id substrings and keeps manual model creation available when discovery returns no rerank models.
- [x] 3.5 Add discovery tests for explicit rerank metadata, unclassified remote models, provider failures, and manual fallback behavior.

## 4. Console RAG Test Endpoint

- [x] 4.1 Define console request/response DTOs for a single-query RAG test with manual sources, optional rerank model name, top count, provider options, and RAG options.
- [x] 4.2 Add a console-only `test-rag` endpoint that builds a request-scoped manual-source retriever and optional `RerankingModelRagSourceReranker`.
- [x] 4.3 Stream the RAG test response using the existing UIMessage stream protocol and standard RAG data part names.
- [x] 4.4 Preserve original source ids, original indexes, final source order, scores, rerank scores, warnings, and display-safe metadata in emitted diagnostics.
- [x] 4.5 Add endpoint tests for RAG without rerank, RAG with provider-backed rerank, empty/invalid source input, and diagnostic data parts.

## 5. Workbench RAG Mode

- [x] 5.1 Add a RAG test mode to the model test workbench with chat model selection, optional rerank model selection, query input, manual source editing, and top count controls.
- [x] 5.2 Use the generated API client and UIMessage runtime path to submit RAG test requests and consume streamed responses.
- [x] 5.3 Display final answer text separately from retrieval, rerank, source, warning, and error diagnostics.
- [x] 5.4 Display source panels with original order, final order, score, rerank score, title, URL, and metadata when available.
- [x] 5.5 Ensure the RAG workbench remains single-query and does not require knowledge base, vector store, document store, or crawler configuration.

## 6. Documentation

- [x] 6.1 Update developer documentation to explain provider-backed reranking for ZhiPu, DashScope, and SiliconFlow without hardcoding recommended model catalogs.
- [x] 6.2 Document remote discovery boundaries: only explicit provider metadata maps to rerank, and manual model creation remains the fallback.
- [x] 6.3 Document the RAG workbench as a console testing tool and production RAG as SDK composition with retrievers, middleware, and optional reranking.

## 7. Validation

- [x] 7.1 Run `openspec validate add-rerank-adapters-and-rag-workbench --strict`.
- [x] 7.2 Run backend tests covering provider adapters, discovery, rerank model resolution, and the RAG test endpoint.
- [x] 7.3 Run `./gradlew generateApiClient -x :ui:pnpmBuild` after endpoint or schema changes.
- [x] 7.4 Run `cd ui && pnpm type-check`.
- [x] 7.5 Run `cd ui && pnpm build-only`.
