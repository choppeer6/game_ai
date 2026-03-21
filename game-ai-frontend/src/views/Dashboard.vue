<template>
  <el-row :gutter="16">
    <el-col :span="8">
      <el-card shadow="hover">
        <div class="kpi">场景</div>
        <div class="num">{{ stats.scenes }}</div>
      </el-card>
    </el-col>
    <el-col :span="8">
      <el-card shadow="hover">
        <div class="kpi">训练任务</div>
        <div class="num">{{ stats.tasks }}</div>
      </el-card>
    </el-col>
    <el-col :span="8">
      <el-card shadow="hover">
        <div class="kpi">数据集</div>
        <div class="num">{{ stats.datasets }}</div>
      </el-card>
    </el-col>
  </el-row>
  <el-card style="margin-top: 16px">
    <p>本系统为 B/S 架构：Vue 控制面 + Spring Boot 编排 + FastAPI 训练引擎 + Redis 指标通道。</p>
    <p style="color: var(--muted)">请依次启动 MySQL、Redis、Python 引擎、后端与前端。</p>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import http from '../api/http'

const stats = reactive({ scenes: 0, tasks: 0, datasets: 0 })

onMounted(async () => {
  try {
    const [scenes, tasks, datasets]: any[] = await Promise.all([
      http.get('/scenes', { params: { current: 1, size: 1 } }),
      http.get('/training/tasks', { params: { current: 1, size: 1 } }),
      http.get('/datasets', { params: { current: 1, size: 1 } }),
    ])
    stats.scenes = scenes?.total ?? 0
    stats.tasks = tasks?.total ?? 0
    stats.datasets = datasets?.total ?? 0
  } catch {
    // ignore
  }
})
</script>

<style scoped>
.kpi {
  color: var(--muted);
  font-size: 13px;
}
.num {
  font-size: 28px;
  font-weight: 700;
  color: var(--accent);
  margin-top: 8px;
}
</style>
