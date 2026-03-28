export const WEATHER_TYPES = [
  'unknown',
  'sunny',
  'cloudy',
  'overcast',
  'light_rain',
  'heavy_rain',
  'thunderstorm',
  'snow',
  'windy',
  'fog',
  'dust'
] as const

export const WEATHER_SEASONS = ['spring', 'summer', 'autumn', 'winter'] as const
export const WEATHER_INTENSITIES = ['light', 'normal', 'rich'] as const
export const WEATHER_PARTICLE_PRESETS = [
  'sunny',
  'light_rain',
  'heavy_rain',
  'thunderstorm',
  'snow',
  'windy',
  'dust',
  'aurora',
  'sakura',
  'leaves',
  'fireflies'
] as const

export type WeatherType = typeof WEATHER_TYPES[number]
export type WeatherSeason = typeof WEATHER_SEASONS[number]
export type WeatherIntensity = typeof WEATHER_INTENSITIES[number]
export type WeatherParticlePreset = typeof WEATHER_PARTICLE_PRESETS[number]
export type WeatherAccentEffect = 'none' | 'aurora' | 'sakura' | 'leaves' | 'fireflies'
type WeatherParticleWeather = Extract<WeatherType, WeatherParticlePreset>

export interface WeatherEffect {
  enabled: boolean
  city: string
  weather: WeatherType
  isNight: boolean
  temperature: number | null
  windLevel: number
  humidity: number | null
  season: WeatherSeason
  airQuality: string
  intensity: WeatherIntensity
}

export interface WeatherEffectPayload extends Record<string, unknown> {
  enabled?: unknown
  city?: unknown
  weather?: unknown
  isNight?: unknown
  temperature?: unknown
  windLevel?: unknown
  humidity?: unknown
  season?: unknown
  airQuality?: unknown
  intensity?: unknown
}

export interface WeatherRenderProfile {
  weather: WeatherType
  accentEffect: WeatherAccentEffect
  particlePreset: WeatherParticlePreset | null
  particleEnabled: boolean
  densityScale: number
  cloudCount: number
  reducedMotion: boolean
  isMobile: boolean
  pageFactor: number
  showMist: boolean
  showLightning: boolean
  showDustGlow: boolean
  showWindLines: boolean
}

const DEFAULT_REFRESH_MINUTES = 30
const PARTICLE_WEATHER_TYPES = new Set<WeatherParticleWeather>([
  'sunny',
  'light_rain',
  'heavy_rain',
  'thunderstorm',
  'snow',
  'windy',
  'dust'
])
const CLOUD_WEATHER_TYPES = new Set<WeatherType>([
  'sunny',
  'cloudy',
  'overcast',
  'light_rain',
  'heavy_rain',
  'thunderstorm',
  'snow',
  'windy',
  'fog',
  'dust'
])
const CALM_WEATHER_TYPES = new Set<WeatherType>(['sunny', 'cloudy', 'overcast'])
const STORMY_WEATHER_TYPES = new Set<WeatherType>(['light_rain', 'heavy_rain', 'thunderstorm', 'snow', 'fog', 'dust'])
const INTENSITY_SCALE: Record<WeatherIntensity, number> = {
  light: 0.72,
  normal: 1,
  rich: 1.28
}

export const DEFAULT_WEATHER_EFFECT: Readonly<WeatherEffect> = Object.freeze({
  enabled: false,
  city: '北京',
  weather: 'unknown',
  isNight: false,
  temperature: null,
  windLevel: 0,
  humidity: null,
  season: 'spring',
  airQuality: 'unknown',
  intensity: 'normal'
})

function isWeatherType(value: unknown): value is WeatherType {
  return typeof value === 'string' && WEATHER_TYPES.includes(value as WeatherType)
}

function isWeatherSeason(value: unknown): value is WeatherSeason {
  return typeof value === 'string' && WEATHER_SEASONS.includes(value as WeatherSeason)
}

function isWeatherIntensity(value: unknown): value is WeatherIntensity {
  return typeof value === 'string' && WEATHER_INTENSITIES.includes(value as WeatherIntensity)
}

function isParticleWeatherType(value: WeatherType): value is WeatherParticleWeather {
  return PARTICLE_WEATHER_TYPES.has(value as WeatherParticleWeather)
}

export function normalizeWeatherEffect(payload: WeatherEffectPayload = {}): WeatherEffect {
  const weather = isWeatherType(payload.weather) ? payload.weather : DEFAULT_WEATHER_EFFECT.weather
  const season = isWeatherSeason(payload.season) ? payload.season : DEFAULT_WEATHER_EFFECT.season
  const intensity = isWeatherIntensity(payload.intensity) ? payload.intensity : DEFAULT_WEATHER_EFFECT.intensity

  return {
    ...DEFAULT_WEATHER_EFFECT,
    ...payload,
    enabled: Boolean(payload.enabled),
    city: String(payload.city || DEFAULT_WEATHER_EFFECT.city),
    weather,
    isNight: Boolean(payload.isNight),
    season,
    intensity: INTENSITY_SCALE[intensity] ? intensity : DEFAULT_WEATHER_EFFECT.intensity,
    temperature: toFiniteNumber(payload.temperature),
    windLevel: toFiniteNumber(payload.windLevel, 0),
    humidity: toFiniteNumber(payload.humidity),
    airQuality: String(payload.airQuality || DEFAULT_WEATHER_EFFECT.airQuality)
  }
}

export function resolveRefreshMinutes(siteInfo: Record<string, unknown> = {}) {
  const raw = Number(siteInfo.weatherRefreshMinutes)
  if (!Number.isFinite(raw)) {
    return DEFAULT_REFRESH_MINUTES
  }

  return Math.min(Math.max(raw, 10), 180)
}

export function resolveRenderProfile({
  effect = DEFAULT_WEATHER_EFFECT,
  reducedMotion = false,
  isMobile = false,
  deviceMemory = 8,
  routePath = '/',
  viewportWidth = 1440
}: {
  effect?: WeatherEffect | WeatherEffectPayload
  reducedMotion?: boolean
  isMobile?: boolean
  deviceMemory?: number
  routePath?: string
  viewportWidth?: number
} = {}) {
  const normalizedEffect = normalizeWeatherEffect(effect as WeatherEffectPayload)
  const weather = normalizedEffect.weather
  const articleLikePage = routePath.startsWith('/post/')
  let densityScale = INTENSITY_SCALE[normalizedEffect.intensity]

  if (isMobile || viewportWidth < 1100) {
    densityScale *= 0.78
  }
  if (deviceMemory <= 4) {
    densityScale *= 0.86
  }
  if (articleLikePage) {
    densityScale *= 0.76
  }

  if (reducedMotion) {
    densityScale *= 0.45
  }

  densityScale = clamp(densityScale, 0.35, 1.35)

  let cloudCount = 0
  if (CLOUD_WEATHER_TYPES.has(weather)) {
    cloudCount = isMobile ? 2 : densityScale > 1.02 ? 4 : 3
    if (weather === 'sunny') {
      cloudCount = isMobile ? 1 : 2
    } else if (weather === 'thunderstorm' || weather === 'windy') {
      cloudCount = isMobile ? 3 : 5
    } else if (weather === 'fog') {
      cloudCount = isMobile ? 2 : 4
    }
  }

  const accentEffect = resolveAccentEffect(normalizedEffect)
  const particlePreset = resolveParticlePreset(weather, accentEffect)

  return {
    weather,
    accentEffect,
    particlePreset,
    particleEnabled: normalizedEffect.enabled && !reducedMotion && Boolean(particlePreset),
    densityScale,
    cloudCount,
    reducedMotion,
    isMobile,
    pageFactor: articleLikePage ? 0.76 : 1,
    showMist: normalizedEffect.enabled && ['fog', 'dust', 'cloudy', 'overcast'].includes(weather),
    showLightning: normalizedEffect.enabled && weather === 'thunderstorm' && !reducedMotion,
    showDustGlow: normalizedEffect.enabled && weather === 'dust',
    showWindLines: normalizedEffect.enabled && weather === 'windy'
  } satisfies WeatherRenderProfile
}

export function buildCloudLayers(profile: {
  cloudCount: number
  isMobile: boolean
  weather: string
}) {
  return Array.from({ length: profile.cloudCount }, (_, index) => {
    const width = Math.round((profile.isMobile ? 150 : 210) + index * 68)
    const height = Math.round(width * 0.34)
    const top = 8 + index * 13
    const left = -18 + index * 24
    const opacityBase = profile.weather === 'sunny' ? 0.18 : profile.weather === 'fog' ? 0.16 : 0.24
    const opacity = Math.min(opacityBase + index * 0.03, 0.36)
    const duration =
      profile.weather === 'heavy_rain' || profile.weather === 'thunderstorm'
        ? 24 + index * 5
        : profile.weather === 'windy'
          ? 18 + index * 4
          : 34 + index * 8

    return {
      id: `${profile.weather}-${index}`,
      style: {
        '--cloud-width': `${width}px`,
        '--cloud-height': `${height}px`,
        '--cloud-top': `${top}%`,
        '--cloud-left': `${left}%`,
        '--cloud-opacity': opacity.toFixed(2),
        '--cloud-duration': `${duration}s`,
        '--cloud-delay': `${index * -6}s`,
        '--cloud-scale': (0.94 + index * 0.08).toFixed(2),
        '--cloud-blur': `${profile.weather === 'fog' ? 38 : 28}px`
      }
    }
  })
}

export function shouldShowCloudLayer(weather: string) {
  return CLOUD_WEATHER_TYPES.has(weather as WeatherType)
}

function resolveAccentEffect(effect: WeatherEffect): WeatherAccentEffect {
  if (!effect.enabled) {
    return 'none'
  }

  const weather = effect.weather
  if (effect.isNight && weather === 'sunny') {
    return 'aurora'
  }

  if (STORMY_WEATHER_TYPES.has(weather)) {
    return 'none'
  }

  if (effect.season === 'spring' && CALM_WEATHER_TYPES.has(weather)) {
    return 'sakura'
  }

  if (effect.season === 'autumn' && (CALM_WEATHER_TYPES.has(weather) || weather === 'windy')) {
    return 'leaves'
  }

  if (effect.season === 'summer' && effect.isNight && CALM_WEATHER_TYPES.has(weather)) {
    return 'fireflies'
  }

  return 'none'
}

function resolveParticlePreset(weather: WeatherType, accentEffect: WeatherAccentEffect): WeatherParticlePreset | null {
  if (accentEffect && accentEffect !== 'none') {
    return accentEffect
  }

  if (isParticleWeatherType(weather)) {
    return weather
  }

  return null
}

function toFiniteNumber(value: unknown): number | null
function toFiniteNumber(value: unknown, fallback: number): number
function toFiniteNumber(value: unknown, fallback: number | null = null) {
  const number = Number(value)
  return Number.isFinite(number) ? number : fallback
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}
