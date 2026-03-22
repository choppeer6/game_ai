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
            <el-input v-model="create.hyperParameters" type="textarea" :rows="7" />
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="create.startImmediately">立即启动</el-checkbox>
          </el-form-item>
          <el-button type="primary" @click="submitCreate">创建</el-button>
        </el-form>
      </el-card>

      <el-card style="margin-top: 16px">
        <template #header>
          <div style="display:flex;justify-content:space-between;align-items:center">
            <span>任务列表</span>
            <el-tag :type="pollingActive ? 'success' : 'info'" size="small">
              {{ pollingActive ? '自动刷新中' : '静止' }}
            </el-tag>
          </div>
        </template>
        <el-table :data="tasks.records" size="small" highlight-current-row
                  :row-class-name="rowClass" @row-click="onRow">
          <el-table-column prop="taskId" label="任务ID" width="200" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="90" :formatter="fmtStatus" />
          <el-table-column prop="currentEpoch" label="Epoch" width="70" />
          <el-table-column v-if="canMutate" label="操作" min-width="170">
            <template #default="{ row }">
              <el-button link size="small" @click.stop="ctrl(row, 'pause')">暂停</el-button>
              <el-button link size="small" @click.stop="ctrl(row, 'resume')">恢复</el-button>
              <el-button link size="small" type="danger" @click.stop="ctrl(row, 'stop')">终止</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-model:current-page="taskPage" small layout="prev, pager, next"
                       :total="tasks.total" :page-size="10" @current-change="loadTasks" />
      </el-card>
    </el-col>

    <el-col :span="14">
      <el-card>
        <template #header>
          <div style="display:flex;justify-content:space-between;align-items:center">
            <span>实时监控</span>
            <div style="display:flex;gap:8px;align-items:center">
              <el-tag v-if="activeTaskId" type="success" size="small" style="max-width:160px;overflow:hidden;text-overflow:ellipsis">
                {{ activeTaskId }}
              </el-tag>
              <el-tag :type="wsStatus === 'open' ? 'success' : wsStatus === 'connecting' ? 'warning' : 'danger'" size="small">
                {{ wsStatus === 'open' ? 'WS 已连接' : wsStatus === 'connecting' ? '连接中…' : 'WS 断开' }}
              </el-tag>
            </div>
          </div>
        </template>
        <div ref="chartEl" style="height:360px;width:100%"></div>
        <p style="color:var(--muted);font-size:12px;margin-top:8px">点击左侧任务行订阅实时指标，断线自动重连。</p>
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
      learning_rate: 0.001,
      discount_factor: 0.99,
      max_epochs: 500,
      rollout_episodes: 20,
      ppo_epochs: 6,
      clip_coef: 0.2,
      ent_coef: 0.05,
    },
    null,
    2
  ),
  startImmediately: true,
})

const tasks = reactive<any>({ records: [], total: 0 })
const taskPage = ref(1)
const pollingActive = ref(false)
let pollTimer: ReturnType<typeof setInterval> | null = null

const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
const activeTaskId = ref('')
const wsStatus = ref<'connecting' | 'open' | 'closed'>('closed')

let ws: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let reconnectDelay = 1000
let destroyed = false

const epochs: number[] = []
const winRates: number[] = []
const losses: number[] = []
const rewards: number[] = []

function rowClass({ row }: any) {
  return row.taskId === activeTaskId.value ? 'active-row' : ''
}

function fmtStatus(row: any) {
  const m: Record<number, string> = { 0: '待启动', 1: '排队', 2: '训练中', 3: '暂停', 4: '已完成', 5: '异常', 6: '已终止' }
  return m[row.status] ?? String(row.status)
}

async function loadScenes() {
  const res: any = await http.get('/scenes', { params: { current: 1, size: 200 } })
  scenes.value = res.records || []
  if (!create.sceneId && scenes.value.length) create.sceneId = scenes.value[0].id
}

async function loadAlgos() {
  const res: any = await http.get('/algorithms', { params: { current: 1, size: 200 } })
  algos.value = res.records || []
}

async function loadTasks() {
  try {
    const res: any = await http.get('/training/tasks', { params: { current: taskPage.value, size: 10 } })
    tasks.records = res.records
    tasks.total = res.total
  } catch {
    // 静默忽略轮询中的网络错误
  }
}

function startPolling() {
  if (pollTimer) return
  pollingActive.value = true
  pollTimer = setInterval(loadTasks, 3000)
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  pollingActive.value = false
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
    startPolling()
    if (task?.taskId) subscribe(task.taskId)
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
  startPolling()
}

function ensureChart() {
  if (!chartEl.value || chart) return
  chart = echarts.init(chartEl.value)
  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    legend: { data: ['胜率', 'Loss', '累计奖励'], textStyle: { color: '#c9d1d9' } },
    xAxis: { type: 'category', data: [], axisLabel: { color: '#8b949e' } },
    yAxis: { type: 'value', axisLabel: { color: '#8b949e' }, splitLine: { lineStyle: { color: '#2d3a4f' } } },
    series: [
      { name: '胜率', type: 'line', data: [], smooth: true, showSymbol: false },
      { name: 'Loss', type: 'line', data: [], smooth: true, showSymbol: false },
      { name: '累计奖励', type: 'line', data: [], smooth: true, showSymbol: false },
    ],
  })
}

function pushPoint(p: any) {
  epochs.push(p.epoch)
  winRates.push(+(p.red_win_rate ?? 0).toFixed(4))
  losses.push(+(p.loss_value ?? 0).toFixed(4))
  rewards.push(+(p.cumulative_reward ?? 0).toFixed(4))
  if (epochs.length > 500) {
    epochs.shift(); winRates.shift(); losses.shift(); rewards.shift()
  }
  // false = merge 模式，避免重绘整个 option，刷新更快
  chart?.setOption({
    xAxis: { data: epochs.slice() },
    series: [{ data: winRates.slice() }, { data: losses.slice() }, { data: rewards.slice() }],
  }, false)
}

function sendSubscribe(taskId: string) {
  ws?.send(JSON.stringify({ action: 'subscribe', taskId }))
}

function connectWs() {
  if (destroyed) return
  wsStatus.value = 'connecting'
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  ws = new WebSocket(`${proto}://${location.host}/ws/train`)

  ws.onopen = () => {
    wsStatus.value = 'open'
    reconnectDelay = 1000
    if (activeTaskId.value) sendSubscribe(activeTaskId.value)
  }

  ws.onmessage = (ev) => {
    try {
      const p = JSON.parse(ev.data)
      if (p.task_id && p.epoch !== undefined) pushPoint(p)
    } catch { /* ignore */ }
  }

  ws.onclose = () => {
    wsStatus.value = 'closed'
    ws = null
    if (!destroyed) {
      reconnectTimer = setTimeout(() => {
        reconnectDelay = Math.min(reconnectDelay * 2, 30000)
        connectWs()
      }, reconnectDelay)
    }
  }

  ws.onerror = () => { ws?.close() }
}

function subscribe(taskId: string) {
  activeTaskId.value = taskId
  epochs.length = 0; winRates.length = 0; losses.length = 0; rewards.length = 0
  ensureChart()
  chart?.setOption({ xAxis: { data: [] }, series: [{ data: [] }, { data: [] }, { data: [] }] })

  const state = ws?.readyState
  if (!ws || state === WebSocket.CLOSED || state === WebSocket.CLOSING) {
    connectWs()
  } else if (state === WebSocket.OPEN) {
    sendSubscribe(taskId)
  }
  // CONNECTING 状态：onopen 触发时会读取 activeTaskId.value 并发送
}

function onResize() { chart?.resize() }

onMounted(async () => {
  await Promise.all([loadScenes(), loadAlgos(), loadTasks()])
  await nextTick()
  ensureChart()
  connectWs()
  window.addEventListener('resize', onResize)
  const hasActive = tasks.records.some((r: any) => r.status === 1 || r.status === 2)
  if (hasActive) startPolling()
})

onBeforeUnmount(() => {
  destroyed = true
  stopPolling()
  if (reconnectTimer) clearTimeout(reconnectTimer)
  ws?.close()
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
:deep(.active-row td) {
  background-color: rgba(64, 158, 255, 0.12) !important;
}
</style>
