<template>
  <el-dialog
    :model-value="modelValue"
    title="Feedback"
    width="560px"
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
    @closed="reset"
  >
    <div class="fb-hint">Have an issue or an improvement suggestion? Tell us and we will follow up soon.</div>

    <el-form label-width="72px" @submit.prevent>
      <div class="fb-category">
        <span class="fb-category__label">Type</span>
        <el-radio-group v-model="category">
          <el-radio :value="1">Issue</el-radio>
          <el-radio :value="2">Suggestion</el-radio>
        </el-radio-group>
      </div>
      <el-form-item label="Content" required>
        <el-input
          v-model="content"
          type="textarea"
          :rows="5"
          maxlength="2000"
          show-word-limit
          placeholder="Describe the issue you hit (steps and page path help) or your suggestion…"
        />
      </el-form-item>
      <el-form-item label="Screenshots">
        <div class="fb-upload">
          <el-upload
            v-model:file-list="fileList"
            list-type="picture-card"
            accept="image/*"
            :limit="4"
            :http-request="doUpload"
            :before-upload="beforeUpload"
            :on-remove="onRemove"
            :on-exceed="onExceed"
            :on-preview="onPreview"
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
          <div class="fb-upload__tip">Up to 4 images, each ≤ 5MB (jpg/png/gif/webp/bmp)</div>
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="close">Cancel</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">Submit Feedback</el-button>
    </template>
  </el-dialog>

  <el-image-viewer
    v-if="viewerVisible"
    :url-list="viewerUrls"
    :initial-index="viewerIndex"
    @close="viewerVisible = false"
  />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadRawFile, UploadRequestOptions } from 'element-plus'
import { submitFeedback, uploadFeedbackImage } from '@/api/feedback'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

const category = ref<number>(1)
const content = ref('')
const submitting = ref(false)

const uploadedNames = ref<string[]>([])
const fileList = ref<UploadFile[]>([])

const viewerVisible = ref(false)
const viewerUrls = ref<string[]>([])
const viewerIndex = ref(0)

function beforeUpload(file: UploadRawFile): boolean {
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('Each image must be ≤ 5MB')
    return false
  }
  if (!/^image\/(jpeg|png|gif|webp|bmp)$/.test(file.type)) {
    ElMessage.warning('Only image formats are supported: jpg / png / gif / webp / bmp')
    return false
  }
  return true
}

async function doUpload(options: UploadRequestOptions) {
  try {
    const name = await uploadFeedbackImage(options.file as File)
    uploadedNames.value.push(name)
    options.onSuccess(name)
  } catch (e) {
    options.onError(
      Object.assign(new Error('Screenshot upload failed'), { status: 0, method: 'POST', url: '' })
    )
  }
}

function onRemove(file: UploadFile) {
  const name = file.response as string
  if (name) {
    uploadedNames.value = uploadedNames.value.filter((n) => n !== name)
  }
}

function onExceed() {
  ElMessage.warning('Up to 4 screenshots')
}

function onPreview(file: UploadFile) {
  const url = file.url
  if (!url) return
  viewerUrls.value = fileList.value.map((f) => f.url).filter(Boolean) as string[]
  viewerIndex.value = Math.max(0, fileList.value.findIndex((f) => f.url === url))
  viewerVisible.value = true
}

function submit() {
  const text = content.value.trim()
  if (!text) {
    ElMessage.warning('Please enter feedback content')
    return
  }
  submitting.value = true
  submitFeedback({ category: category.value, content: text, images: uploadedNames.value })
    .then(() => {
      ElMessage.success('Feedback submitted. Thank you!')
      close()
    })
    .finally(() => {
      submitting.value = false
    })
}

function close() {
  emit('update:modelValue', false)
}

function reset() {
  category.value = 1
  content.value = ''
  uploadedNames.value = []
  fileList.value = []
  viewerVisible.value = false
}

void props
</script>

<style scoped lang="scss">
.fb-hint {
  margin-bottom: var(--sp-4);
  padding: 8px 12px;
  border-radius: var(--r-sm);
  background: var(--c-fill-quaternary);
  color: var(--c-text-secondary);
  font-size: var(--fs-sm);
}

.fb-category {
  display: flex;
  align-items: center;
  margin-bottom: 18px;

  &__label {
    flex: 0 0 72px;
    text-align: right;
    padding-right: 12px;
    font-size: 14px;
    line-height: 32px;
    color: var(--el-text-color-regular);

    &::before {
      content: '*';
      color: var(--el-color-danger);
      margin-right: 4px;
    }
  }
}

.fb-upload {
  width: 100%;

  &__tip {
    margin-top: 4px;
    color: var(--c-text-tertiary);
    font-size: var(--fs-xs);
  }
}
</style>
