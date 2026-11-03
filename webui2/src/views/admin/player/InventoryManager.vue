<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import ItemImg from '../../../components/ItemImg.vue'
import ItemPicker from '../../../components/ItemPicker.vue'
import ItemTooltip from '../../../components/ItemTooltip.vue'
import type { PvfItem } from '../../../components/itemDetails'
import Request from '../../../api/Request'

type Item = PvfItem & { name: string }
type Slot = { index: number; id: number; type: number; seal: number; enhancement: number; sealCount: number; amountOrGrade: number; durability: number; orb: number; increaseType: number; increaseValue: number; forge: number; item?: Item }
type Snapshot = { revision: string; online: boolean | null; slots: Slot[]; money: number; coin: number; payCoin: number; capacity: number }
type InventoryAction = 'edit' | 'delete' | 'move'

const props = defineProps<{ visible: boolean; character: { characNo: number; characName: string; lev?: number } | null }>()
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void }>()
const categories = [
  { label: '装备', type: 1, start: 9, end: 57 }, { label: '消耗品', type: 2, start: 57, end: 105 },
  { label: '材料', type: 3, start: 105, end: 153 }, { label: '副职业', type: 10, start: 201, end: 249 },
  { label: '任务', type: 4, start: 153, end: 201 }
]
const category = ref(0)
const snapshot = ref<Snapshot | null>(null)
const loading = ref(false)
const pendingAction = ref<InventoryAction | null>(null)
const saving = computed(() => pendingAction.value !== null)
const moving = computed(() => pendingAction.value === 'move')
const selectedIndex = ref<number | null>(null)
const dragged = ref<number | null>(null)
const picked = ref<Item | null>(null)
const hoverItem = ref<PvfItem | null>(null)
const hoverPos = reactive({ x: 0, y: 0 })
const shortcutMenu = ref<{ source: number; x: number; y: number } | null>(null)
const edit = reactive({ id: 0, seal: 0, enhancement: 0, sealCount: 0, amountOrGrade: 1, durability: 0, orb: 0, increaseType: 0, increaseValue: 0, forge: 0 })
const quickSlots = computed(() => (snapshot.value?.slots || []).filter(slot => isQuickSlot(slot.index)))
const availableBagSlots = computed(() => {
  const capacity = snapshot.value?.capacity
  if (capacity == null || !Number.isFinite(capacity)) return null
  const expansion = Math.trunc(capacity) & 0xffff
  return expansion === 0 || expansion === 8 || expansion === 16 ? 32 + expansion : null
})
const currentSlots = computed(() => {
  const group = categories[category.value]!
  return (snapshot.value?.slots || []).filter(slot => slot.index >= group.start && slot.index < group.end)
})
const selected = computed(() => snapshot.value?.slots.find(slot => slot.index === selectedIndex.value) || null)
const canEdit = computed(() => snapshot.value?.online === false && !saving.value)
const isEquipment = computed(() => {
  if (!selected.value) return false
  if (picked.value && picked.value.id === Number(edit.id)) {
    if (picked.value.type === 'equipment') return true
    if (picked.value.type === 'stackable') return false
  }
  return selected.value.id !== 0 && selected.value.type === 1
})

function closeShortcutMenuOnEscape(event: KeyboardEvent) {
  if (event.key === 'Escape' && shortcutMenu.value) {
    event.stopPropagation()
    shortcutMenu.value = null
  }
}
onMounted(() => document.addEventListener('keydown', closeShortcutMenuOnEscape, true))
onBeforeUnmount(() => document.removeEventListener('keydown', closeShortcutMenuOnEscape, true))

watch(() => [props.visible, props.character?.characNo], async ([visible]) => {
  hoverItem.value = null
  shortcutMenu.value = null
  if (visible && props.character) { category.value = 0; selectedIndex.value = null; snapshot.value = null; await refresh() }
})
async function refresh() {
  if (!props.character) return
  loading.value = true
  try {
    const response = await Request.get<Snapshot>(`/api/v1/charac/${props.character.characNo}/inventory`, { timeout: 120000 })
    const data = response.data
    // The API serializes Java long values as strings to avoid JSON precision loss.
    snapshot.value = {
      ...data,
      money: Number(data.money), coin: Number(data.coin), payCoin: Number(data.payCoin), capacity: Number(data.capacity),
      slots: data.slots.map(slot => ({ ...slot, id: Number(slot.id), amountOrGrade: Number(slot.amountOrGrade), orb: Number(slot.orb) }))
    }
    if (selectedIndex.value !== null) select(selectedIndex.value)
  } finally { loading.value = false }
}
function select(index: number) {
  const slot = snapshot.value?.slots.find(item => item.index === index)
  if (!slot) return
  if (!slot.id && isLocked(slot)) { Message.warning('该背包栏位尚未解锁'); return }
  selectedIndex.value = index
  picked.value = slot.item || null
  Object.assign(edit, { id: slot.id, seal: slot.seal, enhancement: slot.enhancement, sealCount: slot.sealCount, amountOrGrade: slot.id ? slot.amountOrGrade : 1, durability: slot.durability, orb: slot.orb, increaseType: slot.increaseType, increaseValue: slot.increaseValue, forge: slot.forge })
}
function chooseItem(item: Item | null) { if (item) edit.id = item.id }
async function apply(action: InventoryAction, index: number, extra: Record<string, unknown> = {}): Promise<boolean> {
  if (!snapshot.value || !props.character || !canEdit.value) return false
  pendingAction.value = action
  try {
    await Request.post(`/api/v1/charac/${props.character.characNo}/inventory`, { revision: snapshot.value.revision, action, index, ...extra }, { timeout: 60000 })
    Message.success('背包已更新'); await refresh()
    return true
  } catch {
    // Request displays the server's error, including stale data or insufficient space.
    return false
  } finally { pendingAction.value = null }
}
async function save() {
  if (selectedIndex.value === null) return
  if (!edit.id) { Message.warning('请选择物品'); return }
  const changes: Record<string, number> = { id: edit.id, amountOrGrade: edit.amountOrGrade }
  if (isEquipment.value) Object.assign(changes, {
    seal: edit.seal, enhancement: edit.enhancement, sealCount: edit.sealCount,
    durability: edit.durability, orb: edit.orb, increaseType: edit.increaseType,
    increaseValue: edit.increaseValue, forge: edit.forge
  })
  await apply('edit', selectedIndex.value, { changes })
}
function remove() {
  if (!selected.value?.id) return
  const index = selected.value.index
  Modal.warning({ title: '删除物品', content: `确定删除 ${selected.value.item?.name || `物品 ${selected.value.id}`} 吗？`, hideCancel: false, okText: '删除', onOk: () => apply('delete', index) })
}
function startDrag(slot: Slot, event: DragEvent) {
  if (!slot.id || !canEdit.value) { event.preventDefault(); return }
  hoverItem.value = null
  shortcutMenu.value = null
  dragged.value = slot.index
  event.dataTransfer?.setData('text/plain', String(slot.index))
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}
async function drop(slot: Slot) {
  const from = dragged.value
  dragged.value = null
  if (from === null || from === slot.index || !canEdit.value) return
  const source = snapshot.value?.slots.find(item => item.index === from)
  if (source) await moveSlot(source, slot)
}
function isQuickSlot(index: number) { return index >= 3 && index < 9 }
function isLocked(slot: Slot) {
  if (isQuickSlot(slot.index) || availableBagSlots.value === null) return false
  const group = categories.find(item => slot.index >= item.start && slot.index < item.end)
  return !!group && slot.index >= group.start + availableBagSlots.value
}
function selectCategory(index: number) {
  category.value = index
  hoverItem.value = null
  if (selectedIndex.value !== null && !isQuickSlot(selectedIndex.value)) selectedIndex.value = null
}
function warnWhenUnavailable(): boolean {
  if (canEdit.value) return false
  Message.warning(snapshot.value?.online ? '账号当前在线，请退出游戏后移动物品' : '当前无法确认账号离线状态或正在保存')
  return true
}
function emptyBagSlot(type: number): Slot | null {
  const group = categories.find(item => item.type === type)
  if (!group) { Message.warning('该物品没有对应的背包分类，无法移动'); return null }
  if (availableBagSlots.value === null) { Message.warning('无法识别背包扩容状态，请刷新后重试'); return null }
  const end = group.start + availableBagSlots.value
  const empty = snapshot.value?.slots.find(slot => slot.index >= group.start && slot.index < end && !slot.id)
  if (!empty) {
    Modal.warning({ title: '背包栏位不足', content: `${group.label}背包没有可用空位，无法移动物品。`, okText: '知道了' })
    return null
  }
  return empty
}
async function moveSlot(source: Slot, target: Slot) {
  if (!source.id || source.index === target.index || warnWhenUnavailable()) return
  if (isLocked(target)) { Message.warning('该背包栏位尚未解锁'); return }
  if (!isQuickSlot(source.index) && isQuickSlot(target.index) && target.id && !emptyBagSlot(target.type)) return
  if (await apply('move', source.index, { to: target.index })) {
    if (isQuickSlot(source.index) && !isQuickSlot(target.index)) {
      const nextCategory = categories.findIndex(item => target.index >= item.start && target.index < item.end)
      if (nextCategory >= 0) category.value = nextCategory
    }
    select(target.index)
  }
}
async function onSlotContext(slot: Slot, event: MouseEvent) {
  hoverItem.value = null
  shortcutMenu.value = null
  if (!slot.id || warnWhenUnavailable()) return
  if (isQuickSlot(slot.index)) {
    const empty = emptyBagSlot(slot.type)
    if (empty) await moveSlot(slot, empty)
    return
  }
  if (!quickSlots.value.length) { Message.warning('没有可用的快捷栏'); return }
  shortcutMenu.value = {
    source: slot.index,
    x: Math.max(8, Math.min(event.clientX, window.innerWidth - 280)),
    y: Math.max(8, Math.min(event.clientY, window.innerHeight - 320))
  }
}
async function chooseQuickSlot(target: Slot) {
  const source = snapshot.value?.slots.find(slot => slot.index === shortcutMenu.value?.source)
  shortcutMenu.value = null
  if (source) await moveSlot(source, target)
}
function updateHoverPos(event: MouseEvent) {
  hoverPos.x = event.clientX + 16
  hoverPos.y = event.clientY + 16
}
function showTooltip(slot: Slot, event: MouseEvent) {
  if (!slot.id || dragged.value !== null) return
  hoverItem.value = slot.item || { id: slot.id, name: `物品 ${slot.id}`, rarityName: '物品信息未找到' }
  updateHoverPos(event)
}
</script>

<template>
  <a-modal :visible="visible" :footer="false" width="min(1000px, calc(100vw - 32px))" :body-style="{ height: 'min(700px, calc(100vh - 150px))', overflow: 'hidden' }" :mask-closable="false" unmount-on-close @cancel="emit('update:visible', false)">
    <template #title>角色背包 · {{ character?.characName }}</template>
    <a-spin :loading="loading || moving" :tip="moving ? '正在移动物品…' : undefined" :aria-busy="loading || moving" class="inventory-spin">
      <div v-if="snapshot" class="inventory-layout">
        <div class="game-window">
          <div class="game-title"><span>物品栏</span><span>Lv.{{ character?.lev || '—' }} · {{ character?.characName }}</span></div>
          <div class="game-intro"><div class="portrait">{{ character?.characName?.slice(0, 1) }}</div><div><strong>{{ character?.characName }}</strong><p>物品栏与快捷栏</p><small>右键移动物品，也可拖动格子</small></div></div>
          <div class="shortcut-section">
            <div class="shortcut-heading"><strong>快捷栏</strong><span>右键移回背包</span></div>
            <div class="shortcut-grid" aria-label="快捷栏">
              <button v-for="slot in quickSlots" :key="slot.index" class="slot shortcut-slot" :class="{ selected: selectedIndex === slot.index, occupied: slot.id }"
                :aria-label="slot.id ? `快捷栏 ${slot.index - 2}：${slot.item?.name || `物品 ${slot.id}`}` : `快捷栏 ${slot.index - 2}：空`"
                :draggable="!!slot.id && canEdit" @click="select(slot.index)" @contextmenu.prevent="onSlotContext(slot, $event)"
                @mouseenter="showTooltip(slot, $event)" @mousemove="updateHoverPos" @mouseleave="hoverItem = null"
                @dragstart="startDrag(slot, $event)" @dragend="dragged = null" @dragover.prevent @drop.prevent="drop(slot)">
                <ItemImg v-if="slot.id" :icon="slot.item?.icon || { path: '', index: 0 }" :icon-marks="slot.item?.iconMarks" :rarity="slot.item?.rarity ?? 0" class="slot-icon" />
                <span class="shortcut-key">{{ slot.index - 2 }}</span>
                <span v-if="slot.id && slot.type !== 1" class="slot-count">{{ slot.amountOrGrade }}</span>
                <span v-if="slot.id && slot.type === 1 && slot.enhancement" class="slot-enhance">+{{ slot.enhancement }}</span>
              </button>
            </div>
          </div>
          <div class="category-tabs"><button v-for="(group, index) in categories" :key="group.label" :class="{ active: category === index }" @click="selectCategory(index)">{{ group.label }}</button></div>
          <div class="bag-scroll" aria-label="背包物品列表">
            <div class="slot-grid">
              <button v-for="slot in currentSlots" :key="slot.index" class="slot" :class="{ selected: selectedIndex === slot.index, occupied: slot.id, locked: isLocked(slot) }"
                :aria-label="slot.id ? `${slot.item?.name || `物品 ${slot.id}`} · 槽位 ${slot.index}` : isLocked(slot) ? `未解锁槽位 ${slot.index}` : `空槽位 ${slot.index}`"
                :title="slot.id ? undefined : isLocked(slot) ? '未解锁的背包栏位' : `空槽位 ${slot.index}`" :draggable="!!slot.id && canEdit"
                @click="select(slot.index)" @contextmenu.prevent="onSlotContext(slot, $event)"
                @mouseenter="showTooltip(slot, $event)" @mousemove="updateHoverPos" @mouseleave="hoverItem = null"
                @dragstart="startDrag(slot, $event)" @dragend="dragged = null" @dragover.prevent @drop.prevent="drop(slot)">
                <ItemImg v-if="slot.id" :icon="slot.item?.icon || { path: '', index: 0 }" :icon-marks="slot.item?.iconMarks" :rarity="slot.item?.rarity ?? 0" class="slot-icon" />
                <span v-else-if="isLocked(slot)" class="slot-lock" aria-hidden="true">🔒</span>
                <span v-if="slot.id && slot.type !== 1" class="slot-count">{{ slot.amountOrGrade }}</span>
                <span v-if="slot.id && slot.type === 1 && slot.enhancement" class="slot-enhance">+{{ slot.enhancement }}</span>
              </button>
            </div>
          </div>
          <div class="game-footer"><span>金币 <strong>{{ snapshot.money.toLocaleString() }}</strong></span><span>复活币 {{ snapshot.coin }} / {{ snapshot.payCoin }}</span></div>
        </div>
        <div class="item-panel">
          <div class="item-panel-head"><div><h3>物品信息</h3><p>选择格子查看和修改</p></div><a-button size="small" :loading="loading" @click="refresh">刷新</a-button></div>
          <div class="item-panel-scroll" aria-label="物品属性面板">
          <a-alert v-if="snapshot.online !== false" type="warning" show-icon>{{ snapshot.online ? '账号当前在线，请退出游戏后编辑背包。' : '无法确认账号在线状态，暂不可编辑。' }}</a-alert>
          <template v-if="selected">
            <div class="selected-item" @mouseenter="showTooltip(selected, $event)" @mousemove="updateHoverPos" @mouseleave="hoverItem = null"><ItemImg v-if="selected.id" :icon="selected.item?.icon || { path: '', index: 0 }" :icon-marks="selected.item?.iconMarks" :rarity="selected.item?.rarity ?? 0" style="width:48px;height:48px"/><div><strong>{{ selected.id ? (selected.item?.name || `物品 ${selected.id}`) : '空槽位' }}</strong><small>槽位 {{ selected.index }}<template v-if="selected.item?.minimumLevel"> · Lv.{{ selected.item.minimumLevel }}</template></small></div></div>
            <a-form :model="edit" layout="vertical" size="small">
              <a-form-item label="物品"><ItemPicker v-model="picked" :disabled="!canEdit" width="100%" placeholder="从 PVF 选择物品" @change="chooseItem" /></a-form-item>
              <div class="fields">
                <a-form-item label="物品 ID"><a-input-number v-model="edit.id" :min="1" :max="2147483647" :disabled="!canEdit" hide-button /></a-form-item>
                <a-form-item :label="isEquipment ? '品级数值' : '数量'"><a-input-number v-model="edit.amountOrGrade" :min="0" :max="4294967295" :disabled="!canEdit" hide-button /></a-form-item>
                <template v-if="isEquipment">
                  <a-form-item label="强化"><a-input-number v-model="edit.enhancement" :min="0" :max="31" :disabled="!canEdit" /></a-form-item>
                  <a-form-item label="锻造"><a-input-number v-model="edit.forge" :min="0" :max="255" :disabled="!canEdit" /></a-form-item>
                  <a-form-item label="耐久"><a-input-number v-model="edit.durability" :min="0" :max="65535" :disabled="!canEdit" /></a-form-item>
                  <a-form-item label="封装状态"><a-input-number v-model="edit.seal" :min="0" :max="255" :disabled="!canEdit" /></a-form-item>
                  <a-form-item label="封装次数"><a-input-number v-model="edit.sealCount" :min="0" :max="7" :disabled="!canEdit" /></a-form-item>
                  <a-form-item label="宝珠 ID"><a-input-number v-model="edit.orb" :min="0" :max="4294967295" :disabled="!canEdit" hide-button /></a-form-item>
                  <a-form-item label="增幅类型"><a-input-number v-model="edit.increaseType" :min="0" :max="255" :disabled="!canEdit" /></a-form-item>
                  <a-form-item label="增幅值"><a-input-number v-model="edit.increaseValue" :min="0" :max="65535" :disabled="!canEdit" /></a-form-item>
                </template>
              </div>
            </a-form>
            <p v-if="selected.item?.explain || selected.item?.description" class="item-description">{{ (selected.item.explain || selected.item.description || '').replaceAll('%%', '%').replaceAll('\\n', '\n') }}</p>
          </template>
          <div v-else class="empty-selection">选择左侧的物品或空槽位</div>
          </div>
          <div v-if="selected" class="item-actions">
            <a-button status="danger" :disabled="!selected.id || !canEdit" :loading="pendingAction === 'delete'" @click="remove">删除物品</a-button>
            <a-button type="primary" :disabled="!canEdit" :loading="pendingAction === 'edit'" @click="save">保存物品</a-button>
          </div>
        </div>
      </div>
      <ItemTooltip v-if="visible && hoverItem" :item="hoverItem" :x="hoverPos.x" :y="hoverPos.y" />
    </a-spin>
    <Teleport to="body">
      <div v-if="visible && shortcutMenu" class="shortcut-menu-backdrop" @click="shortcutMenu = null" @contextmenu.prevent="shortcutMenu = null">
        <div class="shortcut-menu" role="menu" :style="{ left: `${shortcutMenu.x}px`, top: `${shortcutMenu.y}px` }" @click.stop @contextmenu.stop.prevent @keydown.esc="shortcutMenu = null">
          <div class="shortcut-menu-heading">移至快捷栏</div>
          <button v-for="slot in quickSlots" :key="slot.index" type="button" role="menuitem" :disabled="saving" @click="chooseQuickSlot(slot)">
            <span class="shortcut-menu-key">{{ slot.index - 2 }}</span>
            <ItemImg v-if="slot.id" :icon="slot.item?.icon || { path: '', index: 0 }" :icon-marks="slot.item?.iconMarks" :rarity="slot.item?.rarity ?? 0" class="shortcut-menu-icon" />
            <span class="shortcut-menu-name">{{ slot.id ? (slot.item?.name || `物品 ${slot.id}`) : '空栏位' }}</span>
            <small v-if="slot.id">原物品返回背包</small>
          </button>
        </div>
      </div>
    </Teleport>
  </a-modal>
</template>

<style scoped>
.inventory-spin { display: block; height: 100%; min-height: 0; }
.inventory-layout { display: grid; grid-template-columns: minmax(0, 430px) minmax(0, 1fr); gap: 24px; height: 100%; min-height: 0; }
.game-window { display: flex; flex-direction: column; min-height: 0; box-sizing: border-box; padding: 5px 14px 14px; background: linear-gradient(140deg, #356573, #203d4d 35%, #172f3c); border: 3px solid #1e3540; box-shadow: inset 0 0 0 1px #7195a0, 0 5px 14px #0d1e2944; color: #d3e6e4; }
.game-title { height: 40px; flex: none; display: flex; justify-content: space-between; align-items: center; color: #f3eddb; border-bottom: 1px solid #101e27; font-weight: 700; }
.game-title span + span { font-size: 12px; font-weight: 400; color: #adc7cb; }
.game-intro { height: 78px; flex: none; display: flex; align-items: center; gap: 14px; padding: 0 5px; }
.game-intro p { font-size: 12px; margin: 3px 0; color: #b9d4d4; }
.game-intro small { font-size: 11px; color: #8faeb1; }
.portrait { width: 55px; height: 55px; flex: none; border-radius: 50%; display: grid; place-items: center; border: 3px solid #a9bcc0; background: radial-gradient(circle at 35% 25%, #83a5a5, #304f5d); font-size: 24px; color: #eef4e7; }
.shortcut-section { flex: none; padding: 0 3px 10px; }
.shortcut-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 5px; font-size: 12px; }
.shortcut-heading span { font-size: 11px; color: #8faeb1; }
.shortcut-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 4px; width: min(284px, 100%); }
.category-tabs { display: flex; flex: none; gap: 3px; border-bottom: 1px solid #6d8c91; }
.category-tabs button { flex: 1; min-width: 0; padding: 7px 2px; background: #314b51; border: 1px solid #182e35; border-radius: 3px 3px 0 0; color: #a4b6ae; font-size: 12px; cursor: pointer; }
.category-tabs button.active { background: linear-gradient(#b0b79d, #758576); color: #182e31; font-weight: bold; }
.bag-scroll { flex: 1 1 0; min-height: 0; overflow-y: auto; overscroll-behavior: contain; scrollbar-width: thin; scrollbar-color: #6a8d91 #1b3440; }
.slot-grid { display: grid; grid-template-columns: repeat(8, minmax(0, 1fr)); gap: 3px; padding: 12px 3px; align-content: start; }
.slot { aspect-ratio: 1; box-sizing: border-box; background: linear-gradient(135deg, #142532, #203541); border: 1px solid #729295; box-shadow: inset 0 0 0 1px #0a1920; position: relative; cursor: pointer; padding: 3px; }
.slot.selected { border-color: #f6dd80; box-shadow: 0 0 6px #f9dc6d, inset 0 0 0 1px #f7e9b0; }
.slot.occupied:hover { background: #4a656a; }
.slot.locked:not(.occupied) { background: #17252d; border-color: #40545c; cursor: default; }
.slot-lock { color: #82999a; font-size: 12px; opacity: .65; }
.slot-icon { width: 100% !important; height: 100% !important; border-radius: 0 !important; }
.slot-count, .slot-enhance, .shortcut-key { position: absolute; color: white; font-size: 11px; text-shadow: 1px 1px 2px black; }
.slot-count { bottom: 2px; right: 3px; }
.slot-enhance { color: #f3e79a; top: 1px; right: 2px; text-align: right; }
.shortcut-key { bottom: 1px; left: 3px; color: #bdcfd0; }
.game-footer { flex: none; border-top: 1px solid #55737a; padding-top: 10px; display: flex; justify-content: space-between; font-size: 12px; color: #aac2c4; }
.game-footer strong { color: #f4d991; }
.item-panel { display: flex; flex-direction: column; min-width: 0; min-height: 0; }
.item-panel-head { flex: none; display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.item-panel-head h3 { margin: 0 0 3px; font-size: 17px; }
.item-panel-head p { margin: 0; font-size: 12px; color: var(--color-text-3); }
.item-panel-scroll { flex: 1 1 0; min-height: 0; overflow-y: auto; overscroll-behavior: contain; padding-right: 8px; scrollbar-width: thin; }
.selected-item { display: flex; gap: 12px; align-items: center; margin: 14px 0; }
.selected-item strong, .selected-item small { display: block; }
.selected-item small { margin-top: 5px; color: var(--color-text-3); }
.fields { display: grid; grid-template-columns: 1fr 1fr; gap: 0 12px; }
.fields :deep(.arco-input-number) { width: 100%; }
.item-description { max-height: 70px; overflow: auto; font-size: 12px; color: var(--color-text-3); }
.item-actions { flex: none; display: flex; justify-content: space-between; gap: 10px; margin-top: 10px; padding-top: 12px; border-top: 1px solid var(--color-border-2); }
.empty-selection { padding: 90px 0; text-align: center; color: var(--color-text-3); }
.shortcut-menu-backdrop { position: fixed; inset: 0; z-index: 10000; }
.shortcut-menu { position: fixed; box-sizing: border-box; width: 264px; max-height: calc(100vh - 16px); overflow-y: auto; padding: 6px; border: 1px solid #718d93; border-radius: 6px; background: #1c333e; box-shadow: 0 8px 24px #0008; color: #e8f0ec; }
.shortcut-menu-heading { padding: 7px 8px; color: #b8d0d0; font-size: 12px; }
.shortcut-menu button { display: flex; align-items: center; width: 100%; gap: 8px; padding: 6px; border: 0; border-radius: 3px; background: transparent; color: inherit; text-align: left; cursor: pointer; }
.shortcut-menu button:hover { background: #365863; }
.shortcut-menu button:disabled { opacity: .55; cursor: default; }
.shortcut-menu-key { width: 18px; flex: none; color: #b4c9c8; text-align: center; }
.shortcut-menu-icon { width: 24px !important; height: 24px !important; flex: none; }
.shortcut-menu-name { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.shortcut-menu small { flex: none; color: #9ab1b2; font-size: 11px; }
@media (max-width: 800px) {
  .inventory-layout { grid-template-columns: minmax(0, 1fr); grid-template-rows: minmax(380px, 1fr) minmax(260px, 1fr); gap: 16px; overflow-y: auto; }
  .game-window { width: min(100%, 430px); }
}
</style>
