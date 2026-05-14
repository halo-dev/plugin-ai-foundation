## 1. Backend Fix

- [x] 1.1 Modify `ProviderConsoleEndpoint.performConnectivityCheck()` to call `providerType.discoverModels()` instead of just building a `ChatModel` instance
- [x] 1.2 Ensure reactive pipeline is preserved (`flatMap` instead of `fromCallable` if needed)
- [x] 1.3 Run `./gradlew compileJava` to verify compilation

## 2. Verification

- [x] 2.1 Run `./gradlew test` to ensure no regressions
- [x] 2.2 Update `plan/code-review-2026-05-14.md` — strike through issue #3 as fixed
