package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.provider.aihubmix.AiHubMixProvider;
import run.halo.aifoundation.provider.deepseek.DeepSeekProvider;
import run.halo.aifoundation.provider.ernie.ErnieProvider;
import run.halo.aifoundation.provider.gitee.GiteeProvider;
import run.halo.aifoundation.provider.mimo.MiMoProvider;
import run.halo.aifoundation.provider.minimax.MiniMaxProvider;
import run.halo.aifoundation.provider.ollama.OllamaProvider;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.siliconflow.SiliconFlowProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;
import run.halo.aifoundation.provider.zhipu.ZhiPuProvider;
import run.halo.aifoundation.schema.OutputSpec;

class ProviderMessagesStructuredOutputContractTest {

    @Test
    void messagesAdaptersWithoutDocumentedNativeFormatUsePromptValidation() {
        var request = GenerateTextRequest.builder()
            .prompt("Generate JSON")
            .output(OutputSpec.object(Map.of("type", "object")))
            .build();

        for (var contract : promptOnlyContracts()) {
            var providerOptions = contract.provider()
                .languageModelProviderOptions(contract.adapterType());
            var chatOptions = (ChatCompletionsOptions) providerOptions
                .structuredOutputChatOptionsFactory().build(request);

            assertThat(providerOptions.structuredOutputSupport())
                .as(contract.providerType())
                .isEqualTo(StructuredOutputSupport.PROMPT_ONLY);
            assertThat(providerOptions.nativeStrictToolSchemas())
                .as(contract.providerType())
                .isFalse();
            assertThat(chatOptions.getResponseFormat())
                .as(contract.providerType())
                .isNull();
        }
    }

    private List<MessagesContract> promptOnlyContracts() {
        return List.of(
            new MessagesContract("aihubmix", new AiHubMixProvider(),
                AdapterType.AIHUBMIX_MESSAGES),
            new MessagesContract("deepseek", new DeepSeekProvider(),
                AdapterType.DEEPSEEK_MESSAGES),
            new MessagesContract("ernie", new ErnieProvider(), AdapterType.ERNIE_MESSAGES),
            new MessagesContract("gitee", new GiteeProvider(), AdapterType.GITEE_MESSAGES),
            new MessagesContract("mimo", new MiMoProvider(), AdapterType.MIMO_MESSAGES),
            new MessagesContract("minimax", new MiniMaxProvider(), AdapterType.MINIMAX_MESSAGES),
            new MessagesContract("ollama", new OllamaProvider(), AdapterType.OLLAMA_MESSAGES),
            new MessagesContract("siliconflow", new SiliconFlowProvider(),
                AdapterType.SILICONFLOW_MESSAGES),
            new MessagesContract("zhipuai", new ZhiPuProvider(), AdapterType.ZHIPU_MESSAGES)
        );
    }

    private record MessagesContract(
        String providerType,
        AiProviderType provider,
        AdapterType adapterType
    ) {
    }
}
