import test from 'node:test'
import assert from 'node:assert/strict'
import { additionalMemoryRequired, formatMemory, parseBytes } from '../webui2/src/views/admin/channel/memory.ts'

const gib = 1024 ** 3

test('memory values serialized as strings are compared numerically', () => {
  assert.equal(parseBytes('536870912'), gib / 2)
  assert.equal(formatMemory('536870912'), '512 MB')
  assert.ok(parseBytes('536870912') < additionalMemoryRequired(gib, []))
  assert.equal(parseBytes(null), null)
})

test('prediction reserves the remainder for enabled channels', () => {
  const required = additionalMemoryRequired(gib, [
    { enabled: true, memoryBytes: String(gib / 4) },
    { enabled: true, memoryBytes: String(2 * gib) },
    { enabled: false, memoryBytes: '0' },
  ])
  assert.equal(required, gib + 3 * gib / 4)
})
