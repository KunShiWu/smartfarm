export const NAV_TABS = [
  '首页',
  '实时数据',
  '历史数据',
  '主机列表',
  '视频监控',
  '设备控制',
  'AI智能体',
  '报警管理',
]

export function supportsWeatherPopover(tab) {
  return NAV_TABS.includes(tab)
}
