<template>
  <nav class="nav-tabs">
    <div
      v-for="tab in tabs"
      :key="tab"
      class="tab"
      :class="{ active: modelValue === tab }"
      @click="$emit('update:modelValue', tab)"
    >
      <i class="corner tl"></i>
      <i class="corner tr"></i>
      <i class="corner bl"></i>
      <i class="corner br"></i>
      <span class="tab-name">{{ tab }}</span>
      <svg v-if="modelValue === tab" viewBox="0 0 24 24" width="12" height="12" fill="none" class="tab-arrow">
        <path d="m9 6 6 6-6 6" stroke="#7ee6ff" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
    </div>
  </nav>
</template>

<script setup>
import { NAV_TABS } from './navigationTabs.js'

defineProps({
  modelValue: { type: String, default: '首页' }
})
defineEmits(['update:modelValue'])

const tabs = NAV_TABS
</script>

<style scoped>
.nav-tabs {
  flex: none;
  display: flex;
  justify-content: center;
  gap: 22px;
  padding: 10px 0 10px;
}

.tab {
  position: relative;
  width: 122px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  cursor: pointer;
  border-radius: 5px;
  border: 1px solid rgba(63, 169, 245, 0.38);
  background: linear-gradient(180deg, rgba(16, 46, 96, 0.85) 0%, rgba(8, 24, 56, 0.9) 100%);
  color: #b9d2f5;
  font-size: 14px;
  transition: color 0.25s, border-color 0.25s, box-shadow 0.25s, background 0.25s;
}
.tab:hover {
  color: #e6f4ff;
  border-color: rgba(99, 205, 255, 0.7);
  box-shadow: 0 0 10px rgba(46, 160, 255, 0.25);
}

/* 四角亮线 */
.corner {
  position: absolute;
  width: 7px;
  height: 7px;
  border-color: rgba(126, 230, 255, 0.9);
  border-style: solid;
  border-width: 0;
  opacity: 0.85;
  transition: opacity 0.25s;
}
.corner.tl { top: -1px; left: -1px; border-top-width: 1.6px; border-left-width: 1.6px; border-top-left-radius: 4px; }
.corner.tr { top: -1px; right: -1px; border-top-width: 1.6px; border-right-width: 1.6px; border-top-right-radius: 4px; }
.corner.bl { bottom: -1px; left: -1px; border-bottom-width: 1.6px; border-left-width: 1.6px; border-bottom-left-radius: 4px; }
.corner.br { bottom: -1px; right: -1px; border-bottom-width: 1.6px; border-right-width: 1.6px; border-bottom-right-radius: 4px; }

.tab.active {
  color: #ffffff;
  font-weight: 600;
  border-color: rgba(126, 230, 255, 0.95);
  background: linear-gradient(180deg, rgba(23, 110, 190, 0.95) 0%, rgba(12, 62, 128, 0.95) 100%);
  box-shadow:
    0 0 14px rgba(46, 180, 255, 0.5),
    inset 0 0 12px rgba(80, 190, 255, 0.25);
  animation: tab-breathe 2.6s ease-in-out infinite;
}
@keyframes tab-breathe {
  0%, 100% { box-shadow: 0 0 10px rgba(46, 180, 255, 0.4), inset 0 0 10px rgba(80, 190, 255, 0.2); }
  50% { box-shadow: 0 0 20px rgba(46, 180, 255, 0.65), inset 0 0 16px rgba(80, 190, 255, 0.35); }
}
.tab.active .corner {
  opacity: 1;
}
.tab-arrow {
  filter: drop-shadow(0 0 3px rgba(126, 230, 255, 0.9));
}
</style>
