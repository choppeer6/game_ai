import axios from 'axios'
import { useAuthStore } from '../stores/auth'

const http = axios.create({
  baseURL: '/api',
  timeout: 120000,
})

http.interceptors.request.use((cfg) => {
  const auth = useAuthStore()
  if (auth.token) {
    cfg.headers = cfg.headers || {}
    cfg.headers.Authorization = `Bearer ${auth.token}`
  }
  return cfg
})

http.interceptors.response.use(
  (res) => {
    if (res.config.responseType === 'blob') {
      return res.data
    }
    const body = res.data as { code?: number; message?: string; data?: unknown }
    if (body == null) {
      return body
    }
    if (typeof body === 'string') {
      return body
    }
    if (typeof body.code === 'number' && body.code !== 0) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body.data
  },
  (err) => Promise.reject(err)
)

export default http
