package run.halo.aifoundation.service.language;

import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
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

public record LanguageModelRuntimeComposition(
    String providerType,
    LanguageModelProviderOptions providerOptions,
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
        return create(providerType, null, providerOptions, runtimeSupport);
    }

    public static LanguageModelRuntimeComposition create(String providerType, String modelId,
        LanguageModelProviderOptions providerOptions, LanguageModelRuntimeSupport runtimeSupport) {
        var resolvedOptions = providerOptions != null
            ? providerOptions
            : LanguageModelProviderOptions.defaults();
        var requestValidator = new LanguageModelRequestValidator(providerType,
            resolvedOptions.reasoningHistorySupported());
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
        return new LanguageModelRuntimeComposition(providerType, resolvedOptions, requestValidator,
            messageMapper, messageHistoryAssembler, chatOptionsBuilder, responseMapper,
            reasoningExtractor, toolCallMapper, structuredOutputHandler, toolExecutor,
            toolStepCoordinator, new ToolApprovalResolver(), runtimeSupport);
    }
}
