## 1. API DTOs

- [x] 1.1 Add provider-neutral DTOs for generated content parts, text output parts, generation warnings, request metadata, response metadata, and generation steps in `api/`
- [x] 1.2 Extend `GenerateTextResult` with `content`, `warnings`, `request`, `response`, `steps`, and `totalUsage` while preserving existing text, finish reason, usage, and provider metadata fields
- [x] 1.3 Extend `TextStreamPart` constants, fields, and factories for `start-step`, `finish-step`, and sanitized `raw` parts
- [x] 1.4 Compile the API module and adjust Lombok/OpenAPI annotations if generated schemas are incomplete

## 2. Backend Mapping

- [x] 2.1 Update `LanguageModelImpl.generateText()` to populate content parts, single-step details, total usage, warnings, request metadata, response metadata, and provider metadata from Spring AI responses
- [x] 2.2 Update `LanguageModelImpl.streamText()` to emit `start-step` and `finish-step` around existing text lifecycle parts
- [x] 2.3 Ensure streaming usage is reported on `finish-step` and total usage is reported on `finish` when available
- [x] 2.4 Ensure sanitized raw diagnostic parts do not include credentials, API keys, or unsanitized request bodies
- [x] 2.5 Add or update backend unit tests for enriched non-streaming result mapping, single-step result shape, stream lifecycle order, usage placement, warnings, and raw sanitization

## 3. Console Endpoint and UI

- [x] 3.1 Update console endpoint OpenAPI descriptions for the richer `TextStreamPart` protocol while keeping `X-Halo-AI-Stream-Protocol: text-v1`
- [x] 3.2 Regenerate the TypeScript API client after backend DTO/schema changes
- [x] 3.3 Update the model test workbench stream handler to render only `text-delta`, stop on `finish`, `error`, abort, or `[DONE]`, and ignore non-renderable or unknown part types
- [x] 3.4 Add or update frontend tests for rich stream parts, unknown stream parts, and progressive Markdown rendering

## 4. Documentation and Verification

- [x] 4.1 Update `dev/dev.md` with enriched `GenerateTextResult`, `GenerationStep`, warning, request/response metadata, and stream lifecycle examples
- [x] 4.2 Run `./gradlew :api:compileJava`
- [x] 4.3 Run `./gradlew :app:test`
- [x] 4.4 Run `pnpm --dir ui type-check`
- [x] 4.5 Run `pnpm --dir ui test:unit`
- [x] 4.6 Run `openspec validate align-text-generation-core-with-ai-sdk --strict`
