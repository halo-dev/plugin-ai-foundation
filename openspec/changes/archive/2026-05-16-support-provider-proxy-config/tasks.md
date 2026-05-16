## 1. Validation

- [x] 1.1 Add server-side validation for `proxyHost` and `proxyPort` in provider create/update flow.
- [x] 1.2 Reject incomplete proxy configuration with a descriptive bad request error.
- [x] 1.3 Reject proxy ports outside the valid TCP port range with a descriptive bad request error.

## 2. Proxy-Aware Client Builders

- [x] 2.1 Add a shared provider-aware Reactor Netty `HttpClient` builder in `AbstractAiProviderType`.
- [x] 2.2 Apply HTTP proxy settings when both `spec.proxyHost` and `spec.proxyPort` are configured.
- [x] 2.3 Keep existing response timeout and connect timeout behavior for proxy and no-proxy clients.
- [x] 2.4 Update shared WebClient and RestClient helper methods to accept `AiProvider`.

## 3. Provider Integration

- [x] 3.1 Update all provider model builders to pass the provider resource into WebClient and RestClient helpers.
- [x] 3.2 Update all discovery paths, including provider-specific overrides, to use provider-aware WebClient helpers.
- [x] 3.3 Verify existing provider cache invalidation covers proxy changes on provider update.

## 4. Tests

- [x] 4.1 Add unit tests for proxy validation success and failure cases.
- [x] 4.2 Add shared builder tests covering no-proxy and configured-proxy behavior.
- [x] 4.3 Add or update discovery/client construction tests to prove configured proxy settings are used for upstream calls.

## 5. Verification

- [x] 5.1 Run backend tests for provider validation and client construction.
- [x] 5.2 Run `./gradlew test` or the narrowest equivalent Gradle test tasks needed for this change.
- [x] 5.3 Search for remaining no-arg `webClientBuilder()` and `restClientBuilder()` provider call sites and confirm none bypass proxy configuration.
