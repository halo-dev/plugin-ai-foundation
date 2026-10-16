# Shared tools

[简体中文](../zh-CN/tool-catalog.md) | English

AI Foundation provides built-in tools and lets plugins contribute their own tools. Tools are grouped
by source plugin under **AI Foundation → Shared tools**, with related operations in expandable business groups.
Configure and enable or disable each tool from its card, or select shared tools in the model test
workbench to try them.

## Select tools in plugin settings

Use the registered FormKit input type `aiToolSelector` in your plugin's settings form:

```yaml
formSchema:
    - $formkit: aiToolSelector
      name: toolIds
      label: Shared tools
```

The saved value is an array of tool IDs (`string[]`), such as `['ai-foundation/search_site']`.
The selector is a compact dropdown grouped by source and business group. Search matches tools,
groups, and presets. Use the group checkbox to select or clear its tools, or expand it to adjust
individual tools. Presets such as **Read-only queries** and **Draft editing** add their currently
available tools and show unavailable member counts. Click a fully selected preset again to deselect
its tools while keeping other selections. Groups and presets save explicit tool IDs; new group members are
never added automatically to an existing selection.

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
| `category`          | Filter by a standard category ID, such as `content` |
| `searchPlaceholder` | Customize the search placeholder                       |
| `disabled`          | Disable selection and removal                          |

Custom administrative interfaces can read the catalog through the browser SDK:

```ts
import { listTools } from "@halo-dev/ai-foundation-sdk";

const tools = await listTools();
```

The catalog includes disabled and unconfigured tools. Each item contains its ID, owning plugin,
display metadata, tags, `enabled`, `configured`, and `status`, plus input/output schemas and an
optional FormKit `configurationSchema` and business `group`. Configuration values are not part of the catalog.
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

To obtain every available tool definition without supplying IDs:

```java
return extensionGetter.getEnabledExtension(AiToolService.class)
    .switchIfEmpty(Mono.error(new IllegalStateException("AI Foundation is unavailable")))
    .flatMap(AiToolService::resolveAllTools);
```

`resolveAllTools()` includes enabled, configured tools according to each tool's permission policy and retains authorization and approval requirements. Use `listTools()` for the complete catalog, including disabled and unconfigured tools.
Continue using `resolveTools(selectedToolIds)` when the business requires a specific capability set.

## Contribute tools to AI Foundation

Implement `AiToolProvider` and return a finite list of `ToolRegistration` entries. Each tool needs a
unique local name, an object input schema, and an executor. Names accept 1–63 letters, digits,
underscores, or hyphens. Complete `tools()` promptly and keep it limited to declarations; perform
operations in the executor.

A plugin can expose multiple `AiToolProvider` beans organized by business domain without a central
aggregating provider. They share the plugin's tool namespace, so local tool names must be unique.
Group presets may reference tools contributed by other providers in the same plugin.

```java
public final class ClockToolProvider implements AiToolProvider {
    @Override
    public Flux<ToolRegistration> tools() {
        return Flux.just(ToolRegistration.builder()
            .displayName("Current time")
            .displayDescription("Read the current UTC time")
            .category("utility")
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

### Declare tool permissions

Set `requiredPermissions` when registering a tool. AI Foundation checks the current caller's Halo permissions:

```java
ToolRegistration.builder()
    .requiredPermissions(List.of(
        ToolPermission.named("content.halo.run", "posts", "get", "name")))
    .permissionDeniedBehavior(ToolPermissionDeniedBehavior.FILTER_ON_RESOLVE)
    // Other tool metadata and definition...
    .build();
```

This requires permission to read the post identified by the input's `name` argument. For collection
operations use `ToolPermission.resource("content.halo.run", "posts", "list")`. Halo core resources
use an empty API group. Batch names support paths such as `items[].name`; every target must be
permitted. Every entry in the list is required. Use `ToolPermission.allOf(...)` and `anyOf(...)`
for combinations. An empty declaration makes the tool public.

Choose `permissionDeniedBehavior`:

- `FILTER_ON_RESOLVE` (default): omit unauthorized tools at resolution and check again before execution.
- `DENY_ON_EXECUTE`: keep the tool at resolution, then reject an unauthorized call before running its executor.

A tool-level authorization callback is not needed. Executors must still enforce business constraints,
such as post ownership and valid resource relationships. Permission to edit one's own posts does not
allow editing other users' posts. Approval grants no additional permissions.

### Require approval before writes

Set `.requiresApproval(true)` on the `ToolDefinition` to require user confirmation for every call,
or use `.approvalPolicy(ToolApprovalPolicy.always())`. Use `.approvalPredicate(context -> ...)`
when the decision depends on the validated arguments.

```java
ToolDefinition.builder()
    .name("update_draft")
    .description("Update an existing article draft after user confirmation.")
    .inputSchema(JsonSchema.object()
        .property("name", JsonSchema.string())
        .property("content", JsonSchema.string())
        .required("name", "content")
        .build())
    .requiresApproval(true)
    .executor(context -> updateDraft(context.getInput()))
    .build();
```

When the model calls such a tool, AI Foundation returns an approval request without performing the
write. The consuming application shows the operation and arguments to the user, submits their
approval response, and continues generation. A pending or denied call is not executed. See
[Tool interaction: approvals](./sdk-ui/chatbot-tool-usage.md#approval) for frontend integration.

All built-in draft creation and edits, publication and withdrawal, category/tag maintenance,
attachment imports, comment replies/moderation, and menu maintenance require user approval.
Execution still checks the current caller's Halo permissions after approval.

### Categories, groups, and presets

Categories are shared across plugins. Use one of these stable IDs:

| ID | Purpose |
| -- | ------- |
| `content` | Content |
| `web` | Web |
| `media` | Media |
| `site` | Site |
| `utility` | Utilities |
| `other` | Other |

Express finer details through business groups and tags.

Set the optional `ToolRegistration.group` to a shared definition for related tools:

```java
var articleGroup = ToolGroup.builder()
    .name("posts")
    .displayName("Posts")
    .description("Find posts and maintain drafts")
    .presets(List.of(ToolPreset.builder()
        .name("read")
        .displayName("Post search")
        .toolNames(List.of("list_posts", "get_post"))
        .build()))
    .build();
```

Add `.group(articleGroup)` to each member registration. Group and preset names are stable local
identifiers. A preset's `toolNames` must reference registered tools from the same source and group.
Catalog group IDs include the source prefix, for example `my-plugin/posts`.
Tools without a group appear as standalone tools.

Groups have no executor or independent settings. Configuration and enablement remain per tool.
Presets are selection shortcuts, not additional execution permissions or model-facing tools.

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

## Use built-in tools

| Tool            | Saved ID                      | Purpose                                                       |
| --------------- | ----------------------------- | ------------------------------------------------------------- |
| Site search     | `ai-foundation/search_site`   | Search public, published, non-recycled site content           |
| Read a web page | `ai-foundation/read_web_page` | Read public pages and text resources such as JSON and XML    |
| Web search      | `ai-foundation/web_search`    | Search the internet through Brave Search                      |

Web search requires a Brave Search API key. Click **Configure** on its card, create or select the
key, and save. Keys are stored in Halo Secrets; tool settings store only the Secret reference.
When using a trusted VPN/TUN with Fake-IP DNS, enable **VPN compatibility mode** in the web search
tool's own configuration. It is disabled by default.

Tools can be **Unconfigured**, **Enabled**, or **Disabled**. Unconfigured and disabled tools remain
visible but cannot be selected. The card switch controls whether a tool can be used. Saving
configuration does not automatically enable a disabled tool.

Web page reading supports public HTTP(S) URLs on ports 80 and 443 as a general text reader for
consumer plugins:

- HTML and XHTML: extract titles and body text, remove scripts and other non-content elements,
  and do not execute JavaScript.
- JSON (including `+json` and NDJSON), XML, RSS, Atom, Markdown, CSV, YAML, TOML, and other common
  text: preserve the original text structure and line breaks.
- Handle compressed responses and up to 5 safe redirects. Recognize declared encodings, common
  Unicode BOMs, and HTML/XML encoding declarations. Responses with no content type or a generic
  download type are checked for readable text.

Results retain `url`, `title`, `content`, and `truncated`, and add `finalUrl` and `contentType`.
Configure both limits under **Shared tools → Read a web page → Configure**:

| Setting | Default | Allowed range |
| ------- | ------- | ------------- |
| Download size limit | 10 MiB after decompression | 1–100 MiB |
| Content length limit | 100000 characters | 1–2000000 characters |

When `truncated` is `true`, content exceeded the configured length limit and structured content
such as JSON or XML may be incomplete.

The tool does not provide login sessions, run JavaScript to load dynamic content, or parse binary
files such as PDF, Office documents, images, audio, video, and archives.

When using a VPN's Fake-IP mode, enable **VPN compatibility mode** under
**Shared tools → Read a web page → Configure**. It is disabled by default. Halo's actual runtime
environment, including containers, must also use the VPN/TUN network.

Compatibility mode supports only HTTPS URLs with domain names on port 443, using the common Fake-IP
ranges `198.18.0.0/15` and `fdfe:dcba:9876::/64`. Other private addresses and URLs containing an IP
address directly remain unsupported.

Alternatively, add the target domain to Mihomo/Clash's `fake-ip-filter` in `blacklist` mode to return
real IP addresses without enabling compatibility mode. See the
[Mihomo DNS configuration](https://wiki.metacubex.one/config/dns/#fake-ip-filter).

## Use Halo core tools

These tools follow the current user's Halo permissions and support media,
content editing, publishing, and site maintenance. List tools accept
`page`, `size`, and `query`, with 20 items per page by default and a maximum of 100.

| Tools | Saved IDs (all prefixed with `ai-foundation/`) | Purpose |
| ----- | --------------------------------------------- | ------- |
| Post list and detail | `list_posts`, `get_post` | Filter posts and read published or draft content |
| Page list and detail | `list_pages`, `get_page` | Find single pages and read published or draft content |
| Categories and tags | `list_categories`, `list_tags` | Find taxonomy entries and stable names |
| Attachments | `list_attachments` | Find existing media, file metadata, and available public URLs |
| Site information | `get_site_info` | Read the title, subtitle, URL, icons, and language |
| Post drafts | `create_post_draft`, `update_post_draft` | Save content, categories, tags, cover, and excerpt |
| Page drafts | `create_page_draft`, `update_page_draft` | Save single-page content, cover, and excerpt |
| Taxonomy maintenance | `create_category`, `update_category`, `create_tag`, `update_tag` | Create and edit categories and tags, including category hierarchy |
| Post publication | `compare_post_versions`, `publish_post`, `unpublish_post` | Review draft changes, publish, or unpublish |
| Page publication | `compare_page_versions`, `publish_page`, `unpublish_page` | Review and publish single pages, or unpublish |
| Attachment management | `get_attachment`, `list_attachment_policies`, `list_attachment_groups`, `import_attachment` | Inspect media, choose storage, and import public files |
| Comments | `list_comments`, `get_comment`, `reply_comment`, `moderate_comment` | Read comments and replies, reply, or change approval |
| Navigation queries | `list_menus`, `get_menu` | Read menus, their hierarchy, and item versions |
| Navigation maintenance | `create_menu`, `create_menu_item`, `update_menu_item`, `reorder_menu_items` | Create menus, maintain links, and reorder siblings |
| Documents | `read_document` | Extract PDF and DOCX text from a public URL or Halo attachment |

Content lists default to published public items. Set `includeUnpublished: true` to include drafts
and non-public content; recycled items are always excluded. Category searches match stable names
or slugs; tag and attachment searches also match display names. Assign categories and tags using
their returned `name` values rather than display names.

Detail tools default to `version: release`. Use `version: head` for drafts. Results include `raw`,
rendered `content`, `rawType`, `metadataVersion`, and truncation flags. Configure post and page detail
length limits independently on their cards; both default to 100000 characters.

Draft writes require approval and the current user's editing permission. Create a draft with `title`, `slug`, and an HTML body in `raw` or `content`;
`rawType` defaults to `HTML`. Markdown requires both `raw` and rendered HTML `content`, and an editor
supporting that format. Before editing, read the latest draft and pass its `metadataVersion` as
`expectedVersion`. After a conflict, read again before deciding what to change. Omitted fields are
preserved, allowing metadata-only edits. When updating the body, omitting `rawType` preserves the
current draft format; Markdown still requires both original text and rendered HTML.
Updates preserve the published version and save only a draft. Separate publication tools publish it.

Category and tag queries also return `metadataVersion`; pass it as `expectedVersion` when editing.
Use an unoccupied `slug` when creating an entry. Categories accept an optional `parent`.

Before publishing, call `compare_post_versions` or `compare_page_versions` to review the changes.
Pass the returned `metadataVersion`, `headSnapshot`, and `headContentFingerprint` as the publication
arguments `expectedVersion`, `headSnapshot`, and `expectedContentFingerprint`. Compare and approve
again if the content changes. If `reconciliationPending` is true, check the publication state again later.

To import an attachment, supply a public `url` and an existing `policyName`, with optional `groupName`
and `filename`. Storage queries return selection information without credentials. The import card
has its own download limit, defaulting to 20 MiB (1–100 MiB), and a VPN compatibility switch.

Comment details include paginated replies. Replies use the current user's identity. Replying to an
unapproved parent or quoted reply also requires permission to approve it. New replies are approved
only when the caller has the corresponding moderation permission. Moderation uses the current
comment's `metadataVersion`.

Navigation items accept `displayName` and `href`, or a `targetRef` to a post, page, category, or tag.
Updates require the item's `expectedVersion`. Reordering requires every current sibling and its
version. Read the menu again after a conflict before choosing a new order.

### Read PDF and DOCX documents

Supply exactly one of `url` or `attachmentName` to `read_document`. The result contains extracted
text and truncation information. Lightweight DOCX extraction covers common paragraphs, tables,
headers, footers, and footnotes. Complex layout, automatic numbering, and revision rendering may
not be reproduced; external document links are not followed. Configure download size (default 20 MiB), text length (200000
characters), PDF pages (200), and VPN compatibility on its card. Maximums are 100 MiB, 2000000
characters, and 2000 pages. Text and page limits mark the result as truncated. Encrypted or damaged
documents return errors. OCR is not included; PDFs without a text layer report that no text could
be extracted. Continue using **Read a web page** for HTML, JSON, and other text resources.

Site management tools follow the current user's Halo permissions; a super administrator role is not
required. Read, edit, publish, comment moderation, and attachment import each require their corresponding
permissions. Contributors and authors can operate only on their own posts. A grant for a named resource
does not grant collection access. Built-in management tools default to filtering unauthorized tools
at resolution; execution checks the target resource again. Guests cannot perform management writes. Public site search, site information, and public
document URLs remain available; reading a document by attachment name requires attachment read permission.

Enabling, selecting, or approving tools grants no additional permissions. Plugin-provided tools must also
check the caller's permission for the target resource and operation. Do not inject an administrator identity
or use a fixed administrator token on behalf of visitors.
