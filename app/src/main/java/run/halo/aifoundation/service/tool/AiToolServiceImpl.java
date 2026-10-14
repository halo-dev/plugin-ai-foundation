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
import run.halo.aifoundation.tool.ToolRegistration;
import run.halo.aifoundation.tool.builtin.BuiltInToolProvider;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

/** Discovers enabled providers on demand and refreshes selected executors before invocation. */
@Slf4j
@Component
public class AiToolServiceImpl implements AiToolService {

    private static final Duration DEFAULT_PROVIDER_TIMEOUT = Duration.ofSeconds(5);
    private static final Set<String> JSON_TYPES = Set.of("object", "array", "string", "number",
        "integer", "boolean", "null");

    private final ExtensionGetter extensionGetter;
    private final PluginManager pluginManager;
    private final List<AiToolProvider> builtInProviders;
    private final Duration providerTimeout;
    private final LanguageModelStructuredOutputHandler valueValidator;
    private final ToolSettingsStore settingsStore;

    @Autowired
    public AiToolServiceImpl(ExtensionGetter extensionGetter, PluginWrapper pluginWrapper,
        BuiltInToolProvider builtInProvider, ToolSettingsStore settingsStore) {
        this(extensionGetter, pluginWrapper.getPluginManager(), List.of(builtInProvider),
            DEFAULT_PROVIDER_TIMEOUT, settingsStore);
    }

    AiToolServiceImpl(ExtensionGetter extensionGetter, PluginManager pluginManager,
        List<AiToolProvider> builtInProviders, Duration providerTimeout) {
        this(extensionGetter, pluginManager, builtInProviders, providerTimeout, null);
    }

    AiToolServiceImpl(ExtensionGetter extensionGetter, PluginManager pluginManager,
        List<AiToolProvider> builtInProviders, Duration providerTimeout,
        ToolSettingsStore settingsStore) {
        this.extensionGetter = extensionGetter;
        this.pluginManager = pluginManager;
        this.builtInProviders = List.copyOf(builtInProviders);
        this.providerTimeout = providerTimeout;
        this.settingsStore = settingsStore;
        this.valueValidator = new LanguageModelStructuredOutputHandler(
            new LanguageModelResponseMapper("tool-registry",
                new LanguageModelMessageMapper("tool-registry")),
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
            return registeredTools().map(tools -> {
                var byId = new LinkedHashMap<String, RegisteredTool>();
                tools.forEach(tool -> byId.put(tool.descriptor().id(), tool));
                return selection.stream().map(id -> {
                    var tool = byId.get(id);
                    if (tool == null) {
                        throw new IllegalArgumentException("Tool is unavailable: " + id);
                    }
                    if (!tool.descriptor().enabled() || !tool.descriptor().configured()) {
                        throw new IllegalArgumentException("Tool is not ready: " + id
                            + " (" + tool.descriptor().status() + ")");
                    }
                    return executable(tool);
                }).toList();
            });
        });
    }

    public Mono<ToolDescriptor> updateEnabled(String id, boolean enabled) {
        return findTool(id).flatMap(ignored -> {
            if (settingsStore == null) {
                return Mono.error(new IllegalStateException("Tool settings store is unavailable"));
            }
            return settingsStore.setEnabled(id, enabled).then(Mono.defer(() -> findTool(id)))
                .map(RegisteredTool::descriptor);
        });
    }

    public Mono<Map<String, Object>> getConfiguration(String id) {
        return findTool(id).flatMap(tool -> {
            var configuration = requireConfiguration(tool);
            return Mono.defer(configuration.getValues())
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(providerTimeout)
                .defaultIfEmpty(Map.of());
        });
    }

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
        return registeredTools().map(tools -> tools.stream()
            .filter(tool -> tool.descriptor().id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Tool is unavailable: " + id)));
    }

    private static ToolConfiguration requireConfiguration(RegisteredTool tool) {
        if (tool.configuration() == null) {
            throw new IllegalArgumentException("Tool has no configuration: " + tool.descriptor().id());
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
            return providers.filter(provider -> builtInProviders.stream()
                .noneMatch(builtin -> builtin == provider)
                && !BuiltInToolProvider.class.isAssignableFrom(AopUtils.getTargetClass(provider)));
        });
        var tools = Flux.merge(
                Flux.fromIterable(builtInProviders)
                    .flatMap(provider -> providerTools(provider, "ai-foundation")),
                externalProviders.flatMap(provider -> providerTools(provider, null)))
            .collectList()
            .map(this::withoutConflicts);
        var states = settingsStore == null ? Mono.<Map<String, Boolean>>just(Map.of())
            : Mono.defer(settingsStore::enabledStates);
        return Mono.zip(tools, states).map(result -> result.getT1().stream().map(tool -> {
            var descriptor = tool.descriptor();
            var enabled = result.getT2().getOrDefault(descriptor.id(), true);
            var managedDescriptor = new ToolDescriptor(descriptor.id(), descriptor.pluginName(),
                descriptor.localName(), descriptor.name(), descriptor.displayName(),
                descriptor.description(), descriptor.displayDescription(), descriptor.category(),
                descriptor.inputSchema(), descriptor.outputSchema(), descriptor.tags(),
                descriptor.configurationSchema(), enabled, descriptor.configured(), null);
            return new RegisteredTool(managedDescriptor, tool.definition(), tool.configuration());
        }).toList());
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
                log.warn("Ignoring unavailable AI tool provider {}", provider.getClass().getName(),
                    error);
                return Flux.empty();
            });
    }

    private String providerOwner(AiToolProvider provider) {
        var owner = pluginManager.whichPlugin(AopUtils.getTargetClass(provider));
        if (owner == null || owner.getPluginId() == null || owner.getPluginId().isBlank()
            || owner.getPluginId().contains("/")) {
            throw new IllegalStateException("Cannot determine the AI tool provider plugin");
        }
        return owner.getPluginId();
    }

    private RegisteredTool register(String owner, ToolRegistration registration) {
        if (registration == null || registration.getDefinition() == null) {
            throw new IllegalArgumentException("Tool provider returned no definition");
        }
        var definition = registration.getDefinition();
        var localName = definition.getName();
        if (localName == null || !localName.matches("[A-Za-z0-9_-]{1,63}")) {
            throw new IllegalArgumentException("Tool local name must contain 1-63 letters, numbers,"
                + " underscores, or hyphens");
        }
        if (definition.getExecutor() == null) {
            throw new IllegalArgumentException("Discoverable tools must have a server-side executor");
        }
        var inputSchema = definition.getInputSchema();
        if (inputSchema == null || !"object".equals(inputSchema.get("type"))) {
            throw new IllegalArgumentException("Tool inputSchema must declare an object type");
        }
        var configuration = registration.getConfiguration();
        if (configuration != null && (configuration.getSchema() == null
            || configuration.getSchema().isEmpty() || configuration.getValues() == null
            || configuration.getSave() == null)) {
            throw new IllegalArgumentException("Tool configuration must provide a schema and callbacks");
        }
        var id = owner + "/" + localName;
        var descriptor = new ToolDescriptor(id, owner, localName, modelName(id, localName),
            hasText(registration.getDisplayName()) ? registration.getDisplayName() : localName,
            definition.getDescription(),
            hasText(registration.getDisplayDescription()) ? registration.getDisplayDescription()
                : definition.getDescription(),
            registration.getCategory(), inputSchema, definition.getOutputSchema(),
            registration.getTags(), configuration != null ? configuration.getSchema() : List.of(),
            true, registration.isConfigured(), null);
        validateSchema(descriptor.inputSchema());
        if (descriptor.outputSchema() != null) {
            validateSchema(descriptor.outputSchema());
        }
        var snapshot = copyDefinition(definition, descriptor);
        return new RegisteredTool(descriptor, snapshot, configuration);
    }

    private ToolDefinition copyDefinition(ToolDefinition source, ToolDescriptor descriptor) {
        var examples = source.getInputExamples() == null ? null : source.getInputExamples().stream()
            .map(example -> new ToolDescriptor(descriptor.id(), descriptor.pluginName(),
                descriptor.localName(), descriptor.name(), null, null, null, null, example, null)
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

    private Mono<Object> executeCurrent(ToolDescriptor selected,
        ToolApprovalPolicy.Mode selectedApprovalMode, ToolExecutionContext context) {
        return registeredTools().flatMap(tools -> {
            var current = tools.stream().filter(tool -> tool.descriptor().id().equals(selected.id()))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                    "Tool is no longer available: " + selected.id()));
            if (!current.descriptor().enabled() || !current.descriptor().configured()) {
                return Mono.error(new IllegalStateException("Tool is no longer ready: "
                    + selected.id() + " (" + current.descriptor().status() + ")"));
            }
            if (!Objects.equals(selected, current.descriptor())) {
                return Mono.error(new IllegalStateException(
                    "Tool definition changed; resolve the selection again: " + selected.id()));
            }
            if (selectedApprovalMode != approvalMode(current.definition().getApprovalPolicy())) {
                return Mono.error(new IllegalStateException(
                    "Tool approval policy changed; resolve the selection again: " + selected.id()));
            }
            return Mono.defer(() -> {
                valueValidator.validateJsonValue(context.getInput(), selected.inputSchema(),
                    "tool input");
                var result = current.definition().getExecutor().execute(context);
                if (result == null) {
                    return Mono.error(new IllegalStateException("Tool executor returned no result"));
                }
                return result.switchIfEmpty(Mono.error(
                        new IllegalStateException("Tool executor returned no result")))
                    .doOnNext(value -> {
                        if (selected.outputSchema() != null) {
                            valueValidator.validateJsonValue(value, selected.outputSchema(),
                                "tool output");
                        }
                    });
            }).subscribeOn(Schedulers.boundedElastic());
        });
    }

    private static ToolApprovalPolicy copyApprovalPolicy(ToolApprovalPolicy policy) {
        if (policy == null) {
            return null;
        }
        if (approvalMode(policy) == ToolApprovalPolicy.Mode.DYNAMIC
            && policy.getPredicate() == null) {
            throw new IllegalArgumentException("Dynamic tool approval policy requires a predicate");
        }
        return ToolApprovalPolicy.builder().mode(policy.getMode()).predicate(policy.getPredicate())
            .build();
    }

    private static ToolApprovalPolicy.Mode approvalMode(ToolApprovalPolicy policy) {
        return policy != null && policy.getMode() != null ? policy.getMode()
            : ToolApprovalPolicy.Mode.NEVER;
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
            log.warn("Ignoring conflicting AI tools: IDs {}, model names {}", conflictingIds,
                conflictingNames);
        }
        return tools.stream()
            .filter(tool -> !conflictingIds.contains(tool.descriptor().id())
                && !conflictingNames.contains(tool.descriptor().name()))
            .sorted(java.util.Comparator.comparing(tool -> tool.descriptor().id()))
            .toList();
    }

    private static String modelName(String id, String localName) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                .digest(id.getBytes(StandardCharsets.UTF_8));
            return localName.substring(0, Math.min(40, localName.length())) + "_"
                + HexFormat.of().formatHex(digest).substring(0, 23);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    /** Checks the shape of standard schema keywords without rejecting extension keywords. */
    private static void validateSchema(Map<?, ?> schema) {
        var type = schema.get("type");
        if (type != null && !(type instanceof String name && JSON_TYPES.contains(name))
            && !(type instanceof List<?> names && !names.isEmpty()
            && names.stream().allMatch(name -> name instanceof String && JSON_TYPES.contains(name)))) {
            throw new IllegalArgumentException("Invalid JSON Schema type");
        }
        var required = schema.get("required");
        if (required != null && !(required instanceof List<?> names
            && names.stream().allMatch(name -> name instanceof String)
            && new HashSet<>(names).size() == names.size())) {
            throw new IllegalArgumentException("JSON Schema required must contain unique strings");
        }
        if (schema.containsKey("enum")
            && !(schema.get("enum") instanceof List<?> values && !values.isEmpty())) {
            throw new IllegalArgumentException("JSON Schema enum must be a nonempty array");
        }
        for (var keyword : List.of("properties", "$defs", "definitions", "patternProperties")) {
            if (schema.containsKey(keyword)) {
                if (!(schema.get(keyword) instanceof Map<?, ?> children)) {
                    throw new IllegalArgumentException("JSON Schema " + keyword + " must be an object");
                }
                children.values().forEach(AiToolServiceImpl::validateChildSchema);
            }
        }
        for (var keyword : List.of("items", "additionalProperties", "contains", "not", "if",
            "then", "else", "propertyNames")) {
            if (schema.containsKey(keyword)) {
                var child = schema.get(keyword);
                if ("items".equals(keyword) && child instanceof List<?> children) {
                    children.forEach(AiToolServiceImpl::validateChildSchema);
                } else {
                    validateChildSchema(child);
                }
            }
        }
        for (var keyword : List.of("anyOf", "allOf", "oneOf", "prefixItems")) {
            if (schema.containsKey(keyword)) {
                if (!(schema.get(keyword) instanceof List<?> children) || children.isEmpty()) {
                    throw new IllegalArgumentException("JSON Schema " + keyword
                        + " must be a nonempty array");
                }
                children.forEach(AiToolServiceImpl::validateChildSchema);
            }
        }
    }

    private static void validateChildSchema(Object child) {
        if (child instanceof Map<?, ?> schema) {
            validateSchema(schema);
        } else if (!(child instanceof Boolean)) {
            throw new IllegalArgumentException("JSON Schema child must be an object or boolean");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record RegisteredTool(ToolDescriptor descriptor, ToolDefinition definition,
        ToolConfiguration configuration) {
    }
}
