import { ref } from 'vue'

export function createWeatherPopoverState({
  delay = 200,
  schedule = setTimeout,
  cancel = clearTimeout,
} = {}) {
  const open = ref(false)
  let triggerHovered = false
  let panelHovered = false
  let locked = false
  let closeTimer = null

  function cancelClose() {
    if (closeTimer == null) return
    cancel(closeTimer)
    closeTimer = null
  }

  function show() {
    cancelClose()
    open.value = true
  }

  function scheduleClose() {
    cancelClose()
    if (triggerHovered || panelHovered || locked) return
    closeTimer = schedule(() => {
      closeTimer = null
      if (!triggerHovered && !panelHovered && !locked) open.value = false
    }, delay)
  }

  function enterTrigger() {
    triggerHovered = true
    show()
  }

  function leaveTrigger() {
    triggerHovered = false
    scheduleClose()
  }

  function enterPanel() {
    panelHovered = true
    show()
  }

  function leavePanel() {
    panelHovered = false
    scheduleClose()
  }

  function setLocked(value) {
    locked = value
    if (locked) show()
    else scheduleClose()
  }

  function destroy() {
    cancelClose()
  }

  return {
    open,
    enterTrigger,
    leaveTrigger,
    enterPanel,
    leavePanel,
    setLocked,
    destroy,
  }
}
