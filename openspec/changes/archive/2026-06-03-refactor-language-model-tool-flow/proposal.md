## Why

`LanguageModelImpl` and `LanguageModelImplTest` have accumulated too many responsibilities while adding tool execution, external tools, approval, response messages, and repair flows. This increases the risk that future tool features will break streaming/non-streaming parity or produce incomplete tool history.

## What Changes

- Extract tool-step orchestration from `LanguageModelImpl` into a focused backend component that owns approval, external-tool pending state, executable tool calls, and continuation eligibility.
- Extract response message history assembly into a focused helper that makes assistant tool-call, approval, tool-result, and tool-error history construction explicit and reusable.
- Keep `LanguageModelImpl` as the high-level language model facade and provider orchestration entry point.
- Split the largest tool-flow tests out of `LanguageModelImplTest` into focused test classes for tool loops, approval, external tools, repair, and response history.
- Preserve all current public APIs, stream part shapes, response message shapes, lifecycle events, and OpenSpec requirements.
- Non-goals:
  - Do not add new tool behavior or user-facing features.
  - Do not change generated OpenAPI contracts or frontend code.
  - Do not change provider-specific request mapping semantics.
  - Do not rewrite unrelated structured-output, reasoning, timeout, or provider-option code beyond what is needed for clean extraction.

## Capabilities

### New Capabilities

- `language-model-maintainability`: Internal maintainability requirements for language model tool-flow orchestration, response history assembly, and focused test organization.

### Modified Capabilities

- None. This is a backend-only internal refactor that preserves existing requirements.

## Impact

- Backend-only implementation and test organization changes.
- Affected areas:
  - `LanguageModelImpl`
  - tool execution support classes under `service/language/tool`
  - response message/history helper code under `service/language`
  - focused language model unit tests
- No new dependencies.
- No public Java API, REST API, OpenAPI, or frontend generated-client changes are expected.
