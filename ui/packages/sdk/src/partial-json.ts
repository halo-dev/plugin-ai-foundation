type State =
  | 'root'
  | 'done'
  | 'string'
  | 'escape'
  | 'unicode'
  | 'literal'
  | 'number'
  | 'object-start'
  | 'object-key'
  | 'object-after-key'
  | 'object-before-value'
  | 'object-after-value'
  | 'object-after-comma'
  | 'array-start'
  | 'array-after-value'
  | 'array-after-comma'

const JSON_LITERALS = ['true', 'false', 'null'] as const

/** Repairs a syntactically incomplete JSON prefix without accepting otherwise invalid JSON. */
export function fixJson(input: string): string {
  const stack: State[] = ['root']
  let lastValidIndex = -1
  let literalStart = -1
  let unicodeDigits = 0

  const beginValue = (character: string, index: number, after: State) => {
    if (character === '"') {
      lastValidIndex = index
      stack.pop()
      stack.push(after, 'string')
    } else if ('ftn'.includes(character)) {
      lastValidIndex = index
      literalStart = index
      stack.pop()
      stack.push(after, 'literal')
    } else if (character === '-' || isDigit(character)) {
      if (isDigit(character)) lastValidIndex = index
      stack.pop()
      stack.push(after, 'number')
    } else if (character === '{') {
      lastValidIndex = index
      stack.pop()
      stack.push(after, 'object-start')
    } else if (character === '[') {
      lastValidIndex = index
      stack.pop()
      stack.push(after, 'array-start')
    }
  }

  const afterObjectValue = (character: string, index: number) => {
    if (character === ',') {
      stack.pop()
      stack.push('object-after-comma')
    } else if (character === '}') {
      lastValidIndex = index
      stack.pop()
    }
  }

  const afterArrayValue = (character: string, index: number) => {
    if (character === ',') {
      stack.pop()
      stack.push('array-after-comma')
    } else if (character === ']') {
      lastValidIndex = index
      stack.pop()
    }
  }

  for (let index = 0; index < input.length; index++) {
    const character = input[index]!
    const state = stack[stack.length - 1]
    switch (state) {
      case 'root':
        beginValue(character, index, 'done')
        break
      case 'object-start':
      case 'object-after-comma':
        if (character === '"') {
          stack.pop()
          stack.push('object-key')
        } else if (state === 'object-start' && character === '}') {
          lastValidIndex = index
          stack.pop()
        }
        break
      case 'object-key':
        if (character === '"') {
          stack.pop()
          stack.push('object-after-key')
        }
        break
      case 'object-after-key':
        if (character === ':') {
          stack.pop()
          stack.push('object-before-value')
        }
        break
      case 'object-before-value':
        beginValue(character, index, 'object-after-value')
        break
      case 'object-after-value':
        afterObjectValue(character, index)
        break
      case 'array-start':
        if (character === ']') {
          lastValidIndex = index
          stack.pop()
        } else if (!isWhitespace(character)) {
          beginValue(character, index, 'array-after-value')
        }
        break
      case 'array-after-value':
        if (character === ',' || character === ']') {
          afterArrayValue(character, index)
        } else if (!isWhitespace(character)) {
          lastValidIndex = index
        }
        break
      case 'array-after-comma':
        if (!isWhitespace(character)) {
          beginValue(character, index, 'array-after-value')
        }
        break
      case 'string':
        if (character === '"') {
          stack.pop()
          lastValidIndex = index
        } else if (character === '\\') {
          stack.push('escape')
        } else {
          lastValidIndex = index
        }
        break
      case 'escape':
        stack.pop()
        if (character === 'u') {
          unicodeDigits = 0
          stack.push('unicode')
        } else {
          lastValidIndex = index
        }
        break
      case 'unicode':
        if (isHexDigit(character) && ++unicodeDigits === 4) {
          stack.pop()
          lastValidIndex = index
        }
        break
      case 'number':
        if (isDigit(character)) {
          lastValidIndex = index
        } else if (!['e', 'E', '-', '+', '.'].includes(character)) {
          stack.pop()
          const parent = stack[stack.length - 1]
          if (parent === 'object-after-value') {
            afterObjectValue(character, index)
          } else if (parent === 'array-after-value') {
            afterArrayValue(character, index)
          }
        }
        break
      case 'literal': {
        const fragment = input.slice(literalStart, index + 1)
        if (JSON_LITERALS.some((value) => value.startsWith(fragment))) {
          lastValidIndex = index
        } else {
          stack.pop()
          const parent = stack[stack.length - 1]
          if (parent === 'object-after-value') {
            afterObjectValue(character, index)
          } else if (parent === 'array-after-value') {
            afterArrayValue(character, index)
          }
        }
        break
      }
    }
  }

  let result = input.slice(0, lastValidIndex + 1)
  for (let index = stack.length - 1; index >= 0; index--) {
    result += completionForState(stack[index], input, literalStart)
  }
  return result
}

/** Parses complete or repairable partial JSON, returning undefined until parsing is possible. */
export function parsePartialJson(input: string | undefined): unknown | undefined {
  if (input === undefined || input.trim() === '') return undefined

  const parsed = tryParseJson(input)
  if (parsed !== undefined) {
    return parsed
  }
  return tryParseJson(fixJson(input))
}

function completionForState(state: State | undefined, input: string, literalStart: number): string {
  if (state === 'string') {
    return '"'
  }
  if (state?.startsWith('object-')) {
    return '}'
  }
  if (state?.startsWith('array-')) {
    return ']'
  }
  if (state !== 'literal') {
    return ''
  }

  const fragment = input.slice(literalStart)
  const literal = JSON_LITERALS.find((value) => value.startsWith(fragment))
  return literal?.slice(fragment.length) ?? ''
}

function tryParseJson(input: string): unknown | undefined {
  try {
    return JSON.parse(input)
  } catch {
    return undefined
  }
}

function isDigit(character: string): boolean {
  return character >= '0' && character <= '9'
}

function isHexDigit(character: string): boolean {
  return /^[0-9a-f]$/i.test(character)
}

function isWhitespace(character: string): boolean {
  return character === ' ' || character === '\n' || character === '\r' || character === '\t'
}
