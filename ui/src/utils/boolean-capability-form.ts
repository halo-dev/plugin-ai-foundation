export function booleanCapabilityValue(value: unknown): boolean | undefined {
  if (value === true || value === 'true') {
    return true
  }
  if (value === false || value === 'false') {
    return false
  }
  return undefined
}

export function booleanCapabilityFormValue(value?: boolean): '' | 'true' | 'false' {
  if (value === true) {
    return 'true'
  }
  if (value === false) {
    return 'false'
  }
  return ''
}
