<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from './api'
import { Activity, KeyRound, ShieldCheck, Users, UserPlus, Trash2, RefreshCw, Save, Mail, RotateCw, Eye, EyeOff } from 'lucide-vue-next'

interface AuditRow { id:number; actorEmail:string; action:string; assetType:string|null; assetId:number|null; details:string|null; createdAt:string }
interface Account { id:number; username:string; displayName:string; role:'ADMIN'|'OPERATOR'|'VIEWER'; enabled:boolean; createdAt:string|null }
interface AccountDraft { displayName:string; role:Account['role'] }
interface RuntimeSettings { smtpHost:string; smtpPort:number; smtpUsername:string; smtpPasswordConfigured:boolean; smtpAuth:boolean; startTls:boolean; fromAddress:string; globalRecipients:string; certificateEncryptionKeyConfigured:boolean }

const auditRows = ref<AuditRow[]>([])
const accounts = ref<Account[]>([])
const currentAccount = ref<Account | null>(null)
const drafts = reactive<Record<number, AccountDraft>>({})
const busy = ref(false)
const loadingUsers = ref(false)
const createForm = reactive({ username:'', displayName:'', password:'', role:'OPERATOR' as Account['role'] })
const errorMessage = ref('')
const settingsSuccess = ref('')
const savingSettings = ref(false)
const runtimeSettings = ref<RuntimeSettings | null>(null)
// 加密密钥默认遮挡；管理员可在生成、核对和保存前切换明文显示。
const showCertificateKey = ref(false)
const settingsForm = reactive({ smtpHost:'', smtpPort:587, smtpUsername:'', smtpPassword:'', smtpAuth:true, startTls:true, fromAddress:'', globalRecipients:'', certificateEncryptionKey:'' })
const resetTarget = ref<Account | null>(null)
const resetPassword = ref('')
const resetDialog = ref<HTMLDialogElement | null>(null)
const emit = defineEmits<{ notice:[message:string] }>()

const roleNames: Record<Account['role'], string> = { ADMIN:'管理员', OPERATOR:'运维', VIEWER:'只读' }
const roleDescriptions = [
  { name:'管理员', role:'ADMIN', permissions:'管理用户、角色和系统设置；管理全部资产并查看审计记录。' },
  { name:'运维', role:'OPERATOR', permissions:'维护域名和证书，可签发、上传及下载私钥。' },
  { name:'只读', role:'VIEWER', permissions:'查看域名、证书和提醒记录，不可修改资产或下载私钥。' },
]

/** 读取当前账户，非管理员仍可查看个人角色并修改自己的密码。 */
async function loadCurrent() {
  try { currentAccount.value = await api<Account>('/users/me') }
  catch (error) { errorMessage.value = error instanceof Error ? error.message : '账户信息读取失败' }
}

/** 管理员加载所有用户资料，并创建用于编辑的本地表单草稿。 */
async function loadUsers() {
  loadingUsers.value = true
  try {
    accounts.value = await api<Account[]>('/users')
    for (const account of accounts.value) drafts[account.id] = { displayName:account.displayName, role:account.role }
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : '用户列表读取失败' }
  finally { loadingUsers.value = false }
}

/** 管理员读取审计记录；服务端只返回最近 50 条。 */
async function loadAudit() {
  try { auditRows.value = await api<AuditRow[]>('/audit?limit=50') }
  catch (error) { emit('notice', error instanceof Error ? error.message : '审计记录读取失败') }
}

/** 管理员加载动态设置；API 只返回敏感字段是否已配置，不返回其明文。 */
async function loadSettings() {
  try {
    const config = await api<RuntimeSettings>('/settings')
    runtimeSettings.value = config
    settingsForm.smtpHost = config.smtpHost
    settingsForm.smtpPort = config.smtpPort
    settingsForm.smtpUsername = config.smtpUsername
    settingsForm.smtpPassword = ''
    settingsForm.smtpAuth = config.smtpAuth
    settingsForm.startTls = config.startTls
    settingsForm.fromAddress = config.fromAddress
    settingsForm.globalRecipients = config.globalRecipients
    settingsForm.certificateEncryptionKey = ''
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : '系统配置读取失败' }
}

/** 将邮件设置和可选密钥轮换一次性保存；空白口令字段表示保留原值。 */
async function saveSettings() {
  errorMessage.value = ''
  settingsSuccess.value = ''
  savingSettings.value = true
  try {
    const config = await api<RuntimeSettings>('/settings', { method:'PUT', body:JSON.stringify(settingsForm) })
    runtimeSettings.value = config
    settingsForm.smtpPassword = ''
    settingsForm.certificateEncryptionKey = ''
    settingsSuccess.value = '系统配置已保存并立即生效。'
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : '系统配置保存失败' }
  finally { savingSettings.value = false }
}

/** 生成新的随机 AES-256 数据密钥；保存时后端会轮换加密所有证书私钥。 */
function generateCertificateKey() {
  const bytes = crypto.getRandomValues(new Uint8Array(32))
  settingsForm.certificateEncryptionKey = btoa(String.fromCharCode(...bytes))
  showCertificateKey.value = true
}

/** 创建用户并刷新用户表；密码只在创建请求内发送。 */
async function createAccount() {
  busy.value = true
  try {
    const account = await api<Account>('/users', { method:'POST', body:JSON.stringify(createForm) })
    accounts.value.push(account)
    drafts[account.id] = { displayName:account.displayName, role:account.role }
    createForm.username = ''; createForm.displayName = ''; createForm.password = ''; createForm.role = 'OPERATOR'
    emit('notice', `账户 ${account.username} 已创建`)
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : '创建账户失败' }
  finally { busy.value = false }
}

/** 保存姓名和角色；服务端会保护最后一个管理员及当前管理员角色。 */
async function saveAccount(account: Account) {
  const draft = drafts[account.id]
  if (!draft) return
  busy.value = true
  try {
    const updated = await api<Account>(`/users/${account.id}`, { method:'PUT', body:JSON.stringify(draft) })
    accounts.value = accounts.value.map(row => row.id === updated.id ? updated : row)
    emit('notice', `账户 ${updated.username} 已更新`)
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : '账户更新失败' }
  finally { busy.value = false }
}

/** 管理员切换账户可用状态。 */
async function toggleAccount(account: Account) {
  try {
    const updated = await api<Account>(`/users/${account.id}/enabled`, {
      method:'PATCH', body:JSON.stringify({ enabled:!account.enabled }),
    })
    accounts.value = accounts.value.map(row => row.id === updated.id ? updated : row)
    emit('notice', updated.enabled ? '账户已启用' : '账户已停用')
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : '账户状态更新失败' }
}

/** 打开管理员重置其他用户密码窗口。 */
function openPasswordReset(account: Account) {
  resetTarget.value = account
  resetPassword.value = ''
  resetDialog.value?.showModal()
}

/** 管理员提交重置密码，窗口关闭后不会保留明文密码。 */
async function submitPasswordReset() {
  if (!resetTarget.value) return
  busy.value = true
  try {
    await api<void>(`/users/${resetTarget.value.id}/password`, {
      method:'PUT', body:JSON.stringify({ password:resetPassword.value }),
    })
    emit('notice', `账户 ${resetTarget.value.username} 的密码已重置`)
    resetDialog.value?.close()
    resetPassword.value = ''
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : '密码重置失败' }
  finally { busy.value = false }
}

/** 删除前明确确认账户名；删除操作不可恢复。 */
async function deleteAccount(account: Account) {
  if (!window.confirm(`确定删除账户“${account.username}”吗？该操作不可恢复。`)) return
  try {
    await api<void>(`/users/${account.id}`, { method:'DELETE' })
    accounts.value = accounts.value.filter(row => row.id !== account.id)
    delete drafts[account.id]
    emit('notice', `账户 ${account.username} 已删除`)
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : '删除账户失败' }
}

/** 根据当前角色决定是否读取管理员专属的账户列表和审计数据。 */
onMounted(async () => {
  await loadCurrent()
  if (currentAccount.value?.role === 'ADMIN') await Promise.all([loadUsers(), loadAudit(), loadSettings()])
})
</script>

<template>
  <section class="module-view">
    <header class="module-header">
      <div><p class="eyebrow">工作空间 / SETTINGS</p><h1>系统设置</h1><p>管理账户、角色、密码及系统安全配置。</p></div>
      <button v-if="currentAccount?.role==='ADMIN'" class="button secondary" :disabled="loadingUsers" @click="loadUsers"><RefreshCw :size="15"/>刷新用户</button>
    </header>
    <p v-if="errorMessage" class="settings-error" role="alert">{{errorMessage}}</p>
    <p v-if="currentAccount && currentAccount.role!=='ADMIN'" class="settings-permission-note" role="status">当前账户角色为「{{roleNames[currentAccount.role]}}」，只有管理员可以创建、编辑、删除用户和分配角色。如需管理用户，请使用管理员账户登录。</p>

    <template v-if="currentAccount?.role==='ADMIN'">
      <article class="module-card user-management-card">
        <div class="audit-heading"><div><Users :size="17"/><div><b>用户管理</b><span>创建账户、分配角色、启停账户、重置密码或删除账户</span></div></div><span class="user-total">{{accounts.length}} 个账户</span></div>
        <form class="user-create-form" @submit.prevent="createAccount">
          <label><b>账户名</b><input v-model="createForm.username" required minlength="3" maxlength="64" pattern="[A-Za-z0-9._@-]+" autocomplete="off" placeholder="例如：alice"/></label>
          <label><b>显示名称</b><input v-model="createForm.displayName" required maxlength="120" placeholder="例如：张三"/></label>
          <label><b>初始密码</b><input v-model="createForm.password" required type="password" minlength="12" maxlength="72" autocomplete="new-password" placeholder="至少 12 个字符"/></label>
          <label><b>角色</b><select v-model="createForm.role"><option value="ADMIN">管理员</option><option value="OPERATOR">运维</option><option value="VIEWER">只读</option></select></label>
          <button class="button primary" :disabled="busy"><UserPlus :size="15"/>创建用户</button>
        </form>
        <div v-if="loadingUsers" class="module-empty">正在读取用户列表…</div>
        <div v-else-if="!accounts.length" class="module-empty">当前没有用户账户。</div>
        <div v-else class="asset-table-wrap"><table class="asset-table user-table"><thead><tr><th>账户</th><th>显示名称</th><th>角色</th><th>状态</th><th>创建时间</th><th>管理</th></tr></thead><tbody>
          <tr v-for="account in accounts" :key="account.id">
            <td><b>{{account.username}}</b></td>
            <td><input v-model="drafts[account.id].displayName" class="user-name-input" maxlength="120"/></td>
            <td><select v-model="drafts[account.id].role" class="user-role-select"><option value="ADMIN">管理员</option><option value="OPERATOR">运维</option><option value="VIEWER">只读</option></select></td>
            <td><span class="status-pill" :class="account.enabled?'status-ok':'status-warning'"><i/>{{account.enabled?'已启用':'已停用'}}</span></td>
            <td>{{account.createdAt?new Date(account.createdAt).toLocaleString('zh-CN'):'—'}}</td>
            <td><div class="user-row-actions"><button class="button secondary small" :disabled="busy" @click="saveAccount(account)">保存</button><button class="button secondary small" :disabled="busy" @click="toggleAccount(account)">{{account.enabled?'停用':'启用'}}</button><button class="button secondary small" title="重置密码" :disabled="busy" @click="openPasswordReset(account)"><KeyRound :size="14"/></button><button class="button secondary small danger-button" title="删除用户" :disabled="busy" @click="deleteAccount(account)"><Trash2 :size="14"/></button></div></td>
          </tr>
        </tbody></table></div>
      </article>
    </template>

    <div class="settings-grid">
      <article class="module-card setting-card"><header><ShieldCheck :size="18"/><div><b>角色权限</b><span>服务端校验权限，不依赖前端隐藏按钮</span></div></header><div v-for="role in roleDescriptions" :key="role.role" class="role-row"><div class="role-badge"><ShieldCheck :size="15"/></div><div><b>{{role.name}} · {{role.role}}</b><span>{{role.permissions}}</span></div></div></article>
      <article v-if="currentAccount?.role==='ADMIN'" class="module-card setting-card dynamic-settings-card">
        <header><Mail :size="18"/><div><b>密钥与邮件</b><span>保存后立即应用于证书加密和邮件发送</span></div></header>
        <form class="dynamic-settings-form" @submit.prevent="saveSettings">
          <div class="settings-form-section"><div class="settings-section-title"><Mail :size="15"/><b>SMTP 邮件</b></div>
            <label><b>SMTP 主机</b><input v-model="settingsForm.smtpHost" required maxlength="255" placeholder="smtp.example.com"/></label>
            <label><b>端口</b><input v-model.number="settingsForm.smtpPort" type="number" min="1" max="65535" required/></label>
            <label><b>SMTP 用户名</b><input v-model="settingsForm.smtpUsername" maxlength="254" autocomplete="off"/></label>
            <label><b>SMTP 密码</b><input v-model="settingsForm.smtpPassword" type="password" maxlength="256" autocomplete="new-password" :placeholder="runtimeSettings?.smtpPasswordConfigured?'已设置；留空保留当前密码':'输入 SMTP 密码'"/></label>
            <label><b>发件地址</b><input v-model="settingsForm.fromAddress" type="email" required maxlength="254" placeholder="alerts@example.com"/></label>
            <label class="settings-wide-field"><b>全局提醒收件人</b><input v-model="settingsForm.globalRecipients" maxlength="1200" placeholder="多个邮箱用逗号分隔"/></label>
            <label class="settings-check"><input v-model="settingsForm.smtpAuth" type="checkbox"/><span>启用 SMTP 身份验证</span></label>
            <label class="settings-check"><input v-model="settingsForm.startTls" type="checkbox"/><span>要求 STARTTLS 加密</span></label>
          </div>
          <div class="settings-form-section certificate-key-section"><div class="settings-section-title"><KeyRound :size="15"/><b>证书私钥加密密钥</b></div>
            <p>当前密钥{{runtimeSettings?.certificateEncryptionKeyConfigured?'已配置':'未配置'}}。输入 32 字节 Base64 AES-256 密钥，或生成随机密钥。更换时系统会重新加密全部已保存证书私钥。</p>
            <div class="key-input-row"><div class="key-secret-input"><input v-model="settingsForm.certificateEncryptionKey" :type="showCertificateKey?'text':'password'" autocomplete="new-password" maxlength="64" placeholder="留空保持当前密钥"/><button type="button" class="key-visibility-button" :aria-label="showCertificateKey?'隐藏密钥':'显示密钥'" :title="showCertificateKey?'隐藏密钥':'显示密钥'" @click="showCertificateKey=!showCertificateKey"><EyeOff v-if="showCertificateKey" :size="16"/><Eye v-else :size="16"/></button></div><button type="button" class="button secondary" @click="generateCertificateKey"><RotateCw :size="14"/>生成密钥</button></div>
            <small>请妥善备份 application.yml 中的系统配置保护密钥；丢失后无法解密数据库中的 SMTP 密码和证书密钥。</small>
          </div>
          <p v-if="settingsSuccess" class="settings-success" role="status">{{settingsSuccess}}</p>
          <div class="dynamic-settings-actions"><span>敏感字段不会回显，留空即可保留原值。</span><button class="button primary" :disabled="savingSettings"><Save :size="15"/>{{savingSettings?'正在保存…':'保存配置'}}</button></div>
        </form>
      </article>
    </div>

    <article v-if="currentAccount?.role==='ADMIN'" class="module-card audit-card"><div class="audit-heading"><div><Activity :size="17"/><div><b>操作审计</b><span>最近 50 条关键变更记录</span></div></div><button class="button secondary" @click="loadAudit">刷新</button></div><div v-if="!auditRows.length" class="module-empty compact">暂无审计记录。</div><div v-else class="asset-table-wrap"><table class="asset-table"><thead><tr><th>操作时间</th><th>操作者</th><th>操作</th><th>资产类型</th><th>资产编号</th><th>详情</th></tr></thead><tbody><tr v-for="row in auditRows" :key="row.id"><td>{{new Date(row.createdAt).toLocaleString('zh-CN')}}</td><td>{{row.actorEmail}}</td><td>{{row.action}}</td><td>{{row.assetType || '—'}}</td><td>{{row.assetId ?? '—'}}</td><td>{{row.details}}</td></tr></tbody></table></div></article>

    <dialog ref="resetDialog" class="mail-test-dialog">
      <form method="dialog" class="mail-dialog-close"><button class="button secondary small">关闭</button></form>
      <p class="eyebrow">账户安全</p><h2>重置 {{resetTarget?.username}} 的密码</h2>
      <form class="password-reset-form" @submit.prevent="submitPasswordReset"><label><b>新密码</b><input v-model="resetPassword" type="password" required minlength="12" maxlength="72" autocomplete="new-password" placeholder="至少 12 个字符"/></label><button class="button primary" :disabled="busy"><KeyRound :size="15"/>保存新密码</button></form>
    </dialog>
  </section>
</template>
