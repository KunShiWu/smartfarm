import request from './request'
import * as mock from './mock'

// 默认使用 mock 数据;后端就绪后,在 .env 中设置 VITE_USE_MOCK=false
// 并配置 VITE_API_BASE_URL 或 vite 代理即可切换到真实接口
const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

// 获取天气信息
export const getWeather = () =>
  USE_MOCK ? mock.getWeather() : request.get('/weather')

// 获取主机列表
export const getHostList = () =>
  USE_MOCK ? mock.getHostList() : request.get('/hosts')

// 获取主机实时参数
export const getRealtimeParams = (hostId) =>
  USE_MOCK ? mock.getRealtimeParams(hostId) : request.get(`/hosts/${hostId}/realtime`)

// 获取实时曲线数据
export const getRealtimeChart = (hostId) =>
  USE_MOCK ? mock.getRealtimeChart(hostId) : request.get(`/hosts/${hostId}/realtime/chart`)

// 获取历史数据（分页表格）params: { startTime, endTime, page, perPage }
export const getHistory = (hostId, params) =>
  USE_MOCK ? mock.getHistory(hostId, params) : request.get(`/hosts/${hostId}/history`, { params })

// 获取历史曲线数据 params: { startTime, endTime, points }
export const getHistoryChart = (hostId, params) =>
  USE_MOCK ? mock.getHistoryChart(hostId, params) : request.get(`/hosts/${hostId}/history/chart`, { params })

// 修改采集间隔
export const updateInterval = (hostId, interval) =>
  USE_MOCK ? mock.updateInterval(hostId, interval) : request.put(`/hosts/${hostId}/interval`, { interval })

// 获取修改记录
export const getModifyRecords = (hostId) =>
  USE_MOCK ? mock.getModifyRecords(hostId) : request.get(`/hosts/${hostId}/modify-records`)

// 保存主机设置
export const updateHostSettings = (hostId, payload) =>
  USE_MOCK ? mock.updateHostSettings(hostId, payload) : request.put(`/hosts/${hostId}/settings`, payload)

// 获取视频直播地址(http-flv);refresh=true 强制生成新地址
export const getVideoLive = (refresh) =>
  USE_MOCK
    ? mock.getVideoLive()
    : request.get('/video/live', { params: refresh ? { refresh: true } : {} })

// 农田分析:结合摄像头截图与土壤数据,由大模型给出农情诊断与农事建议
// 模型链路较慢(视觉识别 + 推理),单独放大超时
export const farmAnalyze = (payload) =>
  USE_MOCK
    ? mock.farmAnalyze(payload)
    : request.post('/farm/analyze', payload, { timeout: 180000 })

// 喷灌设备状态(开关、阈值、模式、最近土壤湿度、剩余喷灌时长)
export const getIrrigation = () =>
  USE_MOCK ? mock.getIrrigation() : request.get('/irrigation')

// 手动开关喷灌水泵
export const switchIrrigation = (on) =>
  USE_MOCK ? mock.switchIrrigation(on) : request.post('/irrigation/switch', { on })

// 修改喷灌设置:阈值(%)、单次时长(分钟)、控制模式(auto/manual)
export const updateIrrigationConfig = (payload) =>
  USE_MOCK ? mock.updateIrrigationConfig(payload) : request.put('/irrigation/config', payload)

// AI 智能体聊天：始终通过真实 Spring Boot /api/agent/chat 调用，不使用 mock。
// 大模型 + MCP 可能需要较长响应时间，单独延长超时。
export const chatWithAgent = (payload) =>
  request.post('/agent/chat', payload, { timeout: 300000 })


// 农作物病害图片上传：始终调用真实的 Spring Boot POST /api/farm/images。
// 不要手动设置 multipart/form-data 的 Content-Type，由浏览器生成 boundary。
export const uploadCropImage = (file) => {
  const form = new FormData()
  form.append('file', file)
  return request.post('/farm/images', form, { timeout: 60000 })
}
