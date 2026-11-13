package run.halo.aifoundation.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
import run.halo.aifoundation.tool.ToolGroup;
import run.halo.aifoundation.tool.ToolInputAvailableCallback;
import run.halo.aifoundation.tool.ToolInputDeltaCallback;
import run.halo.aifoundation.tool.ToolInputStartCallback;
import run.halo.aifoundation.tool.ToolPermission;
import run.halo.aifoundation.tool.ToolPermissionDeniedBehavior;
import run.halo.aifoundation.tool.ToolPreset;
import run.halo.aifoundation.tool.ToolRegistration;
import run.halo.app.core.extension.Role;
import run.halo.app.core.user.service.RoleService;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

class AiToolServiceImplTest {
    private final ExtensionGetter extensionGetter = mock(ExtensionGetter.class);
    private final PluginManager pluginManager = mock(PluginManager.class);
    private final AtomicReference<Flux<AiToolProvider>> providers = new AtomicReference<>(Flux.empty());

    AiToolServiceImplTest() {
        when(extensionGetter.getEnabledExtensions(AiToolProvider.class)).thenAnswer(ignored -> providers.get());
        var demoOwner = owner("demo");
        when(pluginManager.whichPlugin(any())).thenReturn(demoOwner);
    }

    @Test
    void listsBuiltInAndEnabledContributionsInStableOrder() {
        providers.set(Flux.just(provider(tool("zeta")), provider(tool("alpha"))));
        var service = service(List.of(provider(tool("clock"))));

        var tools = service.listTools().collectList().block();

        assertThat(tools)
                .extracting(ToolDescriptor::id)
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
        assertThat(firstCatalog)
                .extracting(ToolDescriptor::name)
                .allSatisfy(name -> assertThat(name).matches("[A-Za-z0-9_-]{64}"));
        assertThat(secondCatalog).isEqualTo(firstCatalog);
    }

    @Test
    void conflictingIdsExcludeEveryContributionWithThatId() {
        providers.set(Flux.just(provider(tool("duplicate"), tool("available")), provider(tool("duplicate"))));

        assertThat(service().listTools().collectList().block())
                .extracting(ToolDescriptor::id)
                .containsExactly("demo/available");
    }

    @Test
    void invalidProvidersAreIsolatedWithoutPartialRegistration() {
        var invalidSchema = tool("invalid-schema");
        invalidSchema
                .getDefinition()
                .setInputSchema(
                        Map.of("type", "object", "properties", Map.of("invalid", Map.of("type", "not-a-type"))));
        var missingExecutor = tool("missing-executor");
        missingExecutor.getDefinition().setExecutor(null);
        var scalarInput = tool("scalar-input");
        scalarInput.getDefinition().setInputSchema(Map.of("type", "string"));
        var invalidName = tool("bad/name");
        providers.set(Flux.just(
                provider(tool("healthy")),
                provider(tool("partial"), invalidSchema),
                provider(missingExecutor),
                provider(scalarInput),
                provider(invalidName),
                new TestProvider(() -> {
                    throw new IllegalStateException("failed discovery");
                }),
                new TestProvider(() -> null),
                new TestProvider(() -> Flux.error(new IllegalStateException("async failure")))));

        assertThat(service().listTools().collectList().block())
                .extracting(ToolDescriptor::id)
                .containsExactly("demo/healthy");
    }

    @Test
    void providerFailureDoesNotWaitForUnboundedStream() {
        providers.set(Flux.just(new TestProvider(Flux::never), provider(tool("healthy"))));
        var service = new AiToolServiceImpl(extensionGetter, pluginManager, List.of(), Duration.ofMillis(50));

        StepVerifier.create(service.listTools())
                .assertNext(tool -> assertThat(tool.id()).isEqualTo("demo/healthy"))
                .verifyComplete();
    }

    @Test
    void providersWithMoreThan100ToolsRemainDiscoverableAndSelectable() {
        providers.set(Flux.just(new TestProvider(() -> Flux.range(0, 250).map(index -> tool("tool" + index)))));
        var service = service();

        assertThat(service.listTools().collectList().block())
                .hasSize(250)
                .extracting(ToolDescriptor::id)
                .contains("demo/tool0", "demo/tool249");
        assertThat(service.resolveTools(List.of("demo/tool249")).block())
                .hasSize(1)
                .first()
                .satisfies(definition -> assertThat(definition.getName()).startsWith("tool249_"));
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
                        .listTools()
                        .collectList()
                        .block())
                .extracting(ToolDescriptor::id)
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
                .first()
                .asString()
                .startsWith("b_");
        StepVerifier.create(service.resolveTools(List.of("demo/a", "demo/a")))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(service.resolveTools(List.of(" ")))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(service.resolveTools(List.of("demo/missing")))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void disabledAndUnconfiguredToolsStayVisibleButCannotResolve() {
        var store = mock(ToolSettingsStore.class);
        when(store.enabledStates()).thenReturn(Mono.just(Map.of("demo/disabled", false)));
        var unconfigured = ToolRegistration.builder()
                .definition(tool("unconfigured").getDefinition())
                .configured(false)
                .tags(List.of("待配置"))
                .build();
        providers.set(Flux.just(provider(tool("disabled"), unconfigured, tool("ready"))));
        var service = new AiToolServiceImpl(extensionGetter, pluginManager, List.of(), Duration.ofSeconds(5), store);

        var catalog = service.listTools().collectList().block();

        assertThat(catalog).extracting(ToolDescriptor::status).containsExactly("DISABLED", "ENABLED", "UNCONFIGURED");
        assertThat(catalog.getLast().tags()).containsExactly("待配置");
        StepVerifier.create(service.resolveTools(List.of("demo/disabled")))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(service.resolveTools(List.of("demo/unconfigured")))
                .expectError(IllegalArgumentException.class)
                .verify();
        assertThat(service.resolveTools(List.of("demo/ready")).block()).hasSize(1);
    }

    @Test
    void declaredPermissionsFilterResolutionsWithoutHidingTheManagementCatalog() {
        var checks = new AtomicInteger();
        var deniedPermissions = List.<ToolPermission>of(ToolPermission.resource("content.halo.run", "posts", "update"));
        var allowedPermissions = List.<ToolPermission>of(ToolPermission.resource("content.halo.run", "posts", "list"));
        var checker = mock(ToolPermissionChecker.class);
        when(checker.canResolve(any())).thenAnswer(invocation -> {
            checks.incrementAndGet();
            return Mono.just(invocation.getArgument(0).equals(allowedPermissions));
        });
        var denied = tool("denied").toBuilder()
                .requiredPermissions(deniedPermissions)
                .build();
        var allowed = tool("allowed").toBuilder()
                .requiredPermissions(allowedPermissions)
                .build();
        providers.set(Flux.just(provider(denied, allowed, tool("open"))));
        var service = service(checker);

        assertThat(service.listTools().collectList().block())
                .extracting(ToolDescriptor::localName)
                .containsExactly("allowed", "denied", "open");
        assertThat(checks).hasValue(0);
        assertThat(service.resolveTools(List.of("demo/open", "demo/denied", "demo/allowed"))
                        .block())
                .extracting(ToolDefinition::getName)
                .satisfiesExactly(
                        name -> assertThat(name).startsWith("open_"),
                        name -> assertThat(name).startsWith("allowed_"));
        assertThat(service.resolveAllTools().block())
                .extracting(ToolDefinition::getName)
                .satisfiesExactly(
                        name -> assertThat(name).startsWith("allowed_"),
                        name -> assertThat(name).startsWith("open_"));
        assertThat(checks).hasValue(4);
    }

    @Test
    void permissionDenialCannotConcealInvalidDisabledOrUnconfiguredSelections() {
        var checks = new AtomicInteger();
        var checker = mock(ToolPermissionChecker.class);
        when(checker.canResolve(any())).thenAnswer(ignored -> {
            checks.incrementAndGet();
            return Mono.just(false);
        });
        var denied = protectedTool("denied");
        var store = mock(ToolSettingsStore.class);
        when(store.enabledStates()).thenReturn(Mono.just(Map.of("demo/disabled", false)));
        providers.set(Flux.just(provider(
                denied,
                tool("disabled"),
                tool("unconfigured").toBuilder().configured(false).build())));
        var service =
                new AiToolServiceImpl(extensionGetter, pluginManager, List.of(), Duration.ofSeconds(5), store, checker);

        for (var id : List.of("demo/missing", "demo/disabled", "demo/unconfigured")) {
            StepVerifier.create(service.resolveTools(List.of("demo/denied", id)))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
        assertThat(checks).hasValue(0);
    }

    @Test
    void emptyFailedNullThrownAndTimedOutPermissionChecksDenyWithoutAffectingPublicTools() {
        var failingChecks = List.<Supplier<Mono<Boolean>>>of(
                Mono::empty,
                () -> Mono.error(new IllegalStateException("Permission service unavailable")),
                () -> null,
                () -> {
                    throw new IllegalStateException("Permission lookup failed");
                },
                Mono::never);
        var checker = mock(ToolPermissionChecker.class);
        var registrations = new ArrayList<ToolRegistration>();
        for (int index = 0; index < failingChecks.size(); index++) {
            var registration = protectedTool("denied" + index);
            var check = failingChecks.get(index);
            when(checker.canResolve(registration.getRequiredPermissions())).thenAnswer(ignored -> check.get());
            registrations.add(registration);
        }
        registrations.add(tool("open"));
        providers.set(Flux.just(provider(registrations.toArray(ToolRegistration[]::new))));
        var service =
                new AiToolServiceImpl(extensionGetter, pluginManager, List.of(), Duration.ofMillis(100), null, checker);

        StepVerifier.create(service.resolveAllTools())
                .assertNext(resolved -> assertThat(resolved)
                        .hasSize(1)
                        .first()
                        .satisfies(
                                definition -> assertThat(definition.getName()).startsWith("open_")))
                .expectComplete()
                .verify(Duration.ofSeconds(3));
        assertThat(service.resolveTools(
                                List.of("demo/denied0", "demo/denied1", "demo/denied2", "demo/denied3", "demo/denied4"))
                        .block(Duration.ofSeconds(3)))
                .isEmpty();
    }

    @Test
    void executionChecksFreshDeclarationsAndNeverInvokesDeniedExecutors() {
        var calls = new AtomicInteger();
        var original = protectedTool("managed");
        original.getDefinition().setExecutor(context -> {
            calls.incrementAndGet();
            return Mono.just("executed");
        });
        providers.set(Flux.just(provider(original)));
        var checker = mock(ToolPermissionChecker.class);
        when(checker.canResolve(any())).thenReturn(Mono.just(true));
        var service =
                new AiToolServiceImpl(extensionGetter, pluginManager, List.of(), Duration.ofMillis(100), null, checker);
        var selected = service.resolveTools(List.of("demo/managed")).block().getFirst();
        var changedPermissions =
                List.<ToolPermission>of(ToolPermission.named("content.halo.run", "posts", "update", "name"));
        providers.set(Flux.just(provider(original.toBuilder()
                .requiredPermissions(changedPermissions)
                .permissionDeniedBehavior(ToolPermissionDeniedBehavior.DENY_ON_EXECUTE)
                .build())));
        var input = Map.<String, Object>of("name", "restricted");

        for (var check : List.<Supplier<Mono<Boolean>>>of(
                () -> Mono.just(false),
                Mono::empty,
                () -> Mono.error(new IllegalStateException("Unavailable")),
                () -> null,
                () -> {
                    throw new IllegalStateException("Lookup failed");
                },
                Mono::never)) {
            doAnswer(ignored -> check.get()).when(checker).canExecute(changedPermissions, input);
            StepVerifier.create(selected.getExecutor().execute(context(input)))
                    .expectErrorSatisfies(error -> assertThat(error)
                            .isInstanceOf(AccessDeniedException.class)
                            .hasMessage("Tool access denied: demo/managed"))
                    .verify(Duration.ofSeconds(3));
        }
        assertThat(calls).hasValue(0);
        when(checker.canExecute(changedPermissions, input)).thenReturn(Mono.just(true));
        assertThat(selected.getExecutor().execute(context(input)).block()).isEqualTo("executed");
        assertThat(calls).hasValue(1);
    }

    @Test
    void denyOnExecuteKeepsToolsResolvableButRejectsUnauthorizedExecution() {
        var calls = new AtomicInteger();
        var resolveChecks = new AtomicInteger();
        var registration = protectedTool("managed").toBuilder()
                .permissionDeniedBehavior(ToolPermissionDeniedBehavior.DENY_ON_EXECUTE)
                .build();
        registration.getDefinition().setExecutor(context -> {
            calls.incrementAndGet();
            return Mono.just("executed");
        });
        var checker = mock(ToolPermissionChecker.class);
        when(checker.canResolve(any())).thenAnswer(ignored -> {
            resolveChecks.incrementAndGet();
            return Mono.just(false);
        });
        when(checker.canExecute(any(), any())).thenReturn(Mono.just(false));
        providers.set(Flux.just(provider(registration)));
        var service = service(checker);

        assertThat(service.resolveAllTools().block()).hasSize(1);
        var selected = service.resolveTools(List.of("demo/managed")).block().getFirst();
        assertThat(resolveChecks).hasValue(0);
        StepVerifier.create(selected.getExecutor().execute(context(Map.of("approved", true))))
                .expectError(AccessDeniedException.class)
                .verify();
        assertThat(calls).hasValue(0);
        when(checker.canExecute(any(), any())).thenReturn(Mono.just(true));
        assertThat(selected.getExecutor().execute(context(Map.of())).block()).isEqualTo("executed");
        assertThat(calls).hasValue(1);
    }

    @Test
    void executionRechecksPermissionsRevokedAfterResolution() {
        var allowed = new AtomicReference<>(true);
        var checker = mock(ToolPermissionChecker.class);
        when(checker.canResolve(any())).thenAnswer(ignored -> Mono.just(allowed.get()));
        when(checker.canExecute(any(), any())).thenAnswer(ignored -> Mono.just(allowed.get()));
        providers.set(Flux.just(provider(protectedTool("managed"))));
        var service = service(checker);
        var selected = service.resolveTools(List.of("demo/managed")).block().getFirst();
        assertThat(selected.getExecutor().execute(context(Map.of())).block()).isEqualTo(Map.of("name", "managed"));

        allowed.set(false);
        StepVerifier.create(selected.getExecutor().execute(context(Map.of())))
                .expectError(AccessDeniedException.class)
                .verify();
        assertThat(service.resolveAllTools().block()).isEmpty();
    }

    @Test
    void namedPermissionsEvaluateTheActualExecutionInput() {
        var permission = ToolPermission.named("content.halo.run", "posts", "get", "name");
        var checker = mock(ToolPermissionChecker.class);
        when(checker.canResolve(List.of(permission))).thenReturn(Mono.just(true));
        when(checker.canExecute(List.of(permission), Map.of("name", "allowed"))).thenReturn(Mono.just(true));
        when(checker.canExecute(List.of(permission), Map.of("name", "other"))).thenReturn(Mono.just(false));
        providers.set(Flux.just(provider(tool("post").toBuilder()
                .requiredPermissions(List.of(permission))
                .build())));
        var selected =
                service(checker).resolveTools(List.of("demo/post")).block().getFirst();

        assertThat(selected.getExecutor()
                        .execute(context(Map.of("name", "allowed")))
                        .block())
                .isEqualTo(Map.of("name", "post"));
        StepVerifier.create(selected.getExecutor().execute(context(Map.of("name", "other"))))
                .expectError(AccessDeniedException.class)
                .verify();
        StepVerifier.create(selected.getExecutor().execute(context(Map.of())))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void nativeNamedResourceRulesPermitOnlyTheDeclaredResourceAndNeverCollectionReads() {
        var calls = new AtomicInteger();
        var rule = new Role.PolicyRule(
                new String[] {"content.halo.run"},
                new String[] {"posts"},
                new String[] {"allowed-post"},
                null,
                new String[] {"get", "list"});
        var role = new Role();
        role.setRules(List.of(rule));
        var roles = mock(RoleService.class);
        var effectiveRoles = new AtomicReference<Flux<Role>>(Flux.just(role));
        when(roles.listDependenciesFlux(Set.of("reader"))).thenAnswer(ignored -> effectiveRoles.get());
        var read = tool("get_post").toBuilder()
                .requiredPermissions(List.of(ToolPermission.named("content.halo.run", "posts", "get", "name")))
                .build();
        read.getDefinition().setExecutor(context -> {
            calls.incrementAndGet();
            return Mono.just(context.getInput().get("name"));
        });
        var list = tool("list_posts").toBuilder()
                .requiredPermissions(List.of(ToolPermission.resource("content.halo.run", "posts", "list")))
                .build();
        providers.set(Flux.just(provider(read, list)));
        var service = service(new ToolPermissionChecker(roles));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "reader", "unused", List.of(new SimpleGrantedAuthority("ROLE_reader")));
        var securityContext = ReactiveSecurityContextHolder.withAuthentication(authentication);

        assertThat(service.resolveAllTools().block()).isEmpty();
        var definitions =
                service.resolveAllTools().contextWrite(securityContext).block();
        assertThat(definitions).hasSize(1);
        var selected = definitions.getFirst();
        assertThat(selected.getName()).startsWith("get_post_");
        assertThat(selected.getExecutor()
                        .execute(context(Map.of("name", "allowed-post")))
                        .contextWrite(securityContext)
                        .block())
                .isEqualTo("allowed-post");
        StepVerifier.create(selected.getExecutor()
                        .execute(context(Map.of("name", "other-post")))
                        .contextWrite(securityContext))
                .expectError(AccessDeniedException.class)
                .verify();
        assertThat(calls).hasValue(1);

        effectiveRoles.set(Flux.empty());
        StepVerifier.create(selected.getExecutor()
                        .execute(context(Map.of("name", "allowed-post")))
                        .contextWrite(securityContext))
                .expectError(AccessDeniedException.class)
                .verify();
        assertThat(service.resolveAllTools().contextWrite(securityContext).block())
                .isEmpty();
        assertThat(calls).hasValue(1);
    }

    @Test
    void permissionChecksUseEachSubscribersTrustedSecurityContextWithoutCachingDecisions() {
        var observed = new ArrayList<org.springframework.security.core.Authentication>();
        var authentication = new UsernamePasswordAuthenticationToken("allowed", "unused");
        Supplier<Mono<Boolean>> access = () -> ReactiveSecurityContextHolder.getContext()
                .map(context -> {
                    observed.add(context.getAuthentication());
                    return context.getAuthentication() == authentication;
                })
                .defaultIfEmpty(false);
        var checker = mock(ToolPermissionChecker.class);
        when(checker.canResolve(any())).thenAnswer(ignored -> access.get());
        when(checker.canExecute(any(), any())).thenAnswer(ignored -> access.get());
        providers.set(Flux.just(provider(protectedTool("caller"))));
        var service = service(checker);

        assertThat(service.resolveAllTools().block()).isEmpty();
        var selected = service.resolveTools(List.of("demo/caller"))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                .block()
                .getFirst();
        assertThat(selected.getExecutor()
                        .execute(context(Map.of()))
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                        .block())
                .isEqualTo(Map.of("name", "caller"));
        var other = new UsernamePasswordAuthenticationToken("other", "unused");
        StepVerifier.create(selected.getExecutor()
                        .execute(context(Map.of("user", "allowed")))
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(other)))
                .expectError(AccessDeniedException.class)
                .verify();
        assertThat(service.resolveTools(List.of("demo/caller")).block()).isEmpty();
        assertThat(observed).containsExactly(authentication, authentication, other);
    }

    @Test
    void unavailablePermissionCheckerFailsClosedForBothBehaviors() {
        providers.set(Flux.just(provider(
                protectedTool("hidden"),
                protectedTool("visible").toBuilder()
                        .permissionDeniedBehavior(ToolPermissionDeniedBehavior.DENY_ON_EXECUTE)
                        .build(),
                tool("open"))));
        var service = service();
        assertThat(service.resolveAllTools().block())
                .extracting(ToolDefinition::getName)
                .satisfiesExactly(
                        name -> assertThat(name).startsWith("open_"),
                        name -> assertThat(name).startsWith("visible_"));
        var selected = service.resolveTools(List.of("demo/visible")).block().getFirst();
        StepVerifier.create(selected.getExecutor().execute(context(Map.of())))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void persistedDisablePreventsAnAlreadyResolvedExecutor() {
        var states = new AtomicReference<Map<String, Boolean>>(Map.of());
        var store = mock(ToolSettingsStore.class);
        when(store.enabledStates()).thenAnswer(ignored -> Mono.just(states.get()));
        when(store.setEnabled("demo/managed", false))
                .thenReturn(Mono.fromRunnable(() -> states.set(Map.of("demo/managed", false))));
        providers.set(Flux.just(provider(tool("managed"))));
        var service = new AiToolServiceImpl(extensionGetter, pluginManager, List.of(), Duration.ofSeconds(5), store);
        var selected = service.resolveTools(List.of("demo/managed")).block().getFirst();

        assertThat(service.updateEnabled("demo/managed", false).block().status())
                .isEqualTo("DISABLED");
        StepVerifier.create(selected.getExecutor().execute(context(Map.of())))
                .expectErrorMessage("Tool is no longer ready: demo/managed (DISABLED)")
                .verify();
    }

    @Test
    void becomingUnconfiguredPreventsAnAlreadyResolvedExecutor() {
        providers.set(Flux.just(provider(tool("managed"))));
        var service = service();
        var selected = service.resolveTools(List.of("demo/managed")).block().getFirst();
        var unconfigured = ToolRegistration.builder()
                .definition(tool("managed").getDefinition())
                .configured(false)
                .build();
        providers.set(Flux.just(provider(unconfigured)));

        StepVerifier.create(selected.getExecutor().execute(context(Map.of())))
                .expectErrorMessage("Tool is no longer ready: demo/managed (UNCONFIGURED)")
                .verify();
    }

    @Test
    void configurationCallbacksRemainAccessibleForDisabledUnconfiguredTools() {
        var values = new AtomicReference<Map<String, Object>>(Map.of());
        var store = mock(ToolSettingsStore.class);
        when(store.enabledStates()).thenReturn(Mono.just(Map.of("demo/configurable", false)));
        var configuration = ToolConfiguration.builder()
                .schema(List.of(Map.of("$formkit", "secret", "name", "secretName")))
                .values(() -> Mono.just(values.get()))
                .save(next -> Mono.fromRunnable(() -> values.set(next)))
                .build();
        var registration = ToolRegistration.builder()
                .definition(tool("configurable").getDefinition())
                .configured(false)
                .configuration(configuration)
                .build();
        providers.set(Flux.just(provider(registration)));
        var service = new AiToolServiceImpl(extensionGetter, pluginManager, List.of(), Duration.ofSeconds(5), store);

        assertThat(service.getConfiguration("demo/configurable").block()).isEmpty();
        assertThat(service.updateConfiguration("demo/configurable", Map.of("secretName", "ref"))
                        .block())
                .containsEntry("secretName", "ref");
        var descriptor = service.listTools().blockFirst();
        assertThat(descriptor.configurationSchema().getFirst()).containsEntry("$formkit", "secret");
        assertThat(descriptor.status()).isEqualTo("DISABLED");
        StepVerifier.create(service.updateConfiguration("demo/configurable", null))
                .expectError(IllegalArgumentException.class)
                .verify();
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
                .expectErrorMessage("Tool is no longer available: demo/write")
                .verify();
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
        registration.getDefinition().setInputSchema(Map.of("type", "object", "required", List.of("name")));

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
                .toolCallId("call-1")
                .toolName("generated-name")
                .input(Map.of())
                .requestContext(Map.of("trusted-user", "admin"))
                .cancellationToken(mock(CancellationToken.class))
                .build();
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
        assertThat(selected.getExecutor()
                        .execute(context)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                        .block())
                .isEqualTo("trusted-admin");
    }

    @Test
    void executorValidatesInputsAndOutputsThroughExistingRuntimeValidator() {
        var calls = new AtomicInteger();
        var registration = tool("typed");
        registration
                .getDefinition()
                .setInputSchema(Map.of(
                        "type",
                        "object",
                        "properties",
                        Map.of("number", Map.of("type", "integer")),
                        "required",
                        List.of("number")));
        registration.getDefinition().setOutputSchema(Map.of("type", "integer"));
        registration.getDefinition().setExecutor(context -> {
            calls.incrementAndGet();
            return Mono.just("unexpected result");
        });
        providers.set(Flux.just(provider(registration)));
        var selected = service().resolveTools(List.of("demo/typed")).block().getFirst();

        StepVerifier.create(selected.getExecutor().execute(context(Map.of("number", "string"))))
                .expectError(StructuredOutputValidationException.class)
                .verify();
        assertThat(calls.get()).isZero();
        StepVerifier.create(selected.getExecutor().execute(context(Map.of("number", 1))))
                .expectError(StructuredOutputValidationException.class)
                .verify();
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

    @Test
    void recognizesOnlyExactStandardCategoriesAndPreservesProvidedTags() {
        var categories = List.of("content", "web", "media", "site", "utility", "other");
        var registrations = categories.stream()
                .map(category -> ToolRegistration.builder()
                        .category(category)
                        .tags(List.of("existing", "原标签"))
                        .definition(tool(category).getDefinition())
                        .build())
                .toArray(ToolRegistration[]::new);
        providers.set(Flux.just(provider(registrations)));

        var tools = service().listTools().collectList().block();

        assertThat(tools).hasSize(categories.size());
        for (var descriptor : tools) {
            assertThat(descriptor.category()).isEqualTo(descriptor.localName());
            assertThat(descriptor.tags()).containsExactly("existing", "原标签");
            assertThat(descriptor.group()).isNull();
        }
    }

    @Test
    void missingOrUnknownCategoriesUseOtherWithoutChangingTags() {
        var categories = new ArrayList<String>();
        categories.add(null);
        categories.addAll(List.of(
                "",
                " ",
                "CONTENT",
                "Content",
                " content ",
                "内容",
                "分类标签",
                "网页",
                "站内",
                "附件",
                "站点",
                "实用工具",
                "Utilities",
                "custom-category"));
        var registrations = new ArrayList<ToolRegistration>();
        for (int index = 0; index < categories.size(); index++) {
            registrations.add(ToolRegistration.builder()
                    .category(categories.get(index))
                    .tags(List.of("existing", "原标签"))
                    .definition(tool("category" + index).getDefinition())
                    .build());
        }
        providers.set(Flux.just(provider(registrations.toArray(ToolRegistration[]::new))));

        assertThat(service().listTools().collectList().block())
                .hasSize(categories.size())
                .allSatisfy(descriptor -> {
                    assertThat(descriptor.category()).isEqualTo("other");
                    assertThat(descriptor.tags()).containsExactly("existing", "原标签");
                });
    }

    @Test
    void groupPresetsUseActualPluginNamespacesAndCanSpanItsProviders() {
        var group = ToolGroup.builder()
                .name("posts")
                .displayName("文章")
                .presets(List.of(ToolPreset.builder()
                        .name("read")
                        .displayName("只读")
                        .toolNames(List.of("list", "get"))
                        .build()))
                .build();
        providers.set(Flux.just(provider(grouped("list", group)), provider(grouped("get", group))));

        var tools = service().listTools().collectList().block();

        assertThat(tools).hasSize(2);
        var descriptor = tools.getFirst().group();
        assertThat(descriptor.id()).isEqualTo("demo/posts");
        assertThat(descriptor.displayName()).isEqualTo("文章");
        assertThat(tools.getLast().group()).isEqualTo(descriptor);
        assertThat(descriptor.presets().getFirst().id()).isEqualTo("demo/posts/read");
        assertThat(descriptor.presets().getFirst().toolIds()).containsExactly("demo/list", "demo/get");
    }

    @Test
    void equalLocalGroupNamesFromDifferentPluginsRemainIndependent() {
        var group = ToolGroup.builder()
                .name("shared")
                .presets(List.of(ToolPreset.builder()
                        .name("all")
                        .toolNames(List.of("read"))
                        .build()))
                .build();
        var first = new FirstProvider(() -> Flux.just(grouped("read", group)));
        var second = new SecondProvider(() -> Flux.just(grouped("read", group)));
        var firstOwner = owner("first");
        var secondOwner = owner("second");
        when(pluginManager.whichPlugin(FirstProvider.class)).thenReturn(firstOwner);
        when(pluginManager.whichPlugin(SecondProvider.class)).thenReturn(secondOwner);
        providers.set(Flux.just(first, second));

        var tools = service().listTools().collectList().block();

        assertThat(tools).extracting(tool -> tool.group().id()).containsExactly("first/shared", "second/shared");
        assertThat(tools.getFirst().group().presets().getFirst().toolIds()).containsExactly("first/read");
        assertThat(tools.getLast().group().presets().getFirst().toolIds()).containsExactly("second/read");
    }

    @Test
    void conflictingGroupMetadataRejectsAllGroupMembersButKeepsUnrelatedTools() {
        var first = ToolGroup.builder().name("conflict").displayName("First").build();
        var second = ToolGroup.builder().name("conflict").displayName("Second").build();
        providers.set(Flux.just(provider(grouped("a", first), tool("plain")), provider(grouped("b", second))));

        assertThat(service().listTools().collectList().block())
                .extracting(ToolDescriptor::id)
                .containsExactly("demo/plain");
    }

    @Test
    void presetsCannotReferenceMissingUngroupedOrDifferentGroupTools() {
        for (var reference : List.of("missing", "plain", "outside")) {
            var invalid = ToolGroup.builder()
                    .name("invalid")
                    .presets(List.of(ToolPreset.builder()
                            .name("selection")
                            .toolNames(List.of(reference))
                            .build()))
                    .build();
            providers.set(Flux.just(provider(
                    grouped("member", invalid),
                    tool("plain"),
                    grouped("outside", ToolGroup.builder().name("other").build()))));

            assertThat(service().listTools().collectList().block())
                    .extracting(ToolDescriptor::id)
                    .containsExactly("demo/outside", "demo/plain");
        }
    }

    @Test
    void groupNamesAndPresetReferencesCannotSpoofAnotherPluginNamespace() {
        var groups = List.of(
                ToolGroup.builder().name("ai-foundation/posts").build(),
                ToolGroup.builder()
                        .name("g")
                        .presets(List.of(ToolPreset.builder()
                                .name("other/read")
                                .toolNames(List.of("member"))
                                .build()))
                        .build(),
                ToolGroup.builder()
                        .name("g")
                        .presets(List.of(ToolPreset.builder()
                                .name("read")
                                .toolNames(List.of("ai-foundation/member"))
                                .build()))
                        .build());
        for (var group : groups) {
            providers.set(Flux.just(provider(grouped("member", group)), provider(tool("healthy"))));
            assertThat(service().listTools().collectList().block())
                    .extracting(ToolDescriptor::id)
                    .containsExactly("demo/healthy");
        }
    }

    @Test
    void duplicateOrEmptyPresetSelectionsRejectTheirProvider() {
        var preset =
                ToolPreset.builder().name("read").toolNames(List.of("member")).build();
        var groups = List.of(
                ToolGroup.builder().name("g").presets(List.of(preset, preset)).build(),
                ToolGroup.builder()
                        .name("g")
                        .presets(List.of(ToolPreset.builder()
                                .name("read")
                                .toolNames(List.of("member", "member"))
                                .build()))
                        .build(),
                ToolGroup.builder()
                        .name("g")
                        .presets(List.of(ToolPreset.builder()
                                .name("read")
                                .toolNames(List.of())
                                .build()))
                        .build());
        for (var group : groups) {
            providers.set(Flux.just(provider(grouped("member", group)), provider(tool("healthy"))));
            assertThat(service().listTools().collectList().block())
                    .extracting(ToolDescriptor::id)
                    .containsExactly("demo/healthy");
        }
    }

    @Test
    void unavailableMembersRemainInPresetsWhileResolveAllUsesOneReadySnapshot() {
        var group = ToolGroup.builder()
                .name("g")
                .presets(List.of(ToolPreset.builder()
                        .name("all")
                        .toolNames(List.of("ready", "disabled", "unconfigured"))
                        .build()))
                .build();
        var calls = new AtomicInteger();
        var discoveries = new AtomicInteger();
        var ready = grouped("ready", group);
        var onStart = mock(ToolInputStartCallback.class);
        ready.getDefinition().setOnInputStart(onStart);
        ready.getDefinition().setApprovalPolicy(ToolApprovalPolicy.always());
        ready.getDefinition().setExecutor(context -> {
            calls.incrementAndGet();
            return Mono.error(new SecurityException("Provider authorization denied"));
        });
        var unconfigured = ToolRegistration.builder()
                .group(group)
                .configured(false)
                .definition(tool("unconfigured").getDefinition())
                .build();
        providers.set(Flux.just(new TestProvider(() -> {
            discoveries.incrementAndGet();
            return Flux.just(ready, grouped("disabled", group), unconfigured);
        })));
        var states = new AtomicReference<Map<String, Boolean>>(Map.of("demo/disabled", false));
        var store = mock(ToolSettingsStore.class);
        when(store.enabledStates()).thenAnswer(ignored -> Mono.just(states.get()));
        var service = new AiToolServiceImpl(extensionGetter, pluginManager, List.of(), Duration.ofSeconds(5), store);

        var resolved = service.resolveAllTools().block();

        assertThat(discoveries).hasValue(1);
        assertThat(resolved).hasSize(1);
        assertThat(resolved.getFirst().getName()).startsWith("ready_");
        assertThat(resolved.getFirst().getApprovalPolicy().getMode()).isEqualTo(ToolApprovalPolicy.Mode.ALWAYS);
        assertThat(resolved.getFirst().getOnInputStart()).isSameAs(onStart);
        assertThat(calls).hasValue(0);
        StepVerifier.create(resolved.getFirst().getExecutor().execute(context(Map.of())))
                .expectErrorMessage("Provider authorization denied")
                .verify();
        assertThat(calls).hasValue(1);
        var catalog = service.listTools().collectList().block();
        assertThat(catalog)
                .hasSize(3)
                .allSatisfy(tool -> assertThat(tool.group().presets().getFirst().toolIds())
                        .containsExactly("demo/ready", "demo/disabled", "demo/unconfigured"));
        states.set(Map.of("demo/disabled", false, "demo/ready", false));
        StepVerifier.create(resolved.getFirst().getExecutor().execute(context(Map.of())))
                .expectErrorMessage("Tool is no longer ready: demo/ready (DISABLED)")
                .verify();
        assertThat(calls).hasValue(1);
    }

    @Test
    void resolveAllOnAnEmptyCatalogReturnsNoTools() {
        assertThat(service().resolveAllTools().block()).isEmpty();
    }

    private static ToolRegistration grouped(String name, ToolGroup group) {
        return ToolRegistration.builder()
                .group(group)
                .definition(tool(name).getDefinition())
                .build();
    }

    private AiToolServiceImpl service() {
        return service(List.of());
    }

    private AiToolServiceImpl service(List<AiToolProvider> builtins) {
        return new AiToolServiceImpl(extensionGetter, pluginManager, builtins, Duration.ofSeconds(5));
    }

    private AiToolServiceImpl service(ToolPermissionChecker checker) {
        return new AiToolServiceImpl(extensionGetter, pluginManager, List.of(), Duration.ofSeconds(5), null, checker);
    }

    private static ToolRegistration protectedTool(String name) {
        return tool(name).toBuilder()
                .requiredPermissions(List.of(ToolPermission.resource("demo.halo.run", name, "get")))
                .build();
    }

    private static TestProvider provider(ToolRegistration... tools) {
        return new TestProvider(() -> Flux.fromArray(tools));
    }

    private static ToolRegistration tool(String name) {
        return ToolRegistration.builder()
                .definition(ToolDefinition.builder()
                        .name(name)
                        .description("Execute " + name)
                        .inputSchema(Map.of("type", "object"))
                        .executor(context -> Mono.just(Map.of("name", name)))
                        .build())
                .build();
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
