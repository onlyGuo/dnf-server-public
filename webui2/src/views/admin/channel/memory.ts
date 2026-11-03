export type Bytes = number | string | null

export function parseBytes(value: Bytes | undefined): number | null {
  if (value == null || value === '') return null
  const number = Number(value)
  return Number.isFinite(number) && number >= 0 ? number : null
}

export function formatMemory(value: Bytes | undefined): string {
  const bytes = parseBytes(value)
  return bytes == null ? '未知' : bytes >= 1024 ** 3 ? `${(bytes / 1024 ** 3).toFixed(2)} GB` : `${(bytes / 1024 ** 2).toFixed(0)} MB`
}

export function additionalMemoryRequired(estimate: number, channels: { enabled: boolean; memoryBytes: Bytes }[]): number {
  return estimate + channels.reduce((sum, channel) => {
    const actual = parseBytes(channel.memoryBytes)
    return sum + (channel.enabled && actual != null ? Math.max(0, estimate - actual) : 0)
  }, 0)
}
