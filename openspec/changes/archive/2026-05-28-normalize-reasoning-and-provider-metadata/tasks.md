## 1. Reasoning Extraction

- [x] 1.1 Add a focused reasoning extraction component for provider metadata and balanced tagged reasoning text.
- [x] 1.2 Update non-streaming text generation step mapping to use extracted reasoning and cleaned answer text.
- [x] 1.3 Update non-streaming tool-step aggregation to use the same extraction behavior where final text is available.
- [x] 1.4 Ensure structured output parsing and validation use cleaned answer text after reasoning extraction.

## 2. Metadata Normalization

- [x] 2.1 Remove normalized `providerType`, `id`, and `model` entries from top-level generation `providerMetadata` output.
- [x] 2.2 Keep response id, model id, headers, body, and messages in typed request/response metadata fields.
- [x] 2.3 Stop emitting normalized `reasoningContent` and `reasoning_content` public metadata keys.
- [x] 2.4 Preserve provider-native metadata only under provider-specific namespaces when it must be surfaced.

## 3. Tests

- [x] 3.1 Add tests for provider-metadata reasoning extraction in non-streaming generation.
- [x] 3.2 Add tests for `<think>` and `<reasoning>` tagged text extraction, including unbalanced-tag behavior.
- [x] 3.3 Add tests proving structured object, array, choice, and JSON parsing use cleaned answer text.
- [x] 3.4 Add tests proving generation and step provider metadata exclude normalized response fields and duplicate reasoning aliases.
- [x] 3.5 Confirm existing stream behavior is not changed by this implementation.

## 4. Documentation And Verification

- [x] 4.1 Update `dev/dev.md` to document reasoning fields, answer text behavior, and metadata layering for callers.
- [x] 4.2 Run `openspec validate --all --strict`.
- [x] 4.3 Run `./gradlew test`.
- [x] 4.4 Run `./gradlew build` if API or generated artifacts are affected.
