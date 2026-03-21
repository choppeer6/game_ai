<template>
  <el-card>
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <span>数据接入（CSV/JSON）</span>
        <el-upload :show-file-list="false" :http-request="customUpload">
          <el-button type="primary">上传文件</el-button>
        </el-upload>
      </div>
    </template>
    <p style="color: var(--muted); margin-top: 0">
      CSV 首行表头：<code>ts,agent_id,action,value_score</code>；JSON 支持对象或数组，字段 ts / agent_id / action / value_score。
    </p>
    <el-table :data="table.records">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="rowCount" label="行数" width="100" />
      <el-table-column prop="errorMessage" label="错误" show-overflow-tooltip />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link type="primary" @click="view(row)">查看样本</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page" layout="prev, pager, next" :total="table.total" :page-size="size" @current-change="load" />
  </el-card>

  <el-dialog v-model="dlg" title="样本行（前 50 条）" width="900px">
    <el-table :data="rows" size="small">
      <el-table-column prop="ts" label="ts" width="120" />
      <el-table-column prop="agentId" label="agent" width="120" />
      <el-table-column prop="action" label="action" />
      <el-table-column prop="valueScore" label="value" width="120" />
    </el-table>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'

const table = reactive<any>({ records: [], total: 0 })
const page = ref(1)
const size = 10
const dlg = ref(false)
const rows = ref<any[]>([])

async function load() {
  const res: any = await http.get('/datasets', { params: { current: page.value, size } })
  table.records = res.records
  table.total = res.total
}

async function customUpload(opt: any) {
  const fd = new FormData()
  fd.append('file', opt.file)
  try {
    await http.post('/datasets/upload', fd)
    ElMessage.success('上传完成')
    await load()
  } catch (e: any) {
    ElMessage.error(e.message || '上传失败')
  }
}

async function view(row: any) {
  const res: any = await http.get(`/datasets/${row.id}/rows`, { params: { limit: 50 } })
  rows.value = res || []
  dlg.value = true
}

onMounted(load)
</script>
