<template>
  <header class="top-header">
    <!-- 左:定位 + 天气 -->
    <div
      class="h-side h-left"
      role="button"
      tabindex="0"
      aria-label="查看当前天气详情"
      @mouseenter="$emit('weather-enter')"
      @mouseleave="$emit('weather-leave')"
      @focus="$emit('weather-enter')"
      @blur="$emit('weather-leave')"
    >
      <span class="loc">
        <svg viewBox="0 0 24 24" width="13" height="13" fill="none">
          <path d="M12 2a7 7 0 0 0-7 7c0 5.2 7 13 7 13s7-7.8 7-13a7 7 0 0 0-7-7z" fill="#e84c3d"/>
          <circle cx="12" cy="9" r="2.6" fill="#fff"/>
        </svg>
        {{ weather.city }}
      </span>
      <span class="weather">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none">
          <circle cx="9" cy="9" r="4" fill="#f5b73c"/>
          <path d="M6 17a3.5 3.5 0 0 1 .6-6.95A4.5 4.5 0 0 1 15 9.5a4 4 0 0 1 3.5 3.97A3 3 0 0 1 18 19.5H7z" fill="#e8eef7" opacity=".9"/>
        </svg>
        <template v-if="weather.low != null && weather.high != null">
          {{ weather.low }}-{{ weather.high }}℃
        </template>
        <template v-else>{{ weather.temperature ?? '--' }}℃ · {{ weather.weather || weather.text }}</template>
      </span>
    </div>

    <!-- 中:标题 -->
    <div class="h-center">
      <div class="title-stage">
        <svg class="title-deco" viewBox="0 0 880 64" fill="none" preserveAspectRatio="xMidYMid meet">
          <defs>
            <linearGradient id="frameLine" x1="0" y1="0" x2="1" y2="0">
              <stop offset="0" stop-color="#1a4e8c" stop-opacity="0.2"/>
              <stop offset="0.5" stop-color="#5fc6ff"/>
              <stop offset="1" stop-color="#1a4e8c" stop-opacity="0.2"/>
            </linearGradient>
            <linearGradient id="frameFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stop-color="#123a72" stop-opacity="0.95"/>
              <stop offset="1" stop-color="#081a3e" stop-opacity="0.9"/>
            </linearGradient>
            <linearGradient id="sweepGrad" x1="0" y1="0" x2="1" y2="0">
              <stop offset="0" stop-color="#fff" stop-opacity="0"/>
              <stop offset="0.5" stop-color="#bfeaff" stop-opacity="0.85"/>
              <stop offset="1" stop-color="#fff" stop-opacity="0"/>
            </linearGradient>
            <radialGradient id="bloom" cx="0.5" cy="0.5" r="0.5">
              <stop offset="0" stop-color="#6fd4ff" stop-opacity="0.9"/>
              <stop offset="1" stop-color="#6fd4ff" stop-opacity="0"/>
            </radialGradient>
            <clipPath id="titleClip">
              <polygon points="60,7 820,7 770,50 110,50"/>
            </clipPath>
            <filter id="soft" x="-60%" y="-60%" width="220%" height="220%">
              <feGaussianBlur stdDeviation="3"/>
            </filter>
          </defs>

          <!-- 底部光晕 -->
          <ellipse cx="440" cy="53" rx="210" ry="7" fill="url(#bloom)" filter="url(#soft)"/>
          <ellipse class="halo" cx="440" cy="53" rx="120" ry="4" fill="url(#bloom)"/>

          <!-- 外框梯形 -->
          <polygon points="60,7 820,7 770,50 110,50" fill="url(#frameFill)" stroke="url(#frameLine)" stroke-width="1.4"/>
          <!-- 内框亮线 -->
          <polygon points="86,13 794,13 752,44 128,44" fill="none" stroke="#2f7fc4" stroke-width="0.8" stroke-opacity="0.7"/>
          <!-- 顶部高光 -->
          <line x1="180" y1="7" x2="700" y2="7" stroke="#9fe2ff" stroke-width="2" stroke-linecap="round" filter="url(#soft)"/>
          <line x1="180" y1="7" x2="700" y2="7" stroke="#e6f8ff" stroke-width="1" stroke-linecap="round"/>

          <!-- 扫光 -->
          <g clip-path="url(#titleClip)">
            <rect class="sweep" x="-140" y="0" width="130" height="60" fill="url(#sweepGrad)" transform="skewX(-18)"/>
          </g>

          <!-- 底部亮线 -->
          <line x1="150" y1="50" x2="730" y2="50" stroke="#4fc3ff" stroke-width="1.6" stroke-linecap="round" filter="url(#soft)"/>
          <line x1="150" y1="50" x2="730" y2="50" stroke="#d8f4ff" stroke-width="0.8" stroke-linecap="round"/>

          <!-- 两侧翼片 -->
          <g stroke="#2a6cae" stroke-width="1.2" stroke-opacity="0.9">
            <polygon points="34,24 58,14 58,40 34,46" fill="#0c2650"/>
            <polygon points="846,24 822,14 822,40 846,46" fill="#0c2650"/>
          </g>
          <g stroke="#f0a23c" stroke-width="1.4" stroke-linecap="round" stroke-opacity="0.85">
            <line x1="14" y1="34" x2="26" y2="32"/>
            <line x1="14" y1="40" x2="26" y2="38"/>
            <line x1="866" y1="34" x2="854" y2="32"/>
            <line x1="866" y1="40" x2="854" y2="38"/>
          </g>
          <g stroke="#3fa9f5" stroke-width="1.4" stroke-linecap="round" stroke-opacity="0.8">
            <line x1="4" y1="46" x2="30" y2="44"/>
            <line x1="876" y1="46" x2="850" y2="44"/>
          </g>
        </svg>
        <div class="title-row">
          <img class="title-logo" :src="logoUrl" alt="茂林学村" />
          <h1 class="title">茂林学村AI农业物联网监测系统</h1>
        </div>
      </div>
    </div>

    <!-- 右:时间 + 操作 -->
    <div class="h-side h-right">
      <span class="clock">{{ clock }}</span>
      <button class="tpl" title="查看模版" @click="todo('查看模版')">
        <svg viewBox="0 0 24 24" width="12" height="12" fill="none">
          <circle cx="12" cy="12" r="4.2" stroke="#f5b73c" stroke-width="1.6"/>
          <path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5 5l2.1 2.1M16.9 16.9 19 19M19 5l-2.1 2.1M7.1 16.9 5 19" stroke="#f5b73c" stroke-width="1.6" stroke-linecap="round"/>
        </svg>
        查看模版
      </button>
      <button class="h-btn" title="刷新数据" @click="$emit('refresh')">
        <svg viewBox="0 0 24 24" width="13" height="13" fill="none">
          <path d="M20 12a8 8 0 1 1-2.34-5.66" stroke="#8fb3e8" stroke-width="1.8" stroke-linecap="round"/>
          <path d="M20 3v4h-4" stroke="#8fb3e8" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
      <button class="h-btn" title="布局设置" @click="todo('布局设置')">
        <svg viewBox="0 0 24 24" width="13" height="13" fill="none">
          <rect x="3" y="3" width="7.5" height="7.5" rx="1" stroke="#8fb3e8" stroke-width="1.6"/>
          <rect x="13.5" y="3" width="7.5" height="7.5" rx="1" stroke="#8fb3e8" stroke-width="1.6"/>
          <rect x="3" y="13.5" width="7.5" height="7.5" rx="1" stroke="#8fb3e8" stroke-width="1.6"/>
          <rect x="13.5" y="13.5" width="7.5" height="7.5" rx="1" stroke="#8fb3e8" stroke-width="1.6"/>
        </svg>
      </button>
      <button class="h-btn" title="全屏" @click="toggleFullscreen">
        <svg viewBox="0 0 24 24" width="13" height="13" fill="none">
          <path d="M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5" stroke="#8fb3e8" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
      <button class="h-btn power" title="退出系统" @click="todo('退出系统')">
        <svg viewBox="0 0 24 24" width="13" height="13" fill="none">
          <path d="M12 3v8" stroke="#e86a6a" stroke-width="1.8" stroke-linecap="round"/>
          <path d="M6.3 6.3a8 8 0 1 0 11.4 0" stroke="#e86a6a" stroke-width="1.8" stroke-linecap="round"/>
        </svg>
      </button>
    </div>

    <div v-if="toast" class="toast">{{ toast }}</div>
  </header>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { getWeather } from '../api'
import logoUrl from '../assets/logo.png'

defineEmits(['refresh', 'weather-enter', 'weather-leave'])

const pad = (n) => String(n).padStart(2, '0')
const weekMap = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']

const clock = ref('')
const weather = ref({ city: '厦门市', low: 23, high: 34 })
const toast = ref('')
let timer = null
let toastTimer = null

function tick() {
  const d = new Date()
  clock.value =
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())} ${weekMap[d.getDay()]}`
}

function toggleFullscreen() {
  if (document.fullscreenElement) {
    document.exitFullscreen()
  } else {
    document.documentElement.requestFullscreen()
  }
}

function todo(name) {
  toast.value = `「${name}」功能待接入`
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toast.value = ''), 2000)
}

onMounted(async () => {
  tick()
  timer = setInterval(tick, 1000)
  try {
    weather.value = await getWeather()
  } catch (e) {
    // 接口异常时保持默认天气
  }
})

onUnmounted(() => {
  clearInterval(timer)
  clearTimeout(toastTimer)
})
</script>

<style scoped>
.top-header {
  position: relative;
  height: 64px;
  flex: none;
  background: linear-gradient(180deg, #0a1c44 0%, #07122f 100%);
  border-bottom: 1px solid #12365f;
}

.h-side {
  position: absolute;
  top: 0;
  height: 100%;
  display: flex;
  align-items: center;
  gap: 14px;
  z-index: 2;
}
.h-left {
  left: 14px;
  height: 40px;
  top: 12px;
  padding: 0 10px 0 4px;
  border: 1px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  transition: color 0.18s, border-color 0.18s, background 0.18s, box-shadow 0.18s;
}
.h-left:hover,
.h-left:focus-visible {
  outline: none;
  border-color: rgba(64, 177, 238, 0.46);
  background: rgba(20, 63, 111, 0.34);
  box-shadow: inset 0 0 16px rgba(40, 155, 222, 0.09);
}
.h-right {
  right: 14px;
  gap: 10px;
}

.loc {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #cfe2ff;
}
.weather {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #8fb3e8;
}

.clock {
  font-size: 12px;
  color: #8fb3e8;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.tpl {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #f5d78a;
}

.h-btn {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #1d4670;
  border-radius: 3px;
  background: #0b2048;
  transition: background 0.2s, border-color 0.2s;
}
.h-btn:hover {
  background: #123368;
  border-color: #2a6bb0;
}

/* 标题 */
.h-center {
  position: absolute;
  left: 50%;
  top: 0;
  transform: translateX(-50%);
  height: 100%;
}

.title-stage {
  position: relative;
  width: 660px;
  height: 100%;
}
.title-deco {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
.sweep {
  animation: sweep 4.5s ease-in-out infinite;
}
@keyframes sweep {
  0% { transform: skewX(-18) translateX(0); opacity: 0; }
  12% { opacity: 1; }
  55% { transform: skewX(-18) translateX(1080px); opacity: 1; }
  62% { opacity: 0; }
  100% { transform: skewX(-18) translateX(1080px); opacity: 0; }
}
.halo {
  animation: halo-pulse 3s ease-in-out infinite;
}
@keyframes halo-pulse {
  0%, 100% { opacity: 0.55; }
  50% { opacity: 1; }
}

.title-row {
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  transform: translateY(-56%);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.title-logo {
  height: 38px;
  width: auto;
  flex: none;
  filter: drop-shadow(0 0 7px rgba(96, 220, 120, 0.5));
}

.title {
  font-size: 25px;
  font-weight: 700;
  letter-spacing: 7px;
  text-indent: 7px;
  color: #eaf4ff;
  text-shadow:
    0 0 10px rgba(120, 210, 255, 0.95),
    0 0 26px rgba(64, 170, 255, 0.7),
    0 0 46px rgba(64, 170, 255, 0.45);
  white-space: nowrap;
  margin: 0;
}

.toast {
  position: absolute;
  top: 70px;
  left: 50%;
  transform: translateX(-50%);
  padding: 6px 16px;
  background: rgba(10, 34, 76, 0.95);
  border: 1px solid #2a6bb0;
  border-radius: 3px;
  font-size: 12px;
  color: #cfe2ff;
  z-index: 99;
}
</style>
