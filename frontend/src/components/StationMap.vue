<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AMapLoader from '@amap/amap-jsapi-loader'
import { amapUri, stationCoordinates } from '../mapUtils'

const props = defineProps({ activeStation: { type: Object, default: null } })
const emit = defineEmits(['select', 'location', 'location-state'])
const container = ref(null), shell = ref(null)
const status = ref('loading'), message = ref(''), busy = ref(false)
const routeSummary = ref(''), fallbackUrl = ref(''), navigationUrl = ref('')
const markerCount = computed(() => stationCoordinates(props.activeStation) ? 1 : 0)
let AMap, map, driving, activeMarker, renderedStation, renderedDestination, markerVisible = false
let destroyed = false, generation = 0, routeRequest = 0
let locationAttempted = false, cachedPosition = null, locationPromise = null, locationRequest = 0
let cancelLocation, pendingNavigation, loadController

function showStationMarker() {
  if (map && activeMarker && !markerVisible && !destroyed) {
    map.add(activeMarker)
    markerVisible = true
  }
}

function hideStationMarker() {
  if (map && activeMarker && markerVisible) map.remove([activeMarker])
  markerVisible = false
}

function clearRoute() {
  routeRequest++
  driving?.clear()
  driving = null
  pendingNavigation?.close()
  pendingNavigation = null
  routeSummary.value = ''
  message.value = ''
  fallbackUrl.value = ''
  navigationUrl.value = ''
  busy.value = false
  showStationMarker()
}

function syncMarker() {
  if (!map || destroyed) return
  const position = stationCoordinates(props.activeStation)
  const destination = position ? `${props.activeStation.id}:${position.join(',')}` : null
  if (activeMarker && destination === renderedDestination) {
    renderedStation = props.activeStation
    return
  }
  clearRoute()
  hideStationMarker()
  activeMarker = null
  renderedStation = props.activeStation
  renderedDestination = destination
  if (!position) return
  const marker = new AMap.Marker({
    position, title: props.activeStation.name, anchor: 'bottom-center', zIndex: 200,
    content: '<button class="station-pin" type="button" aria-label="查看当前站点详情"><svg width="38" height="46" viewBox="0 0 38 46" aria-hidden="true"><path d="M19 44C15 38 2 28 2 19a17 17 0 0 1 34 0c0 9-13 19-17 25Z" fill="#2563eb" stroke="white" stroke-width="2"/><path d="m21 9-9 12h6l-1 9 9-13h-6l1-8Z" fill="white"/></svg></button>'
  })
  activeMarker = marker
  marker.on('click', () => {
    if (!destroyed && activeMarker === marker && props.activeStation) emit('select', props.activeStation)
  })
  showStationMarker()
  map.setZoomAndCenter(16, position)
}

function publishLocation(position) {
  if (destroyed) return
  emit('location', position ? [...position] : null)
  emit('location-state', position ? 'success' : 'failed')
}

// One session-only request is shared by recommendations, routes and navigation.
// A failed/refused request stays cached until the user explicitly retries.
function requestUserLocation(retry = false) {
  if (destroyed) return Promise.resolve(null)
  if (locationPromise) return locationPromise
  if (!retry && locationAttempted) return Promise.resolve(cachedPosition ? [...cachedPosition] : null)
  if (!map || !AMap) { publishLocation(null); return Promise.resolve(null) }
  locationAttempted = true
  const current = ++locationRequest
  emit('location-state', 'loading')
  const promise = new Promise(resolve => {
    let settled = false, timer
    function finish(position = null) {
      if (settled) return
      settled = true
      clearTimeout(timer)
      if (current === locationRequest) cancelLocation = null
      if (destroyed || current !== locationRequest) { resolve(null); return }
      cachedPosition = position
      publishLocation(position)
      resolve(position ? [...position] : null)
    }
    cancelLocation = () => finish()
    timer = setTimeout(() => finish(), 12000)
    try {
      const geolocation = new AMap.Geolocation({
        enableHighAccuracy: true, timeout: 10000, convert: true, GeoLocationFirst: true,
        showMarker: false, showCircle: false, panToLocation: false, zoomToAccuracy: false
      })
      geolocation.getCurrentPosition((state, result) => {
        if (state !== 'complete' || !result?.position || result.location_type === 'ip') { finish(); return }
        const rawPosition = [result.position.lng, result.position.lat]
        if (rawPosition.some(value => value == null || value === '' || typeof value === 'boolean')) { finish(); return }
        const position = rawPosition.map(Number)
        if (!position.every(Number.isFinite) || Math.abs(position[0]) > 180 || Math.abs(position[1]) > 90) { finish(); return }
        finish(position)
      })
    } catch { finish() }
  })
  locationPromise = promise
  promise.then(() => { if (locationPromise === promise) locationPromise = null })
  return promise
}

function retryLocation() {
  return requestUserLocation(true)
}

async function initialize() {
  if (destroyed) return
  const key = import.meta.env.VITE_AMAP_JS_KEY
  if (!key) { status.value = 'unconfigured'; publishLocation(null); return }
  if (status.value === 'failed' && !AMap) AMapLoader.reset()
  const current = ++generation
  status.value = 'loading'; message.value = ''
  loadController?.abort()
  const controller = new AbortController()
  loadController = controller
  let timeout = setTimeout(() => controller.abort(), 12000)
  try {
    const serviceHost = import.meta.env.VITE_AMAP_SERVICE_HOST || 'http://127.0.0.1:8084/_AMapService'
    const readiness = await fetch(`${serviceHost}/status`, { signal: controller.signal })
    if (destroyed || current !== generation) return
    if (!readiness.ok) throw new Error('proxy unavailable')
    const readinessBody = await readiness.json()
    if (destroyed || current !== generation) return
    if (!readinessBody.configured) { status.value = 'unconfigured'; publishLocation(null); return }
    clearTimeout(timeout)
    window._AMapSecurityConfig = { serviceHost }
    const sdk = await Promise.race([
      AMapLoader.load({ key, version: '2.0', plugins: ['AMap.Geolocation', 'AMap.Driving'] }),
      new Promise((_, reject) => { timeout = setTimeout(() => reject(new Error('map timeout')), 20000) })
    ])
    if (destroyed || current !== generation) return
    AMap = sdk
    map?.destroy()
    status.value = 'ready'
    await nextTick()
    if (destroyed || current !== generation) return
    const initializedMap = new AMap.Map(container.value, { zoom: 12, viewMode: '2D', center: [106.43, 29.82] })
    map = initializedMap
    map.on('error', () => { if (!destroyed && map === initializedMap) message.value = '地图暂时无法加载，请重试' })
    syncMarker()
    requestUserLocation()
  } catch {
    if (!destroyed && current === generation) {
      status.value = 'failed'
      message.value = '地图暂时无法加载，请检查地图配置或网络后重试'
      publishLocation(null)
    }
  } finally {
    clearTimeout(timeout)
    if (loadController === controller) loadController = null
  }
}

function focus(station = props.activeStation) {
  if (destroyed || station?.id !== props.activeStation?.id) return false
  shell.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  if (renderedStation !== props.activeStation) syncMarker()
  else clearRoute()
  const position = stationCoordinates(props.activeStation)
  if (!map || !position) { message.value = '地图位置暂未补全或地图尚未就绪'; return false }
  map.setZoomAndCenter(16, position)
  return true
}

function isCurrentRoute(current) {
  return !destroyed && current === routeRequest
}

async function planRoute(station = props.activeStation) {
  const targetStation = props.activeStation
  const destination = stationCoordinates(targetStation)
  if (!destination || station?.id !== targetStation.id || busy.value) return
  focus()
  busy.value = true; message.value = ''
  const current = routeRequest
  let routeDriver, origin
  try {
    origin = await requestUserLocation()
    if (!isCurrentRoute(current)) return
    if (!origin || !map) throw new Error('location unavailable')
    routeDriver = new AMap.Driving({ map, policy: 0, showTraffic: false })
    driving = routeDriver
    hideStationMarker()
    const data = await new Promise((resolve, reject) => routeDriver.search(origin, destination, (state, result) => {
      if (!isCurrentRoute(current)) { routeDriver.clear(); resolve(null); return }
      state === 'complete' ? resolve(result) : reject(new Error('route unavailable'))
    }))
    if (!isCurrentRoute(current)) return
    const route = data?.routes?.[0]
    routeSummary.value = `${targetStation.amapName || targetStation.name}${route && Number.isFinite(route.distance) && Number.isFinite(route.time)
      ? `：${(route.distance / 1000).toFixed(1)} 公里，约 ${Math.ceil(route.time / 60)} 分钟` : '：驾车路线已绘制'}`
  } catch {
    if (isCurrentRoute(current)) {
      routeDriver?.clear()
      driving = null
      showStationMarker()
      message.value = origin ? '路线暂不可用，可在高德地图继续规划' : '未获取当前位置，可点击“重新定位”后重试，或在高德地图查看站点'
      fallbackUrl.value = amapUri(targetStation)
    }
  } finally { if (isCurrentRoute(current)) busy.value = false }
}

async function navigate(station = props.activeStation) {
  const targetStation = props.activeStation
  if (!stationCoordinates(targetStation) || station?.id !== targetStation.id || busy.value) return
  focus()
  busy.value = true; message.value = ''
  const current = routeRequest
  // Open during the click so a later permission prompt does not block the new tab.
  let target = null, origin
  try {
    target = window.open('about:blank', '_blank')
    if (target) target.opener = null
  } catch { /* The navigation link remains usable if the browser blocks a new tab. */ }
  pendingNavigation = target
  try {
    origin = await requestUserLocation()
    if (!isCurrentRoute(current)) { target?.close(); return }
    if (!origin) throw new Error('location unavailable')
    navigationUrl.value = amapUri(targetStation, origin)
    if (target) target.location.replace(navigationUrl.value)
    else message.value = '请点击下方链接打开高德导航'
  } catch {
    target?.close()
    if (isCurrentRoute(current)) {
      message.value = origin ? '暂时无法打开导航，可通过下方链接继续' : '未获取当前位置，可点击“重新定位”后重试，或在高德地图查看站点'
      fallbackUrl.value = amapUri(targetStation)
    }
  } finally {
    if (pendingNavigation === target) pendingNavigation = null
    if (isCurrentRoute(current)) busy.value = false
  }
}

watch(() => props.activeStation, syncMarker, { deep: true })
onMounted(initialize)
onBeforeUnmount(() => {
  destroyed = true; generation++; routeRequest++; locationRequest++
  loadController?.abort()
  cancelLocation?.()
  pendingNavigation?.close()
  driving?.clear()
  map?.destroy()
  activeMarker = null
})
defineExpose({ focus, planRoute, navigate, retryLocation })
</script>

<template>
  <section ref="shell" class="station-map" aria-label="充电站地图">
    <div class="map-heading">
      <div><span class="map-kicker">探索充电站</span><h2>站点地图</h2></div>
      <span class="map-hint">{{ markerCount ? '点击标记查看站点详情' : '从列表中浏览站点' }}</span>
    </div>
    <div class="map-viewport">
      <div v-if="status === 'unconfigured'" class="map-notice"><span class="map-notice-icon">⌖</span>地图功能未配置，仍可浏览站点与预约。</div>
      <div v-else-if="status === 'loading'" class="map-notice"><span class="map-notice-icon">⌖</span>正在加载周边地图…</div>
      <div v-else-if="status === 'failed'" class="map-notice"><span class="map-notice-icon">⌖</span><span>{{ message }}</span><el-button @click="initialize">重试加载地图</el-button></div>
      <div ref="container" v-show="status === 'ready'" class="map-canvas"></div>
      <p v-if="status === 'ready' && !markerCount" class="map-empty">暂无可显示的站点位置，仍可从列表查看详情。</p>
    </div>
    <div v-if="busy || routeSummary || (message && status === 'ready') || fallbackUrl || navigationUrl" class="map-route" aria-live="polite">
      <span v-if="busy">正在获取当前位置 / 规划路线…</span>
      <span v-if="routeSummary">{{ routeSummary }}</span><span v-if="message && status === 'ready'">{{ message }}</span>
      <el-button v-if="routeSummary || busy" size="small" @click="clearRoute">清除路线</el-button>
      <a v-if="fallbackUrl" :href="fallbackUrl" target="_blank" rel="noopener noreferrer">在高德地图查看</a>
      <a v-if="navigationUrl" :href="navigationUrl" target="_blank" rel="noopener noreferrer">打开高德导航</a>
    </div>
  </section>
</template>

<style scoped>
.station-map { min-width: 0; overflow: hidden; border: 1px solid var(--sc-border); border-radius: 18px; background: var(--sc-surface); box-shadow: 0 7px 24px rgb(15 23 42 / 4%); }
.map-heading { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 19px 22px; }
.map-kicker { display: block; margin-bottom: 5px; color: var(--sc-primary); font-size: 11px; letter-spacing: .08em; }
.map-heading h2 { margin: 0; color: var(--sc-text); font-size: 18px; font-weight: 650; }
.map-hint { color: var(--sc-text-secondary); font-size: 12px; }
.map-viewport { position: relative; min-height: 400px; background: var(--sc-bg-soft); }
.map-canvas { width: 100%; height: 400px; }
.map-notice { display: flex; min-height: 400px; flex-direction: column; align-items: center; justify-content: center; gap: 16px; padding: 30px; color: var(--sc-text-secondary); font-size: 14px; line-height: 1.8; text-align: center; }
.map-notice-icon { display: grid; width: 48px; height: 48px; place-items: center; border-radius: 50%; background: var(--sc-surface); color: var(--sc-primary); font-size: 29px; }
.map-empty { position: absolute; right: 16px; bottom: 24px; left: 16px; width: fit-content; max-width: calc(100% - 32px); margin: 0 auto; padding: 10px 14px; border-radius: 9px; background: var(--sc-surface); color: var(--sc-text-secondary); font-size: 13px; box-shadow: 0 3px 12px rgb(15 23 42 / 8%); }
.map-route { display: flex; flex-wrap: wrap; align-items: center; gap: 10px 14px; padding: 13px 20px; color: var(--sc-text-secondary); font-size: 13px; line-height: 1.7; }
.map-route a { color: var(--sc-primary); text-underline-offset: 3px; }
:deep(.station-pin) { display: block; width: 38px; height: 46px; margin: 0; padding: 0; border: 0; background: transparent; cursor: pointer; filter: drop-shadow(0 3px 4px rgb(37 99 235 / 20%)); }
:deep(.station-pin:focus-visible) { outline: 3px solid var(--sc-primary); outline-offset: 4px; border-radius: 16px; }
@media (max-width: 640px) {
  .map-heading { padding: 17px; }
  .map-hint { max-width: 125px; font-size: 11px; text-align: right; }
  .map-viewport, .map-notice { min-height: 330px; }
  .map-canvas { height: 330px; }
}
</style>
