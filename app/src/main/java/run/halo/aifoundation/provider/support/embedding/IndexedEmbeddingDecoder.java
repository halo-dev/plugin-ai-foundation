package run.halo.aifoundation.provider.support.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.springframework.ai.embedding.Embedding;

/** Deterministically maps indexed embedding arrays without provider-specific response probing. */
public final class IndexedEmbeddingDecoder {

    private IndexedEmbeddingDecoder() {
    }

    public static List<Embedding> decode(JsonNode data,
        Function<JsonNode, float[]> vectorDecoder) {
        return decode(data, item -> item.path("index").isNumber()
            ? item.path("index").asInt() : -1, vectorDecoder);
    }

    public static List<Embedding> decode(JsonNode data, ToIntFunction<JsonNode> indexDecoder,
        Function<JsonNode, float[]> vectorDecoder) {
        var values = new ArrayList<IndexedEmbedding>();
        for (var item : data) {
            var decodedIndex = indexDecoder.applyAsInt(item);
            var index = decodedIndex < 0 ? values.size() : decodedIndex;
            values.add(new IndexedEmbedding(index, vectorDecoder.apply(item.path("embedding"))));
        }
        values.sort(Comparator.comparingInt(IndexedEmbedding::index));
        return values.stream()
            .map(value -> new Embedding(value.vector(), value.index()))
            .toList();
    }

    public static float[] floatArray(JsonNode value, String invalidShapeMessage) {
        if (!value.isArray()) {
            throw new IllegalArgumentException(invalidShapeMessage);
        }
        var vector = new float[value.size()];
        for (var index = 0; index < value.size(); index++) {
            vector[index] = (float) value.get(index).asDouble();
        }
        return vector;
    }

    public static float[] floatArrayOrBase64(JsonNode value, String invalidShapeMessage) {
        if (value.isArray()) {
            return floatArray(value, invalidShapeMessage);
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException(invalidShapeMessage);
        }
        var buffer = ByteBuffer.wrap(Base64.getDecoder().decode(value.asText()))
            .order(ByteOrder.LITTLE_ENDIAN);
        var vector = new float[buffer.remaining() / Float.BYTES];
        for (var index = 0; index < vector.length; index++) {
            vector[index] = buffer.getFloat();
        }
        return vector;
    }

    private record IndexedEmbedding(int index, float[] vector) {
    }
}
