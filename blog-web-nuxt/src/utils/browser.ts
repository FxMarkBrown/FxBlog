/**
 * 获取浏览器信息。
 */
export function getBrowserInfo() {
  if (!import.meta.client) {
    return {
      name: 'Unknown',
      version: 'Unknown'
    }
  }

  const userAgent = navigator.userAgent

  if (userAgent.includes('Chrome')) {
    return {
      name: 'Chrome',
      version: resolveVersion(userAgent, 'Chrome/')
    }
  }

  if (userAgent.includes('Firefox')) {
    return {
      name: 'Firefox',
      version: resolveVersion(userAgent, 'Firefox/')
    }
  }

  if (userAgent.includes('Safari') && !userAgent.includes('Chrome')) {
    return {
      name: 'Safari',
      version: resolveVersion(userAgent, 'Safari/')
    }
  }

  if (userAgent.includes('Edg/')) {
    return {
      name: 'Edge',
      version: resolveVersion(userAgent, 'Edg/')
    }
  }

  return {
    name: 'Unknown',
    version: 'Unknown'
  }
}

/**
 * 从 userAgent 中提取版本号。
 */
function resolveVersion(userAgent: string, prefix: string) {
  const versionStart = userAgent.indexOf(prefix)
  if (versionStart === -1) {
    return 'Unknown'
  }

  const rawVersion = userAgent.slice(versionStart + prefix.length)
  const versionEnd = rawVersion.indexOf(' ')
  return versionEnd === -1 ? rawVersion : rawVersion.slice(0, versionEnd)
}
