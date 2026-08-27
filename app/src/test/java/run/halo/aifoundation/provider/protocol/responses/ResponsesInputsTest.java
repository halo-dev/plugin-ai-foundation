package run.halo.aifoundation.provider.protocol.responses;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResponsesInputsTest {

    @Test
    void inspectsUntypedContainerObjectsWithoutTreatingNullAsAType() {
        var input = List.of(Map.of("role", "user", "content",
            List.of(Map.of("type", "input_image"))));

        assertThat(ResponsesInputs.containsType(input, Set.of("input_image"))).isTrue();
        assertThat(ResponsesInputs.containsType(input, Set.of("input_file"))).isFalse();
    }
}
