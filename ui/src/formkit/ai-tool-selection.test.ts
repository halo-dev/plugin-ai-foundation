import type { CatalogTool } from '@/utils/tools'
import { describe, expect, it } from 'vitest'
import {
  addToolSelection,
  availablePresetIds,
  removeToolSelection,
  toggleToolPreset,
  toolGroupSelection,
  toolPresetSelection,
} from './ai-tool-selection'

const tools: CatalogTool[] = [
  { id: 'plugin/read', enabled: true, configured: true, status: 'ENABLED' },
  { id: 'plugin/write', enabled: true, configured: true, status: 'ENABLED' },
  { id: 'plugin/disabled', enabled: false, configured: true, status: 'DISABLED' },
  { id: 'plugin/unconfigured', enabled: true, configured: false, status: 'UNCONFIGURED' },
]

describe('tool selection operations', () => {
  it('distinguishes selectable/unavailable tools and derives none, partial and checked states', () => {
    expect(toolGroupSelection(tools, [])).toMatchObject({
      selectableIds: ['plugin/read', 'plugin/write'],
      unavailableCount: 2,
      selectedCount: 0,
      checked: false,
      indeterminate: false,
    })
    expect(toolGroupSelection(tools, ['plugin/disabled'])).toMatchObject({
      selectedCount: 1,
      checked: false,
      indeterminate: true,
    })
    expect(toolGroupSelection(tools, ['plugin/read', 'plugin/write'])).toMatchObject({
      selectedCount: 2,
      checked: true,
      indeterminate: false,
    })
  })

  it('adds only currently selectable IDs while retaining old choices and their order', () => {
    expect(
      addToolSelection(
        ['removed/tool', 'plugin/disabled', 'plugin/read', 'plugin/read'],
        ['plugin/write', 'plugin/unconfigured', 'unknown/tool'],
        tools,
      ),
    ).toEqual(['removed/tool', 'plugin/disabled', 'plugin/read', 'plugin/write'])
  })

  it('counts missing and unavailable preset members once without expanding future members', () => {
    const presetIds = ['plugin/read', 'plugin/disabled', 'missing/tool', 'plugin/disabled']
    expect(availablePresetIds(presetIds, tools)).toEqual({
      availableIds: ['plugin/read'],
      unavailableCount: 2,
    })
    const saved = addToolSelection([], presetIds, tools)
    const expandedCatalog = [...tools, { ...tools[0], id: 'plugin/new-member' }]
    expect(toolGroupSelection(expandedCatalog, saved).checked).toBe(false)
    expect(saved).toEqual(['plugin/read'])
  })

  it('removes only explicitly requested group members, including previously unavailable choices', () => {
    expect(
      removeToolSelection(
        ['other/tool', 'plugin/read', 'plugin/disabled', 'removed/tool'],
        ['plugin/read', 'plugin/disabled'],
      ),
    ).toEqual(['other/tool', 'removed/tool'])
  })

  it('allows clearing unavailable group selections while preserving their mixed state', () => {
    expect(toolGroupSelection(tools.slice(2), ['plugin/disabled'])).toMatchObject({
      selectableIds: [],
      unavailableCount: 2,
      checked: false,
      indeterminate: true,
      selectedCount: 1,
      canToggle: true,
      removing: true,
    })
    expect(
      toolGroupSelection(tools.slice(2), ['plugin/disabled', 'plugin/unconfigured']),
    ).toMatchObject({ checked: true, indeterminate: false, canToggle: true, removing: true })
    expect(toolGroupSelection(tools.slice(2), [])).toMatchObject({
      checked: false,
      canToggle: false,
    })
  })

  it('fills partial presets with available members and removes complete preset selections', () => {
    const preset = ['plugin/read', 'plugin/write', 'plugin/disabled']
    expect(toolPresetSelection(preset, tools, ['plugin/read'])).toMatchObject({
      checked: false,
      indeterminate: true,
      addingCount: 1,
      removing: false,
    })
    const selected = toggleToolPreset(
      ['other/tool', 'plugin/read', 'plugin/disabled'],
      preset,
      tools,
    )
    expect(selected).toEqual(['other/tool', 'plugin/read', 'plugin/disabled', 'plugin/write'])
    expect(toolPresetSelection(preset, tools, selected).checked).toBe(true)
    expect(toggleToolPreset(selected, preset, tools)).toEqual(['other/tool'])
  })

  it('keeps members of overlapping presets that are outside the toggled preset', () => {
    const catalog = [
      ...tools,
      { ...tools[0]!, id: 'plugin/create' },
      { ...tools[0]!, id: 'plugin/update' },
    ]
    const read = ['plugin/read', 'plugin/write']
    const edit = [...read, 'plugin/create', 'plugin/update']
    const partial = toggleToolPreset(['other/tool', ...edit], read, catalog)
    expect(partial).toEqual(['other/tool', 'plugin/create', 'plugin/update'])
    expect(toolPresetSelection(edit, catalog, partial)).toMatchObject({
      checked: false,
      addingCount: 2,
    })
    expect(toggleToolPreset(partial, edit, catalog)).toEqual([...partial, ...read])
  })

  it('limits preset removals to the permanent catalog scope and preserves missing IDs', () => {
    expect(
      toggleToolPreset(
        ['plugin/read', 'plugin/write', 'removed/tool'],
        ['plugin/read', 'plugin/write', 'removed/tool'],
        [tools[0]!],
      ),
    ).toEqual(['plugin/write', 'removed/tool'])
  })

  it('can cancel unavailable preset members but cannot select them again', () => {
    const preset = ['plugin/disabled', 'plugin/unconfigured']
    expect(toolPresetSelection(preset, tools, ['plugin/disabled'])).toMatchObject({
      checked: false,
      indeterminate: true,
      canToggle: true,
      removing: true,
    })
    expect(toggleToolPreset(['other/tool', 'plugin/disabled'], preset, tools)).toEqual([
      'other/tool',
    ])
    expect(toolPresetSelection(preset, tools, []).canToggle).toBe(false)
    expect(toggleToolPreset(['other/tool'], preset, tools)).toEqual(['other/tool'])
  })
})
