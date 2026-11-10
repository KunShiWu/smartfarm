<template>
  <transition name="farm-fade">
    <div v-if="open" class="farm-drawer">
      <div class="dr-head">
        <span class="dr-title">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none">
            <path d="M12 3C8 7.5 6 11 6 14.5A6 6 0 0 0 18 14.5C18 11 16 7.5 12 3z" stroke="#7fe3b8" stroke-width="1.6" stroke-linejoin="round"/>
            <path d="M12 8v9" stroke="#7fe3b8" stroke-width="1.4" stroke-linecap="round"/>
          </svg>
          农田分析助手
        </span>
        <div class="dr-actions">
          <button class="mini" :disabled="loading" @click="run">重新分析</button>
          <button class="mini ghost" @click="close">关闭</button>
        </div>
      </div>

      <div class="dr-body">
        <div class="shot-row">
          <img v-if="shotUrl" class="shot" :src="shotUrl" alt="当前画面截图" />
          <div v-else class="shot empty">本次未获取到画面</div>
          <div class="shot-info">
            <p v-if="deviceName">监控点位:{{ deviceName }}</p>
            <p v-if="captureTime">截图时间:{{ captureTime }}</p>
            <p v-if="!deviceName && !captureTime" class="dim">实时画面</p>
          </div>
        </div>

        <div v-if="loading" class="state">
          <i class="ring"></i>
          <span>{{ stepText }}</span>
          <span class="wait">
            已等待 {{ elapsed }}s<template v-if="elapsed >= 60">,模型思考较久,请再稍候</template>
          </span>
          <span class="wait dim">整个过程通常需要 20~60 秒</span>
        </div>

        <div v-else-if="error" class="state err">
          <span class="err-text">{{ error }}</span>
          <button class="mini" @click="run">重试</button>
        </div>

        <template v-else-if="result">
          <div class="answer">{{ result.answer }}</div>
          <details v-if="result.observation" class="obs">
            <summary>查看画面识别依据</summary>
            <p>{{ result.observation }}</p>
          </details>
          <p class="foot">
            推理:{{ result.reasonModel }}
            <template v-if="result.usedImage"> · 视觉:{{ result.visionModel }}</template>
            · 耗时 {{ ((result.elapsedMs || 0) / 1000).toFixed(1) }}s
          </p>
        </template>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { ref, computed } from 'vue'
import { farmAnalyze, getHostList, getRealtimeParams } from '../api'

const props = defineProps({
  /** 抓取当前画面的函数,返回 Promise<base64 data URL | ''> */
  captureFrame: { type: Function, default: null },
  deviceName: { type: String, default: '' }
})

const open = ref(false)
const loading = ref(false)
const error = ref('')
const result = ref(null)
const shotUrl = ref('')
const captureTime = ref('')
const elapsed = ref(0)

/** 按已等待秒数推进阶段文案(避免无反馈看起来像卡死) */
const stepText = computed(() => {
  const s = elapsed.value
  if (s < 5) return '正在抓取当前画面…'
  if (s < 22) return '视觉模型识别作物长势与虫害…'
  return 'deepseek-v4-pro 生成农事建议…'
})

let ticker = null

function pad(n) {
  return String(n).padStart(2, '0')
}

function now() {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function openDrawer() {
  open.value = true
  if (!result.value && !loading.value) run()
}

function close() {
  open.value = false
}

/** 拉取当前环境/土壤参数(取第一台真实主机) */
async function collectParams() {
  try {
    const hosts = (await getHostList()) || []
    const host = hosts.find((h) => h.type !== 'camera')
    if (!host) return null
    const detail = await getRealtimeParams(host.id)
    if (!detail || !detail.params) return null
    return detail.params.map((p) => ({ name: p.name, unit: p.unit, value: p.value }))
  } catch (e) {
    return null
  }
}

async function run() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  result.value = null
  shotUrl.value = ''
  captureTime.value = ''
  elapsed.value = 0

  ticker = setInterval(() => {
    elapsed.value += 1
  }, 1000)

  try {
    if (props.captureFrame) {
      const data = await props.captureFrame()
      if (data) {
        shotUrl.value = data
        captureTime.value = now()
      }
    }
    const payload = {
      imageBase64: shotUrl.value || null,
      deviceName: props.deviceName || null,
      captureTime: captureTime.value || null,
      params: await collectParams()
    }
    const res = await farmAnalyze(payload)
    if (!res || !res.answer) {
      throw new Error('模型未返回分析内容,请重试')
    }
    result.value = res
  } catch (e) {
    error.value = (e && (e.mess || e.message)) || '分析失败,请稍后重试'
  } finally {
    clearInterval(ticker)
    ticker = null
    loading.value = false
  }
}

defineExpose({ open: openDrawer, close })
</script>

<style scoped>
.farm-drawer {
  position: absolute;
  inset: 0;
  z-index: 8;
  display: flex;
  flex-direction: column;
  background: rgba(4, 11, 26, 0.95);
  border: 1px solid #16506e;
}

.dr-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid #12406e;
  background: linear-gradient(90deg, rgba(16, 60, 70, 0.6), transparent);
}
.dr-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #cdeee0;
}
.dr-actions {
  display: flex;
  gap: 8px;
}
.mini {
  height: 24px;
  padding: 0 10px;
  border-radius: 12px;
  font-size: 12px;
  color: #7fe3b8;
  border: 1px solid rgba(63, 210, 160, 0.45);
  background: rgba(20, 80, 60, 0.35);
  transition: background 0.2s, border-color 0.2s;
}
.mini:hover:not(:disabled) {
  background: rgba(30, 120, 90, 0.5);
  border-color: rgba(127, 227, 184, 0.85);
}
.mini:disabled {
  opacity: 0.5;
}
.mini.ghost {
  color: #8fb3e8;
  border-color: #1d4670;
  background: #0b2048;
}
.mini.ghost:hover {
  background: #123368;
}

.dr-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.shot-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.shot {
  width: 210px;
  height: 118px;
  flex: none;
  object-fit: cover;
  border: 1px solid #1d4670;
  background: #01040e;
}
.shot.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #4d6a99;
}
.shot-info p {
  font-size: 12px;
  color: #8fb3e8;
  line-height: 1.9;
}
.shot-info .dim {
  color: #4d6a99;
}

.state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #6fa8d9;
  font-size: 13px;
  min-height: 120px;
}
.state.err {
  color: #e8a0a0;
}
.wait {
  font-size: 12px;
  color: #6fa8d9;
}
.wait.dim {
  color: #4d6a99;
  font-size: 11px;
}
.err-text {
  max-width: 80%;
  text-align: center;
  line-height: 1.6;
}
.ring {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 3px solid rgba(63, 169, 245, 0.25);
  border-top-color: #7fe3b8;
  animation: spin 0.9s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

.answer {
  white-space: pre-wrap;
  line-height: 1.85;
  font-size: 13px;
  color: #d7e9ff;
  border: 1px solid #12406e;
  background: rgba(7, 20, 48, 0.7);
  padding: 12px 14px;
  border-radius: 3px;
}
.obs {
  border: 1px dashed #1d4670;
  border-radius: 3px;
  padding: 8px 12px;
  font-size: 12px;
  color: #8fb3e8;
}
.obs summary {
  cursor: pointer;
  color: #7fe3b8;
  outline: none;
}
.obs p {
  margin-top: 8px;
  white-space: pre-wrap;
  line-height: 1.7;
}
.foot {
  font-size: 11px;
  color: #4d6a99;
}

.farm-fade-enter-active,
.farm-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.farm-fade-enter-from,
.farm-fade-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
