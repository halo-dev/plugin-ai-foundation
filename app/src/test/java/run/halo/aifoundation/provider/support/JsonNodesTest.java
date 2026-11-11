package run.halo.aifoundation.provider.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;

class JsonNodesTest {

    @Test
    void distinguishesAbsentNodesFromValues() {
        assertThat(JsonNodes.isAbsent(null)).isTrue();
        assertThat(JsonNodes.isAbsent(MissingNode.getInstance())).isTrue();
        assertThat(JsonNodes.isAbsent(NullNode.getInstance())).isTrue();
        assertThat(JsonNodes.isAbsent(TextNode.valueOf(""))).isFalse();
    }
}
