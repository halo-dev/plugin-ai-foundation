package run.halo.aifoundation.tool.builtin;

import static run.halo.aifoundation.tool.ToolPermission.named;
import static run.halo.aifoundation.tool.ToolPermission.resource;
import static run.halo.aifoundation.tool.builtin.HaloToolRegistrations.read;
import static run.halo.aifoundation.tool.builtin.HaloToolSchemas.pagination;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.tool.AiToolProvider;
import run.halo.aifoundation.tool.ToolApprovalPolicy;
import run.halo.aifoundation.tool.ToolDefinition;
import run.halo.aifoundation.tool.ToolExecutor;
import run.halo.aifoundation.tool.ToolGroup;
import run.halo.aifoundation.tool.ToolPermission;
import run.halo.aifoundation.tool.ToolPreset;
import run.halo.aifoundation.tool.ToolRegistration;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Group;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.service.AttachmentService;
import run.halo.app.extension.Extension;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;

/**
 * Provides attachment metadata queries and remote-file imports backed by Halo's storage service.
 *
 * <p>Metadata responses omit storage credentials and signed URLs. Import uses a selected existing policy and optional
 * group; reading an existing attachment checks its resource permission before obtaining a temporary download address.
 *
 * <pre>{@code
 * attachments.getAttachment(Map.of("name", "attachment-name"));
 * attachments.importAttachment(Map.of("url", "https://example.com/guide.pdf",
 *     "policyName", "default-policy", "filename", "guide.pdf"));
 * }</pre>
 *
 * <p>As a local {@link AiToolProvider} component, this class is collected by the shared tool service without a separate
 * central registration list.
 */
@Component
@RequiredArgsConstructor
public class HaloAttachmentTools implements AiToolProvider {
    /** Attachment query and import presets shared by tools that operate on the same resources. */
    public static final ToolGroup ATTACHMENT_GROUP = ToolGroup.builder()
            .name("attachments")
            .displayName("附件")
            .description("查询附件、存储策略和分组，并导入远程文件")
            .presets(List.of(
                    ToolPreset.builder()
                            .name("read")
                            .displayName("附件查询")
                            .toolNames(List.of(
                                    "list_attachments",
                                    "get_attachment",
                                    "list_attachment_policies",
                                    "list_attachment_groups"))
                            .build(),
                    ToolPreset.builder()
                            .name("import")
                            .displayName("附件导入")
                            .toolNames(List.of(
                                    "list_attachments",
                                    "get_attachment",
                                    "list_attachment_policies",
                                    "list_attachment_groups",
                                    "import_attachment"))
                            .build()))
            .build();

    private final ReactiveExtensionClient client;
    private final AttachmentService attachments;
    private final PublicHttpClient http;
    private final HaloToolAccess access;
    private final DocumentReaderSettings settings;

    /**
     * Describes attachment detail, storage choices and import operations.
     *
     * @return registrations with resource permissions and approval for importing files
     */
    @Override
    public Flux<ToolRegistration> tools() {
        return Flux.just(
                read(
                                "list_attachments",
                                "附件查询",
                                "搜索已有附件，返回可引用的文件信息和公开链接。",
                                "media",
                                "List Halo attachments by metadata.name or displayName. Returns name, displayName, "
                                        + "mediaType, byte size and a public URL when available, not file contents. "
                                        + "Use get_attachment for one file's current metadata/URL, read_document with "
                                        + "attachmentName for PDF/DOCX text, or import_attachment to save a new remote file. "
                                        + "Does not return storage credentials, local paths or signed URLs.",
                                pagination(),
                                this::listAttachments,
                                null)
                        .group(ATTACHMENT_GROUP)
                        .requiredPermissions(List.of(resource("storage.halo.run", "attachments", "list")))
                        .build(),
                registration(
                        "get_attachment",
                        "附件详情",
                        "读取附件的公开地址和基本信息。",
                        "Inspect one existing Halo attachment's metadata and current public URL availability. "
                                + "Does not read file contents or download them to the model. Find its name with "
                                + "list_attachments; use read_document with attachmentName to extract PDF/DOCX text. "
                                + "urlReady=false means no public URL is currently available, not that the file is missing.",
                        object(
                                Map.of(
                                        "name",
                                        string(
                                                "Attachment metadata.name from list_attachments; not filename, displayName or URL",
                                                253)),
                                "name"),
                        context -> getAttachment(context.getInput()),
                        false,
                        named("storage.halo.run", "attachments", "get", "name")),
                registration(
                        "list_attachment_policies",
                        "附件存储策略",
                        "查询可用存储策略，不返回配置和凭据。",
                        "List existing Halo attachment storage policies for choosing import_attachment.policyName. "
                                + "query filters names and display names. Returns identifiers and display metadata, "
                                + "not files, storage credentials or policy configuration. Does not create a policy.",
                        listSchema(),
                        context -> listPolicies(context.getInput()),
                        false,
                        resource("storage.halo.run", "policies", "list")),
                registration(
                        "list_attachment_groups",
                        "附件分组",
                        "查询可用附件分组。",
                        "List existing Halo attachment groups for choosing import_attachment.groupName. "
                                + "query filters names and display names. Returns group identifiers and file counts, "
                                + "not the files themselves. Use list_attachments to find files; this tool does not create groups.",
                        listSchema(),
                        context -> listGroups(context.getInput()),
                        false,
                        resource("storage.halo.run", "groups", "list")),
                registration(
                        "import_attachment",
                        "导入附件",
                        "下载公网文件并保存为 Halo 附件，需审批。",
                        "Download a public HTTP(S) file and persist a NEW Halo attachment; requires approval. "
                                + "Use only when the user wants a saved file, not merely to read a web page or document. "
                                + "Choose policyName from list_attachment_policies and optional groupName from "
                                + "list_attachment_groups; use their name values, not display names. Returns saved "
                                + "attachment metadata and urlReady. If saved but urlReady=false, inspect get_attachment "
                                + "instead of importing again and creating a duplicate. Does not extract file text.",
                        object(
                                Map.of(
                                        "url",
                                        string(
                                                "Public HTTP(S) file URL to download and save; not an attachment ID or local path",
                                                2_048),
                                        "policyName",
                                        string(
                                                "Required existing storage policy name from list_attachment_policies",
                                                253),
                                        "groupName",
                                        string(
                                                "Existing group name from list_attachment_groups; omit to leave ungrouped",
                                                253),
                                        "filename",
                                        string(
                                                "Optional saved filename including extension, without directories; omitted uses the final URL path",
                                                200)),
                                "url",
                                "policyName"),
                        context -> importAttachment(context.getInput()),
                        true,
                        resource("console.api.storage.halo.run", "attachments/upload", "create"),
                        named("storage.halo.run", "policies", "get", "policyName")));
    }

    /**
     * Finds existing attachments without downloading files or exposing storage configuration.
     *
     * <p>For example, {@code {"query":"image","size":20}} matches persisted identifiers or display names. Use
     * {@code get_attachment} when a fresh public URL is needed for one result.
     *
     * @param input optional {@code query}, one-based {@code page} and bounded {@code size}
     * @return attachment names, display names, media types, sizes and safe persisted public URLs
     */
    public Mono<Object> listAttachments(Map<String, Object> input) {
        return new HaloResourceSupport(client)
                .list(
                        input,
                        Attachment.class,
                        List.of("metadata.name", "spec.displayName"),
                        attachment -> attachment.getSpec() != null,
                        HaloAttachmentTools::listSummary);
    }

    private static Map<String, Object> listSummary(Attachment attachment) {
        var spec = attachment.getSpec();
        var status = attachment.getStatus();
        return Map.of(
                "name",
                attachment.getMetadata().getName(),
                "displayName",
                text(spec.getDisplayName(), 300),
                "mediaType",
                text(spec.getMediaType(), 200),
                "size",
                spec.getSize() == null ? 0L : Math.max(0L, spec.getSize()),
                "url",
                HaloResourceSupport.publicUrl(status == null ? null : status.getPermalink()));
    }

    /**
     * Reads attachment metadata and its currently available public URL.
     *
     * @param input attachment {@code name} from the attachment list
     * @return metadata and {@code urlReady}; a missing public URL does not mean the file is absent
     */
    public Mono<Object> getAttachment(Map<String, Object> input) {
        return Mono.defer(() -> {
            fields(input, Set.of("name"));
            return attachment(name(input, "name"))
                    .flatMap(this::withPublicAddress)
                    .map(value -> (Object) value);
        });
    }

    /**
     * Lists existing storage policies without their private configuration.
     *
     * @param input optional {@code query}, {@code page} and {@code size}
     * @return policy identifiers, display names and template names
     */
    public Mono<Object> listPolicies(Map<String, Object> input) {
        return list(
                input,
                Policy.class,
                policy -> policy.getSpec() == null
                        ? null
                        : Map.of(
                                "name",
                                policy.getMetadata().getName(),
                                "displayName",
                                text(policy.getSpec().getDisplayName(), 300),
                                "templateName",
                                text(policy.getSpec().getTemplateName(), 253)));
    }

    /**
     * Lists existing attachment groups for selecting an import destination.
     *
     * @param input optional {@code query}, {@code page} and {@code size}
     * @return group identifiers, display names and attachment counts
     */
    public Mono<Object> listGroups(Map<String, Object> input) {
        return list(
                input,
                Group.class,
                group -> group.getSpec() == null
                        ? null
                        : Map.of(
                                "name",
                                group.getMetadata().getName(),
                                "displayName",
                                text(group.getSpec().getDisplayName(), 300),
                                "totalAttachments",
                                group.getStatus() == null || group.getStatus().getTotalAttachments() == null
                                        ? 0L
                                        : Math.max(0L, group.getStatus().getTotalAttachments())));
    }

    /**
     * Downloads a public file and stores it as a new attachment through Halo's attachment service.
     *
     * @param input required {@code url} and {@code policyName}, optional {@code groupName} and a filename without
     *     directories
     * @return persisted attachment metadata; when {@code urlReady=false}, inspect the existing attachment later instead
     *     of importing a duplicate
     */
    public Mono<Object> importAttachment(Map<String, Object> input) {
        return Mono.defer(() -> {
            fields(input, Set.of("url", "policyName", "groupName", "filename"));
            var uri = PublicHttpClient.parseUri(BuiltInToolInputs.requiredString(input, "url", 2_048));
            var policyName = name(input, "policyName");
            var groupName = input.containsKey("groupName") ? name(input, "groupName") : null;
            var filename = input.containsKey("filename")
                    ? filename(BuiltInToolInputs.requiredString(input, "filename", 200))
                    : null;
            var policy = client.fetch(Policy.class, policyName)
                    .filter(HaloAttachmentTools::active)
                    .filter(value -> value.getSpec() != null)
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Storage policy does not exist")));
            var group = groupName == null
                    ? Mono.empty()
                    : access.requireResource("storage.halo.run", "groups", "get", groupName)
                            .then(Mono.defer(() -> client.fetch(Group.class, groupName)))
                            .filter(HaloAttachmentTools::active)
                            .filter(value -> value.getSpec() != null)
                            .switchIfEmpty(Mono.error(new IllegalArgumentException("Attachment group does not exist")));
            return policy.then(group)
                    .then(Mono.defer(settings::importOptions))
                    .flatMap(options ->
                            http.getResource(uri, options.maxResponseBytes(), options.vpnCompatibilityEnabled()))
                    .flatMap(response -> {
                        var resolvedName = filename != null ? filename : suggestedFilename(response.uri());
                        return attachments
                                .upload(
                                        policyName,
                                        groupName,
                                        resolvedName,
                                        Flux.defer(() -> Flux.just(
                                                DefaultDataBufferFactory.sharedInstance.wrap(response.body()))),
                                        response.contentType())
                                .timeout(Duration.ofSeconds(60))
                                .onErrorMap(error -> new IllegalStateException(
                                        "Halo could not store the attachment; check the selected storage policy"));
                    })
                    .flatMap(this::withPublicAddress)
                    .map(value -> (Object) value);
        });
    }

    /**
     * Downloads an existing attachment after verifying the caller's read permission.
     *
     * <p>Halo's storage service supplies the address. Local upload URLs use the restricted local download path;
     * external locations retain the public-address checks.
     *
     * @param name attachment {@code metadata.name}
     * @param maxBytes maximum response size
     * @param vpn whether trusted HTTPS Fake-IP compatibility is enabled
     * @return bounded attachment bytes and media type
     */
    Mono<PublicHttpClient.Response> downloadAttachment(String name, int maxBytes, boolean vpn) {
        return Mono.defer(() -> access.requireResource("storage.halo.run", "attachments", "get", name)
                        .then(Mono.defer(() -> attachment(name))))
                .flatMap(attachment -> {
                    if (attachment.getSpec().getSize() != null
                            && attachment.getSpec().getSize() > maxBytes) {
                        return Mono.error(new IllegalArgumentException("Attachment exceeds the download size limit"));
                    }
                    return attachments
                            .getSharedURL(attachment, Duration.ofMinutes(2))
                            .onErrorMap(error -> new IllegalStateException("Attachment address is unavailable"))
                            .switchIfEmpty(Mono.defer(() -> attachments
                                    .getPermalink(attachment)
                                    .onErrorMap(
                                            error -> new IllegalStateException("Attachment address is unavailable"))))
                            .switchIfEmpty(Mono.defer(() -> Mono.justOrEmpty(attachment.getStatus())
                                    .mapNotNull(Attachment.AttachmentStatus::getPermalink)
                                    .map(PublicHttpClient::parseUri)))
                            .switchIfEmpty(
                                    Mono.error(new IllegalArgumentException("Attachment has no readable address")))
                            .flatMap(uri -> !uri.isAbsolute() || PublicHttpClient.localAttachmentHost(uri.getHost())
                                    ? http.getLocalAttachment(uri, maxBytes)
                                    : http.getResource(uri, maxBytes, vpn));
                });
    }

    private Mono<Map<String, Object>> withPublicAddress(Attachment attachment) {
        var result = projection(attachment);
        return Mono.defer(() -> attachments.getPermalink(attachment))
                .map(uri -> HaloResourceSupport.publicUrl(uri.toString()))
                .filter(url -> !url.isBlank())
                .onErrorResume(error -> Mono.empty())
                .defaultIfEmpty((String) result.get("url"))
                .map(url -> {
                    result.put("url", url);
                    result.put("urlReady", !url.isBlank());
                    if (url.isBlank()) {
                        result.put(
                                "message",
                                "Attachment is saved but has no public URL yet; "
                                        + "use get_attachment again or check the storage policy's public access");
                    }
                    return result;
                });
    }

    private Mono<Attachment> attachment(String name) {
        return client.fetch(Attachment.class, name)
                .filter(HaloAttachmentTools::active)
                .filter(value -> value.getSpec() != null)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Attachment does not exist")));
    }

    private <E extends Extension> Mono<Object> list(
            Map<String, Object> input, Class<E> type, Function<E, Map<String, Object>> projection) {
        return Mono.defer(() -> {
            fields(input, Set.of("query", "page", "size"));
            var query = input.containsKey("query") ? textQuery(input) : "";
            int page = integer(input, "page", 1, 10_000);
            int size = integer(input, "size", 20, 100);
            var options = ListOptions.builder()
                    .fieldQuery(Queries.isNull("metadata.deletionTimestamp"))
                    .build();
            return client.listAll(type, options, Sort.by("metadata.name").ascending())
                    .take(10_001)
                    .collectList()
                    .map(entries -> {
                        if (entries.size() > 10_000) {
                            throw new IllegalArgumentException("Too many storage entries; narrow the query");
                        }
                        var items = entries.stream()
                                .filter(HaloAttachmentTools::active)
                                .map(projection)
                                .filter(java.util.Objects::nonNull)
                                .filter(value -> query.isEmpty()
                                        || (value.get("name") + " " + value.get("displayName"))
                                                .toLowerCase(Locale.ROOT)
                                                .contains(query))
                                .toList();
                        int from = (int) Math.min((long) (page - 1) * size, items.size());
                        int to = Math.min(from + size, items.size());
                        return (Object) Map.of(
                                "page",
                                page,
                                "size",
                                size,
                                "total",
                                items.size(),
                                "hasNext",
                                to < items.size(),
                                "items",
                                items.subList(from, to));
                    });
        });
    }

    static Map<String, Object> projection(Attachment attachment) {
        var spec = attachment.getSpec();
        var result = new LinkedHashMap<String, Object>();
        result.put("name", attachment.getMetadata().getName());
        result.put("displayName", text(spec.getDisplayName(), 300));
        result.put("mediaType", text(spec.getMediaType(), 200));
        result.put("size", spec.getSize() == null ? 0L : Math.max(0L, spec.getSize()));
        result.put(
                "url",
                HaloResourceSupport.publicUrl(
                        attachment.getStatus() == null
                                ? null
                                : attachment.getStatus().getPermalink()));
        result.put("policyName", text(spec.getPolicyName(), 253));
        result.put("groupName", text(spec.getGroupName(), 253));
        return result;
    }

    private ToolRegistration registration(
            String name,
            String label,
            String summary,
            String description,
            Map<String, Object> schema,
            ToolExecutor executor,
            boolean write,
            ToolPermission... permissions) {
        return ToolRegistration.builder()
                .displayName(label)
                .displayDescription(summary)
                .category("media")
                .tags(List.of("附件", write ? "写入" : "查询"))
                .group(ATTACHMENT_GROUP)
                .configuration(write ? settings.importConfiguration() : null)
                .requiredPermissions(List.of(permissions))
                .definition(ToolDefinition.builder()
                        .name(name)
                        .description(
                                description
                                        + " Requires the caller's corresponding Halo permissions for this operation. Never expose storage credentials.")
                        .inputSchema(schema)
                        .approvalPolicy(write ? ToolApprovalPolicy.always() : ToolApprovalPolicy.never())
                        .executor(executor)
                        .build())
                .build();
    }

    private static boolean active(Extension value) {
        return value != null
                && value.getMetadata() != null
                && value.getMetadata().getDeletionTimestamp() == null;
    }

    private static String name(Map<String, Object> input, String field) {
        var value = BuiltInToolInputs.requiredString(input, field, 253);
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,252}")) {
            throw new IllegalArgumentException(field + " must be a Halo metadata.name");
        }
        return value;
    }

    private static String filename(String value) {
        if (value.equals(".")
                || value.equals("..")
                || value.indexOf('/') >= 0
                || value.indexOf('\\') >= 0
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("filename must not contain directories or control characters");
        }
        return value;
    }

    private static String suggestedFilename(URI uri) {
        if (uri == null || uri.getPath() == null || uri.getPath().endsWith("/")) {
            return "imported-file";
        }
        var path = uri.getPath();
        var name = path.substring(path.lastIndexOf('/') + 1);
        return filename(name.length() > 200 ? name.substring(name.length() - 200) : name);
    }

    private static String text(String value, int maximum) {
        return BuiltInToolInputs.truncate(value, maximum);
    }

    private static void fields(Map<String, Object> input, Set<String> fields) {
        if (input == null || !fields.containsAll(input.keySet())) {
            throw new IllegalArgumentException("Unsupported attachment input field");
        }
    }

    private static String textQuery(Map<String, Object> input) {
        if (!(input.get("query") instanceof String value) || value.length() > 200) {
            throw new IllegalArgumentException("query must be at most 200 characters");
        }
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static int integer(Map<String, Object> input, String name, int fallback, int maximum) {
        if (!input.containsKey(name)) return fallback;
        if (input.get(name) instanceof Number number) {
            try {
                int value = new BigDecimal(number.toString()).intValueExact();
                if (value >= 1 && value <= maximum) return value;
            } catch (ArithmeticException | NumberFormatException ignored) {
                // Reject non-integers consistently.
            }
        }
        throw new IllegalArgumentException(name + " must be an integer between 1 and " + maximum);
    }

    private static Map<String, Object> listSchema() {
        return object(Map.of(
                "query",
                string("Search names and display names", 200),
                "page",
                Map.of(
                        "type",
                        "integer",
                        "minimum",
                        1,
                        "maximum",
                        10_000,
                        "description",
                        "One-based page number; default 1"),
                "size",
                Map.of(
                        "type",
                        "integer",
                        "minimum",
                        1,
                        "maximum",
                        100,
                        "description",
                        "Entries per page; default 20, maximum 100")));
    }

    private static Map<String, Object> object(Map<String, Object> properties, String... required) {
        return Map.of(
                "type",
                "object",
                "properties",
                properties,
                "required",
                List.of(required),
                "additionalProperties",
                false);
    }

    private static Map<String, Object> string(String description, int maxLength) {
        return Map.of("type", "string", "description", description, "minLength", 1, "maxLength", maxLength);
    }
}
