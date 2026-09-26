<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from './api'

const loggedIn = ref(Boolean(localStorage.getItem('smartcharge-token')))
const loginForm = reactive({ username: 'driver', password: '123456' })
const displayName = ref(localStorage.getItem('smartcharge-name') || '')
const loading = ref(false)
const keyword = ref('')
const stations = ref([])
const selected = ref(null)
const availability = ref([])
const result = ref(null)

const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000)
const reservation = reactive({
  reservationDate: tomorrow.toISOString().slice(0, 10),
  timeSlot: ''
})

const selectedAvailability = computed(() =>
  availability.value.find(item => item.timeSlot === reservation.timeSlot)
)

function statusText(station) {
  return station.status === 'SNAPSHOT_FULL' ? '采集时点已满' : '采集时点有空闲'
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
  result.value = null
  selected.value = await api.station(station.id)
  reservation.timeSlot = ''
  await loadAvailability()
}

async function loadAvailability() {
  if (!selected.value || !reservation.reservationDate) return
  try {
    availability.value = await api.availability(selected.value.id, reservation.reservationDate)
    if (reservation.timeSlot && !availability.value.some(item => item.timeSlot === reservation.timeSlot)) {
      reservation.timeSlot = ''
    }
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function createReservation() {
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
    await loadAvailability()
    ElMessage.success('预约已创建，并生成待支付订单')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (loggedIn.value) loadStations()
})
</script>

<template>
  <main class="page-shell">
    <section v-if="!loggedIn" class="login-card">
      <div class="brand-mark">SC</div>
      <p class="eyebrow">第 1 周单体版 Demo</p>
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
          <h2>{{ station.name }}</h2>
          <p class="address">{{ station.address }}</p>
          <p class="snapshot-line">状态采集：{{ station.snapshotTime }}</p>
          <div class="resource-row">
            <div><strong>{{ station.availableChargers }}/{{ station.totalChargers }}</strong><span>空闲 / 总枪数</span></div>
            <div><strong>{{ station.powerSummary }}</strong><span>公开功率</span></div>
            <div><strong>¥{{ station.electricityPrice }}</strong><span>采集时点电价/度</span></div>
          </div>
          <p class="policy-line"><strong>停车：</strong>{{ station.parkingPolicy }}</p>
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
              :clearable="false"
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
            <el-button type="primary" :loading="loading" class="full-button" @click="createReservation">
              创建预约与待支付订单
            </el-button>
          </section>

          <el-result v-if="result" icon="success" title="预约容量已锁定" sub-title="已生成 Demo 待支付订单；该结果仅保证系统内预约容量，不代表现场物理车位或设备状态">
            <template #extra>
              <div class="result-grid">
                <span>预约编号</span><strong>{{ result.reservationId }}</strong>
                <span>订单号</span><strong>{{ result.orderNo }}</strong>
                <span>预约时段</span><strong>{{ result.reservationDate }} {{ result.timeSlot }}</strong>
                <span>订单状态</span><strong>待支付</strong>
                <span>演示金额</span><strong>¥{{ result.estimatedAmount }}</strong>
              </div>
            </template>
          </el-result>
        </template>
      </el-drawer>
    </template>
  </main>
</template>

