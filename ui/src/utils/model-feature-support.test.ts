import { describe, expect, it } from 'vitest'
import {
  featureConstraints,
  featuresWithConstraints,
  filterFeatureSupport,
  withFeatureSupport,
} from './model-feature-support'

describe('model feature support', () => {
  it.each([undefined, ['tool-call', 'structured-output', 'reasoning'] as const])(
    'keeps explicit enabled and disabled choices without an incompatible adapter boundary: %s',
    (supportedFeatures) => {
      const constraints = {
        toolCalling: false,
        structuredOutput: true,
        reasoning: { supported: false, budgetMinimum: 512 },
      }
      expect(filterFeatureSupport(constraints, supportedFeatures)).toEqual(constraints)
      expect(
        featuresWithConstraints(['tool-call', 'reasoning'], constraints, supportedFeatures),
      ).toEqual(['structured-output'])
    },
  )

  it('distinguishes an empty adapter feature list from unknown support', () => {
    const constraints = { toolCalling: true, reasoning: { supported: false } }
    expect(filterFeatureSupport(constraints, [])).toBeUndefined()
    expect(featuresWithConstraints(['tool-call', 'vision'], constraints, [])).toEqual([])
  })

  it.each([false, true])('uses explicit %s constraints for all three features', (supported) => {
    const constraints = {
      toolCalling: supported,
      structuredOutput: supported,
      reasoning: { supported },
    }
    expect(featuresWithConstraints(['vision', 'tool-call', 'reasoning'], constraints)).toEqual(
      supported ? ['vision', 'tool-call', 'reasoning', 'structured-output'] : ['vision'],
    )
  })

  it('preserves historical features when support is unknown', () => {
    expect(featuresWithConstraints(['tool-call', 'reasoning'], featureConstraints())).toEqual([
      'tool-call',
      'reasoning',
    ])
  })

  it('keeps manual support above catalog support and preserves other reasoning constraints', () => {
    const manual = withFeatureSupport({ reasoning: { budgetMinimum: 512 } }, 'reasoning', true)
    expect(manual).toEqual({ reasoning: { supported: true, budgetMinimum: 512 } })
    expect(
      featureConstraints({ constraints: { reasoning: { supported: false } } }, manual),
    ).toMatchObject({ reasoning: { supported: true } })
  })

  it('does not use catalog reasoning support with administrator reasoning mappings', () => {
    const catalog = { constraints: { reasoning: { supported: false } } }
    for (const mode of ['UNSUPPORTED', 'TEMPLATE'] as const) {
      const mappings = { language: { reasoning: { mode } } }
      expect(featureConstraints(catalog, undefined, mappings).reasoning?.supported).toBeUndefined()
      expect(
        featureConstraints(catalog, undefined, undefined, mappings).reasoning?.supported,
      ).toBeUndefined()
      expect(
        featureConstraints(catalog, { reasoning: { supported: true } }, mappings).reasoning
          ?.supported,
      ).toBe(true)
    }
    expect(
      featureConstraints(catalog, undefined, { language: { reasoning: { mode: 'INHERIT' } } })
        .reasoning?.supported,
    ).toBe(false)
  })
})