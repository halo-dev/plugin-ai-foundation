## 1. Persisted Step Protocol

- [x] 1.1 Add marker-only `StepStartPart` to the Java UI message part model and JSON polymorphic contract
- [x] 1.2 Persist `StartStepChunk` in the Java reducer without making marker-only state visible
- [x] 1.3 Add `step-start` to the TypeScript SDK part union and persist it in the frontend reducer without creating blank visible content
- [x] 1.4 Validate that `step-start` is accepted only on assistant UI messages

## 2. Step-Aware Model History

- [x] 2.1 Refactor assistant UI-message conversion to partition parts by generation step
- [x] 2.2 Emit at most one assistant and one tool model message per nonempty step while preserving all calls and results
- [x] 2.3 Cover multi-tool, multi-step, implicit-step, empty-step, codec, reducer, and invalid-role behavior with focused tests

## 3. Effective Reasoning-History Capability

- [x] 3.1 Resolve model-level `reasoningHistory` overrides over provider defaults in the effective capability snapshot
- [x] 3.2 Feed the effective value consistently into capability reporting, request validation, history assembly, and UI-message chat handling
- [x] 3.3 Add runtime and provider-request regression tests for enabled, disabled, inherited, DeepSeek, and non-reasoning cases

## 4. Console Configuration

- [x] 4.1 Add a Chinese tri-state reasoning-history field to the language-model advanced capability editor
- [x] 4.2 Preserve null, true, and false values through model form initialization and submission
- [x] 4.3 Add focused frontend tests for inherited, supported, and unsupported selections

## 5. Documentation and Verification

- [x] 5.1 Update `dev/ui-message-stream.md` with only the public step-boundary persistence and reuse contract
- [x] 5.2 Run OpenSpec validation plus targeted backend and frontend tests, type checks, and formatting/lint gates
- [x] 5.3 Review the final diff for provider neutrality, generated-file policy, and absence of compatibility heuristics
