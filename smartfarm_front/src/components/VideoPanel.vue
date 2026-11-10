<template>
  <section class="video-panel">
    <div class="panel-head">
      <span class="panel-title">视频监控</span>
      <div class="head-right">
        <span class="live-badge" :class="{ on: playing }">
          <i></i>{{ playing ? 'LIVE' : (loading ? '连接中' : '待机') }}
        </span>
        <span v-if="info.deviceName" class="meta">{{ info.deviceName }}</span>
        <span v-if="info.signal" class="meta">
          <svg viewBox="0 0 24 24" width="12" height="12" fill="none">
            <path d="M4 16h2v4H4zM9 12h2v8H9zM14 8h2v12h-2zM19 4h2v16h-2z" fill="#3fd2a0"/>
          </svg>
          {{ info.signal }}
        </span>
        <button class="analyze-btn" title="农田分析" @click="openFarm">
          <svg viewBox="0 0 24 24" width="13" height="13" fill="none">
            <path d="M12 3C8 7.5 6 11 6 14.5A6 6 0 0 0 18 14.5C18 11 16 7.5 12 3z" stroke="#7fe3b8" stroke-width="1.6" stroke-linejoin="round"/>
            <path d="M12 8v9" stroke="#7fe3b8" stroke-width="1.4" stroke-linecap="round"/>
          </svg>
          <span>农田分析</span>
        </button>
        <button class="icon-btn" title="刷新直播" @click="load">
          <svg viewBox="0 0 24 24" width="13" height="13" fill="none">
            <path d="M20 12a8 8 0 1 1-2.34-5.66" stroke="#8fb3e8" stroke-width="1.8" stroke-linecap="round"/>
            <path d="M20 3v4h-4" stroke="#8fb3e8" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
        <button class="icon-btn" title="全屏" @click="toggleFull">
          <svg viewBox="0 0 24 24" width="13" height="13" fill="none">
            <path d="M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5" stroke="#8fb3e8" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
      </div>
    </div>

    <div class="video-box">
      <div :id="playerId" class="player-host"></div>

      <div v-if="loading" class="overlay">
        <i class="ring"></i>
        <span>{{ statusText }}</span>
        <span class="overlay-tip">低功耗电池相机休眠唤醒需要数秒,请稍候</span>
      </div>

      <div v-else-if="errorMsg" class="overlay">
        <svg viewBox="0 0 24 24" width="40" height="40" fill="none">
          <rect x="2" y="7" width="13" height="10" rx="2" stroke="#4d6a99" stroke-width="1.5"/>
          <path d="m15 10 6-3v10l-6-3" stroke="#4d6a99" stroke-width="1.5" stroke-linejoin="round"/>
          <path d="m4 5 16 14" stroke="#e86a6a" stroke-width="1.8" stroke-linecap="round"/>
        </svg>
        <span class="err-text">{{ errorMsg }}</span>
        <button class="btn primary" @click="load">重新连接</button>
      </div>

      <FarmAgent ref="farmRef" :capture-frame="captureFrame" :device-name="info.deviceName || ''" />
    </div>
  </section>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { EZUIKitPlayer } from 'ezuikit-js'
import { getVideoLive } from '../api'
import FarmAgent from './FarmAgent.vue'

const playerId = 'ezuikit-' + Math.random().toString(36).slice(2, 9)
const info = ref({})
const loading = ref(true)
const playing = ref(false)
const errorMsg = ref('')
const statusText = ref('正在连接摄像头…')

let player = null
let watchdog = null

const farmRef = ref(null)

function openFarm() {
  if (farmRef.value) farmRef.value.open()
}

/** 抓取当前播放画面,返回 base64 data URL;失败/未播放时返回空串 */
function captureFrame() {
  return new Promise((resolve) => {
    if (!player || !playing.value) {
      resolve('')
      return
    }
    let done = false
    const finish = (v) => {
      if (done) return
      done = true
      resolve(v || '')
    }
    try {
      player.capturePicture('default', (data) => finish(toDataUrl(data)))
    } catch (e) {
      finish('')
    }
    // 解码库抓图偶有延迟,超时兜底
    setTimeout(() => finish(''), 6000)
  })
}

function toDataUrl(data) {
  let raw = ''
  if (typeof data === 'string') raw = data
  else if (data && typeof data.base64 === 'string') raw = data.base64
  if (!raw) return ''
  return raw.startsWith('data:') ? raw : 'data:image/jpeg;base64,' + raw
}

function clearWatchdog() {
  clearTimeout(watchdog)
  watchdog = null
}

function destroyPlayer() {
  clearWatchdog()
  if (player) {
    try { player.stop() } catch (e) { /* noop */ }
    try { if (typeof player.destroy === 'function') player.destroy() } catch (e) { /* noop */ }
    player = null
  }
  const host = document.getElementById(playerId)
  if (host) host.innerHTML = ''
}

/** 把 EZUIKit 的错误对象转成可读文案 */
function describeError(err) {
  const code = err && err.data && err.data.nErrorCode
  const map = {
    5: '设备加密密码错误',
    5507: '该设备为 4G 无限流量套餐,仅支持萤石云 App 播放,请在萤石控制台更换套餐',
    9049: '账号套餐带宽受限(试用版仅 1M),请升级企业版套餐',
    5405: '设备网络较差或信令超时,请检查设备网络后重试'
  }
  if (code && map[code]) return map[code] + `（错误码 ${code}）`
  if (code) return `播放失败，错误码 ${code}`
  return '播放失败,请稍后重试'
}

function fail(msg) {
  loading.value = false
  playing.value = false
  errorMsg.value = msg
}

async function load() {
  destroyPlayer()
  loading.value = true
  playing.value = false
  errorMsg.value = ''
  statusText.value = '正在连接摄像头…'

  try {
    info.value = await getVideoLive()
  } catch (e) {
    fail((e && e.mess) || '获取直播地址失败,请稍后重试')
    return
  }
  if (!info.value.accessToken || !info.value.ezopenUrl) {
    fail('直播参数缺失,请检查后端萤石配置')
    return
  }
  createPlayer()
}

function createPlayer() {
  const host = document.getElementById(playerId)
  if (!host) {
    fail('播放器容器未就绪')
    return
  }
  const width = host.clientWidth || 800
  const height = host.clientHeight || 450

  // 超时保护:30s 未出画面判定为设备无响应
  watchdog = setTimeout(() => {
    if (!playing.value && !errorMsg.value) {
      fail('设备无响应,可能处于休眠状态,请稍后重试')
    }
  }, 30000)

  try {
    player = new EZUIKitPlayer({
      id: playerId,
      accessToken: info.value.accessToken,
      url: info.value.ezopenUrl,
      width,
      height,
      staticPath: '/ezuikit_static',
      template: 'pcLive',
      handleSuccess: () => {
        clearWatchdog()
        loading.value = false
        playing.value = true
      },
      handleError: (err) => fail(describeError(err))
    })
  } catch (e) {
    fail('播放器初始化失败')
  }
}

function toggleFull() {
  if (player) player.fullScreen()
}

onMounted(load)
onUnmounted(destroyPlayer)
</script>

<style scoped>
.video-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid #12406e;
  background: rgba(7, 20, 48, 0.6);
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 12px;
}
.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: #d7e9ff;
}
.head-right {
  display: flex;
  align-items: center;
  gap: 14px;
}
.meta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #8fb3e8;
}
.live-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #4d6a99;
}
.live-badge i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #4d6a99;
}
.live-badge.on {
  color: #2fe08a;
}
.live-badge.on i {
  background: #2fe08a;
  box-shadow: 0 0 6px rgba(47, 224, 138, 0.9);
  animation: blink 1.2s ease-in-out infinite;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}
.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: 1px solid #1d4670;
  border-radius: 3px;
  background: #0b2048;
  transition: background 0.2s, border-color 0.2s;
}
.icon-btn:hover {
  background: #123368;
  border-color: #2a6bb0;
}
.analyze-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 24px;
  padding: 0 12px;
  border-radius: 12px;
  font-size: 12px;
  color: #7fe3b8;
  border: 1px solid rgba(63, 210, 160, 0.45);
  background: rgba(20, 80, 60, 0.35);
  transition: background 0.2s, border-color 0.2s, box-shadow 0.2s;
}
.analyze-btn:hover {
  background: rgba(30, 120, 90, 0.5);
  border-color: rgba(127, 227, 184, 0.85);
  box-shadow: 0 0 10px rgba(47, 224, 138, 0.35);
}

.video-box {
  position: relative;
  flex: 1;
  min-height: 0;
  margin: 0 12px 12px;
  background: #01040e;
  border: 1px solid #0e2c52;
  display: flex;
}
.player-host {
  flex: 1;
  min-height: 0;
  width: 100%;
  height: 100%;
}
.overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: rgba(1, 4, 14, 0.88);
  color: #6fa8d9;
  font-size: 13px;
  z-index: 5;
}
.err-text {
  max-width: 70%;
  text-align: center;
  line-height: 1.6;
}
.overlay-tip {
  font-size: 11px;
  color: #3d5a85;
}
.ring {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 3px solid rgba(63, 169, 245, 0.25);
  border-top-color: #3fc5ff;
  animation: spin 0.9s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.btn.primary {
  height: 30px;
  padding: 0 18px;
  border-radius: 3px;
  font-size: 13px;
  background: linear-gradient(180deg, #1a7fd2, #0f56a4);
  border: 1px solid #3fa9f5;
  color: #fff;
}
</style>
