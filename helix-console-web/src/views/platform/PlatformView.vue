<template>
  <div class="platform">
    <header class="platform__top">
      <h2 class="platform__title">Platform Operations</h2>
      <p class="platform__desc">Tenant list, role overview, and per-tenant engine run statistics</p>
    </header>

    <div class="cards" v-loading="loading">
      <div class="card">
        <div class="card__label">Total Tenants</div>
        <div class="card__value">{{ overview?.tenantCount ?? '-' }}</div>
      </div>
      <div class="card">
        <div class="card__label">Total Users</div>
        <div class="card__value">{{ overview?.userCount ?? '-' }}</div>
      </div>
      <div class="card">
        <div class="card__label">Total Decisions</div>
        <div class="card__value">{{ overview?.decisionTotal ?? '-' }}</div>
        <div class="card__sub">Today {{ overview?.decisionToday ?? 0 }}</div>
      </div>
      <div class="card">
        <div class="card__label">Batch Tasks</div>
        <div class="card__value">{{ overview?.batchCount ?? '-' }}</div>
        <div class="card__sub">{{ overview?.batchRows ?? 0 }} rows total</div>
      </div>
    </div>

    <el-tabs v-model="tab" class="platform__tabs">
      <el-tab-pane label="Tenants" name="tenants">
        <el-table :data="tenants" v-loading="loading" size="default" class="platform__table">
          <el-table-column prop="organId" label="ID" width="64" />
          <el-table-column prop="name" label="Organization Name" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.organId === 1 ? `${row.name} (Platform)` : row.name }}
            </template>
          </el-table-column>
          <el-table-column prop="code" label="Code" width="140" show-overflow-tooltip />
          <el-table-column prop="userCount" label="Users" width="84" align="right" />
          <el-table-column prop="roleCount" label="Roles" width="84" align="right" />
          <el-table-column label="Total Decisions" width="100" align="right">
            <template #default="{ row }">{{ fmt(row.decisionTotal) }}</template>
          </el-table-column>
          <el-table-column label="Today" width="80" align="right">
            <template #default="{ row }">{{ fmt(row.decisionToday) }}</template>
          </el-table-column>
          <el-table-column label="Pass/Reject/Manual" width="130" align="center">
            <template #default="{ row }">
              {{ fmt(row.passCount) }} / {{ fmt(row.rejectCount) }} / {{ fmt(row.manualCount) }}
            </template>
          </el-table-column>
          <el-table-column label="Pass Rate" width="88" align="right">
            <template #default="{ row }">{{ row.passRate }}%</template>
          </el-table-column>
          <el-table-column prop="lastDecisionTime" label="Last Decision" width="160">
            <template #default="{ row }">{{ row.lastDecisionTime || '—' }}</template>
          </el-table-column>
          <el-table-column label="Batch (Tasks/Rows/OK)" width="150" align="center">
            <template #default="{ row }">
              {{ fmt(row.batchCount) }} / {{ fmt(row.batchRows) }} / {{ fmt(row.batchRowsOk) }}
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="Roles" name="roles">
        <el-table :data="roles" v-loading="loadingRoles" size="default" class="platform__table">
          <el-table-column prop="roleId" label="ID" width="72" />
          <el-table-column prop="roleName" label="Role Name" min-width="140" show-overflow-tooltip />
          <el-table-column prop="roleCode" label="Role Code" width="180" show-overflow-tooltip />
          <el-table-column prop="organName" label="Organization" width="160" show-overflow-tooltip />
          <el-table-column prop="roleDesc" label="Description" min-width="200" show-overflow-tooltip />
          <el-table-column prop="userCount" label="Users" width="100" align="right" />
          <el-table-column label="Status" width="84">
            <template #default="{ row }">
              <span class="tag" :class="row.status === 1 ? 'is-primary' : 'is-muted'">
                {{ row.status === 1 ? 'Enabled' : row.status === 0 ? 'Disabled' : 'Deleted' }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="Engine Stats" name="engine">
        <el-table :data="engineStats" v-loading="loadingEngine" size="default" class="platform__table">
          <el-table-column prop="organName" label="Organization" min-width="180" show-overflow-tooltip />
          <el-table-column label="Total Decisions" width="100" align="right">
            <template #default="{ row }">{{ fmt(row.decisionTotal) }}</template>
          </el-table-column>
          <el-table-column label="Today" width="80" align="right">
            <template #default="{ row }">{{ fmt(row.decisionToday) }}</template>
          </el-table-column>
          <el-table-column label="Pass" width="90" align="right">
            <template #default="{ row }">{{ fmt(row.passCount) }}</template>
          </el-table-column>
          <el-table-column label="Reject" width="90" align="right">
            <template #default="{ row }">{{ fmt(row.rejectCount) }}</template>
          </el-table-column>
          <el-table-column label="Manual Review" width="90" align="right">
            <template #default="{ row }">{{ fmt(row.manualCount) }}</template>
          </el-table-column>
          <el-table-column label="Pass Rate" width="90" align="right">
            <template #default="{ row }">{{ row.passRate }}%</template>
          </el-table-column>
          <el-table-column prop="lastDecisionTime" label="Last Decision" width="160">
            <template #default="{ row }">{{ row.lastDecisionTime || '—' }}</template>
          </el-table-column>
          <el-table-column label="Batch (Tasks/Rows/OK)" width="150" align="center">
            <template #default="{ row }">
              {{ fmt(row.batchCount) }} / {{ fmt(row.batchRows) }} / {{ fmt(row.batchRowsOk) }}
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  getEngineStats,
  getOverview,
  getRoles,
  getTenants,
  type EngineStat,
  type PlatformOverview,
  type RoleStat,
  type TenantStat
} from '@/api/platform'

const tab = ref('tenants')
const loading = ref(false)
const loadingRoles = ref(false)
const loadingEngine = ref(false)
const overview = ref<PlatformOverview | null>(null)
const tenants = ref<TenantStat[]>([])
const roles = ref<RoleStat[]>([])
const engineStats = ref<EngineStat[]>([])

const fmt = (n?: number) => (n == null ? '0' : n.toLocaleString())

async function reload() {
  loading.value = true
  try {
    const [ov, ts] = await Promise.all([getOverview(), getTenants()])
    overview.value = ov
    tenants.value = ts
  } finally {
    loading.value = false
  }
}

async function reloadRoles() {
  loadingRoles.value = true
  try {
    roles.value = await getRoles()
  } finally {
    loadingRoles.value = false
  }
}

async function reloadEngine() {
  loadingEngine.value = true
  try {
    engineStats.value = await getEngineStats()
  } finally {
    loadingEngine.value = false
  }
}

onMounted(() => {
  reload()
  reloadRoles()
  reloadEngine()
})
</script>

<style scoped>
.platform {
  padding: 20px 24px;
  height: 100%;
  overflow: auto;
}
.platform__top {
  margin-bottom: 14px;
}
.platform__title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}
.platform__desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}
.card {
  padding: 14px 18px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
}
.card__label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.card__value {
  margin-top: 6px;
  font-size: 26px;
  font-weight: 600;
  line-height: 1.2;
}
.card__sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.platform__table {
  width: 100%;
}
.tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 12px;
}
.tag.is-primary {
  background: var(--el-color-success-light-8);
  color: var(--el-color-success);
}
.tag.is-muted {
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
}
</style>
