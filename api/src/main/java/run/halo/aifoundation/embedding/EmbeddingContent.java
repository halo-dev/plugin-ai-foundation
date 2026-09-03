package run.halo.aifoundation.embedding;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.aifoundation.media.DataContent;

/**
 * One typed part of a provider-neutral multimodal embedding input.
 *
 * <p>Unlike {@link EmbeddingRequest#getInputs() text inputs}, these parts retain their media type
 * for a provider-native multimodal request. A provider may produce one joint vector or one vector
 * per content item; the returned embedding list preserves the provider's documented semantics.
 */
@Data
@Builder(buildMethodName = "uncheckedBuild")
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingContent {

    private Type type;

    /** Text payload. Set only when {@link #type} is {@link Type#TEXT}. */
    private String text;

    /** Image or video payload. Set only for the matching media type. */
    private DataContent media;

    public static EmbeddingContent text(String text) {
        return builder().type(Type.TEXT).text(text).build();
    }

    public static EmbeddingContent image(DataContent image) {
        return builder().type(Type.IMAGE).media(image).build();
    }

    public static EmbeddingContent video(DataContent video) {
        return builder().type(Type.VIDEO).media(video).build();
    }

    private EmbeddingContent validate() {
        if (type == null) {
            throw new IllegalArgumentException("Embedding content type must not be null");
        }
        if (type == Type.TEXT) {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("Embedding text content must not be blank");
            }
            if (media != null) {
                throw new IllegalArgumentException("Embedding text content must not include media");
            }
            return this;
        }
        if (media == null) {
            throw new IllegalArgumentException(
                "Embedding " + type.name().toLowerCase() + " content must include media");
        }
        if (text != null) {
            throw new IllegalArgumentException(
                "Embedding " + type.name().toLowerCase() + " content must not include text");
        }
        return this;
    }

    public enum Type {
        TEXT,
        IMAGE,
        VIDEO
    }

    /** Validating builder for {@link EmbeddingContent}. */
    public static class EmbeddingContentBuilder {
        public EmbeddingContent build() {
            return uncheckedBuild().validate();
        }
    }
}
