/** 前端 API 访问层：统一处理 Basic 凭据、错误消息和 JSON 响应。 */
export interface DomainAsset {
  id: number
  name: string
  registeredAt: string | null
  expiresAt: string | null
  registrar: string | null
  status: string
  nameServers: string | null
  dnsRecordsJson: string
  lookupStatus: string
  lookupMessage: string | null
  lastRefreshedAt: string | null
  alertRecipients: string | null
}

/** 证书列表仅包含可公开展示的证书元数据，不包含私钥材料。 */
export interface CertificateAsset {
  id: number
  commonName: string
  domains: string
  status: string
  validFrom: string
  validUntil: string
  issuer: string
  autoRenew: boolean
}

/** 在线证书检查只返回服务器公开的叶证书信息和证书链。 */
export interface CertificateInspection {
  domain: string
  commonName: string
  domains: string[]
  issuer: string
  serialNumber: string
  fingerprintSha256: string
  signatureAlgorithm: string
  publicKeyAlgorithm: string
  publicKeyBits: number | null
  validFrom: string
  validUntil: string
  tlsProtocol: string
  chain: Array<{ subject: string; issuer: string; validFrom: string; validUntil: string }>
}

const API_ROOT = '/api'
// HTTP Basic 凭据仅保存在当前标签页会话中，关闭浏览器标签后自动清除。
const AUTH_KEY = 'domain-cert-basic-auth'

/** 将已保存的 Basic 令牌附加到 API 请求中。 */
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  const token = sessionStorage.getItem(AUTH_KEY)
  if (token) headers.set('Authorization', `Basic ${token}`)
  if (!(options.body instanceof FormData) && options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${API_ROOT}${path}`, { ...options, headers })
  if (response.status === 401) {
    sessionStorage.removeItem(AUTH_KEY)
    window.dispatchEvent(new Event('api-auth-expired'))
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { message?: string; detail?: string } | null
    // 登录失败时明确指出角色账户来源，避免用户把环境配置错误误认为页面故障。
    if (response.status === 401) {
      throw new Error('账户名或密码错误。请使用 admin、operator 或 viewer，并核对部署环境中的对应密码。')
    }
    if (response.status === 403) {
      throw new Error('当前账户没有执行此操作的权限。')
    }
    throw new Error(body?.detail || body?.message || `请求失败 (${response.status})`)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

/** 验证凭据成功后才将令牌保存在浏览器本地。 */
export async function signIn(username: string, password: string): Promise<void> {
  const token = btoa(`${username}:${password}`)
  sessionStorage.setItem(AUTH_KEY, token)
  try {
    await api<DomainAsset[]>('/domains')
  } catch (error) {
    sessionStorage.removeItem(AUTH_KEY)
    throw error
  }
}

/** 清除本地认证状态。 */
export function signOut(): void {
  sessionStorage.removeItem(AUTH_KEY)
}

/** 下载私钥时通过 API 层注入身份凭据，浏览器不持久化下载内容。 */
export async function downloadCertificate(id: number): Promise<Blob> {
  const token = sessionStorage.getItem(AUTH_KEY)
  const response = await fetch(`${API_ROOT}/certificates/${id}/download`, {
    headers: token ? { Authorization: `Basic ${token}` } : {},
  })
  if (!response.ok) throw new Error('下载失败，请确认当前账户有运维权限')
  return response.blob()
}
