<template>
  <el-icon :size="size"><component :is="iconComponent" /></el-icon>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  VideoPlay, Document, Share, DataAnalysis, CircleClose, CircleCheck,
  PieChart, Medal, Promotion, Money, Histogram, Grid, SetUp, QuestionFilled
} from '@element-plus/icons-vue'

const props = withDefaults(defineProps<{ name: string; size?: number }>(), { size: 15 })

const ICONS: Record<string, any> = {
  VideoPlay,
  Document,
  Share,
  DataAnalysis,
  CircleClose,
  CircleCheck,
  PieChart,
  Medal,
  Promotion,
  Money,
  Histogram,
  Grid,
  SetUp,
  QuestionFilled
}

const iconComponent = computed(() => ICONS[props.name] || QuestionFilled)
</script>
