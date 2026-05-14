package run.halo.aifoundation;

/**
 * Static locator for AI Foundation services.
 * The ai-foundation plugin registers its implementation on startup;
 * consumer plugins call {@link #getModelService()} to obtain it
 * without relying on Spring bean injection across plugin contexts.
 */
public final class AiServices {

    private static volatile AiModelService modelService;

    private AiServices() {}

    public static AiModelService getModelService() {
        var service = modelService;
        if (service == null) {
            throw new IllegalStateException(
                "AI Foundation plugin is not started or AiModelService is not available");
        }
        return service;
    }

    public static void setModelService(AiModelService service) {
        modelService = service;
    }

    public static void clear() {
        modelService = null;
    }
}
