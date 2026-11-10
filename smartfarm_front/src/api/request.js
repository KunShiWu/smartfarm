import axios from 'axios'

// axios 实例:后端就绪后,通过环境变量 VITE_API_BASE_URL 指向后端地址
// 或在 vite.config.js 中开启 /api 代理
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000
})

// 请求拦截器:可在此注入 token 等
request.interceptors.request.use(
  (config) => config,
  (err) => Promise.reject(err)
)

// 响应拦截器:统一返回 data
request.interceptors.response.use(
  (res) => res.data,
  (err) => Promise.reject(err)
)

export default request
