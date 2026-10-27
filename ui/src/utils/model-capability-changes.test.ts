import type { AiModel } from '@/api/generated'
import { describe, expect, it } from 'vitest'
import { reconcileModelCapabilities } from './model-capability-changes'

function catalogSpec(): AiModel['spec'] {
  return {
    displayName: 'Example',
    enabled: true,
    providerName: 'openai',
    modelId: 'example',
    modelType: 'language',
    adapterType: 'openai-chat',
    features: ['vision', 'audio-input'],
    capabilities: {
      language: {
        imageInput: true,
        fileInput: true,
        inputMediaTypes: ['image/png', 'audio/wav', 'audio/mpeg'],
        inputSources: ['data'],
      },
    },
    capabilitySources: { language: 'unknown' },
    catalogMatch: {
      providerId: 'openai',
      modelId: 'example',
      fields: [
        'features.vision',
        'features.audio-input',
        'language.imageInput',
        'language.fileInput',
        'language.inputMediaTypes',
        'language.inputSources',
      ],
    },
  }
}

describe('catalog capability edits', () => {
  it('preserves the existing edit behavior for models without catalog metadata', () => {
    const previous = catalogSpec()
    delete previous.catalogMatch
    previous.features = []
    previous.capabilities = undefined
    const next: AiModel['spec'] = { ...previous, features: ['vision'] }
    expect(reconcileModelCapabilities(previous, next)).toEqual(next)
  })

  it('changes vision without broadening the remaining audio formats', () => {
    const previous = catalogSpec()
    const original = structuredClone(previous)
    const next = reconcileModelCapabilities(previous, { ...previous, features: ['audio-input'] })
    expect(next.capabilities?.language?.inputMediaTypes).toEqual(['audio/wav', 'audio/mpeg'])
    expect(next.catalogMatch?.fields).toContain('language.fileInput')
    expect(next.catalogMatch?.fields).toContain('language.inputMediaTypes')
    expect(next.capabilitySources?.language).toBe('manual')
    expect(previous).toEqual(original)
  })

  it('can disable and re-enable vision while retaining the other modality', () => {
    const previous = catalogSpec()
    const disabled = reconcileModelCapabilities(previous, {
      ...previous,
      features: ['audio-input'],
    })
    const enabled = reconcileModelCapabilities(disabled, {
      ...disabled,
      features: ['audio-input', 'vision'],
    })
    expect(enabled.capabilities?.language?.imageInput).toBe(true)
    expect(enabled.capabilities?.language?.inputMediaTypes).toEqual([
      'audio/wav',
      'audio/mpeg',
      'image/png',
      'image/jpeg',
    ])
  })

  it('preserves untouched empty coverage when the form omits empty lists and imageInput', () => {
    const previous = catalogSpec()
    previous.features = []
    previous.capabilities = {
      language: { imageInput: false, fileInput: false, inputMediaTypes: [], inputSources: [] },
    }
    const next = reconcileModelCapabilities(previous, {
      ...previous,
      displayName: 'Renamed',
      capabilities: { language: { fileInput: false } },
      capabilitySources: { language: 'manual' },
    })
    expect(next.capabilities?.language).toEqual(previous.capabilities.language)
    expect(next.capabilitySources?.language).toBe('unknown')
    expect(next.catalogMatch).toEqual(previous.catalogMatch)
  })

  it('enables media on a text-only catalog snapshot with empty coverage', () => {
    const previous = catalogSpec()
    previous.features = []
    previous.capabilities = {
      language: { imageInput: false, fileInput: false, inputMediaTypes: [], inputSources: [] },
    }
    const next = reconcileModelCapabilities(previous, {
      ...previous,
      features: ['vision'],
      capabilities: { language: { fileInput: false } },
    })
    expect(next.capabilities?.language).toMatchObject({
      imageInput: true,
      inputMediaTypes: ['image/png', 'image/jpeg'],
      inputSources: ['data'],
    })
    expect(next.catalogMatch?.fields).not.toContain('language.imageInput')
    expect(next.catalogMatch?.fields).not.toContain('language.inputSources')
  })

  it('keeps manual MIME and source edits instead of replacing them with feature defaults', () => {
    const previous = catalogSpec()
    previous.features = ['audio-input']
    const next = reconcileModelCapabilities(previous, {
      ...previous,
      features: ['audio-input', 'vision'],
      capabilities: {
        language: {
          ...previous.capabilities?.language,
          inputMediaTypes: ['image/webp', 'audio/wav'],
          inputSources: ['url'],
        },
      },
    })
    expect(next.capabilities?.language?.inputMediaTypes).toEqual(['image/webp', 'audio/wav'])
    expect(next.capabilities?.language?.inputSources).toEqual(['url'])
  })

  it('clears adapter-specific catalog fields while preserving explicit manual edits', () => {
    const previous = catalogSpec()
    previous.adapterType = 'openai-responses'
    previous.capabilities!.language!.inputMediaTypes = ['application/pdf']
    const next = reconcileModelCapabilities(previous, {
      ...previous,
      adapterType: 'openai-chat',
      capabilities: { language: { ...previous.capabilities?.language, reasoningHistory: false } },
    })
    expect(next.capabilities?.language?.inputMediaTypes).toBeUndefined()
    expect(next.capabilities?.language?.fileInput).toBeUndefined()
    expect(next.capabilities?.language?.reasoningHistory).toBe(false)
    expect(next.catalogMatch?.fields).toEqual(['features.vision', 'features.audio-input'])
  })

  it('keeps the import identity after all fields become manual', () => {
    const previous = catalogSpec()
    previous.catalogMatch!.fields = ['language.inputMediaTypes']
    const next = reconcileModelCapabilities(previous, {
      ...previous,
      capabilities: {
        language: { ...previous.capabilities?.language, inputMediaTypes: ['audio/wav'] },
      },
    })
    expect(next.catalogMatch).toEqual({ ...previous.catalogMatch, fields: [] })
  })

  it('still removes catalog PDF after a vision override and a later adapter change', () => {
    const previous = catalogSpec()
    previous.adapterType = 'openai-responses'
    previous.features = ['vision']
    previous.capabilities!.language!.inputMediaTypes = ['image/png', 'application/pdf']
    const disabled = reconcileModelCapabilities(previous, { ...previous, features: [] })
    const switched = reconcileModelCapabilities(disabled, {
      ...disabled,
      adapterType: 'openai-chat',
    })
    expect(switched.capabilities?.language?.inputMediaTypes || []).not.toContain('application/pdf')
  })
})
