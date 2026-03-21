<template>
  <el-container style="height: 100vh">
    <el-aside width="220px" style="background: #141c26; border-right: 1px solid #2d3a4f">
      <div style="padding: 16px; font-weight: 700; color: var(--accent)">博弈智能体训练</div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="#141c26"
        text-color="#c9d1d9"
        active-text-color="#00d4aa"
      >
        <el-menu-item index="/dashboard">总览</el-menu-item>
        <el-menu-item index="/scenes">场景配置</el-menu-item>
        <el-menu-item v-if="auth.role !== 'COMMANDER'" index="/datasets">数据接入</el-menu-item>
        <el-menu-item v-if="auth.role !== 'COMMANDER'" index="/algorithms">算法模板</el-menu-item>
        <el-menu-item index="/training">智能体训练</el-menu-item>
        <el-menu-item index="/compare">性能对比</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header
        style="display: flex; align-items: center; justify-content: space-between; background: #1a2332; border-bottom: 1px solid #2d3a4f"
      >
        <span style="color: var(--muted)">{{ title }}</span>
        <div>
          <span style="margin-right: 12px; color: var(--muted)">{{ auth.username }} ({{ auth.role }})</span>
          <el-button size="small" @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main style="background: var(--bg)">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const title = computed(() => route.name?.toString() || '')

function logout() {
  auth.logout()
  router.push('/login')
}
</script>
