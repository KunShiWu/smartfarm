import test from 'node:test'
import assert from 'node:assert/strict'

import { alertTone, weatherSymbol } from './weatherPresentation.js'

test('alertTone maps official warning levels to stable visual tones', () => {
  assert.equal(alertTone('蓝色'), 'blue')
  assert.equal(alertTone('黄色'), 'yellow')
  assert.equal(alertTone('橙色'), 'orange')
  assert.equal(alertTone('红色'), 'red')
  assert.equal(alertTone('未知'), 'blue')
})

test('weatherSymbol chooses a readable symbol for common Chinese weather text', () => {
  assert.equal(weatherSymbol('晴'), 'sunny')
  assert.equal(weatherSymbol('多云'), 'cloudy')
  assert.equal(weatherSymbol('雷阵雨'), 'storm')
  assert.equal(weatherSymbol('小雨'), 'rain')
  assert.equal(weatherSymbol('大雪'), 'snow')
  assert.equal(weatherSymbol('雾'), 'fog')
  assert.equal(weatherSymbol(''), 'cloudy')
})
