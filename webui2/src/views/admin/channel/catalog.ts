export type ChannelIcon = { image: 'one' | 'two' | 'pvp'; x: number; y: number; width: number; size?: number }
export type ChannelLabel = { number: number; name: string; icon?: ChannelIcon }

const icons = {
  valley: { image: 'one', x: 129, y: 559, width: 1144 },
  market: { image: 'one', x: 454, y: 559, width: 1144 },
  forest: { image: 'one', x: 129, y: 170, width: 1144 },
  sky: { image: 'one', x: 780, y: 246, width: 1144 },
  behemoth: { image: 'one', x: 129, y: 397, width: 1144 },
  alfhlyra: { image: 'one', x: 454, y: 473, width: 1144 },
  snow: { image: 'two', x: 546, y: 355, width: 1366 },
  coast: { image: 'two', x: 546, y: 430, width: 1366 },
  northmyre: { image: 'two', x: 220, y: 506, width: 1366 },
  antwer: { image: 'two', x: 874, y: 506, width: 1366 },
  train: { image: 'two', x: 546, y: 580, width: 1366 },
  ancient: { image: 'two', x: 874, y: 580, width: 1366 },
  pvp: { image: 'pvp', x: 207, y: 80, width: 400, size: 40 },
} satisfies Record<string, ChannelIcon>

const entries: Record<number, ChannelLabel> = {}
function add(number: number, name: string, icon?: ChannelIcon) { entries[number] = { number, name, icon } }
function series(first: number, last: number, name: string, icon: ChannelIcon) {
  for (let n = first; n <= last; n++) add(n, `${name} ${String.fromCharCode(65 + n - first)}`, icon)
}
add(1, '亡者峡谷', icons.valley)
add(6, '交易－拍卖场', icons.market)
add(7, '交易－拍卖场', icons.market)
series(11, 15, '格兰之森', icons.forest)
series(16, 19, '天空之城', icons.sky)
series(20, 23, '天帷巨兽', icons.behemoth)
series(24, 26, '阿法利亚', icons.alfhlyra)
series(27, 29, '斯顿雪域', icons.snow)
series(30, 32, '海岸三番街', icons.coast)
series(33, 34, '诺斯玛尔', icons.northmyre)
series(35, 36, '安特贝鲁峡谷', icons.antwer)
series(37, 38, '海上列车', icons.train)
add(39, '远古地下城 A', icons.ancient)
for (let n = 52; n <= 56; n++) add(n, '决斗场', icons.pvp)

export const channelNumbers = Object.keys(entries).map(Number).sort((a, b) => a - b)
export function channelLabel(number: number): ChannelLabel { return entries[number] ?? { number, name: '频道' } }
export function channelText(number: number): string {
  const entry = channelLabel(number)
  return `ch.${String(number).padStart(2, '0')} ${entry.name}`
}

// 3 and 11 are present in the DNF project's bundled d_channel data; the other
// labels were supplied for this version by the operator.
export const channelTypes = [
  { label: '使用游戏默认值', value: 'default' },
  { label: '地下城频道', value: '0' },
  { label: '交易频道', value: '3' },
  { label: '普通决斗场', value: '8' },
  { label: '亡者峡谷', value: '11' },
  { label: '自由决斗场', value: '13' },
  { label: '公平决斗场', value: '15' },
]
export function channelTypeText(value: number | null): string {
  if (value === null) return '使用游戏默认值'
  return channelTypes.find(type => type.value === String(value))?.label ?? `类型 ${value}`
}
