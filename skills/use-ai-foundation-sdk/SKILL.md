---
name: use-ai-foundation-sdk
description: Query, explain, integrate, and debug the Halo AI Foundation Java SDK, browser/Vue SDK, UI Message transport, and FormKit model selector. Use when an AI coding agent needs to answer AI Foundation API questions, add AI capabilities to a Halo plugin, implement text generation, structured output, tools, embeddings, reranking, RAG, image generation, streaming chat, message persistence, or model selection, or verify a consumer plugin against the official SDK contracts.
---

# Use AI Foundation SDK

Treat Halo AI Foundation as an independent SDK. Derive answers and code from its official
documentation and public contracts.

## Do not trust memory

AI Foundation evolves independently of other AI libraries. Never write its API calls from memory
or translate a similarly named API from another SDK. Verify every public type, method, option,
event, and package export against the documentation and source for the version used by the target
project.

## Use the closest version-matched evidence

Do not clone the repository by default. Use this evidence order:

1. If the current workspace is already an AI Foundation source checkout containing `api/`, `dev/`,
   and `ui/packages/sdk/`, use it directly.
2. Inspect the target project for its SDK versions:
    - Java: `run.halo.aifoundation:api:<version>` in Gradle files or version catalogs.
    - Browser/Vue: `@halo-dev/ai-foundation-sdk` in `package.json` and the active lockfile.
3. Prefer dependencies already resolved in the target project:
    - Read the Java source JAR from the Gradle module cache. If no source JAR exists, inspect public
      signatures from the binary JAR.
    - Read the npm package version, root exports, declaration files, and runtime files from
      `node_modules/@halo-dev/ai-foundation-sdk`.
    - Read [references/local-artifacts.md](references/local-artifacts.md) for exact lookup commands.
4. Use installed artifacts as the authority for the public contract of the installed version. They
   may not include the developer guides, integration examples, or application implementation.
5. Clone the official repository only when:
    - The dependency is not installed locally.
    - The installed artifacts do not answer the question.
    - The task needs guides, examples, JavaDoc context absent from the artifact, or runtime
      implementation details.

## Prepare the repository only when needed

Reuse the workspace's existing directory for reference repositories. Otherwise, use
`.reference/plugin-ai-foundation` and exclude `.reference/` locally, such as through
`.git/info/exclude`, so reference files are never committed to the consumer plugin.

Clone the official repository when the reference checkout is absent:

```bash
git clone --filter=blob:none \
  https://github.com/halo-dev/plugin-ai-foundation.git \
  .reference/plugin-ai-foundation
```

The remaining examples use this default path. Substitute the existing reference checkout path when
the workspace uses another convention.

Before using the checkout, refresh its branches and tags:

```bash
git -C .reference/plugin-ai-foundation fetch origin --tags --prune
```

Select the matching source:

- For a released dependency `<version>`, verify that `v<version>` exists and check it out in
  detached mode:

    ```bash
    git -C .reference/plugin-ai-foundation \
      show-ref --verify --quiet 'refs/tags/v<version>'
    git -C .reference/plugin-ai-foundation checkout --detach 'v<version>'
    ```

- For a snapshot, unreleased version, or missing tag, check out `origin/main` in detached mode and
  explicitly say that the answer was checked against `main`:

    ```bash
    git -C .reference/plugin-ai-foundation checkout --detach origin/main
    ```

- Java and npm dependencies may use different versions. Verify each surface against its own
  dependency version when both are involved; use `git show <ref>:<path>` when comparing files from
  two refs without repeatedly switching the checkout.
- If the repository cannot be cloned or refreshed, explain what could not be verified instead of
  guessing.

Treat the reference checkout as read-only. Never stage its files with the consumer plugin's
changes.

When a source checkout is available, use [references/sdk-map.md](references/sdk-map.md) to locate
the relevant documentation and public source.

## Follow the query workflow

1. Identify the requested surface:
    - Java backend SDK Core.
    - Browser or Vue SDK UI.
    - UI Message backend-to-frontend transport.
    - FormKit `aiModelSelector`.
    - Halo plugin dependency and lifecycle integration.
2. Choose `dev/zh-CN/` for Chinese output or `dev/en/` for English output.
3. Read the matching installed declaration/source or local path from the SDK map.
4. Read only the task-relevant documentation or artifact files.
5. Verify names, signatures, exports, and behavior against public source before writing code.
6. For a complete consumer shape, read `dev/{locale}/plugin-integration-examples.md`.
7. Inspect the target plugin's existing Gradle, `plugin.yaml`, settings, endpoint, and frontend
   conventions before editing it.

## Verify the public contract

- Treat `api/src/main/java/run/halo/aifoundation/` as the Java public contract.
- Treat exports from `ui/packages/sdk/src/index.ts` as the npm public contract.
- Treat `ui/src/formkit/ai-model-selector-input.ts` and
  `dev/{locale}/model-selector.md` as the model selector contract.
- Do not infer public support from classes under `app/` or unexported Console UI components.
- Search the reference checkout with an available code index or `rg`. Inspect exact public files
  for declarations and use literal search for dependency coordinates, YAML fields, endpoint paths,
  error codes, and package exports.
- Never invent a convenience method from another AI library. If a method is absent from the public
  source, find the supported composition in the current API.

## Integrate a Halo plugin

Apply these defaults unless the target plugin establishes a stronger convention:

- Add the Java API as `compileOnly`; add it to tests separately when tests load SDK types.
- Declare `ai-foundation` in `spec.pluginDependencies`.
- Use `ExtensionGetter.getEnabledExtension(AiModelService.class)` across plugin
  `ApplicationContext` boundaries.
- Store and pass `AiModel.metadata.name` as `modelName`.
- Resolve the appropriate `LanguageModel`, `EmbeddingModel`, `RerankingModel`, or
  `ImageGenerationModel` through `AiModelService`.
- Register beans that reference AI Foundation types only when AI Foundation is available if the
  dependency is optional.
- Use `aiModelSelector` in Halo settings rather than importing the Console's internal Vue
  component.
- Keep business concerns in the consumer plugin: authorization, rate limits, persistence, document
  chunking, vector storage, attachment lifecycle, and user-facing error messages.

## Preserve runtime contracts

- Keep Reactor composition non-blocking. Do not call `block()` in request paths.
- Use either `prompt` or `messages`; combine either with `system` when needed.
- Preserve `responseMessages` for tool loops or continued model context.
- Consume `StreamTextResult` projections according to the caller's need:
  `textStream()`, `fullStream()`, `partialOutputStream()`, `elementStream()`, `output()`, or
  `result()`.
- Keep every assistant tool call paired with one tool result or error.
- Validate and convert UI messages through the Java UI Message APIs before model execution.
- Send the Halo UI Message stream headers and `[DONE]` marker through
  `UIMessageStreamResponse`.
- Persist final reduced UI messages, not arbitrary partial chunks.
- Treat model capabilities and provider warnings as runtime data; do not infer them from model
  names.

## Validate changes

Run checks proportional to the edited surface:

```bash
# Consumer Java plugin
./gradlew compileJava
./gradlew test

# AI Foundation Java API source checkout
./gradlew :api:compileJava

# AI Foundation npm SDK source checkout
cd ui
pnpm --filter @halo-dev/ai-foundation-sdk typecheck
pnpm --filter @halo-dev/ai-foundation-sdk test
```

Also run the target plugin's established frontend type check and tests when changing its UI.

## Report with evidence

- Name the SDK version or repository ref used.
- Name the public types, exports, and documentation files used.
- Distinguish verified current behavior from consumer-specific policy.
- State which compile, type, or test checks ran.
- Present AI Foundation as an independent SDK and avoid cross-SDK parity framing.
