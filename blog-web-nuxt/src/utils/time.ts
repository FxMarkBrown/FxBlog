/**
 * 格式化时间
 * @param time 时间
 * @returns 格式化后的时间
 */
export function formatTime(time?: string | number | Date | null) {
  if (!time) {
    return '未知时间'
  }

  const date = new Date(time)
  if (Number.isNaN(date.getTime())) {
    return '未知时间'
  }

  const now = Date.now()
  const diff = (now - date.getTime()) / 1000

  if (diff < 60) {
    return '刚刚'
  }

  if (diff < 3600) {
    return `${Math.floor(diff / 60)}分钟前`
  }

  if (diff < 86400) {
    return `${Math.floor(diff / 3600)}小时前`
  }

  if (diff < 2592000) {
    return `${Math.floor(diff / 86400)}天前`
  }

  if (diff < 31536000) {
    return `${Math.floor(diff / 2592000)}个月前`
  }

  return formatDate(date)
}

/**
 * 格式化日期
 * @param date 日期
 * @returns 日期字符串
 */
export function formatDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
