package run.halo.aifoundation.service.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.agent.Agent;
import run.halo.aifoundation.agent.AgentCall;
import run.halo.aifoundation.agent.AgentOptions;
import run.halo.aifoundation.tool.ToolDefinition;
import run.halo.aifoundation.ui.UIMessage;
import run.halo.aifoundation.ui.UIMessageChatHandlers;
import run.halo.aifoundation.ui.UIMessageChatRequest;
import run.halo.aifoundation.ui.UIMessageChatTrigger;
import run.halo.aifoundation.ui.UIMessageChunk;
import run.halo.aifoundation.ui.UIMessageChunkType;
import run.halo.aifoundation.ui.UIMessageParts;
import run.halo.aifoundation.ui.UIMessageRole;

class AgentLanguageModelIntegrationTest extends LanguageModelTestSupport {

    @Test
    void generateRunsToolLoopThroughProductionLanguageModel() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3),
            chatResponse("It is 22C.", "stop", 4, 5)
        );
        var executions = new AtomicInteger();
        var agent = weatherAgent(chatModel, executions);

        StepVerifier.create(agent.generate(AgentCall.prompt("Weather in SF?")))
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("It is 22C.");
                assertThat(result.getSteps()).hasSize(2);
                assertThat(result.getToolCalls()).singleElement()
                    .satisfies(call -> assertThat(call.getToolName()).isEqualTo("weather"));
                assertThat(result.getToolResults()).singleElement()
                    .satisfies(toolResult -> assertThat(toolResult.getResult())
                        .isEqualTo(Map.of("location", "SF", "temperature", 22)));
                assertThat(result.getToolErrors()).isEmpty();
            })
            .verifyComplete();

        assertThat(executions).hasValue(1);
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void streamRunsToolLoopOnceAndExposesConsistentProjections() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3)),
            Flux.just(chatResponse("It is 22C.", "stop", 4, 5))
        );
        var executions = new AtomicInteger();
        var agent = weatherAgent(chatModel, executions);
        var stream = agent.stream(AgentCall.prompt("Weather in SF?"));

        StepVerifier.create(stream.textStream())
            .expectNext("It is 22C.")
            .verifyComplete();
        StepVerifier.create(stream.result())
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("It is 22C.");
                assertThat(result.getSteps()).hasSize(2);
                assertThat(result.getToolResults()).hasSize(1);
                assertThat(result.getToolErrors()).isEmpty();
            })
            .verifyComplete();

        assertThat(executions).hasValue(1);
        verify(chatModel, times(2)).stream(any(Prompt.class));
    }

    @Test
    void uiMessageHandlerServesProductionAgentToolStream() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
            Flux.just(toolCallResponse("call_1", "weather", "{\"location\":\"SF\"}", 2, 3)),
            Flux.just(chatResponse("It is 22C.", "stop", 4, 5))
        );
        var executions = new AtomicInteger();
        var agent = weatherAgent(chatModel, executions);
        var request = new UIMessageChatRequest<Void>("chat-1", List.of(
            new UIMessage<>("user-1", UIMessageRole.USER,
                List.of(UIMessageParts.text("text-1", "Weather in SF?")), null)
        ), UIMessageChatTrigger.SUBMIT_MESSAGE, null);

        var chat = UIMessageChatHandlers.streamAgent(agent, request, null);
        var chunks = chat.response().stream().collectList().block();
        var finish = chat.finish().block();

        assertThat(chunks).extracting(UIMessageChunk::type)
            .contains(
                UIMessageChunkType.TOOL_INPUT_AVAILABLE,
                UIMessageChunkType.TOOL_OUTPUT_AVAILABLE,
                UIMessageChunkType.TEXT_DELTA,
                UIMessageChunkType.FINISH
            );
        assertThat(finish.responseMessage().text()).isEqualTo("It is 22C.");
        assertThat(finish.messages()).hasSize(2);
        assertThat(executions).hasValue(1);
        verify(chatModel, times(2)).stream(any(Prompt.class));
    }

    private Agent<Void> weatherAgent(ChatModel chatModel, AtomicInteger executions) {
        var model = languageModel(chatModel, "openai");
        var weather = ToolDefinition.builder()
            .name("weather")
            .description("Get weather")
            .inputSchema(weatherInputSchema())
            .executor(context -> {
                executions.incrementAndGet();
                return Mono.just(Map.of(
                    "location", context.getInput().get("location"),
                    "temperature", 22
                ));
            })
            .build();
        return Agent.create(AgentOptions.forModel(model)
            .instructions("Use the weather tool, then answer the user.")
            .tools(List.of(weather))
            .build());
    }
}
