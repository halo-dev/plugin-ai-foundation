package run.halo.aifoundation.service.language;

import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.service.capability.ModelCapabilityMatcher;
import run.halo.aifoundation.service.language.mapping.LanguageModelChatOptionsBuilder;
import run.halo.aifoundation.service.language.mapping.LanguageModelMessageMapper;
import run.halo.aifoundation.service.language.mapping.LanguageModelRequestValidator;
import run.halo.aifoundation.service.language.mapping.LanguageModelResponseMapper;
import run.halo.aifoundation.service.language.mapping.LanguageModelToolCallMapper;
import run.halo.aifoundation.service.language.reasoning.ReasoningContentExtractor;
import run.halo.aifoundation.service.language.structured.LanguageModelStructuredOutputHandler;
import run.halo.aifoundation.service.language.tool.LanguageModelToolExecutor;
import run.halo.aifoundation.service.language.tool.ToolApprovalResolver;
import run.halo.aifoundation.service.language.tool.ToolStepCoordinator;
import run.halo.aifoundation.service.media.MediaResourcePolicy;

public record LanguageModelRuntimeComposition(
    String providerType,
    LanguageModelProviderOptions providerOptions,
    ModelCapabilities modelCapabilities,
    boolean reasoningHistorySupported,
    LanguageModelRequestValidator requestValidator,
    LanguageModelMessageMapper messageMapper,
    GenerationMessageHistoryAssembler messageHistoryAssembler,
    LanguageModelChatOptionsBuilder chatOptionsBuilder,
    LanguageModelResponseMapper responseMapper,
    ReasoningContentExtractor reasoningExtractor,
    LanguageModelToolCallMapper toolCallMapper,
    LanguageModelStructuredOutputHandler structuredOutputHandler,
    LanguageModelToolExecutor toolExecutor,
    ToolStepCoordinator toolStepCoordinator,
    ToolApprovalResolver approvalResolver,
    LanguageModelRuntimeSupport runtimeSupport
) {
    public static LanguageModelRuntimeComposition create(String providerType,
        LanguageModelProviderOptions providerOptions, LanguageModelRuntimeSupport runtimeSupport) {
        return create(configuration(providerType, null, providerOptions), runtimeSupport,
            new MediaResourcePolicy(), new ModelCapabilityMatcher());
    }

    public static LanguageModelRuntimeComposition create(String providerType,
        LanguageModelProviderOptions providerOptions, LanguageModelRuntimeSupport runtimeSupport,
        MediaResourcePolicy mediaResourcePolicy, ModelCapabilityMatcher capabilityMatcher) {
        return create(configuration(providerType, null, providerOptions), runtimeSupport,
            mediaResourcePolicy, capabilityMatcher);
    }

    public static LanguageModelRuntimeComposition create(String providerType, String modelId,
        LanguageModelProviderOptions providerOptions, LanguageModelRuntimeSupport runtimeSupport) {
        return create(configuration(providerType, modelId, providerOptions), runtimeSupport,
            new MediaResourcePolicy(), new ModelCapabilityMatcher());
    }

    public static LanguageModelRuntimeComposition create(String providerType, String modelId,
        LanguageModelProviderOptions providerOptions, LanguageModelRuntimeSupport runtimeSupport,
        MediaResourcePolicy mediaResourcePolicy, ModelCapabilityMatcher capabilityMatcher) {
        return create(configuration(providerType, modelId, providerOptions), runtimeSupport,
            mediaResourcePolicy, capabilityMatcher);
    }

    public static LanguageModelRuntimeComposition create(
        LanguageModelRuntimeConfiguration configuration,
        LanguageModelRuntimeSupport runtimeSupport, MediaResourcePolicy mediaResourcePolicy,
        ModelCapabilityMatcher capabilityMatcher) {
        var context = configuration.context();
        var resolvedOptions = configuration.providerOptions();
        var resolvedCapabilities = configuration.modelCapabilities();
        var reasoningHistorySupported = reasoningHistorySupported(resolvedCapabilities,
            resolvedOptions);
        var requestValidator = new LanguageModelRequestValidator(context.providerType(),
            reasoningHistorySupported, resolvedCapabilities, context.modelName(),
            context.providerName(), mediaResourcePolicy, capabilityMatcher);
        var messageMapper = new LanguageModelMessageMapper(context.providerType());
        var messageHistoryAssembler = new GenerationMessageHistoryAssembler(context.providerType(),
            reasoningHistorySupported, messageMapper);
        var chatOptionsBuilder = new LanguageModelChatOptionsBuilder(context.providerType(),
            context.modelId(),
            resolvedOptions, runtimeSupport::writeJson);
        var responseMapper = new LanguageModelResponseMapper(context.providerType(), messageMapper);
        var reasoningExtractor =
            new ReasoningContentExtractor(context.providerType(), responseMapper::sanitizeValue);
        var toolCallMapper = new LanguageModelToolCallMapper();
        var structuredOutputHandler =
            new LanguageModelStructuredOutputHandler(responseMapper, runtimeSupport::writeJson);
        var toolExecutor = new LanguageModelToolExecutor(
            structuredOutputHandler::validateJsonValue,
            runtimeSupport::checkCancellation,
            runtimeSupport::withToolTimeout);
        var toolStepCoordinator = new ToolStepCoordinator(toolExecutor);
        return new LanguageModelRuntimeComposition(context.providerType(), resolvedOptions,
            resolvedCapabilities, reasoningHistorySupported, requestValidator, messageMapper,
            messageHistoryAssembler, chatOptionsBuilder, responseMapper, reasoningExtractor,
            toolCallMapper, structuredOutputHandler, toolExecutor, toolStepCoordinator,
            new ToolApprovalResolver(), runtimeSupport);
    }

    private static boolean reasoningHistorySupported(ModelCapabilities capabilities,
        LanguageModelProviderOptions providerOptions) {
        var language = capabilities.getLanguage();
        if (language != null && language.getReasoningHistory() != null) {
            return language.getReasoningHistory();
        }
        return providerOptions.reasoningHistorySupported();
    }

    private static LanguageModelRuntimeConfiguration configuration(String providerType,
        String modelId, LanguageModelProviderOptions providerOptions) {
        var context = run.halo.aifoundation.service.model.ModelRuntimeContext.unresolved(
            providerType, modelId, null, null,
            run.halo.aifoundation.provider.mapping.RuntimeParameterMappings.empty());
        return new LanguageModelRuntimeConfiguration(context, providerOptions,
            ModelCapabilities.empty());
    }
}
