## Why

Halo AI Foundation already provides backend UI message streams and structured output support, but Vue callers still need to hand-roll stream parsing, chat state, tool continuation, and object streaming behavior. A published Vue runtime package will make the SDK UI layer reusable outside this plugin console while letting the current workbench dogfood the same public API.

## What Changes

- Add a publishable `@halo-dev/ai-foundation-sdk` package under `ui/packages/ai-ui-vue`.
- Provide Vue composables and supporting runtime APIs equivalent in scope to AI SDK UI for Vue: `useChat`, `useCompletion`, and `experimental_useObject`.
- Provide framework-neutral chat core primitives inside the package, including `Chat`, Halo chat transports, stream readers, message reducers, tool continuation helpers, and public TypeScript types for Halo UI messages.
- Support Halo UIMessage SSE streams and text streams as the package transport protocols.
- Support JSON Schema first for object generation, with optional Zod compatibility when a caller installs the needed peer dependency.
- Add backend support where needed for text completion and object streaming endpoints used by the package and current console workbench.
- Migrate the model test workbench chat stream path to consume the new package without extracting console UI components into the package.
- Document npm usage in the package README and document Halo backend integration in `dev/ui-message-stream.md`.

### Non-Goals

- Do not provide visual chat, completion, or object UI components in the package.
- Do not depend on Halo Console-only packages such as `@halo-dev/components`, `@halo-dev/ui-shared`, or this plugin's generated API client.
- Do not support Vercel/AI SDK data-stream headers or wire protocol as a first-class input protocol in the initial release.
- Do not publish, push, commit, or create a PR as part of this change proposal.

## Capabilities

### New Capabilities

- `ai-ui-vue-package`: Publishable Vue runtime package for Halo AI UI chat, completion, object streaming, transports, stream parsing, shared state, and TypeScript types.

### Modified Capabilities

- `ui-message-stream`: Clarify and support frontend package consumption of Halo UIMessage SSE streams and tool continuation messages.
- `structured-output-generation`: Support object-stream endpoints that expose structured output as incremental JSON text for `experimental_useObject`.
- `model-test-workbench`: Dogfood the Vue package for the console chat test stream path while preserving the existing workbench UI.

## Impact

- Adds a workspace package below `ui/packages/ai-ui-vue` with its own package metadata, README, source, tests, and build configuration.
- Updates `ui/pnpm-workspace.yaml` and the console UI package dependency graph so the plugin UI can consume the local package.
- May add backend console/test endpoints or request handling for completion and object streaming when current endpoints are insufficient.
- Updates frontend workbench internals to use the package runtime for UIMessage chat state and stream handling.
- Adds focused tests for stream parsing, chat state, tool continuation, completion, object streaming, and any changed backend endpoint behavior.
- Updates `dev/ui-message-stream.md` with frontend package integration guidance.
