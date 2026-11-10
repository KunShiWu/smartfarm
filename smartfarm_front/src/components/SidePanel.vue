<template>
  <aside class="side-panel">
    <div class="panel-head">
      <svg viewBox="0 0 24 24" width="13" height="13" fill="none">
        <rect x="3" y="3" width="18" height="18" rx="2" stroke="#3fa9f5" stroke-width="1.6"/>
        <path d="M3 9h18M9 9v12" stroke="#3fa9f5" stroke-width="1.6"/>
      </svg>
      <span class="panel-title">主机列表</span>
    </div>

    <!-- 状态筛选 -->
    <div class="status-tabs">
      <span
        v-for="t in statusTabs"
        :key="t"
        class="s-tab"
        :class="{ active: statusTab === t }"
        @click="statusTab = t"
      >{{ t }}</span>
    </div>

    <!-- 搜索 -->
    <div class="search-box">
      <input v-model.trim="keyword" type="text" placeholder="请输入主机名称" />
      <svg viewBox="0 0 24 24" width="13" height="13" fill="none">
        <circle cx="11" cy="11" r="6.5" stroke="#5d7fc0" stroke-width="1.8"/>
        <path d="m16 16 4.5 4.5" stroke="#5d7fc0" stroke-width="1.8" stroke-linecap="round"/>
      </svg>
    </div>

    <!-- 统计 + 展开 -->
    <div class="tree-bar">
      <span>共 {{ filteredHosts.length }} 个主机</span>
      <button class="expand-btn" @click="toggleAll">
        {{ allExpanded ? '全部收起' : '全部展开' }}
        <svg viewBox="0 0 24 24" width="11" height="11" fill="none" :style="{ transform: allExpanded ? 'rotate(180deg)' : '' }">
          <path d="m6 9 6 6 6-6" stroke="#8fb3e8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
    </div>

    <!-- 主机树 -->
    <div class="host-tree">
      <div v-for="group in groupedHosts" :key="group.name" class="tree-group">
        <div class="group-row" @click="toggleGroup(group.name)">
          <svg
            viewBox="0 0 24 24" width="11" height="11" fill="none" class="arrow"
            :style="{ transform: collapsed.has(group.name) ? 'rotate(-90deg)' : '' }"
          >
            <path d="m6 9 6 6 6-6" stroke="#8fb3e8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <svg v-if="group.name === '视频监控'" viewBox="0 0 24 24" width="14" height="14" fill="none">
            <rect x="2" y="7" width="13" height="10" rx="2" stroke="#3fc5ff" stroke-width="1.6"/>
            <path d="m15 10 6-3v10l-6-3" stroke="#3fc5ff" stroke-width="1.6" stroke-linejoin="round"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" width="14" height="14" fill="none">
            <path d="M12 3C8 7.5 6 11 6 14.5A6 6 0 0 0 18 14.5C18 11 16 7.5 12 3z" stroke="#3fd2a0" stroke-width="1.6" stroke-linejoin="round"/>
            <path d="M12 8v9" stroke="#3fd2a0" stroke-width="1.4" stroke-linecap="round"/>
          </svg>
          <span class="group-name">{{ group.name }}</span>
        </div>
        <div v-show="!collapsed.has(group.name)" class="group-children">
          <div
            v-for="host in group.children"
            :key="host.id"
            class="host-row"
            :class="{ selected: host.id === modelValue }"
            @click="select(host)"
          >
            <svg v-if="host.type === 'camera'" class="dev-icon" :class="{ on: host.online }" viewBox="0 0 24 24" width="13" height="13" fill="none">
              <rect x="2" y="7" width="13" height="10" rx="2" stroke="currentColor" stroke-width="1.6"/>
              <path d="m15 10 6-3v10l-6-3" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
            </svg>
            <svg v-else-if="host.type === 'irrigation'" class="dev-icon" :class="{ on: host.online }" viewBox="0 0 24 24" width="13" height="13" fill="none">
              <path d="M12 3C8 7.5 6 11 6 14.5A6 6 0 0 0 18 14.5C18 11 16 7.5 12 3z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
              <path d="M12 8v9" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
            </svg>
            <i v-else class="dot" :class="host.online ? 'online' : 'offline'"></i>
            <span class="host-name" :title="host.name">{{ host.name }}</span>
          </div>
        </div>
      </div>
      <div v-if="!groupedHosts.length" class="empty">暂无匹配主机</div>
    </div>

    <ResearchTeamCard />
  </aside>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import ResearchTeamCard from './ResearchTeamCard.vue'

const props = defineProps({
  hosts: { type: Array, default: () => [] },
  modelValue: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'select'])

const statusTabs = ['全部', '在线', '离线']
const statusTab = ref('全部')
const keyword = ref('')
const collapsed = ref(new Set())
const allExpanded = ref(true)

const filteredHosts = computed(() =>
  props.hosts.filter((h) => {
    const matchStatus =
      statusTab.value === '全部' || (statusTab.value === '在线' ? h.online : !h.online)
    const matchKeyword = !keyword.value || h.name.includes(keyword.value)
    return matchStatus && matchKeyword
  })
)

const groupedHosts = computed(() => {
  const map = new Map()
  filteredHosts.value.forEach((h) => {
    if (!map.has(h.group)) map.set(h.group, { name: h.group, children: [] })
    map.get(h.group).children.push(h)
  })
  return [...map.values()]
})

function toggleGroup(name) {
  const next = new Set(collapsed.value)
  next.has(name) ? next.delete(name) : next.add(name)
  collapsed.value = next
}

function toggleAll() {
  allExpanded.value = !allExpanded.value
  collapsed.value = allExpanded.value
    ? new Set()
    : new Set(groupedHosts.value.map((g) => g.name))
}

function select(host) {
  emit('update:modelValue', host.id)
  emit('select', host)
}

// 筛选导致当前选中主机不可见时,自动选中第一个可见主机
watch(filteredHosts, (list) => {
  if (list.length && !list.some((h) => h.id === props.modelValue)) {
    select(list[0])
  }
})
</script>

<style scoped>
.side-panel {
  width: 226px;
  flex: none;
  display: flex;
  flex-direction: column;
  border: 1px solid #12406e;
  background: rgba(7, 20, 48, 0.72);
  min-height: 0;
}

.panel-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 10px 8px;
  border-bottom: 1px solid #12406e;
  background: linear-gradient(90deg, rgba(18, 64, 120, 0.5), transparent);
}
.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: #d7e9ff;
}

.status-tabs {
  display: flex;
  gap: 16px;
  padding: 8px 10px 4px;
}
.s-tab {
  font-size: 12px;
  color: #7d9cc9;
  cursor: pointer;
  padding-bottom: 3px;
  border-bottom: 2px solid transparent;
}
.s-tab.active {
  color: #3fc5ff;
  border-bottom-color: #3fc5ff;
}

.search-box {
  margin: 6px 10px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  height: 28px;
  border: 1px solid #1d4670;
  border-radius: 14px;
  background: #081a3c;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.search-box:focus-within {
  border-color: rgba(99, 205, 255, 0.7);
  box-shadow: 0 0 8px rgba(46, 160, 255, 0.25);
}
.search-box input {
  flex: 1;
  min-width: 0;
  background: transparent;
  border: none;
  color: #d7e9ff;
  font-size: 12px;
}
.search-box input::placeholder {
  color: #4d6a99;
}

.tree-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 10px;
  font-size: 12px;
  color: #7d9cc9;
}
.expand-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  color: #8fb3e8;
}
.expand-btn svg {
  transition: transform 0.2s;
}

.host-tree {
  flex: 1;
  overflow: auto;
  padding: 2px 6px 8px;
}
.group-row {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 4px;
  cursor: pointer;
  color: #9cc0ee;
  font-size: 12px;
}
.arrow {
  transition: transform 0.2s;
}
.group-children {
  padding-left: 18px;
}
.host-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 6px;
  cursor: pointer;
  border-radius: 2px;
  font-size: 12px;
  color: #7d9cc9;
}
.host-row:hover {
  background: rgba(28, 84, 156, 0.25);
}
.host-row.selected {
  background: rgba(28, 116, 200, 0.35);
  color: #d7e9ff;
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex: none;
}
.dot.online {
  background: #2fe08a;
  box-shadow: 0 0 5px rgba(47, 224, 138, 0.8);
}
.dot.offline {
  background: #f0a23c;
  box-shadow: 0 0 5px rgba(240, 162, 60, 0.8);
}
.dev-icon {
  flex: none;
  color: #f0a23c;
}
.dev-icon.on {
  color: #2fe08a;
}
.host-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.empty {
  padding: 20px 0;
  text-align: center;
  color: #4d6a99;
  font-size: 12px;
}
</style>
