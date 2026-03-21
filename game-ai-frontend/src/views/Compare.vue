<template>
  <el-card>
    <template #header>多任务性能对比</template>
    <el-select v-model="selected" multiple filterable placeholder="选择任务 ID" style="width: 100%">
      <el-option v-for="id in taskIds" :key="id" :label="id" :value="id" />
    </el-select>
    <div style="margin-top: 12px">
      <el-button type="primary" @click="loadCompare">加载对比数据</el-button>
      <el-button @click="exportHtml">导出 HTML 报告</el-button>
    </div>
    <div ref="chartEl" style="height: 420px; margin-top: 16px"></div>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import http from '../api/http'

const taskIds = ref<string[]>([])
const selected = ref<string[]>([])
const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

async function refreshTaskIds() {
  const res: any = await http.get('/training/tasks', { params: { current: 1, size: 200 } })
  taskIds.value = (res.records || []).map((r: any) => r.taskId)
}

async function loadCompare() {
  if (!selected.value.length) return
  const data: any = await http.post('/compare', selected.value)
  const seriesMap = data.series as Record<string, any[]>
  const legend: string[] = []
  const series: any[] = []
  for (const tid of Object.keys(seriesMap)) {
    legend.push(tid)
    const pts = (seriesMap[tid] || []).map((x: any) => [x.epoch, x.redWinRate ?? 0])
    series.push({ name: tid, type: 'line', smooth: true, data: pts })
  }
  if (!chart && chartEl.value) {
    chart = echarts.init(chartEl.value)
  }
  chart?.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    legend: { data: legend, textStyle: { color: '#c9d1d9' } },
    xAxis: { type: 'value', name: 'epoch', axisLabel: { color: '#8b949e' } },
    yAxis: { type: 'value', axisLabel: { color: '#8b949e' }, splitLine: { lineStyle: { color: '#2d3a4f' } } },
    series,
  })
}

async function exportHtml() {
  if (!selected.value.length) return
  const html = (await http.get('/compare/export', {
    params: { taskIds: selected.value },
  })) as string
  const blob = new Blob([html], { type: 'text/html;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'report.html'
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(async () => {
  await refreshTaskIds()
})

watch(
  () => chartEl.value,
  (el) => {
    if (el && !chart) {
      chart = echarts.init(el)
    }
  }
)
</script>
