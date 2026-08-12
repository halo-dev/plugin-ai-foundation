## Why

Provider-neutral object, array, and choice outputs are currently sent as native strict JSON Schema
requests by the common OpenAI-compatible adapter even when the caller did not request strict
enforcement. Valid open JSON Schemas are consequently rejected by strict providers, while JSON-only
providers cannot accurately report whether native schema enforcement was applied.

## What Changes

- Give `OutputSpec.strict` an end-to-end three-state meaning: only `true` requests provider-native
  strict enforcement; `false` and an omitted value must not be upgraded to strict mode.
- Validate strict schemas against the supported closed-object contract before invoking a provider,
  including nested objects, required properties, nullable optional values, and unsupported schema
  constructs, and return a typed local error instead of a provider HTTP 400.
- Preserve non-strict schema guidance through a provider-supported non-strict response format or a
  documented JSON/prompt-guidance fallback with Halo-owned final parsing and validation.
- Make JSON-only provider paths, including DeepSeek, explicitly use JSON mode, concrete schema
  guidance, local validation, and stable downgrade warnings without claiming native strict schema
  enforcement.
- Cover object, array, choice, nested, nullable, strict, non-strict, provider fallback, warning, and
  request-serialization behavior with focused tests.
- Document strict schema requirements, portable optional-field modeling, fallback behavior, and
  structured-output diagnostics in both consumer languages.
- Add opt-in, request-correlated TRACE diagnostics that capture the provider request body, HTTP
  status, raw response, normalized model output, and structured-output validation result while
  excluding credentials and request headers.
- Add one safe WARN summary when structured output finally fails so production operators can inspect
  finish reasons and token usage without enabling full-content diagnostics or logging successful
  requests and stream events.
- Preserve normalized and raw finish reasons when structured output cannot be parsed, and classify
  token-limit, content-filter, tool-call, provider-error, and other explicit terminations separately
  from ordinary JSON or schema validation failures.

## Non-goals

- This change does not modify consumer plugins or rewrite their schemas automatically.
- This change does not promise that a provider will never return empty or malformed content; final
  local validation remains authoritative.
- This change does not add automatic structured-output repair or retry a completed provider
  response solely because of its finish reason.
- This change does not add Console UI controls or permissions; it is a backend and documentation
  change.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `structured-output-generation`: Define strict opt-in semantics, strict schema preflight,
  non-strict provider mapping, JSON-mode fallback, and provider warning behavior.
- `consumer-sdk-documentation`: Document portable strict and non-strict structured-output usage and
  diagnostics.

## Impact

- Public Java semantics: `OutputSpec.strict` becomes effective without adding provider-native types
  to the API.
- Backend runtime: structured-output validation, OpenAI-compatible response-format construction,
  DeepSeek mapping, warnings, provider request serialization, and opt-in correlated diagnostics.
- Tests and documentation: provider option tests, language generation tests, and Chinese/English SDK
  guides.
- No new dependencies and no frontend API-client regeneration are expected.
