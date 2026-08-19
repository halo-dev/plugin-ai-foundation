import { describe, expect, it } from 'vitest'
import { booleanCapabilityFormValue, booleanCapabilityValue } from './boolean-capability-form'

describe('boolean capability form values', () => {
  it.each([
    { label: '继承供应商', formValue: '', capabilityValue: undefined },
    { label: '支持', formValue: 'true', capabilityValue: true },
    { label: '不支持', formValue: 'false', capabilityValue: false },
  ])('round-trips $label', ({ formValue, capabilityValue }) => {
    expect(booleanCapabilityValue(formValue)).toBe(capabilityValue)
    expect(booleanCapabilityFormValue(capabilityValue)).toBe(formValue)
  })
})
