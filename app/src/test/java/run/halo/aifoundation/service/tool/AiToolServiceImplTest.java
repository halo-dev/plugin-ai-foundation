package run.halo.aifoundation.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.control.CancellationToken;
import run.halo.aifoundation.exception.StructuredOutputValidationException;
import run.halo.aifoundation.tool.AiToolProvider;
import run.halo.aifoundation.tool.ToolApprovalPolicy;
import run.halo.aifoundation.tool.ToolConfiguration;
import run.halo.aifoundation.tool.ToolDefinition;
import run.halo.aifoundation.tool.ToolDescriptor;
import run.halo.aifoundation.tool.ToolExecutionContext;
import run.halo.aifoundation.tool.ToolInputAvailableCallback;
import run.halo.aifoundation.tool.ToolInputDeltaCallback;
import run.halo.aifoundation.tool.ToolInputStartCallback;
import run.halo.aifoundation.tool.ToolRegistration;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

class AiToolServiceImplTest {
    private final ExtensionGetter extensionGetter = mock(ExtensionGetter.class);
    private final PluginManager pluginManager = mock(PluginManager.class);
    private final AtomicReference<Flux<AiToolProvider>> providers =
        new AtomicReference<>(Flux.empty());

    AiToolServiceImplTest() {
        when(extensionGetter.getEnabledExtensions(AiToolProvider.class))
            .thenAnswer(ignored -> providers.get());
        var demoOwner = owner("demo");
        when(pluginManager.whichPlugin(any())).thenReturn(demoOwner);
    }

    @Test
    void listsBuiltInAndEnabledContributionsInStableOrder() {
        providers.set(Flux.just(provider(tool("zeta")), provider(tool("alpha"))));
        var service = service(List.of(provider(tool("clock"))));

        var tools = service.listTools().collectList().block();

        assertThat(tools).extracting(ToolDescriptor::id)
            .containsExactly("ai-foundation/clock", "demo/alpha", "demo/zeta");
        assertThat(tools.getFirst().pluginName()).isEqualTo("ai-foundation");
        assertThat(tools.get(1).displayName()).isEqualTo("alpha");
    }

    @Test
    void builtInProviderScannedByHaloIsOnlyRegisteredOnce() {
        var builtin = provider(tool("clock"));
        providers.set(Flux.just(builtin, provider(tool("external"))));

        assertThat(service(List.of(builtin)).listTools().collectList().block())
            .extracting(ToolDescriptor::id)
            .containsExactly("ai-foundation/clock", "demo/external");
    }

    @Test
    void generatedNamesRemainDistinctForLossyNamespacesAndLongLocalNames() {
        var first = new FirstProvider(() -> Flux.just(tool("x".repeat(62) + "a")));
        var second = new SecondProvider(() -> Flux.just(tool("x".repeat(62) + "b")));
        var firstOwner = owner("demo-one");
        var secondOwner = owner("demo_one");
        when(pluginManager.whichPlugin(FirstProvider.class)).thenReturn(firstOwner);
        when(pluginManager.whichPlugin(SecondProvider.class)).thenReturn(secondOwner);
        providers.set(Flux.just(second, first));
        var service = service();

        var firstCatalog = service.listTools().collectList().block();
        var secondCatalog = service.listTools().collectList().block();

        assertThat(firstCatalog).extracting(ToolDescriptor::name).doesNotHaveDuplicates();
        assertThat(firstCatalog).extracting(ToolDescriptor::name)
            .allSatisfy(name -> assertThat(name).matches("[A-Za-z0-9_-]{64}"));
        assertThat(secondCatalog).isEqualTo(firstCatalog);
    }

    @Test
    void conflictingIdsExcludeEveryContributionWithThatId() {
        providers.set(Flux.just(provider(tool("duplicate"), tool("available")),
            provider(tool("duplicate"))));

        assertThat(service().listTools().collectList().block()).extracting(ToolDescriptor::id)
            .containsExactly("demo/available");
    }

    @Test
    void invalidProvidersAreIsolatedWithoutPartialRegistration() {
        var invalidSchema = tool("invalid-schema");
        invalidSchema.getDefinition().setInputSchema(Map.of("type", "object", "properties",
            Map.of("invalid", Map.of("type", "not-a-type"))));
        var missingExecutor = tool("missing-executor");
        missingExecutor.getDefinition().setExecutor(null);
        var scalarInput = tool("scalar-input");
        scalarInput.getDefinition().setInputSchema(Map.of("type", "string"));
        var invalidName = tool("bad/name");
        providers.set(Flux.just(provider(tool("healthy")),
            provider(tool("partial"), invalidSchema), provider(missingExecutor),
            provider(scalarInput), provider(invalidName),
            new TestProvider(() -> { throw new IllegalStateException("failed discovery"); }),
            new TestProvider(() -> null),
            new TestProvider(() -> Flux.error(new IllegalStateException("async failure")))));

        assertThat(service().listTools().collectList().block()).extracting(ToolDescriptor::id)
            .containsExactly("demo/healthy");
    }

    @Test
    void providerFailureDoesNotWaitForUnboundedStream() {
        providers.set(Flux.just(new TestProvider(Flux::never), provider(tool("healthy"))));
        var service = new AiToolServiceImpl(extensionGetter, pluginManager, List.of(),
            Duration.ofMillis(50));

        StepVerifier.create(service.listTools())
            .assertNext(tool -> assertThat(tool.id()).isEqualTo("demo/healthy"))
            .verifyComplete();
    }

    @Test
    void providersWithMoreThan100ToolsRemainDiscoverableAndSelectable() {
        providers.set(Flux.just(new TestProvider(() -> Flux.range(0, 250)
            .map(index -> tool("tool" + index)))));
        var service = service();

        assertThat(service.listTools().collectList().block())
            .hasSize(250)
            .extracting(ToolDescriptor::id)
            .contains("demo/tool0", "demo/tool249");
        assertThat(service.resolveTools(List.of("demo/tool249")).block())
            .hasSize(1)
            .first().satisfies(definition ->
                assertThat(definition.getName()).startsWith("tool249_"));
    }

    @Test
    void discoveryRunsOnBoundedElastic() {
        var thread = new AtomicReference<String>();
        providers.set(Flux.just(new TestProvider(() -> {
            thread.set(Thread.currentThread().getName());
            return Flux.just(tool("healthy"));
        })));

        service().listTools().collectList().block();

        assertThat(thread.get()).startsWith("boundedElastic-");
    }

    @Test
    void unownedProviderCannotChooseItsOwnPluginNamespace() {
        when(pluginManager.whichPlugin(any())).thenReturn(null);
        providers.set(Flux.just(provider(tool("external"))));

        assertThat(service(List.of(provider(tool("builtin"))))
            .listTools().collectList().block()).extracting(ToolDescriptor::id)
            .containsExactly("ai-foundation/builtin");
    }

    @Test
    void resolutionsRequireExplicitUniqueAvailableIdsAndPreserveSelectionOrder() {
        providers.set(Flux.just(provider(tool("a"), tool("b"))));
        var service = service();

        assertThat(service.resolveTools(null).block()).isEmpty();
        assertThat(service.resolveTools(List.of()).block()).isEmpty();
        assertThat(service.resolveTools(List.of("demo/b", "demo/a")).block())
            .extracting(ToolDefinition::getName)
            .allSatisfy(name -> assertThat(name).matches("[A-Za-z0-9_-]{1,64}"))
            .first().asString().startsWith("b_");
        StepVerifier.create(service.resolveTools(List.of("demo/a", "demo/a")))
            .expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(service.resolveTools(List.of(" ")))
            .expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(service.resolveTools(List.of("demo/missing")))
            .expectError(IllegalArgumentException.class).verify();
    }

    @Test
    void disabledAndUnconfiguredToolsStayVisibleButCannotResolve() {
        var store = mock(ToolSettingsStore.class);
        when(store.enabledStates()).thenReturn(Mono.just(Map.of("demo/disabled", false)));
        var unconfigured = ToolRegistration.builder().definition(tool("unconfigured").getDefinition())
            .configured(false).tags(List.of("待配置")).build();
        providers.set(Flux.just(provider(tool("disabled"), unconfigured, tool("ready"))));
        var service = new AiToolServiceImpl(extensionGetter, pluginManager, List.of(),
            Duration.ofSeconds(5), store);

        var catalog = service.listTools().collectList().block();

        assertThat(catalog).extracting(ToolDescriptor::status)
            .containsExactly("DISABLED", "ENABLED", "UNCONFIGURED");
        assertThat(catalog.getLast().tags()).containsExactly("待配置");
        StepVerifier.create(service.resolveTools(List.of("demo/disabled")))
            .expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(service.resolveTools(List.of("demo/unconfigured")))
            .expectError(IllegalArgumentException.class).verify();
        assertThat(service.resolveTools(List.of("demo/ready")).block()).hasSize(1);
    }

    @Test
    void persistedDisablePreventsAnAlreadyResolvedExecutor() {
        var states = new AtomicReference<Map<String, Boolean>>(Map.of());
        var store = mock(ToolSettingsStore.class);
        when(store.enabledStates()).thenAnswer(ignored -> Mono.just(states.get()));
        when(store.setEnabled("demo/managed", false)).thenReturn(Mono.fromRunnable(() ->
            states.set(Map.of("demo/managed", false))));
        providers.set(Flux.just(provider(tool("managed"))));
        var service = new AiToolServiceImpl(extensionGetter, pluginManager, List.of(),
            Duration.ofSeconds(5), store);
        var selected = service.resolveTools(List.of("demo/managed")).block().getFirst();

        assertThat(service.updateEnabled("demo/managed", false).block().status()).isEqualTo("DISABLED");
        StepVerifier.create(selected.getExecutor().execute(context(Map.of())))
            .expectErrorMessage("Tool is no longer ready: demo/managed (DISABLED)").verify();
    }

    @Test
    void becomingUnconfiguredPreventsAnAlreadyResolvedExecutor() {
        providers.set(Flux.just(provider(tool("managed"))));
        var service = service();
        var selected = service.resolveTools(List.of("demo/managed")).block().getFirst();
        var unconfigured = ToolRegistration.builder().definition(tool("managed").getDefinition())
            .configured(false).build();
        providers.set(Flux.just(provider(unconfigured)));

        StepVerifier.create(selected.getExecutor().execute(context(Map.of())))
            .expectErrorMessage("Tool is no longer ready: demo/managed (UNCONFIGURED)").verify();
    }

    @Test
    void configurationCallbacksRemainAccessibleForDisabledUnconfiguredTools() {
        var values = new AtomicReference<Map<String, Object>>(Map.of());
        var store = mock(ToolSettingsStore.class);
        when(store.enabledStates()).thenReturn(Mono.just(Map.of("demo/configurable", false)));
        var configuration = ToolConfiguration.builder()
            .schema(List.of(Map.of("$formkit", "secret", "name", "secretName")))
            .values(() -> Mono.just(values.get()))
            .save(next -> Mono.fromRunnable(() -> values.set(next))).build();
        var registration = ToolRegistration.builder().definition(tool("configurable").getDefinition())
            .configured(false).configuration(configuration).build();
        providers.set(Flux.just(provider(registration)));
        var service = new AiToolServiceImpl(extensionGetter, pluginManager, List.of(),
            Duration.ofSeconds(5), store);

        assertThat(service.getConfiguration("demo/configurable").block()).isEmpty();
        assertThat(service.updateConfiguration("demo/configurable", Map.of("secretName", "ref"))
            .block()).containsEntry("secretName", "ref");
        var descriptor = service.listTools().blockFirst();
        assertThat(descriptor.configurationSchema().getFirst()).containsEntry("$formkit", "secret");
        assertThat(descriptor.status()).isEqualTo("DISABLED");
        StepVerifier.create(service.updateConfiguration("demo/configurable", null))
            .expectError(IllegalArgumentException.class).verify();
    }

    @Test
    void removingAProviderPreventsPreviouslyResolvedExecutorFromRunning() {
        var executions = new AtomicInteger();
        var registration = tool("write");
        registration.getDefinition().setExecutor(context -> {
            executions.incrementAndGet();
            return Mono.just("done");
        });
        providers.set(Flux.just(provider(registration)));
        var service = service();
        var selected = service.resolveTools(List.of("demo/write")).block().getFirst();
        providers.set(Flux.empty());

        StepVerifier.create(selected.getExecutor().execute(context(Map.of())))
            .expectErrorMessage("Tool is no longer available: demo/write").verify();
        assertThat(executions.get()).isZero();
    }

    @Test
    void reloadedProviderUsesCurrentExecutorInsteadOfCapturedInstance() {
        var previous = tool("reload");
        previous.getDefinition().setExecutor(context -> Mono.just("old"));
        providers.set(Flux.just(provider(previous)));
        var service = service();
        var selected = service.resolveTools(List.of("demo/reload")).block().getFirst();
        var current = tool("reload");
        current.getDefinition().setExecutor(context -> Mono.just("new"));
        providers.set(Flux.just(provider(current)));

        assertThat(selected.getExecutor().execute(context(Map.of())).block()).isEqualTo("new");
    }

    @Test
    void changedSchemaRequiresResolvingSelectionAgain() {
        var registration = tool("changed");
        providers.set(Flux.just(provider(registration)));
        var service = service();
        var selected = service.resolveTools(List.of("demo/changed")).block().getFirst();
        registration.getDefinition().setInputSchema(Map.of("type", "object", "required",
            List.of("name")));

        StepVerifier.create(selected.getExecutor().execute(context(Map.of())))
            .expectErrorMessage("Tool definition changed; resolve the selection again: demo/changed")
            .verify();
    }

    @Test
    void changingApprovalModeCannotReuseAnEarlierUnapprovedSelection() {
        var registration = tool("approval");
        var policy = ToolApprovalPolicy.never();
        registration.getDefinition().setApprovalPolicy(policy);
        providers.set(Flux.just(provider(registration)));
        var service = service();
        var selected = service.resolveTools(List.of("demo/approval")).block().getFirst();
        policy.setMode(ToolApprovalPolicy.Mode.ALWAYS);

        assertThat(selected.getApprovalPolicy().getMode()).isEqualTo(ToolApprovalPolicy.Mode.NEVER);
        StepVerifier.create(selected.getExecutor().execute(context(Map.of())))
            .expectErrorMessage("Tool approval policy changed; resolve the selection again: demo/approval")
            .verify();
    }

    @Test
    void preservesLifecycleApprovalAndTrustedExecutionContext() {
        var registration = tool("context");
        var definition = registration.getDefinition();
        var onStart = mock(ToolInputStartCallback.class);
        var onDelta = mock(ToolInputDeltaCallback.class);
        var onAvailable = mock(ToolInputAvailableCallback.class);
        var approval = ToolApprovalPolicy.always();
        definition.setApprovalPolicy(approval);
        definition.setOnInputStart(onStart);
        definition.setOnInputDelta(onDelta);
        definition.setOnInputAvailable(onAvailable);
        var context = ToolExecutionContext.builder()
            .toolCallId("call-1").toolName("generated-name").input(Map.of())
            .requestContext(Map.of("trusted-user", "admin"))
            .cancellationToken(mock(CancellationToken.class)).build();
        definition.setExecutor(received -> {
            assertThat(received).isSameAs(context);
            return ReactiveSecurityContextHolder.getContext()
                .map(security -> (Object) security.getAuthentication().getName());
        });
        providers.set(Flux.just(provider(registration)));
        var selected = service().resolveTools(List.of("demo/context")).block().getFirst();

        assertThat(selected.getApprovalPolicy()).isEqualTo(approval).isNotSameAs(approval);
        assertThat(selected.getOnInputStart()).isSameAs(onStart);
        assertThat(selected.getOnInputDelta()).isSameAs(onDelta);
        assertThat(selected.getOnInputAvailable()).isSameAs(onAvailable);
        var authentication = new UsernamePasswordAuthenticationToken("trusted-admin", "unused");
        assertThat(selected.getExecutor().execute(context)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)).block())
            .isEqualTo("trusted-admin");
    }

    @Test
    void executorValidatesInputsAndOutputsThroughExistingRuntimeValidator() {
        var calls = new AtomicInteger();
        var registration = tool("typed");
        registration.getDefinition().setInputSchema(Map.of("type", "object", "properties",
            Map.of("number", Map.of("type", "integer")), "required", List.of("number")));
        registration.getDefinition().setOutputSchema(Map.of("type", "integer"));
        registration.getDefinition().setExecutor(context -> {
            calls.incrementAndGet();
            return Mono.just("unexpected result");
        });
        providers.set(Flux.just(provider(registration)));
        var selected = service().resolveTools(List.of("demo/typed")).block().getFirst();

        StepVerifier.create(selected.getExecutor().execute(context(Map.of("number", "string"))))
            .expectError(StructuredOutputValidationException.class).verify();
        assertThat(calls.get()).isZero();
        StepVerifier.create(selected.getExecutor().execute(context(Map.of("number", 1))))
            .expectError(StructuredOutputValidationException.class).verify();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void catalogSchemasAreDeeplyCopiedAndImmutable() {
        var property = new LinkedHashMap<String, Object>(Map.of("type", "string"));
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>(Map.of("name", property)));
        schema.put("required", new ArrayList<>(List.of("name")));
        var registration = tool("immutable");
        registration.getDefinition().setInputSchema(schema);
        providers.set(Flux.just(provider(registration)));
        var descriptor = service().listTools().blockFirst();
        property.put("type", "integer");
        schema.clear();

        assertThat(descriptor.inputSchema()).containsEntry("type", "object");
        var properties = (Map<?, ?>) descriptor.inputSchema().get("properties");
        assertThat(((Map<?, ?>) properties.get("name")).get("type")).isEqualTo("string");
        assertThatThrownBy(() -> descriptor.inputSchema().put("type", "array"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    private AiToolServiceImpl service() {
        return service(List.of());
    }

    private AiToolServiceImpl service(List<AiToolProvider> builtins) {
        return new AiToolServiceImpl(extensionGetter, pluginManager, builtins, Duration.ofSeconds(5));
    }

    private static TestProvider provider(ToolRegistration... tools) {
        return new TestProvider(() -> Flux.fromArray(tools));
    }

    private static ToolRegistration tool(String name) {
        return ToolRegistration.builder().definition(ToolDefinition.builder().name(name)
            .description("Execute " + name).inputSchema(Map.of("type", "object"))
            .executor(context -> Mono.just(Map.of("name", name))).build()).build();
    }

    private static ToolExecutionContext context(Map<String, Object> input) {
        return ToolExecutionContext.builder().input(input).build();
    }

    private static PluginWrapper owner(String id) {
        var owner = mock(PluginWrapper.class);
        when(owner.getPluginId()).thenReturn(id);
        return owner;
    }

    private static class TestProvider implements AiToolProvider {
        private final Supplier<Flux<ToolRegistration>> registrations;

        TestProvider(Supplier<Flux<ToolRegistration>> registrations) {
            this.registrations = registrations;
        }

        @Override
        public Flux<ToolRegistration> tools() {
            return registrations.get();
        }
    }

    private static final class FirstProvider extends TestProvider {
        FirstProvider(Supplier<Flux<ToolRegistration>> registrations) {
            super(registrations);
        }
    }

    private static final class SecondProvider extends TestProvider {
        SecondProvider(Supplier<Flux<ToolRegistration>> registrations) {
            super(registrations);
        }
    }
}
