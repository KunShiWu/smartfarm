<template>
  <section class="history-panel">
    <!-- 查询工具栏 -->
    <div class="toolbar">
      <div class="quick-ranges">
        <button
          v-for="r in quickRanges"
          :key="r.label"
          class="q-btn"
          :class="{ active: activeQuick === r.label }"
          @click="applyQuick(r)"
        >{{ r.label }}</button>
      </div>
      <div class="time-inputs">
        <input v-model="startInput" type="datetime-local" class="t-input" />
        <span class="sep">至</span>
        <input v-model="endInput" type="datetime-local" class="t-input" />
        <button class="btn primary" :disabled="loading" @click="query">
          {{ loading ? '查询中…' : '查询' }}
        </button>
      </div>
      <span v-if="errMsg" class="err-msg">{{ errMsg }}</span>
    </div>

    <!-- 历史曲线 -->
    <div class="chart-box">
      <div class="box-title">历史变化曲线</div>
      <div ref="chartRef" class="chart"></div>
      <div v-if="!chartTimes.length && !loading" class="empty-tip">所选时间段暂无数据</div>
    </div>

    <!-- 历史数据表格 -->
    <div class="table-box">
      <div class="box-title">历史数据列表</div>
      <div class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>时间</th>
              <th v-for="m in paramMeta" :key="m.key">{{ m.name }}<template v-if="m.unit">({{ m.unit }})</template></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, i) in rows" :key="i">
              <td>{{ row.time }}</td>
              <td v-for="m in paramMeta" :key="m.key">{{ formatVal(row.values[m.key]) }}</td>
            </tr>
            <tr v-if="!rows.length">
              <td :colspan="paramMeta.length + 1" class="empty">{{ loading ? '加载中…' : '暂无数据' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="pager">
        <span class="total">共 {{ total }} 条</span>
        <button class="p-btn" :disabled="page <= 1 || loading" @click="gotoPage(page - 1)">上一页</button>
        <span class="page-info">{{ page }} / {{ totalPages }}</span>
        <button class="p-btn" :disabled="page >= totalPages || loading" @click="gotoPage(page + 1)">下一页</button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { getRealtimeParams, getHistory, getHistoryChart } from '../api'

const props = defineProps({
  hostId: { type: String, default: '' }
})

const COLORS = ['#7f83f0', '#3fd2a0', '#f5b73c', '#3fa9f5', '#e86ad0', '#5ad1e8', '#a3e048', '#f0873c']
const PER_PAGE = 10

const pad = (n) => String(n).padStart(2, '0')
const fmtLocal = (d) =>
  `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
const toApiTime = (s) => (s ? s.replace('T', ' ') + ':00' : '')

const quickRanges = [
  { label: '近1小时', hours: 1 },
  { label: '近24小时', hours: 24 },
  { label: '近7天', hours: 24 * 7 }
]

const paramMeta = ref([])
const startInput = ref('')
const endInput = ref('')
const activeQuick = ref('近24小时')
const loading = ref(false)
const errMsg = ref('')

const chartRef = ref(null)
let chart = null
const chartTimes = ref([])

const rows = ref([])
const page = ref(1)
const total = ref(0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PER_PAGE)))

function setRange(hours) {
  const end = new Date()
  const start = new Date(end.getTime() - hours * 3600 * 1000)
  startInput.value = fmtLocal(start)
  endInput.value = fmtLocal(end)
}

function applyQuick(r) {
  activeQuick.value = r.label
  setRange(r.hours)
  query()
}

async function loadMeta() {
  const detail = await getRealtimeParams(props.hostId)
  paramMeta.value = (detail && detail.paramMeta) || []
}

async function loadChart() {
  const data = await getHistoryChart(props.hostId, {
    startTime: toApiTime(startInput.value),
    endTime: toApiTime(endInput.value),
    points: 100
  })
  chartTimes.value = (data && data.times) || []
  renderChart(data)
}

async function loadTable() {
  const data = await getHistory(props.hostId, {
    startTime: toApiTime(startInput.value),
    endTime: toApiTime(endInput.value),
    page: page.value,
    perPage: PER_PAGE
  })
  rows.value = (data && data.rows) || []
  total.value = (data && data.total) || 0
}

async function query() {
  if (!props.hostId) return
  activeQuick.value = quickRanges.some((q) => q.label === activeQuick.value) ? activeQuick.value : ''
  loading.value = true
  errMsg.value = ''
  page.value = 1
  try {
    await Promise.all([loadChart(), loadTable()])
  } catch (e) {
    errMsg.value = (e && e.mess) || '查询失败,请稍后重试'
    rows.value = []
    total.value = 0
    chartTimes.value = []
    renderChart(null)
  } finally {
    loading.value = false
  }
}

async function gotoPage(p) {
  page.value = p
  loading.value = true
  errMsg.value = ''
  try {
    await loadTable()
  } catch (e) {
    errMsg.value = (e && e.mess) || '查询失败,请稍后重试'
  } finally {
    loading.value = false
  }
}

function renderChart(data) {
  if (!chart) return
  const times = (data && data.times) || []
  const series = (data && data.series) || {}
  chart.setOption(
    {
      backgroundColor: 'transparent',
      animationDuration: 300,
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(8, 26, 60, 0.95)',
        borderColor: '#1d6bb0',
        textStyle: { color: '#d7e9ff', fontSize: 12 }
      },
      legend: {
        top: 0,
        left: 'center',
        icon: 'roundRect',
        itemWidth: 16,
        itemHeight: 3,
        itemGap: 14,
        textStyle: { color: '#7d9cc9', fontSize: 11 },
        inactiveColor: '#3a4f78',
        data: paramMeta.value.map((p) => p.name)
      },
      grid: { left: 46, right: 20, top: 34, bottom: 40 },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: times,
        axisLine: { lineStyle: { color: '#1b3a6e' } },
        axisTick: { show: false },
        axisLabel: {
          color: '#7d9cc9',
          fontSize: 10,
          formatter: (v) => (v && v.length >= 16 ? v.slice(5, 16) : v)
        }
      },
      yAxis: {
        type: 'value',
        scale: true,
        axisLabel: { color: '#7d9cc9', fontSize: 10 },
        splitLine: { lineStyle: { color: '#16305c', type: 'dashed' } }
      },
      dataZoom: [
        { type: 'inside' },
        { type: 'slider', height: 14, bottom: 4, borderColor: '#1d4670', backgroundColor: 'rgba(8,26,60,0.4)' }
      ],
      series: paramMeta.value.map((p, i) => ({
        name: p.name,
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: series[p.key] || [],
        lineStyle: { width: 1.6, color: COLORS[i % COLORS.length] },
        itemStyle: { color: COLORS[i % COLORS.length] }
      }))
    },
    true
  )
}

function formatVal(v) {
  return v === null || v === undefined ? '--' : v
}

function onResize() {
  chart && chart.resize()
}

watch(
  () => props.hostId,
  async () => {
    if (!props.hostId) return
    await loadMeta()
    query()
  }
)

onMounted(async () => {
  chart = echarts.init(chartRef.value)
  setRange(24)
  if (props.hostId) {
    await loadMeta()
    query()
  }
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  chart && chart.dispose()
})
</script>

<style scoped>
.history-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 工具栏 */
.toolbar {
  flex: none;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 6px 8px;
  border: 1px solid #12406e;
  background: rgba(7, 20, 48, 0.6);
  flex-wrap: wrap;
}
.quick-ranges {
  display: flex;
  gap: 6px;
}
.q-btn {
  height: 26px;
  padding: 0 12px;
  font-size: 12px;
  color: #9cc0ee;
  border: 1px solid #1d4670;
  border-radius: 3px;
  background: #0b2048;
  cursor: pointer;
}
.q-btn:hover {
  color: #d7e9ff;
}
.q-btn.active {
  color: #fff;
  border-color: #3fa9f5;
  background: linear-gradient(180deg, #1a7fd2, #0f56a4);
}
.time-inputs {
  display: flex;
  align-items: center;
  gap: 6px;
}
.t-input {
  height: 26px;
  padding: 0 6px;
  font-size: 12px;
  color: #d7e9ff;
  border: 1px solid #1d4670;
  border-radius: 3px;
  background: #081a3c;
  color-scheme: dark;
}
.sep {
  font-size: 12px;
  color: #7d9cc9;
}
.btn {
  height: 26px;
  padding: 0 16px;
  border-radius: 3px;
  font-size: 12px;
  cursor: pointer;
}
.btn.primary {
  background: linear-gradient(180deg, #1a7fd2, #0f56a4);
  border: 1px solid #3fa9f5;
  color: #fff;
}
.btn.primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.err-msg {
  font-size: 12px;
  color: #f0868c;
}

/* 区块通用 */
.box-title {
  font-size: 13px;
  font-weight: 600;
  color: #d7e9ff;
  padding: 8px 10px 0;
}
.chart-box {
  flex: none;
  height: 300px;
  position: relative;
  display: flex;
  flex-direction: column;
  border: 1px solid #12406e;
  background: rgba(7, 20, 48, 0.6);
}
.chart {
  flex: 1;
  min-height: 0;
}
.empty-tip {
  position: absolute;
  inset: 30px 0 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #4d6a99;
  pointer-events: none;
}

/* 表格 */
.table-box {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid #12406e;
  background: rgba(7, 20, 48, 0.6);
}
.table-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 6px 10px 0;
}
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.data-table th,
.data-table td {
  padding: 7px 8px;
  border-bottom: 1px solid #14335f;
  text-align: left;
  color: #b9d2f5;
  white-space: nowrap;
}
.data-table th {
  position: sticky;
  top: 0;
  color: #6f92c4;
  font-weight: 500;
  background: #0a1c46;
  z-index: 1;
}
.data-table .empty {
  text-align: center;
  color: #4d6a99;
}
.pager {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 8px 10px;
  font-size: 12px;
  color: #7d9cc9;
}
.p-btn {
  height: 24px;
  padding: 0 12px;
  font-size: 12px;
  color: #9cc0ee;
  border: 1px solid #1d4670;
  border-radius: 3px;
  background: #0b2048;
  cursor: pointer;
}
.p-btn:hover:not(:disabled) {
  color: #d7e9ff;
}
.p-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.page-info {
  min-width: 56px;
  text-align: center;
}
</style>
