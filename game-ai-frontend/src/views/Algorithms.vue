<template>
  <el-card>
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <span>算法配置模板</span>
        <el-button v-if="canMutate" type="primary" @click="openEdit()">新建模板</el-button>
      </div>
    </template>
    <el-table :data="table.records">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="templateName" label="模板名" />
      <el-table-column prop="algoName" label="算法" width="120" />
      <el-table-column prop="hyperParameters" label="超参数 JSON" show-overflow-tooltip />
      <el-table-column v-if="canMutate" label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page" layout="prev, pager, next" :total="table.total" :page-size="size" @current-change="load" />
  </el-card>

  <el-dialog v-model="visible" :title="form.id ? '编辑模板' : '新建模板'" width="640px">
    <el-form label-width="120px">
      <el-form-item label="模板名称">
        <el-input v-model="form.templateName" />
      </el-form-item>
      <el-form-item label="算法">
        <el-select v-model="form.algoName" placeholder="选择算法">
          <el-option label="MAPPO" value="MAPPO" />
          <el-option label="MADDPG" value="MADDPG" />
          <el-option label="IPPO" value="IPPO" />
        </el-select>
        <el-tooltip content="学习率、折扣因子、探索衰减、训练轮次等" placement="right">
          <span style="margin-left: 8px; color: var(--muted)">?</span>
        </el-tooltip>
      </el-form-item>
      <el-form-item label="超参数 JSON">
        <el-input v-model="form.hyperParameters" type="textarea" :rows="8" :placeholder="defaultHyper" />
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

const defaultHyper = `{
  "learning_rate": 0.0003,
  "discount_factor": 0.99,
  "max_epochs": 20,
  "rollout_episodes": 4,
  "ppo_epochs": 2,
  "clip_coef": 0.2
}`

const table = reactive<any>({ records: [], total: 0 })
const page = ref(1)
const size = 10
const visible = ref(false)
const form = reactive<any>({
  id: null,
  templateName: '',
  algoName: 'MAPPO',
  hyperParameters: defaultHyper,
})

async function load() {
  const res: any = await http.get('/algorithms', { params: { current: page.value, size } })
  table.records = res.records
  table.total = res.total
}

function openEdit(row?: any) {
  if (row) {
    Object.assign(form, row)
  } else {
    form.id = null
    form.templateName = ''
    form.algoName = 'MAPPO'
    form.hyperParameters = defaultHyper
  }
  visible.value = true
}

async function save() {
  await http.post('/algorithms', { ...form })
  ElMessage.success('已保存')
  visible.value = false
  await load()
}

async function remove(row: any) {
  await ElMessageBox.confirm('确认删除？')
  await http.delete(`/algorithms/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>
