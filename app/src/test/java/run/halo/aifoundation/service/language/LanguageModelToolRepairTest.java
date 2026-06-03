package run.halo.aifoundation.service.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.StopCondition;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolCallRepairContext;
import run.halo.aifoundation.tool.ToolCallRepairResult;

class LanguageModelToolRepairTest extends LanguageModelTestSupport {

    @Test
    void generateTextRepairsInvalidToolInputExecutesAndContinues() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"city\":\"SF\"}", 2, 3),
            chatResponse("It is 22C.", "stop", 4, 5)
        );
        var model = new LanguageModelImpl(chatModel, "openai");
        var repairContext = new AtomicReference<ToolCallRepairContext>();

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .context(Map.of("tenant", "demo"))
            .tools(List.of(repairableWeatherTool(context -> Mono.just(Map.of("temperature", 22)))))
            .toolCallRepair(context -> {
                repairContext.set(context);
                return Mono.just(ToolCallRepairResult.repaired(ToolCall.builder()
                    .input(Map.of("location", context.getToolCall().getInput().get("city")))
                    .build()));
            })
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("It is 22C.");
                assertThat(result.getWarnings()).extracting("code")
                    .contains("tool-call-repaired");
                assertThat(result.getToolErrors()).isEmpty();
                assertThat(result.getToolResults()).hasSize(1);
                assertThat(result.getSteps().getFirst().getResponseMessages().stream()
                    .flatMap(message -> message.getContent().stream())
                    .map(part -> part.getType()))
                    .containsExactly(PartType.TOOL_CALL, PartType.TOOL_RESULT);
            })
            .verifyComplete();

        assertThat(repairContext.get().getRequestContext()).containsEntry("tenant", "demo");
        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(captor.capture());
        assertThat(captor.getAllValues().get(1).getInstructions().stream()
            .filter(AssistantMessage.class::isInstance)
            .map(AssistantMessage.class::cast)
            .flatMap(message -> message.getToolCalls().stream())
            .map(AssistantMessage.ToolCall::arguments))
            .anySatisfy(arguments -> assertThat(arguments).contains("\"location\":\"SF\""));
    }

    @Test
    void generateTextRepairAllowsNullValuesInRequestContext() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(
            toolCallResponse("call_1", "weather", "{\"city\":\"SF\"}", 2, 3),
            chatResponse("It is 22C.", "stop", 4, 5)
        );
        var model = new LanguageModelImpl(chatModel, "openai");
        var repairs = new AtomicInteger();
        var requestContext = new LinkedHashMap<String, Object>();
        requestContext.put("tenant", null);

        var request = GenerateTextRequest.builder()
            .prompt("Weather in SF?")
            .context(requestContext)
            .tools(List.of(repairableWeatherTool(context -> Mono.just(Map.of("temperature", 22)))))
            .toolCallRepair(context -> {
                repairs.incrementAndGet();
                return Mono.just(ToolCallRepairResult.repaired(ToolCall.builder()
                    .input(Map.of("location", context.getToolCall().getInput().get("city")))
                    .build()));
            })
            .stopWhen(StopCondition.stepCountIs(2))
            .build();

        StepVerifier.create(model.generateText(request))
            .assertNext(result -> {
                assertThat(result.getText()).isEqualTo("It is 22C.");
                assertThat(result.getToolErrors()).isEmpty();
                assertThat(result.getWarnings()).extracting("code")
                    .contains("tool-call-repaired")
                    .doesNotContain("tool-call-repair-failed");
            })
            .verifyComplete();

        assertThat(repairs).hasValue(1);
    }
}
