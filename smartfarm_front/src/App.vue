<template>
  <div class="screen">
    <TopHeader
      @refresh="reload"
      @weather-enter="enterWeatherTrigger"
      @weather-leave="leaveWeatherTrigger"
    />
    <NavTabs v-model="activeTab" />

    <div class="content" :class="{ 'map-mode': showHome }">
      <SidePanel
        v-if="!showHome && !showAgent"
        :hosts="hosts"
        v-model="selectedHostId"
        @select="onSelectHost"
      />

      <!-- 首页：主机列表以抽屉形式浮在地图上 -->
      <transition name="drawer">
        <SidePanel
          v-if="showHome && listOpen"
          class="map-drawer"
          :hosts="hosts"
          v-model="selectedHostId"
          @select="onSelectHost"
        />
      </transition>

      <transition name="weather-popover">
        <div
          v-if="weatherPopoverAvailable"
          v-show="weatherOpen"
          class="home-weather-shell"
          @mouseenter="enterWeatherPanel"
          @mouseleave="leaveWeatherPanel"
        >
          <WeatherPanel class="home-weather" @dialog-change="setWeatherLocked" />
        </div>
      </transition>

      <main class="main">
        <HomeMap
          v-if="showHome"
          :hosts="hosts"
          :selected-host-id="selectedHostId"
          @select="onSelectHost"
        />

        <template v-else-if="showDashboard && hostDetail">
          <ParamCards
            :detail="hostDetail"
            :active-param="activeParam"
            @select-param="activeParam = $event"
            @updated="reload"
          />
          <RealtimeChart
            ref="chartRef"
            :host-id="selectedHostId"
            :param-meta="hostDetail.paramMeta"
            :active-param="activeParam"
          />
        </template>

        <div v-else-if="showDashboard" class="loading">数据加载中…</div>

        <HistoryPanel v-else-if="showHistory" :host-id="selectedHostId" />

        <VideoPanel v-else-if="showVideo" />

        <IrrigationPanel v-else-if="showControl" />

        <AgentChatPanel v-else-if="showAgent" />

        <div v-else class="placeholder">
          <svg viewBox="0 0 24 24" width="42" height="42" fill="none">
            <rect x="3" y="4" width="18" height="14" rx="2" stroke="#1d6bb0" stroke-width="1.5"/>
            <path d="M3 9h18M8 21h8M12 18v3" stroke="#1d6bb0" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
          <p>「{{ activeTab }}」模块建设中,接口已预留</p>
        </div>
      </main>

      <!-- 首页左下角：主机列表开关 -->
      <button v-if="showHome" class="list-toggle" :class="{ open: listOpen }" @click="listOpen = !listOpen">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none">
          <rect x="3" y="3" width="18" height="18" rx="2" stroke="#7ee6ff" stroke-width="1.6"/>
          <path d="M3 9h18M9 9v12" stroke="#7ee6ff" stroke-width="1.6"/>
        </svg>
        主机列表
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import TopHeader from './components/TopHeader.vue'
import NavTabs from './components/NavTabs.vue'
import SidePanel from './components/SidePanel.vue'
import ParamCards from './components/ParamCards.vue'
import RealtimeChart from './components/RealtimeChart.vue'
import HistoryPanel from './components/HistoryPanel.vue'
import VideoPanel from './components/VideoPanel.vue'
import IrrigationPanel from './components/IrrigationPanel.vue'
import AgentChatPanel from './components/AgentChatPanel.vue'
import HomeMap from './components/HomeMap.vue'
import WeatherPanel from './components/WeatherPanel.vue'
import { createWeatherPopoverState } from './components/weatherPopoverState.js'
import { supportsWeatherPopover } from './components/navigationTabs.js'
import { getHostList, getRealtimeParams } from './api'

const activeTab = ref('首页')
const hosts = ref([])
const selectedHostId = ref('')
const hostDetail = ref(null)
const activeParam = ref('temp')
const chartRef = ref(null)
const listOpen = ref(false)
const {
  open: weatherOpen,
  enterTrigger: enterWeatherTrigger,
  leaveTrigger: leaveWeatherTrigger,
  enterPanel: enterWeatherPanel,
  leavePanel: leaveWeatherPanel,
  setLocked: setWeatherLocked,
  destroy: destroyWeatherPopover,
} = createWeatherPopoverState()

// 首页:全屏地图大屏
const showHome = computed(() => activeTab.value === '首页')
// 实时数据页:参数卡片 + 实时曲线
const showDashboard = computed(() => activeTab.value === '实时数据')
// 历史数据页
const showHistory = computed(() => activeTab.value === '历史数据')
// 视频监控页
const showVideo = computed(() => activeTab.value === '视频监控')
// 设备控制页(喷灌设备)
const showControl = computed(() => activeTab.value === '设备控制')
// AI 智能体对话页
const showAgent = computed(() => activeTab.value === 'AI智能体')
const weatherPopoverAvailable = computed(() => supportsWeatherPopover(activeTab.value))

/** 真实土壤检测主机才有实时参数接口(摄像头/喷灌设备是虚拟设备) */
function isRealHost(host) {
  return !!host && (host.type === undefined || host.type === 'host')
}

async function loadDetail() {
  if (!selectedHostId.value) return
  const cur = hosts.value.find((h) => h.id === selectedHostId.value)
  // 摄像头、喷灌设备没有实时参数接口,跳过
  if (!isRealHost(cur)) return
  hostDetail.value = await getRealtimeParams(selectedHostId.value)
}

function onSelectHost(host) {
  if (!host) return
  // 选中摄像头 → 跳到视频监控页
  if (host.type === 'camera') {
    activeTab.value = '视频监控'
    return
  }
  // 选中喷灌设备 → 跳到设备控制页
  if (host.type === 'irrigation') {
    activeTab.value = '设备控制'
    return
  }
  // 首页点地图标记选中的主机 → 进入实时数据页查看参数
  if (showHome.value) activeTab.value = '实时数据'
  listOpen.value = false
  loadDetail()
}

// 手动从首页/虚拟设备页切到其它标签时,若当前选中不是真实主机,则回落第一台真实主机
watch(activeTab, (tab) => {
  if (tab === '首页' || tab === '视频监控' || tab === '设备控制' || tab === 'AI智能体') return
  listOpen.value = false
  const cur = hosts.value.find((h) => h.id === selectedHostId.value)
  if (!isRealHost(cur)) {
    const first = hosts.value.find(isRealHost)
    if (first) {
      selectedHostId.value = first.id
      loadDetail()
    }
  }
})

async function reload() {
  await loadDetail()
  chartRef.value && chartRef.value.reload()
}

// 实时数据自动刷新(15s,静默无闪烁)
const POLL_MS = 15000
let pollTimer = null
async function pollTick() {
  if (!showDashboard.value && !showHome.value) return
  try {
    hosts.value = await getHostList()
    if (showDashboard.value && selectedHostId.value) {
      await loadDetail()
      chartRef.value && chartRef.value.reload(true)
    }
  } catch (e) {
    // 单次刷新失败,等待下一轮
  }
}

onMounted(async () => {
  hosts.value = await getHostList()
  const first = hosts.value.find(isRealHost)
  if (first) {
    selectedHostId.value = first.id
    await loadDetail()
  }
  pollTimer = setInterval(pollTick, POLL_MS)
})

onUnmounted(() => {
  clearInterval(pollTimer)
  destroyWeatherPopover()
})
</script>

<style scoped>
.screen {
  position: relative;
  height: 100vh;
  min-width: 1180px;
  min-height: 660px;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(1200px 500px at 50% -10%, rgba(20, 70, 140, 0.35), transparent 70%),
    linear-gradient(180deg, #060c26 0%, #04081a 100%);
  overflow: auto;
}
.screen::before {
  content: '';
  position: absolute;
  top: -140px;
  left: 50%;
  transform: translateX(-50%);
  width: 900px;
  height: 320px;
  background: radial-gradient(ellipse at center, rgba(46, 140, 240, 0.22), transparent 65%);
  pointer-events: none;
  animation: bg-breathe 7s ease-in-out infinite;
}
@keyframes bg-breathe {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}

.content {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 8px;
  padding: 0 10px 10px;
  position: relative;
}
/* 首页:地图铺满，无内边距 */
.content.map-mode {
  padding: 0;
  gap: 0;
}

/* 首页主机列表抽屉 */
.map-drawer {
  position: absolute;
  left: 10px;
  top: 10px;
  bottom: 10px;
  z-index: 20;
  background: rgba(6, 18, 44, 0.94);
  backdrop-filter: blur(4px);
  box-shadow: 0 6px 28px rgba(0, 8, 24, 0.7);
}
.drawer-enter-active,
.drawer-leave-active {
  transition: transform 0.22s ease, opacity 0.22s ease;
}
.drawer-enter-from,
.drawer-leave-to {
  transform: translateX(-24px);
  opacity: 0;
}

.home-weather-shell {
  position: absolute;
  left: 14px;
  top: 10px;
  z-index: 30;
}
.home-weather {
  box-shadow: 0 16px 42px rgba(0, 8, 28, 0.68), inset 0 0 28px rgba(28, 112, 189, 0.1);
}
.weather-popover-enter-active,
.weather-popover-leave-active {
  transition: opacity 0.18s ease, transform 0.2s ease;
  transform-origin: top left;
}
.weather-popover-enter-from,
.weather-popover-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.985);
}

/* 首页左下角主机列表开关 */
.list-toggle {
  position: absolute;
  left: 12px;
  bottom: 12px;
  z-index: 21;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 14px;
  font-size: 12px;
  color: #bfe6ff;
  border: 1px solid rgba(63, 169, 245, 0.5);
  background: linear-gradient(180deg, rgba(16, 46, 96, 0.92), rgba(8, 24, 56, 0.94));
  clip-path: polygon(8px 0, 100% 0, 100% calc(100% - 8px), calc(100% - 8px) 100%, 0 100%, 0 8px);
  transition: color 0.2s, border-color 0.2s, box-shadow 0.2s;
}
.list-toggle:hover,
.list-toggle.open {
  color: #ffffff;
  border-color: rgba(126, 230, 255, 0.95);
  box-shadow: 0 0 14px rgba(46, 180, 255, 0.45);
}

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.loading,
.placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #4d6a99;
  border: 1px solid #12406e;
  background: rgba(7, 20, 48, 0.6);
  font-size: 13px;
}
</style>
