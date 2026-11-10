<template>
  <div
    ref="wrapRef"
    class="home-map"
    :class="{ dragging }"
    @mousedown="onDown"
    @wheel.prevent="onWheel"
  >
    <!-- 地图瓦片 -->
    <div class="tile-layer">
      <img
        v-for="t in tiles"
        :key="t.key"
        class="tile"
        :src="t.url"
        :style="{ transform: `translate3d(${t.x}px, ${t.y}px, 0)` }"
        draggable="false"
        alt=""
      />
    </div>

    <!-- 暗角与青色氛围 -->
    <div class="map-veil"></div>

    <!-- 基地定位点 -->
    <div class="base-point" :style="{ left: `${base.x}px`, top: `${base.y}px` }">
      <i class="ring"></i>
      <i class="core"></i>
      <span class="base-label">茂林学村</span>
    </div>

    <!-- 设备标记 -->
    <button
      v-for="(m, i) in markers"
      :key="m.id"
      class="pin-wrap"
      :class="`side-${m.side}`"
      :style="{ left: `${m.x}px`, top: `${m.y}px` }"
      @click.stop="$emit('select', m.raw)"
      @mousedown.stop
    >
      <span class="pin" :class="[m.type, { off: !m.online, active: m.id === selectedHostId }]">{{ i + 1 }}</span>
      <span class="pin-label">{{ m.name }}</span>
    </button>

    <!-- 缩放与复位 -->
    <div class="map-ctrl" @mousedown.stop @click.stop>
      <button title="放大" @click="setZoom(zoom + 1)">+</button>
      <button title="缩小" @click="setZoom(zoom - 1)">−</button>
      <button title="回到基地" class="reset" @click="resetView">◎</button>
    </div>

    <!-- 图例 -->
    <div class="map-legend">
      <span><i class="lg soil"></i>土壤检测</span>
      <span><i class="lg camera"></i>视频监控</span>
      <span><i class="lg irrigation"></i>喷灌设备</span>
    </div>

    <div class="map-scale">缩放级别 {{ zoom }} · 数据来源 高德地图</div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  hosts: { type: Array, default: () => [] },
  selectedHostId: { type: String, default: '' }
})
defineEmits(['select'])

const TILE_SIZE = 256
const MIN_ZOOM = 11
const MAX_ZOOM = 17
// 基地位置：厦门市翔安区新店街道茂林学村一带
const BASE = { lat: 24.62, lng: 118.246 }

const wrapRef = ref(null)
const zoom = ref(14)
const center = reactive({ lat: BASE.lat, lng: BASE.lng })
const size = reactive({ w: 0, h: 0 })
const tiles = ref([])
const dragging = ref(false)

const clamp = (v, min, max) => Math.min(max, Math.max(min, v))

/** 经纬度 → 世界像素坐标 */
function project(lat, lng, z) {
  const world = TILE_SIZE * 2 ** z
  const rad = (lat * Math.PI) / 180
  return {
    x: ((lng + 180) / 360) * world,
    y: ((1 - Math.log(Math.tan(rad) + 1 / Math.cos(rad)) / Math.PI) / 2) * world
  }
}

/** 世界像素坐标 → 经纬度 */
function unproject(x, y, z) {
  const world = TILE_SIZE * 2 ** z
  return {
    lng: (x / world) * 360 - 180,
    lat: (Math.atan(Math.sinh(Math.PI - (2 * Math.PI * y) / world)) * 180) / Math.PI
  }
}

// 高德深色大屏底图（style=8），按 x+y 分散到 4 个子域
const tileUrl = (x, y, z) =>
  `https://webrd0${((x + y) % 4) + 1}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x=${x}&y=${y}&z=${z}`

/** 计算视口内可见瓦片 */
function buildTiles() {
  const { w, h } = size
  if (!w || !h) return
  const n = 2 ** zoom.value
  const c = project(center.lat, center.lng, zoom.value)
  const originX = c.x - w / 2
  const originY = c.y - h / 2
  const x0 = Math.floor(originX / TILE_SIZE) - 1
  const x1 = Math.floor((originX + w) / TILE_SIZE) + 1
  const y0 = Math.max(0, Math.floor(originY / TILE_SIZE) - 1)
  const y1 = Math.min(n - 1, Math.floor((originY + h) / TILE_SIZE) + 1)

  const list = []
  for (let tx = x0; tx <= x1; tx++) {
    for (let ty = y0; ty <= y1; ty++) {
      const wx = ((tx % n) + n) % n
      list.push({
        key: `${zoom.value}/${wx}/${ty}`,
        url: tileUrl(wx, ty, zoom.value),
        x: tx * TILE_SIZE - originX,
        y: ty * TILE_SIZE - originY
      })
    }
  }
  tiles.value = list
}

let raf = null
function scheduleRender() {
  if (raf) return
  raf = requestAnimationFrame(() => {
    raf = null
    buildTiles()
  })
}

watch([() => center.lat, () => center.lng, zoom, () => size.w, () => size.h], scheduleRender)

// 基地定位点屏幕坐标
const base = computed(() => toScreen(BASE.lat, BASE.lng))

// 设备均安装在基地（当前位置）同一地点，屏幕上围绕定位点环形铺开，避免标记完全重叠
const markers = computed(() => {
  const list = props.hosts.filter(
    (h) => h.type === undefined || ['host', 'camera', 'irrigation'].includes(h.type)
  )
  const origin = toScreen(BASE.lat, BASE.lng)
  const n = list.length
  return list.map((h, i) => {
    const angle = (i / n) * Math.PI * 2 - Math.PI / 2
    const radius = n > 1 ? 26 : 0
    const cos = Math.cos(angle)
    return {
      id: h.id,
      name: h.name,
      online: h.online,
      type: h.type === 'camera' ? 'camera' : h.type === 'irrigation' ? 'irrigation' : 'soil',
      raw: h,
      x: origin.x + cos * radius,
      y: origin.y + Math.sin(angle) * radius,
      side: cos > 0.3 ? 'right' : cos < -0.3 ? 'left' : 'center'
    }
  })
})

function toScreen(lat, lng) {
  const { w, h } = size
  const c = project(center.lat, center.lng, zoom.value)
  const p = project(lat, lng, zoom.value)
  return { x: p.x - c.x + w / 2, y: p.y - c.y + h / 2 }
}

function setZoom(next) {
  const z = clamp(next, MIN_ZOOM, MAX_ZOOM)
  if (z === zoom.value) return
  zoom.value = z
  buildTiles()
}

function resetView() {
  center.lat = BASE.lat
  center.lng = BASE.lng
  zoom.value = 14
  buildTiles()
}

function onWheel(e) {
  setZoom(zoom.value + (e.deltaY > 0 ? -1 : 1))
}

// 拖拽平移
let start = null
function onDown(e) {
  if (e.button !== 0) return
  start = {
    x: e.clientX,
    y: e.clientY,
    center: project(center.lat, center.lng, zoom.value)
  }
  dragging.value = true
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}

function onMove(e) {
  if (!start) return
  const next = unproject(
    start.center.x - (e.clientX - start.x),
    start.center.y - (e.clientY - start.y),
    zoom.value
  )
  center.lat = clamp(next.lat, -75, 75)
  center.lng = next.lng
}

function onUp() {
  start = null
  dragging.value = false
  window.removeEventListener('mousemove', onMove)
  window.removeEventListener('mouseup', onUp)
}

let observer = null
onMounted(() => {
  observer = new ResizeObserver(([entry]) => {
    size.w = entry.contentRect.width
    size.h = entry.contentRect.height
    buildTiles()
  })
  observer.observe(wrapRef.value)
})

onUnmounted(() => {
  observer && observer.disconnect()
  onUp()
  if (raf) cancelAnimationFrame(raf)
})
</script>

<style scoped>
.home-map {
  position: relative;
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: #061029;
  cursor: grab;
  user-select: none;
  isolation: isolate;
}
.home-map.dragging {
  cursor: grabbing;
}

.tile-layer {
  position: absolute;
  inset: 0;
}
.tile {
  position: absolute;
  top: 0;
  left: 0;
  width: 256px;
  height: 256px;
  /* 高德浅色底图反色为深蓝大屏风格：invert+hue-rotate 近似还原色相，只翻转明暗 */
  filter: invert(1) hue-rotate(185deg) saturate(1.45) brightness(1.3) contrast(0.96);
  pointer-events: none;
}

/* 暗角 + 蓝色氛围，和参考图一致 */
.map-veil {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(120% 90% at 50% 45%, rgba(8, 30, 74, 0.06) 40%, rgba(3, 10, 30, 0.62) 100%),
    linear-gradient(180deg, rgba(6, 26, 66, 0.2), rgba(4, 12, 34, 0.3));
  box-shadow: inset 0 0 150px rgba(14, 70, 160, 0.28);
}
/* 只给底图染蓝，保留原有明暗层次 */
.map-veil::after {
  content: '';
  position: absolute;
  inset: 0;
  background: #1552a0;
  mix-blend-mode: color;
  opacity: 0.55;
}

/* 基地脉冲定位点 */
.base-point {
  position: absolute;
  width: 0;
  height: 0;
  pointer-events: none;
}
.base-point .core {
  position: absolute;
  left: -7px;
  top: -7px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #3fc5ff;
  border: 2px solid #eaf7ff;
  box-shadow: 0 0 14px rgba(63, 197, 255, 0.95);
}
.base-point .ring {
  position: absolute;
  left: -7px;
  top: -7px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 1px solid rgba(126, 230, 255, 0.9);
  animation: ping 2.4s ease-out infinite;
}
@keyframes ping {
  0% { transform: scale(1); opacity: 0.9; }
  80%, 100% { transform: scale(3.4); opacity: 0; }
}
.base-label {
  position: absolute;
  left: 0;
  top: 26px;
  transform: translateX(-50%);
  font-size: 11px;
  color: #9fe2ff;
  white-space: nowrap;
  text-shadow: 0 1px 4px #04081a, 0 0 8px rgba(10, 30, 70, 0.9);
}

/* 设备标记：pin 精确落在坐标点，名称标签向两侧展开，避免同点多设备互相压住 */
.pin-wrap {
  position: absolute;
  width: 0;
  height: 0;
  padding: 0;
  cursor: pointer;
  z-index: 2;
}
.pin-wrap.side-center {
  z-index: 4;
}
.pin-wrap:hover {
  z-index: 6;
}
.pin {
  position: absolute;
  left: -11px;
  top: -11px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: #04101f;
  border: 2px solid #ffffff;
  box-shadow: 0 2px 8px rgba(2, 10, 28, 0.75);
  transition: transform 0.18s, box-shadow 0.18s;
}
.pin.soil {
  background: #2fe08a;
  box-shadow: 0 0 12px rgba(47, 224, 138, 0.85), 0 2px 8px rgba(2, 10, 28, 0.7);
}
.pin.camera {
  background: #3fc5ff;
  box-shadow: 0 0 12px rgba(63, 197, 255, 0.85), 0 2px 8px rgba(2, 10, 28, 0.7);
}
.pin.irrigation {
  background: #f5a623;
  box-shadow: 0 0 12px rgba(245, 166, 35, 0.85), 0 2px 8px rgba(2, 10, 28, 0.7);
}
.pin.off {
  filter: grayscale(0.75) brightness(0.8);
}
.pin.active {
  transform: scale(1.18);
  border-color: #eaf7ff;
}
.pin-wrap:hover .pin {
  transform: scale(1.15);
}

.pin-label {
  position: absolute;
  top: 15px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 10px;
  line-height: 15px;
  color: #eaf4ff;
  white-space: nowrap;
  padding: 0 5px;
  border-radius: 2px;
  background: rgba(5, 16, 40, 0.72);
  text-shadow: 0 1px 3px #000;
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
}
/* 正上方的标记：标签翻到 pin 上面，避免压住基地点 */
.pin-wrap.side-center .pin-label {
  top: auto;
  bottom: 15px;
}
.pin-wrap.side-right .pin-label {
  left: 13px;
  top: -7px;
  transform: none;
}
.pin-wrap.side-left .pin-label {
  right: 13px;
  left: auto;
  top: -7px;
  transform: none;
}

/* 控件 */
.map-ctrl {
  position: absolute;
  right: 14px;
  bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 1px;
  z-index: 3;
}
.map-ctrl button {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  color: #bfe6ff;
  background: rgba(8, 28, 66, 0.88);
  border: 1px solid #1d4670;
  transition: background 0.2s, border-color 0.2s;
}
.map-ctrl button:hover {
  background: #123368;
  border-color: #2a6bb0;
}
.map-ctrl button:first-child {
  border-radius: 3px 3px 0 0;
}
.map-ctrl button.reset {
  border-radius: 0 0 3px 3px;
  font-size: 13px;
}

/* 图例 */
.map-legend {
  position: absolute;
  right: 14px;
  top: 12px;
  display: flex;
  gap: 12px;
  padding: 6px 10px;
  border: 1px solid #1d4670;
  border-radius: 3px;
  background: rgba(6, 20, 48, 0.8);
  font-size: 11px;
  color: #9cc0ee;
  z-index: 3;
}
.map-legend span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.lg {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.lg.soil { background: #2fe08a; box-shadow: 0 0 6px #2fe08a; }
.lg.camera { background: #3fc5ff; box-shadow: 0 0 6px #3fc5ff; }
.lg.irrigation { background: #f5a623; box-shadow: 0 0 6px #f5a623; }

.map-scale {
  position: absolute;
  right: 58px;
  bottom: 16px;
  font-size: 11px;
  color: rgba(143, 179, 232, 0.65);
  pointer-events: none;
}
</style>
