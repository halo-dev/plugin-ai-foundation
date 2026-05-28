## 1. Package Structure

- [x] 1.1 Create cohesive implementation subpackages for model resolution, language runtime, language mapping, stream handling, tool execution, structured output, and embedding runtime.
- [x] 1.2 Move existing service helper classes out of the flat `service` package into the matching subpackages.
- [x] 1.3 Keep Spring service entry points and service interfaces in `run.halo.aifoundation.service`.

## 2. Service Boundaries

- [x] 2.1 Introduce internal interfaces only for injected service ports that benefit from a clear boundary.
- [x] 2.2 Refactor `AiModelServiceImpl` to delegate model/provider resolution and runtime creation through the new internal ports where appropriate.
- [x] 2.3 Avoid interfaces for deterministic mappers, aggregators, normalizers, and value objects unless a real second implementation or injection boundary exists.

## 3. Runtime Decomposition

- [x] 3.1 Review `LanguageModelImpl` and extract clearly separable responsibilities into package-private collaborators where this reduces class size and improves cohesion.
- [x] 3.2 Review `EmbeddingModelImpl` and extract batching, retry, option, lifecycle, or metadata responsibilities into package-private collaborators where this reduces class size and improves cohesion.
- [x] 3.3 Update visibility so implementation-only helpers are package-private whenever Spring injection and tests do not require public access.

## 4. References And Tests

- [x] 4.1 Update all imports, package declarations, tests, and documentation references after moving implementation classes.
- [x] 4.2 Retain or update extension definition tests so Halo service discovery still references the compiled `AiModelServiceImpl` class name.
- [x] 4.3 Add a focused backend architecture validation that fails when helper classes are added directly to the flat `service` package, without adding checkstyle or formatting tools.

## 5. Verification

- [x] 5.1 Run `./gradlew compileJava` and fix compile errors caused by package movement.
- [x] 5.2 Run focused backend tests for AI model service resolution, language generation helpers, embedding behavior, and extension metadata.
- [x] 5.3 Run `./gradlew build`.
- [x] 5.4 Run `openspec validate organize-app-service-architecture --strict`.
