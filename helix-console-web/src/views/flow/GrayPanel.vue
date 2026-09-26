<template>
  <el-dialog
    v-model="visible"
    title="Gray Release"
    width="880px"
    :close-on-click-modal="false"
    @open="refresh"
  >
    <el-alert
      v-if="tracks.length"
      type="info"
      :closable="false"
      style="margin-bottom: 12px"
    >
      Multiple tracks of the same version coexist; traffic is split by the normalized ratio
      "weight ÷ total weight". Shadow tracks only evaluate and record, without returning results.
      If something goes wrong, use "Full Switch" on the old track to roll back instantly.
    </el-alert>

    <div class="gray-bar">
      <span class="gray-label">Gray Release:</span>
      <el-input-number v-model="grayWeight" :min="1" :max="100" size="small" />
      <span class="gray-pct">% of traffic to the new track (current canvas config)</span>
      <el-button type="primary" size="small" :loading="publishing" @click="onGrayPublish">
        Publish Gray Track
      </el-button>
      <el-button size="small" @click="refresh">Refresh</el-button>
    </div>

    <el-table :data="tracks" size="small" border v-loading="loading">
      <el-table-column label="Track" width="70">
        <template #default="{ row }">#{{ row.publishSeq ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="Artifact" width="140">
        <template #default="{ row }">
          <span class="mono">{{ row.sha256 || 'Live DB' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="Weight" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.trafficWeight > 0 ? 'primary' : 'info'" size="small">
            {{ row.trafficWeight }}%
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Mode" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.shadow" type="warning" size="small">Shadow</el-tag>
          <el-tag v-else type="success" size="small">Normal</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Status" width="110" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.routable" type="success" size="small" effect="plain">Routing</el-tag>
          <el-tag v-else-if="row.shadow" type="warning" size="small" effect="plain">Evaluating</el-tag>
          <el-tag v-else type="info" size="small" effect="plain">Paused</el-tag>
          <el-tag v-if="!row.routed" type="danger" size="small" effect="plain">Inactive</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="publishedTime" label="Publish Time" width="160" />
      <el-table-column label="Actions" min-width="240">
        <template #default="{ row }">
          <template v-if="row.publishId">
            <el-button size="small" link type="primary" @click="onWeight(row)">Weight</el-button>
            <el-button size="small" link :type="row.shadow ? 'success' : 'warning'" @click="onShadow(row)">
              {{ row.shadow ? 'Exit Shadow' : 'To Shadow' }}
            </el-button>
            <el-popconfirm title="This track will take over 100% of traffic and pause all others. Confirm?" width="240" @confirm="onPromote(row)">
              <template #reference>
                <el-button size="small" link type="danger">Full Switch</el-button>
              </template>
            </el-popconfirm>
          </template>
          <span v-else class="muted">Live DB self-healing track (solidified after publish)</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="routing-head">
      <span>Current snapshot routing (verify after reload)</span>
      <el-button size="small" link @click="loadRouting">Refresh View</el-button>
    </div>
    <el-table :data="routing" size="small" border>
      <el-table-column label="Artifact" width="90">
        <template #default="{ row }">{{ row.publishId ?? 'Live DB' }}</template>
      </el-table-column>
      <el-table-column prop="trafficWeight" label="Weight%" width="80" align="center" />
      <el-table-column label="Shadow" width="70" align="center">
        <template #default="{ row }">{{ row.shadow ? 'Yes' : 'No' }}</template>
      </el-table-column>
      <el-table-column label="Routable" width="80" align="center">
        <template #default="{ row }">{{ row.routable ? 'Yes' : 'No' }}</template>
      </el-table-column>
      <el-table-column prop="nodeCount" label="Nodes" width="70" align="center" />
      <el-table-column prop="rulePlanCount" label="Rule Plans" width="90" align="center" />
      <el-table-column label="Notes">
        <template #default="{ row }">
          <span v-if="row.liveFallback" class="muted">Live DB self-healing channel (no published artifact for this version)</span>
          <span v-else class="muted">Published artifact track</span>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="visible = false">Close</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import {
  listTracks,
  routingView,
  grayPublish,
  setTrackWeight,
  setTrackShadow,
  promoteTrack,
  type PublishTrack,
  type RoutingView
} from '@/api/flow'

const props = defineProps<{ modelValue: boolean; versionId?: number | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})

const loading = ref(false)
const publishing = ref(false)
const tracks = ref<PublishTrack[]>([])
const routing = ref<RoutingView[]>([])
const grayWeight = ref(10)

async function refresh() {
  if (!props.versionId) return
  loading.value = true
  try {
    tracks.value = await listTracks(props.versionId)
    routing.value = await routingView(props.versionId)
  } finally {
    loading.value = false
  }
}

async function loadRouting() {
  if (!props.versionId) return
  routing.value = await routingView(props.versionId)
}

async function onGrayPublish() {
  if (!props.versionId) return
  publishing.value = true
  try {
    await grayPublish(props.versionId, grayWeight.value)
    ElMessage.success(`Gray track published; the new track takes ${grayWeight.value}% of traffic`)
    await refresh()
  } finally {
    publishing.value = false
  }
}

async function onWeight(row: PublishTrack) {
  if (!row.publishId) return
  const { value } = await ElMessageBox.prompt('Enter the new weight for this track (0 = pause the track)', 'Adjust Weight', {
    inputValue: String(row.trafficWeight),
    inputPattern: /^\d+$/,
    inputErrorMessage: 'Please enter an integer between 0 and 100'
  })
  const w = Math.max(0, Math.min(100, parseInt(value, 10)))
  tracks.value = await setTrackWeight(row.publishId, w)
  routing.value = await routingView(props.versionId!)
  ElMessage.success(`Track #${row.publishSeq} weight adjusted to ${w}%`)
}

async function onShadow(row: PublishTrack) {
  if (!row.publishId) return
  tracks.value = await setTrackShadow(row.publishId, !row.shadow)
  routing.value = await routingView(props.versionId!)
  ElMessage.success(row.shadow ? 'Exited shadow mode' : 'Entered shadow mode (evaluate and record only, no result returned)')
}

async function onPromote(row: PublishTrack) {
  if (!row.publishId) return
  tracks.value = await promoteTrack(row.publishId)
  routing.value = await routingView(props.versionId!)
  ElMessage.success(`Track #${row.publishSeq} now takes 100% of traffic`)
}
</script>

<style scoped>
.gray-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.gray-label {
  font-weight: 600;
}
.gray-pct {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.mono {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.routing-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 16px 0 8px;
  font-weight: 600;
  font-size: 13px;
}
</style>
