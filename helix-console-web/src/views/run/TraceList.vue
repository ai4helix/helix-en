<template>
  <div class="traces">
    <div v-for="(t, i) in traces" :key="i" class="trace" :class="{ 'is-hit': t.hit }">
      <div class="trace__idx" :class="{ 'is-hit': t.hit }">{{ i + 1 }}</div>
      <div class="trace__body">
        <div class="trace__head">
          <span class="trace__name">{{ t.nodeName || t.nodeCode || ('Node ' + (t.nodeId ?? '')) }}</span>
          <span
            v-if="t.scoreDelta"
            class="trace__delta"
            :class="t.scoreDelta > 0 ? 'is-up' : 'is-down'"
          >
            {{ t.scoreDelta > 0 ? '+' : '' }}{{ t.scoreDelta }}
          </span>
        </div>
        <div class="trace__msg">{{ t.message || '—' }}</div>
        <ul v-if="t.hitDetails?.length" class="trace__hits">
          <li v-for="(h, j) in t.hitDetails" :key="j">{{ h }}</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { NodeTrace } from '@/api/result'

defineProps<{ traces: NodeTrace[] }>()
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss';

.traces {
  display: flex;
  flex-direction: column;
}

.trace {
  display: flex;
  gap: var(--sp-3);
  padding-bottom: var(--sp-4);
  position: relative;

  &:not(:last-child)::before {
    content: '';
    position: absolute;
    left: 10px;
    top: 24px;
    bottom: 0;
    width: 1px;
    background: var(--c-border);
  }

  &__idx {
    width: 21px;
    height: 21px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--r-full);
    background: var(--c-fill-tertiary);
    color: var(--c-text-tertiary);
    font-size: var(--fs-2xs);
    font-weight: var(--fw-semibold);
    z-index: 1;

    &.is-hit {
      background: var(--c-primary);
      color: var(--c-text-inverse);
    }
  }

  &__body {
    flex: 1;
    min-width: 0;
  }

  &__head {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
  }

  &__name {
    font-size: var(--fs-sm);
    font-weight: var(--fw-medium);
    color: var(--c-text);
  }

  &__delta {
    font-size: var(--fs-2xs);
    font-weight: var(--fw-semibold);
    font-family: var(--font-mono);

    &.is-up {
      color: var(--c-success);
    }

    &.is-down {
      color: var(--c-danger);
    }
  }

  &__msg {
    font-size: var(--fs-xs);
    color: var(--c-text-secondary);
    line-height: var(--lh-normal);
  }

  &__hits {
    margin: 4px 0 0;
    padding-left: 14px;
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);

    li {
      list-style: disc;
      line-height: 1.7;
    }
  }
}
</style>
