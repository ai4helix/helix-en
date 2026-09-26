<template>
  <div class="err">
    <div class="err__code">401</div>
    <p class="err__desc">Sorry, you do not have access permission for this page</p>
    <el-button type="primary" @click="$router.push('/')">Back to Home</el-button>
  </div>
</template>

<style scoped lang="scss">
.err {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--sp-4);
  height: 100%;
  min-height: 420px;

  &__code {
    font-size: 96px;
    font-weight: var(--fw-bold);
    letter-spacing: var(--ls-tight);
    color: var(--c-text-quaternary);
    line-height: 1;
  }

  &__desc {
    margin: 0;
    font-size: var(--fs-md);
    color: var(--c-text-secondary);
  }
}
</style>
