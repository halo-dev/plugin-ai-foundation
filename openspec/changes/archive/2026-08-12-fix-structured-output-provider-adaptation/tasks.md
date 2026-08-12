## 1. Public contract and schema validation

- [x] 1.1 Clarify `OutputSpec.strict`, name, and description JavaDoc and add a typed structured-output schema exception with validation path.
- [x] 1.2 Implement recursive portable strict-schema validation for object, array, choice, nested object, required-property, additional-property, and unsupported-composition rules.
- [x] 1.3 Add focused validator tests for accepted closed schemas, nullable values, nested objects, and every local rejection path.

## 2. Provider support profiles

- [x] 2.1 Add internal JSON Schema, JSON Object, and prompt-only structured-output support modes to language provider options.
- [x] 2.2 Declare the correct support mode for the common OpenAI-compatible path, DeepSeek, and Ollama without inferring support from factory presence.
- [x] 2.3 Update request warnings so native-format downgrade and strict-not-guaranteed diagnostics match the declared mode.

## 3. Response-format mapping

- [x] 3.1 Extend internal OpenAI-compatible response-format options with validated name, description, and strict values.
- [x] 3.2 Map object, array, choice, and raw JSON outputs according to strict intent and provider support without changing public output shapes.
- [x] 3.3 Serialize JSON Schema and JSON Object response formats without hard-coded strictness or names.
- [x] 3.4 Add compact schema/example guidance for JSON-only and prompt-only modes while preserving Halo-owned parsing and validation.

## 4. Runtime and provider verification

- [x] 4.1 Add OpenAI-compatible request-body tests for omitted, false, true, named, described, raw JSON, array, and choice output formats.
- [x] 4.2 Add DeepSeek and Ollama tests for JSON Object selection, prompt-only top-level fallbacks, and truthful warnings.
- [x] 4.3 Add language runtime tests proving strict schema errors occur before provider invocation and non-strict final local validation remains authoritative.

## 5. Documentation and validation

- [x] 5.1 Update Chinese and English structured-output guides with strict schemas, nullable fields, non-strict behavior, provider fallbacks, warnings, and safe diagnostics.
- [x] 5.2 Run focused provider and language model tests plus `./gradlew :api:compileJava` and `./gradlew :app:test`.
- [x] 5.3 Run `openspec validate fix-structured-output-provider-adaptation --strict` and `openspec validate --specs --strict` (the change passes; the repository-wide command still reports the pre-existing missing `Purpose` section in `openai-like-endpoint-config`).
- [x] 5.4 Review the final diff for generated files, unrelated changes, complete task coverage, and compatibility with Plugin Links' optional-field schema.

## 6. Correlated runtime diagnostics and live reproduction

- [x] 6.1 Add opt-in correlated TRACE diagnostics for OpenAI-compatible non-streaming requests,
  responses, normalized output, errors, and raw streaming events.
- [x] 6.2 Add structured-output parsing and validation diagnostics with schemas, extracted text,
  failure stages, and validation paths while excluding credentials and request headers.
- [x] 6.3 Add focused tests for correlation, diagnostic content, disabled-by-default behavior, and
  credential exclusion.
- [x] 6.4 Enable the dedicated logger locally, restart the Halo development environment, and reproduce
  Plugin Links recognition with the configured models in Chrome.
- [x] 6.5 Analyze the captured request/response chain, rerun backend tests and strict OpenSpec
  validation, then document the confirmed root cause and any remaining fix.

## 7. Finish-reason-aware structured output failures

- [x] 7.1 Add a public structured-output termination exception that preserves normalized/raw finish
  reasons while remaining compatible with existing validation exception handling.
- [x] 7.2 Classify failed non-streaming structured output by finish reason without rejecting valid
  structured values or partial plain-text results.
- [x] 7.3 Preserve the same termination classification and finish-reason context through streaming
  error parts, output publishers, result publishers, and correlated diagnostics.
- [x] 7.4 Add focused tests for token-limit, content-filter, normal-stop, valid-at-limit, plain-text,
  and streaming behavior.
- [x] 7.5 Update Chinese and English consumer guides, run backend tests and strict OpenSpec validation,
  and review the complete diff for obsolete or unrelated fixes.

## 8. Type-safe stream failure propagation

- [x] 8.1 Retain the first terminal exception on the per-invocation generation run and use the real
  exception type when building stream result/output failures.
- [x] 8.2 Remove string-based `exceptionType` production, parsing, and branching without adding a
  `Throwable` to the public stream-part contract.
- [x] 8.3 Update focused tests, run the full build and strict OpenSpec validation, and confirm no
  string exception dispatch remains in the language runtime.

## 9. Production-safe failure diagnostics

- [x] 9.1 Preserve opt-in full-content TRACE diagnostics and add one safe structured-output failure
  WARN summary with correlation, finish-reason, response, and scalar usage metadata.
- [x] 9.2 Add tests proving production summaries omit content and successful/TRACE events remain
  silent at normal log levels, then document the two logging tiers.
- [x] 9.3 Run focused diagnostics tests, the full build, strict OpenSpec validation, and final diff
  checks.
