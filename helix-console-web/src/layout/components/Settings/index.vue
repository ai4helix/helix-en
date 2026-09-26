<template>
  <el-drawer
    v-model="visible"
    title="Layout Settings"
    direction="rtl"
    size="300px"
    :with-header="true"
  >
    <div class="settings">
      <div class="settings__group">
        <h4 class="settings__title">Theme Mode</h4>
        <div class="settings__modes">
          <div
            class="settings__mode"
            :class="{ 'is-active': !settingsStore.isDark }"
            @click="setMode(false)"
          >
            <div class="settings__mode-preview settings__mode-preview--light">
              <span class="settings__mode-side" />
              <span class="settings__mode-body" />
            </div>
            <span class="settings__mode-label">Light</span>
          </div>
          <div
            class="settings__mode"
            :class="{ 'is-active': settingsStore.isDark }"
            @click="setMode(true)"
          >
            <div class="settings__mode-preview settings__mode-preview--dark">
              <span class="settings__mode-side" />
              <span class="settings__mode-body" />
            </div>
            <span class="settings__mode-label">Dark</span>
          </div>
        </div>
      </div>

      <div class="settings__group">
        <h4 class="settings__title">Sidebar</h4>
        <div class="settings__modes">
          <div
            v-for="opt in sideThemes"
            :key="opt.value"
            class="settings__mode"
            :class="{ 'is-active': settingsStore.sideTheme === opt.value }"
            @click="changeSetting('sideTheme', opt.value)"
          >
            <div class="settings__mode-preview" :class="`settings__mode-preview--${opt.value}`">
              <span class="settings__mode-side" />
              <span class="settings__mode-body" />
            </div>
            <span class="settings__mode-label">{{ opt.label }}</span>
          </div>
        </div>
      </div>

      <div class="settings__group">
        <h4 class="settings__title">Interface</h4>
        <div class="settings__row">
          <span>Tags view</span>
          <el-switch
            :model-value="settingsStore.tagsView"
            @update:model-value="(v: any) => changeSetting('tagsView', v)"
          />
        </div>
        <div class="settings__row">
          <span>Fixed header</span>
          <el-switch
            :model-value="settingsStore.fixedHeader"
            @update:model-value="(v: any) => changeSetting('fixedHeader', v)"
          />
        </div>
        <div class="settings__row">
          <span>Show Logo</span>
          <el-switch
            :model-value="settingsStore.sidebarLogo"
            @update:model-value="(v: any) => changeSetting('sidebarLogo', v)"
          />
        </div>
        <div class="settings__row">
          <span>Dynamic title</span>
          <el-switch
            :model-value="settingsStore.dynamicTitle"
            @update:model-value="(v: any) => changeSetting('dynamicTitle', v)"
          />
        </div>
      </div>

      <div class="settings__group">
        <el-button class="settings__reset" @click="reset">Reset Layout</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import useSettingsStore from '@/store/modules/settings'
import defaultSettings from '@/settings'

const settingsStore = useSettingsStore()
const visible = ref(false)

const sideThemes = [
  { label: 'Light', value: 'theme-light' },
  { label: 'Dark', value: 'theme-dark' }
]

function openSetting() {
  visible.value = true
  nextTick(() => {
    visible.value = true
  })
}

function setMode(dark: boolean) {
  if (dark !== settingsStore.isDark) {
    settingsStore.toggleTheme()
  }
}

function changeSetting(key: string, value: any) {
  settingsStore.changeSetting({ key, value })
  persist()
}

function persist() {
  const data = {
    theme: settingsStore.theme,
    sideTheme: settingsStore.sideTheme,
    topNav: settingsStore.topNav,
    tagsView: settingsStore.tagsView,
    fixedHeader: settingsStore.fixedHeader,
    sidebarLogo: settingsStore.sidebarLogo,
    dynamicTitle: settingsStore.dynamicTitle,
    isDark: settingsStore.isDark
  }
  localStorage.setItem('layout-setting', JSON.stringify(data))
}

function reset() {
  localStorage.removeItem('layout-setting')
  settingsStore.sideTheme = defaultSettings.sideTheme
  settingsStore.tagsView = defaultSettings.tagsView
  settingsStore.fixedHeader = defaultSettings.fixedHeader
  settingsStore.sidebarLogo = defaultSettings.sidebarLogo
  settingsStore.dynamicTitle = defaultSettings.dynamicTitle
  if (settingsStore.isDark) settingsStore.toggleTheme()
}

watch(
  () => [settingsStore.sideTheme, settingsStore.tagsView, settingsStore.fixedHeader, settingsStore.sidebarLogo, settingsStore.dynamicTitle, settingsStore.isDark],
  persist
)

defineExpose({ openSetting })
</script>

<style scoped lang="scss">
.settings {
  &__group {
    margin-bottom: var(--sp-6);
  }

  &__title {
    margin: 0 0 var(--sp-3);
    font-size: var(--fs-sm);
    font-weight: var(--fw-semibold);
    color: var(--c-text);
  }

  &__modes {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: var(--sp-3);
  }

  &__mode {
    cursor: pointer;
    text-align: center;

    &.is-active .settings__mode-preview {
      outline: 2px solid var(--c-primary);
      outline-offset: 2px;
    }

    &.is-active .settings__mode-label {
      color: var(--c-primary);
      font-weight: var(--fw-semibold);
    }
  }

  &__mode-preview {
    position: relative;
    height: 44px;
    border-radius: var(--r-sm);
    border: 1px solid var(--c-border);
    overflow: hidden;
    background: #f5f5f7;
    transition: outline-color var(--dur-fast) var(--ease-standard);

    &--light {
      background: #f5f5f7;
      .settings__mode-side {
        background: #fff;
      }
      .settings__mode-body {
        background: #fff;
      }
    }

    &--dark {
      background: #1c1c1e;
      .settings__mode-side {
        background: #000;
      }
      .settings__mode-body {
        background: #2c2c2e;
      }
    }

    &--theme-light {
      background: #f5f5f7;
      .settings__mode-side {
        background: #fff;
      }
      .settings__mode-body {
        background: #fff;
      }
    }

    &--theme-dark {
      background: #1c1c1e;
      .settings__mode-side {
        background: #000;
      }
      .settings__mode-body {
        background: #2c2c2e;
      }
    }
  }

  &__mode-side {
    position: absolute;
    top: 0;
    left: 0;
    width: 30%;
    height: 100%;
  }

  &__mode-body {
    position: absolute;
    top: 0;
    right: 0;
    width: 70%;
    height: 100%;
    opacity: 0.9;
  }

  &__mode-label {
    display: block;
    margin-top: var(--sp-2);
    font-size: var(--fs-xs);
    color: var(--c-text-secondary);
  }

  &__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--sp-2) 0;
    font-size: var(--fs-sm);
    color: var(--c-text-secondary);
  }

  &__reset {
    width: 100%;
  }
}
</style>
