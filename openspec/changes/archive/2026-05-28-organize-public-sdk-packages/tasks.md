## 1. Package Inventory And Mapping

- [x] 1.1 Inventory every public `api` type and assign it to the target SDK package from the design.
- [x] 1.2 Identify root-package types that may remain as top-level entry points and document the reason for each.
- [x] 1.3 Identify all repo call sites, tests, JavaDoc snippets, and `dev/dev.md` examples that import or reference public SDK types.

## 2. Public API Package Move

- [x] 2.1 Move chat generation service, request, result, usage, timeout, finish, stop, and step-control types into the chat package.
- [x] 2.2 Move model message and message content part types into the message package.
- [x] 2.3 Move generation content, reasoning, stream part, and part kind types into the part package.
- [x] 2.4 Move JSON schema and structured schema helpers into the schema package.
- [x] 2.5 Move tool definition, choice, call, result, error, executor, and execution context types into the tool package.
- [x] 2.6 Move embedding service, request, response, usage, warning, metadata, lifecycle, and utility types into the embedding package.
- [x] 2.7 Move generation lifecycle callback and event types into the lifecycle package.
- [x] 2.8 Move provider option helpers into the options package, model/provider info value types into the model package, and public exceptions into the exception package.
- [x] 2.9 Remove old root-package public type declarations instead of adding compatibility aliases.

## 3. Repo-Wide Import And Documentation Updates

- [x] 3.1 Update `app` implementation imports to use the reorganized SDK packages.
- [x] 3.2 Update all backend tests and SDK ergonomics tests to use the reorganized SDK packages.
- [x] 3.3 Update JavaDoc examples and `dev/dev.md` import guidance/examples to compile against the new package names.
- [x] 3.4 Add `package-info.java` documentation for the public SDK packages introduced by this change.

## 4. Static Quality Gates

- [x] 4.1 Select and configure a Gradle-compatible Java quality gate with minimal high-signal rules.
- [x] 4.2 Avoid adding Gradle formatting or lint checks for import style.
- [x] 4.3 Add a package organization rule or verification task that fails when public SDK types remain in the wrong package.
- [x] 4.4 Ensure the quality gate runs through `./gradlew build`.

## 5. Validation

- [x] 5.1 Run `./gradlew compileJava` and fix package/import issues.
- [x] 5.2 Run `./gradlew test` and fix behavior regressions.
- [x] 5.3 Run `./gradlew build` and verify Java quality gates and UI checks still pass.
- [x] 5.4 Run `openspec validate organize-public-sdk-packages --strict`.
- [x] 5.5 Check whether OpenAPI/generated frontend client files changed; regenerate and include them only if endpoint schema output requires it.
