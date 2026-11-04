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
  it('does not restore feature overrides unsupported by the target adapter', () => {
    const previous: AiModel['spec'] = {
      ...catalogSpec(),
      adapterType: 'gitee-chat',
      features: ['tool-call', 'structured-output', 'reasoning'],
      catalogMatch: undefined,
    }
    const constraints = {
      toolCalling: true,
      structuredOutput: true,
      outputTokens: 2048,
      reasoning: { supported: true, budgetMinimum: 512 },
    }
    const next = reconcileModelCapabilities(
      previous,
      {
        ...previous,
        adapterType: 'gitee-messages',
        features: ['structured-output'],
        parameterConstraints: constraints,
      },
      { supportedFeatures: ['streaming', 'structured-output'] },
    )
    expect(next.features).toEqual(['structured-output'])
    expect(next.parameterConstraints).toEqual({
      structuredOutput: true,
      outputTokens: 2048,
      reasoning: { budgetMinimum: 512 },
    })
    expect(constraints.toolCalling).toBe(true)
    expect(constraints.reasoning.supported).toBe(true)
  })

  it.each([
    ['embedding', 'openai-embedding'],
    ['rerank', 'rerank'],
    ['image-generation', 'openai-image'],
  ] as const)('retains %s profiles during import and rename', (modelType, adapterType) => {
    const previous: AiModel['spec'] = {
      ...catalogSpec(),
      modelType,
      adapterType,
      features: undefined,
      capabilities: undefined,
      catalogMatch: { providerId: 'openai', modelId: 'example', fields: ['parameters'] },
      catalogParameters: {
        providerType: 'openai',
        providerBaseUrl: 'https://api.openai.com/v1',
        modelId: 'example',
        adapterType,
        constraints: { contextTokens: 8191 },
      },
    }
    const next = reconcileModelCapabilities(
      previous,
      { ...previous, displayName: 'Renamed' },
      { importing: true },
    )
    expect(next.catalogParameters).toEqual(previous.catalogParameters)
    for (const change of [{ modelId: 'other' }, { providerName: 'other' }]) {
      const changed = reconcileModelCapabilities(previous, { ...previous, ...change })
      expect(changed.catalogParameters).toBeUndefined()
      expect(changed.catalogMatch).toBeUndefined()
    }
  })

  it('preserves empty image coverage and manual image choices while removing stale adapter formats', () => {
    const previous: AiModel['spec'] = {
      ...catalogSpec(),
      modelType: 'image-generation',
      adapterType: 'openai-image',
      features: undefined,
      capabilities: { imageGeneration: { textToImage: true, outputMediaTypes: [] } },
      capabilitySources: { imageGeneration: 'unknown' },
      catalogMatch: {
        providerId: 'openai',
        modelId: 'example',
        fields: ['imageGeneration.outputMediaTypes', 'parameters'],
      },
    }
    const renamed = reconcileModelCapabilities(previous, {
      ...previous,
      displayName: 'Renamed',
      capabilities: { imageGeneration: { textToImage: true } },
      capabilitySources: { imageGeneration: 'manual' },
    })
    expect(renamed.capabilities?.imageGeneration?.outputMediaTypes).toEqual([])
    expect(renamed.capabilitySources?.imageGeneration).toBe('unknown')
    const switched = reconcileModelCapabilities(previous, {
      ...previous,
      adapterType: 'openrouter-image',
    })
    expect(switched.capabilities?.imageGeneration?.outputMediaTypes).toBeUndefined()
    expect(switched.catalogMatch?.fields).toEqual(['parameters'])
    const manual = reconcileModelCapabilities(previous, {
      ...previous,
      adapterType: 'openrouter-image',
      capabilities: {
        imageGeneration: { textToImage: true, outputMediaTypes: ['image/webp'], maskInput: false },
      },
    })
    expect(manual.capabilities?.imageGeneration).toEqual({
      textToImage: true,
      maxImagesPerCall: 1,
      outputMediaTypes: ['image/webp'],
      maskInput: false,
    })
    expect(manual.capabilitySources?.imageGeneration).toBe('manual')
    expect(manual.catalogMatch?.fields).toEqual(['parameters'])
  })
  it('preserves the existing edit behavior for models without catalog metadata', () => {
    const previous = catalogSpec()
    delete previous.catalogMatch
    previous.features = []
    previous.capabilities = undefined
    const next: AiModel['spec'] = { ...previous, features: ['vision'] }
    expect(reconcileModelCapabilities(previous, next)).toEqual(next)
  })

  it('restores inheritance when the last image field is cleared without mutating either input', () => {
    const previous: AiModel['spec'] = {
      ...catalogSpec(),
      modelType: 'image-generation',
      adapterType: 'openai-image',
      capabilities: {
        imageGeneration: { outputMediaTypes: ['image/png'] },
        sources: { imageGeneration: 'unknown' },
      },
      capabilitySources: { imageGeneration: 'unknown' },
      catalogMatch: {
        providerId: 'openai',
        modelId: 'example',
        fields: ['imageGeneration.outputMediaTypes', 'parameters'],
      },
      catalogParameters: {
        providerType: 'openai',
        providerBaseUrl: 'https://api.openai.com/v1',
        modelId: 'example',
        adapterType: 'openai-image',
      },
    }
    const next: AiModel['spec'] = {
      ...previous,
      capabilities: {
        imageGeneration: { outputMediaTypes: undefined, textToImage: undefined },
        sources: { imageGeneration: 'manual' },
      },
      capabilitySources: { imageGeneration: 'manual' },
    }
    const original = structuredClone(previous)
    const draft = structuredClone(next)
    const cleared = reconcileModelCapabilities(previous, next)

    expect(cleared.capabilities).toBeUndefined()
    expect(cleared.capabilitySources).toBeUndefined()
    expect(cleared.catalogMatch).toEqual({ ...previous.catalogMatch, fields: ['parameters'] })
    expect(cleared.catalogParameters).toEqual(previous.catalogParameters)
    expect(reconcileModelCapabilities(cleared, { ...cleared, displayName: 'Renamed' })).toEqual({
      ...cleared,
      displayName: 'Renamed',
    })
    expect(previous).toEqual(original)
    expect(next).toEqual(draft)

    const switched = reconcileModelCapabilities(previous, {
      ...previous,
      adapterType: 'openrouter-image',
    })
    expect(switched.capabilities).toBeUndefined()
    expect(switched.capabilitySources).toBeUndefined()
    expect(switched.catalogMatch?.fields).toEqual([])
  })

  it('clears only image capabilities and both of their source locations', () => {
    const previous: AiModel['spec'] = {
      ...catalogSpec(),
      modelType: 'image-generation',
      adapterType: 'openai-image',
      capabilities: { imageGeneration: { outputMediaTypes: ['image/png'] } },
      catalogMatch: {
        providerId: 'openai',
        modelId: 'example',
        fields: ['imageGeneration.outputMediaTypes'],
      },
    }
    const next: AiModel['spec'] = {
      ...previous,
      capabilities: {
        language: { imageInput: false },
        sources: { language: 'remote', imageGeneration: 'manual' },
      },
      capabilitySources: { language: 'manual', imageGeneration: 'manual' },
    }
    const cleared = reconcileModelCapabilities(previous, next)
    expect(cleared.capabilities).toEqual({
      language: { imageInput: false },
      sources: { language: 'remote' },
    })
    expect(cleared.capabilitySources).toEqual({ language: 'manual' })
  })

  it.each([{ textToImage: false }, { outputMediaTypes: [] }, { sizes: [] }])(
    'keeps explicit image declarations when they are the only remaining fields: %j',
    (imageGeneration) => {
      const previous: AiModel['spec'] = {
        ...catalogSpec(),
        modelType: 'image-generation',
        adapterType: 'openai-image',
        capabilities: { imageGeneration: { outputMediaTypes: ['image/png'] } },
        capabilitySources: { imageGeneration: 'unknown' },
        catalogMatch: {
          providerId: 'openai',
          modelId: 'example',
          fields: ['imageGeneration.outputMediaTypes'],
        },
      }
      const next = reconcileModelCapabilities(previous, {
        ...previous,
        capabilities: { imageGeneration },
      })
      expect(next.capabilities?.imageGeneration).toMatchObject(imageGeneration)
      expect(next.capabilitySources?.imageGeneration).toBe('manual')
      expect(next.catalogMatch?.fields).toEqual([])
    },
  )

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
      providerBaseUrl: 'https://api.openai.com/v1',
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

  it('rejects fresh profiles across provider names, types and configured endpoints', () => {
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

  it.each([undefined, { providerType: 'openai' }])(
    'rejects catalog snapshots without a captured endpoint even before provider metadata loads',
    (provider) => {
      const previous = catalogSpec()
      previous.catalogParameters = {
        providerType: 'openai',
        modelId: previous.modelId,
        adapterType: previous.adapterType,
        constraints: { temperature: false },
      }
      expect(
        reconcileModelCapabilities(previous, previous, { provider }).catalogParameters,
      ).toBeUndefined()
    },
  )

  it.each([
    ['language', 'openai-chat', false],
    ['language', 'openai-responses', true],
    ['language', 'deepseek-messages', true],
    ['embedding', 'openai-embedding', true],
    ['image-generation', 'openai-image', true],
    ['rerank', 'rerank', true],
  ] as const)('scopes chat path overrides for %s / %s', (modelType, adapterType, retained) => {
    const previous: AiModel['spec'] = {
      ...catalogSpec(),
      modelType,
      adapterType,
      catalogParameters: {
        providerType: 'openai',
        providerBaseUrl: 'https://api.openai.com/v1',
        modelId: 'example',
        adapterType,
      },
    }
    const next = reconcileModelCapabilities(previous, previous, {
      provider: { providerType: 'openai', chatEndpointPath: '/custom/chat' },
    })
    expect(next.catalogParameters).toEqual(retained ? previous.catalogParameters : undefined)
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
        providerBaseUrl: 'https://api.openai.com/v1',
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
        providerBaseUrl: 'https://api.openai.com/v1',
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