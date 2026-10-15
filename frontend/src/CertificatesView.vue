<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, downloadCertificate, type CertificateAsset, type CertificateInspection, type DomainAsset } from './api'
import { Download, RefreshCw, ShieldCheck, Upload, FileKey2, Trash2, Search, Globe2 } from 'lucide-vue-next'

const emit = defineEmits<{ notice: [message: string] }>()
const rows = ref<CertificateAsset[]>([])
const domainRows = ref<DomainAsset[]>([])
const selectedDomains = ref<string[]>([])
const contactEmail = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const privateKeyInput = ref<HTMLInputElement | null>(null)
const selectedPem = ref<File | null>(null)
const selectedPrivateKey = ref<File | null>(null)
const busy = ref(false)
const loading = ref(false)
const inspectDomain = ref('')
const inspecting = ref(false)
const inspection = ref<CertificateInspection | null>(null)
const daysLeft = (date: string) => Math.ceil((new Date(date).getTime() - Date.now()) / 86_400_000)
const activeCount = computed(() => rows.value.filter(row => daysLeft(row.validUntil) > 0).length)

/** 初始化可签发域名清单，ACME 只能为已登记的域名签发证书。 */
async function loadDomains() {
  try { domainRows.value = await api<DomainAsset[]>('/domains') }
  catch (error) { emit('notice', error instanceof Error ? error.message : '域名列表读取失败') }
}

/** 调用 ACME HTTP-01 签发接口；域名必须指向本服务并可从公网访问 80 端口。 */
async function issueCertificate() {
  if (!selectedDomains.value.length) { emit('notice', '请至少选择一个已登记域名'); return }
  if (!contactEmail.value.trim()) { emit('notice', '请输入 ACME 联系邮箱'); return }
  busy.value = true
  try {
    const created = await api<CertificateAsset>('/certificates/issue', {
      method: 'POST',
      body: JSON.stringify({ domains: selectedDomains.value, email: contactEmail.value.trim() }),
    })
    rows.value = [created, ...rows.value.filter(row => row.id !== created.id)]
    emit('notice', `已签发并安全保存 ${created.commonName} 证书`)
  } catch (error) { emit('notice', error instanceof Error ? error.message : '证书签发失败') }
  finally { busy.value = false }
}

/** 载入证书元数据；服务端不会在列表中返回任何私钥。 */
async function load() {
  loading.value = true
  try { rows.value = await api<CertificateAsset[]>('/certificates') }
  catch (error) { emit('notice', error instanceof Error ? error.message : '证书加载失败') }
  finally { loading.value = false }
}

/** 使用域名查询线上 443 端口证书，结果仅展示公开证书内容，不创建私钥记录。 */
async function inspectCertificate() {
  const domain = inspectDomain.value.trim().toLowerCase()
  if (!domain) { emit('notice', '请输入要查询的域名'); return }
  inspecting.value = true
  inspection.value = null
  try {
    inspection.value = await api<CertificateInspection>(`/certificates/inspect?domain=${encodeURIComponent(domain)}`)
    emit('notice', `已获取 ${domain} 的在线 TLS 证书信息`)
  } catch (error) {
    emit('notice', error instanceof Error ? error.message : '在线证书查询失败')
  } finally {
    inspecting.value = false
  }
}

/** 用中文本地格式展示证书时间，保留后端传回的准确时刻。 */
function showDateTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })
}

/** 仅允许 PEM 文件上传；服务端检查有效期及证书/私钥是否匹配。 */
function selectPem(event: Event) {
  selectedPem.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

function selectPrivateKey(event: Event) {
  selectedPrivateKey.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

async function upload() {
  const file = selectedPem.value
  if (!file) return
  const data = new FormData()
  data.set('file', file)
  if (selectedPrivateKey.value) data.set('key', selectedPrivateKey.value)
  busy.value = true
  try {
    const created = await api<CertificateAsset>('/certificates', { method: 'POST', body: data })
    rows.value = [created, ...rows.value.filter(row => row.id !== created.id)]
    selectedPem.value = null
    selectedPrivateKey.value = null
    if (fileInput.value) fileInput.value.value = ''
    if (privateKeyInput.value) privateKeyInput.value.value = ''
    emit('notice', '证书已校验并安全保存')
  } catch (error) { emit('notice', error instanceof Error ? error.message : '证书上传失败') }
  finally { busy.value = false }
}

/** 只在点击下载时请求明文 PEM，下载后立即释放浏览器 Blob 引用。 */
async function download(row: CertificateAsset) {
  try {
    const blob = await downloadCertificate(row.id)
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `certificate-${row.id}.pem`
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (error) { emit('notice', error instanceof Error ? error.message : '下载失败') }
}

/** 删除确认后清理证书及其数据库历史版本。 */
async function remove(row: CertificateAsset) {
  if (!window.confirm(`确定删除 ${row.commonName} 及其历史证书版本吗？`)) return
  try {
    await api<void>(`/certificates/${row.id}`, { method: 'DELETE' })
    rows.value = rows.value.filter(item => item.id !== row.id)
    emit('notice', '证书及历史版本已删除')
  } catch (error) { emit('notice', error instanceof Error ? error.message : '删除证书失败') }
}

onMounted(() => { void load(); void loadDomains() })
</script>

<template>
  <section class="module-view">
    <header class="module-header">
      <div><p class="eyebrow">安全 / TLS CERTIFICATES</p><h1>SSL 证书</h1><p>通过 ACME 签发、上传 PEM、查看有效期并下载证书。</p></div>
      <div class="module-actions"><button class="button secondary" :disabled="loading" @click="load"><RefreshCw :size="16"/>刷新列表</button><button class="button secondary" :disabled="busy" @click="fileInput?.click()"><Upload :size="16"/>选择证书 PEM</button><button class="button secondary" :disabled="busy" @click="privateKeyInput?.click()"><FileKey2 :size="15"/>选择私钥 PEM</button><button class="button primary" :disabled="busy || !selectedPem" @click="upload"><ShieldCheck :size="15"/>{{busy?'正在校验…':'上传并校验'}}</button><input ref="fileInput" class="visually-hidden" type="file" accept=".pem,.crt,.cer,.txt" @change="selectPem"/><input ref="privateKeyInput" class="visually-hidden" type="file" accept=".pem,.key,.txt" @change="selectPrivateKey"/></div>
      <p v-if="selectedPem || selectedPrivateKey" class="cert-upload-selection">证书：{{selectedPem?.name || '尚未选择'}} · 私钥：{{selectedPrivateKey?.name || '从证书 PEM 中读取'}}</p>
    </header>
    <form class="module-card cert-lookup" @submit.prevent="issueCertificate">
      <div class="cert-lookup-heading"><div><b>创建 ACME 证书</b><span>默认通过 Let's Encrypt 正式环境签发。签发前请确保域名已解析至本服务器，且公网 80 端口可访问。</span></div></div>
      <div v-if="!domainRows.length" class="module-empty">请先在域名管理中添加域名，再创建证书。</div>
      <div v-else class="issue-domain-list"><label v-for="domain in domainRows" :key="domain.id"><input v-model="selectedDomains" type="checkbox" :value="domain.name"/>{{domain.name}}</label></div>
      <div class="cert-lookup-controls"><label class="issue-email"><span>ACME 联系邮箱</span><input v-model="contactEmail" required type="email" placeholder="admin@example.com" autocomplete="email"/><button class="button primary" :disabled="busy"><ShieldCheck :size="15"/>{{busy?'正在验证并签发…':'创建证书'}}</button></label></div>
    </form>
    <div class="cert-summary"><div class="metric-icon green"><ShieldCheck :size="18"/></div><div><b>{{activeCount}} 张有效证书</b><span>{{rows.length}} 张证书资产 · 仅运维和管理员可下载私钥</span></div></div>
    <form class="module-card cert-lookup" @submit.prevent="inspectCertificate">
      <div class="cert-lookup-heading"><div><b>查询网站在线 SSL 证书</b><span>输入域名获取当前 HTTPS 证书、有效期、签发者及证书链，例如 baidu.com。</span></div></div>
      <div class="cert-lookup-controls"><label><Globe2 :size="16"/><input v-model="inspectDomain" required placeholder="baidu.com" autocomplete="url"/><button class="button primary" :disabled="inspecting"><Search :size="15"/>{{inspecting?'正在查询…':'查询证书'}}</button></label></div>
      <article v-if="inspection" class="inspection-result">
        <header><div><h2>{{inspection.commonName}}</h2><span>{{inspection.domain}} · {{inspection.tlsProtocol}}</span></div><span class="status-pill" :class="new Date(inspection.validUntil).getTime()>Date.now()?'status-ok':'status-warning'"><i/>{{new Date(inspection.validUntil).getTime()>Date.now()?'有效':'已过期'}}</span></header>
        <div class="inspection-grid"><div><span>证书域名 SAN</span><b>{{inspection.domains.join('、')}}</b></div><div><span>签发机构</span><b>{{inspection.issuer}}</b></div><div><span>生效时间</span><b>{{showDateTime(inspection.validFrom)}}</b></div><div><span>到期时间</span><b>{{showDateTime(inspection.validUntil)}}</b></div><div><span>序列号</span><b>{{inspection.serialNumber}}</b></div><div><span>签名算法</span><b>{{inspection.signatureAlgorithm}}</b></div><div><span>公钥</span><b>{{inspection.publicKeyAlgorithm}}{{inspection.publicKeyBits?` · ${inspection.publicKeyBits} 位`:''}}</b></div><div><span>SHA-256 指纹</span><b>{{inspection.fingerprintSha256}}</b></div></div>
        <div class="inspection-chain"><b>服务器返回的证书链（{{inspection.chain.length}} 张）</b><ol><li v-for="(cert,index) in inspection.chain" :key="`${cert.subject}-${index}`"><span>{{index===0?'站点证书':`第 ${index} 级`}}</span><code>{{cert.subject}}</code></li></ol></div>
      </article>
    </form>
    <div class="module-card">
      <div v-if="loading" class="module-empty">正在读取证书资产…</div>
      <div v-else-if="!rows.length" class="module-empty">还没有证书。可通过 ACME 创建证书，或上传包含证书和 PKCS#8 私钥的 PEM 文件。</div>
      <div v-else class="certificate-cards">
        <article v-for="row in rows" :key="row.id" class="certificate-card">
          <header><div class="cert-icon"><ShieldCheck :size="18"/></div><span class="status-pill" :class="daysLeft(row.validUntil)>0?'status-ok':'status-warning'"><i/>{{daysLeft(row.validUntil)>0?'有效':'已过期'}}</span></header>
          <h2>{{row.commonName}}</h2><p>{{row.issuer || '签发者未知'}}</p>
          <div class="cert-domains"><span v-for="name in row.domains.split(',').slice(0,4)" :key="name">{{name}}</span></div>
          <div class="cert-date-row"><span>有效期至</span><b>{{new Date(row.validUntil).toLocaleDateString('zh-CN')}}</b><strong :class="{red:daysLeft(row.validUntil)<=30}">{{daysLeft(row.validUntil)}} 天</strong></div>
          <footer><span class="cert-version-label">证书版本由 ACME 签发或 PEM 上传更新</span><div class="cert-actions"><button class="button secondary small" title="下载 PEM" @click="download(row)"><Download :size="15"/>下载</button><button class="button secondary small danger-button" title="删除证书" @click="remove(row)"><Trash2 :size="14"/></button></div></footer>
        </article>
      </div>
    </div>
    <div class="module-note"><FileKey2 :size="16"/><span>可上传合并 PEM，也可分别选择证书链和私钥文件；支持 PKCS#8 与 RSA PKCS#1 未加密私钥。系统会验证证书/私钥匹配并在入库前使用 AES-GCM 加密；下载仅限管理员和运维角色。</span></div>
  </section>
</template>
