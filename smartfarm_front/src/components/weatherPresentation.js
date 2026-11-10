export function alertTone(level = '') {
  if (level.includes('红')) return 'red'
  if (level.includes('橙')) return 'orange'
  if (level.includes('黄')) return 'yellow'
  return 'blue'
}

export function weatherSymbol(weather = '') {
  if (weather.includes('雷')) return 'storm'
  if (weather.includes('雨')) return 'rain'
  if (weather.includes('雪')) return 'snow'
  if (weather.includes('雾') || weather.includes('霾')) return 'fog'
  if (weather.includes('晴') && !weather.includes('云')) return 'sunny'
  return 'cloudy'
}
