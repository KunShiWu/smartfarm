<template>
  <section class="weather-panel" @mousedown.stop @wheel.stop>
    <div class="panel-glow"></div>
    <header class="weather-head">
      <div class="head-title">
        <span class="live-dot"></span>
        当前天气
      </div>
      <button class="refresh-btn" :class="{ spinning: loading }" title="刷新天气" @click="loadWeather">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none">
          <path d="M20 12a8 8 0 1 1-2.34-5.66M20 3v5h-5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
    </header>

    <div v-if="loading && !weather" class="weather-loading">
      <span class="skeleton wide"></span>
      <span class="skeleton hero"></span>
      <span class="skeleton"></span>
    </div>

    <div v-else-if="error && !weather" class="weather-error">
      <span class="error-icon">!</span>
      <strong>天气数据暂不可用</strong>
      <small>{{ error }}</small>
      <button @click="loadWeather">重新获取</button>
    </div>

    <template v-else-if="weather">
      <div class="location-line">
        <svg viewBox="0 0 24 24" width="12" height="12" fill="none">
          <path d="M12 21s7-6.3 7-12a7 7 0 1 0-14 0c0 5.7 7 12 7 12z" stroke="currentColor" stroke-width="1.8"/>
          <circle cx="12" cy="9" r="2.4" fill="currentColor"/>
        </svg>
        <span>{{ locationText }}</span>
      </div>

      <div class="weather-main">
        <div class="weather-icon" :class="`is-${symbol}`">
          <svg viewBox="0 0 76 60" fill="none" aria-hidden="true">
            <circle v-if="symbol === 'sunny' || symbol === 'cloudy'" cx="27" cy="22" r="12" fill="#ffc94c"/>
            <g v-if="symbol === 'sunny'" stroke="#ffd86b" stroke-width="2" stroke-linecap="round">
              <path d="M27 3v7M27 34v7M8 22h7M39 22h7M14 9l5 5M35 30l5 5M40 9l-5 5M19 30l-5 5"/>
            </g>
            <path v-if="symbol !== 'sunny'" d="M20 49a11 11 0 0 1-1.2-21.9A16 16 0 0 1 49 25a12 12 0 0 1 2 23.8z" fill="url(#cloudFill)"/>
            <g v-if="symbol === 'rain'" stroke="#62d3ff" stroke-width="3" stroke-linecap="round">
              <path d="M27 51l-3 6M40 51l-3 6M53 51l-3 6"/>
            </g>
            <path v-if="symbol === 'storm'" d="M40 45h9l-7 8h6L35 62l4-10h-6z" fill="#ffd04a"/>
            <g v-if="symbol === 'snow'" stroke="#dff7ff" stroke-width="1.8" stroke-linecap="round">
              <path d="M28 51v8M24.5 53l7 4M31.5 53l-7 4M48 51v8M44.5 53l7 4M51.5 53l-7 4"/>
            </g>
            <g v-if="symbol === 'fog'" stroke="#a9c8db" stroke-width="3" stroke-linecap="round">
              <path d="M16 40h44M21 49h38M27 57h27"/>
            </g>
            <defs>
              <linearGradient id="cloudFill" x1="16" y1="25" x2="52" y2="51" gradientUnits="userSpaceOnUse">
                <stop stop-color="#f7fbff"/><stop offset="1" stop-color="#9ec7e8"/>
              </linearGradient>
            </defs>
          </svg>
        </div>

        <div class="temperature">
          <div><strong>{{ weather.temperature ?? '--' }}</strong><sup>℃</sup></div>
          <span>{{ weather.weather || weather.text || '天气未知' }}</span>
        </div>

        <div class="weather-meta">
          <span>风向</span>
          <strong>{{ weather.windDirection || '--' }}</strong>
          <span>风力</span>
          <strong>{{ weather.windPower || '--' }}</strong>
        </div>
      </div>

      <div class="weather-subline">
        <span v-if="weather.humidity != null">湿度 {{ weather.humidity }}%</span>
        <span>{{ weather.reportTime || '实时更新' }}</span>
      </div>

      <button
        v-if="primaryAlert"
        class="alert-card"
        :class="`tone-${tone}`"
        @click="alertOpen = true"
      >
        <span class="alert-mark">!</span>
        <span class="alert-copy">
          <strong>{{ primaryAlert.title }}</strong>
          <small>{{ primaryAlert.publishTime || primaryAlert.publisher || '气象预警' }}</small>
        </span>
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none">
          <path d="m9 5 7 7-7 7" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>

      <div v-else class="no-alert">
        <span>✓</span> 当前无气象预警
      </div>
    </template>

    <BaseDialog v-model="alertOpen" :title="primaryAlert?.title || '气象预警详情'" width="640px">
      <div v-if="primaryAlert" class="alert-detail" :class="`tone-${tone}`">
        <div class="detail-badges">
          <span>{{ primaryAlert.type }}</span>
          <span>{{ primaryAlert.level }}预警</span>
        </div>
        <div class="detail-meta">
          <span>{{ primaryAlert.publisher }}</span>
          <time>{{ primaryAlert.publishTime }}</time>
        </div>
        <p>{{ primaryAlert.text }}</p>
        <div v-if="primaryAlert.guidance?.length" class="guidance">
          <h4>防御指南</h4>
          <ol>
            <li v-for="(item, index) in primaryAlert.guidance" :key="`${index}-${item}`">{{ item }}</li>
          </ol>
        </div>
      </div>
    </BaseDialog>
  </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { getWeather } from '../api'
import BaseDialog from './BaseDialog.vue'
import { alertTone, weatherSymbol } from './weatherPresentation.js'

const emit = defineEmits(['dialog-change'])

const weather = ref(null)
const loading = ref(false)
const error = ref('')
const alertOpen = ref(false)

const primaryAlert = computed(() => weather.value?.alerts?.[0] || null)
const tone = computed(() => alertTone(primaryAlert.value?.level))
const symbol = computed(() => weatherSymbol(weather.value?.weather || weather.value?.text))
const locationText = computed(() =>
  [weather.value?.province, weather.value?.city, weather.value?.district].filter(Boolean).join(' · ')
)

watch(alertOpen, (value) => emit('dialog-change', value))

async function loadWeather() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    weather.value = await getWeather()
  } catch (e) {
    error.value = e?.response?.data?.message || '请检查天气密钥或网络连接'
  } finally {
    loading.value = false
  }
}

onMounted(loadWeather)
</script>

<style scoped>
.weather-panel {
  position: relative;
  width: 290px;
  min-height: 274px;
  padding: 14px;
  color: #dceeff;
  border: 1px solid rgba(62, 173, 242, 0.58);
  border-radius: 3px;
  background:
    linear-gradient(145deg, rgba(13, 45, 91, 0.96), rgba(5, 20, 51, 0.96)),
    radial-gradient(circle at 85% 12%, rgba(51, 198, 255, 0.18), transparent 45%);
  box-shadow: 0 12px 32px rgba(0, 8, 28, 0.55), inset 0 0 28px rgba(28, 112, 189, 0.1);
  backdrop-filter: blur(8px);
  overflow: hidden;
  user-select: text;
}
.weather-panel::before,
.weather-panel::after {
  content: '';
  position: absolute;
  width: 30px;
  height: 12px;
  pointer-events: none;
}
.weather-panel::before {
  left: -1px;
  top: -1px;
  border-left: 2px solid #72e6ff;
  border-top: 2px solid #72e6ff;
}
.weather-panel::after {
  right: -1px;
  bottom: -1px;
  border-right: 2px solid #268dd6;
  border-bottom: 2px solid #268dd6;
}
.panel-glow {
  position: absolute;
  right: -34px;
  top: -48px;
  width: 140px;
  height: 140px;
  border-radius: 50%;
  background: rgba(49, 198, 255, 0.13);
  filter: blur(20px);
  pointer-events: none;
}
.weather-head,
.head-title,
.location-line,
.weather-subline,
.alert-card,
.no-alert,
.detail-meta,
.detail-badges {
  display: flex;
  align-items: center;
}
.weather-head {
  position: relative;
  justify-content: space-between;
  padding-bottom: 9px;
  border-bottom: 1px solid rgba(58, 132, 198, 0.35);
}
.head-title {
  gap: 7px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 1px;
}
.live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #40e3a0;
  box-shadow: 0 0 8px #40e3a0;
  animation: breathe 2s ease-in-out infinite;
}
@keyframes breathe { 50% { opacity: 0.45; } }
.refresh-btn {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  color: #76bde8;
  border: 1px solid rgba(60, 142, 202, 0.35);
  border-radius: 50%;
}
.refresh-btn:hover { color: #dff7ff; border-color: #5bc8fa; }
.refresh-btn.spinning svg { animation: spin 0.9s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.location-line {
  gap: 5px;
  margin-top: 11px;
  color: #86b9dd;
  font-size: 11px;
}
.weather-main {
  display: grid;
  grid-template-columns: 66px 1fr 64px;
  align-items: center;
  min-height: 92px;
}
.weather-icon svg { width: 64px; height: 56px; overflow: visible; filter: drop-shadow(0 5px 9px rgba(0, 12, 34, 0.5)); }
.temperature strong {
  font-size: 42px;
  line-height: 1;
  font-weight: 500;
  color: #f4fbff;
  text-shadow: 0 0 18px rgba(86, 201, 255, 0.4);
}
.temperature sup { margin-left: 2px; color: #83d8ff; font-size: 15px; vertical-align: top; }
.temperature > span { display: block; margin-top: 4px; color: #c7e9ff; font-size: 13px; }
.weather-meta {
  display: grid;
  grid-template-columns: 1fr;
  gap: 2px;
  padding-left: 10px;
  border-left: 1px solid rgba(75, 143, 195, 0.35);
}
.weather-meta span { color: #6d9abe; font-size: 10px; }
.weather-meta strong { color: #d8efff; font-size: 11px; font-weight: 500; margin-bottom: 3px; }
.weather-subline {
  justify-content: space-between;
  min-height: 24px;
  padding: 0 7px;
  border-radius: 2px;
  color: #77a8cc;
  background: rgba(16, 54, 100, 0.42);
  font-size: 10px;
}
.alert-card {
  width: 100%;
  min-height: 53px;
  gap: 9px;
  margin-top: 10px;
  padding: 7px 9px;
  text-align: left;
  border: 1px solid color-mix(in srgb, var(--tone) 58%, transparent);
  border-radius: 3px;
  color: #fff3cf;
  background: color-mix(in srgb, var(--tone) 14%, rgba(13, 33, 63, 0.9));
  transition: transform 0.18s, box-shadow 0.18s;
}
.alert-card:hover { transform: translateY(-1px); box-shadow: 0 0 14px color-mix(in srgb, var(--tone) 30%, transparent); }
.alert-card > svg { margin-left: auto; flex: none; opacity: 0.75; }
.alert-mark {
  display: grid;
  place-items: center;
  width: 27px;
  height: 27px;
  flex: none;
  border-radius: 50%;
  color: #07182e;
  background: var(--tone);
  font-weight: 800;
}
.alert-copy { min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.alert-copy strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; }
.alert-copy small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #b6c7d8; font-size: 9px; }
.tone-blue { --tone: #4da8ff; }
.tone-yellow { --tone: #ffd04a; }
.tone-orange { --tone: #ff952f; }
.tone-red { --tone: #ff4f59; }
.no-alert {
  justify-content: center;
  gap: 6px;
  min-height: 42px;
  margin-top: 10px;
  color: #74cbaa;
  border: 1px solid rgba(64, 192, 143, 0.18);
  background: rgba(35, 126, 91, 0.08);
  font-size: 11px;
}
.weather-loading { padding-top: 14px; }
.skeleton { display: block; width: 72%; height: 18px; margin-bottom: 12px; border-radius: 3px; background: linear-gradient(90deg, #102e58, #174478, #102e58); background-size: 200% 100%; animation: shimmer 1.2s linear infinite; }
.skeleton.wide { width: 92%; }
.skeleton.hero { width: 100%; height: 88px; }
@keyframes shimmer { to { background-position: -200% 0; } }
.weather-error { display: flex; min-height: 214px; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: #87a8c7; font-size: 11px; text-align: center; }
.weather-error strong { color: #d3e8fb; font-size: 13px; }
.weather-error small { max-width: 230px; line-height: 1.5; color: #6688aa; }
.weather-error button { margin-top: 4px; padding: 5px 12px; color: #9bdfff; border: 1px solid #266a9b; border-radius: 2px; background: rgba(18, 76, 124, 0.35); }
.error-icon { display: grid; place-items: center; width: 30px; height: 30px; border: 1px solid #e1a548; border-radius: 50%; color: #ffd16b; font-weight: 700; }
.alert-detail { --tone: #4da8ff; }
.detail-badges { gap: 8px; margin-bottom: 10px; }
.detail-badges span { padding: 3px 9px; border: 1px solid color-mix(in srgb, var(--tone) 55%, transparent); border-radius: 12px; color: var(--tone); background: color-mix(in srgb, var(--tone) 10%, transparent); font-size: 11px; }
.detail-meta { justify-content: space-between; gap: 18px; padding-bottom: 10px; border-bottom: 1px solid rgba(72, 126, 177, 0.25); color: #789dc0; font-size: 11px; }
.alert-detail p { margin: 14px 0; color: #c7ddf2; line-height: 1.85; text-align: justify; }
.guidance { padding: 12px 14px; border-left: 2px solid var(--tone); background: rgba(7, 23, 50, 0.58); }
.guidance h4 { margin: 0 0 9px; color: #e5f3ff; font-size: 12px; }
.guidance ol { margin: 0; padding-left: 20px; }
.guidance li { margin: 6px 0; line-height: 1.6; color: #a9c4de; }
</style>
