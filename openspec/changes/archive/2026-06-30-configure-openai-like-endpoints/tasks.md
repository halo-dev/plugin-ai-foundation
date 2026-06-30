## 1. Backend Contract

- [x] 1.1 Add optional chat, embedding, rerank, and image endpoint path fields to `AiProvider.spec`.
- [x] 1.2 Add server-side validation and normalization for endpoint path fields, rejecting absolute URLs.
- [x] 1.3 Expose default endpoint paths through provider type metadata where needed for UI previews.
- [x] 1.4 Regenerate OpenAPI docs and the TypeScript API client after schema changes.

## 2. Provider Runtime

- [x] 2.1 Add OpenAI-compatible endpoint resolution helpers that combine Base URL with provider endpoint overrides.
- [x] 2.2 Update chat, embedding, and image OpenAI-compatible client construction to honor configured endpoint paths for `openailike`.
- [x] 2.3 Enable `AdapterType.RERANK` on `OpenAiLikeProvider`.
- [x] 2.4 Build an `openailike` rerank client using the configured or default rerank endpoint.
- [x] 2.5 Add focused backend tests for default paths, overridden paths, validation failures, and rerank client construction.

## 3. Console UI

- [x] 3.1 Extend provider form state and create/edit submissions with endpoint path fields from the generated client types.
- [x] 3.2 Show chat, embedding, rerank, and image endpoint fields only for `openailike`.
- [x] 3.3 Place endpoint fields above proxy host and proxy port.
- [x] 3.4 Add help previews that update from Base URL plus each configured or default endpoint path.
- [x] 3.5 Ensure built-in providers do not show endpoint override fields.

## 4. Verification

- [x] 4.1 Run `./gradlew compileJava`.
- [x] 4.2 Run focused backend tests covering provider validation and OpenAI-compatible endpoint resolution.
- [x] 4.3 Run `cd ui && pnpm type-check`.
- [x] 4.4 Inspect the provider form in the UI to confirm field ordering and preview behavior.
