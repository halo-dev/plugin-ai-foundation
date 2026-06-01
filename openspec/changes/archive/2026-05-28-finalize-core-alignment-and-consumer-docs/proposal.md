## Why

This PR has accumulated the main language, structured output, tool calling, settings, and embedding features, but it still needs a final consumer-facing review before submission. The current `dev/dev.md` mixes SDK usage, implementation notes, debug details, and backend caveats, making it harder for consumer plugin authors to understand the supported public API path.

## What Changes

- Review the current public SDK against the requested Core feature areas: generating text, generating structured data, tools and tool calling, settings, embeddings, and the public reference surface.
- Fold support status and provider-dependent behavior into `dev/dev.md` instead of creating a separate audit document.
- Close the remaining PR-blocking settings gaps identified by the review:
  - first-class `seed` for text generation requests and step overrides
  - first-class `maxRetries` for text generation requests
  - provider mapping, validation/warnings, tests, and consumer documentation for those settings
- Rewrite `dev/dev.md` as a consumer integration guide organized around the public SDK workflows:
  - installation and model resolution
  - generating and streaming text
  - structured output
  - tools and multi-step calls
  - request settings, provider options, headers, timeout, cancellation, and reasoning
  - embeddings and similarity
  - errors, warnings, and testing
- Remove or relocate implementation-only details from `dev/dev.md`, including backend package architecture, provider internals, console implementation details, and low-level stream normalizer mechanics that callers do not need.
- Keep examples focused on typed public SDK APIs first, with raw provider options documented only as advanced escape hatches.
- Add documentation validation so examples and public API references are less likely to drift before PR submission.

Non-goals:

- Do not implement new AI modalities outside the requested scope, such as image generation, video generation, transcription, speech, reranking, middleware, or provider registries.
- Do not rename public SDK types only to mimic another SDK naming style.
- Do not add formatting plugins, checkstyle, or Gradle formatting tasks.
- Do not expose Spring AI or provider-native classes in consumer documentation.

## Capabilities

### New Capabilities
- `consumer-sdk-documentation`: Consumer-facing SDK documentation structure, required content, and implementation-detail boundaries for `dev/dev.md`.

### Modified Capabilities
- `sdk-ergonomics`: Document typed-first usage expectations and example drift prevention for public SDK workflows.
- `ai-model-service`: Clarify consumer-visible text generation and settings coverage in documentation.
- `structured-output-generation`: Clarify documented structured output workflows and unsupported/partial behaviors.
- `structured-tool-io`: Clarify documented tool calling workflows, multi-step behavior, and known limitations.
- `embedding-core-alignment`: Clarify documented embedding workflows, settings, and similarity utilities.

## Impact

- `dev/dev.md` will be substantially reorganized and shortened where it currently exposes implementation details.
- OpenSpec specs will gain requirements for consumer documentation quality.
- Tests or lightweight validation may be added to guard documented public type names, examples, and required sections.
- Runtime behavior changes are limited to the final Core settings closure for text generation (`seed` and `maxRetries`) and any small fixes required to remove overclaimed documentation.
