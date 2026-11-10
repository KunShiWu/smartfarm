<template>
  <section class="chart-panel">
    <div class="panel-head">
      <span class="panel-title">实时变化曲线图</span>
      <button class="expand-btn" title="放大" @click="openFull">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none">
          <path d="M9 4H4v5M15 4h5v5M9 20H4v-5M15 20h5v-5" stroke="#7d9cc9" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
    </div>
    <div ref="chartRef" class="chart"></div>
    <div v-if="loading" class="chart-loading">
      <i class="ring"></i>
      <span>数据加载中</span>
    </div>

    <!-- 全屏查看 -->
    <teleport to="body">
      <div v-if="showFull" class="full-mask" @click.self="closeFull">
        <div class="full-box">
          <div class="full-head">
            <span>实时变化曲线图</span>
            <button class="full-close" @click="closeFull">✕</button>
          </div>
          <div ref="fullChartRef" class="full-chart"></div>
        </div>
      </div>
    </teleport>
  </section>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getRealtimeChart } from '../api'

const props = defineProps({
  hostId: { type: String, default: '' },
  paramMeta: { type: Array, default: () => [] }, // [{key,name,unit}]
  activeParam: { type: String, default: 'temp' }
})

const COLORS = ['#7f83f0', '#3fd2a0', '#f5b73c', '#3fa9f5', '#e86ad0', '#5ad1e8', '#a3e048', '#f0873c']

const chartRef = ref(null)
const fullChartRef = ref(null)
const showFull = ref(false)
const loading = ref(false)
let chart = null
let fullChart = null
let chartData = null // {times, series}

function buildOption() {
  const active = props.paramMeta.find((p) => p.key === props.activeParam) || props.paramMeta[0] || {}
  const selected = {}
  props.paramMeta.forEach((p) => (selected[p.name] = p.key === props.activeParam))

  return {
    backgroundColor: 'transparent',
    animationDuration: 400,
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
      itemGap: 18,
      textStyle: { color: '#7d9cc9', fontSize: 11 },
      inactiveColor: '#3a4f78',
      selected,
      data: props.paramMeta.map((p) => p.name)
    },
    grid: { left: 46, right: 84, top: 34, bottom: 26 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: chartData.times,
      axisLine: { lineStyle: { color: '#1b3a6e' } },
      axisTick: { show: false },
      axisLabel: { color: '#7d9cc9', fontSize: 10, formatter: (v) => v.slice(5, 16) }
    },
    yAxis: {
      type: 'value',
      name: active.unit ? `单位 ${active.unit}` : '',
      nameTextStyle: { color: '#7d9cc9', fontSize: 10, padding: [0, 0, 0, -14] },
      min: (v) => Math.round(v.min) - 1,
      max: (v) => Math.round(v.max),
      splitNumber: 8,
      axisLabel: { color: '#7d9cc9', fontSize: 10 },
      splitLine: { lineStyle: { color: '#16305c', type: 'dashed' } }
    },
    series: props.paramMeta.map((p, i) => ({
      name: p.name,
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      showSymbol: true,
      data: chartData.series[p.key],
      lineStyle: { width: 2, color: COLORS[i % COLORS.length] },
      itemStyle: { color: COLORS[i % COLORS.length], borderColor: '#fff', borderWidth: 1 },
      markPoint: {
        symbol: 'pin',
        symbolSize: 44,
        itemStyle: { color: COLORS[i % COLORS.length] },
        label: { color: '#fff', fontSize: 10, fontWeight: 600 },
        data: [{ type: 'max', name: '最大值' }, { type: 'min', name: '最小值' }]
      },
      markLine: {
        symbol: 'none',
        silent: true,
        lineStyle: { color: '#5d7fc0', type: 'dashed', width: 1 },
        label: {
          position: 'end',
          color: '#8fb3e8',
          fontSize: 11,
          formatter: (p) => `平均值: ${Number(p.value).toFixed(1)}`
        },
        data: [{ type: 'average', name: '平均值' }]
      }
    }))
  }
}

function render() {
  if (!chartData || !chart) return
  chart.setOption(buildOption(), true)
}

async function load(silent = false) {
  if (!props.hostId) return
  if (!silent) loading.value = true
  try {
    chartData = toAscending(await getRealtimeChart(props.hostId))
    render()
  } finally {
    loading.value = false
  }
}

/**
 * 时间轴统一为从左到右递增。
 * 上游返回的实时曲线是最新在前（降序），直接渲染会让时间轴倒着走，这里做一次翻转。
 */
function toAscending(data) {
  const times = data && data.times
  if (!Array.isArray(times) || times.length < 2) return data
  if (times[0] <= times[times.length - 1]) return data // 已是升序
  const series = {}
  Object.keys(data.series || {}).forEach((k) => {
    series[k] = (data.series[k] || []).slice().reverse()
  })
  return { times: times.slice().reverse(), series }
}

async function openFull() {
  showFull.value = true
  await nextTick()
  if (!fullChart) fullChart = echarts.init(fullChartRef.value)
  if (chartData) fullChart.setOption(buildOption(), true)
  fullChart.resize()
}
function closeFull() {
  showFull.value = false
}

function onResize() {
  chart && chart.resize()
  fullChart && fullChart.resize()
}

watch(() => props.activeParam, render)
watch(() => props.hostId, load)

onMounted(() => {
  chart = echarts.init(chartRef.value)
  load()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  chart && chart.dispose()
  fullChart && fullChart.dispose()
})

defineExpose({ reload: load })
</script>

<style scoped>
.chart-panel {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid #12406e;
  background: rgba(7, 20, 48, 0.6);
  margin-top: 8px;
}
.chart-loading {
  position: absolute;
  inset: 30px 0 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: rgba(5, 14, 34, 0.55);
  color: #6fa8d9;
  font-size: 12px;
  z-index: 5;
}
.ring {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 2.5px solid rgba(63, 169, 245, 0.25);
  border-top-color: #3fc5ff;
  animation: spin 0.9s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px 0;
}
.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: #d7e9ff;
}
.expand-btn {
  display: inline-flex;
  padding: 2px;
}
.chart {
  flex: 1;
  min-height: 220px;
}

.full-mask {
  position: fixed;
  inset: 0;
  background: rgba(2, 6, 18, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.full-box {
  width: 86vw;
  height: 80vh;
  display: flex;
  flex-direction: column;
  border: 1px solid #1d6bb0;
  background: #081636;
  padding: 10px 14px;
}
.full-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
  color: #eaf4ff;
  padding-bottom: 6px;
}
.full-close {
  color: #7d9cc9;
  font-size: 14px;
}
.full-close:hover {
  color: #fff;
}
.full-chart {
  flex: 1;
}
</style>
