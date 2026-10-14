# Shared tools

[简体中文](../zh-CN/tool-catalog.md) | English

AI Foundation provides built-in tools and lets plugins contribute their own tools. Tools are grouped
by source plugin under **AI Foundation → Shared tools**, with tags and status on each card.
Configure and enable or disable each tool from its card, or select shared tools in the model test
workbench to try them.

## Use built-in tools

| Tool            | Saved ID                      | Purpose                                                       |
| --------------- | ----------------------------- | ------------------------------------------------------------- |
| Site search     | `ai-foundation/search_site`   | Search public, published, non-recycled site content           |
| Read a web page | `ai-foundation/read_web_page` | Read a public page's title and text without executing scripts |
| Web search      | `ai-foundation/web_search`    | Search the internet through Brave Search                      |

Web search requires a Brave Search API key. Click **Configure** on its card, create or select the
key, and save. Keys are stored in Halo Secrets; tool settings store only the Secret reference.

Tools can be **Unconfigured**, **Enabled**, or **Disabled**. Unconfigured and disabled tools remain
visible but cannot be selected. The card switch controls whether a tool can be used. Saving
configuration does not automatically enable a disabled tool.

Web page reading supports public HTTP(S) URLs on ports 80 and 443. Pages requiring login are
unsupported. For redirects, supply the destination URL. Long page text is truncated.

## Select tools in plugin settings

Use the registered FormKit input type `aiToolSelector` in your plugin's settings form:

```yaml
formSchema:
    - $formkit: aiToolSelector
      name: toolIds
      label: Shared tools
```

The saved value is an array of tool IDs (`string[]`), such as `['ai-foundation/search_site']`.
The selector is a compact, searchable dropdown with multiple selection, grouped by tool source.

**Select all**, or **Select all results** when searching, adds only available tools in the current
results and retains other selections. **Deselect all** clears every selection, including unavailable
tools.

Disabled and unconfigured tools remain visible but cannot be selected. Unavailable selections are
retained and marked, so users can configure or enable the tool, or remove it explicitly.

| Optional prop       | Purpose                                                |
| ------------------- | ------------------------------------------------------ |
| `placeholder`       | Customize the selection placeholder                    |
| `fullWidth`         | Expand the selector to the container width              |
| `pluginName`        | Show tools from one plugin                             |
| `category`          | Filter by the registered category value, such as `site` |
| `searchPlaceholder` | Customize the search placeholder                       |
| `disabled`          | Disable selection and removal                          |

Custom administrative interfaces can read the catalog through the browser SDK:

```ts
import { listTools } from "@halo-dev/ai-foundation-sdk";

const tools = await listTools();
```

The catalog includes disabled and unconfigured tools. Each item contains its ID, owning plugin,
display metadata, tags, `enabled`, `configured`, and `status`, plus input/output schemas and an
optional FormKit `configurationSchema`. Configuration values are not part of the catalog.
This endpoint requires administrator access.

## Call selected tools from the backend

Add a Java SDK version containing the tool APIs and declare an AI Foundation plugin dependency.
For local development, follow the API JAR dependency instructions in the
[clock plugin example](../../examples/plugin-tool-clock/README.md).

Obtain `AiToolService` through `ExtensionGetter`. For each generation request, resolve the saved
tool IDs into `ToolDefinition` entries and pass them to the model request:

```java
return extensionGetter.getEnabledExtension(AiToolService.class)
    .switchIfEmpty(Mono.error(new IllegalStateException("AI Foundation is unavailable")))
    .flatMap(service -> service.resolveTools(selectedToolIds))
    .flatMap(tools -> extensionGetter.getEnabledExtension(AiModelService.class)
        .flatMap(service -> service.languageModel(modelName))
        .flatMap(model -> model.generateText(GenerateTextRequest.builder()
            .prompt("Search the site's public content about Halo")
            .tools(tools)
            .stopWhen(StopCondition.stepCountIs(5))
            .build())));
```

Store and resolve `ToolDescriptor.id`, which has the format `pluginId/localName`. The `name` field
is the model-facing name and is not a saved selection value. An empty array enables no shared tools;
invalid, duplicate, or unavailable IDs fail resolution.

Business endpoints should restrict allowed tools on the server. Tools requiring authorization must
check the caller's permissions in their executor. For approvals and step control, see the
[tool calling guide](./sdk-core/tools-and-tool-calling.md).

## Contribute tools to AI Foundation

Implement `AiToolProvider` and return a finite list of `ToolRegistration` entries. Each tool needs a
unique local name, an object input schema, and an executor. Names accept 1–63 letters, digits,
underscores, or hyphens. Complete `tools()` promptly and keep it limited to declarations; perform
operations in the executor.

```java
public final class ClockToolProvider implements AiToolProvider {
    @Override
    public Flux<ToolRegistration> tools() {
        return Flux.just(ToolRegistration.builder()
            .displayName("Current time")
            .displayDescription("Read the current UTC time")
            .category("Utilities")
            .tags(List.of("Time", "Read only"))
            .definition(ToolDefinition.builder()
                .name("current_time")
                .description("Get the current UTC time.")
                .inputSchema(JsonSchema.object().build())
                .executor(context -> Mono.just((Object) Map.of(
                    "utc", Instant.now().toString())))
                .build())
            .build());
    }
}
```

`displayName`, `displayDescription`, `category`, and `tags` are for administrative interfaces.
`ToolDefinition.description` helps the model understand the tool's purpose. Names must be unique
within one plugin.

### Provide a configuration form

Set `ToolRegistration.configuration` to a `ToolConfiguration`. AI Foundation adds a **Configure**
button to the tool card and renders the form in its shared modal. Use Halo's built-in FormKit inputs,
such as `text`, `select`, and `secret`, by providing a schema and configuration callbacks.

- `schema`: `List<Map<String, Object>>`, the tool's FormKit configuration schema.
- `values`: `Supplier<Mono<Map<String, Object>>>`, which reads the current configuration.
- `save`: `Function<Map<String, Object>, Mono<Void>>`, which validates and saves configuration.

The following tool requires a greeting setting. Place this code in the provider's `tools()` method.
`configService` is **a service implemented by the contributing plugin**. This example assumes it
provides `Mono<Map<String, Object>> values()` and `Mono<Void> save(Map<String, Object> values)`
to read and persist the plugin's configuration.

```java
var configuration = ToolConfiguration.builder()
    .schema(List.of(Map.of(
        "$formkit", "text", "name", "greeting",
        "label", "Greeting", "validation", "required")))
    .values(configService::values)
    .save(values -> {
        if (values.size() != 1
            || !(values.get("greeting") instanceof String greeting)
            || greeting.isBlank()) {
            return Mono.error(new IllegalArgumentException("A greeting is required"));
        }
        return configService.save(Map.of("greeting", greeting.trim()));
    })
    .build();

return configService.values()
    .defaultIfEmpty(Map.of())
    .map(current -> ToolRegistration.builder()
        .displayName("Greeting")
        .configured(current.get("greeting") instanceof String greeting
            && !greeting.isBlank())
        .configuration(configuration)
        .definition(ToolDefinition.builder()
            .name("greeting")
            .description("Read the configured greeting.")
            .inputSchema(JsonSchema.object().build())
            .executor(context -> configService.values()
                .map(saved -> (Object) Map.of("greeting", saved.get("greeting"))))
            .build())
        .build())
    .flux();
```

The plugin sets `configured` according to whether the tool is ready; it defaults to `true` for tools
that need no configuration. Unconfigured tools remain visible but cannot be selected. After saving,
the plugin returns `configured=true`; the tool becomes selectable when it is also enabled. Disabled
tools still require manual enabling. The example's `save` callback validates both allowed fields and
their values. FormKit's `validation` provides frontend feedback.

For service credentials, use Halo's `secret` input and persist only the Secret name. The `values`
callback returns that reference. Validate the Secret and its required fields in `save`, and resolve
the actual credential in the executor.

### Register the provider

Plugins with a required AI Foundation dependency can register their provider as a Spring Bean.
For optional integrations, declare an `ai-foundation?` plugin dependency and use conditional
configuration:

```java
@Configuration
@ConditionalOnClass(name = "run.halo.aifoundation.tool.AiToolProvider")
public class ToolProviderConfiguration {
    @Bean
    ClockToolProvider clockToolProvider() {
        return new ClockToolProvider();
    }
}
```

When using conditional configuration, do not annotate the provider with `@Component`. For complete
dependencies, source, and build instructions, see the
[clock plugin example](../../examples/plugin-tool-clock/README.md).
