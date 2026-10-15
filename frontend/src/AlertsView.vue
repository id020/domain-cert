<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from './api'
import { Bell, RefreshCw, Send, X } from 'lucide-vue-next'

interface Delivery { id:number; assetType:string; assetId:number; alertDays:number; expirationDate:string; recipient:string; status:string; errorMessage:string|null; sentAt:string|null }
interface MailContent { subject:string; body:string }
const rows = ref<Delivery[]>([])
const loading = ref(false)
const preview = ref<MailContent | null>(null)
const recipient = ref('')
const dialog = ref<HTMLDialogElement | null>(null)
const sending = ref(false)
const emit = defineEmits<{ notice: [message:string] }>()

/** 读取最近提醒发送记录，包含 SMTP 失败及失败原因。 */
async function load() {
  loading.value = true
  try { rows.value = await api<Delivery[]>('/alerts') }
  catch (error) { emit('notice', error instanceof Error ? error.message : '提醒记录读取失败') }
  finally { loading.value = false }
}

/** 先读取真正用于自动提醒的同一邮件模板，再展示预览和测试发送操作。 */
async function openMailTest() {
  try {
    preview.value = await api<MailContent>('/alerts/test-preview')
    dialog.value?.showModal()
  } catch (error) { emit('notice', error instanceof Error ? error.message : '邮件内容读取失败') }
}

/** 只向操作人员指定的邮箱发送一封测试信，不会创建到期提醒投递记录。 */
async function sendTestMail() {
  if (!recipient.value.trim()) { emit('notice', '请填写测试邮件收件人'); return }
  sending.value = true
  try {
    await api<void>('/alerts/test-email', { method: 'POST', body: JSON.stringify({ recipient: recipient.value.trim() }) })
    emit('notice', `测试邮件已发送至 ${recipient.value.trim()}`)
    dialog.value?.close()
  } catch (error) { emit('notice', error instanceof Error ? error.message : '测试邮件发送失败') }
  finally { sending.value = false }
}
onMounted(load)
</script>

<template>
  <section class="module-view">
    <header class="module-header">
      <div><p class="eyebrow">自动化 / EXPIRY ALERTS</p><h1>提醒中心</h1><p>每天上午 08:00 检查到期资产，并对 30、14、7、1 天档位发送邮件。</p></div>
      <div class="module-actions"><button class="button secondary" @click="openMailTest"><Send :size="15"/>测试邮件内容</button><button class="button secondary" :disabled="loading" @click="load"><RefreshCw :size="16"/>刷新记录</button></div>
    </header>
    <div class="alert-explainer"><Bell :size="18"/><div><b>邮件投递状态</b><span>系统会记录成功和失败；失败投递会在后续检查中重试。全局收件人由 application.yml 的 app.alert.recipients 配置，域名可单独设置收件人。</span></div></div>
    <div class="module-card">
      <div v-if="loading" class="module-empty">正在读取提醒记录…</div>
      <div v-else-if="!rows.length" class="module-empty">暂无投递记录。到达提醒档位后，系统会自动尝试发送。</div>
      <div v-else class="asset-table-wrap"><table class="asset-table alert-table"><thead><tr><th>状态</th><th>资产</th><th>到期时间</th><th>提醒档位</th><th>收件人</th><th>投递时间 / 错误</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td><span class="status-pill" :class="row.status==='SENT'?'status-ok':'status-warning'"><i/>{{row.status==='SENT'?'已发送':'发送失败'}}</span></td><td>{{row.assetType==='DOMAIN'?'域名':'证书'}} #{{row.assetId}}</td><td>{{row.expirationDate}}</td><td>提前 {{row.alertDays}} 天</td><td>{{row.recipient}}</td><td>{{row.sentAt ? new Date(row.sentAt).toLocaleString('zh-CN') : row.errorMessage || '等待重试'}}</td></tr></tbody></table></div>
    </div>
    <dialog ref="dialog" class="mail-test-dialog">
      <form method="dialog" class="mail-dialog-close"><button class="button secondary small" aria-label="关闭"><X :size="15"/></button></form>
      <p class="eyebrow">邮件模板预览</p><h2>到期提醒测试邮件</h2>
      <label class="mail-subject"><b>邮件主题</b><input :value="preview?.subject" readonly/></label>
      <label class="mail-body"><b>邮件正文</b><textarea :value="preview?.body" readonly rows="5"/></label>
      <form class="mail-send-form" @submit.prevent="sendTestMail">
        <label><b>测试收件人</b><input v-model="recipient" type="email" required placeholder="name@example.com" autocomplete="email"/></label>
        <button class="button primary" :disabled="sending"><Send :size="15"/>{{sending?'发送中…':'发送测试邮件'}}</button>
      </form>
      <p class="mail-dialog-hint">预览不会发送邮件；点击“发送测试邮件”后将按上方模板向指定邮箱发送。</p>
    </dialog>
  </section>
</template>
