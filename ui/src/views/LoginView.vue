<template>
  <div class="login-page">
    <div class="login-grid">
      <section class="brand-panel">
        <span class="brand-badge">AI-QA Workspace</span>
        <h1>统一账号登录</h1>
        <p class="brand-copy">
          面向知识库问答、智能客服和开放文档接入的一体化工作台。
          登录后即可访问会话历史、知识库管理和外部上传能力。
        </p>

        <div class="brand-highlights">
          <article>
            <strong>知识库问答</strong>
            <span>支持多知识库、RAG 检索与流式回复。</span>
          </article>
          <article>
            <strong>统一登录态</strong>
            <span>基于 Sa-Token 与 Redis 管理会话和缓存。</span>
          </article>
          <article>
            <strong>开放文档接入</strong>
            <span>外部系统可通过 token 将文件写入指定知识库。</span>
          </article>
        </div>
      </section>

      <section class="login-card">
        <div class="card-header">
          <div>
            <p class="card-eyebrow">ACCOUNT CENTER</p>
            <h2>登录 AI-QA</h2>
          </div>
          <div class="mode-switch">
            <button class="active" type="button">登录</button>
            <button type="button" disabled>注册已关闭</button>
          </div>
        </div>

        <div class="type-switch">
          <button :class="{ active: accountType === 'email' }" type="button" @click="switchAccountType('email')">邮箱</button>
          <button :class="{ active: accountType === 'phone' }" type="button" @click="switchAccountType('phone')">手机号</button>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" status-icon @submit.prevent>
          <el-form-item :label="accountType === 'email' ? '邮箱' : '手机号'" prop="account">
            <el-input
              v-model="form.account"
              size="large"
              :placeholder="accountType === 'email' ? '请输入企业邮箱' : '请输入手机号'"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              show-password
              placeholder="请输入登录密码"
            />
          </el-form-item>

          <el-button class="submit-btn" type="primary" :loading="submitting" @click="submit">
            登录并进入工作台
          </el-button>
        </el-form>

        <div class="card-footer">
          <p>当前版本仅开放已有账号登录，注册入口已关闭。</p>
          <p>如需开通账号，请联系系统管理员。</p>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { applySession } from '../auth.js'
import * as api from '../api.js'

const router = useRouter()
const route = useRoute()

const formRef = ref()
const accountType = ref('email')
const submitting = ref(false)
const form = reactive({
  account: '',
  password: ''
})

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const phonePattern = /^1\d{10}$/
const validateAccount = (_rule, value, callback) => {
  const account = (value || '').trim()
  if (!account) {
    callback(new Error(accountType.value === 'email' ? '请输入邮箱' : '请输入手机号'))
    return
  }
  if (accountType.value === 'email' && !emailPattern.test(account)) {
    callback(new Error('请输入正确的邮箱格式'))
    return
  }
  if (accountType.value === 'phone' && !phonePattern.test(account)) {
    callback(new Error('请输入正确的手机号格式'))
    return
  }
  callback()
}

const rules = reactive({
  account: [
    { validator: validateAccount, trigger: ['blur', 'change'] }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
})

const switchAccountType = async (type) => {
  if (accountType.value === type) {
    return
  }
  accountType.value = type
  await nextTick()
  formRef.value?.clearValidate('account')
  if (form.account.trim()) {
    formRef.value?.validateField('account')
  }
}

const submit = async () => {
  const account = form.account.trim()
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  submitting.value = true
  try {
    const payload = await api.login(account, form.password)
    applySession(payload)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
    ElMessage.success('登录成功')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at 18% 18%, rgba(54, 92, 181, 0.2), transparent 24%),
    radial-gradient(circle at 86% 12%, rgba(33, 145, 251, 0.14), transparent 20%),
    linear-gradient(135deg, #e9eef8 0%, #dfe7f3 42%, #f4f7fb 100%);
}

.login-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(420px, 520px);
  gap: 40px;
  align-items: center;
  min-height: 100vh;
  padding: 40px 56px;
}

.brand-panel {
  position: relative;
  padding: 44px;
  border-radius: 32px;
  background:
    linear-gradient(160deg, rgba(255, 255, 255, 0.12), rgba(255, 255, 255, 0.04)),
    linear-gradient(135deg, #103067, #2459b8);
  border: 1px solid rgba(255, 255, 255, 0.24);
  box-shadow: 0 30px 90px rgba(17, 33, 66, 0.18);
  overflow: hidden;
}

.brand-panel::after {
  content: '';
  position: absolute;
  right: -80px;
  bottom: -80px;
  width: 280px;
  height: 280px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.18), transparent 68%);
}

.brand-badge {
  display: inline-flex;
  align-items: center;
  height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.9);
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.brand-panel h1 {
  margin: 20px 0 16px;
  color: #fff;
  font-size: clamp(40px, 5vw, 62px);
  line-height: 1.05;
  letter-spacing: -0.04em;
}

.brand-copy {
  max-width: 620px;
  margin: 0;
  color: rgba(234, 241, 255, 0.88);
  font-size: 16px;
  line-height: 1.85;
}

.brand-highlights {
  display: grid;
  gap: 16px;
  margin-top: 34px;
}

.brand-highlights article {
  display: grid;
  gap: 6px;
  padding: 18px 20px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.brand-highlights strong {
  color: #fff;
  font-size: 16px;
  font-weight: 700;
}

.brand-highlights span {
  color: rgba(232, 239, 253, 0.82);
  font-size: 14px;
  line-height: 1.7;
}

.login-card {
  padding: 32px;
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(158, 172, 196, 0.22);
  box-shadow: 0 24px 70px rgba(24, 39, 75, 0.14);
  backdrop-filter: blur(16px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
  margin-bottom: 24px;
}

.card-eyebrow {
  margin: 0 0 10px;
  color: #6a7a93;
  font-size: 12px;
  letter-spacing: 0.12em;
}

.card-header h2 {
  margin: 0;
  color: #15233a;
  font-size: 28px;
}

.mode-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  min-width: 220px;
}

.mode-switch button,
.type-switch button {
  height: 42px;
  border-radius: 14px;
  border: 1px solid rgba(172, 184, 205, 0.24);
  background: rgba(245, 247, 251, 0.88);
  color: #65758d;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.2s ease;
}

.mode-switch button.active,
.type-switch button.active {
  border-color: rgba(37, 99, 235, 0.22);
  background: rgba(37, 99, 235, 0.08);
  color: #2255cb;
}

.mode-switch button:disabled {
  cursor: not-allowed;
  color: #a2aec0;
  background: rgba(240, 243, 248, 0.76);
}

.type-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 20px;
}

:deep(.el-form-item__label) {
  color: #44536a;
  font-weight: 600;
}

:deep(.el-input__wrapper) {
  min-height: 50px;
  border-radius: 16px;
  box-shadow: 0 0 0 1px rgba(179, 191, 211, 0.26) inset;
}

.submit-btn {
  width: 100%;
  height: 50px;
  margin-top: 8px;
  border-radius: 16px;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.02em;
  background: linear-gradient(135deg, #2a61df 0%, #1f8cff 100%);
  border: none;
  box-shadow: 0 16px 36px rgba(39, 106, 224, 0.26);
}

.card-footer {
  display: grid;
  gap: 6px;
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid rgba(221, 228, 239, 0.86);
}

.card-footer p {
  margin: 0;
  color: #728197;
  font-size: 13px;
  line-height: 1.7;
}

@media (max-width: 1100px) {
  .login-grid {
    grid-template-columns: 1fr;
    padding: 28px;
  }

  .brand-panel,
  .login-card {
    max-width: 760px;
    margin: 0 auto;
  }
}

@media (max-width: 680px) {
  .login-grid {
    padding: 18px;
  }

  .brand-panel,
  .login-card {
    padding: 24px;
    border-radius: 24px;
  }

  .card-header {
    flex-direction: column;
  }

  .mode-switch {
    width: 100%;
    min-width: 0;
  }
}
</style>
