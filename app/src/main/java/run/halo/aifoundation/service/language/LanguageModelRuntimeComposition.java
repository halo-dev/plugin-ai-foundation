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
        return create(providerType, null, providerOptions, runtimeSupport, new MediaResourcePolicy(),
            new ModelCapabilityMatcher(), ModelCapabilities.empty(), null, null);
    }

    public static LanguageModelRuntimeComposition create(String providerType,
        LanguageModelProviderOptions providerOptions, LanguageModelRuntimeSupport runtimeSupport,
        MediaResourcePolicy mediaResourcePolicy, ModelCapabilityMatcher capabilityMatcher) {
        return create(providerType, null, providerOptions, runtimeSupport, mediaResourcePolicy,
            capabilityMatcher, ModelCapabilities.empty(), null, null);
    }

    public static LanguageModelRuntimeComposition create(String providerType, String modelId,
        LanguageModelProviderOptions providerOptions, LanguageModelRuntimeSupport runtimeSupport) {
        return create(providerType, modelId, providerOptions, runtimeSupport,
            new MediaResourcePolicy(), new ModelCapabilityMatcher(), ModelCapabilities.empty(), null,
            null);
    }

    public static LanguageModelRuntimeComposition create(String providerType, String modelId,
        LanguageModelProviderOptions providerOptions, LanguageModelRuntimeSupport runtimeSupport,
        MediaResourcePolicy mediaResourcePolicy, ModelCapabilityMatcher capabilityMatcher) {
        return create(providerType, modelId, providerOptions, runtimeSupport, mediaResourcePolicy,
            capabilityMatcher, ModelCapabilities.empty(), null, null);
    }

    public static LanguageModelRuntimeComposition create(String providerType, String modelId,
        LanguageModelProviderOptions providerOptions, LanguageModelRuntimeSupport runtimeSupport,
        MediaResourcePolicy mediaResourcePolicy, ModelCapabilityMatcher capabilityMatcher,
        ModelCapabilities modelCapabilities, String modelName, String providerName) {
        var resolvedOptions = providerOptions != null
            ? providerOptions
            : LanguageModelProviderOptions.defaults();
        var resolvedCapabilities = modelCapabilities != null
            ? modelCapabilities
            : ModelCapabilities.empty();
        var requestValidator = new LanguageModelRequestValidator(providerType,
            resolvedOptions.reasoningHistorySupported(), resolvedCapabilities, modelName,
            providerName, mediaResourcePolicy, capabilityMatcher);
        var messageMapper = new LanguageModelMessageMapper(providerType);
        var messageHistoryAssembler = new GenerationMessageHistoryAssembler(providerType,
            resolvedOptions.reasoningHistorySupported(), messageMapper);
        var chatOptionsBuilder = new LanguageModelChatOptionsBuilder(providerType, modelId,
            resolvedOptions, runtimeSupport::writeJson);
        var responseMapper = new LanguageModelResponseMapper(providerType, messageMapper);
        var reasoningExtractor =
            new ReasoningContentExtractor(providerType, responseMapper::sanitizeValue);
        var toolCallMapper = new LanguageModelToolCallMapper();
        var structuredOutputHandler =
            new LanguageModelStructuredOutputHandler(responseMapper, runtimeSupport::writeJson);
        var toolExecutor = new LanguageModelToolExecutor(
            structuredOutputHandler::validateJsonValue,
            runtimeSupport::checkCancellation,
            runtimeSupport::withToolTimeout);
        var toolStepCoordinator = new ToolStepCoordinator(toolExecutor);
        return new LanguageModelRuntimeComposition(providerType, resolvedOptions,
            resolvedCapabilities, requestValidator, messageMapper, messageHistoryAssembler,
            chatOptionsBuilder, responseMapper, reasoningExtractor, toolCallMapper,
            structuredOutputHandler, toolExecutor, toolStepCoordinator, new ToolApprovalResolver(),
            runtimeSupport);
    }
}
