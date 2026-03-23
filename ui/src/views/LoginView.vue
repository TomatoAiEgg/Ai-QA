<template>
  <div class="login-page">
    <div class="login-backdrop"></div>
    <div class="login-shell">
      <div class="login-brand">
        <span class="login-eyebrow">AI-QA</span>
        <h1>统一登录入口</h1>
        <p>支持邮箱或手机号登录，登录后即可访问对话历史与知识库。</p>
      </div>

      <div class="login-card">
        <div class="mode-switch">
          <button :class="{ active: mode === 'login' }" type="button" @click="mode = 'login'">登录</button>
          <button :class="{ active: mode === 'register' }" type="button" @click="mode = 'register'">注册</button>
        </div>

        <div class="type-switch">
          <button :class="{ active: accountType === 'email' }" type="button" @click="accountType = 'email'">邮箱</button>
          <button :class="{ active: accountType === 'phone' }" type="button" @click="accountType = 'phone'">手机号</button>
        </div>

        <el-form :model="form" label-position="top" @submit.prevent>
          <el-form-item v-if="mode === 'register'" label="昵称">
            <el-input v-model="form.nickname" placeholder="给自己起一个名称" />
          </el-form-item>

          <el-form-item :label="accountType === 'email' ? '邮箱' : '手机号'">
            <el-input
              v-model="form.account"
              :placeholder="accountType === 'email' ? 'name@example.com' : '请输入 11 位手机号'"
            />
          </el-form-item>

          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
          </el-form-item>

          <el-form-item v-if="mode === 'register'" label="确认密码">
            <el-input v-model="form.confirmPassword" type="password" show-password placeholder="再次输入密码" />
          </el-form-item>

          <el-button class="submit-btn" type="primary" :loading="submitting" @click="submit">
            {{ mode === 'login' ? '登录并进入工作台' : '注册并进入工作台' }}
          </el-button>
        </el-form>

        <p class="login-tip">
          {{ mode === 'login' ? '使用邮箱或手机号 + 密码登录。' : '注册时邮箱和手机号至少填写一个。' }}
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { applySession } from '../auth.js'
import * as api from '../api.js'

const router = useRouter()
const route = useRoute()

const mode = ref('login')
const accountType = ref('email')
const submitting = ref(false)
const form = reactive({
  nickname: '',
  account: '',
  password: '',
  confirmPassword: ''
})

const submit = async () => {
  if (!form.account.trim()) {
    ElMessage.error(accountType.value === 'email' ? '请输入邮箱' : '请输入手机号')
    return
  }
  if (!form.password) {
    ElMessage.error('请输入密码')
    return
  }
  if (mode.value === 'register' && form.password !== form.confirmPassword) {
    ElMessage.error('两次输入的密码不一致')
    return
  }

  submitting.value = true
  try {
    const payload = mode.value === 'login'
      ? await api.login(form.account.trim(), form.password)
      : await api.register({
          nickname: form.nickname.trim(),
          email: accountType.value === 'email' ? form.account.trim() : '',
          phone: accountType.value === 'phone' ? form.account.trim() : '',
          password: form.password
        })

    applySession(payload)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
    ElMessage.success(mode.value === 'login' ? '登录成功' : '注册成功')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgba(37, 99, 235, 0.16), transparent 28%),
    radial-gradient(circle at bottom right, rgba(14, 165, 233, 0.18), transparent 24%),
    linear-gradient(180deg, #edf3fb 0%, #e5edf6 100%);
}

.login-backdrop {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 20% 18%, rgba(255, 255, 255, 0.8), transparent 18%),
    radial-gradient(circle at 85% 12%, rgba(255, 255, 255, 0.58), transparent 16%);
  pointer-events: none;
}

.login-shell {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  gap: 32px;
  align-items: center;
  min-height: 100vh;
  padding: 48px;
}

.login-brand h1 {
  margin: 12px 0 14px;
  font-size: clamp(42px, 5vw, 64px);
  line-height: 1.04;
  color: #132031;
}

.login-brand p {
  max-width: 520px;
  margin: 0;
  color: #5f6f84;
  font-size: 16px;
  line-height: 1.7;
}

.login-eyebrow {
  display: inline-flex;
  align-items: center;
  height: 32px;
  padding: 0 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(148, 163, 184, 0.2);
  color: #32507a;
  font-size: 13px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.login-card {
  padding: 28px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(148, 163, 184, 0.18);
  backdrop-filter: blur(18px);
  box-shadow: 0 24px 56px rgba(15, 23, 42, 0.12);
}

.mode-switch,
.type-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.type-switch {
  margin-top: 12px;
  margin-bottom: 18px;
}

.mode-switch button,
.type-switch button {
  height: 44px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.76);
  color: #607086;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.mode-switch button.active,
.type-switch button.active {
  color: #1f4ec8;
  background: rgba(37, 99, 235, 0.1);
  border-color: rgba(37, 99, 235, 0.18);
}

.submit-btn {
  width: 100%;
  height: 48px;
  margin-top: 6px;
  border-radius: 16px;
  font-weight: 600;
}

.login-tip {
  margin: 16px 0 0;
  color: #748295;
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 960px) {
  .login-shell {
    grid-template-columns: 1fr;
    padding: 24px;
  }

  .login-brand {
    text-align: center;
  }

  .login-brand p {
    margin: 0 auto;
  }
}
</style>
