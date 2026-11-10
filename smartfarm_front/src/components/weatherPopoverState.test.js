import test from 'node:test'
import assert from 'node:assert/strict'
import { createWeatherPopoverState } from './weatherPopoverState.js'

function createFakeClock() {
  let nextId = 1
  const pending = new Map()

  return {
    schedule(callback, delay) {
      const id = nextId++
      pending.set(id, { callback, delay })
      return id
    },
    cancel(id) {
      pending.delete(id)
    },
    runNext() {
      const [id, task] = pending.entries().next().value || []
      if (!task) return
      pending.delete(id)
      task.callback()
    },
    pendingTasks() {
      return [...pending.values()]
    },
  }
}

test('leaving the header closes the weather card only after the configured delay', () => {
  const clock = createFakeClock()
  const state = createWeatherPopoverState({
    delay: 200,
    schedule: clock.schedule,
    cancel: clock.cancel,
  })

  state.enterTrigger()
  assert.equal(state.open.value, true)

  state.leaveTrigger()
  assert.equal(state.open.value, true)
  assert.deepEqual(clock.pendingTasks().map((task) => task.delay), [200])

  clock.runNext()
  assert.equal(state.open.value, false)
})

test('moving from the header into the weather card cancels the pending close', () => {
  const clock = createFakeClock()
  const state = createWeatherPopoverState({
    schedule: clock.schedule,
    cancel: clock.cancel,
  })

  state.enterTrigger()
  state.leaveTrigger()
  state.enterPanel()
  clock.runNext()

  assert.equal(state.open.value, true)
  assert.equal(clock.pendingTasks().length, 0)

  state.leavePanel()
  clock.runNext()
  assert.equal(state.open.value, false)
})

test('an open alert dialog locks the weather card until the dialog closes', () => {
  const clock = createFakeClock()
  const state = createWeatherPopoverState({
    schedule: clock.schedule,
    cancel: clock.cancel,
  })

  state.enterPanel()
  state.setLocked(true)
  state.leavePanel()
  clock.runNext()
  assert.equal(state.open.value, true)

  state.setLocked(false)
  clock.runNext()
  assert.equal(state.open.value, false)
})
