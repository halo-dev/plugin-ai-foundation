package run.halo.aifoundation.provider.support;

import com.fasterxml.jackson.databind.JsonNode;

/** Shared presence semantics for Jackson tree values returned by provider APIs. */
public final class JsonNodes {

    private JsonNodes() {
    }

    public static boolean isAbsent(JsonNode node) {
        if (node == null) {
            return true;
        }
        if (node.isMissingNode()) {
            return true;
        }
        return node.isNull();
    }
}
