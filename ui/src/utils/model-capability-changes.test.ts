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
  it('keeps catalog parameter snapshots on rename and removes them across model boundaries', () => {
    const previous = catalogSpec()
    previous.catalogParameters = {
      modelId: 'example',
      providerType: 'openai',
      adapterType: 'openai-chat',
      constraints: { temperature: false, outputTokens: 8192 },
    }
    expect(
      reconcileModelCapabilities(previous, { ...previous, displayName: 'Renamed' })
        .catalogParameters,
    ).toEqual(previous.catalogParameters)
    for (const change of [
      { modelId: 'different' },
      { providerName: 'different' },
      { adapterType: 'openai-responses' as const },
      { modelType: 'embedding' as const },
    ]) {
      expect(
        reconcileModelCapabilities(previous, { ...previous, ...change }).catalogParameters,
      ).toBeUndefined()
    }
  })

  it('preserves a newly matched profile after changing protocol or model ID', () => {
    const previous = catalogSpec()
    previous.catalogParameters = {
      modelId: previous.modelId,
      providerType: 'openai',
      providerBaseUrl: 'https://api.openai.com/v1',
      adapterType: 'openai-chat',
    }
    for (const change of [
      { adapterType: 'openai-responses' as const },
      { modelId: 'another-model' },
    ]) {
      const next = { ...previous, ...change }
      next.catalogParameters = {
        ...previous.catalogParameters,
        modelId: next.modelId,
        adapterType: next.adapterType,
        constraints: { temperature: false },
      }
      expect(
        reconcileModelCapabilities(previous, next, { provider: { providerType: 'openai' } })
          .catalogParameters,
      ).toEqual(next.catalogParameters)
    }
  })

  it('rejects fresh profiles across provider names, types, configured endpoints and custom paths', () => {
    const previous = catalogSpec()
    const next: AiModel['spec'] = {
      ...previous,
      adapterType: 'openai-responses',
      catalogParameters: {
        providerType: 'openai',
        modelId: previous.modelId,
        adapterType: 'openai-responses',
        providerBaseUrl: 'https://api.openai.com/v1',
      },
    }
    expect(
      reconcileModelCapabilities(
        previous,
        { ...next, providerName: 'another-provider' },
        {
          provider: { providerType: 'openai' },
        },
      ).catalogParameters,
    ).toBeUndefined()
    for (const provider of [
      { providerType: 'openailike' },
      { providerType: 'openai', baseUrl: 'https://other.example/v1' },
      { providerType: 'openai', chatEndpointPath: '/custom/chat' },
    ]) {
      expect(
        reconcileModelCapabilities(previous, next, { provider }).catalogParameters,
      ).toBeUndefined()
    }
    expect(
      reconcileModelCapabilities(previous, next, {
        provider: { providerType: 'openai', baseUrl: 'https://api.openai.com/v1' },
      }).catalogParameters,
    ).toEqual(next.catalogParameters)
  })

  it('does not infer a different provider type or endpoint from an unverified replacement profile', () => {
    const previous = catalogSpec()
    previous.catalogParameters = {
      providerType: 'openai',
      modelId: previous.modelId,
      adapterType: 'openai-chat',
      providerBaseUrl: 'https://api.openai.com/v1',
    }
    for (const profileChange of [
      { providerType: 'openailike' },
      { providerBaseUrl: 'https://other.example/v1' },
    ]) {
      expect(
        reconcileModelCapabilities(previous, {
          ...previous,
          adapterType: 'openai-responses',
          catalogParameters: {
            ...previous.catalogParameters,
            adapterType: 'openai-responses',
            ...profileChange,
          },
        }).catalogParameters,
      ).toBeUndefined()
    }
  })
})

describe('parameter feature provenance', () => {
  it.each([false, true])(
    'keeps imported manual %s choices above catalog constraints',
    (supported) => {
      const previous = catalogSpec()
      const features = ['tool-call', 'structured-output', 'reasoning'] as const
      previous.features = supported ? [] : [...features]
      previous.catalogMatch!.fields = features.map((feature) => `features.${feature}`)
      previous.catalogParameters = {
        providerType: 'openai',
        modelId: previous.modelId,
        adapterType: previous.adapterType,
        constraints: {
          toolCalling: !supported,
          structuredOutput: !supported,
          reasoning: { supported: !supported },
        },
        featureEvidence: {},
      }
      const imported = reconcileModelCapabilities(
        previous,
        {
          ...previous,
          features: supported ? [...features] : [],
        },
        { importing: true },
      )
      expect(imported.features).toEqual(supported ? features : [])
      expect(imported.parameterConstraints).toEqual({
        toolCalling: supported,
        structuredOutput: supported,
        reasoning: { supported },
      })
      expect(imported.catalogMatch?.fields).toEqual([])
      expect(imported.capabilitySources?.language).toBe('manual')
      expect(previous.parameterConstraints).toBeUndefined()
    },
  )

  it('removes obsolete catalog attribution when the fresh profile contains provider evidence', () => {
    const previous = catalogSpec()
    previous.features = []
    previous.capabilitySources = { language: 'remote' }
    previous.catalogMatch!.fields = ['features.tool-call']
    const next = reconcileModelCapabilities(previous, {
      ...previous,
      catalogParameters: {
        providerType: 'openai',
        modelId: previous.modelId,
        adapterType: previous.adapterType,
        constraints: { toolCalling: true },
        featureEvidence: { 'tool-call': true },
      },
    })
    expect(next.features).toContain('tool-call')
    expect(next.catalogMatch?.fields).not.toContain('features.tool-call')
    expect(next.capabilitySources?.language).toBe('remote')
  })

  it('records a manual constraint even when the checkbox ends at its original value', () => {
    const previous = catalogSpec()
    previous.features = ['reasoning']
    previous.catalogMatch!.fields = ['features.reasoning']
    const next = reconcileModelCapabilities(previous, {
      ...previous,
      parameterConstraints: { reasoning: { supported: true } },
    })
    expect(next.catalogMatch?.fields).toEqual([])
    expect(next.capabilitySources?.language).toBe('manual')
  })
})