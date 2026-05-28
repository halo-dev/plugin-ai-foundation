## 1. Public SDK API

- [x] 1.1 Add a public typed reasoning settings class or value object with documented mode and effort values.
- [x] 1.2 Add `GenerateTextRequest.reasoning` with JavaDoc and builder support.
- [x] 1.3 Add SDK tests for typed reasoning construction, null/default behavior, and Java serialization compatibility.

## 2. Request Validation

- [x] 2.1 Validate unsupported explicit reasoning settings before invoking providers.
- [x] 2.2 Detect conflicts between typed reasoning settings and known provider-native reasoning keys in `providerOptions`.
- [x] 2.3 Reject disabled reasoning when the request includes assistant reasoning history unless the provider declares support for that combination.

## 3. Provider Mapping

- [x] 3.1 Extend provider support options with a reasoning-control mapping hook that provider classes own.
- [x] 3.2 Implement DeepSeek thinking mode mapping for enabled and disabled reasoning.
- [x] 3.3 Implement OpenAI-compatible reasoning effort mapping where supported by the current provider adapter.
- [x] 3.4 Ensure providers with no mapping reject explicit reasoning settings with stable errors.

## 4. Output Semantics

- [x] 4.1 Preserve returned reasoning parts even when a provider returns reasoning after a disabled request.
- [x] 4.2 Add a stable warning when reasoning content is returned despite an explicit disabled reasoning request.
- [x] 4.3 Verify streaming and non-streaming paths keep reasoning separate from answer text.

## 5. Documentation

- [x] 5.1 Update public JavaDoc for reasoning settings, request field semantics, provider support behavior, and conflict behavior.
- [x] 5.2 Update `dev/dev.md` to show typed disabled reasoning for fast-response use cases.
- [x] 5.3 Keep raw provider reasoning options documented only as an advanced escape hatch.

## 6. Verification

- [x] 6.1 Add unit tests for request validation, DeepSeek mapping, OpenAI-compatible mapping, unsupported providers, and conflict detection.
- [x] 6.2 Run `./gradlew compileJava`.
- [x] 6.3 Run focused backend and API tests covering reasoning control.
- [x] 6.4 Run `./gradlew build`.
- [x] 6.5 Run `openspec validate add-reasoning-control --strict`.
