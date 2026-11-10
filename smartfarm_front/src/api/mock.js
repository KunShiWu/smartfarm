// Mock 数据层:后端接口未接入前,为前端页面提供数据
// 数据结构即前后端约定结构,后端按此返回即可无缝替换

const pad = (n) => String(n).padStart(2, '0')
const fmt = (d) =>
  `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
  `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`

// 参数元信息(8 参数)
const paramMeta = [
  { key: 'temp', name: '土壤温度', unit: '℃' },
  { key: 'humidity', name: '土壤湿度', unit: '%' },
  { key: 'ec', name: '土壤电导率', unit: 'uS/cm' },
  { key: 'salt', name: '土壤盐分', unit: 'mg/L' },
  { key: 'nitrogen', name: '土壤氮含量', unit: 'mg/kg' },
  { key: 'phosphorus', name: '土壤磷含量', unit: 'mg/kg' },
  { key: 'potassium', name: '土壤钾含量', unit: 'mg/kg' },
  { key: 'ph', name: '土壤pH值', unit: '' }
]

// 主机 store(模拟数据库)
const hosts = [
  {
    id: 'host-001',
    name: '4G低功耗土壤8参数检测仪',
    group: '土壤检测',
    online: false,
    battery: 3.7,
    interval: 1, // 采集间隔(分钟)
    collectTime: '2026-10-11 15:57:23',
    params: {
      temp: 27.2,
      humidity: 100.0,
      ec: 1031,
      salt: 689,
      nitrogen: 131,
      phosphorus: 34,
      potassium: 174,
      ph: 6.96
    }
  }
]

// 修改记录 store
const modifyRecords = [
  { time: '2026-10-11 15:57:23', field: '采集间隔', before: '5分钟', after: '1分钟', operator: 'admin' },
  { time: '2026-10-10 09:12:05', field: '主机名称', before: '土壤检测仪', after: '4G低功耗土壤8参数检测仪', operator: 'admin' }
]

// 曲线历史值(与 collectTime 对齐,向左每 7 分钟一个点)
const chartValues = {
  temp: [28, 29.5, 32.8, 40, 40, 24.7, 25, 25.2],
  humidity: [98, 99, 100, 100, 100, 99.5, 99.8, 100],
  ec: [1020, 1025, 1031, 1033, 1030, 1028, 1029, 1031],
  salt: [680, 685, 689, 692, 690, 688, 687, 689],
  nitrogen: [128, 130, 131, 132, 131, 130, 129, 131],
  phosphorus: [33, 34, 34, 35, 34, 34, 33, 34],
  potassium: [170, 172, 174, 175, 174, 173, 172, 174],
  ph: [6.9, 6.95, 6.96, 6.98, 6.96, 6.94, 6.95, 6.96]
}

const delay = (data, ms = 200) =>
  new Promise((resolve) => setTimeout(() => resolve(JSON.parse(JSON.stringify(data))), ms))

// 天气信息
export function getWeather() {
  return delay({
    province: '福建省',
    city: '厦门市',
    district: '翔安区',
    weather: '多云',
    text: '多云',
    temperature: 24,
    windDirection: '东风',
    windPower: '4级',
    humidity: 63,
    reportTime: '5 分钟前发布',
    low: 23,
    high: 29,
    alerts: [
      {
        title: '大风黄色预警',
        type: '大风',
        level: '黄色',
        text: '翔安区气象台继续发布大风黄色预警信号，预计未来12小时我区及沿海风力逐渐增强，请注意防范。',
        publishTime: '2026-10-21 23:30:00',
        publisher: '翔安区气象台',
        guidance: [
          '政府及相关部门按照职责做好防大风工作；',
          '停止露天活动和高空等户外危险作业；',
          '相关水域水上作业和过往船舶采取积极的应对措施；',
          '切断户外危险电源，妥善安置易受大风影响的室外物品；',
          '注意森林、草原等防火。'
        ]
      }
    ]
  })
}

// 主机列表
export function getHostList() {
  return delay(hosts)
}

// 主机实时参数
export function getRealtimeParams(hostId) {
  const host = hosts.find((h) => h.id === hostId)
  if (!host) return delay(null)
  return delay({
    ...host,
    paramMeta,
    params: paramMeta.map((m) => ({ ...m, value: host.params[m.key] }))
  })
}

// 实时曲线
export function getRealtimeChart(hostId) {
  const host = hosts.find((h) => h.id === hostId)
  if (!host) return delay(null)
  const times = []
  const end = new Date(host.collectTime.replace(/-/g, '/'))
  for (let i = 0; i < 8; i++) {
    times.push(fmt(new Date(end.getTime() - i * 7 * 60 * 1000)))
  }
  const series = {}
  Object.keys(chartValues).forEach((k) => {
    series[k] = chartValues[k].slice()
  })
  return delay({ times, series })
}

// 修改采集间隔
export function updateInterval(hostId, interval) {
  const host = hosts.find((h) => h.id === hostId)
  if (!host) return delay(null)
  modifyRecords.unshift({
    time: fmt(new Date()),
    field: '采集间隔',
    before: `${host.interval}分钟`,
    after: `${interval}分钟`,
    operator: 'admin'
  })
  host.interval = interval
  return delay({ success: true })
}

// 生成指定范围内的 mock 历史数据点（每 5 分钟一个点，围绕实时值小幅波动）
function genHistoryPoints(host, startTime, endTime) {
  const start = new Date(startTime.replace(/-/g, '/')).getTime()
  const end = new Date(endTime.replace(/-/g, '/')).getTime()
  if (isNaN(start) || isNaN(end) || start >= end) return []
  const points = []
  for (let t = end; t >= start; t -= 5 * 60 * 1000) {
    const row = { time: fmt(new Date(t)), values: {} }
    paramMeta.forEach((m, i) => {
      const base = host.params[m.key] || 0
      // 以时间为种子的确定性波动
      const wave = Math.sin(t / 3600000 + i * 1.7) * base * 0.02
      row.values[m.key] = Number((base + wave).toFixed(m.key === 'ph' ? 2 : 1))
    })
    points.push(row)
  }
  return points // 最新在前
}

// 历史数据（分页表格）
export function getHistory(hostId, { startTime, endTime, page = 1, perPage = 10 } = {}) {
  const host = hosts.find((h) => h.id === hostId)
  if (!host) return delay(null)
  const all = genHistoryPoints(host, startTime, endTime)
  const startIdx = (page - 1) * perPage
  return delay({
    passageMeta: paramMeta.map((m) => ({ ...m })),
    total: all.length,
    page,
    perPage,
    rows: all.slice(startIdx, startIdx + perPage)
  })
}

// 历史曲线（时间升序，降采样）
export function getHistoryChart(hostId, { startTime, endTime, points = 100 } = {}) {
  const host = hosts.find((h) => h.id === hostId)
  if (!host) return delay(null)
  const all = genHistoryPoints(host, startTime, endTime).slice().reverse() // 升序
  const step = Math.max(1, Math.floor(all.length / points))
  const sampled = all.filter((_, i) => i % step === 0)
  const series = {}
  paramMeta.forEach((m) => {
    series[m.key] = sampled.map((p) => p.values[m.key])
  })
  return delay({ times: sampled.map((p) => p.time), series })
}

// 修改记录
export function getModifyRecords() {
  return delay(modifyRecords)
}

// 主机设置
export function updateHostSettings(hostId, payload) {
  const host = hosts.find((h) => h.id === hostId)
  if (!host) return delay(null)
  if (payload.name && payload.name !== host.name) {
    modifyRecords.unshift({
      time: fmt(new Date()),
      field: '主机名称',
      before: host.name,
      after: payload.name,
      operator: 'admin'
    })
    host.name = payload.name
  }
  return delay({ success: true })
}

// 获取视频直播地址(mock 下给一个公开测试流)
export const getVideoLive = () =>
  delay({
    url: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8',
    protocol: 'flv',
    expireTime: fmt(new Date(Date.now() + 24 * 3600 * 1000)),
    online: true,
    deviceName: 'HB8(BH6776277)',
    signal: '84%'
  })

// 农田分析(mock:返回一段示例诊断,便于无后端时预览界面)
export function farmAnalyze() {
  return delay(
    {
      answer:
        '【作物长势评估】\n' +
        '- 画面上方光照均匀,作物冠层整体偏绿,长势中等偏上。\n' +
        '- 暂未见大面积倒伏或黄化,局部叶片颜色略浅。\n\n' +
        '【病虫害风险提示】\n' +
        '- 部分叶片可见零星咬孔,需警惕蚜虫、蓟马等刺吸式害虫。\n' +
        '- 土壤湿度接近饱和,注意根腐病与霜霉病风险。\n\n' +
        '【下一步农事建议】\n' +
        '1. 优先:明日上午巡查叶片背面,确认是否有虫体,必要时喷施生物农药。\n' +
        '2. 适度控水、加强通风,降低田间湿度。\n' +
        '3. 3 天后复查,若虫口上升及时对症用药。',
      observation: '(mock 数据)画面中作物长势正常,局部叶片有零星咬痕。',
      visionModel: 'deepseek-flash',
      reasonModel: 'deepseek-v4-pro',
      usedImage: true,
      elapsedMs: 1234
    },
    900
  )
}

// 喷灌设备(mock:内存态,便于无后端时预览界面)
const irrigation = {
  id: 'irrigation-pump',
  name: '喷灌设备',
  group: '智能灌溉',
  online: true,
  on: false,
  mode: 'auto',
  threshold: 30,
  durationMinutes: 5,
  soilMoisture: 24.5,
  soilHostName: '001-低功耗土壤',
  soilCollectTime: fmt(new Date()),
  remainingSeconds: null,
  lastAction: '土壤湿度 24.5% 低于阈值 30%，自动开启喷灌',
  lastActionAt: fmt(new Date()),
  lastActionSource: 'auto',
  bridgeConfigured: true,
  updatedAt: fmt(new Date())
}

function irrigationState() {
  return { ...irrigation, updatedAt: fmt(new Date()), remainingSeconds: irrigation.on ? 240 : null }
}

export const getIrrigation = () => delay(irrigationState())

export function switchIrrigation(on) {
  irrigation.on = !!on
  irrigation.mode = 'manual'
  irrigation.lastAction = on ? '手动开启喷灌' : '手动关闭喷灌'
  irrigation.lastActionSource = 'manual'
  irrigation.lastActionAt = fmt(new Date())
  return delay(irrigationState())
}

export function updateIrrigationConfig(payload) {
  if (payload.threshold != null) irrigation.threshold = payload.threshold
  if (payload.durationMinutes != null) irrigation.durationMinutes = payload.durationMinutes
  if (payload.mode) irrigation.mode = payload.mode
  irrigation.lastAction = '修改喷灌设置'
  irrigation.lastActionAt = fmt(new Date())
  return delay(irrigationState())
}
