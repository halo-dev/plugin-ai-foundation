package run.halo.aifoundation.embedding;

/**
 * Utility methods for embedding vectors.
 */
public final class EmbeddingUtils {

    private EmbeddingUtils() {
    }

    /**
     * Calculates cosine similarity between two embedding vectors.
     *
     * @param left first embedding vector
     * @param right second embedding vector
     * @return cosine similarity
     * @throws IllegalArgumentException when vectors are null, empty, have different lengths, or
     *                                  contain a zero-magnitude vector
     */
    public static double cosineSimilarity(float[] left, float[] right) {
        validateVectors(left, right);
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            throw new IllegalArgumentException("Embedding vectors must not have zero magnitude");
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static void validateVectors(float[] left, float[] right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("Embedding vectors must not be null");
        }
        if (left.length == 0 || right.length == 0) {
            throw new IllegalArgumentException("Embedding vectors must not be empty");
        }
        if (left.length != right.length) {
            throw new IllegalArgumentException("Embedding vectors must have the same length");
        }
    }
}
