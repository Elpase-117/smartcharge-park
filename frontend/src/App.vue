<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from './api'
import StationMap from './components/StationMap.vue'
import StationCard from './components/StationCard.vue'
import { distanceMeters, formatDistance, nearestStation, stationCoordinates } from './mapUtils'

const loggedIn = ref(false)
const loginForm = reactive({ username: 'driver', password: '123456' })
const displayName = ref(localStorage.getItem('smartcharge-name') || '')
const loading = ref(false)
const keyword = ref('')
const stations = ref([])
const selected = ref(null)
const availability = ref([])
const result = ref(null)
const resultVisible = ref(false)
const reservationMaxDays = ref(null)
const rulesLoading = ref(false)
const stationMap = ref(null)
const activeMapStation = ref(null)
const userLocation = ref(null)
const locationStatus = ref('loading')
const mapSelectionMode = ref('auto')
const appliedKeyword = ref('')
const resultStation = ref(null)
let rulesRequestId = 0
let stationRequestId = 0
let detailRequestId = 0
let requestedKeyword = ''

const recommendationTitle = computed(() => {
  if (mapSelectionMode.value === 'manual' && activeMapStation.value) return '当前查看'
  if (!activeMapStation.value) return '推荐站点'
  if (userLocation.value) return appliedKeyword.value ? '搜索结果中距您最近' : '距您最近'
  return '推荐站点'
})
const activeDistance = computed(() => {
  const point = stationCoordinates(activeMapStation.value)
  return userLocation.value && point ? formatDistance(distanceMeters(userLocation.value, point)) : ''
})

function chooseAutomaticStation() {
  mapSelectionMode.value = 'auto'
  activeMapStation.value = nearestStation(stations.value, userLocation.value)
}

function receiveLocation(position) {
  userLocation.value = position
  if (mapSelectionMode.value === 'auto') chooseAutomaticStation()
}

function retryLocation() {
  mapSelectionMode.value = 'auto'
  stationMap.value?.retryLocation()
}

function displayDate(value) {
  const parts = (value || '').split('-')
  return parts.length === 3 ? `${Number(parts[1])}月${Number(parts[2])}日` : value
}

function formatLocalDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const tomorrow = new Date()
tomorrow.setDate(tomorrow.getDate() + 1)
const reservation = reactive({
  reservationDate: formatLocalDate(tomorrow),
  timeSlot: ''
})

const selectedAvailability = computed(() =>
  availability.value.find(item => item.timeSlot === reservation.timeSlot)
)

function statusText(station) {
  return station.status === 'SNAPSHOT_FULL' ? '采集时点已满' : '采集时点有空闲'
}

function disabledReservationDate(date) {
  if (reservationMaxDays.value === null) return true
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const lastDay = new Date(today)
  lastDay.setDate(lastDay.getDate() + reservationMaxDays.value)
  const day = new Date(date)
  day.setHours(0, 0, 0, 0)
  return Number.isNaN(day.getTime()) || day < today || day > lastDay
}

async function loadReservationRules() {
  const requestId = ++rulesRequestId
  rulesLoading.value = true
  reservationMaxDays.value = null
  availability.value = []
  try {
    const rules = await api.reservationRules()
    if (requestId !== rulesRequestId) return
    if (!Number.isInteger(rules.maxDays) || rules.maxDays < 0) {
      throw new Error('预约日期规则暂不可用，请重新打开站点重试')
    }
    reservationMaxDays.value = rules.maxDays
    if (disabledReservationDate(new Date(`${reservation.reservationDate}T00:00:00`))) {
      const defaultDate = new Date()
      if (rules.maxDays > 0) defaultDate.setDate(defaultDate.getDate() + 1)
      reservation.reservationDate = formatLocalDate(defaultDate)
      reservation.timeSlot = ''
    }
    if (selected.value) await loadAvailability()
  } catch (error) {
    if (requestId !== rulesRequestId) return
    reservationMaxDays.value = null
    reservation.timeSlot = ''
    availability.value = []
    ElMessage.error(error.message)
  } finally {
    if (requestId === rulesRequestId) rulesLoading.value = false
  }
}

async function login() {
  loading.value = true
  try {
    const data = await api.login(loginForm)
    localStorage.setItem('smartcharge-token', data.token)
    localStorage.setItem('smartcharge-name', data.displayName)
    displayName.value = data.displayName
    loggedIn.value = true
    await loadStations()
    await loadReservationRules()
    ElMessage.success('登录成功')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function logout() {
  stationRequestId++
  detailRequestId++
  rulesRequestId++
  localStorage.removeItem('smartcharge-token')
  localStorage.removeItem('smartcharge-name')
  loggedIn.value = false
  selected.value = null
  result.value = null
  resultVisible.value = false
  userLocation.value = null
  activeMapStation.value = null
  mapSelectionMode.value = 'auto'
  locationStatus.value = 'loading'
  requestedKeyword = ''
  appliedKeyword.value = ''
  keyword.value = ''
  stations.value = []
  rulesLoading.value = false
  reservationMaxDays.value = null
}

async function loadStations() {
  const requestId = ++stationRequestId
  const query = keyword.value.trim()
  requestedKeyword = query
  loading.value = true
  try {
    const items = await api.stations(query)
    if (requestId !== stationRequestId) return
    stations.value = items
    appliedKeyword.value = query
    chooseAutomaticStation()
  } catch (error) {
    if (requestId === stationRequestId) ElMessage.error(error.message)
  } finally {
    if (requestId === stationRequestId) loading.value = false
  }
}

watch(keyword, value => {
  if (!value.trim() && (appliedKeyword.value || requestedKeyword)) loadStations()
})

async function openStation(station) {
  if (!station) return
  const requestId = ++detailRequestId
  mapSelectionMode.value = 'manual'
  activeMapStation.value = station
  resultVisible.value = false
  result.value = null
  selected.value = null
  try {
    const detail = await api.station(station.id)
    if (requestId !== detailRequestId) return
    selected.value = detail
    activeMapStation.value = detail
    reservation.timeSlot = ''
    await loadReservationRules()
  } catch (error) {
    if (requestId === detailRequestId) ElMessage.error(error.message)
  }
}

async function mapAction(action, station = selected.value) {
  if (!station) return
  mapSelectionMode.value = 'manual'
  activeMapStation.value = station
  selected.value = null
  await nextTick()
  stationMap.value?.[action](station)
}

async function loadAvailability() {
  if (!selected.value || !reservation.reservationDate ||
      disabledReservationDate(new Date(`${reservation.reservationDate}T00:00:00`))) return
  const stationId = selected.value.id
  const date = reservation.reservationDate
  try {
    const items = await api.availability(stationId, date)
    if (selected.value?.id !== stationId || reservation.reservationDate !== date) return
    availability.value = items
    if (reservation.timeSlot && !availability.value.some(item => item.timeSlot === reservation.timeSlot && !item.expired)) {
      reservation.timeSlot = ''
    }
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function createReservation() {
  if (loading.value || resultVisible.value || rulesLoading.value || reservationMaxDays.value === null) return
  if (!reservation.timeSlot) {
    ElMessage.warning('请选择预约时段')
    return
  }
  if (selectedAvailability.value?.expired) {
    reservation.timeSlot = ''
    ElMessage.warning('该预约时段已开始或已结束，请选择其他时段')
    return
  }
  loading.value = true
  const submittedStation = selected.value
  try {
    result.value = await api.reserve({
      stationId: selected.value.id,
      reservationDate: reservation.reservationDate,
      timeSlot: reservation.timeSlot
    })
    resultStation.value = submittedStation
    resultVisible.value = true
    await loadAvailability()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (!localStorage.getItem('smartcharge-token')) return
  try {
    const user = await api.me()
    displayName.value = user.displayName
    localStorage.setItem('smartcharge-name', user.displayName)
    loggedIn.value = true
    await loadStations()
    await loadReservationRules()
  } catch (error) {
    if (error.status === 401) logout()
    else ElMessage.error(error.message)
  }
})
</script>

<template>
  <main class="page-shell" :class="{ 'login-shell': !loggedIn }">
    <section v-if="!loggedIn" class="login-layout" aria-label="SmartCharge Park 用户登录">
      <section class="login-brand" aria-label="平台介绍">
        <div class="login-brand__top"><div class="brand-mark" aria-hidden="true">SC</div><span>SmartCharge Park</span></div>
        <div class="login-brand__content">
          <p class="login-kicker">让出行更从容</p>
          <h1>智慧停车与新能源<br>汽车充电预约平台</h1>
          <p class="login-slogan">查站点 · 看容量 · 先预约 · 再出发</p>
          <ul class="login-features">
            <li><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m3 5 6-2 6 2 6-2v16l-6 2-6-2-6 2Z"/><path d="M9 3v16m6-14v16"/></svg><span>真实站点地图</span></li>
            <li><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="5" width="18" height="15" rx="3"/><path d="M7 3v4m10-4v4M3 10h18m-13 5h2m4 0h2"/></svg><span>预约容量查询</span></li>
            <li><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg><span>充电时段预约</span></li>
            <li><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m21 3-7 18-3-8-8-3Z"/></svg><span>导航与站点定位</span></li>
          </ul>
        </div>
        <p class="login-brand__footer">出发前，安排好下一次充电。</p>
      </section>
      <section class="login-card" aria-labelledby="login-heading">
        <h2 id="login-heading">欢迎登录</h2>
        <p class="subtitle">登录后查看附近充电站并创建预约</p>
        <el-form label-position="top" @submit.prevent="login">
          <el-form-item label="用户名"><el-input v-model="loginForm.username" autocomplete="username" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="loginForm.password" type="password" show-password autocomplete="current-password" /></el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading" class="full-button">登录</el-button>
        </el-form>
        <p class="demo-tip">演示账号：driver / 123456</p>
      </section>
    </section>

    <template v-else>
      <header class="topbar">
        <div class="brand-lockup">
          <div class="brand-symbol" aria-hidden="true">SC</div>
          <div><div class="brand-name">SmartCharge Park</div><p class="brand-subtitle">智慧停车 · 充电预约</p></div>
        </div>
        <div class="user-area"><span class="user-avatar" aria-hidden="true">{{ displayName.slice(0, 1) }}</span><span>{{ displayName }}</span><el-button text @click="logout">退出</el-button></div>
      </header>

      <section class="page-intro"><p class="intro-kicker">重庆 · 北碚</p><h1>下一程，充好电再出发</h1><p>找站点、看信息，提前安排合适的充电时段。</p></section>
      <div class="data-boundary" role="note"><span class="boundary-icon" aria-hidden="true">i</span><p>站点地理信息经高德 POI 校准；空闲枪数、电价及停车政策为采集时点快照，未接入运营商实时接口。预约与订单为 Demo 模拟。</p></div>

      <section class="search-panel" aria-label="搜索充电站">
        <el-input v-model="keyword" class="search-field" clearable placeholder="搜索站点名称、品牌或地址" @keyup.enter="loadStations">
          <template #prefix><svg class="search-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5" stroke="currentColor" stroke-width="1.8"/><path d="m16 16 4 4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg></template>
        </el-input>
        <el-button type="primary" @click="loadStations">查询站点</el-button>
      </section>

      <section class="discovery-grid" aria-label="站点地图与推荐">
        <StationMap ref="stationMap" :active-station="activeMapStation" @select="openStation" @location="receiveLocation" @location-state="locationStatus = $event" />
        <aside class="recommendation-card" aria-label="地图站点推荐" aria-live="polite">
          <p class="recommendation-eyebrow"><span class="recommendation-dot" aria-hidden="true"></span>{{ recommendationTitle }}</p>
          <template v-if="activeMapStation">
            <div v-if="activeDistance" class="recommendation-distance"><strong>{{ activeDistance }}</strong><span>直线距离</span></div>
            <h2 class="recommendation-name">{{ activeMapStation.name }}</h2>
            <p class="recommendation-address">{{ activeMapStation.amapAddress || activeMapStation.address }}</p>
            <div class="recommendation-tags"><el-tag effect="plain">{{ activeMapStation.chargingMode }}</el-tag><el-tag v-if="(activeMapStation.amapName || '').includes('内部专用')" type="warning" effect="plain">内部专用</el-tag></div>
            <div class="recommendation-metrics">
              <div><strong>{{ activeMapStation.powerSummary }}</strong><span>公开功率</span></div>
              <div><strong>¥{{ activeMapStation.electricityPrice }}</strong><span>快照电价 / 度</span></div>
              <div><strong>{{ activeMapStation.availableChargers }} / {{ activeMapStation.totalChargers }}</strong><span>快照空闲 / 总枪</span></div>
            </div>
            <div class="recommendation-actions"><el-button type="primary" @click="openStation(activeMapStation)">查看详情</el-button><el-button :disabled="!stationCoordinates(activeMapStation)" @click="mapAction('navigate', activeMapStation)">高德导航</el-button></div>
          </template>
          <div v-else class="empty-state"><p>暂无可定位站点</p><small>可继续浏览列表，查看站点详情。</small></div>
          <div class="location-note"><span>{{ locationStatus === 'loading' ? '正在获取当前位置…' : userLocation ? '位置仅用于本次推荐与导航' : '未获取当前位置' }}</span><el-button v-if="!userLocation" link type="primary" :disabled="locationStatus === 'loading'" @click="retryLocation">重新定位</el-button></div>
        </aside>
      </section>

      <div class="section-heading"><div><p class="eyebrow">选择适合这一程的站点</p><h2>{{ appliedKeyword ? '搜索结果' : '充电站列表' }}</h2></div><span class="search-summary">{{ stations.length }} 个{{ appliedKeyword ? '结果' : '站点' }}</span></div>
      <section v-loading="loading" class="station-grid" aria-label="充电站列表">
        <StationCard v-for="station in stations" :key="station.id" :station="station" :active="activeMapStation?.id === station.id" @select="openStation" @focus="mapAction('focus', $event)" />
      </section>
      <div v-if="!loading && !stations.length" class="empty-state"><h3>没有找到相关站点</h3><p>试试其他名称、品牌或地址，或清空搜索查看全部站点。</p></div>

      <el-drawer v-model="selected" class="detail-drawer" direction="rtl" size="min(600px, 100vw)" :with-header="false">
        <template v-if="selected">
          <div class="drawer-head"><div><p class="eyebrow">站点详情</p><h2>{{ selected.name }}</h2><p class="address">{{ selected.address }}</p></div><el-button class="drawer-close" circle aria-label="关闭站点详情" @click="selected = null">×</el-button></div>
          <div class="detail-tags"><el-tag effect="plain">{{ selected.chargingMode }}</el-tag><el-tag v-if="selected.amapPoiId" effect="plain">高德 POI 已校准</el-tag><el-tag v-if="(selected.amapName || '').includes('内部专用')" type="warning" effect="plain">内部专用</el-tag></div>
          <div class="detail-hero-metrics"><div><strong>{{ selected.availableChargers }}<small> / {{ selected.totalChargers }}</small></strong><span>快照空闲 / 总枪</span></div><div><strong>{{ selected.powerSummary }}</strong><span>公开功率</span></div><div><strong>¥{{ selected.electricityPrice }}</strong><span>采集电价 / 度</span></div></div>

          <section class="detail-section"><h3 class="section-title">站点信息</h3><dl class="detail-facts"><div><dt>运营品牌</dt><dd>{{ selected.operatorBrand }}</dd></div><div><dt>营业时间</dt><dd>{{ selected.operatingHours }}</dd></div><div><dt>充电类型</dt><dd>{{ selected.chargingMode }}</dd></div><div><dt>公开功率</dt><dd>{{ selected.powerSummary }}</dd></div><div><dt>公开电压</dt><dd>{{ selected.voltageV ? `${selected.voltageV} V` : '暂无公开数据' }}</dd></div><div><dt>接口与兼容</dt><dd>{{ selected.connectorType || '暂无公开数据' }} · {{ selected.compatibility || '暂无公开数据' }}</dd></div></dl></section>

          <section class="detail-section poi-details"><h3 class="section-title">地图位置</h3>
            <el-alert v-if="(selected.amapName || '').includes('内部专用')" type="warning" :closable="false" show-icon title="高德地图将该站点标注为“内部专用”，实际对外开放情况请以现场规则为准。" />
            <template v-if="stationCoordinates(selected)"><p class="poi-name">{{ selected.amapName || selected.name }}</p><p>{{ selected.amapAddress || selected.address }}</p><p v-if="selected.district" class="coordinate-note">{{ selected.district }} · 高德坐标 {{ selected.longitude }}, {{ selected.latitude }}</p><div class="poi-actions"><el-button @click="mapAction('focus')">在地图中定位</el-button><el-button @click="mapAction('planRoute')">规划路线</el-button><el-button @click="mapAction('navigate')">高德导航</el-button></div></template>
            <p v-else class="coordinate-note">地图位置暂未补全，仍可正常预约。</p>
          </section>

          <section class="detail-section"><h3 class="section-title">停车与价格</h3><div class="detail-policy"><span>停车政策</span><p>{{ selected.parkingPolicy }}</p></div><div class="detail-policy"><span>价格说明</span><p>{{ selected.priceDetail }}</p></div><p class="coordinate-note">{{ statusText(selected) }} · {{ selected.snapshotTime }}<br>数据来源：{{ selected.dataSource }}<br>{{ selected.dataNotice }}</p></section>

          <section class="reservation-box"><h3 class="section-title">预约充电时段</h3>
            <div class="reservation-step"><span class="step-number">01</span><label class="step-label" for="reservation-date">选择日期</label></div>
            <el-date-picker id="reservation-date" v-model="reservation.reservationDate" type="date" value-format="YYYY-MM-DD" :editable="false" :clearable="false" :disabled="rulesLoading || reservationMaxDays === null" :disabled-date="disabledReservationDate" @change="loadAvailability" />
            <div class="reservation-step"><span class="step-number">02</span><span class="step-label">选择预约时段</span></div>
            <div class="slot-list"><button v-for="item in availability" :key="item.timeSlot" class="slot-button" :class="{ active: reservation.timeSlot === item.timeSlot }" :disabled="item.expired || item.remaining === 0" :aria-pressed="reservation.timeSlot === item.timeSlot" @click="reservation.timeSlot = item.timeSlot"><span>{{ item.timeSlot }}</span><small>{{ item.expired ? '已过时段' : item.remaining === 0 ? '已满' : `剩余 ${item.remaining} / ${item.capacity}` }}</small></button></div>
            <p v-if="selectedAvailability" class="capacity-note">当前剩余 {{ selectedAvailability.remaining }} / {{ selectedAvailability.capacity }} 个预约名额</p>
            <p class="reservation-boundary">仅为 Demo 系统内预约容量，不代表现场设备可用。</p>
            <el-button type="primary" :loading="loading" :disabled="rulesLoading || reservationMaxDays === null || resultVisible" class="full-button" @click="createReservation">创建预约与待支付订单</el-button>
          </section>
        </template>
      </el-drawer>

      <el-dialog v-model="resultVisible" class="result-dialog" title="预约已创建" width="min(480px, 94vw)" append-to-body :close-on-click-modal="false">
        <template v-if="result"><div class="result-heading"><span class="success-mark" aria-hidden="true">✓</span><h3>充电行程，已安排</h3><p class="result-station">{{ resultStation?.name }}</p></div><div class="result-schedule"><strong>{{ displayDate(result.reservationDate) }}</strong><span>{{ result.timeSlot }}</span></div><div class="result-grid"><span>预约编号</span><strong>{{ result.reservationId }}</strong><span>订单号</span><strong>{{ result.orderNo }}</strong><span>预约日期</span><strong>{{ result.reservationDate }}</strong></div><div class="result-payment"><el-tag type="warning" effect="plain">待支付</el-tag><div><small>演示金额</small><strong>¥{{ Number(result.estimatedAmount).toFixed(2) }}</strong></div></div><p class="result-boundary">已生成 Demo 待支付订单。当前结果仅代表系统内预约容量，不代表现场物理车位或设备状态，尚未进行真实支付。</p></template>
        <template #footer><el-button type="primary" class="full-button" @click="resultVisible = false">关闭</el-button></template>
      </el-dialog>
    </template>
  </main>
</template>
