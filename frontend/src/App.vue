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
      <p class="subtitle">智慧停车与新能源汽车充电预约平台</p>
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
          <h1>停车与充电站点预约</h1>
        </div>
        <div class="user-area">
          <span>{{ displayName }}</span>
          <el-button text @click="logout">退出</el-button>
        </div>
      </header>

      <section class="search-panel">
        <el-input v-model="keyword" clearable placeholder="输入站点名称或地址" @keyup.enter="loadStations" />
        <el-button type="primary" @click="loadStations">查询站点</el-button>
      </section>

      <section v-loading="loading" class="station-grid">
        <article v-for="station in stations" :key="station.id" class="station-card">
          <div class="station-card__head">
            <div>
              <span class="status-dot"></span>
              <span>{{ station.status === 'AVAILABLE' ? '可预约' : station.status }}</span>
            </div>
            <el-tag type="success" effect="plain">停充一体</el-tag>
          </div>
          <h2>{{ station.name }}</h2>
          <p class="address">{{ station.address }}</p>
          <div class="resource-row">
            <div><strong>{{ station.availableParking }}</strong><span>空闲车位</span></div>
            <div><strong>{{ station.availableChargers }}</strong><span>空闲充电桩</span></div>
            <div><strong>¥{{ station.parkingFee }}</strong><span>基础停车费</span></div>
          </div>
          <el-button type="primary" plain class="full-button" @click="openStation(station)">查看详情并预约</el-button>
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
            <el-descriptions-item label="当前状态">可预约</el-descriptions-item>
            <el-descriptions-item label="空闲资源">{{ selected.availableParking }} 个车位 / {{ selected.availableChargers }} 个充电桩</el-descriptions-item>
            <el-descriptions-item label="接口类型">{{ selected.connectorType }}</el-descriptions-item>
            <el-descriptions-item label="快慢充">{{ selected.chargingMode }}</el-descriptions-item>
            <el-descriptions-item label="额定功率">{{ selected.ratedPowerKw }} kW</el-descriptions-item>
            <el-descriptions-item label="当前电价">¥{{ selected.electricityPrice }}/度</el-descriptions-item>
            <el-descriptions-item label="服务费">¥{{ selected.serviceFee }}</el-descriptions-item>
            <el-descriptions-item label="兼容信息">{{ selected.compatibility }}</el-descriptions-item>
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
              已检查该时段容量，当前剩余 {{ selectedAvailability.remaining }} 个名额。
            </p>
            <el-button type="primary" :loading="loading" class="full-button" @click="createReservation">
              创建预约与待支付订单
            </el-button>
          </section>

          <el-result v-if="result" icon="success" title="预约创建成功" sub-title="已同步生成待支付订单">
            <template #extra>
              <div class="result-grid">
                <span>预约编号</span><strong>{{ result.reservationId }}</strong>
                <span>订单号</span><strong>{{ result.orderNo }}</strong>
                <span>预约时段</span><strong>{{ result.reservationDate }} {{ result.timeSlot }}</strong>
                <span>订单状态</span><strong>待支付</strong>
                <span>预计金额</span><strong>¥{{ result.estimatedAmount }}</strong>
              </div>
            </template>
          </el-result>
        </template>
      </el-drawer>
    </template>
  </main>
</template>

