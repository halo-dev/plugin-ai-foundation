package run.halo.aifoundation.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;

class UIMessageSharedFixtureTest {

    @ParameterizedTest
    @ValueSource(strings = {"data-parts", "tool-lifecycle", "terminal-states"})
    void javaReaderReducesSharedFixtures(String name) {
        var fixture = UIMessageFixtureReader.read(name);
        var chunks = fixture.chunks().stream()
            .map(UIMessageTransportCodec::chunkFromMap)
            .toList();
        var result = UIMessageStreamReader.read(new UIMessageStream(Flux.fromIterable(chunks)));

        var message = result.responseMessage().block();
        var terminal = result.finish().block();

        assertThat(UIMessageTransportCodec.messageToMap(message))
            .isEqualTo(fixture.expectedMessage());
        assertThat(terminalToMap(terminal)).containsAllEntriesOf(fixture.expectedTerminal());
    }

    private static Map<String, Object> terminalToMap(UIMessageStreamTerminal terminal) {
        var map = new LinkedHashMap<String, Object>();
        if (terminal.finishReason() != null) {
            map.put("finishReason", terminal.finishReason().name().toLowerCase().replace('_', '-'));
        }
        if (terminal.aborted()) {
            map.put("aborted", true);
        }
        if (terminal.errorText() != null) {
            map.put("errorText", terminal.errorText());
        }
        return Map.copyOf(map);
    }
}
