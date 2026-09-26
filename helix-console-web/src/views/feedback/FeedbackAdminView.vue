<template>
  <div class="fb-admin">
    <el-card shadow="never" class="fb-admin__filter">
      <div class="fb-admin__filter-row">
        <el-radio-group v-model="status" @change="reload">
          <el-radio-button :value="-1">All</el-radio-button>
          <el-radio-button :value="0">Pending</el-radio-button>
          <el-radio-button :value="1">Handled</el-radio-button>
        </el-radio-group>
        <el-input
          v-model="keyword"
          class="fb-admin__search"
          placeholder="Search account / feedback content"
          clearable
          @keyup.enter="reload"
          @clear="reload"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="records" stripe>
        <el-table-column prop="id" label="ID" width="64" />
        <el-table-column prop="username" label="User" width="130" show-overflow-tooltip />
        <el-table-column label="Organization" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.organName || (row.organId === 0 ? 'Platform' : `#${row.organId}`) }}
          </template>
        </el-table-column>
        <el-table-column label="Type" width="84">
          <template #default="{ row }">
            <el-tag :type="row.category === 1 ? 'danger' : 'success'" size="small">
              {{ row.category === 1 ? 'Issue' : 'Suggestion' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="Feedback" min-width="260" show-overflow-tooltip />
        <el-table-column label="Screenshots" width="100">
          <template #default="{ row }">
            <el-button
              v-if="row.images && row.images.length"
              link
              type="primary"
              @click="openPreview(row)"
            >
              {{ row.images.length }}
            </el-button>
            <span v-else class="fb-admin__muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="Status" width="92">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'info' : 'warning'" size="small">
              {{ row.status === 1 ? 'Handled' : 'Pending' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reply" label="Reply" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.reply">{{ row.reply }}</span>
            <span v-else class="fb-admin__muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="Submitted At" width="170">
          <template #default="{ row }">{{ formatTime(row.createdTime) }}</template>
        </el-table-column>
        <el-table-column label="Actions" width="90" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              link
              type="primary"
              @click="openHandle(row)"
            >
              Handle
            </el-button>
            <span v-else class="fb-admin__muted">{{ row.handledBy }}</span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        class="fb-admin__pager"
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-sizes="[10, 20, 50]"
        @current-change="load"
        @size-change="reload"
      />
    </el-card>

    <el-image-viewer
      v-if="viewerVisible"
      :url-list="viewerUrls"
      :initial-index="viewerIndex"
      @close="closePreview"
    />

    <el-dialog v-model="handleVisible" title="Handle Feedback" width="520px" :close-on-click-modal="false">
      <div class="fb-admin__handle-content">
        <div class="fb-admin__handle-meta">
          <el-tag :type="current?.category === 1 ? 'danger' : 'success'" size="small">
            {{ current?.category === 1 ? 'Issue' : 'Suggestion' }}
          </el-tag>
          <span class="fb-admin__muted">{{ current?.username }}</span>
          <span class="fb-admin__muted">{{ formatTime(current?.createdTime) }}</span>
        </div>
        <div class="fb-admin__handle-text">{{ current?.content }}</div>
        <el-input
          v-model="reply"
          type="textarea"
          :rows="4"
          maxlength="1000"
          show-word-limit
          placeholder="Reply (optional, archived with the status)…"
        />
      </div>
      <template #footer>
        <el-button @click="handleVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="handling" @click="confirmHandle">Mark as Handled</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchFeedbackImage, handleFeedback, pageFeedback, type FeedbackRecord } from '@/api/feedback'

const records = ref<FeedbackRecord[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
const status = ref<number>(-1)
const keyword = ref('')
const loading = ref(false)

const viewerVisible = ref(false)
const viewerUrls = ref<string[]>([])
const viewerIndex = ref(0)
let previewBlobUrls: string[] = []

const handleVisible = ref(false)
const handling = ref(false)
const current = ref<FeedbackRecord | null>(null)
const reply = ref('')

function formatTime(t?: string | null): string {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 19)
}

function reload() {
  pageNo.value = 1
  load()
}

function load() {
  loading.value = true
  pageFeedback({
    status: status.value === -1 ? undefined : status.value,
    keyword: keyword.value.trim() || undefined,
    pageNo: pageNo.value,
    pageSize: pageSize.value
  })
    .then((page) => {
      records.value = page.records || []
      total.value = page.total
    })
    .finally(() => {
      loading.value = false
    })
}

function openPreview(row: FeedbackRecord) {
  const names = row.images || []
  viewerUrls.value = []
  viewerIndex.value = 0
  viewerVisible.value = true
  Promise.all(names.map((n) => fetchFeedbackImage(n)))
    .then((blobs) => {
      previewBlobUrls.forEach((u) => URL.revokeObjectURL(u))
      previewBlobUrls = blobs.map((b) => URL.createObjectURL(b))
      viewerUrls.value = previewBlobUrls
    })
    .catch(() => {
      ElMessage.error('Failed to load screenshots')
      viewerVisible.value = false
    })
}

function closePreview() {
  viewerVisible.value = false
  previewBlobUrls.forEach((u) => URL.revokeObjectURL(u))
  previewBlobUrls = []
  viewerUrls.value = []
}

function openHandle(row: FeedbackRecord) {
  current.value = row
  reply.value = ''
  handleVisible.value = true
}

function confirmHandle() {
  if (!current.value) return
  handling.value = true
  handleFeedback(current.value.id, reply.value.trim())
    .then(() => {
      ElMessage.success('Marked as handled')
      handleVisible.value = false
      load()
    })
    .finally(() => {
      handling.value = false
    })
}

onMounted(load)
</script>

<style scoped lang="scss">
.fb-admin {
  padding: var(--sp-4);

  &__filter {
    margin-bottom: var(--sp-4);

    &-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--sp-4);
      flex-wrap: wrap;
    }
  }

  &__search {
    width: 280px;
  }

  &__muted {
    color: var(--c-text-tertiary);
    font-size: var(--fs-xs);
  }

  &__pager {
    margin-top: var(--sp-4);
    justify-content: flex-end;
  }

  &__handle-content {
    display: flex;
    flex-direction: column;
    gap: var(--sp-3);
  }

  &__handle-meta {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
  }

  &__handle-text {
    padding: var(--sp-3);
    border-radius: var(--r-sm);
    background: var(--c-fill-quaternary);
    color: var(--c-text);
    font-size: var(--fs-sm);
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-word;
  }
}
</style>
