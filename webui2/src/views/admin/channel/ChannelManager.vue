<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import Request from '../../../api/Request'
import ChannelIcon from './ChannelIcon.vue'
import { channelNumbers, channelText, channelTypeText, channelTypes } from './catalog'
import { additionalMemoryRequired, formatMemory, parseBytes } from './memory'
import type { Bytes } from './memory'

type Mode = 'INHERIT' | 'STATIC' | 'DDNS'
type Channel = { number: number; enabled: boolean; mode: Mode; target: string; resolvedIp: string; tcpPort: number; udpPort: number; status: string; listening: boolean | null; memoryBytes: Bytes; channelType: number | null; actualType: number | null; error?: string }
type Memory = { totalBytes: Bytes; usedBytes: Bytes; availableBytes: Bytes; channelEstimateBytes: Bytes; scope: 'host' | 'container' }
type DefaultAddress = { managed: boolean; mode: 'STATIC' | 'DDNS' | 'AUTO' | 'UNMANAGED'; target: string; resolvedIp: string; error?: string }
type Overview = { available: boolean; serverGroup: number; dataDir: string; defaultIp: string; defaultAddress?: DefaultAddress; memory: Memory | null; channels: Channel[] }
const overview = ref<Overview>({ available: false, serverGroup: 3, dataDir: '', defaultIp: '', memory: null, channels: [] })
const loading = ref(true)
const loaded = ref(false)
const saving = ref(false)
const switching = reactive<Record<number, boolean>>({})
const visible = ref(false)
const defaultVisible = ref(false)
const defaultSaving = ref(false)
const defaultForm = reactive<{ mode: 'STATIC' | 'DDNS'; target: string }>({ mode: 'STATIC', target: '' })
const isNew = ref(false)
const form = reactive<{ number: number | undefined; enabled: boolean; mode: Mode; target: string; typeChoice: string }>({ number: undefined, enabled: true, mode: 'INHERIT', target: '', typeChoice: 'default' })
const options = computed(() => channelNumbers.filter(n => !overview.value.channels.some(c => c.number === n)).map(n => ({ label: channelText(n), value: n })))
const typeOptions = computed(() => channelTypes.some(item => item.value === form.typeChoice) ? channelTypes : [...channelTypes, { label: `其他（现有值 ${form.typeChoice}）`, value: form.typeChoice }])
const activeCount = computed(() => overview.value.channels.filter(c => c.enabled).length)
let timer: ReturnType<typeof setInterval> | undefined
let loadVersion = 0

async function load(quiet = false) {
  const version = loadVersion
  if (!quiet) loading.value = true
  try {
    const response: any = await Request.get('/api/v1/admin/channels', { timeout: 60000 })
    if (version === loadVersion) {
      overview.value = response.data
      loaded.value = true
    }
  } finally { if (!quiet) loading.value = false }
}
onMounted(() => { load().catch(() => {}); timer = setInterval(() => load(true).catch(() => {}), 15000) })
onUnmounted(() => { if (timer) clearInterval(timer) })
function edit(channel?: Channel) {
  isNew.value = !channel
  Object.assign(form, channel ? { number: channel.number, enabled: channel.enabled, mode: channel.mode, target: channel.target, typeChoice: channel.channelType == null ? 'default' : String(channel.channelType) } : { number: undefined, enabled: true, mode: 'INHERIT', target: '', typeChoice: 'default' })
  visible.value = true
}
function editDefault() {
  const current = overview.value.defaultAddress
  defaultForm.mode = current?.mode === 'DDNS' ? 'DDNS' : 'STATIC'
  defaultForm.target = current?.managed && current.mode !== 'AUTO' ? current.target : overview.value.defaultIp
  defaultVisible.value = true
}
async function saveDefault() {
  if (!defaultForm.target.trim()) { Message.warning('请填写默认 IP 或 DDNS 域名'); return }
  defaultSaving.value = true
  try {
    await Request.put('/api/v1/admin/channels/default-address', { mode: defaultForm.mode, target: defaultForm.target }, { timeout: 300000 })
    defaultVisible.value = false
    Message.success('默认通告地址已保存')
    await load(true)
  } finally { defaultSaving.value = false }
}
async function save() {
  if (!form.number) { Message.warning('请选择频道号'); return }
  if (form.mode !== 'INHERIT' && !form.target.trim()) { Message.warning('请填写 IP 或域名'); return }
  const previous = overview.value.channels.find(channel => channel.number === form.number)
  const addsChannel = form.enabled && (!previous || !previous.enabled)
  if (addsChannel) await checkMemory(() => { saving.value = true }, () => { saving.value = false }, persistSave)
  else await persistSave()
}
async function checkMemory(start: () => void, finish: () => void, proceed: () => Promise<void>) {
  start()
  try {
    const response: any = await Request.get('/api/v1/admin/channels/memory', { timeout: 10000 })
    overview.value.memory = response.data
  } finally { finish() }
  const memory = overview.value.memory
  const available = parseBytes(memory?.availableBytes)
  const estimate = parseBytes(memory?.channelEstimateBytes)
  const required = estimate == null ? null : additionalMemoryRequired(estimate, overview.value.channels)
  if (available != null && required != null && available < required) {
    Modal.warning({ title: '内存可能不足', content: `每个频道预计需要 1 GB；计入现有频道预留后，本次至少还需 ${formatMemory(required)}，当前可用 ${formatMemory(available)}。不建议继续启用，仍要继续吗？`, hideCancel: false, okText: '继续启用', cancelText: '取消', onOk: proceed })
    return
  }
  await proceed()
}
async function persistSave() {
  saving.value = true
  try {
    await Request.put(`/api/v1/admin/channels/${form.number}`, { enabled: form.enabled, mode: form.mode, target: form.target, channelType: form.typeChoice === 'default' ? null : Number(form.typeChoice) }, { timeout: 60000 })
    visible.value = false
    Message.success('频道配置已保存')
    await load(true)
  } finally { saving.value = false }
}
async function changeEnabled(channel: Channel, value: boolean | string | number) {
  const enabled = value === true
  if (enabled === channel.enabled || switching[channel.number]) return
  const start = () => { switching[channel.number] = true }
  const finish = () => { delete switching[channel.number] }
  const apply = async () => {
    start()
    try {
      const response: any = await Request.put(`/api/v1/admin/channels/${channel.number}/enabled`, { enabled }, { timeout: 60000 })
      ++loadVersion
      const index = overview.value.channels.findIndex(item => item.number === channel.number)
      if (index >= 0) overview.value.channels[index] = response.data
      Message.success(`频道 ${channel.number} 已${enabled ? '启用' : '停用'}`)
      await load(true).catch(() => {})
    } catch {
      // The backend may have saved the state before a process operation failed.
      ++loadVersion
      await load(true).catch(() => {})
    } finally { finish() }
  }
  if (enabled) await checkMemory(start, finish, apply)
  else await apply()
}
function restart(channel: Channel) {
  Modal.warning({ title: `重启频道 ${channel.number}`, content: '仅重启此频道的游戏进程，该频道内的玩家可能断线。', hideCancel: false,
    onOk: async () => {
      await Request.post(`/api/v1/admin/channels/${channel.number}/restart`, {}, { timeout: 60000 })
      Message.success(`频道 ${channel.number} 已发出重启命令`)
      await load(true)
    } })
}
const modeText = (mode: Mode) => ({ INHERIT: '跟随默认地址', STATIC: '固定 IP', DDNS: 'DDNS 域名' })[mode]
const running = (channel: Channel) => channel.status?.includes('RUNNING') && channel.listening === true
const estimatedChannelMemory = computed(() => activeCount.value * (parseBytes(overview.value.memory?.channelEstimateBytes) ?? 1024 ** 3))
</script>

<template>
  <div class="channels-page">
    <div class="page-head">
      <div><h2>频道管理</h2><p>管理游戏频道和每个频道的客户端通告地址。</p></div>
      <div class="head-actions"><a-button @click="load()" :loading="loading">刷新状态</a-button><a-button type="primary" :disabled="!overview.available || !options.length" @click="edit()">新增频道</a-button></div>
    </div>
    <div v-if="!loaded && loading" class="loading-state"><a-spin tip="正在加载频道状态" /></div>
    <a-alert v-else-if="!loaded" type="error" show-icon>频道状态加载失败，请点击“刷新状态”重试。</a-alert>
    <a-alert v-else-if="!overview.available" type="warning" show-icon>当前未发现可管理的本地 DNF 游戏服务。用户、邮件等数据库功能仍可使用；请检查游戏目录和 Supervisor。</a-alert>
    <template v-else>
      <section class="overview-panel" aria-label="频道和内存概览">
        <div class="overview-grid">
          <div class="overview-item"><span>大区</span><strong>{{ overview.serverGroup }}</strong></div>
          <div class="overview-item default-address">
            <div class="default-heading"><span>默认通告地址</span><a-button size="mini" type="text" aria-label="编辑默认通告地址" @click="editDefault"><svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true"><path fill="currentColor" d="M16.9 3.3a2.2 2.2 0 0 1 3.1 3.1L8.3 18.1 4 19l.9-4.3L16.9 3.3Zm-1 3.1L6.7 15.6l-.3 1.5 1.5-.3L17.1 7.6l-1.2-1.2Z"/></svg></a-button></div>
            <strong>{{ overview.defaultIp || '等待地址' }}</strong>
            <small :title="overview.defaultAddress?.target">{{ overview.defaultAddress?.mode === 'DDNS' ? `DDNS · ${overview.defaultAddress.target}` : overview.defaultAddress?.mode === 'AUTO' ? '自动检测公网 IP' : overview.defaultAddress?.managed ? '固定 IP' : '等待初始化' }}</small>
          </div>
          <div class="overview-item"><span>已启用频道</span><strong>{{ activeCount }} <small>/ {{ overview.channels.length }}</small></strong></div>
          <div class="overview-item memory-start"><span>{{ overview.memory?.scope === 'container' ? '容器可用总内存' : '总内存' }}</span><strong>{{ formatMemory(overview.memory?.totalBytes) }}</strong></div>
          <div class="overview-item"><span>已用内存</span><strong>{{ formatMemory(overview.memory?.usedBytes) }}</strong></div>
          <div class="overview-item"><span>可用内存</span><strong>{{ formatMemory(overview.memory?.availableBytes) }}</strong></div>
          <div class="overview-item"><span>已启用频道预估</span><strong>{{ formatMemory(estimatedChannelMemory) }}</strong></div>
        </div>
        <div class="overview-notes">
          <span>每频道预估 1 GB，列表显示进程实际内存</span>
          <span>独立地址变化只重启该频道；默认地址变化会更新所有已启用频道的中继和 STUN 地址</span>
          <span>解析失败时保留上次可用地址</span>
        </div>
      </section>
      <a-alert v-if="overview.defaultAddress?.error" type="warning" show-icon class="notice">{{ overview.defaultAddress.error }}</a-alert>
      <a-card :bordered="false" class="channel-card">
        <template #title>频道列表</template>
        <a-table :data="overview.channels" :loading="loading" :pagination="false" row-key="number" :scroll="{ x: 1120 }">
          <template #columns>
            <a-table-column title="频道" :width="220"><template #cell="{ record }"><div class="channel-name"><ChannelIcon :number="record.number" /><strong>{{ channelText(record.number) }}</strong></div></template></a-table-column>
            <a-table-column title="运行状态" :width="165"><template #cell="{ record }"><div class="channel-status"><a-switch :model-value="record.enabled" size="small" :loading="Boolean(switching[record.number])" :aria-label="`${record.enabled ? '停用' : '启用'} ${channelText(record.number)}`" :title="record.enabled ? '停用会断开该频道的玩家' : '启用频道'" @change="value => changeEnabled(record, value)" /><a-tag :color="!record.enabled ? (record.listening ? 'red' : 'gray') : running(record) ? 'green' : 'orange'">{{ !record.enabled ? (record.listening ? '停止异常' : '已停用') : running(record) ? '运行中' : record.listening === null ? '状态未知' : '启动中 / 异常' }}</a-tag></div></template></a-table-column>
            <a-table-column title="地址"><template #cell="{ record }"><div class="address">{{ record.resolvedIp || '等待解析' }}</div><div class="sub">{{ modeText(record.mode) }}<span v-if="record.target"> · {{ record.target }}</span></div><div v-if="record.error" class="error">{{ record.error }}</div></template></a-table-column>
            <a-table-column title="TCP / UDP" :width="150"><template #cell="{ record }">{{ record.tcpPort }} / {{ record.udpPort }}</template></a-table-column>
            <a-table-column title="频道类型" :width="150"><template #cell="{ record }">{{ channelTypeText(record.channelType) }}<div v-if="record.actualType != null && record.actualType !== record.channelType" class="sub">当前 {{ channelTypeText(record.actualType) }}</div></template></a-table-column>
            <a-table-column title="进程内存" :width="115"><template #cell="{ record }">{{ formatMemory(record.memoryBytes) }}</template></a-table-column>
            <a-table-column title="操作" :width="165"><template #cell="{ record }"><a-space><a-button type="text" size="small" @click="edit(record)">编辑</a-button><a-button type="text" size="small" :disabled="!record.enabled" @click="restart(record)">重启</a-button></a-space></template></a-table-column>
          </template>
        </a-table>
      </a-card>
    </template>
    <a-modal v-model:visible="defaultVisible" title="编辑默认通告地址" :footer="false" unmount-on-close>
      <a-form :model="defaultForm" layout="vertical">
        <a-form-item label="地址模式" required><a-radio-group v-model="defaultForm.mode" type="button"><a-radio value="STATIC">固定 IP</a-radio><a-radio value="DDNS">DDNS 域名</a-radio></a-radio-group></a-form-item>
        <a-form-item :label="defaultForm.mode === 'STATIC' ? 'IPv4 地址' : 'DDNS 域名'" required><a-input v-model="defaultForm.target" :placeholder="defaultForm.mode === 'STATIC' ? '例如 192.0.2.10' : '例如 game.example.com'" allow-clear /><template #extra>DDNS 会定期解析 IPv4，解析失败时保留上次成功地址。</template></a-form-item>
        <a-alert type="warning" show-icon>默认地址变化时会重新加载中央频道和桥接服务，并重启所有已启用频道以更新共用中继和 STUN 地址，在线玩家可能断线。</a-alert>
      </a-form>
      <div class="modal-actions"><a-button @click="defaultVisible = false">取消</a-button><a-button type="primary" :loading="defaultSaving" @click="saveDefault">保存并应用</a-button></div>
    </a-modal>
    <a-modal v-model:visible="visible" :title="isNew ? '新增频道' : `编辑频道 ${form.number}`" :footer="false" unmount-on-close>
      <a-form :model="form" layout="vertical">
        <a-form-item v-if="isNew" label="频道号" required><a-select v-model="form.number" placeholder="选择频道" :options="options" allow-search><template #option="{ data }"><div class="channel-name channel-option"><ChannelIcon :number="data.value" :size="28" /><span>{{ data.label }}</span></div></template><template #label="{ data }"><div class="channel-name"><ChannelIcon :number="data.value" :size="24" /><span>{{ data.label }}</span></div></template></a-select></a-form-item>
        <a-form-item label="启用频道"><a-switch v-model="form.enabled" /><span class="field-hint">停用会停止频道并断开该频道的玩家。</span></a-form-item>
        <a-form-item label="通告地址" required><a-radio-group v-model="form.mode" type="button"><a-radio value="INHERIT">跟随默认</a-radio><a-radio value="STATIC">固定 IP</a-radio><a-radio value="DDNS">DDNS 域名</a-radio></a-radio-group></a-form-item>
        <a-form-item v-if="form.mode !== 'INHERIT'" :label="form.mode === 'STATIC' ? 'IPv4 地址' : 'DDNS 域名'" required><a-input v-model="form.target" :placeholder="form.mode === 'STATIC' ? '例如 192.168.1.22' : '例如 game.example.com'" allow-clear /><template #extra>DDNS 需要解析出 IPv4；更新地址后只重启这个频道。</template></a-form-item>
        <a-form-item label="频道类型"><a-select v-model="form.typeChoice" :options="typeOptions" placeholder="选择频道类型" /><template #extra>使用默认值时读取游戏频道配置；只更新运行状态。</template></a-form-item>
        <a-alert type="info">保存会在必要时重启此频道。游戏公共中继和 STUN 服务仍使用默认地址。</a-alert>
      </a-form>
      <div class="modal-actions"><a-button @click="visible = false">取消</a-button><a-button type="primary" :loading="saving" @click="save">保存</a-button></div>
    </a-modal>
  </div>
</template>

<style scoped>
.channels-page { padding: 24px; max-width: 1500px; margin: auto; }
.page-head { display: flex; justify-content: space-between; gap: 20px; align-items: center; margin-bottom: 16px; }
h2 { margin: 0 0 7px; color: var(--color-text-1); font-size: 23px; }
p, .sub { margin: 0; color: var(--color-text-3); font-size: 13px; }
.head-actions { display: flex; gap: 10px; }
.overview-panel { margin-bottom: 16px; overflow: hidden; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-bg-2); }
.overview-grid { display: grid; grid-template-columns: .65fr 1.35fr .85fr repeat(4, minmax(0, 1fr)); }
.overview-item { min-width: 0; padding: 12px 16px; border-right: 1px solid var(--color-border-2); display: flex; flex-direction: column; gap: 4px; }
.overview-item:last-child { border-right: 0; }
.overview-item > span, .default-heading > span { color: var(--color-text-3); font-size: 12px; line-height: 20px; }
.overview-item strong { color: var(--color-text-1); font-size: 18px; line-height: 24px; overflow-wrap: anywhere; }
.overview-item small { color: var(--color-text-3); font-size: 12px; font-weight: normal; }
.default-address small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.overview-item:nth-child(n + 4) { background: var(--color-fill-1); }
.overview-notes { display: flex; flex-wrap: wrap; gap: 4px 20px; padding: 8px 16px; border-top: 1px solid var(--color-border-2); color: var(--color-text-2); font-size: 12px; line-height: 18px; }
.overview-notes span + span::before { content: '·'; margin-right: 8px; color: var(--color-text-3); }
.channel-name { display: flex; align-items: center; gap: 10px; min-width: 0; }
.channel-status { display: flex; align-items: center; gap: 8px; white-space: nowrap; }
.channel-option { padding: 3px 0; }
.default-heading { display: flex; justify-content: space-between; align-items: center; }
.default-heading :deep(.arco-btn) { color: rgb(var(--primary-6)); padding: 2px; }
.notice { margin-bottom: 16px; }
.channel-card { border-radius: 8px; }
.loading-state { display: flex; justify-content: center; padding: 48px 0; }
.address { font-weight: 500; }
.error { color: rgb(var(--danger-6)); font-size: 12px; margin-top: 4px; }
.field-hint { margin-left: 12px; color: var(--color-text-3); font-size: 13px; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
@media (max-width: 1100px) {
  .overview-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
  .overview-item:nth-child(4) { border-right: 0; }
  .overview-item:nth-child(n + 5) { border-top: 1px solid var(--color-border-2); }
  .overview-item:last-child { grid-column: span 2; }
}
@media (max-width: 720px) {
  .channels-page { padding: 16px; }
  .page-head { align-items: flex-start; flex-direction: column; }
  .overview-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .overview-item { padding: 10px 12px; }
  .overview-item:nth-child(n + 3) { border-top: 1px solid var(--color-border-2); }
  .overview-item:nth-child(2n) { border-right: 0; }
  .overview-item:nth-child(2n + 1):not(:last-child) { border-right: 1px solid var(--color-border-2); }
  .overview-item:last-child { grid-column: span 2; flex-direction: row; align-items: baseline; justify-content: space-between; }
  .overview-notes { padding: 8px 12px; }
}
</style>
