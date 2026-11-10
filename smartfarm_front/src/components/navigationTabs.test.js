import test from 'node:test'
import assert from 'node:assert/strict'
import { NAV_TABS, supportsWeatherPopover } from './navigationTabs.js'

test('every navigation page supports the weather popover', () => {
  assert.deepEqual(
    NAV_TABS.filter((tab) => !supportsWeatherPopover(tab)),
    [],
    'weather details must remain available after navigating away from the home page'
  )
})
