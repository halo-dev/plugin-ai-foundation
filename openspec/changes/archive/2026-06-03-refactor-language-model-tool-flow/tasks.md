## 1. Baseline And Fixtures

- [x] 1.1 Run the focused language model test suite before refactoring to establish the current baseline.
- [x] 1.2 Introduce shared test fixture/helper support for language model tests without changing assertions.

## 2. Tool Step Coordination

- [x] 2.1 Add a `ToolStepCoordinator` and step-resolution result type that wrap tool approval evaluation, execution, warnings, recorded tool calls, and continuation eligibility.
- [x] 2.2 Update non-streaming `generateText` tool-loop code to use the coordinator.
- [x] 2.3 Update streaming `streamText` tool-loop code to use the same coordinator.
- [x] 2.4 Verify mixed executable/external, executable/approval, approval/unknown, repair, and server-side continuation tests still pass.

## 3. Response History Assembly

- [x] 3.1 Add a `GenerationMessageHistoryAssembler` for assistant response messages, approval messages, and tool response messages.
- [x] 3.2 Replace `LanguageModelImpl` private history assembly methods with calls to the assembler.
- [x] 3.3 Add or preserve tests proving response messages remain appendable for external tool, approval, repair, and multi-step tool flows.

## 4. Test Organization

- [x] 4.1 Split server-side tool-loop tests into a focused test class.
- [x] 4.2 Split external tool tests into a focused test class.
- [x] 4.3 Split approval tests into a focused test class.
- [x] 4.4 Split tool repair tests into a focused test class.
- [x] 4.5 Keep remaining broad generation, streaming, structured output, timeout, and reasoning coverage discoverable.

## 5. Verification

- [x] 5.1 Run focused language model tests after refactoring.
- [x] 5.2 Run `./gradlew compileJava`.
- [x] 5.3 Run `openspec validate refactor-language-model-tool-flow --strict`.
- [x] 5.4 Run `git diff --check`.
