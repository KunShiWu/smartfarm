<template>
  <section class="irrigation">
    <div class="irr-head">
      <div class="left">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none">
          <path d="M12 3C8 7.5 6 11 6 14.5A6 6 0 0 0 18 14.5C18 11 16 7.5 12 3z" stroke="#3fd2a0" stroke-width="1.6" stroke-linejoin="round"/>
          <path d="M12 8v9" stroke="#3fd2a0" stroke-width="1.4" stroke-linecap="round"/>
        </svg>
        <span class="title">喷灌设备</span>
        <i class="dot" :class="state.online ? 'online' : 'offline'"></i>
        <span class="sub">{{ state.online ? '桥接服务在线' : '桥接服务离线' }}</span>
      </div>
      <div class="right">
        <span class="mode-tag" :class="state.mode">{{ state.mode === 'auto' ? '自动模式' : '手动模式' }}</span>
        <span class="sub">更新于 {{ timeText(state.updatedAt) }}</span>
      </div>
    </div>

    <div v-if="!state.bridgeConfigured" class="warn">
      未配置米家桥接服务 Token，暂时无法控制水泵开关。请在服务端设置环境变量
      <code>MIJIA_BRIDGE_TOKEN</code>（需与桥接服务一致）后重启。
    </div>

    <!-- 关键指标 -->
    <div class="cards">
      <div class="card">
        <span class="card-label">当前土壤湿度</span>
        <span class="card-value" :class="{ low: isDry }">
          {{ state.soilMoisture == null ? '--' : state.soilMoisture.toFixed(1) }}<i>%</i>
        </span>
        <span class="card-foot">{{ state.soilHostName || '土壤检测设备' }} · {{ timeText(state.soilCollectTime) }}</span>
      </div>
      <div class="card">
        <span class="card-label">湿度阈值</span>
        <span class="card-value">{{ fmtNum(state.threshold) }}<i>%</i></span>
        <span class="card-foot">{{ dryHint }}</span>
      </div>
      <div class="card">
        <span class="card-label">水泵状态</span>
        <span class="card-value" :class="state.on ? 'running' : ''">{{ state.on ? '喷灌中' : '已停止' }}</span>
        <span class="card-foot">{{ state.on ? '剩余 ' + remainText : '等待下一次判断' }}</span>
      </div>
      <div class="card">
        <span class="card-label">单次喷灌时长</span>
        <span class="card-value">{{ state.durationMinutes }}<i>分钟</i></span>
        <span class="card-foot">达到时长自动关闭</span>
      </div>
    </div>

    <!-- 开关 -->
    <div class="switch-row">
      <button class="switch-btn" :class="{ on: state.on }" :disabled="busy || !state.bridgeConfigured" @click="toggle">
        <span class="knob"></span>
        <span class="switch-text">{{ state.on ? '关闭喷灌' : '开启喷灌' }}</span>
      </button>
      <span class="hint">手动操作会切换到手动模式；如需自动控制请切回自动模式。</span>
    </div>

    <!-- 设置 -->
    <div class="settings">
      <div class="set-block">
        <span class="set-label">控制模式</span>
        <div class="seg">
          <span :class="{ active: state.mode === 'auto' }" @click="saveMode('auto')">自动</span>
          <span :class="{ active: state.mode === 'manual' }" @click="saveMode('manual')">手动</span>
        </div>
        <span class="set-tip">自动：湿度低于阈值自动开启，达到阈值自动关闭</span>
      </div>

      <div class="set-block">
        <span class="set-label">湿度阈值(%)</span>
        <div class="num-box">
          <input v-model.number="form.threshold" type="number" min="1" max="99" step="0.5" />
          <i>%</i>
        </div>
        <span class="set-tip">低于该值自动开启喷灌</span>
      </div>

      <div class="set-block">
        <span class="set-label">喷灌时长(分钟)</span>
        <div class="num-box">
          <input v-model.number="form.durationMinutes" type="number" min="1" max="180" step="1" />
          <i>min</i>
        </div>
        <span class="set-tip">单次喷灌到达该时长后自动关闭</span>
      </div>

      <button class="save-btn" :disabled="busy || !dirty" @click="saveConfig">保存设置</button>
    </div>

    <div class="last" :class="errMsg ? 'err' : ''">
      {{ errMsg || '最近动作：' + (state.lastAction ? state.lastAction + '（' + timeText(state.lastActionAt) + '）' : '暂无') }}
    </div>
  </section>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import { getIrrigation, switchIrrigation, updateIrrigationConfig } from '../api'

const state = ref({})
const errMsg = ref('')
const busy = ref(false)
const form = reactive({ threshold: 30, durationMinutes: 5 })
const remain = ref(null)

// 湿度直接取自传感器实时数据的「土壤湿度」通道,0 也是有效读数(极干),低于阈值即需喷灌
const isDry = computed(() => {
  const m = state.value.soilMoisture
  return m != null && state.value.threshold != null && m < state.value.threshold
})
const dryHint = computed(() => {
  if (state.value.soilMoisture == null) return '暂未获取到土壤湿度'
  return isDry.value ? '低于阈值,需喷灌' : '高于阈值,无需喷灌'
})
const dirty = computed(
  () => Number(form.threshold) !== Number(state.value.threshold) || Number(form.durationMinutes) !== Number(state.value.durationMinutes)
)

const remainText = computed(() => {
  const s = remain.value
  if (s == null) return '--'
  const m = Math.floor(s / 60)
  const sec = s % 60
  return `${m}:${String(sec).padStart(2, '0')}`
})

function fmtNum(v) {
  return v == null ? '--' : Number(v).toFixed(1)
}
function timeText(t) {
  return (t || '').length >= 16 ? t.slice(5, 16) : t || '--'
}

async function load() {
  try {
    const res = await getIrrigation()
    state.value = res || {}
    // 轮询成功后清除上一次的瞬时错误提示,避免错误一直挂在面板上
    errMsg.value = ''
    // 后端返回的配置回填到输入框(用户正在编辑时不覆盖)
    if (!dirty.value) {
      form.threshold = res.threshold
      form.durationMinutes = res.durationMinutes
    }
    remain.value = res.remainingSeconds == null ? null : res.remainingSeconds
  } catch (e) {
    errMsg.value = (e && e.mess) || '喷灌设备状态获取失败'
  }
}

async function toggle() {
  if (busy.value) return
  busy.value = true
  errMsg.value = ''
  try {
    state.value = await switchIrrigation(!state.value.on)
    remain.value = state.value.remainingSeconds
  } catch (e) {
    errMsg.value = (e && e.mess) || '操作失败,请稍后重试'
  } finally {
    busy.value = false
  }
}

async function saveMode(mode) {
  if (busy.value || state.value.mode === mode) return
  busy.value = true
  errMsg.value = ''
  try {
    state.value = await updateIrrigationConfig({ mode })
  } catch (e) {
    errMsg.value = (e && e.mess) || '切换模式失败'
  } finally {
    busy.value = false
  }
}

async function saveConfig() {
  if (busy.value) return
  busy.value = true
  errMsg.value = ''
  try {
    state.value = await updateIrrigationConfig({
      threshold: Number(form.threshold),
      durationMinutes: Number(form.durationMinutes)
    })
    form.threshold = state.value.threshold
    form.durationMinutes = state.value.durationMinutes
  } catch (e) {
    errMsg.value = (e && e.mess) || '保存失败,请稍后重试'
  } finally {
    busy.value = false
  }
}

// 剩余时间本地逐秒倒计时,每 10 秒与后端重新对齐
let pollTimer = null
let secTimer = null

onMounted(() => {
  form.threshold = state.value.threshold
  load()
  pollTimer = setInterval(load, 10000)
  secTimer = setInterval(() => {
    if (remain.value != null && remain.value > 0) remain.value -= 1
  }, 1000)
})

onUnmounted(() => {
  clearInterval(pollTimer)
  clearInterval(secTimer)
})

watch(
  () => state.value.threshold,
  (v) => {
    if (!dirty.value) form.threshold = v
  }
)
</script>

<style scoped>
.irrigation {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #12406e;
  background: rgba(7, 20, 48, 0.6);
  border-radius: 4px;
  overflow: auto;
}

.irr-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #12406e;
  padding-bottom: 10px;
}
.irr-head .left,
.irr-head .right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.title {
  font-size: 15px;
  font-weight: 600;
  color: #d7e9ff;
}
.sub {
  font-size: 12px;
  color: #6d8db8;
}
.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}
.dot.online {
  background: #2fe08a;
  box-shadow: 0 0 6px rgba(47, 224, 138, 0.8);
}
.dot.offline {
  background: #f0a23c;
  box-shadow: 0 0 6px rgba(240, 162, 60, 0.8);
}
.mode-tag {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  border: 1px solid rgba(63, 169, 245, 0.5);
  color: #7ee6ff;
}
.mode-tag.manual {
  border-color: rgba(240, 162, 60, 0.6);
  color: #f0b56a;
}

.warn {
  font-size: 12px;
  line-height: 1.7;
  color: #f0c078;
  border: 1px solid rgba(240, 162, 60, 0.45);
  background: rgba(80, 50, 10, 0.35);
  padding: 8px 10px;
  border-radius: 3px;
}
.warn code {
  color: #ffe0a8;
}

.cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  border: 1px solid #14406e;
  background: linear-gradient(180deg, rgba(14, 40, 82, 0.75), rgba(8, 22, 50, 0.75));
  border-radius: 3px;
}
.card-label {
  font-size: 12px;
  color: #7d9cc9;
}
.card-value {
  font-size: 24px;
  font-weight: 600;
  color: #eaf4ff;
  font-variant-numeric: tabular-nums;
}
.card-value i {
  font-size: 12px;
  font-style: normal;
  color: #7d9cc9;
  margin-left: 3px;
}
.card-value.low {
  color: #ff9c6b;
}
.card-value.running {
  color: #3fd2a0;
}
.card-foot {
  font-size: 11px;
  color: #5d7fc0;
}

.switch-row {
  display: flex;
  align-items: center;
  gap: 14px;
}
.switch-btn {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 18px 8px 10px;
  border-radius: 22px;
  border: 1px solid rgba(63, 169, 245, 0.5);
  background: linear-gradient(180deg, rgba(16, 46, 96, 0.9), rgba(8, 24, 56, 0.9));
  color: #b9d2f5;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.25s;
}
.switch-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.knob {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #5d7fc0;
  box-shadow: inset 0 0 4px rgba(0, 0, 0, 0.5);
  transition: all 0.25s;
}
.switch-btn.on {
  border-color: rgba(63, 210, 160, 0.85);
  color: #eafff7;
  box-shadow: 0 0 12px rgba(47, 224, 138, 0.35);
}
.switch-btn.on .knob {
  background: #2fe08a;
  box-shadow: 0 0 8px rgba(47, 224, 138, 0.9);
}
.hint {
  font-size: 12px;
  color: #5d7fc0;
}

.settings {
  display: grid;
  grid-template-columns: 1.2fr 1fr 1fr auto;
  align-items: end;
  gap: 12px;
  border-top: 1px solid #12406e;
  padding-top: 12px;
}
.set-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.set-label {
  font-size: 12px;
  color: #7d9cc9;
}
.set-tip {
  font-size: 11px;
  color: #4d6a99;
}
.seg {
  display: flex;
  border: 1px solid #1d4670;
  border-radius: 3px;
  overflow: hidden;
}
.seg span {
  flex: 1;
  text-align: center;
  padding: 6px 0;
  font-size: 12px;
  color: #7d9cc9;
  cursor: pointer;
  transition: all 0.2s;
}
.seg span.active {
  background: rgba(28, 116, 200, 0.45);
  color: #eaf4ff;
}
.num-box {
  display: flex;
  align-items: center;
  border: 1px solid #1d4670;
  border-radius: 3px;
  background: #081a3c;
  padding: 0 8px;
  transition: border-color 0.2s;
}
.num-box:focus-within {
  border-color: rgba(99, 205, 255, 0.7);
}
.num-box input {
  width: 100%;
  height: 30px;
  background: transparent;
  border: none;
  color: #d7e9ff;
  font-size: 13px;
}
.num-box i {
  font-size: 11px;
  font-style: normal;
  color: #5d7fc0;
}
.save-btn {
  height: 32px;
  padding: 0 18px;
  border-radius: 3px;
  border: 1px solid rgba(63, 169, 245, 0.7);
  background: linear-gradient(180deg, rgba(23, 110, 190, 0.95), rgba(12, 62, 128, 0.95));
  color: #eaf4ff;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.save-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.last {
  margin-top: auto;
  font-size: 12px;
  color: #6d8db8;
  border-top: 1px solid #12406e;
  padding-top: 10px;
}
.last.err {
  color: #e8a0a0;
}
</style>
