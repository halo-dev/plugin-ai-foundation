package run.halo.aifoundation.service.tool;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.aifoundation.AiToolService;
import run.halo.aifoundation.service.language.LanguageModelRuntimeSupport;
import run.halo.aifoundation.service.language.mapping.LanguageModelMessageMapper;
import run.halo.aifoundation.service.language.mapping.LanguageModelResponseMapper;
import run.halo.aifoundation.service.language.structured.LanguageModelStructuredOutputHandler;
import run.halo.aifoundation.tool.AiToolProvider;
import run.halo.aifoundation.tool.ToolApprovalPolicy;
import run.halo.aifoundation.tool.ToolConfiguration;
import run.halo.aifoundation.tool.ToolDefinition;
import run.halo.aifoundation.tool.ToolDescriptor;
import run.halo.aifoundation.tool.ToolExecutionContext;
import run.halo.aifoundation.tool.ToolGroup;
import run.halo.aifoundation.tool.ToolGroupDescriptor;
import run.halo.aifoundation.tool.ToolPermission;
import run.halo.aifoundation.tool.ToolPermissionDeniedBehavior;
import run.halo.aifoundation.tool.ToolPresetDescriptor;
import run.halo.aifoundation.tool.ToolRegistration;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

/**
 * Discovers shared tool providers and resolves catalog selections to request-scoped definitions.
 *
 * <p>Registrations are validated and copied before entering the catalog. Conflicting identities or inconsistent groups
 * are excluded, administrator choices determine readiness, and declared permissions determine which definitions a
 * caller receives. Catalog responses contain metadata only; executors and callbacks remain on the server.
 *
 * <p>For example, resolving {@code article-tools/get_post} returns a model-facing definition whose executor checks the
 * current registration, readiness, and caller permissions before executing. This keeps a previously selected definition
 * from retaining access after a provider is disabled.
 */
@Slf4j
@Component
public class AiToolServiceImpl implements AiToolService {

    private static final Duration DEFAULT_PROVIDER_TIMEOUT = Duration.ofSeconds(5);
    private static final Set<String> JSON_TYPES =
            Set.of("object", "array", "string", "number", "integer", "boolean", "null");
    private static final Set<String> CATEGORIES = Set.of("content", "web", "media", "site", "utility", "other");

    private final ExtensionGetter extensionGetter;
    private final PluginManager pluginManager;
    private final List<AiToolProvider> builtInProviders;
    private final Duration providerTimeout;
    private final LanguageModelStructuredOutputHandler valueValidator;
    private final ToolSettingsStore settingsStore;
    private final ToolPermissionChecker permissionChecker;

    /**
     * Creates the registry with Halo extension discovery and this plugin's tool-provider beans.
     *
     * <p>Business components implement {@link AiToolProvider} directly. Spring collects them without a separate
     * registration list; enabled providers from other plugins are discovered through Halo.
     *
     * @param extensionGetter enabled cross-plugin extension lookup
     * @param pluginWrapper owning plugin and plugin manager
     * @param builtInProviders tool-provider beans from AI Foundation's application context
     * @param settingsStore persisted administrator choices
     * @param permissionChecker current caller's resource permission evaluator
     */
    @Autowired
    public AiToolServiceImpl(
            ExtensionGetter extensionGetter,
            PluginWrapper pluginWrapper,
            List<AiToolProvider> builtInProviders,
            ToolSettingsStore settingsStore,
            ToolPermissionChecker permissionChecker) {
        this(
                extensionGetter,
                pluginWrapper.getPluginManager(),
                builtInProviders,
                DEFAULT_PROVIDER_TIMEOUT,
                settingsStore,
                permissionChecker);
    }

    AiToolServiceImpl(
            ExtensionGetter extensionGetter,
            PluginManager pluginManager,
            List<AiToolProvider> builtInProviders,
            Duration providerTimeout) {
        this(extensionGetter, pluginManager, builtInProviders, providerTimeout, null);
    }

    AiToolServiceImpl(
            ExtensionGetter extensionGetter,
            PluginManager pluginManager,
            List<AiToolProvider> builtInProviders,
            Duration providerTimeout,
            ToolSettingsStore settingsStore) {
        this(extensionGetter, pluginManager, builtInProviders, providerTimeout, settingsStore, null);
    }

    AiToolServiceImpl(
            ExtensionGetter extensionGetter,
            PluginManager pluginManager,
            List<AiToolProvider> builtInProviders,
            Duration providerTimeout,
            ToolSettingsStore settingsStore,
            ToolPermissionChecker permissionChecker) {
        this.extensionGetter = extensionGetter;
        this.pluginManager = pluginManager;
        this.builtInProviders = List.copyOf(builtInProviders);
        this.providerTimeout = providerTimeout;
        this.settingsStore = settingsStore;
        this.permissionChecker = permissionChecker;
        this.valueValidator = new LanguageModelStructuredOutputHandler(
                new LanguageModelResponseMapper("tool-registry", new LanguageModelMessageMapper("tool-registry")),
                new LanguageModelRuntimeSupport()::writeJson);
    }

    @Override
    public Flux<ToolDescriptor> listTools() {
        return registeredTools().flatMapMany(Flux::fromIterable).map(RegisteredTool::descriptor);
    }

    @Override
    public Mono<List<ToolDefinition>> resolveTools(List<String> toolIds) {
        return Mono.defer(() -> {
            if (toolIds == null || toolIds.isEmpty()) {
                return Mono.just(List.of());
            }
            var selection = new ArrayList<String>();
            var uniqueIds = new HashSet<String>();
            for (var id : toolIds) {
                if (id == null || id.isBlank()) {
                    return Mono.error(new IllegalArgumentException("Tool IDs must not be blank"));
                }
                if (!uniqueIds.add(id)) {
                    return Mono.error(new IllegalArgumentException("Duplicate tool ID: " + id));
                }
                selection.add(id);
            }
            return registeredTools()
                    .map(tools -> {
                        var byId = new LinkedHashMap<String, RegisteredTool>();
                        tools.forEach(tool -> byId.put(tool.descriptor().id(), tool));
                        return selection.stream()
                                .map(id -> {
                                    var tool = byId.get(id);
                                    if (tool == null) {
                                        throw new IllegalArgumentException("Tool is unavailable: " + id);
                                    }
                                    if (!tool.descriptor().enabled()
                                            || !tool.descriptor().configured()) {
                                        throw new IllegalArgumentException("Tool is not ready: " + id + " ("
                                                + tool.descriptor().status() + ")");
                                    }
                                    return tool;
                                })
                                .toList();
                    })
                    .flatMap(this::accessibleDefinitions);
        });
    }

    @Override
    public Mono<List<ToolDefinition>> resolveAllTools() {
        return registeredTools()
                .flatMap(tools -> accessibleDefinitions(tools.stream()
                        .filter(tool ->
                                tool.descriptor().enabled() && tool.descriptor().configured())
                        .toList()));
    }

    private Mono<List<ToolDefinition>> accessibleDefinitions(List<RegisteredTool> tools) {
        return Flux.fromIterable(tools)
                .concatMap(tool -> canResolve(tool).filter(Boolean.TRUE::equals).map(allowed -> executable(tool)))
                .collectList();
    }

    private Mono<Boolean> canResolve(RegisteredTool tool) {
        if (tool.requiredPermissions().isEmpty()
                || tool.permissionDeniedBehavior() == ToolPermissionDeniedBehavior.DENY_ON_EXECUTE) {
            return Mono.just(true);
        }
        if (permissionChecker == null) {
            return Mono.just(false);
        }
        return Mono.defer(() -> permissionChecker.canResolve(tool.requiredPermissions()))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(providerTimeout)
                .defaultIfEmpty(false)
                .onErrorReturn(false);
    }

    private Mono<Boolean> canExecute(RegisteredTool tool, Map<String, Object> input) {
        if (tool.requiredPermissions().isEmpty()) {
            return Mono.just(true);
        }
        if (permissionChecker == null) {
            return Mono.just(false);
        }
        return Mono.defer(() -> permissionChecker.canExecute(tool.requiredPermissions(), input))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(providerTimeout)
                .defaultIfEmpty(false)
                .onErrorReturn(false);
    }

    /**
     * Persists an administrator's enablement choice for an available catalog entry.
     *
     * @param id stable catalog ID
     * @param enabled whether the tool may be selected and executed
     * @return refreshed metadata after persistence
     */
    public Mono<ToolDescriptor> updateEnabled(String id, boolean enabled) {
        return findTool(id).flatMap(ignored -> {
            if (settingsStore == null) {
                return Mono.error(new IllegalStateException("Tool settings store is unavailable"));
            }
            return settingsStore
                    .setEnabled(id, enabled)
                    .then(Mono.defer(() -> findTool(id)))
                    .map(RegisteredTool::descriptor);
        });
    }

    /**
     * Reads editable settings through the registered provider's configuration callback.
     *
     * @param id stable catalog ID of a configurable tool
     * @return current values without resolved credentials
     */
    public Mono<Map<String, Object>> getConfiguration(String id) {
        return findTool(id).flatMap(tool -> {
            var configuration = requireConfiguration(tool);
            return Mono.defer(configuration.getValues())
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(providerTimeout)
                    .defaultIfEmpty(Map.of());
        });
    }

    /**
     * Validates and saves settings through the provider, then rereads its current values.
     *
     * @param id stable catalog ID of a configurable tool
     * @param values settings submitted by the administrator
     * @return refreshed editable settings after a successful save
     */
    public Mono<Map<String, Object>> updateConfiguration(String id, Map<String, Object> values) {
        if (values == null) {
            return Mono.error(new IllegalArgumentException("Tool configuration values are required"));
        }
        return findTool(id).flatMap(tool -> {
            var configuration = requireConfiguration(tool);
            return Mono.defer(() -> configuration.getSave().apply(values))
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(providerTimeout)
                    .then(Mono.defer(() -> getConfiguration(id)));
        });
    }

    private Mono<RegisteredTool> findTool(String id) {
        return registeredTools()
                .map(tools -> tools.stream()
                        .filter(tool -> tool.descriptor().id().equals(id))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Tool is unavailable: " + id)));
    }

    private static ToolConfiguration requireConfiguration(RegisteredTool tool) {
        if (tool.configuration() == null) {
            throw new IllegalArgumentException(
                    "Tool has no configuration: " + tool.descriptor().id());
        }
        return tool.configuration();
    }

    private Mono<List<RegisteredTool>> registeredTools() {
        var externalProviders = Flux.defer(() -> {
            var providers = extensionGetter.getEnabledExtensions(AiToolProvider.class);
            if (providers == null) {
                return Flux.error(new IllegalStateException("Tool providers are unavailable"));
            }
            // Halo also scans this plugin's beans. Local built-ins are already included directly.
            return providers.filter(provider -> builtInProviders.stream().noneMatch(builtin -> builtin == provider));
        });
        var tools = Flux.merge(
                        Flux.fromIterable(builtInProviders)
                                .flatMap(provider -> providerTools(provider, "ai-foundation")),
                        externalProviders.flatMap(provider -> providerTools(provider, null)))
                .collectList()
                .map(this::withoutConflicts)
                .map(this::withoutGroupConflicts);
        var states = settingsStore == null
                ? Mono.<Map<String, Boolean>>just(Map.of())
                : Mono.defer(settingsStore::enabledStates);
        return Mono.zip(tools, states)
                .map(result -> result.getT1().stream()
                        .map(tool -> {
                            var descriptor = tool.descriptor();
                            var enabled = result.getT2().getOrDefault(descriptor.id(), true);
                            var managedDescriptor = new ToolDescriptor(
                                    descriptor.id(),
                                    descriptor.pluginName(),
                                    descriptor.localName(),
                                    descriptor.name(),
                                    descriptor.displayName(),
                                    descriptor.description(),
                                    descriptor.displayDescription(),
                                    descriptor.category(),
                                    descriptor.inputSchema(),
                                    descriptor.outputSchema(),
                                    descriptor.tags(),
                                    descriptor.configurationSchema(),
                                    enabled,
                                    descriptor.configured(),
                                    null,
                                    descriptor.group());
                            return new RegisteredTool(
                                    managedDescriptor,
                                    tool.definition(),
                                    tool.configuration(),
                                    tool.requiredPermissions(),
                                    tool.permissionDeniedBehavior());
                        })
                        .toList());
    }

    private Flux<RegisteredTool> providerTools(AiToolProvider provider, String builtInOwner) {
        return Flux.defer(() -> {
                    var tools = provider.tools();
                    if (tools == null) {
                        return Flux.error(new IllegalStateException("Tool provider returned no stream"));
                    }
                    return tools;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .collectList()
                .map(tools -> {
                    var owner = builtInOwner != null ? builtInOwner : providerOwner(provider);
                    return tools.stream().map(tool -> register(owner, tool)).toList();
                })
                .timeout(providerTimeout)
                .flatMapMany(Flux::fromIterable)
                .onErrorResume(error -> {
                    log.warn(
                            "Ignoring unavailable AI tool provider {}",
                            provider.getClass().getName(),
                            error);
                    return Flux.empty();
                });
    }

    private String providerOwner(AiToolProvider provider) {
        var owner = pluginManager.whichPlugin(AopUtils.getTargetClass(provider));
        if (owner == null) {
            throw new IllegalStateException("Cannot determine the AI tool provider plugin");
        }
        var pluginId = owner.getPluginId();
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalStateException("Cannot determine the AI tool provider plugin");
        }
        if (pluginId.contains("/")) {
            throw new IllegalStateException("Cannot determine the AI tool provider plugin");
        }
        return pluginId;
    }

    private RegisteredTool register(String owner, ToolRegistration registration) {
        if (registration == null || registration.getDefinition() == null) {
            throw new IllegalArgumentException("Tool provider returned no definition");
        }
        var definition = registration.getDefinition();
        var localName = definition.getName();
        if (localName == null || !localName.matches("[A-Za-z0-9_-]{1,63}")) {
            throw new IllegalArgumentException(
                    "Tool local name must contain 1-63 letters, numbers," + " underscores, or hyphens");
        }
        if (definition.getExecutor() == null) {
            throw new IllegalArgumentException("Discoverable tools must have a server-side executor");
        }
        var inputSchema = definition.getInputSchema();
        if (inputSchema == null || !"object".equals(inputSchema.get("type"))) {
            throw new IllegalArgumentException("Tool inputSchema must declare an object type");
        }
        var configuration = registration.getConfiguration();
        validateConfiguration(configuration);
        var id = owner + "/" + localName;
        var category = category(registration.getCategory());
        var descriptor = new ToolDescriptor(
                id,
                owner,
                localName,
                modelName(id, localName),
                hasText(registration.getDisplayName()) ? registration.getDisplayName() : localName,
                definition.getDescription(),
                hasText(registration.getDisplayDescription())
                        ? registration.getDisplayDescription()
                        : definition.getDescription(),
                category,
                inputSchema,
                definition.getOutputSchema(),
                registration.getTags(),
                configuration != null ? configuration.getSchema() : List.of(),
                true,
                registration.isConfigured(),
                null,
                group(owner, registration.getGroup()));
        validateSchema(descriptor.inputSchema());
        if (descriptor.outputSchema() != null) {
            validateSchema(descriptor.outputSchema());
        }
        var snapshot = copyDefinition(definition, descriptor);
        return new RegisteredTool(
                descriptor,
                snapshot,
                configuration,
                registration.getRequiredPermissions(),
                registration.getPermissionDeniedBehavior());
    }

    private static void validateConfiguration(ToolConfiguration configuration) {
        if (configuration == null) {
            return;
        }
        if (configuration.getSchema() == null || configuration.getSchema().isEmpty()) {
            throw new IllegalArgumentException("Tool configuration must provide a schema and callbacks");
        }
        if (configuration.getValues() == null || configuration.getSave() == null) {
            throw new IllegalArgumentException("Tool configuration must provide a schema and callbacks");
        }
    }

    private static String category(String value) {
        return value != null && CATEGORIES.contains(value) ? value : "other";
    }

    private static ToolGroupDescriptor group(String owner, ToolGroup group) {
        if (group == null) {
            return null;
        }
        validateLocalName(group.getName(), "Tool group");
        var id = owner + "/" + group.getName();
        var presetNames = new HashSet<String>();
        var presets = new ArrayList<ToolPresetDescriptor>();
        for (var preset : group.getPresets()) {
            validateLocalName(preset.getName(), "Tool preset");
            if (!presetNames.add(preset.getName())) {
                throw new IllegalArgumentException("Tool preset names must be unique within a group");
            }
            if (preset.getToolNames().isEmpty()) {
                throw new IllegalArgumentException("Tool presets must reference at least one tool");
            }
            var localNames = new HashSet<String>();
            var toolIds = new ArrayList<String>();
            for (var localName : preset.getToolNames()) {
                validateLocalName(localName, "Preset tool");
                if (!localNames.add(localName)) {
                    throw new IllegalArgumentException("Tool presets must reference unique tools");
                }
                toolIds.add(owner + "/" + localName);
            }
            presets.add(new ToolPresetDescriptor(
                    id + "/" + preset.getName(),
                    hasText(preset.getDisplayName()) ? preset.getDisplayName() : preset.getName(),
                    preset.getDescription(),
                    toolIds));
        }
        return new ToolGroupDescriptor(
                id,
                hasText(group.getDisplayName()) ? group.getDisplayName() : group.getName(),
                group.getDescription(),
                presets);
    }

    private static void validateLocalName(String name, String label) {
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,63}")) {
            throw new IllegalArgumentException(
                    label + " name must contain 1-63 letters, numbers," + " underscores, or hyphens");
        }
    }

    private List<RegisteredTool> withoutGroupConflicts(List<RegisteredTool> tools) {
        var groups = new LinkedHashMap<String, ToolGroupDescriptor>();
        var byId = new LinkedHashMap<String, RegisteredTool>();
        var invalidGroups = new HashSet<String>();
        for (var tool : tools) {
            byId.put(tool.descriptor().id(), tool);
            var group = tool.descriptor().group();
            if (group == null) {
                continue;
            }
            var existing = groups.putIfAbsent(group.id(), group);
            if (existing != null && !existing.equals(group)) {
                invalidGroups.add(group.id());
                log.warn("Ignoring AI tool group with conflicting metadata: {}", group.id());
            }
        }
        for (var group : groups.values()) {
            if (!validGroupReferences(group, byId)) {
                invalidGroups.add(group.id());
            }
        }
        return tools.stream()
                .filter(tool -> tool.descriptor().group() == null
                        || !invalidGroups.contains(tool.descriptor().group().id()))
                .toList();
    }

    private static boolean validGroupReferences(ToolGroupDescriptor group, Map<String, RegisteredTool> toolsById) {
        var valid = true;
        for (var preset : group.presets()) {
            for (var id : preset.toolIds()) {
                if (belongsToGroup(toolsById.get(id), group.id())) {
                    continue;
                }
                valid = false;
                log.warn(
                        "Ignoring AI tool group {}: preset {} references a missing or " + "different-group tool {}",
                        group.id(),
                        preset.id(),
                        id);
            }
        }
        return valid;
    }

    private static boolean belongsToGroup(RegisteredTool tool, String groupId) {
        if (tool == null || tool.descriptor().group() == null) {
            return false;
        }
        return groupId.equals(tool.descriptor().group().id());
    }

    private ToolDefinition copyDefinition(ToolDefinition source, ToolDescriptor descriptor) {
        var examples = source.getInputExamples() == null
                ? null
                : source.getInputExamples().stream()
                        .map(example -> new ToolDescriptor(
                                        descriptor.id(),
                                        descriptor.pluginName(),
                                        descriptor.localName(),
                                        descriptor.name(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        example,
                                        null,
                                        List.of(),
                                        List.of(),
                                        true,
                                        true,
                                        null,
                                        null)
                                .inputSchema())
                        .toList();
        return ToolDefinition.builder()
                .name(descriptor.name())
                .description(descriptor.description())
                .inputSchema(descriptor.inputSchema())
                .outputSchema(descriptor.outputSchema())
                .inputExamples(examples)
                .strict(source.getStrict())
                .approvalPolicy(copyApprovalPolicy(source.getApprovalPolicy()))
                .onInputStart(source.getOnInputStart())
                .onInputDelta(source.getOnInputDelta())
                .onInputAvailable(source.getOnInputAvailable())
                .executor(source.getExecutor())
                .build();
    }

    private ToolDefinition executable(RegisteredTool tool) {
        var descriptor = tool.descriptor();
        var definition = copyDefinition(tool.definition(), descriptor);
        var approvalMode = approvalMode(definition.getApprovalPolicy());
        definition.setExecutor(context -> executeCurrent(descriptor, approvalMode, context));
        return definition;
    }

    private Mono<Object> executeCurrent(
            ToolDescriptor selected, ToolApprovalPolicy.Mode selectedApprovalMode, ToolExecutionContext context) {
        return registeredTools().flatMap(tools -> {
            var current = tools.stream()
                    .filter(tool -> tool.descriptor().id().equals(selected.id()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Tool is no longer available: " + selected.id()));
            if (!current.descriptor().enabled() || !current.descriptor().configured()) {
                return Mono.error(new IllegalStateException("Tool is no longer ready: " + selected.id() + " ("
                        + current.descriptor().status() + ")"));
            }
            if (!Objects.equals(selected, current.descriptor())) {
                return Mono.error(new IllegalStateException(
                        "Tool definition changed; resolve the selection again: " + selected.id()));
            }
            if (selectedApprovalMode != approvalMode(current.definition().getApprovalPolicy())) {
                return Mono.error(new IllegalStateException(
                        "Tool approval policy changed; resolve the selection again: " + selected.id()));
            }
            return canExecute(current, context.getInput()).flatMap(allowed -> {
                if (!allowed) {
                    return Mono.error(new AccessDeniedException("Tool access denied: " + selected.id()));
                }
                return Mono.defer(() -> {
                            valueValidator.validateJsonValue(context.getInput(), selected.inputSchema(), "tool input");
                            var result = current.definition().getExecutor().execute(context);
                            if (result == null) {
                                return Mono.error(new IllegalStateException("Tool executor returned no result"));
                            }
                            return result.switchIfEmpty(
                                            Mono.error(new IllegalStateException("Tool executor returned no result")))
                                    .doOnNext(value -> {
                                        if (selected.outputSchema() != null) {
                                            valueValidator.validateJsonValue(
                                                    value, selected.outputSchema(), "tool output");
                                        }
                                    });
                        })
                        .subscribeOn(Schedulers.boundedElastic());
            });
        });
    }

    private static ToolApprovalPolicy copyApprovalPolicy(ToolApprovalPolicy policy) {
        if (policy == null) {
            return null;
        }
        if (approvalMode(policy) == ToolApprovalPolicy.Mode.DYNAMIC && policy.getPredicate() == null) {
            throw new IllegalArgumentException("Dynamic tool approval policy requires a predicate");
        }
        return ToolApprovalPolicy.builder()
                .mode(policy.getMode())
                .predicate(policy.getPredicate())
                .build();
    }

    private static ToolApprovalPolicy.Mode approvalMode(ToolApprovalPolicy policy) {
        return policy != null && policy.getMode() != null ? policy.getMode() : ToolApprovalPolicy.Mode.NEVER;
    }

    private List<RegisteredTool> withoutConflicts(List<RegisteredTool> tools) {
        var ids = new HashSet<String>();
        var names = new HashSet<String>();
        var conflictingIds = new HashSet<String>();
        var conflictingNames = new HashSet<String>();
        for (var tool : tools) {
            if (!ids.add(tool.descriptor().id())) {
                conflictingIds.add(tool.descriptor().id());
            }
            if (!names.add(tool.descriptor().name())) {
                conflictingNames.add(tool.descriptor().name());
            }
        }
        if (!conflictingIds.isEmpty() || !conflictingNames.isEmpty()) {
            log.warn("Ignoring conflicting AI tools: IDs {}, model names {}", conflictingIds, conflictingNames);
        }
        return tools.stream()
                .filter(tool -> !conflictingIds.contains(tool.descriptor().id())
                        && !conflictingNames.contains(tool.descriptor().name()))
                .sorted(java.util.Comparator.comparing(tool -> tool.descriptor().id()))
                .toList();
    }

    private static String modelName(String id, String localName) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
            return localName.substring(0, Math.min(40, localName.length())) + "_"
                    + HexFormat.of().formatHex(digest).substring(0, 23);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    /**
     * Validates standard schema keyword shapes while preserving provider extension keywords.
     *
     * <p>This checks schema structure during registration; actual arguments and results use the runtime value
     * validator. Child schemas may be objects or JSON Schema boolean shortcuts.
     *
     * @param schema JSON Schema object being registered
     */
    private static void validateSchema(Map<?, ?> schema) {
        validateSchemaType(schema.get("type"));
        validateRequiredProperties(schema.get("required"));
        if (schema.containsKey("enum") && !(schema.get("enum") instanceof List<?> values && !values.isEmpty())) {
            throw new IllegalArgumentException("JSON Schema enum must be a nonempty array");
        }
        for (var keyword : List.of("properties", "$defs", "definitions", "patternProperties")) {
            if (!schema.containsKey(keyword)) {
                continue;
            }
            if (!(schema.get(keyword) instanceof Map<?, ?> children)) {
                throw new IllegalArgumentException("JSON Schema " + keyword + " must be an object");
            }
            children.values().forEach(AiToolServiceImpl::validateChildSchema);
        }
        for (var keyword :
                List.of("items", "additionalProperties", "contains", "not", "if", "then", "else", "propertyNames")) {
            if (!schema.containsKey(keyword)) {
                continue;
            }
            var child = schema.get(keyword);
            if ("items".equals(keyword) && child instanceof List<?> children) {
                children.forEach(AiToolServiceImpl::validateChildSchema);
                continue;
            }
            validateChildSchema(child);
        }
        for (var keyword : List.of("anyOf", "allOf", "oneOf", "prefixItems")) {
            if (!schema.containsKey(keyword)) {
                continue;
            }
            if (!(schema.get(keyword) instanceof List<?> children) || children.isEmpty()) {
                throw new IllegalArgumentException("JSON Schema " + keyword + " must be a nonempty array");
            }
            children.forEach(AiToolServiceImpl::validateChildSchema);
        }
    }

    private static void validateSchemaType(Object type) {
        if (type == null) {
            return;
        }
        if (type instanceof String name && JSON_TYPES.contains(name)) {
            return;
        }
        if (!(type instanceof List<?> names) || names.isEmpty()) {
            throw new IllegalArgumentException("Invalid JSON Schema type");
        }
        if (!names.stream().allMatch(name -> name instanceof String && JSON_TYPES.contains(name))) {
            throw new IllegalArgumentException("Invalid JSON Schema type");
        }
    }

    private static void validateRequiredProperties(Object required) {
        if (required == null) {
            return;
        }
        if (!(required instanceof List<?> names)) {
            throw new IllegalArgumentException("JSON Schema required must contain unique strings");
        }
        if (!names.stream().allMatch(name -> name instanceof String)) {
            throw new IllegalArgumentException("JSON Schema required must contain unique strings");
        }
        if (new HashSet<>(names).size() != names.size()) {
            throw new IllegalArgumentException("JSON Schema required must contain unique strings");
        }
    }

    private static void validateChildSchema(Object child) {
        if (child instanceof Map<?, ?> schema) {
            validateSchema(schema);
            return;
        }
        if (child instanceof Boolean) {
            return;
        }
        throw new IllegalArgumentException("JSON Schema child must be an object or boolean");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Server-side registration snapshot paired with its serializable catalog metadata.
     *
     * @param descriptor immutable public catalog metadata
     * @param definition copied definition retaining server-side callbacks
     * @param configuration optional provider-owned settings callbacks
     * @param requiredPermissions declarations evaluated for the current caller
     * @param permissionDeniedBehavior resolution or execution rejection policy
     */
    private record RegisteredTool(
            ToolDescriptor descriptor,
            ToolDefinition definition,
            ToolConfiguration configuration,
            List<ToolPermission> requiredPermissions,
            ToolPermissionDeniedBehavior permissionDeniedBehavior) {}
}
