<template>
  <el-row :gutter="16">
    <el-col :span="10">
      <el-card v-if="canMutate">
        <template #header>创建训练任务</template>
        <el-form label-width="110px">
          <el-form-item label="场景" required>
            <el-select v-model="create.sceneId" placeholder="选择场景" filterable style="width: 100%">
              <el-option v-for="s in scenes" :key="s.id" :label="`${s.id} - ${s.sceneName}`" :value="s.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="算法模板">
            <el-select v-model="create.algoTemplateId" clearable placeholder="可选" style="width: 100%">
              <el-option v-for="a in algos" :key="a.id" :label="`${a.id} - ${a.templateName}`" :value="a.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="或直接算法">
            <el-input v-model="create.algoName" placeholder="MAPPO" />
          </el-form-item>
          <el-form-item label="超参数 JSON">
            <el-input v-model="create.hyperParameters" type="textarea" :rows="6" />
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="create.startImmediately">立即启动</el-checkbox>
          </el-form-item>
          <el-button type="primary" @click="submitCreate">创建</el-button>
        </el-form>
      </el-card>

      <el-card style="margin-top: 16px">
        <template #header>任务列表</template>
        <el-table :data="tasks.records" size="small" @row-click="onRow">
          <el-table-column prop="taskId" label="任务ID" width="200" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="90" :formatter="fmtStatus" />
          <el-table-column prop="currentEpoch" label="Epoch" width="80" />
          <el-table-column v-if="canMutate" label="操作" width="220">
            <template #default="{ row }">
              <el-button link size="small" @click.stop="ctrl(row, 'pause')">暂停</el-button>
              <el-button link size="small" @click.stop="ctrl(row, 'resume')">恢复</el-button>
              <el-button link size="small" type="danger" @click.stop="ctrl(row, 'stop')">终止</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-model:current-page="taskPage" small layout="prev, pager, next" :total="tasks.total" :page-size="10" @current-change="loadTasks" />
      </el-card>
    </el-col>

    <el-col :span="14">
      <el-card>
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <span>实时监控（WebSocket + ECharts）</span>
            <el-tag v-if="activeTaskId" type="success">订阅 {{ activeTaskId }}</el-tag>
          </div>
        </template>
        <div ref="chartEl" style="height: 360px; width: 100%"></div>
        <p style="color: var(--muted); font-size: 12px">点击左侧任务行以订阅指标推送（≤2s 延迟目标依赖本机网络）。</p>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const canMutate = computed(() => auth.role === 'ADMIN' || auth.role === 'ENGINEER')

const scenes = ref<any[]>([])
const algos = ref<any[]>([])
const create = reactive({
  sceneId: null as any,
  algoTemplateId: null as any,
  algoName: 'MAPPO',
  hyperParameters: JSON.stringify(
    {
      learning_rate: 0.0003,
      discount_factor: 0.99,
      max_epochs: 20,
      rollout_episodes: 4,
      ppo_epochs: 2,
      clip_coef: 0.2,
    },
    null,
    2
  ),
  startImmediately: true,
})

const tasks = reactive<any>({ records: [], total: 0 })
const taskPage = ref(1)

const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
const activeTaskId = ref('')
let ws: WebSocket | null = null

const epochs: number[] = []
const winRates: number[] = []
const losses: number[] = []
const rewards: number[] = []

function fmtStatus(row: any) {
  const m: any = { 0: '待启动', 1: '排队', 2: '训练中', 3: '暂停', 4: '已完成', 5: '异常', 6: '已终止' }
  return m[row.status] ?? row.status
}

async function loadScenes() {
  const res: any = await http.get('/scenes', { params: { current: 1, size: 200 } })
  scenes.value = res.records || []
  if (!create.sceneId && scenes.value.length) {
    create.sceneId = scenes.value[0].id
  }
}

async function loadAlgos() {
  const res: any = await http.get('/algorithms', { params: { current: 1, size: 200 } })
  algos.value = res.records || []
}

async function loadTasks() {
  const res: any = await http.get('/training/tasks', { params: { current: taskPage.value, size: 10 } })
  tasks.records = res.records
  tasks.total = res.total
}

async function submitCreate() {
  try {
    const payload = {
      sceneId: create.sceneId,
      algoTemplateId: create.algoTemplateId || null,
      algoName: create.algoName,
      hyperParameters: create.hyperParameters,
      startImmediately: create.startImmediately,
    }
    const task: any = await http.post('/training/tasks', payload)
    ElMessage.success('任务已创建')
    await loadTasks()
    if (task?.taskId) {
      subscribe(task.taskId)
    }
  } catch (e: any) {
    ElMessage.error(e.message || '创建失败')
  }
}

async function ctrl(row: any, action: string) {
  try {
    await http.post(`/training/tasks/${row.taskId}/${action}`)
    ElMessage.success('已发送指令')
    await loadTasks()
  } catch (e: any) {
    ElMessage.error(e.message || '失败')
  }
}

function onRow(row: any) {
  subscribe(row.taskId)
}

function ensureChart() {
  if (!chartEl.value) return
  if (!chart) {
    chart = echarts.init(chartEl.value)
    chart.setOption({
      backgroundColor: 'transparent',
      tooltip: { trigger: 'axis' },
      legend: { data: ['胜率', 'Loss', '累计奖励'], textStyle: { color: '#c9d1d9' } },
      xAxis: { type: 'category', data: epochs, axisLabel: { color: '#8b949e' } },
      yAxis: { type: 'value', axisLabel: { color: '#8b949e' }, splitLine: { lineStyle: { color: '#2d3a4f' } } },
      series: [
        { name: '胜率', type: 'line', data: winRates, smooth: true },
        { name: 'Loss', type: 'line', data: losses, smooth: true },
        { name: '累计奖励', type: 'line', data: rewards, smooth: true },
      ],
    })
  }
}

function pushPoint(p: any) {
  epochs.push(p.epoch)
  winRates.push(p.red_win_rate ?? 0)
  losses.push(p.loss_value ?? 0)
  rewards.push(p.cumulative_reward ?? 0)
  if (epochs.length > 500) {
    epochs.shift()
    winRates.shift()
    losses.shift()
    rewards.shift()
  }
  chart?.setOption({
    xAxis: { data: [...epochs] },
    series: [
      { data: [...winRates] },
      { data: [...losses] },
      { data: [...rewards] },
    ],
  })
}

function connectWs() {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  ws = new WebSocket(`${proto}://${location.host}/ws/train`)
  ws.onopen = () => {
    if (activeTaskId.value) {
      ws?.send(JSON.stringify({ action: 'subscribe', taskId: activeTaskId.value }))
    }
  }
  ws.onmessage = (ev) => {
    try {
      const p = JSON.parse(ev.data)
      if (p.task_id && p.epoch !== undefined) {
        pushPoint(p)
      }
    } catch {
      // ignore non-json
    }
  }
}

function subscribe(taskId: string) {
  activeTaskId.value = taskId
  epochs.length = 0
  winRates.length = 0
  losses.length = 0
  rewards.length = 0
  ensureChart()
  chart?.setOption({
    xAxis: { data: [] },
    series: [{ data: [] }, { data: [] }, { data: [] }],
  })
  if (!ws || ws.readyState !== WebSocket.OPEN) {
    connectWs()
  } else {
    ws.send(JSON.stringify({ action: 'subscribe', taskId }))
  }
}

onMounted(async () => {
  await Promise.all([loadScenes(), loadAlgos(), loadTasks()])
  await nextTick()
  ensureChart()
  connectWs()
})

onBeforeUnmount(() => {
  ws?.close()
  chart?.dispose()
})
</script>
