<template>
  <section class="param-section" v-if="detail">
    <!-- 采集信息栏 -->
    <div class="info-bar">
      <div class="info-left">
        <span class="info-item">采集时间：<b>{{ detail.collectTime }}</b></span>
        <span class="info-item">
          采集间隔：<b>{{ detail.interval }}分钟</b>
          <button class="link" @click="openInterval">修改</button>
        </span>
        <span class="info-item battery">
          <svg viewBox="0 0 24 24" width="15" height="15" fill="none">
            <rect x="2" y="7" width="17" height="10" rx="2" stroke="#3fd2a0" stroke-width="1.6"/>
            <rect x="4.5" y="9.5" width="9" height="5" rx="1" fill="#3fd2a0"/>
            <path d="M21 10v4" stroke="#3fd2a0" stroke-width="1.8" stroke-linecap="round"/>
          </svg>
          电池电压：<b>{{ detail.battery }}V</b>
        </span>
        <span class="info-item">
          设备状态：
          <i class="st-dot" :class="detail.online ? 'online' : 'offline'"></i>
          <b :class="detail.online ? 'st-online' : 'st-offline'">{{ detail.online ? '在线' : '离线' }}</b>
        </span>
      </div>
      <div class="info-right">
        <img class="device-img" :src="deviceImg" alt="设备图片" />
        <button class="link" @click="openRecords">修改记录</button>
        <button class="link" @click="openSettings">主机设置</button>
      </div>
    </div>

    <!-- 参数卡片 -->
    <div class="card-grid">
      <div
        v-for="p in detail.params"
        :key="p.key"
        class="card"
        :class="{ active: activeParam === p.key }"
        @click="$emit('select-param', p.key)"
      >
        <i class="corner tl"></i>
        <i class="corner tr"></i>
        <i class="corner bl"></i>
        <i class="corner br"></i>
        <span class="card-dots">⋮</span>

        <div class="card-icon-zone">
          <svg viewBox="0 0 24 24" width="26" height="26" fill="none" class="card-icon">
            <g stroke="#eaf6ff" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" v-html="iconOf(p.key)"></g>
          </svg>
          <svg viewBox="0 0 48 22" width="42" height="20" class="pedestal">
            <ellipse class="ped-glow" cx="24" cy="12" rx="17" ry="6" fill="rgba(63, 210, 160, 0.45)"/>
            <polygon points="24,4 44,11 24,18 4,11" fill="rgba(63, 210, 160, 0.2)" stroke="#3fd2a0" stroke-width="1.1"/>
            <polygon points="24,7.5 34,11 24,14.5 14,11" fill="rgba(120, 255, 205, 0.35)"/>
          </svg>
        </div>

        <div class="card-text">
          <span class="card-name">{{ p.name }}</span>
          <div class="card-value">
            <b :key="p.value" class="val">{{ p.value }}</b>
            <span v-if="p.unit" class="card-unit">{{ p.unit }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 修改采集间隔 -->
    <BaseDialog v-model="showInterval" title="修改采集间隔" width="380px">
      <div class="form-row">
        <label>采集间隔</label>
        <input v-model.number="intervalForm" type="number" min="1" class="form-input" />
        <span class="form-unit">分钟</span>
      </div>
      <template #footer>
        <button class="btn ghost" @click="showInterval = false">取消</button>
        <button class="btn primary" @click="saveInterval">确定</button>
      </template>
    </BaseDialog>

    <!-- 修改记录 -->
    <BaseDialog v-model="showRecords" title="修改记录" width="560px">
      <table class="record-table">
        <thead>
          <tr><th>时间</th><th>修改项</th><th>修改前</th><th>修改后</th><th>操作人</th></tr>
        </thead>
        <tbody>
          <tr v-for="(r, i) in records" :key="i">
            <td>{{ r.time }}</td><td>{{ r.field }}</td><td>{{ r.before }}</td><td>{{ r.after }}</td><td>{{ r.operator }}</td>
          </tr>
          <tr v-if="!records.length"><td colspan="5" class="empty">暂无记录</td></tr>
        </tbody>
      </table>
    </BaseDialog>

    <!-- 主机设置 -->
    <BaseDialog v-model="showSettings" title="主机设置" width="420px">
      <div class="form-row">
        <label>主机名称</label>
        <input v-model.trim="settingsForm.name" type="text" class="form-input" />
      </div>
      <div class="form-row">
        <label>所属分组</label>
        <input :value="detail.group" type="text" class="form-input" disabled />
      </div>
      <template #footer>
        <button class="btn ghost" @click="showSettings = false">取消</button>
        <button class="btn primary" @click="saveSettings">保存</button>
      </template>
    </BaseDialog>
  </section>
</template>

<script setup>
import { ref, reactive } from 'vue'
import BaseDialog from './BaseDialog.vue'
import { updateInterval, getModifyRecords, updateHostSettings } from '../api'

const props = defineProps({
  detail: { type: Object, default: null },
  activeParam: { type: String, default: 'temp' }
})
const emit = defineEmits(['select-param', 'updated'])

const deviceImg = '/device.jpg'

// 参数图标(白色线性,均带土壤基线)
const ICONS = {
  // 温度计
  temp: '<path d="M10 3.5a2 2 0 0 1 4 0v7.6a4.6 4.6 0 1 1-4 0z"/><path d="M12 8.5v5"/><circle cx="12" cy="15.6" r="1.2" fill="#eaf6ff" stroke="none"/><path d="M4 20.5h16" stroke-opacity=".55"/>',
  // 水滴
  humidity: '<path d="M12 3c-3.6 4.2-5.6 7.3-5.6 10a5.6 5.6 0 0 0 11.2 0c0-2.7-2-5.8-5.6-10z"/><path d="M9.8 13a2.4 2.4 0 0 0 2 2.4"/><path d="M4 20.5h16" stroke-opacity=".55"/>',
  // 闪电(电导率)
  ec: '<path d="M13 3 6.5 13H11l-1.2 7L17 10h-4.4z"/><path d="M4 20.5h16" stroke-opacity=".55"/>',
  // 晶粒(盐分)
  salt: '<path d="m8 6.5 2 2-2 2-2-2z"/><path d="m14 4.5 2 2-2 2-2-2z"/><path d="m11 10.5 2 2-2 2-2-2z"/><path d="M4 20.5h16" stroke-opacity=".55"/>',
  // 叶片(氮)
  nitrogen: '<path d="M5.5 18.5C5.5 11 10.5 5.5 18.5 5.5c0 8-5 13-13 13z"/><path d="M5.5 18.5c2.6-2.8 5.4-5.6 8.5-8.5"/>',
  // 烧瓶(磷)
  phosphorus: '<path d="M10 3h4"/><path d="M11 3v4.6L6.8 15a2.4 2.4 0 0 0 2.2 3.4h6a2.4 2.4 0 0 0 2.2-3.4L13 7.6V3"/><path d="M8.6 13h6.8"/><circle cx="11" cy="15.8" r=".9" fill="#eaf6ff" stroke="none"/>',
  // 原子(钾)
  potassium: '<circle cx="12" cy="11.5" r="1.4" fill="#eaf6ff" stroke="none"/><ellipse cx="12" cy="11.5" rx="7.5" ry="3"/><ellipse cx="12" cy="11.5" rx="7.5" ry="3" transform="rotate(64 12 11.5)"/>',
  // 试管(pH)
  ph: '<path d="M9 3h6"/><path d="M10 3v5.2l-2.2 8A2.6 2.6 0 0 0 10.3 20h3.4a2.6 2.6 0 0 0 2.5-3.8l-2.2-8V3"/><path d="M8.7 13.5h6.6"/>'
}
const FALLBACK_ICON = '<circle cx="12" cy="10" r="5.5"/><path d="M12 7v3l2 2"/><path d="M4 20.5h16" stroke-opacity=".55"/>'
function iconOf(key) {
  return ICONS[key] || FALLBACK_ICON
}

// 修改采集间隔
const showInterval = ref(false)
const intervalForm = ref(1)
function openInterval() {
  intervalForm.value = props.detail.interval
  showInterval.value = true
}
async function saveInterval() {
  if (!intervalForm.value || intervalForm.value < 1) return
  await updateInterval(props.detail.id, intervalForm.value)
  showInterval.value = false
  emit('updated')
}

// 修改记录
const showRecords = ref(false)
const records = ref([])
async function openRecords() {
  showRecords.value = true
  records.value = await getModifyRecords(props.detail.id)
}

// 主机设置
const showSettings = ref(false)
const settingsForm = reactive({ name: '' })
function openSettings() {
  settingsForm.name = props.detail.name
  showSettings.value = true
}
async function saveSettings() {
  if (!settingsForm.name) return
  await updateHostSettings(props.detail.id, { name: settingsForm.name })
  showSettings.value = false
  emit('updated')
}
</script>

<style scoped>
.param-section {
  flex: none;
}

.info-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px;
  border: 1px solid #12406e;
  background: rgba(7, 20, 48, 0.6);
  margin-bottom: 8px;
}
.info-left {
  display: flex;
  align-items: center;
  gap: 26px;
}
.info-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #7d9cc9;
}
.info-item b {
  color: #d7e9ff;
  font-weight: 600;
}
.link {
  color: #3fa9f5;
  font-size: 12px;
  margin-left: 6px;
}
.link:hover {
  text-decoration: underline;
}
.st-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}
.st-dot.online {
  background: #2fe08a;
  box-shadow: 0 0 6px rgba(47, 224, 138, 0.9);
}
.st-dot.offline {
  background: #f0a23c;
  box-shadow: 0 0 6px rgba(240, 162, 60, 0.9);
}
.st-online {
  color: #2fe08a !important;
}
.st-offline {
  color: #f0a23c !important;
}
.info-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.device-img {
  width: 44px;
  height: 44px;
  object-fit: cover;
  border: 1px solid #1d6bb0;
  border-radius: 2px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
}
.card {
  position: relative;
  height: 92px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  cursor: pointer;
  border-radius: 6px;
  border: 1px solid rgba(63, 169, 245, 0.3);
  background: linear-gradient(155deg, rgba(17, 58, 116, 0.85) 0%, rgba(8, 24, 56, 0.92) 100%);
  transition: transform 0.25s, border-color 0.25s, box-shadow 0.25s;
}
.card:hover {
  transform: translateY(-2px);
  border-color: rgba(99, 205, 255, 0.75);
  box-shadow: 0 4px 16px rgba(20, 120, 220, 0.35);
}
.card.active {
  border-color: rgba(126, 230, 255, 0.95);
  box-shadow:
    0 0 16px rgba(46, 180, 255, 0.45),
    inset 0 0 14px rgba(80, 190, 255, 0.18);
}

/* 四角亮线 */
.corner {
  position: absolute;
  width: 8px;
  height: 8px;
  border-color: rgba(126, 230, 255, 0.85);
  border-style: solid;
  border-width: 0;
  pointer-events: none;
}
.corner.tl { top: -1px; left: -1px; border-top-width: 1.6px; border-left-width: 1.6px; border-top-left-radius: 5px; }
.corner.tr { top: -1px; right: -1px; border-top-width: 1.6px; border-right-width: 1.6px; border-top-right-radius: 5px; }
.corner.bl { bottom: -1px; left: -1px; border-bottom-width: 1.6px; border-left-width: 1.6px; border-bottom-left-radius: 5px; }
.corner.br { bottom: -1px; right: -1px; border-bottom-width: 1.6px; border-right-width: 1.6px; border-bottom-right-radius: 5px; }

.card-dots {
  position: absolute;
  top: 5px;
  right: 9px;
  font-size: 12px;
  line-height: 1;
  color: rgba(126, 200, 255, 0.5);
  letter-spacing: 1px;
}

/* 图标 + 发光底座 */
.card-icon-zone {
  flex: none;
  width: 52px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
}
.card-icon {
  filter: drop-shadow(0 0 4px rgba(160, 220, 255, 0.6));
}
.pedestal {
  margin-top: -2px;
}
.ped-glow {
  animation: ped-pulse 2.4s ease-in-out infinite;
}
@keyframes ped-pulse {
  0%, 100% { opacity: 0.35; }
  50% { opacity: 0.9; }
}

.card-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.card-name {
  font-size: 13px;
  color: #8fd8e8;
  white-space: nowrap;
}
.card-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.card-value .val {
  font-size: 24px;
  font-weight: 700;
  color: #2fe8d8;
  text-shadow: 0 0 12px rgba(47, 232, 216, 0.45);
  font-variant-numeric: tabular-nums;
  animation: val-in 0.45s ease-out;
}
@keyframes val-in {
  from { opacity: 0; transform: translateY(7px); }
  to { opacity: 1; transform: translateY(0); }
}
.card-unit {
  font-size: 11px;
  color: #6fa8c9;
}

/* 表单 / 弹窗 */
.form-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.form-row label {
  width: 64px;
  flex: none;
  color: #9cc0ee;
}
.form-input {
  flex: 1;
  height: 30px;
  padding: 0 10px;
  border: 1px solid #1d4670;
  border-radius: 3px;
  background: #081a3c;
  color: #d7e9ff;
  font-size: 13px;
}
.form-input:disabled {
  color: #4d6a99;
}
.form-unit {
  color: #7d9cc9;
}
.btn {
  height: 30px;
  padding: 0 18px;
  border-radius: 3px;
  font-size: 13px;
}
.btn.primary {
  background: linear-gradient(180deg, #1a7fd2, #0f56a4);
  border: 1px solid #3fa9f5;
  color: #fff;
}
.btn.ghost {
  border: 1px solid #1d4670;
  color: #9cc0ee;
  background: transparent;
}
.record-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.record-table th,
.record-table td {
  padding: 7px 8px;
  border-bottom: 1px solid #14335f;
  text-align: left;
  color: #b9d2f5;
  white-space: nowrap;
}
.record-table th {
  color: #6f92c4;
  font-weight: 500;
  background: rgba(18, 64, 120, 0.3);
}
.record-table .empty {
  text-align: center;
  color: #4d6a99;
}
</style>
