<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AMapLoader from '@amap/amap-jsapi-loader'
import { amapUri, stationCoordinates } from '../mapUtils'

const props = defineProps({ stations: { type: Array, default: () => [] }, selectedId: Number })
const emit = defineEmits(['select'])
const container = ref(null), shell = ref(null)
const status = ref('loading'), message = ref(''), busy = ref(false)
const routeSummary = ref(''), fallbackUrl = ref(''), navigationUrl = ref('')
const markerCount = computed(() => props.stations.filter(stationCoordinates).length)
let AMap, map, infoWindow, driving, markers = new Map(), destroyed = false, generation = 0, routeRequest = 0

function clearRoute() {
  routeRequest++
  driving?.clear()
  routeSummary.value = ''
  message.value = ''
  fallbackUrl.value = ''
  navigationUrl.value = ''
  busy.value = false
}

function selectMarker(station, openDetails) {
  const marker = markers.get(station.id)
  if (!map || !marker) return false
  for (const [id, item] of markers) { item.setLabel({ content: String(id), direction: 'top' }); item.setzIndex(id === station.id ? 200 : 100) }
  marker.setLabel({ content: `已选 ${station.id}`, direction: 'top' })
  map.setZoomAndCenter(16, stationCoordinates(station))
  const content = document.createElement('div')
  content.className = 'map-info'
  for (const text of [station.name, station.amapAddress || station.address,
    `${station.operatorBrand} · ${station.chargingMode}`,
    `采集时点空闲 / 总枪数：${station.availableChargers} / ${station.totalChargers}`]) {
    const line = document.createElement('p'); line.textContent = text; content.appendChild(line)
  }
  const button = document.createElement('button'); button.textContent = '查看详情 / 预约'
  button.addEventListener('click', () => emit('select', station)); content.appendChild(button)
  infoWindow.setContent(content); infoWindow.open(map, stationCoordinates(station))
  if (openDetails) emit('select', station)
  return true
}

function syncMarkers() {
  if (!map) return
  clearRoute()
  infoWindow.close()
  map.remove([...markers.values()]); markers.clear()
  for (const station of props.stations) {
    const position = stationCoordinates(station)
    if (!position) continue
    const marker = new AMap.Marker({ position, title: station.name, label: { content: String(station.id), direction: 'top' } })
    marker.on('click', () => selectMarker(station, true))
    markers.set(station.id, marker); map.add(marker)
  }
  if (markers.size) map.setFitView([...markers.values()], false, [45, 45, 45, 45], 16)
}

async function initialize() {
  const key = import.meta.env.VITE_AMAP_JS_KEY
  if (!key) { status.value = 'unconfigured'; return }
  if (status.value === 'failed' && !AMap) AMapLoader.reset()
  const current = ++generation
  status.value = 'loading'; message.value = ''
  try {
    const serviceHost = import.meta.env.VITE_AMAP_SERVICE_HOST || 'http://127.0.0.1:8084/_AMapService'
    const readiness = await fetch(`${serviceHost}/status`,
      { signal: AbortSignal.timeout(12000) })
    if (!readiness.ok) throw new Error('proxy unavailable')
    const readinessBody = await readiness.json()
    if (!readinessBody.configured) { status.value = 'unconfigured'; return }
    window._AMapSecurityConfig = { serviceHost }
    const sdk = await Promise.race([
      AMapLoader.load({ key, version: '2.0', plugins: ['AMap.Geolocation', 'AMap.Driving'] }),
      new Promise((_, reject) => setTimeout(() => reject(new Error('map timeout')), 20000))
    ])
    if (destroyed || current !== generation) return
    AMap = sdk
    map?.destroy()
    status.value = 'ready'
    await nextTick()
    if (destroyed || current !== generation) return
    map = new AMap.Map(container.value, { zoom: 12, viewMode: '2D', center: [106.43, 29.82] })
    infoWindow = new AMap.InfoWindow({ offset: new AMap.Pixel(0, -25) })
    map.on('error', () => { message.value = '地图暂时无法加载，请重试' })
    status.value = 'ready'; syncMarkers()
  } catch {
    if (!destroyed && current === generation) { status.value = 'failed'; message.value = '地图暂时无法加载，请检查地图配置或网络后重试' }
  }
}

function focus(station) {
  shell.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  if (!selectMarker(station, false)) message.value = '地图位置暂未补全或地图尚未就绪'
}

function locate() {
  return new Promise((resolve, reject) => {
    if (!map || !AMap) { reject(new Error('map unavailable')); return }
    const geolocation = new AMap.Geolocation({ enableHighAccuracy: true, timeout: 10000, convert: true, GeoLocationFirst: true })
    geolocation.getCurrentPosition((state, result) => {
      if (state !== 'complete' || !result.position || result.location_type === 'ip') { reject(new Error('location denied')); return }
      const position = [Number(result.position.lng), Number(result.position.lat)]
      if (!position.every(Number.isFinite)) { reject(new Error('invalid location')); return }
      resolve(position)
    })
  })
}

async function planRoute(station) {
  if (!stationCoordinates(station) || busy.value) return
  focus(station); clearRoute(); busy.value = true; message.value = ''; fallbackUrl.value = ''; navigationUrl.value = ''
  const current = routeRequest
  try {
    const origin = await locate()
    if (destroyed || current !== routeRequest) return
    driving = new AMap.Driving({ map, policy: 0, showTraffic: false })
    const data = await new Promise((resolve, reject) => driving.search(origin, stationCoordinates(station), (state, result) => {
      state === 'complete' ? resolve(result) : reject(new Error('route unavailable'))
    }))
    if (destroyed || current !== routeRequest) return
    const route = data.routes?.[0]
    routeSummary.value = `${station.amapName || station.name}${route && Number.isFinite(route.distance) && Number.isFinite(route.time)
      ? `：${(route.distance / 1000).toFixed(1)} 公里，约 ${Math.ceil(route.time / 60)} 分钟` : '：驾车路线已绘制'}`
  } catch {
    if (current === routeRequest) { message.value = '无法获取当前位置或规划路线，请允许定位后重试；也可在高德页面继续规划'; fallbackUrl.value = amapUri(station) }
  } finally { if (current === routeRequest) busy.value = false }
}

async function navigate(station) {
  if (!stationCoordinates(station) || busy.value) return
  busy.value = true; message.value = ''; fallbackUrl.value = ''; navigationUrl.value = ''
  // Open synchronously from the user gesture so an asynchronous geolocation prompt cannot trigger popup blocking.
  const target = window.open('about:blank', '_blank')
  if (target) target.opener = null
  try {
    const origin = await locate()
    navigationUrl.value = amapUri(station, origin)
    if (target) target.location.replace(navigationUrl.value)
    else message.value = '请点击下方链接打开高德导航'
  } catch {
    target?.close()
    message.value = '无法获取当前位置，请允许定位后重试；可在高德页面继续规划路线'
    fallbackUrl.value = amapUri(station)
    focus(station)
  } finally { busy.value = false }
}

watch(() => props.stations, syncMarkers)
watch(() => props.selectedId, id => { const station = props.stations.find(s => s.id === id); if (station) selectMarker(station, false) })
onMounted(initialize)
onBeforeUnmount(() => { destroyed = true; generation++; routeRequest++; driving?.clear(); infoWindow?.close(); map?.destroy(); markers.clear() })
defineExpose({ focus, planRoute, navigate })
</script>

<template>
  <section ref="shell" class="station-map" aria-label="充电站地图">
    <div class="map-heading"><h2>充电站地图</h2><span>{{ markerCount }} 个站点已补全位置 / 当前 {{ stations.length }} 个站点</span></div>
    <div v-if="status === 'unconfigured'" class="map-notice">地图功能未配置，站点列表和预约功能仍可正常使用。</div>
    <div v-else-if="status === 'loading'" class="map-notice">正在加载地图…</div>
    <div v-if="status === 'failed'" class="map-notice">{{ message }} <el-button @click="initialize">重试加载地图</el-button></div>
    <div ref="container" v-show="status === 'ready'" class="map-canvas"></div>
    <p v-if="status === 'ready' && !markerCount" class="capacity-note">地图位置暂未补全，仍可从下方列表查看详情与预约。</p>
    <div v-if="busy || routeSummary || (message && status === 'ready') || fallbackUrl || navigationUrl" class="map-route" aria-live="polite">
      <span v-if="busy">正在获取当前位置 / 规划路线…</span>
      <span v-if="routeSummary">{{ routeSummary }}</span><span v-if="message && status === 'ready'">{{ message }}</span>
      <el-button v-if="routeSummary || busy" @click="clearRoute">清除路线</el-button>
      <a v-if="fallbackUrl" :href="fallbackUrl" target="_blank" rel="noopener noreferrer">在高德地图查看</a>
      <a v-if="navigationUrl" :href="navigationUrl" target="_blank" rel="noopener noreferrer">打开高德导航</a>
    </div>
  </section>
</template>

<style scoped>
.station-map { margin-top: 22px; padding: 20px; border: 1px solid #dbe5f1; border-radius: 14px; background: white; }
.map-heading { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; align-items: center; }
.map-heading h2 { margin: 0; font-size: 20px; }
.map-heading span, .map-notice { color: #68788c; font-size: 14px; }
.map-canvas { width: 100%; height: 380px; }
.map-notice { padding: 24px 0; line-height: 1.8; }
.map-route { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-top: 14px; font-size: 14px; }
.map-route a { color: #1769e0; }
@media (max-width: 640px) { .map-canvas { height: 300px; } }
</style>
