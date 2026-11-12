<script setup>
import { computed } from 'vue'
import { stationCoordinates } from '../mapUtils'

const props = defineProps({
  station: { type: Object, required: true },
  active: { type: Boolean, default: false }
})
const emit = defineEmits(['select', 'focus'])
const isFull = computed(() => props.station.status === 'SNAPSHOT_FULL')
const hasCoordinates = computed(() => Boolean(stationCoordinates(props.station)))
const isPrivate = computed(() => (props.station.amapName || '').includes('内部专用'))

function selectStation() {
  emit('select', props.station)
}
</script>

<template>
  <article
    class="station-card"
    :class="{ 'is-active': active }"
    tabindex="0"
    role="group"
    :aria-label="`${station.name}，按回车查看详情`"
    @click="selectStation"
    @keydown.enter.self.prevent="selectStation"
    @keydown.space.self.prevent="selectStation"
  >
    <div class="station-card__head">
      <h2 :title="station.name">{{ station.name }}</h2>
      <div class="station-card__tags">
        <span class="charging-tag">{{ station.chargingMode || '类型未公开' }}</span>
        <span v-if="isPrivate" class="private-tag">内部专用</span>
      </div>
    </div>
    <p class="address" :title="station.address">{{ station.address || '地址未公开' }}</p>
    <div class="station-card__status">
      <span class="station-status" :class="{ 'is-full': isFull }"><span class="status-dot" aria-hidden="true"></span>{{ isFull ? '已满' : '有空闲' }}</span>
      <span class="snapshot-label">状态快照</span>
    </div>
    <div class="resource-row">
      <div><strong>{{ station.availableChargers ?? '—' }}<small> / {{ station.totalChargers ?? '—' }}</small></strong><span>空闲 / 总枪</span></div>
      <div><strong class="power-value">{{ station.powerSummary || '暂无公开数据' }}</strong><span>公开功率</span></div>
      <div><strong><small>¥</small>{{ station.electricityPrice ?? '—' }}</strong><span>快照电价 / 度</span></div>
    </div>
    <p class="policy-line" :title="station.parkingPolicy"><span>停车：</span>{{ station.parkingPolicy || '暂无公开政策' }}</p>
    <p class="snapshot-line">快照时间：{{ station.snapshotTime || '未公开' }}</p>
    <div class="station-card__actions">
      <el-button class="station-details-button" type="primary" plain @click.stop="selectStation">查看详情</el-button>
      <button type="button" class="station-map-button" :disabled="!hasCoordinates" :aria-label="hasCoordinates ? `在地图中定位${station.name}` : `${station.name}地图位置暂未补全`" :title="hasCoordinates ? '地图定位' : '地图位置暂未补全'" @click.stop="emit('focus', station)">
        <svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="6.5"/><circle cx="12" cy="12" r="2"/><path d="M12 2v3.5M12 18.5V22M2 12h3.5M18.5 12H22"/></svg>
      </button>
    </div>
  </article>
</template>
