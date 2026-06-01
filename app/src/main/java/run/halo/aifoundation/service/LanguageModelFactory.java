package run.halo.aifoundation.service;

import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.service.model.ModelResolution;

public interface LanguageModelFactory {

    LanguageModel create(ModelResolution resolution);
}
