/**
 * Model capability snapshots, sources, and requirement types.
 *
 * <p>These types are part of the public SDK contract used by model resolution, model option
 * filtering, console-managed model profiles, and runtime validation. Unknown semantic
 * capabilities are conservative: a request that requires an unknown capability is rejected before
 * provider invocation.
 */
package run.halo.aifoundation.capability;
