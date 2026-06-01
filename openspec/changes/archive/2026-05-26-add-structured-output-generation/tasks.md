## 1. Public API Model

- [x] 1.1 Add structured output DTOs for output type, schema, element schema, choices, validation options, provider options, and transient Java class/record convenience factories.
- [x] 1.2 Extend `GenerateTextRequest` with an optional structured output specification.
- [x] 1.3 Extend `GenerateTextResult` and `GenerationStep` with parsed `output`, raw `outputText`, and output validation metadata.
- [x] 1.4 Keep structured output out of stream/content part types.
- [x] 1.5 Extend `ToolDefinition` with optional output schema and input examples while preserving provider-neutral Java collection types.
- [x] 1.6 Add JavaDoc examples for object, array, choice, json, Java record/class schema generation, stream partial output, and tool output schema use.

## 2. Validation and Parsing

- [x] 2.1 Add request validation for output type, object schema, array element schema, choice values, and JSON object shape.
- [x] 2.2 Implement local JSON parsing, Java class/record schema generation, and JSON Schema validation utilities for final structured outputs and tool input/output schemas.
- [x] 2.3 Add a typed structured output validation exception with safe error messages.
- [x] 2.4 Ensure final streamed output is validation-authoritative.

## 3. Provider Mapping

- [x] 3.1 Add provider-neutral hooks/options for applying structured output hints to provider calls.
- [x] 3.2 Implement OpenAI-compatible response format mapping for object/json output where supported by current Spring AI options.
- [x] 3.3 Keep structured output provider handling outside generic service provider-name checks.
- [x] 3.4 Preserve existing reasoning and streaming tool-call behavior when output hints are present.

## 4. Generation Flow

- [x] 4.1 Parse and validate final structured output in non-streaming `generateText`.
- [x] 4.2 Attach parsed structured output to the top-level result and final answer step.
- [x] 4.3 Support structured output after multi-step tool execution, validating the final answer step only.
- [x] 4.4 Return validation failures through the reactive error channel for `generateText`.

## 5. Streaming Flow

- [x] 5.1 Accumulate streamed text needed for final structured output validation.
- [x] 5.2 Validate complete structured stream text before final `finish`.
- [x] 5.3 Avoid emitting structured output as a generated content or lifecycle part.
- [x] 5.4 Emit safe `error` stream parts when final structured validation fails.
- [x] 5.5 Ensure structured output works with streamed tool-call continuation and preserves tool event ordering.

## 6. Tool IO

- [x] 6.1 Validate model-produced tool input against `ToolDefinition.inputSchema` before executor invocation.
- [x] 6.2 Emit or record `tool-error` without invoking the executor when tool input validation fails.
- [x] 6.3 Validate executor results against optional tool output schema before returning them to the model.
- [x] 6.4 Pass strict schema flags and input examples through provider hooks when supported.

## 7. Console Workbench

- [x] 7.1 Regenerate the OpenAPI TypeScript client after backend API field changes.
- [x] 7.2 Add minimal structured output controls to the model test workbench.
- [x] 7.3 Include structured output options in test-chat stream requests.
- [x] 7.4 Render structured stream output as normal assistant answer text while preserving reasoning and tool events.

## 8. Tests and Documentation

- [x] 8.1 Add backend tests for object, array, choice, json, and default text output modes.
- [x] 8.2 Add backend tests for structured output validation failure in non-streaming and streaming generation.
- [x] 8.3 Add backend tests for structured output after multi-step tool calls.
- [x] 8.4 Add backend tests for tool input schema validation and tool output schema validation.
- [x] 8.5 Add frontend parser/workbench tests for structured output as normal streamed text.
- [x] 8.6 Update `dev/dev.md` with structured output and structured tool IO examples.
- [x] 8.7 Run `./gradlew :app:test`, `./gradlew generateApiClient`, `pnpm --dir ui type-check`, targeted UI unit tests, and `openspec validate add-structured-output-generation --strict`.
