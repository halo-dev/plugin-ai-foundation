## 1. Backend Naming

- [x] 1.1 Add a focused `AiModel` resource-name generator/helper that normalizes provider and model identifiers into a DNS-compliant readable prefix.
- [x] 1.2 Add suffix generation for normalized-name collision resistance, including Ollama-style model IDs with `.` and `:`.
- [x] 1.3 Add collision fallback so create can choose another DNS-compliant `metadata.name` when the first generated candidate already exists.
- [x] 1.4 Replace `ModelConsoleEndpoint` inline naming logic with the shared generator/helper.
- [x] 1.5 Remove create/update uniqueness checks that reject requests solely because another resource has the same `spec.providerName` and `spec.modelId`.

## 2. Tests

- [x] 2.1 Add unit coverage for DNS-compliant name generation from model IDs containing `/`, `:`, `_`, `.`, spaces, and uppercase letters.
- [x] 2.2 Add coverage for Ollama examples such as `qwen2.5-coder:7b` and `llama3.1:8b`.
- [x] 2.3 Add coverage that normalized-prefix collisions receive distinct resource-name candidates.
- [x] 2.4 Add endpoint coverage that duplicate `spec.providerName + spec.modelId` can create a separate `AiModel` with a distinct `metadata.name`.
- [x] 2.5 Update existing model create/update tests that asserted plain slug names or provider/model uniqueness.

## 3. Verification

- [x] 3.1 Run `./gradlew :app:test --tests "run.halo.aifoundation.endpoint.ModelConsoleEndpointTest"`.
- [x] 3.2 Run broader backend tests if naming logic is placed outside the endpoint package.
- [x] 3.3 Run `openspec validate fix-ai-model-resource-name --strict`.
