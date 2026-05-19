package run.halo.aifoundation.setting;

import lombok.Data;

@Data
public class DefaultModelSlots {

    public static final String GROUP = "defaults";

    private String languageModelName;

    private String embeddingModelName;

    private String rerankModelName;

    private String imageGenerationModelName;
}
