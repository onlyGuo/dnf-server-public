<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import * as monaco from 'monaco-editor/esm/vs/editor/editor.api.js'
import 'monaco-editor/esm/vs/basic-languages/sql/sql.contribution.js'
import EditorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import Request from '../../../api/Request'

type Table = { name: string; type: string }
type Column = { name: string; type: string; nullable: boolean; primary: boolean; auto: boolean; defaultValue: string | null; comment: string }
type Metadata = { columns: Column[]; primaryKeys: string[]; view: boolean }
type Result = { columns?: { name: string; type: string }[]; rows?: unknown[][]; affectedRows?: number; error?: string; truncated?: boolean; statement?: string }
type Field = { name: string; type: string; enabled: boolean; isNull: boolean; value: string; binary: boolean }

const schemas = ref<string[]>([])
const expanded = ref('')
const selectedSchema = ref('')
const selectedTable = ref('')
const tableMap = reactive<Record<string, Table[]>>({})
const completion = reactive<Record<string, { table: string; name: string }[]>>({})
const filter = ref('')
const page = ref(1)
const pageSize = ref(50)
const total = ref(0)
const metadata = ref<Metadata | null>(null)
const tableRows = ref<unknown[][]>([])
const results = ref<Result[]>([])
const resultIndex = ref(0)
const tab = ref<'sql' | 'table' | 'structure'>('sql')
const busy = ref(false)
const running = ref(false)
const editorHost = ref<HTMLElement>()
const modalVisible = ref(false)
const saving = ref(false)
const isInsert = ref(false)
const fields = ref<Field[]>([])
const rowKeys = ref<Record<string, unknown>>({})
let editor: monaco.editor.IStandaloneCodeEditor | undefined
let provider: monaco.IDisposable | undefined

const currentResult = computed(() => results.value[resultIndex.value])
const filteredTables = (schema: string) => (tableMap[schema] || []).filter(t => t.name.toLowerCase().includes(filter.value.trim().toLowerCase()))
const displayedRows = computed(() => toRecords(tableRows.value))
const resultRows = computed(() => toRecords(currentResult.value?.rows || []))
function toRecords(rows: unknown[][]) { return rows.map((cells, index) => ({ _index: index, cells })) }
function q(name: string) { return '`' + name.replace(/`/g, '``') + '`' }
function display(value: any): string {
  if (value == null) return 'NULL'
  if (typeof value === 'object' && value.encoding === 'base64') return `BLOB · ${Math.floor(value.value.length * 3 / 4)} B`
  return String(value)
}
function params(schema: string, table?: string) {
  const result: Record<string, string> = { schema }
  if (table) result.table = table
  return { params: result }
}
async function refreshSchemas() {
  const response = await Request.get<string[]>('/api/v1/admin/database/schemas')
  schemas.value = response.data
  if (selectedSchema.value && schemas.value.includes(selectedSchema.value)) {
    await selectSchema(selectedSchema.value, true)
    if (selectedTable.value && !tableMap[selectedSchema.value]?.some(table => table.name === selectedTable.value)) {
      selectedTable.value = ''; metadata.value = null; tableRows.value = []; tab.value = 'sql'
    }
  } else if (selectedSchema.value) {
    selectedSchema.value = ''; selectedTable.value = ''; expanded.value = ''; metadata.value = null; tableRows.value = []; tab.value = 'sql'
  }
}
async function selectSchema(schema: string, force = false) {
  if (selectedSchema.value !== schema) {
    selectedTable.value = ''; metadata.value = null; tableRows.value = []
    if (tab.value !== 'sql') tab.value = 'sql'
  }
  selectedSchema.value = schema
  expanded.value = expanded.value === schema && !force ? '' : schema
  if (!tableMap[schema] || force) {
    const [tables, columns] = await Promise.all([
      Request.get<Table[]>('/api/v1/admin/database/tables', params(schema)),
      Request.get<{ columns: { table: string; name: string }[] }>('/api/v1/admin/database/completion', params(schema))
    ])
    tableMap[schema] = tables.data
    completion[schema] = columns.data.columns
  }
}
async function selectTable(schema: string, table: string) {
  selectedSchema.value = schema; selectedTable.value = table; page.value = 1; tab.value = 'table'
  await loadRows()
}
async function loadRows() {
  if (!selectedSchema.value || !selectedTable.value) return
  busy.value = true
  try {
    const response: any = await Request.get('/api/v1/admin/database/rows', { params: { schema: selectedSchema.value, table: selectedTable.value, page: page.value, pageSize: pageSize.value }, timeout: 60000 })
    metadata.value = response.data.metadata
    tableRows.value = response.data.rows
    total.value = Number(response.data.total)
  } finally { busy.value = false }
}
async function runSql() {
  if (!editor || running.value) return
  const selection = editor.getSelection()
  const sql = selection && !selection.isEmpty() ? editor.getModel()?.getValueInRange(selection) : editor.getValue()
  if (!sql?.trim()) { Message.warning('请输入 SQL'); return }
  running.value = true
  try {
    const response: any = await Request.post('/api/v1/admin/database/execute', { schema: selectedSchema.value, sql }, { timeout: 180000 })
    results.value = response.data.results
    resultIndex.value = 0
  } finally { running.value = false }
}
function showSqlForTable() {
  if (!selectedTable.value) return
  tab.value = 'sql'
  editor?.setValue(`SELECT * FROM ${q(selectedSchema.value)}.${q(selectedTable.value)} LIMIT 100;`)
  editor?.focus()
}
function openRow(row?: { cells: unknown[] }) {
  if (!metadata.value) return
  isInsert.value = !row
  const keys: Record<string, unknown> = {}
  fields.value = metadata.value.columns.map((col, index) => {
    const original: any = row?.cells[index]
    if (row && col.primary) keys[col.name] = original
    const binary = original && typeof original === 'object' && original.encoding === 'base64'
    return { name: col.name, type: col.type, enabled: false, isNull: original === null, value: binary ? original.value : original == null ? '' : String(original), binary: !!binary || /BINARY|BLOB/i.test(col.type) }
  })
  rowKeys.value = keys
  modalVisible.value = true
}
async function saveRow() {
  const values: Record<string, unknown> = {}
  for (const field of fields.value.filter(f => f.enabled)) values[field.name] = field.isNull ? null : field.binary ? { encoding: 'base64', value: field.value } : field.value
  if (!isInsert.value && !Object.keys(values).length) { Message.warning('请选择需要修改的字段'); return }
  saving.value = true
  try {
    await Request.post('/api/v1/admin/database/rows', { schema: selectedSchema.value, table: selectedTable.value, operation: isInsert.value ? 'insert' : 'update', keys: rowKeys.value, values })
    modalVisible.value = false; Message.success('已保存'); await loadRows()
  } finally { saving.value = false }
}
function deleteRow(row: { cells: unknown[] }) {
  if (!metadata.value) return
  const keys: Record<string, unknown> = {}
  metadata.value.columns.forEach((col, index) => { if (col.primary) keys[col.name] = row.cells[index] })
  Modal.warning({ title: '删除数据行', content: '确定删除这一行吗？', hideCancel: false, okText: '删除', onOk: async () => {
    await Request.post('/api/v1/admin/database/rows', { schema: selectedSchema.value, table: selectedTable.value, operation: 'delete', keys, values: {} })
    Message.success('已删除'); await loadRows()
  } })
}
onMounted(async () => {
  await nextTick()
  ;(globalThis as any).MonacoEnvironment = { getWorker: () => new EditorWorker() }
  if (editorHost.value) {
    editor = monaco.editor.create(editorHost.value, { value: '-- 选择数据库后输入 SQL，Ctrl/Cmd + Enter 执行\nSELECT 1;', language: 'sql', theme: 'vs', automaticLayout: true, fontSize: 14, minimap: { enabled: false }, scrollBeyondLastLine: false })
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, runSql)
    provider = monaco.languages.registerCompletionItemProvider('sql', {
      triggerCharacters: ['.'],
      provideCompletionItems(model, position) {
        const word = model.getWordUntilPosition(position)
        const range = { startLineNumber: position.lineNumber, endLineNumber: position.lineNumber, startColumn: word.startColumn, endColumn: word.endColumn }
        const schema = selectedSchema.value
        const keywords = ['SELECT', 'FROM', 'WHERE', 'JOIN', 'LEFT JOIN', 'INNER JOIN', 'ORDER BY', 'GROUP BY', 'LIMIT', 'INSERT INTO', 'UPDATE', 'DELETE FROM', 'CREATE TABLE', 'ALTER TABLE', 'SHOW TABLES']
        return { suggestions: [
          ...keywords.map(name => ({ label: name, kind: monaco.languages.CompletionItemKind.Keyword, insertText: name, range })),
          ...schemas.value.map(name => ({ label: name, detail: '数据库', kind: monaco.languages.CompletionItemKind.Module, insertText: q(name), range })),
          ...(tableMap[schema] || []).map(t => ({ label: t.name, detail: `${schema} · ${t.type}`, kind: monaco.languages.CompletionItemKind.Class, insertText: q(t.name), range })),
          ...(completion[schema] || []).map(c => ({ label: c.name, detail: `${c.table}.${c.name}`, kind: monaco.languages.CompletionItemKind.Field, insertText: q(c.name), range }))
        ] }
      }
    })
  }
  await refreshSchemas()
})
onUnmounted(() => { provider?.dispose(); editor?.dispose() })
</script>

<template>
  <div class="db-page">
    <div class="page-title"><div><h2>数据库管理</h2><p>浏览数据表、编辑数据并执行 SQL</p></div><a-button @click="refreshSchemas">刷新数据库</a-button></div>
    <div class="workspace">
      <aside class="explorer">
        <div class="explorer-head"><strong>数据库</strong><span>{{ schemas.length }} 个</span></div>
        <a-input v-model="filter" allow-clear placeholder="搜索表名" size="small" />
        <div class="tree">
          <div v-for="schema in schemas" :key="schema">
            <button class="schema-row" :class="{ chosen: selectedSchema === schema }" @click="selectSchema(schema)"><span>{{ expanded === schema ? '▾' : '▸' }}</span><span class="schema-icon">◫</span><strong>{{ schema }}</strong></button>
            <div v-if="expanded === schema" class="table-list">
              <button v-for="table in filteredTables(schema)" :key="table.name" class="table-row" :class="{ chosen: selectedSchema === schema && selectedTable === table.name }" :title="table.name" @click="selectTable(schema, table.name)"><span>{{ table.type === 'VIEW' ? '◈' : '▦' }}</span>{{ table.name }}</button>
              <div v-if="!filteredTables(schema).length" class="empty-tree">没有匹配的表</div>
            </div>
          </div>
        </div>
      </aside>
      <main class="main-pane">
        <div class="toolbar">
          <div class="tabs"><button :class="{ active: tab === 'sql' }" @click="tab = 'sql'">SQL 控制台</button><button :disabled="!selectedTable" :class="{ active: tab === 'table' }" @click="tab = 'table'">表数据</button><button :disabled="!selectedTable" :class="{ active: tab === 'structure' }" @click="tab = 'structure'">表结构</button></div>
          <div class="context">{{ selectedSchema || '未选择数据库' }}<template v-if="selectedTable"> / {{ selectedTable }}</template></div>
        </div>
        <div v-show="tab === 'sql'" class="console">
          <div class="action-bar"><a-button type="primary" size="small" :loading="running" @click="runSql">▶ 执行 SQL</a-button><span>选中内容优先 · Ctrl / ⌘ + Enter</span><a-button v-if="selectedTable" size="small" @click="showSqlForTable">查询当前表</a-button></div>
          <div ref="editorHost" class="editor"></div>
          <section class="results"><div class="result-head"><strong>执行结果</strong><div v-if="results.length > 1" class="result-tabs"><button v-for="(_, index) in results" :key="index" :class="{ active: resultIndex === index }" @click="resultIndex = index">{{ index + 1 }}</button></div><span v-if="currentResult?.truncated">仅显示前 1000 行</span></div>
            <div v-if="!currentResult" class="placeholder">执行 SQL 后，结果会显示在这里</div>
            <a-alert v-else-if="currentResult.error" type="error">{{ currentResult.error }}</a-alert>
            <div v-else-if="currentResult.affectedRows !== undefined" class="result-message">执行成功，影响 {{ currentResult.affectedRows }} 行</div>
            <a-table v-else :data="resultRows" :pagination="false" size="small" row-key="_index" :scroll="{ x: Math.max(800, (currentResult.columns?.length || 0) * 160), y: 310 }"><template #columns><a-table-column v-for="(col, index) in currentResult.columns" :key="index" :title="col.name" :width="160"><template #cell="{ record }"><span class="cell" :title="display(record.cells[index])">{{ display(record.cells[index]) }}</span></template></a-table-column></template></a-table>
          </section>
        </div>
        <div v-if="tab === 'table'" class="table-content">
          <div class="action-bar"><a-button size="small" @click="loadRows">刷新</a-button><a-button size="small" type="primary" :disabled="metadata?.view" @click="openRow()">新增行</a-button><a-button size="small" @click="showSqlForTable">在 SQL 中查询</a-button><span v-if="metadata && !metadata.primaryKeys.length">此表没有主键，行修改和删除请使用 SQL</span></div>
          <a-table :data="displayedRows" :loading="busy" :pagination="false" size="small" row-key="_index" :scroll="{ x: Math.max(800, (metadata?.columns.length || 0) * 160 + 110), y: 480 }"><template #columns><a-table-column title="操作" :width="110" fixed="left"><template #cell="{ record }"><a-button type="text" size="mini" :disabled="!metadata?.primaryKeys.length || metadata?.view" @click="openRow(record)">编辑</a-button><a-button type="text" size="mini" status="danger" :disabled="!metadata?.primaryKeys.length || metadata?.view" @click="deleteRow(record)">删除</a-button></template></a-table-column><a-table-column v-for="(col, index) in metadata?.columns" :key="col.name" :title="col.name" :width="160"><template #cell="{ record }"><span class="cell" :class="{ null: record.cells[index] == null }" :title="display(record.cells[index])">{{ display(record.cells[index]) }}</span></template></a-table-column></template></a-table>
          <div class="pagination"><span>共 {{ total }} 行</span><a-pagination v-model:current="page" :page-size="pageSize" :total="total" show-jumper @change="loadRows" /></div>
        </div>
        <div v-if="tab === 'structure'" class="table-content"><a-table :data="metadata?.columns || []" :pagination="false" size="small" row-key="name"><template #columns><a-table-column title="字段" data-index="name"/><a-table-column title="类型" data-index="type"/><a-table-column title="主键"><template #cell="{ record }">{{ record.primary ? '是' : '—' }}</template></a-table-column><a-table-column title="可为空"><template #cell="{ record }">{{ record.nullable ? '是' : '否' }}</template></a-table-column><a-table-column title="默认值" data-index="defaultValue"/><a-table-column title="备注" data-index="comment"/></template></a-table></div>
      </main>
    </div>
    <a-modal v-model:visible="modalVisible" :title="isInsert ? '新增数据行' : '编辑数据行'" :footer="false" width="min(720px, calc(100vw - 32px))" unmount-on-close><p class="modal-note">勾选需要填写的字段；二进制字段使用 Base64。</p><div class="field-list"><div v-for="field in fields" :key="field.name" class="field-row"><a-checkbox v-model="field.enabled"/><div class="field-label"><strong>{{ field.name }}</strong><small>{{ field.type }}</small></div><a-input v-model="field.value" :disabled="!field.enabled || field.isNull" :placeholder="field.binary ? 'Base64' : '字段值'" /><a-checkbox v-model="field.isNull" :disabled="!field.enabled">NULL</a-checkbox></div></div><div class="modal-footer"><a-button @click="modalVisible = false">取消</a-button><a-button type="primary" :loading="saving" @click="saveRow">保存</a-button></div></a-modal>
  </div>
</template>

<style scoped>
.db-page{padding:22px;height:100%;box-sizing:border-box;display:flex;flex-direction:column;background:var(--color-fill-1)}.page-title{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px}.page-title h2{font-size:21px;margin:0 0 5px}.page-title p{margin:0;color:var(--color-text-3);font-size:13px}.workspace{display:flex;flex:1;min-height:560px;overflow:hidden;border:1px solid var(--color-border-2);border-radius:8px;background:var(--color-bg-2)}.explorer{width:250px;flex-shrink:0;padding:14px 12px;border-right:1px solid var(--color-border-2);display:flex;flex-direction:column}.explorer-head{display:flex;justify-content:space-between;margin:0 2px 12px}.explorer-head span{font-size:12px;color:var(--color-text-3)}.tree{overflow:auto;margin:12px -5px}.schema-row,.table-row{border:0;background:transparent;color:var(--color-text-2);text-align:left;cursor:pointer;width:100%;display:flex;align-items:center;gap:8px;padding:7px 8px;border-radius:5px;font-size:13px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.schema-row:hover,.table-row:hover{background:var(--color-fill-2)}.schema-row.chosen,.table-row.chosen{background:rgb(var(--primary-1));color:rgb(var(--primary-6))}.schema-icon{color:#4783d7}.table-list{padding-left:22px}.table-row span{color:#8096ad}.empty-tree{padding:8px;color:var(--color-text-3);font-size:12px}.main-pane{min-width:0;flex:1;display:flex;flex-direction:column}.toolbar{height:48px;display:flex;align-items:center;border-bottom:1px solid var(--color-border-2);padding:0 16px;gap:16px}.tabs{display:flex;align-self:stretch}.tabs button{border:0;background:none;padding:0 13px;color:var(--color-text-2);cursor:pointer;white-space:nowrap}.tabs button.active{color:rgb(var(--primary-6));border-bottom:2px solid rgb(var(--primary-6))}.tabs button:disabled{opacity:.4;cursor:not-allowed}.context{margin-left:auto;font-size:12px;color:var(--color-text-3);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.console{flex:1;min-height:0;display:flex;flex-direction:column}.action-bar{display:flex;align-items:center;gap:10px;padding:11px 16px;overflow:auto}.action-bar span{font-size:12px;color:var(--color-text-3);white-space:nowrap}.editor{height:250px;min-height:180px;border-top:1px solid var(--color-border-1);border-bottom:1px solid var(--color-border-2)}.results{flex:1;min-height:150px;overflow:auto}.result-head{display:flex;align-items:center;gap:12px;padding:12px 16px}.result-head span,.result-message{font-size:12px;color:var(--color-text-3)}.result-tabs{display:flex;gap:4px}.result-tabs button{border:0;border-radius:4px;padding:3px 8px;cursor:pointer}.result-tabs .active{color:rgb(var(--primary-6));background:rgb(var(--primary-1))}.placeholder,.result-message{padding:24px 16px;color:var(--color-text-3)}.table-content{padding-bottom:16px;overflow:auto}.cell{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.cell.null{color:var(--color-text-4);font-style:italic}.pagination{display:flex;align-items:center;justify-content:space-between;padding:16px;font-size:12px;color:var(--color-text-3)}.modal-note{color:var(--color-text-3);font-size:12px}.field-list{max-height:55vh;overflow:auto}.field-row{display:grid;grid-template-columns:24px 150px 1fr 62px;align-items:center;gap:8px;padding:6px 0}.field-label{display:flex;flex-direction:column;overflow:hidden}.field-label strong{overflow:hidden;text-overflow:ellipsis;font-size:13px}.field-label small{color:var(--color-text-3)}.modal-footer{display:flex;justify-content:flex-end;gap:8px;margin-top:18px}@media(max-width:800px){.db-page{padding:12px}.explorer{width:150px}.context{display:none}.toolbar{padding:0 6px}.tabs button{padding:0 8px}.action-bar{padding:10px 8px}.action-bar span{display:none}}
</style>
