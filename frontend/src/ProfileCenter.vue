<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, signOut } from './api'
import { KeyRound, ShieldCheck, UserRound, ArrowLeft } from 'lucide-vue-next'

interface Account { id:number; username:string; displayName:string; role:'ADMIN'|'OPERATOR'|'VIEWER'; enabled:boolean; createdAt:string|null }

const account = ref<Account | null>(null)
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const busy = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const emit = defineEmits<{ notice:[message:string] }>()
const roleNames: Record<Account['role'], string> = { ADMIN:'管理员', OPERATOR:'运维', VIEWER:'只读' }

/** 读取当前登录用户资料；密码不会从服务端返回。 */
onMounted(async () => {
  try { account.value = await api<Account>('/users/me') }
  catch (error) { errorMessage.value = error instanceof Error ? error.message : '个人资料读取失败' }
})

/** 验证当前密码并提交新密码；成功后要求用新密码重新登录。 */
async function changePassword() {
  errorMessage.value = ''
  successMessage.value = ''
  if (newPassword.value !== confirmPassword.value) {
    errorMessage.value = '两次输入的新密码不一致'
    return
  }
  busy.value = true
  try {
    await api<void>('/users/me/password', {
      method:'PUT',
      body:JSON.stringify({ currentPassword:currentPassword.value, newPassword:newPassword.value }),
    })
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    signOut()
    successMessage.value = '密码已修改，请使用新密码重新登录。'
    emit('notice', '密码已修改，请使用新密码重新登录')
    window.dispatchEvent(new Event('api-auth-expired'))
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '修改密码失败'
  } finally { busy.value = false }
}
</script>

<template>
  <section class="module-view">
    <header class="module-header">
      <div><p class="eyebrow">账户 / PROFILE</p><h1>个人中心</h1><p>查看账户信息并修改登录密码。</p></div>
    </header>

    <article class="module-card profile-center-card">
      <header><UserRound :size="18"/><div><b>账户资料</b><span>角色权限由管理员分配</span></div></header>
      <div v-if="account" class="profile-details">
        <div class="role-badge"><ShieldCheck :size="16"/></div>
        <div><b>{{account.displayName}} · {{account.username}}</b><span>{{roleNames[account.role]}} · {{account.enabled?'已启用':'已停用'}}</span></div>
      </div>
    </article>

    <article class="module-card setting-card profile-password-card">
      <header><KeyRound :size="18"/><div><b>修改密码</b><span>修改后需要使用新密码重新登录</span></div></header>
      <form class="profile-password-form" @submit.prevent="changePassword">
        <label><b>当前密码</b><input v-model="currentPassword" type="password" autocomplete="current-password" required/></label>
        <label><b>新密码</b><input v-model="newPassword" type="password" autocomplete="new-password" minlength="12" maxlength="72" required/><span>至少 12 个字符，UTF-8 编码不超过 72 字节</span></label>
        <label><b>确认新密码</b><input v-model="confirmPassword" type="password" autocomplete="new-password" minlength="12" maxlength="72" required/></label>
        <p v-if="errorMessage" class="settings-error">{{errorMessage}}</p>
        <p v-if="successMessage" class="settings-success">{{successMessage}}</p>
        <button class="button primary" :disabled="busy"><KeyRound :size="15"/>{{busy?'正在保存…':'保存新密码'}}</button>
      </form>
    </article>
  </section>
</template>
