<template>
  <div class="navbar">
    <button class="navbar__hamburger" :class="{ 'is-collapsed': !appStore.sidebar.opened }" @click="toggleSideBar">
      <el-icon><Fold v-if="appStore.sidebar.opened" /><Expand v-else /></el-icon>
    </button>

    <Breadcrumb class="navbar__breadcrumb" />

    <div class="navbar__right">
      <el-tooltip :content="settingsStore.isDark ? 'Switch to light mode' : 'Switch to dark mode'" placement="bottom">
        <button class="navbar__icon-btn" @click="settingsStore.toggleTheme()">
          <el-icon><Moon v-if="!settingsStore.isDark" /><Sunny v-else /></el-icon>
        </button>
      </el-tooltip>

      <el-tooltip content="Refresh page" placement="bottom">
        <button class="navbar__icon-btn" @click="refresh">
          <el-icon><Refresh /></el-icon>
        </button>
      </el-tooltip>

      <el-tooltip v-if="tenantLabel" :content="`Organization: ${tenantLabel}`" placement="bottom">
        <div class="navbar__tenant">
          <el-icon><OfficeBuilding /></el-icon>
          <span class="navbar__tenant-name">{{ tenantLabel }}</span>
        </div>
      </el-tooltip>

      <el-tooltip content="Feedback" placement="bottom">
        <button class="navbar__icon-btn" @click="feedbackVisible = true">
          <el-icon><ChatLineRound /></el-icon>
        </button>
      </el-tooltip>

      <el-dropdown trigger="click" @command="handleCommand">
        <div class="navbar__avatar">
          <span class="navbar__avatar-mark">{{ initial }}</span>
          <span class="navbar__avatar-name">{{ auth.nickName || auth.account }}</span>
          <el-icon class="navbar__avatar-caret"><CaretBottom /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item disabled>
              <span class="navbar__meta">{{ auth.account }}</span>
            </el-dropdown-item>
            <el-dropdown-item v-if="tenantLabel" disabled>
              <span class="navbar__meta">Org: {{ tenantLabel }}</span>
            </el-dropdown-item>
            <el-dropdown-item v-if="settingsStore.showSettings" command="setLayout" divided>
              <el-icon><Setting /></el-icon><span>Layout Settings</span>
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>
              <el-icon><SwitchButton /></el-icon><span>Sign out</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <FeedbackDialog v-model="feedbackVisible" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { OfficeBuilding } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb/index.vue'
import FeedbackDialog from '@/views/feedback/FeedbackDialog.vue'
import useAppStore from '@/store/modules/app'
import useSettingsStore from '@/store/modules/settings'
import { useAuthStore } from '@/stores/auth'

const emit = defineEmits<{ (e: 'set-layout'): void }>()

const router = useRouter()
const appStore = useAppStore()
const settingsStore = useSettingsStore()
const auth = useAuthStore()

const feedbackVisible = ref(false)

const initial = computed(() => {
  const s = auth.nickName || auth.account || 'U'
  return s.trim().charAt(0).toUpperCase()
})

const tenantLabel = computed(() => {
  if (auth.isAdmin) return 'Platform'
  return auth.organName || ''
})

function toggleSideBar() {
  appStore.toggleSideBar()
}

function refresh() {
  const { fullPath, query, path } = router.currentRoute.value
  router.replace({ path: '/redirect' + path, query }).catch(() => {
    window.location.reload()
  })
}

function handleCommand(command: string) {
  if (command === 'setLayout') {
    emit('set-layout')
  } else if (command === 'logout') {
    logout()
  }
}

function logout() {
  ElMessageBox.confirm('Are you sure you want to sign out?', 'Notice', {
    confirmButtonText: 'OK',
    cancelButtonText: 'Cancel',
    type: 'warning'
  })
    .then(() => {
      auth.logout()
      ElMessage.success('Signed out')
      location.href = import.meta.env.BASE_URL + 'login'
    })
    .catch(() => {})
}
</script>

<style scoped lang="scss">
.navbar {
  position: relative;
  display: flex;
  align-items: center;
  height: var(--h-header);
  padding: 0 var(--sp-4);
  background: var(--c-surface-raised);
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  border-bottom: 1px solid var(--c-border);

  &__hamburger {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 30px;
    height: 30px;
    margin-right: var(--sp-2);
    padding: 0;
    border: none;
    border-radius: var(--r-sm);
    background: transparent;
    color: var(--c-text-secondary);
    font-size: 17px;
    cursor: pointer;
    transition: background-color var(--dur-fast) var(--ease-standard),
      color var(--dur-fast) var(--ease-standard);

    &:hover {
      background: var(--c-fill-quaternary);
      color: var(--c-text);
    }
  }

  &__breadcrumb {
    flex: 1;
    min-width: 0;
  }

  &__right {
    display: flex;
    align-items: center;
    gap: var(--sp-1);
  }

  &__icon-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 30px;
    height: 30px;
    padding: 0;
    border: none;
    border-radius: var(--r-sm);
    background: transparent;
    color: var(--c-text-secondary);
    font-size: 16px;
    cursor: pointer;
    transition: background-color var(--dur-fast) var(--ease-standard),
      color var(--dur-fast) var(--ease-standard);

    &:hover {
      background: var(--c-fill-quaternary);
      color: var(--c-text);
    }
  }

  &__tenant {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    max-width: 180px;
    height: 26px;
    margin-left: var(--sp-2);
    padding: 0 var(--sp-3);
    border: 1px solid var(--c-border);
    border-radius: var(--r-full);
    background: var(--c-fill-quaternary);
    color: var(--c-text-secondary);
    font-size: var(--fs-xs);

    &-name {
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }

  &__avatar {
    display: inline-flex;
    align-items: center;
    gap: var(--sp-2);
    height: 32px;
    margin-left: var(--sp-2);
    padding: 0 var(--sp-2) 0 4px;
    border-radius: var(--r-full);
    cursor: pointer;
    outline: none;
    transition: background-color var(--dur-fast) var(--ease-standard);

    &:hover {
      background: var(--c-fill-quaternary);
    }

    &-mark {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 24px;
      height: 24px;
      flex-shrink: 0;
      border-radius: var(--r-full);
      background: linear-gradient(135deg, var(--c-primary) 0%, #5e5ce6 100%);
      color: #fff;
      font-size: var(--fs-2xs);
      font-weight: var(--fw-semibold);
    }

    &-name {
      max-width: 100px;
      font-size: var(--fs-sm);
      font-weight: var(--fw-medium);
      color: var(--c-text);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    &-caret {
      font-size: 12px;
      color: var(--c-text-tertiary);
    }
  }

  &__meta {
    color: var(--c-text-tertiary);
    font-size: var(--fs-xs);
  }
}
</style>
