<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api, type DomainAsset } from './api'
import { Plus, RefreshCw, Search, Trash2, RotateCw, ChevronDown } from 'lucide-vue-next'

const emit = defineEmits<{ notice: [message: string] }>()
const rows = ref<DomainAsset[]>([])
const query = ref('')
const newName = ref('')
const loading = ref(false)
const adding = ref(false)
let lookupPoller: number | undefined
const selected = ref<DomainAsset | null>(null)
const recipientInput = ref('')
const filtered = computed(() => rows.value.filter(row => row.name.includes(query.value.trim().toLowerCase())))

/** 请求域名资产列表；错误通过全局提示交给外层应用展示。 */
async function load() {
  loading.value = true
  try { rows.value = await api<DomainAsset[]>('/domains') }
  catch (error) { emit('notice', error instanceof Error ? error.message : '域名加载失败') }
  finally { loading.value = false }
}

/** 新增记录先立即显示，后台查询结束后定时同步状态和公开注册字段。 */
async function refreshPending() {
  if (!rows.value.some(row => row.lookupStatus === 'PENDING')) return
  try {
    const latest = await api<DomainAsset[]>('/domains')
    rows.value = latest
    if (selected.value) selected.value = latest.find(row => row.id === selected.value?.id) || selected.value
  } catch {
    // 轮询只是辅助更新；失败时保留已有列表，用户仍可点击手动刷新。
  }
}

/** 创建资产后使用服务端 RDAP/DNS 查询结果刷新本地列表。 */
async function add() {
  const name = newName.value.trim().toLowerCase()
  if (!name) { emit('notice', '请输入域名'); return }
  if (!/^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/i.test(name)) {
    emit('notice', '域名格式无效，请输入 example.com 这样的完整域名'); return
  }
  adding.value = true
  try {
    const created = await api<DomainAsset>('/domains', { method: 'POST', body: JSON.stringify({ name }) })
    rows.value = [created, ...rows.value.filter(row => row.id !== created.id)]
    selected.value = created
    newName.value = ''
    emit('notice', '域名已添加，正在后台查询注册信息和 DNS')
  } catch (error) { emit('notice', error instanceof Error ? error.message : '添加失败') }
  finally { adding.value = false }
}

/** 手动触发域名刷新并立即展示刷新后的注册及 DNS 数据。 */
async function refresh(id: number) {
  try {
    const updated = await api<DomainAsset>(`/domains/${id}/refresh`, { method: 'POST' })
    rows.value = rows.value.map(row => row.id === id ? updated : row)
    if (selected.value?.id === id) selected.value = updated
    emit('notice', '域名信息已刷新')
  } catch (error) { emit('notice', error instanceof Error ? error.message : '刷新失败') }
}

/** 将单域名收件人设置保存到后端；留空后恢复全局默认收件人。 */
async function saveRecipients() {
  if (!selected.value) return
  try {
    const updated = await api<DomainAsset>(`/domains/${selected.value.id}/recipients`, {
      method: 'PUT', body: JSON.stringify({ recipients: recipientInput.value }),
    })
    rows.value = rows.value.map(row => row.id === updated.id ? updated : row)
    selected.value = updated
    emit('notice', '域名提醒收件人已保存')
  } catch (error) { emit('notice', error instanceof Error ? error.message : '保存收件人失败') }
}

/** 删除操作由浏览器二次确认，后端删除后再从列表移除。 */
async function remove(row: DomainAsset) {
  if (!window.confirm(`确定删除 ${row.name} 吗？`)) return
  try {
    await api<void>(`/domains/${row.id}`, { method: 'DELETE' })
    rows.value = rows.value.filter(item => item.id !== row.id)
    if (selected.value?.id === row.id) selected.value = null
    emit('notice', '域名已删除')
  } catch (error) { emit('notice', error instanceof Error ? error.message : '删除失败') }
}

/** 用 ISO 日期计算剩余天数，未知注册到期日保持空状态。 */
function remainingDays(date: string | null): number | null {
  if (!date) return null
  return Math.ceil((new Date(`${date}T00:00:00Z`).getTime() - Date.now()) / 86_400_000)
}

/** 后端以 JSON 文本保存 DNS 结果，畸形数据时安全返回空列表。 */
function dnsRecords(domain: DomainAsset): Array<{ type: string; value: string }> {
  try { return JSON.parse(domain.dnsRecordsJson || '[]') }
  catch { return [] }
}

onMounted(() => {
  load()
  lookupPoller = window.setInterval(refreshPending, 2500)
})
onUnmounted(() => { if (lookupPoller) window.clearInterval(lookupPoller) })
</script>

<template>
  <section class="module-view">
    <header class="module-header">
      <div><p class="eyebrow">资产管理 / DOMAIN INVENTORY</p><h1>域名管理</h1><p>管理注册信息、名称服务器和 DNS 记录。</p></div>
      <button class="button secondary" :disabled="loading" @click="load"><RefreshCw :size="16"/>刷新列表</button>
    </header>
    <div class="module-toolbar">
      <label class="search-box"><Search :size="16"/><input v-model="query" placeholder="搜索域名"/></label>
      <form class="inline-add" @submit.prevent="add"><input v-model="newName" required autocomplete="url" placeholder="输入域名，例如 example.com"/><button class="button primary" :disabled="adding"><Plus :size="16"/>{{adding?'正在添加…':'添加域名'}}</button></form>
    </div>
    <div class="module-card">
      <div v-if="loading" class="module-empty">正在读取域名资产…</div>
      <div v-else-if="!filtered.length" class="module-empty">还没有域名。添加域名后会自动查询公开注册信息和 DNS 记录。</div>
      <div v-else class="asset-table-wrap"><table class="asset-table"><thead><tr><th>域名</th><th>注册商</th><th>注册日期</th><th>到期日期</th><th>查询状态</th><th>刷新时间</th><th></th></tr></thead><tbody>
        <tr v-for="row in filtered" :key="row.id" :class="{selected:selected?.id===row.id}" @click="selected=row;recipientInput=row.alertRecipients||''"><td><b>{{row.name}}</b></td><td>{{row.registrar || '未知（注册局未公开或查询失败）'}}</td><td>{{row.registeredAt || '未知（公开查询未提供）'}}</td><td><span>{{row.expiresAt || '未知（公开查询未提供）'}}</span><small v-if="remainingDays(row.expiresAt)!==null">{{remainingDays(row.expiresAt)}} 天后</small></td><td><span class="status-pill" :class="row.lookupStatus==='SUCCESS'?'status-ok':row.lookupStatus==='FAILED'?'status-warning':'status-pending'"><i/>{{row.lookupStatus==='SUCCESS'?'已同步':row.lookupStatus==='FAILED'?'查询失败':row.lookupStatus==='PENDING'?'查询中':'部分数据'}}</span></td><td>{{row.lastRefreshedAt ? new Date(row.lastRefreshedAt).toLocaleString('zh-CN') : '正在首次查询'}}</td><td><button class="icon-action" title="刷新" @click.stop="refresh(row.id)"><RotateCw :size="15"/></button><button class="icon-action danger" title="删除" @click.stop="remove(row)"><Trash2 :size="15"/></button></td></tr>
      </tbody></table></div>
    </div>
    <aside v-if="selected" class="module-card domain-detail">
      <div class="detail-heading"><div><span class="eyebrow">域名详情</span><h2>{{selected.name}}</h2></div><button class="button secondary" @click="refresh(selected!.id)"><RefreshCw :size="15"/>立即刷新</button></div>
      <p v-if="selected.lookupMessage" class="inline-warning">{{selected.lookupMessage}}；没有公开返回的数据会显示为“未知”。</p>
      <div class="detail-grid"><div><span>注册商</span><b>{{selected.registrar || '未知'}}</b></div><div><span>注册日期</span><b>{{selected.registeredAt || '未知'}}</b></div><div><span>注册到期</span><b>{{selected.expiresAt || '未知'}}</b></div><div><span>名称服务器</span><b>{{selected.nameServers || '未知'}}</b></div></div>
      <form class="recipient-form" @submit.prevent="saveRecipients"><label><b>到期提醒收件人</b><span>最多五个邮箱，逗号分隔；留空时使用全局默认收件人。</span></label><input v-model="recipientInput" placeholder="ops@example.com, owner@example.com"/><button class="button secondary">保存收件人</button></form>
      <div class="dns-heading">DNS 记录 <ChevronDown :size="15"/></div>
      <div v-if="dnsRecords(selected).length" class="dns-grid"><div v-for="(record,index) in dnsRecords(selected)" :key="`${record.type}-${index}`"><span>{{record.type}}</span><code>{{record.value}}</code></div></div><div v-else class="module-empty compact">当前未查询到公开 DNS 记录。</div>
    </aside>
  </section>
</template>
