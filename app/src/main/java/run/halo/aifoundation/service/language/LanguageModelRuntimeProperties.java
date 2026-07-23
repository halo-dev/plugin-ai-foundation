package run.halo.aifoundation.service.language;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Server-owned safety limits for language-model generation loops.
 */
@Component
@ConfigurationProperties(prefix = "halo.ai-foundation.language")
public class LanguageModelRuntimeProperties {

    static final int DEFAULT_MAX_STEPS = 32;
    static final int MAX_ALLOWED_STEPS = 64;

    private int maxSteps = DEFAULT_MAX_STEPS;

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        if (maxSteps < 1 || maxSteps > MAX_ALLOWED_STEPS) {
            throw new IllegalArgumentException("halo.ai-foundation.language.max-steps must be "
                + "between 1 and " + MAX_ALLOWED_STEPS);
        }
        this.maxSteps = maxSteps;
    }
}
