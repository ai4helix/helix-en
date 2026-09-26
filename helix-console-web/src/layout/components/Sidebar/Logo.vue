<template>
  <div class="sidebar-logo" :class="{ collapse }">
    <router-link to="/" class="sidebar-logo__link">
      <span class="sidebar-logo__mark">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none">
          <path
            d="M12 2.5 20.5 7v10L12 21.5 3.5 17V7L12 2.5Z"
            stroke="currentColor"
            stroke-width="1.6"
            stroke-linejoin="round"
          />
          <path d="M12 8v8M8 12h8" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
        </svg>
      </span>
      <transition name="sidebar-title-fade">
        <span v-if="!collapse" class="sidebar-logo__text">{{ title }}</span>
      </transition>
    </router-link>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import defaultSettings from '@/settings'

defineProps<{ collapse: boolean }>()

const title = computed(() => defaultSettings.title)
</script>

<style scoped lang="scss">
.sidebar-logo {
  height: var(--h-header);
  display: flex;
  align-items: center;
  padding: 0 var(--sp-3);
  overflow: hidden;

  &.collapse {
    justify-content: center;
    padding: 0;
  }

  &__link {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    text-decoration: none;
    color: var(--c-text);
  }

  &__mark {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    width: 28px;
    height: 28px;
    border-radius: var(--r-sm);
    background: linear-gradient(135deg, var(--c-primary) 0%, #5e5ce6 100%);
    color: #fff;
    box-shadow: var(--sh-xs);
  }

  &__text {
    font-size: var(--fs-md);
    font-weight: var(--fw-semibold);
    letter-spacing: var(--ls-snug);
    white-space: nowrap;
  }
}

.sidebar-title-fade-enter-active,
.sidebar-title-fade-leave-active {
  transition: opacity var(--dur-fast) var(--ease-standard);
}
.sidebar-title-fade-enter-from,
.sidebar-title-fade-leave-to {
  opacity: 0;
}
</style>
