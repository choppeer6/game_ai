<template>
  <el-card>
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <span>博弈对抗场景配置</span>
        <el-button v-if="canMutate" type="primary" @click="openEdit()">新建场景</el-button>
      </div>
    </template>
    <el-table :data="table.records" style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="sceneName" label="名称" />
      <el-table-column prop="mapWidth" label="宽" width="80" />
      <el-table-column prop="mapHeight" label="高" width="80" />
      <el-table-column prop="redDroneCount" label="红方" width="80" />
      <el-table-column prop="blueDroneCount" label="蓝方" width="80" />
      <el-table-column v-if="canMutate" label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="page"
      layout="prev, pager, next"
      :total="table.total"
      :page-size="size"
      @current-change="load"
    />
  </el-card>

  <el-dialog v-model="visible" :title="form.id ? '编辑场景' : '新建场景'" width="600px">
    <el-form label-width="120px">
      <el-form-item label="场景名称" required>
        <el-input v-model="form.sceneName" placeholder="如：无人机集群对抗-演示" />
      </el-form-item>
      <el-form-item label="地图宽高">
        <el-input-number v-model="form.mapWidth" :min="5" :max="500" />
        <span style="margin: 0 8px">×</span>
        <el-input-number v-model="form.mapHeight" :min="5" :max="500" />
      </el-form-item>
      <el-form-item label="红方数量">
        <el-input-number v-model="form.redDroneCount" :min="1" :max="50" />
        <el-tooltip content="无人机集群规模" placement="top">
          <span style="margin-left: 8px; color: var(--muted)">?</span>
        </el-tooltip>
      </el-form-item>
      <el-form-item label="蓝方数量">
        <el-input-number v-model="form.blueDroneCount" :min="1" :max="50" />
      </el-form-item>
      <el-form-item label="扩展 JSON">
        <el-input
          v-model="form.sceneJson"
          type="textarea"
          :rows="6"
          placeholder='{"obstacles":[[1,1],[2,2]],"win_condition":{"type":"eliminate","max_steps":200},"params":{"speed":1}}'
        />
        <el-tooltip content="obstacles 为 [行,列] 障碍坐标；max_steps 为单局最大步数" placement="top">
          <span style="color: var(--muted); font-size: 12px">字段说明</span>
        </el-tooltip>
      </el-form-item>
      <el-form-item label="二维预览">
        <div class="grid-preview" :style="{ gridTemplateColumns: `repeat(${previewW}, 14px)` }">
          <div
            v-for="(c, i) in previewCells"
            :key="i"
            class="cell"
            :class="{ obs: c === 1 }"
            :title="String(i)"
          />
        </div>
        <span style="font-size: 12px; color: var(--muted)">黑格为障碍（来自 JSON obstacles）</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button v-if="canMutate" type="primary" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const canMutate = computed(() => auth.role === 'ADMIN' || auth.role === 'ENGINEER')

const table = reactive<any>({ records: [], total: 0 })
const page = ref(1)
const size = 10
const visible = ref(false)
const form = reactive<any>({
  id: null,
  sceneName: '',
  mapWidth: 32,
  mapHeight: 32,
  redDroneCount: 3,
  blueDroneCount: 3,
  sceneJson: '',
})

const previewW = computed(() => Math.min(24, Number(form.mapWidth) || 12))
const previewH = computed(() => Math.min(24, Number(form.mapHeight) || 12))

const previewCells = computed(() => {
  const w = previewW.value
  const h = previewH.value
  let obs: number[][] = []
  try {
    const j = form.sceneJson ? JSON.parse(form.sceneJson) : {}
    const ex = j.obstacles || j.extra?.obstacles
    if (Array.isArray(ex)) {
      obs = ex.filter((x: any) => Array.isArray(x) && x.length >= 2).map((x: any) => [Number(x[0]), Number(x[1])])
    }
  } catch {
    obs = []
  }
  const set = new Set(obs.map(([r, c]) => `${r},${c}`))
  const cells: number[] = []
  for (let r = 0; r < h; r++) {
    for (let c = 0; c < w; c++) {
      cells.push(set.has(`${r},${c}`) ? 1 : 0)
    }
  }
  return cells
})

async function load() {
  const res: any = await http.get('/scenes', { params: { current: page.value, size } })
  table.records = res.records
  table.total = res.total
}

function openEdit(row?: any) {
  if (row) {
    Object.assign(form, row)
  } else {
    form.id = null
    form.sceneName = ''
    form.mapWidth = 32
    form.mapHeight = 32
    form.redDroneCount = 3
    form.blueDroneCount = 3
    form.sceneJson = ''
  }
  visible.value = true
}

async function save() {
  await http.post('/scenes', { ...form })
  ElMessage.success('已保存')
  visible.value = false
  await load()
}

async function remove(row: any) {
  await ElMessageBox.confirm('确认删除？')
  await http.delete(`/scenes/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.grid-preview {
  display: grid;
  gap: 1px;
  background: #2d3a4f;
  padding: 4px;
  border-radius: 4px;
  width: fit-content;
}
.cell {
  width: 14px;
  height: 14px;
  background: #1a2332;
}
.cell.obs {
  background: #4a5568;
}
</style>
