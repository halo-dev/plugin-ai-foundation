package run.halo.aifoundation.provider.protocol.chatcompletions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;

class ChatCompletionsStreamUsageTest {
    @Test
    void absentStreamOptionsKeepUsageDefaultButExplicitEmptyOptionsOmitTheExtension() {
        var encoder = new ChatCompletionsRequestEncoder(mock(ChatCompletionsProfile.class));
        var defaults = ChatCompletionsOptions.builder().model("model").build();
        assertThat(encoder.encode(new Prompt("Hello"), defaults, true))
            .containsEntry("stream_options", Map.of("include_usage", true));
        var constrained = defaults.mutate()
            .streamOptions(new ChatCompletionsOptions.StreamOptions(null, null, null)).build();
        assertThat(encoder.encode(new Prompt("Hello"), constrained, true))
            .doesNotContainKey("stream_options").containsEntry("stream", true);
    }
}