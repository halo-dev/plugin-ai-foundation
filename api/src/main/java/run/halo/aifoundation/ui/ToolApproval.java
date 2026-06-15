package run.halo.aifoundation.ui;

/**
 * Approval metadata attached to a dynamic tool part.
 *
 * @param id approval request id
 * @param approved optional approval decision
 * @param reason optional approval or denial reason
 */
public record ToolApproval(String id, Boolean approved, String reason) {
}
