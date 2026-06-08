package run.halo.aifoundation.ui;

/**
 * Factory methods for UI message cancellation helpers.
 */
public final class UIMessageCancellations {

    private UIMessageCancellations() {
    }

    /**
     * Creates a caller-owned cancellation helper.
     *
     * @return cancellation helper
     */
    public static UIMessageCancellation create() {
        return new UIMessageCancellation();
    }
}
