import test from 'node:test'
import assert from 'node:assert/strict'
import { createSSRApp } from 'vue'
import { renderToString } from '@vue/server-renderer'
import { createServer } from 'vite'

test('the host sidebar renders the institution and all research members', async (t) => {
  const vite = await createServer({
    server: { middlewareMode: true },
    appType: 'custom',
    logLevel: 'silent',
  })
  t.after(() => vite.close())

  const { default: SidePanel } = await vite.ssrLoadModule('/src/components/SidePanel.vue')
  const html = await renderToString(createSSRApp(SidePanel, { hosts: [], modelValue: '' }))

  assert.match(html, /aria-label="茂林学村AI农业物联网监测系统主要研究成员"/)
  assert.match(html, /厦门大学信息学院/)
  assert.match(html, /郭程/)
  assert.match(html, /吴史焜/)
  assert.match(html, /赖文瑜/)
  assert.match(html, /黄胜鑫/)
  assert.doesNotMatch(html, /张德富教授/)
})
