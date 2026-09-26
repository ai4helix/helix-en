<template>
  <div class="login">
    <div class="login__brand">
      <div class="brand">
        <div class="brand__mark"><el-icon :size="22"><Share /></el-icon></div>
        <div class="brand__text">
          <div class="brand__title">Decision Engine</div>
          <div class="brand__sub">Helix Rules X6</div>
        </div>
      </div>

      <div class="brand__slogan">
        <h1>Visual decision flows<br />Risk strategy, WYSIWYG</h1>
        <p>Flow orchestration based on AntV X6, combined with rules, scorecards, and list libraries, to build an executable decision engine.</p>
      </div>

      <ul class="brand__features">
        <li><span class="dot" />Drag-and-drop flow orchestration</li>
        <li><span class="dot" />Auto-generated rule expressions</li>
        <li><span class="dot" />Multi-dimension scorecard weighting</li>
        <li><span class="dot" />Batch testing and result tracing</li>
      </ul>
    </div>

    <div class="login__panel">
      <div class="form">
        <div class="form__head">
          <h2>Welcome back</h2>
          <p>Sign in to continue using the decision engine</p>
        </div>

        <form @submit.prevent="handleLogin">
          <div class="field">
            <label class="field__label">Account</label>
            <el-input
              v-model="form.account"
              size="large"
              placeholder="Enter your account"
              autocomplete="username"
            >
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </div>

          <div class="field">
            <label class="field__label">Password</label>
            <el-input
              v-model="form.password"
              type="password"
              size="large"
              placeholder="Enter your password"
              show-password
              autocomplete="current-password"
              @keyup.enter="handleLogin"
            >
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </div>

          <div v-if="errorMsg" class="error">
            <el-icon :size="13"><WarningFilled /></el-icon>
            <span>{{ errorMsg }}</span>
          </div>

          <button class="submit" type="submit" :disabled="loading">
            {{ loading ? 'Signing in…' : 'Sign in' }}
          </button>
        </form>

        <div class="register-entry">
          <button type="button" class="register-link" @click="openRegister">
            New organization? Register with phone number <el-icon :size="12"><ArrowRight /></el-icon>
          </button>
        </div>

        <div class="demo">
          <div class="demo__label">Demo accounts (password is Init@1234 for all)</div>
          <div class="demo__list">
            <button v-for="d in demoAccounts" :key="d.account" class="demo__item" type="button" @click="fill(d)">
              <span class="demo__role">{{ d.role }}</span>
              <span class="demo__account">{{ d.account }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="regVisible" title="Organization Registration" width="420px" :close-on-click-modal="false">
      <div class="reg">
        <div class="reg__tips">Registration creates a tenant organization and a default "Tenant Admin" account (phone-number sign-in); add other roles in System Management.</div>
        <div class="field">
          <label class="field__label">Phone Number</label>
          <el-input v-model="reg.phone" size="large" maxlength="11" placeholder="Enter phone number">
            <template #prefix><el-icon><Iphone /></el-icon></template>
          </el-input>
        </div>
        <div class="field">
          <label class="field__label">SMS Code</label>
          <div class="reg__captcha">
            <el-input v-model="reg.code" size="large" maxlength="6" placeholder="6-digit code" />
            <button type="button" class="reg__send" :disabled="regCountdown > 0 || regSending" @click="sendCode">
              {{ regSending ? 'Sending…' : regCountdown > 0 ? `Resend in ${regCountdown}s` : 'Get code' }}
            </button>
          </div>
          <div v-if="regDebugCode" class="reg__debug">Debug code: {{ regDebugCode }} (not shown in production)</div>
        </div>
        <div class="field">
          <label class="field__label">Organization Name</label>
          <el-input v-model="reg.orgName" size="large" maxlength="64" placeholder="e.g. Example Technology Co., Ltd." />
        </div>
        <div class="field">
          <label class="field__label">Admin Nickname (optional)</label>
          <el-input v-model="reg.nickName" size="large" maxlength="64" :placeholder="`${reg.orgName || 'Organization'} admin`" />
        </div>
        <div class="field">
          <label class="field__label">Password</label>
          <el-input
            v-model="reg.password"
            type="password"
            size="large"
            show-password
            placeholder="At least 8 characters with letters and digits"
          />
        </div>
        <div class="field">
          <label class="field__label">Confirm Password</label>
          <el-input v-model="reg.confirm" type="password" size="large" show-password placeholder="Re-enter password" />
        </div>
        <div v-if="regError" class="error">
          <el-icon :size="13"><WarningFilled /></el-icon>
          <span>{{ regError }}</span>
        </div>
      </div>
      <template #footer>
        <button type="button" class="reg__cancel" @click="regVisible = false">Cancel</button>
        <button type="button" class="submit reg__submit" :disabled="regLoading" @click="handleRegister">
          {{ regLoading ? 'Registering…' : 'Register and Open Organization' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Iphone } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { registerTenant, sendRegisterCaptcha } from '@/api/system'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const form = reactive({ account: '', password: '' })
const loading = ref(false)
const errorMsg = ref('')

function normalizeRedirect(raw: string | undefined): string {
  const base = import.meta.env.BASE_URL
  if (base !== '/' && raw && raw.startsWith(base)) {
    return raw.slice(base.length - 1)
  }
  return raw || '/'
}

function targetAfterLogin(): string {
  return normalizeRedirect(route.query.redirect as string) || (auth.isAdmin ? '/platform' : '/workbench')
}

const demoAccounts = [
  { role: 'Tenant Admin (Demo Tenant Tech)', account: '13700000001' },
  { role: 'Tenant Operator (Demo Tenant Tech)', account: 'strategist' }
]

onMounted(() => {
  if (auth.isLoggedIn) {
    router.replace(targetAfterLogin())
  }
})

function fill(d: { account: string }) {
  form.account = d.account
  form.password = 'Init@1234'
  errorMsg.value = ''
}

async function handleLogin() {
  if (!form.account.trim() || !form.password) {
    errorMsg.value = 'Enter account and password'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    await auth.login(form.account.trim(), form.password)
    router.replace(targetAfterLogin())
  } catch (e: any) {
    errorMsg.value = e?.message || 'Sign-in failed'
  } finally {
    loading.value = false
  }
}

const regVisible = ref(false)
const regLoading = ref(false)
const regSending = ref(false)
const regCountdown = ref(0)
const regError = ref('')
const regDebugCode = ref('')
const reg = reactive({ phone: '', code: '', password: '', confirm: '', orgName: '', nickName: '' })
let countdownTimer: number | undefined

function openRegister() {
  reg.phone = ''
  reg.code = ''
  reg.password = ''
  reg.confirm = ''
  reg.orgName = ''
  reg.nickName = ''
  regError.value = ''
  regDebugCode.value = ''
  regVisible.value = true
}

function validateReg(): string {
  if (!/^1[3-9]\d{9}$/.test(reg.phone)) return 'Enter a valid phone number'
  if (!/^\d{6}$/.test(reg.code)) return 'Enter the 6-digit SMS code'
  if (!reg.orgName.trim()) return 'Enter the organization name'
  if (reg.password.length < 8 || !/[a-zA-Z]/.test(reg.password) || !/\d/.test(reg.password)) {
    return 'Password must be at least 8 characters with both letters and digits'
  }
  if (reg.password !== reg.confirm) return 'Passwords do not match'
  return ''
}

async function sendCode() {
  if (!/^1[3-9]\d{9}$/.test(reg.phone)) {
    regError.value = 'Enter a valid phone number'
    return
  }
  regError.value = ''
  regSending.value = true
  try {
    const rsp = await sendRegisterCaptcha(reg.phone)
    if (rsp.code) regDebugCode.value = rsp.code
    ElMessage.success('Code sent')
    regCountdown.value = rsp.cooldown || 60
    countdownTimer = window.setInterval(() => {
      regCountdown.value--
      if (regCountdown.value <= 0) window.clearInterval(countdownTimer)
    }, 1000)
  } catch (e: any) {
    regError.value = e?.message || 'Failed to send code'
  } finally {
    regSending.value = false
  }
}

async function handleRegister() {
  const invalid = validateReg()
  if (invalid) {
    regError.value = invalid
    return
  }
  regError.value = ''
  regLoading.value = true
  try {
    const rsp = await registerTenant({
      phone: reg.phone,
      code: reg.code,
      password: reg.password,
      orgName: reg.orgName.trim(),
      nickName: reg.nickName.trim() || undefined
    })
    regVisible.value = false
    ElMessage.success(`Organization "${rsp.organName}" created. Admin account ${rsp.account}, please sign in`)
    form.account = rsp.account
    form.password = reg.password
  } catch (e: any) {
    regError.value = e?.message || 'Registration failed'
  } finally {
    regLoading.value = false
  }
}

onBeforeUnmount(() => window.clearInterval(countdownTimer))
</script>

<style scoped lang="scss">
.login {
  display: flex;
  height: 100vh;
  background: var(--c-bg);
  overflow: hidden;

  &__brand {
    flex: 1.1;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    padding: 56px 64px;
    background: linear-gradient(145deg, #0a84ff 0%, #5e5ce6 55%, #bf5af2 100%);
    color: #fff;
    position: relative;
    overflow: hidden;

    &::before,
    &::after {
      content: '';
      position: absolute;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.12);
      filter: blur(2px);
    }

    &::before {
      width: 420px;
      height: 420px;
      top: -140px;
      right: -120px;
    }

    &::after {
      width: 300px;
      height: 300px;
      bottom: -100px;
      left: -80px;
      background: rgba(255, 255, 255, 0.08);
    }
  }

  &__panel {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 40px;
    background: var(--c-surface);
  }
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
  z-index: 1;

  &__mark {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 44px;
    height: 44px;
    border-radius: var(--r-md);
    background: rgba(255, 255, 255, 0.18);
    backdrop-filter: blur(10px);
    border: 1px solid rgba(255, 255, 255, 0.25);
  }

  &__title {
    font-size: var(--fs-lg);
    font-weight: var(--fw-semibold);
    letter-spacing: var(--ls-tight);
  }

  &__sub {
    font-size: var(--fs-xs);
    opacity: 0.75;
    font-family: var(--font-mono);
    letter-spacing: 0.04em;
  }

  &__slogan {
    position: relative;
    z-index: 1;

    h1 {
      font-size: 38px;
      font-weight: var(--fw-bold);
      line-height: 1.28;
      letter-spacing: -0.03em;
      margin-bottom: 18px;
    }

    p {
      font-size: var(--fs-md);
      line-height: 1.7;
      opacity: 0.85;
      max-width: 420px;
    }
  }

  &__features {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 14px 24px;
    position: relative;
    z-index: 1;

    li {
      display: flex;
      align-items: center;
      gap: 9px;
      font-size: var(--fs-sm);
      opacity: 0.9;
    }

    .dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.85);
      flex-shrink: 0;
    }
  }
}

.form {
  width: 100%;
  max-width: 360px;

  &__head {
    margin-bottom: 32px;

    h2 {
      font-size: 28px;
      font-weight: var(--fw-semibold);
      letter-spacing: -0.02em;
      color: var(--c-text);
      margin-bottom: 6px;
    }

    p {
      font-size: var(--fs-sm);
      color: var(--c-text-tertiary);
    }
  }
}

.field {
  margin-bottom: 18px;

  &__label {
    display: block;
    font-size: var(--fs-xs);
    font-weight: var(--fw-medium);
    color: var(--c-text-secondary);
    margin-bottom: 7px;
  }
}

.error {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 12px;
  margin-bottom: 16px;
  border-radius: var(--r-sm);
  background: var(--c-danger-soft);
  color: var(--c-danger);
  font-size: var(--fs-xs);
}

.submit {
  width: 100%;
  height: 44px;
  border: none;
  border-radius: var(--r-md);
  background: var(--c-primary);
  color: #fff;
  font-size: var(--fs-md);
  font-weight: var(--fw-semibold);
  letter-spacing: 0.06em;
  cursor: pointer;
  box-shadow: var(--sh-sm), var(--sh-inset);
  transition: all var(--dur-fast) var(--ease-standard);

  &:hover:not(:disabled) {
    background: var(--c-primary-hover);
    box-shadow: var(--sh-md), var(--sh-inset);
  }

  &:active:not(:disabled) {
    transform: scale(0.985);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.register-entry {
  margin-top: 18px;
  text-align: center;

  .register-link {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    border: none;
    background: none;
    color: var(--c-primary);
    font-size: var(--fs-sm);
    cursor: pointer;

    &:hover {
      text-decoration: underline;
    }
  }
}

.reg {
  &__tips {
    margin-bottom: 16px;
    padding: 8px 12px;
    border-radius: var(--r-sm);
    background: var(--c-primary-soft, rgba(10, 132, 255, 0.08));
    color: var(--c-text-secondary);
    font-size: var(--fs-xs);
    line-height: 1.6;
  }

  &__captcha {
    display: flex;
    gap: 10px;

    .el-input {
      flex: 1;
    }
  }

  &__send {
    flex-shrink: 0;
    min-width: 110px;
    border: 1px solid var(--c-primary);
    border-radius: var(--r-md);
    background: var(--c-primary-soft, rgba(10, 132, 255, 0.08));
    color: var(--c-primary);
    font-size: var(--fs-sm);
    cursor: pointer;

    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  }

  &__debug {
    margin-top: 6px;
    font-size: var(--fs-2xs);
    color: var(--c-warning, #d97706);
  }

  &__cancel {
    padding: 8px 18px;
    border: 1px solid var(--c-border);
    border-radius: var(--r-md);
    background: var(--c-surface);
    color: var(--c-text-secondary);
    font-size: var(--fs-sm);
    cursor: pointer;
    margin-right: 10px;

    &:hover {
      border-color: var(--c-primary);
      color: var(--c-primary);
    }
  }

  &__submit {
    width: auto;
    height: auto;
    padding: 9px 18px;
    font-size: var(--fs-sm);
  }
}

.demo {
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid var(--c-separator);

  &__label {
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
    margin-bottom: 10px;
  }

  &__list {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
  }

  &__item {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
    padding: 8px 11px;
    border-radius: var(--r-sm);
    border: 1px solid var(--c-border);
    background: var(--c-surface);
    cursor: pointer;
    text-align: left;
    transition: all var(--dur-fast) var(--ease-standard);

    &:hover {
      border-color: var(--c-primary);
      background: var(--c-primary-soft);
    }
  }

  &__role {
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
  }

  &__account {
    font-size: var(--fs-xs);
    font-weight: var(--fw-medium);
    color: var(--c-text);
    font-family: var(--font-mono);
  }
}

@media (max-width: 900px) {
  .login__brand {
    display: none;
  }
}
</style>
