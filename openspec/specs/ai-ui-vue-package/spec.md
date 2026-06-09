## Purpose

Define the publishable Vue runtime package for building Halo AI chat, completion, and structured object streaming interfaces.

## Requirements

### Requirement: Publishable Vue runtime package
The system SHALL provide a publishable `@halo-dev/ai-ui-vue` package that is separate from the plugin console UI.

#### Scenario: Package is separate from console source
- **WHEN** the frontend workspace is installed
- **THEN** the package source SHALL live under `ui/packages/ai-ui-vue`
- **AND** it SHALL NOT be mixed into `ui/src`

#### Scenario: Package exposes npm metadata
- **WHEN** the package is built for publication
- **THEN** it SHALL expose ESM JavaScript and TypeScript declarations through explicit package exports
- **AND** it SHALL declare `vue` as a peer dependency

#### Scenario: Package avoids console-only dependencies
- **WHEN** a non-console Vue application installs the package
- **THEN** it SHALL NOT need `@halo-dev/components`, `@halo-dev/ui-shared`, or the plugin generated API client

### Requirement: Halo UI message TypeScript model
The package SHALL expose TypeScript types that model the Halo UI message HTTP wire contract.

#### Scenario: Message roles use wire values
- **WHEN** a caller creates or receives a UI message
- **THEN** the message role SHALL use lowercase string values such as `system`, `user`, and `assistant`

#### Scenario: Part and chunk types use wire values
- **WHEN** a caller inspects UI message parts or stream chunks
- **THEN** discriminators SHALL use the lowercase and kebab-case values sent over HTTP
- **AND** the public frontend types SHALL NOT require Java enum names

### Requirement: Chat core class
The package SHALL provide a framework-neutral `Chat` class for Halo UI message conversations.

#### Scenario: Chat does not import Vue
- **WHEN** tests instantiate `Chat` with a plain state adapter
- **THEN** chat state transitions, message mutation, cancellation, and stream handling SHALL work without Vue runtime APIs

#### Scenario: Chat sends Halo chat requests
- **WHEN** a caller sends a user message through `Chat`
- **THEN** the transport request body SHALL include the chat id, current messages, trigger `submit-message`, and relevant message id

#### Scenario: Chat regenerates assistant output
- **WHEN** a caller regenerates an assistant message
- **THEN** the request SHALL use trigger `regenerate-message`
- **AND** the visible chat state SHALL remove the regenerated assistant output before appending the new response
- **AND** the transport request MAY preserve the original message list with `messageId` when the backend requires the target assistant message for validation

#### Scenario: Chat stops active response
- **WHEN** a caller stops a submitted or streaming response
- **THEN** the active abort controller SHALL abort the request
- **AND** any partial assistant message already received SHALL remain in state

### Requirement: Vue chat composable
The package SHALL provide `useChat` as a Vue adapter around the `Chat` core.

#### Scenario: useChat exposes reactive state
- **WHEN** a Vue component calls `useChat`
- **THEN** it SHALL receive reactive messages, status, error, and chat action helpers

#### Scenario: useChat keeps input external
- **WHEN** a caller uses `useChat`
- **THEN** the composable SHALL NOT require or manage an input field value
- **AND** the caller SHALL send user content through `sendMessage`

#### Scenario: Shared id reuses chat state
- **WHEN** two Vue scopes call `useChat` with the same id
- **THEN** they SHALL observe and mutate the same chat state

#### Scenario: Last subscriber cleanup
- **WHEN** the last Vue scope using a generated or explicit chat store is disposed
- **THEN** the package SHALL release that store unless the caller keeps an explicit `Chat` instance

### Requirement: Halo chat transports
The package SHALL provide transports for Halo UIMessage SSE streams and text streams.

#### Scenario: Default transport reads Halo UIMessage SSE
- **WHEN** the default chat transport receives an SSE response containing JSON UI message chunks
- **THEN** it SHALL parse each chunk and feed it to the chat stream reducer
- **AND** it SHALL treat `[DONE]` as the normal completion marker

#### Scenario: Default transport validates Halo protocol marker
- **WHEN** the response includes `X-Halo-AI-UI-Message-Stream`
- **THEN** the transport SHALL accept version `v1`
- **AND** it SHALL fail the request for unsupported versions

#### Scenario: Text stream transport appends assistant text
- **WHEN** the text stream chat transport receives text chunks
- **THEN** it SHALL append them to the active assistant text part in order

#### Scenario: Transport resolves fetch at request time
- **WHEN** a transport sends a request
- **THEN** it SHALL use a caller-provided fetch or resolve `globalThis.fetch` at that moment
- **AND** it SHALL NOT cache fetch at module initialization

### Requirement: Tool continuation helpers
The package SHALL support frontend continuation of Halo tool calls and approval requests.

#### Scenario: Add tool output
- **WHEN** a caller adds a tool output for a pending tool call through `useChat`
- **THEN** the package SHALL append or update the matching `tool-result` part
- **AND** it SHALL resolve the tool name from existing assistant message parts when the caller only provides `toolCallId`

#### Scenario: Add tool error
- **WHEN** a caller adds a tool error for a pending tool call
- **THEN** the package SHALL append or update a `tool-error` part associated with the matching assistant message

#### Scenario: Add approval response
- **WHEN** a caller responds to a tool approval request through `useChat`
- **THEN** the package SHALL append or update a `tool-approval-response` part for the matching approval id
- **AND** it SHALL resolve the tool call id and tool name from the existing approval request when possible

#### Scenario: Automatic continuation
- **WHEN** a tool helper changes messages and `sendAutomaticallyWhen` returns true
- **THEN** the chat SHALL submit the updated message history without requiring the caller to invoke `sendMessage`

### Requirement: Completion composable
The package SHALL provide `useCompletion` for prompt-based streamed text completion.

#### Scenario: Completion sends prompt body
- **WHEN** a caller invokes `complete("hello")`
- **THEN** the request SHALL post `{ "prompt": "hello" }` plus caller-provided body fields to the configured endpoint

#### Scenario: Completion consumes text stream
- **WHEN** the completion endpoint streams text chunks
- **THEN** the composable SHALL append chunks to the reactive completion string in order

#### Scenario: Completion input helpers
- **WHEN** a caller uses the returned input helpers
- **THEN** the composable SHALL expose input, setInput, handleSubmit, loading, stop, and error state

### Requirement: Object composable
The package SHALL provide `experimental_useObject` for streamed structured object generation.

#### Scenario: Object request includes schema and output
- **WHEN** a caller submits input with a JSON Schema
- **THEN** the request body SHALL include the input, schema, and an `output` object describing object output for the backend

#### Scenario: Object stream updates partial object
- **WHEN** the endpoint streams JSON text incrementally
- **THEN** the composable SHALL parse safe partial snapshots and update the reactive object when the snapshot changes

#### Scenario: Object stream validates final output
- **WHEN** the stream completes
- **THEN** the composable SHALL strictly parse and validate the final object against the configured schema
- **AND** it SHALL expose an error if final validation fails

#### Scenario: Zod is optional compatibility
- **WHEN** a caller passes a supported Zod schema and has installed the optional dependency path
- **THEN** the package MAY convert or validate through Zod
- **AND** JSON Schema SHALL remain the primary documented protocol

### Requirement: OpenAPI request preparation
The package SHALL allow generated OpenAPI request builders to provide stream endpoint request details without taking over stream consumption.

#### Scenario: Chat transport prepares request from OpenAPI args
- **WHEN** a caller uses an OpenAPI param creator to build chat stream request args
- **THEN** the default chat transport SHALL be able to use the prepared URL, headers, body, and credentials while still consuming the response through fetch and the Halo stream parser

#### Scenario: Completion and object composables prepare requests
- **WHEN** callers use `useCompletion` or `experimental_useObject` with generated OpenAPI request args
- **THEN** the composables SHALL accept prepared request values before posting
- **AND** they SHALL still consume text streams through the package runtime rather than Axios operation promises

### Requirement: SSR import safety
The package SHALL be safe to import in server-rendered Vue environments.

#### Scenario: Module import does not touch browser globals
- **WHEN** a Nuxt or SSR build imports the package
- **THEN** module initialization SHALL NOT access `window`, `document`, browser fetch, abort controllers, or stream constructors

#### Scenario: Requests require runtime Web APIs
- **WHEN** a caller starts a streaming request
- **THEN** the package SHALL use runtime Web APIs or caller-provided equivalents
- **AND** missing runtime APIs SHALL surface as request errors rather than import-time failures

### Requirement: Package documentation
The package SHALL document installation, runtime APIs, protocols, and backend expectations.

#### Scenario: README covers Vue usage
- **WHEN** a user reads the package README
- **THEN** it SHALL show basic `useChat`, `useCompletion`, and `experimental_useObject` examples

#### Scenario: README covers backend contracts
- **WHEN** a user reads the package README
- **THEN** it SHALL describe the Halo UIMessage SSE and text stream response expectations
