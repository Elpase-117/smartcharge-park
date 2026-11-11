<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from './api'

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
let rulesRequestId = 0

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
  localStorage.removeItem('smartcharge-token')
  localStorage.removeItem('smartcharge-name')
  loggedIn.value = false
  selected.value = null
  result.value = null
  resultVisible.value = false
}

async function loadStations() {
  loading.value = true
  try {
    stations.value = await api.stations(keyword.value)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function openStation(station) {
  resultVisible.value = false
  result.value = null
  selected.value = await api.station(station.id)
  reservation.timeSlot = ''
  await loadReservationRules()
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
    if (reservation.timeSlot && !availability.value.some(item => item.timeSlot === reservation.timeSlot)) {
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
  loading.value = true
  try {
    result.value = await api.reserve({
      stationId: selected.value.id,
      reservationDate: reservation.reservationDate,
      timeSlot: reservation.timeSlot
    })
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
  <main class="page-shell">
    <section v-if="!loggedIn" class="login-card">
      <div class="brand-mark">SC</div>
      <p class="eyebrow">停车与充电预约 Demo</p>
      <h1>SmartCharge Park</h1>
      <p class="subtitle">出发前查清站点信息，锁定合适的充电时段</p>
      <el-form label-position="top" @submit.prevent="login">
        <el-form-item label="用户名">
          <el-input v-model="loginForm.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="loginForm.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" class="full-button">登录</el-button>
      </el-form>
      <p class="demo-tip">演示账号：driver / 123456</p>
    </section>

    <template v-else>
      <header class="topbar">
        <div>
          <p class="eyebrow">SmartCharge Park</p>
          <h1>找到并预约合适的充电时段</h1>
        </div>
        <div class="user-area">
          <span>{{ displayName }}</span>
          <el-button text @click="logout">退出</el-button>
        </div>
      </header>

      <el-alert class="data-boundary" type="info" :closable="false" show-icon>
        <template #title>重庆北碚区域公开站点样本</template>
        站点基础信息来源于高德地图公开页面；空闲数量为采集时点状态快照。当前 Demo 未接入第三方实时运营接口，预约容量与订单属于业务流程模拟。
      </el-alert>

      <section class="search-panel">
        <el-input v-model="keyword" clearable placeholder="输入站点名称、品牌或地址" @keyup.enter="loadStations" />
        <el-button type="primary" @click="loadStations">查询站点</el-button>
      </section>

      <section v-loading="loading" class="station-grid">
        <article v-for="station in stations" :key="station.id" class="station-card">
          <div class="station-card__head">
            <div>
              <span class="status-dot" :class="{ 'status-dot--full': station.status === 'SNAPSHOT_FULL' }"></span>
              <span>{{ statusText(station) }}</span>
            </div>
            <el-tag :type="station.status === 'SNAPSHOT_FULL' ? 'info' : 'success'" effect="plain">
              {{ station.chargingMode }}
            </el-tag>
          </div>
          <h2 :title="station.name">{{ station.name }}</h2>
          <p class="address" :title="station.address">{{ station.address }}</p>
          <p class="snapshot-line">状态采集：{{ station.snapshotTime }}</p>
          <div class="resource-row">
            <div><strong>{{ station.availableChargers }}/{{ station.totalChargers }}</strong><span>空闲 / 总枪数</span></div>
            <div><strong>{{ station.powerSummary }}</strong><span>公开功率</span></div>
            <div><strong>¥{{ station.electricityPrice }}</strong><span>采集时点电价/度</span></div>
          </div>
          <p class="policy-line" :title="station.parkingPolicy"><strong>停车：</strong>{{ station.parkingPolicy }}</p>
          <el-button type="primary" plain class="full-button" @click="openStation(station)">查看详情 / 预约</el-button>
        </article>
      </section>

      <el-drawer v-model="selected" direction="rtl" size="min(560px, 94vw)" :with-header="false">
        <template v-if="selected">
          <div class="drawer-head">
            <div>
              <p class="eyebrow">站点详情</p>
              <h2>{{ selected.name }}</h2>
              <p class="address">{{ selected.address }}</p>
            </div>
            <el-button circle @click="selected = null">×</el-button>
          </div>

          <el-descriptions :column="2" border class="details">
            <el-descriptions-item label="状态快照">{{ statusText(selected) }}（空闲 {{ selected.availableChargers }}/{{ selected.totalChargers }}）</el-descriptions-item>
            <el-descriptions-item label="采集时点">{{ selected.snapshotTime }}</el-descriptions-item>
            <el-descriptions-item label="运营品牌">{{ selected.operatorBrand }}</el-descriptions-item>
            <el-descriptions-item label="营业时间">{{ selected.operatingHours }}</el-descriptions-item>
            <el-descriptions-item label="充电类型">{{ selected.chargingMode }}</el-descriptions-item>
            <el-descriptions-item label="公开功率">{{ selected.powerSummary }}</el-descriptions-item>
            <el-descriptions-item label="公开电压">{{ selected.voltageV ? `${selected.voltageV} V` : '暂无公开数据' }}</el-descriptions-item>
            <el-descriptions-item label="当前公开价格">¥{{ selected.electricityPrice }}/度</el-descriptions-item>
            <el-descriptions-item label="价格说明" :span="2">{{ selected.priceDetail }}</el-descriptions-item>
            <el-descriptions-item label="停车政策" :span="2">{{ selected.parkingPolicy }}</el-descriptions-item>
            <el-descriptions-item label="接口类型">{{ selected.connectorType || '暂无公开数据' }}</el-descriptions-item>
            <el-descriptions-item label="兼容信息">{{ selected.compatibility || '暂无公开数据' }}</el-descriptions-item>
            <el-descriptions-item label="数据来源">{{ selected.dataSource }}</el-descriptions-item>
            <el-descriptions-item label="数据边界" :span="2">{{ selected.dataNotice }}</el-descriptions-item>
          </el-descriptions>

          <section class="reservation-box">
            <h3>选择日期与预约时段</h3>
            <el-date-picker
              v-model="reservation.reservationDate"
              type="date"
              value-format="YYYY-MM-DD"
              :editable="false"
              :clearable="false"
              :disabled="rulesLoading || reservationMaxDays === null"
              :disabled-date="disabledReservationDate"
              @change="loadAvailability"
            />
            <div class="slot-list">
              <button
                v-for="item in availability"
                :key="item.timeSlot"
                class="slot-button"
                :class="{ active: reservation.timeSlot === item.timeSlot }"
                :disabled="item.remaining === 0"
                @click="reservation.timeSlot = item.timeSlot"
              >
                <span>{{ item.timeSlot }}</span>
                <small>剩余 {{ item.remaining }} / {{ item.capacity }}</small>
              </button>
            </div>
            <p v-if="selectedAvailability" class="capacity-note">
              已检查 Demo 预约容量，当前剩余 {{ selectedAvailability.remaining }} / {{ selectedAvailability.capacity }} 个名额。
            </p>
            <el-button type="primary" :loading="loading" :disabled="rulesLoading || reservationMaxDays === null || resultVisible" class="full-button" @click="createReservation">
              创建预约与待支付订单
            </el-button>
          </section>

        </template>
      </el-drawer>

      <el-dialog v-model="resultVisible" title="预约已创建" width="min(480px, 94vw)" append-to-body :close-on-click-modal="false">
        <template v-if="result">
          <p class="capacity-note">已生成 Demo 待支付订单；当前结果仅代表系统内预约容量，不代表现场物理车位或设备状态，尚未进行真实支付。</p>
          <div class="result-grid">
            <span>预约编号</span><strong>{{ result.reservationId }}</strong>
            <span>订单号</span><strong>{{ result.orderNo }}</strong>
            <span>预约日期</span><strong>{{ result.reservationDate }}</strong>
            <span>预约时段</span><strong>{{ result.timeSlot }}</strong>
            <span>订单状态</span><strong>待支付</strong>
            <span>演示金额</span><strong>¥{{ Number(result.estimatedAmount).toFixed(2) }}</strong>
          </div>
        </template>
        <template #footer>
          <el-button type="primary" @click="resultVisible = false">关闭</el-button>
        </template>
      </el-dialog>
    </template>
  </main>
</template>

