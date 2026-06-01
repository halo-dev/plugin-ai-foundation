# SDK Package Mapping

## Root Package

- `run.halo.aifoundation.AiModelService`: the Halo Extension Point and top-level SDK service entry.

## Domain Packages

- `chat`: language model API, text generation request/result, usage, finish reason, timeout, stop condition, stream result, and step controls.
- `message`: model messages, roles, and message input parts.
- `part`: generation output parts, reasoning parts, stream parts, and part kind constants.
- `schema`: JSON schema, structured schema, output specs, and output type helpers.
- `tool`: tool definitions, tool choices, tool calls, tool execution context, tool results, and tool errors.
- `embedding`: embedding model API, request/response, usage, warnings, metadata, lifecycle, and utility helpers.
- `lifecycle`: generation lifecycle callbacks and event payloads.
- `options`: provider-specific option namespace helpers.
- `model`: provider and model information value types.
- `exception`: public AI Foundation exception types.
- `control`: shared cancellation primitives used by both chat and embedding requests.

## Call Sites

- `app/src/main/java`: service implementation, provider support, and console endpoint imports.
- `app/src/test/java`: backend unit tests and endpoint tests.
- `dev/dev.md`: developer SDK examples and package guidance.
- `app/src/main/resources/extensions/ai-model-service-extension-point.yaml` and OpenSpec ai-model-service specs keep the root `AiModelService` reference because it remains the Extension Point class.
