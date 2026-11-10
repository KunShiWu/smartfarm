<template>
  <section class="agent-page">
    <header class="agent-header">
      <div>
        <p class="eyebrow">SMARTFARM · AGRICULTURE AGENT</p>
        <h2>农业智能体</h2>
        <p class="subtitle">基于 DeepSeek 与 MCP 工具的农场数据查询和辅助分析</p>
      </div>
      <div class="header-actions">
        <span class="mode-tag">文本 · 图片诊断</span>
        <button class="clear-button" :disabled="loading || !messages.length" @click="clearChat">清空对话</button>
      </div>
    </header>

    <div class="agent-workspace">
      <section class="chat-panel" aria-label="智能体对话">
        <div ref="feedRef" class="message-feed" role="log" aria-live="polite" aria-relevant="additions">
          <div v-if="messages.length === 0" class="welcome">
            <div class="welcome-mark">AI</div>
            <h3>你好，我是智慧农场智能体</h3>
            <p>支持天气与设备查询，也可以上传作物叶片图片进行病害初步识别。</p>
            <div class="suggestions">
              <button v-for="example in examples" :key="example" :disabled="loading" @click="quickAsk(example)">
                {{ example }}
              </button>
            </div>
          </div>

          <div v-for="message in messages" :key="message.id" class="message-row" :class="message.role">
            <span class="avatar">{{ message.role === 'user' ? '我' : 'AI' }}</span>
            <div class="message-body">
              <div class="message-meta">{{ message.role === 'user' ? '你' : '农业智能体' }} · {{ message.time }}</div>
              <div
                v-if="message.role === 'assistant'"
                class="message-text markdown-body"
                v-html="renderMarkdown(message.content)"
              ></div>
              <div v-else class="message-text user-message-text">
                <img
                  v-if="message.imageUrl"
                  class="message-image"
                  :src="message.imageUrl"
                  :alt="message.imageName || '用户上传的农作物图片'"
                />
                <div v-if="message.content">{{ message.content }}</div>
              </div>
            </div>
          </div>

          <div v-if="loading" class="message-row assistant">
            <span class="avatar">AI</span>
            <div class="message-body">
              <div class="message-meta">农业智能体 · 正在处理</div>
              <div class="message-text working">{{ loadingText }}<span class="dots">...</span></div>
            </div>
          </div>
        </div>

        <div v-if="error" class="error-message" role="alert">
          <span>{{ error }}</span>
          <button @click="error = ''">关闭</button>
        </div>

        <div class="composer">
          <input
            ref="fileInputRef"
            class="sr-only"
            type="file"
            accept="image/jpeg,image/png,.jpg,.jpeg,.png"
            aria-label="选择农作物图片"
            @change="onImageSelected"
          />
          <div v-if="previewUrl" class="selected-image">
            <img :src="previewUrl" alt="待发送图片预览" />
            <span class="selected-name" :title="selectedFile?.name">{{ selectedFile?.name }}</span>
            <button type="button" :disabled="loading" @click="removeSelectedImage">移除</button>
          </div>
          <label class="sr-only" for="agent-question">向农业智能体提问</label>
          <textarea
            id="agent-question"
            v-model="draft"
            :disabled="loading"
            :maxlength="2000"
            rows="2"
            placeholder="输入农业问题，或点击下方按钮上传叶片图片进行病害初步识别…"
            @keydown.enter.exact.prevent="submitMessage"
          ></textarea>
          <div class="composer-bottom">
            <div class="composer-left">
              <button type="button" class="attach-button" :disabled="loading" @click="openImagePicker">
                <span aria-hidden="true">＋</span> 上传图片
              </button>
              <span>JPG / PNG · ≤ 5 MB · Enter 发送</span>
            </div>
            <div class="send-actions">
              <span>{{ draft.length }}/2000</span>
              <button
                type="button"
                class="send-button"
                :disabled="loading || (!draft.trim() && !selectedFile)"
                @click="submitMessage"
              >
                {{ loading ? '处理中…' : '发送问题' }}
              </button>
            </div>
          </div>
        </div>
      </section>

      <aside class="agent-aside">
        <div class="aside-heading">已对接的查询工具</div>
        <div class="tool-item">
          <div class="tool-name">get_weather</div>
          <p>查询农场所在地天气和预警信息</p>
        </div>
        <div class="tool-item">
          <div class="tool-name">get_farm_hosts</div>
          <p>查看农场设备列表及在线状态</p>
        </div>
        <div class="tool-item">
          <div class="tool-name">get_environment</div>
          <p>查询指定监测主机的环境参数</p>
        </div>
        <div class="tool-item">
          <div class="tool-name">identify_crop_disease</div>
          <p>读取本次上传的农作物照片，提取可见症状和候选病因</p>
        </div>
        <div class="aside-note">
          <strong>使用说明</strong>
          <p>设备离线或数据过期时，分析结果不应将其视为实时监测值。</p>
          <p>图片识别属于初步观察，无法替代专业植保检测和病原学确诊。</p>
          <p>当前自动携带最近 5 条文本消息作为上下文，清空对话后重置。</p>
        </div>
      </aside>
    </div>
  </section>
</template>

<script setup>
import { nextTick, onUnmounted, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import { chatWithAgent, uploadCropImage } from '../api'

// 模型生成的内容先转换为 Markdown，再执行 HTML 净化。
const markdown = new MarkdownIt({ html: false, linkify: true, breaks: true })
markdown.renderer.rules.table_open = () => '<div class="markdown-table-scroll"><table>\n'
markdown.renderer.rules.table_close = () => '</table></div>\n'

function renderMarkdown(content) {
  const html = markdown.render(typeof content === 'string' ? content : '')
  return DOMPurify.sanitize(html, { USE_PROFILES: { html: true } })
}

const MAX_IMAGE_BYTES = 5 * 1024 * 1024
const DEFAULT_IMAGE_QUESTION = '请分析我上传的农作物图片，描述可见症状、候选病因与不确定性，并给出谨慎的处理建议。'

const examples = [
  '查询我的智慧农场当前天气',
  '我的农场现在有哪些设备？',
  '查看土壤监测设备的状态和最近采集时间'
]

const messages = ref([])
const draft = ref('')
const loading = ref(false)
const loadingText = ref('正在分析问题，必要时将查询 MCP 工具')
const error = ref('')
const feedRef = ref(null)
const fileInputRef = ref(null)
const selectedFile = ref(null)
const previewUrl = ref('')
const objectUrls = new Set()
let sequence = 0

function nowTime() {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

async function scrollToLatest() {
  await nextTick()
  if (feedRef.value) feedRef.value.scrollTop = feedRef.value.scrollHeight
}

function quickAsk(question) {
  if (loading.value) return
  draft.value = question
  submitMessage()
}

function releaseObjectUrl(url) {
  if (!url) return
  URL.revokeObjectURL(url)
  objectUrls.delete(url)
}

function openImagePicker() {
  if (!loading.value) fileInputRef.value?.click()
}

function onImageSelected(event) {
  const file = event.target.files?.[0]
  // 重置 input，允许用户再次选择同名文件。
  event.target.value = ''
  if (!file) return

  if (!['image/jpeg', 'image/png'].includes(file.type)) {
    error.value = '只支持 JPG、PNG 图片'
    return
  }
  if (file.size <= 0 || file.size > MAX_IMAGE_BYTES) {
    error.value = '图片大小必须在 0～5 MB 之间'
    return
  }

  if (previewUrl.value) releaseObjectUrl(previewUrl.value)
  selectedFile.value = file
  previewUrl.value = URL.createObjectURL(file)
  objectUrls.add(previewUrl.value)
  error.value = ''
}

function removeSelectedImage() {
  if (loading.value) return
  releaseObjectUrl(previewUrl.value)
  previewUrl.value = ''
  selectedFile.value = null
  if (fileInputRef.value) fileInputRef.value.value = ''
}

function clearChat() {
  if (loading.value) return
  for (const url of objectUrls) URL.revokeObjectURL(url)
  objectUrls.clear()
  messages.value = []
  draft.value = ''
  selectedFile.value = null
  previewUrl.value = ''
  error.value = ''
  if (fileInputRef.value) fileInputRef.value.value = ''
}

onUnmounted(() => {
  for (const url of objectUrls) URL.revokeObjectURL(url)
  objectUrls.clear()
})

function getErrorMessage(e) {
  const data = e?.response?.data
  const detail = data?.mess || data?.detail || data?.message
  return typeof detail === 'string' ? detail : (e?.message || '请求失败，请检查 Java、MCP 和 Agent 服务')
}

async function submitMessage() {
  const enteredText = draft.value.trim()
  const attachedFile = selectedFile.value
  if (loading.value || (!enteredText && !attachedFile)) return

  const question = enteredText || DEFAULT_IMAGE_QUESTION
  // 先提取历史：只保留最近 5 条，不能包含本次当前问题。
  const history = messages.value
    .filter(item => (item.role === 'user' || item.role === 'assistant') && typeof item.content === 'string' && item.content.trim())
    .slice(-5)
    .map(item => ({ role: item.role, content: item.content.slice(0, 2000) }))

  const imagePreview = previewUrl.value
  let postedMessageId = null
  error.value = ''
  loading.value = true
  loadingText.value = attachedFile ? '正在上传作物图片' : '正在查询农场数据'

  try {
    let imageId = null
    if (attachedFile) {
      // 用 multipart/form-data 上传文件，Axios/浏览器自动生成 boundary。
      const uploaded = await uploadCropImage(attachedFile)
      if (typeof uploaded?.image_id !== 'string' || !uploaded.image_id.trim()) {
        throw new Error('图片上传成功，但未收到有效的 image_id')
      }
      imageId = uploaded.image_id
    }

    const newMessage = {
      id: ++sequence,
      role: 'user',
      content: question,
      time: nowTime(),
      imageUrl: attachedFile ? imagePreview : null,
      imageName: attachedFile?.name || ''
    }
    postedMessageId = newMessage.id
    messages.value.push(newMessage)
    draft.value = ''
    await scrollToLatest()

    loadingText.value = imageId ? '正在进行图片视觉识别与病害综合分析' : '正在分析问题并调用 MCP 工具'
    const response = await chatWithAgent({
      message: question,
      history,
      ...(imageId ? { image_id: imageId } : {})
    })
    if (typeof response?.answer !== 'string' || !response.answer.trim()) {
      throw new Error('Agent 未返回有效回答')
    }

    messages.value.push({ id: ++sequence, role: 'assistant', content: response.answer, time: nowTime() })
    if (attachedFile) {
      // 成功后清空待发送选择，但保留 objectURL 供聊天气泡显示，清空对话时释放。
      selectedFile.value = null
      previewUrl.value = ''
    }
  } catch (e) {
    // 失败时移除尚未完成的用户消息，保留选中的图片与输入，便于重新发送。
    if (postedMessageId !== null) {
      messages.value = messages.value.filter(item => item.id !== postedMessageId)
    }
    if (!draft.value.trim()) draft.value = enteredText
    error.value = getErrorMessage(e)
  } finally {
    loading.value = false
    loadingText.value = '正在分析问题，必要时将查询 MCP 工具'
    await scrollToLatest()
  }
}
</script>

<style scoped>
.agent-page {
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  color: #d7e9ff;
  border: 1px solid rgba(40, 110, 173, 0.55);
  background: linear-gradient(155deg, #071b3c 0%, #06112b 48%, #040d24 100%);
}
.agent-header {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 17px 25px;
  border-bottom: 1px solid rgba(63, 169, 245, 0.26);
  background: linear-gradient(90deg, rgba(15, 69, 131, .45), transparent);
}
.eyebrow { color: #6cb4e9; font-size: 10px; letter-spacing: 2px; margin-bottom: 6px; }
h2 { color: #f0f8ff; font-size: 21px; font-weight: 650; letter-spacing: 1px; }
.subtitle { margin-top: 6px; font-size: 12px; color: #87add3; }
.header-actions { display: flex; align-items: center; gap: 12px; }
.mode-tag { padding: 6px 10px; color: #7ee6ff; border: 1px solid rgba(81, 186, 225, .38); border-radius: 14px; font-size: 11px; background: rgba(14, 77, 133, .3); }
.clear-button { color: #93c0e5; border: 1px solid #1d4778; border-radius: 5px; padding: 7px 12px; font-size: 12px; }
.clear-button:hover:enabled { color: #e9f8ff; border-color: #50b2eb; }
.clear-button:disabled { opacity: .4; cursor: not-allowed; }
.agent-workspace { flex: 1; min-height: 0; display: grid; grid-template-columns: minmax(0, 1fr) 260px; }
.chat-panel { min-width: 0; min-height: 0; display: flex; flex-direction: column; }
.message-feed { flex: 1; min-height: 0; overflow-y: auto; padding: 26px 32px 20px; display: flex; flex-direction: column; gap: 22px; }
.welcome { margin: auto; text-align: center; width: min(100%, 720px); padding: 40px 0; }
.welcome-mark { width: 62px; height: 62px; display: grid; place-items: center; margin: 0 auto 23px; font-size: 25px; font-weight: 700; color: #a8f2ff; border-radius: 18px; border: 1px solid rgba(79, 184, 255, .55); background: linear-gradient(135deg, #0b396f, #071c45); box-shadow: 0 0 32px rgba(30, 133, 208, .2); }
.welcome h3 { font-size: 20px; font-weight: 600; color: #ecf8ff; }
.welcome p { max-width: 560px; margin: 15px auto 26px; line-height: 1.8; color: #92b8df; }
.suggestions { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; }
.suggestions button { padding: 11px 14px; border: 1px solid rgba(76, 155, 222, .45); border-radius: 7px; background: rgba(14, 49, 94, .66); color: #c0e4ff; font-size: 12px; text-align: left; }
.suggestions button:hover:enabled { background: #12477f; border-color: #4da6e9; }
.message-row { display: flex; align-items: flex-start; gap: 11px; max-width: 95%; }
.message-row.user { align-self: flex-end; flex-direction: row-reverse; }
.avatar { flex: none; width: 33px; height: 33px; border-radius: 9px; display: grid; place-items: center; background: #10385f; border: 1px solid #276b9d; color: #84ddff; font-size: 12px; font-weight: 700; }
.user .avatar { color: #d7e8ff; border-color: #2e77b7; background: #155292; }
.message-body { min-width: 0; max-width: 100%; }
.message-meta { color: #719cbd; font-size: 11px; margin: 0 0 7px; }
.user .message-meta { text-align: right; }
.message-text { font-size: 13px; line-height: 1.85; white-space: pre-wrap; overflow-wrap: anywhere; padding: 14px 18px; background: rgba(15, 39, 73, .85); border: 1px solid #183d6b; border-radius: 2px 11px 11px 11px; }
.user .message-text { background: #124b87; border-color: #2b75b8; border-radius: 11px 2px 11px 11px; }
.user-message-text { display: flex; flex-direction: column; gap: 9px; }
.message-image { display: block; width: min(300px, 100%); max-height: 240px; object-fit: contain; border-radius: 7px; border: 1px solid rgba(135, 204, 255, .35); background: rgba(3, 14, 35, .6); }
.working { color: #90caee; }
.dots { display: inline-block; animation: blink 1.2s infinite; }
@keyframes blink { 50% { opacity: .25; } }
.error-message { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin: 0 22px 12px; padding: 10px 13px; background: rgba(107, 39, 41, .3); border: 1px solid rgba(221, 108, 103, .5); border-radius: 5px; color: #ffbdb8; }
.error-message button { color: #ffdad7; text-decoration: underline; }
.composer { flex: none; margin: 0 22px 20px; padding: 10px 14px; background: #0b2044; border: 1px solid #27608d; border-radius: 8px; }
.composer:focus-within { border-color: #52b7f0; box-shadow: 0 0 0 1px rgba(41, 136, 212, .2); }
.composer textarea { display: block; width: 100%; min-height: 52px; max-height: 170px; resize: vertical; font: inherit; line-height: 1.6; color: #e8f6ff; border: 0; outline: 0; background: transparent; }
.composer textarea::placeholder { color: #6489ad; }
.composer-bottom { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: #678faf; font-size: 11px; }
.composer-left { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.attach-button { padding: 7px 11px; border-radius: 5px; background: rgba(24, 82, 135, .65); border: 1px solid #3186c3; color: #c7eaff; font-size: 12px; }
.attach-button:hover:enabled { background: #155a96; }
.attach-button:disabled { opacity: .5; cursor: not-allowed; }
.selected-image { display: flex; align-items: center; gap: 10px; padding: 7px 9px; margin-bottom: 9px; border: 1px solid rgba(74, 144, 204, .4); border-radius: 6px; background: rgba(11, 47, 86, .6); }
.selected-image img { width: 55px; height: 49px; object-fit: cover; border-radius: 4px; }
.selected-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; font-size: 12px; color: #b6daef; }
.selected-image button { flex: none; font-size: 12px; color: #f0bab5; border: 1px solid rgba(229, 130, 124, .5); border-radius: 4px; padding: 5px 9px; }
.selected-image button:disabled { opacity: .5; }
@media (max-width: 740px) { .composer-bottom { flex-direction: column; align-items: stretch; } .send-actions { justify-content: space-between; } }
.send-actions { display: flex; align-items: center; gap: 12px; }
.send-button { min-width: 100px; padding: 9px 15px; border-radius: 5px; background: linear-gradient(#2085cc, #0e559c); border: 1px solid #57b6f9; color: #fff; font-weight: 600; font-size: 12px; }
.send-button:hover:enabled { filter: brightness(1.15); }
.send-button:disabled { opacity: .5; cursor: not-allowed; }
.agent-aside { min-height: 0; overflow-y: auto; padding: 24px 19px; border-left: 1px solid rgba(50, 120, 174, .35); background: rgba(5, 20, 47, .55); }
.aside-heading { color: #e1f3ff; font-size: 13px; font-weight: 650; letter-spacing: .6px; margin-bottom: 21px; }
.tool-item { padding: 0 0 16px 11px; margin: 0 0 18px; border-left: 2px solid #2c8eca; }
.tool-name { color: #7cdbff; font-size: 12px; font-weight: 600; }
.tool-item p { margin-top: 6px; color: #85aacf; font-size: 11px; line-height: 1.8; }
.aside-note { padding: 15px; border: 1px solid #23456c; border-radius: 7px; background: rgba(17, 49, 88, .5); }
.aside-note strong { color: #c3e5ff; font-size: 12px; }
.aside-note p { margin-top: 9px; color: #83a9ce; font-size: 11px; line-height: 1.85; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
@media (max-width: 1300px) {
  .agent-workspace { grid-template-columns: minmax(0, 1fr) 220px; }
  .message-feed { padding: 20px; }
  .agent-aside { padding: 20px 13px; }
}

/* v-html 注入的元素不带 Vue scoped 属性，必须通过 :deep() 设置样式。 */
.message-text.markdown-body { white-space: normal; max-width: 100%; }
.markdown-body :deep(p) { margin: 0 0 11px; line-height: 1.8; }
.markdown-body :deep(p:last-child) { margin-bottom: 0; }
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  color: #e8f6ff;
  line-height: 1.45;
  font-weight: 650;
  margin: 17px 0 9px;
  letter-spacing: 0;
}
.markdown-body :deep(h1) { font-size: 18px; }
.markdown-body :deep(h2) { font-size: 16px; }
.markdown-body :deep(h3) { font-size: 14px; }
.markdown-body :deep(h4) { font-size: 13px; }
.markdown-body :deep(h1:first-child),
.markdown-body :deep(h2:first-child),
.markdown-body :deep(h3:first-child),
.markdown-body :deep(h4:first-child) { margin-top: 0; }
.markdown-body :deep(strong) { color: #eaf7ff; font-weight: 700; }
.markdown-body :deep(ul),
.markdown-body :deep(ol) { margin: 7px 0 12px; padding-left: 23px; }
.markdown-body :deep(li) { padding-left: 1px; margin: 3px 0; }
.markdown-body :deep(li > p) { margin: 0 0 5px; }
.markdown-body :deep(blockquote) {
  margin: 10px 0;
  padding: 8px 12px;
  border-left: 3px solid #49a5e6;
  border-radius: 3px;
  background: rgba(25, 75, 122, .32);
  color: #b9daef;
}
.markdown-body :deep(blockquote > p:last-child) { margin-bottom: 0; }
.markdown-body :deep(.markdown-table-scroll) {
  max-width: 100%;
  overflow-x: auto;
  margin: 10px 0 14px;
  border: 1px solid rgba(68, 131, 185, .4);
  border-radius: 5px;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  border-spacing: 0;
  width: 100%;
  min-width: max-content;
  font-size: 12px;
  line-height: 1.65;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid rgba(71, 133, 189, .36);
  padding: 8px 11px;
  text-align: left;
  vertical-align: top;
  white-space: normal;
}
.markdown-body :deep(th) { background: #143a67; color: #d8f2ff; font-weight: 650; }
.markdown-body :deep(tr:nth-child(even) td) { background: rgba(29, 77, 122, .25); }
.markdown-body :deep(a) { color: #78d2ff; text-decoration: underline; overflow-wrap: anywhere; }
.markdown-body :deep(a:hover) { color: #c5edff; }
.markdown-body :deep(code) {
  padding: 2px 5px;
  border-radius: 4px;
  color: #a5e2ff;
  background: #071a33;
  font-family: Consolas, 'Courier New', monospace;
  font-size: .95em;
  overflow-wrap: anywhere;
}
.markdown-body :deep(pre) {
  max-width: 100%;
  overflow-x: auto;
  padding: 11px 13px;
  margin: 10px 0 13px;
  border: 1px solid #264a70;
  border-radius: 6px;
  background: #071a33;
}
.markdown-body :deep(pre code) { background: transparent; padding: 0; white-space: pre; overflow-wrap: normal; }
.markdown-body :deep(hr) { border: 0; border-top: 1px solid #2c5278; margin: 14px 0; }

</style>
