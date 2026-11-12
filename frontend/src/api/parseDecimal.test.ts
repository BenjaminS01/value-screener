import { describe, expect, it } from 'vitest'
import { parseDecimal } from './parseDecimal'

describe('parseDecimal', () => {
  it.each([
    ['150,5', 150.5],
    ['150.5', 150.5],
    [' 3 ', 3],
    ['', null],
    ['   ', null],
    ['abc', null],
    ['1,2,3', null],
    ['1.2.3', null],
  ])('parses %j', (text, expected) => {
    expect(parseDecimal(text)).toBe(expected)
  })
})
