<template>
  <teleport to="body">
    <div v-if="modelValue" class="dlg-mask" @click.self="close">
      <div class="dlg-wrap" :style="{ width }">
        <div class="dlg-inner">
          <div class="dlg-head">
            <span class="dlg-title">{{ title }}</span>
            <button class="dlg-close" @click="close">✕</button>
          </div>
          <div class="dlg-body">
            <slot />
          </div>
          <div v-if="$slots.footer" class="dlg-foot">
            <slot name="footer" />
          </div>
        </div>
      </div>
    </div>
  </teleport>
</template>

<script setup>
const props = defineProps({
  modelValue: Boolean,
  title: { type: String, default: '' },
  width: { type: String, default: '420px' }
})
const emit = defineEmits(['update:modelValue'])
const close = () => emit('update:modelValue', false)
</script>

<style scoped>
.dlg-mask {
  position: fixed;
  inset: 0;
  background: rgba(2, 6, 18, 0.72);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.dlg-wrap {
  padding: 1px;
  clip-path: polygon(12px 0, 100% 0, 100% calc(100% - 12px), calc(100% - 12px) 100%, 0 100%, 0 12px);
  background: linear-gradient(135deg, #2a7ec2, #14406e);
}
.dlg-inner {
  clip-path: polygon(12px 0, 100% 0, 100% calc(100% - 12px), calc(100% - 12px) 100%, 0 100%, 0 12px);
  background: linear-gradient(180deg, #0b2350, #081636);
  padding: 14px 16px;
}
.dlg-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.dlg-title {
  font-size: 14px;
  font-weight: 600;
  color: #eaf4ff;
}
.dlg-close {
  color: #7d9cc9;
  font-size: 13px;
}
.dlg-close:hover {
  color: #fff;
}
.dlg-body {
  font-size: 13px;
  color: #b9d2f5;
  max-height: 60vh;
  overflow: auto;
}
.dlg-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 14px;
}
</style>
