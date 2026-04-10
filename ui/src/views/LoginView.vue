<template>
  <div class="login-page">
    <div class="login-grid">
      <section class="brand-panel">
        <span class="brand-badge">AI-QA Workspace</span>
        <h1>统一账号体系</h1>
        <p class="brand-copy">
          面向知识库问答、智能客服和开放文档接入的统一工作台。
          现在只允许邮箱登录和邮箱注册，登录、注册都会校验服务端验证码。
        </p>

        <div class="brand-highlights">
          <article>
            <strong>邮箱唯一入口</strong>
            <span>手机号登录入口已移除，前后端只接受邮箱账号。</span>
          </article>
          <article>
            <strong>双场景验证码</strong>
            <span>登录和注册分别生成验证码，并在后端一次性校验。</span>
          </article>
          <article>
            <strong>统一工作台</strong>
            <span>注册成功后直接建立登录态，无需再手动登录一次。</span>
          </article>
        </div>
      </section>

      <section class="login-card">
        <div class="card-header">
          <div>
            <p class="card-eyebrow">ACCOUNT CENTER</p>
            <h2>{{ isRegisterMode ? '注册 AI-QA' : '登录 AI-QA' }}</h2>
          </div>
          <div class="mode-switch">
            <button
              :class="{ active: !isRegisterMode }"
              type="button"
              @click="switchMode('login')"
            >
              登录
            </button>
            <button
              :class="{ active: isRegisterMode }"
              type="button"
              @click="switchMode('register')"
            >
              注册
            </button>
          </div>
        </div>

        <template v-if="!isRegisterMode">
          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="loginRules"
            label-position="top"
            status-icon
            @submit.prevent
          >
            <el-form-item label="邮箱" prop="email">
              <el-input
                v-model="loginForm.email"
                size="large"
                placeholder="请输入邮箱"
              />
            </el-form-item>

            <el-form-item label="密码" prop="password">
              <el-input
                v-model="loginForm.password"
                size="large"
                type="password"
                show-password
                placeholder="请输入登录密码"
              />
            </el-form-item>

            <div class="captcha-row">
              <el-form-item class="captcha-form-item" label="验证码" prop="captchaCode">
                <el-input
                  v-model="loginForm.captchaCode"
                  size="large"
                  maxlength="6"
                  placeholder="请输入验证码"
                />
              </el-form-item>

              <button
                class="captcha-button"
                type="button"
                :disabled="loginCaptcha.loading"
                @click="loadLoginCaptcha"
              >
                <img
                  v-if="loginCaptcha.image"
                  :src="loginCaptcha.image"
                  alt="登录验证码"
                >
                <span v-else>{{ loginCaptcha.loading ? '加载中...' : '获取验证码' }}</span>
              </button>
            </div>
            <p class="helper-link">
              登录需要验证码，看不清可以点击图片刷新。
            </p>

            <el-button class="submit-btn" type="primary" :loading="loginSubmitting" @click="submitLogin">
              登录并进入工作台
            </el-button>
          </el-form>
        </template>

        <template v-else>
          <el-form
            ref="registerFormRef"
            :model="registerForm"
            :rules="registerRules"
            label-position="top"
            status-icon
            @submit.prevent
          >
            <el-form-item label="邮箱" prop="email">
              <el-input
                v-model="registerForm.email"
                size="large"
                placeholder="请输入可用邮箱"
                @blur="handleRegisterEmailBlur"
              />
            </el-form-item>
            <p
              v-if="registerEmailStatus.message"
              class="field-tip"
              :class="{ warning: registerEmailStatus.registered, success: registerEmailStatus.available }"
            >
              {{ registerEmailStatus.message }}
            </p>

            <el-form-item label="昵称">
              <el-input
                v-model="registerForm.nickname"
                size="large"
                placeholder="不填则默认使用邮箱"
              />
            </el-form-item>

            <el-form-item label="密码" prop="password">
              <el-input
                v-model="registerForm.password"
                size="large"
                type="password"
                show-password
                placeholder="请输入至少 6 位密码"
              />
            </el-form-item>

            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input
                v-model="registerForm.confirmPassword"
                size="large"
                type="password"
                show-password
                placeholder="请再次输入密码"
              />
            </el-form-item>

            <div class="captcha-row">
              <el-form-item class="captcha-form-item" label="验证码" prop="captchaCode">
                <el-input
                  v-model="registerForm.captchaCode"
                  size="large"
                  maxlength="6"
                  placeholder="请输入验证码"
                />
              </el-form-item>

              <button
                class="captcha-button"
                type="button"
                :disabled="registerCaptcha.loading"
                @click="loadRegisterCaptcha"
              >
                <img
                  v-if="registerCaptcha.image"
                  :src="registerCaptcha.image"
                  alt="注册验证码"
                >
                <span v-else>{{ registerCaptcha.loading ? '加载中...' : '获取验证码' }}</span>
              </button>
            </div>
            <p class="helper-link">
              注册验证码 {{ registerCaptcha.expiresInSeconds || 300 }} 秒内有效，看不清可以点击刷新。
            </p>

            <el-button class="submit-btn" type="primary" :loading="registerSubmitting" @click="submitRegister">
              注册并进入工作台
            </el-button>
          </el-form>
        </template>

        <div class="card-footer">
          <template v-if="!isRegisterMode">
            <p>当前只支持邮箱登录，手机号登录入口已关闭。</p>
            <p>没有账号时可直接切换到注册，注册成功后会自动登录。</p>
          </template>
          <template v-else>
            <p>注册仅支持邮箱，系统会在提交前检查邮箱是否已被占用。</p>
            <p>验证码会在后端强制校验，绕过前端输入不会生效。</p>
          </template>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { applySession } from '../auth.js'
import * as api from '../api.js'

const router = useRouter()
const route = useRoute()

const loginFormRef = ref()
const registerFormRef = ref()
const mode = ref('login')
const loginSubmitting = ref(false)
const registerSubmitting = ref(false)
const emailChecking = ref(false)

const loginForm = reactive({
  email: '',
  password: '',
  captchaCode: ''
})

const registerForm = reactive({
  email: '',
  nickname: '',
  password: '',
  confirmPassword: '',
  captchaCode: ''
})

const loginCaptcha = reactive({
  captchaId: '',
  image: '',
  expiresInSeconds: 300,
  loading: false
})

const registerCaptcha = reactive({
  captchaId: '',
  image: '',
  expiresInSeconds: 300,
  loading: false
})

const registerEmailStatus = reactive({
  checkedEmail: '',
  available: false,
  registered: false,
  message: ''
})

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const isRegisterMode = computed(() => mode.value === 'register')
const redirectPath = computed(() => (
  typeof route.query.redirect === 'string' ? route.query.redirect : '/'
))

const validateEmail = (_rule, value, callback) => {
  const email = (value || '').trim()
  if (!email) {
    callback(new Error('请输入邮箱'))
    return
  }
  if (!emailPattern.test(email)) {
    callback(new Error('请输入正确的邮箱格式'))
    return
  }
  callback()
}

const validatePassword = (_rule, value, callback) => {
  if (!value) {
    callback(new Error('请输入密码'))
    return
  }
  if (value.length < 6) {
    callback(new Error('密码长度不能少于 6 位'))
    return
  }
  callback()
}

const validateConfirmPassword = (_rule, value, callback) => {
  if (!value) {
    callback(new Error('请再次输入密码'))
    return
  }
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
    return
  }
  callback()
}

const loginRules = reactive({
  email: [
    { validator: validateEmail, trigger: ['blur', 'change'] }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ],
  captchaCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' }
  ]
})

const registerRules = reactive({
  email: [
    { validator: validateEmail, trigger: ['blur', 'change'] }
  ],
  password: [
    { validator: validatePassword, trigger: ['blur', 'change'] }
  ],
  confirmPassword: [
    { validator: validateConfirmPassword, trigger: ['blur', 'change'] }
  ],
  captchaCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' }
  ]
})

const resetEmailAvailability = () => {
  registerEmailStatus.checkedEmail = ''
  registerEmailStatus.available = false
  registerEmailStatus.registered = false
  registerEmailStatus.message = ''
}

const switchMode = async (nextMode) => {
  if (mode.value === nextMode) {
    return
  }
  mode.value = nextMode
  await nextTick()

  if (nextMode === 'login' && !loginCaptcha.image) {
    await loadLoginCaptcha()
  }
  if (nextMode === 'register' && !registerCaptcha.image) {
    await loadRegisterCaptcha()
  }
}

const checkRegisterEmailAvailability = async (showMessage = false) => {
  const email = registerForm.email.trim()
  if (!emailPattern.test(email)) {
    resetEmailAvailability()
    return false
  }

  if (registerEmailStatus.checkedEmail === email) {
    if (showMessage) {
      if (registerEmailStatus.registered) {
        ElMessage.warning(registerEmailStatus.message)
      } else if (registerEmailStatus.available) {
        ElMessage.success(registerEmailStatus.message)
      }
    }
    return registerEmailStatus.available
  }

  emailChecking.value = true
  try {
    const payload = await api.checkEmailAvailability(email)
    registerEmailStatus.checkedEmail = payload?.email || email
    registerEmailStatus.registered = Boolean(payload?.registered)
    registerEmailStatus.available = Boolean(payload?.available)
    registerEmailStatus.message = registerEmailStatus.registered
      ? '该邮箱已注册，请直接登录'
      : '该邮箱可以注册'

    if (showMessage) {
      if (registerEmailStatus.registered) {
        ElMessage.warning(registerEmailStatus.message)
      } else {
        ElMessage.success(registerEmailStatus.message)
      }
    }
    return registerEmailStatus.available
  } catch (error) {
    resetEmailAvailability()
    ElMessage.error(error.message)
    return false
  } finally {
    emailChecking.value = false
  }
}

const handleRegisterEmailBlur = async () => {
  if (emailChecking.value) {
    return
  }
  await checkRegisterEmailAvailability(false)
}

const applyCaptchaPayload = (state, payload) => {
  state.captchaId = payload?.captchaId || ''
  state.image = payload?.captchaImage || ''
  state.expiresInSeconds = Number(payload?.expiresInSeconds || 300)
}

const loadLoginCaptcha = async () => {
  loginCaptcha.loading = true
  try {
    applyCaptchaPayload(loginCaptcha, await api.getLoginCaptcha())
    loginForm.captchaCode = ''
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loginCaptcha.loading = false
  }
}

const loadRegisterCaptcha = async () => {
  registerCaptcha.loading = true
  try {
    applyCaptchaPayload(registerCaptcha, await api.getRegisterCaptcha())
    registerForm.captchaCode = ''
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    registerCaptcha.loading = false
  }
}

const submitLogin = async () => {
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  if (!loginCaptcha.captchaId) {
    await loadLoginCaptcha()
    ElMessage.warning('请先获取验证码')
    return
  }

  loginSubmitting.value = true
  try {
    const payload = await api.login({
      email: loginForm.email.trim(),
      password: loginForm.password,
      captchaId: loginCaptcha.captchaId,
      captchaCode: loginForm.captchaCode.trim()
    })
    applySession(payload)
    await router.replace(redirectPath.value)
    ElMessage.success('登录成功')
  } catch (error) {
    ElMessage.error(error.message)
    await loadLoginCaptcha()
  } finally {
    loginSubmitting.value = false
  }
}

const submitRegister = async () => {
  const valid = await registerFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  const emailAvailable = await checkRegisterEmailAvailability(false)
  if (!emailAvailable) {
    if (registerEmailStatus.registered) {
      ElMessage.warning('该邮箱已注册，请直接登录')
    }
    return
  }

  if (!registerCaptcha.captchaId) {
    await loadRegisterCaptcha()
    ElMessage.warning('请先获取验证码')
    return
  }

  registerSubmitting.value = true
  try {
    const payload = await api.register({
      email: registerForm.email.trim(),
      password: registerForm.password,
      nickname: registerForm.nickname.trim(),
      captchaId: registerCaptcha.captchaId,
      captchaCode: registerForm.captchaCode.trim()
    })
    applySession(payload)
    await router.replace(redirectPath.value)
    ElMessage.success('注册成功')
  } catch (error) {
    ElMessage.error(error.message)
    await loadRegisterCaptcha()
  } finally {
    registerSubmitting.value = false
  }
}

watch(() => registerForm.email, () => {
  resetEmailAvailability()
})

onMounted(async () => {
  await loadLoginCaptcha()
})
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

.mode-switch button {
  height: 42px;
  border-radius: 14px;
  border: 1px solid rgba(172, 184, 205, 0.24);
  background: rgba(245, 247, 251, 0.88);
  color: #65758d;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.2s ease;
}

.mode-switch button.active {
  border-color: rgba(37, 99, 235, 0.22);
  background: rgba(37, 99, 235, 0.08);
  color: #2255cb;
}

.field-tip {
  margin: -8px 0 14px;
  color: #708198;
  font-size: 12px;
  line-height: 1.6;
}

.field-tip.warning {
  color: #c2410c;
}

.field-tip.success {
  color: #0f766e;
}

.captcha-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 128px;
  gap: 12px;
  align-items: start;
}

.captcha-form-item {
  margin-bottom: 0;
}

.captcha-button {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 50px;
  margin-top: 30px;
  padding: 0;
  overflow: hidden;
  border-radius: 16px;
  border: 1px solid rgba(179, 191, 211, 0.26);
  background: #f7faff;
  color: #46607f;
  cursor: pointer;
}

.captcha-button:disabled {
  cursor: wait;
  opacity: 0.8;
}

.captcha-button img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.helper-link {
  margin: 10px 0 0;
  color: #728197;
  font-size: 12px;
  line-height: 1.6;
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
  margin-top: 18px;
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

  .captcha-row {
    grid-template-columns: 1fr;
  }

  .captcha-button {
    margin-top: 0;
  }
}
</style>
