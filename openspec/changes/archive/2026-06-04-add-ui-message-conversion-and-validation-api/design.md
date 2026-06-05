# Design: UI Message Conversion And Validation API

## API Placement

The conversion and validation API belongs in `api/src/main/java/run/halo/aifoundation/ui/`.

Rationale:

- Consumer plugins compile against `run.halo.aifoundation:api`.
- `plugin-ai-foundation` packages API classes into the installed app jar.
- Consumer plugins must use `compileOnly` for the API and declare `pluginDependencies.ai-foundation` so there is one runtime copy of the public SDK classes.
- The helpers are pure SDK logic and must not depend on `app`, Spring WebFlux, `AiProvider`, `AiModel`, provider registries, or provider runtime beans.

## Public Types

### Conversion

Add:

- `UIMessageConverters`
- `UIMessageConversionOptions<M>`
- `UIMessageConversionResult`
- `UIMessageConversionWarning`
- `UIMessagePartConverter<M>`
- `UIMessageDataConverter<M>`
- `UIMessageConversionContext<M>`
- `UnsupportedUIMessagePartPolicy`
- `EmptyUIMessagePolicy`
- `UIReasoningConversion`

Recommended entry points:

```java
List<ModelMessage> modelMessages =
    UIMessageConverters.toModelMessages(uiMessages);

UIMessageConversionResult result =
    UIMessageConverters.convertToModelMessages(uiMessages, options -> options
        .unsupportedPartPolicy(UnsupportedUIMessagePartPolicy.WARN)
        .emptyMessagePolicy(EmptyUIMessagePolicy.SKIP)
    );
```

`toModelMessages(...)` is the ergonomic list-returning helper. `convertToModelMessages(...)` returns model messages plus warnings.

### Validation

Add:

- `UIMessageValidators`
- `UIMessageValidationOptions<M>`
- `UIMessageValidationResult<M>`
- `UIMessageValidationIssue`
- `InvalidUIMessageException`
- `UIMessageValidationContext<M>`
- `UIMessageMetadataValidator<M>`
- `UIMessageDataValidator<M>`
- `UIMessageToolValidator<M>`

Recommended entry points:

```java
List<UIMessage<MyMetadata>> validMessages =
    UIMessageValidators.validate(messages, options -> options
        .metadataValidator(...)
        .dataValidator("post-draft", ...)
    );

UIMessageValidationResult<MyMetadata> result =
    UIMessageValidators.safeValidate(messages, options -> options
        .toolValidator(...)
    );
```

`validate(...)` throws `InvalidUIMessageException` when issues exist. `safeValidate(...)` returns issues without throwing.

## Default Conversion Rules

The converter treats `UIMessage` as application state and `ModelMessage` as model context. They are not equivalent.

Default rules:

- `UIMessageRole.SYSTEM` converts to a system `ModelMessage`.
- `UIMessageRole.USER` converts to a user `ModelMessage`.
- `UIMessageRole.ASSISTANT` converts to an assistant `ModelMessage`.
- `TextPart` converts to text model content.
- `ToolCallPart`, `ToolResultPart`, and `ToolErrorPart` convert using the existing provider-neutral tool message model where supported.
- `ToolApprovalRequestPart` is skipped with warning by default.
- `DataPart` is skipped with warning by default unless a converter is registered for its name.
- `SourceUrlPart` and `FilePart` are skipped with warning by default.
- `ReasoningPart.text()` is not converted to ordinary prompt text by default.
- `ReasoningPart.providerMetadata()` is not interpreted by the default converter, but the API exposes `UIReasoningConversion.PRESERVE_PROVIDER_STATE` and converter hooks for future provider-specific logic.
- Metadata is ignored by default and remains available to custom converters through context.
- A message with no convertible parts is skipped with `message.empty-after-conversion` warning by default.

Strict callers can set:

- `unsupportedPartPolicy(FAIL)`
- `emptyMessagePolicy(FAIL)`

## Data Part Conversion

Applications can register data converters by name:

```java
options.dataConverter("post-draft", (part, context) -> List.of(...));
```

This avoids treating arbitrary UI state as model prompt content.

## Custom Part Conversion

Applications can register fallback part converters:

```java
options.partConverter((part, context) -> ...);
```

The default converter should run built-in conversion first, then named data converters, then custom part converters. A custom converter returning no content can produce a `part.converter-empty` warning when policy is `WARN`.

## Validation Rules

Base validation SHALL check structure only:

- message list is non-null and contains no null messages
- message id is present
- role is present
- parts list is non-null and contains no null parts
- part type discriminator is present
- text/reasoning ids are present when required
- data name is present
- tool call id is present for tool call/result/error/approval parts
- tool name is present for tool call and approval parts
- tool result/error refers to a previous tool call id in the UI message history
- a tool call id has at most one final result or final error

Validation SHALL NOT:

- mutate or migrate input messages
- verify that a tool still exists in the next request's tool registry unless a caller-provided validator does so
- validate business schemas for metadata or data parts unless a caller-provided validator does so
- execute tools
- convert approval requests into results

Validator hooks return issue lists. If a hook throws, `safeValidate(...)` converts the exception into a `validator.exception` issue; `validate(...)` throws `InvalidUIMessageException` based on the final issue list.

## Reasoning State Boundary

Some providers require opaque reasoning or thinking state to be sent back in later requests. This API must not flatten visible reasoning text into prompt text by default.

First version behavior:

- expose `UIReasoningConversion.DROP`, `PRESERVE_PROVIDER_STATE`, `INCLUDE_TEXT_AS_CONTEXT`, and `STRICT`
- default to `PRESERVE_PROVIDER_STATE`
- do not implement provider-specific preservation in the default converter
- emit warning such as `reasoning.provider-state-unsupported` when preservation is requested but no converter can handle the state

Provider-specific conversion can be added later once provider/model metadata becomes a public SDK contract or a `LanguageModel`-aware helper is introduced.

## Documentation

Update consumer-facing docs to show:

- `compileOnly 'run.halo.aifoundation:api:...'`
- `pluginDependencies.ai-foundation`
- validate persisted UI messages
- convert to model messages
- call `streamText(...)`
- persist the final `UIMessageStreamFinish.messages()`
- inspect conversion warnings
- register data validators/converters

The docs must not instruct consumers to use `implementation 'run.halo.aifoundation:api'`.

## Risks

- Tool message mapping may reveal limitations in the current `ModelMessagePart` shape. If so, keep conversion conservative and document skipped cases rather than inventing provider-native payloads.
- Reasoning state preservation is provider-specific. The first version must not imply that opaque state is fully preserved unless a converter handles it.
- Too many overloads can make the API hard to learn. Prefer list-based entry points only for the first version.
