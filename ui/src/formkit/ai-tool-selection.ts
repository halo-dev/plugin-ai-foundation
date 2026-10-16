import { toolSelectable, type CatalogTool } from '@/utils/tools'

export function toolGroupSelection(tools: CatalogTool[], selectedIds: readonly string[]) {
  const ids = [...new Set(tools.map((tool) => tool.id).filter((id): id is string => !!id))]
  const selectableIds = [...new Set(tools.filter(toolSelectable).map((tool) => tool.id!))]
  const selected = new Set(selectedIds)
  const selectedCount = ids.filter((id) => selected.has(id)).length
  const checked = selectableIds.length
    ? selectableIds.every((id) => selected.has(id))
    : ids.length > 0 && selectedCount === ids.length
  return {
    ids,
    selectableIds,
    unavailableCount: ids.length - selectableIds.length,
    selectedCount,
    checked,
    indeterminate: !checked && selectedCount > 0,
    canToggle: selectableIds.length > 0 || selectedCount > 0,
    removing: checked || (selectableIds.length === 0 && selectedCount > 0),
  }
}

export function availablePresetIds(ids: readonly string[], catalog: CatalogTool[]) {
  const selectable = new Set(catalog.filter(toolSelectable).map((tool) => tool.id))
  const uniqueIds = [...new Set(ids)]
  const availableIds = uniqueIds.filter((id) => selectable.has(id))
  return { availableIds, unavailableCount: uniqueIds.length - availableIds.length }
}

export function toolPresetSelection(
  ids: readonly string[],
  catalog: CatalogTool[],
  selectedIds: readonly string[],
) {
  const members = new Set(ids)
  const group = toolGroupSelection(
    catalog.filter((tool) => !!tool.id && members.has(tool.id)),
    selectedIds,
  )
  const availability = availablePresetIds(ids, catalog)
  return {
    ...group,
    ...availability,
    addingCount: availability.availableIds.filter((id) => !selectedIds.includes(id)).length,
  }
}

export function toggleToolPreset(
  selectedIds: readonly string[],
  presetIds: readonly string[],
  catalog: CatalogTool[],
) {
  const preset = toolPresetSelection(presetIds, catalog, selectedIds)
  if (!preset.canToggle) return [...new Set(selectedIds)]
  return preset.removing
    ? removeToolSelection(selectedIds, preset.ids)
    : addToolSelection(selectedIds, preset.availableIds, catalog)
}

export function addToolSelection(
  selectedIds: readonly string[],
  addedIds: readonly string[],
  catalog: CatalogTool[],
) {
  const { availableIds } = availablePresetIds(addedIds, catalog)
  return [...new Set([...selectedIds, ...availableIds])]
}

export function removeToolSelection(selectedIds: readonly string[], removedIds: readonly string[]) {
  const removed = new Set(removedIds)
  return [...new Set(selectedIds)].filter((id) => !removed.has(id))
}
